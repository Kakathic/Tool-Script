package com.tool.script

import android.graphics.Color
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.tool.script.boot.BootViewModel
import com.tool.script.boot.SplashScreen
import com.tool.script.crash.CrashActivity
import com.tool.script.ui.main.MainScreen
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.ui.theme.ThemePrefs
import com.tool.script.ui.theme.ToolScriptTheme
import com.tool.script.ui.theme.isDarkTheme
import com.tool.script.util.Util

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(ThemePrefs.load(context)) }
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
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this@MainActivity) {
                ToolScriptTheme(themeMode) {
                    AppRoot(
                        themeMode = themeMode,
                        onThemeChange = {
                            themeMode = it
                            ThemePrefs.save(context, it)
                        },
                        onExit = { finish() },
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
private fun AppRoot(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val vm: BootViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Util.hasAllFilesAccess()) vm.start() else onExit()
    }
    LaunchedEffect(Unit) {
        if (Util.hasAllFilesAccess()) {
            vm.start()
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
            CrashActivity.launch(context, "Lỗi cấu hình", fatal, fatal = true)
            onExit()
        }
    }

    val result = state.result
    if (result != null) {
        MainScreen(
            result = result,
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onShowDetails = { title, text -> CrashActivity.launch(context, title, text, fatal = false) },
        )
    } else {
        SplashScreen(state)
    }
}
