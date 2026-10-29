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
import main.service.EngagementService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
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
            assertEquals(3, flyway.migrate().migrationsExecuted);
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
            assertEquals(3, flyway(p, schema).migrate().migrationsExecuted);
            try(var result = sql.executeQuery("SELECT context,sort_order,is_deleted,is_completed FROM chapters WHERE chapter_id=7")) {
                assertTrue(result.next());
                assertEquals("Chữ tiếng Việt còn nguyên", result.getString(1));
                assertEquals(0, result.getInt(2)); assertFalse(result.getBoolean(3)); assertTrue(result.getBoolean(4));
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE chapters SET sort_order=NULL WHERE chapter_id=7"));
        });
    }

    @Test
    void interactionCountsAreIdempotentAndPrivateProductsNeverRank() throws Exception {
        isolated((connection,schema,p) -> {
            flyway(p,schema).migrate();
            var source = new SingleConnectionDataSource(connection,true);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            var service = new EngagementService(jdbc);
            jdbc.update("INSERT INTO users(user_id,user_name) VALUES (1,'Reader')");
            jdbc.update("INSERT INTO accounts(user_id,username,email,password) VALUES (1,'reader','reader@example.test','test-hash')");
            jdbc.update("INSERT INTO products(product_id,title,status) VALUES (1,'Công khai','PUBLIC'),(2,'Riêng tư','PRIVATE'),(3,'Ẩn','PUBLIC')");
            jdbc.update("UPDATE products SET is_deleted=TRUE WHERE product_id=3");
            Instant now=Instant.now();
            service.recordView(1,"browser",now);
            service.recordView(1,"browser",now);
            service.recordView(1,"other-browser",now);
            service.recordView(2,"browser",now);
            service.recordView(3,"browser",now);
            tx.executeWithoutResult(s -> service.vote(1,1,true));
            tx.executeWithoutResult(s -> service.vote(1,1,true));
            tx.executeWithoutResult(s -> service.comment(1,1,"<script>plain text</script>"));
            assertEquals(new EngagementService.Signals(2,1,1),service.signals(1));
            assertEquals(112,service.signals(1).score());
            assertEquals(1,service.ranking(Instant.EPOCH,100,false).size());
            assertEquals(112,service.ranking(Instant.EPOCH,100,false).get(0).score());
            assertTrue(service.ranking(now.plusSeconds(3600),6,true).isEmpty());
            assertEquals("<script>plain text</script>",service.comments(1,0).get(0).body());
            tx.executeWithoutResult(s -> service.vote(1,1,false));
            assertEquals(12,service.signals(1).score());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s -> service.comment(1,1,"Too soon")));
            jdbc.update("UPDATE accounts SET is_deleted=TRUE WHERE user_id=1");
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s -> service.vote(1,1,true)));
        });
    }

    @Test
    void recommendationsUseOnlyLast24HoursAndReturnAtMostSixPublicBooks() throws Exception {
        isolated((connection,schema,p) -> {
            flyway(p,schema).migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            var service = new EngagementService(jdbc);
            jdbc.update("INSERT INTO users(user_id,user_name) VALUES (1,'Reader')");
            for(int id=1;id<=9;id++) jdbc.update("INSERT INTO products(product_id,title,status) VALUES (?,?,'PUBLIC')",id,"Book "+id);
            jdbc.update("UPDATE products SET is_deleted=TRUE WHERE product_id=9");
            Instant now=Instant.now();
            jdbc.update("INSERT INTO product_views(product_id,viewer_key,view_bucket,viewed_at) VALUES (1,'old',1,?)",
                    Timestamp.from(now.minusSeconds(25*3600)));
            jdbc.update("INSERT INTO product_votes(product_id,user_id,created_at) VALUES (2,1,?),(9,1,?)",
                    Timestamp.from(now.minusSeconds(300)),Timestamp.from(now));
            jdbc.update("INSERT INTO product_comments(product_id,user_id,body) VALUES (3,1,'one'),(3,1,'two')");
            for(int i=0;i<5;i++) service.recordView(4,"view"+i,now);
            for(int id=5;id<=8;id++) service.recordView(id,"one",now);
            var recent=service.ranking(now.minusSeconds(24*3600),6,true);
            assertEquals(List.of(2,3,4,8,7,6),recent.stream().map(EngagementService.Ranked::productId).toList());
            assertEquals(List.of(100L,20L,5L,1L,1L,1L),recent.stream().map(EngagementService.Ranked::score).toList());
            assertTrue(service.ranking(now.plusSeconds(3600),6,true).isEmpty());
        });
    }
}
