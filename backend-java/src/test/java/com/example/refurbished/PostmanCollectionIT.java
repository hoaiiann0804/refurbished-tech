package com.example.refurbished;

import com.example.refurbished.security.AppUser;
import com.example.refurbished.security.AppUserRepository;
import com.example.refurbished.security.UserRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/** Optional Newman acceptance test; default Java builds do not require Node/npm. */
@EnabledIfEnvironmentVariable(named = "REFURBISHED_RUN_POSTMAN", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.permit-all-for-tests=false")
class PostmanCollectionIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @LocalServerPort int port;

    @Test void collectionRunsAgainstIsolatedDatabaseWithTemporaryAdmin() throws Exception {
        assertEquals("refurbished_test", jdbc.queryForObject("SELECT current_database()", String.class));
        String run = UUID.randomUUID().toString();
        String email = "postman-" + run + "@test.local", password = UUID.randomUUID().toString();
        UUID actor = users.saveAndFlush(new AppUser(email, "Collection fixture", passwords.encode(password), UserRole.ADMIN)).getId();
        Path log = Path.of(".run", "postman-" + run + ".log");
        Files.createDirectories(log.getParent());
        try {
            ProcessBuilder builder = new ProcessBuilder("node", "postman/run-collection.cjs");
            builder.environment().put("POSTMAN_BASE_URL", "http://127.0.0.1:" + port);
            builder.environment().put("POSTMAN_EMAIL", email);
            builder.environment().put("POSTMAN_PASSWORD", password);
            builder.environment().put("POSTMAN_RUN_ID", run);
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            Process process = builder.start();
            try {
                assertTrue(process.waitFor(90, TimeUnit.SECONDS), "Collection timed out");
                String report = Files.readString(log);
                System.out.println("Postman acceptance: " + report);
                assertEquals(0, process.exitValue(), report);
            } finally {
                if (process.isAlive()) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
            }
        } finally {
            // Fixture ownership comes from the generated run ID and actor, never a broad DELETE.
            jdbc.update("DELETE FROM checkout_requests WHERE actor_id=?", actor);
            jdbc.update("DELETE FROM audit_events WHERE actor_user_id=?", actor);
            for (UUID product : jdbc.queryForList("SELECT id FROM products WHERE model_code=?", UUID.class, "PM-" + run)) {
                var orders = jdbc.queryForList("SELECT DISTINCT oi.order_id FROM order_items oi JOIN device_units d "
                        + "ON d.id=oi.device_unit_id WHERE d.product_id=?", UUID.class, product);
                jdbc.update("DELETE FROM warranties WHERE device_unit_id IN (SELECT id FROM device_units WHERE product_id=?)", product);
                for (UUID order : orders) {
                    jdbc.update("DELETE FROM order_items WHERE order_id=?", order);
                    jdbc.update("DELETE FROM sales_orders WHERE id=?", order);
                }
                jdbc.update("DELETE FROM device_units WHERE product_id=?", product);
                jdbc.update("DELETE FROM products WHERE id=?", product);
            }
            users.deleteById(actor);
        }
    }
}
