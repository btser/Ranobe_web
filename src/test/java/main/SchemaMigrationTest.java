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
import main.service.DiscussionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** Chỉ tạo/xóa lược đồ tạm riêng, không chạy ứng dụng và không đụng bảng public. */
@EnabledIfEnvironmentVariable(named="RUN_DATABASE_TESTS", matches="true")
class SchemaMigrationTest {
    @Test
    void chapterRenameAcceptsDatabaseAlreadyUsingContent() throws Exception {
        isolated((connection,schema,p) -> {
            ScriptUtils.executeSqlScript(connection,new EncodedResource(
                    new ClassPathResource("db/migration/V1__create_existing_schema.sql"),"UTF-8"));
            var sql=connection.createStatement();
            sql.execute("ALTER TABLE chapters RENAME COLUMN context TO content");
            sql.execute("INSERT INTO products(product_id,title) VALUES (5,'Truyện')");
            sql.execute("INSERT INTO volumes(volume_id,product_id) VALUES (6,5)");
            sql.execute("INSERT INTO chapters(chapter_id,volume_id,content) VALUES (7,6,'Tiếng Việt giữ nguyên')");
            assertEquals(5,flyway(p,schema).migrate().migrationsExecuted);
            assertEquals("Tiếng Việt giữ nguyên",new JdbcTemplate(new SingleConnectionDataSource(connection,true))
                    .queryForObject("SELECT content FROM chapters WHERE chapter_id=7",String.class));
        });
    }

