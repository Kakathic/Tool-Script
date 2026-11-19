package com.tool.script.executor

// Backend thực thi, thứ tự ưu tiên: su -> ROM -> adb -> thường.
// Giá trị biến ROOT_MODE: root / system / adb / any.
// Lưu ý: file chép từ assets ra thư mục app không exec trực tiếp được (Android 10+),
//        hãy chạy qua trình thông dịch: `sh /đường/dẫn/init.sh`.
// TODO: ShellExecutor (interface), SuExecutor (libsu), RomExecutor, AdbExecutor, LocalExecutor,
//       ExecutorManager (dò quyền, timeout 5 giây rồi hạ cấp), ExecResult.
