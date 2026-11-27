# Nhật ký phiên bản

Mỗi lần có cập nhật hoặc sửa lỗi đều ghi vào đây: thêm dòng `- ` vào mục trên cùng, hoặc mở mục mới `## <phiên bản> · <ngày>` ở đầu danh sách (mới nhất nằm trên).
Workflow ghép file này vào mô tả của release; app đọc 3 mục đầu để hiện trong phần Nhật ký khi có bản cập nhật.

## 0.1.0 · 2026-11-27
- Thêm: tải và cài đặt bản mới ngay trong app (nút ở đáy Cài đặt có thanh tiến độ, tải xong mở trình cài đặt của hệ thống).
- Thêm: khi có bản mới, Cài đặt ẩn Giao diện, Tùy chọn, Liên kết và chỉ hiện Nhật ký thay đổi của bản mới; app tự kiểm tra một lần khi mở Cài đặt.
- Thêm: file CHANGELOG.md này; workflow ghép nội dung vào mô tả release.
- Đổi: giao diện kính mặc định bật từ Android 13 trở lên (máy đã tự chọn thì giữ nguyên).
- Đổi: thẻ trong Cài đặt dùng nền trong suốt nhẹ ở cả chế độ thường và kính.
- Sửa: khay chọn Hệ thống / Sáng / Tối ở chế độ không kính dùng xám nhạt thay vì đen.
- Sửa: thanh trên của Cài đặt hiện nền ngay khi thẻ chạm vào, không đợi cuộn hết phần đầu.
- Sửa: chế độ kính không còn khung đen nhẹ ở thanh trên khi cuộn (dải mờ lấy màu từ nền động và thẻ).
