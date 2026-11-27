package com.tool.script.util

/** Một mục trong CHANGELOG.md: tiêu đề (dòng `## ...`) và các dòng gạch đầu dòng bên dưới. */
class ChangelogEntry(val title: String, val items: List<String>)

/**
 * Đọc CHANGELOG.md. Mỗi mục bắt đầu bằng dòng `## <phiên bản> · <ngày>`, các thay đổi là dòng bắt đầu bằng `- `.
 * Dòng trước mục đầu tiên (tiêu đề `#`, ghi chú, dòng `versionCode=N` của release) và mục không có dòng gạch đầu dòng nào đều bị bỏ qua.
 * Mục mới nhất nằm trên cùng.
 */
object Changelog {
    /** Số mục mới nhất hiện trong phần Nhật ký khi có bản cập nhật. */
    const val MaxShown = 3

    fun parse(text: String): List<ChangelogEntry> {
        val out = ArrayList<ChangelogEntry>()
        var title: String? = null
        var items = ArrayList<String>()
        fun flush() {
            val t = title
            if (t != null && items.isNotEmpty()) out += ChangelogEntry(t, items)
            items = ArrayList()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    title = line.removePrefix("## ").trim()
                }
                title != null && (line.startsWith("- ") || line.startsWith("* ")) -> items += line.drop(2).trim()
            }
        }
        flush()
        return out
    }
}
