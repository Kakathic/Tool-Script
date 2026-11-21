package com.tool.script.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileNotFoundException

/** Kết quả chuẩn bị env.sh: [file] đã ghi ra thư mục app, [unknownKeys] là các `@KEY@` không có giá trị. */
class PreparedEnv(val file: File, val unknownKeys: List<String>)

/**
 * `assets/scripts/env.sh`: file biến dùng chung cho mọi lệnh shell của app.
 * Mỗi lần mở app, các key dạng `@KEY@` trong file được thay bằng đường dẫn thật rồi ghi ra
 * `filesDir/scripts/env.sh`; mọi lệnh chạy qua app nạp file này trước (xem ExecutorManager).
 */
object EnvFile {

    const val ASSET = "scripts/env.sh"

    private val KEY = Regex("@([A-Z][A-Z0-9_]*)@")

    /** Các key có sẵn và đường dẫn tương ứng. */
    fun keys(context: Context): Map<String, String> {
        val ext = Environment.getExternalStorageDirectory().absolutePath
        return linkedMapOf(
            "PACKAGE" to context.packageName,
            "APP_DIR" to context.applicationInfo.dataDir,
            "FILES_DIR" to context.filesDir.absolutePath,
            "CACHE_DIR" to context.cacheDir.absolutePath,
            "SCRIPTS_DIR" to File(context.filesDir, "scripts").absolutePath,
            "STATE_DIR" to File(context.filesDir, "state").absolutePath,
            "NOBACKUP_DIR" to context.noBackupFilesDir.absolutePath,
            "BIN_DIR" to File(context.noBackupFilesDir, "bin").absolutePath,
            "LIB_DIR" to context.applicationInfo.nativeLibraryDir,
            "ABIS" to Build.SUPPORTED_ABIS.joinToString(" "),
            "EXT_ROOT" to ext,
            "EXT_DIR" to "$ext/ToolScript",
        )
    }

    /** Thay mọi `@KEY@` có trong [keys]; key lạ giữ nguyên và được trả về trong danh sách thứ hai. */
    fun render(text: String, keys: Map<String, String>): Pair<String, List<String>> {
        val unknown = LinkedHashSet<String>()
        val out = KEY.replace(text.replace("\r\n", "\n")) { m ->
            keys[m.groupValues[1]] ?: run {
                unknown += m.value
                m.value
            }
        }
        return out to unknown.toList()
    }

    /** Đọc asset, thay key, ghi ra thư mục app. Trả về null nếu app không có file env.sh. */
    fun prepare(context: Context): PreparedEnv? {
        val text = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: FileNotFoundException) {
            return null
        }
        val (rendered, unknown) = render(text, keys(context))
        val dest = File(context.filesDir, "scripts/env.sh")
        dest.parentFile?.mkdirs()
        dest.writeText(rendered, Charsets.UTF_8)
        File(context.filesDir, "state").mkdirs()
        return PreparedEnv(dest, unknown)
    }
}
