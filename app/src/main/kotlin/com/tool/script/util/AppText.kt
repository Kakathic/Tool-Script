package com.tool.script.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Lấy chuỗi tài nguyên ở những nơi không có Context (parser TOML, executor, tệp log...).
 * Gắn Context của ứng dụng một lần trong `App.onCreate`; chuỗi theo ngôn ngữ app đang chọn (xem [LanguagePrefs]), mặc định theo hệ thống.
 */
object AppText {
    @Volatile
    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any): String = context?.let { LanguagePrefs.localize(it).getString(id, *args) }.orEmpty()
}
