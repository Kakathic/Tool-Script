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
env     = "export PATH=$PATH:/data/adb/bin"   # tùy chọn, xem bên dưới

[[tab]]
id    = "home"
title = "Trang chủ"             # bắt buộc, tên tab
icon  = "icons/home.svg"        # bắt buộc, đường dẫn icon tab
file  = "pages/home.toml"
```

- `[app] env` (tùy chọn): đoạn shell chạy **đầu tiên** trước mọi lệnh của app: `run`, `on`, `off`, `get`, lệnh của link dialog và lệnh khởi động lại máy. Dùng để đặt `PATH`, nạp hàm dùng chung, v.v.
  - Được ghép vào đầu mỗi lệnh (cách bằng xuống dòng), trước cả các biến `export` của item, nên không dùng được `$id` của item trong `env`.
  - Biến dựng sẵn (`SDK`, `ABI`, `ROOT_MODE`) cũng chưa có khi `env` chạy.
  - Script khởi động `assets/scripts/init.sh` chạy trước khi nạp TOML nên không áp dụng `env` (nhưng có áp dụng `env.sh`, xem bên dưới).
  - Thứ tự: `env.sh` → `[app] env` → các biến `export` của item → lệnh.
  - `env` lỗi cú pháp sẽ làm mọi lệnh lỗi theo; viết nhiều dòng bằng chuỗi ba nháy `"""..."""`.
- Mỗi `[[tab]]` là một tab ở thanh dưới. `title` và `icon` bắt buộc.
- Cú pháp cũ `[[page]]` đã bỏ: gặp thì bỏ qua kèm cảnh báo.
- Thiếu `title` hoặc `icon`: bỏ tab đó và ghi cảnh báo vào log.
- `icon` là đường dẫn tới file icon:
  - Trong assets: `icons/home.svg` (tính từ thư mục configs).
  - Đường dẫn ngoài: `/sdcard/ToolScript/icons/home.png`.
- Thanh tab hiện tối đa 4 tab, tối thiểu 2 (xem mục 5).

### env.sh (biến dùng chung cho mọi lệnh)

`assets/scripts/env.sh` (tùy chọn) chứa các biến dùng chung. Mỗi lần mở app, các key dạng `@KEY@` được thay bằng đường dẫn thật rồi ghi ra thư mục `files/scripts/env.sh` của app; **mọi lệnh shell chạy qua app đều nạp file này trước** (`run`, `on`, `off`, `get`, lệnh dialog, khởi động lại máy, và cả `init.sh`).

| Key | Giá trị |
|---|---|
| `@PACKAGE@` | tên gói của app |
| `@APP_DIR@` | thư mục dữ liệu của app |
| `@FILES_DIR@` | thư mục `files` của app |
| `@CACHE_DIR@` | thư mục cache của app |
| `@SCRIPTS_DIR@` | thư mục chứa script chép từ assets (cùng nơi có `env.sh`) |
| `@STATE_DIR@` | thư mục lưu trạng thái các tính năng (`files/state`, app tạo sẵn) |
| `@BIN_DIR@` | thư mục lệnh dựng sẵn (`files/bin`, xem "Lệnh dựng sẵn" bên dưới) |
| `@LIB_DIR@` | thư mục thư viện native của app |
| `@EXT_ROOT@` | gốc bộ nhớ trong (thường `/storage/emulated/0`) |
| `@EXT_DIR@` | thư mục làm việc ngoài của app (`@EXT_ROOT@/ToolScript`) |

```sh
export FILES_DIR="@FILES_DIR@"
export EXT_DIR="@EXT_DIR@"
export PATH="$FILES_DIR/bin:$PATH"
```

- `env.sh` mẫu có sẵn hai hàm để script tự đọc / ghi cùng thư mục `$STATE_DIR` mà app dùng để lưu giá trị item (xem "Lưu giá trị theo id", mục 7):
  `state_set <id> <giá trị>` và `state_get <id> [mặc định]` (chưa có file thì in giá trị mặc định, mặc định `0`).
  Item không cần dùng chúng nữa: app tự lưu và tự đọc lại giá trị.

### Lệnh dựng sẵn (bin)

Các file chạy được nằm trong thư viện native của app: `app/src/main/jniLibs/<abi>/libbin_<tên>.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
Android cài đúng ABI của máy và giải nén ra `$LIB_DIR` (`useLegacyPackaging = true` trong `app/build.gradle.kts`), nơi app thường không root cũng được chạy file.
Mỗi lần mở app, app tạo symlink `files/bin/<tên>` (`$BIN_DIR`) trỏ tới `libbin_<tên>.so` tương ứng. `$BIN_DIR` đứng đầu `PATH` trong `env.sh`,
nên mọi lệnh của app gọi thẳng được `7z`, `toybox`, `busybox`. Thêm lệnh mới: bỏ file `libbin_<tên>.so` vào đủ các thư mục ABI.

