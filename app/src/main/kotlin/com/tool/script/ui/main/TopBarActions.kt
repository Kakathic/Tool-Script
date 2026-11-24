package com.tool.script.ui.main

import androidx.annotation.StringRes
import com.tool.script.R
import androidx.compose.ui.res.stringResource
import com.tool.script.util.AppIcons
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
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
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Icon ở thanh trên cùng:
// Cài đặt (sát lề phải) -> hộp thoại giữa màn hình: giao diện, tùy chọn, ngôn ngữ;
// Nguồn -> menu thả xuống ngay dưới icon với các tùy chọn khởi động lại.

/** Độ trong của icon khi đang nhấn (chế độ [TopBarIcon] pressAlpha). */
private const val PressedIconAlpha = 0.4f

/**
 * Icon bấm được ở thanh trên; [icon] tính từ assets/configs/ giống icon tab.
 * Vùng chạm 48dp, hiệu ứng nhấn là gợn sóng tròn nhỏ ([CompactRipple]) thay cho ô chữ nhật phủ kín mặc định;
 * [pressAlpha] = true thì bỏ gợn sóng, icon mờ đi trong lúc nhấn.
 */
@Composable
fun TopBarIcon(icon: String, description: String, onClick: () -> Unit, circle: Boolean = false, pressAlpha: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val rippleColor = MiuixTheme.colorScheme.onBackground
    val ripple = remember(rippleColor) { CompactRipple(color = rippleColor) }
    // pressAlpha: không gợn sóng, icon mờ đi khi đang nhấn (dùng cho icon cài đặt / nguồn).
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedAlpha by animateFloatAsState(if (pressAlpha && pressed) PressedIconAlpha else 1f, tween(90), label = "iconPress")
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { alpha = pressedAlpha }
            .clickable(
                interactionSource = interactionSource,
                indication = if (pressAlpha) null else ripple,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (circle) {
            // Nền tròn bo quanh icon để nút nổi rõ trên thanh (dùng cho X / tích của dialog).
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = Util.iconModel(icon),
                    contentDescription = description,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                )
            }
        } else {
            AsyncImage(
                model = Util.iconModel(icon),
                contentDescription = description,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
            )
        }
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
    minWidth: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box {
        TopBarIcon(icon = icon, description = description, onClick = onOpen, pressAlpha = true)
        if (expanded) {
            MenuPopup(onDismiss = onDismiss, minWidth = minWidth, content = content)
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

/** Độ đậm nền ô đang chọn / đang bật trong hộp thoại cài đặt (0.16, bằng ô chọn nhiều mục). */
private const val ThemeSelectedAlpha = 0.16f

/** Khoảng cách dọc mỗi bên của một mục menu (hai mục liền nhau cách nhau gấp đôi giá trị này). */
private val MenuItemGap = 2.dp

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
            // Chừa khe nhỏ giữa các mục để nền mục đang chọn không dính vào mục kế bên.
            .padding(horizontal = 8.dp, vertical = MenuItemGap)
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
                        contentDescription = stringResource(R.string.state_selected),
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** Nhãn nhỏ đầu mỗi nhóm trong menu (ví dụ "Giao diện", "Tùy chọn"). */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
    )
}

/**
 * Hàng chọn một xếp ngang kiểu "thanh trượt phân đoạn": các ô chia đều chiều rộng nằm trong một khay bo tròn,
 * ô đang chọn có nền màu chủ đạo nhạt, chữ màu chủ đạo, không đổi độ đậm chữ.
 */
@Composable
internal fun SettingsSegmentedRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val trackShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = ThemeSelectedAlpha) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Một dòng bật / tắt trong [SettingsToggleCard]. */
internal class SettingToggle(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

/**
 * Một dòng bật / tắt trong thẻ riêng: chữ bên trái, ô tích tròn của Miuix bên phải.
 * Bấm cả thẻ để đổi trạng thái, hộp thoại giữ nguyên.
 */
@Composable
internal fun SettingsToggleCard(toggle: SettingToggle) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    ) {
        SettingsToggleRow(toggle)
    }
}

@Composable
private fun SettingsToggleRow(toggle: SettingToggle) {
    val on = toggle.checked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { toggle.onChange(!on) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = toggle.label,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = if (on) ToggleableState.On else ToggleableState.Off,
            onClick = { toggle.onChange(!on) },
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp, start = 20.dp, end = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f)),
    )
}

