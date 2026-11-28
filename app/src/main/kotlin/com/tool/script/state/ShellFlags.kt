package com.tool.script.state

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.tool.script.executor.ExecResult
import com.tool.script.util.ShellText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Kết quả của một lệnh `visible-sh` / `readonly-sh` / `lock-sh`: [on] = điều kiện đúng, [message] = lý do (sau dấu `|`). */
data class FlagResult(val on: Boolean, val message: String?)

/**
 * Chạy các lệnh `-sh` (docs/schema.md mục 9): lệnh in ra stdout `0` hoặc rỗng là sai, `1` hoặc `1|lý do` là đúng.
 * Mỗi lệnh chỉ có một lần theo dõi (khóa theo chuỗi lệnh): chạy khi được hỏi lần đầu, rồi chạy lại mỗi khi
 * một biến trong kho đổi (đợi [DEBOUNCE_MS] để gõ chữ không chạy lệnh từng phím). Lệnh lỗi hoặc hết giờ thì
 * không có kết quả (null): người gọi coi như không khóa, không ẩn.
 */
class ShellFlags(
    private val store: VariableStore,
    private val scope: CoroutineScope,
    private val exec: suspend (String) -> ExecResult,
) {
    private val results = mutableStateMapOf<String, FlagResult>()
    private val started = HashSet<String>()
    private val refreshTicks = mutableStateMapOf<String, Int>()

    /** Chạy lại các lệnh `-sh` [commands] (khóa `refresh`, chỉ các lệnh của item trong cùng trang). */
    fun refresh(commands: List<String>) {
        commands.forEach { refreshTicks[it] = (refreshTicks[it] ?: 0) + 1 }
    }

    /** Kết quả mới nhất của [command]; null khi chưa chạy xong hoặc lệnh lỗi. Gọi lần đầu sẽ bắt đầu theo dõi lệnh. */
    fun get(command: String): FlagResult? {
        request(command)
        return results[command]
    }

    @Synchronized
    private fun request(command: String) {
        if (!started.add(command)) return
        scope.launch {
            var first = true
            snapshotFlow { store.snapshot() to (refreshTicks[command] ?: 0) }.distinctUntilChanged().collectLatest { (vars, _) ->
                if (!first) delay(DEBOUNCE_MS)
                first = false
                val result = exec(ShellText.silenceStderr(ShellText.withEnv(command, vars)))
                if (result.ok) results[command] = parse(result.output) else results.remove(command)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L

        fun parse(output: String): FlagResult {
            val line = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return FlagResult(false, null)
            val bar = line.indexOf('|')
            val head = (if (bar >= 0) line.substring(0, bar) else line).trim()
            val message = if (bar >= 0) line.substring(bar + 1).trim().takeIf { it.isNotEmpty() } else null
            return FlagResult(head == "1" || head.equals("true", ignoreCase = true), message)
        }
    }
}
