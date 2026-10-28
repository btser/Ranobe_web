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
        values.put("categoryId", null);
        values.put("sort", "updated");
        values.put("author", "");
        values.put("artist", "");
        values.put("uploader", "");
        values.put("advancedSearch", false);
        values.put("results", new PageImpl<>(List.of(product), PageRequest.of(0, 24), 30));
        Path output = Path.of("target", "ui-preview");
        Files.createDirectories(output);
        for (String template : List.of("chapter-detail", "product-detail", "chapter-edit", "index", "search")) {
            values.put("advancedSearch", "search".equals(template));
            MockHttpServletRequest request = new MockHttpServletRequest(servlet);
            request.setRequestURI("/");
            MockHttpServletResponse response = new MockHttpServletResponse();
            WebContext context = new WebContext(app.buildExchange(request, response), Locale.forLanguageTag("vi"), values);
            String html = engine.process(template, context);
            assertTrue(html.contains("<html"), template);
            assertFalse(html.contains("th:action"), template);
            Files.writeString(output.resolve(template + ".html"), html);
        }
    }
}
