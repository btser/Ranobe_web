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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PdfImportController(VolumeRepository volumeRepository,
                               ChapterRepository chapterRepository,
                               ChapterImageRepository chapterImageRepository,
                               ProductRepository productRepository,
                               R2StorageService r2StorageService, JdbcTemplate jdbc,
                               PlatformTransactionManager transactionManager) {
        this.volumeRepository = volumeRepository;
        this.chapterRepository = chapterRepository;
        this.chapterImageRepository = chapterImageRepository;
        this.productRepository = productRepository;
        this.r2StorageService = r2StorageService;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
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

            registerJob(token, session, volumeId);
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
                         HttpSession session, Model model) {

        Volume volume = loadManaged(volumeId, session);
        if (volume == null) {
            return "redirect:/";
        }
        String token = p.get("token");
        if (token == null || !TOKEN.matcher(token).matches()) {
            return "redirect:" + editPath(volume) + "?importError=1";
        }
        try {
            if (completed(token, sessionUserId(session), volumeId)) {
                return "redirect:" + editPath(volume) + "?importedAlready=1";
            }
        } catch (Exception e) {
            return retryPreview(model, volume, token, p, "Không kiểm tra được trạng thái nhập. Chưa gửi lại dữ liệu; hãy thử lại.");
        }
        ImportJob job = JOBS.get(token);
        if (job == null || job.userId != sessionUserId(session) || job.volumeId != volumeId
                || job.expiresAt < System.currentTimeMillis()) {
            fillFormModel(model, volume, PdfAnalyzer.DEFAULT_PATTERN,
                    "Bản xem trước đã hết hạn hoặc máy chủ đã khởi động lại. Hãy chọn lại PDF. Nếu lần trước đã lưu thành công, không nhập lại bản mới.");
            return "volume-import";
        }
        if (!job.lock.tryLock()) {
            return retryPreview(model, volume, token, p, "Lượt nhập này đang được xử lý. Hãy chờ rồi thử lại, không chọn lại PDF.");
        }
        try {
            // Giữ mã xác nhận và tệp tạm khi thất bại. Chỉ hoàn tất sau giao dịch DB thành công.
            return commitLocked(volume, token, p, session, model, job);
        } catch (Exception e) {
            log.error("Lưu lượt nhập PDF {} thất bại", token, e);
            return retryPreview(model, volume, token, p,
                    "Không lưu được lượt nhập. Các thay đổi trong cơ sở dữ liệu đã được hủy nếu giao dịch chưa hoàn tất. "
                    + "Bấm lưu lại để kiểm tra và thử lại bằng cùng bản xem trước; hệ thống không tạo trùng nếu lượt trước đã hoàn tất.");
        } finally {
            job.expiresAt = System.currentTimeMillis() + JOB_TTL_MS;
            job.lock.unlock();
        }
    }

    private String commitLocked(Volume volume, String token, Map<String, String> p,
                                HttpSession session, Model model, ImportJob job) throws IOException {
        int volumeId = volume.getVolumeId();
        int productId = volume.getProduct().getProductId();
        if (completed(token, sessionUserId(session), volumeId)) {
            return "redirect:" + editPath(volume) + "?importedAlready=1";
        }
        Path tmpDir = tmpRoot().resolve(token).normalize();

        // 1. Gộp / bỏ chương theo lựa chọn
        int chapterCount = clamp(parseInt(p.get("chapterCount"), 0), 0, MAX_CHAPTERS);
        List<String> titles = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < chapterCount; i++) {
            if (p.containsKey("skip_" + i)) {
                continue;
            }
            String title = p.getOrDefault("title_" + i, "").strip();
            String text = p.getOrDefault("text_" + i, "").strip();
            if (p.containsKey("merge_" + i) && !titles.isEmpty()) {
                int last = titles.size() - 1;
                texts.set(last, texts.get(last) + "\n\n" + text);
            } else {
                titles.add(title.isEmpty() ? "Chương " + (titles.size() + 1) : limit(title, 250));
                texts.add(text);
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
            if (f == null || !IMG_FILE.matcher(f).matches()) {
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
                toUpload.add(m.group(1));
            }
        }

        // Tóm tắt lấy từ PDF -> ghi vào tóm tắt của tập (để trống thì giữ nguyên)
        String summary = p.getOrDefault("summary", "").strip();
        if (titles.isEmpty() && poolFiles.isEmpty()) {
            return retryPreview(model, volume, token, p, "Chưa chọn chương hoặc ảnh nào để nhập.");
        }

        // 3. Upload mỗi ảnh một lần (song song)
        List<String> fileList = new ArrayList<>(toUpload);
        Map<String, String> urlOf = job.uploaded;
        Map<String, String> previouslyUploaded = Map.copyOf(urlOf);
        List<String> failures = new ArrayList<>();
        ExecutorService exec = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String f : fileList) {
                Path file = tmpDir.resolve(f);
                futures.add(exec.submit(() ->
                        previouslyUploaded.containsKey(f) ? previouslyUploaded.get(f) :
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
                    failures.add(fileList.get(j));
                } else {
                    urlOf.put(fileList.get(j), url);
                }
            }
        } finally {
            exec.shutdown();
        }
        if (!failures.isEmpty()) {
            return retryPreview(model, volume, token, p,
                    "Chưa lưu chương nào: không tải được ảnh " + String.join(", ", failures)
                    + ". Hãy bấm lưu để thử lại; ảnh đã tải thành công sẽ được dùng lại.");
        }
        int ok = fileList.size();

        // 4. Lưu tất cả trong một giao dịch. Khóa tập để hai lượt nhập không lấy trùng thứ tự.
        Integer savedCount = transaction.execute(status -> {
        jdbc.queryForObject("SELECT volume_id FROM volumes WHERE volume_id=? FOR UPDATE", Integer.class, volumeId);
        if (completed(token, sessionUserId(session), volumeId)) return -1;
        if (!summary.isEmpty()) {
            volume.setSummary(summary);
            volumeRepository.save(volume);
        }
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
                if (u == null) throw new IllegalStateException("Thiếu ảnh " + m.group(1));
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("[img]" + u + "[/img]"));
            }
            m.appendTail(sb);
            String body = sb.toString().replaceAll("\\n{3,}", "\n\n").strip();
            saveChapter(volume, titles.get(k), body, order++);
            createdCount++;
        }

        productRepository.touch(productId, LocalDateTime.now());
        jdbc.update("INSERT INTO pdf_import_receipts(token,user_id,volume_id,completed_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",
                token, sessionUserId(session), volumeId);
        return createdCount;
        });
        deleteQuietly(tmpDir);
        JOBS.remove(token, job);

        return "redirect:" + editPath(volume) + (savedCount == -1 ? "?importedAlready=1" : "?imported=" + savedCount
                + "&images=" + ok + "&failed=0");
    }

    // ---------- helpers ----------

    private static final long JOB_TTL_MS = 2 * 60 * 60 * 1000L;
    private static final java.util.concurrent.ConcurrentHashMap<String, ImportJob> JOBS = new java.util.concurrent.ConcurrentHashMap<>();
    private static class ImportJob {
        final int userId;
        final int volumeId;
        volatile long expiresAt;
        final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        final Map<String, String> uploaded = new HashMap<>();
        ImportJob(int userId, int volumeId, long expiresAt) {
            this.userId = userId; this.volumeId = volumeId; this.expiresAt = expiresAt;
        }
    }

    private static int sessionUserId(HttpSession session) {
        User u = (User) session.getAttribute("loggedInUser");
        return u == null ? -1 : u.getUserId();
    }

    /** Ghi nhận lượt phân tích: token -> {userId, volumeId, hạn}. */
    private static void registerJob(String token, HttpSession session, int volumeId) {
        long now = System.currentTimeMillis();
        JOBS.values().removeIf(j -> j.expiresAt < now && !j.lock.isLocked());
        JOBS.put(token, new ImportJob(sessionUserId(session), volumeId, now + JOB_TTL_MS));
    }

    private boolean completed(String token, int userId, int volumeId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pdf_import_receipts WHERE token=? AND user_id=? AND volume_id=?)",
                Boolean.class, token, userId, volumeId));
    }

    private String retryPreview(Model model, Volume volume, String token, Map<String, String> p, String error) {
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (int i = 0; i < clamp(parseInt(p.get("chapterCount"), 0), 0, MAX_CHAPTERS); i++) {
            chapters.add(Map.of("title", p.getOrDefault("title_" + i, ""), "text", p.getOrDefault("text_" + i, ""),
                    "skipDefault", p.containsKey("skip_" + i), "startPage", "—", "length", p.getOrDefault("text_" + i, "").length()));
        }
        List<Map<String, Object>> images = new ArrayList<>();
        for (int i = 0; i < clamp(parseInt(p.get("imageCount"), 0), 0, MAX_IMAGES); i++) {
            String file = p.getOrDefault("img_" + i, "");
            if (IMG_FILE.matcher(file).matches()) images.add(Map.of("file", file,
                    "chapterIndex", p.getOrDefault("imgCh_" + i, "-1"), "kindName", p.getOrDefault("imgKind_" + i, "BW"),
                    "selected", p.containsKey("imgOn_" + i)));
        }
        model.addAttribute("volume", volume);
        model.addAttribute("product", volume.getProduct());
        model.addAttribute("token", token);
        model.addAttribute("chapters", chapters);
        model.addAttribute("images", images);
        model.addAttribute("pageCount", "—");
        model.addAttribute("summary", p.getOrDefault("summary", ""));
        model.addAttribute("retryValues", p);
        model.addAttribute("importFailure", error);
        return "volume-import-preview";
    }

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
