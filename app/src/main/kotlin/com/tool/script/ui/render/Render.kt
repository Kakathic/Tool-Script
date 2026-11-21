package com.tool.script.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tool.script.config.Item
import coil3.compose.AsyncImage
import com.tool.script.config.ItemType
import com.tool.script.config.LockMode
import com.tool.script.config.ShellMode
import com.tool.script.crash.CrashDemo
import com.tool.script.executor.ExecResult
import com.tool.script.executor.ExecutorManager
import com.tool.script.state.VariableStore
import com.tool.script.ui.feedback.FeedbackHost
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tool.script.ui.main.MenuItem
import com.tool.script.ui.main.MenuPopup
import com.tool.script.util.Util
import com.tool.script.util.ShellText
import com.tool.script.util.formatNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

// Renderer: ánh xạ type trong TOML sang composable Miuix.
// Lề 2 bên 16dp, khoảng cách giữa các thẻ 16dp (do trang đặt). Áp dụng lock (readonly = alpha / hide) và visible_if.

private const val SELECTED_ALPHA = 0.16f

// Nền nhạt của thẻ chưa chọn trong hộp thoại chọn (thẻ không viền).
private const val UNSELECTED_ALPHA = 0.06f

// Độ đậm nền nút Chạy (0 = trong suốt, 1 = đặc). Muốn nhạt hơn nữa thì giảm số này.
internal const val RUN_BUTTON_ALPHA = 0.16f

// Lệnh `get` (đọc trạng thái) không khai báo timeout vẫn giới hạn 30 giây để không treo giao diện; lệnh run thì không giới hạn.
private const val GET_TIMEOUT_SEC = 30

// Nút Chạy trong ô nhập: ô cao khoảng 56dp (đệm 16dp + một dòng chữ), nút cao 40dp nên hở trên và dưới còn 8dp, bằng hở bên phải.
// Muốn hở nhỏ hơn thì tăng chiều cao nút; RUN_BUTTON_GAP_START là khoảng cách từ chữ đang nhập tới nút.
private val RUN_BUTTON_HEIGHT = 40.dp
private val RUN_BUTTON_GAP = 8.dp
private val RUN_BUTTON_GAP_START = 12.dp

// Bọc onClick của item: rung nhẹ khi chạm rồi mới chạy hành động.
@Composable
private fun withTapHaptic(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
}

class ConfirmRequest(val message: String, val onConfirm: () -> Unit)

/** Mọi thứ item cần để chạy lệnh, đọc/ghi biến và hiện kết quả. */
class RenderEnv(
    val store: VariableStore,
    val feedback: FeedbackHost,
    private val scope: CoroutineScope,
    val openTab: (String) -> Unit,
    val openPage: (String) -> Unit,
    /** true = chỉ truyền biến (trang dialog): item không chạy lệnh, lệnh do nút Xác nhận của dialog chạy. */
    val varsOnly: Boolean = false,
) {
    var confirm by mutableStateOf<ConfirmRequest?>(null)

    /** Bản dùng trong dialog: cùng kho biến, nhưng item chỉ đổi biến, không chạy lệnh. */
    fun asVarsOnly() = RenderEnv(store, feedback, scope, openTab, openPage, varsOnly = true)

    /**
     * Chạy [command] của [item] với các biến trong kho cộng [extra]; hiện kết quả theo `shell`.
     * Có `confirm` thì hỏi trước. [onDone] chạy sau khi lệnh xong (kể cả lỗi).
     */
    fun run(
        item: Item,
        command: String?,
        extra: Map<String, String> = emptyMap(),
        onDone: (ExecResult) -> Unit = {},
    ) {
        if (varsOnly || command.isNullOrBlank()) {
            onDone(ExecResult(0, "", ""))
            return
        }
        val notifies = item.shell == ShellMode.Banner || item.shell == ShellMode.Notice
        val action: () -> Unit = action@{
            // Lệnh demo `@crash` / `@crash-thread`: cố ý làm app crash để xem Trung tâm lỗi (xem crash/CrashDemo.kt).
            if (CrashDemo.trigger(command)) return@action
            if (notifies && feedback.isBusy(item)) return@action
            val runId = if (notifies) feedback.beginRun(item) else 0
            val job = scope.launch {
                try {
                    val vars = store.snapshot() + extra
                    val full = ShellText.withEnv(command, vars)
                    val result = if (item.shell == ShellMode.Live || item.shell == ShellMode.Full) {
                        val entry = feedback.startLive(item.title, command, full = item.shell == ShellMode.Full)
                        // Nút Hủy của log toàn màn hình: hủy coroutine đang chạy lệnh này.
                        entry.cancelAction = { coroutineContext[Job]?.cancel() }
                        ExecutorManager.current.exec(full, item.timeoutSec) { line, isError -> entry.append(line, isError) }
                            .also { entry.finish(it) }
                    } else {
                        ExecutorManager.current.exec(full, item.timeoutSec)
                    }
                    feedback.report(item, command, result, runId)
                    onDone(result)
                } finally {
                    if (notifies) feedback.endRun(runId)
                }
            }
            if (notifies) feedback.attachJob(runId, job)
        }
        val message = item.confirm
        if (message != null) confirm = ConfirmRequest(message, action) else action()
    }
}

