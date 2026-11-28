# Tool Script – Schema TOML (bản nháp v0.6)

## 1. Backend thực thi (Executor)

Thứ tự ưu tiên khi app chọn backend:

| Ưu tiên | Backend | Giá trị `ROOT_MODE` |
|---|---|---|
| 1 | su (root) | `root` |
| 2 | ROM (lệnh tích hợp hệ thống) | `system` |
| 3 | adb (Wireless Debugging) | `adb` |
| 4 | Không root | `any` |

Backend hiện tại có sẵn dưới dạng biến `ROOT_MODE`, dùng trong `visible`, `readonly` và
`lock` (xem mục 8, 9).

**Tiến trình nền:** lệnh `cmd &` không chuyển hướng đầu ra thì tiến trình nền vẫn giữ đường ống đọc kết quả. App chỉ chờ đọc nốt khoảng 1 giây sau khi lệnh chính thoát rồi coi là xong; đầu ra đến muộn vẫn hiện trong log nhưng không nằm trong kết quả cuối. Nên viết `cmd > /dev/null 2>&1 &` (hoặc ghi vào tệp) để tiến trình nền không in vào log. Nút Hủy giết cả cây tiến trình, kể cả tiến trình nền.

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
env     = "export PATH=$PATH:/data/adb/bin"   # tùy chọn, xem bên dưới

[[tab]]
id    = "switches"
title = "Công tắc"              # bắt buộc, tên tab
icon  = "icons/home.svg"        # bắt buộc, đường dẫn icon tab
file  = "pages/switches.toml"
```

- **Đường dẫn trang** (`file` của tab, `page` của link, `lang`, `lang-sh`): không bắt đầu bằng `/` thì tính từ `assets/configs`; bắt đầu bằng `/` thì đọc thẳng file ngoài APK, ví dụ `file = "/sdcard/ToolScript/pages/extra.toml"`.
  - App chỉ đọc được nơi nó có quyền: thư mục riêng của app, hoặc nơi đã cấp quyền bộ nhớ. File không tồn tại hoặc không đọc được thì trang hiện lỗi "không đọc được file" (riêng `main.toml` luôn đọc từ assets).
  - `main.toml` vẫn nằm trong assets, nên chỉ trang con và tab mới trỏ ra ngoài được. Chưa có reload nóng.

- `[app] env` (tùy chọn): đoạn shell chạy **đầu tiên** trước mọi lệnh của app: `script`, `get`, lệnh của link dialog và lệnh khởi động lại máy. Dùng để đặt `PATH`, nạp hàm dùng chung, v.v.
  - Được ghép vào đầu mỗi lệnh (cách bằng xuống dòng), trước cả các biến `export` của item, nên không dùng được `$id` của item trong `env`.
  - Biến dựng sẵn (`SDK`, `ABI`, `ARCH`, `ROOT_MODE`) cũng chưa có khi `env` chạy.
  - Script khởi động `assets/scripts/init.sh` chạy trước khi nạp TOML nên không áp dụng `env` (nhưng có áp dụng `env.sh`, xem bên dưới).
  - Thứ tự: `env.sh` → `[app] env` → các biến `export` của item → lệnh.
  - `env` lỗi cú pháp sẽ làm mọi lệnh lỗi theo; viết nhiều dòng bằng chuỗi ba nháy `"""..."""`.
- Mỗi `[[tab]]` là một tab ở thanh dưới. `title` và `icon` bắt buộc.
- Thiếu `title` hoặc `icon`: bỏ tab đó và ghi cảnh báo vào log.
- `icon` là đường dẫn tới file icon:
  - Trong assets: `icons/home.svg` (tính từ thư mục configs).
  - Đường dẫn ngoài: `/sdcard/ToolScript/icons/home.png`.
- Thanh tab hiện tối đa 5 tab, tối thiểu 2 (xem mục 5).

### Ngôn ngữ (`@khóa`)

Chuỗi hiển thị trong TOML có thể viết `@khóa`; app thay bằng văn bản lấy từ bảng chuỗi được nạp từ `[app]`:

```toml
[app]
lang           = "lang/vi.txt"       # tệp khóa=văn bản
lang-sh        = "lang/lang.sh"      # tệp shell: những gì nó in ra là khóa=văn bản
load-string    = "wifi_title=Wi-Fi"  # khóa=văn bản nhập thẳng, để bổ sung vài khóa nhỏ
load-string-sh = "echo wifi_desc=Bật hoặc tắt"   # lệnh shell nhập thẳng (ví dụ echo)
```

- Cả bốn khóa đều tùy chọn và dùng cùng lúc được. Khóa nào xuất hiện sau thì **đè** khóa trước theo thứ tự `lang` < `lang-sh` < `load-string` < `load-string-sh`. `load-string` chủ yếu để thêm vài khóa lẻ mà không phải sửa tệp ngôn ngữ.
- **Định dạng** (giống bash, dùng cho cả tệp, đầu ra shell và chuỗi nhập thẳng): mỗi dòng `khóa=giá trị`.
  - Bỏ dòng trống và dòng bắt đầu bằng `#`; chấp nhận tiền tố `export ` và khoảng trắng quanh `=` (nên tệp TOML phẳng `khóa = "văn bản"` cũng đọc được).
  - Giá trị không nháy chạy đến hết dòng (hoặc đến ` #` bắt đầu ghi chú). Nháy đơn: nguyên văn. Nháy kép: hiểu `\n` (xuống dòng), `\t`, `\"`, `\\`, `\$`.
  - Khóa gồm chữ, số, gạch dưới, không bắt đầu bằng số. Khóa trùng thì dòng sau thắng. Dòng sai dạng chỉ bị bỏ qua kèm cảnh báo.
