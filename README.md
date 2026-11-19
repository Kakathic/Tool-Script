# Tool-Script
Ứng dụng framework ui toml


Ứng dụng Android nạp giao diện từ file TOML, dùng thư viện [Miuix](https://github.com/compose-miuix-ui/miuix) (Compose).

> Đã có luồng chạy đầy đủ từ splash tới màn hình chính, đã build và mở được trên máy thật. Nhiều tính năng mới chỉ viết xong, **chưa kiểm chứng hết trên máy**: xem mục "Lộ trình" bên dưới.

## Cách mở

1. Mở thư mục này bằng Android Studio (cần SDK 37 và JDK 17+).
2. Thư mục `gradle/wrapper/` mới có `gradle-wrapper.properties` (Gradle 9.3.1), chưa có `gradlew` và `gradle-wrapper.jar`.
   Android Studio dùng Gradle theo cài đặt, hoặc chạy `gradle wrapper` để sinh wrapper.
3. Kiểm tra version trong `gradle/libs.versions.toml` (Miuix, AGP, Kotlin, Compose...) trước lần sync đầu.

## Build tự động (GitHub Actions)

- Workflow: `.github/workflows/build.yml`, chạy khi push lên `main`, khi có pull request, hoặc chạy tay (Actions > Build APK > Run workflow).
- Kết quả: APK debug trong mục Artifacts của lần chạy (`ToolScript-debug-apk`), giữ 30 ngày.
- Lần chạy đầu cũng là lần build thử đầu tiên: nếu lỗi, xem log của bước "Build debug APK".

## Tài liệu

- `docs/schema.md`: schema TOML (bản v0.5), nguồn sự thật cho parser và renderer.

## Cấu trúc

```
app/src/main/
├─ assets/
│  ├─ configs/            # main.toml, pages/*.toml, icons/ (mẫu)
│  └─ scripts/init.sh     # script khởi động (báo tiến trình bằng "@progress N")
├─ kotlin/com/tool/script/
│  ├─ App.kt, MainActivity.kt
│  ├─ boot/               # splash + máy trạng thái khởi động
│  ├─ config/             # model + parser TOML
│  ├─ executor/           # su -> ROM -> adb -> thường
│  ├─ state/              # kho biến, visible_if / lock
│  ├─ crash/              # CrashActivity (tiến trình :crash, không ghi log ra file)
│  ├─ ui/{theme,main,render,feedback}
│  └─ util/
└─ res/
```

## Quyết định đã chốt

- Khởi động: Splash (logo, thanh tiến trình, 5 dòng log; căn giữa, lề 16dp) -> xin quyền bộ nhớ (từ chối thì thoát) -> chạy script -> nạp TOML -> màn hình chính. Script lỗi vẫn vào app, có banner cảnh báo.
- Thanh tab: cửa sổ trượt 4 ô (tối thiểu 2); mỗi `[[page]]` là một tab, bắt buộc `title` và `icon`.
- Backend: su -> ROM -> adb -> không root, biến `ROOT_MODE`; `lock` thay cho `require`.
- Hiển thị khi chạy lệnh: `shell = log | hidden | banner | notice`; `hidden` mà lỗi thì hiện toast.
- Lỗi không lưu ra file; quyền thông báo chỉ xin khi dùng tới.
- Nhóm khai báo bằng `[[group]]`, item con là `[[group.item]]` (không còn `type = "group"`); thứ tự `[[item]]` / `[[group]]` theo thứ tự trong file. Item trong group dùng chung một thẻ lớn; item đứng ngoài group mỗi item một thẻ riêng.
- Thanh trên cùng có 2 icon riêng biệt: Thông tin (mở hộp thoại tùy chỉnh chủ đề: Theo hệ thống / Sáng / Tối, được lưu lại) và Nguồn (mở menu khởi động lại: khởi động lại app, hoặc khởi động lại máy bằng `reboot`, cần root).

## Lưu ý kỹ thuật

- `minSdk = 30`: Wireless Debugging (backend adb) và quyền "Truy cập tất cả tệp" cần Android 11+.
- File chép từ assets ra thư mục app không exec trực tiếp được trên Android 10+; chạy bằng `sh init.sh`.
- Backend adb: chưa chọn thư viện ADB client (xem TODO trong `libs.versions.toml`).
- Chế độ không root: shell chạy được (`sh -c ...`) nhưng với quyền của chính app (SELinux `untrusted_app`), nên nhiều lệnh hệ thống như `pm`, `settings put`, `dumpsys` có thể bị từ chối. Tài liệu hành vi của Android 17 (`targetSdk = 37`) không nêu thay đổi nào về chạy shell, nhưng nên thử trên máy thật.
- Android 17 thêm quyền `ACCESS_LOCAL_NETWORK` cho app target 37 (đã khai báo trong Manifest, chỉ xin lúc runtime khi dùng backend adb). Chưa rõ kết nối loopback tới adbd có cần quyền này không, cần thử trên máy thật.
- Package / applicationId: `com.tool.script`.

## Lộ trình

Đối chiếu với `docs/schema.md` (bản v0.5): mục 1 đến 11 đã có code, riêng backend ROM và adb (mục 1) chưa làm; mục 12 ("Để lại sau") chưa làm.

### Đã hoàn thành

Giai đoạn 1: khung ứng dụng

- [x] `boot/`: splash (logo, thanh tiến trình, 5 dòng log) -> xin quyền bộ nhớ -> dò backend -> chạy `init.sh` -> nạp TOML -> vào màn hình chính. Script lỗi vẫn vào app, có banner cảnh báo.
- [x] `config/`: model + parser TOML theo `docs/schema.md`. Cảnh báo khi thiếu trường hoặc `type` lạ, báo file + dòng khi sai cú pháp. `main.toml` sai thì sang Trung tâm lỗi; file trang sai thì chỉ trang đó hiện thẻ lỗi.
- [x] `executor/`: backend `su` (libsu) và shell thường, biến `ROOT_MODE`; hết giờ dò quyền root sau 5 giây.
- [x] `state/`: kho biến (`VariableStore`), bộ tính điều kiện `visible_if` / `lock` (`==`, `!=`, `&&`, `||`), biến dựng sẵn `SDK`, `ABI`, `ROOT_MODE`.
- [x] `crash/`: Trung tâm lỗi (tiến trình `:crash`) và bộ bắt crash trong `App`; nút Sao chép, Chia sẻ, Khởi động lại, Thoát.

Giai đoạn 2: giao diện

- [x] Màn hình chính: tiêu đề, `HorizontalPager`, thanh tab cửa sổ trượt 4 ô, ẩn thanh tab khi chỉ có 1 trang; giao diện sáng / tối.
- [x] Renderer cho `text`, `group` (`[[group]]`), `button`, `switch` (có `get`), `input` (text / number), `select`, `slider`, `link`.
- [x] Trường chung: `visible_if`, `lock` + `lock_mode` (`readonly` / `hide`), `confirm`, `timeout`, `shell` (`log` / `hidden` / `banner` / `notice`).
- [x] Icon Thông tin (tùy chỉnh chủ đề) và Nguồn (khởi động lại) ở thanh trên cùng.
- [x] Trang mẫu trong `assets/configs/pages` (thông tin máy, mạng, chế độ, lệnh cần root).
- [x] Build tự động bằng GitHub Actions.

### Chưa hoàn thành

Giai đoạn 3: kiểm chứng trên máy thật (code đã viết, chưa thử hoặc mới thử một phần)

- [ ] Chế độ có root: cấp quyền `su`, công tắc có `get`, các lệnh mẫu cần root.
- [ ] Chế độ không root: những lệnh nào bị SELinux chặn (`pm`, `settings put`, `dumpsys`...).
- [ ] Hiển thị kết quả lệnh: bảng log, banner, thông báo hệ thống (xin quyền `POST_NOTIFICATIONS`), toast khi `hidden` bị lỗi.
- [ ] Hộp thoại xác nhận và menu Khởi động lại: khởi động lại app, `reboot` máy.
- [ ] Quyền `ACCESS_LOCAL_NETWORK` (Android 17): chưa rõ kết nối loopback tới adbd có cần không.

Giai đoạn 4: backend mở rộng

- [ ] Backend ROM (lệnh tích hợp hệ thống, danh sách lệnh cho phép), `ROOT_MODE = system`. Hiện tạm hạ xuống shell thường.
- [ ] Backend adb qua Wireless Debugging, `ROOT_MODE = adb`. Chưa chọn thư viện ADB client (xem TODO trong `libs.versions.toml`).
- [ ] `SuExecutor` đọc từng dòng khi lệnh đang chạy. Hiện `onLine` chỉ được gọi sau khi lệnh xong, nên thanh tiến trình `@progress` của `init.sh` nhảy một lần khi chạy bằng root.

Giai đoạn 5: cấu hình và schema

- [ ] Nạp cấu hình từ thư mục ngoài và reload nóng (hiện chỉ đọc từ `assets/configs`).
- [ ] Đa ngôn ngữ theo khóa (`title_vi`, `title_en`...). `lang` trong `main.toml` đã được đọc nhưng chưa dùng.
- [ ] Điều kiện: dấu ngoặc và so sánh lớn / nhỏ.
- [ ] `[[group]]`: hiển thị `desc`, hỗ trợ group lồng group (`[[group.group]]` hiện bị bỏ qua kèm cảnh báo).

Giai đoạn 6: chất lượng

- [ ] Unit test (bộ tính điều kiện, `ShellText.quote`, cửa sổ tab, parser TOML). Logic thuần mới được kiểm tra tay, chưa có thư mục test.
- [ ] Commit `gradlew` và `gradle-wrapper.jar`, rồi đổi workflow sang `./gradlew` và bỏ `gradle-version`.
