package vn.guidinghand

import java.text.Normalizer

/** Final client-side guard. Provider text must never override the saved UI language. */
object OutputLanguagePolicy {
    private val vietnameseMarks = Regex("[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]", RegexOption.IGNORE_CASE)
    private val vietnameseWords = Regex("(^|\\s)(bac|hay|bam|chon|man hinh|nut|dang|tiep theo|vui long|toi|giup|kiem tra|dung lai)(\\s|$)", RegexOption.IGNORE_CASE)
    private val englishWords = Regex("(^|\\s)(tap|choose|open|screen|button|please|wait|next step|go back|check again|stop|help)(\\s|$)", RegexOption.IGNORE_CASE)

    private fun folded(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun accepts(value: String, language: String): Boolean {
        if (value.isBlank()) return true
        val normalized = folded(value)
        val vietnamese = vietnameseMarks.containsMatchIn(value) || vietnameseWords.containsMatchIn(normalized)
        val english = englishWords.containsMatchIn(normalized)
        return when (language) {
            "en" -> !vietnamese
            "vi" -> !english || vietnamese
            else -> false
        }
    }

    fun acceptsAll(values: Iterable<String>, language: String) = values.all { accepts(it, language) }
}