- `lang` và `lang-sh` là đường dẫn tính từ thư mục configs (assets), hoặc đường dẫn tuyệt đối bắt đầu bằng `/` để đọc file ngoài (xem mục 3). Đường dẫn theo ngôn ngữ chưa hỗ trợ.
- `lang-sh` và `load-string-sh` chạy bằng chính shell của app, sau `env.sh` và `[app] env`, tối đa **5 giây**; lỗi, hết giờ hoặc thoát khác 0 thì cảnh báo và bỏ nguồn đó. Chỉ stdout được đọc (stderr bị bỏ). Script chọn chuỗi theo ngôn ngữ bằng biến `$LANGAPP` (ngôn ngữ app đang dùng), `$LANGUAGE` (ngôn ngữ thiết bị) và `$COUNTRY` từ `env.sh`, ví dụ `lang-sh = "lang/lang.sh"` với `case "$LANGAPP" in vi) echo wifi_title=Wi-Fi ;; esac`.
- **Cách thay:** ở bất cứ chuỗi nào, nếu có `@khóa` mà **khóa đó có trong bảng** thì thay; không có thì giữ nguyên (nên `user@host`, `@progress` không bị đụng).
  - `@` không được đứng ngay sau chữ, số, gạch dưới hay `@` khác; `@khóa` nhận trọn một định danh ("Xin chào @ten." giữ dấu chấm). Cần viết liền chữ phía sau thì dùng `@{khóa}`.
  - Không thay lồng nhau: văn bản vừa thay vào không bị quét lại.
  - **Không thay** ở các khóa kỹ thuật: `id`, `type`, `icon`, `icon_size`, `icon_tint`, `divider`, `shell`, `columns`, `input_type`, `style`, `tab`, `page`, `file`, `visible`, `readonly`, `lock`, `timeout`, `min`, `max`, `step`, `default`, `separator`. Trong `options` chỉ phần **nhãn** được thay, giá trị (sau `|`) giữ nguyên.
  - Các khóa còn lại thay được, kể cả lệnh `script`, `get` và `[app] env`; bản dịch nằm trong lệnh shell nên tránh ký tự `'`, `"`, `$`, `` ` ``, `\` nếu lệnh đặt nó trong dấu nháy.
- **Đổi ngôn ngữ:** bảng chuỗi chỉ nạp lúc mở app (cùng lúc nạp TOML). Đổi `app_language` thì app đổi ngay, còn chuỗi `@khóa` chọn theo `$LANGAPP` (`lang-sh`, `load-string-sh`) chỉ đổi theo từ lần mở app sau.
- **Demo:** `main.toml` khai báo `lang-sh = "lang/lang.sh"`; script chọn chuỗi `vi` / `en` theo `$LANGAPP`. Xem nhóm "Trang đa ngôn ngữ" ở tab Tính năng mới (`pages/news.toml`): `@demo_lang_title`, `@demo_lang_body`, `@demo_lang_button`, `@demo_lang_echo`.
- Output của lệnh, nội dung log và biến không đi qua bước thay này.
- **Khóa lạ:** mọi khóa TOML không có trong code (thường là gõ sai, kể cả trong `[app]`, `[[tab]]`, `[[item]]`, `[[group]]`) đều hiện cảnh báo cấu hình. Khóa `lang` cũ (mã ngôn ngữ như `"vi"`) không còn nghĩa đó: giờ nó là đường dẫn tệp.

### env.sh (biến dùng chung cho mọi lệnh)

`assets/scripts/env.sh` (tùy chọn) chứa các biến dùng chung. Mỗi lần mở app, các key dạng `@KEY@` được thay bằng đường dẫn thật rồi ghi ra `no_backup/scripts/env.sh` (cùng thư mục với `init.sh`, nằm cạnh `$HOME`); **mọi lệnh shell chạy qua app đều nạp file này trước** (`script`, `on`, `off`, `get`, lệnh dialog, khởi động lại máy, và cả `init.sh`).

| Key | Giá trị |
|---|---|
| `@PACKAGE@` | tên gói của app |
| `@APP_DIR@` | thư mục dữ liệu của app |
| `@FILES_DIR@` | thư mục `files` của app |
| `@CACHE_DIR@` | thư mục cache của app |
| `@HOME_DIR@` | `$HOME` của app: `no_backup/home`, nơi app chép assets `bin`, `lib` ra; env.sh dùng nó làm `$HOME` (shell `cd` vào đây) |
| `@SCRIPTS_DIR@` | `no_backup/scripts` (cạnh `$HOME`): chứa `init.sh` (chép từ assets) và `env.sh` đã thay key |
| `@STATE_DIR@` | thư mục lưu trạng thái các tính năng (`files/state`, app tạo sẵn) |
| `@NOBACKUP_DIR@` | thư mục `no_backup` của app (không bị Auto Backup sao lưu), chứa `home` và `scripts` |
| `@BIN_DIR@` | thư mục lệnh dựng sẵn (`$HOME/bin`, nằm trong `PATH`, xem "Lệnh dựng sẵn" bên dưới) |
| `@LIB_DIR@` | thư mục thư viện native của app |
| `@ABI@` | ABI chính thức mà app đang chạy, một giá trị (vd `arm64-v8a`); `init.sh` dùng để chọn bản busybox |
| `@ARCH@` | kiến trúc CPU, lấy từ `Os.uname().machine` của Android (như `uname -m`): `aarch64`, `armv7l`, `x86_64`, `i686`... |
| `@EXT_ROOT@` | gốc bộ nhớ trong (thường `/storage/emulated/0`) |
| `@EXT_DIR@` | thư mục làm việc ngoài của app (`@EXT_ROOT@/ToolScript`) |
| `@LANGUAGE@` | ngôn ngữ của thiết bị (mã hai chữ thường, vd `vi`, `en`), không bị ngôn ngữ riêng của app ảnh hưởng |
| `@LANGAPP@` | ngôn ngữ app đang dùng (`vi` hoặc `en`): lựa chọn trong Cài đặt của app, hoặc ngôn ngữ thiết bị nếu app hỗ trợ (không thì `en`). Đổi ngôn ngữ trong Cài đặt thì app ghi lại `env.sh`, lệnh chạy sau đó thấy giá trị mới |
| `@COUNTRY@` | quốc gia của thiết bị (mã hai chữ hoa, vd `VN`, `US`); rỗng nếu ngôn ngữ hệ thống không kèm quốc gia |

```sh
export FILES_DIR="@FILES_DIR@"
export EXT_DIR="@EXT_DIR@"
export PATH="$FILES_DIR/bin:$PATH"
```

- Hai lệnh dựng sẵn `slog` và `glog` để script tự ghi / đọc cùng thư mục `$STATE_DIR` mà app dùng để lưu giá trị item (xem "Lưu giá trị theo id", mục 7):
  `slog <id> <giá trị>` ghi, `slog <id>` tạo file rỗng, `slog -d <id>` xóa; `glog <id>` đọc (chưa có file thì in giá trị app truyền vào biến `$id`, tức mặc định hoặc giá trị hiện tại, không tạo file; không có biến đó thì **không in gì**), `glog <id> <giá trị>` ghi nếu chưa có file rồi in giá trị.
  Khác `state_get` cũ: không có giá trị mặc định `0`. Item không cần dùng chúng: app tự lưu và tự đọc lại giá trị.

### Chép assets và lệnh dựng sẵn (bin)

**Chép assets.** Lúc khởi động (trước `init.sh`), app chép **các thư mục cần thiết trong assets** (`scripts`, `bin`, `lib`) ra `no_backup` (không bị Auto Backup sao lưu), giữ nguyên đường dẫn: `assets/scripts/init.sh` -> `no_backup/scripts/init.sh` (cạnh `$HOME`), `assets/bin/glog` -> `$HOME/bin/glog`, `assets/lib/<abi>/libbusybox.so` -> `$HOME/lib/<abi>/libbusybox.so`.

- Chỉ chép thư mục trong danh sách `INCLUDED` của `util/AssetInstaller.kt` (thêm tên vào đó khi cần thêm thư mục); các thư mục khác như `configs` (app đọc thẳng từ assets) không chép. Trong đó `scripts/env.sh` cũng không chép (có key `@KEY@`, app tự ghi bản đã thay key, xem trên). Trong `lib/` chỉ chép thư mục của ABI đang chạy (`$ABI`).
- Tệp trong `bin/` được `chmod 755`. Tệp được ghi ra tệp tạm rồi đổi tên nên không bị lỗi "text file busy".
- Không có tệp đánh dấu: tệp đã chép mang `lastModified` = thời điểm cập nhật app, trùng thì bỏ qua, lệch hoặc mất thì chép lại. Tệp đã chép mà nay không còn trong assets không bị xóa.

**Lệnh dựng sẵn.** `$HOME/bin` (`$BIN_DIR`) nằm trong `PATH` (xem env.sh), nên:

- `assets/bin/<tên>`: script dùng chung mọi ABI (glog, slog, killtree), chép là gọi được ngay. Script nên có dòng `#!/system/bin/sh`.
- `assets/lib/<abi>/libbusybox.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`): file ELF theo ABI. `init.sh` lấy bản của `$ABI`, chép thành `$BIN_DIR/busybox` rồi `chmod 755` và chạy thử. Thêm lệnh ELF mới: bỏ `lib<tên>.so` vào đủ các thư mục ABI rồi thêm đoạn cài tương tự vào `init.sh`.