/** Nút Cài đặt ở thanh trên (icon bánh răng, đặt sát lề phải): bấm để mở [SettingsDialog]. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    TopBarIcon(
        icon = AppIcons.BarSettings,
        description = stringResource(R.string.settings_title),
        onClick = onClick,
        pressAlpha = true,
    )
}

/** Nhãn nhóm trong hộp thoại cài đặt (lề trái 4dp, khớp mép các ô bên dưới). */
@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Một nhóm trong hộp thoại cài đặt: nhãn nhỏ phía trên, các điều khiển bên dưới cách nhau 8dp. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionLabel(title)
        content()
    }
}

/**
 * Hộp thoại Cài đặt hiện giữa màn hình. Đổi xong áp dụng ngay, hộp thoại giữ nguyên để thấy kết quả phía sau;
 * chạm ra ngoài hoặc Back để đóng (không có nút Đóng). Hai nhóm:
 * - Giao diện: khay phân đoạn Hệ thống / Sáng / Tối.
 * - Tùy chọn: mỗi tùy chọn một thẻ riêng có ô tích: Màu động (áp lên mục đang chọn), Làm mờ nền (chỉ hiện khi máy hỗ trợ), Rung khi chạm.
 * Ngôn ngữ không còn ở đây: đổi bằng item `app_language` trong TOML.
 */
@Composable
fun SettingsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    haptic: Boolean,
    onHapticChange: (Boolean) -> Unit,
    blur: Boolean,
    onBlurChange: (Boolean) -> Unit,
) {
    // Màn hình thấp (xoay ngang): giới hạn chiều cao và cho cuộn.
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    BlurDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_title),
        centered = true,
    ) {
        Column(
            modifier = Modifier.heightIn(max = maxContentHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsSection(stringResource(R.string.menu_section_appearance)) {
                SettingsSegmentedRow(
                    labels = BASE_THEME_MODES.map { stringResource(it.labelRes) },
                    selectedIndex = BASE_THEME_MODES.indexOf(current.base),
                    onSelect = { index -> onSelect(BASE_THEME_MODES[index].withMonet(current.monet)) },
                )
            }
            SettingsSection(stringResource(R.string.menu_section_options)) {
                SettingsToggleCard(SettingToggle(stringResource(R.string.option_dynamic_color), current.monet) { onSelect(current.withMonet(it)) })
                if (isBlurSupported()) SettingsToggleCard(SettingToggle(stringResource(R.string.option_blur), blur, onBlurChange))
                SettingsToggleCard(SettingToggle(stringResource(R.string.option_haptic), haptic, onHapticChange))
            }
        }
    }
}

/** Cửa sổ menu mở từ icon 3 gạch cạnh tên app (màn hình chính). Tạm thời chưa có nội dung; chạm ra ngoài hoặc Back để đóng. */
@Composable
fun MenuDialog(show: Boolean, onDismiss: () -> Unit) {
    BlurDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.menu_title),
        centered = true,
    ) {
        Spacer(Modifier.height(8.dp))
    }
}

private val BASE_THEME_MODES = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)

/**
 * Các kiểu tắt / khởi động lại máy trong menu Nguồn, theo menu của Magisk. Chạy bằng root: [command] thử `svc power` trước,
 * không được thì dùng `reboot` (cùng cách Magisk gọi). [confirm] là câu hỏi xác nhận trước khi chạy.
 */
enum class PowerAction(@StringRes val labelRes: Int, @StringRes val confirmRes: Int, val command: String) {
    PowerOff(R.string.power_off, R.string.power_off_confirm, "/system/bin/svc power shutdown || /system/bin/reboot -p"),
    Reboot(R.string.power_reboot, R.string.power_reboot_confirm, rebootCommand("")),
    Recovery(R.string.power_recovery, R.string.power_recovery_confirm, rebootCommand("recovery")),
    SoftReboot(R.string.power_soft_reboot, R.string.power_soft_reboot_confirm, "killall zygote || setprop ctl.restart zygote"),
    Bootloader(R.string.power_bootloader, R.string.power_bootloader_confirm, rebootCommand("bootloader")),
    Download(R.string.power_download, R.string.power_download_confirm, rebootCommand("download")),
    Edl(R.string.power_edl, R.string.power_edl_confirm, rebootCommand("edl")),
}

