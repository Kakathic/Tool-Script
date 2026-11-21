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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme


enum class LogStatus { Running, Done, Failed, TimedOut, Cancelled }

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

    /**
     * Hủy lệnh nếu còn đang chạy: dừng lệnh và chốt log ngay với phần đã in ra (không đợi lệnh kết thúc,
     * vì backend root không dừng được ngay). Log vẫn mở, người dùng tự thoát.
     */
    fun cancel() {
        if (!running) return
        cancelled = true
        cancelAction?.invoke()
        result = ExecResult(code = -1, stdout = lines.joinToString("\n"), stderr = "")
    }

    /** true khi người dùng bấm Hủy: log chốt lại ngay, giữ phần đã in ra. */
    var cancelled by mutableStateOf(false)
        private set

    val status: LogStatus
        get() {
            val r = result ?: return LogStatus.Running
            return when {
                cancelled -> LogStatus.Cancelled
                r.timedOut -> LogStatus.TimedOut
                r.ok -> LogStatus.Done
                else -> LogStatus.Failed
            }
        }

    val exitCode: Int? get() = result?.takeIf { !it.timedOut && !cancelled }?.code

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
        // Đã bị hủy (log đã chốt): bỏ kết quả muộn của lệnh.
        if (this.result != null) return
        if (result.ok) progress = 1f
        this.result = result
    }

    private companion object {
        const val MAX_LIVE_LINES = 2000
    }
}

class FeedbackHost(private val context: Context) {
    var log by mutableStateOf<LogEntry?>(null)
    /** Danh sách banner đang hiện, xếp chồng từ trên xuống (mỗi lượt chạy `banner` là một banner riêng). */
    val banners = mutableStateListOf<BannerEntry>()
    private var lastRun = 0

    /** Item này có lượt chạy `banner` / `notice` chưa xong không: item đó bị khóa, các item khác vẫn bấm được. */
    fun isRunning(item: Item): Boolean = banners.any { it.item === item && it.running }

    /** Bắt đầu một lượt chạy: thêm banner "Đang chạy script…" của riêng [item]; trả về mã lượt cho [attachJob] / [endRun] / [report]. */
    fun beginRun(item: Item): Int {
        banners.filter { it.item === item && !it.running }.forEach { dismissBanner(it) }
        val entry = BannerEntry(++lastRun, item, RUNNING_TEXT, warning = false, running = true)
        banners.add(entry)
        return entry.id
    }

    fun attachJob(id: Int, job: Job) {
        banners.firstOrNull { it.id == id }?.job = job
    }

    /** Đổi banner của lượt [id] sang kết quả và bắt đầu đếm ngược; bỏ qua nếu banner đã bị hủy / ẩn. */
    private fun finishBanner(id: Int, message: String, warning: Boolean) {
        val entry = banners.firstOrNull { it.id == id } ?: return
        if (entry.closing) return
        entry.message = message
        entry.warning = warning
        entry.running = false
        entry.token++
    }

    /** Lượt chạy kết thúc (kể cả lỗi / bị hủy): banner nào còn "đang chạy" thì ẩn đi. */
    fun endRun(id: Int) {
        val entry = banners.firstOrNull { it.id == id } ?: return
        if (entry.running) dismissBanner(entry)
    }

    /** Hủy lượt đang chạy của [entry]: dừng coroutine, ẩn banner và mở khóa item ngay (kết quả của lượt bị bỏ). */
    fun cancelBanner(entry: BannerEntry) {
        if (entry.closing || !entry.running) return
        entry.job?.cancel()
        entry.job = null
        dismissBanner(entry)
        toast("Đã hủy")
    }

    /** Ẩn banner: chạy hiệu ứng thu lại rồi mới gỡ khỏi danh sách. Banner coi như hết "đang chạy" ngay. */
    fun dismissBanner(entry: BannerEntry) {
        entry.running = false
        entry.closing = true
    }

