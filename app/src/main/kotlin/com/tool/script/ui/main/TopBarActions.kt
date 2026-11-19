package com.tool.script.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.util.Util
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Icon ở thanh trên cùng và menu thả xuống ngay dưới icon:
// Thông tin -> tùy chỉnh chủ đề, Nguồn -> tùy chọn khởi động lại.

/** Icon bấm được ở thanh trên; [icon] tính từ assets/configs/ giống icon tab. */
@Composable
fun TopBarIcon(icon: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Util.iconModel(icon),
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
        )
    }
}

/** Đặt menu ngay dưới icon, căn mép phải với icon và giữ trong màn hình. */
private class BelowAnchorPositionProvider(private val marginPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceAtMost(windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        return IntOffset(x, anchorBounds.bottom)
    }
}

/** Icon kèm menu thả xuống. Chạm ra ngoài hoặc bấm Back thì đóng. */
@Composable
private fun TopBarMenu(
    icon: String,
    description: String,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box {
        TopBarIcon(icon = icon, description = description, onClick = onOpen)
        if (expanded) {
            Popup(
                popupPositionProvider = BelowAnchorPositionProvider(
                    marginPx = with(LocalDensity.current) { 16.dp.roundToPx() },
                ),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                val shape = RoundedCornerShape(16.dp)
                BoxWithConstraints {
                    Column(
                        modifier = Modifier
                            .widthIn(max = (maxWidth - 32.dp).coerceAtLeast(0.dp))
                            .width(IntrinsicSize.Max)
                            .shadow(elevation = 8.dp, shape = shape)
                            .background(MiuixTheme.colorScheme.surfaceContainer, shape)
                            .padding(vertical = 8.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    Text(
        text = if (selected) "✓  $label" else label,
        color = color,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f)),
    )
}

/** Nút thông tin: menu chọn chủ đề Theo hệ thống / Sáng / Tối / Màu động. Chọn xong áp dụng ngay và đóng menu. */
@Composable
fun ThemeMenu(
    expanded: Boolean,
    current: ThemeMode,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    TopBarMenu(
        icon = "icons/bar_about.svg",
        description = "Tùy chỉnh chủ đề",
        expanded = expanded,
        onOpen = onOpen,
        onDismiss = onDismiss,
    ) {
        for ((index, mode) in ThemeMode.entries.withIndex()) {
            // Vạch ngăn giữa nhóm cơ bản và nhóm "Màu động".
            if (index > 0 && mode.monet && !ThemeMode.entries[index - 1].monet) MenuDivider()
            MenuItem(label = mode.label, selected = mode == current) { onSelect(mode) }
        }
    }
}

/** Nút nguồn: menu chọn kiểu khởi động lại. Việc xác nhận và thực thi do nơi gọi xử lý. */
@Composable
fun PowerMenu(
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onRestartApp: () -> Unit,
    onRebootDevice: () -> Unit,
) {
    TopBarMenu(
        icon = "icons/bar_power.svg",
        description = "Nguồn",
        expanded = expanded,
        onOpen = onOpen,
        onDismiss = onDismiss,
    ) {
        MenuItem(label = "Khởi động lại ứng dụng", onClick = onRestartApp)
        MenuItem(label = "Khởi động lại máy (cần root)", onClick = onRebootDevice)
    }
}
