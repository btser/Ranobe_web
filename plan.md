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

# 11.Recommend
Dựa trên điểm (đề xuất ở phần 10. Ranking) được tính trong vòng 24h gần nhất. Hiện recommend khoảng 5-6 tác phẩm trên trang chủ.