    /** Banner độc lập (không gắn item), ví dụ báo khi không có quyền gửi thông báo. */
    fun showBanner(message: String, warning: Boolean = false) {
        banners.add(BannerEntry(++lastRun, null, message, warning, running = false))
    }

    internal var requestNoticePermission: (() -> Unit)? = null
    private var pendingNotice: Triple<String, String, Boolean>? = null

    /** Snackbar có sẵn của Miuix, hiện ở đáy màn hình (host đặt trong Scaffold), có nút đóng; lỗi thì hiện lâu hơn. */
    val snackbarState = SnackbarHostState()
    private val snackbarScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun showSnackbar(message: String, long: Boolean = false) {
        snackbarScope.launch {
            snackbarState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun startLive(title: String, command: String, full: Boolean = false): LogEntry =
        LogEntry(title, command, startFull = full).also { log = it }

    fun report(item: Item, command: String, result: ExecResult, runId: Int = 0) {
        val summary = summaryOf(item.title, result)
        when (item.shell) {
            ShellMode.Live, ShellMode.Full -> Unit
            ShellMode.Hidden -> if (!result.ok) toast(summary)
            ShellMode.Banner -> finishBanner(runId, summary, warning = !result.ok)
            ShellMode.Notice -> {
                banners.firstOrNull { it.id == runId }?.let { dismissBanner(it) }
                notice(item.title, summary, warning = !result.ok)
            }
            ShellMode.Snackbar -> showSnackbar(summary, long = !result.ok)
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

/** Lề ngoài của log và khoảng cách giữa các khối (cùng giá trị với màn hình lỗi). */
private val LogMargin = 12.dp

private val LogYellow = Color(0xFFFFC107)

// Bảng log thường (shell = "live", hoặc Thu nhỏ từ toàn màn hình). Bố cục y hệt toàn màn hình: hàng tiêu đề có nút Toàn màn hình,
// nhãn trạng thái + thanh tiến trình (chỉ khi đang chạy hoặc lỗi), khung log chọn / sao chép được, hàng nút Chia sẻ và Hủy / Thoát.
@Composable
fun LogDialog(entry: LogEntry?, onDismiss: () -> Unit, onCancel: () -> Unit) {
    var last by remember { mutableStateOf(entry) }
    if (entry != null) last = entry
    OverlayDialog(
        show = entry != null && !entry.full,
        title = null,
        // Đang chạy: chạm ra ngoài không đóng log (Miuix dùng chung một callback cho chạm ngoài và Back, nên Back cũng tạm không đóng);
        // chỉ đóng bằng nút Hủy rồi Thoát. Lệnh xong thì chạm ngoài / Back đóng như cũ.
        onDismissRequest = if (last?.running == true) null else onDismiss,
    ) {
        last?.let { shown ->
            val output = shown.output
            Column(verticalArrangement = Arrangement.spacedBy(LogMargin)) {
                LogHeader(shown.title, "Toàn màn hình", onClick = { shown.full = true })
                LogStatusBlock(shown)
                LogSelectableCard(shown, output, Modifier.heightIn(max = 320.dp), fillHeight = false)
                LogActionRow(shown, output, onCancel = onCancel, onDismiss = onDismiss)
            }
        }
    }
}

// Log live toàn màn hình: mở khi shell = "full" hoặc bấm "Toàn màn hình". Bố cục giống màn hình lỗi (crash/CrashActivity):
// hàng tiêu đề có nút Thu nhỏ, khung log chọn / sao chép được, hàng nút Chia sẻ và Hủy / Thoát ở dưới.
// Nút cuối: đang chạy là Hủy (vàng, chỉ dừng lệnh, log vẫn mở), xong hoặc đã hủy là Thoát (xanh dương), lỗi là "Thoát: <mã>" (đỏ nhạt).
// Back của máy thì đóng log, lệnh vẫn chạy tiếp.
@Composable
fun LogFullScreen(entry: LogEntry?, onDismiss: () -> Unit, onCancel: () -> Unit) {
    if (entry == null || !entry.full) return
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = LogMargin),
                contentAlignment = Alignment.CenterStart,
            ) {
                LogHeader(entry.title, "Thu nhỏ", onClick = { entry.full = false })
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = LogMargin, end = LogMargin, bottom = LogMargin),
                verticalArrangement = Arrangement.spacedBy(LogMargin),
            ) {
                LogStatusBlock(entry)
                LogSelectableCard(entry, output, Modifier.weight(1f), fillHeight = true)
                LogActionRow(entry, output, onCancel = onCancel, onDismiss = onDismiss)
            }
        }
    }
}

