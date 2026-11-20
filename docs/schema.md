# Tool Script – Schema TOML (bản nháp v0.6)

## 1. Backend thực thi (Executor)

Thứ tự ưu tiên khi app chọn backend:

| Ưu tiên | Backend | Giá trị `ROOT_MODE` |
|---|---|---|
| 1 | su (root) | `root` |
| 2 | ROM (lệnh tích hợp hệ thống) | `system` |
| 3 | adb (Wireless Debugging) | `adb` |
| 4 | Không root | `any` |

Backend hiện tại có sẵn dưới dạng biến `ROOT_MODE`, dùng trong `lock` và
`visible_if` (xem mục 8, 9). Không còn trường `require`.

## 2. Cấu trúc file

```
assets/configs/
├─ main.toml          # danh sách tab
└─ pages/
   └─ <ten>.toml      # nội dung từng trang
```

## 3. main.toml

```toml
[app]
name    = "Tool Script"   # hiện làm tiêu đề thanh trên cùng
version = 1               # phiên bản schema
lang    = "vi"

[[tab]]
id    = "home"
title = "Trang chủ"             # bắt buộc, tên tab
icon  = "icons/home.svg"        # bắt buộc, đường dẫn icon tab
file  = "pages/home.toml"
```

- Mỗi `[[tab]]` là một tab ở thanh dưới. `title` và `icon` bắt buộc.
- Cú pháp cũ `[[page]]` đã bỏ: gặp thì bỏ qua kèm cảnh báo.
- Thiếu `title` hoặc `icon`: bỏ tab đó và ghi cảnh báo vào log.
- `icon` là đường dẫn tới file icon:
  - Trong assets: `icons/home.svg` (tính từ thư mục configs).
  - Đường dẫn ngoài: `/sdcard/ToolScript/icons/home.png`.
- Thanh tab hiện tối đa 4 tab, tối thiểu 2 (xem mục 5).

## 4. File trang (pages/*.toml)

```toml
[[item]]
type  = "switch"
id    = "game_mode"
title = "Chế độ game"
desc  = "Tăng xung CPU/GPU"
on    = "echo 1 > /sys/xxx"
off   = "echo 0 > /sys/xxx"
get   = "cat /sys/xxx"       # đọc trạng thái hiện tại
shell = "log"                # chế độ hiển thị khi chạy (mặc định log)
lock  = "ROOT_MODE != root"
lock_mode = "readonly"
```

## 5. Bố cục

- Thanh trên cùng: tiêu đề là tên app (`[app] name`).
  Bên phải có 2 icon riêng biệt: Thông tin (menu thả xuống tùy chỉnh chủ đề: Theo hệ thống / Sáng / Tối / 3 chế độ Màu động (Monet) có sẵn của Miuix,
  lựa chọn được lưu lại) và Nguồn (menu: khởi động lại ứng dụng, hoặc khởi động lại máy bằng `reboot`, cần root).
- Lề 2 bên 16dp, khoảng cách giữa các thẻ 16dp.
- Thanh tab dưới cùng, mỗi trang một tab (icon + title).
- Cửa sổ tab trượt: hiện tối đa 4 tab. Từ 5 tab trở lên, vuốt tới tab thứ 4
  của cửa sổ thì tab kế tiếp hiện ra bên phải và tab đầu ẩn đi; vuốt ngược
  về tab đầu của cửa sổ thì tab trước đó hiện lại.
- Chỉ có 1 tab: ẩn thanh tab, hiện trang toàn màn hình.

## 6. Các trường chung của item

| Trường | Bắt buộc | Mô tả |
|---|---|---|
| `type` | có | Loại thành phần |
| `id` | tùy loại | Định danh, dùng làm tên biến |
| `title` | có | Tiêu đề hiển thị |
| `desc` | không | Mô tả phụ |
| `visible_if` | không | Điều kiện hiển thị (mục 8) |
| `lock` | không | Điều kiện khóa item (mục 9) |
| `lock_mode` | không | `readonly` (mặc định) hoặc `hide` |
| `shell` | không | Chế độ hiển thị khi chạy lệnh (mục 10), mặc định `log` |
| `confirm` | không | Nội dung hộp thoại xác nhận trước khi chạy |
| `timeout` | không | Giây, mặc định 30 |

