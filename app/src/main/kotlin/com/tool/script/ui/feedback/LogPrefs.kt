package com.tool.script.ui.feedback

import android.content.Context

/** Lưu hai tùy chọn trong menu của khung log (Ngắt dòng, Font mono) vào SharedPreferences để mọi log và lần mở app sau vẫn giữ. */
object LogPrefs {
    private const val FILE = "log_prefs"
    private const val KEY_WRAP = "wrap"
    private const val KEY_MONO = "mono"

    /** Ngắt dòng dài trong khung log; mặc định tắt (giữ một dòng, cuộn ngang). */
    fun loadWrap(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_WRAP, false)

    fun saveWrap(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WRAP, enabled).apply()
    }

    /** Dùng font monospace cho nội dung log; mặc định bật. */
    fun loadMono(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_MONO, true)

    fun saveMono(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_MONO, enabled).apply()
    }
}