/** Item có được vẽ không (visible_if, và lock kiểu hide). Đọc kho biến nên tự vẽ lại khi biến đổi. */
fun isShown(item: Item, store: VariableStore): Boolean {
    val visibleIf = item.visibleIf
    if (visibleIf != null && !store.evaluate(visibleIf)) return false
    val lock = item.lock
    if (lock != null && item.lockMode == LockMode.Hide && store.evaluate(lock)) return false
    return true
}

@Composable
fun ItemRenderer(item: Item, env: RenderEnv, inGroup: Boolean = false) {
    val lock = item.lock
    val locked = lock != null && env.store.evaluate(lock)
    // Item bị khóa (mờ) khi dính `lock`, hoặc khi banner của chính nó còn hiện (đang chạy tới hết đếm ngược 5 giây); item banner khác vẫn bấm được.
    val bannerBusy = (item.shell == ShellMode.Banner || item.shell == ShellMode.Notice) && env.feedback.isBusy(item)
    val enabled = !locked && !bannerBusy
    val modifier = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.4f)
    when (item.type) {
        ItemType.Text -> TextItem(item, modifier, inGroup)
        ItemType.Group -> GroupItem(item, env)
        ItemType.Button -> ButtonItem(item, env, modifier, enabled, inGroup)
        ItemType.Switch -> SwitchItem(item, env, modifier, enabled, inGroup)
        ItemType.Input -> InputItem(item, env, modifier, enabled, inGroup)
        ItemType.MultiSelect -> MultiSelectItem(item, env, modifier, enabled, inGroup)
        ItemType.SingleSelect -> SingleSelectItem(item, env, modifier, enabled, inGroup)
        ItemType.Dropdown -> DropdownItem(item, env, modifier, enabled, inGroup)
        ItemType.Slider -> SliderItem(item, env, modifier, enabled, inGroup)
        ItemType.Link -> LinkItem(item, env, modifier, enabled, inGroup)
        ItemType.Checkbox -> CheckboxItem(item, env, modifier, enabled, inGroup)
        ItemType.Radio -> RadioItem(item, env, modifier, enabled, inGroup)
        ItemType.Progress -> ProgressItem(item, env, modifier, inGroup)
    }
}

/** Đệm bên trong thẻ (từ mép thẻ tới nội dung), dùng cho mọi item: trái, phải 16dp, trên và dưới 12dp. */
internal val CardInsideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)

/**
 * Nền của một item. Item đứng riêng (ngoài group) có thẻ của mình;
 * item trong group không vẽ thẻ, vì cả group dùng chung một thẻ lớn (xem [GroupItem]).
 */
@Composable
private fun ItemSurface(modifier: Modifier, inGroup: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (inGroup) {
        Column(modifier) { content() }
    } else {
        Card(modifier) { content() }
    }
}

@Composable
private fun TextItem(item: Item, modifier: Modifier, inGroup: Boolean) {
    ItemSurface(modifier, inGroup) {
        BasicComponent(insideMargin = CardInsideMargin, title = item.title, summary = item.content ?: item.desc)
    }
}

