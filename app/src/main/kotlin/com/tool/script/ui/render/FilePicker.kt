package com.tool.script.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.tool.script.R
import com.tool.script.executor.ExecutorManager
import com.tool.script.ui.theme.AppTextField
import com.tool.script.ui.theme.DialogButton
import com.tool.script.util.AppIcons
import com.tool.script.util.ShellText
import com.tool.script.util.Util
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Trình quản lý tệp cho type `file` / `folder` (docs/schema.md mục 7). Duyệt thư mục bằng `ls` qua backend shell
// (có root thì xem được cả "/"), nên đường dẫn trả về là đường dẫn thật chứ không phải content:// của hệ thống.

private val PICKER_MARGIN = 16.dp
private val PICKER_CONTROL_HEIGHT = 50.dp
private val PICKER_ROW_MARGIN = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
private const val LIST_TIMEOUT_SEC = 15

private data class PathEntry(val name: String, val isDir: Boolean)

private fun joinPath(dir: String, name: String) = if (dir == "/") "/$name" else "$dir/$name"

private fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" }

/**
 * Màn hình chọn tệp / thư mục toàn màn hình.
 * - `file`: bấm thư mục để mở, bấm tệp để chọn (multi = true chọn nhiều, có thể chọn ở nhiều thư mục khác nhau).
 * - `folder`: bấm thư mục để mở, ô chọn ở cuối dòng để chọn thư mục đó; không chọn gì thì Xác nhận lấy thư mục đang mở.
 * [onConfirm] nhận các đường dẫn nối bằng [separator]. [ext] rỗng = mọi tệp.
 */
