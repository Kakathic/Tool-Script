package com.tool.script.util

/**
 * Bộ dựng văn bản log kiểu terminal tối giản, không phụ thuộc Android.
 *
 * Hỗ trợ: `\n` xuống dòng, `\r` về đầu dòng (chữ in sau đó ghi đè), `\b`, `\t`,
 * màu và kiểu chữ SGR (`ESC[...m`), xóa dòng (`ESC[K`, `ESC[1K`, `ESC[2K`), xóa màn hình (`ESC[2J`, `ESC[3J`).
 * Các mã di chuyển con trỏ và mọi mã điều khiển khác bị bỏ qua (không hiện ra chữ rác).
 *
 * Mọi hàm public đều khóa, nên stdout / stderr ghi từ luồng nền còn giao diện đọc [snapshot] cùng lúc vẫn an toàn.
 */
class AnsiTerminal(private val maxLines: Int = 2000) {

    /** Kiểu của một ô chữ. Màu là 0xRRGGBB, null là màu mặc định của giao diện; [palette] là chỉ số 0-15 của bảng màu chuẩn. */
    data class Style(
        val fg: Color? = null,
        val bg: Color? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val inverse: Boolean = false,
        val error: Boolean = false,
    ) {
        companion object {
            val DEFAULT = Style()
        }
    }

    /** Màu ANSI: [rgb] có giá trị thì là màu thật (256 màu / truecolor), ngược lại [palette] là chỉ số 0-15 để giao diện tự chọn bảng màu. */
    data class Color(val palette: Int = -1, val rgb: Int = 0) {
        val isPalette: Boolean get() = palette >= 0
    }

    /** Một đoạn chữ liên tiếp cùng kiểu trong một dòng. */
    class Run(val text: String, val style: Style)

    private class Row {
        val chars = StringBuilder()
        val styles = ArrayList<Style>()

        val length: Int get() = chars.length

        fun truncate(n: Int) {
            if (n >= chars.length) return
            chars.setLength(n)
            while (styles.size > n) styles.removeAt(styles.size - 1)
        }
    }

    private class StreamState {
        var mode = MODE_TEXT
        val params = StringBuilder()
    }

    private var rows = ArrayList<Row>().apply { add(Row()) }
    private var col = 0
    private var sgr = Style.DEFAULT
    private var normalStyle = Style.DEFAULT
    private var errorStyle = Style.DEFAULT.copy(error = true)
    private val streams = arrayOf(StreamState(), StreamState())

    /** Ghi [text] (một đoạn đầu ra bất kỳ, có thể cắt giữa chừng mã escape) từ stdout ([isError] = false) hoặc stderr. */
    @Synchronized
    fun write(text: String, isError: Boolean = false) {
        val st = streams[if (isError) 1 else 0]
        for (ch in text) feed(st, ch, if (isError) errorStyle else normalStyle)
    }

    /** Xóa toàn bộ nội dung, giữ kiểu chữ hiện tại. */
    @Synchronized
    fun clear() {
        rows = ArrayList<Row>().apply { add(Row()) }
        col = 0
    }

    /** Các dòng hiện có, mỗi dòng là danh sách đoạn cùng kiểu; bỏ các dòng trống ở cuối. */
    @Synchronized
    fun snapshot(): List<List<Run>> {
        var last = rows.size
        while (last > 0 && rows[last - 1].chars.isBlank()) last--
        val out = ArrayList<List<Run>>(last)
        for (i in 0 until last) out += runsOf(rows[i])
        return out
    }

    /** Văn bản thuần (không mã ANSI) theo đúng nội dung đang hiện. */
    @Synchronized
    fun plainText(): String {
        var last = rows.size
        while (last > 0 && rows[last - 1].chars.isBlank()) last--
        return (0 until last).joinToString("\n") { rows[it].chars.toString() }
    }

    private fun runsOf(row: Row): List<Run> {
        if (row.length == 0) return emptyList()
        val runs = ArrayList<Run>()
        var start = 0
        for (i in 1..row.length) {
            if (i == row.length || row.styles[i] != row.styles[start]) {
                runs += Run(row.chars.substring(start, i), row.styles[start])
                start = i
            }
        }
        return runs
    }

    private fun feed(st: StreamState, ch: Char, cell: Style) {
        when (st.mode) {
            MODE_ESC -> {
                when (ch) {
                    '[' -> { st.mode = MODE_CSI; st.params.setLength(0) }
                    ']' -> st.mode = MODE_OSC
                    '(', ')', '*', '+', '#', '%' -> st.mode = MODE_SKIP_ONE
                    ESC -> Unit
                    else -> st.mode = MODE_TEXT
                }
            }
            MODE_SKIP_ONE -> st.mode = MODE_TEXT
            MODE_OSC -> {
                if (ch == BEL) st.mode = MODE_TEXT
                else if (ch == ESC) st.mode = MODE_OSC_ESC
            }
            MODE_OSC_ESC -> st.mode = MODE_TEXT
            MODE_CSI -> {
                if (ch in '@'..'~') {
                    st.mode = MODE_TEXT
                    csi(st.params.toString(), ch)
                } else if (ch == ESC) {
                    st.mode = MODE_ESC
                } else if (st.params.length < MAX_PARAMS) {
                    st.params.append(ch)
                } else {
                    st.mode = MODE_TEXT
                }
            }
            else -> text(st, ch, cell)
        }
    }

