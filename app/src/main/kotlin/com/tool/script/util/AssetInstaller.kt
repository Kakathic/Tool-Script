package com.tool.script.util

import android.content.Context
import android.content.res.AssetManager
import android.system.Os
import java.io.File
import java.io.IOException

/**
 * Chép các thư mục cần thiết trong assets ([INCLUDED]) ra `no_backup` (không bị Auto Backup sao lưu), giữ nguyên đường dẫn:
 * - `assets/scripts/...` -> `no_backup/scripts/...` (cạnh `$HOME`, xem [Util.scriptsDir]): `init.sh`, và `env.sh` do EnvFile ghi.
 * - `assets/bin/...`, `assets/lib/<abi>/...` -> `$HOME/bin/...`, `$HOME/lib/<abi>/...` (`$HOME` = `no_backup/home`, xem [Util.homeDir]).
 * Chạy lúc khởi động, trước `init.sh` (xem Boot). Không chép các mục trong [EXCLUDED] và các thư mục ABI khác [Util.abi].
 *
 * - Tệp trong thư mục `bin` được `chmod 755` (script / lệnh dùng chung, `$HOME/bin` = `$BIN_DIR` nằm trong `PATH`, xem env.sh).
 * - `lib/<abi>/libbusybox.so`... chỉ chép ABI đang chạy của app ([Util.abi]); việc cài thành lệnh do `init.sh` làm.
 * - Không ghi tệp đánh dấu: tệp đã chép mang `lastModified` = thời điểm cập nhật app; trùng thì bỏ qua, lệch hoặc mất thì chép lại.
 * - Ghi ra tệp tạm rồi đổi tên để không dính lỗi "text file busy" khi tệp cũ đang chạy.
 */
object AssetInstaller {

    /** Thư mục ở gốc assets được chép; thư mục khác (configs, images, webkit...) bỏ qua. Thêm tên vào đây khi cần thêm thư mục. */
    private val INCLUDED = setOf("scripts", "bin", "lib")

    /** Đường dẫn (tính từ gốc assets), tệp hoặc thư mục nằm trong [INCLUDED], không chép. */
    private val EXCLUDED = setOf(
        "scripts/env.sh", // có key @KEY@: EnvFile thay key rồi tự ghi ra no_backup/scripts/env.sh
    )

    private const val MODE_755 = 493 // 0755 (hệ bát phân)

    /** [copied] = số tệp vừa chép, [total] = số tệp cần có, [failed] = lỗi kèm lý do. */
    class Result(val copied: Int, val total: Int, val failed: List<String>)

    fun install(context: Context): Result {
        val assets = context.assets
        val sources = mutableListOf<String>()
        walk(assets, "", Util.abi(), sources)

        val stamp = lastUpdate(context)
        var copied = 0
        val failed = mutableListOf<String>()
        for (path in sources) {
            val dest = destination(context, path)
            if (stamp != 0L && dest.isFile && dest.lastModified() / 1000 == stamp / 1000) continue
            try {
                val dir = dest.parentFile ?: continue
                dir.mkdirs()
                val tmp = File(dir, ".${dest.name}.tmp")
                assets.open(path).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (path.startsWith("bin/")) Os.chmod(tmp.absolutePath, MODE_755)
                if (stamp != 0L) tmp.setLastModified(stamp)
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    throw IOException("không đổi tên được tệp tạm")
                }
                copied++
            } catch (e: Exception) {
                failed += "$path: ${e.message ?: e}"
            }
        }
        return Result(copied, sources.size, failed)
    }

    /** `scripts/...` nằm ở `no_backup/scripts` (cạnh home), các đường dẫn khác nằm trong `$HOME`. */
    private fun destination(context: Context, path: String): File =
        if (path.startsWith("scripts/")) File(Util.scriptsDir(context), path.removePrefix("scripts/"))
        else File(Util.homeDir(context), path)

    /** Liệt kê mọi tệp dưới [dir] (đệ quy) vào [out]: ở gốc chỉ lấy thư mục trong [INCLUDED], bỏ mục loại trừ và `lib/<abi>` khác [abi]. */
    private fun walk(assets: AssetManager, dir: String, abi: String, out: MutableList<String>) {
        for (name in assets.list(dir).orEmpty()) {
            val path = if (dir.isEmpty()) name else "$dir/$name"
            if (dir.isEmpty() && name !in INCLUDED) continue
            if (path in EXCLUDED) continue
            if (dir == "lib" && name != abi) continue
            if (assets.list(path).orEmpty().isNotEmpty()) walk(assets, path, abi, out) else out += path
        }
    }

    /** Thời điểm cập nhật app (ms); 0 nếu không đọc được (khi đó luôn chép lại). */
    private fun lastUpdate(context: Context): Long =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        } catch (_: Exception) {
            0L
        }
}
