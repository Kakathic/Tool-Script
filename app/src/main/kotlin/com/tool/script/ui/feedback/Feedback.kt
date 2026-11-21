package com.tool.script.ui.feedback

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    /** Dừng lệnh đang chạy của log này (do nơi chạy lệnh gắn vào); null nếu không hủy được. */
    var cancelAction: (() -> Unit)? = null

    val running: Boolean get() = result == null

    /** Hủy lệnh nếu còn đang chạy. */
    fun cancel() {
        if (running) cancelAction?.invoke()
    }

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
    var bannerWarning by mutableStateOf(false)

    /** true khi banner đang báo "Đang chạy script": thanh chạy vô hạn, chưa đếm ngược. */
    var bannerRunning by mutableStateOf(false)
        private set

    private var activeRun by mutableIntStateOf(0)
    private var lastRun = 0
    private var runJob: Job? = null

    /** Có lệnh `banner` / `notice` đang chạy hoặc banner còn hiện: các item dùng hai kiểu này bị khóa cho tới khi banner biến mất. */
    val busy: Boolean get() = activeRun != 0 || banner != null

    /** Bắt đầu một lượt chạy; trả về mã lượt để [attachJob] / [endRun] nhận ra đúng lượt. */
    fun beginRun(): Int {
        activeRun = ++lastRun
        banner = RUNNING_TEXT
        bannerWarning = false
        bannerRunning = true
        bannerToken++
        return activeRun
    }

    fun attachJob(id: Int, job: Job) {
        if (activeRun == id) runJob = job
    }

    fun endRun(id: Int) {
        if (activeRun != id) return
        activeRun = 0
        runJob = null
        if (bannerRunning) banner = null
    }

    /** Hủy lượt đang chạy: dừng coroutine, ẩn banner và mở khóa ngay (kết quả của lượt bị bỏ). */
    fun cancelRun() {
        if (activeRun == 0) return
        runJob?.cancel()
        runJob = null
        activeRun = 0
        banner = null
        bannerRunning = false
        toast("Đã hủy")
    }

    var bannerToken by mutableIntStateOf(0)
        private set

    fun showBanner(message: String, warning: Boolean = false) {
        banner = message
        bannerWarning = warning
        bannerRunning = false
        bannerToken++
    }

    internal var requestNoticePermission: (() -> Unit)? = null
    private var pendingNotice: Triple<String, String, Boolean>? = null

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun startLive(title: String, command: String, full: Boolean = false): LogEntry =
        LogEntry(title, command, startFull = full).also { log = it }

    /** Nút Hủy của log toàn màn hình: dừng lệnh đang chạy rồi đóng log. */
    fun cancelLog() {
        val entry = log ?: return
        entry.cancel()
        log = null
        toast("Đã hủy")
    }

    fun report(item: Item, command: String, result: ExecResult) {
        val summary = summaryOf(item.title, result)
        when (item.shell) {
            ShellMode.Live, ShellMode.Full -> Unit
            ShellMode.Hidden -> if (!result.ok) toast(summary)
            ShellMode.Banner -> showBanner(summary, warning = !result.ok)
            ShellMode.Notice -> {
                if (bannerRunning) banner = null
                notice(item.title, summary, warning = !result.ok)
            }
        }
    }

    private fun summaryOf(title: String, result: ExecResult): String = when {
        result.timedOut -> withOutput("$title: hết thời gian chờ", result)
        result.ok -> outputOf(result.stdout) ?: "$title: hoàn tất"
        else -> withOutput("$title: lỗi (mã ${result.code})", result)
    }

    /** Tiêu đề lỗi, xuống dòng rồi tới toàn bộ phần lệnh in ra (stdout và stderr) nếu có. */
    private fun withOutput(head: String, result: ExecResult): String =
        outputOf(result.combined)?.let { "$head\n$it" } ?: head

    /** Phần lệnh in ra để làm nội dung banner / thông báo (bỏ dòng `@progress`); null nếu không có gì. */
    private fun outputOf(text: String): String? =
        text.lineSequence()
            .filterNot { it.startsWith("@progress") }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.take(OUTPUT_MAX_CHARS)

    fun notice(title: String, text: String, warning: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val ask = requestNoticePermission
            if (ask == null) {
                showBanner(text, warning)
            } else {
                pendingNotice = Triple(title, text, warning)
                ask()
            }
            return
        }
        post(title, text)
    }

    internal fun onNoticePermissionResult(granted: Boolean) {
        val pending = pendingNotice ?: return
        pendingNotice = null
        if (granted) post(pending.first, pending.second) else showBanner(pending.second, pending.third)
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

/** Lề ngoài của log toàn màn hình và khoảng cách giữa các khối (cùng giá trị với màn hình lỗi). */
private val LogMargin = 12.dp

private val LogYellow = Color(0xFFFFC107)
private val LogGreen = Color(0xFF2EA043)
private val LogRed = Color(0xFFD93025)

// Log live toàn màn hình: mở khi shell = "full" hoặc bấm "Toàn màn hình". Bố cục như màn hình lỗi (crash/CrashActivity):
// hàng tiêu đề có nút Thu nhỏ, khung log chọn / sao chép được, hàng nút Chia sẻ và Hủy / Thoát ở dưới.
// Nút cuối: đang chạy là Hủy (vàng, dừng lệnh), xong là Thoát (xanh), lỗi là "Kết thúc: <mã>" (đỏ). Back của máy thì đóng log, lệnh vẫn chạy tiếp.
@Composable
fun LogFullScreen(entry: LogEntry?, onDismiss: () -> Unit, onCancel: () -> Unit) {
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
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = LogMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LogPillButton(text = "Thu nhỏ", onClick = { entry.full = false })
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = LogMargin, end = LogMargin, bottom = LogMargin),
                verticalArrangement = Arrangement.spacedBy(LogMargin),
            ) {
                Column {
                    LogStatusRow(entry)
                    // Chạy xong thì ẩn thanh tiến trình.
                    LogProgress(entry, hideWhenDone = true)
                }
                LogSelectableCard(entry, output, Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(LogMargin)) {
                    TextButton(
                        text = "Chia sẻ",
                        onClick = { shareText(context, entry.title, output.ifBlank { "(không có đầu ra)" }) },
                        modifier = Modifier.weight(1f),
                    )
                    val (label, buttonColor, labelColor) = when (entry.status) {
                        LogStatus.Running -> Triple("Hủy", LogYellow, Color(0xFF1F1F1F))
                        LogStatus.Done -> Triple("Thoát", LogGreen, Color.White)
                        LogStatus.Failed -> Triple("Kết thúc: ${entry.exitCode}", LogRed, Color.White)
                        LogStatus.TimedOut -> Triple("Kết thúc: hết giờ", LogRed, Color.White)
                    }
                    TextButton(
                        text = label,
                        onClick = if (entry.running) onCancel else onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(color = buttonColor, textColor = labelColor),
                    )
                }
            }
        }
    }
}

