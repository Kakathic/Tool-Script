package com.tool.script.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.tool.script.ui.theme.ToolScriptTheme
import com.tool.script.util.copyToClipboard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * Trung tâm lỗi (chạy ở tiến trình ":crash").
 * Hiển thị: crash của app, lỗi script khởi động, lỗi parse TOML.
 * Lỗi chỉ giữ trong bộ nhớ (qua Intent), không ghi ra file.
 * Nút: Sao chép, Chia sẻ, Khởi động lại, Thoát.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lỗi"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "(không có nội dung)"
        val fatal = intent.getBooleanExtra(EXTRA_FATAL, true)
        setContent {
            ToolScriptTheme {
                CrashScreen(
                    title = title,
                    text = text,
                    fatal = fatal,
                    onCopy = { copyToClipboard(this, "lỗi", text) },
                    onShare = { share(title, text) },
                    onRestart = { restart() },
                    onExit = { exit(fatal) },
                )
            }
        }
    }

    private fun share(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Chia sẻ lỗi"))
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
        private const val MAX_CHARS = 60_000

        /**
         * Mở Trung tâm lỗi. [fatal] = true: tách task mới (dùng khi app chính sắp bị đóng).
         */
        fun launch(context: Context, title: String, text: String, fatal: Boolean) {
            val shortened = if (text.length > MAX_CHARS) text.take(MAX_CHARS) + "\n... (đã cắt bớt)" else text
            val intent = Intent(context, CrashActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, shortened)
                .putExtra(EXTRA_FATAL, fatal)
            if (fatal) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }

        /** Nội dung báo cáo cho một exception không bắt được. */
        fun report(thread: Thread, throwable: Throwable): String = buildString {
            appendLine("Luồng: ${thread.name}")
            appendLine("Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            append(throwable.stackTraceToString())
        }
    }
}

@Composable
private fun CrashScreen(
    title: String,
    text: String,
    fatal: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Scaffold(topBar = { SmallTopAppBar(title = title) }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.weight(1f).fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                Text(
                    text = text,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    softWrap = false,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "Sao chép", onClick = onCopy, modifier = Modifier.weight(1f))
                TextButton(text = "Chia sẻ", onClick = onShare, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "Khởi động lại", onClick = onRestart, modifier = Modifier.weight(1f))
                TextButton(text = if (fatal) "Thoát" else "Đóng", onClick = onExit, modifier = Modifier.weight(1f))
            }
        }
    }
}