## 7. Các loại item (type)

| type | Trường riêng |
|---|---|
| `text` | `content` |
| `button` | `run` |
| `switch` | `on`, `off`, `get` |
| `input` | `hint`, `default`, `run`, `input_type` (`text`/`number`) |
| `select` | `options`, `default`, `run` |
| `slider` | `min`, `max`, `step`, `default`, `run` |
| `link` | `tab` (id của một `[[tab]]`, chuyển tab) hoặc `page` (đường dẫn file trang con, mở trang mới) |
| `checkbox` | `on`, `off`, `get` (giống `switch`, hiện ô tick) |
| `radio` | `options`, `default`, `run` (giống `select`, hiện sẵn mọi lựa chọn) |
| `progress` | `min`, `max`, `default`, `get` (chỉ để xem, không có lệnh chạy) |

`group` không phải một `type`: nhóm khai báo bằng `[[group]]` riêng, item con
nằm trong `[[group.item]]`.

```toml
[[item]]                      # item đứng ngoài nhóm
type    = "text"
title   = "Chào mừng"
content = "Nội dung"

[[group]]
title = "Công tắc"            # bắt buộc; hiện sát phía trên thẻ đầu tiên

[[group.item]]
type  = "switch"
id    = "game_mode"
title = "Chế độ game"
```

- `[[group]]` dùng được `title`, `visible_if`, `lock`, `lock_mode`; không có `type`.
- Thứ tự `[[item]]` và `[[group]]` trên trang theo thứ tự xuất hiện trong file.
- Các item con trong một `[[group]]` nằm chung trong một thẻ lớn. `[[item]]` đứng ngoài group thì mỗi item một thẻ riêng.
- Group mà mọi item con đều bị ẩn (`visible_if`, `lock_mode = "hide"`) thì ẩn luôn cả tiêu đề.
- Cú pháp cũ `[[item]]` + `type = "group"` và `[[item.item]]` không còn dùng:
  `type = "group"` bị bỏ qua kèm cảnh báo.
- Chưa hỗ trợ group lồng group (`[[group.group]]`).

`select` – cách khai báo `options`:

```toml
[[item]]
type  = "select"
id    = "governor"
title = "Governor"
run   = "echo $governor > /sys/yyy"
options = [
  "Hiệu năng|performance",   # "nhãn|giá trị"
  "Tiết kiệm|powersave",
  "schedutil",               # không có | thì nhãn = giá trị
]
```

- Mỗi phần tử là một chuỗi `"nhãn|giá trị"`, tách ở dấu `|` đầu tiên.
- Nhãn hiện trên giao diện, giá trị được truyền vào lệnh qua `$id`.
- Phần tử có giá trị rỗng bị bỏ qua và ghi cảnh báo.

`link` – chuyển tab hoặc mở trang con:

```toml
[[item]]
type  = "link"
title = "Đến tab Công cụ"
tab   = "tools"                  # id của một [[tab]]; từ trang con thì đóng trang con rồi chuyển tab

[[item]]
type  = "link"
title = "Cài đặt nâng cao"
page  = "pages/advanced.toml"    # file trang con, tính từ thư mục configs

[[item]]
type  = "link"
title = "Nhập nhanh"
page  = "pages/quick.toml"
style = "dialog"                 # dialog toàn màn hình
```

- Trang con là màn hình mới có nút quay lại ở thanh trên (và nút Back của máy); tiêu đề lấy từ `title` của item link.
- `style` (chỉ với `page`): `page` (mặc định) hoặc `dialog`. `dialog` mở trang trượt từ dưới lên che kín màn hình, thanh trên có nút đóng (X) thay vì mũi tên.
- Trong trang dialog không mở được trang con: link `page` bị bỏ qua kèm cảnh báo, link `tab` vẫn dùng được (đóng dialog rồi chuyển tab).
- Cùng một file trang con được nhiều link trỏ tới thì dùng `title` và `style` của link đầu tiên.
- File trang con viết như file trang thường (mục 4); trong đó có thể có link tới trang con khác.
- Có cả `tab` và `page` thì dùng `tab`. Thiếu cả hai: bỏ item. `tab` không tồn tại: ghi cảnh báo.
- Biến (`id`) của trang con dùng chung kho biến với các tab.

