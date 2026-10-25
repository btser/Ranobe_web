package main.controller;

import jakarta.servlet.http.HttpSession;
import main.entity.Chapter;
import main.entity.ChapterImage;
import main.entity.User;
import main.entity.Volume;
import main.repository.ChapterImageRepository;
import main.repository.ChapterRepository;
import main.repository.ProductRepository;
import main.repository.VolumeRepository;
import main.service.PdfAnalyzer;
import main.service.R2StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Nhập một tập từ file PDF: upload PDF -> xem trước (chương + ảnh minh họa) -> lưu hàng loạt.
 * Ảnh trang minh họa được render ra thư mục tạm uploads/tmp/<token>/ để xem trước,
 * chỉ đẩy lên R2 khi bấm lưu.
 */
@Controller
@RequestMapping("/truyen")
public class PdfImportController {

    private static final Logger log = LoggerFactory.getLogger(PdfImportController.class);
    private static final Pattern TOKEN = Pattern.compile("^[0-9a-f\\-]{36}$");
    private static final Pattern IMG_FILE = Pattern.compile("^p\\d{3,4}\\.jpg$");
    private static final int MAX_CHAPTERS = 1000;
    private static final int MAX_IMAGES = 200;

    @Value("${upload.local-dir:uploads}")
    private String uploadDir;

    private final VolumeRepository volumeRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterImageRepository chapterImageRepository;
    private final ProductRepository productRepository;
    private final R2StorageService r2StorageService;

    public PdfImportController(VolumeRepository volumeRepository,
                               ChapterRepository chapterRepository,
                               ChapterImageRepository chapterImageRepository,
                               ProductRepository productRepository,
                               R2StorageService r2StorageService) {
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
        this.chapterImageRepository = chapterImageRepository;
        this.productRepository = productRepository;
        this.r2StorageService = r2StorageService;
    }

    // ---------- 1. Form upload PDF ----------

    @GetMapping("/{pid}/{pslug}/tap/{volumeId}/{vslug}/import-pdf")
    public String form(@PathVariable("volumeId") int volumeId, Model model, HttpSession session) {
        Volume volume = loadManaged(volumeId, session);
        if (volume == null) {
            return "redirect:/";
        }
        fillFormModel(model, volume, PdfAnalyzer.DEFAULT_PATTERN, null);
        return "volume-import";
    }

    // ---------- 2. Phân tích PDF -> trang xem trước ----------

    @PostMapping("/{pid}/{pslug}/tap/{volumeId}/{vslug}/import-pdf")
    public String analyze(@PathVariable("volumeId") int volumeId,
                          @RequestParam("pdfFile") MultipartFile pdfFile,
                          @RequestParam(value = "pattern", required = false) String pattern,
                          Model model, HttpSession session) {

        Volume volume = loadManaged(volumeId, session);
        if (volume == null) {
            return "redirect:/";
        }
        String usedPattern = (pattern == null || pattern.isBlank()) ? PdfAnalyzer.DEFAULT_PATTERN : pattern;

        String name = pdfFile == null ? null : pdfFile.getOriginalFilename();
        if (pdfFile == null || pdfFile.isEmpty() || name == null || !name.toLowerCase().endsWith(".pdf")) {
            fillFormModel(model, volume, usedPattern, "Hãy chọn một file PDF.");
            return "volume-import";
        }

        Path tmpPdf = null;
        Path outDir = null;
        try {
            cleanupOldTmp();
            String token = UUID.randomUUID().toString();
            outDir = tmpRoot().resolve(token);
            tmpPdf = Files.createTempFile("import-", ".pdf");
            pdfFile.transferTo(tmpPdf);

            PdfAnalyzer.Result result = new PdfAnalyzer().analyze(tmpPdf, usedPattern, outDir);

            if (result.isNoText()) {
                deleteRecursively(outDir);
                fillFormModel(model, volume, usedPattern,
                        "PDF này gần như không có chữ chọn được (có thể là bản scan nên cần OCR). Chưa hỗ trợ loại này.");
                return "volume-import";
            }

            model.addAttribute("volume", volume);
            model.addAttribute("product", volume.getProduct());
            model.addAttribute("token", token);
            model.addAttribute("chapters", result.getChapters());
            model.addAttribute("images", result.getImages());
            model.addAttribute("pageCount", result.getPageCount());
            model.addAttribute("summary", result.getSummary());
            return "volume-import-preview";

        } catch (PatternSyntaxException e) {
            deleteQuietly(outDir);
            fillFormModel(model, volume, usedPattern, "Mẫu nhận diện chương không hợp lệ: " + e.getDescription());
            return "volume-import";
        } catch (Exception e) {
            log.error("Đọc PDF lỗi", e);
            deleteQuietly(outDir);
            fillFormModel(model, volume, usedPattern, "Không đọc được PDF: " + e.getMessage());
            return "volume-import";
        } finally {
            if (tmpPdf != null) {
                try {
                    Files.deleteIfExists(tmpPdf);
                } catch (IOException ignored) {
                    // file tạm, bỏ qua
                }
            }
        }
    }

