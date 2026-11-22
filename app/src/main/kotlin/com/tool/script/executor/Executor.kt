package com.tool.script.executor

import android.os.Build
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
     * Chạy [command]; [onLine] nhận từng đoạn đầu ra ngay khi lệnh in ra, theo thứ tự đến:
     * mỗi đoạn kết thúc bằng `\n` hoặc `\r` (đoạn cuối có thể không có), nên chữ ghi đè bằng `\r` đến được ngay.
     * `isError` = false là stdout, true là stderr (có thể gọi từ luồng nền).
     * [timeoutSec] = null là không giới hạn thời gian; có giá trị thì quá số giây đó lệnh bị dừng và báo hết giờ.
     */
    suspend fun exec(command: String, timeoutSec: Int? = null, onLine: ((line: String, isError: Boolean) -> Unit)? = null): ExecResult
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
                    val outJoin = LineJoiner(out)
                    val errJoin = LineJoiner(err)
                    val outJob = async {
                        readSegments(process.inputStream) {
                            outJoin.add(it)
                            onLine?.invoke(it, false)
                        }
                    }
                    val errJob = async {
                        readSegments(process.errorStream) {
                            errJoin.add(it)
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
                                process.waitForCompat(timeoutSec.toLong())
                            }
                        }
                    } catch (e: CancellationException) {
                        // Bị hủy (nút Hủy của log): dừng hẳn tiến trình để các luồng đọc đầu ra kết thúc ngay.
                        process.kill()
                        throw e
                    }
                    if (!finished) {
                        timedOut = true
                        process.kill()
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
                if (process.alive()) process.kill()
            }
        }
}

/** Shell root qua libsu. [onLine] nhận từng dòng stdout và stderr (thêm `\n` cuối dòng) ngay khi lệnh in ra; libsu đã tách dòng sẵn nên `\r` giữa chừng không được giữ lại. */
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
                    onLine?.invoke(e + "\n", false)
                }
            }
            val errLines = object : CallbackList<String>(Executor { it.run() }) {
                override fun onAddElement(e: String) {
                    err += e
                    onLine?.invoke(e + "\n", true)
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
 * Gắn mốc thời gian cho từng đoạn đầu ra ngay lúc nhận (stdout và stderr đến từ hai luồng đọc khác nhau),
 * giữ trong bộ đệm ngắn [windowMs], rồi đẩy ra [emit] theo thứ tự mốc thời gian (hòa thì theo số thứ tự đến).
 * Nhờ vậy đoạn đến trễ vài mili giây từ pipe kia vẫn được xếp về đúng chỗ. Mốc thời gian chỉ dùng để xếp, không đưa ra log.
 */
private class ReorderSink(
    private val windowMs: Long,
    private val emit: (String, Boolean) -> Unit,
) {
    private class Item(val ts: Long, val seq: Long, val text: String, val isError: Boolean)

    private val queue = PriorityQueue<Item>(compareBy<Item>({ it.ts }, { it.seq }))
    private var seq = 0L

    @Synchronized
    fun add(text: String, isError: Boolean) {
        queue += Item(System.nanoTime(), seq++, text, isError)
    }

    /** [all] = true: đẩy hết; ngược lại chỉ đẩy các đoạn đã nằm trong bộ đệm quá [windowMs]. */
    @Synchronized
    fun flush(all: Boolean) {
        val limit = System.nanoTime() - windowMs * 1_000_000L
        while (true) {
            val head = queue.peek() ?: break
            if (!all && head.ts > limit) break
            queue.poll()
            emit(head.text, head.isError)
        }
    }
}

/**
 * Thêm vào đầu mọi lệnh: đặt `shell_progres` (PID của shell chạy lệnh), nạp env.sh ([ExecutorManager.envFile]) kèm PID đó làm `$1`,
 * rồi chạy [ExecutorManager.prelude] (nếu có), để chúng luôn chạy trước và lệnh dùng được các biến trong env.sh.
 *
 * Hủy lệnh (nút Hủy của log / banner) và hết giờ: shell chạy lệnh in dòng `@pid N` đầu tiên (không đưa ra log, không nằm trong kết quả);
 * khi coroutine bị hủy hoặc hết giờ, `killtree` được chạy bằng chính backend đó để giết cả cây tiến trình con trước, rồi lệnh gốc mới kết thúc.
 */
private class PreludeExecutor(private val inner: ShellExecutor) : ShellExecutor {
    override val rootMode: String get() = inner.rootMode

    override suspend fun exec(command: String, timeoutSec: Int?, onLine: ((String, Boolean) -> Unit)?): ExecResult = coroutineScope {
        val pid = AtomicReference<String?>(null)
        // stdout / stderr đọc ở hai luồng nên thứ tự đến không chắc đúng: gắn thời gian, đệm ngắn rồi xếp lại trước khi ra log.
        val sink = onLine?.let { ReorderSink(REORDER_WINDOW_MS, it) }
        val flusher = sink?.let { s -> launch(Dispatchers.Default) { while (true) { delay(FLUSH_INTERVAL_MS); s.flush(false) } } }
        // NonCancellable: không để bên trong tự kill tiến trình gốc trước, nếu không các tiến trình con mồ côi (ppid = 1)
        // sẽ không còn tìm thấy từ gốc. Thứ tự đúng: killtree giết cả cây, rồi lệnh gốc tự kết thúc.
        // Hết giờ cũng do đây tính (bên trong chạy không giới hạn) để dùng được killtree.
        val run = async(NonCancellable) {
            inner.exec(withHead(command, withPid = true), null) { line, isError ->
                val p = if (isError) null else parsePid(line)
                if (p != null) pid.compareAndSet(null, p) else sink?.add(line, isError)
            }
        }
        try {
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
                    (partial ?: ExecResult(-1, "", "")).copy(code = -1, timedOut = true).withoutPidLine()
                }
            } catch (e: CancellationException) {
                stop(run, pid.get())
                throw e
            }
        } finally {
            // Dừng bộ đẩy định kỳ và đẩy nốt phần còn đệm, trước khi trả kết quả.
            flusher?.cancel()
            sink?.flush(true)
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
        if (stdout.startsWith(PID_MARK)) copy(stdout = stdout.substringAfter('\n', "")) else this

    private companion object {
        const val PID_MARK = "@pid "
        const val PID_HEAD = "shell_progres=\$\$; export shell_progres; echo \"$PID_MARK\$shell_progres\""
        const val KILL_TIMEOUT_SEC = 5
        const val STOP_WAIT_MS = 2000L
        const val REORDER_WINDOW_MS = 40L
        const val FLUSH_INTERVAL_MS = 15L
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
