package com.example.refurbished.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * This learning application may connect only to its two isolated local databases.
 * Validate the effective settings before constructing a connection pool.
 */
@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Profile("!staging")
@EnableConfigurationProperties(DataSourceProperties.class)
public class LocalDataSourceConfiguration {

    @Bean
    public HikariDataSource dataSource(DataSourceProperties properties, Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length != 1 || !(profiles[0].equals("dev") || profiles[0].equals("test"))) {
            throw new IllegalStateException("Activate exactly one local profile: dev or test.");
        }

        boolean testing = profiles[0].equals("test");
        String expectedUrl = testing
                ? "jdbc:postgresql://127.0.0.1:55433/refurbished_test"
                : "jdbc:postgresql://127.0.0.1:55432/refurbished_dev";
        String expectedUser = testing ? "refurbished_test" : "refurbished_app";

        if (!expectedUrl.equals(properties.getUrl()) || !expectedUser.equals(properties.getUsername())) {
            throw new IllegalStateException("Database settings must match the isolated local "
                    + profiles[0] + " database. External URLs and other roles are rejected.");
        }
        if (properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("Local database password is missing. Load .env.local first.");
        }

        // Do not bind arbitrary spring.datasource.hikari.* connection overrides.
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(expectedUrl);
        dataSource.setUsername(expectedUser);
        dataSource.setPassword(properties.getPassword());
        dataSource.setPoolName("refurbished-" + profiles[0]);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(5000);
        return dataSource;
    }
}
