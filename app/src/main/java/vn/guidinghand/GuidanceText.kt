package vn.guidinghand

/** Guidance is written in the selected language, independently of the target app's labels. */
object GuidanceText {
    private val controls = listOf(
        Triple(listOf("search", "search apps", "search for apps", "search your phone", "search zalo", "tim kiem", "tim kiem ung dung"), "Search", "Tìm kiếm"),
        Triple(listOf("settings", "cai dat"), "Settings", "Cài đặt"),
        Triple(listOf("home", "trang chu"), "Home", "Trang chủ"),
        Triple(listOf("back", "navigate up", "quay lai", "tro lai"), "Back", "Quay lại"),
        Triple(listOf("messages", "your messages", "direct messages", "inbox", "tin nhan", "nhan tin"), "Messages", "Tin nhắn"),
        Triple(listOf("message", "type a message", "nhap tin nhan"), "Message box", "Ô tin nhắn"),
        Triple(listOf("new message", "tin nhan moi"), "New message", "Tin nhắn mới"),
        Triple(listOf("video call", "goi video", "goi hinh"), "Video call", "Gọi hình"),
        Triple(listOf("audio call", "goi thoai"), "Voice call", "Gọi thoại"),
        Triple(listOf("photos", "photo", "hinh anh", "anh", "gui anh"), "Photos", "Hình ảnh"),
        Triple(listOf("gallery", "thu vien"), "Gallery", "Thư viện"),
        Triple(listOf("recent", "gan day"), "Recent", "Gần đây"),
        Triple(listOf("send", "gui"), "Send", "Gửi"),
        Triple(listOf("personal", "ca nhan"), "Personal", "Cá nhân"),
        Triple(listOf("font size", "co chu"), "Font size", "Cỡ chữ"),
        Triple(listOf("appearance", "giao dien"), "Appearance", "Giao diện"),
        Triple(listOf("large", "lon"), "Large", "Lớn"),
        Triple(listOf("very large", "rat lon"), "Very large", "Rất lớn"),
        Triple(listOf("scan qr", "quet ma qr", "quet qr"), "Scan code", "Quét mã"),
        Triple(listOf("downloads", "download", "tai xuong"), "Downloads", "Tải xuống"),
        Triple(listOf("save", "luu"), "Save", "Lưu"),
        Triple(listOf("cancel", "huy"), "Cancel", "Hủy"),
        Triple(listOf("close", "dong"), "Close", "Đóng"),
        Triple(listOf("continue", "tiep tuc"), "Continue", "Tiếp tục"),
        Triple(listOf("next", "tiep", "tiep theo"), "Next", "Tiếp theo"),
        Triple(listOf("notifications", "thong bao"), "Notifications", "Thông báo"),
        Triple(listOf("profile", "ho so", "trang ca nhan"), "Profile", "Trang cá nhân"),
        Triple(listOf("like", "thich"), "Like", "Thích"),
        Triple(listOf("comment", "binh luan"), "Comment", "Bình luận"),
        Triple(listOf("share", "chia se"), "Share", "Chia sẻ"),
        Triple(listOf("follow", "theo doi"), "Follow", "Theo dõi")
    )

    private fun knownControl(node: ScreenNode): Triple<List<String>, String, String>? {
        val labels = (node.aliases + node.text).map { folded(it).trim('.', '…', ' ') }
        return controls.firstOrNull { entry -> entry.first.any { it in labels } }
    }
    fun localizedControlLabels(node: ScreenNode): List<String> = knownControl(node)?.let { listOf(it.second, it.third) }.orEmpty()
    fun controlLabel(node: ScreenNode, ui: UiText, number: Int): String {
        val known = knownControl(node)
        return if (known != null) ui.pick(known.second, known.third)
        else ui.pick("Control $number", "Nút $number")
    }

    fun instruction(step: StepGoal, node: ScreenNode, appLabel: String, recipient: String,
                    language: String, address: String = ""): String {
        val ui = UiText(language)
        val position = when {
            (node.region?.top ?: .5f) < .33f -> ui.pick("near the top", "ở phía trên")
            (node.region?.top ?: .5f) > .66f -> ui.pick("near the bottom", "ở phía dưới")
            else -> ui.pick("in the middle", "ở giữa")
        }
        val label = when (step.id) {
            "openApp" -> appLabel
            "conversation" -> recipient.ifBlank { ui.pick("your contact", "tên người thân") }
            "video" -> ui.pick("Video call", "Gọi hình")
            "compose" -> ui.pick("the message box", "ô tin nhắn")
            "attach" -> ui.pick("Photos", "Hình ảnh")
            "choose" -> ui.pick("your photo", "ảnh cần gửi")
            "send" -> ui.pick("Send", "Gửi")
            "personal" -> ui.pick("Personal", "Cá nhân")
            "settings" -> ui.pick("Settings", "Cài đặt")
            "font" -> ui.pick("Font size", "Cỡ chữ")
            "large" -> ui.pick("Large", "Lớn")
            "network" -> ui.pick("your network", "tên mạng")
            "scanner" -> ui.pick("Scan code", "Quét mã")
            "chat", "inbox" -> ui.pick("Messages", "Tin nhắn")
            // Untranslated arbitrary labels must not leak into app-authored speech.
            else -> ui.pick("the highlighted control", "nút được đánh dấu")
        }
        val sentence = if (step.completion == "scan") ui.pick("Place the code inside the frame.", "Đặt mã vào giữa khung.")
            else ui.pick("Tap $label $position.", "Bấm $label $position.")
        val familiar = address.trim().lowercase().takeIf { ui.vi && it in listOf("bà", "ông", "cô", "chú", "mẹ") }
        return if (familiar == null) sentence else "${familiar.replaceFirstChar { it.uppercase() }}, ${sentence.replaceFirstChar { it.lowercase() }}"
    }
}
