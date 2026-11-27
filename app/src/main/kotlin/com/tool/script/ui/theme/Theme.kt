package com.tool.script.ui.theme

import com.tool.script.R
import androidx.annotation.StringRes
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Các chế độ chủ đề Miuix có sẵn ([ColorSchemeMode]).
 * Nhóm "Màu động" (Monet) lấy màu từ hình nền hệ thống (Android 12+); máy cũ hơn tự dùng màu mặc định.
 */
enum class ThemeMode(val key: String, @StringRes val labelRes: Int, val monet: Boolean) {
    System("system", R.string.theme_system, false),
    Light("light", R.string.theme_light, false),
    Dark("dark", R.string.theme_dark, false),
    MonetSystem("monet_system", R.string.theme_monet_system, true),
    MonetLight("monet_light", R.string.theme_monet_light, true),
    MonetDark("monet_dark", R.string.theme_monet_dark, true),
    ;

    /** Nhánh cơ bản (Hệ thống / Sáng / Tối), bỏ phần Màu động. */
    val base: ThemeMode
        get() = when (this) {
            MonetSystem -> System
            MonetLight -> Light
            MonetDark -> Dark
            else -> this
        }

    /** Cùng nhánh cơ bản nhưng bật / tắt Màu động. */
    fun withMonet(enabled: Boolean): ThemeMode = when (base) {
        System -> if (enabled) MonetSystem else System
        Light -> if (enabled) MonetLight else Light
        else -> if (enabled) MonetDark else Dark
    }
}

/** Lưu lựa chọn chủ đề vào SharedPreferences để lần sau mở app vẫn giữ. */
object ThemePrefs {
    private const val FILE = "theme_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_HAPTIC = "haptic"
    private const val KEY_BLUR = "blur"
    private const val KEY_GLASS = "glass"

    fun load(context: Context): ThemeMode {
        val key = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return ThemeMode.entries.firstOrNull { it.key == key } ?: ThemeMode.System
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.key).apply()
    }

    fun loadHaptic(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_HAPTIC, true)

    fun saveHaptic(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    /** Làm mờ nền thanh trên / thanh tab; mặc định bật (chỉ có tác dụng trên Android 13+). */
    fun loadBlur(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_BLUR, true)

    fun saveBlur(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_BLUR, enabled).apply()
    }

    /**
     * Giao diện kính (miuix-glass); mặc định bật từ Android 13 (API 33) trở lên, nơi hỗ trợ shader làm mờ, và tắt ở máy thấp hơn.
     * Chỉ là giá trị mặc định khi người dùng chưa tự chọn; đã bật / tắt trong Cài đặt thì giữ lựa chọn đó.
     */
    fun loadGlass(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_GLASS, Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

    fun saveGlass(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_GLASS, enabled).apply()
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
fun ToolScriptTheme(mode: ThemeMode = ThemeMode.System, haptic: Boolean = true, content: @Composable () -> Unit) {
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
    val feedback = if (haptic) LocalHapticFeedback.current else NoHapticFeedback
    CompositionLocalProvider(LocalHapticFeedback provides feedback, LocalHapticEnabled provides haptic, LocalMonetEnabled provides mode.monet) {
        MiuixTheme(controller = controller, content = content)
    }
}

// Tắt rung: các component Miuix lấy rung từ LocalHapticFeedback nên thay bằng bản không làm gì.
private object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

val LocalHapticEnabled = staticCompositionLocalOf { true }

/** Đang dùng giao diện kính (đã tính máy có hỗ trợ blur): các thành phần đọc giá trị này để chọn bản glass. */
val LocalGlassEnabled = staticCompositionLocalOf { false }

/** Đang bật Màu động (Monet) hay không; để các thành phần chỉnh lại màu mà Miuix pha xám ở chế độ này. */
val LocalMonetEnabled = staticCompositionLocalOf { false }
