package com.tool.script

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: gắn bộ bắt crash (Thread.setDefaultUncaughtExceptionHandler).
        //  Không ghi log ra file: gửi stack trace qua Intent sang crash/CrashActivity rồi kill tiến trình.
    }
}
