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