| Lệnh | Nội dung |
|---|---|
| `busybox` | BusyBox 1.36.1 (bản NDK của osm0sis, qua Magisk-Modules-Repo/busybox-ndk), biên dịch tĩnh. Gọi applet: `busybox ls`, `busybox wget`... (chưa tạo symlink applet để không đè lệnh hệ thống) |
| `toybox` | Toybox 0.8.15 (landley/toybox), biên dịch tĩnh bằng glibc; đã tắt `login`, `mkpasswd`, `passwd`, `su`, `sulogin` vì cần `crypt`. Gọi: `toybox ls`... |
| `7z` | 7-Zip 26.06 (`7zzs` bản tĩnh của ip7z/7zip). Giấy phép: `docs/licenses/7zip.txt` |

- Script `.sh` cũng đặt được theo cách này (dòng đầu phải là `#!/system/bin/sh`). Hai script thử `state-set` / `state-get` (ghi / đọc `$STATE_DIR/<id>`)
  nằm sẵn trong `jniLibs`; trang `tools.toml` có nhóm nút "Thử script trong bin" để kiểm tra. Tên `state-set` dùng dấu gạch ngang để không trùng
  hàm `state_set` của `env.sh` (hàm shell luôn được ưu tiên hơn file trong `PATH`).
- Toybox biên dịch bằng glibc tĩnh nên tra cứu tên (DNS, `getpwnam`...) có thể khác bản của Android; các lệnh thông thường không bị ảnh hưởng.
- Chưa thử trên máy thật: cần kiểm tra chạy được ở chế độ không root.

- Key lạ (không có trong bảng) giữ nguyên và ghi một dòng vào log khởi động.
- Giữ nháy kép quanh `@KEY@` để đường dẫn có khoảng trắng vẫn đúng.
- Không có `env.sh` trong assets thì bỏ qua. File lỗi cú pháp không chặn lệnh, nhưng các biến sau chỗ lỗi sẽ thiếu.
- Biến trong `env.sh` có thể bị ghi đè bởi biến cùng tên của item (cùng `id`).

## 4. File trang (pages/*.toml)

```toml
[[item]]
type  = "switch"
id    = "game_mode"
title = "Chế độ game"
desc  = "Tăng xung CPU/GPU"
on    = "echo 1 > /sys/xxx"
off   = "echo 0 > /sys/xxx"
get   = "cat /sys/xxx"       # tùy chọn: đọc trạng thái thật bằng shell lúc item xuất hiện
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
| `dropdown` | `options`, `default`, `run` (giống `select`, nhưng chọn bằng menu thả xuống) |
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

### Lưu giá trị theo id

Mọi item có `id` và có giá trị (`switch`, `checkbox`, `input`, `select`, `radio`, `dropdown`, `slider`, `progress`) được lưu tự động:

- Mỗi `id` một file, **tên file = id**, nằm trong `$STATE_DIR` (`files/state`). Nội dung là giá trị kèm xuống dòng, cùng định dạng với `state_set`
  (`switch` / `checkbox` lưu `1` hoặc `0`).
- Mỗi lần giá trị đổi (với `switch`, `checkbox`, `select`, `radio`, `dropdown`: sau khi lệnh chạy thành công) app ghi lại file.
- Lúc mở app: chưa có file thì app tạo file với giá trị mặc định (`default`, hoặc `0`, `min`, lựa chọn đầu); có file thì lấy giá trị trong đó.
  File có giá trị không hợp lệ (vd lựa chọn không còn trong `options`, không phải số) thì bị ghi đè bằng giá trị mặc định.
- Item không có `id`, và `text` / `button` / `link`, không lưu gì.
- `id` chỉ gồm chữ, số, `_`, `.`, `-` (tối đa 128 ký tự, không bắt đầu bằng `.` hoặc `-`); id khác không được lưu.
- `get` (switch, checkbox, progress) vẫn dùng được nhưng là tùy chọn: có khai báo thì lúc item xuất hiện app chạy `get` và lấy kết quả thay cho
  giá trị đã lưu (rồi lưu lại). Dùng khi trạng thái thật nằm ở hệ thống (vd `cat /sys/xxx`).
- Script đọc được giá trị bằng `state_get <id>`.

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
run   = 'echo "$ten"'            # chạy khi bấm Xác nhận
```

