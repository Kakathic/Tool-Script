package com.tool.script.util

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Lệnh dựng sẵn (busybox, toybox, 7z...) nằm trong thư viện native của app: `app/src/main/jniLibs/<abi>/lib_<tên>.so`.
 * Android cài đúng ABI của máy rồi giải nén ra `nativeLibraryDir` (`$LIB_DIR`), nơi app thường (không root) được phép chạy file.
 * Ở đây chỉ tạo symlink `filesDir/bin/<tên>` -> `lib_<tên>.so` (`$BIN_DIR`, đã nằm đầu `PATH`, xem env.sh) để gọi bằng tên gốc;
 * gọi qua symlink thì argv[0] vẫn là `busybox` / `toybox` nên các lệnh multi-call chạy đúng.
 * Đường dẫn thư viện đổi sau mỗi lần cập nhật app nên mỗi lần mở app symlink được kiểm tra và tạo lại nếu lệch.
 */
object BinInstaller {

    private const val PREFIX = "lib_"
    private const val SUFFIX = ".so"

    /** [linked] = tên lệnh đã tạo / sửa symlink, [total] = số lệnh tìm thấy, [failed] = lỗi kèm lý do. */
    class Result(val linked: List<String>, val total: Int, val failed: List<String>)

    fun install(context: Context): Result {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val libs = libDir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }.orEmpty()
        val linked = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val wanted = HashSet<String>()
        for (lib in libs) {
            val name = lib.name.removePrefix(PREFIX).removeSuffix(SUFFIX)
            if (name.isEmpty()) continue
            wanted += name
            try {
                lib.setExecutable(true, false)
                val link = File(binDir, name)
                val current = try {
                    Os.readlink(link.absolutePath)
                } catch (_: Exception) {
                    null
                }
                if (current != lib.absolutePath) {
                    link.delete()
                    Os.symlink(lib.absolutePath, link.absolutePath)
                    linked += name
                }
            } catch (e: Exception) {
                failed += "$name: ${e.message ?: e}"
            }
        }
        // Dọn symlink của lệnh đã bị gỡ khỏi jniLibs (file thường do người dùng tự đặt vào thì giữ nguyên).
        binDir.listFiles()?.forEach { f ->
            if (f.name !in wanted && isSymlink(f)) f.delete()
        }
        return Result(linked, libs.size, failed)
    }

    /** Thay cho java.nio.file.Files.isSymbolicLink (chỉ có từ API 26); Os.lstat có từ API 21. */
    private fun isSymlink(f: File): Boolean =
        try {
            OsConstants.S_ISLNK(Os.lstat(f.absolutePath).st_mode)
        } catch (_: Exception) {
            false
        }
}
