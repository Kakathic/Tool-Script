package com.tool.script.ui.feedback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tool.script.MainActivity
import com.tool.script.R
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Log đã ẩn: lệnh vẫn chạy nền, mọi dòng log gửi lên thông báo Android (tiêu đề = tên tính năng, nội dung = log của tính năng đó).
 * Chạm vào thông báo thì mở lại đúng màn hình log đó (xem [FeedbackHost.showHiddenLog]).
 *
 * Đặt ở cấp tiến trình (không gắn vào màn hình) để Activity dựng lại (xoay màn hình, mở từ thông báo) vẫn tìm thấy log đã ẩn.
 * Lệnh vẫn chạy trong coroutine của màn hình chính như cũ, nên chỉ chạy nền được khi ứng dụng còn sống
 * (ra màn hình chính, mở app khác, chạy tính năng khác đều không sao); thoát hẳn ứng dụng thì lệnh bị hủy và thông báo chốt "Đã hủy".
 * Chỉ dùng trên luồng chính.
 */
object LogCenter {
    /** Extra của Intent mở từ thông báo: mã [LogEntry.id] của log cần mở lại. */
    const val EXTRA_LOG_ID = "tool_script.log_id"

    private const val CHANNEL_ID = "tool_script_log"

    /** Số log đã ẩn tối đa còn giữ lại; quá thì bỏ cái đã xong cũ nhất (log đang chạy không bao giờ bị bỏ). */
    private const val MAX_KEPT = 12

    /** Khoảng cách tối thiểu giữa hai lần cập nhật thông báo (Android hạn chế cập nhật quá dày). */
    private const val POST_INTERVAL_MS = 800L

    /** Log đang chạy mà không có gì mới vẫn làm mới thông báo mỗi chừng này, để thông báo không tự biến mất (xem [TIMEOUT_MS]). */
    private const val KEEPALIVE_MS = 30_000L

    /** Thông báo của log đang chạy tự gỡ sau chừng này nếu không được làm mới: ứng dụng bị hệ thống tắt thì thông báo không treo mãi. */
    private const val TIMEOUT_MS = 120_000L

    /** Số ký tự cuối của log đưa vào thông báo (thông báo quá dài hệ thống sẽ cắt hoặc bỏ). */
    private const val TAIL_CHARS = 3000

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hidden = LinkedHashMap<Int, LogEntry>()

    fun init(context: Context) {
        appContext = context.applicationContext
        LogFiles.init(context)
    }

    /** Ẩn [entry]: giữ lại để mở sau và bắt đầu gửi log lên thông báo. */
    fun hide(entry: LogEntry) {
        hidden.remove(entry.id)
        hidden[entry.id] = entry
        trim()
        startNotifying(entry)
    }

    /** Có log đang ẩn không (để báo khi chưa cấp quyền thông báo: không có thông báo thì không mở lại được). */
    fun hasHidden(): Boolean = hidden.isNotEmpty()

    /** Lấy log đã ẩn ra để mở lại: bỏ khỏi danh sách ẩn và gỡ thông báo của nó. Không có (đã bị bỏ, hoặc ứng dụng đã khởi động lại) thì null. */
    fun take(id: Int): LogEntry? {
        val entry = hidden.remove(id) ?: return null
        entry.notifyJob?.cancel()
        entry.notifyJob = null
        appContext?.getSystemService(NotificationManager::class.java)?.cancel(entry.id)
        return entry
    }

    private fun trim() {
        while (hidden.size > MAX_KEPT) {
            val oldest = hidden.values.firstOrNull { !it.running } ?: return
            hidden.remove(oldest.id)
            appContext?.getSystemService(NotificationManager::class.java)?.cancel(oldest.id)
        }
    }

    /** Vòng cập nhật thông báo của [entry]: gửi khi log có dòng mới / đổi tiến trình, và một lần chốt khi lệnh kết thúc. */
    private fun startNotifying(entry: LogEntry) {
        if (entry.notifyJob?.isActive == true) return
        entry.notifyJob = scope.launch {
            var lastRevision = -1
            var lastProgress: Float? = null
            var lastPost = 0L
            while (entry.running) {
                val now = SystemClock.elapsedRealtime()
                val changed = entry.revision != lastRevision || entry.progress != lastProgress
                if (lastPost == 0L || (changed && now - lastPost >= POST_INTERVAL_MS) || now - lastPost >= KEEPALIVE_MS) {
                    lastRevision = entry.revision
                    lastProgress = entry.progress
                    lastPost = now
                    post(entry)
                }
                delay(POST_INTERVAL_MS)
            }
            // Lệnh đã xong (hoặc đã hủy): chốt thông báo với kết quả cuối, bỏ thanh tiến trình, vuốt được và chạm là tự gỡ.
            post(entry)
        }
    }

    private fun hasPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun post(entry: LogEntry) {
        val context = appContext ?: return
        // Chưa có quyền thông báo (Android 13+): bỏ qua, có quyền rồi thì lần cập nhật sau sẽ gửi.
        if (!hasPermission(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            // Mức thấp: cập nhật liên tục không kêu / không rung / không hiện nổi.
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Log tính năng chạy nền", NotificationManager.IMPORTANCE_LOW))
        }
        val running = entry.running
        val body = entry.tail(TAIL_CHARS).ifBlank { if (running) "Đang chạy..." else "Không có đầu ra" }
        val lastLine = body.lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty()

        val open = Intent(context, MainActivity::class.java).apply {
            // Đưa Activity đang có (nếu còn) lên trước thay vì dựng thêm một cái.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_LOG_ID, entry.id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(context, entry.id, open, flags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entry.title)
            .setContentText(lastLine.ifEmpty { body })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(statusLabel(entry))
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(running)
            .setAutoCancel(!running)
        if (running) {
            val progress = entry.progress
            if (progress != null) builder.setProgress(100, (progress * 100).roundToInt().coerceIn(0, 100), false)
            else builder.setProgress(0, 0, true)
            builder.setTimeoutAfter(TIMEOUT_MS)
        }
        nm.notify(entry.id, builder.build())
    }

    private fun statusLabel(entry: LogEntry): String = when (entry.status) {
        LogStatus.Running -> "Đang chạy"
        LogStatus.Done -> "Xong"
        LogStatus.Failed -> "Lỗi (mã ${entry.exitCode ?: "?"})"
        LogStatus.TimedOut -> "Hết thời gian chờ"
        LogStatus.Cancelled -> "Đã hủy"
    }
}
