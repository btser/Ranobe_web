# Task Completion Reports

# Báo cáo: tác vụ dọn ảnh không còn được dùng trên Cloudflare R2

## Cập nhật 29/10/2026: nhập PDF lỗi có thể thử lại

- Tải ảnh lên R2 lỗi: báo tên ảnh lỗi ngay trên bản xem trước, không lưu các chương thiếu ảnh. Bấm **Thử lưu lại** dùng lại ảnh đã tải thành công trong cùng lượt.
- Lưu các chương, ảnh minh họa, tóm tắt tập và thời điểm cập nhật truyện trong một giao dịch cơ sở dữ liệu: một bước lỗi thì hủy các thay đổi của lượt đó.
- Giữ nội dung chỉnh sửa, lựa chọn bỏ/gộp chương, ảnh và tên chương minh họa trên trang báo lỗi.
- Thêm bảng `pdf_import_receipts` ghi nhận lượt nhập thành công trong cùng giao dịch với chương. Gửi lại cùng mã sau khi lưu thành công sẽ chỉ báo đã nhập, không tạo trùng. Khóa tập khi lưu để các lượt nhập đồng thời không lấy trùng thứ tự chương.
- Mã nhập không bị tiêu thụ trước khi lưu; người khác không thể lấy/hủy mã của chủ lượt nhập. Bấm lưu đồng thời cho cùng lượt được chặn.
- Chỉ xóa thư mục xem trước khi xác nhận giao dịch thành công. Ảnh đã lên R2 nhưng lượt nhập bị bỏ được tác vụ dọn ảnh xử lý về sau.
- Thời hạn bản xem trước vẫn là **2 giờ**, được gia hạn khi thực hiện một lần lưu/thử lại. Nội dung xem trước, mã đang xử lý và bộ nhớ ảnh đã tải vẫn nằm trên máy chủ; nếu ứng dụng khởi động lại/ngủ mất bộ nhớ, cần chọn lại PDF cho lượt chưa hoàn tất. Bảng xác nhận thành công vẫn giữ để ngăn gửi lại **cùng mã** tạo trùng. Chọn PDF và tạo một lượt hoàn toàn mới chưa có cơ chế nhận diện trùng nội dung.
- Cấu hình Hibernate hiện tại tự tạo bảng mới khi ứng dụng chạy; chưa chạy thay đổi dữ liệu hay tải ảnh thật để kiểm thử chức năng này.
- Các file liên quan: `PdfImportController.java`, `PdfImportReceipt.java`, `volume-import-preview.html`, `product-edit.html`, `PdfImportControllerTest.java`, và bổ sung kiểm tra dựng trang xem trước trong `UiTemplateTest.java`.
- Kiểm thử dùng mô phỏng giao dịch và kho R2; xác minh hệ thống yêu cầu hủy giao dịch khi lỗi, chưa thử gây lỗi trên cơ sở dữ liệu/R2 thật.
- Kết quả cập nhật: **37 kiểm thử đạt**, gồm 7 tình huống nhập PDF mới; dựng trang ban đầu và trang thử lại thành công. Kiểm tra khoảng trắng các file thay đổi đạt.

Ngày thực hiện: 28/10/2026.

## Đã làm

- Thêm tác vụ nền trong ứng dụng Spring Boot, không cần thuê máy riêng và không chạy trong yêu cầu đọc truyện/tìm kiếm.
- Quét ảnh trên R2 theo từng trang nhỏ; lưu vị trí quét và danh sách ảnh nghi không còn được dùng vào PostgreSQL.
- Khi ứng dụng khởi động lại, hoặc thức sau một thời gian ngủ, tác vụ tiếp tục từ vị trí đã lưu và xử lý các ảnh đã đến hạn. Không chạy bù hàng loạt tất cả các lần bị lỡ.
- Trước khi xóa, kiểm tra lại dữ liệu đang lưu. Nếu cơ sở dữ liệu hoặc R2 lỗi, không mặc định xem ảnh là ảnh dư.
- Khi đổi/xóa bìa truyện, bìa tập, ảnh đại diện hoặc bìa hồ sơ: bỏ liên kết như cũ nhưng không xóa tệp R2 ngay. Tác vụ nền quyết định sau khi kiểm tra tất cả nơi dùng ảnh.
- Không thay đổi cách nhập PDF: ảnh xem trước vẫn ở thư mục tạm, chỉ tải lên R2 khi lưu. Không thay đổi cách lưu bản nháp trên trình duyệt.

## Quy tắc xóa

1. Chỉ xét ảnh có đường dẫn do ứng dụng sinh, tên tệp dạng mã ngẫu nhiên: `chapters/p{mã truyện}/…`, `chapters/v{mã tập}/…`, `chapters/{mã chương}/…`, `covers/…`, `avatars/…`, `user-covers/…`.
2. Không xóa tệp ngoài các nhóm trên, tên tệp thủ công hoặc ảnh logo/ảnh mặc định đang cấu hình cho web.
3. Nếu không tìm thấy tham chiếu, ghi nhận ảnh là ứng viên dọn. **Chờ ít nhất 24 giờ từ lúc phát hiện**, không xóa ngay trong lần quét đầu. Đây là khoảng đệm để người đang soạn có thời gian lưu, không phải cam kết giữ bản nháp.
4. Khi đến hạn, kiểm tra lại tham chiếu và thông tin tệp. Nếu ảnh đã được dùng thì bỏ khỏi danh sách dọn; nếu tệp thay đổi thì bắt đầu lại khoảng đệm.
5. Chỉ xóa tệp sau khi các kiểm tra thành công. Lỗi xóa giữ nguyên ứng viên và chờ 5 phút trước khi thử lại, để một ảnh lỗi không chặn lượt của các ảnh khác.

Ảnh chỉ có trong bản nháp trên trình duyệt không được bảo vệ lâu dài, theo lựa chọn của bạn. Sau khi ảnh bị dọn, bản nháp vẫn còn chữ nhưng ảnh đó sẽ không tải được và phải chèn lại. Khôi phục một bản nháp cũ không tự gia hạn thời gian giữ ảnh.

## Những nơi được kiểm tra tham chiếu

- Bìa và phần tóm tắt truyện.
- Bìa và phần tóm tắt tập.
- Ảnh đại diện, bìa hồ sơ, giới thiệu và sở thích của người dùng.
- Nội dung chương: cả mã HTML và ảnh dạng `[img]…[/img]`.
- Danh sách ảnh minh họa riêng trong bảng `chapter_images`.

Truy vấn trực tiếp cả bản ghi đang ẩn/riêng tư và hồ sơ bị khóa, không đi qua bộ lọc ẩn chương. **Ẩn truyện/tập/chương hoặc khóa tài khoản không làm ảnh của chúng bị xóa.** Tìm theo khóa tệp trong liên kết để nhận ra cả liên kết dùng tên miền cũ. Nếu một ảnh còn được hai chương dùng, xóa liên kết ở một chương chưa đủ để xóa tệp.

Chỉ biết được tham chiếu trong cơ sở dữ liệu này và ba ảnh hệ thống đang cấu hình. Không biết các liên kết trên web khác, bản xuất cũ, nội dung chỉ ở trình duyệt hoặc ứng dụng khác. **Kho R2 phải dành riêng cho dự án này**, hoặc các ứng dụng khác không được dùng chung các nhóm đường dẫn do tool quản lý. Khi thêm một nơi lưu ảnh mới trong cơ sở dữ liệu, phải cập nhật bộ kiểm tra trước khi dùng chức năng đó.

## Lịch chạy và ảnh hưởng hiệu năng

- Mặc định bắt đầu sau 60 giây từ lúc ứng dụng chạy; chờ 60 giây giữa hai đợt.
- Mỗi đợt quét tối đa 100 tệp, xử lý tối đa 10 ứng viên đến hạn.
- Sau khi quét hết bốn nhóm thư mục, chờ 24 giờ trước vòng quét tiếp theo. Quét một kho lớn sẽ cần nhiều đợt.
- Ghi nhận thời gian và vị trí trong cơ sở dữ liệu, không phụ thuộc ổ đĩa tạm của Render.
- Dùng khóa tác vụ tại PostgreSQL để nhiều bản ứng dụng dùng cùng cơ sở dữ liệu không dọn cùng lúc.
- Không thêm truy vấn vào mỗi lần đọc/tìm kiếm. Tác vụ nền vẫn tiêu thụ tài nguyên và số lượt gọi R2; không phải hoàn toàn miễn phí về tài nguyên.
- Ngay trước khi xóa, giữ khóa chia sẻ trên năm bảng chứa tham chiếu để không đua với giao dịch đang ghi. Nếu bảng đang bận, chờ tối đa 100 mili giây để lấy khóa rồi bỏ qua ảnh đó và thử lại sau. Các truy vấn đọc vẫn chạy được; thao tác ghi đến trong lúc xóa có thể chờ ngắn, tối đa khoảng thời gian gọi xóa R2 (giới hạn 5 giây, chưa tính chi phí xử lý cơ sở dữ liệu).
- Yêu cầu đọc thông tin tệp được thực hiện ngoài khóa bảng, giới hạn 5 giây. Liệt kê tệp giới hạn 10 giây.
- Đây không phải cơ chế ngăn người dùng dán lại một liên kết của ảnh đã bị xóa. Với bản nháp đã hết thời gian giữ ảnh, cần tải lại ảnh.

## Các phần thêm/sửa trong dự án

| File | Vai trò |
|---|---|
| `src/main/java/main/config/ImageCleanupConfig.java` | Lịch chạy riêng và giá trị cấu hình mặc định |
| `src/main/java/main/entity/ImageCleanupCandidate.java` | Bảng `image_cleanup_candidates`, ghi ảnh chờ dọn |
| `src/main/java/main/entity/ImageCleanupState.java` | Bảng `image_cleanup_state`, ghi vị trí và lịch quét |
| `src/main/java/main/service/ImageCleanupStore.java` | Kiểm tra tham chiếu, lưu tiến độ, khóa khi dọn |
| `src/main/java/main/service/ImageCleanupService.java` | Quét, chờ khoảng đệm, kiểm tra lại và dọn |
| `src/main/java/main/service/R2StorageService.java` | Thêm liệt kê/đọc thông tin/xóa có kiểm soát; bỏ xóa ảnh cũ tức thì |
| `src/test/java/main/service/ImageCleanupServiceTest.java` | Kiểm tra các tình huống an toàn và lỗi bằng kho giả lập |
| `src/test/java/main/service/ImageCleanupStoreTest.java` | Kiểm tra phạm vi truy vấn tham chiếu |

Không sửa `pom.xml`, `.gitignore`, `plan.md`, file dữ liệu SQL cũ hoặc tạo lại `docs/`, `de-xuat/`. Hai bảng mới được Hibernate tạo khi chạy ứng dụng với cấu hình hiện có `spring.jpa.hibernate.ddl-auto=update`. Nếu nơi triển khai tắt tự cập nhật cấu trúc, cần tạo hai bảng trước khi bật tác vụ. Không cần cấp quyền thay đổi cấu trúc cho từng lần dọn.

`.gitignore` hiện đã bỏ qua `bao_cao.md`, nên báo cáo này đọc được trên máy nhưng không tự xuất hiện trong lần đưa mã lên GitHub. Không thay đổi quy tắc đó.

## Cấu hình

Các giá trị mặc định nằm trong mã; không cần thêm dòng để bật. Có thể đặt các thuộc tính sau trong cấu hình ứng dụng:

```properties
# Tắt toàn bộ tác vụ
image-cleanup.enabled=false

# Hoặc bật nhưng chỉ ghi nhật ký ảnh đủ điều kiện, không xóa trên R2
image-cleanup.enabled=true
image-cleanup.dry-run=true
```

Để tự xóa: `image-cleanup.enabled=true`, `image-cleanup.dry-run=false` (mặc định).

Các giới hạn khác:

```properties
image-cleanup.grace-period=24h
image-cleanup.scan-interval=24h
image-cleanup.scan-page-size=100
image-cleanup.delete-batch-size=10
image-cleanup.initial-delay-ms=60000
image-cleanup.tick-ms=60000
```

Khoảng đệm không được đặt thấp hơn 1 giờ; kích thước quét 1–100, số ảnh xóa 1–10. Chế độ chỉ báo cáo vẫn ghi tiến độ/ứng viên trong hai bảng riêng, không sửa nội dung truyện. Khi một ứng viên được báo cáo, bản ghi ứng viên được bỏ để tránh lặp mãi cùng một nhóm; vòng quét sau có thể ghi nhận lại. Vì vậy đổi sang xóa thật có thể phải chờ vòng quét và khoảng đệm mới.

Khóa truy cập R2 cần có quyền liệt kê, đọc thông tin và xóa trong kho hiện tại. Không đưa khóa bí mật vào báo cáo hoặc kho mã công khai. Các ảnh chưa xóa có thể vẫn hiện từ bộ nhớ đệm sau khi tệp gốc bị xóa; tool không xóa bộ nhớ đệm của trình duyệt hoặc Cloudflare.

## Kiểm tra và giới hạn xác minh

- Các kiểm thử tự động dùng kho R2 giả lập, không tải lên hay xóa ảnh thật.
- Đã kiểm tra các nhánh: chờ khoảng đệm, ảnh còn dùng, ảnh được gắn vào chương ngay trước bước xóa, tệp bị thay đổi, lỗi cơ sở dữ liệu/R2, tiếp tục con trỏ sau khi thức, phân trang, chế độ chỉ báo cáo, tắt tác vụ và bảo vệ ảnh hệ thống.
- Kết quả kiểm tra cuối: **30 kiểm thử đạt, 0 thất bại, 0 lỗi**; gồm 15 kiểm thử mới cho tool và 15 kiểm thử có sẵn. Maven biên dịch thành công với mức ngôn ngữ Java 17. Kiểm tra khoảng trắng các file thay đổi không phát hiện lỗi.
- Chưa chạy tool xóa thật trên kho R2, chưa kiểm tra quyền khóa R2 trên môi trường triển khai. Khi ứng dụng được chạy với cấu hình mặc định, tool sẽ bắt đầu quét và chỉ có thể xóa ứng viên sau khoảng đệm kể trên.
- Tệp đổi bìa/ảnh hồ sơ cũ không theo mẫu tên do ứng dụng sinh sẽ được giữ lại, cần xem xét riêng nếu muốn dọn.
- Không có thùng rác cho ảnh đã xóa trên R2. Cơ chế chờ và kiểm tra lại giúp tránh xóa nhầm, không thay thế bản sao lưu.

---

## Report 6 — Nhập PDF thất bại có thể thử lại

**Date:** 2026-10-29
**Status:** Completed
**Branch:** sub

### 1. Task Overview — Tổng quan
Hoàn tất xác minh mục 6 đã triển khai trước lượt này: thông báo lỗi, giữ bản xem trước, thử lại cùng lượt không trùng chương. Không triển khai lại mã đã có.

### 2. Implementation Summary — Nội dung triển khai
`PdfImportController.commit()` kiểm tra chủ sở hữu, mã lượt nhập và dấu hoàn tất; `commitLocked()` tải ảnh rồi lưu toàn bộ dữ liệu trong giao dịch. Trang `volume-import-preview.html` giữ lựa chọn và hiện nút thử lại.

### 3. Methods and Algorithms — Phương pháp và thuật toán
`ImportJob.lock` ngăn gửi cùng lượt đồng thời trong một tiến trình. Bản đồ `uploaded` dùng lại ảnh R2 thành công. `TransactionTemplate` hủy thay đổi chương, ảnh, tóm tắt và dấu hoàn tất khi lỗi. `pdf_import_receipts` ghi cùng giao dịch, ngăn gửi lại cùng mã tạo trùng kể cả mất phản hồi thành công. Khóa hàng tập bằng `FOR UPDATE` bảo vệ thứ tự chương. Độ phức tạp phụ thuộc tổng ký tự và số ảnh; truyền ảnh là chi phí chính, không có số đo hiệu năng thực tế.

### 4. Design Decisions — Quyết định thiết kế
Giữ triển khai đã được xác minh; không sửa quyền chủ sở hữu hay luồng tải ảnh của tác vụ khác. Không xem ảnh lỗi là ảnh có thể bỏ âm thầm.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
Biểu mẫu xem trước → kiểm tra quyền/mã → tải ảnh R2 → giao dịch lưu toàn lượt và dấu hoàn tất → chuyển trang. Lỗi trả bản xem trước, còn lượt thành công được nhận diện khi gửi lại.

### 6. Files Changed — Các file thay đổi
Lượt xác minh này chỉ cập nhật `plan.md` và phần báo cáo này trong `reports.md`. Mã và kiểm thử của mục 6 đã có trong lịch sử nhánh.

