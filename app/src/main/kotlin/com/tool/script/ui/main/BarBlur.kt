package com.tool.script.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Làm mờ nền thanh trên và thanh tab khi thẻ cuộn qua phía dưới (miuix-blur, theo ví dụ của Miuix).
// Cách dùng: [rememberBarBackdrop] tạo backdrop, gắn `Modifier.layerBackdrop(backdrop)` vào vùng nội dung,
// thêm `Modifier.barBlur(backdrop)` vào mỗi thanh và để nền thanh trong suốt khi backdrop khác null.

private const val BarBlurRadius = 25f
private const val BarBlurTint = 0.8f

/** Máy có hỗ trợ blur không (Android 13+). Dưới mức này thanh giữ nền đặc như cũ và công tắc "Làm mờ nền" bị ẩn. */
fun isBlurSupported(): Boolean = isRuntimeShaderSupported()

/** Backdrop ghi lại nội dung để làm mờ; null khi [enabled] = false hoặc máy không hỗ trợ blur. */
@Composable
fun rememberBarBackdrop(enabled: Boolean): LayerBackdrop? {
    if (!enabled || !isBlurSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}

/** Làm mờ phần nội dung nằm sau thanh mang modifier này. [backdrop] = null thì không làm gì (thanh tự vẽ nền đặc). */
@Composable
fun Modifier.barBlur(backdrop: LayerBackdrop?): Modifier {
    if (backdrop == null) return this
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = BarBlurTint))),
    )
    return textureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        blurRadius = BarBlurRadius,
        colors = colors,
    )
}
