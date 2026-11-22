package com.tool.script.ui.feedback

import com.tool.script.util.AppIcons
import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tool.script.R
import coil3.compose.AsyncImage
import com.tool.script.config.Item
import com.tool.script.config.ShellMode
import com.tool.script.ui.main.BlurDialog
import com.tool.script.ui.main.LocalBlurRequests
import com.tool.script.ui.main.MenuItem
import com.tool.script.ui.main.cardBlur
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import com.tool.script.ui.main.MenuPopup
import com.tool.script.executor.ExecResult
import com.tool.script.util.AnsiTerminal
import com.tool.script.util.copyToClipboard
import com.tool.script.util.ShellText
import com.tool.script.util.Util
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme


enum class LogStatus { Running, Done, Failed, TimedOut, Cancelled }

class LogEntry(
    val title: String,
    val command: String,
    initialResult: ExecResult? = null,
    startFull: Boolean = false,
    val desc: String? = null,
    initialWrap: Boolean = false,
    initialMono: Boolean = true,
) {
    /** Mã riêng của log: cũng là mã thông báo Android khi log bị ẩn, và mã để mở lại log từ thông báo (xem [LogCenter]). */
    val id: Int = nextId.getAndIncrement()

    /** Vòng cập nhật thông báo khi log đang ẩn (do [LogCenter] quản lý). */
    internal var notifyJob: Job? = null

    private val terminal = AnsiTerminal(MAX_LIVE_LINES)

    /** Tệp log đầy đủ (không bị cắt) cho nút Chia sẻ; màn hình chỉ giữ [MAX_LIVE_LINES] dòng cuối. */
    private val logWriter = PlainLogWriter(LogFiles.fileFor(id))

    private val uiHandler = Handler(Looper.getMainLooper())
    private val flushRunnable = Runnable { synchronized(this) { if (dirty) publish(SystemClock.elapsedRealtime()) } }
    private var dirty = false
    private var newLines = 0
    private var lastPublish = 0L

    /** Tăng sau mỗi lần nội dung log đổi (kể cả ghi đè bằng `\r`), để giao diện vẽ lại và tự cuộn xuống. */
    var revision by mutableIntStateOf(0)
        private set

    /** Bật: ngắt dòng dài trong khung log; tắt (mặc định): giữ nguyên một dòng và cuộn ngang. */
    var wrap by mutableStateOf(initialWrap)

    /** Bật (mặc định): font monospace; tắt: font mặc định. Giá trị ban đầu của [wrap] và [mono] đọc từ [LogPrefs]. */
    var mono by mutableStateOf(initialMono)
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
        result = ExecResult(code = -1, output = terminal.plainText())
        synchronized(this) {
            publish(SystemClock.elapsedRealtime())
            logWriter.close()
        }
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

    /** Các dòng đang hiện; không có dòng nào được phát trực tiếp thì dùng đầu ra gom lại khi xong. */
    private fun shownRows(): List<List<AnsiTerminal.Run>> {
        revision
        val live = terminal.snapshot()
        if (live.isNotEmpty()) return live
        val r = result ?: return emptyList()
        val fallback = AnsiTerminal(MAX_LIVE_LINES)
        r.output.lineSequence()
            .filter { ShellText.parseProgress(it) == null }
            .forEach { fallback.write(it + "\n") }
        return fallback.snapshot()
    }

    /** Tối đa [maxChars] ký tự cuối của log (văn bản thuần), cắt thừa thì thêm "…" ở đầu; dùng cho nội dung thông báo. */
    fun tail(maxChars: Int): String {
        val text = output
        return if (text.length > maxChars) "…" + text.takeLast(maxChars) else text
    }

    /** Văn bản thuần của log (không mã ANSI); dùng cho nút Chia sẻ. */
    val output: String
        get() = shownRows().joinToString("\n") { row -> row.joinToString("") { it.text } }

    /**
     * Cùng nội dung với [output] kèm màu: mã ANSI theo bảng màu sáng / tối ([dark]), stderr mặc định màu [errorColor],
     * chữ không có màu riêng dùng [textColor], nền đảo màu dùng [backgroundColor].
     */
    fun styled(errorColor: Color, textColor: Color, backgroundColor: Color, dark: Boolean): AnnotatedString {
        val rows = shownRows()
        return buildAnnotatedString {
            rows.forEachIndexed { i, row ->
                if (i > 0) append('\n')
                for (run in row) {
                    val style = spanOf(run.style, errorColor, textColor, backgroundColor, dark)
                    if (style == null) append(run.text) else withStyle(style) { append(run.text) }
                }
            }
        }
    }

    /**
     * Ghi một đoạn đầu ra (kết thúc bằng `\n` hoặc `\r`, hoặc đoạn dở); đầu ra đã gộp một pipe nên đến đúng thứ tự. Gọi từ luồng nền nên cần khóa.
     * Mọi dòng vào tệp log và terminal ngay, nhưng giao diện chỉ vẽ lại theo nhịp (xem [intervalFor]) để log dồn dập không làm giật máy.
     */
    @Synchronized
    fun append(chunk: String) {
        val p = ShellText.parseProgress(chunk)
        if (p != null) {
            progress = p / 100f
            return
        }
        terminal.write(chunk)
        logWriter.write(chunk)
        dirty = true
        newLines += chunk.count { it == '\n' }
        val now = SystemClock.elapsedRealtime()
        val wait = intervalFor(newLines) - (now - lastPublish)
        if (wait <= 0L) {
            publish(now)
        } else {
            uiHandler.removeCallbacks(flushRunnable)
            uiHandler.postDelayed(flushRunnable, wait)
        }
    }

    /** Báo giao diện vẽ lại với nội dung terminal hiện tại; gọi khi đang giữ khóa. */
    private fun publish(now: Long) {
        uiHandler.removeCallbacks(flushRunnable)
        revision++
        dirty = false
        newLines = 0
        lastPublish = now
    }

    fun finish(result: ExecResult) {
        // Đã bị hủy (log đã chốt): bỏ kết quả muộn của lệnh.
        if (this.result != null) return
        if (result.ok) progress = 1f
        this.result = result
        synchronized(this) {
            publish(SystemClock.elapsedRealtime())
            logWriter.close()
        }
    }

    /**
     * Sao log đầy đủ ra một tệp riêng trong cache/logs/share để chia sẻ (log đang chạy thì lấy phần đã có tới lúc này).
     * Chạy ở luồng nền: tệp có thể lớn.
     */
    fun exportFile(context: Context): File {
        val dir = File(context.cacheDir, "logs/share").apply { mkdirs() }
        val safe = title.replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').take(40).ifEmpty { "log" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dest = File(dir, "${safe}_$stamp.txt")
        val pending = synchronized(this) { logWriter.flushAndPending() }
        val src = LogFiles.fileFor(id)
        if (src != null && src.exists() && src.length() > 0L) {
            src.copyTo(dest, overwrite = true)
            if (pending.isNotEmpty()) dest.appendText(pending + "\n")
        } else {
            dest.writeText(output.ifBlank { "(không có đầu ra)" })
        }
        return dest
    }

    private companion object {
        /** Số dòng tối đa giữ trên màn hình; log đầy đủ nằm trong tệp (xem [exportFile]). */
        const val MAX_LIVE_LINES = 1000

        /** Nhịp vẽ lại tối thiểu theo số dòng mới từ lần vẽ trước: bình thường 100ms, từ 100 dòng trở lên 250ms, từ 1000 dòng trở lên 500ms. */
        fun intervalFor(newLines: Int): Long = when {
            newLines >= 1000 -> 500L
            newLines >= 100 -> 250L
            else -> 100L
        }
        val nextId = AtomicInteger(1000)
    }
}

