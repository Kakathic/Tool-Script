package com.tool.script.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Mỗi kiểu UI có 2 chế độ: thường và glass (quy tắc dự án). Các wrapper ở đây chọn bản theo [LocalGlassEnabled]:
// tắt "Giao diện kính" thì y hệt thành phần Miuix thường, bật thì dùng bản glass của miuix-glass.

/**
 * Nút hành động trong dialog. Thường: [TextButton]. Glass: [GlassDialogButton] (nền mờ nhẹ, nút chính dùng màu chủ đạo).
 * [colors] != null (ví dụ nút Hủy vàng / Thoát đỏ của log) thì giữ nguyên màu đó ở cả hai chế độ.
 * [glass] cho phép tắt bản glass ở nơi không phải dialog kính (ví dụ log toàn màn hình).
 */
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    colors: ButtonColors? = null,
    glass: Boolean = LocalGlassEnabled.current,
) {
    if (glass) {
        val role = if (primary) GlassDialogButtonRole.Primary else GlassDialogButtonRole.Normal
        val resolved = colors ?: GlassDialogButtonDefaults.buttonColors(role)
        GlassDialogButton(onClick = onClick, modifier = modifier, role = role, colors = resolved) {
            GlassButtonLabel(text, resolved.contentColor)
        }
    } else {
        TextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            colors = colors ?: if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
        )
    }
}

@Composable
private fun RowScope.GlassButtonLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, color = color, style = MiuixTheme.textStyles.button)
}

/**
 * Ô nhập chữ. Thường: [TextField]. Glass: [GlassTextField] (nền mờ trong suốt một phần, chữ nhãn tương phản cao).
 */
@Suppress("DEPRECATION")
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = false,
    insideMargin: DpSize,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    if (LocalGlassEnabled.current) {
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            insideMargin = insideMargin,
            label = label,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
        )
    } else {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            insideMargin = insideMargin,
            label = label,
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
        )
    }
}
