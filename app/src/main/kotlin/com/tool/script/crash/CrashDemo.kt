package com.tool.script.crash

import com.tool.script.R
import com.tool.script.util.AppText

/**
 * Lệnh demo để thử màn hình lỗi (CrashActivity) mà không cần sửa code.
 * Dùng làm giá trị `run` của một button trong TOML (nên kèm `confirm`, vì app sẽ thoát thật):
 *
 * - `@crash`: ném lỗi ngay trên luồng chính, rơi vào bộ bắt crash trong `App`.
 * - `@crash-thread`: ném lỗi trong một luồng nền, cũng rơi vào cùng bộ bắt crash.
 *
 * Cả hai đều hiện Trung tâm lỗi với tiêu đề "Ứng dụng bị lỗi" (chế độ fatal) rồi tiến trình app bị kill.
 * Giá trị khác thì [trigger] trả về false và lệnh chạy bình thường như một lệnh shell.
 */
object CrashDemo {
    const val MAIN_THREAD = "@crash"
    const val BACKGROUND_THREAD = "@crash-thread"

    /** @return true nếu [command] là lệnh demo crash đã được xử lý (luồng chính thì không bao giờ quay lại vì ném lỗi). */
    fun trigger(command: String): Boolean = when (command.trim()) {
        MAIN_THREAD -> throw IllegalStateException(AppText.get(R.string.crash_demo_main, MAIN_THREAD))
        BACKGROUND_THREAD -> {
            Thread(
                { throw IllegalStateException(AppText.get(R.string.crash_demo_background, BACKGROUND_THREAD)) },
                "crash-demo",
            ).start()
            true
        }
        else -> false
    }
}