Chạy trực tiếp được vì app target SDK 28 (xem README); file ELF phải liên kết tĩnh, không dùng `linker` dự phòng.

| Lệnh | Nội dung |
|---|---|
| `busybox` | BusyBox 1.36.1 (bản NDK của osm0sis, qua Magisk-Modules-Repo/busybox-ndk), biên dịch tĩnh. Gọi applet: `busybox ls`, `busybox wget`... (chưa tạo symlink applet để không đè lệnh hệ thống) |
| `glog`, `slog` | Đọc / ghi giá trị trong `$STATE_DIR` (xem env.sh ở trên) |
| `killtree` | Giết toàn bộ tiến trình con cháu (và cả tiến trình gốc): `killtree [thông báo] [tệp / thư mục cần xóa]`. Gốc là `$shell_progres`: số = PID, chữ = mẫu cho `pgrep -f`. Nút Hủy của app dùng lệnh này (xem mục 10) |

- Trang `pages/news.toml` có nhóm nút "Biến env.sh, lệnh dựng sẵn, lưu giá trị" để kiểm tra.
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
script = "echo $game_mode > /sys/xxx"
get   = "cat /sys/xxx"       # tùy chọn: đọc trạng thái thật bằng shell lúc item xuất hiện
shell = "live"               # chế độ hiển thị khi chạy (mặc định live)
readonly = "ROOT_MODE != root"
```

## 5. Bố cục

- Thanh trên cùng: tiêu đề là tên app (`[app] name`), lúc mở rộng hiện chữ lớn kiểu Miuix bên dưới hàng icon; vuốt lên thì thu thành chữ nhỏ căn trái, vuốt xuống tới đầu danh sách thì mở rộng lại (một trạng thái chung cho mọi tab).
  Bên phải có 2 icon riêng biệt: Thông tin (menu thả xuống tùy chỉnh chủ đề: một hàng ngang chọn Hệ thống / Sáng / Tối, kèm các ô tích Màu động (Monet) của Miuix,
  Rung khi chạm và Làm mờ nền; lựa chọn được lưu lại) và Nguồn (menu: khởi động lại ứng dụng, hoặc khởi động lại máy bằng `reboot`, cần root).
- Lề ngoài thẻ 16dp (trái, phải, trên, dưới), khoảng cách giữa các thẻ 16dp, đệm bên trong thẻ: trái, phải 16dp, trên và dưới 12dp. Tiêu đề group nằm ngoài thẻ, dính sát thẻ của nó.
- Thanh tab dưới cùng, mỗi trang một tab (icon + title).
- Cửa sổ tab trượt: hiện tối đa 5 tab. Từ 6 tab trở lên, vuốt tới tab thứ 5
  của cửa sổ thì tab kế tiếp hiện ra bên phải và tab đầu ẩn đi; vuốt ngược
  về tab đầu của cửa sổ thì tab trước đó hiện lại.
- Chỉ có 1 tab: ẩn thanh tab, hiện trang toàn màn hình.

## 6. Các trường chung của item

| Trường | Bắt buộc | Mô tả |
|---|---|---|
| `type` | có | Loại thành phần |
| `id` | tùy loại | Định danh, dùng làm tên biến |
| `title` | có (`[[group]]` không bắt buộc; `text` có thể bỏ nếu có `desc`) | Tiêu đề hiển thị |
| `desc` | không | Mô tả phụ |
| `icon` | không | Icon bên trái tiêu đề, đường dẫn như icon tab (`icons/x.svg` trong configs, hoặc `/sdcard/...`). Mặc định tô màu theo theme (xem `icon_tint`). Dùng được cho mọi type trừ `text` (bị bỏ qua); `group` chưa hỗ trợ |
| `icon_size` | không | Kích thước icon (dp), số dương, mặc định `24` |
| `icon_tint` | không | `true` (mặc định): tô icon theo màu theme; `false`: giữ nguyên màu của ảnh (PNG / SVG nhiều màu) |
| `divider` | không | Chỉ có tác dụng với item con của `[[group]]`. `true`: vẽ đường kẻ ngăn cách ngay dưới item này (giữa nó và item hiện kế tiếp). Mặc định `false`. Không vẽ ở item cuối của group (kể cả khi các item sau bị ẩn) và group chỉ có một item; item đứng ngoài group bỏ qua khóa này |
| `visible` / `visible-sh` | không | Điều kiện hiện item, sai thì ẩn hẳn (mục 8, 9) |
| `readonly` / `readonly-sh` | không | Điều kiện làm mờ item, không bấm được (mục 9) |
| `lock` / `lock-sh` | không | Điều kiện khóa: bấm vào thì hiện snackbar lý do (mục 9) |
| `refresh` | không | `true` làm mới cả trang, hoặc `"id1,id2"` làm mới các item đó (chỉ trong cùng trang), sau khi lệnh của item xong thành công (mục 9.1) |
| `shell` | không | Chế độ hiển thị khi chạy lệnh (mục 10), mặc định `live` |
| `confirm` | không | Nội dung hộp thoại xác nhận trước khi chạy |
| `timeout` | không | Giây. Không khai báo thì lệnh `script` chạy **không giới hạn thời gian**; có khai báo thì quá số giây đó bị dừng và báo hết giờ. Riêng lệnh `get` không khai báo vẫn giới hạn 30 giây để không treo giao diện |

## 7. Các loại item (type)

| type | Trường riêng |
|---|---|
| `text` | (không có trường riêng; đoạn chữ viết ở `desc`) |
| `button` | `script` |
| `switch` | `script` (chạy khi đổi, `$id` = `1` bật / `0` tắt), `get` |
| `input` | `hint`, `default`, `script`, `input_type` (`text`/`number`) |
| `slider` | `min`, `max`, `step`, `default`, `script` |
| `link` | `tab` (id của một `[[tab]]`, chuyển tab) hoặc `page` (đường dẫn file trang con, mở trang mới) |
| `checkbox` | `script`, `get` (giống `switch`, hiện ô tick) |
| `radio` | `options`, `default`, `script` (hiện sẵn mọi lựa chọn, mỗi dòng một nút tròn) |
| `dropdown` | `options`, `default`, `script` (chọn bằng menu thả xuống) |
| `multiselect` | `options`, `default`, `script`, `columns`, `separator` (chọn nhiều mục, xem bên dưới) |
| `singleselect` | `options`, `default`, `script`, `columns` (chọn một mục: hộp nhỏ chạm là chọn luôn, toàn màn hình có Xác nhận, xem bên dưới) |
| `applist` | `get` (bắt buộc, lệnh in danh sách app), `multi`, `default`, `script`, `separator` (chọn app trên máy, xem bên dưới) |
| `progress` | `min`, `max`, `default`, `get` (chỉ để xem, không có lệnh chạy) |

`slider`: `max < min` thì hai giá trị được đổi chỗ; `min = max` thì `max` được đặt lại thành `min + step`; `step` không chia hết `max - min` thì thanh trượt không tới được `max`. Cả ba trường hợp đều hiện cảnh báo cấu hình ở đầu trang.

`group` không phải một `type`: nhóm khai báo bằng `[[group]]` riêng, item con
nằm trong `[[group.item]]`.

```toml
[[item]]                      # item đứng ngoài nhóm
type    = "text"
title   = "Chào mừng"
desc    = "Nội dung"