/** Cắt bớt phần hở phía trên tiêu đề group: khoảng cách thẻ giữa các mục là 16dp, cắt 12dp để tiêu đề cách thẻ phía trên 12dp. */
private val GroupTitleTrimTop = 12.dp

/** Dời tiêu đề group sang trái, để thẳng hàng với chữ trong thẻ. */
private val GroupTitleShiftLeft = 12.dp

/** Cắt bớt phần hở phía dưới tiêu đề group để tiêu đề sát thẻ của nó hơn. Tăng số này để sát hơn nữa. */
private val GroupTitleTrimBottom = 4.dp

@Composable
private fun GroupItem(item: Item, env: RenderEnv) {
    val children = item.children.filter { isShown(it, env.store) }
    if (children.isEmpty()) return // group trống (con bị ẩn hết) thì ẩn luôn cả tiêu đề
    Column {
        // Group không có title thì không vẽ tiêu đề, chỉ còn thẻ.
        if (item.title.isNotBlank()) {
            SmallTitle(
                text = item.title,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val trim = GroupTitleTrimTop.roundToPx().coerceAtMost(placeable.height)
                    val shift = GroupTitleShiftLeft.roundToPx()
                    val trimBottom = GroupTitleTrimBottom.roundToPx().coerceAtMost((placeable.height - trim).coerceAtLeast(0))
                    layout(placeable.width, placeable.height - trim - trimBottom) { placeable.place(-shift, -trim) }
                },
            )
        }
        // Mọi item con gộp chung trong một thẻ lớn, tiêu đề dính sát thẻ.
        Card(Modifier.fillMaxWidth()) {
            children.forEach { ItemRenderer(it, env, inGroup = true) }
        }
    }
}

@Composable
private fun ButtonItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    ItemSurface(modifier, inGroup) {
        BasicComponent(
            insideMargin = CardInsideMargin,
            title = item.title,
            summary = item.desc,
            startAction = itemIcon(item),
            enabled = enabled,
            onClick = withTapHaptic { env.run(item, item.run) },
        )
    }
}

@Composable
private fun SwitchItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    // Bấm cả hàng (tiêu đề / mô tả) cũng đổi trạng thái; bấm trực tiếp vào công tắc thì dùng rung riêng của Switch.
    BinaryItem(item, env, modifier, enabled, inGroup, rowClickable = true, hapticOnControl = false) { checked, isEnabled, onChange ->
        Switch(checked = checked, enabled = isEnabled, onCheckedChange = onChange)
    }
}

/** Giống switch (on / off / get) nhưng hiện ô tick; bấm cả hàng cũng đổi trạng thái. */
@Composable
private fun CheckboxItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    BinaryItem(item, env, modifier, enabled, inGroup, rowClickable = true) { checked, isEnabled, onChange ->
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            enabled = isEnabled,
            onClick = { onChange(!checked) },
        )
    }
}

/**
 * Khung chung cho item bật / tắt: giá trị lấy từ file đã lưu (xem Boot), chạy `on` / `off`, chỉ đổi trạng thái khi lệnh thành công.
 * `get` là tùy chọn: có khai báo thì vẫn chạy lúc item xuất hiện và ghi đè giá trị (rồi được lưu lại như mọi lần đổi).
 */
