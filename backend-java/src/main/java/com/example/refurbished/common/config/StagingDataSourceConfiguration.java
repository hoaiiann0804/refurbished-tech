package com.example.refurbished.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Staging has its own allowlist; enabling deployment must not weaken dev/test isolation. */
@Configuration(proxyBeanMethods = false)
@Profile("staging")
@EnableConfigurationProperties(DataSourceProperties.class)
public class StagingDataSourceConfiguration {
    @Bean
    HikariDataSource dataSource(DataSourceProperties properties, Environment environment) {
        if (!java.util.Arrays.equals(environment.getActiveProfiles(), new String[]{"staging"})
                || !"jdbc:postgresql://db:5432/refurbished_staging".equals(properties.getUrl())
                || !"refurbished_staging".equals(properties.getUsername())
                || properties.getPassword() == null || properties.getPassword().isBlank()
                || environment.getProperty("app.online.sandbox-enabled", Boolean.class, false)) {
            throw new IllegalStateException("Staging requires its isolated DB/role, password and disabled sandbox payments.");
        }
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(properties.getUrl());
        pool.setUsername(properties.getUsername());
        pool.setPassword(properties.getPassword());
        pool.setMaximumPoolSize(5);
        pool.setConnectionTimeout(5000);
        // A transaction must fail and rollback instead of waiting indefinitely for a sold/held device.
        pool.setConnectionInitSql("SET lock_timeout = '5s'");
        return pool;
    }
}
