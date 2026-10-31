package vn.guidinghand

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

enum class BackendHealthStatus { READY, NOT_CONFIGURED, AUTH_REQUIRED, UNREACHABLE, INCOMPATIBLE }

/** A no-model, no-credit readiness probe. It never logs or returns the session token. */
object BackendHealth {
    suspend fun check(preferences: FamilyPreferences): BackendHealthStatus = withContext(Dispatchers.IO) {
        val base = BackendEndpoint.base(preferences) ?: return@withContext BackendHealthStatus.NOT_CONFIGURED
        val token = preferences.backendSession
        if (token.length < 32) return@withContext BackendHealthStatus.NOT_CONFIGURED
        val connection = try { URI("$base/core/capabilities").toURL().openConnection() as HttpURLConnection }
        catch (_: Exception) { return@withContext BackendHealthStatus.NOT_CONFIGURED }
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 2_500
            connection.readTimeout = 3_500
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val response = connection.responseCode
            if (response == 401) return@withContext BackendHealthStatus.AUTH_REQUIRED
            if (response == 404) return@withContext BackendHealthStatus.INCOMPATIBLE
            if (response != 200) return@withContext BackendHealthStatus.UNREACHABLE
            val bytes = connection.inputStream.use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(4096)
                while (output.size() <= 64_000) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } }
            if (bytes.size > 64_000) return@withContext BackendHealthStatus.INCOMPATIBLE
            if (compatible(JSONObject(String(bytes, Charsets.UTF_8)))) BackendHealthStatus.READY
            else BackendHealthStatus.INCOMPATIBLE
        } catch (_: Exception) {
            BackendHealthStatus.UNREACHABLE
        } finally {
            connection.disconnect()
        }
    }

    internal fun compatible(value: JSONObject): Boolean {
        val tasks = value.optJSONArray("task_kinds") ?: return false
        val voice = value.optJSONObject("voice_input") ?: return false
        return compatibleContract(value.optString("snapshot_protocol"), (0 until tasks.length()).map { tasks.optString(it) },
            voice.optString("model"), voice.optString("role"), value.optBoolean("automatic_actions", true))
    }

    internal fun compatibleContract(snapshotProtocol: String, tasks: List<String>, voiceModel: String,
                                    voiceRole: String, automaticActions: Boolean) =
        snapshotProtocol == "single-image-v1" && "guide_app" in tasks &&
            voiceModel == "muse-voice-transcribe-1.0" && voiceRole == "speech_to_text" && !automaticActions
}
