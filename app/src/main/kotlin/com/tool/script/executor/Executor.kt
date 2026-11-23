package com.tool.script.executor

import com.tool.script.util.AppText
import com.tool.script.R
import android.os.Build
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

// Mọi backend đều chỉ dùng MỘT pipe: stderr gộp vào stdout (như `2>&1`) ngay trong shell, nên đầu ra đến đúng thứ tự lệnh in ra.
// Đổi lại không phân biệt được dòng stderr (không còn màu đỏ tự động).
//
// Backend thực thi, thứ tự ưu tiên: su -> ROM -> adb -> thường.
// Giá trị biến ROOT_MODE: root / system / adb / any.
// File chép từ assets ra thư mục app không exec trực tiếp được (Android 10+),
// nên script được chạy qua trình thông dịch: `sh /đường/dẫn/init.sh`.

/** [output] là toàn bộ đầu ra (stdout và stderr gộp, đúng thứ tự in ra). */
data class ExecResult(
    val code: Int,
    val output: String,
    val timedOut: Boolean = false,
) {
    val ok: Boolean get() = code == 0 && !timedOut
}

interface ShellExecutor {
    /** Giá trị của biến `ROOT_MODE` khi dùng backend này. */
    val rootMode: String

    /**
     * Chạy [command]; [onOutput] nhận từng đoạn đầu ra (stdout và stderr gộp một pipe) ngay khi lệnh in ra, đúng thứ tự:
     * mỗi đoạn kết thúc bằng `\n` hoặc `\r` (đoạn cuối có thể không có), nên chữ ghi đè bằng `\r` đến được ngay
     * (có thể gọi từ luồng nền).
     * [timeoutSec] = null là không giới hạn thời gian; có giá trị thì quá số giây đó lệnh bị dừng và báo hết giờ.
     */
    suspend fun exec(command: String, timeoutSec: Int? = null, onOutput: ((chunk: String) -> Unit)? = null): ExecResult
}

/**
 * Đọc [stream] tới khi hết, gọi [onSegment] cho từng đoạn kết thúc bằng `\n` hoặc `\r` (kể cả dấu kết thúc), đoạn cuối còn dở thì gọi khi hết luồng.
 * Tách theo `\r` để thanh tiến trình in bằng `\r` hiện ra ngay thay vì chờ tới `\n`.
 * Luồng bị ngắt khi hủy lệnh hoặc tiến trình bị dừng sẽ ném IOException
 * (ví dụ InterruptedIOException "read interrupted"), ở đây coi là hết đầu ra để lỗi này không làm app crash.
 */
private fun readSegments(stream: java.io.InputStream, onSegment: (String) -> Unit) {
    val seg = StringBuilder()
    try {
        val reader = stream.reader(Charsets.UTF_8)
        val buf = CharArray(4096)
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            for (i in 0 until n) {
                val c = buf[i]
                seg.append(c)
                if (c == '\n' || c == '\r') {
                    onSegment(seg.toString())
                    seg.setLength(0)
                }
            }
        }
    } catch (_: IOException) {
        // hết đầu ra
    }
    if (seg.isNotEmpty()) onSegment(seg.toString())
}

/**
 * Gom các đoạn của [readSegments] về dạng từng dòng như trước (`\n`, `\r`, `\r\n` đều là một lần xuống dòng) cho [ExecResult].
 */
private class LineJoiner(private val sb: StringBuilder) {
    private var afterCr = false

    fun add(segment: String) {
        if (segment == "\n" && afterCr) {
            afterCr = false
            return
        }
        val last = segment.last()
        if (last == '\r') {
            sb.append(segment, 0, segment.length - 1).append('\n')
            afterCr = true
        } else {
            sb.append(segment)
            afterCr = false
        }
    }
}

// Process.isAlive / destroyForcibly / waitFor(timeout) chỉ có từ API 26; minSdk 24 nên dùng bản tương thích dưới đây.
private fun Process.alive(): Boolean =
    try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

private fun Process.kill() {
    if (Build.VERSION.SDK_INT >= 26) destroyForcibly() else destroy()
}

/** Chờ tối đa [timeoutSec] giây; true = tiến trình đã kết thúc. Có thể ném InterruptedException (runInterruptible xử lý). */
private fun Process.waitForCompat(timeoutSec: Long): Boolean {
    if (Build.VERSION.SDK_INT >= 26) return waitFor(timeoutSec, TimeUnit.SECONDS)
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
    while (alive()) {
        if (System.nanoTime() >= deadline) return false
        Thread.sleep(50)
    }
    return true
}

