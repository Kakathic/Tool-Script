package com.tool.script.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.tool.script.ui.theme.LocalGlassEnabled
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.overlay.OverlayDialog

// Làm mờ nền phía sau dialog (OverlayDialog của Miuix, vẽ ở lớp popup của Scaffold gốc) bật nền mờ riêng thay cho lớp tối của Miuix.
// Đăng ký qua [BlurRequests], chỉ có khi MainScreen bật blur ([LocalBlurRequests] = null thì mọi thứ chạy như cũ).

/** Số lớp phủ đang yêu cầu làm mờ nền. */
@Stable
class BlurRequests {
    var dialogs by mutableIntStateOf(0)
}

val LocalBlurRequests = staticCompositionLocalOf<BlurRequests?> { null }

/** Backdrop của cả trang (không gồm lớp popup của Scaffold gốc) để các thành phần glass làm mờ phần phía sau; null thì glass dùng nền đặc. */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Thay cho OverlayDialog: có blur thì tắt lớp tối của Miuix và đăng ký để [DialogBlurScrim] vẽ nền mờ,
 * không có blur thì y hệt OverlayDialog.
 * Có blur thì luôn vẽ ở lớp popup của Scaffold gốc (trên nền mờ), nếu không dialog nằm trong trang và bị [DialogBlurScrim] phủ mờ theo.
 * [centered] = true: dialog hiện giữa màn hình (phóng to / mờ dần) kể cả trên điện thoại, thay cho kiểu trượt lên từ đáy mặc định của Miuix.
 */
@Composable
fun BlurDialog(
    show: Boolean,
    onDismissRequest: (() -> Unit)?,
    title: String? = null,
    summary: String? = null,
    centered: Boolean = false,
    fullScreen: Boolean = false,
    // Cho phép dùng GlassDialog khi bật Giao diện kính. Mặc định chỉ dialog nhỏ; CompactDialog đặt fullScreen = true (để vẽ ở Scaffold gốc) nhưng vẫn là dialog nhỏ nên bật tay.
    glassStyle: Boolean = !fullScreen,
    content: @Composable () -> Unit,
) {
    val requests = LocalBlurRequests.current
    // Giao diện kính: dialog nhỏ là GlassDialog của Miuix (nền kính tự làm mờ trang phía sau, có lớp tối riêng), nên không đăng ký nền mờ của [DialogBlurScrim].
    // Dialog full màn hình giữ bản cũ (nội dung tự vẽ, xem SubPageScreen / SheetGlassAlpha).
    val glass = LocalGlassEnabled.current && glassStyle
    if (requests != null && !glass) {
        DisposableEffect(show) {
            if (show) requests.dialogs++
            onDispose { if (show) requests.dialogs-- }
        }
    }
    if (glass) {
        GlassDialog(
            show = show,
            title = title,
            summary = summary,
            // onDismissRequest = null nghĩa là không cho đóng bằng chạm ngoài / Back (log đang chạy): GlassDialog bắt buộc có callback nên truyền hàm rỗng.
            onDismissRequest = onDismissRequest ?: {},
            backdrop = LocalGlassBackdrop.current,
            largeScreen = if (centered) true else null,
            outsideMargin = DpSize(16.dp, 16.dp),
            content = content,
        )
        return
    }
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        enableWindowDim = requests == null,
        onDismissRequest = onDismissRequest,
        largeScreen = if (centered) true else null,
        renderInRootScaffold = fullScreen || requests != null,
        outsideMargin = DpSize(16.dp, 16.dp),
        content = content,
    )
}

/** Nền mờ phía sau dialog, hiện / ẩn dần cùng dialog. Đặt trên trang, dưới lớp popup của Scaffold gốc. */
@Composable
fun DialogBlurScrim(requests: BlurRequests, backdrop: LayerBackdrop) {
    val progress by animateFloatAsState(if (requests.dialogs > 0) 1f else 0f, tween(260), label = "dialogScrim")
    if (progress > 0f) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress }.scrimBlur(backdrop))
    }
}