`checkbox`, `radio`, `progress` – ghi chú:

- `checkbox` dùng đúng quy tắc của `switch`: `get` in `1`/`true`/`on` là bật, `on` / `off` chạy khi đổi trạng thái, chỉ đổi khi lệnh thành công. Bấm cả hàng cũng đổi.
- `radio` khai báo `options` như `select` (mục 7), nhãn hiện trên giao diện, giá trị truyền qua `$id`.
- `progress` cần `id`. `get` in ra một số trong khoảng `min`..`max` (mặc định 0..100), chỉ đọc khi item xuất hiện. Giá trị nằm ngoài khoảng được cắt về mép. Không có `get` thì dùng `default` (mặc định `min`).

```toml
[[item]]
type  = "checkbox"
id    = "auto_clean"
title = "Tự dọn dẹp"
on    = "echo 1 > /sys/xxx"
off   = "echo 0 > /sys/xxx"

[[item]]
type    = "radio"
id      = "profile"
title   = "Chế độ"
run     = "echo $profile > /sys/yyy"
options = ["Cân bằng|balanced", "Hiệu năng|performance"]

[[item]]
type  = "progress"
id    = "battery"
title = "Pin"
get   = "cat /sys/class/power_supply/battery/capacity"
```

## 8. Biến và điều kiện

- Giá trị của item có `id` dùng trong lệnh dưới dạng `$id`.
- Trong điều kiện (`visible_if`, `lock`) viết tên biến không có `$`:
  `game_mode == 1`, `ROOT_MODE != root`.
- Toán tử: `==`, `!=`, `&&`, `||`.
- Biến dựng sẵn: `SDK`, `ABI`, `ROOT_MODE`.

## 9. Khóa item (lock)

`lock` là một điều kiện. Điều kiện đúng thì item bị khóa:

| `lock_mode` | Khi bị khóa |
|---|---|
| `readonly` (mặc định) | Vẫn hiện, làm mờ (alpha), không bấm được |
| `hide` | Ẩn hẳn, các item dưới dồn lên |

Ví dụ:

```toml
# Chỉ dùng được khi có root, ngược lại hiện mờ
lock = "ROOT_MODE != root"

# Ẩn nếu không phải root hoặc ROM
lock = "ROOT_MODE != root && ROOT_MODE != system"
lock_mode = "hide"
```

## 10. Chế độ hiển thị khi chạy (shell)

Áp dụng cho mọi item có chạy lệnh: `button`, `switch`, `checkbox`, `input`, `select`, `radio`, `slider`.

| `shell` | Hành vi |
|---|---|
| `log` (mặc định) | Hiện log đầy đủ của lệnh vừa chạy |
| `hidden` | Chạy ẩn, không hiện gì |
| `banner` | Hiện thông báo ngắn trong app (banner) |
| `notice` | Gửi thông báo hệ thống của Android (chỉ xin quyền thông báo khi dùng tới, Android 13+) |

Quy tắc chung: ở chế độ `hidden`, nếu lệnh lỗi thì hiện **toast** báo lỗi.

## 11. Khởi động và xử lý lỗi

- Script khởi động nằm trong assets, báo tiến trình bằng dòng `@progress N` (0-100).
- Script lỗi: vẫn vào app, hiện banner cảnh báo, xem lỗi trong Trung tâm lỗi.
- TOML parse lỗi: hiện file, dòng, mô tả lỗi.
- Thiếu trường bắt buộc hoặc `type` lạ: bỏ qua mục đó và ghi cảnh báo.
- Lỗi không lưu ra file, chỉ giữ trong bộ nhớ của phiên chạy.

## 12. Để lại sau

Lộ trình thành phần Miuix (xếp từ dễ đến khó, mức 1 đã xong) nằm trong `README.md`, mục "Lộ trình thành phần Miuix".

- Nạp config từ thư mục ngoài và reload nóng.
- Đa ngôn ngữ theo khóa (`title_vi`, `title_en`...).
- Backend ROM: danh sách lệnh cho phép.
