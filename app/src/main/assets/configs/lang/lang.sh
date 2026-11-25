# Bảng chuỗi @khóa theo ngôn ngữ app: mỗi dòng khóa=giá trị, chọn bằng $LANGAPP (vi, en...). Đổi ngôn ngữ thì có hiệu lực từ lần mở app sau.
case "$LANGAPP" in
  en*)
    echo "demo_lang_title=This app has English and Vietnamese"
    echo "demo_lang_body=Text written as @key in TOML is replaced from lang/lang.sh by the app language. Change the language, then reopen the app."
    echo "demo_lang_button=Show current language"
    echo "demo_lang_echo=Current app language: $LANGAPP"
    ;;
  *)
    echo "demo_lang_title=Giao diện app có English và Vietnamese"
    echo "demo_lang_body=Chữ viết dạng @khóa trong TOML được thay từ lang/lang.sh theo ngôn ngữ app. Đổi ngôn ngữ rồi mở lại app để thấy."
    echo "demo_lang_button=Xem ngôn ngữ hiện tại"
    echo "demo_lang_echo=Ngôn ngữ app hiện tại: $LANGAPP"
    ;;
esac
