package com.tool.script.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.tool.script.MainActivity
import java.io.File

// Tiện ích dùng chung: quyền bộ nhớ, đường dẫn icon assets / ngoài, chép file từ assets.
object Util {

    /**
     * Đã có quyền bộ nhớ chưa. Android 11+ (API 30): "Truy cập tất cả tệp";
     * Android 7 đến 10: quyền ghi bộ nhớ ngoài (xin lúc chạy, xem [needsLegacyStoragePermission]).
     */
    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** true = máy dưới Android 11, phải xin WRITE_EXTERNAL_STORAGE bằng hộp thoại quyền thường. */
    fun needsLegacyStoragePermission(): Boolean = Build.VERSION.SDK_INT < 30

    /** Chỉ gọi khi SDK_INT >= 30 (hằng số Settings này mới có từ Android 11). */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * Model cho Coil: đường dẫn bắt đầu bằng `/` là file ngoài, còn lại tính từ `assets/configs/`
     * (schema mục 3).
     */
    fun iconModel(icon: String): Any =
        if (icon.startsWith("/")) File(icon) else "file:///android_asset/configs/$icon"

    /** Mở lại app từ đầu (chạy lại cả khâu khởi động) rồi thoát tiến trình hiện tại. */
    fun restartApp(context: Context) {
        context.startActivity(Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java)))
        Runtime.getRuntime().exit(0)
    }

    /** `$HOME` của app: `no_backup/home` (không bị Auto Backup sao lưu), nơi chép assets cần thiết và đặt env.sh cạnh init.sh. */
    fun homeDir(context: Context): File = File(context.noBackupFilesDir, "home").apply { mkdirs() }

    /** Chép một file trong assets ra [dest], tạo thư mục cha nếu cần. */
    fun copyAsset(context: Context, assetPath: String, dest: File): File {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input -> dest.outputStream().use { input.copyTo(it) } }
        return dest
    }
}

/** Số Float -> chuỗi gọn: 5.0 -> "5", 2.5 -> "2.5". */
fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toLong().toString() else value.toString()

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}
