# Báo cáo bug
Quét gần nhất: 2026-11-24 06:30 (quét lại sau khi sửa các rủi ro trong Kotlin)
Tổng: 0 lỗi, 3 rủi ro, 3 không xác định, 11 đã sửa

Phạm vi: đọc toàn bộ 31 tệp Kotlin (7.595 dòng), script `assets/scripts`, `assets/bin`, `AndroidManifest.xml`, các tệp Gradle, `build.yml`, `proguard-rules.pro`, tài nguyên chuỗi, và kiểm tra các tệp TOML mẫu bằng script. Bỏ qua icon svg, ảnh và tệp `*.bak`.
Giới hạn: đây là đọc tĩnh. Chưa build và chưa chạy trên máy thật, nên mục nào phụ thuộc hành vi Android thật đều ghi rõ.
Đã kiểm tra và không thấy vấn đề: chuỗi `R.string` có đủ trong cả `values` và `values-vi` (160 khoá dùng đều có), định dạng `%s/%d` hai ngôn ngữ khớp nhau, mọi icon, trang và tab được TOML mẫu tham chiếu đều tồn tại, không có id trùng giữa các trang mẫu, `shell = "snackbar"` là chế độ hợp lệ.

## Cây thư mục
```
Tool-Script/
├── .github/workflows/
│   └── build.yml                      (rủi ro 1)
├── app/
│   ├── build.gradle.kts               (rủi ro 1, không xác định 1)
│   └── src/main/
│       ├── assets/
│       │   ├── bin/
│       │   │   └── slog               (đã sửa 2)
│       │   └── scripts/
│       │       └── init.sh            (rủi ro 1)
│       └── kotlin/com/tool/script/
│           ├── config/Config.kt       (đã sửa 3)
│           ├── crash/CrashActivity.kt (đã sửa 1)
│           ├── executor/Executor.kt   (đã sửa 1)
│           └── ui/
│               ├── feedback/LogFiles.kt (đã sửa 3)
│               ├── main/Main.kt         (đã sửa 1)
│               └── render/Render.kt     (không xác định 1, đã sửa 1)
└── gradle/libs.versions.toml          (không xác định 1)
```
(chỉ hiện thư mục và tệp có vấn đề)

## Chi tiết theo tệp

### app/src/main/kotlin/com/tool/script/crash/CrashActivity.kt
Đã sửa lỗi (cam kết không bị lại):
+ [share(), dòng 90-105] Nút Chia sẻ trên màn hình lỗi gửi tệp `cache/logs/share/error_*.txt` nhưng tệp bị xoá khi tiến trình chính khởi động lại. Nguyên nhân gốc: `LogFiles.init` gọi `deleteRecursively()` cả `cache/logs`. Đã sửa ở LogFiles.kt (chỉ xoá `log_*.txt`, và tệp trong `share` quá 24 giờ). Kiểm tra tái phát: trong `LogFiles.init` không được có `deleteRecursively()` áp lên thư mục `logs` (grep `deleteRecursively` trong LogFiles.kt chỉ được thấy ở nhánh tệp `share` quá hạn). Cần thử trên máy thật: gây crash, bấm Chia sẻ, mở tệp ở ứng dụng nhận.

### app/src/main/kotlin/com/tool/script/ui/feedback/LogFiles.kt
Đã sửa lỗi (cam kết không bị lại):
+ [LogFiles.init] Xoá nhầm tệp chia sẻ của màn hình lỗi: xem mục CrashActivity.kt. Kiểm tra tái phát: `init` chỉ xoá tệp có tên bắt đầu `log_` và tệp `share` cũ hơn `SHARE_MAX_AGE_MS`.
+ [PlainLogWriter.close()] Rò file descriptor khi log vượt 10 MB hoặc ghi lỗi, vì `close()` thoát sớm khi `stopped = true` nên `out` không được đóng. Đã sửa: `close()` không thoát sớm theo `stopped`, luôn đóng `out`. Kiểm tra tái phát: dòng `if (file == null || stopped) return` không được xuất hiện trong `close()`.
+ [flushLine()] Giới hạn 10 MB đếm bằng số ký tự thay vì byte, nên log nhiều chữ ngoài ASCII vượt giới hạn. Đã sửa: đếm `text.toByteArray(Charsets.UTF_8).size`. Kiểm tra tái phát: `bytes +=` phải dùng kích thước UTF-8, không dùng `text.length`.

