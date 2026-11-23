package com.tool.script.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Lấy chuỗi tài nguyên ở những nơi không có Context (parser TOML, executor, tệp log...).
 * Gắn Context của ứng dụng một lần trong `App.onCreate`; chuỗi luôn theo ngôn ngữ hiện tại của hệ thống.
 */
object AppText {
    @Volatile
    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String = context?.getString(id, *args).orEmpty()
}
