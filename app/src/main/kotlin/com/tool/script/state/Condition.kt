package com.tool.script.state

/**
 * Bộ tính điều kiện cho `visible_if` / `lock` (docs/schema.md mục 8).
 *
 * Cú pháp: `biến == giá_trị`, `biến != giá_trị`, nối bằng `&&` và `||` (`&&` ưu tiên hơn `||`).
 * Chưa hỗ trợ dấu ngoặc. Vế trái là tên biến (không có `$`), vế phải là giá trị chữ,
 * có thể đặt trong nháy đơn hoặc nháy kép. Một vế đơn không có toán tử (vd `game_mode`)
 * đúng khi biến khác rỗng và khác `0` / `false`.
 * Biến chưa có giá trị được coi là chuỗi rỗng.
 */
object ConditionEvaluator {

    fun evaluate(expression: String, lookup: (String) -> String): Boolean {
        val expr = expression.trim()
        if (expr.isEmpty()) return false
        return expr.split("||").any { orPart ->
            orPart.split("&&").all { atom -> evalAtom(atom.trim(), lookup) }
        }
    }

    private fun evalAtom(atom: String, lookup: (String) -> String): Boolean {
        if (atom.isEmpty()) return false
        val neq = atom.indexOf("!=")
        val eq = atom.indexOf("==")
        return when {
            neq >= 0 && (eq < 0 || neq < eq) ->
                lookup(atom.substring(0, neq).trim()) != unquote(atom.substring(neq + 2))
            eq >= 0 ->
                lookup(atom.substring(0, eq).trim()) == unquote(atom.substring(eq + 2))
            else -> {
                val v = lookup(atom).trim()
                v.isNotEmpty() && v != "0" && !v.equals("false", ignoreCase = true)
            }
        }
    }

    private fun unquote(raw: String): String {
        val s = raw.trim()
        if (s.length >= 2) {
            val first = s.first()
            if ((first == '"' || first == '\'') && s.last() == first) return s.substring(1, s.length - 1)
        }
        return s
    }
}
