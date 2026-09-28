package com.example.refurbished;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.security.AppUser;
import com.example.refurbished.security.AppUserRepository;
import com.example.refurbished.security.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.permit-all-for-tests=false")
class OperationsIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired AuditService audit;
    @Autowired PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;
    @LocalServerPort int port;
    UUID adminId, staffId, productId;
    String adminToken, staffToken, suffix;

    @BeforeEach void setup() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        suffix = UUID.randomUUID().toString();
        adminId = createUser("admin", UserRole.ADMIN);
        staffId = createUser("staff", UserRole.STAFF);
        adminToken = login("admin");
        staffToken = login("staff");
        productId = UUID.fromString(call(HttpMethod.POST, "/api/products", Map.of(
                "modelCode", suffix, "name", "Operations fixture", "brand", "Test"), adminToken, null, 201).get("id").asText());
    }

    @AfterEach void cleanup() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        // Xóa đúng fixture của lượt test, không xóa đơn hoặc lịch sử do người khác tạo.
        jdbc.update("DELETE FROM audit_events WHERE actor_user_id IN (?,?) OR target_id=?", adminId, staffId, productId);
        jdbc.update("DELETE FROM checkout_requests WHERE actor_id IN (?,?)", adminId, staffId);
        var orders = jdbc.queryForList("SELECT DISTINCT oi.order_id FROM order_items oi JOIN device_units d "
                + "ON d.id=oi.device_unit_id WHERE d.product_id=?", UUID.class, productId);
        for (UUID id : orders) {
            jdbc.update("DELETE FROM order_items WHERE order_id=?", id);
            jdbc.update("DELETE FROM sales_orders WHERE id=?", id);
        }
        jdbc.update("DELETE FROM warranties WHERE device_unit_id IN (SELECT id FROM device_units WHERE product_id=?)", productId);
        jdbc.update("DELETE FROM device_units WHERE product_id=?", productId);
        jdbc.update("DELETE FROM products WHERE id=?", productId);
        users.deleteAllById(List.of(adminId, staffId));
    }

    @Test void serialOrderFiltersAndAuditRespectBusinessAndPermissions() {
        UUID device = device(true);
        JsonNode found = call(HttpMethod.GET, "/api/device-units?serialNumber=%20ops-" + suffix + "%20", null, staffToken, null, 200);
        assertEquals(device.toString(), found.get("items").get(0).get("id").asText());
        assertEquals(0, call(HttpMethod.GET, "/api/device-units?serialNumber=missing-" + suffix, null, staffToken, null, 200).get("totalElements").asInt());
        assertEquals(0, call(HttpMethod.GET, "/api/device-units?serialNumber=OPS-" + suffix + "&status=RECEIVED", null, staffToken, null, 200).get("totalElements").asInt());
        JsonNode order = checkout(device, "Buyer %_ " + suffix, "sale", staffToken, 201);
        String date = order.get("createdAt").asText();
        JsonNode list = call(HttpMethod.GET, "/api/orders?customerName=buyer%20%25_%20" + suffix + "&from=" + date, null, staffToken, null, 200);
        assertEquals(1, list.get("totalElements").asInt());
        assertFalse(list.get("items").get(0).has("items"));
        assertEquals(0, call(HttpMethod.GET, "/api/orders?customerName=" + suffix + "&page=1&size=1", null, staffToken, null, 200).get("items").size());
        assertEquals(0, call(HttpMethod.GET, "/api/orders?customerName=" + suffix + "&to=" + date, null, staffToken, null, 200).get("totalElements").asInt());
        call(HttpMethod.GET, "/api/orders?from=" + date + "&to=" + date, null, staffToken, null, 400);
        call(HttpMethod.GET, "/api/orders?size=101", null, staffToken, null, 400);
        call(HttpMethod.GET, "/api/orders?page=-1", null, staffToken, null, 400);
        call(HttpMethod.GET, "/api/orders", null, null, null, 401);
        call(HttpMethod.GET, "/api/audit-events", null, staffToken, null, 403);
        JsonNode events = call(HttpMethod.GET, "/api/audit-events?targetId=" + order.get("id").asText(), null, adminToken, null, 200);
        assertEquals(1, events.get("totalElements").asInt());
        JsonNode event = events.get("items").get(0);
        assertEquals(staffId.toString(), event.get("actorUserId").asText());
        assertEquals("ORDER_CHECKED_OUT", event.get("action").asText());
        assertFalse(events.toString().contains(staffToken));
        assertFalse(events.toString().contains("password"));
        call(HttpMethod.GET, "/api/audit-events?from=" + date + "&to=" + date, null, adminToken, null, 400);
        call(HttpMethod.GET, "/api/audit-events?size=101", null, adminToken, null, 400);
    }

    @Test void operationalEndpointsAreProtectedAndProbesAreMinimal() {
        call(HttpMethod.GET,"/actuator/metrics",null,null,null,401);
        call(HttpMethod.GET,"/actuator/prometheus",null,staffToken,null,403);
        assertTrue(call(HttpMethod.GET,"/actuator/metrics",null,adminToken,null,200).has("names"));
        JsonNode readiness=call(HttpMethod.GET,"/actuator/health/readiness",null,null,null,200);
        assertEquals("UP",readiness.get("status").asText());assertFalse(readiness.has("components"));
        call(HttpMethod.GET,"/actuator/health/liveness",null,null,null,200);
    }

    @Test void replayReturnsOriginalOrderAndRejectsChangedPayloadAndDifferentActor() {
        UUID device = device(true);
        JsonNode first = checkout(device, "Buyer", "retry", staffToken, 201);
        JsonNode replay = checkout(device, " Buyer ", "retry", staffToken, 201);
        assertEquals(first, replay);
        checkout(device, "Different buyer", "retry", staffToken, 409);
        // Khóa thuộc người thao tác: ADMIN không được replay đơn của STAFF bằng cùng key.
        checkout(device, "Buyer", "retry", adminToken, 409);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE target_id=?", Integer.class, UUID.fromString(first.get("id").asText())));
    }

    @Test void failedCheckoutReleasesKeyAndAuditRollsBackWithTransaction() {
        UUID device = device(false);
        checkout(device, "Buyer", "retry-failure", staffToken, 409);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM checkout_requests WHERE actor_id=?", Integer.class, staffId));
        inspect(device);
        checkout(device, "Buyer", "retry-failure", staffToken, 201);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            audit.record("ROLLBACK_PROBE", "PRODUCT", productId, Map.of());
            throw new IllegalStateException("Simulated business rollback");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE target_id=? AND action='ROLLBACK_PROBE'", Integer.class, productId));
    }

    @Test void concurrentRetriesCreateOneOrder() throws Exception {
        UUID device = device(true);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<JsonNode> action = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return checkout(device, "Concurrent buyer", "concurrent", staffToken, 201);
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            // Giữ khóa máy để chứng minh hai HTTP request thật sự chồng lấn trong DB,
            // thay vì test có thể vô tình chạy tuần tự và vẫn xanh.
            try (var blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try (var statement = blocker.prepareStatement("SELECT id FROM device_units WHERE id=? FOR UPDATE")) {
                    statement.setObject(1, device);
                    assertTrue(statement.executeQuery().next());
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                boolean overlapped = false;
                for (int attempt = 0; attempt < 100; attempt++) {
                    int waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() "
                            + "AND usename=current_user AND state='active' AND wait_event_type='Lock'", Integer.class);
                    if (waiting >= 2) { overlapped = true; break; }
                    Thread.sleep(50);
                }
                assertTrue(overlapped, "Both requests must wait on device/idempotency locks");
                blocker.commit();
            } finally { start.countDown(); }
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM checkout_requests WHERE actor_id=?", Integer.class, staffId));
    }

    @Test void multipleDevicesReplayIgnoresInputOrderAndRejectsInvalidKeys() {
        UUID first = device(true);
        suffix = UUID.randomUUID().toString();
        UUID second = device(true);
        Map<String, Object> body = Map.of("customerName", "Multiple", "deviceUnitIds", List.of(first, second));
        call(HttpMethod.POST, "/api/orders/checkout", body, staffToken, "invalid key", 400);
        JsonNode order = call(HttpMethod.POST, "/api/orders/checkout", body, staffToken, "multi", 201);
        JsonNode replay = call(HttpMethod.POST, "/api/orders/checkout", Map.of("customerName", "Multiple",
                "deviceUnitIds", List.of(second, first)), staffToken, "multi", 201);
        assertEquals(order, replay);
    }

    UUID createUser(String prefix, UserRole role) {
        return users.saveAndFlush(new AppUser(prefix + suffix + "@test.local", prefix,
                passwords.encode("OperationsPassword123!"), role)).getId();
    }
    String login(String prefix) {
        return call(HttpMethod.POST, "/api/auth/login", Map.of("email", prefix + suffix + "@test.local",
                "password", "OperationsPassword123!"), null, null, 200).get("accessToken").asText();
    }
    UUID device(boolean available) {
        UUID id = UUID.fromString(call(HttpMethod.POST, "/api/device-units", Map.of("productId", productId,
                "serialNumber", "OPS-" + suffix), staffToken, null, 201).get("id").asText());
        if (available) inspect(id);
        return id;
    }
    void inspect(UUID id) {
        call(HttpMethod.POST, "/api/device-units/" + id + "/start-inspection", null, staffToken, null, 200);
        call(HttpMethod.POST, "/api/device-units/" + id + "/complete-inspection", Map.of("passed", true,
                "grade", "A", "batteryHealth", 90, "salePrice", 1500,
                "inspectionNotes", "Passed operations fixture inspection"), staffToken, null, 200);
    }
    JsonNode checkout(UUID device, String customer, String key, String token, int status) {
        return call(HttpMethod.POST, "/api/orders/checkout", Map.of("customerName", customer,
                "deviceUnitIds", List.of(device)), token, key, status);
    }
    JsonNode call(HttpMethod method, String path, Object body, String token, String key, int status) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (key != null) headers.set("Idempotency-Key", key);
        ResponseEntity<JsonNode> response = http.exchange(URI.create("http://localhost:" + port + path), method,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertEquals(status, response.getStatusCode().value(), () -> path + " -> " + response.getBody());
        return response.getBody();
    }
}
