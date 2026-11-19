# Tool-Script
Ứng dụng framework ui toml


Ứng dụng Android nạp giao diện từ file TOML, dùng thư viện [Miuix](https://github.com/compose-miuix-ui/miuix) (Compose).

> Đây mới là **khung project**, chưa viết logic và **chưa build thử**.

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

## Bước tiếp theo

1. Model + parser TOML, test bằng file mẫu trong `assets/configs/`.
2. Executor (local trước, rồi su, rồi adb/ROM).
3. Splash + script khởi động.
4. Màn hình chính + thanh tab trượt.
5. Renderer các loại item, rồi `shell` / `lock`.
6. Trung tâm lỗi.
