package com.tool.script.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Chế độ chủ đề người dùng chọn. */
enum class ThemeMode(val key: String, val label: String) {
    System("system", "Theo hệ thống"),
    Light("light", "Sáng"),
    Dark("dark", "Tối"),
}

/** Lưu lựa chọn chủ đề vào SharedPreferences để lần sau mở app vẫn giữ. */
object ThemePrefs {
    private const val FILE = "theme_prefs"
    private const val KEY_MODE = "mode"

    fun load(context: Context): ThemeMode {
        val key = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return ThemeMode.entries.firstOrNull { it.key == key } ?: ThemeMode.System
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.key).apply()
    }
}

/** Chủ đề đang dùng là tối hay sáng (đã tính cả trường hợp "Theo hệ thống"). */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun ToolScriptTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val colors = if (isDarkTheme(mode)) darkColorScheme() else lightColorScheme()
    MiuixTheme(colors = colors, content = content)
}
