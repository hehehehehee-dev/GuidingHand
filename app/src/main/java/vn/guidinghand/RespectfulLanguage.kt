package vn.guidinghand

enum class UserAddress(val key: String, val label: String) {
    BAC("bac", "Bác"), ONG("ong", "Ông"), BA("ba", "Bà"), CO("co", "Cô"), CHU("chu", "Chú"),
    ANH("anh", "Anh"), CHI("chi", "Chị"), NONE("none", "Không xưng hô");
    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } ?: BAC }
}
enum class AssistantAddress(val key: String, val label: String) {
    TOI("toi", "Tôi"), CHAU("chau", "Cháu"), CON("con", "Con"), ASSISTANT("assistant", "Trợ lý");
    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } ?: TOI }
}
enum class GuidanceTone(val key: String, val label: String) {
    GENTLE("gentle", "Nhẹ nhàng"), CONCISE("concise", "Ngắn gọn");
    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } ?: GENTLE }
}

/** Wording only: never changes a target, recipient, region, task or verification rule. */
data class RespectfulLanguage(
    val user: UserAddress = UserAddress.BAC,
    val assistant: AssistantAddress = AssistantAddress.TOI,
    val tone: GuidanceTone = GuidanceTone.GENTLE
) {
    private val address get() = if (user == UserAddress.NONE) "" else user.label
    private val self get() = assistant.label
    val goalQuestion get() = if (address.isEmpty()) "Cần hỗ trợ việc gì ạ?" else "$address muốn được hỗ trợ việc gì ạ?"
    val waitingExample get() = if (address.isEmpty()) "$self sẽ chờ, không cần vội ạ." else "$address cứ thong thả, ${self.lowercase()} sẽ chờ."
    fun message(value: String, protectedLabels: List<String> = emptyList()): String {
        if (value.isBlank()) return value
        // Preserve names, filenames and literal app labels, even if they resemble a pronoun.
        val literals = protectedLabels.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        var text = value.trim()
        val saved = mutableListOf<Pair<String, String>>()
        literals.forEachIndexed { index, label ->
            if (text.contains(label)) {
                val token = "\uE000$index\uE001"
                text = text.replace(label, token); saved += token to label
            }
        }
        // Quoted app labels and dictated content are literal, not assistant narration.
        text = Regex("[\"“][^\"”]+[\"”]").replace(text) { match ->
            val token = "\uE002${saved.size}\uE003"
            saved += token to match.value
            token
        }
        text = when (text) {
            "Ông bà muốn làm việc gì?" -> goalQuestion
            "Mình sẽ hướng dẫn tiếp từ màn hình này." -> "$self sẽ tiếp tục hướng dẫn từ màn hình hiện tại."
            "Đã xác minh ảnh được gửi. Xong rồi." -> "Đã xác minh ảnh được gửi đến đúng người nhận ạ."
            "Đã tạm dừng để bảo vệ thông tin. Ông bà có thể chọn Gọi người thân hoặc Dừng." ->
                "$self đã tạm dừng để bảo vệ thông tin riêng tư. ${if (address.isEmpty()) "Có thể" else "$address có thể"} chọn Gọi người thân hoặc Dừng ạ."
            else -> text
        }
        // Normalize an already-polite model command before applying the chosen address/tone.
        val actionWords = "bấm|gõ|tự gõ|kiểm tra|chọn|nhập|tự bấm"
        text = Regex("^(?:Ông bà|Bác|Ông|Bà|Cô|Chú|Anh|Chị) (?:(?:vui lòng|hãy) )?(?=(?:$actionWords)\\s)", RegexOption.IGNORE_CASE).replaceFirst(text, "")
        text = Regex("^(?:Xin vui lòng|Vui lòng|Xin|Hãy) (?=(?:$actionWords)\\s)", RegexOption.IGNORE_CASE).replaceFirst(text, "")
        val leadingAddress = Regex("^(Ông bà|Bác|Ông|Bà|Cô|Chú|Anh|Chị) (?=(?:vui lòng|muốn|có thể|cứ|hãy|bấm|kiểm tra|chọn|nhập|tự)\\b)")
        text = leadingAddress.replaceFirst(text, if (address.isEmpty()) "" else "$address ")
        text = Regex("(?<![\\p{L}\\p{N}_])(Tôi|Mình|Cháu|Con|Trợ lý)(?=\\s+(?:sẽ|đã|đang|chưa|không|có thể|cần|hiểu|nghe|thấy|xin|muốn|dừng)(?![\\p{L}\\p{N}_]))", RegexOption.IGNORE_CASE)
            .replace(text) { match -> if (match.range.first == 0 || text.substring(0, match.range.first).trimEnd().lastOrNull() in listOf('.', '!', '?')) self else self.lowercase() }
        text = Regex("(?i)(giúp) (tôi|mình)(?=[\\s.!?,]|$)")
            .replace(text) { "${it.groupValues[1]} ${self.lowercase()}" }
        text = Regex("(?<![\\p{L}\\p{N}_])(Ông bà|Bác|Ông|Bà|Cô|Chú|Anh|Chị)(?=\\s+(?:giữ|đóng|mở|nói|thử|chờ|kéo|vuốt|vẫn|đừng|muốn|cần|có thể|cứ|hãy|vui lòng)(?![\\p{L}\\p{N}_]))", RegexOption.IGNORE_CASE)
            .replace(text) { match ->
                if (address.isEmpty()) "" else if (match.range.first == 0 || text.substring(0, match.range.first).trimEnd().lastOrNull() in listOf('.', '!', '?')) address else address.lowercase()
            }
        text = text.trim().replace(Regex(" {2,}"), " ")
        val imperative = Regex("^(Bấm|Gõ|Tự gõ|Kiểm tra|Chọn|Nhập|Tự bấm)\\s+", RegexOption.IGNORE_CASE).find(text)
        if (imperative != null) {
            val verb = imperative.groupValues[1].lowercase().replace("tự gõ", "nhập").replace("gõ", "nhập")
            val prefix = if (address.isEmpty()) "Vui lòng " else if (tone == GuidanceTone.GENTLE) "$address vui lòng " else "$address, "
            text = prefix + verb + " " + text.substring(imperative.range.last + 1).trimEnd('.', '!', '?', ' ')
            if (!text.endsWith(" ạ")) text += " ạ"
            text += "."
        } else if (address.isNotEmpty() && text.startsWith("$address ") && text.endsWith("?") && !text.endsWith(" ạ?")) {
            text = text.dropLast(1) + " ạ?"
        }
        saved.asReversed().forEach { (token, label) -> text = text.replace(token, label) }
        return text
    }
    fun step(value: GuidanceStep, protectedLabels: List<String>) = value.copy(instructionVi = message(value.instructionVi, protectedLabels))
}