### 7. Testing and Validation — Kiểm thử
Đã chạy `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test` với Java 25, mức ngôn ngữ dự án Java 17. Kết quả: 37 kiểm thử đạt, không lỗi; gồm 7 tình huống `PdfImportControllerTest` và dựng trang xem trước/thử lại trong `UiTemplateTest`. Cơ sở dữ liệu và R2 được giả lập trong kiểm thử chức năng nhập.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
Lượt chưa hoàn tất chỉ giữ trong bộ nhớ hai giờ; khởi động lại cần chọn PDF mới. Chống trùng theo mã lượt, chưa nhận dạng cùng nội dung ở lượt hoàn toàn mới. Chưa gây lỗi trên kho R2/cơ sở dữ liệu thực để thử.

### 9. Final Result — Kết quả cuối cùng
Mục 6 đã đánh ✅ trong `plan.md` sau xác minh thành công. Kết quả đẩy mã và mã commit được báo riêng sau thao tác Git, không ghi trước vào báo cáo.

### 10. Thay đổi mã nguồn — Đối chiếu triển khai

**Trước → sau:** trước đây mã xác nhận bị lấy khỏi bộ nhớ trước khi lưu, ảnh lỗi có thể bị bỏ và chương lưu lần lượt. Hiện giữ lượt nhập khi lỗi, yêu cầu đủ ảnh được chọn rồi lưu toàn bộ dữ liệu trong một giao dịch.

| File | Hàm hoặc thành phần | Thay đổi và tác dụng |
|---|---|---|
| `src/main/java/main/controller/PdfImportController.java` | `commit()`, `commitLocked()` | Kiểm tra lượt đã hoàn tất; khóa lượt đang xử lý; chỉ dọn tệp tạm khi giao dịch thành công. Bấm lại cùng mã không tạo thêm chương. |
| Cùng file | `ImportJob`, `registerJob()` | Thay dữ liệu lượt nhập dạng mảng bằng đối tượng chứa người tạo, tập, thời hạn, khóa và bản đồ ảnh đã tải. Khi thử lại chỉ tải ảnh chưa thành công. |
| Cùng file | `completed()`, `retryPreview()` | Đọc dấu hoàn tất trong cơ sở dữ liệu; dựng lại bản xem trước từ tên/nội dung chương và lựa chọn người dùng đã gửi. |
| `src/main/java/main/entity/PdfImportReceipt.java` | `PdfImportReceipt` | Thêm bảng ghi nhận mã lượt, người thực hiện, tập và thời điểm hoàn tất. Ghi cùng giao dịch với chương. |
| `src/main/resources/templates/volume-import-preview.html` | Biểu mẫu lưu | Hiện lỗi và nút thử lại; giữ tên chương minh họa, ô chọn ảnh, lựa chọn bỏ/gộp chương; mở lại nút khi trình duyệt quay về trang. |
| `src/main/resources/templates/product-edit.html` | Thông báo `importedAlready` | Báo lượt này đã thành công, thay vì khiến người dùng tưởng cần nhập lại. |
| `src/test/java/main/controller/PdfImportControllerTest.java` và `src/test/java/main/UiTemplateTest.java` | Kiểm thử nhập/dựng trang | Bao phủ ảnh lỗi, thiếu ảnh nội tuyến, lỗi giữa các lần lưu, gửi lại lượt thành công, sai chủ sở hữu và giữ biểu mẫu. |

Mã dùng `TransactionTemplate.execute()` bao quanh việc lưu tóm tắt, chương, ảnh và dấu hoàn tất. `INSERT INTO pdf_import_receipts` nằm **trong** khối đó, còn xóa thư mục tạm nằm **sau** khi khối trả về thành công. Đây là điểm ngăn lưu nửa chừng và mất khả năng thử lại.

**Phân biệt lịch sử:** các thay đổi mã nguồn trên đã được triển khai trước commit xác minh mục 6; lượt hoàn tất kế hoạch chỉ kiểm thử lại và ghi nhận. Phần bổ sung báo cáo ngày 30/10/2026 này không sửa mã nguồn và không chạy lại kiểm thử ứng dụng.

---

## Report 8 — Tìm kiếm, lọc và phân trang

**Date:** 2026-10-29
**Status:** Completed
**Branch:** sub

### 1. Task Overview — Tổng quan
Hoàn thiện chọn nhiều thể loại và danh sách quản trị. Giữ trang chủ chỉ tìm tên truyện; giữ bộ lọc tác giả, họa sĩ, người đăng tại trang nâng cao.

### 2. Implementation Summary — Nội dung triển khai
Tìm kiếm nâng cao dùng ô chọn từng thể loại; kết quả phải chứa tất cả thể loại đã chọn. Quản trị có từ khóa tài khoản (tên đăng nhập, thư điện tử, tên hiển thị), tình trạng khóa; truyện có từ khóa và trạng thái ẩn. Hai danh sách phân trang độc lập, 24 bản ghi/trang, giữ bộ lọc khi chuyển trang.

### 3. Methods and Algorithms — Phương pháp và thuật toán
`CatalogSearch.categoryIds()` lọc số dương, bỏ trùng, giới hạn 20 thể loại. `matchingCategories()` kết hợp các điều kiện bằng phép VÀ; mỗi thể loại có truy vấn con kiểm tra bảng nối hoặc thể loại chính cũ. Không nối nhiều tập hợp vào truy vấn phân trang nên tránh nhân bản hàng. `AdministrationSearch` tạo điều kiện truy vấn theo dữ liệu đầu vào đã chuẩn hóa, thoát ký tự đại diện của phép tìm chuỗi. `PageRequest` đưa giới hạn/phân trang xuống cơ sở dữ liệu; thứ tự mã giảm dần ổn định ở quản trị. Chi phí truy vấn phụ thuộc chỉ mục và độ lớn bảng; tìm chuỗi chứa có thể quét bảng, chưa có phép đo hiệu năng.

### 4. Design Decisions — Quyết định thiết kế
Ô chọn dễ dùng trên điện thoại hơn danh sách cần giữ phím chọn nhiều. Giới hạn số điều kiện để tránh truy vấn quá lớn. Chỉ quản trị viên/điều phối viên được xem danh sách quản trị. Các thao tác khóa/ẩn giữ tại hồ sơ/trang truyện; không đưa trở lại danh sách.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
Biểu mẫu gửi tham số → bộ điều khiển chuẩn hóa → điều kiện truy vấn và trang → kho dữ liệu trả trang → mẫu hiển thị giữ trạng thái. Một liên kết cũ `categoryId=1` vẫn dùng được; nhiều giá trị gửi lặp cùng tên tham số.

### 6. Files Changed — Các file thay đổi
`HomeController.java`, `CatalogSearch.java`, `search.html`, `catalog.css`: nhiều thể loại. `AdminController.java`, `AdministrationSearch.java`, `AccountRepository.java`, `admin.html`: truy vấn/phân trang quản trị. `UiTemplateTest.java`, `AdminControllerTest.java`, `CatalogSearchTest.java`, `scripts/verify-reading-ui.cjs`, `scripts/verify-live-reading.cjs`: kiểm tra và cập nhật lựa chọn thể loại. `plan.md` và `reports.md`: ghi nhận hoàn tất. Không thay đổi `.gitignore`.

Bổ sung sau rà soát: truy vấn trang truyện quản trị tải sẵn người đăng bằng phép nối trái; truy vấn đếm không tải quan hệ. Điều này tránh lỗi tải dữ liệu quan hệ sau khi phiên truy vấn đã đóng. Có kiểm thử riêng hai loại truy vấn.

### 7. Testing and Validation — Kiểm thử
Đã chạy `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test`: 41 kiểm thử đạt. Lần đầu kỳ vọng kiểm thử đếm một phép kiểm tra/thể loại nhưng thực tế có cả thể loại chính và bảng nối; đã sửa kỳ vọng thành hai và chạy lại thành công. Đã chạy `node scripts/verify-reading-ui.cjs`: đạt kiểm tra biểu mẫu, kích thước 320/390/1280 và các chức năng đọc/bản nháp. Mẫu `admin.html` được dựng trong kiểm thử giao diện. Chưa chạy script đọc dữ liệu thực sau thay đổi này.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
Kiểm thử điều kiện truy vấn dùng đối tượng giả lập, chưa đo trên lượng dữ liệu lớn. Phân trang vượt cuối danh sách hiện trả trang trống; người dùng có thể quay trang trước hoặc lọc lại.

### 9. Final Result — Kết quả cuối cùng
Mục 8 đã đánh ✅ sau kiểm thử. Các thay đổi chỉ thuộc tìm kiếm, quản trị và kiểm tra liên quan; kết quả commit/push báo riêng sau thao tác thực tế.

### 10. Thay đổi mã nguồn — Đối chiếu triển khai

**Trước → sau:** tìm kiếm nâng cao chỉ nhận một thể loại; quản trị tải toàn bộ tài khoản/truyện. Hiện nhận nhiều thể loại và chỉ tải một trang kết quả phù hợp cho từng danh sách.

| File | Hàm hoặc thành phần | Thay đổi và tác dụng |
|---|---|---|
| `src/main/java/main/controller/HomeController.java` | `advancedSearch()`, `catalog()` | Đổi tham số thể loại từ `Integer` sang `List<Integer>`; chuẩn hóa danh sách rồi truyền vào điều kiện truy vấn. Giữ liên kết cũ chỉ có một giá trị. |
| `src/main/java/main/service/CatalogSearch.java` | `categoryIds()`, `matchingCategories()` | Bổ sung lọc số dương, bỏ trùng, giới hạn 20; thêm một điều kiện cho từng thể loại, kết hợp bằng phép VÀ. Các hàm `matching()` cũ chuyển tiếp sang cách xử lý mới. |
| `src/main/java/main/service/AdministrationSearch.java` | `accounts()`, `products()`, `keyword()`, `state()`, `pattern()` | Thêm điều kiện tìm tài khoản/truyện và trạng thái; giới hạn độ dài từ khóa, xử lý ký tự đặc biệt. Truy vấn trang truyện tải sẵn người đăng; truy vấn đếm không tải quan hệ. |
| `src/main/java/main/repository/AccountRepository.java` | Khai báo kho dữ liệu | Thêm `JpaSpecificationExecutor<Account>` — thành phần Spring Data hỗ trợ truy vấn bằng điều kiện kết hợp và phân trang. |
| `src/main/java/main/controller/AdminController.java` | `dashboard()` | Nhận bộ lọc và số trang riêng cho tài khoản/truyện; dùng `PageRequest` với 24 bản ghi; đưa dữ liệu trang và bộ lọc vào mẫu. |
| `src/main/resources/templates/search.html`, `src/main/resources/static/css/catalog.css` | Chọn nhiều thể loại | Đổi danh sách chọn đơn thành các ô đánh dấu; chú thích rằng truyện phải có đủ thể loại đã chọn. |
| `src/main/resources/templates/admin.html` | Hai danh sách quản trị | Thêm biểu mẫu lọc, tổng số kết quả, liên kết trang trước/sau và vùng cuộn ngang cho bảng. Tên tài khoản dẫn đến hồ sơ khi có hồ sơ. |
| `src/test/java/main/controller/AdminControllerTest.java`, `src/test/java/main/service/CatalogSearchTest.java`, `src/test/java/main/UiTemplateTest.java` | Kiểm thử | Xác minh quyền truy cập, phân trang độc lập, điều kiện thể loại và tải người đăng đúng loại truy vấn. |
| `scripts/verify-reading-ui.cjs`, `scripts/verify-live-reading.cjs` | Kiểm tra trình duyệt | Đổi thao tác kiểm tra thể loại từ chọn một mục sang đánh dấu ô chọn. Lượt mục 8 có chạy script trang mẫu; script dữ liệu thực chỉ được cập nhật. |

Ví dụ đầu vào mới: `categoryId=1&categoryId=2`. Cả hai mã được giữ trong liên kết phân trang; bên truy vấn phải thỏa **thể loại 1 VÀ thể loại 2**, không phải một trong hai.

Phần bổ sung báo cáo ngày 30/10/2026 không thêm thay đổi mã nguồn hay kết quả kiểm thử mới.

---

## Report 9 — Quản lý cấu trúc dữ liệu theo phiên bản

**Date:** 2026-10-29
**Status:** Completed
**Branch:** sub

### 1. Task Overview — Tổng quan
Thay việc tự thêm cột không có lịch sử bằng các bản SQL có phiên bản; thêm cột, bổ sung giá trị cũ rồi mới đặt ràng buộc để tránh lỗi khi đọc số/giá trị đúng-sai bị trống.

### 2. Implementation Summary — Nội dung triển khai
Thêm Flyway và thành phần hỗ trợ PostgreSQL theo phiên bản Spring Boot 3.3.0 quản lý. `SchemaVersionConfig` tiếp nhận cơ sở dữ liệu có sẵn từ mốc 0 và buộc Hibernate chỉ kiểm tra cấu trúc, kể cả cấu hình cục bộ cũ còn đặt `ddl-auto=update`. Không sửa hoặc đưa file cấu hình có khóa bí mật vào Git.

### 3. Methods and Algorithms — Phương pháp và thuật toán
Flyway lưu lịch sử và mã kiểm tra nội dung tại `flyway_schema_history`. `V1__create_existing_schema.sql` tạo cấu trúc khi thiếu mà không ghi đè bảng đang có. `V2__backfill_legacy_fields.sql` bổ sung các cột từ các tính năng cũ, đổi kiểu liệt kê PostgreSQL cũ sang chuỗi Java sử dụng, cập nhật giá trị NULL an toàn (trạng thái truyện trống thành riêng tư), đặt mặc định và ràng buộc không trống. Chỉ mục duy nhất tên đăng nhập/thư điện tử không phân biệt hoa thường được tạo; dữ liệu trùng/thiếu gây dừng thay vì xóa tài khoản tự động. Mỗi bản SQL chạy theo giao dịch PostgreSQL; lỗi không được coi là đã áp dụng. Chi phí khởi tạo gồm quét bảng và tạo chỉ mục; những lần sau chỉ kiểm tra lịch sử, không chạy lại cập nhật.

