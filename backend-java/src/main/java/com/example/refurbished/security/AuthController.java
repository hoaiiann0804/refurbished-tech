package com.example.refurbished.security;

import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.security.dto.LoginRequest;
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

    public AuthController(AuthService auth, OAuthCodeService oauthCodes) {
        this.auth = auth;
        this.oauthCodes = oauthCodes;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }

    @PostMapping("/oauth/exchange")
    public TokenResponse exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return oauthCodes.exchange(request.code());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResourceNotFoundException("Authenticated user not found.");
        return auth.get(UUID.fromString(jwt.getSubject()));
    }
}
