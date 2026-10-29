package main;

import main.entity.*;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Chỉ tạo/xóa lược đồ tạm riêng, không chạy ứng dụng và không đụng bảng public. */
@EnabledIfEnvironmentVariable(named="RUN_DATABASE_TESTS", matches="true")
class SchemaMigrationTest {
    interface Check { void run(Connection connection, String schema, Properties config) throws Exception; }
    private void isolated(Check check) throws Exception {
        Properties config = new Properties();
        try(var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) { config.load(input); }
        String schema = "codex_schema_test_" + UUID.randomUUID().toString().replace("-", "");
        try(Connection connection = DriverManager.getConnection(config.getProperty("spring.datasource.url"),
                config.getProperty("spring.datasource.username"), config.getProperty("spring.datasource.password"))) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
            try {
                connection.createStatement().execute("SET search_path TO " + schema);
                check.run(connection, schema, config);
            } finally {
                if (!schema.matches("codex_schema_test_[0-9a-f]{32}")) throw new IllegalStateException("Unsafe schema");
                connection.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
    private Flyway flyway(Properties p, String schema) {
        return Flyway.configure().dataSource(p.getProperty("spring.datasource.url"), p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password")).defaultSchema(schema).schemas(schema)
                .baselineOnMigrate(true).baselineVersion("0").load();
    }

    @Test
    void emptyDatabaseMigratesAndMatchesAllEntities() throws Exception {
        isolated((connection, schema, p) -> {
            Flyway flyway = flyway(p, schema);
            assertEquals(2, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            Configuration hibernate = new Configuration();
            hibernate.setProperty("hibernate.connection.url", p.getProperty("spring.datasource.url"));
            hibernate.setProperty("hibernate.connection.username", p.getProperty("spring.datasource.username"));
            hibernate.setProperty("hibernate.connection.password", p.getProperty("spring.datasource.password"));
            hibernate.setProperty("hibernate.default_schema", schema);
            hibernate.setProperty("hibernate.hbm2ddl.auto", "validate");
            for(Class<?> entity : List.of(Account.class, User.class, Product.class, Category.class, Volume.class, Chapter.class,
                    ChapterImage.class, Ranking.class, ImageCleanupCandidate.class, ImageCleanupState.class, PdfImportReceipt.class)) {
                hibernate.addAnnotatedClass(entity);
            }
            try(var factory = hibernate.buildSessionFactory()) { assertTrue(factory.isOpen()); }
        });
    }

    @Test
    void existingSchemaBackfillsNullsAndMissingColumnsWithoutLosingContent() throws Exception {
        isolated((connection, schema, p) -> {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("db/migration/V1__create_existing_schema.sql"), "UTF-8"));
            var sql = connection.createStatement();
            sql.execute("ALTER TABLE chapters DROP COLUMN is_completed");
            sql.execute("INSERT INTO products(product_id,title,is_deleted,status) VALUES (5,'Truyện cũ',NULL,NULL)");
            sql.execute("INSERT INTO volumes(volume_id,product_id,sort_order,is_deleted) VALUES (6,5,NULL,NULL)");
            sql.execute("INSERT INTO chapters(chapter_id,volume_id,context,sort_order,is_deleted) VALUES (7,6,'Chữ tiếng Việt còn nguyên',NULL,NULL)");
            assertEquals(2, flyway(p, schema).migrate().migrationsExecuted);
            try(var result = sql.executeQuery("SELECT context,sort_order,is_deleted,is_completed FROM chapters WHERE chapter_id=7")) {
                assertTrue(result.next());
                assertEquals("Chữ tiếng Việt còn nguyên", result.getString(1));
                assertEquals(0, result.getInt(2)); assertFalse(result.getBoolean(3)); assertTrue(result.getBoolean(4));
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE chapters SET sort_order=NULL WHERE chapter_id=7"));
        });
    }
}
