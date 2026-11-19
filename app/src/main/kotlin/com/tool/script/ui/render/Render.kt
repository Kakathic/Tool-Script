package com.tool.script.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tool.script.config.Item
import com.tool.script.config.ItemType
import com.tool.script.config.LockMode
import com.tool.script.executor.ExecResult
import com.tool.script.executor.ExecutorManager
import com.tool.script.state.VariableStore
import com.tool.script.ui.feedback.FeedbackHost
import com.tool.script.util.ShellText
import com.tool.script.util.formatNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
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

class ConfirmRequest(val message: String, val onConfirm: () -> Unit)

/** Mọi thứ item cần để chạy lệnh, đọc/ghi biến và hiện kết quả. */
class RenderEnv(
    val store: VariableStore,
    val feedback: FeedbackHost,
    private val scope: CoroutineScope,
    val navigateToPage: (String) -> Unit,
) {
    var confirm by mutableStateOf<ConfirmRequest?>(null)

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
        if (command.isNullOrBlank()) {
            onDone(ExecResult(0, "", ""))
            return
        }
        val action: () -> Unit = {
            scope.launch {
                val vars = store.snapshot() + extra
                val result = ExecutorManager.current.exec(ShellText.withEnv(command, vars), item.timeoutSec)
                feedback.report(item, command, result)
                onDone(result)
            }
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
    val enabled = !locked
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
            enabled = enabled,
            onClick = { env.run(item, item.run) },
        )
    }
}

@Composable
private fun SwitchItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    BinaryItem(item, env, modifier, enabled, inGroup, rowClickable = false) { checked, isEnabled, onChange ->
        Switch(checked = checked, enabled = isEnabled, onCheckedChange = onChange)
    }
}

/** Giống switch (on / off / get) nhưng hiện ô tick; bấm cả hàng cũng đổi trạng thái. */
@Composable
private fun CheckboxItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    BinaryItem(item, env, modifier, enabled, inGroup, rowClickable = true) { checked, isEnabled, onChange ->
        Checkbox(checked = checked, enabled = isEnabled, onCheckedChange = onChange)
    }
}

/** Khung chung cho item bật / tắt: đọc `get` lúc xuất hiện, chạy `on` / `off`, chỉ đổi trạng thái khi lệnh thành công. */
@Composable
private fun BinaryItem(
    item: Item,
    env: RenderEnv,
    modifier: Modifier,
    enabled: Boolean,
    inGroup: Boolean,
    rowClickable: Boolean,
    control: @Composable (checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) -> Unit,
) {
    val id = item.id ?: return
    val checked = env.store[id] == "1"

    // Đọc trạng thái hiện tại bằng `get` khi item xuất hiện lần đầu.
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

    val change: (Boolean) -> Unit = { newValue ->
        val value = if (newValue) "1" else "0"
        val command = if (newValue) item.on else item.off
        // Chỉ đổi trạng thái khi lệnh thành công (hoặc item không có lệnh).
        env.run(item, command, mapOf(id to value)) { result ->
            if (result.ok) env.store[id] = value
        }
    }

    ItemSurface(modifier, inGroup) {
        BasicComponent(
            title = item.title,
            summary = item.desc,
            enabled = enabled,
            endActions = { control(checked, enabled, change) },
            onClick = if (rowClickable) ({ change(!checked) }) else null,
        )
    }
}

/** Chọn một trong nhiều `options` (giống select) nhưng hiện sẵn tất cả lựa chọn, mỗi dòng một nút tròn. */
@Composable
private fun RadioItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    val current = env.store[id]
    ItemSurface(modifier, inGroup) {
        BasicComponent(title = item.title, summary = item.desc, enabled = enabled)
        item.options.forEach { option ->
            val select = {
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
            Text(item.title, style = MiuixTheme.textStyles.headline1)
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { env.run(item, item.run) },
                enabled = enabled && !item.run.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Chạy")
            }
        }
    }
}

@Composable
private fun SelectItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    var showDialog by remember { mutableStateOf(false) }
    val current = env.store[id]
    val label = item.options.firstOrNull { it.value == current }?.label ?: current

    ItemSurface(modifier, inGroup) {
        BasicComponent(
            title = item.title,
            summary = item.desc,
            enabled = enabled,
            endActions = {
                Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantActions, style = MiuixTheme.textStyles.body2)
            },
            onClick = { showDialog = true },
        )
    }
    OverlayDialog(show = showDialog, title = item.title, onDismissRequest = { showDialog = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.options.forEach { option ->
                TextButton(
                    text = if (option.value == current) "✓ ${option.label}" else option.label,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showDialog = false
                        env.run(item, item.run, mapOf(id to option.value)) { result ->
                            if (result.ok) env.store[id] = option.value
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderItem(item: Item, env: RenderEnv, modifier: Modifier, enabled: Boolean, inGroup: Boolean) {
    val id = item.id ?: return
    val value = (env.store[id].toFloatOrNull() ?: item.min).coerceIn(item.min, item.max)
    val steps = (((item.max - item.min) / item.step).roundToInt() - 1).coerceAtLeast(0)

    ItemSurface(modifier, inGroup) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    env.store[id] = formatNumber(snapped.coerceIn(item.min, item.max))
                },
                enabled = enabled,
                valueRange = item.min..item.max,
                steps = steps,
                onValueChangeFinished = { env.run(item, item.run) },
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
            enabled = enabled,
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = { item.page?.let(env.navigateToPage) },
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
