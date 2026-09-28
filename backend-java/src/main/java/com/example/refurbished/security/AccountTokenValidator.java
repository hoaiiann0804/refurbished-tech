package com.example.refurbished.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final AppUserRepository users;
    public AccountTokenValidator(AppUserRepository users) { this.users = users; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // Chữ ký đúng chưa đủ: user có thể đã khóa hoặc đổi mật khẩu sau khi cấp JWT.
        // Chấp nhận thêm một lần đọc DB để thu hồi phiên có hiệu lực ở request kế tiếp.
        try {
            Object version = jwt.getClaims().get("ver");
            if (jwt.getSubject() == null || !(version instanceof Long || version instanceof Integer)) {
                return invalidSession();
            }
            AppUser user = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
            if (version instanceof Number number && user != null && user.isEnabled()
                    && number.longValue() == user.getTokenVersion()
                    && user.getRole().name().equals(jwt.getClaimAsString("role"))) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException ignored) {
            // Subject không hợp lệ là lỗi xác thực, không phải lỗi HTTP 500.
        }
        return invalidSession();
    }

    private OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "Session is invalid or revoked. Please sign in again.", null));
    }
}
