package com.tool.script.ui.render

import com.tool.script.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tool.script.config.SelectOption
import com.tool.script.ui.main.LocalBlurRequests
import com.tool.script.util.InstalledApps
import java.text.Normalizer
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import com.tool.script.ui.theme.DialogButton
import com.tool.script.ui.theme.AppTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Chọn toàn màn hình cho `select` khi có từ FULLSCREEN_MIN_OPTIONS mục trở lên.
// Dưới ngưỡng này vẫn dùng hộp thoại nhỏ như cũ (xem Render.kt). docs/schema.md, mục 7.

/** Chiều cao chung của ô tìm kiếm và các nút Hủy / Xác nhận ở màn hình chọn (ép cả hai về cùng một giá trị để luôn bằng nhau). */
private val SELECT_CONTROL_HEIGHT = 50.dp

/** Số mục tối thiểu để chuyển từ hộp thoại nhỏ sang toàn màn hình. */
internal const val FULLSCREEN_MIN_OPTIONS = 6

/**
 * Lề bốn phía và khoảng cách giữa các khối (tiêu đề, ô tìm, danh sách, hai nút) của màn hình chọn toàn màn hình: 16dp,
 * cùng mức với log shell toàn màn hình (LogFullMargin trong Feedback.kt).
 */
private val SCREEN_MARGIN = 16.dp

/** Lề trong mỗi dòng danh sách: 12dp hai bên và 12dp trên dưới (danh sách không còn khung nên chữ cách mép màn hình 16 + 12 = 28dp). */
private val ListRowMargin = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)

private val COMBINING_MARKS = Regex("\\p{M}+")

/** Chuẩn hóa để tìm không phân biệt hoa thường và dấu tiếng Việt: "Đà Lạt" khớp với "da lat". */
internal fun foldSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('đ', 'd')

/** Độ đục của nền màn hình chọn khi có blur: phần còn lại là trang phía sau đã làm mờ (cùng mức với log toàn màn hình). */
private const val GLASS_ALPHA = 0.6f

/** Một lần mở màn hình chọn toàn màn hình, gửi lên [SelectHost] để vẽ ở gốc cây giao diện. */
@Stable
internal class SelectRequest(
    val title: String,
    val options: List<SelectOption>,
    val multi: Boolean,
    val selected: Set<String>,
    val onDismiss: () -> Unit,
    val onConfirm: (Set<String>) -> Unit,
    val desc: String? = null,
    val instant: Boolean = false,
    val live: LiveOptions? = null,
)

/**
 * Danh sách lựa chọn nạp dần (type `applist`): [options] và [loading] đổi theo thời gian. [SelectRequest] chỉ tạo một lần lúc mở nên
 * màn hình chọn đọc thẳng từ đây (state) để cập nhật theo. Có [LiveOptions] thì nó thay cho `options` truyền vào.
 * Dòng [SelectOption.placeholder] vẽ khung xương; [loading] = true thì góc phải hiện "Đang tải danh sách…", Xác nhận và Chọn tất cả mờ.
 */
@Stable
class LiveOptions {
    var options by mutableStateOf<List<SelectOption>>(emptyList())
    var loading by mutableStateOf(false)
}

/**
 * Chỗ vẽ màn hình chọn toàn màn hình. Item chọn nằm sâu trong trang nên không phủ kín màn hình được và không dùng chung nền mờ của trang;
 * vì vậy item gửi yêu cầu lên đây ([SelectFullScreenSlot]) và [SelectFullScreenHost] ở gốc Main vẽ nó (giống log toàn màn hình).
 */
@Stable
class SelectHost {
    internal var request by mutableStateOf<SelectRequest?>(null)
}

/** [SelectHost] của Main; null (không có host) thì màn hình chọn mở bằng cửa sổ riêng, nền đặc như trước. */
val LocalSelectHost = staticCompositionLocalOf<SelectHost?> { null }

/**
 * Chỗ item `select` gọi: [show] = true thì mở màn hình chọn toàn màn hình (qua [LocalSelectHost], không có thì mở cửa sổ riêng),
 * false hoặc item rời khỏi cây thì đóng. Mỗi lần mở bắt đầu lại từ [selected].
 */