    // ---------- 3. Lưu hàng loạt ----------

    @PostMapping("/{pid}/{pslug}/tap/{volumeId}/{vslug}/import-pdf/commit")
    public String commit(@PathVariable("volumeId") int volumeId,
                         @RequestParam Map<String, String> p,
                         HttpSession session) {

        Volume volume = loadManaged(volumeId, session);
        if (volume == null) {
            return "redirect:/";
        }
        int productId = volume.getProduct().getProductId();

        String token = p.get("token");
        if (token == null || !TOKEN.matcher(token).matches()) {
            return "redirect:" + editPath(volume) + "?importError=1";
        }
        Path tmpDir = tmpRoot().resolve(token).normalize();

        // 1. Gộp / bỏ chương theo lựa chọn
        int chapterCount = clamp(parseInt(p.get("chapterCount"), 0), 0, MAX_CHAPTERS);
        int[] mapTo = new int[chapterCount]; // chỉ số chương gốc -> chỉ số chương cuối cùng (-1 = bị bỏ)
        List<String> titles = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < chapterCount; i++) {
            if (p.containsKey("skip_" + i)) {
                mapTo[i] = -1;
                continue;
            }
            String title = p.getOrDefault("title_" + i, "").strip();
            String text = p.getOrDefault("text_" + i, "").strip();
            if (p.containsKey("merge_" + i) && !titles.isEmpty()) {
                int last = titles.size() - 1;
                texts.set(last, texts.get(last) + "\n\n" + text);
                mapTo[i] = last;
            } else {
                titles.add(title.isEmpty() ? "Chương " + (titles.size() + 1) : limit(title, 250));
                texts.add(text);
                mapTo[i] = titles.size() - 1;
            }
        }

        // 2. Ảnh. Có 2 nguồn:
        //    (a) ảnh nằm giữa nội dung: trong text là dòng [[IMG:pXXX.jpg]] đúng vị trí -> thay bằng [img]link[/img];
        //    (b) ảnh được tick "Vào Minh họa" -> thêm vào chương Minh họa (màu trước, mục lục, đen trắng sau).
        int imageCount = clamp(parseInt(p.get("imageCount"), 0), 0, MAX_IMAGES);
        Map<String, Integer> rankOf = new HashMap<>();   // file -> hạng (0 màu, 1 mục lục, 2 đen trắng)
        List<String> poolFiles = new ArrayList<>();
        for (int j = 0; j < imageCount; j++) {
            String f = p.get("img_" + j);
            if (f == null || !IMG_FILE.matcher(f).matches() || !Files.isRegularFile(tmpDir.resolve(f))) {
                continue;
            }
            String kind = p.getOrDefault("imgKind_" + j, "BW");
            rankOf.put(f, "COLOR".equals(kind) ? 0 : "TOC".equals(kind) ? 1 : 2);
            if (p.containsKey("imgOn_" + j)) {
                poolFiles.add(f);
            }
        }
        Pattern tokenPat = Pattern.compile("\\[\\[IMG:(p\\d{3,4}\\.jpg)\\]\\]");
        java.util.Set<String> toUpload = new java.util.LinkedHashSet<>(poolFiles);
        for (String t : texts) {
            java.util.regex.Matcher m = tokenPat.matcher(t);
            while (m.find()) {
                if (Files.isRegularFile(tmpDir.resolve(m.group(1)))) {
                    toUpload.add(m.group(1));
                }
            }
        }

        // Tóm tắt lấy từ PDF -> ghi vào tóm tắt của tập (để trống thì giữ nguyên)
        String summary = p.getOrDefault("summary", "").strip();
        if (!summary.isEmpty()) {
            volume.setSummary(summary);
            volumeRepository.save(volume);
        }

        if (titles.isEmpty() && poolFiles.isEmpty()) {
            deleteQuietly(tmpDir);
            return "redirect:" + editPath(volume) + "?importError=2";
        }