[[group]]
title = "Công tắc"            # tùy chọn; hiện sát phía trên thẻ. Bỏ trống thì group chỉ có thẻ, không có tiêu đề

[[group.item]]
type  = "switch"
id    = "game_mode"
title = "Chế độ game"
```

- `[[group]]` dùng được `title` (không bắt buộc), `visible`, `readonly`, `lock` (và các bản `-sh`); không có `type`. `readonly` của group làm mờ mọi item con; `lock` của group khiến bấm vào item con nào cũng hiện lý do.
- Thứ tự `[[item]]` và `[[group]]` trên trang theo thứ tự xuất hiện trong file.
- Các item con trong một `[[group]]` nằm chung trong một thẻ lớn. `[[item]]` đứng ngoài group thì mỗi item một thẻ riêng.
- Muốn kẻ ngang giữa các item con, đặt `divider = true` ở item đứng trên đường kẻ (ví dụ `divider = true` ở item một thì đường kẻ nằm giữa item một và item hai). Đường kẻ không vẽ sau item cuối đang hiện của group.
- Group mà mọi item con đều bị ẩn (`visible` / `visible-sh`) thì ẩn luôn cả group (kể cả tiêu đề nếu có).
- Chưa hỗ trợ group lồng group (`[[group.group]]`).

### Lưu giá trị theo id

Mọi item có `id` và có giá trị (`switch`, `checkbox`, `input`, `radio`, `dropdown`, `multiselect`, `singleselect`, `applist`, `slider`, `progress`) được lưu tự động:

- Mỗi `id` một file, **tên file = id**, nằm trong `$STATE_DIR` (`files/state`). Nội dung là giá trị kèm xuống dòng, cùng định dạng với `slog`
  (`switch` / `checkbox` lưu `1` hoặc `0`).
- Mỗi lần giá trị đổi (với `switch`, `checkbox`, `radio`, `dropdown`: sau khi lệnh chạy thành công) app ghi lại file.
- File chỉ được tạo khi giá trị đổi. Lúc mở app: chưa có file thì dùng giá trị mặc định trong bộ nhớ (`default`, hoặc `0`, `min`, lựa chọn đầu), không tạo file; có file thì lấy giá trị trong đó.
  File có giá trị không hợp lệ (vd lựa chọn không còn trong `options`, không phải số) thì bị xóa, dùng mặc định.
- Item không có `id`, và `text` / `button` / `link`, không lưu gì.
- `id` chỉ gồm chữ, số, `_`, `.`, `-` (tối đa 128 ký tự, không bắt đầu bằng `.` hoặc `-`); id khác không được lưu.
- `get` (switch, checkbox, progress) vẫn dùng được nhưng là tùy chọn: có khai báo thì lúc item xuất hiện app chạy `get` và lấy kết quả thay cho
  giá trị đã lưu (rồi lưu lại). Dùng khi trạng thái thật nằm ở hệ thống (vd `cat /sys/xxx`).
- Script đọc được giá trị bằng `glog <id>`; lệnh chạy qua app còn dùng thẳng `$id` (app truyền sẵn mọi giá trị item, cả mặc định, nếu `id` là tên biến hợp lệ: chữ, số, `_`).

Cách khai báo `options` (dùng cho `radio`, `dropdown`, `multiselect`, `singleselect`):

```toml
[[item]]
type  = "radio"
id    = "governor"
title = "Governor"
script = "echo $governor > /sys/yyy"
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
style = "dialog"                 # dialog: dưới 5 mục thì nhỏ gọn giữa màn hình, từ 5 mục thì full màn hình
script = 'echo "$ten"'            # chạy khi bấm Xác nhận
```

- Trang con là màn hình mới có nút quay lại ở thanh trên (và nút Back của máy); tiêu đề lấy từ `title` của item link.
  Mở trang con: trang mới trượt vào từ bên phải, trang bên dưới lùi nhẹ sang trái; đóng thì ngược lại (dialog vẫn trượt từ dưới lên).
- `style` (chỉ với `page`): `page` (mặc định) hoặc `dialog`. `dialog` mở trang dưới dạng hộp thoại; chế độ tự chọn **theo số mục hiển thị** (mục trong `group` tính riêng từng mục con, bản thân `group` không tính), chốt một lần lúc mở:
  - **Dưới 5 mục: dialog nhỏ gọn** ở giữa màn hình, có tiêu đề, các mục (cuộn nếu cao) và hai nút **Hủy** / **Xác nhận**. Chạm ra ngoài hoặc Back là Hủy.
  - **Từ 5 mục: full màn hình**, trượt từ dưới lên, thanh trên có nút đóng (X) ở góc trái, hai nút **Hủy** / **Xác nhận** cố định ở đáy. Không kéo thu gọn, không có dấu tích.
- Dialog có hai nút ở cuối: **Hủy** và **Xác nhận**.
  - Item trong dialog chỉ để truyền biến: đổi giá trị chỉ cập nhật biến, không chạy lệnh (`script`, `confirm` của item bị bỏ qua, nút "Chạy" của `input` ẩn).
  - **Xác nhận**: đóng dialog rồi chạy `script` của item link đã mở dialog, với các biến vừa chọn. `shell`, `timeout`, `confirm` của link áp dụng như button. Link không có `script` thì chỉ lưu biến.
  - **Hủy**, nút X hoặc Back của máy: đóng dialog và khôi phục biến về giá trị lúc mở.
- `button` và `link` không dùng được trong dialog (đã có Hủy / Xác nhận): bị bỏ qua kèm cảnh báo.
- `singleselect` / `multiselect` trong dialog không có Xác nhận riêng (dialog ngoài đã có): `singleselect` chạm mục là chọn luôn và đóng; `multiselect` đổi là ghi vào biến ngay, đóng hộp bằng chạm ra ngoài, Back hoặc nút Đóng (màn hình chọn toàn màn hình). Hủy của dialog ngoài khôi phục lại các giá trị này.
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
script    = 'echo "Đã chọn: $features"'
```

