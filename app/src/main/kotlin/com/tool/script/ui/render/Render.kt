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
import androidx.compose.ui.unit.offset
import com.tool.script.config.Item
import coil3.compose.AsyncImage
import com.tool.script.config.ItemType
import com.tool.script.config.LockMode
import com.tool.script.config.ShellMode
import com.tool.script.executor.ExecResult
import com.tool.script.executor.ExecutorManager
import com.tool.script.state.VariableStore
import com.tool.script.ui.feedback.FeedbackHost
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tool.script.config.SelectOption
import com.tool.script.ui.main.MenuItem
import com.tool.script.ui.main.MenuPopup
import com.tool.script.util.Util
import com.tool.script.util.ShellText
import com.tool.script.util.formatNumber
import kotlinx.coroutines.CoroutineScope
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
private val SELECT_ROW_BLEED = 48.dp

// Độ đậm nền nút Chạy (0 = trong suốt, 1 = đặc). Muốn nhạt hơn nữa thì giảm số này.
private const val RUN_BUTTON_ALPHA = 0.16f

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
            if (notifies && feedback.busy) return@action
            val runId = if (notifies) feedback.beginRun() else 0
            val job = scope.launch {
                try {
                    val vars = store.snapshot() + extra
                    val full = ShellText.withEnv(command, vars)
                    val result = if (item.shell == ShellMode.Live || item.shell == ShellMode.Full) {
                        val entry = feedback.startLive(item.title, command, full = item.shell == ShellMode.Full)
                        ExecutorManager.current.exec(full, item.timeoutSec) { line -> entry.append(line) }
                            .also { entry.finish(it) }
                    } else {
                        ExecutorManager.current.exec(full, item.timeoutSec)
                    }
                    feedback.report(item, command, result)
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
    val enabled = !locked && !(env.feedback.busy && (item.shell == ShellMode.Banner || item.shell == ShellMode.Notice))
    val modifier = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.4f)
    when (item.type) {
        ItemType.Text -> TextItem(item, modifier, inGroup)
        ItemType.Group -> GroupItem(item, env)
        ItemType.Button -> ButtonItem(item, env, modifier, enabled, inGroup)
        ItemType.Switch -> SwitchItem(item, env, modifier, enabled, inGroup)
        ItemType.Input -> InputItem(item, env, modifier, enabled, inGroup)
        ItemType.Select -> SelectItem(item, env, modifier, enabled, inGroup)
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
        BasicComponent(title = item.title, summary = item.content ?: item.desc)
    }
}

/** Cắt bớt phần hở phía trên tiêu đề group (hở với thẻ ở trên). Tăng số này để hở nhỏ hơn nữa. */
private val GroupTitleTrimTop = 8.dp

/** Dời tiêu đề group sang trái, để thẳng hàng với chữ trong thẻ. */
private val GroupTitleShiftLeft = 12.dp

@Composable
private fun GroupItem(item: Item, env: RenderEnv) {
    val children = item.children.filter { isShown(it, env.store) }
    if (children.isEmpty()) return // group trống (con bị ẩn hết) thì ẩn luôn cả tiêu đề
    Column {
        SmallTitle(
            text = item.title,
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val trim = GroupTitleTrimTop.roundToPx().coerceAtMost(placeable.height)
                val shift = GroupTitleShiftLeft.roundToPx()
                layout(placeable.width, placeable.height - trim) { placeable.place(-shift, -trim) }
            },
        )
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
            val result = ExecutorManager.current.exec(ShellText.withEnv(get, env.store.snapshot()), item.timeoutSec)
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
            title = item.title,
            summary = item.desc,
            startAction = itemIcon(item),
            enabled = enabled,
            endActions = { control(checked, enabled, if (hapticOnControl) change else apply) },
            onClick = if (rowClickable) ({ change(!checked) }) else null,
        )
    }
}

