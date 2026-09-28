package com.example.refurbished.security;

import com.example.refurbished.common.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecretKey jwtSecretKey(@Value("${app.security.jwt.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("REFURBISHED_JWT_SECRET must contain at least 32 characters.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${app.security.jwt.issuer}") String issuer,
            AccountTokenValidator accounts) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), accounts));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            GoogleOAuthSuccessHandler googleSuccess,
            ObjectProvider<ClientRegistrationRepository> registrations,
            org.springframework.core.env.Environment environment,
            @Value("${app.security.permit-all-for-tests:false}") boolean permitAllTests) throws Exception {
        // Không cho một biến môi trường vô tình tắt authorization ở môi trường dev.
        if (permitAllTests && !java.util.Arrays.equals(environment.getActiveProfiles(), new String[]{"test"})) {
            throw new IllegalStateException("Security bypass is allowed only with the test profile.");
        }
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response, mapper, 401, "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response, mapper, 403, "FORBIDDEN", "You do not have permission for this action.")));

        if (permitAllTests) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // Chỉ mở trang tài liệu khi bật OpenAPI; quyền của API vẫn do matcher phía dưới quyết định.
            if (environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
                http.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET,
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll());
            }
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/admin/**", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    .requestMatchers("/api/online/reservations/{id}/sandbox-payment", "/api/online/reservations/{id}/sandbox-refund").hasRole("ADMIN")
                    .requestMatchers("/api/health", "/api/auth/login", "/api/auth/oauth/exchange",
                            "/oauth2/**", "/login/oauth2/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                    .requestMatchers("/api/users/**", "/api/audit-events/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/device-units/**", "/api/orders/**",
                            "/api/warranties/**").hasAnyRole("ADMIN", "STAFF")
                    .anyRequest().hasAnyRole("ADMIN", "STAFF"));
        }

        http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
            String role = token.getClaimAsString("role");
            return new JwtAuthenticationToken(token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)), token.getClaimAsString("email"));
        })));
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth.successHandler(googleSuccess));
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://127.0.0.1:5174", "http://localhost:5174"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-ID"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, ObjectMapper mapper,
            int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(code, message, java.util.Map.of()));
    }
}