        // 3. Upload mỗi ảnh một lần (song song)
        List<String> fileList = new ArrayList<>(toUpload);
        Map<String, String> urlOf = new HashMap<>();
        int failed = 0;
        ExecutorService exec = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String f : fileList) {
                Path file = tmpDir.resolve(f);
                futures.add(exec.submit(() ->
                        r2StorageService.uploadBytes(Files.readAllBytes(file), "image/jpeg", "chapters/v" + volumeId)));
            }
            for (int j = 0; j < fileList.size(); j++) {
                String url = null;
                try {
                    url = futures.get(j).get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    log.error("Upload ảnh PDF lỗi", e);
                }
                if (url == null) {
                    failed++;
                } else {
                    urlOf.put(fileList.get(j), url);
                }
            }
        } finally {
            exec.shutdown();
        }
        int ok = urlOf.size();

        // 4. Tạo chương. Token ảnh trong text -> [img]link[/img] (ảnh lỗi thì bỏ dòng đó)
        int order = chapterRepository.findMaxSortOrderByVolumeId(volumeId) + 1;
        Chapter pool = null;
        List<String> okPool = new ArrayList<>();
        for (String f : poolFiles) {
            if (urlOf.containsKey(f)) {
                okPool.add(f);
            }
        }
        if (!okPool.isEmpty()) {
            String poolTitle = limit(p.getOrDefault("poolTitle", "Minh họa").strip(), 250);
            pool = saveChapter(volume, poolTitle.isEmpty() ? "Minh họa" : poolTitle, "", order++);
            okPool.sort(Comparator.<String>comparingInt(f -> rankOf.getOrDefault(f, 2))
                    .thenComparingInt(f -> parseInt(f.replaceAll("\\D", ""), 0)));
            int ord = 0;
            for (String f : okPool) {
                chapterImageRepository.save(new ChapterImage(pool, urlOf.get(f), ord++));
            }
        }
        int createdCount = 0;
        for (int k = 0; k < titles.size(); k++) {
            java.util.regex.Matcher m = tokenPat.matcher(texts.get(k));
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String u = urlOf.get(m.group(1));
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(u == null ? "" : "[img]" + u + "[/img]"));
            }
            m.appendTail(sb);
            String body = sb.toString().replaceAll("\\n{3,}", "\n\n").strip();
            saveChapter(volume, titles.get(k), body, order++);
            createdCount++;
        }

        productRepository.touch(productId, LocalDateTime.now());
        deleteQuietly(tmpDir);

        return "redirect:" + editPath(volume) + "?imported=" + createdCount
                + "&images=" + ok + "&failed=" + failed;
    }

    // ---------- helpers ----------

    /** Link đẹp của trang chỉnh sửa truyện chứa tập này. */
    private static String editPath(Volume volume) {
        return volume.getProduct().getPath() + "/edit";
    }

    private Volume loadManaged(int volumeId, HttpSession session) {
        User currentUser = (User) session.getAttribute("loggedInUser");
        Volume volume = volumeRepository.findByIdWithProductUploader(volumeId).orElse(null);
        if (currentUser == null || volume == null || volume.getProduct() == null
                || volume.getProduct().getUploader() == null
                || currentUser.getUserId() != volume.getProduct().getUploader().getUserId()) {
            return null;
        }
        return volume;
    }

    private void fillFormModel(Model model, Volume volume, String pattern, String error) {
        model.addAttribute("volume", volume);
        model.addAttribute("product", volume.getProduct());
        model.addAttribute("pattern", pattern);
        model.addAttribute("error", error);
    }

    private Chapter saveChapter(Volume volume, String title, String text, int order) {
        Chapter ch = new Chapter();
        ch.setVolume(volume);
        ch.setTitle(title);
        ch.setContext(text);
        ch.setDeleted(false);
        ch.setSortOrder(order);
        return chapterRepository.save(ch);
    }

    private Path tmpRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve("tmp");
    }

    /** Xóa thư mục tạm cũ hơn 6 giờ (người dùng xem trước rồi bỏ đi). */
    private void cleanupOldTmp() {
        Path root = tmpRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 6L * 3600 * 1000;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path dir : ds) {
                if (Files.getLastModifiedTime(dir).toMillis() < cutoff) {
                    deleteQuietly(dir);
                }
            }
        } catch (IOException e) {
            log.warn("Dọn thư mục tạm lỗi: {}", e.toString());
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            deleteRecursively(dir);
        } catch (IOException e) {
            log.warn("Xóa thư mục tạm lỗi: {}", e.toString());
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}