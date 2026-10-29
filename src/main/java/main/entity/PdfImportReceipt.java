package main.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Dấu xác nhận bền vững, lưu cùng giao dịch với các chương để chống nhập trùng. */
@Entity
@Table(name = "pdf_import_receipts")
public class PdfImportReceipt {
    @Id
    @Column(length = 36)
    private String token;
    @Column(name = "user_id", nullable = false)
    private int userId;
    @Column(name = "volume_id", nullable = false)
    private int volumeId;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
    protected PdfImportReceipt() {}
}