/** Hàng tiêu đề dùng chung: tên lệnh bên trái, nút dạng viên thuốc (Toàn màn hình / Thu nhỏ) bên phải. */
@Composable
private fun LogHeader(title: String, pillText: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LogPillButton(text = pillText, onClick = onClick)
    }
}

/**
 * Nhãn trạng thái và thanh tiến trình. Chạy xong không lỗi thì không hiện gì (nút Thoát đã nói lên kết quả);
 * không phát ra gì thì cũng không chiếm khoảng cách giữa các khối.
 */
@Composable
private fun LogStatusBlock(entry: LogEntry) {
    if (entry.status == LogStatus.Done) return
    Column {
        LogStatusRow(entry)
        LogProgress(entry)
    }
}

/** Hàng nút dưới cùng: Chia sẻ, và nút Hủy (vàng) / Thoát (xanh dương) / Thoát: <mã> (đỏ nhạt) theo trạng thái. */
@Composable
private fun LogActionRow(entry: LogEntry, output: String, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(LogMargin)) {
        TextButton(
            text = "Chia sẻ",
            onClick = { shareText(context, entry.title, output.ifBlank { "(không có đầu ra)" }) },
            modifier = Modifier.weight(1f),
        )
        val label = when (entry.status) {
            LogStatus.Running -> "Hủy"
            LogStatus.Done, LogStatus.Cancelled -> "Thoát"
            LogStatus.Failed, LogStatus.TimedOut -> "Thoát: ${entry.exitCode ?: "hết giờ"}"
        }
        val colors = when (entry.status) {
            LogStatus.Running -> ButtonDefaults.textButtonColors(color = LogYellow, textColor = Color(0xFF1F1F1F))
            LogStatus.Done, LogStatus.Cancelled -> ButtonDefaults.textButtonColorsPrimary()
            LogStatus.Failed, LogStatus.TimedOut -> ButtonDefaults.textButtonColors(
                color = MiuixTheme.colorScheme.errorContainer,
                textColor = MiuixTheme.colorScheme.onErrorContainer,
            )
        }
        TextButton(
            text = label,
            onClick = if (entry.running) onCancel else onDismiss,
            modifier = Modifier.weight(1f),
            colors = colors,
        )
    }
}

