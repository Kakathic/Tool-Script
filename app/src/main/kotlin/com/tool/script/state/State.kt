package com.tool.script.state

import androidx.compose.runtime.mutableStateMapOf

/**
 * Kho biến: giá trị item theo id + biến dựng sẵn SDK / ABI / ROOT_MODE (schema mục 8).
 * Dùng snapshot state nên composable đọc biến sẽ tự vẽ lại khi giá trị đổi.
 * Bộ tính điều kiện nằm ở [ConditionEvaluator].
 */
class VariableStore(builtin: Map<String, String>) {
    private val values = mutableStateMapOf<String, String>().apply { putAll(builtin) }

    operator fun get(name: String): String = values[name] ?: ""

    operator fun set(name: String, value: String) {
        values[name] = value
    }

    fun contains(name: String): Boolean = values.containsKey(name)

    fun snapshot(): Map<String, String> = values.toMap()

    fun evaluate(condition: String): Boolean = ConditionEvaluator.evaluate(condition, ::get)
}
