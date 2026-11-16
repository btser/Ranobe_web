package main;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
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
                exerciseProtectedForms(client, base);
            } finally {
                if (!schema.matches("codex_app_test_[0-9a-f]{32}")) throw new IllegalStateException("Unsafe schema");
                connection.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static void exerciseProtectedForms(HttpClient client, String base) throws Exception {
        String token = token(client.send(HttpRequest.newBuilder(URI.create(base + "/register")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
        String registration = "username=security_test&email=security_test%40example.test"
                + "&password=Test-password-2026%21&userName=Security+Test";
        assertEquals(403, post(client, base + "/register", registration, "application/x-www-form-urlencoded").statusCode());
        assertEquals(403, post(client, base + "/register", registration + "&_csrf=invalid",
                "application/x-www-form-urlencoded").statusCode());
        assertEquals(302, post(client, base + "/register", registration + "&_csrf=" + encode(token),
                "application/x-www-form-urlencoded").statusCode());

        // JSON có mã hợp lệ đi qua bộ lọc rồi vẫn bị kiểm tra đăng nhập ở bộ điều khiển.
        var json = HttpRequest.newBuilder(URI.create(base + "/api/manage/products"))
                .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Test\"}")).build();
        assertEquals(401, client.send(json, HttpResponse.BodyHandlers.ofString()).statusCode());

        // Biểu mẫu nhiều phần có tệp phải đọc được mã ẩn trước khi kiểm tra đăng nhập.
        String boundary = "SecurityIntegrationBoundary";
        String multipart = field(boundary, "username", "security_test")
                + field(boundary, "password", "Test-password-2026!") + field(boundary, "_csrf", token)
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.txt\""
                + "\r\nContent-Type: text/plain\r\n\r\ntest\r\n--" + boundary + "--\r\n";
        assertEquals(302, post(client, base + "/login", multipart,
                "multipart/form-data; boundary=" + boundary).statusCode());
        assertEquals(403, post(client, base + "/logout", "_csrf=" + encode(token),
                "application/x-www-form-urlencoded").statusCode(), "Login must rotate the request token");
        assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(base + "/logout")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        String fresh = token(client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(302, post(client, base + "/logout", "_csrf=" + encode(fresh),
                "application/x-www-form-urlencoded").statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base + "/api/manage/products")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private static HttpResponse<String> post(HttpClient client, String url, String body, String contentType) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String html) {
        var match = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(match.find(), "Rendered form must contain a request verification token");
        return match.group(1);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static String field(String boundary, String name, String value) {
        return "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n";
    }
}
