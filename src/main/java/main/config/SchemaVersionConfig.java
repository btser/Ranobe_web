package main.config;

import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cấu trúc do các bản thay đổi SQL quản lý; Hibernate chỉ xác minh sau khi cập nhật. */
@Configuration
public class SchemaVersionConfig {
    @Bean
    public FlywayConfigurationCustomizer adoptExistingSchema() {
        return configuration -> configuration.baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("0"));
    }

    @Bean
    public HibernatePropertiesCustomizer validateVersionedSchema() {
        return properties -> properties.put("hibernate.hbm2ddl.auto", "validate");
    }
}