private val DarkPalette = intArrayOf(
    0x6E7681, 0xF47067, 0x57AB5A, 0xC69026, 0x6CB6FF, 0xDCBDFB, 0x39C5CF, 0xCDD9E5,
    0x909DAB, 0xFF938A, 0x6BC46D, 0xDAAA3F, 0x96D0FF, 0xDCBDFB, 0x56D4DD, 0xFFFFFF,
)

private val LightPalette = intArrayOf(
    0x24292F, 0xCF222E, 0x116329, 0x9A6700, 0x0969DA, 0x8250DF, 0x1B7C83, 0x6E7781,
    0x57606A, 0xA40E26, 0x1A7F37, 0x7D4E00, 0x218BFF, 0xA475F9, 0x3192AA, 0x8C959F,
)

private fun ansiColor(c: AnsiTerminal.Color, dark: Boolean): Color {
    val rgb = if (c.isPalette) (if (dark) DarkPalette else LightPalette)[c.palette] else c.rgb
    return Color(0xFF000000.toInt() or rgb)
}

/** Kiểu Compose của một ô chữ; null khi là chữ thường không cần kiểu riêng. */
private fun spanOf(style: AnsiTerminal.Style, errorColor: Color, textColor: Color, backgroundColor: Color, dark: Boolean): SpanStyle? {
    if (style == AnsiTerminal.Style.DEFAULT) return null
    var fg = style.fg?.let { ansiColor(it, dark) } ?: if (style.error) errorColor else null
    var bg = style.bg?.let { ansiColor(it, dark) }
    if (style.inverse) {
        val newBg = fg ?: textColor
        fg = bg ?: backgroundColor
        bg = newBg
    }
    return SpanStyle(
        color = fg ?: Color.Unspecified,
        background = bg ?: Color.Unspecified,
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        textDecoration = if (style.underline) TextDecoration.Underline else null,
    )
}

