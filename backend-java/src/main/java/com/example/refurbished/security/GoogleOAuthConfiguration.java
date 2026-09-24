package com.example.refurbished.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
public class GoogleOAuthConfiguration {
    @Bean
    @ConditionalOnExpression("'${app.security.google.client-id:}' != '' && '${app.security.google.client-secret:}' != ''")
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${app.security.google.client-id}") String clientId,
            @Value("${app.security.google.client-secret}") String clientSecret) {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(clientId).clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
