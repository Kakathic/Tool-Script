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

/** Kết quả kiểm tra phiên bản mới. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val code: Long, val url: String) : UpdateState
    data object Failed : UpdateState
}

object UpdateChecker {
    private val CodeRegex = Regex("""versionCode\s*=\s*(\d+)""")

    /**
     * Đọc release [AppAbout.ReleaseTag] trên GitHub, lấy `versionCode=N` trong phần mô tả (workflow ghi vào)
     * rồi so với [current]. Lỗi mạng, hoặc release không có dòng đó thì trả [UpdateState.Failed].
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
                val latest = CodeRegex.find(json.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: return@runCatching UpdateState.Failed
                if (latest > current.code) UpdateState.Available(latest, json.optString("html_url", AppAbout.GithubUrl))
                else UpdateState.UpToDate
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
