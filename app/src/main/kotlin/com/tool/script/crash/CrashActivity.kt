package com.tool.script.crash

import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tool.script.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tool.script.ui.theme.ThemePrefs
import com.tool.script.ui.theme.ToolScriptTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trung tâm lỗi (chạy ở tiến trình ":crash").
 * Hiển thị: crash của app, lỗi script khởi động, lỗi parse TOML.
 * Màn hình chỉ hiện phần đầu (tối đa [MAX_CHARS] ký tự, do giới hạn của Intent); nội dung đầy đủ ghi vào tệp tạm trong cache/logs/share để nút Chia sẻ gửi cả tệp.
 * Nút: Sao chép, Chia sẻ (gửi tệp), Khởi động lại, Thoát.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.crash_title_default)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: getString(R.string.crash_no_content)
        val fatal = intent.getBooleanExtra(EXTRA_FATAL, true)
        val filePath = intent.getStringExtra(EXTRA_FILE)
        setContent {
            ToolScriptTheme(ThemePrefs.load(this), ThemePrefs.loadHaptic(this)) {
                CrashScreen(
                    title = title,
                    text = text,
                    fatal = fatal,
                    onShare = { share(title, text, filePath) },
                    onRestart = { restart() },
                    onExit = { exit(fatal) },
                )
            }
        }
    }

    private fun share(title: String, text: String, filePath: String?) {
        // Tệp đầy đủ do nơi báo lỗi ghi sẵn; không có (ghi lỗi) thì ghi lại phần đang hiện.
        val file = filePath?.let(::File)?.takeIf { it.exists() }
            ?: runCatching { saveFull(this, text) }.getOrNull()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            if (file != null) {
                val uri = FileProvider.getUriForFile(this@CrashActivity, "$packageName.fileprovider", file)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }
        startActivity(Intent.createChooser(send, getString(R.string.crash_share_error)))
    }

    private fun restart() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    private fun exit(fatal: Boolean) {
        finishAndRemoveTask()
        // Lỗi nghiêm trọng: app chính đã chết, chỉ còn tiến trình này. Không nghiêm trọng: quay lại app.
        if (fatal) Process.killProcess(Process.myPid())
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_FATAL = "fatal"
        private const val EXTRA_FILE = "file"
        private const val MAX_CHARS = 60_000

        /**
         * Mở Trung tâm lỗi. [fatal] = true: tách task mới (dùng khi app chính sắp bị đóng).
         */
        fun launch(context: Context, title: String, text: String, fatal: Boolean) {
            val shortened = if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n" + context.getString(R.string.crash_truncated) else text
            val file = runCatching { saveFull(context, text) }.getOrNull()
            val intent = Intent(context, CrashActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, shortened)
                .putExtra(EXTRA_FATAL, fatal)
                .putExtra(EXTRA_FILE, file?.absolutePath)
            if (fatal) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }

        /** Ghi nội dung đầy đủ vào cache/logs/share (cùng thư mục FileProvider cho phép) để chia sẻ dạng tệp. */
        private fun saveFull(context: Context, text: String): File {
            val dir = File(context.cacheDir, "logs/share").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return File(dir, "error_$stamp.txt").also { it.writeText(text) }
        }

        /** Nội dung báo cáo cho một exception không bắt được. */
        fun report(context: Context, thread: Thread, throwable: Throwable): String = buildString {
            appendLine(context.getString(R.string.crash_report_thread, thread.name))
            appendLine(context.getString(R.string.crash_report_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT.toString()))
            appendLine()
            append(throwable.stackTraceToString())
        }
    }
}

/** Lề ngoài của màn hình lỗi (trái, phải, dưới) và khoảng cách giữa log với hàng nút. */
private val CrashMargin = 12.dp

/**
 * Màn hình lỗi: hàng tiêu đề (tiêu đề sát lề trái, nút Chia sẻ có khung bo ở góc phải), khung log (nhấn giữ để chọn chữ,
 * thanh công cụ của hệ thống có Sao chép), rồi hai nút Khởi động lại và Thoát / Đóng ở dưới.
 */
@Composable
private fun CrashScreen(
    title: String,
    text: String,
    fatal: Boolean,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
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
                .padding(start = CrashMargin, end = CrashMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ShareButton(onClick = onShare)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = CrashMargin, end = CrashMargin, bottom = CrashMargin),
            verticalArrangement = Arrangement.spacedBy(CrashMargin),
        ) {
            // insideMargin = 0 và đệm 12dp đặt trong vùng cuộn: vuốt ở đâu trong khung (kể cả chỗ trống, sát mép)
            // cũng cuộn được cả dọc lẫn ngang. fillMaxSize đứng trước hai modifier cuộn để vùng cuộn phủ kín khung.
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                // SelectionContainer: nhấn giữ để chọn chữ, hiện thanh Sao chép / Chọn tất cả của hệ thống.
                // Màu vùng chọn và tay cầm theo màu chủ đạo của theme.
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
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            softWrap = false,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CrashMargin)) {
                TextButton(text = stringResource(R.string.action_restart), onClick = onRestart, modifier = Modifier.weight(1f))
                TextButton(text = stringResource(if (fatal) R.string.action_exit else R.string.action_close), onClick = onExit, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Nút Chia sẻ ở hàng tiêu đề: icon và chữ trong một khung bo, nền xám nhạt theo màu chữ của theme. */
@Composable
private fun ShareButton(onClick: () -> Unit) {
    val content = MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(content.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_share),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = stringResource(R.string.action_share), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = content)
    }
}
