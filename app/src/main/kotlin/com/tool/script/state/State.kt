package com.tool.script.state

import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.util.concurrent.Executors

/**
 * Lưu giá trị item ra file: mỗi item có `id` một file, tên file = id, nằm trong [dir] (`files/state`, cũng là
 * `$STATE_DIR` trong script nên `glog <id>` đọc được). Nội dung = giá trị + xuống dòng, cùng định dạng với
 * `slog`. Ghi chạy nền trên một luồng riêng (giữ đúng thứ tự); ghi lỗi thì bỏ qua, không chặn app.
 */
class StateFiles(private val dir: File) {
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "state-files").apply { isDaemon = true } }

    /** Giá trị đã lưu của [id], null nếu chưa có file / id không hợp lệ / đọc lỗi. */
    fun read(id: String): String? {
        if (!isValidId(id)) return null
        return try {
            val file = File(dir, id)
            if (file.isFile) file.readText(Charsets.UTF_8).removeSuffix("\n").removeSuffix("\r") else null
        } catch (_: Exception) {
            null
        }
    }

    /** Ghi nền, dùng khi giá trị đổi lúc app đang chạy. */
    fun write(id: String, value: String) {
        if (!isValidId(id)) return
        writer.execute { writeNow(id, value) }
    }

    /** Ghi ngay trên luồng gọi (lúc khởi động, đã ở luồng IO). Ghi ra file tạm rồi đổi tên để script không đọc phải file dở. */
    fun writeNow(id: String, value: String) {
        if (!isValidId(id)) return
        try {
            dir.mkdirs()
            val target = File(dir, id)
            val tmp = File(dir, ".$id.tmp")
            tmp.writeText(value + "\n", Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(value + "\n", Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        // id cũng là tên file nên chỉ nhận ký tự an toàn (không có dấu / hay ..).
        private val VALID_ID = Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")

        fun isValidId(id: String): Boolean = VALID_ID.matches(id)
    }
}

/**
 * Kho biến: giá trị item theo id + biến dựng sẵn SDK / ABI / ROOT_MODE (schema mục 8).
 * Dùng snapshot state nên composable đọc biến sẽ tự vẽ lại khi giá trị đổi.
 * Bộ tính điều kiện nằm ở [ConditionEvaluator].
 *
 * [persistIds] là các id được lưu ra file qua [files]: mỗi lần giá trị đổi thì ghi lại.
 * Biến dựng sẵn và item không có id không được lưu.
 */
class VariableStore(
    builtin: Map<String, String>,
    private val files: StateFiles? = null,
    private val persistIds: Set<String> = emptySet(),
) {
    private val values = mutableStateMapOf<String, String>().apply { putAll(builtin) }

    operator fun get(name: String): String = values[name] ?: ""

    operator fun set(name: String, value: String) {
        val old = values[name]
        values[name] = value
        if (files != null && old != value && name in persistIds) files.write(name, value)
    }

    fun contains(name: String): Boolean = values.containsKey(name)

    fun snapshot(): Map<String, String> = values.toMap()

    fun evaluate(condition: String): Boolean = ConditionEvaluator.evaluate(condition, ::get)
}
