package com.example.refurbished.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityConfigurationTest {
    @Test
    void refusesAuthorizationBypassOutsideExclusiveTestProfile() {
        for (String[] profiles : new String[][]{{}, {"dev"}, {"dev", "test"}}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profiles);
            // Guard phải chạy trước khi cấu hình HTTP: không cần khởi động DB/server.
            assertThrows(IllegalStateException.class, () -> new SecurityConfiguration()
                    .securityFilterChain(null, null, null, null, environment, true));
        }
    }
}
