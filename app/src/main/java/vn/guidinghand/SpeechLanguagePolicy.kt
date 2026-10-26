package vn.guidinghand

import java.util.Locale

/** Language metadata does not imply a model is installed or a microphone is listening. */
object SpeechLanguagePolicy {
    private fun matches(requested: String, reported: String): Boolean {
        val a = requested.trim().replace('_', '-').lowercase(Locale.ROOT)
        val b = reported.trim().replace('_', '-').lowercase(Locale.ROOT)
        return a == b || (a.substringBefore('-') == b.substringBefore('-') && ('-' !in a || '-' !in b))
    }
    fun classify(tag: String, installed: List<String>, pending: List<String>, downloadable: List<String>,
                 online: List<String>): OfflineLanguageState = when {
        installed.any { matches(tag, it) } -> OfflineLanguageState.READY
        pending.any { matches(tag, it) } -> OfflineLanguageState.PENDING
        downloadable.any { matches(tag, it) } -> OfflineLanguageState.DOWNLOADABLE
        installed.isEmpty() && pending.isEmpty() && downloadable.isEmpty() && online.isEmpty() -> OfflineLanguageState.UNKNOWN
        else -> OfflineLanguageState.UNSUPPORTED
    }
}
