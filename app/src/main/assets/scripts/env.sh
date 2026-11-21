#!/system/bin/sh
# Biến dùng chung cho mọi lệnh shell của app: được nạp (source) trước mọi lệnh, kể cả init.sh.
# Mỗi lần mở app, key dạng @KEY@ được thay bằng đường dẫn thật.
# Key có sẵn:
#   @PACKAGE@      tên gói của app
#   @APP_DIR@      thư mục dữ liệu của app
#   @FILES_DIR@    thư mục files của app
#   @CACHE_DIR@    thư mục cache của app
#   @SCRIPTS_DIR@  thư mục chứa script chép từ assets (cùng nơi có file này)
#   @STATE_DIR@    thư mục lưu trạng thái các tính năng (app tạo sẵn)
#   @NOBACKUP_DIR@ thư mục riêng của app, không bị Auto Backup sao lưu (dữ liệu gắn với máy này, dùng khi cần)
#   @BIN_DIR@      thư mục lệnh dựng sẵn (busybox): symlink tới thư viện native của app ($LIB_DIR)
#   @LIB_DIR@      thư mục thư viện native của app
#   @EXT_ROOT@     gốc bộ nhớ trong (thường /storage/emulated/0)
#   @EXT_DIR@      thư mục làm việc ngoài của app (@EXT_ROOT@/ToolScript)
# Lưu ý: giữ nháy kép quanh @KEY@ để đường dẫn có khoảng trắng vẫn đúng.

export PACKAGE="@PACKAGE@"
export APP_DIR="@APP_DIR@"
export FILES_DIR="@FILES_DIR@"
export CACHE_DIR="@CACHE_DIR@"
export SCRIPTS_DIR="@SCRIPTS_DIR@"
export STATE_DIR="@STATE_DIR@"
export NOBACKUP_DIR="@NOBACKUP_DIR@"
export BIN_DIR="@BIN_DIR@"
export LIB_DIR="@LIB_DIR@"
export EXT_ROOT="@EXT_ROOT@"
export EXT_DIR="@EXT_DIR@"

# $BIN_DIR đứng đầu: `busybox` gọi thẳng bản của app (applet của busybox chưa tạo symlink: dùng `busybox ls`...).
export PATH="$BIN_DIR:$SCRIPTS_DIR:$PATH"

# Chạy file ELF tải về (không cần root): run_elf <file> [đối số...]
# App target SDK 28 nên exec thẳng file trong $FILES_DIR được. Nếu máy vẫn chặn (mã 126), chạy lại qua linker hệ thống
# (chỉ chạy được ELF Android: PIE, liên kết động). Chương trình multi-call (busybox...) nên để trong jniLibs.
run_elf() {
  _f="$1"; shift
  [ -f "$_f" ] || { echo "run_elf: không thấy file: $_f" >&2; return 127; }
  chmod 755 "$_f" 2>/dev/null
  "$_f" "$@"
  _rc=$?
  [ "$_rc" -eq 126 ] || return "$_rc"
  # Byte thứ 5 của ELF: 02 = 64-bit, 01 = 32-bit
  if [ "$(od -An -tx1 -j4 -N1 "$_f" | tr -d ' ')" = "02" ]; then _ld=/system/bin/linker64; else _ld=/system/bin/linker; fi
  "$_ld" "$_f" "$@"
}

# Lưu / đọc trạng thái của một tính năng, tên file = tên (id) tính năng trong $STATE_DIR.
#   state_set <tên> <giá trị>      ví dụ: state_set home_switch 1
#   state_get <tên> [mặc định]     in giá trị đã lưu; chưa có file thì in giá trị mặc định (mặc định 0)
state_set() { echo "$2" > "$STATE_DIR/$1"; }
state_get() { cat "$STATE_DIR/$1" 2>/dev/null || echo "${2:-0}"; }
