package com.tool.script.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Icon launcher theo chủ đề của app (không phải chủ đề hệ thống): hai activity-alias trong manifest, chỉ một cái bật.
 * App chế độ sáng dùng icon tối ([FOR_LIGHT], bật mặc định), chế độ tối dùng icon sáng ([FOR_DARK]).
 * Bật alias mới trước rồi mới tắt alias cũ, kèm DONT_KILL_APP, để app đang mở không bị đóng.
 */
object AppIcon {
    private const val FOR_LIGHT = "com.tool.script.IconForLight"
    private const val FOR_DARK = "com.tool.script.IconForDark"

    fun apply(context: Context, dark: Boolean) {
        val pm = context.packageManager
        val on = ComponentName(context.packageName, if (dark) FOR_DARK else FOR_LIGHT)
        val off = ComponentName(context.packageName, if (dark) FOR_LIGHT else FOR_DARK)
        try {
            if (isEnabled(pm, on, defaultEnabled = !dark) && !isEnabled(pm, off, defaultEnabled = dark)) return
            pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } catch (_: Exception) {
        }
    }

    /** DEFAULT = theo giá trị `android:enabled` trong manifest ([defaultEnabled]). */
    private fun isEnabled(pm: PackageManager, name: ComponentName, defaultEnabled: Boolean): Boolean =
        when (pm.getComponentEnabledSetting(name)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> defaultEnabled
            else -> false
        }
}