/** Shell thường (`sh -c`), quyền của chính app. */
class LocalExecutor(override val rootMode: String = "any") : ShellExecutor {
    override suspend fun exec(command: String, timeoutSec: Int?, onOutput: ((String) -> Unit)?): ExecResult =
        withContext(Dispatchers.IO) {
            val process = try {
                // Một pipe duy nhất cho cả stdout và stderr: thứ tự đọc ra chính là thứ tự lệnh ghi vào.
                ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            } catch (e: IOException) {
                return@withContext ExecResult(127, e.message ?: AppText.get(R.string.exec_sh_failed))
            }
            runCatching { process.outputStream.close() }
            val out = StringBuilder()
            try {
                coroutineScope {
                    val joiner = LineJoiner(out)
                    val readJob = async {
                        readSegments(process.inputStream) {
                            joiner.add(it)
                            onOutput?.invoke(it)
                        }
                    }
                    var timedOut = false
                    val finished = try {
                        runInterruptible {
                            if (timeoutSec == null) {
                                process.waitFor()
                                true
                            } else {
                                process.waitForCompat(timeoutSec.toLong())
                            }
                        }
                    } catch (e: CancellationException) {
                        // Bị hủy (nút Hủy của log): dừng hẳn tiến trình để luồng đọc đầu ra kết thúc ngay.
                        process.kill()
                        throw e
                    }
                    if (!finished) {
                        timedOut = true
                        process.kill()
                    }
                    readJob.await()
                    ExecResult(
                        code = if (timedOut) -1 else process.exitValue(),
                        output = out.toString().trimEnd(),
                        timedOut = timedOut,
                    )
                }
            } finally {
                if (process.alive()) process.kill()
            }
        }
}

/** Shell root qua libsu. [onOutput] nhận từng dòng (thêm `\n` cuối dòng) ngay khi lệnh in ra; libsu đã tách dòng sẵn nên `\r` giữa chừng không được giữ lại. */
class SuExecutor : ShellExecutor {
    override val rootMode: String = "root"

    // Số lệnh đang chạy. Shell chính của libsu chạy tuần tự từng lệnh, nên khi đã có lệnh đang chạy,
    // lệnh mới mở một shell root riêng để chạy song song (không phải chờ), xong thì đóng.
    private val inFlight = AtomicInteger(0)

