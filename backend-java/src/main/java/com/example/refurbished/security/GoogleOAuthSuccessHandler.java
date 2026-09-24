package com.example.refurbished.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService auth;
    private final OAuthCodeService codes;
    private final String frontendCallback;

    public GoogleOAuthSuccessHandler(AuthService auth, OAuthCodeService codes,
            @Value("${app.security.google.frontend-callback}") String frontendCallback) {
        this.auth = auth;
        this.codes = codes;
        this.frontendCallback = frontendCallback;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        try {
            if (!(authentication.getPrincipal() instanceof OidcUser oidc)
                    || !Boolean.TRUE.equals(oidc.getClaimAsBoolean("email_verified"))) {
                redirectError(response, "google_email_not_verified");
                return;
            }
            AppUser user = auth.requireEnabledByEmail(oidc.getEmail());
            String code = codes.issue(user);
            response.sendRedirect(UriComponentsBuilder.fromUriString(frontendCallback)
                    .queryParam("code", code).build().encode().toUriString());
        } catch (RuntimeException exception) {
            redirectError(response, "google_account_not_provisioned");
        }
    }

    private void redirectError(HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(UriComponentsBuilder.fromUriString(frontendCallback)
                .queryParam("error", error).build().encode().toUriString());
    }
}
