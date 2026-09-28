package com.example.refurbished;

import com.example.refurbished.inventory.dto.DeviceUnitResponse;
import com.example.refurbished.product.dto.ProductResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductInventoryIT {

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private DataSource dataSource;

    private final List<UUID> createdProducts = new ArrayList<>();

    @BeforeEach
    void verifyDatabaseBeforeWritingFixtures() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
    }

    @AfterEach
    void removeOnlyFixturesCreatedByThisTest() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        for (UUID id : createdProducts) {
            jdbc.update("DELETE FROM audit_events WHERE target_id IN (SELECT id FROM device_units WHERE product_id=?)", id);
            jdbc.update("DELETE FROM audit_events WHERE target_id=?", id);
            jdbc.update("DELETE FROM device_units WHERE product_id = ?", id);
            jdbc.update("DELETE FROM products WHERE id = ?", id);
        }
    }

    @Test
    void migrationUsesGuardedDataSourceAndVersionedSchema() {
        assertSame(dataSource, flyway.getConfiguration().getDataSource());
        assertTrue(flyway.getConfiguration().isCleanDisabled());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name IN ('products', 'device_units')", Integer.class));
    }

    @Test
    void createsNormalizedProductAndIndependentReceivedDevices() {
        String code = " mba-" + UUID.randomUUID() + " ";
        ProductResponse product = createProduct(code, true);
        assertEquals(code.trim().toUpperCase(java.util.Locale.ROOT), product.modelCode());
        assertEquals("MacBook Air M1", product.name());
        assertNotNull(product.createdAt());

        ResponseEntity<DeviceUnitResponse> first = http.postForEntity("/api/device-units", Map.of(
                "productId", product.id(), "serialNumber", " mba001-" + UUID.randomUUID() + " ",
                "grade", "A", "batteryHealth", 91, "salePrice", new BigDecimal("12500000.25")),
                DeviceUnitResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        DeviceUnitResponse device = first.getBody();
        assertNotNull(device);
        assertEquals("RECEIVED", device.status().name());
        assertEquals("A", device.grade().name());
        assertEquals(91, device.batteryHealth());
        assertEquals(0, device.salePrice().compareTo(new BigDecimal("12500000.25")));
        assertNull(device.inspectionPassed());
        assertNull(device.inspectedAt());
        assertEquals(device.serialNumber().toUpperCase(java.util.Locale.ROOT), device.serialNumber());
        assertEquals("/api/device-units/" + device.id(), first.getHeaders().getLocation().toString());

        DeviceUnitResponse second = receive(product.id(), "MBA002-" + UUID.randomUUID());
        assertNotEquals(device.id(), second.id());
        assertNull(second.grade());
        assertNull(second.batteryHealth());
        assertNull(second.salePrice());
        assertEquals(2, countDevices(product.id()));

        ResponseEntity<DeviceUnitResponse> detail = http.getForEntity("/api/device-units/" + device.id(), DeviceUnitResponse.class);
        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertEquals(product.id(), detail.getBody().productId());
        JsonNode productJson = http.getForObject("/api/products/" + product.id(), JsonNode.class);
        assertFalse(productJson.has("quantity"));
        assertFalse(productJson.has("stockQuantity"));
    }

    @Test
    void rejectsDuplicateNormalizedModelCode() {
        ProductResponse product = createProduct("MODEL-" + UUID.randomUUID(), true);
        ResponseEntity<JsonNode> duplicate = http.postForEntity("/api/products", Map.of(
                "modelCode", " " + product.modelCode().toLowerCase(java.util.Locale.ROOT) + " ",
                "name", "Another model", "brand", "Apple"), JsonNode.class);
        assertError(duplicate, HttpStatus.CONFLICT, "DATA_CONFLICT");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM products WHERE model_code = ?",
                Integer.class, product.modelCode()));
        assertFalse(duplicate.getBody().toString().contains("uq_products"));
    }

    @Test
    void duplicateSerialAcrossDifferentProductsRollsBack() {
        ProductResponse first = createProduct("MODEL-" + UUID.randomUUID(), true);
        ProductResponse second = createProduct("MODEL-" + UUID.randomUUID(), true);
        DeviceUnitResponse device = receive(first.id(), "SERIAL-" + UUID.randomUUID());
        ResponseEntity<JsonNode> duplicate = http.postForEntity("/api/device-units", Map.of(
                "productId", second.id(), "serialNumber", " " + device.serialNumber().toLowerCase(java.util.Locale.ROOT) + " "), JsonNode.class);
        assertError(duplicate, HttpStatus.CONFLICT, "DATA_CONFLICT");
        assertEquals(1, countDevices(first.id()));
        assertEquals(0, countDevices(second.id()));
        // The failed transaction did not poison the next request/connection.
        receive(second.id(), "OTHER-" + UUID.randomUUID());
        assertEquals(1, countDevices(second.id()));
    }

    @Test
    void updatingProductCanDisableFurtherIntakeWithoutChangingExistingDevice() {
        ProductResponse product = createProduct("MODEL-" + UUID.randomUUID(), true);
        DeviceUnitResponse existing = receive(product.id(), "SERIAL-" + UUID.randomUUID());
        ResponseEntity<ProductResponse> update = http.exchange("/api/products/" + product.id(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Updated model", "brand", "Apple", "active", false)), ProductResponse.class);
        assertEquals(HttpStatus.OK, update.getStatusCode());
        assertFalse(update.getBody().active());
        assertEquals(product.modelCode(), update.getBody().modelCode());
        assertEquals("Updated model", update.getBody().name());
        assertError(http.postForEntity("/api/device-units", Map.of("productId", product.id(),
                "serialNumber", "NEW-" + UUID.randomUUID()), JsonNode.class), HttpStatus.CONFLICT, "BUSINESS_CONFLICT");
        assertEquals(1, countDevices(product.id()));
        assertEquals("RECEIVED", http.getForObject("/api/device-units/" + existing.id(), JsonNode.class).get("status").asText());
    }

    @Test
    void filtersAndPaginatesWithoutSerializingJpaRelationships() {
        ProductResponse product = createProduct("MODEL-" + UUID.randomUUID(), true);
        receive(product.id(), "SERIAL-" + UUID.randomUUID());
        receive(product.id(), "SERIAL-" + UUID.randomUUID());
        String filtered = "/api/device-units?productId=" + product.id() + "&status=RECEIVED&size=1";
        JsonNode first = http.getForObject(filtered + "&page=0", JsonNode.class);
        JsonNode second = http.getForObject(filtered + "&page=1", JsonNode.class);
        assertEquals(2, first.get("totalElements").asInt());
        assertEquals(2, first.get("totalPages").asInt());
        assertEquals(1, first.get("items").size());
        assertNotEquals(first.at("/items/0/id"), second.at("/items/0/id"));
        assertFalse(first.at("/items/0").has("product"));
        assertEquals(2, http.getForObject("/api/device-units?productId=" + product.id(), JsonNode.class).get("totalElements").asInt());
        assertEquals(0, http.getForObject("/api/device-units?productId=" + product.id() + "&status=AVAILABLE", JsonNode.class).get("totalElements").asInt());
        assertEquals(HttpStatus.OK, http.getForEntity("/api/device-units?status=RECEIVED", JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/api/device-units", JsonNode.class).getStatusCode());
        JsonNode products = http.getForObject("/api/products?active=true&size=100", JsonNode.class);
        assertTrue(products.get("totalElements").asInt() >= 1);
        products.get("items").forEach(item -> assertTrue(item.get("active").asBoolean()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"modelCode\":\"ABC\",\"name\":\"   \",\"brand\":\"Apple\"}",
            "{\"modelCode\":\"BAD CODE\",\"name\":\"Mac\",\"brand\":\"Apple\"}"})
    void invalidProductRequestsReturnFieldErrors(String json) {
        ResponseEntity<JsonNode> response = postJson("/api/products", json);
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertFalse(response.getBody().get("fieldErrors").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"batteryHealth\":-1", "\"batteryHealth\":101", "\"salePrice\":0",
            "\"salePrice\":-1", "\"salePrice\":12.345", "\"salePrice\":1000000000000"})
    void invalidDeviceValuesAreRejectedWithoutPersisting(String invalidField) {
        ProductResponse product = createProduct("MODEL-" + UUID.randomUUID(), true);
        ResponseEntity<JsonNode> response = postJson("/api/device-units", "{\"productId\":\"" + product.id()
                + "\",\"serialNumber\":\"INVALID-" + UUID.randomUUID() + "\"," + invalidField + "}");
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertEquals(0, countDevices(product.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"status\":\"SOLD\"", "\"inspectionPassed\":true", "\"grade\":\"D\"", "\"batteryHealth\":91.5"})
    void clientCannotForgeStateOrSendInvalidTypedValues(String invalidField) {
        ProductResponse product = createProduct("MODEL-" + UUID.randomUUID(), true);
        ResponseEntity<JsonNode> response = postJson("/api/device-units", "{\"productId\":\"" + product.id()
                + "\",\"serialNumber\":\"INVALID-" + UUID.randomUUID() + "\"," + invalidField + "}");
        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertEquals(0, countDevices(product.id()));
    }

    @Test
    void missingProductAndUnknownResourcesReturn404() {
        UUID missing = UUID.randomUUID();
        assertError(http.postForEntity("/api/device-units", Map.of("productId", missing,
                "serialNumber", "UNKNOWN"), JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertError(http.getForEntity("/api/products/" + missing, JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertError(http.getForEntity("/api/device-units/" + missing, JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/api/nonexistent", JsonNode.class).getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products?page=-1", "/api/products?size=0", "/api/products?size=101",
            "/api/device-units?size=101", "/api/device-units?status=WRONG", "/api/products/not-a-uuid"})
    void invalidQueryParametersReturn400(String url) {
        ResponseEntity<JsonNode> response = http.getForEntity(url, JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().get("code"));
        assertFalse(response.getBody().has("trace"));
    }

    @Test
    void databaseConstraintsProtectAgainstBypassingHttpValidation() {
        ProductResponse product = createProduct("MODEL-" + UUID.randomUUID(), true);
        DeviceUnitResponse device = receive(product.id(), "SERIAL-" + UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE device_units SET battery_health = 101 WHERE id = ?", device.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE device_units SET sale_price = 0 WHERE id = ?", device.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE device_units SET status = 'AVAILABLE' WHERE id = ?", device.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE device_units SET serial_number = 'lowercase' WHERE id = ?", device.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE device_units SET product_id = ? WHERE id = ?", UUID.randomUUID(), device.id()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM products WHERE id = ?", product.id()));
        assertEquals("RECEIVED", jdbc.queryForObject("SELECT status FROM device_units WHERE id = ?", String.class, device.id()));
    }

    private ProductResponse createProduct(String code, boolean active) {
        ResponseEntity<ProductResponse> response = http.postForEntity("/api/products", Map.of(
                "modelCode", code, "name", " MacBook Air M1 ", "brand", " Apple ",
                "specificationSummary", "8GB / 256GB", "active", active), ProductResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ProductResponse product = response.getBody();
        assertNotNull(product);
        createdProducts.add(product.id());
        assertEquals("/api/products/" + product.id(), response.getHeaders().getLocation().toString());
        return product;
    }

    private DeviceUnitResponse receive(UUID productId, String serial) {
        ResponseEntity<DeviceUnitResponse> response = http.postForEntity("/api/device-units",
                Map.of("productId", productId, "serialNumber", serial), DeviceUnitResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private int countDevices(UUID productId) {
        return jdbc.queryForObject("SELECT count(*) FROM device_units WHERE product_id = ?", Integer.class, productId);
    }

    private ResponseEntity<JsonNode> postJson(String url, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(url, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(code, response.getBody().get("code").asText());
    }
}
