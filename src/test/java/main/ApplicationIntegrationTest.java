package main;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Khởi động máy chủ thật với vùng dữ liệu tạm; không gọi kho ảnh bên ngoài. */
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class ApplicationIntegrationTest {
    @Test
    void upgradedApplicationStartsAndServesPublicPages() throws Exception {
        Properties config = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            config.load(input);
        }
        String url = config.getProperty("spring.datasource.url");
        assertTrue(url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1)(:|/).*"),
                "Integration test requires a local PostgreSQL instance");
        String schema = "codex_app_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url,
                config.getProperty("spring.datasource.username"), config.getProperty("spring.datasource.password"))) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
            try (var application = new SpringApplicationBuilder(Application.class).run(
                    "--server.address=127.0.0.1", "--server.port=0",
                    "--spring.datasource.hikari.schema=" + schema,
                    "--spring.flyway.default-schema=" + schema, "--spring.flyway.schemas=" + schema,
                    "--spring.jpa.properties.hibernate.default_schema=" + schema,
                    "--image-cleanup.enabled=false",
                    "--r2.account-id=integration-test", "--r2.access-key=integration-test",
                    "--r2.secret-key=integration-test", "--r2.bucket=integration-test",
                    "--r2.public-url=https://example.invalid")) {
                String base = "http://127.0.0.1:" + application.getEnvironment().getProperty("local.server.port");
                var client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                        .connectTimeout(Duration.ofSeconds(10)).build();
                for (String path : new String[]{"/", "/login", "/register", "/thao-luan", "/tim-kiem"}) {
                    var response = client.send(HttpRequest.newBuilder(URI.create(base + path))
                            .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode(), path);
                    assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"), path);
                }
                var denied = client.send(HttpRequest.newBuilder(URI.create(base + "/api/manage/products"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(401, denied.statusCode());
            } finally {
                if (!schema.matches("codex_app_test_[0-9a-f]{32}")) throw new IllegalStateException("Unsafe schema");
                connection.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
