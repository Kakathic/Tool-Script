package com.tool.script.ui.feedback

import com.tool.script.util.AppText
import com.tool.script.R
import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Tệp log đầy đủ của từng [LogEntry], nằm ở cache/logs. Màn hình chỉ giữ vài trăm dòng cuối cho nhẹ, còn nút Chia sẻ gửi tệp này nên không bị cắt.
 * Mỗi lần ứng dụng khởi động (gọi từ [LogCenter.init]) chỉ xóa các tệp log_*.txt; tệp trong share chỉ xóa khi quá [SHARE_MAX_AGE_MS].
 * Không xóa share ngay vì tiến trình chính được khởi động lại khi ứng dụng nhận đọc tệp chia sẻ từ màn hình lỗi.
 */
object LogFiles {
    /** Mỗi log ghi tối đa chừng này; quá thì ngừng ghi và thêm một dòng ghi chú ở cuối tệp. */
    const val MAX_BYTES = 10L * 1024 * 1024

    private const val SHARE_MAX_AGE_MS = 24L * 60 * 60 * 1000

    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.cacheDir, "logs").also { logs ->
            logs.mkdirs()
            logs.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("log_")) f.delete()
            }
            val now = System.currentTimeMillis()
            File(logs, "share").listFiles()?.forEach { f ->
                if (now - f.lastModified() > SHARE_MAX_AGE_MS) f.deleteRecursively()
            }
        }
    }

    internal fun fileFor(id: Int): File? = dir?.let { File(it, "log_$id.txt") }
}

/**
 * Ghi log dạng chữ thuần vào tệp: bỏ mã màu ANSI, dòng bị ghi đè bằng `\r` (thanh tiến trình kiểu curl) chỉ giữ trạng thái cuối.
 * Không an toàn đa luồng: nơi gọi ([LogEntry]) khóa giúp.
 */
internal class PlainLogWriter(private val file: File?) {
    private var out: BufferedWriter? = null
    private val line = StringBuilder()
    private var overwrite = false
    private var bytes = 0L
    private var stopped = false

    fun write(chunk: String) {
        if (file == null || stopped) return
        for (c in chunk) {
            if (overwrite) {
                overwrite = false
                if (c != '\n') line.setLength(0)
            }
            when (c) {
                '\r' -> overwrite = true
                '\n' -> flushLine()
                else -> line.append(c)
            }
        }
    }

    /** Đẩy phần đã ghi xuống đĩa và trả dòng đang viết dở (chưa có xuống dòng), để tệp chia sẻ có đủ dòng cuối. */
    fun flushAndPending(): String {
        runCatching { out?.flush() }
        return strip(line)
    }

    /** Chốt tệp: ghi nốt dòng dở rồi đóng. */
    fun close() {
        if (file == null) return
        if (!stopped && line.isNotEmpty()) flushLine()
        stopped = true
        runCatching { out?.close() }
        out = null
    }

    private fun flushLine() {
        val target = file ?: return
        val text = strip(line)
        line.setLength(0)
        try {
            val w = out ?: BufferedWriter(OutputStreamWriter(FileOutputStream(target, true), Charsets.UTF_8), 32 * 1024).also { out = it }
            w.write(text)
            w.write("\n")
            bytes += text.toByteArray(Charsets.UTF_8).size + 1
            if (bytes > LogFiles.MAX_BYTES) {
                w.write(AppText.get(R.string.log_write_stopped, (LogFiles.MAX_BYTES / 1024 / 1024).toString()) + "\n")
                w.flush()
                stopped = true
            }
        } catch (_: Exception) {
            stopped = true
        }
    }

    private fun strip(text: CharSequence): String = ANSI.replace(text, "")

    private companion object {
        val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]")
    }
}
