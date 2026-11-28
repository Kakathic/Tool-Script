package com.tool.script.ui.main

/** Số ô tab tối đa trên thanh tab (docs/schema.md mục 5). */
const val MAX_VISIBLE_TABS = 5

/**
 * Tính vị trí đầu của cửa sổ tab sau khi chọn tab [selected].
 * - Chọn tab cuối cửa sổ và còn tab phía sau: trượt sang phải 1 ô.
 * - Chọn tab đầu cửa sổ và còn tab phía trước: trượt sang trái 1 ô.
 * - Tab được chọn luôn nằm trong cửa sổ.
 */
fun nextWindowStart(selected: Int, start: Int, visible: Int, total: Int): Int {
    if (total <= visible) return 0
    var s = start
    if (selected < s) s = selected
    if (selected > s + visible - 1) s = selected - visible + 1
    if (selected == s + visible - 1 && s + visible < total) {
        s += 1
    } else if (selected == s && s > 0) {
        s -= 1
    }
    return s.coerceIn(0, total - visible)
}