class FeedbackHost(private val context: Context) {
    var log by mutableStateOf<LogEntry?>(null)
    /** Danh sách banner đang hiện, xếp chồng từ trên xuống (mỗi lượt chạy `banner` là một banner riêng). */
    val banners = mutableStateListOf<BannerEntry>()
    private var lastRun = 0

    /**
     * Item này còn banner của lượt chạy trước không (đang chạy, hoặc đã xong nhưng chưa hết đếm ngược 5 giây / chưa bị ẩn):
     * item đó bị khóa tới khi banner biến mất, các item khác vẫn bấm được.
     */
    fun isBusy(item: Item): Boolean = banners.any { it.item === item && !it.closing }

    /** Bắt đầu một lượt chạy: thêm banner "Đang chạy script…" của riêng [item]; trả về mã lượt cho [attachJob] / [endRun] / [report]. */
    fun beginRun(item: Item): Int {
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

    fun startLive(title: String, command: String, full: Boolean = false, desc: String? = null): LogEntry =
        LogEntry(
            title, command, startFull = full, desc = desc,
            initialWrap = LogPrefs.loadWrap(context),
            initialMono = LogPrefs.loadMono(context),
        ).also { log = it }

    /**
     * Ẩn log đang xem: đóng màn hình log, lệnh vẫn chạy nền để làm việc khác; mọi dòng log gửi lên thông báo Android
     * (tiêu đề = tên tính năng), chạm vào thông báo thì mở lại ([showHiddenLog]). Chưa có quyền thông báo (Android 13+) thì xin luôn.
     */
    fun hideLog(entry: LogEntry) {
        if (log === entry) log = null
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNoticePermission?.invoke()
        }
        LogCenter.hide(entry)
    }

