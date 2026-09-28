package com.example.refurbished;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceLifecycleIT {

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;

    private final List<UUID> createdProducts = new ArrayList<>();

    @BeforeEach
    void verifyTestDatabase() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        for (UUID productId : createdProducts) {
            jdbc.update("DELETE FROM audit_events WHERE target_id IN (SELECT id FROM device_units WHERE product_id=?)", productId);
            jdbc.update("DELETE FROM audit_events WHERE target_id=?", productId);
            jdbc.update("DELETE FROM device_units WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
    }

    @Test
    void migrationV2IsAppliedAndValidated() {
        assertTrue(Integer.parseInt(flyway.info().current().getVersion().getVersion()) >= 2);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema='public' AND table_name='device_units' "
                + "AND column_name='battery_health_unavailable_reason'", Integer.class));
    }

    @Test
    void receivedToInspectingToAvailablePersistsInspectionEvidence() {
        UUID deviceId = createReceivedDevice();

        JsonNode inspecting = post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);
        assertEquals("INSPECTING", inspecting.get("status").asText());
        assertTrue(inspecting.get("inspectionPassed").isNull());

        JsonNode available = post("/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                "passed", true,
                "grade", "A",
                "batteryHealth", 91,
                "salePrice", 12500000.25,
                "inspectionNotes", " Display, keyboard and ports passed. "), HttpStatus.OK);

        assertEquals("AVAILABLE", available.get("status").asText());
        assertTrue(available.get("inspectionPassed").asBoolean());
        assertEquals("A", available.get("grade").asText());
        assertEquals(91, available.get("batteryHealth").asInt());
        assertEquals("Display, keyboard and ports passed.", available.get("inspectionNotes").asText());
        assertFalse(available.get("inspectedAt").isNull());
        assertEquals("AVAILABLE", getDevice(deviceId).get("status").asText());
    }

    @Test
    void passingInspectionAcceptsExplicitReasonWhenBatteryMetricDoesNotApply() {
        UUID deviceId = createReceivedDevice();
        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);

        JsonNode result = post("/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                "passed", true,
                "grade", "B",
                "salePrice", 3500000,
                "inspectionNotes", "Functional checks passed.",
                "batteryHealthUnavailableReason", "Desktop device without a battery."), HttpStatus.OK);

        assertEquals("AVAILABLE", result.get("status").asText());
        assertTrue(result.get("batteryHealth").isNull());
        assertEquals("Desktop device without a battery.",
                result.get("batteryHealthUnavailableReason").asText());
    }

    @Test
    void inspectingCanEndInRejectedWithReasonOnly() {
        UUID deviceId = createReceivedDevice();
        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);

        JsonNode rejected = post("/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                "passed", false,
                "inspectionNotes", "Mainboard diagnostics failed."), HttpStatus.OK);

        assertEquals("REJECTED", rejected.get("status").asText());
        assertFalse(rejected.get("inspectionPassed").asBoolean());
        assertTrue(rejected.get("grade").isNull());
        assertTrue(rejected.get("salePrice").isNull());
        assertFalse(rejected.get("inspectedAt").isNull());
    }

    @Test
    void invalidTransitionsReturn409AndLeaveStateUnchanged() {
        UUID deviceId = createReceivedDevice();

        assertError(http.postForEntity("/api/device-units/" + deviceId + "/complete-inspection",
                Map.of("passed", false, "inspectionNotes", "Cannot skip."), JsonNode.class),
                HttpStatus.CONFLICT, "BUSINESS_CONFLICT");
        assertEquals("RECEIVED", getDevice(deviceId).get("status").asText());

        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);
        assertError(http.postForEntity("/api/device-units/" + deviceId + "/start-inspection", null, JsonNode.class),
                HttpStatus.CONFLICT, "BUSINESS_CONFLICT");
        assertEquals("INSPECTING", getDevice(deviceId).get("status").asText());

        post("/api/device-units/" + deviceId + "/complete-inspection", Map.of(
                "passed", false, "inspectionNotes", "Rejected."), HttpStatus.OK);
        assertError(http.postForEntity("/api/device-units/" + deviceId + "/start-inspection", null, JsonNode.class),
                HttpStatus.CONFLICT, "BUSINESS_CONFLICT");
        assertError(http.postForEntity("/api/device-units/" + deviceId + "/complete-inspection",
                Map.of("passed", false, "inspectionNotes", "Again."), JsonNode.class),
                HttpStatus.CONFLICT, "BUSINESS_CONFLICT");
        assertEquals("REJECTED", getDevice(deviceId).get("status").asText());
    }

    @Test
    void invalidPassingEvidenceRollsBackAndDeviceRemainsInspecting() {
        UUID deviceId = createReceivedDevice();
        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);

        List<Map<String, ?>> invalidBodies = List.of(
                Map.of("passed", true, "salePrice", 1000, "batteryHealth", 90,
                        "inspectionNotes", "Missing grade."),
                Map.of("passed", true, "grade", "A", "batteryHealth", 90,
                        "inspectionNotes", "Missing price."),
                Map.of("passed", true, "grade", "A", "salePrice", 1000,
                        "inspectionNotes", "Missing battery evidence."),
                Map.of("passed", true, "grade", "A", "salePrice", 1000, "batteryHealth", 90,
                        "batteryHealthUnavailableReason", "Conflicting evidence.",
                        "inspectionNotes", "Both metric and reason."));

        for (Map<String, ?> body : invalidBodies) {
            assertError(http.postForEntity("/api/device-units/" + deviceId + "/complete-inspection",
                    body, JsonNode.class), HttpStatus.BAD_REQUEST, "INVALID_INSPECTION");
            JsonNode current = getDevice(deviceId);
            assertEquals("INSPECTING", current.get("status").asText());
            assertTrue(current.get("inspectionPassed").isNull());
            assertTrue(current.get("inspectedAt").isNull());
        }
    }

    @Test
    void requestValidationRejectsMissingResultBlankNotesAndBadNumbers() {
        UUID deviceId = createReceivedDevice();
        post("/api/device-units/" + deviceId + "/start-inspection", null, HttpStatus.OK);

        for (Map<String, ?> body : List.<Map<String, ?>>of(
                Map.of("inspectionNotes", "Has no result."),
                Map.of("passed", false, "inspectionNotes", "   "),
                Map.of("passed", true, "grade", "A", "batteryHealth", 101,
                        "salePrice", 1000, "inspectionNotes", "Bad battery."),
                Map.of("passed", true, "grade", "A", "batteryHealth", 90,
                        "salePrice", 12.345, "inspectionNotes", "Bad price scale."))) {
            assertError(http.postForEntity("/api/device-units/" + deviceId + "/complete-inspection",
                    body, JsonNode.class), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
        assertEquals("INSPECTING", getDevice(deviceId).get("status").asText());
    }

    @Test
    void missingDeviceReturns404ForBothActions() {
        UUID missing = UUID.randomUUID();
        assertError(http.postForEntity("/api/device-units/" + missing + "/start-inspection", null, JsonNode.class),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertError(http.postForEntity("/api/device-units/" + missing + "/complete-inspection",
                Map.of("passed", false, "inspectionNotes", "Missing."), JsonNode.class),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void databaseConstraintsRejectForgedLifecycleEvidence() {
        UUID deviceId = createReceivedDevice();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE device_units SET status='AVAILABLE', inspection_passed=true, "
                        + "inspected_at=now(), grade='A', sale_price=1000, inspection_notes='Passed' WHERE id=?",
                deviceId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE device_units SET status='REJECTED', inspection_passed=true, "
                        + "inspected_at=now(), inspection_notes='Invalid' WHERE id=?", deviceId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE device_units SET battery_health=90, battery_health_unavailable_reason='Conflicting' WHERE id=?",
                deviceId));
        assertEquals("RECEIVED", getDevice(deviceId).get("status").asText());
    }

    private UUID createReceivedDevice() {
        String suffix = UUID.randomUUID().toString();
        ResponseEntity<JsonNode> productResponse = http.postForEntity("/api/products", Map.of(
                "modelCode", "LIFECYCLE-" + suffix, "name", "Lifecycle fixture", "brand", "Test"), JsonNode.class);
        assertEquals(HttpStatus.CREATED, productResponse.getStatusCode());
        UUID productId = UUID.fromString(productResponse.getBody().get("id").asText());
        createdProducts.add(productId);

        ResponseEntity<JsonNode> deviceResponse = http.postForEntity("/api/device-units", Map.of(
                "productId", productId, "serialNumber", "LIFECYCLE-" + suffix), JsonNode.class);
        assertEquals(HttpStatus.CREATED, deviceResponse.getStatusCode());
        return UUID.fromString(deviceResponse.getBody().get("id").asText());
    }

    private JsonNode post(String path, Object body, HttpStatus expectedStatus) {
        ResponseEntity<JsonNode> response = http.postForEntity(path, body, JsonNode.class);
        assertEquals(expectedStatus, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private JsonNode getDevice(UUID id) {
        ResponseEntity<JsonNode> response = http.getForEntity("/api/device-units/" + id, JsonNode.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(code, response.getBody().get("code").asText());
    }
}