### 4. Design Decisions — Quyết định thiết kế
Dùng công cụ quản lý phiên bản đã tích hợp với Spring Boot; không tự viết bộ chạy SQL. Mốc tiếp nhận là 0 để hai bản V1/V2 vẫn chạy trên cơ sở dữ liệu cũ. Hibernate không còn tự sửa để thay đổi cấu trúc bắt buộc đi qua file phiên bản. Các bản đã áp dụng không được sửa nội dung; thay đổi tiếp theo thêm `V3__...sql`, `V4__...sql`.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
Khởi động → Flyway kiểm tra lịch sử → áp dụng bản mới theo thứ tự → Hibernate xác minh lớp dữ liệu → ứng dụng chạy. Cơ sở dữ liệu cũ giữ nguyên truyện/chương, chỉ bổ sung cột/giá trị thiếu. Tham khảo cơ chế tích hợp tại [Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html) và ý nghĩa mốc tiếp nhận tại [Flyway](https://documentation.red-gate.com/flyway/reference/commands/baseline).

### 6. Files Changed — Các file thay đổi
`pom.xml`: hai thành phần Flyway. `SchemaVersionConfig.java`: mốc tiếp nhận và chế độ xác minh Hibernate. Hai file trong `src/main/resources/db/migration/`: cấu trúc hiện tại và nâng cấp dữ liệu cũ. `SchemaMigrationTest.java`: kiểm thử PostgreSQL tách biệt. `plan.md` và `reports.md`: kết quả. Không sửa `.gitignore` hay `Database/ranobe_web.sql`.

### 7. Testing and Validation — Kiểm thử
Đã chạy biên dịch bằng `mvn -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true -DskipTests compile`, sau đó `RUN_DATABASE_TESTS=true` với `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test`: **44 kiểm thử đạt, không bỏ qua**. Hai kiểm thử PostgreSQL tạo lược đồ tạm tên ngẫu nhiên `codex_schema_test_…`, xóa đúng lược đồ đó khi xong; không dùng các bảng `public` để thử. Đã kiểm tra cơ sở dữ liệu mới chạy đủ V1/V2, chạy lại không áp dụng trùng, Hibernate xác minh tất cả lớp dữ liệu hiện có, cơ sở dữ liệu cũ thiếu cột/có NULL được nâng cấp, chữ tiếng Việt giữ nguyên và ràng buộc chặn NULL mới. Không khởi động toàn ứng dụng/R2 để kiểm thử này.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
Thư viện tải thành công và SQL chạy trên PostgreSQL 16.14 cục bộ, chưa chạy trên máy chủ triển khai. Trước lần chạy ứng dụng đầu tiên với dữ liệu thật cần có bản sao lưu. Những lỗi dữ liệu khác như trùng tài khoản không được tự xóa hoặc đoán giá trị. Tài khoản kết nối cần quyền thay đổi cấu trúc khi có bản nâng cấp mới. Bộ kiểm thử PostgreSQL chỉ chạy khi đặt `RUN_DATABASE_TESTS=true`, dùng thông tin kết nối từ file cục bộ không đưa vào Git.

### 9. Final Result — Kết quả cuối cùng
Mục 9 đánh ✅ sau 44 kiểm thử thành công. Các lần thêm trường sau phải bổ sung file SQL phiên bản mới; cấu hình riêng tư và `.gitignore` giữ nguyên.

### 10. Thay đổi mã nguồn — Đối chiếu triển khai

**Trước → sau:** Hibernate tự cập nhật cấu trúc theo lớp Java nhưng không có bước sửa dữ liệu cũ được ghi phiên bản. Hiện Flyway áp dụng các file SQL theo thứ tự, còn Hibernate chỉ xác minh cấu trúc sau đó.

| File | Hàm hoặc thành phần | Thay đổi và tác dụng |
|---|---|---|
| `pom.xml` | `flyway-core`, `flyway-database-postgresql` | Thêm công cụ quản lý phiên bản cấu trúc và thành phần hỗ trợ PostgreSQL; phiên bản thư viện do Spring Boot quản lý. |
| `src/main/java/main/config/SchemaVersionConfig.java` | `adoptExistingSchema()` | Cho tiếp nhận cơ sở dữ liệu có sẵn ở mốc 0, để các bản nâng cấp V1/V2 vẫn được áp dụng. |
| Cùng file | `validateVersionedSchema()` | Đặt `hibernate.hbm2ddl.auto=validate` trong cấu hình Hibernate cuối cùng, tránh tiếp tục tự sửa cấu trúc ngoài file phiên bản. Không sửa file cấu hình riêng tư. |
| `src/main/resources/db/migration/V1__create_existing_schema.sql` | Khởi tạo cấu trúc | Tạo các bảng hiện có nếu thiếu, gồm dữ liệu truyện, tác vụ dọn ảnh và dấu nhập PDF. Không chứa tài khoản mẫu hoặc khóa bí mật. |
| `src/main/resources/db/migration/V2__backfill_legacy_fields.sql` | Nâng cấp dữ liệu cũ | Thêm cột; bổ sung giá trị còn trống; đồng bộ kiểu trạng thái; sau đó mới đặt mặc định, ràng buộc không trống và các chỉ mục. Không tự xóa tài khoản trùng. |
| `src/test/java/main/SchemaMigrationTest.java` | Kiểm thử cấu trúc | Dùng lược đồ PostgreSQL tạm để chạy từ mới, tiếp nhận dữ liệu cũ, kiểm tra chạy lại không lặp và xác minh lớp Java khớp cấu trúc. |

Ví dụ thứ tự xử lý thật trong V2:

```sql
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS sort_order INTEGER;
UPDATE chapters SET sort_order=0 WHERE sort_order IS NULL;
ALTER TABLE chapters ALTER COLUMN sort_order SET DEFAULT 0,
    ALTER COLUMN sort_order SET NOT NULL;
```

Thứ tự này tránh đặt ràng buộc khi các hàng cũ vẫn còn giá trị trống. V3 tương tác được bổ sung ở **mục 10**, không phải một thay đổi của mục 9.

Phần bổ sung báo cáo ngày 30/10/2026 không chạy nâng cấp trên dữ liệu thật và không thay đổi các file SQL đã có.

---

## Report 10 — Xếp hạng theo tương tác

**Date:** 2026-10-29
**Status:** Completed
**Branch:** sub

### 1. Task Overview — Tổng quan
Người dùng chốt công thức tạm thời: **1 bình chọn = 100 điểm; 1 bình luận = 10 điểm; 1 lượt xem = 1 điểm**. Có thể đổi sau. Cần dữ liệu tương tác thực thay vì dùng trường trung bình bình chọn cũ làm số bình chọn.

### 2. Implementation Summary — Nội dung triển khai
Thêm trang `/xep-hang`, liên kết điều hướng, phần bình chọn/bình luận trên truyện công khai, thống kê tương tác. Thêm ba bảng qua V3: `product_views`, `product_votes`, `product_comments`. Không tự quy đổi dữ liệu tổng hợp cũ chưa biết nguồn. Bình chọn/bình luận chỉ dành cho tài khoản đang hoạt động; cơ chế khóa phiên hiện có vẫn kiểm tra trước yêu cầu ghi.

### 3. Methods and Algorithms — Phương pháp và thuật toán
`EngagementService.recordView()` ghi một khóa gồm truyện, mã phiên ngẫu nhiên và khung 30 phút; khóa chính và `ON CONFLICT DO NOTHING` chống tải lại tăng liên tục. Đây là khung thời gian cố định, không phải khoảng cách trượt kể từ lượt trước. Không thu địa chỉ mạng. Một tài khoản có tối đa một bình chọn/truyện; thao tác chọn rõ trạng thái nên gửi lại không tăng trùng, bỏ chọn xóa bình chọn và điểm tương ứng. `comment()` giới hạn 1–2000 ký tự và 10 giây giữa bình luận; khóa hàng người dùng trong giao dịch giúp các yêu cầu đồng thời không vượt giới hạn. Bảng truyện được khóa khi ghi để kiểm tra trạng thái công khai cùng giao dịch.

`signals()` đếm riêng ba bảng tránh nhân bản khi nối. `ranking()` dùng các truy vấn đếm có chỉ mục và phép tính số nguyên lớn, sắp giảm theo điểm, số bình chọn, lượt xem, mã truyện. Bảng xếp hạng lấy tối đa 100 truyện công khai chưa ẩn. Các trọng số tập trung tại `VIEW_WEIGHT`, `COMMENT_WEIGHT`, `VOTE_WEIGHT` trong `EngagementService`; công thức được ghi ở giao diện, cần cập nhật phần chữ khi đổi trọng số. Chi phí đếm phụ thuộc số truyện và số tương tác; không có số đo để bảo đảm tốc độ cho lượng truy cập lớn.

### 4. Design Decisions — Quyết định thiết kế
Ghi tương tác có thời gian để mục 11 dùng dữ liệu 24 giờ. Không thêm công cụ chống gian lận phức tạp: có thể tăng lượt xem bằng nhiều phiên trình duyệt và nhiều bình luận hợp lệ vẫn cộng điểm. Bình luận hiển thị bằng văn bản đã thoát ký tự, không chạy mã HTML. Các biểu mẫu dùng mã xác nhận riêng trong phiên để chặn gửi yêu cầu từ trang khác; không thay đổi hệ thống quyền biên tập truyện.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
Mở trang chi tiết truyện công khai → ghi lượt xem có chống lặp → đọc thống kê/bình luận. Gửi bình chọn hoặc bình luận → kiểm tra phiên/tài khoản → xác minh mã biểu mẫu → giao dịch kiểm tra truyện và ghi → trở về thảo luận. Trang xếp hạng đọc dữ liệu công khai và tính điểm. Bình luận phân trang 20 mục, mới nhất trước. Lượt xem hiện tính ở trang chi tiết truyện, không cộng thêm trên từng chương.

### 6. Files Changed — Các file thay đổi
`V3__product_engagement.sql`, `EngagementService.java`, `EngagementController.java`: lưu và xử lý tương tác. `ProductController.java`: thống kê và bình luận trang truyện. `product-detail.html`, `ranking.html`, `index.html`, `catalog.css`: giao diện. `SchemaMigrationTest.java`, `EngagementControllerTest.java`, `UiTemplateTest.java`, `scripts/verify-reading-ui.cjs`: kiểm tra dữ liệu, quyền biểu mẫu, nội dung hiển thị và kích thước màn hình. `plan.md`, `reports.md`: ghi nhận công thức và hoàn tất.

### 7. Testing and Validation — Kiểm thử
Chạy toàn bộ `RUN_DATABASE_TESTS=true` với `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test`: **47 kiểm thử đạt**. Sau khi gom trọng số, chạy lại `-Dtest=SchemaMigrationTest`: 3 kiểm thử dữ liệu đạt. Kiểm thử dùng lược đồ PostgreSQL tạm: đếm lặp, hai phiên xem khác nhau, bỏ bình chọn, truyện riêng tư/ẩn không lọt xếp hạng, giới hạn bình luận và tài khoản khóa bị từ chối. Kiểm thử bộ điều khiển chặn khách và mã biểu mẫu giả. Dựng trang kiểm tra điểm 115 và bình luận chứa thẻ mã được thoát ký tự. `node scripts/verify-reading-ui.cjs` đạt cả trang xếp hạng ở 320/390/1280.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
Chưa nhập lại số liệu lịch sử vào bảng mới, chưa có sửa/xóa/quản trị bình luận hoặc chống bình luận trùng theo nội dung. Chưa có cơ chế nhận diện người thật; quy tắc lượt xem chỉ giảm đếm lặp đơn giản. Đã thêm một thao tác ghi chống lặp khi mở trang truyện và đọc thống kê; có chi phí cơ sở dữ liệu, chưa đo tải thực. Các bảng sự kiện sẽ tăng dung lượng theo thời gian. Không dùng bảng `ranking` cũ để suy đoán dữ liệu mới.

### 9. Final Result — Kết quả cuối cùng
Mục 10 đã đánh ✅, công thức ghi rõ theo quyết định người dùng. Các kiểm tra trên dữ liệu tạm thành công; không tạo bình chọn/bình luận vào dữ liệu truyện hiện có để thử nghiệm.

### 10. Thay đổi mã nguồn — Đối chiếu triển khai

**Trước → sau:** chỉ có lớp/bảng `ranking` cũ, chưa có luồng ghi tương tác cho tính điểm mới. Hiện ghi lượt xem, bình chọn và bình luận riêng; xếp hạng đọc trực tiếp các bảng đó.

| File | Hàm hoặc thành phần | Thay đổi và tác dụng |
|---|---|---|
| `src/main/resources/db/migration/V3__product_engagement.sql` | Ba bảng tương tác | Thêm `product_views`, `product_votes`, `product_comments`, khóa chính/khóa ngoại và chỉ mục thời gian. Khóa ghép ngăn lượt xem và bình chọn trùng theo quy tắc đã mô tả. |
| `src/main/java/main/service/EngagementService.java` | `recordView()`, `vote()`, `comment()` | Ghi lượt xem có chống lặp, đặt/bỏ bình chọn theo trạng thái yêu cầu và lưu bình luận với giới hạn độ dài/tần suất. |
| Cùng file | `requireActiveUser()`, `requirePublicProduct()` | Kiểm tra người dùng đang hoạt động và truyện công khai trong giao dịch; khóa hàng tương ứng khi ghi để kiểm tra và lưu không tách rời. |
| Cùng file | `signals()`, `voted()`, `comments()`, `ranking()` | Đọc thống kê, trạng thái đã bình chọn, trang bình luận và xếp hạng. Đếm từng nguồn riêng, tránh nhân số liệu do nối nhiều bảng. |
| `src/main/java/main/controller/EngagementController.java` | `ranking()`, `vote()`, `comment()`, `submit()` | Thêm trang xếp hạng và đường dẫn gửi tương tác. Kiểm tra đăng nhập/mã biểu mẫu trong phiên; giữ thông báo lỗi và đưa người dùng về phần thảo luận. |
| `src/main/java/main/controller/ProductController.java` | Hàm khởi tạo, `showProductDetail()` | Nhận dịch vụ tương tác; bổ sung mã phiên xem, mã biểu mẫu, thống kê, trạng thái bình chọn và trang bình luận cho truyện công khai. |
| `src/main/resources/templates/product-detail.html` | Phần thảo luận | Thêm thống kê, biểu mẫu bình chọn/bình luận, thông báo lỗi và phân trang bình luận; nội dung người dùng hiển thị bằng văn bản được thoát ký tự. |
| `src/main/resources/templates/ranking.html`, `src/main/resources/templates/index.html`, `src/main/resources/static/css/catalog.css` | Trang và điều hướng | Thêm trang xếp hạng, liên kết truy cập và định dạng phù hợp màn hình nhỏ. |
| `src/test/java/main/controller/EngagementControllerTest.java`, `src/test/java/main/SchemaMigrationTest.java`, `src/test/java/main/UiTemplateTest.java`, `scripts/verify-reading-ui.cjs` | Kiểm thử | Kiểm tra quyền/mã biểu mẫu, số đếm thực trên dữ liệu tạm, điểm, nội dung hiển thị và bố cục trình duyệt. |

Trọng số thật trong mã:

```java
public static final long VIEW_WEIGHT = 1;
public static final long COMMENT_WEIGHT = 10;
public static final long VOTE_WEIGHT = 100;
```

Cả `Signals.score()` và truy vấn `ranking()` sử dụng các hằng này. Thay trọng số cần sửa tại đây và đồng bộ phần giải thích công thức trên trang `ranking.html`. Bảng `ranking` cũ không bị xóa và không được dùng để suy đoán số tương tác mới.

Phần bổ sung báo cáo ngày 30/10/2026 chỉ giải thích mã đã có; không ghi tương tác hoặc sửa điểm của dữ liệu thật.

---

## Report 11 — Đề xuất theo tương tác 24 giờ

**Date:** 2026-10-29
**Status:** Completed
**Branch:** sub

### 1. Task Overview — Tổng quan
Hiện tối đa 6 truyện trên trang chủ dựa vào điểm tương tác trong 24 giờ gần nhất, dùng đúng trọng số tạm của mục 10: bình chọn 100, bình luận 10, lượt xem 1.

### 2. Implementation Summary — Nội dung triển khai
`HomeController.catalog()` chỉ lấy đề xuất khi mở trang chủ không có từ khóa. `EngagementService.ranking()` nhận mốc hiện tại trừ 24 giờ, giới hạn 6 và yêu cầu có điểm. `index.html` hiển thị khu đề xuất trước danh sách mới cập nhật; 6 cột trên máy tính, 3 trên màn hình vừa và 2 trên điện thoại.

### 3. Methods and Algorithms — Phương pháp và thuật toán
Truy vấn đếm riêng lượt xem/bình luận/bình chọn có thời điểm từ mốc trượt 24 giờ đến hiện tại thực tế ghi dữ liệu. Trọng số dùng chung với bảng xếp hạng. Chỉ lấy truyện công khai chưa ẩn; sắp theo điểm, số bình chọn, lượt xem, mã truyện giảm dần để kết quả ổn định khi bằng điểm. Không có hoạt động thì trả danh sách trống và không hiện khu đề xuất, không tự bịa điểm hoặc lấy dữ liệu cả đời thay thế. Thời gian là Instant và cột thời gian có múi giờ, tránh tính nhầm theo ngày lịch.

### 4. Design Decisions — Quyết định thiết kế
Không thêm lịch chạy hay bộ nhớ đệm giữ danh sách đề xuất, để truyện vừa ẩn không còn xuất hiện từ kết quả cũ. Chi phí thêm một truy vấn xếp hạng trên lượt mở trang chủ không lọc; chưa đo tải lớn. Đây là đề xuất phổ biến chung, không cá nhân hóa theo lịch sử người đọc.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
Truy cập trang chủ không tìm kiếm → xác định mốc 24 giờ → truy vấn tương tác hiện hành → lấy tối đa 6 → hiển thị ảnh/tên/điểm và đường dẫn chi tiết. Tìm kiếm không chạy truy vấn đề xuất. Bình chọn bị bỏ sẽ không được tính; bình chọn còn hiệu lực nhưng tạo ngoài 24 giờ không cộng điểm đề xuất.

### 6. Files Changed — Các file thay đổi
`HomeController.java`: lấy đề xuất đúng ngữ cảnh. `index.html`, `catalog.css`: bố cục đề xuất và đường dẫn. `HomeControllerTest.java`: kiểm tra mốc 24 giờ và không truy vấn khi tìm kiếm. `SchemaMigrationTest.java`: kiểm tra thứ tự/giới hạn trên dữ liệu PostgreSQL tạm. `UiTemplateTest.java`: dữ liệu mẫu hiển thị. `plan.md`, `reports.md`: hoàn tất mục 11.

### 7. Testing and Validation — Kiểm thử
`RUN_DATABASE_TESTS=true` với `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test`: **49 kiểm thử đạt, không bỏ qua**. Dữ liệu tạm kiểm tra truyện có hoạt động quá 24 giờ bị loại; truyện ẩn không lọt; điểm 100/20/5/1 đúng công thức; lấy đúng 6 và thứ tự khi bằng điểm; mốc tương lai trả rỗng. `node scripts/verify-reading-ui.cjs` đạt 320/390/1280; đã xem ảnh chụp trang chủ điện thoại, khu đề xuất/tìm kiếm không chồng lên nhau.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
Chưa có tương tác ở các bảng mới thì khu đề xuất chưa hiện. Không chuyển số tổng hợp cũ thành sự kiện có thời điểm giả. Quy tắc đếm và hạn chế gian lận giữ như mục 10. Chưa tối ưu bằng bảng tổng hợp cho lượng tương tác lớn; cần đo trước khi thêm.

### 9. Final Result — Kết quả cuối cùng
Mục 11 đã đánh ✅ sau kiểm thử. Các mục có thể thực hiện trong kế hoạch đã hoàn tất; mục 3 được người dùng loại bỏ và phạm vi mục 5 đã chốt chỉ cần bản nháp. Công thức có thể đổi sau và đã được ghi rõ trong báo cáo mục 10.

### 10. Thay đổi mã nguồn — Đối chiếu triển khai

**Trước → sau:** trang chủ chỉ có danh sách mới cập nhật và tìm tên truyện. Hiện bổ sung một khu đề xuất, nhưng không chen kết quả đề xuất vào danh sách đang tìm kiếm.

| File | Hàm hoặc thành phần | Thay đổi và tác dụng |
|---|---|---|
| `src/main/java/main/controller/HomeController.java` | Hàm khởi tạo, `catalog()` | Nhận `EngagementService`; khi không ở trang nâng cao và không có từ khóa, gọi xếp hạng từ mốc hiện tại trừ 24 giờ, giới hạn 6, yêu cầu có tương tác. Đưa kết quả vào `recommendedProducts`. |
| `src/main/resources/templates/index.html` | Khu `recommendations` | Chỉ hiện khi có đề xuất; từng thẻ có bìa, tên, điểm 24 giờ và liên kết truyện. Không dùng chung lớp thẻ kết quả tìm kiếm để tránh lẫn hai danh sách. |
| `src/main/resources/static/css/catalog.css` | Các lớp `.recommendation-*` | Thêm lưới 6/3/2 cột theo độ rộng màn hình, giới hạn tên truyện và ảnh bìa để không tràn ngang. |
| `src/test/java/main/controller/HomeControllerTest.java` | Kiểm thử trang chủ | Kiểm tra mốc thời gian và giới hạn 6; xác nhận tìm theo từ khóa không gọi truy vấn đề xuất. |
| `src/test/java/main/SchemaMigrationTest.java` | Kiểm thử đề xuất | Thêm dữ liệu tạm khác thời điểm/điểm để kiểm tra loại tương tác quá 24 giờ, bỏ truyện ẩn và lấy đúng thứ tự sáu truyện. |
| `src/test/java/main/UiTemplateTest.java` | Dữ liệu mẫu | Bổ sung `recommendedProducts` để dựng và kiểm tra giao diện có khu đề xuất. |

Lời gọi chính hiện có trong `catalog()`:

```java
engagement.ranking(
    java.time.Instant.now().minus(java.time.Duration.ofHours(24)),
    6,
    true
)
```

`true` ở đây là yêu cầu truyện có điểm tương tác, không phải bật cá nhân hóa. Mục này dùng lại truy vấn và trọng số từ mục 10, không thêm bảng hoặc sửa lại V3.

Phần bổ sung báo cáo ngày 30/10/2026 không sửa giao diện, logic đề xuất hoặc chạy lại các kiểm thử đã ghi ở mục 7.

---

## Report 13 — Gỡ ảnh bìa mặc định cũ

**Date:** 2026-10-30
**Status:** Completed
**Branch:** sub

### 1. Task Overview — Tổng quan
Gỡ ảnh `src/main/resources/static/images/default-cover.webp` và không tự sử dụng ảnh đó làm bìa truyện/tập hoặc bất kỳ bìa thay thế nào. Đây là tác vụ người dùng yêu cầu riêng; không triển khai mục 12 về hiệu ứng tài khoản còn chờ quyết định.

### 2. Implementation Summary — Nội dung triển khai
Xóa tệp ảnh trong nguồn. Bỏ biến mẫu `siteDefaultCover` và cấu hình đọc đường dẫn bìa mặc định ở `SiteAssetsAdvice`. Các trang chỉ hiển thị bìa đã có; khi thiếu hoặc ảnh tải lỗi thì để nền trống, không chuyển sang một ảnh khác. Logo của web giữ nguyên.

### 3. Methods and Algorithms — Phương pháp và thuật toán
Mẫu Thymeleaf kiểm tra đường dẫn có giá trị trước khi xuất thẻ ảnh. Sự kiện lỗi ảnh chỉ ẩn ảnh lỗi. Khung nền độc lập giữ kích thước để danh sách và chi tiết truyện không xô lệch. Không có thuật toán mới; đây là sửa điều kiện dựng giao diện và xử lý lỗi ảnh, không truy vấn thêm cơ sở dữ liệu.

### 4. Design Decisions — Quyết định thiết kế
Không tự tạo ảnh mặc định mới và không thêm nhãn/biểu tượng mới. Tập không có bìa riêng vẫn có thể dùng bìa thật của truyện đã lưu, nhưng không dùng ảnh mặc định cũ. Không xóa tệp trên R2 hoặc đổi các đường dẫn người dùng đã chủ động lưu trong cơ sở dữ liệu.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
Mẫu nhận đường dẫn bìa thật → có đường dẫn thì xuất ảnh → ảnh lỗi thì ẩn → không có đường dẫn thì chỉ có nền. `SiteAssetsAdvice` chỉ còn cung cấp hai logo. Tác vụ dọn ảnh giữ bảo vệ hai logo hệ thống, không còn gọi hàm cung cấp bìa mặc định đã bỏ.

### 6. Files Changed — Các file thay đổi
Xóa `src/main/resources/static/images/default-cover.webp`. Sửa `SiteAssetsAdvice.java`, `ImageCleanupService.java`, `index.html`, `product-detail.html`, `product-edit.html`, `profile.html`, `catalog.css`, `UiTemplateTest.java`. Ghi mục 13 trong `plan.md` và báo cáo này, chỉ giữ cục bộ. Không sửa `.gitignore`, khóa dịch vụ hoặc dữ liệu hiện có.

### 7. Testing and Validation — Kiểm thử
Đã chạy `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true -Dtest=UiTemplateTest,ImageCleanupServiceTest test`: **15 kiểm thử đạt, không lỗi**. Mẫu dựng cả khi thiếu bìa và có bìa thật; kiểm tra không còn đường dẫn ảnh mặc định hoặc thuộc tính chuyển sang ảnh thay thế. Bổ sung dựng trang hồ sơ và sửa truyện. Chạy `node scripts/verify-reading-ui.cjs`: đạt kiểm tra 320/390/1280, tìm kiếm, đọc tiếp, tùy chỉnh đọc và bản nháp. Đã xem ảnh chụp trang chủ điện thoại; khung bìa trống giữ đúng bố cục. Kiểm tra văn bản UTF-8 và rà đường dẫn trong nguồn để tránh gọi lại ảnh đã xóa.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
Nếu dữ liệu trước đây đã lưu trực tiếp đường dẫn ảnh cũ vào trường bìa, tác vụ này không tự xóa giá trị đó vì không có yêu cầu thay dữ liệu thật. Không kiểm tra/xóa bản trên Cloudflare. Bản sao cũ của ảnh trong `target/classes/static/images/` đã được dọn để lần đóng gói sau không mang theo tệp đã xóa ở nguồn. Các tài liệu lịch sử vẫn có thể nhắc tới tên ảnh; đó không phải tham chiếu chạy của ứng dụng.

### 9. Final Result — Kết quả cuối cùng
Mục 13 được đánh ✅ sau xác minh. Chỉ mã liên quan và việc xóa ảnh được chọn cho Git. `plan.md`, `reports.md`, `bao_cao.md` và `.gitignore` không đưa vào commit. Kết quả đẩy mã báo riêng sau khi thực hiện, không ghi trước.

### 10. Thay đổi mã nguồn — Đối chiếu triển khai

**Trước → sau:** các trang dùng `siteDefaultCover` khi thiếu bìa và đổi `src` sang ảnh đó nếu tải lỗi. Hiện chỉ dùng ảnh do dữ liệu chỉ định, không có ảnh thay thế toàn cục.

| File | Thành phần | Thay đổi và tác dụng |
|---|---|---|
| `src/main/resources/static/images/default-cover.webp` | Tệp ảnh | Xóa tệp bìa cũ khỏi nguồn dự án. |
| `src/main/java/main/config/SiteAssetsAdvice.java` | Hàm khởi tạo và `siteDefaultCover()` | Bỏ trường `cover`, tham số `coverOverride` và hàm cung cấp bìa mặc định; giữ hai hàm cung cấp logo. |
| `src/main/java/main/service/ImageCleanupService.java` | Hàm khởi tạo | Danh sách ảnh hệ thống được bảo vệ chỉ lấy hai logo; không gọi hàm bìa mặc định đã bỏ. |
| `src/main/resources/templates/index.html` | Bìa đề xuất và kết quả | Chỉ xuất ảnh khi có bìa thật; bọc bìa đề xuất bằng khung nền riêng để không mất tỷ lệ khi thiếu ảnh. |
| `src/main/resources/static/css/catalog.css` | `.recommendation-cover` | Chuyển tỷ lệ ảnh bìa sang khung độc lập, giữ chiều cao ngay cả khi không có thẻ ảnh. |
| `src/main/resources/templates/product-detail.html` | Bìa truyện/tập | Giữ khung bìa; bỏ ảnh mặc định và đường dẫn tự thay khi lỗi. Tập có thể dùng bìa thật của truyện khi chưa có bìa riêng. |
| `src/main/resources/templates/product-edit.html` | Xem trước và gỡ bìa | Không xuất ảnh khi chưa có bìa; bỏ câu hứa dùng ảnh mặc định trong xác nhận gỡ bìa. |
| `src/main/resources/templates/profile.html` | Bìa truyện đã đăng | Chỉ dùng đường dẫn bìa thật; tải lỗi thì ẩn ảnh. |
| `src/test/java/main/UiTemplateTest.java` | Dữ liệu và kiểm tra mẫu | Logo mẫu dùng dữ liệu ảnh nhỏ độc lập thay vì tệp đã xóa; kiểm tra mẫu không có ảnh mặc định, đồng thời giữ được bìa thật. |

Không sửa cách tải ảnh lên R2, không thay đổi quyền người dùng và không chạy cập nhật cơ sở dữ liệu.

#### Điều chỉnh theo xác nhận ngày 30/10/2026 — khôi phục bìa LN xanh–tím

Người dùng xác nhận cần giữ logo LN xanh–tím làm bìa thay thế; chỉ bỏ bìa trang trí vàng `default-cover.webp`. Phần điều chỉnh này thay thế hành vi để nền trống nêu ở báo cáo mục 13 bên trên.

- Đã xem và đối chiếu ảnh công khai `logo-ln.png` trên Cloudflare: đúng logo LN xanh–tím trong hình người dùng gửi, không phải bìa trang trí vàng.
- `SiteAssetsAdvice.siteDefaultCover()` được khôi phục, trả về cùng đường dẫn `siteLogoBig` hiện có; không tải ảnh mới lên R2, không thêm bản ảnh vào dự án.
- `index.html`: bìa truyện trong đề xuất và kết quả tìm kiếm dùng logo khi đường dẫn bìa trống. Giữ nguyên hai chỉnh sửa riêng của người dùng: tiêu đề “Đề xuất” và bỏ dòng điểm 24 giờ.
- `product-detail.html`, `product-edit.html`: cả bìa truyện và bìa tập dùng logo này khi chưa có bìa riêng. Bìa tập trống không tự lấy bìa truyện nữa. `profile.html` cũng hiển thị đúng bìa thay thế ở danh sách tác phẩm.
- Các ảnh có `data-fallback` và xử lý `onerror`: bìa riêng lỗi sẽ chuyển về logo, tắt trình xử lý để tránh lặp vô hạn nếu logo cũng lỗi. Bìa riêng hợp lệ vẫn ưu tiên.
- `UiTemplateTest.java` kiểm tra logo xuất hiện khi thiếu bìa, bìa riêng vẫn giữ khi có, và đường dẫn dự phòng trỏ đúng logo. Tệp vàng đã xóa không được tạo lại.
- Lệnh kiểm tra: `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true -Dtest=UiTemplateTest,ImageCleanupServiceTest test`: **15 kiểm thử đạt**. Kiểm tra bổ sung bằng Edge với ảnh logo thật đã đọc từ Cloudflare: bốn trang tải được ảnh và xử lý ảnh lỗi quay về logo đúng; đạt.
- Chỉ thay đổi cục bộ, **không commit và không push** theo phạm vi đã chốt. Không sửa `.gitignore`, `plan.md`, dữ liệu cơ sở dữ liệu hoặc tệp `image.png` người dùng đang có. Nhật ký kiểm tra không đồng nghĩa đã khởi động lại phiên web đang chạy của người dùng.

#### Đính chính đường dẫn bìa theo xác nhận tiếp theo của người dùng

Người dùng xác nhận tệp `default-cover.webp` trên Cloudflare mới là bìa xanh–tím dạng dọc cần dùng. Đã tải bản công khai để xem và xác nhận đúng ảnh. Không đồng nhất tệp này với bản trang trí vàng từng nằm trong thư mục ảnh cục bộ đã xóa.

- `SiteAssetsAdvice` có trường `cover` riêng, nhận `{r2.public-url}/default-cover.webp`; `siteDefaultCover()` trả trường này, không trả đường dẫn logo lớn nữa.
- `ImageCleanupService` bảo vệ cả hai logo và bìa mặc định đang cấu hình. Các mẫu dùng `siteDefaultCover` tự nhận đường dẫn WebP mới; không cần thay dữ liệu bìa riêng.
- `logo-ln.png` **vẫn dùng ở login.html và register.html**, do đó giữ nguyên; chưa xóa trên Cloudflare hoặc bỏ chức năng logo lớn.
- `UiTemplateTest` dùng đường dẫn WebP mẫu, xác nhận thay logo lớn riêng không làm đổi bìa mặc định và không gọi lại ảnh cục bộ đã xóa.
- Chạy lại `mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true -Dtest=UiTemplateTest,ImageCleanupServiceTest test`: **15 kiểm thử đạt**. Không chuyển định dạng ảnh, không ghi cơ sở dữ liệu, không tải lên/xóa tệp trên R2 và không commit/push.

---

## Report 14 — Thêm chức năng thảo luận

**Date:** 2026-10-30  
**Status:** Đã triển khai, kiểm thử cục bộ; chờ người dùng kiểm tra  
**Branch:** sub

### 1. Task Overview — Tổng quan

- Thêm mục Thảo luận trên thanh điều hướng dùng chung và trang danh sách chủ đề riêng tại `/thao-luan`.
- Người đăng nhập tạo chủ đề, trả lời và sửa nội dung của mình. Chỉ điều phối viên (MOD) và quản trị viên (ADMIN) đăng thông báo và quản lý ẩn/khôi phục, khóa/mở trả lời.
- Nội dung chỉ là văn bản, chưa nhận ảnh. Bỏ mục Danh sách theo xác nhận; tìm kiếm nâng cao hiện có vẫn đảm nhiệm liệt kê truyện.
- Chỉ sửa cục bộ theo yêu cầu hiện tại; không commit hoặc push. Không làm tiếp mục 12 hoặc 15.

### 2. Implementation Summary — Nội dung triển khai

- `DiscussionController` xử lý trang danh sách, tạo/sửa chủ đề, chi tiết, gửi/sửa bình luận và quản lý. Các biểu mẫu ghi dữ liệu cần đăng nhập và mã xác nhận gắn với phiên đăng nhập.
- `DiscussionService` dùng `JdbcTemplate` để truy vấn có tham số, kiểm tra quyền bằng dữ liệu tài khoản hiện tại và lưu trong giao dịch. Không sửa chức năng bình luận truyện trong `product_comments`.
- Ba mẫu Thymeleaf và `discussions.css` tạo giao diện nền tối, liên kết hồ sơ, bộ lọc và phân trang. Trên điện thoại, bảng cuộn ngang bên trong khung, không đẩy tràn toàn trang.
- Flyway, công cụ quản lý thay đổi cấu trúc cơ sở dữ liệu theo phiên bản, chạy tệp V4 khi ứng dụng khởi động. Hai bảng mới là `discuss` và `comments`.

### 3. Methods and Algorithms — Phương pháp và thuật toán

**Danh sách và hoạt động mới nhất:** `DiscussionService.topics()` nhận từ khóa tiêu đề, loại chủ đề, trạng thái hiển thị, quyền xem và số trang. Truy vấn `LOWER(title) LIKE ?` xử lý các ký tự `%`, `_`, `!` như ký tự tìm kiếm thông thường. Mỗi trang có 24 chủ đề; sắp xếp theo hoạt động bình luận mới nhất rồi mã chủ đề để ổn định thứ tự. Trang bình luận có 20 mục, từ cũ đến mới.

`TOPICS` đếm bình luận chưa ẩn bằng truy vấn con, đồng thời dùng `LEFT JOIN LATERAL` tìm một bình luận đang hiện mới nhất theo `(created_at DESC, comment_id DESC)`. Nếu chưa có bình luận, thời gian hoạt động và người đăng cuối lấy từ chủ đề ban đầu. `last_updated` chỉ phản ánh sửa nội dung; sửa bài cũ không đẩy hoạt động lên đầu. Không lưu trùng `comment_count`, `last_uploaded` hoặc khóa vòng `comment_id` trên bảng chủ đề.

Truy vấn có thể phải lọc/sắp xếp nhiều chủ đề và đếm các bình luận liên quan; phân trang không bảo đảm chỉ đọc 24 hàng. Chưa đo thời gian hoặc chứng minh một độ phức tạp cố định cho kế hoạch truy vấn PostgreSQL. Các chỉ mục theo chủ đề/thời gian và người viết/thời gian hỗ trợ lấy bình luận cuối và kiểm tra khoảng chờ; dữ liệu lớn cần đo lại thực tế.

**Quyền và ghi đồng thời:** `activeUser()` đọc lại vai trò và trạng thái của cả `accounts` và `users`, khóa hàng bằng `FOR UPDATE` trong giao dịch. `lockedTopic()` khóa chủ đề trước thao tác nội dung/quản lý. Trình tự là kiểm tra văn bản → kiểm tra người dùng → khóa chủ đề khi cần → kiểm tra quyền/trạng thái → cập nhật. Khóa tài khoản giúp các lần gửi cùng tài khoản không cùng vượt kiểm tra khoảng chờ; chưa chạy thử tải nhiều kết nối đồng thời.

- `create()`: tiêu đề 1–200 ký tự, nội dung 1–20.000 ký tự sau loại khoảng trắng ở hai đầu; thông báo chỉ dành cho MOD/ADMIN; cách nhau ít nhất 60 giây giữa hai chủ đề.
- `postReply()`: nội dung 1–5.000 ký tự, chủ đề phải đang hiện và chưa khóa trả lời; cách nhau ít nhất 10 giây giữa hai bình luận của một tài khoản.
- `editTopic()` và `editReply()`: chỉ người viết sửa nội dung của mình khi còn đang hiện. MOD/ADMIN không được sửa nội dung người khác. Mã bình luận phải thuộc đúng chủ đề.
- `moderate()`: chỉ nhận danh sách tác vụ đã định nghĩa; ẩn/khôi phục chủ đề hoặc bình luận, khóa/mở trả lời. Không có xóa vĩnh viễn. Khóa trả lời ngăn gửi mới, vẫn cho người viết sửa bình luận cũ đang hiện.
- Chủ đề ẩn không xuất hiện với khách/người dùng thường, kể cả nhập trực tiếp đường dẫn. MOD/ADMIN có bộ lọc xem các chủ đề đã ẩn để khôi phục.

**An toàn biểu mẫu và văn bản:** `common()` tạo chuỗi ngẫu nhiên `discussionToken` cho phiên; `requireUser()` so sánh bằng `MessageDigest.isEqual`. Đây là mã chống gửi yêu cầu giả mạo từ trang khác, không phải mã đăng nhập. Các lệnh ghi còn đi qua `ActiveAccountInterceptor` hiện có. Thymeleaf `th:text` chuyển thẻ HTML thành văn bản, nên chuỗi chèn ảnh hoặc mã lệnh không trở thành phần tử có thể chạy. Không thêm đường dẫn tải ảnh lên máy chủ hoặc R2.

**Đếm xem:** `shouldCountView()` giữ `LinkedHashMap<Long,Long>` trong phiên, tối đa 200 chủ đề. Một chủ đề được cộng lại sau 30 phút trong cùng phiên; khi đầy loại phần tử cũ nhất theo thứ tự ghi nhận. Không đếm chủ đề ẩn. Đây là giới hạn đếm theo phiên, không phải số người đọc duy nhất; xóa phiên vẫn có thể tăng lượt xem lại. Tìm/ghi bản đồ trung bình O(1), bộ nhớ bị chặn ở 200 mục mỗi phiên.

**Kết quả gửi:** nội dung không hợp lệ được giữ lại cùng thông báo lỗi trên biểu mẫu. Trả lời lỗi dùng thuộc tính tạm qua chuyển hướng để giữ nội dung. Gửi thành công chuyển tới trang bình luận cuối và điểm neo `comment-{id}`. Trường hợp nội dung bị ẩn đồng thời có thể không còn xem được trang sau chuyển hướng.

### 4. Design Decisions — Quyết định thiết kế

- Tách thảo luận khỏi bình luận truyện để không thay đổi luồng đã có.
- Tính số bình luận/người viết cuối từ bình luận đang hiện giúp ẩn/khôi phục phản ánh ngay, tránh các cột tổng hợp bị lệch. Đổi lại truy vấn danh sách nặng hơn khi dữ liệu tăng; chưa xây bộ nhớ đệm.
- Giữ `created_at`, `last_updated` và hoạt động mới nhất riêng biệt để sửa nội dung không giả thành bình luận mới.
- Dùng biểu mẫu và giao diện kết xuất trên máy chủ theo kiến trúc đang có; chưa thêm trình soạn thảo ảnh hoặc thư viện giao diện khác.
- Ràng buộc khóa ngoại bảo vệ liên kết tác giả/chủ đề; các cột trạng thái, nội dung và thời gian có giá trị không rỗng; lượt xem không âm. Không sửa những trường nullable của các bảng khác thuộc mục 15.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu

1. Trình duyệt gửi yêu cầu tới `DiscussionController`; trang đọc không yêu cầu đăng nhập.
2. Thao tác ghi qua bộ kiểm tra tài khoản hiện có và kiểm tra mã của phiên, rồi gọi `DiscussionService`.
3. Dịch vụ kiểm tra văn bản, quyền và trạng thái, thực hiện truy vấn trong giao dịch trên PostgreSQL.
4. Bộ điều khiển chuyển hướng khi thành công; lỗi nhập liệu giữ lại nội dung cho người dùng sửa.
5. Trang danh sách/chi tiết lấy thông tin tác giả từ `users`, đếm bình luận đang hiện và tìm bình luận cuối để kết xuất.

### 6. Files Changed — Các file thay đổi

Các tệp mới: `src/main/java/main/controller/DiscussionController.java`, `src/main/java/main/service/DiscussionService.java`, `src/main/resources/db/migration/V4__discussion_topics_and_comments.sql`, `src/main/resources/static/css/discussions.css`, ba mẫu `discussions.html`, `discussion-detail.html`, `discussion-form.html` trong `src/main/resources/templates`, và `src/test/java/main/controller/DiscussionControllerTest.java`.

Các tệp sửa trong phạm vi nhiệm vụ: `src/main/resources/templates/index.html`, `src/test/java/main/SchemaMigrationTest.java`, `src/test/java/main/UiTemplateTest.java`, `scripts/verify-reading-ui.cjs`, `plan.md`, `reports.md`. Giữ nguyên các sửa đổi khác đang có sẵn của người dùng; không sửa `.gitignore`.

### 7. Testing and Validation — Kiểm thử

Chạy thực tế với Java 25 và Maven đang cài, bật `$env:RUN_DATABASE_TESTS='true'`:

```text
mvn -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test
```

Kết quả lượt cuối: **55 kiểm thử đạt, 0 lỗi, 0 bỏ qua**. Tham số trình biên dịch tắt xử lý chú thích tự động; tham số Byte Buddy cho phép công cụ giả lập kiểm thử hoạt động với Java đang cài. Không đưa các tham số này thành thay đổi vào `pom.xml`.

- Sáu kiểm thử trong `SchemaMigrationTest` chạy với PostgreSQL thật, mỗi lần dùng lược đồ ngẫu nhiên riêng `codex_schema_test_*` rồi dọn đúng lược đồ đó. Kiểm tra cả bốn bản thay đổi cấu trúc trên dữ liệu mới/cũ và chạy lại không lặp thay đổi; không áp V4 lên bảng thật trong `public`.
- Các ca mới kiểm tra quyền thông báo/quản lý, cấm sửa người khác, ẩn/khôi phục, khóa trả lời, đếm và người bình luận cuối, sửa không đẩy hoạt động, tài khoản bị khóa, khoảng chờ, phân trang, sai chủ đề và tìm ký tự `%` theo nghĩa thông thường.
- `DiscussionControllerTest`: khách/mã phiên sai không ghi; lỗi tạo giữ biểu mẫu; xem lại không tăng ngay; gửi trả lời chuyển tới trang cuối và giữ nội dung khi gửi lỗi.
- `UiTemplateTest`: kết xuất mẫu thật cho khách và tài khoản quản lý; kiểm tra nội dung HTML được chuyển thành chữ, nút theo quyền, biểu mẫu và phân trang. Các kiểm tra bìa cũ đang có được giữ lại.
- Chạy `node scripts/verify-reading-ui.cjs` bằng Edge không mở cửa sổ: đạt. Kiểm tra trang cũ và mới ở độ rộng 320, 390, 1280 pixel, không tràn toàn trang; không có lỗi mã trình duyệt; không có phần tử ảnh/mã lệnh trong nội dung thảo luận. Đã xem ảnh chụp danh sách trên máy tính và chi tiết trên điện thoại.
- `git diff --check`: đạt. Chưa kiểm thử thủ công trên ứng dụng đang chạy, chưa đo tải/đồng thời nhiều kết nối, chưa triển khai lên máy chủ công khai.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến

- Chưa có ảnh, ghim thông báo, chuông thông báo, lịch sử sửa, xóa vĩnh viễn hay tự lưu bản nháp thảo luận.
- Giới hạn khoảng chờ hiện đặt trong mã; có thể chuyển thành cấu hình sau nếu cần. Đếm xem theo phiên không chống tăng lượt xem bằng việc đổi phiên.
- Khi dữ liệu lớn cần xem kế hoạch truy vấn thực tế trước khi thêm tối ưu. Chưa khẳng định hiệu năng từ kiểm thử chức năng.
- Muốn thử trên dữ liệu chính, người dùng khởi động lại ứng dụng; Flyway sẽ tự áp V4 và tạo hai bảng. Phiên ứng dụng cũ chưa được khởi động lại trong lần làm này.

### 9. Final Result — Kết quả cuối cùng

Đã có chức năng thảo luận để kiểm tra cục bộ. `plan.md` ghi trạng thái chờ người dùng kiểm tra, chưa đánh dấu ✅ chốt nhiệm vụ. Không tạo commit hoặc push; không chạy sang nhiệm vụ khác. `plan.md` và `reports.md` vẫn chỉ là tài liệu cục bộ.

### 10. Code Changes — Thay đổi mã nguồn cuối nhiệm vụ

| Tệp/thành phần | Thay đổi thực tế |
| --- | --- |
| `DiscussionService.java` | Thêm các bản ghi `Topic`, `Reply`; truy vấn danh sách/chi tiết; kiểm tra văn bản, quyền và khoảng chờ; giao dịch tạo/sửa/quản lý; đếm xem. |
| `DiscussionController.java` | Thêm các đường dẫn `/thao-luan`; kiểm tra phiên và mã biểu mẫu; giữ lỗi nhập; chống cộng xem liên tục; chuyển tới bình luận mới. |
| `V4__discussion_topics_and_comments.sql` | Tạo `discuss`, `comments`, khóa ngoại và chỉ mục; không sửa dữ liệu truyện hoặc tài khoản. |
| `index.html` | Chỉ thêm liên kết Thảo luận trong thanh điều hướng dùng chung; giữ các sửa đổi bìa và tiêu đề có trước. |
| Ba mẫu thảo luận và `discussions.css` | Danh sách, tạo/sửa, chi tiết, trả lời, nút quản lý theo quyền; nội dung chỉ là văn bản. |
| `SchemaMigrationTest.java` | Tăng số bản thay đổi dự kiến từ 3 lên 4 và thêm hai ca kiểm thử nghiệp vụ thảo luận trên lược đồ riêng. |
| `DiscussionControllerTest.java` | Thêm bốn ca kiểm thử phiên/quyền, biểu mẫu lỗi, lượt xem và kết quả gửi trả lời. |
| `UiTemplateTest.java` | Thêm dữ liệu mẫu, kết xuất ba trang cho khách/người quản lý và kiểm tra xuất nội dung an toàn. |
| `scripts/verify-reading-ui.cjs` | Mở thêm các trang thảo luận ở ba kích thước; kiểm tra nội dung không tạo ảnh/mã lệnh và lưu ảnh chụp dưới `target`. |
| `plan.md`, `reports.md` | Ghi trạng thái chờ kiểm tra và báo cáo kỹ thuật cục bộ; không đưa lên Git. |

---

## Report CHAPTER-CONTENT — Đổi tên nội dung chương từ context sang content

**Date:** 2026-10-30  
**Status:** Đã sửa mã nguồn và kiểm thử; chờ khởi động lại ứng dụng để áp dụng lên dữ liệu chính  
**Branch:** sub

### 1. Task Overview — Tổng quan

Đổi tên cột và thuộc tính chương từ `context` (ngữ cảnh) sang `content` (nội dung) theo yêu cầu trực tiếp. Giữ nguyên nội dung, kiểu dữ liệu và quyền truy cập. Không làm các nhiệm vụ khác trong kế hoạch; không commit/push.

### 2. Implementation Summary — Nội dung triển khai

- `Chapter` đổi thuộc tính, hàm khởi tạo, phương thức đọc/ghi thành `content`, `getContent()`, `setContent()`.
- Các bộ điều khiển tạo/sửa/đọc chương, nhập tài liệu và truy vấn bảo vệ ảnh đều dùng tên mới.
- Biểu mẫu soạn chương gửi trường `content`; tên phần tử ẩn đổi thành `ceContent`. Cơ chế bản nháp vẫn lưu `html`, không thay khóa lưu trình duyệt.
- Tệp SQL tạo cơ sở dữ liệu mẫu dùng `content`. Thêm bản thay đổi V5 cho cơ sở dữ liệu đã có; giữ nguyên V1 đã được áp dụng trước đó để không làm sai mã kiểm tra lịch sử Flyway.

### 3. Methods and Algorithms — Phương pháp và thuật toán

`V5__rename_chapter_context_to_content.sql` kiểm tra các cột trong `information_schema.columns` thuộc lược đồ hiện hành:

1. Chỉ có `context`: chạy `ALTER TABLE chapters RENAME COLUMN context TO content`.
2. Chỉ có `content`: giữ nguyên, phù hợp cơ sở dữ liệu tạo từ bản SQL mới.
3. Có cả hai hoặc không có cả hai: báo lỗi rõ ràng và dừng, không tự ghép/xóa dữ liệu.

Đây là đổi tên cột bằng cơ chế PostgreSQL, không sao chép từng nội dung chương, không thêm cột nội dung rỗng để thay thế. Lệnh cần khóa bảng khi áp dụng; chưa đo thời gian trên dữ liệu lớn nên không đưa ra cam kết thời gian thực hiện.

`ContentController.ChapterInput` dùng trường chính thức `content` và `JsonAlias("context")` của Jackson: yêu cầu JSON cũ vẫn được nhận để tương thích, mã nghiệp vụ và phản hồi `Chapter` dùng tên mới. Biểu mẫu web hiện chỉ gửi `content`; cần tải lại trang soạn cũ sau khi ứng dụng khởi động lại.

### 4. Design Decisions — Quyết định thiết kế

Không sửa V1 vì bản thay đổi đã áp dụng được Flyway kiểm tra tính toàn vẹn. Dùng V5 để nâng cấp dữ liệu cũ và cho phép bản SQL tạo mới dùng tên đúng. Không tự xử lý trường hợp hai cột tồn tại vì chưa thể xác định bản nội dung cần giữ.

Đổi cả truy vấn trong `ImageCleanupStore.referenced()` để công cụ dọn ảnh tiếp tục nhận ra liên kết trong nội dung chương, kể cả chương bị ẩn. Không gọi xóa hoặc tải ảnh R2 trong lần làm này.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu

Biểu mẫu `chapter-form.html` ghi nội dung soạn vào trường ẩn `content` → `ProductController` làm sạch nội dung bằng `ChapterContent.sanitize()` → `Chapter.setContent()` → ánh xạ Hibernate tới `chapters.content`. Chiều đọc dùng `getContent()` cho nội dung, khối hiển thị và số từ. Nhập tài liệu qua `PdfImportController` cũng dùng `setContent()`.

Khi khởi động lại ứng dụng, Flyway chạy V5 trước bước Hibernate kiểm tra cấu trúc bảng. Chưa khởi động lại ứng dụng đang dùng hoặc chạy đổi tên trực tiếp trên các bảng chính trong lần thực hiện này.

### 6. Files Changed — Các file thay đổi

Tệp mới: `src/main/resources/db/migration/V5__rename_chapter_context_to_content.sql`.

Tệp sửa: `Database/ranobe_web.sql`, `src/main/java/main/entity/Chapter.java`, các bộ điều khiển `ContentController.java`, `ProductController.java`, `PdfImportController.java`, `src/main/java/main/service/ImageCleanupStore.java`, `src/main/resources/templates/chapter-form.html`, `src/test/java/main/SchemaMigrationTest.java`, `scripts/verify-reading-ui.cjs` và báo cáo cục bộ này. Không sửa `.gitignore` hoặc đánh dấu các nhiệm vụ chưa làm trong `plan.md`.

### 7. Testing and Validation — Kiểm thử

Chạy với `$env:RUN_DATABASE_TESTS='true'`:

```text
mvn -q -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true test
node scripts/verify-reading-ui.cjs
git diff --check
```

- **57 kiểm thử đạt, 0 lỗi, 0 bỏ qua.** Kiểm thử PostgreSQL dùng lược đồ tạm riêng rồi dọn đúng lược đồ đó; không sửa bảng dữ liệu chính.
- Kiểm tra chuyển đổi từ tên cũ, giữ nguyên nội dung tiếng Việt và bỏ tên cột cũ sau chuyển đổi. Truy vấn bảo vệ ảnh nhận ra ảnh đang nằm trong `content` và không nhận nhầm khóa ảnh khác.
- Hai ca mới kiểm tra cơ sở dữ liệu đã dùng `content`, và trường hợp hai cột cùng tồn tại phải dừng mà giữ nguyên cả hai nội dung.
- Kiểm thử Hibernate trên cơ sở dữ liệu mới xác nhận ánh xạ `Chapter` khớp sau năm bản thay đổi.
- Kiểm tra Edge đạt: khi lưu chương, dữ liệu gửi có `content` chứa nội dung bản nháp, không còn trường `context`; các tình huống khôi phục bản nháp, lưu lỗi/lưu thành công và chức năng đọc trước đó vẫn đạt.
- Kiểm tra chênh lệch không có lỗi khoảng trắng. Chưa kiểm tra thủ công trên ứng dụng đang chạy với dữ liệu chính.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến

Ứng dụng đang chạy phiên cũ vẫn dùng cấu trúc cũ cho tới khi khởi động lại. Không chạy SQL đổi tên riêng trong lúc phiên ứng dụng cũ còn hoạt động. Bản sơ đồ đã xuất trước lần đổi tên này vẫn là ảnh chụp cấu trúc cũ; không tự ghi đè `ERD.png` của người dùng.

### 9. Final Result — Kết quả cuối cùng

Đã sửa đồng bộ mã nguồn và chuẩn bị V5 đổi tên an toàn, kiểm thử cục bộ đạt. Người dùng khởi động lại ứng dụng để tự áp V5 lên cơ sở dữ liệu chính. Không commit, không push; không đánh dấu mục 15 hoàn thành vì yêu cầu đổi tên này không phải nhiệm vụ sửa trường cho phép NULL.

### 10. Code Changes — Thay đổi mã nguồn cuối nhiệm vụ

| Thành phần | Thay đổi |
| --- | --- |
| `Chapter` | Đổi thuộc tính và phương thức đọc/ghi thành `content`. |
| `ProductController`, `ContentController`, `PdfImportController` | Dùng trường/phương thức mới khi tạo, sửa, đọc và nhập chương; giữ tên JSON cũ làm bí danh đầu vào. |
| `chapter-form.html` | Đổi trường gửi nội dung và tên phần tử ẩn; giữ nguyên cách lấy HTML từ vùng soạn. |
| `ImageCleanupStore` | Tra cứu liên kết ảnh trong `chapters.content`. |
| V5 và `Database/ranobe_web.sql` | Đổi tên cho dữ liệu đã có; dùng tên đúng khi tạo mới. |
| `SchemaMigrationTest` | Kiểm tra năm bản thay đổi, dữ liệu giữ nguyên, tên đã đổi, bảo vệ ảnh, tên mới đã có và xung đột hai cột. |
| `verify-reading-ui.cjs` | Kiểm tra dữ liệu gửi thực sự có `content` và không còn `context`. |

---

## Report HOST-RENDER — Chuẩn bị triển khai Render

**Date:** 2026-10-30  
**Status:** Đã tạo cấu hình; kiểm thử bị chặn, chưa triển khai  
**Branch:** main — theo nhánh người dùng đã chuyển và push trước đó

### 1. Task Overview — Tổng quan

Chuẩn bị chạy ứng dụng Java Spring Boot trên Render, dùng PostgreSQL Neon và ảnh trên Cloudflare R2. Không commit/push hoặc sửa `.gitignore`; giữ nguyên cấu hình chạy máy cá nhân đang được Git bỏ qua.

### 2. Implementation Summary — Nội dung triển khai

Thêm `Dockerfile`, `.dockerignore` và `src/main/resources/application-render.properties`. Docker, công cụ đóng gói môi trường chạy ứng dụng, dùng Maven/Java 17 để tạo tệp Java thực thi rồi chuyển tệp sang môi trường Java chạy riêng. Cấu hình `render` lấy thông tin kết nối từ biến môi trường trên dịch vụ.

### 3. Methods and Algorithms — Phương pháp và thuật toán

Không thêm thuật toán nghiệp vụ. Quá trình đóng gói gồm hai giai đoạn: `mvn -B -DskipTests package`, sau đó sao chép tệp `project-web-0.0.1-SNAPSHOT.jar` sang ảnh Java 17 chỉ dùng để chạy. Lệnh bỏ kiểm thử chỉ dùng khi đóng gói Docker; không được hiểu là đã kiểm thử thành công.

`.dockerignore` chỉ cho `pom.xml` và `src/main` vào ngữ cảnh đóng gói, loại riêng cấu hình máy cá nhân `application.properties`, `application-local.properties`, các tệp `.env` và bản sao lưu. Bản Docker không lấy cấu hình chứa mật khẩu trên máy. Chưa xác minh bằng Docker thực tế vì máy không có lệnh Docker.

### 4. Design Decisions — Quyết định thiết kế

Giữ nguyên cấu hình cục bộ để không làm mất cách chạy hiện tại; thêm hồ sơ cấu hình `render` được bật trong Docker thay vì thay cấu hình mặc định. Môi trường chạy dùng tài khoản hệ điều hành riêng thay vì tài khoản quản trị. Java giới hạn phần heap ở 60% bộ nhớ môi trường; đây là cấu hình khởi đầu, chưa chứng minh đủ cho mọi tài liệu PDF trên gói 512 MB.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu

Render xây ảnh từ mã GitHub → bật `SPRING_PROFILES_ACTIVE=render` → ứng dụng lắng nghe `0.0.0.0` tại cổng `PORT`, mặc định 10000 → nhận thông tin PostgreSQL và R2 qua biến môi trường. Kết nối PostgreSQL tối đa ba, tối thiểu không giữ kết nối rỗi; phiên web dùng cookie bảo mật trên HTTPS. Hibernate chỉ kiểm tra cấu trúc, Flyway quản lý cập nhật như trước.

Biến cần đặt trong Render: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `R2_ACCOUNT_ID`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_BUCKET`, `R2_PUBLIC_URL`. URL PostgreSQL cần dạng JDBC có mã hóa, ví dụ `jdbc:postgresql://HOST/neondb?sslmode=require`, không đặt mật khẩu trong URL. Chưa thu thập hoặc điền mật khẩu Neon, chưa thử kết nối Neon từ mã ứng dụng.

### 6. Files Changed — Các file thay đổi

- `Dockerfile`: đóng gói hai giai đoạn, bật cấu hình Render, tài khoản chạy và giới hạn heap Java.
- `.dockerignore`: giới hạn tệp đóng gói và loại cấu hình bí mật cục bộ.
- `src/main/resources/application-render.properties`: cấu hình cổng, PostgreSQL, R2, tải tài liệu và phiên web cho Render.
- `reports.md`: báo cáo tiến độ cục bộ này.

### 7. Testing and Validation — Kiểm thử

Đã gọi `mvn -q -o -Dmaven.compiler.proc=none -Dnet.bytebuddy.experimental=true package` với `RUN_DATABASE_TESTS=true`, nhưng quyền truy cập Maven bị chặn (`Access is denied` / `LocalRepositoryNotAccessibleException`). Thử yêu cầu chạy với quyền cần thiết bị hệ thống duyệt tự động từ chối thực hiện vì phiên đăng nhập công cụ không làm mới được. Đây là lỗi duyệt quyền; không có kết quả kiểm thử hoặc đóng gói thành công cho lần thay đổi này. Không lách bước duyệt.

`git diff --check` không báo lỗi trên thay đổi đã theo dõi; ba tệp mới đang chưa được Git theo dõi nên lệnh này chưa chứng minh kiểm tra toàn bộ tệp mới. Kiểm tra trạng thái xác nhận chỉ có ba tệp triển khai mới; cấu hình cục bộ không bị sửa. Tra cứu tài liệu trực tuyến cũng bị lỗi phiên đăng nhập, nên chưa xác minh trực tuyến các chi tiết dịch vụ ở lượt này.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến

Cần đăng nhập lại công cụ rồi chạy kiểm thử/đóng gói. Cần xây và chạy Docker thật hoặc xác minh nhật ký Render trước khi khẳng định triển khai thành công. Chưa tạo dịch vụ Render hoặc cấu hình biến trên tài khoản người dùng. Không dùng kết quả kiểm thử của lần đổi tên cột làm bằng chứng cho lần chuẩn bị hosting này.

### 9. Final Result — Kết quả cuối cùng

Tệp cấu hình đã tạo cục bộ; dừng ở bước kiểm thử bị chặn. Không đánh dấu nhiệm vụ nào trong `plan.md` hoàn thành, không commit/push. Chưa có website được triển khai từ lần thay đổi này.

### 10. Code Changes — Thay đổi mã nguồn cuối nhiệm vụ

Chỉ thêm ba tệp triển khai nêu trên; không đổi lớp Java, cấu trúc cơ sở dữ liệu, nội dung truyện, mật khẩu cục bộ, ảnh hay quyền người dùng. Hồ sơ Render độc lập với cấu hình máy cá nhân.

---

## Report 16 — Nhập thử một tập EPUB lên Neon

**Date:** 2026-10-30  
**Status:** Đã nhập tập thử và thêm 39 truyện; còn 8 tệp chờ duyệt thông tin, chưa đánh dấu hoàn thành cả thư mục  
**Branch:** main — không thay mã nguồn, không commit/push

### 1. Task Overview — Tổng quan

Người dùng yêu cầu chọn đúng một tệp có tên rõ ràng trong `data`, nhập lên Neon của web online, tài khoản admin và công khai, để kiểm tra trước. Không xử lý các tệp mất tên hoặc nhập hàng loạt.

### 2. Implementation Summary — Nội dung triển khai

Chọn `[Kindlesaigon.vn] HUNG KHI HOAN MY - Higashino Keigo.epub`. Thông tin trong EPUB xác nhận tác phẩm Hung khí hoàn mỹ, tác giả Higashino Keigo, dịch giả Vũ Bích Lệ Thủy. Mục lục có 43 chương và một liên kết bìa; ảnh bìa dùng đúng `cover.jpg` trong sách, đã xem ảnh trước khi tải.

Đã tạo trên Neon: truyện `product_id=8`, tập `volume_id=45`, chương `chapter_id=294..336`. Người đăng là `admin` có `user_id=17`, vai trò ADMIN đang hoạt động; truyện PUBLIC. Tập đặt tên Tập 1 theo quy ước mỗi tệp là một tập đã chốt. Thể loại dùng sẵn Mystery và Sports, phù hợp hai chủ đề Trinh thám/Thể thao trong EPUB; không tạo thêm thể loại.

### 3. Methods and Algorithms — Phương pháp và thuật toán

Python dùng `zipfile` và `xml.etree.ElementTree` đọc EPUB trực tiếp, không cần chuyển qua PDF. Mục lục `nav.xhtml` xác định thứ tự; nội dung nằm trong `index_split_000.html` đến `index_split_042.html`. Loại tiêu đề lặp đầu chương vì đã có cột tiêu đề riêng; giữ các đoạn, in đậm/in nghiêng, ngắt dòng, ngắt cảnh và hai chú thích cuối chương. Không đưa trang bìa thành chương 44.

Nội dung xuất dạng HTML giới hạn, có dấu `<!--h-->` theo cơ chế `ChapterContent` hiện có, chuẩn hóa Unicode NFC. Kiểm tra từng chương: bỏ thẻ, giải mã thực thể HTML và so toàn bộ chữ với phần thân EPUB sau khi chuẩn hóa khoảng trắng; kết quả khớp cả 43 chương. Có 2.829 khối đoạn/ngắt cảnh/chú thích, 81.176 đơn vị tách bằng khoảng trắng theo cách đếm sơ bộ trên nguồn, không gọi đó là phép phân tích từ ngữ tiếng Việt chuyên dụng.

Tạo mã băm SHA-256 cho tệp nguồn để nhận diện lần nhập, và MD5 cho nội dung UTF-8 từng chương chỉ nhằm kiểm tra tính toàn vẹn, không dùng cho mật khẩu hoặc bảo mật. Việc đọc/chuyển đổi và đối chiếu duyệt nội dung theo tổng số ký tự; bộ nhớ giữ toàn bộ một tập trong lần thử này.

### 4. Design Decisions — Quyết định thiết kế

Không dùng tên tệp làm thông tin duy nhất: đối chiếu tiêu đề, tác giả, mục lục và ảnh bìa bên trong EPUB. Giữ tên dịch giả trong mô tả tập, không đưa nhầm vào tác giả hoặc họa sĩ. Trường họa sĩ chưa có nguồn xác nhận nên để NULL. Mô tả truyện lấy đoạn giới thiệu sách, không sao chép các ghi chú xử lý tệp của người làm EPUB vào phần giới thiệu.

Ảnh tải bằng `R2StorageService.uploadBytes()` hiện có lên `covers/5c80d317-504d-4a80-b019-799494fe0020.jpg`; truyện và tập cùng tham chiếu ảnh này. Không tải ảnh mới khác hoặc xóa ảnh cũ. Tệp EPUB nguồn không bị thay đổi.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu

EPUB → đọc mục lục và nội dung → dữ liệu UTF-8 đã đối chiếu → ảnh bìa lên R2 → giao dịch PostgreSQL trên `neondb` → đọc lại bằng kết nối độc lập.

Giao dịch nhập kiểm tra đúng tên cơ sở dữ liệu/tài khoản kết nối, admin còn hoạt động, đúng hai thể loại đang hiện, đủ 43 chương và chưa có truyện trùng tiêu đề/tác giả. Khóa tư vấn trong giao dịch dùng mã băm tệp để tránh hai lần chạy cùng bộ nhập. Thêm sản phẩm, liên kết thể loại, tập và chương trong cùng giao dịch; mỗi chương kiểm tra MD5 ngay sau ghi. Có lỗi sẽ dừng qua `ON_ERROR_STOP` và không commit dữ liệu dở dang. Không thực thi thay đổi cấu trúc bảng hoặc ghi vào cơ sở dữ liệu localhost.

### 6. Files Changed — Các file thay đổi

Không sửa mã nguồn ứng dụng, `.gitignore` hoặc `plan.md`. Dữ liệu trung gian và công cụ dùng một lần nằm trong `target/epub-trial-hung-khi/`, thư mục đã được bỏ qua trong Git: `book.json`, `expected.json`, `cover.jpg`, `cover-url.txt`, `UploadCover.java`, `lib/`, `import-one-volume.sql`, `import-receipt.txt`, `persisted-chapters.json`, `validation.txt`. Báo cáo này chỉ lưu cục bộ trong `reports.md`.

### 7. Testing and Validation — Kiểm thử

- Đọc EPUB bằng Python: 43 tiêu đề duy nhất, khớp thứ tự mục lục, không ký tự thay thế Unicode; toàn bộ chữ thân chương và hai chú thích được bảo toàn sau đối chiếu.
- `UploadCover.java` chạy bằng Java 25 với các thư viện đã có trong tệp ứng dụng: tải ảnh R2 thành công và trả đường dẫn công khai.
- `psql -X -w ... -v ON_ERROR_STOP=1 -f target/epub-trial-hung-khi/import-one-volume.sql`: kết thúc mã 0, báo `Imported product 8, volume 45, chapters 43`, có COMMIT. Mật khẩu chỉ truyền qua môi trường tiến trình và xóa khỏi môi trường phiên lệnh sau chạy; không ghi vào tệp trung gian/báo cáo.
- Đọc lại từ Neon ra `persisted-chapters.json` rồi so với `expected.json`: đủ 43 chương, cả 43 tên/thứ tự/mã băm nội dung khớp. Tổng nội dung lưu là 503.215 byte UTF-8. Không lấy số lượng làm bằng chứng duy nhất cho tính toàn vẹn.
- Hai lần thử mở `https://ranobe-web.onrender.com/truyen/8/hung-khi-hoan-my` bằng Python `urllib.request` đều hết thời gian chờ đọc 45 giây; chưa lấy được trang để xác nhận giao diện. Không kết luận nguyên nhân là Render ngủ hay mã ứng dụng chậm vì chưa có nhật ký máy chủ tương ứng. Việc nhập Neon và đối chiếu sau COMMIT đã đạt độc lập với kiểm tra trang web này.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến

Chỉ làm một tập thử EPUB; chưa kiểm tra hoặc nhập các tệp MOBI, chưa xây chức năng nhập EPUB/MOBI trong ứng dụng. Đây là luồng nhập một lần có xác nhận của người dùng. Cần người dùng xem trải nghiệm đọc thực tế trước khi áp dụng cho toàn bộ thư mục. Tệp sách đơn lẻ không có số tập gốc nên Tập 1 là cách đặt cho lần nhập thử, không khẳng định thông tin xuất bản gốc.

### 9. Final Result — Kết quả cuối cùng

Đã nhập một tập công khai bằng admin trên Neon và xác minh dữ liệu sau COMMIT. Không đánh dấu nhiệm vụ 16 hoàn thành trong `plan.md`, vì người dùng mới yêu cầu thử một tập. Không commit/push hoặc thay những truyện/tập đã có.

### 10. Code Changes — Thay đổi mã nguồn cuối nhiệm vụ

Không thay mã sản phẩm. Chỉ dùng công cụ chuyển đổi một lần và lớp tải ảnh sẵn có; kết quả lưu thêm trong các bảng `products`, `product_categories`, `volumes`, `chapters` trên Neon và một ảnh bìa mới trên R2.


#### [BOOK-a8e9ec2befb1] Ảo Dạ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Ao Da - Higashino Keigo.mobi`; SHA-256: `a8e9ec2befb1a7eec4d94d3ac2ac82c182ef859e0307e769f4ef345fe5d4a340`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `9`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 12. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/9/truyen
- Mã tập: `46`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-7502754d8a6a] Bạch Dạ Hành

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/bach-da-hanh-mobi.mobi`; SHA-256: `7502754d8a6a2a68fa3c1f6a32f5cb1028fc34e58c070f47c609e1782a6751b2`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `10`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 13. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/10/truyen
- Mã tập: `47`; ảnh tải mới: 3. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-d709b1ddec41] Biến Thân

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Biến Thân - Higashino Keigo & Cẩm Hương (dịch).mobi`; SHA-256: `d709b1ddec4188fec9a17dde7c2b8a61ee6192ad651683e1e6a8754d1d0c11e0`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `11`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 41. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/11/truyen
- Mã tập: `48`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-ba963a179bd5] Cáp Treo Tình Yêu

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/cap-treo-tinh-yeu-mobi.mobi`; SHA-256: `ba963a179bd5130764ecfa638aa4718fbde893462f4919e18d340fd92eccd9a3`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `12`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 7. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/12/truyen
- Mã tập: `49`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-766d18090a38] Cuộc Diễu Hành Thầm Lặng

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Cuộc Diễu Hành Thầm Lặng - Higashino Keigo & Vương Hải Yến (dịch) & Bảo Lam Anh (dịch).mobi`; SHA-256: `766d18090a388a186e71567ea9b0f911e49974e7949c55f691405b8ae4d9aacd`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `13`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 50. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/13/truyen
- Mã tập: `50`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-0fae4409ef52] Cánh Kỳ Lân

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Cánh Kỳ Lân - Higashino Keigo & Phương Dung (dịch).mobi`; SHA-256: `0fae4409ef52fc59661e49eac6b13b2fb3c2447c3f927e271bb25e935c7b7038`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `14`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 36. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/14/truyen
- Mã tập: `51`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-ea4ca6b3902f] Điều Kỳ Diệu Của Tiệm Tạp Hóa Namiya

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Dieu Ky Dieu Cua Tiem Tap Hoa Namiya - Higashino Keigo.mobi`; SHA-256: `ea4ca6b3902fb24c3ec138142991679ea7e7273258fdd1d292a71695c71d6ed3`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `15`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 7. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/15/truyen
- Mã tập: `52`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-eaff479cdf81] Dữ liệu bạch kim

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Du lieu bach kim - Keigo Higashino.mobi`; SHA-256: `eaff479cdf810b74e1a7826cf29dad36beeffa2ae00f7229664cec17c9fa44f0`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `16`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 50. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/16/truyen
- Mã tập: `53`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-f82bf32a4511] Even If These Tears Disappear Tonight

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Even-If-These-Tears-Disappear-Tonight.mobi`; SHA-256: `f82bf32a45118a251e4a4415db785666aeee3e62a533b2c31fe6d938a21f97d4`. Giữ nguyên tệp gốc.
- Tác giả: Misaki Ichijo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `17`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 7. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/17/truyen
- Mã tập: `54`; ảnh tải mới: 7. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-b99060c79939] Giấc mơ tiên tri

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Giấc Mơ Tiên Tri - Higashino Keigo [eBook].mobi`; SHA-256: `b99060c799393aa85f29ffa880931a5143c350fb14ec47850bc88f51a4b5ea3a`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `18`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 5. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/18/truyen
- Mã tập: `55`; ảnh tải mới: 2. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-3223ea665488] Hoa Mộng Ảo

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Hoa Mong Ao - Higashino Keigo.mobi`; SHA-256: `3223ea6654880bf09b93e8ad18afc195b7aa18c5e34aef90946df3f13efaf1fc`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `19`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 42. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/19/truyen
- Mã tập: `56`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-8f1a9c4a1bef] Khách Sạn Mặt Nạ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Khách Sạn Mặt Nạ - Higashino Keigo & Lam Ngọc (dịch).mobi`; SHA-256: `8f1a9c4a1bef53858ae88d22ba0c5cd7d10f79974ec922b3fab2381d57cf416e`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `20`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 44. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/20/truyen
- Mã tập: `57`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-87f53a481cc7] Khách Sạn Mặt Nạ Tập 2 - Đêm Trước Lễ Hội Hóa Trang

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Khách Sạn Mặt Nạ Tập 2 - Đêm Trước Lễ Hội Hóa Trang - Higashino Keigo & Lam Ngọc (dịch).mobi`; SHA-256: `87f53a481cc78a54c0965ad9818ed116922d0e705fd8d75fbc77113e3b61e264`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `21`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 4. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/21/truyen
- Mã tập: `58`; ảnh tải mới: 2. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-7a3e457c4099] HUNG KHÍ HOÀN MỸ