- `options` khai báo như mục 7. Hàng hiện số mục đã chọn dạng `Đã chọn n` (không có thì "Chưa chọn"); bấm mở hộp thoại, mỗi mục là một thẻ bo góc không viền (chưa chọn: nền xám nhạt; đã chọn: nền và chữ màu chủ đạo), chữ bên trái, ô tích bên phải. Riêng khi `columns = 1` (mặc định) thì bỏ nền thẻ, chỉ còn chữ và ô tích, màu chữ giữ nguyên khi chọn; `columns = 2` giữ thẻ có nền.
- Hộp thoại có hai nút **Hủy** và **Xác nhận**. Chọn thoải mái, chỉ khi bấm Xác nhận mới lưu và chạy `script` một lần với `$id` mới; Hủy hoặc Back thì bỏ thay đổi. Bấm Xác nhận mà không đổi gì thì không chạy `script`.
- `$id` là các **giá trị** đã chọn nối bằng `separator` (mặc định **mỗi giá trị một dòng**), theo thứ tự khai báo trong `options`; chưa chọn gì thì rỗng. Ví dụ chọn 1, 2, 3, 4 thì `echo "$id"` in ra bốn dòng `1` `2` `3` `4`; banner hiện tối đa 8 dòng. Giá trị trong `options` không được chứa `separator` (có thì cảnh báo).
- `default` lấy các giá trị hợp lệ; không khai báo thì không chọn gì. File đã lưu có giá trị không còn trong `options` thì bị lọc bỏ.

`singleselect` – chọn một mục: hộp nhỏ (dưới 6 mục) chạm mục là lưu và chạy `script` luôn (chỉ khi giá trị đổi), không có nút, chạm ra ngoài hoặc Back để thoát; toàn màn hình (từ 6 mục) có Xác nhận:

```toml
[[item]]
type    = "singleselect"
id      = "mode"
title   = "Chế độ"
options = ["Hiệu năng|performance", "Tiết kiệm|powersave"]
default = "performance"      # tùy chọn: mặc định là lựa chọn đầu
columns = 2                  # tùy chọn: 1 (mặc định) hoặc 2 cột trong hộp thoại
script  = 'echo "Đã chọn: $mode"'
```

- `options` khai báo như mục 7. Hàng hiện nhãn đang chọn kèm mũi tên lên/xuống; bấm mở hộp thoại, mỗi mục là một thẻ bo góc không viền (chưa chọn: nền xám nhạt; đã chọn: nền và chữ màu chủ đạo), chữ bên trái, nút tròn bên phải (chỉ chọn được một mục). Riêng khi `columns = 1` (mặc định) thì bỏ nền thẻ, chỉ còn chữ và nút tròn, dòng đã chọn đổi chữ sang màu chủ đạo, giống chế độ toàn màn hình; `columns = 2` giữ thẻ có nền.
- Hộp thoại có hai nút **Hủy** và **Xác nhận**. Chọn thoải mái, chỉ khi bấm Xác nhận mới lưu và chạy `script` một lần với `$id` mới; Hủy hoặc Back thì bỏ thay đổi. Bấm Xác nhận mà không đổi gì thì không chạy `script`.
- `$id` là **giá trị** của mục đã chọn. `default` không hợp lệ thì dùng lựa chọn đầu; file đã lưu có giá trị không còn trong `options` thì bị ghi đè bằng mặc định.

`multiselect` / `singleselect` – chế độ toàn màn hình:

- Có **từ 6 mục trở lên** trong `options` thì bấm hàng sẽ mở **toàn màn hình** thay cho hộp thoại nhỏ. Dưới 6 mục giữ hộp thoại như cũ. Ngưỡng cố định trong code (`FULLSCREEN_MIN_OPTIONS`, `ui/render/SelectScreen.kt`), không cấu hình trong TOML.
- Màn hình gồm tiêu đề, ô **Tìm kiếm**, danh sách (một cột, `columns` bị bỏ qua), rồi hai nút **Hủy** và **Xác nhận**. Mọi thành phần có lề ngoài hai bên 16dp, đệm trong thẻ như trên; danh sách nằm trong một thẻ bo góc, mỗi dòng bấm được trên toàn chiều ngang thẻ và không có hiệu ứng nhấn. Màn hình **trượt từ dưới lên** khi mở và trượt xuống khi đóng. Cách lưu và chạy `script` y hệt hộp thoại: chỉ khi bấm Xác nhận, chỉ khi có thay đổi. Hủy hoặc Back thì bỏ thay đổi.
- Nền: khi bật **Làm mờ nền** (Android 13+) màn hình có nền bán trong suốt, trang phía sau mờ và tối nhẹ, giống log toàn màn hình; tắt blur hoặc máy không hỗ trợ thì nền đặc. Cũng vậy với dialog sheet khi kéo lên full màn hình.
- Ô tìm lọc theo **nhãn**, không phân biệt hoa thường và dấu tiếng Việt (gõ `da lat` khớp `Đà Lạt`).
- `multiselect` có thêm ô **Chọn tất cả** phía trên danh sách. Ô này chỉ tác động lên các mục **đang hiện trong kết quả tìm** (chưa tìm gì thì là toàn bộ danh sách): chưa chọn hết thì chọn hết các mục đang hiện, đã chọn hết thì bỏ chọn các mục đang hiện. Mục nằm ngoài kết quả tìm **không bị đụng tới**, giữ nguyên trạng thái trước đó. Ô có ba trạng thái: trống, gạch ngang (chọn một phần), dấu tích (chọn hết). Góc phải trên cùng màn hình hiện khung bo góc không viền, nền màu chủ đạo nhạt `Đã chọn x/y` tính trên toàn bộ danh sách.
- `singleselect` bấm một dòng để chọn (nút tròn), dòng đã chọn đổi chữ sang màu chủ đạo, chưa lưu cho tới khi bấm Xác nhận (chế độ toàn màn hình). `multiselect` giữ nguyên màu chữ, chỉ đổi ô tích.

`applist` – chọn ứng dụng trên máy. Không khai báo `options`: danh sách app do lệnh `get` in ra.

```toml
[[item]]
type    = "applist"
id      = "target_apps"
title   = "Ứng dụng áp dụng"
get     = "pm list packages -3"      # bắt buộc: lệnh in danh sách app, mỗi dòng một package
multi   = true                       # tùy chọn: true (mặc định) chọn nhiều / false chọn một
default = "com.android.chrome\ncom.google.android.youtube"   # tùy chọn: package chọn sẵn, cách nhau bằng separator
script  = 'echo "Đã chọn: $target_apps"'
```

Ví dụ `get`:

```toml
get = "pm list packages -d"                              # app đã tắt
get = "pm list packages -s"                              # app hệ thống
get = "pm list packages -u"                              # kể cả app đã gỡ nhưng còn dữ liệu
get = "pm list packages -3 -i com.android.vending"       # app cài từ Play Store
get = 'pm list packages --user $user_id'                 # dùng biến của item khác
get = "pm list packages -3 | grep -v google"             # lọc bằng pipe
```

