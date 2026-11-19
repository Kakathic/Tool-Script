# Tool-Script
Ứng dụng framework ui toml


Ứng dụng Android nạp giao diện từ file TOML, dùng thư viện [Miuix](https://github.com/compose-miuix-ui/miuix) (Compose).

> Đã có luồng chạy đầy đủ từ splash tới màn hình chính (xem "Trạng thái" bên dưới). Code mới viết, **chưa build thử trên máy**: build bằng GitHub Actions hoặc Android Studio rồi báo lỗi nếu có.

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

## Lưu ý kỹ thuật

- `minSdk = 30`: Wireless Debugging (backend adb) và quyền "Truy cập tất cả tệp" cần Android 11+.
- File chép từ assets ra thư mục app không exec trực tiếp được trên Android 10+; chạy bằng `sh init.sh`.
- Backend adb: chưa chọn thư viện ADB client (xem TODO trong `libs.versions.toml`).
- Chế độ không root: shell chạy được (`sh -c ...`) nhưng với quyền của chính app (SELinux `untrusted_app`), nên nhiều lệnh hệ thống như `pm`, `settings put`, `dumpsys` có thể bị từ chối. Tài liệu hành vi của Android 17 (`targetSdk = 37`) không nêu thay đổi nào về chạy shell, nhưng nên thử trên máy thật.
- Android 17 thêm quyền `ACCESS_LOCAL_NETWORK` cho app target 37 (đã khai báo trong Manifest, chỉ xin lúc runtime khi dùng backend adb). Chưa rõ kết nối loopback tới adbd có cần quyền này không, cần thử trên máy thật.
- Package / applicationId: `com.tool.script`.

## Trạng thái

Đã viết:

- `config/`: model + parser TOML theo `docs/schema.md` (cảnh báo khi thiếu trường / `type` lạ, báo file + dòng khi sai cú pháp). `main.toml` sai thì sang Trung tâm lỗi; file trang sai thì chỉ trang đó hiện thẻ lỗi.
- `state/`: kho biến (`VariableStore`) và bộ tính điều kiện `visible_if` / `lock` (`==`, `!=`, `&&`, `||`, chưa có ngoặc).
- `executor/`: `su` (libsu) và shell thường; ROM / adb **chưa làm**, tạm hạ xuống shell thường. Hết giờ dò quyền root: 5 giây.
- `boot/`: splash (logo, thanh tiến trình, 5 dòng log) -> xin quyền bộ nhớ -> dò backend -> chạy `init.sh` -> nạp TOML -> vào màn hình chính.
- `ui/`: màn hình chính (tiêu đề, `HorizontalPager`, thanh tab cửa sổ trượt), renderer cho `text`, `group`, `button`, `switch`, `input`, `select`, `slider`, `link`, hộp thoại `confirm`, log, banner, thông báo hệ thống.
- `crash/`: Trung tâm lỗi (tiến trình `:crash`) + bộ bắt crash trong `App`.

Còn lại:

1. Backend ROM và adb.
2. Chạy thử trên máy thật (nhất là chế độ không root, `ACCESS_LOCAL_NETWORK`).
3. Đọc cấu hình từ thư mục ngoài + reload nóng, đa ngôn ngữ (schema mục 12).
4. Thêm unit test (logic thuần đã được kiểm tra tay: điều kiện, quote shell, cửa sổ tab).
