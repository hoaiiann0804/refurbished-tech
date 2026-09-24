package com.example.refurbished.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalDataSourceConfigurationTest {

    private final LocalDataSourceConfiguration configuration = new LocalDataSourceConfiguration();

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://remote.invalid:5432/production",
            "jdbc:postgresql://127.0.0.1:6000/websitebanhangmini",
            "jdbc:postgresql://127.0.0.1:55432/refurbished_dev?currentSchema=other",
            "jdbc:postgresql://127.0.0.1:55433/refurbished_test"
    })
    void rejectsUnexpectedDatabaseBeforeOpeningConnections(String url) {
        DataSourceProperties properties = devProperties();
        properties.setUrl(url);
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(properties, environment("dev")));
    }

    @Test
    void testProfileCannotConnectToDevelopmentDatabase() {
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(devProperties(), environment("test")));
    }

    @Test
    void rejectsBootstrapRole() {
        DataSourceProperties properties = devProperties();
        properties.setUsername("refurbished_admin");
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(properties, environment("dev")));
    }

    @Test
    void rejectsMissingPassword() {
        DataSourceProperties properties = devProperties();
        properties.setPassword("");
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(properties, environment("dev")));
    }

    @Test
    void rejectsMissingProfile() {
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(devProperties(), environment()));
    }

    @Test
    void rejectsProductionProfile() {
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(devProperties(), environment("prod")));
    }

    @Test
    void rejectsMixedProfiles() {
        assertThrows(IllegalStateException.class,
                () -> configuration.dataSource(devProperties(), environment("dev", "test")));
    }

    private DataSourceProperties devProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://127.0.0.1:55432/refurbished_dev");
        properties.setUsername("refurbished_app");
        properties.setPassword("unit-test-placeholder-never-used-for-a-connection");
        return properties;
    }

    private MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }
}
