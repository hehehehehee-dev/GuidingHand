package vn.guidinghand

import java.text.Normalizer

/** Selects the assistant language from reviewed user text, never from screen labels or task keywords. */
object SpokenLanguagePolicy {
    private val vietnameseMarks = Regex(
        "[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]",
        RegexOption.IGNORE_CASE
    )
    private val vietnameseWords = setOf(
        "bac", "ban", "bam", "bai", "cho", "chon", "cua", "den", "doc", "duoc", "giup",
        "goi", "gui", "hay", "khong", "man", "mo", "muon", "nhan", "nhan tin", "nut",
        "qua", "tim", "toi", "ung", "vao", "voi", "xem"
    )
    private val englishWords = setOf(
        "button", "call", "choose", "find", "for", "help", "i", "me", "message", "my", "open",
        "please", "read", "screen", "search", "send", "tap", "to", "watch"
    )

    private fun folded(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun detect(text: String, fallback: String): String {
        val safeFallback = if (fallback == "en") "en" else "vi"
        if (text.isBlank()) return safeFallback
        if (vietnameseMarks.containsMatchIn(text)) return "vi"
        val words = folded(text).split(' ').filter(String::isNotBlank)
        val vietnamese = words.count(vietnameseWords::contains)
        val english = words.count(englishWords::contains)
        return when {
            vietnamese >= 2 && vietnamese > english -> "vi"
            english >= 1 && english > vietnamese -> "en"
            else -> safeFallback
        }
    }
}
