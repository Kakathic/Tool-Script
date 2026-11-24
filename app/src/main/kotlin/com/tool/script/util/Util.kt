package com.tool.script.util

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.system.Os
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

    /** Đóng hẳn app: gỡ khỏi danh sách gần đây rồi thoát tiến trình (lệnh đang chạy bị dừng). */
    fun exitApp(context: Context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.finishAndRemoveTask()
        Runtime.getRuntime().exit(0)
    }

    /** `$HOME` của app: `no_backup/home` (không bị Auto Backup sao lưu), nơi chép assets `bin`, `lib`. */
    fun homeDir(context: Context): File = File(context.noBackupFilesDir, "home").apply { mkdirs() }

    /** `no_backup/scripts`, cạnh `$HOME`: chứa `init.sh` (chép từ assets) và `env.sh` đã thay key. */
    fun scriptsDir(context: Context): File = File(context.noBackupFilesDir, "scripts").apply { mkdirs() }

    /** ABI chính thức mà tiến trình app đang chạy (một giá trị, vd `arm64-v8a`), không phải danh sách ABI máy hỗ trợ. */
    fun abi(): String {
        val abis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        return abis.firstOrNull() ?: Build.SUPPORTED_ABIS.firstOrNull() ?: ""
    }

    /** Kiến trúc CPU lấy từ framework Android (`Os.uname().machine`, như `uname -m`): `aarch64`, `armv7l`, `x86_64`, `i686`... */
    fun arch(): String =
        runCatching { Os.uname().machine }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: System.getProperty("os.arch").orEmpty()

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

enum class CopyResult { Full, Truncated, Failed }

/** Văn bản quá lớn (vượt giới hạn binder ~1 MB) thì thử lại với [CLIPBOARD_FALLBACK_CHARS] ký tự cuối thay vì để app crash. */
private const val CLIPBOARD_FALLBACK_CHARS = 200_000

fun copyToClipboard(context: Context, label: String, text: String): CopyResult {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return CopyResult.Failed
    try {
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        return CopyResult.Full
    } catch (_: Exception) {
        if (text.length <= CLIPBOARD_FALLBACK_CHARS) return CopyResult.Failed
    }
    return try {
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text.takeLast(CLIPBOARD_FALLBACK_CHARS)))
        CopyResult.Truncated
    } catch (_: Exception) {
        CopyResult.Failed
    }
}
