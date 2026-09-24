package com.example.refurbished;

import com.example.refurbished.security.AppUser;
import com.example.refurbished.security.AppUserRepository;
import com.example.refurbished.security.OAuthCodeService;
import com.example.refurbished.security.UserRole;
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
        jdbc.update("DELETE FROM oauth_login_codes WHERE user_id IN (?, ?)", admin.getId(), staff.getId());
        users.deleteAllById(java.util.List.of(admin.getId(), staff.getId()));
        users.flush();
    }

    @Test
    void migrationV5AndAuthenticationAreActive() {
        assertEquals("5", flyway.info().current().getVersion().getVersion());

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
