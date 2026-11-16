# Kế hoạch bảo mật được duyệt ngày 16/11/2026

Phạm vi phiên này: thực hiện tuần tự SEC-01, SEC-02, SEC-03 trên `main` theo chỉ định trực tiếp của người dùng; giữ nguyên các công việc cũ bên dưới. Các lần push cách nhau ít nhất 5 phút.

- ✅ SEC-01 — Đối chiếu thư viện, nâng bộ phụ thuộc được hỗ trợ, kiểm thử và đóng gói; ghi rõ giới hạn xác minh cảnh báo.
- [ ] SEC-02 — Bảo vệ thống nhất các yêu cầu thay đổi dữ liệu trước giả mạo từ trang khác; kiểm thử biểu mẫu, yêu cầu JavaScript và tải tệp.
- [ ] SEC-03 — Xác minh phiên bản đang triển khai, kết nối HTTPS và quét lại bằng công cụ của nền tảng sau triển khai; không đánh dấu hoàn tất nếu chưa có bằng chứng.

---

# ✅1.Đóng lỗ hổng ở các đường dẫn quản lý cũ.
[ContentController.java]đang cho tạo và đổi trạng thái ẩn truyện/tập/chương mà không kiểm tra đăng nhập hoặc vai trò trong các hàm xử lý. Cần bổ sung kiểm tra quyền hoặc bỏ các đường dẫn này nếu không còn dùng.

# ✅2.Khóa tài khoản phải có hiệu lực với người đang đăng nhập.
Kiểm tra lại tài khoản có bị khóa hay không. Nên kiểm tra tập trung trước mỗi thao tác ghi dữ liệu; tài khoản bị khóa thì kết thúc phiên đăng nhập.

# ### Trash ### 3.Tách rõ quyền xem, sửa và quản trị.
Trong [ProductController.java], chủ sở hữu thường đang bị chặn xem truyện riêng tư của chính mình. Nên có ba phép kiểm tra riêng:
- Xem: truyện công khai hoặc người có quyền xem nội dung riêng tư.
- Sửa: chủ sở hữu.
- Ẩn/khôi phục: quản trị viên hoặc điều phối viên.
Quyền xóa tập/chương hiện cũng đang theo chủ sở hữu; cần thống nhất với quy tắc bạn muốn dành thao tác xóa cho người quản trị.

# ✅4.Băm mật khẩu và kiểm tra dữ liệu đăng ký.
[AuthController.java] đang lưu và so sánh mật khẩu nguyên văn. Nên dùng hàm băm mật khẩu chuyên dụng, kiểm tra thư điện tử trùng và giới hạn độ dài dữ liệu. Việc tạo hồ sơ cùng tài khoản nên nằm trong một giao dịch cơ sở dữ liệu: cả hai cùng thành công hoặc cùng hủy.

# ✅5.Tự lưu bản nháp và giữ lịch sử sửa chương.
Đây là cải tiến hữu ích nhất cho người đăng truyện: tránh mất bài khi tải lại trang, mất mạng hoặc đóng nhầm cửa sổ. Thêm cảnh báo khi rời trang có nội dung chưa lưu và khả năng khôi phục phiên bản trước.

# ✅6.Cho nhập tài liệu thất bại rồi thử lại được.
[PdfImportController.java] hiện tiêu thụ mã xác nhận trước khi hoàn tất và lưu các chương lần lượt. Nếu lỗi giữa chừng có thể đã lưu một phần. Nên giữ kết quả xem trước, báo chính xác ảnh/chương lỗi, hỗ trợ thử lại mà không tạo chương trùng.

# ✅7.Lưu lịch sử đọc để nút “Đọc tiếp” đúng nghĩa.
Nút hiện tại trỏ vào chương đầu của tập đầu. Nên lưu chương đọc gần nhất và vị trí cuộn trang; người chưa đọc thì hiện “Đọc từ đầu”. Đồng thời tìm chương đầu thực sự có nội dung nếu tập đầu đang trống.

# ✅8.Thêm tìm kiếm, lọc và phân trang.
Tìm theo tên truyện/tác giả, lọc nhiều thể loại và sắp xếp theo cập nhật. Trang quản trị cũng cần tìm tài khoản, lọc truyện đã ẩn và tài khoản bị khóa.

# ✅9.Quản lý thay đổi cấu trúc cơ sở dữ liệu theo phiên bản.
Mỗi lần thêm cột cần có bước bổ sung giá trị cho dữ liệu cũ và ràng buộc không để trống khi cần. Việc này tránh lặp lại lỗi truyện cũ bị sập sau khi thêm trường.

# ✅10. Ranking
Xếp hạng truyện công khai theo điểm = 100 × bình chọn + 10 × bình luận + lượt xem. Công thức tạm thời do người dùng chốt ngày 29/10/2026, có thể điều chỉnh sau.

# ✅11.Recommend
Dựa trên tổng điểm tương tác tích lũy từ khi tác phẩm được đăng: 100 × bình chọn + 10 × bình luận + lượt xem. Hiển thị tối đa 6 tác phẩm công khai, chưa bị xóa, theo điểm giảm dần; vẫn lấy tác phẩm 0 điểm khi chưa đủ 6 tác phẩm có điểm.

Ghi chú chỉnh sửa 04/11/2026: bỏ giới hạn 24 giờ và điều kiện bắt buộc có điểm để phần Đề xuất vẫn có tác phẩm khi không có tương tác gần đây. Khi bằng điểm, ưu tiên số bình chọn, lượt xem rồi mã tác phẩm giảm dần. Không có tác phẩm công khai hợp lệ thì không hiển thị phần Đề xuất.

# 12. Cập nhật hiệu ứng cho mod, admin
Tên của mod, admin sẽ có hiệu ứng riêng (quyết định sau), profile của mod và admin cũng xịn xò con bò hơn.

# ✅13. Gỡ ảnh bìa mặc định cũ
Xóa `default-cover.webp`, bỏ các liên kết tự dùng ảnh này thay bìa. Chưa có bìa hoặc tải ảnh lỗi thì giữ nền trống; không đổi ảnh người dùng đã lưu hay xóa dữ liệu R2.

# 14. Thêm chức năng thảo luận
Trạng thái 30/10/2026: đã triển khai và kiểm thử cục bộ; chờ người dùng kiểm tra trước khi chốt. Chưa commit/push. Phạm vi đã chốt: chỉ văn bản; chỉ Mod/Admin đăng thông báo; người đăng cuối là người bình luận cuối; bỏ mục Danh sách. Chi tiết thay đổi mã nguồn và kiểm thử ở Report 14 trong reports.md.
Ở thanh bar trên cùng của trang chủ, thêm 1 số lựa chọn như Thảo luận, Danh sách. Thảo luận sẽ là 1 bảng riêng, C:\Users\OS\Desktop\Project\examples\image.png. ví dụ như hình. Cập nhật dtb thêm 1 bảng discuss, bao gồm title, notification, comment_count, views, last_updated, last_uploaded, discuss_id, comment_id. Link tới bảng comments, bao gồm discuss_id, comment_id, content, last_updated, user_id. 

# 15. Chỉnh sửa database
Chỉnh sửa các trường trong table có thể là null. Hiện tại theo ảnh ERD thì có artist.

# 16. Cập nhật data
Cập nhật data từ folder data. Trong đó là file dạng mobi, epub nên cần tự chuyển đổi. 