@Composable
private fun BinaryItem(
    item: Item,
    env: RenderEnv,
    modifier: Modifier,
    enabled: Boolean,
    inGroup: Boolean,
    rowClickable: Boolean,
    hapticOnControl: Boolean = true,
    control: @Composable (checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) -> Unit,
) {
    val id = item.id ?: return
    val checked = env.store[id] == "1"
    val haptic = LocalHapticFeedback.current

    // Tùy chọn: có `get` thì đọc trạng thái thật bằng shell khi item xuất hiện lần đầu.
    LaunchedEffect(id) {
        val get = item.get
        if (!get.isNullOrBlank()) {
            val result = ExecutorManager.current.exec(ShellText.withEnv(get, env.store.snapshot()), item.timeoutSec ?: GET_TIMEOUT_SEC)
            if (result.ok) {
                val out = result.stdout.trim().lowercase()
                env.store[id] = if (out == "1" || out == "true" || out == "on") "1" else "0"
            }
        }
    }

    val apply: (Boolean) -> Unit = { newValue ->
        val value = if (newValue) "1" else "0"
        val command = if (newValue) item.on else item.off
        // Chỉ đổi trạng thái khi lệnh thành công (hoặc item không có lệnh).
        env.run(item, command, mapOf(id to value)) { result ->
            if (result.ok) env.store[id] = value
        }
    }
    // Bấm vào hàng: rung rồi đổi trạng thái.
    val change: (Boolean) -> Unit = { newValue ->
        haptic.performHapticFeedback(if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        apply(newValue)
    }

    ItemSurface(modifier, inGroup) {
        BasicComponent(
            insideMargin = CardInsideMargin,
            title = item.title,
            summary = item.desc,
            startAction = itemIcon(item),
            enabled = enabled,
            endActions = { control(checked, enabled, if (hapticOnControl) change else apply) },
            onClick = if (rowClickable) ({ change(!checked) }) else null,
        )
    }
}

/** Chọn một trong nhiều `options` nhưng hiện sẵn tất cả lựa chọn, mỗi dòng một nút tròn. */
@Composable
private fun RadioItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    val current = env.store[id]
    val haptic = LocalHapticFeedback.current
    ItemSurface(modifier, inGroup) {
        BasicComponent(insideMargin = CardInsideMargin, title = item.title, summary = item.desc, enabled = enabled, startAction = itemIcon(item))
        // Đường kẻ ngăn cách giữa tiêu đề và danh sách lựa chọn
        if (item.options.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item.options.forEach { option ->
            val select = {
                if (option.value != current) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                env.run(item, item.run, mapOf(id to option.value)) { result ->
                    if (result.ok) env.store[id] = option.value
                }
            }
            BasicComponent(
                insideMargin = CardInsideMargin,
                title = option.label,
                enabled = enabled,
                endActions = {
                    RadioButton(selected = option.value == current, enabled = enabled, onClick = { select() })
                },
                onClick = { select() },
            )
        }
    }
}

/** Thanh tiến trình chỉ để xem: đọc giá trị (số trong khoảng min..max) từ lệnh `get` khi item xuất hiện. */
@Composable
private fun ProgressItem(item: Item, env: RenderEnv, modifier: Modifier, inGroup: Boolean) {
    val id = item.id ?: return

    LaunchedEffect(id) {
        val get = item.get
        if (!get.isNullOrBlank()) {
            val result = ExecutorManager.current.exec(ShellText.withEnv(get, env.store.snapshot()), item.timeoutSec ?: GET_TIMEOUT_SEC)
            val number = result.stdout.trim().toFloatOrNull()
            if (result.ok && number != null) env.store[id] = formatNumber(number.coerceIn(item.min, item.max))
        }
    }

    val value = (env.store[id].toFloatOrNull() ?: item.min).coerceIn(item.min, item.max)
    val fraction = if (item.max > item.min) (value - item.min) / (item.max - item.min) else 0f

    ItemSurface(modifier, inGroup) {
        Column(Modifier.padding(CardInsideMargin)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIconInline(item)
                Text(item.title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.headline1)
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            item.desc?.let {
                Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun InputItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    ItemSurface(modifier, inGroup) {
        Column(Modifier.padding(CardInsideMargin)) {
            TitleRow(item) { Text(item.title, style = MiuixTheme.textStyles.headline1) }
            item.desc?.let {
                Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
            Spacer(Modifier.height(12.dp))
            TextField(
                value = env.store[id],
                onValueChange = { env.store[id] = it },
                modifier = Modifier.fillMaxWidth(),
                label = item.hint.orEmpty(),
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (item.numberInput) KeyboardType.Number else KeyboardType.Text,
                ),
                // Nút Chạy nằm ngay trong ô nhập, sát mép phải (trang dialog không có nút).
                trailingIcon = if (env.varsOnly) null else {
                    {
                        val runEnabled = enabled && !item.run.isNullOrBlank()
                        val onRun = withTapHaptic { env.run(item, item.run) }
                        Box(
                            modifier = Modifier
                                .padding(start = RUN_BUTTON_GAP_START, end = RUN_BUTTON_GAP)
                                .height(RUN_BUTTON_HEIGHT)
                                .alpha(if (runEnabled) 1f else 0.4f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = RUN_BUTTON_ALPHA))
                                .clickable(enabled = runEnabled, onClick = onRun)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Chạy",
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                },
            )
        }
    }
}

// Chọn nhiều: hàng hiện các mục đã chọn; hộp thoại (hoặc toàn màn hình khi từ 6 mục) có ô tích, bấm Xác nhận mới lưu và chạy `run` (chỉ khi có thay đổi).
@Composable
private fun MultiSelectItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(emptySet<String>()) }
    val haptic = LocalHapticFeedback.current
    val current = env.store[id]
    val currentSet = current.split(item.separator).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val selectedCount = item.options.count { it.value in currentSet }
    val summary = if (selectedCount == 0) "Chưa chọn" else "Đã chọn $selectedCount"

    ItemSurface(modifier, inGroup) {
        BasicComponent(
            insideMargin = CardInsideMargin,
            title = item.title,
            summary = item.desc,
            startAction = itemIcon(item),
            enabled = enabled,
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    UpDownIcon()
                }
            },
            onClick = withTapHaptic {
                draft = currentSet
                showDialog = true
            },
        )
    }
    // Từ FULLSCREEN_MIN_OPTIONS mục trở lên: chọn toàn màn hình (có ô tìm và Chọn tất cả); ít hơn thì hộp thoại nhỏ.
    val fullScreen = item.options.size >= FULLSCREEN_MIN_OPTIONS
    if (fullScreen && showDialog) {
        SelectFullScreen(
            title = item.title,
            options = item.options,
            multi = true,
            selected = currentSet,
            onDismiss = { showDialog = false },
            onConfirm = { chosen ->
                showDialog = false
                val newValue = item.options.filter { it.value in chosen }.joinToString(item.separator) { it.value }
                if (newValue != current) {
                    env.run(item, item.run, mapOf(id to newValue)) { result ->
                        if (result.ok) env.store[id] = newValue
                    }
                }
            },
        )
    }
    OverlayDialog(show = showDialog && !fullScreen, title = item.title, onDismissRequest = { showDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.chunked(item.columns).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val checked = option.value in draft
                        val toggle = {
                            haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            draft = if (checked) draft - option.value else draft + option.value
                        }
                        // 1 cột: không nền (chỉ chữ + ô tích); 2 cột: giữ thẻ có nền như cũ.
                        ChoiceCard(option.label, checked, toggle, Modifier.weight(1f), framed = item.columns > 1)
                    }
                    repeat(item.columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "Hủy", onClick = { showDialog = false }, modifier = Modifier.weight(1f))
                TextButton(
                    text = "Xác nhận",
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        showDialog = false
                        val newValue = item.options.filter { it.value in draft }.joinToString(item.separator) { it.value }
                        if (newValue != current) {
                            env.run(item, item.run, mapOf(id to newValue)) { result ->
                                if (result.ok) env.store[id] = newValue
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Một lựa chọn dạng thẻ trong hộp thoại `multiselect` / `singleselect`: thẻ bo góc không viền, chữ bên trái,
 * ô tích bên phải. Mục chưa chọn có nền xám nhạt; mục đã chọn có nền màu chủ đạo nhạt và chữ màu chủ đạo.
 * [framed] = false: bỏ nền, chỉ còn chữ và ô tích / nút tròn; dùng khi 1 cột. [radio] = true (chọn một): nút tròn thay ô tích, dòng đã chọn đổi chữ sang màu chủ đạo.
 */
@Composable
private fun ChoiceCard(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    radio: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val primary = MiuixTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    !framed -> Color.Transparent
                    checked -> primary.copy(alpha = SELECTED_ALPHA)
                    else -> MiuixTheme.colorScheme.onSurface.copy(alpha = UNSELECTED_ALPHA)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            // Mục đã chọn đổi chữ sang màu chủ đạo (thẻ có nền, hoặc chọn một dùng nút tròn). Chỉ multiselect 1 cột không nền giữ nguyên màu chữ.
            color = if (checked && (framed || radio)) primary else MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        if (radio) {
            RadioButton(selected = checked, onClick = onClick)
        } else {
            Checkbox(
                state = if (checked) ToggleableState.On else ToggleableState.Off,
                onClick = onClick,
            )
        }
    }
}

// Chọn một có xác nhận: hàng hiện mục đang chọn; hộp thoại (hoặc toàn màn hình khi từ 6 mục) có nút tròn bên phải mỗi dòng, bấm Xác nhận mới lưu và chạy `run` (chỉ khi có thay đổi).
@Composable
private fun SingleSelectItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val current = env.store[id]
    val label = item.options.firstOrNull { it.value == current }?.label ?: current

    ItemSurface(modifier, inGroup) {
        BasicComponent(
            insideMargin = CardInsideMargin,
            title = item.title,
            summary = item.desc,
            startAction = itemIcon(item),
            enabled = enabled,
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    UpDownIcon()
                }
            },
            onClick = withTapHaptic {
                draft = current
                showDialog = true
            },
        )
    }
    // Từ FULLSCREEN_MIN_OPTIONS mục trở lên: chọn toàn màn hình (có ô tìm); ít hơn thì hộp thoại nhỏ.
    val fullScreen = item.options.size >= FULLSCREEN_MIN_OPTIONS
    if (fullScreen && showDialog) {
        SelectFullScreen(
            title = item.title,
            options = item.options,
            multi = false,
            selected = setOf(current),
            onDismiss = { showDialog = false },
            onConfirm = { chosen ->
                showDialog = false
                val newValue = chosen.firstOrNull() ?: current
                if (newValue != current) {
                    env.run(item, item.run, mapOf(id to newValue)) { result ->
                        if (result.ok) env.store[id] = newValue
                    }
                }
            },
        )
    }
    OverlayDialog(show = showDialog && !fullScreen, title = item.title, onDismissRequest = { showDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.chunked(item.columns).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val selected = option.value == draft
                        val pick = {
                            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            draft = option.value
                        }
                        ChoiceCard(option.label, selected, pick, Modifier.weight(1f), framed = item.columns > 1, radio = true)
                    }
                    repeat(item.columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "Hủy", onClick = { showDialog = false }, modifier = Modifier.weight(1f))
                TextButton(
                    text = "Xác nhận",
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        showDialog = false
                        val newValue = draft
                        if (newValue != current) {
                            env.run(item, item.run, mapOf(id to newValue)) { result ->
                                if (result.ok) env.store[id] = newValue
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Icon bên trái của item (`icon` trong TOML): cỡ `icon_size`, tô màu theme nếu `icon_tint` bật, tắt thì giữ màu ảnh. */
@Composable
private fun ItemIconImage(item: Item, icon: String, balanceEnd: Boolean = true) {
    // Ô chứa icon rộng cố định tối thiểu 24dp và căn giữa: icon nhỏ/lớn đều cùng một trục,
    // chữ bên phải không bị xô lệch giữa các hàng. Miuix chỉ chừa khoảng cách nhỏ ở bên phải
    // startAction trong khi bên trái có padding 16dp của hàng, nên bù thêm 8dp ở đây cho cân.
    val slot = maxOf(item.iconSize, 24f).dp
    Box(
        modifier = Modifier
            .padding(end = if (balanceEnd) 8.dp else 0.dp)
            .size(slot),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Util.iconModel(icon),
            contentDescription = null,
            modifier = Modifier.size(item.iconSize.dp),
            colorFilter = if (item.iconTint) ColorFilter.tint(MiuixTheme.colorScheme.onSurface) else null,
        )
    }
}

private fun itemIcon(item: Item): (@Composable () -> Unit)? =
    item.icon?.let { icon -> { ItemIconImage(item, icon) } }

/** Icon đứng trước tiêu đề cho các item tự dựng bố cục (input, slider, progress). */
@Composable
private fun ItemIconInline(item: Item) {
    val icon = item.icon ?: return
    ItemIconImage(item, icon, balanceEnd = false)
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun TitleRow(item: Item, title: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemIconInline(item)
        title()
    }
}

/** Icon mũi tên lên/xuống (icons/bar_updown.svg) ở cuối hàng chọn giá trị: dùng chung cho `multiselect`, `singleselect` và `dropdown`. */
@Composable
private fun UpDownIcon() {
    AsyncImage(
        model = Util.iconModel("icons/bar_updown.svg"),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
    )
}

/**
 * Chọn một trong `options` bằng menu thả xuống ngay dưới hàng (không dùng hộp thoại):
 * bên phải hiện giá trị đang chọn kèm mũi tên lên/xuống; mục đang chọn nổi nền, có dấu tích bên phải.
 */
@Composable
private fun DropdownItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val current = env.store[id]
    val label = item.options.firstOrNull { it.value == current }?.label ?: current

    ItemSurface(modifier, inGroup) {
        // Popup lấy cả hàng làm điểm neo: menu căn mép phải với hàng, nằm ngay dưới (lật lên nếu thiếu chỗ).
        Box(Modifier.fillMaxWidth()) {
            BasicComponent(
                insideMargin = CardInsideMargin,
                title = item.title,
                summary = item.desc,
                startAction = itemIcon(item),
                enabled = enabled,
                endActions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantActions, style = MiuixTheme.textStyles.body2)
                        Spacer(Modifier.width(4.dp))
                        UpDownIcon()
                    }
                },
                onClick = withTapHaptic { expanded = true },
            )
            if (expanded) {
                MenuPopup(onDismiss = { expanded = false }, minWidth = 180.dp) {
                    item.options.forEach { option ->
                        MenuItem(label = option.label, selected = option.value == current) {
                            expanded = false
                            if (option.value != current) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            env.run(item, item.run, mapOf(id to option.value)) { result ->
                                if (result.ok) env.store[id] = option.value
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    val value = (env.store[id].toFloatOrNull() ?: item.min).coerceIn(item.min, item.max)
    val steps = (((item.max - item.min) / item.step).roundToInt() - 1).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current

    ItemSurface(modifier, inGroup) {
        Column(Modifier.padding(CardInsideMargin)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIconInline(item)
                Text(item.title, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.headline1)
                Text(
                    formatNumber(value),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            item.desc?.let {
                Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = { raw ->
                    val snapped = item.min + ((raw - item.min) / item.step).roundToInt() * item.step
                    val text = formatNumber(snapped.coerceIn(item.min, item.max))
                    // Rung nhẹ mỗi lần giá trị nhích sang bước mới (không rung khi chỉ kéo trong cùng một bước).
                    // LocalHapticFeedback đã là bản không làm gì khi người dùng tắt "Rung khi chạm".
                    if (text != env.store[id]) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        env.store[id] = text
                    }
                },
                enabled = enabled,
                valueRange = item.min..item.max,
                steps = steps,
                onValueChangeFinished = { env.run(item, item.run) },
                // Rung đã tự xử lý ở onValueChange; tắt rung có sẵn của Miuix để không bị rung đôi.
                hapticEffect = SliderDefaults.SliderHapticEffect.None,
            )
        }
    }
}

@Composable
private fun LinkItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    ItemSurface(modifier, inGroup) {
        BasicComponent(
            insideMargin = CardInsideMargin,
            title = item.title,
            summary = item.desc,
            startAction = itemIcon(item),
            enabled = enabled,
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = withTapHaptic {
                val tab = item.tab
                val page = item.page
                if (tab != null) env.openTab(tab) else if (page != null) env.openPage(page)
            },
        )
    }
}

/** Thẻ báo file trang bị lỗi parse. */
@Composable
fun PageErrorCard(message: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), insideMargin = CardInsideMargin) {
        Text("Không đọc được trang này", style = MiuixTheme.textStyles.headline1, color = MiuixTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MiuixTheme.textStyles.body2)
    }
}
