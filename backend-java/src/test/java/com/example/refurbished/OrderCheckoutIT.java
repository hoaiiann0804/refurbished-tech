package com.example.refurbished;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
class OrderCheckoutIT {

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

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
            jdbc.update("DELETE FROM audit_events WHERE target_id=?", orderId);
            jdbc.update("DELETE FROM sales_orders WHERE id = ?", orderId);
        }
        for (UUID productId : createdProducts) {
            jdbc.update("DELETE FROM audit_events WHERE target_id IN (SELECT id FROM device_units WHERE product_id=?)", productId);
            jdbc.update("DELETE FROM audit_events WHERE target_id=?", productId);
            jdbc.update("DELETE FROM device_units WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
    }

    @Test
    void migrationV3CreatesOrderSchema() {
        assertTrue(Integer.parseInt(flyway.info().current().getVersion().getVersion()) >= 3);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version='3' AND success", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name IN ('sales_orders','order_items')", Integer.class));
    }

    @Test
    void checkoutCreatesCompletedOrderAndSellsActualDevice() {
        UUID deviceId = createAvailableDevice(new BigDecimal("12500000.25"));

        JsonNode order = checkout("Nguyen Van A", List.of(deviceId), HttpStatus.CREATED);

        assertEquals("Nguyen Van A", order.get("customerName").asText());
        assertEquals("COMPLETED", order.get("status").asText());
        assertEquals(0, new BigDecimal(order.get("totalAmount").asText())
                .compareTo(new BigDecimal("12500000.25")));
        assertEquals(deviceId.toString(), order.get("items").get(0).get("deviceUnitId").asText());
        assertEquals("SOLD", getDevice(deviceId).get("status").asText());

        UUID orderId = rememberOrder(order);
        JsonNode retrieved = get("/api/orders/" + orderId, HttpStatus.OK);
        assertEquals(deviceId.toString(), retrieved.get("items").get(0).get("deviceUnitId").asText());
    }

    @Test
    void checkoutMultipleDevicesCalculatesTotalFromPriceSnapshots() {
        UUID first = createAvailableDevice(new BigDecimal("1000.10"));
        UUID second = createAvailableDevice(new BigDecimal("2000.20"));

        JsonNode order = checkout("Multiple devices", List.of(first, second), HttpStatus.CREATED);
        rememberOrder(order);

        assertEquals(2, order.get("items").size());
        assertEquals(0, new BigDecimal(order.get("totalAmount").asText())
                .compareTo(new BigDecimal("3000.30")));
        assertEquals("SOLD", getDevice(first).get("status").asText());
        assertEquals("SOLD", getDevice(second).get("status").asText());
    }

    @Test
    void unavailableDeviceRollsBackEntireCheckout() {
        UUID available = createAvailableDevice(new BigDecimal("1000.00"));
        UUID unavailable = createReceivedDevice();
        int ordersBefore = countOrders();

        JsonNode error = checkout("Rollback", List.of(available, unavailable), HttpStatus.CONFLICT);

        assertEquals("BUSINESS_CONFLICT", error.get("code").asText());
        assertEquals("AVAILABLE", getDevice(available).get("status").asText());
        assertEquals("RECEIVED", getDevice(unavailable).get("status").asText());
        assertEquals(ordersBefore, countOrders());
    }

    @Test
    void duplicateDeviceInRequestIsRejectedBeforeCreatingOrder() {
        UUID deviceId = createAvailableDevice(new BigDecimal("1000.00"));

        JsonNode error = checkout("Duplicate", List.of(deviceId, deviceId), HttpStatus.CONFLICT);

        assertEquals("BUSINESS_CONFLICT", error.get("code").asText());
        assertEquals("AVAILABLE", getDevice(deviceId).get("status").asText());
    }

    @Test
    void missingDeviceRollsBackWithoutOrder() {
        UUID available = createAvailableDevice(new BigDecimal("1000.00"));
        int ordersBefore = countOrders();

        JsonNode error = checkout("Missing", List.of(available, UUID.randomUUID()), HttpStatus.NOT_FOUND);

        assertEquals("NOT_FOUND", error.get("code").asText());
        assertEquals("AVAILABLE", getDevice(available).get("status").asText());
        assertEquals(ordersBefore, countOrders());
    }

    @Test
    void requestValidationAndMissingOrderReturnExpectedErrors() {
        ResponseEntity<JsonNode> invalid = http.postForEntity("/api/orders/checkout",
                Map.of("customerName", "Customer", "deviceUnitIds", List.of()), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("VALIDATION_ERROR", invalid.getBody().get("code").asText());

        ResponseEntity<JsonNode> blank = http.postForEntity("/api/orders/checkout",
                Map.of("customerName", "   ", "deviceUnitIds", List.of(UUID.randomUUID())), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, blank.getStatusCode());
        assertEquals("VALIDATION_ERROR", blank.getBody().get("code").asText());

        JsonNode missing = get("/api/orders/" + UUID.randomUUID(), HttpStatus.NOT_FOUND);
        assertEquals("NOT_FOUND", missing.get("code").asText());
    }

    private UUID createAvailableDevice(BigDecimal price) {
        UUID deviceId = createReceivedDevice();
        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);
        post("/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                "passed", true, "grade", "A", "batteryHealth", 90,
                "salePrice", price, "inspectionNotes", "Passed for checkout test."), HttpStatus.OK);
        return deviceId;
    }

    private UUID createReceivedDevice() {
        String suffix = UUID.randomUUID().toString();
        JsonNode product = post("/api/products", Map.of(
                "modelCode", "ORDER-" + suffix, "name", "Order fixture", "brand", "Test"), HttpStatus.CREATED);
        UUID productId = UUID.fromString(product.get("id").asText());
        createdProducts.add(productId);
        JsonNode device = post("/api/device-units", Map.of(
                "productId", productId, "serialNumber", "ORDER-" + suffix), HttpStatus.CREATED);
        return UUID.fromString(device.get("id").asText());
    }

    private JsonNode checkout(String customerName, List<UUID> deviceIds, HttpStatus expected) {
        return post("/api/orders/checkout", Map.of(
                "customerName", customerName, "deviceUnitIds", deviceIds), expected);
    }

    private UUID rememberOrder(JsonNode order) {
        UUID id = UUID.fromString(order.get("id").asText());
        createdOrders.add(id);
        return id;
    }

    private JsonNode post(String path, Object body, HttpStatus expected) {
        ResponseEntity<JsonNode> response = http.postForEntity(path, body, JsonNode.class);
        assertEquals(expected, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private JsonNode get(String path, HttpStatus expected) {
        ResponseEntity<JsonNode> response = http.getForEntity(path, JsonNode.class);
        assertEquals(expected, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private JsonNode getDevice(UUID id) {
        return get("/api/device-units/" + id, HttpStatus.OK);
    }

    private int countOrders() {
        return jdbc.queryForObject("SELECT count(*) FROM sales_orders", Integer.class);
    }
}
