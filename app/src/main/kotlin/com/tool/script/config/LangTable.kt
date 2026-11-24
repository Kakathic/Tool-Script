package com.tool.script.config

import com.tool.script.R
import com.tool.script.util.AppText

// Bảng chuỗi `@khóa` theo docs/schema.md: parse dạng bash (khóa=giá trị) và thay `@khóa` trong chuỗi hiển thị.
class LangTable(private val map: Map<String, String>) {

    fun resolve(text: String): String {
        if (map.isEmpty() || '@' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '@' || (i > 0 && text[i - 1].let { it == '@' || it == '_' || it.isLetterOrDigit() })) {
                out.append(c)
                i++
                continue
            }
            val braced = i + 1 < text.length && text[i + 1] == '{'
            val start = if (braced) i + 2 else i + 1
            var end = start
            if (braced) {
                while (end < text.length && text[end] != '}') end++
                if (end >= text.length) {
                    out.append(c)
                    i++
                    continue
                }
            } else {
                while (end < text.length && isKeyChar(text[end], end == start)) end++
            }
            val key = text.substring(start, end)
            val value = if (key.isEmpty()) null else map[key]
            val next = if (braced) end + 1 else end
            if (value == null) {
                out.append(text, i, next)
            } else {
                out.append(value)
            }
            i = next
        }
        return out.toString()
    }

    companion object {
        val EMPTY = LangTable(emptyMap())

        private val KEY_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        private fun isKeyChar(c: Char, first: Boolean): Boolean =
            c == '_' || c in 'a'..'z' || c in 'A'..'Z' || (!first && c in '0'..'9')

        /** Mỗi dòng `khóa=giá trị`; dòng sai dạng bị bỏ qua kèm cảnh báo. Khóa trùng thì dòng sau thắng. */
        fun parse(text: String, source: String, warnings: MutableList<String>): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            text.lineSequence().forEachIndexed { index, rawLine ->
                var line = rawLine.trim().removePrefix("﻿")
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                if (line.startsWith("export ")) line = line.removePrefix("export ").trimStart()
                val eq = line.indexOf('=')
                val key = if (eq > 0) line.substring(0, eq).trim() else ""
                if (!KEY_REGEX.matches(key)) {
                    warnings += AppText.get(R.string.cfg_lang_bad_line, source, index + 1)
                    return@forEachIndexed
                }
                result[key] = parseValue(line.substring(eq + 1).trim())
            }
            return result
        }

        private fun parseValue(v: String): String {
            if (v.isEmpty()) return ""
            return when (v[0]) {
                '\'' -> {
                    val close = v.indexOf('\'', 1)
                    if (close < 0) v.substring(1) else v.substring(1, close)
                }
                '"' -> {
                    val sb = StringBuilder()
                    var i = 1
                    while (i < v.length) {
                        val c = v[i]
                        if (c == '"') break
                        if (c == '\\' && i + 1 < v.length) {
                            when (val n = v[i + 1]) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                '"', '\\', '$' -> sb.append(n)
                                else -> sb.append(c).append(n)
                            }
                            i += 2
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    sb.toString()
                }
                else -> {
                    val comment = v.indexOf(" #")
                    (if (comment >= 0) v.substring(0, comment) else v).trimEnd()
                }
            }
        }
    }
}
