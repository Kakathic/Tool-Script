#!/system/bin/sh

export PACKAGE="@PACKAGE@"
export APP_DIR="@APP_DIR@"
export FILES_DIR="@FILES_DIR@"
export CACHE_DIR="@CACHE_DIR@"
export SCRIPTS_DIR="@SCRIPTS_DIR@"
export STATE_DIR="@STATE_DIR@"
export HOME="@HOME_DIR@"
export BIN_DIR="@BIN_DIR@"
export LIB_DIR="@LIB_DIR@"
export ABI="@ABI@"
export ARCH="@ARCH@"
export EXT_ROOT="@EXT_ROOT@"
export EXT_DIR="@EXT_DIR@"

# $BIN_DIR = $HOME/bin: glog, slog, killtree chép từ assets/bin lúc mở app, và busybox do init.sh cài từ assets/lib/<abi>/libbusybox.so.
# Tự đặt lệnh khác (vd ELF tải về) vào đó cũng gọi được. Applet của busybox chưa tạo symlink: dùng `busybox ls`...
export PATH="$BIN_DIR:$PATH"

# $HOME = no_backup/home: nơi app chép assets bin, lib ra; env.sh này nằm cạnh init.sh trong $SCRIPTS_DIR (no_backup/scripts, cạnh home); lệnh chạy từ $HOME (dùng đường dẫn tuyệt đối trong TOML cho chắc).
cd "$HOME"
