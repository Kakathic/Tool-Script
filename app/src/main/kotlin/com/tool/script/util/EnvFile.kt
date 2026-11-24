package com.tool.script.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileNotFoundException

/** Kết quả chuẩn bị env.sh: [file] đã ghi ra thư mục app, [unknownKeys] là các `@KEY@` không có giá trị. */
class PreparedEnv(val file: File, val unknownKeys: List<String>)

/**
 * `assets/scripts/env.sh`: file biến dùng chung cho mọi lệnh shell của app.
 * Mỗi lần mở app, các key dạng `@KEY@` trong file được thay bằng đường dẫn thật rồi ghi ra
 * `no_backup/scripts/env.sh` (cạnh `init.sh`, nằm cạnh `$HOME` = `no_backup/home`); mọi lệnh chạy qua app nạp file này trước (xem ExecutorManager).
 */
object EnvFile {

    const val ASSET = "scripts/env.sh"

    private val KEY = Regex("@([A-Z][A-Z0-9_]*)@")

    /**
     * Các key có sẵn và giá trị tương ứng. [language] là ngôn ngữ app vừa chọn (null = đọc từ [LanguagePrefs]).
     * `LANGUAGE`, `COUNTRY`: ngôn ngữ và quốc gia của thiết bị; `LANGAPP`: ngôn ngữ app đang dùng.
     */
    fun keys(context: Context, language: AppLanguage? = null): Map<String, String> {
        val ext = Environment.getExternalStorageDirectory().absolutePath
        val home = Util.homeDir(context)
        return linkedMapOf(
            "PACKAGE" to context.packageName,
            "APP_DIR" to context.applicationInfo.dataDir,
            "FILES_DIR" to context.filesDir.absolutePath,
            "CACHE_DIR" to context.cacheDir.absolutePath,
            "HOME_DIR" to home.absolutePath,
            "SCRIPTS_DIR" to Util.scriptsDir(context).absolutePath,
            "STATE_DIR" to File(context.filesDir, "state").absolutePath,
            "NOBACKUP_DIR" to context.noBackupFilesDir.absolutePath,
            "BIN_DIR" to File(home, "bin").absolutePath,
            "LIB_DIR" to context.applicationInfo.nativeLibraryDir,
            "ABI" to Util.abi(),
            "ARCH" to Util.arch(),
            "EXT_ROOT" to ext,
            "EXT_DIR" to "$ext/ToolScript",
            "LANGUAGE" to LanguagePrefs.deviceLocale().language,
            "LANGAPP" to LanguagePrefs.effectiveTag(context, language),
            "COUNTRY" to LanguagePrefs.deviceLocale().country,
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

    /**
     * Đọc asset, thay key, ghi ra thư mục app. Trả về null nếu app không có file env.sh.
     * Gọi lại được lúc app đang chạy (ví dụ khi đổi ngôn ngữ để cập nhật `LANGAPP`): ghi file tạm rồi đổi tên nên lệnh đang nạp env.sh không đọc phải file dở.
     */
    fun prepare(context: Context, language: AppLanguage? = null): PreparedEnv? {
        val text = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: FileNotFoundException) {
            return null
        }
        val (rendered, unknown) = render(text, keys(context, language))
        val dest = File(Util.scriptsDir(context), "env.sh")
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "env.sh.tmp")
        tmp.writeText(rendered, Charsets.UTF_8)
        if (!tmp.renameTo(dest)) {
            dest.writeText(rendered, Charsets.UTF_8)
            tmp.delete()
        }
        File(context.filesDir, "state").mkdirs()
        return PreparedEnv(dest, unknown)
    }
}
