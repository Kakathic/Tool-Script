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
`visible_if` (xem mục 8, 9).

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
id    = "switches"
title = "Công tắc"              # bắt buộc, tên tab
icon  = "icons/home.svg"        # bắt buộc, đường dẫn icon tab
file  = "pages/switches.toml"
```

- `[app] env` (tùy chọn): đoạn shell chạy **đầu tiên** trước mọi lệnh của app: `run`, `on`, `off`, `get`, lệnh của link dialog và lệnh khởi động lại máy. Dùng để đặt `PATH`, nạp hàm dùng chung, v.v.
  - Được ghép vào đầu mỗi lệnh (cách bằng xuống dòng), trước cả các biến `export` của item, nên không dùng được `$id` của item trong `env`.
  - Biến dựng sẵn (`SDK`, `ABI`, `ROOT_MODE`) cũng chưa có khi `env` chạy.
  - Script khởi động `assets/scripts/init.sh` chạy trước khi nạp TOML nên không áp dụng `env` (nhưng có áp dụng `env.sh`, xem bên dưới).
  - Thứ tự: `env.sh` → `[app] env` → các biến `export` của item → lệnh.
  - `env` lỗi cú pháp sẽ làm mọi lệnh lỗi theo; viết nhiều dòng bằng chuỗi ba nháy `"""..."""`.
- Mỗi `[[tab]]` là một tab ở thanh dưới. `title` và `icon` bắt buộc.
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

Các file chạy được nằm trong thư viện native của app: `app/src/main/jniLibs/<abi>/lib_<tên>.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
Android cài đúng ABI của máy và giải nén ra `$LIB_DIR` (`useLegacyPackaging = true` trong `app/build.gradle.kts`), nơi app thường không root cũng được chạy file.
Mỗi lần mở app, app tạo symlink `files/bin/<tên>` (`$BIN_DIR`) trỏ tới `lib_<tên>.so` tương ứng. `$BIN_DIR` đứng đầu `PATH` trong `env.sh`,
nên mọi lệnh của app gọi thẳng được `busybox`. Thêm lệnh mới: bỏ file `lib_<tên>.so` vào đủ các thư mục ABI.

| Lệnh | Nội dung |
|---|---|
| `busybox` | BusyBox 1.36.1 (bản NDK của osm0sis, qua Magisk-Modules-Repo/busybox-ndk), biên dịch tĩnh. Gọi applet: `busybox ls`, `busybox wget`... (chưa tạo symlink applet để không đè lệnh hệ thống) |

