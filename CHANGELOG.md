# Nhật ký phiên bản

Mỗi lần có cập nhật hoặc sửa lỗi đều ghi vào đây: thêm dòng `- ` vào mục trên cùng, hoặc mở mục mới `## <phiên bản> · <ngày>` ở đầu danh sách (mới nhất nằm trên).
Workflow ghép file này vào mô tả của release; app đọc 3 mục đầu để hiện trong phần Nhật ký khi có bản cập nhật.

## 0.1.0 · 2026-11-27
- Thêm: khóa `title-sh` và `desc-sh` cho mọi item: lệnh shell in tiêu đề (dòng đầu) / mô tả (cả đầu ra) động; `title` / `desc` là giá trị tạm và dự phòng khi chưa chạy xong, lỗi hoặc rỗng. Chạy lại khi biến đổi hoặc `refresh`. Có `title-sh` thì không cần `title`.
- Đổi: type `applist` bỏ khóa `get`, dùng `options-sh` (lệnh shell in danh sách app, mỗi dòng một package). TOML cũ còn `get` sẽ báo cảnh báo; thiếu `options-sh` thì bỏ item.
- Đổi: gộp type `multiselect` và `singleselect` thành `select` với `multi = true` (mặc định, chọn nhiều) hoặc `multi = false` (chọn một), giống `applist`. Trang demo gộp thành `pages/types/select.toml`. TOML cũ còn `multiselect` / `singleselect` sẽ báo type không hợp lệ và bị bỏ qua.
- Đổi: type `applist` mở màn hình chọn ngay khi lệnh `get` cho ra package: mỗi app là một dòng khung xương (shimmer kiểu Facebook), tên app đọc dần theo nhóm và thay đúng vị trí khung xương, xong thì sắp theo tên; góc phải trên hiện "Đang tải danh sách…" rồi "Đã chọn x/y" (multi) hoặc ẩn (chọn một); Xác nhận và Chọn tất cả mờ khi đang tải; danh sách trống thì báo snackbar. Giúp máy nhiều app không bị đơ.
- Thêm: khóa `refresh` cho mọi item: `refresh = true` làm mới cả trang, `refresh = "id1,id2"` làm mới các item đó (chỉ trong cùng trang; đọc lại `get`, chạy lại `-sh`) sau khi lệnh của item chạy xong thành công; bật hay tắt công tắc đều làm mới. `id` không có trong trang báo cảnh báo.
- Thêm: type `applist` (chọn app trên máy): danh sách do lệnh `get` in ra (thường là `pm list packages ...`, chạy qua backend shell, có root thì bằng root; dùng được biến `$id` và pipe), chạy lại mỗi lần mở. Toàn màn hình có icon, tên, package và ô tìm; `multi = true / false` chọn nhiều / một; `$id` là package name. Có trang demo `pages/types/applist.toml`.
- Sửa: Cài đặt ở giao diện kính: nút tròn và dải mờ bật muộn thêm đúng `GlassBarBandOverhang` (cùng quy tắc với màn hình chính), thẻ vừa chạm mép thanh không còn bị mờ ngay.
- Đổi: khoảng trống giữa thanh trên và thẻ đầu tiên của màn hình chính nhỏ lại, từ 16dp xuống 0dp (chỉnh ở `ListTopGap` trong Main.kt).
- Sửa: màn hình chính ở chế độ kính: nút chuyển sang viên kính tròn và dải mờ bật muộn hơn, sau khi cuộn quá `GlassBarBandOverhang` (dùng chung biến với chiều cao dải mờ, hiện 16dp) thay vì ngay từ lần cuộn đầu tiên.
- Đổi: đảo kính (thanh tab nổi) rộng hơn: lề trái / phải từ 24dp xuống 16dp, ngang lề thẻ (chỉnh ở `FloatingBarSideMargin` trong Main.kt).
- Đổi: thanh tab hiện tối đa 5 tab cùng lúc (trước là 4); từ 6 tab trở lên mới trượt cửa sổ. Cập nhật README và docs/schema.md cho khớp.
- Sửa: Cài đặt bật làm mờ nền: dải mờ của thanh trên không còn ra màu đen (giao diện tối) khi cuộn qua phần đầu; nền động giữ lại độ đục tối thiểu 0.3 thay vì mờ về 0 (chỉnh ở `BgMinAlphaWhenBlur` trong SettingsScreen.kt).
- Sửa: thanh trên kính (màn hình chính, Cài đặt, trang con, cửa sổ Nguồn): dải mờ phía sau thanh kéo dài thêm 16dp dưới mép (Miuix mặc định 32dp) nên thẻ vừa chạm mép bớt bị mờ một chút; chỉnh ở `GlassBarBandOverhang` trong BarBlur.kt.
- Đổi: tách khóa `visible_if` / `lock` + `lock_mode` thành ba khóa `visible` (ẩn hiện), `readonly` (mờ, không bấm được) và `lock` (bấm thì hiện snackbar lý do, dạng `lock = "điều kiện|lý do"`); mỗi khóa có bản `-sh` chạy lệnh shell (in `0` = sai, `1` hoặc `1|lý do` = đúng). Dùng được cho cả `[[group]]`. TOML cũ còn `visible_if` / `lock_mode` sẽ báo khóa lạ.
- Thêm: câu mặc định khi `lock` không kèm lý do ("Mục này đang bị khóa").
- Đổi: `switch` / `checkbox` bỏ `on` / `off`, dùng `script` chạy cho cả hai chiều với `$id` = `1` (bật) hoặc `0` (tắt). TOML cũ còn `on` / `off` sẽ báo khóa lạ và lệnh không chạy.
- Đổi: khóa `run` của item đổi tên thành `script` (TOML cũ còn `run` sẽ báo khóa lạ và lệnh không chạy).
- Đổi: type `text` không còn bắt buộc `title`; bỏ trống thì chỉ hiện đoạn chữ `desc` (thiếu cả hai thì bỏ item và báo cảnh báo).
- Đổi: type `text` bỏ trường `content`, viết đoạn chữ ở `desc` như các type khác (TOML cũ còn `content` sẽ báo khóa lạ và không hiện chữ).
- Thêm: tải và cài đặt bản mới ngay trong app (nút ở đáy Cài đặt có thanh tiến độ, tải xong mở trình cài đặt của hệ thống).
- Thêm: khi có bản mới, Cài đặt ẩn Giao diện, Tùy chọn, Liên kết và chỉ hiện Nhật ký thay đổi của bản mới; app tự kiểm tra một lần khi mở Cài đặt.
- Thêm: file CHANGELOG.md này; workflow ghép nội dung vào mô tả release.
- Đổi: giao diện kính mặc định bật từ Android 13 trở lên (máy đã tự chọn thì giữ nguyên).
- Đổi: thẻ trong Cài đặt dùng nền trong suốt nhẹ ở cả chế độ thường và kính.
- Sửa: khay chọn Hệ thống / Sáng / Tối ở chế độ không kính dùng xám nhạt trong suốt (thay vì đen, rồi xám đặc lệch màu so với thẻ).
- Thêm: màn hình chính ở chế độ kính có dải nền mờ dần phía sau đảo (thanh tab nổi) giống dải sau nút cập nhật, để đảo nổi lên khỏi thẻ cuộn.
- Sửa: khi cuộn, các thẻ (Giao diện, Tùy chọn...) không còn đi quá mép dưới thanh trên: phần nằm trên mép bị ẩn, mờ dần vào.
- Sửa: thanh trên của Cài đặt hiện nền ngay khi thẻ chạm vào, không đợi cuộn hết phần đầu.
- Sửa: chế độ kính không còn khung đen nhẹ ở thanh trên khi cuộn (dải mờ lấy màu từ nền động và thẻ).
