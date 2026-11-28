# Tool-Script
Ứng dụng framework ui toml


Ứng dụng Android nạp giao diện từ file TOML, dùng thư viện [Miuix](https://github.com/compose-miuix-ui/miuix) (Compose).

> Đã có luồng chạy đầy đủ từ splash tới màn hình chính, đã build và mở được trên máy thật. Nhiều tính năng mới chỉ viết xong, **chưa kiểm chứng hết trên máy**: xem mục "Lộ trình" bên dưới.

## Cách mở

1. Mở thư mục này bằng Android Studio (cần SDK 37 và JDK 21).
2. Thư mục `gradle/wrapper/` mới có `gradle-wrapper.properties` (Gradle 9.7.0), chưa có `gradlew` và `gradle-wrapper.jar`.
   Android Studio dùng Gradle theo cài đặt, hoặc chạy `gradle wrapper` để sinh wrapper.
3. Kiểm tra version trong `gradle/libs.versions.toml` (Miuix, AGP, Kotlin, Compose...) trước lần sync đầu.

## Build tự động (GitHub Actions)

- Workflow: `.github/workflows/build.yml`, chạy khi push lên `main`, khi có pull request, hoặc chạy tay (Actions > Build APK > Run workflow).
- Kết quả: APK release (`ToolScript-release.apk`) đăng lên mục Releases, tag `latest`, mỗi lần build trên main ghi đè bản cũ. Pull request chỉ build kiểm tra, không đăng.
- Lần chạy đầu cũng là lần build thử đầu tiên: nếu lỗi, xem log của bước "Build release APK".

## Bản chính thức (APK release)

Mỗi lần push lên `main` (hoặc chạy tay workflow), GitHub Actions dựng APK release và đăng vào Release `latest`:

- `ToolScript-release.apk`: bản chính thức, đã bật R8 (`isMinifyEnabled = true`). Workflow không dựng bản debug.

Pull request chỉ build, không đăng.

**Chữ ký hiện tại: khóa beta (tạm thời)**

APK release ký bằng khóa beta `app/beta.jks` (alias `beta`, mật khẩu `android`), khai báo trong `app/build.gradle.kts`. File này commit trong repo và mật khẩu công khai, nên **không bảo mật**: ai cũng ký được app giả mạo cùng chữ ký (buildType `debug` trong Gradle cũng dùng khóa này). Ưu điểm là mọi build cùng chữ ký, cài đè / cập nhật lên nhau được, không cần cấu hình secret.

Khi có khóa thật, thay thông tin trong `signingConfigs` (hoặc thêm một signingConfig mới cho `release`). Đổi chữ ký thì người đã cài bản ký bằng khóa beta phải gỡ app rồi cài lại. Khóa thật không được commit vào repo.

> Bản release bật R8 nhưng `proguard-rules.pro` mới chỉ có luật cho model TOML. Nếu build release báo thiếu class hoặc app lỗi do bị rút gọn, thêm luật `-keep` tương ứng hoặc tạm đặt `isMinifyEnabled = false`.

## Tài liệu

- `docs/schema.md`: schema TOML (bản v0.6), nguồn sự thật cho parser và renderer.
- `CHANGELOG.md`: nhật ký phiên bản. Mỗi cập nhật hoặc sửa lỗi ghi vào mục trên cùng; workflow ghép vào mô tả release và app hiện trong phần Nhật ký khi có bản mới.

## Cấu trúc

```
.github/workflows/build.yml   # dựng + đăng APK release
docs/schema.md                 # schema TOML
app/                           # beta.jks, proguard-rules.pro, build.gradle.kts
└─ src/main/
   ├─ assets/
   │  ├─ configs/            # main.toml, pages/*.toml, icons/ (mẫu); đọc thẳng từ assets, không chép
   │  ├─ scripts/            # init.sh (khởi động, cài busybox), env.sh (biến dùng chung): chép ra no_backup/scripts
   │  ├─ bin/                # glog, slog, killtree: chép ra $HOME/bin ($BIN_DIR, trong PATH)
   │  └─ lib/<abi>/libbusybox.so  # chép ra $HOME/lib, init.sh cài thành $BIN_DIR/busybox
   ├─ kotlin/com/tool/script/
   │  ├─ App.kt, MainActivity.kt
   │  ├─ boot/               # splash + máy trạng thái khởi động
   │  ├─ config/             # model + parser TOML
   │  ├─ executor/           # su -> ROM -> adb -> thường
   │  ├─ state/              # kho biến, visible / readonly / lock
   │  ├─ crash/              # CrashActivity (tiến trình :crash, không ghi log ra file)
   │  ├─ ui/{theme,main,render,feedback}
   │  └─ util/
   └─ res/                # drawable (ic_logo, ic_logo_fg, ic_notification...), mipmap-anydpi*, values, values-night
```

## Quyết định đã chốt

- Khởi động: Splash (logo, thanh tiến trình, 5 dòng log; căn giữa, lề 16dp) -> xin quyền bộ nhớ (từ chối thì thoát) -> chạy script -> nạp TOML -> màn hình chính. Script lỗi vẫn vào app, có banner cảnh báo.
- Thanh tab: cửa sổ trượt 5 ô (tối thiểu 2); mỗi `[[tab]]` là một tab, bắt buộc `title` và `icon`.
- Backend: su -> ROM -> adb -> không root, biến `ROOT_MODE`; `lock` thay cho `require`.
- Ngôn ngữ giao diện: `res/values/strings.xml` là English (mặc định), `res/values-vi/strings.xml` là tiếng Việt; app theo ngôn ngữ hệ thống (Android 13+ còn chọn riêng cho app được nhờ `res/xml/locales_config.xml`). Mọi chuỗi trong code Kotlin đã nằm trong tài nguyên; nơi không có Context (parser TOML, executor, tệp log) lấy chuỗi qua `util/AppText.kt` (gắn Context trong `App.onCreate`). Cảnh báo cấu hình được dịch lúc nạp nên đổi ngôn ngữ thì phải mở lại app mới cập nhật. Nội dung các file TOML (tiêu đề, mô tả, nhãn) vẫn là tiếng Việt, chưa đa ngôn ngữ.
- Hiển thị khi chạy lệnh: `shell = live | full | hidden | banner | notice | snackbar` (mặc định `live`); `hidden` mà lỗi thì hiện toast.
- Lỗi không lưu ra file; quyền thông báo chỉ xin khi dùng tới.
- Nhóm khai báo bằng `[[group]]`, item con là `[[group.item]]` (không còn `type = "group"`); thứ tự `[[item]]` / `[[group]]` theo thứ tự trong file. Item trong group dùng chung một thẻ lớn; item đứng ngoài group mỗi item một thẻ riêng.
- Thanh trên cùng có 2 icon riêng biệt: Nguồn (nằm trong, mở menu thả xuống ngay dưới icon: khởi động lại app, hoặc khởi động lại máy bằng `reboot`, cần root) và Cài đặt (icon bánh răng, sát lề phải, mở hộp thoại giữa màn hình `SettingsDialog` gồm bốn phần từ trên xuống: thẻ giới thiệu (logo, tên app, phiên bản `versionName (versionCode)`, dòng giới thiệu, tác giả, nút "Kiểm tra phiên bản mới"); Giao diện - một hàng ngang chọn Hệ thống / Sáng / Tối; Tùy chọn - các ô tích Màu động (Monet, áp lên mục đang chọn), "Rung khi chạm" (tắt rung của mọi component) và "Làm mờ nền"; Liên kết - GitHub và liên kết khác. Đổi giao diện / tùy chọn xong áp dụng ngay, hộp thoại giữ nguyên để thấy kết quả, và mọi lựa chọn được lưu lại. Thông tin tác giả, repo và liên kết khai báo trong `util/AppAbout.kt`. Kiểm tra phiên bản: đọc release GitHub tag `V1`, lấy dòng `versionCode=N` trong mô tả (workflow ghi `github.run_number` vào đó) rồi so với versionCode của app; có bản mới thì bấm nút để mở trang release). Ngôn ngữ: Android 13+ giao cho hệ thống (`LocaleManager`, cùng nguồn với Cài đặt hệ thống > Ngôn ngữ ứng dụng, khớp `locales_config.xml`); Android 12 trở xuống lưu vào SharedPreferences rồi bọc Context (`util/AppLanguage.kt`), không dựng lại Activity nên lệnh shell đang chạy không bị hủy.

## Lưu ý kỹ thuật

- `minSdk = 24`, `targetSdk = 28`, `compileSdk = 37.2` (API 37 minor 2, Miuix 0.9.5 yêu cầu): target 28 là chủ ý để exec được file ELF tải về trong `filesDir` (app target >= 29 bị SELinux chặn). Không đưa lên Google Play. Tính năng chỉ có ở Android cao (Wireless Debugging, "Truy cập tất cả tệp", kênh thông báo, xin quyền thông báo) được kiểm tra `SDK_INT` lúc chạy. Lúc khởi động app chép các thư mục cần thiết của assets ra `no_backup`: `bin`, `lib/<abi>` vào `$HOME` (`no_backup/home`), `scripts` ở `no_backup/scripts` (cạnh home); lệnh dựng sẵn (busybox, glog, slog, killtree) nằm trong `$HOME/bin` và exec trực tiếp.
- Script chép từ assets không phải lúc nào cũng exec trực tiếp được trên Android 10+ (khi app target >= 29); `init.sh` chạy bằng `sh init.sh`.
- Làm mờ nền thanh trên và thanh tab (thẻ cuộn qua phía dưới thì bị làm mờ) dùng `miuix-blur`, chỉ chạy Android 13+ (API 33); máy thấp hơn giữ thanh nền đặc và ẩn ô tích "Làm mờ nền" trong hộp thoại Cài đặt. Manifest có `tools:overrideLibrary` cho `top.yukonga.miuix.kmp.blur` vì `minSdk = 24`. Dialog dạng sheet: chưa full màn hình thì thanh trên có X (Hủy) bên trái, tích (Xác nhận) bên phải, cả hai nằm trong nền tròn, không có hai nút ở dưới; full màn hình thì thanh trên còn tiêu đề và X (Hủy), hai nút Hủy / Xác nhận hiện ở dưới. Kéo thanh trên xuống từ trạng thái full thì rời full, thả thấp hơn mức tối thiểu thì đóng như Hủy; kéo từ nội dung chỉ thu nhỏ sheet. Nền phía sau là trang bên dưới được làm mờ và phủ tối nhẹ, đậm nhạt theo chiều cao sheet và tắt khi full (không có blur thì nền tối như cũ). Banner thông báo nổi ở đầu màn hình có nền kính mờ. Mọi `OverlayDialog` (log shell, hộp xác nhận, dialog của item, hộp thoại Cài đặt) dùng `BlurDialog` (tham số `centered = true` ép dialog hiện giữa màn hình thay vì trượt lên từ đáy): có blur thì tắt lớp tối của Miuix và vẽ nền mờ riêng (MainScreen bọc trong một `Scaffold` gốc để lớp popup nằm trên cả thanh trên / thanh tab). Menu thả xuống là cửa sổ riêng nên làm mờ chính trang phía sau khi menu mở. Code ở `ui/main/BlurOverlay.kt` và `ui/main/BarBlur.kt`.
- Backend adb: chưa chọn thư viện ADB client (xem TODO trong `libs.versions.toml`).
- Chế độ không root: shell chạy được (`sh -c ...`) nhưng với quyền của chính app (SELinux `untrusted_app`), nên nhiều lệnh hệ thống như `pm`, `settings put`, `dumpsys` có thể bị từ chối. Tài liệu hành vi của Android 17 (`targetSdk = 37`) không nêu thay đổi nào về chạy shell, nhưng nên thử trên máy thật.
- Android 17 thêm quyền `ACCESS_LOCAL_NETWORK` cho app target 37 (đã khai báo trong Manifest, chỉ xin lúc runtime khi dùng backend adb). Chưa rõ kết nối loopback tới adbd có cần quyền này không, cần thử trên máy thật.
- Package / applicationId: `com.tool.script`.

## Lộ trình

Đối chiếu với `docs/schema.md` (bản v0.6): mục 1 đến 11 đã có code, riêng backend ROM và adb (mục 1) chưa làm; mục 12 ("Để lại sau") chưa làm.

### Đã hoàn thành

Giai đoạn 1: khung ứng dụng

- [x] `boot/`: splash (logo, thanh tiến trình, 5 dòng log) -> xin quyền bộ nhớ -> dò backend -> chạy `init.sh` -> nạp TOML -> vào màn hình chính. Script lỗi vẫn vào app, có banner cảnh báo.
- [x] `config/`: model + parser TOML theo `docs/schema.md`. Cảnh báo khi thiếu trường hoặc `type` lạ, báo file + dòng khi sai cú pháp. `main.toml` sai thì sang Trung tâm lỗi; file trang sai thì chỉ trang đó hiện thẻ lỗi.
- [x] `executor/`: backend `su` (libsu) và shell thường, biến `ROOT_MODE`; hết giờ dò quyền root sau 5 giây.
- [x] `state/`: kho biến (`VariableStore`), bộ tính điều kiện `visible` / `readonly` / `lock` (`==`, `!=`, `&&`, `||`), biến dựng sẵn `SDK`, `ABI`, `ARCH`, `ROOT_MODE`.
- [x] `crash/`: Trung tâm lỗi (tiến trình `:crash`) và bộ bắt crash trong `App`; nút Chia sẻ (icon + chữ, khung bo) ở góc phải hàng tiêu đề, hai nút Khởi động lại và Thoát ở dưới; nhấn giữ log để chọn và sao chép chữ. Demo crash: `run = "@crash"` hoặc `"@crash-thread"` (nhóm cuối `output.toml`).

Giai đoạn 2: giao diện

- [x] Màn hình chính: tiêu đề lớn kiểu Miuix thu gọn khi vuốt lên (`LargeTitleTopBar`, dùng `MiuixScrollBehavior`), `HorizontalPager`, thanh tab cửa sổ trượt 5 ô, ẩn thanh tab khi chỉ có 1 trang; giao diện sáng / tối.
- [x] Renderer cho `text`, `group` (`[[group]]`), `button`, `switch` (có `get`), `input` (text / number), `slider`, `link`, `checkbox`, `radio`, `dropdown`, `progress`.
- [x] Trường chung: `visible`, `readonly`, `lock` (và bản `-sh`), `title-sh` / `desc-sh`, `refresh`, `confirm`, `timeout`, `shell` (`live` mặc định / `hidden` / `banner` / `notice`).
- [x] Icon Nguồn (menu khởi động lại, thả xuống ngay dưới icon) và Cài đặt (hộp thoại giữa màn hình, sát lề phải) ở thanh trên cùng.
- [x] Chủ đề (trong hộp thoại Cài đặt): hàng ngang chọn Hệ thống / Sáng / Tối, kèm ô tích Màu động (Monet) của Miuix cho cả ba; được lưu lại (vẫn là 6 giá trị `ThemeMode`, không đổi dữ liệu lưu).
- [x] Ngôn ngữ (trong hộp thoại Cài đặt): Hệ thống / English / Tiếng Việt, đổi ngay không cần mở lại app.
- [x] Trang mẫu trong `assets/configs/pages`: chỉ minh họa tính năng (nút, nhập liệu, lựa chọn, điều kiện, khóa, trang con, dialog, biến env.sh), lệnh chỉ là `echo`, không đổi thông số thiết bị.
- [x] Build tự động bằng GitHub Actions.

### Chưa hoàn thành

Giai đoạn 3: kiểm chứng trên máy thật (code đã viết, chưa thử hoặc mới thử một phần)

- [ ] Chế độ có root: cấp quyền `su`, công tắc có `get`, các lệnh mẫu cần root.
- [ ] Chế độ không root: những lệnh nào bị SELinux chặn (`pm`, `settings put`, `dumpsys`...).
- [ ] Hiển thị kết quả lệnh: bảng log, banner, thông báo hệ thống (xin quyền `POST_NOTIFICATIONS`), toast khi `hidden` bị lỗi.
- [ ] Hộp thoại xác nhận và menu Khởi động lại: khởi động lại app, `reboot` máy.
- [ ] Tiêu đề lớn thu gọn: thử khi bật / tắt làm mờ, khi đổi tab lúc đã thu gọn, khi mở rồi đóng trang con.
- [ ] Quyền `ACCESS_LOCAL_NETWORK` (Android 17): chưa rõ kết nối loopback tới adbd có cần không.

Giai đoạn 4: backend mở rộng

- [ ] Backend ROM (lệnh tích hợp hệ thống, danh sách lệnh cho phép), `ROOT_MODE = system`. Hiện tạm hạ xuống shell thường.
- [ ] Backend adb qua Wireless Debugging, `ROOT_MODE = adb`. Chưa chọn thư viện ADB client (xem TODO trong `libs.versions.toml`).
- [ ] `SuExecutor` đọc từng dòng khi lệnh đang chạy. Hiện `onLine` chỉ được gọi sau khi lệnh xong, nên thanh tiến trình `@progress` của `init.sh` nhảy một lần khi chạy bằng root.

Giai đoạn 5: cấu hình và schema

- [x] Đọc trang từ đường dẫn ngoài: `file`, `page`, `lang`, `lang-sh` bắt đầu bằng `/` thì đọc thẳng file ngoài APK; `main.toml` vẫn ở `assets/configs`.
- [ ] Reload nóng (tự nạp lại khi file đổi).
- [x] Đa ngôn ngữ theo `@khóa` trong TOML (`lang`, `lang-sh`, `load-string`, `load-string-sh` trong `[app]`, xem docs/schema.md). `env.sh` có sẵn `LANGUAGE`, `LANGAPP`, `COUNTRY`. Còn lại: nạp lại chuỗi TOML khi đổi ngôn ngữ trong Cài đặt.
- [ ] Điều kiện: dấu ngoặc và so sánh lớn / nhỏ.
- [ ] `[[group]]`: hiển thị `desc`, hỗ trợ group lồng group (`[[group.group]]` hiện bị bỏ qua kèm cảnh báo).

Giai đoạn 6: chất lượng

- [ ] Unit test (bộ tính điều kiện, `ShellText.quote`, cửa sổ tab, parser TOML). Logic thuần mới được kiểm tra tay, chưa có thư mục test.
- [ ] Commit `gradlew` và `gradle-wrapper.jar`, rồi đổi workflow sang `./gradlew` và bỏ `gradle-version`.

### Lộ trình thành phần Miuix (xếp từ dễ đến khó)

Danh sách này lấy từ tài liệu Miuix bản mới nhất, chưa đối chiếu riêng bản 0.9.4: khi làm từng mục cần kiểm tra lại API.
Làm lần lượt từ trên xuống. Mức 1 đã xong, mức 2 xong `icon` (còn `badge`, `tip`).

Mức 1: dễ, thêm `type` mới, dùng lại khung có sẵn

- [x] Lưu giá trị: item có `id` tự lưu / đọc lại qua file `$STATE_DIR/<id>` (docs/schema.md, mục 7); `get` thành tùy chọn.
- [x] `checkbox`: giống `switch` (`on` / `off` / `get`), hiện ô tick, bấm cả hàng cũng đổi.
- [x] `radio`: chọn một trong `options`, hiện sẵn mọi lựa chọn.
- [x] `dropdown`: chọn một trong `options` bằng menu thả xuống ngay dưới hàng.
- [x] `progress`: thanh tiến trình chỉ để xem, đọc giá trị từ `get`.
- [x] `select` (`multi = true` chọn nhiều, `false` chọn một) từ 6 mục trở lên: chọn toàn màn hình, có ô tìm (bỏ dấu tiếng Việt); `multi = true` có ô Chọn tất cả chỉ áp dụng cho kết quả tìm. Dưới 6 mục giữ hộp thoại nhỏ. Cần thử trên máy thật: gõ phím với `imePadding`, ô Chọn tất cả ở trạng thái gạch ngang.

Mức 2: dễ đến vừa, thêm trường mới cho item có sẵn

- [ ] `badge`: nhãn nhỏ cạnh tiêu đề (Miuix `Badge`).
- [x] `icon`: icon đầu dòng, dùng cùng cách khai báo đường dẫn icon của tab; kèm `icon_size` (dp) và `icon_tint` (docs/schema.md, mục 6). `group` chưa hỗ trợ `icon`.
- [ ] `tip`: chú thích khi nhấn giữ (Miuix `Tooltip`).

Mức 3: vừa, cần thêm logic

- [ ] `refresh = true` ở mức trang: kéo xuống để chạy lại các lệnh `get` (Miuix `PullToRefresh`).
- [ ] `range`: thanh trượt hai đầu (Miuix `RangeSlider`), truyền `$id_min` và `$id_max` vào lệnh.
- [ ] `style = "sheet"` cho `input`: bảng trượt từ dưới lên (Miuix `OverlayBottomSheet`).
- [ ] `[app] key_color`: chọn màu gốc cho chủ đề Màu động (`ThemeController(keyColor = ...)`).

Mức 4: khó, thay đổi lớn hơn

- [ ] `color`: chọn màu, truyền mã hex vào `$id` (Miuix `ColorPicker`, `ColorPalette`).
- [ ] `search`: ô tìm kiếm lọc các item trong trang (Miuix `SearchBar`).
- [ ] `[app] nav_style = "floating"`: thanh tab dưới dạng nổi (Miuix `FloatingNavigationBar`).
- [ ] `[app] blur` và `squircle`: hiệu ứng mờ và góc bo kiểu HyperOS (module `miuix-blur`, `miuix-squircle`, cần thêm dependency).
- [ ] Chuyển sang module `miuix-preference` (`SwitchPreference`, `SliderPreference`...) cho giao diện hàng cài đặt chính chủ, phải viết lại một phần `Render.kt`.