- `get` (bắt buộc): lệnh shell in danh sách app. Thiếu `get` thì item bị bỏ kèm cảnh báo. Lệnh chạy qua backend shell của app (có root thì chạy bằng root, nên dùng được mọi cờ của `pm`), **chạy lại mỗi lần mở danh sách** nên danh sách luôn mới, và dùng được biến của các item khác như `$user_id`. Khác với `get` của `switch` / `checkbox` / `progress` (chạy khi item hiện, thay giá trị đã lưu): ở `applist`, `get` chỉ tạo danh sách để chọn, còn `$id` vẫn là các package đã chọn và vẫn lưu như thường.
- Đầu ra của `get`: mỗi dòng một package. Chấp nhận cả dạng của `pm list packages`: `package:com.x`, `package:/đường/dẫn.apk=com.x` (`-f`), kèm phần phụ như ` uid:1000` (`-U`). Dòng không ra tên package hợp lệ (chữ, số, `_`, `.`) bị bỏ qua; chính Tool Script và package trùng bị bỏ. stderr của lệnh bị bỏ, không lẫn vào danh sách.
- Lệnh thoát khác 0, chạy quá giờ (mặc định 20 giây, đổi bằng `timeout`) hoặc không có package nào thì hiện snackbar báo và không mở danh sách. Trong lúc chạy `get`, hàng hiện "Đang tải danh sách…".
- Có package thì màn hình chọn **mở ngay**: mỗi app là một dòng khung xương (dải sáng chạy ngang, kiểu Facebook); tên app đọc dần theo nhóm và thay đúng vị trí khung xương của nó, đọc xong thì sắp theo tên. Góc phải trên hiện "Đang tải danh sách…"; xong thì `multi = true` hiện "Đã chọn x/y", `multi = false` không hiện gì. Lúc đang tải, **Xác nhận** và **Chọn tất cả** mờ, ô tìm chỉ lọc các dòng đã đọc xong, **Hủy** dùng được và hủy việc tải.
- Tên và icon lấy từ PackageManager (kể cả app đã gỡ còn dữ liệu); PackageManager không thấy thì hiện tên package và không có icon. Danh sách sắp theo tên, không phân biệt hoa thường.
- `multi`: `true` (mặc định) chọn nhiều, `$id` là các package nối bằng `separator` (mặc định mỗi package một dòng), theo thứ tự danh sách app; `false` chọn một, `$id` là một package, chưa chọn thì rỗng. Giá trị khác `true` / `false` bị báo cảnh báo và dùng `true`.
- Hàng hiện `Đã chọn N` (`multi = true`) hoặc tên app đang chọn (`multi = false`; app đã gỡ thì hiện package). Bấm hàng mở **toàn màn hình** (không có hộp thoại nhỏ, `columns` không dùng): mỗi dòng có icon, tên app và package name; ô tìm lọc theo tên (không phân biệt hoa thường và dấu) hoặc package; `multi = true` có ô **Chọn tất cả** như `multiselect`.
- Cách lưu và chạy `script` y hệt `multiselect` / `singleselect` toàn màn hình: chỉ khi bấm **Xác nhận**, chỉ khi có thay đổi; Hủy hoặc Back thì bỏ thay đổi. Lưu vào `files/state` theo id như các type khác.
- Package trong `default` hoặc trong file đã lưu mà không còn trong danh sách của `get` vẫn giữ trong `$id` cho tới lần xác nhận kế tiếp (lúc đó bị bỏ).
- Tương thích Android: `targetSdk = 28` nên không bị lọc tầm nhìn package (Android 11+), không cần quyền `QUERY_ALL_PACKAGES` để lấy tên và icon. Nếu sau này nâng `targetSdk` lên 30 trở lên thì phải khai báo quyền này (hoặc `<queries>`), nếu không tên và icon của nhiều app sẽ không đọc được.

`checkbox`, `radio`, `dropdown`, `progress` – ghi chú:

- `checkbox` dùng đúng quy tắc của `switch`: `get` in `1`/`true`/`on` là bật, `script` chạy khi đổi trạng thái (`$id` = `1` bật / `0` tắt), chỉ đổi khi lệnh thành công. Bấm cả hàng cũng đổi.
- `radio` khai báo `options` như mục 7, nhãn hiện trên giao diện, giá trị truyền qua `$id`.
- `dropdown` khai báo `options` như mục 7. Bên phải hàng hiện nhãn đang chọn kèm mũi tên lên/xuống; bấm vào thì menu thả xuống ngay dưới hàng, căn mép phải (thiếu chỗ phía dưới thì lật lên trên, danh sách dài thì cuộn được). Mục đang chọn nổi nền màu chủ đạo và có dấu tích bên phải. Chọn xong đóng menu và chạy `script`.
- `progress` cần `id`. `get` in ra một số trong khoảng `min`..`max` (mặc định 0..100), chỉ đọc khi item xuất hiện. Giá trị nằm ngoài khoảng được cắt về mép. Không có `get` thì dùng `default` (mặc định `min`).

```toml
[[item]]
type  = "checkbox"
id    = "auto_clean"
title = "Tự dọn dẹp"
script = "echo $auto_clean > /sys/xxx"

[[item]]
type    = "radio"
id      = "profile"
title   = "Chế độ"
script  = "echo $profile > /sys/yyy"
options = ["Cân bằng|balanced", "Hiệu năng|performance"]

[[item]]
type  = "progress"
id    = "battery"
title = "Pin"
get   = "cat /sys/class/power_supply/battery/capacity"
```

### Id dành riêng `app_language` (chọn ngôn ngữ app)

Item `radio`, `dropdown` hoặc `singleselect` có `id = "app_language"` được gắn thẳng vào ngôn ngữ app (không còn mục chọn ngôn ngữ trong dialog Cài đặt):

```toml
[[item]]
type    = "radio"            # hoặc dropdown / singleselect
id      = "app_language"
title   = "Ngôn ngữ"
options = ["Hệ thống|system", "English|en", "Tiếng Việt|vi"]
```

- Giá trị option chỉ nhận `system`, `en`, `vi`; giá trị khác bị bỏ kèm cảnh báo. Có thể chỉ liệt kê một vài ngôn ngữ.
- Chọn xong ngôn ngữ đổi ngay, không nạp lại TOML; `script`, `shell`, `confirm` bị bỏ qua (không chạy lệnh).
- Không lưu vào `files/state`; giá trị luôn là ngôn ngữ app đang dùng. Nhiều item cùng id (ở các trang khác nhau) dùng chung một trạng thái.
- Type khác dùng id này bị báo là id dành riêng và bỏ qua.

## 8. Biến và điều kiện

- Giá trị của item có `id` dùng trong lệnh dưới dạng `$id`.
- Trong điều kiện (`visible`, `readonly`, `lock`) viết tên biến không có `$`:
  `game_mode == 1`, `ROOT_MODE != root`.
- Toán tử: `==`, `!=`, `&&`, `||`. Hằng số: `1` (đúng), `0` (sai).
- Biến dựng sẵn: `SDK`, `ABI`, `ARCH`, `ROOT_MODE`.

## 9. Ẩn, mờ, khóa item (visible / readonly / lock)

Ba khóa độc lập, dùng được cho item và `[[group]]`. Mỗi khóa là một điều kiện (mục 8):

| Khóa | Điều kiện đúng thì | Bấm vào |
|---|---|---|
| `visible` | Item hiện; sai thì ẩn hẳn, các item dưới dồn lên | Bình thường |
| `readonly` | Item mờ (alpha) | Không phản ứng |
| `lock` | Item vẫn hiện bình thường | Hiện snackbar lý do, không chạy lệnh |

- `lock = "điều kiện|lý do"`: lý do (tùy chọn) nằm sau dấu `|` đơn cuối cùng (không tính `||`), được thay `@khóa` để dịch, và không được chứa `|`. Không có lý do thì hiện câu mặc định ("Mục này đang bị khóa" / "This item is locked").
- `readonly` và `lock` cùng đúng thì `readonly` thắng (mờ, không hiện snackbar).
- `[[group]]` có `readonly` thì mọi item con mờ; có `lock` thì bấm vào item con nào cũng hiện lý do (lý do của item con ưu tiên hơn); có `visible` sai thì ẩn cả group.

