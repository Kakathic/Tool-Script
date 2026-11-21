package com.tool.script.util

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.system.Os
import java.io.File
import java.io.IOException

/**
 * Chép các thư mục cần thiết trong assets ([INCLUDED]) ra `$HOME` (= `no_backup/home`, xem [Util.homeDir], không bị Auto Backup sao lưu),
 * giữ nguyên đường dẫn: `assets/scripts/init.sh` -> `$HOME/scripts/init.sh`.
 * Chạy lúc khởi động, trước `init.sh` (xem Boot). Không chép các mục trong [EXCLUDED] và các thư mục ABI không khớp máy.
 *
 * - Tệp trong thư mục `bin` được `chmod 755` (script / lệnh dùng chung, `$HOME/bin` = `$BIN_DIR` nằm trong `PATH`, xem env.sh).
 * - `lib/<abi>/libbusybox.so`... chỉ chép ABI có trong `Build.SUPPORTED_ABIS`; việc cài thành lệnh do `init.sh` làm.
 * - Chỉ chép lại khi app được cài mới / cập nhật (so `lastUpdateTime`) hoặc khi tệp bị mất; tệp đã chép mà nay không còn trong assets thì bị xóa.
 * - Ghi ra tệp tạm rồi đổi tên để không dính lỗi "text file busy" khi tệp cũ đang chạy.
 */
object AssetInstaller {

    /** Thư mục ở gốc assets được chép ra `$HOME`; thư mục khác (configs, images, webkit...) bỏ qua. Thêm tên vào đây khi cần thêm thư mục. */
    private val INCLUDED = setOf("scripts", "bin", "lib")

    /** Đường dẫn (tính từ gốc assets), tệp hoặc thư mục nằm trong [INCLUDED], không chép. */
    private val EXCLUDED = setOf(
        "scripts/env.sh", // có key @KEY@: EnvFile thay key rồi tự ghi ra $HOME/scripts/env.sh
    )

    private const val MODE_755 = 493 // 0755 (hệ bát phân)
    private const val STAMP = ".assets"

    /** [copied] = số tệp vừa chép, [total] = số tệp cần có, [failed] = lỗi kèm lý do. */
    class Result(val copied: Int, val total: Int, val failed: List<String>)

    fun install(context: Context): Result {
        val assets = context.assets
        val root = Util.homeDir(context)
        val sources = mutableListOf<String>()
        walk(assets, "", Build.SUPPORTED_ABIS.toSet(), sources)

        // Dấu cài: dòng đầu = lần cập nhật app, các dòng sau = tệp đã chép (để dọn tệp đã bị gỡ khỏi assets).
        val stampFile = File(root, STAMP)
        val old = runCatching { stampFile.readLines() }.getOrDefault(emptyList())
        val stamp = lastUpdate(context)
        val force = old.firstOrNull() != stamp

        var copied = 0
        val failed = mutableListOf<String>()
        for (path in sources) {
            val dest = File(root, path)
            if (!force && dest.isFile) continue
            try {
                val dir = dest.parentFile ?: root
                dir.mkdirs()
                val tmp = File(dir, ".${dest.name}.tmp")
                assets.open(path).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (path.startsWith("bin/")) Os.chmod(tmp.absolutePath, MODE_755)
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    throw IOException("không đổi tên được tệp tạm")
                }
                copied++
            } catch (e: Exception) {
                failed += "$path: ${e.message ?: e}"
            }
        }

        val wanted = sources.toHashSet()
        for (path in old.drop(1)) {
            if (path !in wanted) File(root, path).delete()
        }

        if (failed.isEmpty()) {
            runCatching { stampFile.writeText((listOf(stamp) + sources).joinToString("\n") + "\n") }
        } else {
            stampFile.delete() // lần mở sau chép lại hết
        }
        return Result(copied, sources.size, failed)
    }

    /** Liệt kê mọi tệp dưới [dir] (đệ quy) vào [out]: ở gốc chỉ lấy thư mục trong [INCLUDED], bỏ mục loại trừ và `lib/<abi>` không khớp [abis]. */
    private fun walk(assets: AssetManager, dir: String, abis: Set<String>, out: MutableList<String>) {
        for (name in assets.list(dir).orEmpty()) {
            val path = if (dir.isEmpty()) name else "$dir/$name"
            if (dir.isEmpty() && name !in INCLUDED) continue
            if (path in EXCLUDED) continue
            if (dir == "lib" && name !in abis) continue
            if (assets.list(path).orEmpty().isNotEmpty()) walk(assets, path, abis, out) else out += path
        }
    }

    private fun lastUpdate(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        } catch (_: Exception) {
            "0"
        }
}
