package com.tool.script.executor

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    /**
     * Chạy [command]; [onLine] nhận từng dòng đầu ra ngay khi lệnh in ra, theo thứ tự đến:
     * `isError` = false là stdout, true là stderr (có thể gọi từ luồng nền).
     * [timeoutSec] = null là không giới hạn thời gian; có giá trị thì quá số giây đó lệnh bị dừng và báo hết giờ.
     */
    suspend fun exec(command: String, timeoutSec: Int? = null, onLine: ((line: String, isError: Boolean) -> Unit)? = null): ExecResult
}

/**
 * Đọc từng dòng của [stream] tới khi hết. Luồng bị ngắt khi hủy lệnh hoặc tiến trình bị dừng sẽ ném IOException
 * (ví dụ InterruptedIOException "read interrupted"), ở đây coi là hết đầu ra để lỗi này không làm app crash.
 */
private fun readLines(stream: java.io.InputStream, onLine: (String) -> Unit) {
    try {
        stream.bufferedReader().forEachLine(onLine)
    } catch (_: IOException) {
        // hết đầu ra
    }
}

/** Shell thường (`sh -c`), quyền của chính app. */
class LocalExecutor(override val rootMode: String = "any") : ShellExecutor {
    override suspend fun exec(command: String, timeoutSec: Int?, onLine: ((String, Boolean) -> Unit)?): ExecResult =
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
                        readLines(process.inputStream) {
                            out.appendLine(it)
                            onLine?.invoke(it, false)
                        }
                    }
                    val errJob = async {
                        readLines(process.errorStream) {
                            err.appendLine(it)
                            onLine?.invoke(it, true)
                        }
                    }
                    var timedOut = false
                    val finished = try {
                        runInterruptible {
                            if (timeoutSec == null) {
                                process.waitFor()
                                true
                            } else {
                                process.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)
                            }
                        }
                    } catch (e: CancellationException) {
                        // Bị hủy (nút Hủy của log): dừng hẳn tiến trình để các luồng đọc đầu ra kết thúc ngay.
                        process.destroyForcibly()
                        throw e
                    }
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

/** Shell root qua libsu. [onLine] nhận từng dòng stdout và stderr ngay khi lệnh in ra. */
class SuExecutor : ShellExecutor {
    override val rootMode: String = "root"

    // Số lệnh đang chạy. Shell chính của libsu chạy tuần tự từng lệnh, nên khi đã có lệnh đang chạy,
    // lệnh mới mở một shell root riêng để chạy song song (không phải chờ), xong thì đóng.
    private val inFlight = AtomicInteger(0)

    override suspend fun exec(command: String, timeoutSec: Int?, onLine: ((String, Boolean) -> Unit)?): ExecResult =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            val err = mutableListOf<String>()
            // `timeout` của toybox (Android 6+) giữ giới hạn thời gian cho từng lệnh; mã 124 = hết giờ.
            val quoted = com.tool.script.util.ShellText.quote(command)
            val wrapped = if (timeoutSec == null) "sh -c $quoted" else "timeout $timeoutSec sh -c $quoted"
            val outLines = object : CallbackList<String>(Executor { it.run() }) {
                override fun onAddElement(e: String) {
                    out += e
                    onLine?.invoke(e, false)
                }
            }
            val errLines = object : CallbackList<String>(Executor { it.run() }) {
                override fun onAddElement(e: String) {
                    err += e
                    onLine?.invoke(e, true)
                }
            }
            val parallel = inFlight.getAndIncrement() > 0
            try {
                // Không mở được shell riêng thì dùng shell chính (xếp hàng chờ như trước).
                val extra = if (parallel) runCatching { Shell.Builder.create().build("su") }.getOrNull() else null
                try {
                    val result = (extra ?: Shell.getShell()).newJob().add(wrapped).to(outLines, errLines).exec()
                    ExecResult(
                        code = result.code,
                        stdout = out.joinToString("\n"),
                        stderr = err.joinToString("\n"),
                        timedOut = timeoutSec != null && result.code == 124,
                    )
                } finally {
                    extra?.close()
                }
            } finally {
                inFlight.decrementAndGet()
            }
        }
}

class DetectResult(val executor: ShellExecutor, val notes: List<String>)

/** Dò backend theo thứ tự su -> ROM -> adb -> thường, giữ backend đang dùng. */
/**
 * Thêm vào đầu mọi lệnh: nạp env.sh ([ExecutorManager.envFile]) rồi chạy [ExecutorManager.prelude] (nếu có),
 * để chúng luôn chạy trước và lệnh dùng được các biến trong env.sh.
 */
private class PreludeExecutor(private val inner: ShellExecutor) : ShellExecutor {
    override val rootMode: String get() = inner.rootMode

    override suspend fun exec(command: String, timeoutSec: Int?, onLine: ((String, Boolean) -> Unit)?): ExecResult {
        val envFile = ExecutorManager.envFile
        val prelude = ExecutorManager.prelude
        val full = buildList {
            if (envFile != null) {
                val path = com.tool.script.util.ShellText.quote(envFile)
                add("[ -r $path ] && . $path")
            }
            if (!prelude.isNullOrBlank()) add(prelude)
            add(command)
        }.joinToString("\n")
        return inner.exec(full, timeoutSec, onLine)
    }
}

object ExecutorManager {
    @Volatile
    var current: ShellExecutor = PreludeExecutor(LocalExecutor())
        private set

    /** Đường dẫn env.sh đã thay key (xem EnvFile), được nạp trước mọi lệnh qua [current]. */
    @Volatile
    var envFile: String? = null

    /** Đoạn shell (`[app] env` trong main.toml) chạy sau env.sh, trước mọi lệnh qua [current]. */
    @Volatile
    var prelude: String? = null

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
            return DetectResult(SuExecutor(), notes).also { current = PreludeExecutor(it.executor) }
        }
        notes += if (hasRoot == null) "su: hết thời gian chờ, hạ cấp" else "su: không có quyền root"

        // 2. ROM, 3. adb: chưa cài đặt (xem README, Bước tiếp theo).
        notes += "ROM / adb: chưa hỗ trợ, dùng shell thường"

        // 4. Không root.
        notes += "Backend: shell thường (ROOT_MODE = any)"
        return DetectResult(LocalExecutor("any"), notes).also { current = PreludeExecutor(it.executor) }
    }
}
