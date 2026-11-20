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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Hiển thị kết quả chạy lệnh theo `shell`:
//   live (mặc định) = bảng log hiện từng dòng ngay khi lệnh in ra | hidden = ẩn, lỗi thì toast
//   banner = thông báo ngắn trong app | notice = thông báo hệ thống (xin POST_NOTIFICATIONS khi dùng tới)

/** Trạng thái của một lần chạy lệnh, hiện thành nhãn trên bảng log. */
enum class LogStatus { Running, Done, Failed, TimedOut }

/**
 * Một lần chạy lệnh để hiện trong bảng log. [initialResult] null = đang chạy (live): các dòng được thêm dần
 * bằng [append], xong thì gọi [finish]. Có sẵn kết quả thì là log tĩnh.
 * [output] chỉ gồm phần lệnh in ra; trạng thái và mã thoát nằm riêng ([status], [exitCode]).
 */
class LogEntry(val title: String, val command: String, initialResult: ExecResult? = null) {
    private val lines = mutableStateListOf<String>()
    var result by mutableStateOf(initialResult)
        private set

    val running: Boolean get() = result == null

    val status: LogStatus
        get() {
            val r = result ?: return LogStatus.Running
            return when {
                r.timedOut -> LogStatus.TimedOut
                r.ok -> LogStatus.Done
                else -> LogStatus.Failed
            }
        }

    /** Mã thoát của lệnh; null khi đang chạy hoặc hết thời gian chờ. */
    val exitCode: Int? get() = result?.takeIf { !it.timedOut }?.code

    val output: String
        get() {
            val r = result
            return if (r == null) lines.joinToString("\n") else r.combined.trimEnd('\n', '\r')
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

    /** Tăng mỗi lần hiện banner, để cùng một nội dung hiện lại vẫn tính lại thời gian. */
    var bannerToken by mutableIntStateOf(0)
        private set

    fun showBanner(message: String) {
        banner = message
        bannerToken++
    }

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
            ShellMode.Live -> Unit // đã hiện từng dòng qua startLive
            ShellMode.Hidden -> if (!result.ok) toast(summary)
            ShellMode.Banner -> showBanner(summary)
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
                showBanner(text)
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
        if (granted) post(pending.first, pending.second) else showBanner(pending.second)
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

/** Bảng log của lệnh vừa chạy: nhãn trạng thái + mã thoát ở trên, phần lệnh in ra trong khung riêng, nút ở dưới. */
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
        val output = last?.output.orEmpty()
        // Đang chạy live: tự cuộn xuống dòng mới nhất.
        LaunchedEffect(output) {
            if (last?.running == true) scroll.scrollTo(scroll.maxValue)
        }
        Column {
            LogStatusRow(last)
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .heightIn(max = 320.dp)
                    .verticalScroll(scroll)
                    .padding(12.dp),
            ) {
                if (output.isBlank()) {
                    Text(
                        text = if (last?.running == false) "Không có đầu ra" else "Đang chờ đầu ra...",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "Sao chép",
                    onClick = { copyToClipboard(context, "log", output) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "Đóng",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** Hàng trạng thái trên bảng log: nhãn bo tròn (đang chạy / xong / lỗi / hết giờ) bên trái, mã thoát bên phải. */
@Composable
private fun LogStatusRow(entry: LogEntry?) {
    val status = entry?.status ?: LogStatus.Running
    val label: String
    val container: Color
    val content: Color
    when (status) {
        LogStatus.Running -> {
            label = "Đang chạy..."
            container = MiuixTheme.colorScheme.primaryContainer
            content = MiuixTheme.colorScheme.onPrimaryContainer
        }
        LogStatus.Done -> {
            label = "Xong"
            container = MiuixTheme.colorScheme.primaryContainer
            content = MiuixTheme.colorScheme.onPrimaryContainer
        }
        LogStatus.Failed -> {
            label = "Lỗi"
            container = MiuixTheme.colorScheme.errorContainer
            content = MiuixTheme.colorScheme.onErrorContainer
        }
        LogStatus.TimedOut -> {
            label = "Hết thời gian chờ"
            container = MiuixTheme.colorScheme.errorContainer
            content = MiuixTheme.colorScheme.onErrorContainer
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        entry?.exitCode?.let { code ->
            Text(
                text = "Mã thoát $code",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
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
    /** Khác null: hiện thanh tiến trình thời gian còn lại (1 → 0) ở cuối banner. */
    progress: Float? = null,
    /** null = không có nút "Ẩn" (banner tự ẩn khi hết giờ). */
    onDismiss: (() -> Unit)? = null,
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
    // Có nút bên phải thì lề phải 8dp (nút tự có đệm), không có nút thì 16dp cho cân với lề trái.
    val hasButtons = actionLabel != null || onDismiss != null
    val endPadding = if (hasButtons) 8.dp else 16.dp
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = endPadding, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = colors.contentColor,
                style = MiuixTheme.textStyles.body2,
            )
            if (actionLabel != null) TextButton(text = actionLabel, onClick = onAction)
            if (onDismiss != null) TextButton(text = "Ẩn", onClick = onDismiss)
        }
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(end = if (hasButtons) 8.dp else 0.dp),
                // Nền thanh sáng (trắng mờ) thay vì màu tối mặc định.
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    backgroundColor = Color.White.copy(alpha = 0.6f),
                ),
            )
        }
    }
}

/** Thời gian hiện mặc định của banner shell (ms). */
const val BANNER_MILLIS = 5_000

/**
 * Banner shell = "banner": hiện đè lên cả thanh trên, trượt xuống khi hiện và trượt lên khi ẩn,
 * có thanh tiến trình đếm ngược [BANNER_MILLIS] rồi tự ẩn. Đặt trong Box phủ toàn màn hình.
 */
@Composable
fun BannerOverlay(feedback: FeedbackHost, modifier: Modifier = Modifier) {
    val message = feedback.banner
    // Giữ nội dung cuối để banner không rỗng trong lúc trượt lên.
    var last by remember { mutableStateOf(message.orEmpty()) }
    if (message != null) last = message

    val progress = remember { Animatable(1f) }
    LaunchedEffect(feedback.bannerToken, message != null) {
        if (message != null) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(BANNER_MILLIS, easing = LinearEasing))
            feedback.banner = null
        }
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically(tween(BANNER_SLIDE_MILLIS)) { -it } + fadeIn(tween(BANNER_SLIDE_MILLIS)),
        exit = slideOutVertically(tween(BANNER_SLIDE_MILLIS)) { -it } + fadeOut(tween(BANNER_SLIDE_MILLIS)),
    ) {
        Banner(
            message = last,
            warning = false,
            // statusBarsPadding nằm trong phần trượt để banner trượt hẳn ra khỏi mép trên màn hình.
            modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            progress = progress.value,
        )
    }
}

private const val BANNER_SLIDE_MILLIS = 300