### app/src/main/assets/bin/slog
Đã sửa lỗi (cam kết không bị lại):
+ [nhánh xoá] `slog -d` / `--delete` không kèm khoá tạo tệp rác `-d` trong `$STATE_DIR`. Nguyên nhân gốc: kiểm tra `-d` nằm trong nhánh `if [ "$2" ]`. Đã sửa: kiểm tra `-d` trước, thiếu khoá thì in cách dùng và thoát mã 1. Kiểm tra tái phát: chạy `STATE_DIR=$(mktemp -d) sh slog -d; ls -A $STATE_DIR` phải in ra rỗng và mã thoát 1 (đã chạy thử, đạt).
+ [ghi giá trị] `echo "$2"` làm giá trị `-n`, `-e` hoặc có `\` bị diễn giải. Đã sửa bằng `printf '%s\n'`. Kiểm tra tái phát: `slog k -n` rồi `cat $STATE_DIR/k` phải ra `-n`; `slog k2 'a\nb'` phải ra đúng `a\nb` (đã chạy thử, đạt). Đã đổi `==` thành `=` cho tương thích mọi `sh`.

### app/src/main/kotlin/com/tool/script/config/Config.kt
Đã sửa lỗi (cam kết không bị lại):
+ [normalizeMulti] Option multiselect có giá trị chứa dấu phẩy bị tách nhầm vì hàm tách thêm theo `,` khi separator là xuống dòng. Đã sửa: chỉ tách theo `separator` (mặc định `\n`). Lưu ý: giá trị đã lưu kiểu cũ phân tách bằng dấu phẩy sẽ không còn được đọc, item quay về `default`. Kiểm tra tái phát: `raw.split(separator)` là cách tách duy nhất trong `normalizeMulti`, không có `","` trong hàm.
+ [parseItem] Id item trùng tên biến môi trường (`PATH`, `HOME`, `BIN_DIR`, `STATE_DIR`, `SDK`, `ABI`, `ROOT_MODE`...) đè môi trường của mọi lệnh. Đã sửa: id nằm trong `RESERVED_IDS` bị bỏ qua kèm cảnh báo `cfg_reserved_id`. Kiểm tra tái phát: mọi tên `export` trong `assets/scripts/env.sh` và biến dựng sẵn ở `Boot.kt` (SDK, ABI, ARCH, ROOT_MODE) phải có trong `RESERVED_IDS`; thêm biến mới vào env.sh thì thêm vào danh sách.
+ [parsePage, Boot.collectPersisted] Id trùng giữa các trang dùng chung giá trị và tệp trạng thái mà không báo. Đã xử lý: `Boot` ghi một dòng vào log khởi động (`boot_duplicate_id`) cho mỗi id trùng; cách xử lý item cuối thắng được giữ nguyên. Kiểm tra tái phát: `collectPersisted` phải nhận `duplicates` và khi `out.put(...)` trả về khác null thì ghi id vào đó.

### app/src/main/kotlin/com/tool/script/ui/render/Render.kt
Đã sửa lỗi (cam kết không bị lại):
+ [BinaryItem và ProgressItem, `LaunchedEffect(id)`] `get` chạy lại mỗi lần item vào lại cây giao diện (cuộn, đổi tab) và có thể đè giá trị người dùng vừa đặt. Đã sửa: mỗi id chỉ đọc một lần qua `VariableStore.claimFetch` (đọc lỗi hoặc bị huỷ thì `releaseFetch` để lần sau đọc lại), và công tắc không ghi đè nếu giá trị đã đổi trong lúc chờ. Kiểm tra tái phát: cả hai `LaunchedEffect` phải có `claimFetch(id)`; mọi chỗ gọi `exec` với `item.get` phải nằm sau điều kiện đó.
Không xác định:
+ [SliderItem, dòng 838-871] Ba trường hợp cấu hình lệch chưa kiểm chứng được nếu không chạy: (1) `min == max` mà bộ đọc vẫn cho qua, Slider có thể chia cho 0 khi tính vị trí, chưa rõ Miuix xử lý ra sao. (2) `step` không chia hết `max - min` (ví dụ 0..10 bước 3) thì thanh trượt không bao giờ tới `max` và số vạch chia (`steps`) lệch với các mốc bước. (3) `step` số thực như 0.1 tính bằng `Float` có thể ra giá trị lẻ như `0.70000005` truyền vào shell. Cần thử trên máy để kết luận. Các TOML mẫu không dính.

### app/src/main/kotlin/com/tool/script/ui/main/Main.kt
Đã sửa lỗi (cam kết không bị lại):
+ [DialogContentMeasurer, dòng 786-805] Bản vẽ ẩn dùng để đo chiều cao dialog chạy `get` lần thứ hai mỗi lần mở dialog. Đã sửa gián tiếp ở Render.kt: item không chạy `get` khi `env.varsOnly` (môi trường của bản đo). Kiểm tra tái phát: hai `LaunchedEffect` của `get` trong Render.kt phải có điều kiện `env.varsOnly`.

### app/src/main/kotlin/com/tool/script/executor/Executor.kt
Đã sửa lỗi (cam kết không bị lại):
+ [PreludeExecutor.exec] Mọi dòng output bắt đầu bằng `@pid <số>` đều bị nuốt khỏi log, trong khi output cuối chỉ bỏ dòng đầu. Đã sửa: chỉ nhận dòng pid khi chưa có pid (`if (pid.get() == null) parsePid(line)`), các dòng `@pid` sau đó được giữ. Kiểm tra tái phát: `parsePid` chỉ được gọi khi `pid.get() == null`.

### app/src/main/assets/scripts/init.sh
Rủi ro:
+ [install_busybox, dòng 28-31] busybox được chép ra `$BIN_DIR` rồi chạy trực tiếp (`"$dst" true`). Cách này chỉ chạy được nhờ `targetSdk = 28` (app/build.gradle.kts:14); từ Android 10, app target 29 trở lên bị SELinux chặn thực thi tệp trong thư mục dữ liệu. `BinInstaller` (liên kết tới thư viện native) đã có nhưng không nơi nào gọi. Điều kiện: nâng `targetSdk` lên 29 trở lên (trước đó có ý định nâng lên 37) mà chưa chuyển sang `BinInstaller`, khi đó busybox báo "không chạy được", script khởi động lỗi và mọi lần mở app hiện cảnh báo trên Android 10+ không root.

### .github/workflows/build.yml
Rủi ro:
+ [dòng 9] `permissions: write-all` cấp toàn quyền ghi cho `GITHUB_TOKEN` ở mọi job. Bản sao `build.yml.bak` còn ghi `contents: write`, đủ cho bước tạo release. Điều kiện: bất kỳ bước hoặc action bên thứ ba nào bị chiếm quyền thì có thể ghi vào repo, issue, package. Hướng sửa: trả về `contents: write`.

### app/build.gradle.kts
Rủi ro:
+ [dòng 19-24] Keystore `app/beta.jks` được đưa vào repo (`.gitignore` có ngoại lệ `!app/beta.jks`) cùng mật khẩu `android` ghi thẳng trong tệp. Ai đọc được repo đều ký được APK có cùng chữ ký, nên bản giả mạo có thể ghi đè bản cài thật khi cập nhật. Điều kiện: repo công khai hoặc chia sẻ rộng. Chấp nhận được cho bản thử nghiệm; cần tách khoá thật ra khỏi repo trước khi phát hành.
Không xác định:
+ [dòng 34] Bản release bật `isMinifyEnabled = true` (R8). Chưa có bằng chứng bản release đã được chạy thử. Cần cài và mở bản release để xem quy tắc giữ lớp trong `proguard-rules.pro` có đủ cho tomlkt, libsu và Miuix không.

### gradle/libs.versions.toml
Không xác định:
+ [dòng 1-2] Tệp tự ghi "CHƯA build thử" và liệt kê các phiên bản (AGP 9.5.1, Kotlin 2.4.20, Compose 1.12.0, Miuix 0.9.4, Gradle 9.7.0 ở build.yml:32...). Mình không xác minh được các bản này có tồn tại và biên dịch cùng nhau. Cần kết quả một lần build (CI hoặc Android Studio) để kết luận.
