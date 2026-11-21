#!/system/bin/sh
# Script khởi động. App chạy bằng: sh init.sh (env.sh đã được nạp sẵn, assets đã chép ra $HOME).
# Báo tiến trình bằng dòng "@progress N" (0-100); các dòng khác hiện lên log splash.

# Cài busybox: chép $HOME/lib/$ABI/libbusybox.so thành $BIN_DIR/busybox ($BIN_DIR = $HOME/bin, chmod 755).
# $ABI là ABI đang chạy của app (một giá trị, env.sh).
install_busybox() {
    src="$HOME/lib/$ABI/libbusybox.so"
    if [ ! -f "$src" ]; then
        echo "busybox: không có bản cho ABI của máy ($ABI)" >&2
        return 1
    fi

    dst="$BIN_DIR/busybox"
    mkdir -p "$BIN_DIR"
    # Đã cài đúng bản này thì giữ nguyên (không có `cmp` thì cứ chép lại).
    if [ ! -x "$dst" ] || ! cmp -s "$src" "$dst" 2>/dev/null; then
        # Ghi ra tệp tạm rồi đổi tên: không dính lỗi "text file busy" nếu busybox đang chạy.
        if ! { cp "$src" "$dst.tmp" && chmod 755 "$dst.tmp" && mv -f "$dst.tmp" "$dst"; }; then
            rm -f "$dst.tmp"
            echo "busybox: không chép được ra $dst" >&2
            return 1
        fi
        echo "busybox: đã cài ($ABI)"
    fi

    if ! "$dst" true; then
        echo "busybox: không chạy được $dst" >&2
        return 1
    fi
    "$dst" 2>&1 | head -n 1
}

status=0

echo "@progress 0"
echo "Đang kiểm tra môi trường..."
echo "@progress 30"
echo "Đang cài busybox..."
install_busybox || status=1
echo "@progress 60"
echo "Đang chuẩn bị thư mục làm việc..."
mkdir -p "$STATE_DIR"
echo "@progress 100"
echo "Đang hoàn tất..."
exit "$status"
