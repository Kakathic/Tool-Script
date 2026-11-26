package com.tool.script.ui.render

import com.tool.script.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import java.text.Normalizer
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import com.tool.script.ui.theme.AppTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Chọn toàn màn hình cho `singleselect` / `multiselect` khi có từ FULLSCREEN_MIN_OPTIONS mục trở lên.
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
)

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
 * Chỗ item `singleselect` / `multiselect` gọi: [show] = true thì mở màn hình chọn toàn màn hình (qua [LocalSelectHost], không có thì mở cửa sổ riêng),
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
) {
    val host = LocalSelectHost.current
    if (host == null) {
        if (show) SelectFullScreen(title, options, multi, selected, onDismiss, onConfirm, desc = desc, instant = instant)
    } else {
        val latestDismiss by rememberUpdatedState(onDismiss)
        val latestConfirm by rememberUpdatedState(onConfirm)
        DisposableEffect(show) {
            if (!show) return@DisposableEffect onDispose {}
            val request = SelectRequest(title, options, multi, selected, { latestDismiss() }, { latestConfirm(it) }, desc, instant)
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
        )
    }
}

/**
 * Màn hình chọn toàn màn hình: tiêu đề, ô tìm kiếm, (multiselect) ô Chọn tất cả, danh sách, rồi Hủy / Xác nhận.
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
) {
    var draft by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val visible = remember(options, query) {
        val q = foldSearch(query.trim())
        if (q.isEmpty()) options else options.filter { foldSearch(it.label).contains(q) }
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
                    if (multi) {
                        // Số mục đã chọn nằm ở góc phải trên cùng, trong khung bo góc có nền, không viền (cùng kiểu nút Chạy).
                        Text(
                            text = stringResource(R.string.select_count_of, draft.size, options.size),
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
                                    enabled = visible.isNotEmpty(),
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { toggleAll() },
                            title = stringResource(if (query.isBlank()) R.string.select_all else R.string.select_all_results),
                            enabled = visible.isNotEmpty(),
                            endActions = {
                                Checkbox(
                                    state = allState,
                                    enabled = visible.isNotEmpty(),
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
                        TextButton(
                            text = stringResource(R.string.action_close),
                            onClick = { close(onDismiss) },
                            modifier = Modifier.weight(1f).height(SELECT_CONTROL_HEIGHT),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    } else {
                        TextButton(text = stringResource(R.string.action_cancel), onClick = { close(onDismiss) }, modifier = Modifier.weight(1f).height(SELECT_CONTROL_HEIGHT))
                        TextButton(
                            text = stringResource(R.string.action_confirm),
                            onClick = {
                                val result = draft
                                close { onConfirm(result) }
                            },
                            modifier = Modifier.weight(1f).height(SELECT_CONTROL_HEIGHT),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
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