### Bản shell (`visible-sh`, `readonly-sh`, `lock-sh`)

Mỗi khóa có bản `-sh`: chạy một lệnh shell và đọc **stdout** dòng đầu tiên:

| Lệnh in ra | Ý nghĩa |
|---|---|
| `0` hoặc rỗng | Điều kiện sai |
| `1` | Điều kiện đúng |
| `1\|lý do` | Điều kiện đúng kèm lý do (dùng cho `lock-sh`, hiện trong snackbar) |

- Lệnh chạy khi item xuất hiện và chạy lại mỗi khi một biến đổi (đợi khoảng 0,3 giây để gõ chữ không chạy từng phím). Biến dùng được dưới dạng `$id`, giới hạn 5 giây mỗi lần chạy.
- Lệnh lỗi hoặc hết giờ thì coi như không ẩn, không mờ, không khóa.
- Có cả bản tĩnh và bản `-sh` thì `visible` phải đúng cả hai; `readonly` / `lock` chỉ cần một trong hai đúng.

Ví dụ:

```toml
# Chỉ dùng được khi có root, ngược lại hiện mờ
readonly = "ROOT_MODE != root"

# Ẩn nếu không phải root hoặc ROM
visible = "ROOT_MODE == root || ROOT_MODE == system"

# Bấm thì hiện lý do, không chạy lệnh
lock = "game_mode != 1|Bật Chế độ game trước"

# Khóa theo kết quả lệnh shell
lock-sh = 'test -f /sys/xxx && echo 0 || echo "1|Máy không hỗ trợ"'
```

### 9.1 Làm mới (refresh)

`get` chỉ chạy một lần khi item xuất hiện. Khóa `refresh` (mọi item đều dùng được) bắt app đọc lại sau khi item đổi trạng thái. Chỉ có tác dụng trong **trang chứa item** (file `pages/*.toml` đó):

| `refresh` | Làm mới |
|---|---|
| `true` | Mọi item của trang này |
| `"id1,id2"` | Chỉ các item có `id` đó trong trang này |

- Làm mới là đọc lại `get` và chạy lại các lệnh `visible-sh`, `readonly-sh`, `lock-sh` của các item đó; item không có `get` thì giữ nguyên giá trị.
- Chạy ngầm, sau khi `script` của chính item xong **thành công** (lệnh lỗi hoặc hết giờ thì không làm mới). Với `switch` / `checkbox`, bật hay tắt đều làm mới.
- Item không có `script` (chỉ lưu giá trị) thì làm mới ngay sau khi đổi giá trị. Trang dialog (item chỉ đổi biến) không làm mới.
- `id` không có trong cùng trang (kể cả `id` của trang khác) thì báo cảnh báo khi đọc config và bỏ qua.

```toml
[[item]]
type    = "switch"
id      = "wifi"
title   = "Wi-Fi"
script  = 'svc wifi $([ "$wifi" = 1 ] && echo enable || echo disable)'
refresh = "hotspot"          # đổi xong thì hotspot (cùng trang) đọc lại get
```

## 10. Chế độ hiển thị khi chạy (shell)

Áp dụng cho mọi item có chạy lệnh: `button`, `switch`, `checkbox`, `input`, `multiselect`, `singleselect`, `radio`, `dropdown`, `slider`.

| `shell` | Hành vi |
|---|---|
| `live` (mặc định) | Bảng log mở ngay khi bắt đầu chạy và hiện từng dòng ngay khi lệnh in ra (tự cuộn xuống). Bố cục giống `full`: hàng tiêu đề có nút **Toàn màn hình**, nhãn trạng thái (Đang chạy / Lỗi / Hết thời gian chờ / Đã hủy) và thanh tiến trình chỉ hiện khi đang chạy hoặc lỗi, khung log chỉ chứa phần lệnh in ra (nhấn giữ để chọn và sao chép), hàng nút dưới cùng gồm **Chia sẻ** và Hủy / Thoát như bảng bên dưới |
| `full` | Như `live` nhưng log mở **toàn màn hình** ngay khi bắt đầu chạy, bố cục giống màn hình lỗi: nút **Thu nhỏ** (về bảng log thường) ở hàng tiêu đề, khung log nhấn giữ để chọn và sao chép chữ, hàng nút dưới cùng gồm **Chia sẻ** và nút cuối đổi theo trạng thái: đang chạy là **Hủy** (vàng, chỉ dừng lệnh, log vẫn mở và chốt phần đã in, nhãn "Đã hủy"), xong không lỗi hoặc đã hủy là **Thoát** (xanh dương), lỗi là **Thoát: <mã thoát>** (đỏ nhạt; hết giờ thì "Thoát: hết giờ"). Màn hình này không hiện nhãn "Xong" và mã thoát; thanh tiến trình chỉ hiện lúc đang chạy. Back của máy đóng log nhưng lệnh vẫn chạy tiếp |
| `hidden` | Chạy ẩn, không hiện gì |
| `banner` | Banner trong app. Khi lệnh bắt đầu chạy, banner hiện ngay dòng "Đang chạy script…" kèm thanh chạy vô hạn; lệnh xong thì banner đổi sang kết quả và thanh đếm ngược 5 giây. Thành công có in ra thì hiện đúng phần in ra (stdout, tối đa 300 ký tự), không in gì thì hiện "<title>: hoàn tất". Lỗi hoặc hết thời gian chờ thì banner nền đỏ nhạt, dòng đầu là mã lỗi, kèm phần lệnh in ra (stdout và stderr, tối đa 300 ký tự) |
| `notice` | Chạy như `banner` (banner "Đang chạy script…" khi đang chạy), xong thì banner ẩn đi và gửi thông báo hệ thống của Android với nội dung như quy tắc của `banner` (chỉ xin quyền thông báo khi dùng tới, Android 13+; chưa có quyền hoặc bị từ chối thì hiện banner lỗi "Không có quyền thông báo" thay cho thông báo) |
| `snackbar` | Chạy ẩn, xong thì hiện **Snackbar có sẵn của Miuix** ở đáy màn hình (trên thanh tab nếu có) với đầu ra của lệnh (cùng quy tắc nội dung như `banner`: không in gì thì hiện "tên: hoàn tất", lỗi thì dòng mã lỗi kèm phần in ra). Có nút đóng, vuốt ngang cũng đóng; thành công tự ẩn sau 4 giây, lỗi sau 10 giây. Không tối nền, không khóa item, không có trạng thái "đang chạy". |

Mỗi lượt chạy `banner` / `notice` có **một banner riêng**; nhiều banner xếp chồng từ trên xuống ở đầu màn hình, nền màu đậm hơn thẻ để không trùng màu thẻ, không tối màn hình. Item bị khóa (mờ, không bấm được) cho tới khi banner của nó **biến mất**: lệnh chạy xong, rồi hết đếm ngược 5 giây (hoặc bấm Ẩn / Hủy, vuốt) mới mở khóa. Các item banner / notice khác vẫn bấm được và chạy cùng lúc, kể cả khi dùng root (mỗi lệnh chạy song song trên một shell root riêng khi đã có lệnh khác đang chạy). Banner có nút **Hủy** khi đang chạy (dừng lệnh, mở khóa item ngay) và nút **Ẩn** khi đã xong; vuốt banner sang trái hoặc phải cũng làm đúng việc đó (đang chạy thì hủy, đã xong thì ẩn).

