package com.tool.script.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tool.script.util.Util
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Icon ở thanh trên cùng (Giới thiệu, Khởi động lại) và menu khởi động lại.

/** Icon bấm được ở thanh trên; [icon] tính từ assets/configs/ giống icon tab. */
@Composable
fun TopBarIcon(icon: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Util.iconModel(icon),
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
        )
    }
}

/** Menu chọn kiểu khởi động lại. Việc xác nhận và thực thi do nơi gọi xử lý. */
@Composable
fun RestartMenuDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onRestartApp: () -> Unit,
    onRebootDevice: () -> Unit,
) {
    OverlayDialog(show = show, title = "Khởi động lại", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = "Khởi động lại ứng dụng", modifier = Modifier.fillMaxWidth(), onClick = onRestartApp)
            TextButton(text = "Khởi động lại máy (cần root)", modifier = Modifier.fillMaxWidth(), onClick = onRebootDevice)
            TextButton(text = "Đóng", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}
