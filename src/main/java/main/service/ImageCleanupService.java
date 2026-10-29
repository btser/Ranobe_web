package main.service;

import main.config.ImageCleanupConfig.Settings;
import main.config.SiteAssetsAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);
    private static final List<String> PREFIXES = List.of("chapters/", "covers/", "avatars/", "user-covers/");
    // Chỉ ảnh có tên do ứng dụng sinh. Không động đến logo, ảnh tải thủ công, tệp khác trong kho.
    private static final Pattern MANAGED = Pattern.compile(
            "(?:chapters/(?:p\\d+|v\\d+|\\d+)|covers|avatars|user-covers)/"
            + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|jpeg|png|gif|webp)");
    private final R2StorageService storage;
    private final ImageCleanupStore store;
    private final Settings settings;
    private final Clock clock;
    private final List<String> protectedUrls;

    @Autowired
    public ImageCleanupService(R2StorageService storage, ImageCleanupStore store, Settings settings, SiteAssetsAdvice assets) {
        this(storage, store, settings, Clock.systemUTC(), List.of(assets.siteLogoSmall(), assets.siteLogoBig(), assets.siteDefaultCover()));
    }

    ImageCleanupService(R2StorageService storage, ImageCleanupStore store, Settings settings, Clock clock, List<String> protectedUrls) {
        this.storage = storage;
        this.store = store;
        this.settings = settings;
        this.clock = clock;
        this.protectedUrls = List.copyOf(protectedUrls);
    }

    static boolean managed(String key) { return key != null && MANAGED.matcher(key).matches(); }

    @Scheduled(initialDelayString = "${image-cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${image-cleanup.tick-ms:60000}", scheduler = "imageCleanupScheduler")
    public void tick() {
        if (!settings.isEnabled()) return;
        try {
            settings.validate();
            store.withWorkerLock(this::runBatch);
        } catch (Exception e) {
            log.warn("Dọn ảnh tạm dừng; sẽ thử lại ở đợt sau: {}", e.getClass().getSimpleName());
        }
    }

    private void runBatch() {
        Instant now = clock.instant();
        for (var candidate : store.due(now.minus(settings.getGracePeriod()), settings.getDeleteBatchSize())) {
            try {
                recheckAndDelete(candidate, now);
            } catch (Exception e) {
                // Giữ lại bản ghi, không coi lỗi mạng/quyền R2/lỗi DB là xóa thành công.
                log.warn("Chưa dọn được ảnh {}; sẽ thử lại: {}", candidate.key(), e.getClass().getSimpleName());
                store.defer(candidate.key(), now.plusSeconds(300));
            }
        }
        var cursor = store.cursor();
        if (cursor.nextScanAt().isAfter(now)) return;
        var page = storage.listImages(PREFIXES.get(cursor.prefixIndex()), cursor.token(), settings.getScanPageSize());
        for (var image : page.images()) {
            if (!eligible(image.key())) continue;
            if (store.referenced(image.key())) store.forget(image.key());
            else store.mark(image, now);
        }
        if (page.nextToken() != null) {
            store.advance(new ImageCleanupStore.Cursor(cursor.prefixIndex(), page.nextToken(), now));
        } else {
            int next = (cursor.prefixIndex() + 1) % PREFIXES.size();
            store.advance(new ImageCleanupStore.Cursor(next, null, next == 0 ? now.plus(settings.getScanInterval()) : now));
        }
        log.info("Dọn ảnh: đã kiểm tra {} tệp; chế độ {}", page.images().size(), settings.isDryRun() ? "chỉ báo cáo" : "tự động");
    }

    private void recheckAndDelete(ImageCleanupStore.Candidate candidate, Instant now) {
        if (!eligible(candidate.key())) { store.forget(candidate.key()); return; }
        if (store.referenced(candidate.key())) { store.forget(candidate.key()); return; }
        var current = storage.findImage(candidate.key());
        if (current == null) { store.forget(candidate.key()); return; }
        if (!current.etag().equals(candidate.etag()) || !current.modifiedAt().equals(candidate.modifiedAt())) {
            store.mark(current, now);
            return;
        }
        if (settings.isDryRun()) {
            log.info("Dọn ảnh: sẽ xóa ảnh không được dùng {}", candidate.key());
            // Cho các ảnh khác lượt kiểm tra, không lặp mãi cùng một nhóm trong chế độ báo cáo.
            store.forget(candidate.key());
            return;
        }
        store.duringDeletion(() -> {
            // Kiểm tra lần cuối dưới khóa DB, sau khi đã đọc thông tin R2 ngoài khóa.
            if (!store.referenced(candidate.key())) {
                storage.deleteUnusedImage(candidate.key());
                log.info("Dọn ảnh: đã xóa ảnh không được dùng {}", candidate.key());
            }
            store.forget(candidate.key());
            return null;
        });
    }

    private boolean eligible(String key) {
        return managed(key) && protectedUrls.stream().noneMatch(url -> url.contains(key));
    }
}