@Composable
internal fun SelectFullScreenSlot(
    show: Boolean,
    title: String,
    options: List<SelectOption>,
    multi: Boolean,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    desc: String? = null,
    instant: Boolean = false,
    live: LiveOptions? = null,
) {
    val host = LocalSelectHost.current
    if (host == null) {
        if (show) SelectFullScreen(title, options, multi, selected, onDismiss, onConfirm, desc = desc, instant = instant, live = live)
    } else {
        val latestDismiss by rememberUpdatedState(onDismiss)
        val latestConfirm by rememberUpdatedState(onConfirm)
        DisposableEffect(show) {
            if (!show) return@DisposableEffect onDispose {}
            val request = SelectRequest(title, options, multi, selected, { latestDismiss() }, { latestConfirm(it) }, desc, instant, live)
            host.request = request
            onDispose { if (host.request === request) host.request = null }
        }
    }
}

/** Đặt ở gốc Main, trên trang và dưới các popup: vẽ màn hình chọn đang được yêu cầu (nếu có). */
@Composable
fun SelectFullScreenHost(host: SelectHost) {
    val request = host.request ?: return
    key(request) {
        SelectFullScreen(
            title = request.title,
            options = request.options,
            multi = request.multi,
            selected = request.selected,
            onDismiss = request.onDismiss,
            onConfirm = request.onConfirm,
            inline = true,
            desc = request.desc,
            instant = request.instant,
            live = request.live,
        )
    }
}

/**
 * Màn hình chọn toàn màn hình: tiêu đề, ô tìm kiếm, (multi = true) ô Chọn tất cả, danh sách, rồi Hủy / Xác nhận.
 *
 * - Ô tìm lọc theo nhãn (không phân biệt hoa thường và dấu).
 * - Chọn tất cả chỉ tác động lên các mục đang hiện trong kết quả tìm; mục ngoài kết quả giữ nguyên.
 *   Chưa tìm gì thì kết quả là toàn bộ danh sách.
 * - Chỉ khi bấm Xác nhận mới gọi [onConfirm]; Hủy, Back hoặc chạm ra ngoài thì [onDismiss] và bỏ thay đổi.
 *
 * [instant] = true (item trong dialog, chỉ truyền biến, đã có Hủy / Xác nhận của dialog): không có Xác nhận. Chọn một: chạm dòng là chọn luôn và đóng;
 * chọn nhiều: mỗi lần đổi gọi [onConfirm] ngay (không đóng), nút Đóng ở đáy (hoặc Back) để thoát.
 *
 * Gọi khi cần hiện (`if (show) SelectFullScreen(...)`) để mỗi lần mở bắt đầu lại từ [selected].
 *
 * [inline] = true: vẽ thẳng trong cây giao diện (do [SelectFullScreenHost] gọi). Có blur thì nền bán trong suốt, trang phía sau mờ và tối nhẹ
 * (dùng chung DialogBlurScrim); không có blur thì nền đặc. [inline] = false: mở bằng cửa sổ riêng, nền đặc.
 */
