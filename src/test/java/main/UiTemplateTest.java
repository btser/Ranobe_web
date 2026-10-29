package main;

import main.entity.*;
import main.service.ReadingNavigation;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UiTemplateTest {
    @Test
    void rendersRealTemplatesForBrowserChecksWithoutDatabase() throws Exception {
        MockServletContext servlet = new MockServletContext();
        var app = JakartaServletWebApplication.buildApplication(servlet);
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        Product product = new Product();
        ReflectionTestUtils.setField(product, "productId", 101);
        product.setTitle("Bên kia những ngày mưa");
        product.setAuthor("Tác giả thử nghiệm");
        product.setStatus(Status.PUBLIC);
        product.setUpdatedAt(LocalDateTime.of(2026, 10, 27, 8, 30));
        product.setSummary("Một câu chuyện về những cuộc gặp gỡ, ký ức và hành trình trở về.");
        Category category = new Category(1, "Đời thường");
        product.setCategory(category);
        product.getCategories().add(category);
        Volume empty = new Volume(1, product);
        Volume volume = new Volume(2, product);
        volume.setTitle("Tập 1 · Những cuộc gặp gỡ");
        volume.setSortOrder(1);
        Volume second = new Volume(3, product);
        second.setTitle("Tập 2");
        second.setSortOrder(2);
        List<Chapter> chapters = new ArrayList<>();
        String paragraph = "Buổi sáng, ánh nắng xuyên qua tán cây bên hiên nhà. "
                + "Tôi mở cuốn sách còn đánh dấu từ hôm trước, nghe tiếng gió và chậm rãi đọc tiếp. "
                + "Những điều tưởng như rất nhỏ đôi khi lại ở lại trong ký ức lâu nhất.";
        for (int i = 1; i <= 28; i++) {
            Chapter chapter = new Chapter(i, i <= 14 ? volume : second, paragraph);
            chapter.setTitle("Chương " + i + " · Con đường sau cơn mưa");
            chapter.setSortOrder(i);
            chapters.add(chapter);
        }
        volume.setChapters(chapters.subList(0, 14));
        second.setChapters(chapters.subList(14, 28));
        List<Volume> volumes = List.of(empty, volume, second);
        Map<String, Object> values = new HashMap<>();
        values.put("siteLogoSmall", "/images/default-cover.webp");
        values.put("siteLogoBig", "/images/default-cover.webp");
        values.put("siteDefaultCover", "/images/default-cover.webp");
        values.put("product", product);
        values.put("volumes", volumes);
        values.put("chapter", chapters.get(0));
        values.put("images", List.of());
        values.put("blocks", List.of());
        values.put("wordCount", 2500);
        values.put("contentHtml", java.util.stream.IntStream.range(0, 50)
                    .mapToObj(i -> "<p>" + (i + 1) + ". " + paragraph + "</p>").reduce("", String::concat));
        values.put("readingNavigation", ReadingNavigation.build(product.getPath(), volumes, 1));
        values.put("firstChapterPath", ReadingNavigation.build(product.getPath(), volumes, null).chapters().get(0).path());
        values.put("isOwner", true);
        values.put("engagementSummary", new main.service.EngagementService.Signals(5,1,1));
        values.put("hasVoted",false);
        values.put("interactionToken","test-token");
        values.put("commentPage",0);
        values.put("comments",List.of(new main.service.EngagementService.Comment(1,7,"Người đọc","<script>không chạy</script>",java.time.Instant.now(),false)));
        values.put("rankedProducts",List.of(new main.service.EngagementService.Ranked(101,product.getTitle(),null,5,1,1,115)));
        values.put("recommendedProducts", values.get("rankedProducts"));
        values.put("isStaff", false);
        values.put("canEdit", true);
        values.put("canManageProduct", false);
        values.put("user", null);
        values.put("editorHtml", "<p>Nội dung ban đầu.</p>");
        values.put("editorDraftKey", "user:7:chapter:1");
        values.put("products", List.of(product));
        values.put("latestChapters", Map.of(101, chapters.get(27)));
        values.put("categories", List.of(category));
        values.put("q", "");
        values.put("categoryId", List.of(1));
        values.put("sort", "updated");
        values.put("author", "");
        values.put("artist", "");
        values.put("uploader", "");
        values.put("advancedSearch", false);
        values.put("results", new PageImpl<>(List.of(product), PageRequest.of(0, 24), 30));
        Account administrator = new Account();
        administrator.setAccountId(1);
        administrator.setRole(AccountRole.ADMIN);
        administrator.setUsername("admin");
        values.put("accounts", List.of(administrator));
        values.put("currentAccount", administrator);
        values.put("isAdmin", true);
        values.put("accountResults", new PageImpl<>(List.of(administrator), PageRequest.of(0, 24), 30));
        values.put("productResults", new PageImpl<>(List.of(product), PageRequest.of(0, 24), 30));
        values.put("accountQ", ""); values.put("productQ", "");
        values.put("accountState", "all"); values.put("productState", "all");
        Path output = Path.of("target", "ui-preview");
        Files.createDirectories(output);
        for (String template : List.of("chapter-detail", "product-detail", "chapter-edit", "index", "search", "admin", "ranking")) {
            values.put("advancedSearch", "search".equals(template));
            MockHttpServletRequest request = new MockHttpServletRequest(servlet);
            request.setRequestURI("/");
            MockHttpServletResponse response = new MockHttpServletResponse();
            WebContext context = new WebContext(app.buildExchange(request, response), Locale.forLanguageTag("vi"), values);
            String html = engine.process(template, context);
            assertTrue(html.contains("<html"), template);
            assertFalse(html.contains("th:action"), template);
            if (template.equals("ranking")) {
                assertTrue(html.contains("115 điểm"));
                assertTrue(html.contains(product.getTitle()));
            }
            if (template.equals("product-detail")) {
                assertTrue(html.contains("&lt;script&gt;không chạy&lt;/script&gt;"));
            }
            Files.writeString(output.resolve(template + ".html"), html);
        }
        values.put("volume", volume);
        values.put("token", "12345678-1234-1234-1234-123456789abc");
        values.put("pageCount", 10);
        values.put("chapters", List.of(Map.of("title", "Chương thử", "text", "Nội dung giữ lại",
                "skipDefault", false, "startPage", 1, "length", 16)));
        values.put("images", List.of(Map.of("file", "p001.jpg", "chapterIndex", 0, "kindName", "COLOR", "selected", false)));
        for (boolean retry : List.of(false, true)) {
            if (retry) {
                values.put("retryValues", Map.of("poolTitle", "Minh họa đã sửa"));
                values.put("importFailure", "Không tải được ảnh p001.jpg");
            }
            MockHttpServletRequest request = new MockHttpServletRequest(servlet);
            request.setRequestURI("/");
            var context = new WebContext(app.buildExchange(request, new MockHttpServletResponse()), Locale.forLanguageTag("vi"), values);
            String html = engine.process("volume-import-preview", context);
            assertTrue(html.contains("Nội dung giữ lại"));
            if (retry) {
                assertTrue(html.contains("Thử lưu lại"));
                assertTrue(html.contains("Minh họa đã sửa"));
            }
        }
    }
}
