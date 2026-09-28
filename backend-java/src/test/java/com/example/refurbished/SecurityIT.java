package com.example.refurbished;

import com.example.refurbished.security.AppUser;
import com.example.refurbished.security.AppUserRepository;
import com.example.refurbished.security.OAuthCodeService;
import com.example.refurbished.security.UserRole;
import com.example.refurbished.security.LocalAdminRecovery;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.permit-all-for-tests=false",
        "app.security.google.client-id=test-google-client",
        "app.security.google.client-secret=test-google-secret"
})
class SecurityIT {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private OAuthCodeService oauthCodes;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ApplicationContext context;
    @Autowired private com.example.refurbished.audit.AuditService audit;
    @Autowired private org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @LocalServerPort private int port;

    private AppUser admin;
    private AppUser staff;

    @BeforeEach
    void createUsersInTestDatabase() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        admin = users.saveAndFlush(new AppUser("admin@test.local", "Admin",
                passwords.encode("AdminPassword123!"), UserRole.ADMIN));
        staff = users.saveAndFlush(new AppUser("staff@test.local", "Staff",
                passwords.encode("StaffPassword123!"), UserRole.STAFF));
    }

    @AfterEach
    void cleanupOwnedSecurityFixtures() {
        jdbc.update("DELETE FROM audit_events WHERE actor_user_id IN (?, ?) OR target_id IN (?, ?)",
                admin.getId(), staff.getId(), admin.getId(), staff.getId());
        jdbc.update("DELETE FROM oauth_login_codes WHERE user_id IN (?, ?)", admin.getId(), staff.getId());
        users.deleteAllById(java.util.List.of(admin.getId(), staff.getId()));
        users.flush();
    }

    @Test
    void migrationV5AndAuthenticationAreActive() {
        assertEquals("11", flyway.info().current().getVersion().getVersion());

        ResponseEntity<JsonNode> unauthenticated = http.postForEntity("/api/device-units",
                Map.of(), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        assertEquals("UNAUTHENTICATED", unauthenticated.getBody().get("code").asText());

        ResponseEntity<JsonNode> invalid = login("admin@test.local", "wrong-password");
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
        assertEquals("AUTHENTICATION_FAILED", invalid.getBody().get("code").asText());

        String token = token(login("ADMIN@TEST.LOCAL", "AdminPassword123!"));
        ResponseEntity<JsonNode> me = getWithToken("/api/auth/me", token);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals("ADMIN", me.getBody().get("role").asText());
    }

    @Test
    void roleAuthorizationProtectsAdminAndStaffOperations() {
        String adminToken = token(login("admin@test.local", "AdminPassword123!"));
        String staffToken = token(login("staff@test.local", "StaffPassword123!"));

        ResponseEntity<JsonNode> forbidden = postWithToken("/api/products", Map.of(
                "modelCode", "SEC-STAFF-" + UUID.randomUUID(), "name", "Forbidden", "brand", "Test"), staffToken);
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals("FORBIDDEN", forbidden.getBody().get("code").asText());

        String modelCode = "SEC-ADMIN-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = postWithToken("/api/products", Map.of(
                "modelCode", modelCode, "name", "Authorized", "brand", "Test"), adminToken);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        UUID productId = UUID.fromString(created.getBody().get("id").asText());

        ResponseEntity<JsonNode> intake = postWithToken("/api/device-units", Map.of(
                "productId", productId, "serialNumber", "SEC-" + UUID.randomUUID()), staffToken);
        assertEquals(HttpStatus.CREATED, intake.getStatusCode());

        UUID deviceId = UUID.fromString(intake.getBody().get("id").asText());
        jdbc.update("DELETE FROM device_units WHERE id=?", deviceId);
        jdbc.update("DELETE FROM products WHERE id=?", productId);
    }

    @Test
    void adminCanProvisionUserAndGoogleUsesOneTimeExchangeCode() {
        String adminToken = token(login("admin@test.local", "AdminPassword123!"));
        String email = "created-" + UUID.randomUUID() + "@test.local";
        ResponseEntity<JsonNode> created = postWithToken("/api/users", Map.of(
                "email", email, "displayName", "Created Staff",
                "password", "CreatedPassword123!", "role", "STAFF"), adminToken);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        UUID createdId = UUID.fromString(created.getBody().get("id").asText());

        String code = oauthCodes.issue(staff);
        ResponseEntity<JsonNode> exchange = http.postForEntity("/api/auth/oauth/exchange",
                Map.of("code", code), JsonNode.class);
        assertEquals(HttpStatus.OK, exchange.getStatusCode());
        assertEquals("STAFF", exchange.getBody().get("user").get("role").asText());
        ResponseEntity<JsonNode> reused = http.postForEntity("/api/auth/oauth/exchange",
                Map.of("code", code), JsonNode.class);
        assertEquals(HttpStatus.UNAUTHORIZED, reused.getStatusCode());

        users.deleteById(createdId);
        users.flush();
    }

    @Test
    void googleAuthorizationEndpointRedirectsToGoogle() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<Void> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/oauth2/authorization/google")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(302, response.statusCode());
        URI location = URI.create(response.headers().firstValue("location").orElseThrow());
        assertEquals("accounts.google.com", location.getHost());
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return http.postForEntity("/api/auth/login", Map.of("email", email, "password", password), JsonNode.class);
    }

    @Test
    void swaggerIsPublicButBusinessEndpointsRemainProtected() {
        ResponseEntity<String> ui = http.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(HttpStatus.OK, ui.getStatusCode());
        assertTrue(ui.getBody().contains("swagger-ui"));
        assertEquals(HttpStatus.OK, http.getForEntity("/swagger-ui/swagger-ui-bundle.js", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/v3/api-docs/swagger-config", JsonNode.class).getStatusCode());
        ResponseEntity<JsonNode> response = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode spec = response.getBody();
        assertEquals("bearer", spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertTrue(spec.at("/security/0").has("bearerAuth"));
        JsonNode paths = spec.get("paths");
        for (String path : List.of("/api/auth/login", "/api/auth/me", "/api/auth/change-password",
                "/api/auth/logout-all", "/api/auth/oauth/exchange", "/api/users", "/api/products",
                "/api/products/{id}", "/api/device-units", "/api/device-units/{id}",
                "/api/device-units/{id}/start-inspection", "/api/device-units/{id}/complete-inspection",
                "/api/orders/checkout", "/api/orders/{id}", "/api/warranties", "/api/warranties/{id}",
                "/api/warranties/device-unit/{deviceUnitId}", "/api/health")) {
            assertTrue(paths.has(path), "Missing documented API: " + path);
        }
        assertEquals(0, paths.get("/api/auth/login").get("post").get("security").size());
        assertEquals(0, paths.get("/api/products").get("get").get("security").size());
        assertTrue(paths.get("/api/auth/me").get("get").path("parameters").isMissingNode()
                || paths.get("/api/auth/me").get("get").get("parameters").isEmpty());
        assertEquals(HttpStatus.UNAUTHORIZED, http.getForEntity("/api/device-units", JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity("/api/users", Map.of(), JsonNode.class).getStatusCode());
    }

    @Test
    void changingPasswordRevokesAllOldTokensAndPendingGoogleCodes() {
        String first = token(login("admin@test.local", "AdminPassword123!"));
        String second = token(login("admin@test.local", "AdminPassword123!"));
        String code = oauthCodes.issue(admin);
        assertEquals(HttpStatus.NO_CONTENT, postWithToken("/api/auth/change-password", Map.of(
                "currentPassword", "AdminPassword123!", "newPassword", "UpdatedPassword123!"), first).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", first).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", second).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, login("admin@test.local", "AdminPassword123!").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity("/api/auth/oauth/exchange",
                Map.of("code", code), JsonNode.class).getStatusCode());
        String fresh = token(login("admin@test.local", "UpdatedPassword123!"));
        assertEquals(HttpStatus.OK, getWithToken("/api/auth/me", fresh).getStatusCode());
    }

    @Test
    void invalidPasswordChangeDoesNotRevokeSessionOrChangeCredentials() {
        String access = token(login("staff@test.local", "StaffPassword123!"));
        assertEquals(HttpStatus.UNAUTHORIZED, postWithToken("/api/auth/change-password", Map.of(
                "currentPassword", "wrong", "newPassword", "UpdatedPassword123!"), access).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, postWithToken("/api/auth/change-password", Map.of(
                "currentPassword", "StaffPassword123!", "newPassword", "StaffPassword123!"), access).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, postWithToken("/api/auth/change-password", Map.of(
                "currentPassword", "StaffPassword123!", "newPassword", "short"), access).getStatusCode());
        assertEquals(HttpStatus.CONFLICT, postWithToken("/api/auth/change-password", Map.of(
                "currentPassword", "StaffPassword123!", "newPassword", "ậ".repeat(25)), access).getStatusCode());
        assertEquals(HttpStatus.OK, getWithToken("/api/auth/me", access).getStatusCode());
        assertEquals(HttpStatus.OK, login("staff@test.local", "StaffPassword123!").getStatusCode());
    }

    @Test
    void logoutAllRevokesStaffSessionsButAllowsFreshLogin() {
        String access = token(login("staff@test.local", "StaffPassword123!"));
        assertEquals(HttpStatus.NO_CONTENT, postWithToken("/api/auth/logout-all", null, access).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", access).getStatusCode());
        assertEquals(HttpStatus.OK, login("staff@test.local", "StaffPassword123!").getStatusCode());
    }

    @Test
    void disabledAccountCannotUseExistingJwtOrExchangeGoogleCode() {
        String access = token(login("staff@test.local", "StaffPassword123!"));
        String code = oauthCodes.issue(staff);
        jdbc.update("UPDATE app_users SET enabled=false WHERE id=?", staff.getId());
        assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", access).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity("/api/auth/oauth/exchange",
                Map.of("code", code), JsonNode.class).getStatusCode());
    }

    @Test
    void changedRoleInvalidatesPreviouslyIssuedJwt() {
        String access = token(login("admin@test.local", "AdminPassword123!"));
        jdbc.update("UPDATE app_users SET role='STAFF' WHERE id=?", admin.getId());
        assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", access).getStatusCode());
    }

    @Test
    void legacyAndMalformedSessionVersionsAreRejectedEvenWithValidSignature() {
        for (Object version : List.of("missing", "0", 0.5)) {
            var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                    .issuer("refurbished-backend").subject(admin.getId().toString())
                    .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(60))
                    .claim("role", "ADMIN").claim("email", admin.getEmail());
            if (!version.equals("missing")) claims.claim("ver", version);
            var header = org.springframework.security.oauth2.jwt.JwsHeader
                    .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
            String access = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters
                    .from(header, claims.build())).getTokenValue();
            assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", access).getStatusCode());
        }
    }

    @Test
    void localRecoveryChangesOnlyExistingEnabledAdminAndRevokesSessions() {
        // Không kích hoạt dev/database dev trong test. Gọi runner trong transaction
        // test để kiểm chứng nghiệp vụ bằng fixture, không đặt lại ADMIN thật.
        assertTrue(context.getBeansOfType(LocalAdminRecovery.class).isEmpty());
        String access = token(login("admin@test.local", "AdminPassword123!"));
        String code = oauthCodes.issue(admin);
        recover(" ADMIN@TEST.LOCAL ", "RecoveredPassword123!");
        AppUser recovered = users.findById(admin.getId()).orElseThrow();
        assertEquals(admin.getId(), recovered.getId());
        assertEquals(UserRole.ADMIN, recovered.getRole());
        assertTrue(recovered.isEnabled());
        assertEquals(1, recovered.getTokenVersion());
        assertEquals(HttpStatus.OK, login("admin@test.local", "RecoveredPassword123!").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, getWithToken("/api/auth/me", access).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity("/api/auth/oauth/exchange",
                Map.of("code", code), JsonNode.class).getStatusCode());
    }

    @Test
    void recoveryRejectsStaffDisabledAndMissingUsersWithoutMutation() {
        long count = users.count();
        assertThrows(IllegalStateException.class, () -> recover("missing@test.local", "RecoveredPassword123!"));
        assertThrows(IllegalStateException.class, () -> recover("staff@test.local", "RecoveredPassword123!"));
        jdbc.update("UPDATE app_users SET enabled=false WHERE id=?", admin.getId());
        assertThrows(IllegalStateException.class, () -> recover("admin@test.local", "RecoveredPassword123!"));
        assertEquals(count, users.count());
        AppUser unchanged = users.findById(admin.getId()).orElseThrow();
        assertEquals(0, unchanged.getTokenVersion());
        assertTrue(passwords.matches("AdminPassword123!", unchanged.getPasswordHash()));
        assertEquals(HttpStatus.OK, login("staff@test.local", "StaffPassword123!").getStatusCode());
    }

    private void recover(String email, String password) {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                new LocalAdminRecovery(users, passwords, audit, email, password).run(new DefaultApplicationArguments()));
    }

    @Test
    void concurrentPasswordChangesAllowExactlyOneWinner() throws Exception {
        String access = token(login("staff@test.local", "StaffPassword123!"));
        var executor = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("SELECT id FROM app_users WHERE id=? FOR UPDATE")) {
                statement.setObject(1, staff.getId());
                assertTrue(statement.executeQuery().next());
            }
            var first = executor.submit(() -> postWithToken("/api/auth/change-password", Map.of(
                    "currentPassword", "StaffPassword123!", "newPassword", "FirstNewPassword123!"), access));
            var second = executor.submit(() -> postWithToken("/api/auth/change-password", Map.of(
                    "currentPassword", "StaffPassword123!", "newPassword", "SecondNewPassword123!"), access));
            // Bằng chứng cạnh tranh thực: cả hai request phải chờ row lock trên PostgreSQL.
            boolean waiting = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                int count = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                        + "WHERE datname=current_database() AND usename=current_user "
                        + "AND state='active' AND wait_event_type='Lock'", Integer.class);
                if (count >= 2) { waiting = true; break; }
                Thread.sleep(50);
            }
            assertTrue(waiting, "Both password changes must reach the database row lock.");
            blocker.commit();
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(r -> r.getStatusCode() == HttpStatus.NO_CONTENT).count());
            assertEquals(1, results.stream().filter(r -> r.getStatusCode() == HttpStatus.UNAUTHORIZED).count());
            assertEquals(1, users.findById(staff.getId()).orElseThrow().getTokenVersion());
            long successfulLogins = List.of(login("staff@test.local", "FirstNewPassword123!"),
                    login("staff@test.local", "SecondNewPassword123!")).stream()
                    .filter(r -> r.getStatusCode() == HttpStatus.OK).count();
            assertEquals(1, successfulLogins);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private String token(ResponseEntity<JsonNode> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> postWithToken(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
}
