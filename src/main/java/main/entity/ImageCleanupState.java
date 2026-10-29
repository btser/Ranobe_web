package main.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Con trỏ quét bền vững để khởi động lại vẫn tiếp tục được. */
@Entity
@Table(name = "image_cleanup_state")
public class ImageCleanupState {
    @Id
    private int id;
    @Column(name = "prefix_index", nullable = false)
    private int prefixIndex;
    @Column(name = "continuation_token", columnDefinition = "TEXT")
    private String continuationToken;
    @Column(name = "next_scan_at", nullable = false)
    private Instant nextScanAt;
    protected ImageCleanupState() {}
}
