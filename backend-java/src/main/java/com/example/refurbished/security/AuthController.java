package com.example.refurbished.security;

import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.security.dto.LoginRequest;
import com.example.refurbished.security.dto.ChangePasswordRequest;
import com.example.refurbished.common.exception.AuthenticationFailedException;
import org.springframework.http.ResponseEntity;
import com.example.refurbished.security.dto.OAuthExchangeRequest;
import com.example.refurbished.security.dto.TokenResponse;
import com.example.refurbished.security.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final OAuthCodeService oauthCodes;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService auth, OAuthCodeService oauthCodes, LoginRateLimiter rateLimiter) {
        this.auth = auth;
        this.oauthCodes = oauthCodes;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        String rateLimitKey = clientIp + ":" + request.email().trim().toLowerCase(java.util.Locale.ROOT);
        rateLimiter.checkLimit(rateLimitKey);
        try {
            TokenResponse response = auth.login(request);
            rateLimiter.recordSuccess(rateLimitKey);
            return response;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    @PostMapping("/oauth/exchange")
    public TokenResponse exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return oauthCodes.exchange(request.code());
    }

    @GetMapping("/me")
    public UserResponse me(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResourceNotFoundException("Authenticated user not found.");
        return auth.get(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        long version = sessionVersion(jwt);
        auth.changePassword(UUID.fromString(jwt.getSubject()), version, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long version = sessionVersion(jwt);
        auth.logoutAll(UUID.fromString(jwt.getSubject()), version);
        return ResponseEntity.noContent().build();
    }

    private long sessionVersion(Jwt jwt) {
        // Kể cả test bypass cũng không được đổi mật khẩu mà không có danh tính JWT.
        if (jwt == null || !(jwt.getClaims().get("ver") instanceof Number version)) {
            throw new AuthenticationFailedException("A valid Bearer session is required.");
        }
        return version.longValue();
    }
}