    /** Mở lại log đã ẩn có mã [id] (từ thông báo). false nếu không còn (đã bị bỏ, hoặc ứng dụng đã khởi động lại). */
    fun showHiddenLog(id: Int): Boolean {
        val entry = LogCenter.take(id) ?: return false
        // Đang xem một log khác còn chạy thì ẩn nó đi, khỏi mất dấu.
        log?.takeIf { it !== entry && it.running }?.let { LogCenter.hide(it) }
        log = entry
        return true
    }

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
        result.ok -> outputOf(result.output) ?: "$title: hoàn tất"
        else -> withOutput("$title: lỗi (mã ${result.code})", result)
    }

    /** Tiêu đề lỗi, xuống dòng rồi tới toàn bộ phần lệnh in ra nếu có. */
    private fun withOutput(head: String, result: ExecResult): String =
        outputOf(result.output)?.let { "$head\n$it" } ?: head

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
        // Từ chối quyền khi vừa ẩn log: không có thông báo thì không mở lại log đã ẩn được, báo rõ cho người dùng.
        if (!granted && pendingNotice == null && LogCenter.hasHidden()) {
            showBanner("Chưa cấp quyền thông báo: log đã ẩn vẫn chạy nhưng sẽ không có thông báo để mở lại.", warning = true)
        }
        val pending = pendingNotice ?: return
        pendingNotice = null
        if (granted) post(pending.first, pending.second) else showBanner(pending.second, pending.third)
    }

    private fun post(title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Kênh thông báo chỉ có từ Android 8 (API 26); máy thấp hơn dùng NotificationCompat không cần kênh.
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Kết quả lệnh", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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

/** Mọi lề và khoảng cách giữa các khối của log toàn màn hình (trên, dưới, trái, phải, giữa các khối); cũng là khoảng cách giữa hai nút ở hàng dưới cùng. */
private val LogFullMargin = 16.dp

/** Tỉ lệ chiều cao màn hình mà cả cửa sổ log thường chiếm (khung log cố định, thêm dòng thì cuộn chứ không nở ra). */
private const val LogWindowFraction = 0.55f

/** Phần cố định ngoài khung log (tiêu đề, thanh tiến trình, hàng nút, khoảng cách, lề dialog), tính bằng dp. */
private const val LogWindowOverheadDp = 150

/** Chiều cao khung log ở bảng log thường: [LogWindowFraction] màn hình trừ phần cố định, không thấp hơn 160dp. */
@Composable
private fun logCardHeight(): Dp {
    val screen = LocalConfiguration.current.screenHeightDp
    return (screen * LogWindowFraction - LogWindowOverheadDp).dp.coerceAtLeast(160.dp)
}

/** Chiều cao ô giữ chỗ của thanh tiến trình. */
private val LogProgressHeight = 3.dp


private val LogYellow = Color(0xFFFFC107)

/** Màu dòng stderr trong log. */
private val LogRed = Color(0xFFE5534B)

// Bảng log thường (shell = "live", hoặc Thu nhỏ từ toàn màn hình). Bố cục y hệt toàn màn hình: hàng tiêu đề có icon Toàn màn hình và icon menu,
// nhãn trạng thái + thanh tiến trình (chỉ khi đang chạy hoặc lỗi), khung log chọn / sao chép được, hàng nút Chia sẻ và Hủy / Thoát.
@Composable
fun LogDialog(entry: LogEntry?, onDismiss: () -> Unit, onCancel: () -> Unit, onHide: () -> Unit) {
    var last by remember { mutableStateOf(entry) }
    if (entry != null) last = entry
    BlurDialog(
        show = entry != null && !entry.full,
        title = null,
        // Đang chạy: chạm ra ngoài không đóng log (Miuix dùng chung một callback cho chạm ngoài và Back, nên Back cũng tạm không đóng);
        // chỉ đóng bằng nút Hủy rồi Thoát. Lệnh xong thì chạm ngoài / Back đóng như cũ.
        onDismissRequest = if (last?.running == true) null else onDismiss,
    ) {
        last?.let { shown ->
            val output = shown.output
            Column {
                LogHeader(shown, onHide)
                // Ô giữ chỗ cao đúng [LogMargin], bằng khoảng cách khung log - hàng nút: thanh tiến trình nằm giữa, ẩn đi thì vẫn 12dp.
                Box(Modifier.fillMaxWidth().height(LogMargin), contentAlignment = Alignment.Center) {
                    LogProgress(shown)
                }
                LogSelectableCard(shown, output, Modifier.height(logCardHeight()), full = false)
                Spacer(Modifier.height(LogMargin))
                LogActionRow(shown, onCancel = onCancel, onDismiss = onDismiss)
            }
        }
    }
}

// Log live toàn màn hình: mở khi shell = "full" hoặc bấm "Toàn màn hình". Vẽ thẳng trong cây giao diện chính (không phải cửa sổ riêng)
// để dùng chung nền mờ của DialogBlurScrim: có blur thì trang phía sau mờ và tối nhẹ, phủ thêm lớp màu nền trong suốt một phần cho dễ đọc;
// không có blur thì nền đặc như cũ. Đặt ở gốc Main, trên lớp scrim và dưới lớp popup của Scaffold (menu, hộp xác nhận vẫn nổi lên trên).
// Nút cuối: đang chạy là Hủy (vàng, chỉ dừng lệnh, log vẫn mở), xong hoặc đã hủy là Thoát (xanh dương), lỗi là "Thoát: <mã>" (đỏ nhạt).
// Back của máy thì đóng log, lệnh vẫn chạy tiếp.
@Composable
fun LogFullScreen(entry: LogEntry?, onDismiss: () -> Unit, onCancel: () -> Unit, onHide: () -> Unit) {
    var last by remember { mutableStateOf(entry) }
    if (entry != null) last = entry
    val show = entry != null && entry.full
    val requests = LocalBlurRequests.current
    if (requests != null) {
        DisposableEffect(show) {
            if (show) requests.dialogs++
            onDispose { if (show) requests.dialogs-- }
        }
    }
    BackHandler(enabled = show, onBack = onDismiss)
    AnimatedVisibility(visible = show, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        val shown = last ?: return@AnimatedVisibility
        val output = shown.output
        val base = MiuixTheme.colorScheme.background
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (requests != null) base.copy(alpha = 0.6f) else base)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(LogFullMargin),
            ) {
                LogHeader(shown, onHide)
                // Ô giữ chỗ cao đúng [LogFullMargin]: thanh tiến trình nằm giữa, ẩn đi thì khoảng cách tiêu đề - khung log vẫn là 16dp.
                Box(Modifier.fillMaxWidth().height(LogFullMargin), contentAlignment = Alignment.Center) {
                    LogProgress(shown)
                }
                LogSelectableCard(shown, output, Modifier.weight(1f), full = true)
                Spacer(Modifier.height(LogFullMargin))
                LogActionRow(shown, onCancel = onCancel, onDismiss = onDismiss)
            }
        }
    }
}

