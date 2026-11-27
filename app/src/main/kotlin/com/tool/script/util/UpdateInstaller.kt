package com.tool.script.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Kết quả mở trình cài đặt. */
enum class InstallResult { Started, NeedPermission, Failed }

/**
 * Tải APK bản mới vào cache/updates rồi mở trình cài đặt của hệ thống. Cài đè được vì mọi bản dựng cùng chữ ký (xem README).
 * Cần quyền REQUEST_INSTALL_PACKAGES; Android 8+ còn bắt người dùng cho phép "cài ứng dụng không rõ nguồn" cho chính app này.
 */
object UpdateInstaller {
    private const val FileName = "ToolScript-update.apk"

    private fun dir(context: Context): File = File(context.cacheDir, "updates")

    /** Tải [url] về, báo tiến độ 0..1 qua [onProgress] (gọi từ luồng nền). Trả null nếu lỗi, hoặc APK tải về không phải của ứng dụng này. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val dir = dir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "$FileName.part")
        val target = File(dir, FileName)
        val ok = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                if (conn.responseCode != 200) return@runCatching false
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) {
                                    last = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
                (total <= 0 || part.length() == total) && part.renameTo(target)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (!ok || !isOurApk(context, target)) {
            part.delete()
            target.delete()
            return@withContext null
        }
        target
    }

    /** APK phải đọc được và cùng tên gói với ứng dụng đang chạy, tránh mở trình cài đặt cho tệp hỏng hoặc sai. */
    private fun isOurApk(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        return info.packageName == context.packageName
    }

    /** Mở trình cài đặt cho [file]. Chưa được phép cài từ nguồn này thì mở trang cấp quyền và trả [InstallResult.NeedPermission]. */
    fun install(context: Context, file: File): InstallResult {
        if (!file.exists()) return InstallResult.Failed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
            return InstallResult.NeedPermission
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            InstallResult.Started
        }.getOrDefault(InstallResult.Failed)
    }
}
