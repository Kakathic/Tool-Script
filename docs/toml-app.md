# Tool Script: app giao diện TOML

Tài liệu này giải thích **app làm được gì** và **bộ demo TOML** đi kèm. Chi tiết từng trường, từng quy tắc nằm ở `docs/schema.md` (nguồn sự thật cho parser và renderer).

## 1. Ý tưởng

Tool Script là ứng dụng Android không cố định giao diện. Giao diện nằm trong các file TOML ở `assets/configs`; app đọc file, vẽ ra công tắc, nút, ô nhập, hộp chọn... (thư viện Miuix, Compose) và chạy lệnh shell khi người dùng thao tác. Muốn thêm một tính năng chỉ cần thêm vài dòng TOML, không sửa Kotlin.

Ví dụ ngắn nhất, một công tắc bật tắt một thông số:

```toml
[[item]]
type  = "switch"
id    = "game_mode"
title = "Chế độ game"
script = "echo $game_mode > /sys/xxx"
```

## 2. Luồng chạy

1. Splash: xin quyền bộ nhớ, dò backend thực thi (su, rồi shell thường), `ROOT_MODE` cho biết kết quả.
2. Chép `scripts`, `bin`, `lib/<abi>` từ assets ra `no_backup`, tạo `env.sh`, chạy `init.sh` (cài busybox).
3. Nạp `main.toml` (danh sách tab), rồi từng file trang. Trang con được nạp luôn lúc này.
4. Vào màn hình chính. Script khởi động lỗi vẫn vào app, kèm banner cảnh báo.

## 3. Cấu trúc file

```
assets/configs/
├─ main.toml            # [app] và danh sách [[tab]]
├─ icons/               # icon tab và icon item
└─ pages/
   ├─ <tab>.toml        # nội dung mỗi tab
   └─ <thư mục>/*.toml  # trang con, mở từ item link
```

Trang gồm `[[item]]` (mỗi item một thẻ) và `[[group]]` có `[[group.item]]` (các item chung một thẻ), xếp theo thứ tự trong file.

## 4. Tính năng chính

| Nhóm | Nội dung |
|---|---|
| Type | `text`, `button`, `switch`, `checkbox`, `input`, `slider`, `radio`, `dropdown`, `singleselect`, `multiselect`, `applist`, `progress`, `link`; `[[group]]` để gom nhóm |
| Lưu giá trị | Item có `id` tự lưu vào file tên = id trong `$STATE_DIR`, mở lại app vẫn giữ; lệnh dùng giá trị qua `$id` |
| Điều kiện | `visible` ẩn/hiện, `readonly` mờ, `lock` bấm hiện snackbar lý do (mỗi khóa có bản `-sh` chạy shell); toán tử `==`, `!=`, `&&`, `\|\|`; biến `SDK`, `ABI`, `ARCH`, `ROOT_MODE` và `id` của item |
| Chạy lệnh | `script`, `get`; `confirm` hỏi trước khi chạy; `timeout` giới hạn giây |
| Hiển thị kết quả | `shell = live / full / hidden / banner / notice / snackbar`; thanh tiến trình bằng dòng `@progress N`; stderr màu đỏ |
| Chạy nền | Bảng log có nút Ẩn: lệnh chạy tiếp, log đi vào thông báo Android; nút Hủy chạy `killtree` giết cả cây tiến trình |
| Điều hướng | `link` chuyển tab, mở trang con, hoặc mở dialog (Xác nhận mới chạy `script`) |
| Chọn nhiều mục | `singleselect` / `multiselect` từ 6 mục thì toàn màn hình, có ô tìm không phân biệt dấu; `multiselect` có Chọn tất cả |
| Chọn app | `applist`: chọn app trên máy (icon, tên, package); danh sách do lệnh `get` in ra, thường là `pm list packages ...` (app đã tắt, hệ thống, theo nơi cài...); `multi = true / false`; `$id` là package name |
| Icon | `icon`, `icon_size`, `icon_tint` cho mọi item trừ `text` |
| Môi trường shell | `env.sh` (biến đường dẫn), `[app] env`, lệnh dựng sẵn `busybox`, `slog`, `glog`, `killtree` |
| Giao diện app | Tiêu đề lớn thu gọn khi vuốt, thanh tab cửa sổ trượt 5 ô, hộp thoại Cài đặt (thẻ giới thiệu và kiểm tra phiên bản, Hệ thống/Sáng/Tối, Màu động, Rung khi chạm, Làm mờ nền, liên kết GitHub), menu Nguồn |
| Lỗi | Lỗi cú pháp TOML: trang hiện thẻ lỗi kèm file và dòng. Lỗi nhẹ: bỏ mục sai hoặc dùng mặc định, banner cảnh báo ở đầu trang có lỗi. Crash app: Trung tâm lỗi với stack trace |