- Trang `pages.toml` có nhóm nút "Busybox dựng sẵn (bin)" để kiểm tra.
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
shell = "live"               # chế độ hiển thị khi chạy (mặc định live)
lock  = "ROOT_MODE != root"
lock_mode = "readonly"
```

## 5. Bố cục

- Thanh trên cùng: tiêu đề là tên app (`[app] name`).
  Bên phải có 2 icon riêng biệt: Thông tin (menu thả xuống tùy chỉnh chủ đề: Theo hệ thống / Sáng / Tối / 3 chế độ Màu động (Monet) có sẵn của Miuix,
  lựa chọn được lưu lại) và Nguồn (menu: khởi động lại ứng dụng, hoặc khởi động lại máy bằng `reboot`, cần root).
- Lề ngoài thẻ 16dp (trái, phải, trên, dưới), khoảng cách giữa các thẻ 12dp, đệm bên trong thẻ: trái, phải, dưới 16dp, trên 12dp. Tiêu đề group nằm ngoài thẻ, dính sát thẻ của nó.
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
| `title` | có (riêng `[[group]]` không bắt buộc) | Tiêu đề hiển thị |
| `desc` | không | Mô tả phụ |
| `icon` | không | Icon bên trái tiêu đề, đường dẫn như icon tab (`icons/x.svg` trong configs, hoặc `/sdcard/...`). Mặc định tô màu theo theme (xem `icon_tint`). Dùng được cho mọi type trừ `text` (bị bỏ qua); `group` chưa hỗ trợ |
| `icon_size` | không | Kích thước icon (dp), số dương, mặc định `24` |
| `icon_tint` | không | `true` (mặc định): tô icon theo màu theme; `false`: giữ nguyên màu của ảnh (PNG / SVG nhiều màu) |
| `visible_if` | không | Điều kiện hiển thị (mục 8) |
| `lock` | không | Điều kiện khóa item (mục 9) |
| `lock_mode` | không | `readonly` (mặc định) hoặc `hide` |
| `shell` | không | Chế độ hiển thị khi chạy lệnh (mục 10), mặc định `live` |
| `confirm` | không | Nội dung hộp thoại xác nhận trước khi chạy |
| `timeout` | không | Giây, mặc định 30 |

## 7. Các loại item (type)

| type | Trường riêng |
|---|---|
| `text` | `content` |
| `button` | `run` |
| `switch` | `on`, `off`, `get` |
| `input` | `hint`, `default`, `run`, `input_type` (`text`/`number`) |
| `slider` | `min`, `max`, `step`, `default`, `run` |
| `link` | `tab` (id của một `[[tab]]`, chuyển tab) hoặc `page` (đường dẫn file trang con, mở trang mới) |
| `checkbox` | `on`, `off`, `get` (giống `switch`, hiện ô tick) |
| `radio` | `options`, `default`, `run` (hiện sẵn mọi lựa chọn, mỗi dòng một nút tròn) |
| `dropdown` | `options`, `default`, `run` (chọn bằng menu thả xuống) |
| `multiselect` | `options`, `default`, `run`, `columns`, `separator` (chọn nhiều mục, xem bên dưới) |
| `singleselect` | `options`, `default`, `run`, `columns` (chọn một mục có Xác nhận, xem bên dưới) |
| `progress` | `min`, `max`, `default`, `get` (chỉ để xem, không có lệnh chạy) |

`group` không phải một `type`: nhóm khai báo bằng `[[group]]` riêng, item con
nằm trong `[[group.item]]`.

```toml
[[item]]                      # item đứng ngoài nhóm
type    = "text"
title   = "Chào mừng"
content = "Nội dung"

[[group]]
title = "Công tắc"            # tùy chọn; hiện sát phía trên thẻ. Bỏ trống thì group chỉ có thẻ, không có tiêu đề

