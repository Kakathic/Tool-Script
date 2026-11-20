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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tool.script.R
import coil3.compose.AsyncImage
import com.tool.script.config.Item
import com.tool.script.config.ShellMode
import com.tool.script.executor.ExecResult
import com.tool.script.util.copyToClipboard
import com.tool.script.util.ShellText
import com.tool.script.util.Util
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme


enum class LogStatus { Running, Done, Failed, TimedOut }

class LogEntry(
    val title: String,
    val command: String,
    initialResult: ExecResult? = null,
    startFull: Boolean = false,
) {
    private val lines = mutableStateListOf<String>()
    var result by mutableStateOf(initialResult)
        private set

    var progress by mutableStateOf<Float?>(null)
        private set

    var full by mutableStateOf(startFull)

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

    val exitCode: Int? get() = result?.takeIf { !it.timedOut }?.code

    val output: String
        get() {
            val r = result
            if (r == null) return lines.joinToString("\n")
            return r.combined.lineSequence()
                .filter { ShellText.parseProgress(it) == null }
                .joinToString("\n")
                .trimEnd('\n', '\r')
        }

    fun append(line: String) {
        val p = ShellText.parseProgress(line)
        if (p != null) {
            progress = p / 100f
            return
        }
        lines += line
        while (lines.size > MAX_LIVE_LINES) lines.removeAt(0)
    }

    fun finish(result: ExecResult) {
        if (result.ok) progress = 1f
        this.result = result
    }

    private companion object {
        const val MAX_LIVE_LINES = 2000
    }
}

class FeedbackHost(private val context: Context) {
    var log by mutableStateOf<LogEntry?>(null)
    var banner by mutableStateOf<String?>(null)

    var bannerToken by mutableIntStateOf(0)
        private set

    fun showBanner(message: String) {
        banner = message
        bannerToken++
    }

    internal var requestNoticePermission: (() -> Unit)? = null
    private var pendingNotice: Pair<String, String>? = null

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun startLive(title: String, command: String, full: Boolean = false): LogEntry =
        LogEntry(title, command, startFull = full).also { log = it }

    fun report(item: Item, command: String, result: ExecResult) {
        val summary = summaryOf(item.title, result)
        when (item.shell) {
            ShellMode.Live, ShellMode.Full -> Unit
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

@Composable
fun LogDialog(entry: LogEntry?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var last by remember { mutableStateOf(entry) }
    if (entry != null) last = entry
    OverlayDialog(
        show = entry != null && !entry.full,
        title = last?.title,
        onDismissRequest = onDismiss,
    ) {
        val output = last?.output.orEmpty()
        Column {
            LogStatusRow(last)
            LogProgress(last)
            Spacer(Modifier.height(12.dp))
            LogBody(last, output, Modifier.heightIn(max = 320.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "Sao chép",
                    onClick = { copyToClipboard(context, "log", output) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "Toàn màn hình",
                    onClick = { last?.full = true },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "Đóng",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

// Log live toàn màn hình: mở khi shell = "full" hoặc bấm "Toàn màn hình"; Back của máy hoặc "Đóng" thì đóng hẳn.
@Composable
fun LogFullScreen(entry: LogEntry?, onDismiss: () -> Unit) {
    if (entry == null || !entry.full) return
    val context = LocalContext.current
    val output = entry.output
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Text(
                text = entry.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            LogStatusRow(entry)
            LogProgress(entry)
            Spacer(Modifier.height(12.dp))
            LogBody(entry, output, Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "Sao chép",
                    onClick = { copyToClipboard(context, "log", output) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "Thu nhỏ",
                    onClick = { entry.full = false },
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

// Khung chứa phần lệnh in ra, tự cuộn xuống dòng mới nhất khi đang chạy.
@Composable
private fun LogBody(entry: LogEntry?, output: String, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(output) {
        if (entry?.running == true) scroll.scrollTo(scroll.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .verticalScroll(scroll)
            .padding(12.dp),
    ) {
        if (output.isBlank()) {
            Text(
                text = if (entry?.running == false) "Không có đầu ra" else "Đang chờ đầu ra...",
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
}

// Thanh tiến trình: đang chạy chưa có "@progress N" thì chạy vô định, có thì hiện đúng %; xong không lỗi thì đầy.
@Composable
private fun LogProgress(entry: LogEntry?) {
    if (entry == null) return
    val value = entry.progress
    if (!entry.running && value == null) return
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(progress = value, modifier = Modifier.fillMaxWidth())
}

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
        val rightText = entry?.exitCode?.let { "Mã thoát $it" }
            ?: entry?.takeIf { it.running }?.progress?.let { "${(it * 100).roundToInt()}%" }
        rightText?.let { text ->
            Text(
                text = text,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
            )
        }
    }
}

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

@Composable
fun Banner(
    message: String,
    warning: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    progress: Float? = null,
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
    val hasButtons = actionLabel != null || onDismiss != null
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        insideMargin = PaddingValues(start = 14.dp, top = 12.dp, end = if (hasButtons) 8.dp else 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = Util.iconModel(if (warning) "icons/banner_warning.svg" else "icons/banner_info.svg"),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(colors.contentColor),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = colors.contentColor,
                style = MiuixTheme.textStyles.body2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionLabel != null) TextButton(text = actionLabel, onClick = onAction)
            if (onDismiss != null) TextButton(text = "Ẩn", onClick = onDismiss)
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (hasButtons) 6.dp else 0.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.6f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
    }
}

const val BANNER_MILLIS = 5_000

@Composable
fun BannerOverlay(feedback: FeedbackHost, modifier: Modifier = Modifier) {
    val message = feedback.banner
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
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp)),
            progress = progress.value,
        )
    }
}

private const val BANNER_SLIDE_MILLIS = 300