    @Test
    void chapterRenameStopsWhenBothColumnsExistWithoutDiscardingData() throws Exception {
        isolated((connection,schema,p) -> {
            ScriptUtils.executeSqlScript(connection,new EncodedResource(
                    new ClassPathResource("db/migration/V1__create_existing_schema.sql"),"UTF-8"));
            var sql=connection.createStatement();
            sql.execute("ALTER TABLE chapters ADD COLUMN content TEXT");
            sql.execute("INSERT INTO products(product_id,title) VALUES (5,'Truyện')");
            sql.execute("INSERT INTO volumes(volume_id,product_id) VALUES (6,5)");
            sql.execute("INSERT INTO chapters(chapter_id,volume_id,context,content) VALUES (7,6,'Bản cũ','Bản mới')");
            assertThrows(org.flywaydb.core.api.FlywayException.class,()->flyway(p,schema).migrate());
            try(var row=sql.executeQuery("SELECT context,content FROM chapters WHERE chapter_id=7")) {
                assertTrue(row.next());assertEquals("Bản cũ",row.getString(1));assertEquals("Bản mới",row.getString(2));
            }
        });
    }
    @Test
    void discussionPermissionsModerationAndActivityRemainConsistent() throws Exception {
        isolated((connection,schema,p) -> {
            flyway(p,schema).migrate();
            var source=new SingleConnectionDataSource(connection,true);
            var jdbc=new JdbcTemplate(source);
            var service=new DiscussionService(jdbc);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            for(int id=1;id<=3;id++) {
                jdbc.update("INSERT INTO users(user_id,user_name) VALUES (?,?)",id,"User "+id);
                jdbc.update("INSERT INTO accounts(user_id,username,email,password,role) VALUES (?,?,?,?,?)",id,"user"+id,"user"+id+"@example.test","test-hash",id==3?"MOD":"USER");
            }
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.execute(s->service.create(1,"Fake notice","body",true)));
            long topicId=tx.execute(s->service.create(1,"Chủ đề Việt","<img src=x onerror=alert(1)>",false));
            var topic=service.topic(topicId,false);
            assertEquals(1,topic.lastUserId());assertEquals(0,topic.commentCount());
            assertEquals("<img src=x onerror=alert(1)>",topic.content());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.execute(s->service.create(1,"Too soon","body",false)));
            long replyId=tx.execute(s->service.postReply(topicId,2,"Trả lời"));
            assertEquals(2,service.topic(topicId,false).lastUserId());
            assertEquals(1,service.topic(topicId,false).commentCount());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.execute(s->service.postReply(topicId,2,"Too soon")));
            Instant activity=service.topic(topicId,false).activityAt();
            tx.executeWithoutResult(s->service.editTopic(topicId,1,"Tên mới","Nội dung sửa",false));
            tx.executeWithoutResult(s->service.editReply(topicId,replyId,2,"Bình luận sửa"));
            assertEquals(activity,service.topic(topicId,false).activityAt());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s->service.editTopic(topicId,3,"Mod sửa người khác","body",false)));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s->service.editReply(topicId,replyId,1,"Sửa người khác")));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s->service.editTopic(topicId,1,"Tên","body",true)));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s->service.moderate(topicId,1,"hide",null)));
            tx.executeWithoutResult(s->service.moderate(topicId,3,"hide-comment",replyId));
            assertEquals(0,service.topic(topicId,false).commentCount());
            assertEquals(1,service.topic(topicId,false).lastUserId());
            assertEquals(0,service.replies(topicId,false,0).getTotalElements());
            assertEquals(1,service.replies(topicId,true,0).getTotalElements());
            tx.executeWithoutResult(s->service.moderate(topicId,3,"restore-comment",replyId));
            tx.executeWithoutResult(s->service.moderate(topicId,3,"lock",null));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.execute(s->service.postReply(topicId,1,"Locked")));
            tx.executeWithoutResult(s->service.moderate(topicId,3,"unlock",null));
            tx.executeWithoutResult(s->service.moderate(topicId,3,"hide",null));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.topic(topicId,false));
            assertEquals(0,service.topics("","all","hidden",false,0).getTotalElements());
            assertEquals(1,service.topics("","all","hidden",true,0).getTotalElements());
            tx.executeWithoutResult(s->service.moderate(topicId,3,"restore",null));
            long notice=tx.execute(s->service.create(3,"Thông báo","Thông báo quản trị",true));
            assertTrue(service.topic(notice,false).notification());
            assertEquals(1,service.topics("","notification","active",false,0).getTotalElements());
            assertEquals(0,service.topics("%","all","active",false,0).getTotalElements());
            jdbc.update("UPDATE accounts SET is_deleted=TRUE WHERE user_id=2");
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.executeWithoutResult(s->service.editReply(topicId,replyId,2,"Banned")));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> tx.execute(s->service.postReply(topicId,2,"Banned")));
        });
    }

    @Test
    void discussionReplyCannotBeModifiedThroughAnotherTopic() throws Exception {
        isolated((connection,schema,p) -> {
            flyway(p,schema).migrate();
            var source=new SingleConnectionDataSource(connection,true);var jdbc=new JdbcTemplate(source);
            var service=new DiscussionService(jdbc);var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            jdbc.update("INSERT INTO users(user_id,user_name) VALUES (1,'A'),(2,'B')");
            jdbc.update("INSERT INTO accounts(user_id,username,email,password,role) VALUES (1,'a','a@t.test','hash','USER'),(2,'b','b@t.test','hash','ADMIN')");
            long first=tx.execute(s->service.create(1,"First","body",false));
            long second=tx.execute(s->service.create(2,"Second","body",false));
            long comment=tx.execute(s->service.postReply(first,1,"Reply"));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    ()->tx.executeWithoutResult(s->service.editReply(second,comment,1,"Wrong topic")));
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    ()->tx.executeWithoutResult(s->service.moderate(second,2,"hide-comment",comment)));
            assertFalse(service.reply(first,comment,false).deleted());
            for(int i=0;i<25;i++) jdbc.update("INSERT INTO discuss(user_id,title,content) VALUES (1,?,'body')","Extra "+i);
            assertEquals(24,service.topics("","all","active",false,0).getContent().size());
            assertEquals(3,service.topics("","all","active",false,1).getContent().size());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    ()->tx.execute(s->service.postReply(second,1," ")));
        });
    }
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
            assertEquals(5, flyway.migrate().migrationsExecuted);
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
            assertEquals(5, flyway(p, schema).migrate().migrationsExecuted);
            try(var result = sql.executeQuery("SELECT content,sort_order,is_deleted,is_completed FROM chapters WHERE chapter_id=7")) {
                assertTrue(result.next());
                assertEquals("Chữ tiếng Việt còn nguyên", result.getString(1));
                assertEquals(0, result.getInt(2)); assertFalse(result.getBoolean(3)); assertTrue(result.getBoolean(4));
            }
            var source=new SingleConnectionDataSource(connection,true);
            var jdbc=new JdbcTemplate(source);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='chapters' AND column_name='context'",Integer.class,schema));
            jdbc.update("UPDATE chapters SET content=? WHERE chapter_id=7","<p>Tiếng Việt</p><img src=\"https://example.test/chapters/p1/kept.jpg\">");
            var cleanup=new main.service.ImageCleanupStore(jdbc,new DataSourceTransactionManager(source));
            assertTrue(cleanup.referenced("chapters/p1/kept.jpg"));
            assertFalse(cleanup.referenced("chapters/p1/missing.jpg"));
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
