package com.tool.script.util

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.tool.script.R
import java.util.Locale

/** Ngôn ngữ giao diện: theo hệ thống hoặc chọn riêng cho app (khớp `res/xml/locales_config.xml` và các thư mục `values-*`). */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_english),
    Vietnamese("vi", R.string.language_vietnamese),
    ;

    /** null = theo hệ thống. */
    val locale: Locale?
        get() = if (tag.isEmpty()) null else Locale.forLanguageTag(tag)

    /** Giá trị của item `app_language` trong TOML: `system`, `en`, `vi`. */
    val key: String
        get() = if (tag.isEmpty()) "system" else tag

    companion object {
        fun fromKey(key: String): AppLanguage? = entries.firstOrNull { it.key == key }
    }
}

/** id dành riêng cho item radio / dropdown / select (multi = false) gắn thẳng vào ngôn ngữ app (không chạy lệnh, không lưu file). */
const val APP_LANGUAGE_ID = "app_language"

/**
 * Lưu và áp dụng ngôn ngữ riêng của app.
 * - Android 13+ (API 33): giao cho hệ thống ([LocaleManager]), cùng nguồn với Cài đặt hệ thống > Ngôn ngữ ứng dụng,
 *   hệ thống tự áp dụng cho mọi thành phần của app và bắn thay đổi cấu hình (Activity đã khai báo `locale` trong configChanges nên không dựng lại).
 * - Android 12 trở xuống: lưu vào SharedPreferences, áp dụng bằng [localize] (MainActivity bọc Context của Compose, App và CrashActivity bọc lúc khởi động).
 */
object LanguagePrefs {
    private const val FILE = "language_prefs"
    private const val KEY = "language"

    fun load(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).getApplicationLocales()
            if (locales.isEmpty()) return AppLanguage.System
            val lang = locales.get(0).language
            return AppLanguage.entries.firstOrNull { it.tag == lang } ?: AppLanguage.System
        }
        val tag = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.System
    }

    fun save(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = if (language == AppLanguage.System) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            context.getSystemService(LocaleManager::class.java).setApplicationLocales(locales)
        } else {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
        }
    }

    /** Mã ngôn ngữ mặc định của app khi ngôn ngữ thiết bị không được hỗ trợ: thư mục `values/` là English. */
    private const val DEFAULT_TAG = "en"

    /** Locale của thiết bị (hệ thống); ngôn ngữ riêng của app không ảnh hưởng. */
    fun deviceLocale(): Locale = Resources.getSystem().configuration.locales.get(0)

    /**
     * Mã ngôn ngữ mà app đang thật sự dùng (`vi`, `en`): lựa chọn riêng nếu có, còn "Hệ thống" thì lấy ngôn ngữ thiết bị
     * nếu app hỗ trợ, không thì [DEFAULT_TAG]. Truyền [language] khi vừa đổi lựa chọn để khỏi phụ thuộc thời điểm hệ thống cập nhật.
     */
    fun effectiveTag(context: Context, language: AppLanguage? = null): String {
        val chosen = language ?: load(context)
        if (chosen != AppLanguage.System) return chosen.tag
        val device = deviceLocale().language
        return AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && it.tag == device }?.tag ?: DEFAULT_TAG
    }

    /**
     * Context dùng ngôn ngữ [language] cho tài nguyên (chuỗi). Android 13+ hoặc theo hệ thống: trả lại [base] không đổi.
     * Bọc bằng ContextWrapper nên vẫn lần ngược được ra Activity (Util.exitApp, v.v.).
     */
    fun localize(base: Context, language: AppLanguage? = null): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val locale = (language ?: load(base)).locale ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return LocalizedContext(base, base.createConfigurationContext(config).resources)
    }

    private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
        override fun getResources(): Resources = localized
    }
}
