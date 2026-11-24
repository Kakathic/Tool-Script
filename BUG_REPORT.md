# Báo cáo bug
Quét gần nhất: 2026-11-24 12:10 (sau khi sửa LogCenter và chốt các rủi ro được chấp nhận)
Tổng: 0 lỗi, 0 rủi ro, 0 không xác định, 21 đã sửa, 6 rủi ro đã chấp nhận

Phạm vi: đọc toàn bộ 31 tệp Kotlin (7.595 dòng), script `assets/scripts`, `assets/bin`, `AndroidManifest.xml`, các tệp Gradle, `build.yml`, `proguard-rules.pro`, tài nguyên chuỗi, và kiểm tra các tệp TOML mẫu bằng script. Bỏ qua icon svg, ảnh và tệp `*.bak`.
Giới hạn: đây là đọc tĩnh. Người dùng xác nhận bản release (R8) và phiên bản thư viện đã build, chạy ổn; các sửa mới nhất chưa chạy trên máy thật, mục nào phụ thuộc hành vi Android thật đều ghi rõ.
Đã kiểm tra và không thấy vấn đề: chuỗi `R.string` có đủ trong cả `values` và `values-vi` (160 khoá dùng đều có), định dạng `%s/%d` hai ngôn ngữ khớp nhau, mọi icon, trang và tab được TOML mẫu tham chiếu đều tồn tại, không có id trùng giữa các trang mẫu, `shell = "snackbar"` là chế độ hợp lệ.

