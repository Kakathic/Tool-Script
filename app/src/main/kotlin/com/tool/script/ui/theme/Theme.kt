package com.tool.script.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Các chế độ chủ đề Miuix có sẵn ([ColorSchemeMode]).
 * Nhóm "Màu động" (Monet) lấy màu từ hình nền hệ thống (Android 12+); máy cũ hơn tự dùng màu mặc định.
 */
enum class ThemeMode(val key: String, val label: String, val monet: Boolean) {
    System("system", "Theo hệ thống", false),
    Light("light", "Sáng", false),
    Dark("dark", "Tối", false),
    MonetSystem("monet_system", "Màu động (theo hệ thống)", true),
    MonetLight("monet_light", "Màu động - Sáng", true),
    MonetDark("monet_dark", "Màu động - Tối", true),
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

/** Chủ đề đang dùng là tối hay sáng (đã tính cả các chế độ "theo hệ thống"). */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System, ThemeMode.MonetSystem -> isSystemInDarkTheme()
    ThemeMode.Light, ThemeMode.MonetLight -> false
    ThemeMode.Dark, ThemeMode.MonetDark -> true
}

@Composable
fun ToolScriptTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val controller = remember(mode, systemDark) {
        when (mode) {
            ThemeMode.System -> ThemeController(ColorSchemeMode.System)
            ThemeMode.Light -> ThemeController(ColorSchemeMode.Light)
            ThemeMode.Dark -> ThemeController(ColorSchemeMode.Dark)
            ThemeMode.MonetSystem -> ThemeController(ColorSchemeMode.MonetSystem, isDark = systemDark)
            ThemeMode.MonetLight -> ThemeController(ColorSchemeMode.MonetLight)
            ThemeMode.MonetDark -> ThemeController(ColorSchemeMode.MonetDark)
        }
    }
    MiuixTheme(controller = controller, content = content)
}
