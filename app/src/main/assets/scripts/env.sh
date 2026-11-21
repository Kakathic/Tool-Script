#!/system/bin/sh

export PACKAGE="@PACKAGE@"
export APP_DIR="@APP_DIR@"
export FILES_DIR="@FILES_DIR@"
export CACHE_DIR="@CACHE_DIR@"
export SCRIPTS_DIR="@SCRIPTS_DIR@"
export STATE_DIR="@STATE_DIR@"
export HOME="@NOBACKUP_DIR@"
export BIN_DIR="@BIN_DIR@"
export LIB_DIR="@LIB_DIR@"
export ABIS="@ABIS@"
export EXT_ROOT="@EXT_ROOT@"
export EXT_DIR="@EXT_DIR@"

# $BIN_DIR = $HOME/bin: glog, slog, killtree chép từ assets/bin lúc mở app, và busybox do init.sh cài từ assets/lib/<abi>/libbusybox.so.
# Tự đặt lệnh khác (vd ELF tải về) vào đó cũng gọi được. Applet của busybox chưa tạo symlink: dùng `busybox ls`...
export PATH="$BIN_DIR:$PATH"

# $HOME là thư mục no_backup, nơi app chép assets ra; lệnh chạy từ đây (dùng đường dẫn tuyệt đối trong TOML cho chắc).
cd "$HOME"
