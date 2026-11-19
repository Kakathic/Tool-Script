package com.tool.script.boot

// Khởi động (docs/schema.md mục 11):
//   1. Xin quyền bộ nhớ (từ chối -> thoát app)        - thanh tiến trình vô hạn
//   2. Dò backend (su -> ROM -> adb -> thường), copy script từ assets
//   3. Chạy script, đọc dòng "@progress N"            - thanh tiến trình hiện %
//   4. Nạp TOML từ assets                             - thanh tiến trình vô hạn trở lại
//   5. Vào màn hình chính (script lỗi vẫn vào, có banner cảnh báo)
// Bố cục splash: logo trên cùng, thanh tiến trình, 5 dòng log; căn giữa, lề 2 bên 16dp.
// TODO: BootState, BootViewModel, SplashScreen
