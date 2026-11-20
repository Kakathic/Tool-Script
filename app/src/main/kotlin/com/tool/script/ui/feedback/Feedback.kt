package com.tool.script.ui.feedback

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tool.script.R
import com.tool.script.config.Item
import com.tool.script.config.ShellMode
import com.tool.script.executor.ExecResult
import com.tool.script.util.copyToClipboard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Hiển thị kết quả chạy lệnh theo `shell`:
//   log (mặc định) = bảng log đầy đủ | live = bảng log hiện từng dòng ngay khi lệnh in ra | hidden = ẩn, lỗi thì toast
//   banner = thông báo ngắn trong app | notice = thông báo hệ thống (xin POST_NOTIFICATIONS khi dùng tới)

/**
 * Một lần chạy lệnh để hiện trong bảng log. [initialResult] null = đang chạy (live): các dòng được thêm dần
 * bằng [append], xong thì gọi [finish]. Có sẵn kết quả thì là log tĩnh.
 */
class LogEntry(val title: String, val command: String, initialResult: ExecResult? = null) {
    private val lines = mutableStateListOf<String>()
    var result by mutableStateOf(initialResult)
        private set

    val running: Boolean get() = result == null

    val text: String
        get() = buildString {
            appendLine("$ $command")
            val r = result
            if (r == null) {
                lines.forEach { appendLine(it) }
                append("[đang chạy...]")
            } else {
                if (r.combined.isNotBlank()) appendLine(r.combined)
                append(
                    when {
                        r.timedOut -> "[hết thời gian chờ]"
                        r.ok -> "[xong, mã 0]"
                        else -> "[lỗi, mã ${r.code}]"
                    },
                )
            }
        }

    fun append(line: String) {
        lines += line
        while (lines.size > MAX_LIVE_LINES) lines.removeAt(0)
    }

    fun finish(result: ExecResult) {
        this.result = result
    }

    private companion object {
        const val MAX_LIVE_LINES = 2000
    }
}

class FeedbackHost(private val context: Context) {
    var log by mutableStateOf<LogEntry?>(null)
    var banner by mutableStateOf<String?>(null)

    /** Gắn bởi [rememberFeedbackHost]: mở hộp thoại xin quyền thông báo. */
    internal var requestNoticePermission: (() -> Unit)? = null
    private var pendingNotice: Pair<String, String>? = null

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /** Chế độ live: mở bảng log ngay, nơi gọi thêm dòng bằng [LogEntry.append] rồi [LogEntry.finish]. */
    fun startLive(title: String, command: String): LogEntry = LogEntry(title, command).also { log = it }

    fun report(item: Item, command: String, result: ExecResult) {
        val summary = summaryOf(item.title, result)
        when (item.shell) {
            ShellMode.Log -> log = LogEntry(item.title, command, result)
            ShellMode.Live -> Unit // đã hiện từng dòng qua startLive
            ShellMode.Hidden -> if (!result.ok) toast(summary)
            ShellMode.Banner -> banner = summary
            ShellMode.Notice -> notice(item.title, summary)
        }
    }

    private fun summaryOf(title: String, result: ExecResult): String = when {
        result.timedOut -> "$title: hết thời gian chờ"
        result.ok -> "$title: hoàn tất"
        else -> {
            val firstLine = result.stderr.lineSequence().firstOrNull { it.isNotBlank() }
            "$title: lỗi (mã ${result.code})" + (firstLine?.let { " - $it" } ?: "")
        }
    }

    fun notice(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val ask = requestNoticePermission
            if (ask == null) {
                banner = text
            } else {
                pendingNotice = title to text
                ask()
            }
            return
        }
        post(title, text)
    }

    internal fun onNoticePermissionResult(granted: Boolean) {
        val pending = pendingNotice ?: return
        pendingNotice = null
        if (granted) post(pending.first, pending.second) else banner = pending.second
    }

    private fun post(title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kết quả lệnh", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "tool_script_result"
    }
}

@Composable
fun rememberFeedbackHost(): FeedbackHost {
    val context = LocalContext.current
    val host = remember { FeedbackHost(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        host.onNoticePermissionResult(granted)
    }
    host.requestNoticePermission = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    return host
}

/** Bảng log đầy đủ của lệnh vừa chạy. */
@Composable
fun LogDialog(entry: LogEntry?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Giữ nội dung cuối cùng để hộp thoại không rỗng trong lúc đóng.
    var last by remember { mutableStateOf(entry) }
    if (entry != null) last = entry
    OverlayDialog(
        show = entry != null,
        title = last?.title,
        onDismissRequest = onDismiss,
    ) {
        val scroll = rememberScrollState()
        val text = last?.text.orEmpty()
        // Đang chạy live: tự cuộn xuống dòng mới nhất.
        LaunchedEffect(text) {
            if (last?.running == true) scroll.scrollTo(scroll.maxValue)
        }
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "Sao chép",
                    onClick = { copyToClipboard(context, "log", text) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(text = "Đóng", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Hộp thoại xác nhận trước khi chạy lệnh (trường `confirm`). */
@Composable
fun ConfirmDialog(message: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    OverlayDialog(
        show = message != null,
        title = "Xác nhận",
        summary = last,
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "Hủy", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = "Chạy",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/** Thông báo ngắn trong app (banner). */
@Composable
fun Banner(
    message: String,
    warning: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val colors = if (warning) {
        CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer,
            contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = colors.contentColor,
                style = MiuixTheme.textStyles.body2,
            )
            if (actionLabel != null) TextButton(text = actionLabel, onClick = onAction)
            TextButton(text = "Ẩn", onClick = onDismiss)
        }
    }
}
