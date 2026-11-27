package com.tool.script.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.tool.script.R
import com.tool.script.ui.feedback.LogEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tool.script.util.AppAbout
import com.tool.script.util.AppIcons
import com.tool.script.util.AppVersion
import com.tool.script.util.UpdateChecker
import com.tool.script.util.UpdateState
import com.tool.script.util.openUrl
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.tool.script.ui.theme.CompactRipple
import com.tool.script.ui.theme.ThemeMode
import com.tool.script.util.Util
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.tool.script.ui.theme.LocalGlassEnabled
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import com.tool.script.ui.theme.isDarkSurface
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.glassSurface
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Icon ở thanh trên cùng:
// Cài đặt (sát lề phải) -> màn hình toàn màn hình có X ở góc trái: giao diện, tùy chọn, liên kết;
// Nguồn -> màn hình toàn màn hình có X ở góc trái: nhóm ứng dụng (khởi động lại, đóng) và nhóm thiết bị (tắt / khởi động lại máy).
// Cả hai vẽ ở gốc Main ([ToolbarFullScreen]), nên nút chỉ mở / đóng cờ hiển thị còn nội dung đặt cạnh LogFullScreen.

/** true bên trong slot của GlassTopAppBar: [TopBarIcon] vẽ thành nút kính. */
val LocalInGlassBar = staticCompositionLocalOf { false }

/** Độ trong của icon khi đang nhấn (chế độ [TopBarIcon] pressAlpha). */
private const val PressedIconAlpha = 0.4f

/**
 * Icon bấm được ở thanh trên; [icon] tính từ assets/configs/ giống icon tab.
 * Vùng chạm 48dp, hiệu ứng nhấn là gợn sóng tròn nhỏ ([CompactRipple]) thay cho ô chữ nhật phủ kín mặc định;
 * [pressAlpha] = true thì bỏ gợn sóng, icon mờ đi trong lúc nhấn.
 */
@Composable
fun TopBarIcon(icon: String, description: String, onClick: () -> Unit, circle: Boolean = false, pressAlpha: Boolean = false) {
    // Trong GlassTopAppBar (giao diện kính): nút là viên kính GlassIconButton của Miuix thay cho icon trần.
    if (LocalInGlassBar.current) {
        GlassIconButton(onClick = onClick) {
            AsyncImage(
                model = Util.iconModel(icon),
                contentDescription = description,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
            )
        }
        return
    }
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

/** Độ đậm nền ô đang chọn / đang bật trong hộp thoại cài đặt (0.16, bằng ô chọn nhiều mục). */
private const val ThemeSelectedAlpha = 0.16f

/**
 * Backdrop của chính màn hình toàn màn hình (Cài đặt / Nguồn) khi bật kính; null nếu không có (tắt blur). Chỉ dành cho thanh trên (GlassTopAppBar, nằm ngoài lớp được ghi).
 * KHÔNG dùng cho thành phần nằm trong nội dung cuộn: nội dung chính là lớp được ghi, thành phần kính lấy mờ từ lớp chứa nó sẽ tự vẽ vào chính mình
 * và làm tiến trình render chết ngay (lỗi native ở RenderThread, không qua được bộ bắt lỗi Java nên không hiện màn hình lỗi).
 */
val LocalGlassScreenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Hàng chọn một xếp ngang (Hệ thống / Sáng / Tối): các ô chia đều chiều rộng, viên chọn trượt.
 * Thường: [TabRowWithContour] của Miuix. Kính: [GlassSegmentedTabRow] (cần backdrop; thiếu thì dùng bản thường).
 */
@Composable
internal fun SettingsSegmentedRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    // Chỉ dùng backdrop của trang phía sau (nằm ngoài màn hình Cài đặt). Dùng LocalGlassScreenBackdrop ở đây là vòng tự tham chiếu (xem chú thích ở trên).
    val backdrop = LocalGlassBackdrop.current
    if (LocalGlassEnabled.current && backdrop != null) {
        GlassSegmentedTabRow(
            tabs = labels,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        // Khay không dùng màu đen (surface của giao diện tối gần như đen) và cũng không dùng màu chủ đạo: tô khay xám nhạt (onSurface mờ),
        // pha sẵn lên nền thẻ thực tế thành màu đặc để khay không phụ thuộc lớp phía sau. Viên chọn sáng hơn khay một bậc:
        // giao diện sáng dùng nền trắng (surface), giao diện tối dùng xám sáng hơn khay.
        val dark = isDarkSurface()
        val onSurface = MiuixTheme.colorScheme.onSurface
        val card = settingsCardColor().compositeOver(MiuixTheme.colorScheme.surface)
        val colors = TabRowDefaults.tabRowColors(
            backgroundColor = onSurface.copy(alpha = if (dark) 0.08f else 0.07f).compositeOver(card),
            selectedBackgroundColor = if (dark) onSurface.copy(alpha = 0.22f).compositeOver(card) else MiuixTheme.colorScheme.surface,
        )
        // maxWidth lớn để các ô chia đều hết chiều rộng (mặc định Miuix giới hạn mỗi ô 84dp).
        TabRowWithContour(
            tabs = labels,
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelect,
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            maxWidth = 600.dp,
        )
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
internal fun SettingsToggleRow(toggle: SettingToggle) {
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

/** Lề vạch kẻ của dòng trong thẻ: 16dp hai bên, khớp padding ngang của dòng. */
private val RowDividerStart = 16.dp
private val RowDividerEnd = 16.dp

/** Vạch kẻ mảnh trong thẻ; [startInset] / [endInset] chừa lề hai bên (thẻ giới thiệu đã có padding riêng nên để 0). */
@Composable
private fun SettingsCardDivider(startInset: Dp = 0.dp, endInset: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = endInset)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.2f)),
    )
}

