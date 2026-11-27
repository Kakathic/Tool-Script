package com.tool.script.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.pm.PackageInfoCompat
import com.tool.script.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Thông tin hiển thị trong thẻ giới thiệu của hộp thoại Cài đặt. Đổi tại đây khi cần. */
object AppAbout {
    const val Author = "Kakathic"

    /** Repo GitHub dạng "chủ/tên-repo": dùng cho link GitHub và kiểm tra phiên bản mới. */
    const val GithubRepo = "Kakathic/Tool-Script"
    const val GithubUrl = "https://github.com/$GithubRepo"

    /** Liên kết khác (để trống thì ẩn dòng này). */
    const val OtherUrl = "https://github.com/Kakathic"

    /** Release trên GitHub chứa bản mới nhất (tag trùng tag_name trong .github/workflows/build.yml). */
    const val ReleaseTag = "V1"
}

/** Phiên bản đang cài: [name] lấy từ versionName, [code] từ versionCode (số lần chạy workflow). */
class AppVersion(val name: String, val code: Long) {
    val display: String get() = "$name ($code)"

    companion object {
        fun of(context: Context): AppVersion {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return AppVersion(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
        }
    }
}

/** Trạng thái kiểm tra / tải / cài phiên bản mới. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState

    /** Có bản mới: [url] là trang release, [apkUrl] là liên kết tải APK (null nếu release không có APK), [notes] là các mục nhật ký thay đổi mới nhất. */
    data class Available(val code: Long, val url: String, val apkUrl: String?, val notes: List<ChangelogEntry>) : UpdateState

    /** Đang tải APK: [progress] từ 0 đến 1. */
    data class Downloading(val update: Available, val progress: Float) : UpdateState

    /** Đã tải xong, chờ cài ([file] nằm trong cache/updates). */
    data class Downloaded(val update: Available, val file: File) : UpdateState

    /** Tải lỗi (mất mạng, APK hỏng hoặc không đúng ứng dụng). */
    data class DownloadFailed(val update: Available) : UpdateState
}

/** Thông tin bản mới nếu đang có (kể cả khi đang tải, đã tải hoặc tải lỗi), ngược lại null. */
val UpdateState.pending: UpdateState.Available?
    get() = when (this) {
        is UpdateState.Available -> this
        is UpdateState.Downloading -> update
        is UpdateState.Downloaded -> update
        is UpdateState.DownloadFailed -> update
        else -> null
    }

object UpdateChecker {
    private val CodeRegex = Regex("""versionCode\s*=\s*(\d+)""")

    /**
     * Đọc release [AppAbout.ReleaseTag] trên GitHub, lấy `versionCode=N` trong phần mô tả (workflow ghi vào)
     * rồi so với [current]. Phần mô tả còn chứa CHANGELOG.md (workflow ghép vào) để hiện nhật ký thay đổi. Lỗi mạng,
     * hoặc release không có dòng versionCode thì trả [UpdateState.Failed].
     */
    suspend fun check(current: AppVersion): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val api = "https://api.github.com/repos/${AppAbout.GithubRepo}/releases/tags/${AppAbout.ReleaseTag}"
            val conn = URL(api).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                if (conn.responseCode != 200) return@runCatching UpdateState.Failed
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val body = json.optString("body")
                val latest = CodeRegex.find(body)?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@runCatching UpdateState.Failed
                if (latest <= current.code) return@runCatching UpdateState.UpToDate
                var apk: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apk = asset.optString("browser_download_url").ifBlank { null }
                            break
                        }
                    }
                }
                UpdateState.Available(
                    code = latest,
                    url = json.optString("html_url", AppAbout.GithubUrl),
                    apkUrl = apk,
                    notes = Changelog.parse(body).take(Changelog.MaxShown),
                )
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(UpdateState.Failed)
    }
}

/** Mở [url] bằng trình duyệt / app mặc định; không có app nào mở được thì báo bằng Toast. */
fun openUrl(context: Context, url: String) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
}
