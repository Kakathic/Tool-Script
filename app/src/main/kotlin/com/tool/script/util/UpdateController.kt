package com.tool.script.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tool.script.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Giữ trạng thái cập nhật cho màn hình Cài đặt: kiểm tra, tải và cài. Trạng thái nằm ở đây (không nằm trong nút) để cả danh sách
 * Cài đặt đọc được: có bản mới thì ẩn các nhóm tùy chọn, chỉ hiện Nhật ký.
 */
@Stable
class UpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    val version: AppVersion,
) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private val busy: Boolean get() = state is UpdateState.Checking || state is UpdateState.Downloading

    /** Tự kiểm tra một lần khi mở Cài đặt (còn Idle); các lần sau do người dùng bấm nút. */
    fun checkOnce() {
        if (state is UpdateState.Idle) check()
    }

    fun check() {
        if (busy) return
        state = UpdateState.Checking
        scope.launch { state = UpdateChecker.check(version) }
    }

    /** Tải APK rồi mở trình cài đặt. Release không có APK thì mở trang release. */
    fun download() {
        val available = state.pending ?: return
        if (busy) return
        val apk = available.apkUrl
        if (apk == null) {
            openUrl(context, available.url)
            return
        }
        state = UpdateState.Downloading(available, 0f)
        scope.launch {
            val file = UpdateInstaller.download(context, apk) { state = UpdateState.Downloading(available, it) }
            if (file == null) {
                state = UpdateState.DownloadFailed(available)
            } else {
                state = UpdateState.Downloaded(available, file)
                install()
            }
        }
    }

    /** Mở trình cài đặt cho APK đã tải. Tệp bị xóa mất (xóa cache) thì quay về trạng thái có bản mới để tải lại. */
    fun install() {
        val done = state as? UpdateState.Downloaded ?: return
        if (!done.file.exists()) {
            state = done.update
            return
        }
        when (UpdateInstaller.install(context, done.file)) {
            InstallResult.NeedPermission -> Toast.makeText(context, R.string.update_install_permission, Toast.LENGTH_LONG).show()
            InstallResult.Failed -> Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_SHORT).show()
            InstallResult.Started -> Unit
        }
    }
}

@Composable
fun rememberUpdateController(): UpdateController {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { UpdateController(appContext, scope, AppVersion.of(appContext)) }
}
