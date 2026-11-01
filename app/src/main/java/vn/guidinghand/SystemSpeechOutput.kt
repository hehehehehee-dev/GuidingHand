package vn.guidinghand

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale

/** Installed offline system voice in the selected language, always 0.85 speed. No cloud TTS. */
interface SpeechOutput {
    suspend fun speak(text: String, stillSafe: suspend () -> Boolean)
    fun stop()
    fun close()
}
class SystemSpeechOutput(private val context: Context) : SpeechOutput {
    private val initialized = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }
    private var epoch = 0L
    override suspend fun speak(text: String, stillSafe: suspend () -> Boolean) {
        val request = epoch
        check(withTimeout(8000) { initialized.await() }) { "Android TTS unavailable" }
        withContext(Dispatchers.Main.immediate) {
            check(request == epoch && DeviceVisibility.allowed(context) && stillSafe())
            val language = FamilyPreferences(context).language
            val voice = tts.voices?.filter { it.locale.language == language && !it.isNetworkConnectionRequired &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                ?.sortedBy { it.name }?.firstOrNull() ?: error("Install an offline system voice for the selected language")
            check(tts.setLanguage(Locale.forLanguageTag(if (language == "vi") "vi-VN" else "en-US")) >= TextToSpeech.LANG_AVAILABLE)
            check(tts.setVoice(voice) == TextToSpeech.SUCCESS)
            tts.setSpeechRate(.85f)
            check(request == epoch && stillSafe())
            check(tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuidingHand-step") == TextToSpeech.SUCCESS)
        }
    }
    override fun stop() { epoch++; tts.stop() }
    override fun close() { stop(); tts.shutdown() }
}
