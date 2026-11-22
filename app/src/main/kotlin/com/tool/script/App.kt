package com.tool.script

import android.app.Application
import android.os.Process
import com.tool.script.crash.CrashActivity
import com.tool.script.ui.feedback.LogCenter
import kotlin.system.exitProcess

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Tiến trình ":crash" không gắn bộ bắt lỗi, tránh vòng lặp mở CrashActivity.
        if (Application.getProcessName()?.endsWith(":crash") == true) return

        // Log đã ẩn cần Context của ứng dụng để gửi thông báo.
        LogCenter.init(this)

        // Không ghi log ra file: gửi stack trace qua Intent sang CrashActivity rồi kill tiến trình.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashActivity.launch(
                    context = this,
                    title = "Ứng dụng bị lỗi",
                    text = CrashActivity.report(thread, throwable),
                    fatal = true,
                )
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, throwable)
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
