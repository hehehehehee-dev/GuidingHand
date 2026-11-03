package vn.guidinghand

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

class SpeechInputFailure(val category: String) : Exception("speech_input_stopped")

/** Only Muse Voice Transcribe through our backend. No provider key or alternate recognizer. */
class SpeechInputClient(private val prefs: FamilyPreferences) {
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect(); connection = null }
    suspend fun transcribe(wave: ByteArray, screen: ScreenState, keywords: List<String>, stillSafe: suspend () -> Boolean): String = withContext(Dispatchers.IO) {
        check(prefs.voiceConsent && prefs.screenConsent && screen.complete && !screen.isLocked && !SafetyPolicy.sensitive(screen) && stillSafe())
        val base = BackendEndpoint.base(prefs) ?: error("Chưa kết nối máy chủ nhận giọng nói. Nhờ người thân thiết lập một lần.")
        val token = prefs.backendSession
        check(token.isNotBlank() && token.length <= 4096 && token.none { it == '\r' || it == '\n' })
        val id = UUID.randomUUID().toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(screen.fingerprint.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val body = JSONObject().put("request_id", id).put("fingerprint", hash).put("consent", true).put("unlocked", true)
            .put("language", prefs.language)
            .put("speech_keywords", JSONArray(keywords.asSequence().map(String::trim).filter(String::isNotBlank)
                .distinctBy(String::lowercase).take(32).toList()))
            .put("goal", JSONObject().put("text", "Nhận lời nói của người dùng").put("answers", JSONArray()))
            .put("screen", JSONObject().put("package_name", screen.packageName).put("complete", true)
                .put("nodes", JSONArray(screen.nodes.mapIndexed { index, node -> JSONObject().put("id", "n$index")
                    .put("text", node.text.take(300)).put("aliases", JSONArray(node.aliases.take(16).map { it.take(200) }))
                    .put("password", node.password).put("selected", node.selected).put("clickable", node.clickable)
                    .put("region", JSONObject.NULL) })))
            .put("audio_base64", Base64.encodeToString(wave, Base64.NO_WRAP)).toString().toByteArray()
        check(body.size <= 2_000_000 && stillSafe())
        val http = URI("$base/core/transcribe").toURL().openConnection() as HttpURLConnection
        connection = http
        try {
            http.requestMethod = "POST"; http.doOutput = true; http.instanceFollowRedirects = false
            http.connectTimeout = 6000; http.readTimeout = 40000
            http.setRequestProperty("Content-Type", "application/json"); http.setRequestProperty("Authorization", "Bearer $token")
            http.setFixedLengthStreamingMode(body.size); http.outputStream.use { it.write(body) }
            val responseCode = http.responseCode
            if (responseCode != 200) {
                throw SpeechInputFailure(when (responseCode) {
                    401, 403 -> "auth"
                    400, 413, 422 -> "bad_request"
                    else -> "server"
                })
            }
            val bytes = http.inputStream.use { source -> ByteArrayOutputStream().use { out ->
                val buffer = ByteArray(1024)
                while (out.size() <= 16_000) { val n = source.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                out.toByteArray()
            } }
            check(bytes.size <= 16_000 && prefs.voiceConsent && prefs.screenConsent && stillSafe())
            val result = JSONObject(String(bytes, Charsets.UTF_8))
            if (result.optString("status") == "blocked") {
                val category = result.optString("reason_category")
                throw SpeechInputFailure(if (category in setOf(
                    "sensitive", "cannot_read", "unavailable", "provider_auth", "rate_limited", "provider_failed", "provider_network"
                )) category else "unavailable")
            }
            check(result.optString("request_id") == id && result.optString("status") == "transcribed" &&
                result.optString("model") == "muse-voice-transcribe-1.0")
            result.optString("transcript").trim().also {
                if (it.isBlank()) throw SpeechInputFailure("no_transcript")
                check(it.length <= 500)
            }
        } finally { http.disconnect(); if (connection === http) connection = null }
    }
}
