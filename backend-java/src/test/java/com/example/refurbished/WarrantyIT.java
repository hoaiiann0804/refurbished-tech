package com.example.refurbished;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class WarrantyIT {

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
        for (UUID productId : createdProducts) {
            jdbc.update("DELETE FROM warranties WHERE device_unit_id IN "
                    + "(SELECT id FROM device_units WHERE product_id = ?)", productId);
        }
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
    void migrationV4CreatesWarrantySchema() {
        assertTrue(Integer.parseInt(flyway.info().current().getVersion().getVersion()) >= 4);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version='4' AND success", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema='public' AND table_name='warranties'", Integer.class));
    }

    @Test
    void soldDeviceCanReceiveOneWarrantyAndBeRetrieved() {
        UUID deviceId = createSoldDevice();

        JsonNode created = post("/api/warranties", Map.of(
                "deviceUnitId", deviceId, "durationMonths", 12), HttpStatus.CREATED);

        UUID warrantyId = UUID.fromString(created.get("id").asText());
        assertEquals(deviceId.toString(), created.get("deviceUnitId").asText());
        assertEquals(12, created.get("durationMonths").asInt());
        LocalDate startsOn = LocalDate.parse(created.get("startsOn").asText());
        assertEquals(startsOn.plusMonths(12), LocalDate.parse(created.get("endsOn").asText()));
        assertNotNull(created.get("createdAt"));

        assertEquals(warrantyId.toString(), get("/api/warranties/" + warrantyId, HttpStatus.OK)
                .get("id").asText());
        assertEquals(warrantyId.toString(), get("/api/warranties/device-unit/" + deviceId, HttpStatus.OK)
                .get("id").asText());
    }

    @Test
    void unsoldDeviceCannotReceiveWarranty() {
        UUID deviceId = createAvailableDevice();

        JsonNode error = post("/api/warranties", Map.of(
                "deviceUnitId", deviceId, "durationMonths", 12), HttpStatus.CONFLICT);

        assertEquals("BUSINESS_CONFLICT", error.get("code").asText());
        assertEquals(0, countWarranties(deviceId));
    }

    @Test
    void secondWarrantyForSameSoldDeviceIsRejected() {
        UUID deviceId = createSoldDevice();
        post("/api/warranties", Map.of("deviceUnitId", deviceId, "durationMonths", 12), HttpStatus.CREATED);

        JsonNode error = post("/api/warranties", Map.of(
                "deviceUnitId", deviceId, "durationMonths", 24), HttpStatus.CONFLICT);

        assertEquals("BUSINESS_CONFLICT", error.get("code").asText());
        assertEquals(1, countWarranties(deviceId));
    }

    @Test
    void validationAndMissingResourcesReturnExpectedErrors() {
        for (int invalidDuration : List.of(0, 37)) {
            ResponseEntity<JsonNode> response = http.postForEntity("/api/warranties", Map.of(
                    "deviceUnitId", UUID.randomUUID(), "durationMonths", invalidDuration), JsonNode.class);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("VALIDATION_ERROR", response.getBody().get("code").asText());
        }

        JsonNode missingDevice = post("/api/warranties", Map.of(
                "deviceUnitId", UUID.randomUUID(), "durationMonths", 12), HttpStatus.NOT_FOUND);
        assertEquals("NOT_FOUND", missingDevice.get("code").asText());
        assertEquals("NOT_FOUND", get("/api/warranties/" + UUID.randomUUID(), HttpStatus.NOT_FOUND)
                .get("code").asText());
        assertEquals("NOT_FOUND", get("/api/warranties/device-unit/" + UUID.randomUUID(), HttpStatus.NOT_FOUND)
                .get("code").asText());
    }

    private UUID createSoldDevice() {
        UUID deviceId = createAvailableDevice();
        JsonNode order = post("/api/orders/checkout", Map.of(
                "customerName", "Warranty customer", "deviceUnitIds", List.of(deviceId)), HttpStatus.CREATED);
        createdOrders.add(UUID.fromString(order.get("id").asText()));
        return deviceId;
    }

    private UUID createAvailableDevice() {
        String suffix = UUID.randomUUID().toString();
        JsonNode product = post("/api/products", Map.of(
                "modelCode", "WARRANTY-" + suffix, "name", "Warranty fixture", "brand", "Test"),
                HttpStatus.CREATED);
        UUID productId = UUID.fromString(product.get("id").asText());
        createdProducts.add(productId);
        JsonNode device = post("/api/device-units", Map.of(
                "productId", productId, "serialNumber", "WARRANTY-" + suffix), HttpStatus.CREATED);
        UUID deviceId = UUID.fromString(device.get("id").asText());
        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);
        post("/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                "passed", true, "grade", "A", "batteryHealth", 90,
                "salePrice", new BigDecimal("1000.00"),
                "inspectionNotes", "Passed for warranty test."), HttpStatus.OK);
        return deviceId;
    }

    private int countWarranties(UUID deviceId) {
        return jdbc.queryForObject("SELECT count(*) FROM warranties WHERE device_unit_id = ?",
                Integer.class, deviceId);
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
}