Bảng log `live` có nút **Toàn màn hình** để mở rộng giữa chừng, `full` có nút **Thu nhỏ** để quay lại; hai bảng dùng chung bố cục và hành vi nút. Hủy lệnh (nút Hủy của log `live` / `full`, nút Hủy hoặc vuốt banner) chạy `killtree`: giết cả shell chạy lệnh lẫn mọi tiến trình con cháu của nó, với backend thường và cả backend root (su, `killtree` chạy bằng shell root riêng). Cách hoạt động: mọi lệnh được đặt sẵn `shell_progres` = PID của shell chạy lệnh và in dòng `@pid N` đầu tiên (app giữ lại, không hiện trong log, không nằm trong kết quả); khi hủy app chạy `shell_progres=N killtree`. Script cũng tự gọi `killtree` được để dừng cả lệnh đang chạy. Hết giờ (`timeout`) cũng chạy `killtree` như khi hủy, rồi báo hết giờ với phần đã in ra. Dòng của **stderr** (`>&2`) hiện màu đỏ, stdout màu thường; stdout và stderr hiện đúng thứ tự đến ngay khi lệnh in ra (không còn dồn stderr xuống cuối), nút Chia sẻ gửi đúng văn bản đang hiện. Dòng `@progress N` không hiện trong log. Khi lệnh còn đang chạy, bảng log `live` không đóng khi chạm ra ngoài (và Back tạm thời cũng không đóng), chỉ đóng bằng nút Hủy rồi Thoát; lệnh xong thì chạm ra ngoài hoặc Back đóng như cũ.

**Ẩn log (chạy nền):** log `live` / `full` đang chạy có nút **Ẩn** (mũi tên xuống) ở hàng tiêu đề, bên trái nút Toàn màn hình. Bấm Ẩn thì màn hình log đóng nhưng lệnh **vẫn chạy**, để chạy tính năng khác (có thể ẩn nhiều tính năng cùng lúc). Từ lúc ẩn, mọi dòng log gửi lên **thông báo Android**: tiêu đề là tên tính năng, nội dung là phần log mới nhất (mở rộng thông báo để xem nhiều dòng hơn), kèm trạng thái (Đang chạy / Xong / Lỗi (mã N) / Hết thời gian chờ / Đã hủy) và thanh tiến trình (đúng % nếu lệnh in `@progress N`). Chạm vào thông báo thì mở lại đúng màn hình log đó (toàn màn hình hay bảng thường tùy lúc ẩn). Thông báo của lệnh đang chạy không vuốt bỏ được; khi xong thì thông báo chốt kết quả và vuốt bỏ được. Giữ tối đa 12 log đã ẩn (bỏ cái đã xong cũ nhất). Android 13+: bấm Ẩn lần đầu sẽ xin quyền thông báo; từ chối thì lệnh vẫn chạy nhưng không có thông báo để mở lại. Lưu ý: lệnh chạy nền khi app còn sống (ra màn hình chính, mở app khác đều được); thoát hẳn ứng dụng thì tiến trình app dừng, thông báo của lệnh đang chạy không được chốt mà tự gỡ sau tối đa 2 phút (lệnh chạy nền bằng root để chạy tiếp, app không dừng chúng).

Thanh tiến trình (`live` và `full`): khi đang chạy, thanh nằm dưới nhãn trạng thái. Lệnh in dòng `@progress N` (0-100) thì thanh hiện đúng N% (số % hiện bên phải nhãn) và dòng đó không xuất hiện trong log; chưa in thì thanh chạy vô định. Lệnh xong không lỗi thì thanh đầy; lỗi hoặc hết giờ thì giữ mức cuối (không có `@progress` thì ẩn thanh).

Quy tắc chung: ở chế độ `hidden`, nếu lệnh lỗi thì hiện **toast** báo lỗi.

## 11. Khởi động và xử lý lỗi

- Trước khi chạy script, app chép các thư mục cần thiết của assets ra `no_backup` (xem "Chép assets và lệnh dựng sẵn"), rồi tạo `env.sh`.
- Script khởi động `assets/scripts/init.sh` báo tiến trình bằng dòng `@progress N` (0-100), đồng thời cài busybox (`$BIN_DIR/busybox`); không cài được thì thoát mã khác 0 (app vẫn vào, có banner cảnh báo).
- Script lỗi: vẫn vào app, hiện banner cảnh báo, xem lỗi trong Trung tâm lỗi.
- TOML parse lỗi: hiện file, dòng, mô tả lỗi.
- Thiếu trường bắt buộc hoặc `type` lạ: bỏ qua mục đó và ghi cảnh báo.
- Cảnh báo cấu hình hiện thành banner ở đầu danh sách của **trang có lỗi** (tab hoặc trang con), cuộn cùng nội dung; trang không có cảnh báo thì không hiện. Bấm Xem để đọc danh sách của trang đó, Ẩn để ẩn banner. Trang bị lỗi cú pháp TOML chỉ hiện thẻ lỗi, không kèm banner. Banner chung phía trên chỉ còn cho lỗi toàn app: script khởi động lỗi hoặc cảnh báo của `main.toml`.
- Lỗi không lưu ra file, chỉ giữ trong bộ nhớ của phiên chạy.
- **Demo crash**: để thử màn hình lỗi, đặt `script` của một `button` là một trong hai lệnh dựng sẵn dưới đây (nên kèm `confirm` vì app thoát thật):
  - `@crash`: ném lỗi trên luồng chính.
  - `@crash-thread`: ném lỗi trong luồng nền.
  Cả hai hiện Trung tâm lỗi "Ứng dụng bị lỗi" với stack trace rồi kill tiến trình. Mẫu có sẵn ở nhóm "Crash app" của `pages/crash.toml` (tab Lỗi code).
- **Demo lỗi cấu hình**: tab Trang hỏng (`pages/broken.toml`) có các link tới trang con cố ý sai trong `pages/broken/`, tiêu biểu:
  - `pages/broken/syntax.toml`: sai cú pháp TOML, trang hiện thẻ "Không đọc được trang này" kèm file và dòng lỗi.
  - `pages/broken/fields.toml`: đúng cú pháp nhưng khai báo sai trường; mục thiếu trường bắt buộc bị bỏ qua, giá trị sai dùng mặc định, mỗi lỗi là một dòng trong "Cảnh báo cấu hình".
  Cảnh báo chỉ hiện trong chính trang có lỗi, nên các trang khác không bị ảnh hưởng; xóa tab `broken`, `pages/broken.toml` và thư mục `pages/broken` để bỏ demo.

## 12. Để lại sau

Lộ trình thành phần Miuix (xếp từ dễ đến khó, mức 1 đã xong) nằm trong `README.md`, mục "Lộ trình thành phần Miuix".

- Reload nóng (nạp lại khi file đổi) và nạp `main.toml` từ thư mục ngoài.
- Backend ROM: danh sách lệnh cho phép.
