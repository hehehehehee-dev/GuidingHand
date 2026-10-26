package vn.guidinghand

import androidx.compose.runtime.staticCompositionLocalOf

/** English is the product default. Vietnamese is an explicit, persistent choice. */
data class UiText(val language: String = "en") {
    val vi get() = language == "vi"
    fun pick(en: String, vi: String) = if (this.vi) vi else en
    fun languageName(code: String) = if (code == "vi") pick("Vietnamese", "Tiếng Việt") else pick("English", "Tiếng Anh")
    val helpMenu get() = pick("‹ Help menu", "‹ Trợ giúp")
    val start get() = pick("Let's do it together", "Cùng làm từng bước")
    val subtitle get() = pick("Patient help, inspired by the people who care for you.", "Kiên nhẫn chỉ dẫn, như có người thân bên cạnh.")
    val ask get() = pick("What would you like to do?", "Muốn làm việc gì?")
    val who get() = pick("Who would you like to message?", "Muốn nhắn tin cho ai?")
    val talk get() = pick("Tap to speak", "Bấm để nói")
    val listening get() = pick("I'm listening…", "Đang nghe…")
    val replay get() = pick("Repeat", "Nghe lại")
    val stop get() = pick("Stop", "Dừng lại")
    val hide get() = pick("Hide bubble", "Ẩn bong bóng")
    val collapse get() = pick("Minimize", "Thu gọn")
    val call get() = pick("Call family", "Gọi người thân")
    val thinking get() = pick("One moment. Let's look together…", "Chờ một chút nhé, mình cùng xem…")
    val sensitive get() = pick("Ask family to help with this step.", "Nhờ người thân giúp bước này.")
    val unknown get() = pick("I can't confirm this screen. Let's ask family.", "Chưa xác nhận được. Nhờ người thân giúp.")
    val done get() = pick("✓ You did it. Nicely done.", "✓ Làm được rồi. Tốt lắm!")
    val returnTask get() = pick("Return to the app you're using.", "Trở về việc đang làm.")
    val noVoice get() = pick("Install an offline English voice in Speech settings.", "Cài giọng đọc tiếng Việt trong cài đặt giọng nói.")
    val noSpeech get() = pick("Offline voice isn't available on this device yet. Choose a task below.", "Máy chưa có nhận giọng nói ngoại tuyến. Chọn việc ở dưới.")
    val family get() = pick("Family & preferences", "Gia đình và tùy chọn")
    val ready get() = pick("Ready when you are", "Sẵn sàng khi cần")
    val enable get() = pick("Enable floating guide", "Bật hướng dẫn nổi")
    val show get() = pick("Show my guide", "Hiện hướng dẫn")
    val settings get() = pick("Preferences", "Tùy chọn")
    val back get() = pick("Back", "Quay lại")
    val save get() = pick("Save preferences", "Lưu tùy chọn")
    fun task(id: String) = when (id) {
        "open" -> pick("Find Zalo", "Tìm Zalo")
        "message" -> pick("Send a message", "Nhắn tin")
        "video" -> pick("Make a video call", "Gọi hình")
        "photo" -> pick("Send a photo", "Gửi ảnh")
        "font" -> pick("Make text larger", "Phóng to chữ")
        "wifi" -> pick("Connect to Wi-Fi", "Kết nối mạng")
        "qr" -> pick("Scan a QR code", "Quét mã")
        else -> pick("Get help", "Nhận trợ giúp")
    }
    fun task(task: GuideTask): String = when (task.id) {
        "open" -> pick("Find ${task.appLabel}", "Tìm ${task.appLabel}")
        "message" -> pick("Message in ${task.appLabel}", "Nhắn tin trong ${task.appLabel}")
        else -> this.task(task.id)
    }
    fun taskHint(id: String) = when (id) {
        "open" -> pick("I'll point out the app", "Chỉ nơi mở ứng dụng")
        "message" -> pick("One person. One step at a time.", "Chọn người, rồi làm từng bước.")
        "video" -> pick("See a familiar face", "Gặp người thân qua màn hình")
        "photo" -> pick("Share a little moment", "Gửi một khoảnh khắc")
        else -> pick("Patient, step-by-step help", "Chậm rãi, từng bước một")
    }
}
val LocalUi = staticCompositionLocalOf { UiText() }

object Conversation {
    private val commonAppNames = listOf("Zalo", "Instagram", "Facebook", "Messenger", "YouTube", "gia lo", "za lo", "in sta", "in sta gram")
    fun isAppOpenRequest(utterance: String, appNames: List<String>): Boolean {
        val query = folded(utterance).replace(Regex("\\s+"), " ").trim(' ', '.', '?')
        if (appNames.any { folded(it) == query }) return true
        return Regex("^(?:(?:please|can you|could you|i want to|toi muon|minh muon|giup toi|hay) )*(?:open|find|launch|start|show me|where is|mo|tim|vao)\\b")
            .containsMatchIn(query)
    }
    fun recipient(utterance: String, appNames: List<String> = commonAppNames, matchedAppContext: Boolean = false): String? {
        val patterns = listOf("(?i)(?:message|text|call|send (?:a )?(?:message|photo) to)\\s+(.+)",
            "(?i)(?:nhắn tin cho|nhắn cho|gọi cho|gửi ảnh cho)\\s+(.+)")
        return patterns.firstNotNullOfOrNull { Regex(it).find(utterance)?.groupValues?.get(1)?.trim()?.let { name ->
            val context = Regex("(?i)(?:^|\\s)(?:on|in|using|with|trên|tren|bằng|bang|qua)\\s+(.+)$").find(name)
            if (context != null && (matchedAppContext || appNames.any { folded(it) == folded(context.groupValues[1]) }))
                name.substring(0, context.range.first).trim() else name
        }?.takeIf { name -> name.isNotBlank() } }
    }
}