- Ngày: 2026-10-30. Trạng thái: đã có trên Neon, bỏ qua để không nhập trùng.
- Nguồn: `data/Kindlesaigon.vn_-HUNG-KHI-HOAN-MY-Higashino-Keigo.mobi`; SHA-256: `7a3e457c4099fa8b813bde0f1f2f39ec80906dff7a88f8cb2f612b03d39c0bb1`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `8`; số tập nhập mới: 0; số chương theo tệp nguồn đã kiểm tra: 43. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/8/truyen
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-3daea53a9137] Ma Nữ của Laplace

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Ma Nu cua Laplace - Higashino Keigo.mobi`; SHA-256: `3daea53a9137590bd78fa112c0b8c89ba7d50fb7ea00e8e944eff2d36f63a1c3`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `22`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 41. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/22/truyen
- Mã tập: `59`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-880ed1197afd] Ma Thuật Bị Cấm

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Ma Thuat Bi Cam - Higashino Keigo.mobi`; SHA-256: `880ed1197afd2abf69b8dc3357e39ea2407c4fed4cb6193f4953444b25d12ec1`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `23`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 34. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/23/truyen
- Mã tập: `60`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-5387e8412d97] Newcomer - Người mới đến

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Newcomer - Nguoi moi den - Keigo Higashino.mobi`; SHA-256: `5387e8412d9715946c28ee24c8cb482d502f491ea08244c2dec77b9d8db247fc`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `24`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 9. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/24/truyen
- Mã tập: `61`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-4ab91a26c5fb] Ngôi Nhà Của Người Cá Say Ngủ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Ngoi Nha Cua Nguoi Ca Say Ngu - Higashino Keigo.mobi`; SHA-256: `4ab91a26c5fb63ee7a959c2e10cd42e5c9861b685df08ff2ae765b6d657a6199`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `25`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 8. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/25/truyen
- Mã tập: `62`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-eac63cc28485] Rừng Na Uy

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/nhasachmienphi-rung-na-uy.mobi`; SHA-256: `eac63cc28485589a060c239a50617011e3d0815439f1282b6d1434b540f3db8b`. Giữ nguyên tệp gốc.
- Tác giả: Haruki Murakami. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `26`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 12. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/26/truyen
- Mã tập: `63`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-ffe5db08951e] Nhà Ảo Thuật Đen Và Vụ Án Mạng Tại Thị Trấn Không Tên

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Nhà Ảo Thuật Đen Và Vụ Án Mạng Tại Thị Trấn Không Tên - Higashino Keigo & Mai Khanh (dịch).mobi`; SHA-256: `ffe5db08951ec3d70e5ace86e14db2691310d99785221090360f326e9369027b`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `27`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 30. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/27/truyen
- Mã tập: `64`; ảnh tải mới: 2. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-5a3d900f571e] Những Kẻ Đáng Ngờ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Những Kẻ Đáng Ngờ - Higashino Keigo & Vương Hải Yến (dịch).mobi`; SHA-256: `5a3d900f571e60a0eccf857e3c66f8749c9cf8613ab35768f14d555a423dd048`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `28`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 8. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/28/truyen
- Mã tập: `65`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-f39421969024] Nỗi Niềm Của Thám Tử Galileo

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Noi Niem Cua Tham Tu Galileo - Higashino Keigo.mobi`; SHA-256: `f3942196902486f50698f5dcff670fdec6182117529685d47356cb5f0f5d07d5`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `29`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 5. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/29/truyen
- Mã tập: `66`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-d4783dc3aa22] Phía Sau Nghi Can X

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Phia-Sau-Nghi-Can-X-Higashino-Keigo.mobi`; SHA-256: `d4783dc3aa22c7944b83a9eb6e42b712e56be1abb4f10a58430c76c5fb0e2467`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `30`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 19. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/30/truyen
- Mã tập: `67`; ảnh tải mới: 22. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-9f1c42584080] Phố tan màn đêm

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Pho tan man dem - Keigo Higashino.mobi`; SHA-256: `9f1c4258408079609caacc5fac1a1eab1e163b82906bdf4e97c743956758b5a7`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `31`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 40. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/31/truyen
- Mã tập: `68`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-8c95b14b8578] Phương Trình Hạ Chí

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Phuong Trinh Ha Chi - Higashino Keigo.mobi`; SHA-256: `8c95b14b85787c8bfd90ccf8bfac5295285406c3084262221dd0edbd7a365198`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `32`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 64. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/32/truyen
- Mã tập: `69`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-d9a5e86edbbd] Sau Giờ Học

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Sau Giờ Học - Higashino Keigo & Thúy Hương (dịch).mobi`; SHA-256: `d9a5e86edbbd2c702f15ee3957ddf3f03365c308a68986a49dd6ff58ac07122b`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `33`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 19. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/33/truyen
- Mã tập: `70`; ảnh tải mới: 6. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-27722a863440] Sự Cứu Rỗi Của Thánh Nữ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Su Cuu Roi Cua Thanh Nu - Higashino Keigo.mobi`; SHA-256: `27722a86344013ddf1c9c7196ace205dd03ee9e444086ea5e574997437edad4a`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `34`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 33. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/34/truyen
- Mã tập: `71`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-865d222d540a] Tên Của Trò Chơi Là Bắt Cóc

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Ten Cua Tro Choi La Bat Coc - Higashino Keigo.mobi`; SHA-256: `865d222d540abb2e6ed8c16b3e09b25d22c6cda8421669640be2dbb08acdaf9c`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `35`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 21. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/35/truyen
- Mã tập: `72`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-1dbc45355e56] Thánh Giá Rỗng

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Thanh Gia Rong - Higashino Keigo.mobi`; SHA-256: `1dbc45355e5668a9061b3fdc45bfccd1077f9887714cba1f2d72939fa15c600c`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `36`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 25. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/36/truyen
- Mã tập: `73`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-2f00058daeb5] Thanh Gươm Do Dự

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Thanh Gươm Do Dự - Higashino Keigo & Mộc Miên (dịch).mobi`; SHA-256: `2f00058daeb59c1cd1165c9b075294ae233633a2163bf8c66506511a3e51895c`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `37`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 50. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/37/truyen
- Mã tập: `74`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-775855b50606] Thiên nga và dơi

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Thien nga va doi - Keigo Higashino.mobi`; SHA-256: `775855b50606208acb28a87508fadf3279f97c2e7f643a6a489364e631a45cfb`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `38`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 49. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/38/truyen
- Mã tập: `75`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-8dbea0dd1807] THƯ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/THU - Higashino Keigo.mobi`; SHA-256: `8dbea0dd1807f995c42509c5147d2ac94f4f96884ee068da578aeea9e86fb8cb`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `39`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 15. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/39/truyen
- Mã tập: `76`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-2ed58c5a29b2] Trước Khi Nhắm Mắt

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Truoc Khi Nham Mat - Higashino Keigo.mobi`; SHA-256: `2ed58c5a29b2d4b163f19ebe9e077fb275716ea4f2257ca67d339e5ee61441ec`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `40`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 44. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/40/truyen
- Mã tập: `77`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-c142b757f8c3] Trái Tim Của Brutus

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Trái Tim Của Brutus - Higashino Keigo.mobi`; SHA-256: `c142b757f8c3f8714cfc292716ec9c2355faec223916d394984ea3615041e526`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `41`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 6. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/41/truyen
- Mã tập: `78`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-648403030edf] Trứng Chim Cúc Cu Này Thuộc Về Ai

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Trứng Chim Cúc Cu Này Thuộc Về Ai - Higashino Keigo & Nguyễn Hải Hà (dịch).mobi`; SHA-256: `648403030edfb23620daeb88c07e616a5bef07da4fc2eacab95510c43fbb38a5`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `42`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 42. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/42/truyen
- Mã tập: `79`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-082facabfdf7] Vụ Án Mạng Bên Hồ

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Vụ Án Mạng Bên Hồ - Higashino Keigo & Nguyễn Phương Chi (dịch).mobi`; SHA-256: `082facabfdf7e72176a7f51dab7544629aefbc78e4163384062313c91a79c2ed`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `43`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 4. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/43/truyen
- Mã tập: `80`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-b3fcd43629c8] Vụ Án Mạng Ở Lữ Quán Kairotei

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Vụ Án Mạng Ở Lữ Quán Kairotei - Higashino Keigo.mobi`; SHA-256: `b3fcd43629c81a2d594e52777d11b2103f97160ddac382432ac42e89b2222a74`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `44`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 32. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/44/truyen
- Mã tập: `81`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-b157a11fde04] Vụ Án Mạng Ở Nhà Khách Núi Hakuba

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Vụ Án Mạng Ở Nhà Khách Núi Hakuba - Higashino Keigo & Kuroi Neko (dịch).mobi`; SHA-256: `b157a11fde04990981a8cab615d6eeceb15cc500b0114ae269465ef85bc41716`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `45`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 12. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/45/truyen
- Mã tập: `82`; ảnh tải mới: 7. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-99d02d7a3630] Ác Ý

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Ác Ý - Higashino Keigo.mobi`; SHA-256: `99d02d7a363053ad2fd515bb57c7de0ee7bb11ce73f2db2cc323c289e9cec205`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `46`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 10. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/46/truyen
- Mã tập: `83`; ảnh tải mới: 1. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-fb87b2d2269b] Sau những cánh cửa đóng kín

- Ngày: 2026-10-30. Trạng thái: đã nhập và đọc lại xác minh trên Neon.
- Nguồn: `data/Sau nhung canh cua dong kin - Shannon McKenna.epub`; SHA-256: `fb87b2d2269bd7cce3b6d3eaf0c81fb712265e2a8e63605e613212ce275ac11a`. Giữ nguyên tệp gốc.
- Tác giả: Shannon McKenna. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `47`; số tập nhập mới: 1; số chương theo tệp nguồn đã kiểm tra: 29. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/47/truyen
- Mã tập: `84`; ảnh tải mới: 4. Đã kiểm tra đủ tiêu đề, thứ tự và MD5 của từng nội dung sau COMMIT bằng kết nối riêng.
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


#### [BOOK-f0792393c825] HUNG KHÍ HOÀN MỸ

- Ngày: 2026-10-30. Trạng thái: đã có trên Neon, bỏ qua để không nhập trùng.
- Nguồn: `data/[Kindlesaigon.vn] HUNG KHI HOAN MY - Higashino Keigo.epub`; SHA-256: `f0792393c8257b260bf8c3a4d633b5212d5f7eb07621d99ee5c98c2cb18fc9da`. Giữ nguyên tệp gốc.
- Tác giả: Higashino Keigo. Người đăng: `admin` (`user_id=17`); công khai.
- Mã truyện: `8`; số tập nhập mới: 0; số chương theo tệp nguồn đã kiểm tra: 43. Không suy ra sách gốc đã đầy đủ chỉ từ số chương trong file.
- Link: https://ranobe-web.onrender.com/truyen/8/truyen
- Thể loại Keigo được xếp tạm Mystery theo xác nhận người dùng; các sách khác theo nhóm đã đối chiếu. Không sửa mã nguồn hoặc các truyện đã có; không commit/push Git. Chưa kiểm tra toàn bộ giao diện đọc trên Render.


### Tổng kết đợt nhập tiếp theo — 39 truyện mới

Theo xác nhận tiếp theo của người dùng, đã nhập lần lượt từng truyện lên Neon, tài khoản admin và công khai; sau mỗi truyện đọc lại kiểm tra rồi ghi ngay mục riêng ở trên. Không cần giữ pgAdmin hoặc Visual Studio Code mở. Không thực hiện commit/push mã nguồn.

**Kết quả:** 39 truyện mới, 39 tập mới, 979 chương mới, 85 ảnh bìa/minh họa mới trên Cloudflare R2. Bỏ qua hai tệp Hung khí hoàn mỹ (MOBI và EPUB) vì truyện đã được nhập thử trước đó; không nhân đôi hoặc ghi đè phiên bản đã có. Cộng với lần thử, đã đưa lên 40 tác phẩm khác nhau từ thư mục nguồn trong hai đợt.

**Cách thực hiện thực tế:** dùng thư viện `mobi` 0.4.1 (KindleUnpack) giải nén MOBI không mã hóa, Beautiful Soup đọc HTML; EPUB đọc mục lục NCX hoặc mục lục EPUB 3. Các thư viện chỉ tải vào `target/ebook-import-tools`, kiểm tra SHA-256 gói tải từ PyPI; không cài toàn hệ thống hoặc thêm phụ thuộc vào `pom.xml`. Công cụ một lần nằm trong `target/ebook-import-batch/prepare.py` và `ImportBooks.java`, không thuộc mã ứng dụng được push.

**Tách nội dung:** dùng mốc chương đã có trong mục lục, không chia theo số trang hay độ dài tùy ý. Khi sách liệt kê cả chương chính và các tiểu mục số, gộp tiểu mục vào đúng chương chính; ví dụ Ảo Dạ có 12 chương theo cấu trúc file, Bạch Dạ Hành có 13 phần chương trong file. Không khẳng định đây là số chương của mọi ấn bản hay rằng bản nguồn chắc chắn đầy đủ. Chương mở đầu/lời bạt có nội dung được giữ; nhãn rỗng/ngắn được gộp vào phần liền sau hoặc trước thay vì tạo chương không có nội dung. Đã bỏ các mục điều hướng/bìa khỏi chương đọc; chú thích, đoạn văn và ảnh trong các phần nội dung được giữ. Tiêu đề và tên tác giả đối chiếu trong thông tin sách; tên dịch giả được giữ khi nguồn có. Trường tác giả Keigo được thống nhất Higashino Keigo khi nguồn ghi Keigo Higashino.

**Thể loại:** sách Keigo tạm xếp Mystery theo xác nhận người dùng; thể loại phụ có trong nguồn được giữ khi có ánh xạ. Người dùng sẽ kiểm tra và chỉnh sau. Không tự tạo thể loại mới. Tác phẩm có ghi rõ Tập 2 trong tên giữ nhãn Tập 2, không gọi nhầm thành Tập 1; các file độc lập khác dùng Tập 1 theo quy ước mỗi file là một tập.

**Ảnh:** dùng đúng ảnh trong từng file nguồn, không tạo bìa mới bằng công cụ sinh ảnh. Tải qua `R2StorageService.uploadBytes()` hiện có; bìa và ảnh minh họa đều ở nhóm khóa `covers/UUID.ext` trên R2, ảnh minh họa được chèn trong HTML nội dung, không cần thêm hàng `chapter_images` riêng. Dùng bộ nhớ đệm theo SHA-256 để không tải trùng ảnh nếu có cùng nội dung. Các tham chiếu trong nội dung đã thay hoàn toàn thành đường dẫn công khai; không để đường dẫn file máy cá nhân hoặc mã giữ chỗ ảnh trên Neon.

**Giao dịch và kiểm tra:** mỗi sách một giao dịch PostgreSQL, dùng câu lệnh có tham số và tài khoản admin còn hoạt động. Kiểm tra tên/tác giả trùng trước khi tải ảnh và kiểm tra lại trong giao dịch; dừng nếu xảy ra lỗi ghi. Sau COMMIT, mở kết nối riêng, so đủ số chương, tiêu đề, thứ tự, MD5 nội dung UTF-8 và trạng thái hoàn thành/không ẩn. Sau toàn bộ đợt, chạy thêm `psql ... -f target/ebook-import-batch/verify-all.sql` rồi Python đối chiếu `neon-all.json` với dữ liệu chuyển đổi và ánh xạ ảnh: đạt cả 39 truyện / 39 tập / 979 chương; tất cả người đăng user_id=17, trạng thái PUBLIC. Tệp nguồn của các mục trong nhật ký có SHA-256 giữ nguyên.

**Kiểm tra ảnh công khai:** các yêu cầu HEAD thử nghiệm trả HTTPError; kiểm tra lại bằng GET như trình duyệt với User-Agent thông thường trả HTTP 200, image/jpeg cho ba bìa mẫu Ảo Dạ, Bạch Dạ Hành, Biến Thân. Không khẳng định đã xem toàn bộ trang/chương trên Render; kiểm tra dữ liệu độc lập đã đạt. File Even If These Tears Disappear Tonight giữ ngôn ngữ nguồn, không tự dịch.

**Báo cáo và Git:** 41 mục nhật ký nguồn (39 nhập mới, 2 bỏ qua vì đã có) đã có trong reports.md ngay sau mỗi kết quả. `git status --short --branch` chỉ hiện nhánh main theo dõi origin/main, không có mã nguồn thay đổi. Không sửa .gitignore, cấu hình, các sách đã có hoặc plan.md; không đánh dấu nhiệm vụ 16 hoàn thành vì còn các file dưới đây. Không có công việc tiếp tục ngầm sau khi kết thúc lượt.

#### Các tệp chưa nhập — cần người dùng duyệt

| Tệp | Lý do |
| --- | --- |
| `17401.mobi` | Tên tệp không rõ; để người dùng duyệt: Toi co hon the, nhung nho lai la Nu phan dien khet tieng o truong, phai lam sao day chu!?! |
| `5cm per second.mobi` | Tên trong sách thiếu hoặc chưa chuẩn: Unknown |
| `ac1.mobi` | Tên tệp không rõ; để người dùng duyệt: Song chung voi Nu hoang kieu ngao thoi cao trung hoa ra lai khong he kho chiu mot cach dang ngac nhien |
| `bimatnaoko-HIGASHINO-KEIGO.mobi` | Tên trong sách thiếu hoặc chưa chuẩn: bimatnaoko |
| `Higa.mobi` | Tên tệp không rõ; để người dùng duyệt: Đơn Phương |
| `Những gì em đã làm cho tôi, với 1 năm còn lại của cuộc đời.mobi` | Tên trong sách thiếu hoặc chưa chuẩn: Unknown |
| `sei.mobi` | Tên tệp không rõ; để người dùng duyệt: d88744e7de474f5696811eb32d811930 |
| `thanchet.mobi` | Tên tệp không rõ; để người dùng duyệt: Thieu nu duoc nuoi duong boi Than chet om chat thanh kiem den tuyen |

Các file này giữ nguyên tại data; không tự đoán tên/tác giả hoặc nhập dưới tên Unknown. Việc giải nén đã đọc được vài tên bên trong (như Đơn Phương trong Higa.mobi), nhưng vẫn để lại theo nguyên tắc người dùng yêu cầu kiểm tra tên trước.

---

## Report SEC-01 — Nâng bộ thư viện và xác minh bản dựng bảo mật

**Date:** 2026-11-16  
**Status:** Completed  
**Branch:** main

### 1. Task Overview — Tổng quan
- Xử lý cảnh báo thư viện người dùng cung cấp; dự án là Java/Maven nên hướng dẫn `npm audit fix` không áp dụng.
- Người dùng duyệt kế hoạch và đổi nhánh đích thành `main`; các lần push cách nhau ít nhất 5 phút.
- Giữ nguyên bốn tệp đang được người dùng chỉnh: `HomeController.java`, `ranking.html`, `SchemaMigrationTest.java`, `HomeControllerTest.java`; không đưa các thay đổi đó vào bản giao.

### 2. Implementation Summary — Nội dung triển khai
- `pom.xml`: chuyển Spring Boot 3.3.0 qua bước kiểm tra 3.5.16 rồi lên 4.0.9. Giữ mức Java đích 17.
- Đổi sang `spring-boot-starter-webmvc`, `spring-boot-starter-webmvc-test` và `spring-boot-starter-flyway` theo cấu trúc mô-đun Spring Boot 4.
- `SchemaVersionConfig`: cập nhật vị trí `FlywayConfigurationCustomizer` và `HibernatePropertiesCustomizer`; giữ cơ chế tiếp nhận cấu trúc cũ ở phiên bản 0 và chỉ xác minh cấu trúc bằng Hibernate.
- Đồng bộ thư viện Amazon bằng bảng phiên bản `software.amazon.awssdk:bom:2.59.1`; loại `netty-nio-client` khỏi S3 vì `R2StorageService` dùng `S3Client` đồng bộ với `ApacheHttpClient`.
- PDFBox 2.0.37; ghi đè HttpClient 5 lên 5.6.3 và HttpCore 5 lên 5.4.3 sau khi lần quét đầu phát hiện ba cảnh báo bổ sung.
- `.dockerignore`: cho phép lại `src/main/resources/db/migration/*.sql`, tránh loại bản chuyển đổi cấu trúc dữ liệu khỏi Docker build context.

### 3. Methods and Algorithms — Phương pháp và thuật toán
- **Quản lý phụ thuộc:** Maven hợp nhất bảng phiên bản của Spring Boot và Amazon, sau đó giải cây thư viện. Chỉ ghi đè riêng hai phiên bản Apache mà lần quét thực tế vẫn báo lỗi. Không nâng rời Spring Framework lên phiên bản lớn không tương thích với Spring Boot cũ.
- **Đối chiếu lỗ hổng:** xuất cây thư viện chạy thực tế bằng `dependency:tree`, duyệt các nút và khử trùng theo cặp tên Maven/phiên bản; gửi danh sách công khai tới `https://api.osv.dev/v1/querybatch`. Không gửi mã nguồn hoặc thông tin cấu hình. Độ phức tạp duyệt O(V + E), lưu O(V) với V thư viện và E quan hệ phụ thuộc.
- **Kiểm thử khởi động:** `ApplicationIntegrationTest.upgradedApplicationStartsAndServesPublicPages()` chỉ chấp nhận PostgreSQL trên `localhost` hoặc `127.0.0.1`; tạo vùng cấu trúc `codex_app_test_<UUID>`, cấu hình Flyway/Hikari/Hibernate cùng sử dụng vùng này và khởi động máy chủ ở cổng ngẫu nhiên.
- Gửi yêu cầu thật tới trang chủ, đăng nhập, đăng ký, thảo luận, tìm kiếm; yêu cầu thành công và trả HTML. Khách truy cập `/api/manage/products` phải nhận 401.
- Tắt dọn ảnh, dùng thông tin kho ảnh giả, đóng ứng dụng rồi xóa vùng dữ liệu tạm trong `finally`. Không gọi kho ảnh thật. Với số trang kiểm tra cố định, số yêu cầu là O(1); thời gian chủ yếu là khởi động ứng dụng và truy vấn cơ sở dữ liệu.

### 4. Design Decisions — Quyết định thiết kế
- Chọn bản bảo trì Spring Boot 4.0.9 thay vì dừng ở 3.5 đã kết thúc cập nhật cộng đồng; bước 3.5 được kiểm tra trước theo hướng dẫn chuyển đổi chính thức.
- Giữ PDFBox trong dòng 2 để tránh thay đổi giao diện xử lý tài liệu không cần thiết.
- Chọn loại máy khách Netty không sử dụng thay vì đóng gói thêm thư viện mạng bất đồng bộ; bản JAR cuối không có JAR Netty.
- Dựng bản giao riêng từ `git archive HEAD` rồi chép đúng các tệp thuộc SEC-01; kiểm thử thành công không phụ thuộc vào thay đổi chưa commit của người dùng.

### 5. Architecture and Data Flow — Kiến trúc và luồng dữ liệu
- Maven → bảng phiên bản → các starter → JAR đóng gói Spring Boot.
- Yêu cầu web → Tomcat 11 → Spring Framework 7 → bộ điều khiển → Thymeleaf/Jackson/JPA.
- Flyway chạy các bản chuyển đổi trước khi Hibernate xác minh cấu trúc. Luồng tải ảnh vẫn dùng máy khách Apache đồng bộ.
- Phiên bản kiểm tra trong JAR: Tomcat 11.0.27, Spring Framework 7.0.10, Thymeleaf 3.1.5.RELEASE, PostgreSQL 42.7.14, Spring Data Commons 4.0.8, Jackson 2.21.7 và 3.1.7. Jackson 2 còn được Flyway kéo theo, Jackson 3 phục vụ bộ chuyển đổi web.

### 6. Files Changed — Các file thay đổi
- `pom.xml`: phiên bản, bảng phụ thuộc, starter và loại Netty không dùng.
- `src/main/java/main/config/SchemaVersionConfig.java`: đường dẫn import tương thích Spring Boot 4.
- `src/test/java/main/ApplicationIntegrationTest.java`: kiểm thử máy chủ thật và cơ sở dữ liệu tạm.
- `.dockerignore`: giữ các bản chuyển đổi cơ sở dữ liệu trong đầu vào Docker.
- `plan.md`, `reports.md`: trạng thái và báo cáo; giữ nguyên báo cáo cũ.

### 7. Testing and Validation — Kiểm thử
- Ban đầu `mvn -B test` trên 3.3.0: 57 bài, 29 lỗi do Mockito/Byte Buddy cũ không hỗ trợ Java 25 của máy, 8 bài cơ sở dữ liệu bị bỏ qua.
- Bước trung gian 3.5.16: `mvn -B test` thành công; 57 bài, 0 lỗi, 8 bài cơ sở dữ liệu chưa bật.
- Bản giao độc lập cuối: đặt `RUN_DATABASE_TESTS=true`, chạy `mvn -B verify`: **58 bài, 0 thất bại, 0 lỗi, 0 bỏ qua; BUILD SUCCESS**. Kiểm tra trên Java 25, trình biên dịch xuất mã đích Java 17. Bao gồm 8 bài chuyển đổi dữ liệu và 1 bài khởi động máy chủ thật.
- `mvn -B dependency:tree -Dscope=runtime -DoutputType=json -DoutputFile=...`: xuất thành công cây thư viện chạy thực tế.
- Lần đối chiếu OSV đầu phát hiện `GHSA-hjcp-jmpx-g3qm`, `GHSA-v3jc-474w-2wm6`, `GHSA-hf6x-8p5f-cgmf` trong Apache 5. Sau khi cập nhật và kiểm thử lại, đối chiếu **126 tọa độ Maven/phiên bản: 0 thư viện có cảnh báo được trả về**.
- Đọc danh sách thư viện trong JAR thực tế và xác nhận không đóng gói Netty. `git diff --check` không phát hiện lỗi khoảng trắng.
- Nhật ký, cây thư viện, kết quả OSV, bản sao kiểm tra và JAR nằm trong `target/`, không commit.

### 8. Limitations and Future Improvements — Hạn chế và cải tiến
- Kết quả OSV phụ thuộc dữ liệu và thời điểm quét, không chứng minh ứng dụng không còn mọi lỗ hổng; không phân tích thư viện hệ điều hành hoặc mã đã bị đóng gói lại bên trong thư viện khác.
- Chưa chạy lại công cụ quét của nền tảng, chưa xác nhận bản đang chạy ngoài Internet; thuộc SEC-03.
- Chưa kiểm tra tải ảnh lên kho thật, gửi tài liệu thật hoặc chạy container Docker. Kiểm thử đóng gói và máy chủ nhúng đã thành công; Docker context được sửa để giữ bản chuyển đổi dữ liệu.
- Chưa khẳng định từng mã lỗ hổng trong danh sách ban đầu có thể khai thác trên cấu hình này. Điều kiện cụ thể của từng thông báo vẫn cần đối chiếu khi đánh giá ảnh hưởng.
- Qua rà soát còn thấy `DataInitializer` có thông tin quản trị mặc định và tự mở khóa tài khoản `admin`; cần xử lý trong bước bảo vệ ứng dụng tiếp theo.
- Tham khảo chính thức: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide ; https://spring.io/blog/2026/10/23/spring-boot-4-0-9-available-now/ ; https://pdfbox.apache.org/security.html .

### 9. Final Result — Kết quả cuối cùng
- Bộ thư viện mới được kiểm thử và đóng gói thành công; SEC-01 đã đánh dấu `✅` trong `plan.md`.
- Nhánh `main` theo chỉ định mới nhất; kết quả commit/push thực tế được báo sau khi thực hiện, không chèn mã giả vào báo cáo.
- SEC-02 và SEC-03 chưa được đánh dấu hoàn tất.

---
