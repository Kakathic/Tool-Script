package com.tool.script.ui.main

import com.tool.script.util.AppIcons
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.tool.script.ui.theme.CompactRipple
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.util.Util
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Icon ở thanh trên cùng và menu thả xuống ngay dưới icon:
// Chủ đề -> tùy chỉnh giao diện, Nguồn -> tùy chọn khởi động lại.

/**
 * Icon bấm được ở thanh trên; [icon] tính từ assets/configs/ giống icon tab.
 * Vùng chạm 48dp, hiệu ứng nhấn là gợn sóng tròn nhỏ ([CompactRipple]) thay cho ô chữ nhật phủ kín mặc định.
 */
@Composable
fun TopBarIcon(icon: String, description: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val rippleColor = MiuixTheme.colorScheme.onBackground
    val ripple = remember(rippleColor) { CompactRipple(color = rippleColor) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple,
                role = Role.Button,
                onClick = onClick,
            ),
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

/** Đặt menu ngay dưới anchor, căn mép phải với anchor và giữ trong màn hình; thiếu chỗ phía dưới thì lật lên trên. */
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
        val y = when {
            anchorBounds.bottom + popupContentSize.height + marginPx <= windowSize.height -> anchorBounds.bottom
            anchorBounds.top - popupContentSize.height >= marginPx -> anchorBounds.top - popupContentSize.height
            else -> (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}

/**
 * Làm tối cửa sổ phía sau Popup mà không thay đổi kích thước hay vùng nhận chạm.
 * Độ tối lấy từ màu nền mờ của chủ đề Miuix (`windowDimming`), cùng nguồn với hộp thoại nên hai bên luôn đồng nhất
 * (tự đổi theo sáng / tối).
 */
@Composable
private fun DimMenuBackground() {
    val view = LocalView.current
    val dimAmount = MiuixTheme.colorScheme.windowDimming.alpha
    DisposableEffect(view, dimAmount) {
        val popupView = view.rootView
        val params = popupView.layoutParams as? WindowManager.LayoutParams
        val windowManager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val originalFlags = params?.flags
        val originalDimAmount = params?.dimAmount
        if (params != null) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount = dimAmount
            windowManager.updateViewLayout(popupView, params)
        }
        onDispose {
            if (params != null && originalFlags != null && originalDimAmount != null) {
                params.flags = originalFlags
                params.dimAmount = originalDimAmount
                if (popupView.isAttachedToWindow) {
                    windowManager.updateViewLayout(popupView, params)
                }
            }
        }
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
            MenuPopup(onDismiss = onDismiss, content = content)
        }
    }
}

/**
 * Popup menu thả xuống dùng chung (menu thanh trên và item `dropdown`): nằm ngay dưới vị trí đặt nó
 * (lật lên trên nếu thiếu chỗ), làm tối nền (và làm mờ trang phía sau khi bật blur), bo góc, cuộn được khi danh sách dài.
 * Chạm ra ngoài hoặc bấm Back thì gọi [onDismiss].
 */
@Composable
internal fun MenuPopup(
    onDismiss: () -> Unit,
    minWidth: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Menu là cửa sổ riêng nên không làm mờ nền được từ trong menu: báo để MainScreen làm mờ trang phía sau.
    MenuBlurRequest()
    Popup(
        popupPositionProvider = BelowAnchorPositionProvider(
            marginPx = with(LocalDensity.current) { 16.dp.roundToPx() },
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        DimMenuBackground()
        val shape = RoundedCornerShape(16.dp)
        BoxWithConstraints {
            val maxW = (maxWidth - 32.dp).coerceAtLeast(0.dp)
            val maxH = (maxHeight - 96.dp).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .widthIn(min = minWidth.coerceAtMost(maxW), max = maxW)
                    .width(IntrinsicSize.Max)
                    .heightIn(max = maxH)
                    .shadow(elevation = 8.dp, shape = shape)
                    .background(MiuixTheme.colorScheme.surfaceContainer, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}

/** Độ đậm nền của mục đang chọn trong menu (0..1). Tăng để đậm hơn, giảm để nhạt hơn. */
private const val MenuSelectedAlpha = 0.24f

/**
 * Mục menu. [selected] = null: menu không có lựa chọn (không chừa chỗ cho dấu tích);
 * khác null: luôn chừa ô tích bên phải để các mục thẳng hàng, true thì hiện dấu tích.
 */
@Composable
internal fun MenuItem(label: String, selected: Boolean? = null, onClick: () -> Unit) {
    val color = if (selected == true) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    // Mục đang chọn: nền màu chủ đạo nhạt bo tròn, chữ màu chủ đạo, dấu tích bên phải.
    val itemShape = RoundedCornerShape(12.dp)
    val itemBackground = if (selected == true) MiuixTheme.colorScheme.primary.copy(alpha = MenuSelectedAlpha) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(itemShape)
            .background(itemBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected != null) {
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    AsyncImage(
                        model = Util.iconModel(AppIcons.BarCheck),
                        contentDescription = "Đang chọn",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

// Mục menu có nút gạt bên phải: bấm cả hàng hoặc nút gạt đều đổi trạng thái.
@Composable
internal fun MenuSwitchItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
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

/** Nút chủ đề: menu chọn chủ đề Theo hệ thống / Sáng / Tối / Màu động. Chọn xong áp dụng ngay và đóng menu. */
@Composable
fun ThemeMenu(
    expanded: Boolean,
    current: ThemeMode,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
    haptic: Boolean,
    onHapticChange: (Boolean) -> Unit,
    blur: Boolean,
    onBlurChange: (Boolean) -> Unit,
) {
    TopBarMenu(
        icon = AppIcons.BarTheme,
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
        MenuDivider()
        MenuSwitchItem(label = "Rung khi chạm", checked = haptic, onChange = onHapticChange)
        if (isBlurSupported()) MenuSwitchItem(label = "Làm mờ nền", checked = blur, onChange = onBlurChange)
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
        icon = AppIcons.BarPower,
        description = "Nguồn",
        expanded = expanded,
        onOpen = onOpen,
        onDismiss = onDismiss,
    ) {
        MenuItem(label = "Khởi động lại ứng dụng", onClick = onRestartApp)
        MenuItem(label = "Khởi động lại máy (cần root)", onClick = onRebootDevice)
    }
}