/** Chọn một trong nhiều `options` (giống select) nhưng hiện sẵn tất cả lựa chọn, mỗi dòng một nút tròn. */
@Composable
private fun RadioItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    val current = env.store[id]
    val haptic = LocalHapticFeedback.current
    ItemSurface(modifier, inGroup) {
        BasicComponent(title = item.title, summary = item.desc, enabled = enabled, startAction = itemIcon(item))
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
            val result = ExecutorManager.current.exec(ShellText.withEnv(get, env.store.snapshot()), item.timeoutSec)
            val number = result.stdout.trim().toFloatOrNull()
            if (result.ok && number != null) env.store[id] = formatNumber(number.coerceIn(item.min, item.max))
        }
    }

    val value = (env.store[id].toFloatOrNull() ?: item.min).coerceIn(item.min, item.max)
    val fraction = if (item.max > item.min) (value - item.min) / (item.max - item.min) else 0f

    ItemSurface(modifier, inGroup) {
        Column(Modifier.padding(16.dp)) {
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
        Column(Modifier.padding(16.dp)) {
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
            )
            if (!env.varsOnly) {
                Spacer(Modifier.height(12.dp))
                // Nút Chạy dạng nhạt: nền màu chủ đạo mờ, chữ màu chủ đạo (cùng kiểu mục đang chọn của radio/select).
                TextButton(
                    text = "Chạy",
                    onClick = withTapHaptic { env.run(item, item.run) },
                    enabled = enabled && !item.run.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = RUN_BUTTON_ALPHA),
                        textColor = MiuixTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SelectItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    var showDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val current = env.store[id]
    val label = item.options.firstOrNull { it.value == current }?.label ?: current

    ItemSurface(modifier, inGroup) {
        BasicComponent(
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
            onClick = withTapHaptic { showDialog = true },
        )
    }
    OverlayDialog(show = showDialog, title = item.title, onDismissRequest = { showDialog = false }) {
        val choose = { option: SelectOption ->
            showDialog = false
            if (option.value != current) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            env.run(item, item.run, mapOf(id to option.value)) { result ->
                if (result.ok) env.store[id] = option.value
            }
        }
        if (item.columns <= 1) {
            // Mỗi lựa chọn một dòng: dòng đang chọn nền màu chủ đạo nhạt tràn hết chiều ngang hộp thoại,
            // chữ màu chủ đạo, dấu tích bên phải.
            Column {
                item.options.forEach { option ->
                    SelectDialogRow(
                        label = option.label,
                        selected = option.value == current,
                        onClick = { choose(option) },
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.options.chunked(item.columns).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOptions.forEach { option ->
                            val selected = option.value == current
                            TextButton(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                // Mục đang chọn: nền màu chủ đạo nhạt, chữ màu chủ đạo.
                                colors = if (selected) {
                                    ButtonDefaults.textButtonColors(
                                        color = MiuixTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA),
                                        textColor = MiuixTheme.colorScheme.primary,
                                    )
                                } else {
                                    ButtonDefaults.textButtonColors()
                                },
                                onClick = { choose(option) },
                            )
                        }
                        repeat(item.columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * Một dòng lựa chọn trong hộp thoại `select` (kiểu HyperOS): chữ trái, dấu tích phải.
 * Hàng được kéo rộng ra [SELECT_ROW_BLEED] mỗi bên (vượt lề trong của hộp thoại, phần thừa bị hộp thoại cắt theo
 * góc bo) rồi lùi chữ vào đúng bấy nhiêu, nên nền và hiệu ứng nhấn phủ hết chiều ngang mà chữ vẫn thẳng hàng với tiêu đề.
 */
@Composable
private fun SelectDialogRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .layout { measurable, constraints ->
                val extra = (SELECT_ROW_BLEED * 2).roundToPx()
                val placeable = measurable.measure(constraints.offset(horizontal = extra))
                layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
            }
            .fillMaxWidth()
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = SELECT_ROW_BLEED, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        if (selected) {
            Spacer(Modifier.width(16.dp))
            AsyncImage(
                model = Util.iconModel("icons/bar_check.svg"),
                contentDescription = "Đang chọn",
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
            )
        }
    }
}

// Chọn nhiều: hàng hiện các mục đã chọn; hộp thoại có ô tích, bấm Xác nhận mới lưu và chạy `run` (chỉ khi có thay đổi).
@Composable
private fun MultiSelectItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(emptySet<String>()) }
    val haptic = LocalHapticFeedback.current
    val current = env.store[id]
    val currentSet = current.split(item.separator).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val summary = item.options.filter { it.value in currentSet }.joinToString(", ") { it.label }.ifEmpty { "Chưa chọn" }

    ItemSurface(modifier, inGroup) {
        BasicComponent(
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
    OverlayDialog(show = showDialog, title = item.title, onDismissRequest = { showDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.chunked(item.columns).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val checked = option.value in draft
                        val toggle = {
                            haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            draft = if (checked) draft - option.value else draft + option.value
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = toggle)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                state = if (checked) ToggleableState.On else ToggleableState.Off,
                                onClick = toggle,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, modifier = Modifier.weight(1f))
                        }
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

// Chọn một có xác nhận: hàng hiện mục đang chọn; hộp thoại có nút tròn, bấm Xác nhận mới lưu và chạy `run` (chỉ khi có thay đổi).
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
    OverlayDialog(show = showDialog, title = item.title, onDismissRequest = { showDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.chunked(item.columns).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val selected = option.value == draft
                        val pick = {
                            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            draft = option.value
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = pick)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = pick)
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, modifier = Modifier.weight(1f))
                        }
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

/** Icon mũi tên lên/xuống (icons/bar_updown.svg) ở cuối hàng chọn giá trị: dùng chung cho `select`, `multiselect`, `singleselect` và `dropdown`. */
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
 * Chọn một trong `options` bằng menu thả xuống ngay dưới hàng (khác `select` là hộp thoại):
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
        Column(Modifier.padding(16.dp)) {
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
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text("Không đọc được trang này", style = MiuixTheme.textStyles.headline1, color = MiuixTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MiuixTheme.textStyles.body2)
    }
}
