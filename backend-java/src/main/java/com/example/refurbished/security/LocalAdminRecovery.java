package com.example.refurbished.security;

import com.example.refurbished.audit.AuditService;
import java.util.Map;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Công cụ vận hành local; không có HTTP endpoint và không chạy trong profile khác. */
@Component
@Profile("dev")
public class LocalAdminRecovery implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final String email;
    private final String password;

    public LocalAdminRecovery(AppUserRepository users, PasswordEncoder passwords, AuditService audit,
            @Value("${REFURBISHED_RECOVERY_ADMIN_EMAIL:}") String email,
            @Value("${REFURBISHED_RECOVERY_ADMIN_PASSWORD:}") String password) {
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
        if (email.isBlank()) throw new IllegalStateException("Recovery admin email is required.");
        PasswordPolicy.validate(password);
        // Đọc lần đầu ngay dưới khóa: tránh giữ entity cũ trong persistence context
        // rồi nhầm rằng truy vấn khóa lần hai đã refresh role/version của nó.
        AppUser user = users.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException("Recovery requires an existing ADMIN account."));
        // Không tự tạo user, nâng quyền STAFF hoặc mở lại tài khoản đã khóa.
        // Người vận hành phải chọn đúng ADMIN đang hoạt động trong database dev đã được guard.
        if (user.getRole() != UserRole.ADMIN || !user.isEnabled()) {
            throw new IllegalStateException("Recovery requires an enabled ADMIN account.");
        }
        user.changePassword(passwords.encode(password));
        users.flush();
        // Recovery do người vận hành local thực hiện, không gán nhầm cho ADMIN đang được khôi phục.
        audit.recordAs(null, "LOCAL_OPERATOR", "ADMIN_RECOVERED", "USER", user.getId(), Map.of());
    }
}
