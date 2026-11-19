package com.tool.script.boot

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tool.script.config.ConfigParseException
import com.tool.script.config.ConfigRepository
import com.tool.script.config.Item
import com.tool.script.config.ItemType
import com.tool.script.config.LoadedConfig
import com.tool.script.executor.ExecutorManager
import com.tool.script.state.VariableStore
import com.tool.script.util.ShellText
import com.tool.script.util.Util
import com.tool.script.util.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Khởi động (docs/schema.md mục 11):
//   1. Xin quyền bộ nhớ (từ chối -> thoát app)        - thanh tiến trình vô hạn   (làm ở UI, xem BootScreen)
//   2. Dò backend (su -> ROM -> adb -> thường), copy script từ assets
//   3. Chạy script, đọc dòng "@progress N"            - thanh tiến trình hiện %
//   4. Nạp TOML từ assets                             - thanh tiến trình vô hạn trở lại
//   5. Vào màn hình chính (script lỗi vẫn vào, có banner cảnh báo)

enum class BootStage { Permission, Detect, Script, Config, Done }

/** Kết quả khởi động, đủ để vẽ màn hình chính. */
class BootResult(
    val config: LoadedConfig,
    val store: VariableStore,
    /** Khác null nếu script khởi động lỗi: app vẫn vào, có banner cảnh báo. */
    val scriptError: String?,
    val bootLog: List<String>,
)

data class BootState(
    val stage: BootStage = BootStage.Permission,
    /** 0..1, null = thanh tiến trình vô hạn. */
    val progress: Float? = null,
    /** Tối đa 5 dòng log gần nhất. */
    val logs: List<String> = emptyList(),
    val result: BootResult? = null,
    /** Lỗi không vào được app (vd main.toml sai cú pháp): chuyển sang Trung tâm lỗi. */
    val fatal: String? = null,
)

class BootViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(BootState())
    val state: StateFlow<BootState> = _state.asStateFlow()

    private var started = false
    private val allLogs = mutableListOf<String>()

    /** Gọi sau khi đã có quyền bộ nhớ. */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { run() }
    }

    private fun log(line: String) {
        synchronized(allLogs) {
            allLogs += line
            val recent = allLogs.takeLast(LOG_LINES)
            _state.update { it.copy(logs = recent) }
        }
    }

    private suspend fun run() {
        val app = getApplication<Application>()

        // 2. Dò backend
        _state.update { it.copy(stage = BootStage.Detect, progress = null) }
        log("Đang dò quyền hệ thống...")
        val detect = ExecutorManager.detect()
        detect.notes.forEach(::log)
        val executor = detect.executor

        // 3. Chạy script khởi động (chép từ assets, chạy bằng `sh`)
        _state.update { it.copy(stage = BootStage.Script, progress = 0f) }
        var scriptError: String? = null
        try {
            val script = withContext(Dispatchers.IO) {
                Util.copyAsset(app, "scripts/init.sh", File(app.filesDir, "scripts/init.sh"))
            }
            val result = executor.exec("sh ${ShellText.quote(script.absolutePath)}", timeoutSec = SCRIPT_TIMEOUT_SEC) { line ->
                val p = ShellText.parseProgress(line)
                if (p != null) {
                    _state.update { it.copy(progress = p / 100f) }
                } else if (line.isNotBlank()) {
                    log(line.trim())
                }
            }
            if (!result.ok) {
                scriptError = buildString {
                    append("init.sh ")
                    append(if (result.timedOut) "hết thời gian chờ" else "thoát với mã ${result.code}")
                    if (result.stderr.isNotBlank()) append("\n").append(result.stderr)
                }
                log("Script khởi động lỗi")
            }
        } catch (e: Exception) {
            scriptError = "Không chạy được init.sh: ${e.message ?: e}"
            log("Script khởi động lỗi")
        }

        // 4. Nạp TOML
        _state.update { it.copy(stage = BootStage.Config, progress = null) }
        log("Đang nạp giao diện...")
        val config = try {
            withContext(Dispatchers.IO) { ConfigRepository.load(app) }
        } catch (e: ConfigParseException) {
            _state.update { it.copy(fatal = e.message ?: "Lỗi nạp cấu hình") }
            return
        } catch (e: Exception) {
            _state.update { it.copy(fatal = "Lỗi nạp cấu hình: ${e.message ?: e}") }
            return
        }

        // 5. Dựng kho biến rồi vào màn hình chính
        val builtin = mapOf(
            "SDK" to Build.VERSION.SDK_INT.toString(),
            "ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""),
            "ROOT_MODE" to executor.rootMode,
        )
        val defaults = LinkedHashMap<String, String>()
        config.pages.forEach { collectDefaults(it.items, defaults) }
        val store = VariableStore(defaults + builtin)

        val bootLog = synchronized(allLogs) { allLogs.toList() }
        _state.update {
            it.copy(
                stage = BootStage.Done,
                progress = 1f,
                result = BootResult(config, store, scriptError, bootLog),
            )
        }
    }

    private fun collectDefaults(items: List<Item>, out: MutableMap<String, String>) {
        for (item in items) {
            val id = item.id
            when (item.type) {
                ItemType.Switch -> if (id != null) out[id] = "0"
                ItemType.Input -> if (id != null) out[id] = item.default.orEmpty()
                ItemType.Select ->
                    if (id != null) out[id] = item.default ?: item.options.firstOrNull()?.value.orEmpty()
                ItemType.Slider ->
                    if (id != null) out[id] = formatNumber(item.default?.toFloatOrNull() ?: item.min)
                ItemType.Group -> collectDefaults(item.children, out)
                else -> Unit
            }
        }
    }

    private companion object {
        const val LOG_LINES = 5
        const val SCRIPT_TIMEOUT_SEC = 60
    }
}