// Khung log: nhấn giữ để chọn chữ (thanh công cụ hệ thống có Sao chép), cuộn dọc và ngang, tự cuộn xuống dòng mới nhất khi đang chạy.
// Cùng kiểu với khung log của màn hình lỗi. [fillHeight] = true: nội dung phủ kín khung (toàn màn hình, vuốt chỗ trống cũng cuộn được);
// false: khung ôm theo nội dung tới chiều cao tối đa do [modifier] đặt (bảng log thường).
@Composable
private fun LogSelectableCard(entry: LogEntry, output: String, modifier: Modifier, fillHeight: Boolean) {
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
                modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
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

// Thanh tiến trình: đang chạy chưa có "@progress N" thì chạy vô định, có thì hiện đúng %; xong không lỗi thì đầy.
// Chạy xong (kể cả lỗi, hết giờ, đã hủy) thì ẩn hẳn thanh.
@Composable
private fun LogProgress(entry: LogEntry?) {
    if (entry == null || !entry.running) return
    val value = entry.progress
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(progress = value, modifier = Modifier.fillMaxWidth())
}

// Không hiện "Xong" và mã thoát: nút Thoát ở dưới đã báo kết quả (Thoát: <mã> khi lỗi).
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
        LogStatus.Cancelled -> {
            label = "Đã hủy"
            container = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            content = MiuixTheme.colorScheme.onSurface
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
        val rightText = entry?.takeIf { it.running }?.progress?.let { "${(it * 100).roundToInt()}%" }
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
            // Banner thông tin: nền trung tính (không xanh) đậm hơn nền thẻ (thẻ dùng surfaceContainer) để không trùng màu thẻ.
            color = MiuixTheme.colorScheme.surfaceContainerHighest,
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

/** Một banner: mỗi lượt chạy `banner` / `notice` có một cái, [item] là item đã chạy (null nếu là banner độc lập). */
@Stable
class BannerEntry(
    val id: Int,
    val item: Item?,
    message: String,
    warning: Boolean,
    running: Boolean,
) {
    var message by mutableStateOf(message)
    var warning by mutableStateOf(warning)

    /** true khi lệnh còn chạy: thanh chạy vô hạn, có nút Hủy; false thì đếm ngược 5 giây rồi tự ẩn. */
    var running by mutableStateOf(running)

    /** true khi đang chạy hiệu ứng ẩn đi, sắp gỡ khỏi danh sách. */
    var closing by mutableStateOf(false)

    /** Tăng mỗi khi chuyển sang kết quả để đếm ngược chạy lại từ đầu. */
    var token by mutableIntStateOf(0)
    var job: Job? = null
}

/** Các banner xếp chồng ở đầu màn hình; chỉ chiếm chỗ của chính banner nên các item khác vẫn bấm được. */
@Composable
fun BannerOverlay(feedback: FeedbackHost, modifier: Modifier = Modifier) {
    if (feedback.banners.isEmpty()) return
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
        for (entry in feedback.banners.toList()) {
            key(entry.id) { BannerEntryView(entry, feedback) }
        }
    }
}

@Composable
private fun BannerEntryView(entry: BannerEntry, feedback: FeedbackHost) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = !entry.closing

    val progress = remember { Animatable(1f) }
    LaunchedEffect(entry.token, entry.running, entry.closing) {
        if (!entry.running && !entry.closing) {
            progress.snapTo(1f)
            progress.animateTo(0f, tween(BANNER_MILLIS, easing = LinearEasing))
            feedback.dismissBanner(entry)
        }
    }

    // Vuốt ngang: đang chạy thì hủy script, đã xong thì ẩn banner. Banner chuyển sang kết quả thì về lại vị trí giữa.
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(entry.token) { offsetX.snapTo(0f) }
    val dismiss: () -> Unit = {
        if (entry.running) feedback.cancelBanner(entry) else feedback.dismissBanner(entry)
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = expandVertically(tween(BANNER_SLIDE_MILLIS)) + slideInVertically(tween(BANNER_SLIDE_MILLIS)) { -it } + fadeIn(tween(BANNER_SLIDE_MILLIS)),
        exit = shrinkVertically(tween(BANNER_SLIDE_MILLIS)) + slideOutVertically(tween(BANNER_SLIDE_MILLIS)) { -it } + fadeOut(tween(BANNER_SLIDE_MILLIS)),
    ) {
        Banner(
            message = entry.message,
            warning = entry.warning,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (abs(offsetX.value) / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f) }
                .onSizeChanged { widthPx = it.width }
                .padding(horizontal = 16.dp, vertical = 4.dp)
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
            indeterminate = entry.running,
            maxLines = BANNER_OUTPUT_MAX_LINES,
            actionLabel = if (entry.running) "Hủy" else null,
            onAction = dismiss,
            onDismiss = if (entry.running) null else dismiss,
        )
    }

    if (entry.closing && visibleState.isIdle && !visibleState.currentState) {
        LaunchedEffect(entry) { feedback.banners.remove(entry) }
    }
}

private const val BANNER_OUTPUT_MAX_LINES = 8

private const val SWIPE_DISMISS_FRACTION = 0.3f

private const val SWIPE_OUT_MILLIS = 150

private const val BANNER_SLIDE_MILLIS = 300
