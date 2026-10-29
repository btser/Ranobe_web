package main.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Chỉ lưu bằng chứng ảnh không được dùng; không lưu ảnh hay bản nháp. */
@Entity
@Table(name = "image_cleanup_candidates", indexes = @Index(name = "idx_image_cleanup_due", columnList = "unreferenced_since"))
public class ImageCleanupCandidate {
    @Id
    @Column(name = "object_key", length = 255)
    private String objectKey;
    @Column(name = "object_etag", nullable = false)
    private String objectEtag;
    @Column(name = "object_modified_at", nullable = false)
    private Instant objectModifiedAt;
    @Column(name = "unreferenced_since", nullable = false)
    private Instant unreferencedSince;
    @Column(name = "retry_at")
    private Instant retryAt;
    protected ImageCleanupCandidate() {}
}
