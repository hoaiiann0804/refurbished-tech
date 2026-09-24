package com.example.refurbished.common.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MigrationConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        // Always use the guarded application DataSource, never a separate Flyway URL.
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();
    }
}
