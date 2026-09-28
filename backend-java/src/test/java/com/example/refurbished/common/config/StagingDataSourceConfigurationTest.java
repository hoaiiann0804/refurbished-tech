package com.example.refurbished.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class StagingDataSourceConfigurationTest {
    private DataSourceProperties properties() {
        var p=new DataSourceProperties();p.setUrl("jdbc:postgresql://db:5432/refurbished_staging");
        p.setUsername("refurbished_staging");p.setPassword("unit-test-only");return p;
    }
    @Test void stagingPoolUsesItsOwnAllowlistWithoutConnecting() {
        var environment = new MockEnvironment().withProperty("app.online.sandbox-enabled", "false");
        environment.setActiveProfiles("staging");
        try(var pool=new StagingDataSourceConfiguration().dataSource(properties(), environment)) {
            assertEquals("jdbc:postgresql://db:5432/refurbished_staging",pool.getJdbcUrl());
        }
    }
    @Test void rejectMixedProfilesExternalDatabaseAndSandboxPayments() {
        var config=new StagingDataSourceConfiguration();var env=new MockEnvironment();env.setActiveProfiles("dev","staging");
        assertThrows(IllegalStateException.class,()->config.dataSource(properties(),env));
        env.setActiveProfiles("staging");var external=properties();external.setUrl("jdbc:postgresql://example.com:5432/prod");
        assertThrows(IllegalStateException.class,()->config.dataSource(external,env));
        env.setProperty("app.online.sandbox-enabled","true");
        assertThrows(IllegalStateException.class,()->config.dataSource(properties(),env));
    }
}