// Khung log của màn hình toàn màn hình: nhấn giữ để chọn chữ (thanh công cụ hệ thống có Sao chép), cuộn dọc và ngang,
// tự cuộn xuống dòng mới nhất khi đang chạy. Cùng kiểu với khung log của màn hình lỗi.
@Composable
private fun LogSelectableCard(entry: LogEntry, output: String, modifier: Modifier) {
    val vertical = rememberScrollState()
    LaunchedEffect(output) {
        if (entry.running) vertical.scrollTo(vertical.maxValue)
    }
    Card(modifier = modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        val primary = MiuixTheme.colorScheme.primary
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = primary,
                backgroundColor = primary.copy(alpha = 0.3f),
            ),
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vertical)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                if (output.isBlank()) {
                    Text(
                        text = if (entry.running) "Đang chờ đầu ra..." else "Không có đầu ra",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                } else {
                    Text(
                        text = output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Nút dạng viên thuốc ở hàng tiêu đề (cùng kiểu nút Chia sẻ của màn hình lỗi): chữ trong khung bo, nền xám nhạt. */
@Composable
private fun LogPillButton(text: String, onClick: () -> Unit) {
    val content = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(content.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = content)
    }
}

private fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Chia sẻ log")
    // Context không phải Activity thì bắt buộc cờ NEW_TASK.
    val hasActivity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.any { it is Activity }
    if (!hasActivity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
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
// [hideWhenDone] = true: chạy xong (kể cả lỗi, hết giờ) thì ẩn hẳn thanh.
@Composable
private fun LogProgress(entry: LogEntry?, hideWhenDone: Boolean = false) {
    if (entry == null) return
    if (hideWhenDone && !entry.running) return
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
    indeterminate: Boolean = false,
    maxLines: Int = 3,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = if (warning) {
        CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.defaultColors(
            // Banner thông tin: nền trung tính (không xanh), chữ theo màu chữ chính.
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
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
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasButtons) {
                // Các nút (Xem, Ẩn, Hủy...) cách chữ và cách nhau 8dp; Banner dùng chung cho thông báo script và lỗi cấu hình.
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (actionLabel != null) TextButton(text = actionLabel, onClick = onAction)
                    if (onDismiss != null) TextButton(text = "Ẩn", onClick = onDismiss)
                }
            }
        }
        if (indeterminate) {
            Spacer(Modifier.height(10.dp))
            val start by rememberInfiniteTransition(label = "banner-run").animateFloat(
                initialValue = 0f,
                targetValue = 1f - INDETERMINATE_WIDTH,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
                label = "banner-run-start",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (hasButtons) 6.dp else 0.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.contentColor.copy(alpha = 0.15f)),
            ) {
                Spacer(Modifier.weight(start + 0.001f))
                Box(
                    Modifier
                        .weight(INDETERMINATE_WIDTH)
                        .height(3.dp)
                        .background(MiuixTheme.colorScheme.primary),
                )
                Spacer(Modifier.weight(1f - INDETERMINATE_WIDTH - start + 0.001f))
            }
        } else if (progress != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (hasButtons) 6.dp else 0.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.contentColor.copy(alpha = 0.15f)),
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

private const val INDETERMINATE_WIDTH = 0.35f

private const val OUTPUT_MAX_CHARS = 300

private const val RUNNING_TEXT = "Đang chạy script…"

@Composable
fun BannerOverlay(feedback: FeedbackHost, modifier: Modifier = Modifier) {
    val message = feedback.banner
    var last by remember { mutableStateOf(message.orEmpty()) }
    var lastWarning by remember { mutableStateOf(false) }
    var lastRunning by remember { mutableStateOf(false) }
    if (message != null) {
        last = message
        lastWarning = feedback.bannerWarning
        lastRunning = feedback.bannerRunning
    }

    val progress = remember { Animatable(1f) }
    val running = feedback.bannerRunning
    LaunchedEffect(feedback.bannerToken, message != null, running) {
        if (message != null && !running) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(BANNER_MILLIS, easing = LinearEasing))
            feedback.banner = null
        }
    }

    // Vuốt ngang: đang chạy thì hủy script, đã xong thì ẩn banner. Banner mới hiện thì về lại vị trí giữa.
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedback.bannerToken) { offsetX.snapTo(0f) }
    val dismiss: () -> Unit = {
        if (feedback.bannerRunning) feedback.cancelRun() else feedback.banner = null
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically(tween(BANNER_SLIDE_MILLIS)) { -it } + fadeIn(tween(BANNER_SLIDE_MILLIS)),
        exit = slideOutVertically(tween(BANNER_SLIDE_MILLIS)) { -it } + fadeOut(tween(BANNER_SLIDE_MILLIS)),
    ) {
        Banner(
            message = last,
            warning = lastWarning,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (abs(offsetX.value) / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f) }
                .onSizeChanged { widthPx = it.width }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val width = widthPx.coerceAtLeast(1)
                            if (abs(offsetX.value) > width * SWIPE_DISMISS_FRACTION) {
                                val target = if (offsetX.value > 0f) width.toFloat() else -width.toFloat()
                                scope.launch {
                                    offsetX.animateTo(target, tween(SWIPE_OUT_MILLIS))
                                    dismiss()
                                }
                            } else {
                                scope.launch { offsetX.animateTo(0f) }
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                },
            progress = progress.value,
            indeterminate = lastRunning,
            maxLines = BANNER_OUTPUT_MAX_LINES,
            actionLabel = if (lastRunning) "Hủy" else null,
            onAction = dismiss,
            onDismiss = if (lastRunning) null else dismiss,
        )
    }
}

private const val BANNER_OUTPUT_MAX_LINES = 8

private const val SWIPE_DISMISS_FRACTION = 0.3f

private const val SWIPE_OUT_MILLIS = 150

private const val BANNER_SLIDE_MILLIS = 300