[[group.item]]
type  = "switch"
id    = "game_mode"
title = "Chế độ game"
```

- `[[group]]` dùng được `title` (không bắt buộc), `visible_if`, `lock`, `lock_mode`; không có `type`.
- Thứ tự `[[item]]` và `[[group]]` trên trang theo thứ tự xuất hiện trong file.
- Các item con trong một `[[group]]` nằm chung trong một thẻ lớn. `[[item]]` đứng ngoài group thì mỗi item một thẻ riêng.
- Group mà mọi item con đều bị ẩn (`visible_if`, `lock_mode = "hide"`) thì ẩn luôn cả group (kể cả tiêu đề nếu có).
- Chưa hỗ trợ group lồng group (`[[group.group]]`).

### Lưu giá trị theo id

Mọi item có `id` và có giá trị (`switch`, `checkbox`, `input`, `radio`, `dropdown`, `multiselect`, `singleselect`, `slider`, `progress`) được lưu tự động:

- Mỗi `id` một file, **tên file = id**, nằm trong `$STATE_DIR` (`files/state`). Nội dung là giá trị kèm xuống dòng, cùng định dạng với `state_set`
  (`switch` / `checkbox` lưu `1` hoặc `0`).
- Mỗi lần giá trị đổi (với `switch`, `checkbox`, `radio`, `dropdown`: sau khi lệnh chạy thành công) app ghi lại file.
- Lúc mở app: chưa có file thì app tạo file với giá trị mặc định (`default`, hoặc `0`, `min`, lựa chọn đầu); có file thì lấy giá trị trong đó.
  File có giá trị không hợp lệ (vd lựa chọn không còn trong `options`, không phải số) thì bị ghi đè bằng giá trị mặc định.
- Item không có `id`, và `text` / `button` / `link`, không lưu gì.
- `id` chỉ gồm chữ, số, `_`, `.`, `-` (tối đa 128 ký tự, không bắt đầu bằng `.` hoặc `-`); id khác không được lưu.
- `get` (switch, checkbox, progress) vẫn dùng được nhưng là tùy chọn: có khai báo thì lúc item xuất hiện app chạy `get` và lấy kết quả thay cho
  giá trị đã lưu (rồi lưu lại). Dùng khi trạng thái thật nằm ở hệ thống (vd `cat /sys/xxx`).
- Script đọc được giá trị bằng `state_get <id>`.

Cách khai báo `options` (dùng cho `radio`, `dropdown`, `multiselect`, `singleselect`):

```toml
[[item]]
type  = "radio"
id    = "governor"
title = "Governor"
run   = "echo $governor > /sys/yyy"
options = [
  "Hiệu năng|performance",   # "nhãn|giá trị"
  "Tiết kiệm|powersave",
  "schedutil",               # không có | thì nhãn = giá trị
]
```

- `columns` (tùy chọn, chỉ với `multiselect` và `singleselect`): số cột lựa chọn trong hộp thoại, `1` (mặc định, mỗi lựa chọn một dòng) hoặc `2` (hai lựa chọn mỗi hàng). Giá trị khác thì cảnh báo và dùng `1`. `columns` chỉ có tác dụng với hộp thoại nhỏ (dưới 6 mục), chế độ toàn màn hình luôn một cột.
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
  Mở trang con: trang mới trượt vào từ bên phải, trang bên dưới lùi nhẹ sang trái; đóng thì ngược lại (dialog vẫn trượt từ dưới lên).
- `style` (chỉ với `page`): `page` (mặc định) hoặc `dialog`. `dialog` mở trang trượt từ dưới lên, thanh trên có nút đóng (X) thay vì mũi tên.
  - Dialog là sheet trượt từ dưới lên, nền mờ phía sau (bấm vào nền mờ là Hủy). Chiều cao mở **tự tính theo nội dung thật** (thanh trên + toàn bộ mục + hai nút), tối thiểu 30% màn hình; nội dung cao quá 80% màn hình thì mở **toàn màn hình**.
  - **Kéo thanh trên** để đổi chiều cao. Thả tay: dưới 30% thì **đóng** như Hủy, trên 80% thì **lên toàn màn hình**, ở giữa thì giữ nguyên chiều cao vừa kéo. **Vuốt mạnh lên thì lên toàn màn hình, vuốt mạnh xuống thì đóng** (như Hủy); vuốt nhẹ thì chỉ đổi chiều cao theo đà vuốt (giảm không thấp hơn 30% và không đóng, tăng vượt 80% thì lên toàn màn hình). Cuộn nội dung lên khi chưa full thì sheet mở rộng trước; nội dung đã ở đầu mà còn kéo xuống thì sheet thu lại (không thấp hơn 30%, không đóng).
  - Hai nút **Hủy** và **Xác nhận** cố định ở đáy sheet; kéo sheet thấp xuống thì các mục phía trên bị che dần, hai nút vẫn thấy.
- Dialog có hai nút ở cuối: **Hủy** và **Xác nhận**.
  - Item trong dialog chỉ để truyền biến: đổi giá trị chỉ cập nhật biến, không chạy lệnh (`run`, `on`, `off`, `confirm` của item bị bỏ qua, nút "Chạy" của `input` ẩn).
  - **Xác nhận**: đóng dialog rồi chạy `run` của item link đã mở dialog, với các biến vừa chọn. `shell`, `timeout`, `confirm` của link áp dụng như button. Link không có `run` thì chỉ lưu biến.
  - **Hủy**, nút X hoặc Back của máy: đóng dialog và khôi phục biến về giá trị lúc mở.
- `button` và `link` không dùng được trong dialog: bị bỏ qua kèm cảnh báo.
- Cùng một file trang con được nhiều link trỏ tới thì dùng `title` và `style` của link đầu tiên.
- File trang con viết như file trang thường (mục 4); trong đó có thể có link tới trang con khác.
- Có cả `tab` và `page` thì dùng `tab`. Thiếu cả hai: bỏ item. `tab` không tồn tại: ghi cảnh báo.
- Biến (`id`) của trang con dùng chung kho biến với các tab.

`multiselect` – chọn nhiều mục:

```toml
[[item]]
type      = "multiselect"
id        = "features"
title     = "Tính năng bật"
options   = ["Âm thanh|audio", "Mạng|net", "Màn hình|screen"]
default   = "audio\nnet"     # tùy chọn: các giá trị chọn sẵn, cách nhau bằng separator (kiểu cũ "audio,net" vẫn đọc được khi separator là xuống dòng)
columns   = 2                # tùy chọn: 1 (mặc định) hoặc 2 cột trong hộp thoại
separator = ","              # tùy chọn: dấu phân tách trong $id, mặc định là xuống dòng (mỗi giá trị một dòng)
run       = 'echo "Đã chọn: $features"'
```

- `options` khai báo như mục 7. Hàng hiện nhãn các mục đã chọn (không có thì "Chưa chọn"); bấm mở hộp thoại, mỗi mục là một thẻ bo góc không viền (chưa chọn: nền xám nhạt; đã chọn: nền và chữ màu chủ đạo), chữ bên trái, ô tích bên phải. Riêng khi `columns = 1` (mặc định) thì bỏ nền thẻ, chỉ còn chữ và ô tích, màu chữ giữ nguyên khi chọn; `columns = 2` giữ thẻ có nền.
- Hộp thoại có hai nút **Hủy** và **Xác nhận**. Chọn thoải mái, chỉ khi bấm Xác nhận mới lưu và chạy `run` một lần với `$id` mới; Hủy hoặc Back thì bỏ thay đổi. Bấm Xác nhận mà không đổi gì thì không chạy `run`.
- `$id` là các **giá trị** đã chọn nối bằng `separator` (mặc định **mỗi giá trị một dòng**), theo thứ tự khai báo trong `options`; chưa chọn gì thì rỗng. Ví dụ chọn 1, 2, 3, 4 thì `echo "$id"` in ra bốn dòng `1` `2` `3` `4`; banner hiện tối đa 8 dòng. Giá trị trong `options` không được chứa `separator` (có thì cảnh báo).
- `default` lấy các giá trị hợp lệ; không khai báo thì không chọn gì. File đã lưu có giá trị không còn trong `options` thì bị lọc bỏ.

`singleselect` – chọn một mục, có Xác nhận:

```toml
[[item]]
type    = "singleselect"
id      = "mode"
title   = "Chế độ"
options = ["Hiệu năng|performance", "Tiết kiệm|powersave"]
default = "performance"      # tùy chọn: mặc định là lựa chọn đầu
columns = 2                  # tùy chọn: 1 (mặc định) hoặc 2 cột trong hộp thoại
run     = 'echo "Đã chọn: $mode"'
```

- `options` khai báo như mục 7. Hàng hiện nhãn đang chọn kèm mũi tên lên/xuống; bấm mở hộp thoại, mỗi mục là một thẻ bo góc không viền (chưa chọn: nền xám nhạt; đã chọn: nền và chữ màu chủ đạo), chữ bên trái, ô tích bên phải (chỉ tích được một mục).
- Hộp thoại có hai nút **Hủy** và **Xác nhận**. Chọn thoải mái, chỉ khi bấm Xác nhận mới lưu và chạy `run` một lần với `$id` mới; Hủy hoặc Back thì bỏ thay đổi. Bấm Xác nhận mà không đổi gì thì không chạy `run`.
- `$id` là **giá trị** của mục đã chọn. `default` không hợp lệ thì dùng lựa chọn đầu; file đã lưu có giá trị không còn trong `options` thì bị ghi đè bằng mặc định.

`multiselect` / `singleselect` – chế độ toàn màn hình:

- Có **từ 6 mục trở lên** trong `options` thì bấm hàng sẽ mở **toàn màn hình** thay cho hộp thoại nhỏ. Dưới 6 mục giữ hộp thoại như cũ. Ngưỡng cố định trong code (`FULLSCREEN_MIN_OPTIONS`, `ui/render/SelectScreen.kt`), không cấu hình trong TOML.
- Màn hình gồm tiêu đề, ô **Tìm kiếm**, danh sách (một cột, `columns` bị bỏ qua), rồi hai nút **Hủy** và **Xác nhận**. Mọi thành phần có lề ngoài hai bên 16dp, đệm trong thẻ như trên; danh sách nằm trong một thẻ bo góc, mỗi dòng bấm được trên toàn chiều ngang thẻ và không có hiệu ứng nhấn. Màn hình **trượt từ dưới lên** khi mở và trượt xuống khi đóng. Cách lưu và chạy `run` y hệt hộp thoại: chỉ khi bấm Xác nhận, chỉ khi có thay đổi. Hủy hoặc Back thì bỏ thay đổi.
- Ô tìm lọc theo **nhãn**, không phân biệt hoa thường và dấu tiếng Việt (gõ `da lat` khớp `Đà Lạt`).
- `multiselect` có thêm ô **Chọn tất cả** phía trên danh sách. Ô này chỉ tác động lên các mục **đang hiện trong kết quả tìm** (chưa tìm gì thì là toàn bộ danh sách): chưa chọn hết thì chọn hết các mục đang hiện, đã chọn hết thì bỏ chọn các mục đang hiện. Mục nằm ngoài kết quả tìm **không bị đụng tới**, giữ nguyên trạng thái trước đó. Ô có ba trạng thái: trống, gạch ngang (chọn một phần), dấu tích (chọn hết). Chân màn hình hiện `Đã chọn x/y` tính trên toàn bộ danh sách.
- `singleselect` bấm một dòng để chọn (nút tròn), dòng đã chọn đổi chữ sang màu chủ đạo, chưa lưu cho tới khi bấm Xác nhận. `multiselect` giữ nguyên màu chữ, chỉ đổi ô tích.

`checkbox`, `radio`, `dropdown`, `progress` – ghi chú:

- `checkbox` dùng đúng quy tắc của `switch`: `get` in `1`/`true`/`on` là bật, `on` / `off` chạy khi đổi trạng thái, chỉ đổi khi lệnh thành công. Bấm cả hàng cũng đổi.
- `radio` khai báo `options` như mục 7, nhãn hiện trên giao diện, giá trị truyền qua `$id`.
- `dropdown` khai báo `options` như mục 7. Bên phải hàng hiện nhãn đang chọn kèm mũi tên lên/xuống; bấm vào thì menu thả xuống ngay dưới hàng, căn mép phải (thiếu chỗ phía dưới thì lật lên trên, danh sách dài thì cuộn được). Mục đang chọn nổi nền màu chủ đạo và có dấu tích bên phải. Chọn xong đóng menu và chạy `run`.
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

Áp dụng cho mọi item có chạy lệnh: `button`, `switch`, `checkbox`, `input`, `multiselect`, `singleselect`, `radio`, `dropdown`, `slider`.

| `shell` | Hành vi |
|---|---|
| `live` (mặc định) | Bảng log mở ngay khi bắt đầu chạy và hiện từng dòng ngay khi lệnh in ra (tự cuộn xuống). Nhãn trạng thái (Đang chạy / Xong / Lỗi / Hết thời gian chờ) và mã thoát nằm riêng phía trên khung log, khung log chỉ chứa phần lệnh in ra; nút Sao chép chép phần đó |
| `full` | Như `live` nhưng log mở **toàn màn hình** ngay khi bắt đầu chạy, bố cục giống màn hình lỗi: nút **Thu nhỏ** (về bảng log thường) ở hàng tiêu đề, khung log nhấn giữ để chọn và sao chép chữ, hàng nút dưới cùng gồm **Chia sẻ** và nút cuối đổi theo trạng thái: đang chạy là **Hủy** (vàng, dừng lệnh và đóng log), xong là **Thoát** (xanh), lỗi là **Kết thúc: <mã thoát>** (đỏ; hết giờ thì "Kết thúc: hết giờ"). Thanh tiến trình chỉ hiện lúc đang chạy, xong thì ẩn. Back của máy đóng log nhưng lệnh vẫn chạy tiếp |
| `hidden` | Chạy ẩn, không hiện gì |
| `banner` | Banner trong app. Khi lệnh bắt đầu chạy, banner hiện ngay dòng "Đang chạy script…" kèm thanh chạy vô hạn; lệnh xong thì banner đổi sang kết quả và thanh đếm ngược 5 giây. Thành công có in ra thì hiện đúng phần in ra (stdout, tối đa 300 ký tự), không in gì thì hiện "<title>: hoàn tất". Lỗi hoặc hết thời gian chờ thì banner nền đỏ nhạt, dòng đầu là mã lỗi, kèm phần lệnh in ra (stdout và stderr, tối đa 300 ký tự) |
| `notice` | Chạy như `banner` (banner "Đang chạy script…" khi đang chạy), xong thì banner ẩn đi và gửi thông báo hệ thống của Android với nội dung như quy tắc của `banner` (chỉ xin quyền thông báo khi dùng tới, Android 13+) |

Khi một lệnh `banner` / `notice` đang chạy, mọi item dùng hai kiểu này bị khóa (mờ, không bấm được) cho tới khi lệnh xong **và banner biến mất** (hết đếm ngược, bấm Ẩn hoặc vuốt). Banner có nút **Hủy** khi đang chạy (dừng lệnh, mở khóa ngay) và nút **Ẩn** khi đã xong; vuốt banner sang trái hoặc phải cũng làm đúng việc đó (đang chạy thì hủy, đã xong thì ẩn).

Bảng log `live` có thêm nút **Toàn màn hình** để mở rộng giữa chừng. Hủy lệnh `live` / `full` dừng hẳn tiến trình với backend thường; với backend root (su) lệnh có thể chạy tiếp ở nền nhưng log bị bỏ.

Thanh tiến trình (`live` và `full`): khi đang chạy, thanh nằm dưới nhãn trạng thái. Lệnh in dòng `@progress N` (0-100) thì thanh hiện đúng N% (số % hiện bên phải nhãn) và dòng đó không xuất hiện trong log; chưa in thì thanh chạy vô định. Lệnh xong không lỗi thì thanh đầy; lỗi hoặc hết giờ thì giữ mức cuối (không có `@progress` thì ẩn thanh).

Quy tắc chung: ở chế độ `hidden`, nếu lệnh lỗi thì hiện **toast** báo lỗi.

## 11. Khởi động và xử lý lỗi

- Script khởi động nằm trong assets, báo tiến trình bằng dòng `@progress N` (0-100).
- Script lỗi: vẫn vào app, hiện banner cảnh báo, xem lỗi trong Trung tâm lỗi.
- TOML parse lỗi: hiện file, dòng, mô tả lỗi.
- Thiếu trường bắt buộc hoặc `type` lạ: bỏ qua mục đó và ghi cảnh báo.
- Cảnh báo cấu hình hiện thành banner ở đầu danh sách của **trang có lỗi** (tab hoặc trang con), cuộn cùng nội dung; trang không có cảnh báo thì không hiện. Bấm Xem để đọc danh sách của trang đó, Ẩn để ẩn banner. Trang bị lỗi cú pháp TOML chỉ hiện thẻ lỗi, không kèm banner. Banner chung phía trên chỉ còn cho lỗi toàn app: script khởi động lỗi hoặc cảnh báo của `main.toml`.
- Lỗi không lưu ra file, chỉ giữ trong bộ nhớ của phiên chạy.
- **Demo crash**: để thử màn hình lỗi, đặt `run` của một `button` là một trong hai lệnh dựng sẵn dưới đây (nên kèm `confirm` vì app thoát thật):
  - `@crash`: ném lỗi trên luồng chính.
  - `@crash-thread`: ném lỗi trong luồng nền.
  Cả hai hiện Trung tâm lỗi "Ứng dụng bị lỗi" với stack trace rồi kill tiến trình. Mẫu có sẵn ở nhóm "Crash app" cuối `pages/output.toml`.
- **Demo lỗi cấu hình**: nhóm "Lỗi cấu hình (demo)" trong `pages/output.toml` có hai link tới trang con cố ý sai:
  - `pages/demo_error_syntax.toml`: sai cú pháp TOML, trang hiện thẻ "Không đọc được trang này" kèm file và dòng lỗi.
  - `pages/demo_error_warn.toml`: đúng cú pháp nhưng khai báo sai trường; mục thiếu trường bắt buộc bị bỏ qua, giá trị sai dùng mặc định, mỗi lỗi là một dòng trong "Cảnh báo cấu hình".
  Cảnh báo chỉ hiện trong chính trang có lỗi, nên các trang khác không bị ảnh hưởng; xóa nhóm và hai file để bỏ demo.

## 12. Để lại sau

Lộ trình thành phần Miuix (xếp từ dễ đến khó, mức 1 đã xong) nằm trong `README.md`, mục "Lộ trình thành phần Miuix".

- Nạp config từ thư mục ngoài và reload nóng.
- Đa ngôn ngữ theo khóa (`title_vi`, `title_en`...).
- Backend ROM: danh sách lệnh cho phép.