/**
 * Màn hình toàn màn hình của Cài đặt / Nguồn: thanh trên có X ở góc trái (đóng) và tiêu đề ở giữa, nội dung cuộn bên dưới.
 * Bật Giao diện kính ([LocalGlassEnabled]) thì thanh trên là GlassTopAppBar của Miuix (nút X dạng kính, nền mờ dần khi cuộn).
 * Vẽ thẳng trong cây giao diện, đặt ở gốc Main (trên DialogBlurScrim): có blur thì trang phía sau mờ và tối nhẹ, nền màn hình trong suốt một phần;
 * không có blur thì nền đặc. Back và X đều đóng.
 */
@Composable
fun ToolbarFullScreen(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val requests = LocalBlurRequests.current
    if (requests != null) {
        DisposableEffect(show, requests) {
            if (show) requests.dialogs++
            onDispose { if (show) requests.dialogs-- }
        }
    }
    BackHandler(enabled = show, onBack = onDismiss)
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
    ) {
        val base = MiuixTheme.colorScheme.background
        val glass = LocalGlassEnabled.current
        val scrollState = rememberScrollState()
        if (glass) {
            // Giao diện kính: thanh trên là GlassTopAppBar của Miuix (nút X dạng viên kính, nền mờ dần khi nội dung cuộn qua phía dưới),
            // nội dung cuộn tràn lên dưới thanh để có gì đó cho kính làm mờ.
            // Tắt "Làm mờ nền" (requests = null) thì vẫn là thanh kính nhưng không blur.
            val contentBackdrop = rememberBarBackdrop(requests != null)
            val scrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (requests != null) base.copy(alpha = 0.6f) else base)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                CompositionLocalProvider(LocalGlassScreenBackdrop provides contentBackdrop) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                            .verticalScroll(scrollState)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = topInset + TopAppBarDefaults.CollapsedHeight + 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        content = content,
                    )
                }
                GlassTopAppBar(
                    title = title,
                    isContentScrolled = scrolled,
                    modifier = Modifier.align(Alignment.TopCenter),
                    expandable = false,
                    backdrop = contentBackdrop,
                    progressiveBlur = contentBackdrop != null,
                    navigationIcon = {
                        GlassIconButton(onClick = onDismiss) {
                            AsyncImage(
                                model = Util.iconModel(AppIcons.BarClose),
                                contentDescription = stringResource(R.string.action_cancel),
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                            )
                        }
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (requests != null) base.copy(alpha = 0.6f) else base)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp)) {
                    Text(
                        text = title,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 52.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.align(Alignment.CenterStart)) {
                        TopBarIcon(icon = AppIcons.BarClose, description = stringResource(R.string.action_cancel), onClick = onDismiss, circle = true)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * Màn hình Cài đặt toàn màn hình kiểu trang "Giới thiệu" HyperOS 3 của Miuix ([SettingsScreen]): nền động OS3, logo / tên app / phiên bản / giới thiệu ở đầu
 * mờ dần khi cuộn, nút kiểm tra phiên bản mới nổi ở đáy. X ở góc trái hoặc Back để đóng; đổi xong áp dụng ngay. Ba nhóm thẻ, từ trên xuống:
 * - Giao diện: khay phân đoạn Hệ thống / Sáng / Tối.
 * - Tùy chọn: một thẻ chung, mỗi tùy chọn một dòng có ô tích: Màu động (áp lên mục đang chọn), Làm mờ nền và Giao diện kính (chỉ hiện khi máy hỗ trợ), Rung khi chạm.
 * - Liên kết: GitHub và liên kết khác.
 * - Nhật ký (đáy): 6 log mới nhất của phiên này, bấm để mở lại toàn màn hình; "Xóa log đã xong" dọn danh sách.
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
    glass: Boolean,
    onGlassChange: (Boolean) -> Unit,
    logs: List<LogEntry>,
    onOpenLog: (LogEntry) -> Unit,
    onClearLogs: () -> Unit,
) {
    SettingsScreen(show = show, title = stringResource(R.string.settings_title), onDismiss = onDismiss) {
        SettingsGroup(stringResource(R.string.menu_section_appearance)) {
            Box(Modifier.padding(12.dp)) {
                SettingsSegmentedRow(
                    labels = BASE_THEME_MODES.map { stringResource(it.labelRes) },
                    selectedIndex = BASE_THEME_MODES.indexOf(current.base),
                    onSelect = { index -> onSelect(BASE_THEME_MODES[index].withMonet(current.monet)) },
                )
            }
        }
        SettingsGroup(stringResource(R.string.menu_section_options)) {
            SettingsToggleRow(SettingToggle(stringResource(R.string.option_dynamic_color), current.monet) { onSelect(current.withMonet(it)) })
            if (isBlurSupported()) {
                SettingsRowDivider()
                SettingsToggleRow(SettingToggle(stringResource(R.string.option_blur), blur, onBlurChange))
                SettingsRowDivider()
                SettingsToggleRow(SettingToggle(stringResource(R.string.option_glass), glass, onGlassChange))
            }
            SettingsRowDivider()
            SettingsToggleRow(SettingToggle(stringResource(R.string.option_haptic), haptic, onHapticChange))
        }
        SettingsGroup(stringResource(R.string.menu_section_links)) {
            SettingsLinkItem(AppIcons.BarGithub, stringResource(R.string.link_github), AppAbout.GithubUrl)
            if (AppAbout.OtherUrl.isNotBlank()) {
                SettingsRowDivider()
                SettingsLinkItem(AppIcons.BarLink, stringResource(R.string.link_other), AppAbout.OtherUrl)
            }
        }
        SettingsGroup(stringResource(R.string.menu_section_log)) {
            if (logs.isEmpty()) {
                SettingsCenterRow(stringResource(R.string.log_history_empty), accent = false)
            } else {
                logs.asReversed().take(SETTINGS_LOG_SHOWN).forEachIndexed { index, entry ->
                    if (index > 0) SettingsRowDivider()
                    SettingsLogItem(entry) { onOpenLog(entry) }
                }
                if (logs.any { !it.running }) {
                    SettingsRowDivider()
                    SettingsCenterRow(stringResource(R.string.log_history_clear), accent = true, onClick = onClearLogs)
                }
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
        fullScreen = true,
    ) {
        Spacer(Modifier.height(8.dp))
    }
}

private val BASE_THEME_MODES = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)

/** Số log mới nhất hiện trong nhóm Nhật ký của Cài đặt. */
private const val SETTINGS_LOG_SHOWN = 6

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

/** Hai kiểu hay dùng (Tắt máy, Khởi động lại máy) nằm ở nhóm thiết bị; các kiểu còn lại (Recovery, Khởi động lại mềm, Bootloader, Download, EDL) nằm ở nhóm "Nâng cao", cũng hiện sẵn, không mở / thu. */
private val BASIC_POWER_ACTIONS = listOf(PowerAction.PowerOff, PowerAction.Reboot)

/** Một dòng hành động của máy trong thẻ: icon bên trái, nhãn bên phải. */
@Composable
private fun PowerRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Util.iconModel(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** Dòng khóa (mờ, không bấm được) có icon khóa, dùng khi tính năng cần root mà máy chưa có. */
@Composable
private fun PowerLockedRow(label: String) {
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
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

/** Thẻ nền nhạt bo tròn chứa các dòng của hộp thoại Nguồn. */
@Composable
private fun PowerCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        content = content,
    )
}

/** Thẻ các kiểu tắt / khởi động lại máy: mỗi kiểu một dòng, vạch kẻ giữa các dòng. */
@Composable
private fun PowerActionsCard(actions: List<PowerAction>, onDeviceAction: (PowerAction) -> Unit) {
    PowerCard {
        actions.forEachIndexed { index, action ->
            if (index > 0) SettingsCardDivider(RowDividerStart, RowDividerEnd)
            PowerRow(action.icon(), stringResource(action.labelRes)) { onDeviceAction(action) }
        }
    }
}

/** Icon của từng kiểu tắt / khởi động lại máy (Tắt máy dùng icon nguồn, còn lại dùng icon khởi động lại). */
private fun PowerAction.icon(): String = if (this == PowerAction.PowerOff) AppIcons.BarPower else AppIcons.BarRestart

/** Nút nguồn trên thanh trên: chỉ bấm để mở; màn hình Nguồn là [PowerScreen], vẽ ở gốc Main. */
@Composable
fun PowerMenu(onOpen: () -> Unit) {
    TopBarIcon(
        icon = AppIcons.BarPower,
        description = stringResource(R.string.power_menu_description),
        onClick = onOpen,
        pressAlpha = true,
    )
}

/**
 * Menu Nguồn. Có root: màn hình toàn màn hình (X ở góc trái hoặc Back để đóng); chưa root: hộp thoại giữa màn hình (chạm ra ngoài hoặc Back để đóng). Danh sách dài, hiện sẵn tất cả (không mở / thu), nội dung dài thì cuộn. Ba nhóm, mỗi nhóm một thẻ có vạch kẻ:
 * - Ứng dụng: Khởi động lại ứng dụng, Đóng ứng dụng.
 * - Thiết bị (cần root): Tắt máy, Khởi động lại máy.
 * - Nâng cao: Recovery, Khởi động lại mềm, Bootloader, Download, EDL.
 * Chưa có root ([hasRoot] = false): nhóm thiết bị chỉ còn một dòng khóa "Cần root để hiển thị", không hiện nhóm Nâng cao.
 * Việc xác nhận và thực thi do nơi gọi xử lý.
 */
@Composable
fun PowerScreen(
    show: Boolean,
    onDismiss: () -> Unit,
    onRestartApp: () -> Unit,
    onCloseApp: () -> Unit,
    onDeviceAction: (PowerAction) -> Unit,
    hasRoot: Boolean = true,
) {
    val advancedActions = remember { PowerAction.entries.filter { it !in BASIC_POWER_ACTIONS } }
    val title = stringResource(R.string.power_menu_description)
    val appSection: @Composable () -> Unit = {
        SettingsSection(stringResource(R.string.power_section_app)) {
            PowerCard {
                PowerRow(AppIcons.BarRestart, stringResource(R.string.power_restart_app), onRestartApp)
                SettingsCardDivider(RowDividerStart, RowDividerEnd)
                PowerRow(AppIcons.BarClose, stringResource(R.string.power_close_app), onCloseApp)
            }
        }
    }
    if (hasRoot) {
        ToolbarFullScreen(show = show, title = title, onDismiss = onDismiss) {
            appSection()
            SettingsSection(stringResource(R.string.power_section_device)) {
                PowerActionsCard(BASIC_POWER_ACTIONS, onDeviceAction)
            }
            SettingsSection(stringResource(R.string.power_section_advanced)) {
                PowerActionsCard(advancedActions, onDeviceAction)
            }
        }
    } else {
        BlurDialog(show = show, onDismissRequest = onDismiss, title = title, centered = true, fullScreen = true) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                appSection()
                SettingsSection(stringResource(R.string.power_section_device)) {
                    PowerCard { PowerLockedRow(stringResource(R.string.power_need_root)) }
                }
            }
        }
    }
}