## 5. Bộ demo (5 tab)

Mọi lệnh demo chỉ là `echo` / `sleep` / `ls`, không đổi thông số thiết bị; riêng hai tab cuối cố ý gây lỗi.

| Tab | File | Nội dung |
|---|---|---|
| Các type | `pages/types.toml` | Danh sách link; mỗi type một trang demo trong `pages/types/` (`text`, `button`, `switch`, `checkbox`, `input`, `slider`, `radio`, `dropdown`, `singleselect`, `multiselect`, `applist`, `progress`, `link`, `group`). Trang `link` còn dùng `link_sub`, `link_sub2`, `link_dialog` |
| Tính năng mới | `pages/news.toml` | Các tính năng thêm gần đây, mỗi nhóm có nút bấm thử: snackbar, notice, `@progress`, Ẩn log, Hủy bằng killtree, chọn toàn màn hình, dialog nhỏ gọn / full màn hình, icon, env.sh, slog/glog, busybox, trang đa ngôn ngữ (`@khóa`) |
| Giới thiệu | `pages/about.toml` | Tác giả (chủ dự án), người viết code (Claude), phân công, công nghệ, thông tin máy |
| Lỗi code | `pages/crash.toml` | Crash app (`@crash`, `@crash-thread`), lỗi lệnh shell (exit 1, not found, sai cú pháp, permission denied, SIGKILL, hết giờ), lỗi ở mọi kiểu hiển thị, công tắc có lệnh lỗi |
| Trang hỏng | `pages/broken.toml` | Link tới các trang cố ý hỏng trong `pages/broken/`: `syntax`, `syntax_dup`, mất file, `fields`, `options`, `links`, `nested`, `dialog_bad` |

Quy ước: `id` của item demo có tiền tố theo trang (`t_switch_...`, `n_...`, `c_...`, `b_...`) vì mọi trang dùng chung một kho biến.

### Đặc biệt với tab Lỗi code

- `script = "@crash"` hoặc `"@crash-thread"` làm app thoát thật rồi mở Trung tâm lỗi (tiến trình `:crash`). Luôn kèm `confirm`.
- Lỗi lệnh shell không làm app thoát: log hiện mã lỗi và phần đã in. `hidden` chỉ hiện toast, `banner` / `snackbar` / `notice` hiện nền đỏ nhạt.
- `switch` / `checkbox`: lệnh `script` lỗi thì trạng thái không đổi.

### Đặc biệt với tab Trang hỏng

- Trang sai cú pháp chỉ hiện thẻ lỗi, các trang khác không bị ảnh hưởng.
- Cảnh báo cấu hình chỉ nằm ở trang có lỗi; bấm Xem để đọc danh sách.
- Muốn bỏ demo: xóa tab `broken` trong `main.toml`, `pages/broken.toml` và thư mục `pages/broken/`.

## 6. Viết trang mới

1. Tạo `assets/configs/pages/<ten>.toml`.
2. Thêm `[[tab]]` vào `main.toml` (bắt buộc `id`, `title`, `icon`, `file`), hoặc mở bằng link `page` từ trang khác.
3. Đặt `id` hợp lệ (chữ, số, `_`; không bắt đầu bằng số), không trùng giữa các trang.
4. Đặt lệnh trong nháy đơn khi chứa nháy kép hoặc `$`: `script = 'echo "$id"'`.
5. Luôn đặt `$id` trong nháy kép trong lệnh.

## 7. Chưa làm

Reload nóng, nạp `main.toml` từ thư mục ngoài, nội dung TOML đa ngôn ngữ, backend ROM và adb, group lồng group. Danh sách đầy đủ ở `README.md`, mục "Lộ trình". Một số tính năng chưa được thử hết trên máy thật, cũng ghi ở đó.
