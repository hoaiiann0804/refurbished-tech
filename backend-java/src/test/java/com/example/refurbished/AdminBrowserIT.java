package com.example.refurbished;

import com.example.refurbished.security.*;
import java.nio.file.*;
import java.util.*;
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

@EnabledIfEnvironmentVariable(named="REFURBISHED_RUN_BROWSER",matches="true")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="app.security.permit-all-for-tests=false")
class AdminBrowserIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @LocalServerPort int port;
    @Test void browserExercisesRealBackendAndRecoversLostCheckoutResponse() throws Exception {
        assertEquals("refurbished_test",jdbc.queryForObject("SELECT current_database()",String.class));
        String run=UUID.randomUUID().toString(),email="browser-"+run+"@test.local",staffEmail="staff-browser-"+run+"@test.local";
        String password=UUID.randomUUID().toString();
        UUID admin=users.saveAndFlush(new AppUser(email,"Browser Admin",passwords.encode(password),UserRole.ADMIN)).getId();
        Path log=Path.of(".run","browser-"+run+".log");Files.createDirectories(log.getParent());
        try {
            ProcessBuilder builder=new ProcessBuilder("node","scripts/test-admin-browser.cjs");
            builder.environment().putAll(Map.of("POSTMAN_BASE_URL","http://127.0.0.1:"+port,"POSTMAN_EMAIL",email,
                    "POSTMAN_PASSWORD",password,"POSTMAN_RUN_ID",run,"BROWSER_STAFF_EMAIL",staffEmail,"BROWSER_STAFF_PASSWORD",password));
            Process child=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(150,TimeUnit.SECONDS),"Browser timed out");
                String report=Files.readString(log);System.out.println(report);assertEquals(0,child.exitValue(),report);
            } finally {if(child.isAlive()){child.destroyForcibly();child.waitFor(10,TimeUnit.SECONDS);}}
        } finally {
            var actorIds=new ArrayList<UUID>();actorIds.add(admin);users.findByEmail(staffEmail).ifPresent(u->actorIds.add(u.getId()));
            for(UUID actor:actorIds){jdbc.update("DELETE FROM checkout_requests WHERE actor_id=?",actor);jdbc.update("DELETE FROM audit_events WHERE actor_user_id=?",actor);}
            for(UUID product:jdbc.queryForList("SELECT id FROM products WHERE model_code=?",UUID.class,"PM-"+run)){
                var orderIds=jdbc.queryForList("SELECT DISTINCT oi.order_id FROM order_items oi JOIN device_units d ON d.id=oi.device_unit_id WHERE d.product_id=?",UUID.class,product);
                jdbc.update("DELETE FROM warranties WHERE device_unit_id IN (SELECT id FROM device_units WHERE product_id=?)",product);
                for(UUID order:orderIds){jdbc.update("DELETE FROM order_items WHERE order_id=?",order);jdbc.update("DELETE FROM sales_orders WHERE id=?",order);}
                jdbc.update("DELETE FROM device_units WHERE product_id=?",product);jdbc.update("DELETE FROM products WHERE id=?",product);
            }
            users.deleteAllById(actorIds);
        }
    }
}