## Cây thư mục
```
Tool-Script/
├── .github/workflows/
│   └── build.yml                      (đã sửa 1)
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── bin/
│       │       └── slog               (đã sửa 2)
│       └── kotlin/com/tool/script/
│           ├── boot/Boot.kt           (đã sửa 1)
│           ├── config/Config.kt       (đã sửa 3)
│           ├── crash/CrashActivity.kt (đã sửa 1)
│           ├── executor/Executor.kt   (đã sửa 2)
│           └── ui/
│               ├── feedback/Feedback.kt (đã sửa 1)
│               ├── feedback/LogCenter.kt (đã sửa 2)
│               ├── feedback/LogFiles.kt (đã sửa 3)
│               ├── main/Main.kt         (đã sửa 3)
│               └── render/Render.kt     (đã sửa 2)
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
+ [SliderItem, Config.parseItem] `min == max` và `step` không chia hết `max - min` chưa có cảnh báo (Miuix Slider có thể chia cho 0; thanh không tới `max`). Đã sửa ở Config.kt: `min == max` thì cảnh báo `cfg_min_eq_max` và đặt `max = min + step`; `step` không chia hết thì cảnh báo `cfg_step_not_divide` (dung sai 1e-3). Kiểm tra tái phát: khối `if (type == ItemType.Slider)` sau `val step` trong `parseItem` còn đủ hai cảnh báo. Giá trị `step` số thực (0.1) làm số lẻ kiểu `0.70000005` vẫn chưa kiểm chứng, cần thử trên máy.

### app/src/main/kotlin/com/tool/script/ui/main/Main.kt
Đã sửa lỗi (cam kết không bị lại):
+ [DialogContentMeasurer, dòng 786-805] Bản vẽ ẩn dùng để đo chiều cao dialog chạy `get` lần thứ hai mỗi lần mở dialog. Đã sửa gián tiếp ở Render.kt: item không chạy `get` khi `env.varsOnly` (môi trường của bản đo). Kiểm tra tái phát: hai `LaunchedEffect` của `get` trong Render.kt phải có điều kiện `env.varsOnly`.
+ [AnimatedContent `subPage`, transitionSpec] Từ tab mở trang rồi mở trang con, đóng trang con thì đúng nhưng đóng tiếp trang đầu thì hiệu ứng trở lại sai (báo từ người dùng, suy ra từ code, chưa chạy máy thật). Nguyên nhân gốc: `targetContentZIndex` cố định +1/-1, mà AnimatedContent giữ zIndex đặt lúc trang là đích; ở lần đóng thứ hai trang đang thoát (zIndex -1 từ lần đóng trước) bằng zIndex với trang đích (-1) nên bị vẽ sai lớp. Đã sửa: zIndex = độ sâu trong stack (tab = 0). Kiểm tra tái phát: không có `targetContentZIndex = 1f` hoặc `-1f` cố định trong Main.kt; mọi nhánh đặt `= depth`. Thử: tab → trang → trang con → Back → Back.
+ [closeTop, DialogSheet.settle, scrim của DialogLayer] Back hoặc chạm nền đến muộn lúc sheet đang trượt đóng làm dialog đóng một lần rồi `onBack` cuối hiệu ứng đóng thêm trang bên dưới. Đã sửa: `closeIfTop(file, confirmed)` chỉ đóng khi `file` vẫn là trang trên cùng; trang và dialog đều truyền tên file của mình. Kiểm tra tái phát: `SubPageScreen` và `DialogLayer` trong Main.kt phải gọi `closeIfTop`, không gọi `closeTop` trực tiếp (chỉ `BackHandler` được gọi `closeTop`). Thử: vuốt xuống đóng dialog rồi bấm Back ngay.

### app/src/main/kotlin/com/tool/script/executor/Executor.kt
Đã sửa lỗi (cam kết không bị lại):
+ [PreludeExecutor.exec] Mọi dòng output bắt đầu bằng `@pid <số>` đều bị nuốt khỏi log, trong khi output cuối chỉ bỏ dòng đầu. Đã sửa: chỉ nhận dòng pid khi chưa có pid (`if (pid.get() == null) parsePid(line)`), các dòng `@pid` sau đó được giữ. Kiểm tra tái phát: `parsePid` chỉ được gọi khi `pid.get() == null`.
+ [LocalExecutor.exec] Tiến trình nền (`cmd &`) giữ pipe làm log và banner kẹt ở "Đang chạy". Đã sửa: sau khi lệnh chính thoát chỉ chờ đọc nốt `DRAIN_MS` (1 giây) rồi trả kết quả; việc đọc chạy ngầm trong `readerScope` tới khi pipe đóng, không đóng đầu đọc nên tiến trình nền không bị SIGPIPE. Kiểm tra tái phát: `LocalExecutor.exec` không được `await`/`join` việc đọc mà không có `withTimeoutOrNull(DRAIN_MS)`. Thử trên máy không root: item có lệnh `sleep 300 &` rồi `echo xong` phải kết thúc sau khoảng 1 giây. Giới hạn: đầu ra tiến trình nền in sau đó chỉ vào log, không vào kết quả cuối.

### .github/workflows/build.yml
Đã sửa lỗi (cam kết không bị lại):
+ [dòng 9] `permissions: write-all` cấp toàn quyền ghi cho `GITHUB_TOKEN`. Đã sửa: chỉ `contents: write`, đủ cho bước `softprops/action-gh-release` tạo release. Kiểm tra tái phát: không có `write-all` trong `.github/workflows/*.yml`. Cần chạy CI một lần để xác nhận bước release vẫn đạt.

### app/src/main/kotlin/com/tool/script/boot/Boot.kt
Đã sửa lỗi (cam kết không bị lại):
+ [restoreValue, collectDefaults; Render.kt ProgressItem; Config.kt `float()`] Giá trị `NaN` từ tệp trạng thái, `default`, `min`/`max` hoặc `get` lọt qua `coerceIn` vào slider và progress. Đã sửa: bỏ giá trị không hữu hạn bằng `isFinite()` ở bốn chỗ trên. Kiểm tra tái phát: grep `toFloatOrNull()` trong Boot.kt, Render.kt, Config.kt, mọi chỗ đọc số cho slider/progress phải kèm `isFinite()`. Cần thử: ghi `NaN` vào `$STATE_DIR/<id_slider>` rồi mở app.

### app/src/main/kotlin/com/tool/script/ui/feedback/Feedback.kt
Đã sửa lỗi (cam kết không bị lại):
+ [LogHeader, nút sao chép] Sao chép log lớn ném `TransactionTooLargeException` làm app crash. Đã sửa: `copyToClipboard` bắt lỗi, thử lại với 200.000 ký tự cuối và trả `CopyResult` để hiện toast phù hợp. Kiểm tra tái phát: `copyToClipboard` trong Util.kt phải có try/catch quanh `setPrimaryClip`.

### app/src/main/kotlin/com/tool/script/ui/feedback/LogCenter.kt
Đã sửa lỗi (cam kết không bị lại):
+ [FeedbackHost.showHiddenLog] Chạm lại thông báo của log đang mở hiện toast "log không còn" dù log đang hiển thị. Đã sửa: `showHiddenLog` trả true và bỏ qua nếu `log?.id == id`. Kiểm tra tái phát: dòng `if (log?.id == id) return true` có ở đầu `showHiddenLog`.
+ [thoát app khi log chạy nền, docs/schema.md] Tài liệu nói thông báo chốt "Đã hủy" nhưng không có code nào làm vậy (tiến trình đã chết). Đã sửa tài liệu: thông báo tự gỡ sau tối đa 2 phút. Kiểm tra tái phát: docs/schema.md không còn câu "thông báo chốt \"Đã hủy\"" ở mục Ẩn log.

## Rủi ro đã chấp nhận (người dùng quyết định không sửa)
+ [init.sh install_busybox] busybox chỉ chạy được khi `targetSdk = 28`: app cố định target 28 nên không áp dụng.
+ [Feedback.kt FeedbackHost.notice] Hai thông báo liên tiếp khi hộp xin quyền thông báo còn mở (Android 13+) làm mất thông báo đầu: hiếm gặp.
+ [Feedback.kt LogEntry, LogFiles.init] Tệp log tích lũy trong một phiên rất dài: mỗi lần mở app đã dọn hết `log_*.txt`.
+ [Executor.kt ExecutorManager.detect] Hộp xin quyền su mở quá 5 giây thì dùng backend thường cả phiên: đánh đổi để app không treo ở màn hình khởi động, có ghi chú ở log khởi động.
+ [app/build.gradle.kts, app/beta.jks] Khóa beta (mật khẩu `android`) nằm trong repo nên ai cũng ký được APK cùng chữ ký: giữ có chủ ý vì là khóa test giống khóa debug của Android, mọi build cùng chữ ký nên cập nhật đè được; README đã ghi rõ. Thay bằng khóa thật trước khi phát hành.
+ [thoát app khi lệnh root đang chạy] Người dùng chấp nhận: tiến trình chạy nền bằng root cứ để chạy tiếp, app không dừng chúng.

## Chưa bao phủ
+ Quét tĩnh không bắt được: thời gian và độ mượt của hiệu ứng, blur trên từng máy, hành vi su/root thật. Người dùng xác nhận đã build và chạy bản release (R8) và phiên bản thư viện đều ổn.
+ Đã duyệt kỹ theo kịch bản lặp: Feedback.kt, TopBarActions.kt (không thấy lỗi), Boot.kt, Executor.kt, LogCenter.kt, cùng điều hướng/dialog ở Main.kt. Chưa duyệt kỹ: Render.kt (các item còn lại), SelectScreen.kt, Config.kt, AssetInstaller/EnvFile/BinInstaller.
+ Đã kiểm tra bằng máy: mọi tệp XML parse được, không còn dấu nháy đơn hay định dạng sai trong strings.xml, số tham số `%n$s` khớp giữa tiếng Anh và tiếng Việt, TOML mẫu parse được (trừ `demo_error_syntax.toml` cố ý lỗi).
