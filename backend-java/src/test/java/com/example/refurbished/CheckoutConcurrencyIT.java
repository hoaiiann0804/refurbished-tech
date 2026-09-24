package com.example.refurbished;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutConcurrencyIT {

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private final List<UUID> createdProducts = new ArrayList<>();
    private final List<UUID> createdOrders = new ArrayList<>();

    @BeforeEach
    void verifyTestDatabase() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        for (UUID orderId : createdOrders) {
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM sales_orders WHERE id = ?", orderId);
        }
        for (UUID productId : createdProducts) {
            jdbc.update("DELETE FROM device_units WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
    }

    @Test
    void exactlyOneOfTwoConcurrentCheckoutsCanBuyTheDevice() throws Exception {
        UUID deviceId = createAvailableDevice();
        Map<String, Object> customerA = Map.of(
                "customerName", "Customer A", "deviceUnitIds", List.of(deviceId));
        Map<String, Object> customerB = Map.of(
                "customerName", "Customer B", "deviceUnitIds", List.of(deviceId));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockDeviceFromTestConnection(blocker, deviceId);

            Future<ResponseEntity<JsonNode>> attemptA = executor.submit(
                    () -> checkoutWhenReleased(customerA, workersReady, startTogether));
            Future<ResponseEntity<JsonNode>> attemptB = executor.submit(
                    () -> checkoutWhenReleased(customerB, workersReady, startTogether));

            assertTrue(workersReady.await(5, TimeUnit.SECONDS), "Both checkout workers must be ready.");
            startTogether.countDown();

            assertTrue(waitUntilBothRequestsAreBlocked(),
                    "Both HTTP checkouts must reach PostgreSQL and wait for the same device row lock.");
            blocker.commit();

            ResponseEntity<JsonNode> responseA = attemptA.get(10, TimeUnit.SECONDS);
            ResponseEntity<JsonNode> responseB = attemptB.get(10, TimeUnit.SECONDS);
            assertExactlyOneSuccess(responseA, responseB);
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals("SOLD", jdbc.queryForObject(
                "SELECT status FROM device_units WHERE id = ?", String.class, deviceId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM order_items WHERE device_unit_id = ?", Integer.class, deviceId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM sales_orders o JOIN order_items i ON i.order_id=o.id "
                        + "WHERE i.device_unit_id = ? AND o.status='COMPLETED'", Integer.class, deviceId));
    }

    private ResponseEntity<JsonNode> checkoutWhenReleased(Map<String, Object> body,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return http.postForEntity("/api/orders/checkout", body, JsonNode.class);
    }

    private void lockDeviceFromTestConnection(Connection connection, UUID deviceId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM device_units WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, deviceId);
            assertTrue(statement.executeQuery().next());
        }
    }

    private boolean waitUntilBothRequestsAreBlocked() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Integer waiting = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity "
                            + "WHERE datname=current_database() AND usename=current_user "
                            + "AND state='active' AND wait_event_type='Lock'",
                    Integer.class);
            if (waiting != null && waiting >= 2) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private void assertExactlyOneSuccess(ResponseEntity<JsonNode> first, ResponseEntity<JsonNode> second) {
        List<ResponseEntity<JsonNode>> responses = List.of(first, second);
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count());
        assertEquals(1, responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count());

        ResponseEntity<JsonNode> success = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CREATED)
                .findFirst().orElseThrow();
        ResponseEntity<JsonNode> conflict = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .findFirst().orElseThrow();
        assertNotNull(success.getBody());
        assertNotNull(conflict.getBody());
        assertEquals("COMPLETED", success.getBody().get("status").asText());
        assertEquals("BUSINESS_CONFLICT", conflict.getBody().get("code").asText());
        createdOrders.add(UUID.fromString(success.getBody().get("id").asText()));
    }

    private UUID createAvailableDevice() {
        String suffix = UUID.randomUUID().toString();
        JsonNode product = post("/api/products", Map.of(
                "modelCode", "RACE-" + suffix, "name", "Concurrency fixture", "brand", "Test"));
        UUID productId = UUID.fromString(product.get("id").asText());
        createdProducts.add(productId);
        JsonNode device = post("/api/device-units", Map.of(
                "productId", productId, "serialNumber", "RACE-" + suffix));
        UUID deviceId = UUID.fromString(device.get("id").asText());

        ResponseEntity<JsonNode> start = http.postForEntity(
                "/api/device-units/" + deviceId + "/start-inspection", null, JsonNode.class);
        assertEquals(HttpStatus.OK, start.getStatusCode());
        ResponseEntity<JsonNode> complete = http.postForEntity(
                "/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                        "passed", true, "grade", "A", "batteryHealth", 90,
                        "salePrice", new BigDecimal("1000.00"),
                        "inspectionNotes", "Passed for concurrency test."), JsonNode.class);
        assertEquals(HttpStatus.OK, complete.getStatusCode());
        return deviceId;
    }

    private JsonNode post(String path, Object body) {
        ResponseEntity<JsonNode> response = http.postForEntity(path, body, JsonNode.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
