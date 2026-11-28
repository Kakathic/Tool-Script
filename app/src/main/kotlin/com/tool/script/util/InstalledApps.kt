package com.tool.script.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.tool.script.config.SelectOption
import com.tool.script.executor.ExecutorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Danh sách ứng dụng cho type `applist` (docs/schema.md, mục 7): danh sách package do lệnh `get` của item in ra
 * (thường là `pm list packages ...`), tên và icon lấy từ PackageManager.
 * targetSdk 28 nên hệ thống không lọc tầm nhìn package (Android 11+), không cần quyền QUERY_ALL_PACKAGES.
 */
object InstalledApps {

    /** Cỡ ảnh icon vẽ ra (px): đủ nét cho ô 40dp ở máy mật độ cao mà không tốn bộ nhớ. */
    private const val ICON_PX = 128

    /** Thời gian tối đa mặc định cho lệnh `get` của applist (item khai báo `timeout` thì dùng giá trị đó). */
    const val DEFAULT_TIMEOUT_SEC = 20

    private val icons = LruCache<String, ImageBitmap>(300)

    /** Tên package hợp lệ: chữ, số, `_`, `.`. */
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")

    /** Kết quả [load]: [error] khác null nghĩa là lệnh `get` không chạy được (mô tả ngắn để báo người dùng). */
    class LoadResult(val apps: List<SelectOption>, val error: String? = null)

    /**
     * Chạy [command] (lệnh `get` đã kèm biến của các item) qua backend shell của app (có root thì chạy bằng root) rồi đọc danh sách package.
     * Mỗi dòng đầu ra một package; chấp nhận cả dạng của `pm list packages`: `package:<tên>`, `package:/đường/dẫn.apk=<tên>` (`-f`),
     * và phần phụ phía sau như ` uid:1000` (`-U`). Dòng không ra tên package hợp lệ bị bỏ qua. Bỏ chính Tool Script, bỏ trùng.
     * Tên hiển thị lấy từ PackageManager (kể cả app đã gỡ còn dữ liệu); PackageManager không thấy thì hiện tên package. Sắp theo tên.
     */
    suspend fun load(context: Context, command: String, timeoutSec: Int): LoadResult {
        val result = try {
            ExecutorManager.current.exec(ShellText.silenceStderr(command), timeoutSec)
        } catch (e: Exception) {
            return LoadResult(emptyList(), e.message ?: e.toString())
        }
        if (!result.ok) {
            return LoadResult(emptyList(), if (result.timedOut) "timeout ${timeoutSec}s" else "exit ${result.code}")
        }
        val packages = result.output.lineSequence()
            .map { it.trim().removePrefix("package:").substringBefore(' ').substringAfterLast('=') }
            .filter { PACKAGE_NAME.matches(it) && it != context.packageName }
            .distinct()
            .toList()
        val apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            packages.map { pkg ->
                val label = try {
                    // MATCH_UNINSTALLED_PACKAGES: thấy cả app đã gỡ nhưng còn dữ liệu (pm list packages -u).
                    pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(pm).toString().ifBlank { pkg }
                } catch (_: Exception) {
                    pkg
                }
                SelectOption(label, pkg, app = true)
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
        return LoadResult(apps)
    }

    /** Tên hiển thị của một package, null nếu app không còn được cài. */
    fun label(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString().ifBlank { null }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Icon đã nạp sẵn trong bộ nhớ đệm, null nếu chưa có. */
    fun cachedIcon(pkg: String): ImageBitmap? = icons.get(pkg)

    /** Nạp icon của [pkg] (chạy ở luồng IO, có đệm); null nếu app không còn hoặc không đọc được icon. */
    suspend fun icon(context: Context, pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                    .also { icons.put(pkg, it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}
