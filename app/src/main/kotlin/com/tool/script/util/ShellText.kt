package com.tool.script.util

/** Các hàm xử lý chuỗi lệnh shell, không phụ thuộc Android. */
object ShellText {

    private val VAR_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun isValidVarName(name: String): Boolean = VAR_NAME.matches(name)

    /** Bọc [s] trong nháy đơn để shell không diễn giải ký tự đặc biệt. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Thêm `export NAME='giá trị';` cho từng biến hợp lệ trước [command],
     * để lệnh dùng được `$id` theo schema (mục 8).
     */
    fun withEnv(command: String, vars: Map<String, String>): String {
        val prefix = vars.entries
            .filter { isValidVarName(it.key) }
            .joinToString(separator = "") { "export ${it.key}=${quote(it.value)}; " }
        return prefix + command
    }

    /**
     * Bọc [command] để bỏ stderr. Backend chỉ có một pipe (stderr gộp vào stdout), nên lệnh `get` đọc giá trị
     * phải tự bỏ stderr, không thì cảnh báo của lệnh lẫn vào giá trị. Dùng `{ }` (không phải subshell) để `exit` giữ nguyên nghĩa.
     */
    fun silenceStderr(command: String): String = "{\n$command\n} 2>/dev/null"

    /** Đọc dòng `@progress N` (0-100). Trả về null nếu không phải dòng tiến trình. */
    fun parseProgress(line: String): Int? {
        val t = line.trim()
        if (!t.startsWith("@progress")) return null
        return t.removePrefix("@progress").trim().toIntOrNull()?.coerceIn(0, 100)
    }
}
