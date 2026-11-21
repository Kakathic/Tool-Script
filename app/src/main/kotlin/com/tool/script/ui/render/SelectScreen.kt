package com.tool.script.ui.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tool.script.config.SelectOption
import java.text.Normalizer
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Chọn toàn màn hình cho `singleselect` / `multiselect` khi có từ FULLSCREEN_MIN_OPTIONS mục trở lên.
// Dưới ngưỡng này vẫn dùng hộp thoại nhỏ như cũ (xem Render.kt). docs/schema.md, mục 7.

/** Số mục tối thiểu để chuyển từ hộp thoại nhỏ sang toàn màn hình. */
internal const val FULLSCREEN_MIN_OPTIONS = 6

private val COMBINING_MARKS = Regex("\\p{M}+")

/** Chuẩn hóa để tìm không phân biệt hoa thường và dấu tiếng Việt: "Đà Lạt" khớp với "da lat". */
internal fun foldSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('đ', 'd')

/**
 * Màn hình chọn toàn màn hình: tiêu đề, ô tìm kiếm, (multiselect) ô Chọn tất cả, danh sách, rồi Hủy / Xác nhận.
 *
 * - Ô tìm lọc theo nhãn (không phân biệt hoa thường và dấu).
 * - Chọn tất cả chỉ tác động lên các mục đang hiện trong kết quả tìm; mục ngoài kết quả giữ nguyên.
 *   Chưa tìm gì thì kết quả là toàn bộ danh sách.
 * - Chỉ khi bấm Xác nhận mới gọi [onConfirm]; Hủy, Back hoặc chạm ra ngoài thì [onDismiss] và bỏ thay đổi.
 *
 * Gọi khi cần hiện (`if (show) SelectFullScreen(...)`) để mỗi lần mở bắt đầu lại từ [selected].
 */
@Composable
internal fun SelectFullScreen(
    title: String,
    options: List<SelectOption>,
    multi: Boolean,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
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
    val toggleAll = {
        if (allState == ToggleableState.On) {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
            draft = draft - visibleValues.toSet()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            draft = draft + visibleValues
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Cột ngoài không có lề ngang: các dòng chọn chạy hết chiều rộng màn hình, vùng bấm và hiệu ứng nhấn
        // phủ trọn dòng, không bị thẻ bo góc cắt. Lề 16dp chỉ áp cho tiêu đề, ô tìm và hai nút.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = "Tìm kiếm",
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))

            // Vùng danh sách: dùng chính Card của Miuix (cùng màu nền với các thẻ trong app, cả sáng lẫn tối) nhưng
            // không bo góc và chạy hết ngang. Không tự chọn màu `surface`: ở chủ đề tối nó gần như đen, trùng nền màn hình.
            // Ô Chọn tất cả nằm trên cùng, cách danh sách bằng một đường kẻ.
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                cornerRadius = 0.dp,
            ) {
                if (multi) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = if (query.isBlank()) "Chọn tất cả (${visible.size})" else "Chọn tất cả kết quả tìm (${visible.size})",
                        enabled = visible.isNotEmpty(),
                        endActions = {
                            Checkbox(
                                state = allState,
                                enabled = visible.isNotEmpty(),
                                onClick = { toggleAll() },
                            )
                        },
                        onClick = { toggleAll() },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (visible.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Không có mục nào khớp",
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
                                    draft = if (checked) draft - option.value else draft + option.value
                                } else {
                                    if (!checked) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    draft = setOf(option.value)
                                }
                            }
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = option.label,
                                endActions = {
                                    if (multi) {
                                        Checkbox(
                                            state = if (checked) ToggleableState.On else ToggleableState.Off,
                                            onClick = { click() },
                                        )
                                    } else {
                                        RadioButton(selected = checked, onClick = { click() })
                                    }
                                },
                                onClick = { click() },
                            )
                        }
                    }
                }
            }

            if (multi) {
                Text(
                    text = "Đã chọn ${draft.size}/${options.size}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(text = "Hủy", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "Xác nhận",
                    onClick = { onConfirm(draft) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
