package com.tool.script

import com.tool.script.R
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.tool.script.boot.BootViewModel
import com.tool.script.boot.SplashScreen
import com.tool.script.crash.CrashActivity
import com.tool.script.ui.feedback.LogCenter
import com.tool.script.ui.main.MainScreen
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.ui.theme.ThemePrefs
import com.tool.script.ui.theme.ToolScriptTheme
import com.tool.script.ui.theme.isDarkTheme
import com.tool.script.util.AppLanguage
import com.tool.script.util.LanguagePrefs
import com.tool.script.util.Util

class MainActivity : ComponentActivity() {
    /** Mã log đã ẩn cần mở lại (do chạm vào thông báo); MainScreen mở xong thì xóa. */
    private var openLogId by mutableStateOf<Int?>(null)

    private fun readLogId(intent: Intent?): Int? =
        intent?.getIntExtra(LogCenter.EXTRA_LOG_ID, -1)?.takeIf { it >= 0 }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLogId(intent)?.let { openLogId = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Chỉ lần mở mới: Activity dựng lại (xoay màn hình) thì Intent cũ còn nguyên, không mở lại log lần nữa.
        if (savedInstanceState == null) openLogId = readLogId(intent)
        setContent {
            val activityContext = LocalContext.current
            val systemConfig = LocalConfiguration.current
            // Đọc lại mỗi khi cấu hình đổi (Android 13+: người dùng có thể đổi ngôn ngữ app từ Cài đặt hệ thống).
            var language by remember(systemConfig) { mutableStateOf(LanguagePrefs.load(activityContext)) }
            // Android 12 trở xuống: bọc Context + Configuration của Compose để đổi ngôn ngữ ngay mà không dựng lại Activity
            // (lệnh shell đang chạy gắn với màn hình này). Android 13+: hệ thống tự áp dụng nên không cần bọc.
            val context = remember(language, activityContext, systemConfig) { LanguagePrefs.localize(activityContext, language) }
            val localeProviders: Array<ProvidedValue<*>> = if (Build.VERSION.SDK_INT < 33) {
                arrayOf(LocalContext provides context, LocalConfiguration provides context.resources.configuration)
            } else {
                emptyArray()
            }
            var themeMode by remember { mutableStateOf(ThemePrefs.load(context)) }
            var hapticEnabled by remember { mutableStateOf(ThemePrefs.loadHaptic(context)) }
            var blurEnabled by remember { mutableStateOf(ThemePrefs.loadBlur(context)) }
            val dark = isDarkTheme(themeMode)
            // Thanh trạng thái / thanh điều hướng đổi màu icon theo chủ đề app (có thể khác chủ đề hệ thống).
            DisposableEffect(dark) {
                val barStyle = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose { }
            }
            // Miuix (OverlayDialog) cần NavigationEventDispatcher để bắt nút Back. ComponentActivity (activity >= 1.12)
            // chính là NavigationEventDispatcherOwner; cung cấp rõ ràng để không phụ thuộc việc tìm qua view tree.
            CompositionLocalProvider(*localeProviders, LocalNavigationEventDispatcherOwner provides this@MainActivity) {
                ToolScriptTheme(themeMode, hapticEnabled) {
                    AppRoot(
                        themeMode = themeMode,
                        hapticEnabled = hapticEnabled,
                        onHapticChange = {
                            hapticEnabled = it
                            ThemePrefs.saveHaptic(context, it)
                        },
                        blurEnabled = blurEnabled,
                        onBlurChange = {
                            blurEnabled = it
                            ThemePrefs.saveBlur(context, it)
                        },
                        onThemeChange = {
                            themeMode = it
                            ThemePrefs.save(context, it)
                        },
                        language = language,
                        onLanguageChange = {
                            language = it
                            LanguagePrefs.save(activityContext, it)
                        },
                        onExit = { finish() },
                        openLogId = openLogId,
                        onLogOpened = { openLogId = null },
                    )
                }
            }
        }
    }
}

/**
 * Luồng: Splash -> xin quyền bộ nhớ (từ chối thì thoát) -> dò backend, chạy script -> nạp TOML -> màn hình chính.
 */
@Composable
private fun AppRoot(
    themeMode: ThemeMode,
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onExit: () -> Unit,
    openLogId: Int?,
    onLogOpened: () -> Unit,
) {
    val context = LocalContext.current
    val vm: BootViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Util.hasAllFilesAccess(context)) vm.start() else onExit()
    }
    // Android 7 đến 10: hộp thoại quyền ghi bộ nhớ thường.
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else onExit()
    }
    LaunchedEffect(Unit) {
        if (Util.hasAllFilesAccess(context)) {
            vm.start()
        } else if (Util.needsLegacyStoragePermission()) {
            legacyLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            try {
                permissionLauncher.launch(Util.allFilesAccessIntent(context))
            } catch (_: Exception) {
                // Máy không mở được màn hình cấp quyền riêng cho app: mở màn hình chung.
                permissionLauncher.launch(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                )
            }
        }
    }

    // Lỗi nghiêm trọng (vd main.toml sai cú pháp): sang Trung tâm lỗi rồi đóng màn hình này.
    val fatal = state.fatal
    LaunchedEffect(fatal) {
        if (fatal != null) {
            CrashActivity.launch(context, context.getString(R.string.crash_config_error), fatal, fatal = true)
            onExit()
        }
    }

    val result = state.result
    if (result != null) {
        MainScreen(
            result = result,
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            hapticEnabled = hapticEnabled,
            onHapticChange = onHapticChange,
            blurEnabled = blurEnabled,
            onBlurChange = onBlurChange,
            language = language,
            onLanguageChange = onLanguageChange,
            onShowDetails = { title, text -> CrashActivity.launch(context, title, text, fatal = false) },
            openLogId = openLogId,
            onLogOpened = onLogOpened,
        )
    } else {
        SplashScreen(state)
    }
}
