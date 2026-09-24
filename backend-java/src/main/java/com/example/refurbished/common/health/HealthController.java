package com.example.refurbished.common.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        // A successful response means PostgreSQL answered, not merely that HTTP is running.
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return new HealthResponse("UP");
    }

    public record HealthResponse(String status) {
    }
}