private fun rebootCommand(reason: String): String {
    val arg = if (reason.isEmpty()) "" else " $reason"
    return "/system/bin/svc power reboot$arg || /system/bin/reboot$arg"
}

/** Ba kiểu hay dùng (Tắt máy, Khởi động lại máy, Recovery), luôn hiện sẵn trong menu Nguồn; các kiểu còn lại gom vào nhóm [POWER_ADVANCED_LABEL] (bấm để mở / thu). */
private val BASIC_POWER_ACTIONS = listOf(PowerAction.PowerOff, PowerAction.Reboot, PowerAction.Recovery)

/**
 * Dòng bấm để mở / thu một nhóm mục trong menu (cùng kiểu dáng [MenuItem]), bên phải là icon lên-xuống giống item `dropdown`.
 */
@Composable
private fun MenuExpandItem(label: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = MenuItemGap)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        AsyncImage(
            model = Util.iconModel(AppIcons.BarUpDown),
            contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
        )
    }
}

/** Dòng khóa (mờ, không bấm được) có icon khóa bên trái, dùng khi tính năng cần root mà máy chưa có. */
@Composable
private fun MenuLockedItem(label: String) {
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = MenuItemGap)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Util.iconModel(AppIcons.BarLock),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(color),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * Nút nguồn: nhóm app (khởi động lại, đóng) rồi vạch kẻ, rồi nhóm máy (cần root).
 * Nhóm máy chỉ hiện sẵn Tắt máy, Khởi động lại máy và Recovery cho menu ngắn gọn; Khởi động lại mềm, Bootloader, Download, EDL
 * gom vào dòng "Khởi động lại nâng cao" (mở / thu, mỗi lần mở menu bắt đầu ở trạng thái thu).
 * Chưa có root ([hasRoot] = false): nhóm máy được thay bằng một dòng khóa "Cần root để hiển thị".
 * Việc xác nhận và thực thi do nơi gọi xử lý.
 */
@Composable
fun PowerMenu(
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onRestartApp: () -> Unit,
    onCloseApp: () -> Unit,
    onDeviceAction: (PowerAction) -> Unit,
    hasRoot: Boolean = true,
) {
    TopBarMenu(
        icon = AppIcons.BarPower,
        description = stringResource(R.string.power_menu_description),
        expanded = expanded,
        onOpen = onOpen,
        onDismiss = onDismiss,
    ) {
        var advanced by remember { mutableStateOf(false) }
        MenuItem(label = stringResource(R.string.power_restart_app), onClick = onRestartApp)
        MenuItem(label = stringResource(R.string.power_close_app), onClick = onCloseApp)
        MenuDivider()
        if (!hasRoot) {
            // Chưa có root: không hiện nhóm thiết bị, chỉ một dòng khóa mờ, không bấm được.
            MenuLockedItem(label = stringResource(R.string.power_need_root))
            return@TopBarMenu
        }
        MenuSectionLabel(stringResource(R.string.power_section_device))
        BASIC_POWER_ACTIONS.forEach { action ->
            MenuItem(label = stringResource(action.labelRes)) { onDeviceAction(action) }
        }
        MenuExpandItem(label = stringResource(R.string.power_advanced), expanded = advanced, onClick = { advanced = !advanced })
        // Khi nhóm nâng cao đang thu: đưa bản ẩn cao 0dp của nó vào phép đo để menu tự co theo mục dài nhất mà không đổi rộng khi mở / thu.
        if (!advanced) {
            Column(Modifier.height(0.dp).clipToBounds().clearAndSetSemantics { }) {
                PowerAction.entries.filter { it !in BASIC_POWER_ACTIONS }.forEach { action ->
                    MenuItem(label = stringResource(action.labelRes)) { }
                }
            }
        }
        AnimatedVisibility(
            visible = advanced,
            enter = expandVertically(tween(180)),
            exit = shrinkVertically(tween(150)),
        ) {
            // Các mục nhóm nâng cao thẳng hàng với các mục khác, không thụt vào.
            Column {
                PowerAction.entries.filter { it !in BASIC_POWER_ACTIONS }.forEach { action ->
                    MenuItem(label = stringResource(action.labelRes)) { onDeviceAction(action) }
                }
            }
        }
    }
}