/**
 * Hàng tiêu đề dùng chung: tên lệnh bên trái, bên phải là một thanh công cụ dạng viên thuốc gom các icon Ẩn (chỉ khi đang chạy),
 * Toàn màn hình / Thu nhỏ và menu (ba chấm). Menu thả xuống ngay dưới icon; các tính năng của log thêm vào đây bằng [MenuItem].
 */
@Composable
private fun LogHeader(entry: LogEntry, onHide: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.desc?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ẩn: chỉ khi lệnh còn chạy (xong rồi thì đã có nút Thoát). Lệnh chạy nền, log gửi lên thông báo Android.
            if (entry.running) {
                LogIconButton(R.drawable.ic_hide, "Ẩn (chạy nền)", onHide)
                LogToolbarDivider()
            }
            if (entry.full) {
                LogIconButton(R.drawable.ic_fullscreen_exit, "Thu nhỏ") { entry.full = false }
            } else {
                LogIconButton(R.drawable.ic_fullscreen, "Toàn màn hình") { entry.full = true }
            }
            LogToolbarDivider()
            Box {
                LogIconButton(R.drawable.ic_more_vert, "Menu") { menuOpen = true }
                if (menuOpen) {
                    MenuPopup(onDismiss = { menuOpen = false }, minWidth = 160.dp) {
                        MenuItem(label = "Ngắt dòng", selected = entry.wrap) {
                            entry.wrap = !entry.wrap
                            LogPrefs.saveWrap(context, entry.wrap)
                            menuOpen = false
                        }
                        MenuItem(label = "Font mono", selected = entry.mono) {
                            entry.mono = !entry.mono
                            LogPrefs.saveMono(context, entry.mono)
                            menuOpen = false
                        }
                    }
                }
            }
        }
    }
}

/** Hàng nút dưới cùng: Chia sẻ, và nút Hủy (vàng) / Thoát (xanh dương) / Thoát: <mã> (đỏ nhạt) theo trạng thái. */
@Composable
private fun LogActionRow(entry: LogEntry, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(LogFullMargin)) {
        TextButton(
            text = "Chia sẻ",
            onClick = {
                scope.launch {
                    val file = withContext(Dispatchers.IO) { runCatching { entry.exportFile(context) }.getOrNull() }
                    if (file != null) shareFile(context, entry.title, file)
                    else Toast.makeText(context, "Không tạo được tệp log để chia sẻ", Toast.LENGTH_LONG).show()
                }
            },
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

// Khung log: nhấn giữ để chọn chữ (thanh công cụ hệ thống có Sao chép), cuộn dọc và ngang (bật Ngắt dòng thì chỉ cuộn dọc), tự cuộn xuống dòng mới nhất khi đang chạy.
// Toàn màn hình có blur: khung trong suốt, phủ đen nhạt để thấy nền mờ phía sau, chữ sáng cho dễ đọc. Font mono bật mặc định, tắt được ở menu ba chấm.
// Khung có chiều cao cố định và nội dung luôn phủ kín khung, nên vuốt ở chỗ trống (kể cả log chỉ vài dòng) vẫn cuộn được; [full] = true là log toàn màn hình.
@Composable
private fun LogSelectableCard(entry: LogEntry, output: String, modifier: Modifier, full: Boolean) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val revision = entry.revision
    LaunchedEffect(output, revision) {
        if (entry.running) vertical.scrollTo(vertical.maxValue)
    }
    val glass = full && LocalBlurRequests.current != null
    val cardColors = if (glass) {
        CardDefaults.defaultColors(color = Color.Black.copy(alpha = 0.35f), contentColor = Color(0xFFE6EDF3))
    } else {
        CardDefaults.defaultColors()
    }
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors, insideMargin = PaddingValues(0.dp)) {
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
                    .then(if (entry.wrap) Modifier else Modifier.horizontalScroll(horizontal))
                    .padding(12.dp),
            ) {
                val font = if (entry.mono) FontFamily.Monospace else FontFamily.Default
                if (output.isBlank()) {
                    Text(
                        text = if (entry.running) "Đang chờ đầu ra..." else "Không có đầu ra",
                        color = if (glass) Color.White.copy(alpha = 0.6f) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontFamily = font,
                        fontSize = 12.sp,
                    )
                } else {
                    val scheme = MiuixTheme.colorScheme
                    Text(
                        text = entry.styled(
                            errorColor = LogRed,
                            textColor = if (glass) Color(0xFFE6EDF3) else scheme.onSurface,
                            backgroundColor = if (glass) Color(0xFF161B22) else scheme.surface,
                            dark = glass || scheme.background.luminance() < 0.5f,
                        ),
                        color = if (glass) Color(0xFFE6EDF3) else scheme.onSurface,
                        fontFamily = font,
                        fontSize = 12.sp,
                        softWrap = entry.wrap,
                    )
                }
            }
        }
    }
}

