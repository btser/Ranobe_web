package main.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Service
public class ImageCleanupStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate workerTransaction;
    private final TransactionTemplate deletionTransaction;

    public ImageCleanupStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        workerTransaction = new TransactionTemplate(manager);
        deletionTransaction = new TransactionTemplate(manager);
        deletionTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Cursor(int prefixIndex, String token, Instant nextScanAt) {}
    public record Candidate(String key, String etag, Instant modifiedAt, Instant since) {}

    public void withWorkerLock(Runnable action) {
        workerTransaction.executeWithoutResult(status -> {
            // Khóa riêng của tác vụ, không khóa yêu cầu đọc trang; tự nhả khi lỗi/khởi động lại.
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(7262667301)", Boolean.class))) {
                action.run();
            }
        });
    }

    public Cursor cursor() {
        jdbc.update("INSERT INTO image_cleanup_state(id,prefix_index,next_scan_at) VALUES (1,0,?) ON CONFLICT DO NOTHING",
                Timestamp.from(Instant.EPOCH));
        return jdbc.queryForObject("SELECT prefix_index,continuation_token,next_scan_at FROM image_cleanup_state WHERE id=1",
                (r, n) -> new Cursor(r.getInt(1), r.getString(2), r.getTimestamp(3).toInstant()));
    }

    public void advance(Cursor cursor) {
        jdbc.update("UPDATE image_cleanup_state SET prefix_index=?,continuation_token=?,next_scan_at=? WHERE id=1",
                cursor.prefixIndex(), cursor.token(), Timestamp.from(cursor.nextScanAt()));
    }

    public List<Candidate> due(Instant cutoff, int limit) {
        return jdbc.query("SELECT object_key,object_etag,object_modified_at,unreferenced_since FROM image_cleanup_candidates "
                        + "WHERE unreferenced_since <= ? AND (retry_at IS NULL OR retry_at <= CURRENT_TIMESTAMP) "
                        + "ORDER BY unreferenced_since,object_key LIMIT ?",
                (r, n) -> new Candidate(r.getString(1), r.getString(2), r.getTimestamp(3).toInstant(), r.getTimestamp(4).toInstant()),
                Timestamp.from(cutoff), limit);
    }

    public void mark(R2StorageService.StoredImage image, Instant now) {
        jdbc.update("""
                INSERT INTO image_cleanup_candidates(object_key,object_etag,object_modified_at,unreferenced_since)
                VALUES (?,?,?,?) ON CONFLICT (object_key) DO UPDATE SET
                unreferenced_since = CASE WHEN image_cleanup_candidates.object_etag = EXCLUDED.object_etag
                    AND image_cleanup_candidates.object_modified_at = EXCLUDED.object_modified_at
                    THEN image_cleanup_candidates.unreferenced_since ELSE EXCLUDED.unreferenced_since END,
                object_etag=EXCLUDED.object_etag, object_modified_at=EXCLUDED.object_modified_at
                """, image.key(), image.etag(), Timestamp.from(image.modifiedAt()), Timestamp.from(now));
    }

    public void forget(String key) {
        jdbc.update("DELETE FROM image_cleanup_candidates WHERE object_key=?", key);
    }

    public void defer(String key, Instant retryAt) {
        jdbc.update("UPDATE image_cleanup_candidates SET retry_at=? WHERE object_key=?", Timestamp.from(retryAt), key);
    }

    public boolean referenced(String key) {
        // Truy vấn trực tiếp để tính cả nội dung riêng tư, bị ẩn và tài khoản bị khóa.
        // Dùng khóa ảnh thay vì tên miền để bảo vệ cả liên kết dùng tên miền cũ.
        // POSITION so khớp chuỗi trực tiếp, không diễn giải % và _ thành ký tự đại diện.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM products WHERE POSITION(? IN COALESCE(cover_image,''))>0 OR POSITION(? IN COALESCE(summary,''))>0)
                OR EXISTS(SELECT 1 FROM volumes WHERE POSITION(? IN COALESCE(cover_image,''))>0 OR POSITION(? IN COALESCE(summary,''))>0)
                OR EXISTS(SELECT 1 FROM users WHERE POSITION(? IN COALESCE(avatar_url,''))>0 OR POSITION(? IN COALESCE(cover_url,''))>0
                    OR POSITION(? IN COALESCE(introduce,''))>0 OR POSITION(? IN COALESCE(hobby,''))>0)
                OR EXISTS(SELECT 1 FROM chapters WHERE POSITION(? IN COALESCE(content,''))>0)
                OR EXISTS(SELECT 1 FROM chapter_images WHERE POSITION(? IN COALESCE(image_url,''))>0)
                """, Boolean.class, key, key, key, key, key, key, key, key, key, key));
    }

    public <T> T duringDeletion(Supplier<T> action) {
        return deletionTransaction.execute(status -> {
            // Không đua với giao dịch đang lưu chương/đổi bìa. Nếu bảng bận thì bỏ qua đợt này.
            jdbc.execute("SET LOCAL lock_timeout = '100ms'");
            jdbc.execute("LOCK TABLE products, volumes, users, chapters, chapter_images IN SHARE MODE");
            return action.get();
        });
    }
}
