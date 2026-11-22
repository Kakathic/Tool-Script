package com.tool.script.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
// Nền sau dialog dùng `Modifier.scrimBlur(backdrop)` với backdrop của cả trang.

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

private const val ScrimBlurRadius = 20f
private const val ScrimDim = 0.35f

/** Nền phía sau dialog: trang bên dưới làm mờ rồi phủ lớp tối nhẹ, thay cho lớp đen đặc. */
@Composable
fun Modifier.scrimBlur(backdrop: LayerBackdrop): Modifier {
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(color = Color.Black.copy(alpha = ScrimDim))),
    )
    return textureBlur(
        backdrop = backdrop,
        shape = RectangleShape,
        blurRadius = ScrimBlurRadius,
        colors = colors,
    )
}
