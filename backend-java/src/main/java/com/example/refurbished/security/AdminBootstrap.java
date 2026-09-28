package com.example.refurbished.security;

import com.example.refurbished.audit.AuditService;
import java.util.Map;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final String email;
    private final String password;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwords, AuditService audit,
            @Value("${REFURBISHED_BOOTSTRAP_ADMIN_EMAIL:}") String email,
            @Value("${REFURBISHED_BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("Bootstrap admin requires email and a 12-72 character password.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!users.existsByEmail(normalized)) {
            PasswordPolicy.validate(password);
            AppUser user = users.saveAndFlush(new AppUser(normalized, "Local Administrator", passwords.encode(password), UserRole.ADMIN));
            audit.recordAs(null, "LOCAL_OPERATOR", "ADMIN_BOOTSTRAPPED", "USER", user.getId(), Map.of());
        }
    }
}
