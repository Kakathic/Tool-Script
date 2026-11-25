package com.tool.script.ui.main

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog

// Làm mờ nền phía sau mọi lớp phủ làm tối nền. Dialog (OverlayDialog của Miuix, vẽ ở lớp popup của Scaffold gốc) bật nền mờ riêng
// thay cho lớp tối của Miuix; menu thả xuống là cửa sổ riêng nên làm mờ chính trang phía sau. Cả hai đăng ký qua [BlurRequests],
// chỉ có khi MainScreen bật blur ([LocalBlurRequests] = null thì mọi thứ chạy như cũ).

/** Số lớp phủ đang yêu cầu làm mờ nền. */
@Stable
class BlurRequests {
    var dialogs by mutableIntStateOf(0)
    var menus by mutableIntStateOf(0)
}

val LocalBlurRequests = staticCompositionLocalOf<BlurRequests?> { null }

private const val MenuBlurRadiusDp = 12
private const val MenuBlurOpenMillis = 110
private const val MenuBlurCloseMillis = 180

/**
 * Thay cho OverlayDialog: có blur thì tắt lớp tối của Miuix và đăng ký để [DialogBlurScrim] vẽ nền mờ,
 * không có blur thì y hệt OverlayDialog.
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
    content: @Composable () -> Unit,
) {
    val requests = LocalBlurRequests.current
    if (requests != null) {
        DisposableEffect(show) {
            if (show) requests.dialogs++
            onDispose { if (show) requests.dialogs-- }
        }
    }
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        enableWindowDim = requests == null,
        onDismissRequest = onDismissRequest,
        largeScreen = if (centered) true else null,
        renderInRootScaffold = fullScreen,
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

/** Bán kính làm mờ trang khi có menu đang mở: mở nhanh (đà đầu mạnh để thấy mờ ngay), đóng chậm hơn một chút cho êm. */
@Composable
fun menuBlurRadius(requests: BlurRequests): Dp {
    val open = requests.menus > 0
    val radius by animateDpAsState(
        targetValue = if (open) MenuBlurRadiusDp.dp else 0.dp,
        animationSpec = tween(durationMillis = if (open) MenuBlurOpenMillis else MenuBlurCloseMillis, easing = LinearOutSlowInEasing),
        label = "menuBlur",
    )
    return radius
}

/** Gọi trong menu thả xuống: trong lúc menu còn mở, trang phía sau được làm mờ. */
@Composable
internal fun MenuBlurRequest() {
    val requests = LocalBlurRequests.current ?: return
    DisposableEffect(Unit) {
        requests.menus++
        onDispose { requests.menus-- }
    }
}

/** Làm mờ chính nội dung mang modifier này; bán kính 0 thì không thêm hiệu ứng nào. */
fun Modifier.blurIfPositive(radius: Dp): Modifier = if (radius > 0.dp) blur(radius) else this