@Composable
internal fun FilePicker(
    title: String,
    desc: String?,
    folder: Boolean,
    multi: Boolean,
    ext: List<String>,
    start: String,
    separator: String,
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember { current.split(separator).map { it.trim() }.filter { it.startsWith("/") } }
    var dir by remember { mutableStateOf(initial.firstOrNull()?.let(::parentOf) ?: start.ifBlank { "/" }) }
    var selected by remember { mutableStateOf(if (multi) initial.toSet() else initial.take(1).toSet()) }
    var entries by remember { mutableStateOf<List<PathEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Lịch sử các thư mục đã rời đi: Back quay lại thư mục trước đó (hết lịch sử mới đóng màn hình chọn).
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    val startDir = start.ifBlank { "/" }
    val go: (String) -> Unit = { target ->
        if (target != dir) {
            history = history + dir
            query = ""
            dir = target
        }
    }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(dir) {
        loading = true
        failed = false
        val result = try {
            ExecutorManager.current.exec(ShellText.silenceStderr("ls -1ALp -- ${ShellText.quote(dir)}"), LIST_TIMEOUT_SEC)
        } catch (_: Exception) {
            null
        }
        if (result == null || !result.ok) {
            entries = emptyList()
            failed = true
        } else {
            entries = result.output.lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() }
                .map { if (it.endsWith("/")) PathEntry(it.dropLast(1), true) else PathEntry(it, false) }
                .filter { folder.not() || it.isDir }
                .filter { it.isDir || ext.isEmpty() || it.name.substringAfterLast('.', "").lowercase() in ext }
                .sortedWith(compareBy<PathEntry> { !it.isDir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .toList()
        }
        loading = false
    }

    val shown = remember(entries, query) {
        val q = foldSearch(query.trim())
        if (q.isEmpty()) entries else entries.filter { foldSearch(it.name).contains(q) }
    }
    val toggle: (String) -> Unit = { path ->
        haptic.performHapticFeedback(if (path in selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        selected = if (multi) {
            if (path in selected) selected - path else selected + path
        } else {
            if (path in selected) emptySet() else setOf(path)
        }
    }
    val confirm = {
        // Thư mục: không chọn gì thì lấy thư mục đang mở. Tệp: phải chọn ít nhất một.
        val result = if (selected.isNotEmpty()) selected.toList() else if (folder) listOf(dir) else emptyList()
        if (result.isNotEmpty()) onConfirm(result.joinToString(separator))
    }
    val canConfirm = selected.isNotEmpty() || folder

    // Dialog là cửa sổ riêng nên phím Back vào thẳng cửa sổ này (gọi onDismissRequest), BackHandler của Activity không nhận được.
    val onBack = {
        if (history.isEmpty()) {
            onDismiss()
        } else {
            dir = history.last()
            history = history.dropLast(1)
            query = ""
        }
    }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(PICKER_MARGIN), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    desc?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                val topRight = when {
                    loading -> stringResource(R.string.path_loading)
                    multi && selected.isNotEmpty() -> stringResource(R.string.path_selected, selected.size)
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
            // Đường dẫn thư mục đang mở, kèm nút về nhanh thư mục bắt đầu (`start`) khi đang ở chỗ khác.
            Row(Modifier.fillMaxWidth().padding(horizontal = PICKER_MARGIN), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dir,
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dir != startDir) {
                    Text(
                        text = stringResource(R.string.path_go_start),
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = RUN_BUTTON_ALPHA))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                go(startDir)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(PICKER_MARGIN))
            AppTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PICKER_MARGIN).height(PICKER_CONTROL_HEIGHT),
                label = stringResource(R.string.select_search),
                singleLine = true,
                insideMargin = DpSize(16.dp, 13.dp),
            )
            Spacer(Modifier.height(PICKER_MARGIN - PICKER_ROW_MARGIN.calculateTopPadding()))
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = PICKER_MARGIN)) {
                if (dir != "/") {
                    BasicComponent(
                        insideMargin = PICKER_ROW_MARGIN,
                        modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            go(parentOf(dir))
                        },
                        title = "..",
                        summary = stringResource(R.string.path_up),
                        startAction = { PathIcon(AppIcons.BarFolder) },
                    )
                    HorizontalDivider()
                }
                when {
                    loading -> Unit
                    failed -> CenterNote(stringResource(R.string.path_read_failed) + "\n" + stringResource(R.string.path_need_root))
                    shown.isEmpty() -> CenterNote(stringResource(if (query.isBlank()) R.string.path_empty else R.string.select_no_match))
                    else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(shown, key = { it.name }) { entry ->
                            val path = joinPath(dir, entry.name)
                            val checked = path in selected
                            val selectable = if (folder) entry.isDir else !entry.isDir
                            BasicComponent(
                                insideMargin = PICKER_ROW_MARGIN,
                                modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    when {
                                        entry.isDir -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            go(path)
                                        }
                                        selectable -> toggle(path)
                                    }
                                },
                                title = entry.name,
                                titleColor = if (!multi && checked) {
                                    BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary)
                                } else {
                                    BasicComponentDefaults.titleColor()
                                },
                                startAction = { PathIcon(if (entry.isDir) AppIcons.BarFolder else AppIcons.BarFile) },
                                endActions = {
                                    if (selectable) {
                                        if (multi) {
                                            Checkbox(state = if (checked) ToggleableState.On else ToggleableState.Off, onClick = { toggle(path) })
                                        } else {
                                            RadioDot(selected = checked)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(PICKER_MARGIN), horizontalArrangement = Arrangement.spacedBy(PICKER_MARGIN)) {
                DialogButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.weight(1f).height(PICKER_CONTROL_HEIGHT))
                DialogButton(
                    text = stringResource(if (folder && selected.isEmpty()) R.string.path_pick_here else R.string.action_confirm),
                    onClick = { if (canConfirm) confirm() },
                    modifier = Modifier.weight(1f).height(PICKER_CONTROL_HEIGHT).alpha(if (canConfirm) 1f else 0.4f),
                    primary = true,
                )
            }
        }
    }
}

@Composable
private fun PathIcon(icon: String) {
    AsyncImage(
        model = Util.iconModel(icon),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
    )
}

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = PICKER_MARGIN))
    }
}
