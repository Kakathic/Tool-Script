package com.tool.script.crash

import androidx.activity.ComponentActivity

/**
 * Trung tâm lỗi (chạy ở tiến trình ":crash").
 * Hiển thị: crash của app, lỗi script khởi động, lỗi parse TOML.
 * Lỗi chỉ giữ trong bộ nhớ (qua Intent), không ghi ra file.
 * Nút: Sao chép, Chia sẻ, Khởi động lại, Thoát.
 */
class CrashActivity : ComponentActivity()
// TODO: giao diện + nhận stack trace từ Intent (cắt bớt nếu quá dài)