    override suspend fun exec(command: String, timeoutSec: Int?, onOutput: ((String) -> Unit)?): ExecResult =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<String>()
            // `timeout` của toybox (Android 6+) giữ giới hạn thời gian cho từng lệnh; mã 124 = hết giờ.
            val quoted = com.tool.script.util.ShellText.quote(command)
            // `2>&1` gộp stderr vào stdout ngay trong shell root: một pipe duy nhất nên thứ tự là thứ tự thật.
            val wrapped = if (timeoutSec == null) "sh -c $quoted 2>&1" else "timeout $timeoutSec sh -c $quoted 2>&1"
            val outLines = object : CallbackList<String>(Executor { it.run() }) {
                override fun onAddElement(e: String) {
                    out += e
                    onOutput?.invoke(e + "\n")
                }
            }
            val parallel = inFlight.getAndIncrement() > 0
            try {
                // Không mở được shell riêng thì dùng shell chính (xếp hàng chờ như trước).
                val extra = if (parallel) runCatching { Shell.Builder.create().build("su") }.getOrNull() else null
                try {
                    val result = (extra ?: Shell.getShell()).newJob().add(wrapped).to(outLines).exec()
                    ExecResult(
                        code = result.code,
                        output = out.joinToString("\n"),
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
 * Thêm vào đầu mọi lệnh: đặt `shell_progres` (PID của shell chạy lệnh), nạp env.sh ([ExecutorManager.envFile]) kèm PID đó làm `$1`,
 * rồi chạy [ExecutorManager.prelude] (nếu có), để chúng luôn chạy trước và lệnh dùng được các biến trong env.sh.
 *
 * Hủy lệnh (nút Hủy của log / banner) và hết giờ: shell chạy lệnh in dòng `@pid N` đầu tiên (không đưa ra log, không nằm trong kết quả);
 * khi coroutine bị hủy hoặc hết giờ, `killtree` được chạy bằng chính backend đó để giết cả cây tiến trình con trước, rồi lệnh gốc mới kết thúc.
 */
private class PreludeExecutor(private val inner: ShellExecutor) : ShellExecutor {
    override val rootMode: String get() = inner.rootMode

    override suspend fun exec(command: String, timeoutSec: Int?, onOutput: ((String) -> Unit)?): ExecResult = coroutineScope {
        val pid = AtomicReference<String?>(null)
        // NonCancellable: không để bên trong tự kill tiến trình gốc trước, nếu không các tiến trình con mồ côi (ppid = 1)
        // sẽ không còn tìm thấy từ gốc. Thứ tự đúng: killtree giết cả cây, rồi lệnh gốc tự kết thúc.
        // Hết giờ cũng do đây tính (bên trong chạy không giới hạn) để dùng được killtree.
        val run = async(NonCancellable) {
            inner.exec(withHead(command, withPid = true), null) { line ->
                val p = parsePid(line)
                if (p != null) pid.compareAndSet(null, p) else onOutput?.invoke(line)
            }
        }
        try {
            val finished = if (timeoutSec == null) {
                run.join()
                true
            } else {
                withTimeoutOrNull(timeoutSec * 1000L) { run.join(); true } == true
            }
            if (finished) {
                run.await().withoutPidLine()
            } else {
                stop(run, pid.get())
                // Lấy phần đã in ra; không lấy được thì để trống.
                val partial = runCatching { withTimeoutOrNull(STOP_WAIT_MS) { run.await() } }.getOrNull()
                (partial ?: ExecResult(-1, "")).copy(code = -1, timedOut = true).withoutPidLine()
            }
        } catch (e: CancellationException) {
            stop(run, pid.get())
            throw e
        }
    }

    /** Giết cả cây tiến trình bằng killtree, rồi chờ lệnh gốc kết thúc; chưa kết thúc (killtree lỗi...) thì dừng theo cách của backend. */
    private suspend fun stop(run: Deferred<ExecResult>, pid: String?) {
        withContext(NonCancellable) {
            killTree(pid)
            if (withTimeoutOrNull(STOP_WAIT_MS) { run.join(); true } != true) run.cancel()
        }
    }

    /** Chạy `killtree` với `shell_progres` = [pid] bằng cùng backend (root thì giết được tiến trình root). */
    private suspend fun killTree(pid: String?) {
        if (pid == null) return
        runCatching { inner.exec(withHead("shell_progres=$pid killtree", withPid = false), KILL_TIMEOUT_SEC) }
    }

    private fun withHead(command: String, withPid: Boolean): String {
        val envFile = ExecutorManager.envFile
        val prelude = ExecutorManager.prelude
        return buildList {
            if (withPid) add(PID_HEAD)
            if (envFile != null) {
                val path = com.tool.script.util.ShellText.quote(envFile)
                add("[ -r $path ] && . $path \$\$")
            }
            if (!prelude.isNullOrBlank()) add(prelude)
            add(command)
        }.joinToString("\n")
    }

    private fun parsePid(line: String): String? =
        if (line.startsWith(PID_MARK)) line.removePrefix(PID_MARK).trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } else null

    private fun ExecResult.withoutPidLine(): ExecResult =
        if (output.startsWith(PID_MARK)) copy(output = output.substringAfter('\n', "")) else this

    private companion object {
        const val PID_MARK = "@pid "
        const val PID_HEAD = "shell_progres=\$\$; export shell_progres; echo \"$PID_MARK\$shell_progres\""
        const val KILL_TIMEOUT_SEC = 5
        const val STOP_WAIT_MS = 2000L
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

    /** Đã có quyền root (su) hay chưa, cập nhật sau mỗi lần [detect]; menu Nguồn dùng để ẩn nhóm thiết bị. */
    @Volatile
    var hasRoot: Boolean = false
        private set

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
        ExecutorManager.hasRoot = hasRoot == true
        if (hasRoot == true) {
            notes += "Backend: root (su)"
            return DetectResult(SuExecutor(), notes).also { current = PreludeExecutor(it.executor) }
        }
        notes += AppText.get(if (hasRoot == null) R.string.exec_su_timeout else R.string.exec_su_denied)

        // 2. ROM, 3. adb: chưa cài đặt (xem README, Bước tiếp theo).
        notes += AppText.get(R.string.exec_rom_adb)

        // 4. Không root.
        notes += AppText.get(R.string.exec_backend_plain)
        return DetectResult(LocalExecutor("any"), notes).also { current = PreludeExecutor(it.executor) }
    }
}