    private fun text(st: StreamState, ch: Char, cell: Style) {
        when (ch) {
            ESC -> st.mode = MODE_ESC
            '\n' -> newLine()
            '\r' -> col = 0
            '\b' -> if (col > 0) col--
            '\t' -> repeat(8 - col % 8) { put(' ', cell) }
            else -> if (ch >= ' ' && ch != '\u007F') put(ch, cell)
        }
    }

    private fun newLine() {
        rows += Row()
        while (rows.size > maxLines) rows.removeAt(0)
        col = 0
    }

    private fun put(ch: Char, style: Style) {
        val row = rows[rows.size - 1]
        while (row.length < col) {
            row.chars.append(' ')
            row.styles += Style.DEFAULT
        }
        if (col < row.length) {
            row.chars.setCharAt(col, ch)
            row.styles[col] = style
        } else {
            row.chars.append(ch)
            row.styles += style
        }
        col++
    }

    private fun csi(params: String, final: Char) {
        when (final) {
            'm' -> sgr(params)
            'K' -> eraseLine(params.toIntOrNull() ?: 0)
            'J' -> when (params.toIntOrNull() ?: 0) {
                0 -> eraseLine(0)
                2, 3 -> clear()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val row = rows[rows.size - 1]
        when (mode) {
            0 -> row.truncate(col)
            1 -> for (i in 0..minOf(col, row.length - 1)) {
                row.chars.setCharAt(i, ' ')
                row.styles[i] = Style.DEFAULT
            }
            2 -> row.truncate(0)
        }
    }

    private fun sgr(params: String) {
        val p = if (params.isEmpty()) listOf(0) else params.split(';', ':').map { it.toIntOrNull() ?: 0 }
        var s = sgr
        var i = 0
        while (i < p.size) {
            val c = p[i]
            when (c) {
                0 -> s = Style.DEFAULT
                1 -> s = s.copy(bold = true)
                3 -> s = s.copy(italic = true)
                4 -> s = s.copy(underline = true)
                7 -> s = s.copy(inverse = true)
                22 -> s = s.copy(bold = false)
                23 -> s = s.copy(italic = false)
                24 -> s = s.copy(underline = false)
                27 -> s = s.copy(inverse = false)
                in 30..37 -> s = s.copy(fg = Color(palette = c - 30))
                39 -> s = s.copy(fg = null)
                in 40..47 -> s = s.copy(bg = Color(palette = c - 40))
                49 -> s = s.copy(bg = null)
                in 90..97 -> s = s.copy(fg = Color(palette = c - 90 + 8))
                in 100..107 -> s = s.copy(bg = Color(palette = c - 100 + 8))
                38, 48 -> {
                    val color: Color?
                    when (p.getOrNull(i + 1)) {
                        5 -> {
                            color = p.getOrNull(i + 2)?.let { color256(it) }
                            i += 2
                        }
                        2 -> {
                            val r = p.getOrNull(i + 2)
                            val g = p.getOrNull(i + 3)
                            val b = p.getOrNull(i + 4)
                            color = if (r != null && g != null && b != null) {
                                Color(rgb = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255))
                            } else null
                            i += 4
                        }
                        else -> color = null
                    }
                    if (color != null) s = if (c == 38) s.copy(fg = color) else s.copy(bg = color)
                }
            }
            i++
        }
        sgr = s
        normalStyle = s
        errorStyle = s.copy(error = true)
    }

    private fun color256(n: Int): Color? = when {
        n in 0..15 -> Color(palette = n)
        n in 16..231 -> {
            val v = n - 16
            val level = { x: Int -> if (x == 0) 0 else 55 + 40 * x }
            Color(rgb = (level(v / 36) shl 16) or (level(v / 6 % 6) shl 8) or level(v % 6))
        }
        n in 232..255 -> {
            val g = 8 + 10 * (n - 232)
            Color(rgb = (g shl 16) or (g shl 8) or g)
        }
        else -> null
    }

    private companion object {
        const val ESC = '\u001B'
        const val BEL = '\u0007'
        const val MAX_PARAMS = 64

        const val MODE_TEXT = 0
        const val MODE_ESC = 1
        const val MODE_CSI = 2
        const val MODE_OSC = 3
        const val MODE_OSC_ESC = 4
        const val MODE_SKIP_ONE = 5
    }
}