/** Vạch dọc mảnh ngăn cách các nút trong thanh công cụ của hàng tiêu đề. */
@Composable
private fun LogToolbarDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    )
}

/** Nút icon tròn 32dp nằm trong thanh công cụ của hàng tiêu đề (nền chung do thanh vẽ); [icon] là vector drawable. */
@Composable
private fun LogIconButton(icon: Int, description: String, onClick: () -> Unit) {
    val content = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = description,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun shareFile(context: Context, subject: String, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(subject, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Chia sẻ log")
    // Context không phải Activity thì bắt buộc cờ NEW_TASK.
    val hasActivity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.any { it is Activity }
    if (!hasActivity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

// Thanh tiến trình: đang chạy chưa có "@progress N" thì chạy vô định, có thì hiện đúng %. Chạy xong (kể cả lỗi, hết giờ, đã hủy) thì thanh ẩn,
// nhưng ô giữ chỗ cao cố định [LogProgressHeight] nên bố cục bên dưới không bị xê dịch.
@Composable
private fun LogProgress(entry: LogEntry?) {
    Box(Modifier.fillMaxWidth().height(LogProgressHeight)) {
        if (entry != null && entry.running) {
            LinearProgressIndicator(progress = entry.progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ConfirmDialog(message: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    BlurDialog(
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
    backdrop: LayerBackdrop? = null,
) {
    // Banner thông tin: nền trung tính (không xanh) đậm hơn nền thẻ (thẻ dùng surfaceContainer) để không trùng màu thẻ.
    val base = if (warning) MiuixTheme.colorScheme.errorContainer else MiuixTheme.colorScheme.surfaceContainerHighest
    val content = if (warning) MiuixTheme.colorScheme.onErrorContainer else MiuixTheme.colorScheme.onSurface
    // Có backdrop: nền kính mờ (làm mờ phần phía sau rồi phủ màu nền trong suốt một phần), Card để trong suốt.
    val colors = CardDefaults.defaultColors(color = if (backdrop != null) Color.Transparent else base, contentColor = content)
    val hasButtons = actionLabel != null || onDismiss != null
    Card(
        modifier = modifier.fillMaxWidth().cardBlur(backdrop, RoundedCornerShape(16.dp), base),
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
                    model = Util.iconModel(if (warning) AppIcons.BannerWarning else AppIcons.BannerInfo),
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
fun BannerOverlay(feedback: FeedbackHost, modifier: Modifier = Modifier, backdrop: LayerBackdrop? = null) {
    if (feedback.banners.isEmpty()) return
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp)) {
        for (entry in feedback.banners.toList()) {
            key(entry.id) { BannerEntryView(entry, feedback, backdrop) }
        }
    }
}

@Composable
private fun BannerEntryView(entry: BannerEntry, feedback: FeedbackHost, backdrop: LayerBackdrop?) {
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
            backdrop = backdrop,
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
