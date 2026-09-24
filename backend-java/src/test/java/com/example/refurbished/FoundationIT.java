package com.example.refurbished;

import com.example.refurbished.common.health.HealthController.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate http;

    @Test
    void connectsToIsolatedPostgresWithAnUnprivilegedRole() {
        assertEquals("refurbished_test",
                jdbcTemplate.queryForObject("SELECT current_database()", String.class));
        assertEquals("refurbished_test",
                jdbcTemplate.queryForObject("SELECT current_user", String.class));
        assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname = current_user",
                Boolean.class)));
    }

    @Test
    void healthEndpointReturnsUpThroughRealHttpAndDatabase() {
        ResponseEntity<HealthResponse> response = http.getForEntity("/api/health", HealthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().status());
    }
}
