package com.tool.script.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

// Tiện ích dùng chung: quyền bộ nhớ, đường dẫn icon assets / ngoài, chép file từ assets.
object Util {

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

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