- Trang con là màn hình mới có nút quay lại ở thanh trên (và nút Back của máy); tiêu đề lấy từ `title` của item link.
- `style` (chỉ với `page`): `page` (mặc định) hoặc `dialog`. `dialog` mở trang trượt từ dưới lên che kín màn hình, thanh trên có nút đóng (X) thay vì mũi tên.
- Dialog có hai nút ở cuối: **Hủy** và **Xác nhận**.
  - Item trong dialog chỉ để truyền biến: đổi giá trị chỉ cập nhật biến, không chạy lệnh (`run`, `on`, `off`, `confirm` của item bị bỏ qua, nút "Chạy" của `input` ẩn).
  - **Xác nhận**: đóng dialog rồi chạy `run` của item link đã mở dialog, với các biến vừa chọn. `shell`, `timeout`, `confirm` của link áp dụng như button. Link không có `run` thì chỉ lưu biến.
  - **Hủy**, nút X hoặc Back của máy: đóng dialog và khôi phục biến về giá trị lúc mở.
- `button` và `link` không dùng được trong dialog: bị bỏ qua kèm cảnh báo.
- Cùng một file trang con được nhiều link trỏ tới thì dùng `title` và `style` của link đầu tiên.
- File trang con viết như file trang thường (mục 4); trong đó có thể có link tới trang con khác.
- Có cả `tab` và `page` thì dùng `tab`. Thiếu cả hai: bỏ item. `tab` không tồn tại: ghi cảnh báo.
- Biến (`id`) của trang con dùng chung kho biến với các tab.

`checkbox`, `radio`, `dropdown`, `progress` – ghi chú:

- `checkbox` dùng đúng quy tắc của `switch`: `get` in `1`/`true`/`on` là bật, `on` / `off` chạy khi đổi trạng thái, chỉ đổi khi lệnh thành công. Bấm cả hàng cũng đổi.
- `radio` khai báo `options` như `select` (mục 7), nhãn hiện trên giao diện, giá trị truyền qua `$id`.
- `dropdown` khai báo `options` như `select` (mục 7). Bên phải hàng hiện nhãn đang chọn kèm mũi tên lên/xuống; bấm vào thì menu thả xuống ngay dưới hàng, căn mép phải (thiếu chỗ phía dưới thì lật lên trên, danh sách dài thì cuộn được). Mục đang chọn nổi nền màu chủ đạo và có dấu tích bên phải. Chọn xong đóng menu và chạy `run` như `select`.
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

Áp dụng cho mọi item có chạy lệnh: `button`, `switch`, `checkbox`, `input`, `select`, `radio`, `dropdown`, `slider`.

| `shell` | Hành vi |
|---|---|
| `log` (mặc định) | Hiện log đầy đủ của lệnh vừa chạy |
| `live` | Như `log` nhưng bảng log mở ngay khi bắt đầu chạy và hiện từng dòng ngay khi lệnh in ra (tự cuộn xuống, xong thì ghi mã thoát) |
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
