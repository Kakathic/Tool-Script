package com.tool.script.executor

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

// Backend thực thi, thứ tự ưu tiên: su -> ROM -> adb -> thường.
// Giá trị biến ROOT_MODE: root / system / adb / any.
// File chép từ assets ra thư mục app không exec trực tiếp được (Android 10+),
// nên script được chạy qua trình thông dịch: `sh /đường/dẫn/init.sh`.

data class ExecResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    val ok: Boolean get() = code == 0 && !timedOut

    /** stdout rồi stderr, bỏ phần rỗng. */
    val combined: String
        get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
}

interface ShellExecutor {
    /** Giá trị của biến `ROOT_MODE` khi dùng backend này. */
    val rootMode: String

    /** Chạy [command]; [onLine] nhận từng dòng stdout (có thể gọi từ luồng nền). */
    suspend fun exec(command: String, timeoutSec: Int = 30, onLine: ((String) -> Unit)? = null): ExecResult
}

/** Shell thường (`sh -c`), quyền của chính app. */
class LocalExecutor(override val rootMode: String = "any") : ShellExecutor {
    override suspend fun exec(command: String, timeoutSec: Int, onLine: ((String) -> Unit)?): ExecResult =
        withContext(Dispatchers.IO) {
            val process = try {
                ProcessBuilder("sh", "-c", command).start()
            } catch (e: IOException) {
                return@withContext ExecResult(127, "", e.message ?: "không chạy được sh")
            }
            runCatching { process.outputStream.close() }
            val out = StringBuilder()
            val err = StringBuilder()
            try {
                coroutineScope {
                    val outJob = async {
                        process.inputStream.bufferedReader().forEachLine {
                            out.appendLine(it)
                            onLine?.invoke(it)
                        }
                    }
                    val errJob = async { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
                    var timedOut = false
                    val finished = runInterruptible { process.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS) }
                    if (!finished) {
                        timedOut = true
                        process.destroyForcibly()
                    }
                    outJob.await()
                    errJob.await()
                    ExecResult(
                        code = if (timedOut) -1 else process.exitValue(),
                        stdout = out.toString().trimEnd(),
                        stderr = err.toString().trimEnd(),
                        timedOut = timedOut,
                    )
                }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
}

/** Shell root qua libsu. Chưa stream từng dòng: [onLine] được gọi sau khi lệnh chạy xong. */
class SuExecutor : ShellExecutor {
    override val rootMode: String = "root"

    override suspend fun exec(command: String, timeoutSec: Int, onLine: ((String) -> Unit)?): ExecResult =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            val err = mutableListOf<String>()
            // `timeout` của toybox (Android 6+) giữ giới hạn thời gian cho từng lệnh; mã 124 = hết giờ.
            val wrapped = "timeout $timeoutSec sh -c " + com.tool.script.util.ShellText.quote(command)
            val result = Shell.cmd(wrapped).to(out, err).exec()
            out.forEach { onLine?.invoke(it) }
            ExecResult(
                code = result.code,
                stdout = out.joinToString("\n"),
                stderr = err.joinToString("\n"),
                timedOut = result.code == 124,
            )
        }
}

class DetectResult(val executor: ShellExecutor, val notes: List<String>)

/** Dò backend theo thứ tự su -> ROM -> adb -> thường, giữ backend đang dùng. */
object ExecutorManager {
    @Volatile
    var current: ShellExecutor = LocalExecutor()
        private set

    private const val DETECT_TIMEOUT_MS = 5_000L

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun detect(): DetectResult {
        val notes = mutableListOf<String>()

        // 1. su. getShell() chặn luồng khi hộp thoại cấp root đang chờ, nên chạy tách rời + timeout 5 giây.
        val probe = GlobalScope.async(Dispatchers.IO) {
            runCatching {
                Shell.getShell()
                Shell.isAppGrantedRoot() == true
            }.getOrDefault(false)
        }
        val hasRoot = withTimeoutOrNull(DETECT_TIMEOUT_MS) { probe.await() }
        if (hasRoot == true) {
            notes += "Backend: root (su)"
            return DetectResult(SuExecutor(), notes).also { current = it.executor }
        }
        notes += if (hasRoot == null) "su: hết thời gian chờ, hạ cấp" else "su: không có quyền root"

        // 2. ROM, 3. adb: chưa cài đặt (xem README, Bước tiếp theo).
        notes += "ROM / adb: chưa hỗ trợ, dùng shell thường"

        // 4. Không root.
        notes += "Backend: shell thường (ROOT_MODE = any)"
        return DetectResult(LocalExecutor("any"), notes).also { current = it.executor }
    }
}