@Composable
internal fun SelectFullScreen(
    title: String,
    options: List<SelectOption>,
    multi: Boolean,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    inline: Boolean = false,
    desc: String? = null,
    instant: Boolean = false,
    live: LiveOptions? = null,
) {
    // Có [live] thì danh sách và trạng thái tải lấy từ đó (đọc state nên tự vẽ lại khi nạp thêm).
    val allOptions = live?.options ?: options
    val loading = live?.loading == true
    val shimmer = if (loading) rememberShimmerBrush() else null
    var draft by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val visible = remember(allOptions, query) {
        val q = foldSearch(query.trim())
        // Đang tìm thì bỏ các dòng khung xương (chưa có tên để lọc).
        if (q.isEmpty()) allOptions else allOptions.filter { !it.placeholder && (foldSearch(it.label).contains(q) || (it.app && it.value.lowercase().contains(q))) }
    }
    val visibleValues = visible.map { it.value }
    val checkedVisible = visibleValues.count { it in draft }
    val allState = when {
        visibleValues.isEmpty() || checkedVisible == 0 -> ToggleableState.Off
        checkedVisible == visibleValues.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    // Chọn nhiều ở chế độ instant: mỗi lần đổi gửi ngay lên item.
    val applyDraft: (Set<String>) -> Unit = { next ->
        draft = next
        if (instant && multi) onConfirm(next)
    }
    val toggleAll = {
        if (allState == ToggleableState.On) {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
            applyDraft(draft - visibleValues.toSet())
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            applyDraft(draft + visibleValues)
        }
    }

    // Trượt từ dưới lên khi mở; khi đóng (Hủy, Xác nhận, Back) thì trượt xuống xong mới gọi onDismiss / onConfirm
    // để màn hình không biến mất đột ngột.
    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val close: (() -> Unit) -> Unit = { after ->
        if (afterClose == null) {
            afterClose = after
            transition.targetState = false
        }
    }
    LaunchedEffect(afterClose, transition.currentState) {
        val after = afterClose
        if (after != null && !transition.currentState && transition.isIdle) after()
    }

    // Có blur (chỉ ở chế độ inline): đăng ký để DialogBlurScrim làm mờ trang phía sau; bỏ đăng ký ngay khi bắt đầu đóng để nền mờ tan cùng lúc màn hình trượt xuống.
    val requests = LocalBlurRequests.current
    val glass = inline && requests != null
    if (glass && requests != null) {
        val open = transition.targetState
        DisposableEffect(open) {
            if (open) requests.dialogs++
            onDispose { if (open) requests.dialogs-- }
        }
    }

    val screen: @Composable () -> Unit = {
        AnimatedVisibility(
            visibleState = transition,
            enter = slideInVertically(animationSpec = tween(300)) { it },
            exit = slideOutVertically(animationSpec = tween(220)) { it },
        ) {
            // Lề bốn phía 16dp và khoảng cách giữa các khối 16dp (SCREEN_MARGIN), giống log shell toàn màn hình:
            // tiêu đề, ô tìm, danh sách, hai nút. Mỗi dòng trong danh sách vẫn bấm được trên toàn chiều ngang.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background.let { if (glass) it.copy(alpha = GLASS_ALPHA) else it })
                    // Inline: chặn chạm xuyên xuống trang phía sau qua phần nền bán trong suốt.
                    .then(
                        if (inline) {
                            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        } else {
                            Modifier
                        },
                    )
                    .statusBarsPadding()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SCREEN_MARGIN, end = SCREEN_MARGIN, top = SCREEN_MARGIN, bottom = SCREEN_MARGIN),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tiêu đề và mô tả (desc) của item, cùng kiểu với hàng tiêu đề của log: mô tả cỡ 12sp, màu phụ, nằm dưới tiêu đề.
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        desc?.takeIf { it.isNotBlank() }?.let { text ->
                            Text(
                                text = text,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // Góc phải trên cùng, trong khung bo góc có nền, không viền (cùng kiểu nút Chạy): đang nạp danh sách thì hiện
                    // "Đang tải danh sách…" (cả chọn một và chọn nhiều); xong thì chọn nhiều hiện số mục đã chọn, chọn một thì không hiện gì.
                    val topRight = when {
                        loading -> stringResource(R.string.applist_loading)
                        multi -> stringResource(R.string.select_count_of, draft.size, allOptions.size)
                        else -> null
                    }
                    if (topRight != null) {
                        Text(
                            text = topRight,
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = RUN_BUTTON_ALPHA))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SCREEN_MARGIN).height(SELECT_CONTROL_HEIGHT),
                    label = stringResource(R.string.select_search),
                    singleLine = true,
                    insideMargin = DpSize(16.dp, 13.dp),
                )
                // Khoảng cách ô tìm tới chữ dòng đầu = SCREEN_MARGIN: dòng đã có đệm trên 12dp (ListRowMargin) nên chỉ chừa phần còn lại.
                Spacer(Modifier.height(SCREEN_MARGIN - ListRowMargin.calculateTopPadding()))

                // Vùng danh sách: không bọc khung đặc (không Card), các dòng nằm thẳng trên nền màn hình
                // nên khi có blur thì list hiện trên trang phía sau đã làm mờ. Lề ngoài 16dp hai bên.
                // Ô Chọn tất cả nằm trên cùng, cách danh sách bằng một đường kẻ.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = SCREEN_MARGIN),
                ) {
                    if (multi) {
                        BasicComponent(
                            insideMargin = ListRowMargin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = visible.isNotEmpty() && !loading,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { toggleAll() },
                            title = stringResource(if (query.isBlank()) R.string.select_all else R.string.select_all_results),
                            enabled = visible.isNotEmpty() && !loading,
                            endActions = {
                                Checkbox(
                                    state = allState,
                                    enabled = visible.isNotEmpty() && !loading,
                                    onClick = { toggleAll() },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                    if (visible.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.select_no_match),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(visible) { option ->
                                if (option.placeholder) {
                                    SkeletonRow(shimmer ?: rememberShimmerBrush(), multi)
                                    return@items
                                }
                                val checked = option.value in draft
                                val click = {
                                    if (multi) {
                                        haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                        applyDraft(if (checked) draft - option.value else draft + option.value)
                                    } else {
                                        if (!checked) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        draft = setOf(option.value)
                                        if (instant) close { onConfirm(setOf(option.value)) }
                                    }
                                }
                                BasicComponent(
                                    insideMargin = ListRowMargin,
                                    // Bấm cả dòng nhưng không có hiệu ứng nhấn (indication = null, không dùng onClick của BasicComponent).
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { click() },
                                    title = option.label,
                                    // applist: icon app bên trái, package name làm dòng phụ.
                                    summary = if (option.app) option.value else null,
                                    startAction = if (option.app) ({ AppIcon(option.value) }) else null,
                                    // Chọn một: dòng đã chọn đổi chữ sang màu chủ đạo; chọn nhiều giữ nguyên màu chữ.
                                    titleColor = if (!multi && checked) {
                                        BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary)
                                    } else {
                                        BasicComponentDefaults.titleColor()
                                    },
                                    endActions = {
                                        if (multi) {
                                            Checkbox(
                                                state = if (checked) ToggleableState.On else ToggleableState.Off,
                                                onClick = { click() },
                                            )
                                        } else {
                                            RadioDot(selected = checked)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(start = SCREEN_MARGIN, end = SCREEN_MARGIN, top = SCREEN_MARGIN, bottom = SCREEN_MARGIN),
                    horizontalArrangement = Arrangement.spacedBy(SCREEN_MARGIN),
                ) {
                    if (instant) {
                        DialogButton(
                            text = stringResource(R.string.action_close),
                            onClick = { close(onDismiss) },
                            modifier = Modifier.weight(1f).height(SELECT_CONTROL_HEIGHT),
                            primary = true,
                        )
                    } else {
                        DialogButton(text = stringResource(R.string.action_cancel), onClick = { close(onDismiss) }, modifier = Modifier.weight(1f).height(SELECT_CONTROL_HEIGHT))
                        DialogButton(
                            text = stringResource(R.string.action_confirm),
                            // Đang nạp danh sách thì mờ và không bấm được (thứ tự danh sách còn đổi khi sắp theo tên).
                            onClick = {
                                if (!loading) {
                                    val result = draft
                                    close { onConfirm(result) }
                                }
                            },
                            modifier = Modifier.weight(1f).height(SELECT_CONTROL_HEIGHT).alpha(if (loading) 0.4f else 1f),
                            primary = true,
                        )
                    }
                }
            }
        }
    }

    if (inline) {
        // Back đóng màn hình chọn (trượt xuống rồi mới gọi onDismiss). Chỉ có mặt khi đang hiện nên được ưu tiên hơn Back của trang.
        BackHandler { close(onDismiss) }
        screen()
    } else {
        Dialog(
            onDismissRequest = { close(onDismiss) },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) { screen() }
    }
}

/** Dải sáng chạy ngang (shimmer) dùng chung cho mọi dòng khung xương: một animation duy nhất cho cả danh sách. */
@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    val glow = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.30f)
    val progress by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "skeletonShift",
    )
    val start = -300f + 1500f * progress
    return Brush.linearGradient(listOf(base, glow, base), start = Offset(start, 0f), end = Offset(start + 300f, 0f))
}

/** Dòng khung xương (kiểu Facebook) giữ chỗ cho một app chưa đọc xong tên: ô icon, hai thanh chữ, ô chọn. Cao bằng dòng thật. */
@Composable
private fun SkeletonRow(brush: Brush, multi: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(ListRowMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 12.dp).size(40.dp).clip(RoundedCornerShape(10.dp)).background(brush))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.8f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(22.dp)
                .clip(if (multi) RoundedCornerShape(6.dp) else CircleShape)
                .background(brush),
        )
    }
}

/** Icon của một app (type `applist`): nạp ở luồng nền, có đệm; chưa nạp xong hoặc app đã gỡ thì chừa ô trống để dòng không nhảy. */
@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(InstalledApps.cachedIcon(pkg), pkg) {
        if (value == null) value = InstalledApps.icon(context, pkg)
    }
    Box(Modifier.padding(end = 12.dp).size(40.dp)) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}
