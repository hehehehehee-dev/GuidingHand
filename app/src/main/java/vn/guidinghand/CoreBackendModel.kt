package vn.guidinghand

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

/** One production interface. All provider credentials and orchestration stay on our HTTPS backend. */
class CoreBackendModel(
    private val context: Context,
    private val preferences: FamilyPreferences,
    private val freshScreen: suspend () -> ScreenState?,
    private val captureForSam: suspend (ScreenState) -> Bitmap?
) : GuideModel {
    @Volatile private var epoch = 0L
    @Volatile private var connection: HttpURLConnection? = null
    private val speech = SystemSpeechOutput(context)
    private fun safe(screen: ScreenState) = ScreenReadPolicy.verdict(screen) == ScreenReadVerdict.SAFE
    private suspend fun current(expected: ScreenState): Boolean {
        val actual = freshScreen() ?: return false
        return safe(actual) && actual.planningFingerprint == expected.planningFingerprint && preferences.screenConsent && DeviceVisibility.allowed(context)
    }
    private fun endpoint(): String? = BackendEndpoint.base(preferences)?.plus("/core/step")
    private fun stale(reason: String): GuideDecision {
        GuideTrace.event(context, "stale_result", code = reason)
        return GuideDecision.StateChanged
    }
    override suspend fun next(input: GuideInput): GuideDecision {
        if (!safe(input.screen) || SafetyPolicy.sensitiveGoal(input.goal)) return GuideDecision.Await(tr("Màn hình chưa an toàn để hướng dẫn.", "I cannot guide on this screen yet."))
        val contextText = input.answers + input.userUpdates + listOf(input.family.recipient, input.family.photo, input.task?.recipient.orEmpty(), input.task?.photo.orEmpty(), input.task?.content.orEmpty())
        if (contextText.any { SafetyPolicy.sensitiveGoal(it) }) return GuideDecision.Await(tr("Thông tin cần được kiểm tra trước khi tiếp tục. Đã dừng chỉ dẫn.", "This information needs to be checked before continuing. Guidance is paused."))
        if (!preferences.screenConsent) return GuideDecision.Unavailable("Consent", tr("Cần đồng ý chia sẻ màn hình với máy chủ hướng dẫn trong Thiết lập.", "Please allow screen sharing with the guidance server in Setup."))
        val url = endpoint() ?: return GuideDecision.Unavailable("Backend", tr("Chưa cấu hình máy chủ HTTPS của GuidingHand.", "The GuidingHand HTTPS server is not configured."))
        val token = preferences.backendSession
        if (token.isBlank() || token.length > 4096 || token.any { it == '\r' || it == '\n' })
            return GuideDecision.Unavailable("Backend", tr("Cần phiên đăng nhập máy chủ GuidingHand.", "A GuidingHand server session is required."))
        val requestEpoch = epoch
        val requestId = UUID.randomUUID().toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(input.screen.planningFingerprint.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val nodes = input.screen.nodes.mapIndexed { index, node -> JSONObject()
            .put("id", "n$index").put("text", node.text.take(300)).put("aliases", JSONArray(node.aliases.take(16).map { it.take(200) }))
            .put("clickable", node.clickable).put("selected", node.selected).put("password", node.password)
            .put("class_name", node.className.take(200)).put("heading", node.heading)
            .put("region", node.region?.let { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) } ?: JSONObject.NULL) }
        val body = JSONObject().put("request_id", requestId).put("fingerprint", hash).put("consent", true).put("unlocked", true)
            .put("language", preferences.language)
            .put("allow_task_update", input.allowTaskUpdate)
            .put("installed_apps", JSONArray(InstalledApps.read(context).map { JSONObject().put("label", it.label).put("package", it.packageName) }))
            .put("goal", JSONObject().put("text", input.goal.take(500)).put("answers", JSONArray(input.answers.takeLast(8).map { it.take(200) }))
                .put("updates", JSONArray(input.userUpdates.takeLast(5).map { it.take(200) }))
                .put("dialogue", JSONArray(input.dialogue.takeLast(8).map { JSONObject().put("question", it.question.take(200)).put("answer", it.answer.take(200)) })))
            .put("screen", JSONObject().put("package_name", input.screen.packageName).put("complete", true)
                .put("is_launcher", input.screen.isLauncher).put("nodes", JSONArray(nodes)))
            .put("family", JSONObject().put("recipient", input.family.recipient.take(100)).put("photo", input.family.photo.take(100)))
            .put("observations", JSONArray(input.observations.takeLast(5).map { JSONObject().put("target", it.target.take(120))
                .put("matched_tap", it.matchedTap).put("screen_changed", it.screenChanged)
                .put("instruction", it.instruction.take(180)).put("target_label", it.targetLabel.take(120)).put("from_package", it.fromPackage.take(160)) }))
        input.task?.let { body.put("task", JSONObject().put("kind", it.id).put("recipient", it.recipient).put("photo", it.photo)
            .put("content", it.content)
            .put("app_package", it.appPackage)
            .put("verified_steps", JSONArray(input.verifiedStepIds.toList()))) }
        return try {
            if (!current(input.screen) || requestEpoch != epoch) return stale("before_capture")
            val started = android.os.SystemClock.elapsedRealtime()
            val bitmap = captureForSam(input.screen) ?: return if (!current(input.screen)) stale("capture_state_changed")
                else GuideDecision.Await(tr("Chưa chụp được màn hình an toàn. Bác giữ nguyên màn hình rồi chọn Kiểm tra lại nhé.",
                    "I could not take a safe screen snapshot. Keep this screen open and tap Check again."), "snapshot_unavailable")
            val bytes: ByteArray
            try {
                if (!current(input.screen) || requestEpoch != epoch) return stale("during_capture")
                val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
                val upload = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                try {
                bytes = withContext(Dispatchers.IO) { ByteArrayOutputStream().use { out ->
                    check(upload.compress(Bitmap.CompressFormat.JPEG, 80, out)); out.toByteArray() } }
                check(bytes.size <= 1_000_000)
                body.put("image_width", upload.width).put("image_height", upload.height)
                } finally { if (upload !== bitmap) upload.recycle() }
            } finally { bitmap.recycle() }
            // One capture and one immutable byte array. The backend passes this exact image to both models.
            val imageHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            body.put("image", "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)).put("image_sha256", imageHash)
            GuideTrace.event(context, "snapshot_encoded", code = "${body.getInt("image_width")}x${body.getInt("image_height")}:q80", value = bytes.size.toLong())
            GuideTrace.event(context, "capture_encode", android.os.SystemClock.elapsedRealtime() - started, requestId = requestId)
            if (!current(input.screen) || requestEpoch != epoch) return stale("before_upload")
            val postStarted = android.os.SystemClock.elapsedRealtime()
            val result = post(url, token, body, requestEpoch)
            GuideTrace.event(context, "backend_roundtrip", android.os.SystemClock.elapsedRealtime() - postStarted, requestId = requestId)
            result.optJSONObject("timings_ms")?.let { timing ->
                for (name in listOf("spark", "sam", "total")) if (timing.has(name) && !timing.isNull(name))
                    GuideTrace.event(context, "provider_$name", timing.optLong(name), requestId = requestId)
            }
            if (requestEpoch != epoch || !current(input.screen)) return stale("after_upload")
            if (result.optString("request_id") != requestId || result.optString("fingerprint") != hash || result.optString("image_sha256") != imageHash)
                return GuideDecision.Await(tr("Chưa xác minh được phản hồi. Đã dừng để tránh chỉ sai.", "I could not verify the response. Guidance is paused."))
            val returnedTask = result.optJSONObject("task")?.let { PhotoTask(it.optString("recipient"), it.optString("photo"), it.optString("kind"), it.optString("app_package"), it.optString("content")) }
            if (returnedTask?.id == "guide_app" && returnedTask.appPackage != input.screen.packageName &&
                InstalledApps.read(context).none { it.packageName == returnedTask.appPackage })
                return GuideDecision.Await(tr("Chưa tìm thấy ứng dụng này trên máy. Bác cho tôi biết tên ứng dụng nhé.", "I cannot find that installed app. Which app would you like?"))
            when (result.optString("status")) {
                "await" -> if (result.optString("handoff") == "screen_change") GuideDecision.Navigate(result.optString("message_vi"), returnedTask)
                    else GuideDecision.Await(result.optString("message_vi"))
                "clarify" -> GuideDecision.Clarify(result.getString("question_vi").take(300), result.optJSONArray("options")?.let { array ->
                    (0 until minOf(array.length(), 6)).map { array.getString(it).take(120) } }.orEmpty(),
                    when (result.optString("speech_purpose")) {
                        "recipient" -> SpeechPurpose.RECIPIENT
                        "message_content" -> SpeechPurpose.MESSAGE_CONTENT
                        else -> SpeechPurpose.GENERAL
                    })
                "unsupported" -> GuideDecision.Unsupported(result.optString("message_vi", tr("Việc này chưa nằm trong phạm vi hỗ trợ hiện tại.", "This task is not supported yet.")),
                    result.optJSONArray("options")?.let { array -> (0 until minOf(array.length(), 4)).map { array.getString(it).take(100) } }.orEmpty())
                "blocked" -> if (result.optString("reason_category") == "sensitive") GuideDecision.Sensitive(result.optString("message_vi"))
                    else {
                        val code = result.optString("reason").takeIf { it in setOf(
                            "task_changed", "missing_task_details", "ungrounded_target", "invalid_general_stage",
                            "unknown_app_package", "snapshot_changed", "invalid_spark_response", "invalid_region",
                            "message_content", "provider_unavailable", "core_unavailable", "sam_parser_unavailable",
                            "budget_limit", "sam_no_unique_region", "sam_target_mismatch", "invalid_sam_mask",
                            "empty_sam_mask", "sam_mask_bounds_mismatch", "incomplete_sam", "sam_dimensions_mismatch"
                        ) } ?: "model_blocked"
                        GuideDecision.Await(result.optString("message_vi", tr("Chưa chắc chắn bước tiếp theo. Cần hỏi lại.", "I am not sure about the next step. Please clarify.")), code)
                    }
                "required_core_unavailable" -> GuideDecision.Unavailable(result.optString("component"), result.optString("message_vi"))
                "step" -> {
                    if (result.optString("region_source") != "sam-3.1") return GuideDecision.Await(tr("Chưa có vùng SAM đã xác minh.", "A verified SAM region is not available yet."), "sam_missing")
                    val targetId = result.getString("target_node_id")
                    val index = Regex("n(0|[1-9]\\d*)").matchEntire(targetId)?.groupValues?.get(1)?.toIntOrNull()
                    val node = index?.let { input.screen.nodes.getOrNull(it) } ?: return GuideDecision.Await(tr("Không tìm thấy nút trong màn hình hiện tại.", "I cannot find that button on the current screen."), "target_absent")
                    val liveScreen = freshScreen() ?: return stale("missing_live_screen")
                    if (!safe(liveScreen) || liveScreen.planningFingerprint != input.screen.planningFingerprint)
                        return stale("screen_changed")
                    val liveNode = liveScreen.nodes.singleOrNull { current -> current.id == node.id && current.region == node.region &&
                        current.className == node.className && current.clickable == node.clickable && current.password == node.password &&
                        current.selected == node.selected && current.labels == node.labels } ?: return stale("target_changed")
                    val box = result.getJSONArray("region")
                    if (box.length() != 4) return GuideDecision.Await(tr("SAM chưa trả về một vùng duy nhất.", "SAM has not returned one clear region."))
                    val region = Region(box.getDouble(0).toFloat(), box.getDouble(1).toFloat(), box.getDouble(2).toFloat(), box.getDouble(3).toFloat())
                    val bounds = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
                    val targetPixels = GuideBox(kotlin.math.floor(region.left * bounds.width()).toInt(), kotlin.math.floor(region.top * bounds.height()).toInt(),
                        kotlin.math.ceil(region.right * bounds.width()).toInt(), kotlin.math.ceil(region.bottom * bounds.height()).toInt())
                    val uiRegion = liveNode.region
                    val uiPixels = uiRegion?.let { GuideBox(kotlin.math.floor(it.left * bounds.width()).toInt(), kotlin.math.floor(it.top * bounds.height()).toInt(),
                        kotlin.math.ceil(it.right * bounds.width()).toInt(), kotlin.math.ceil(it.bottom * bounds.height()).toInt()) }
                    val visibleTarget = GuideRingGeometry.displayTarget(targetPixels, uiPixels,
                        GuideBox(0, 0, bounds.width(), bounds.height()), context.resources.displayMetrics.density)
                    // If the control itself is edge-mounted, the overlay hugs the edge
                    // while keeping both strokes visible instead of rejecting the step.
                    val bounded = visibleTarget != null && visibleTarget != targetPixels
                    val visibleRegion = if (bounded) uiRegion!! else region
                    val stage = PhotoStage.valueOf(result.getString("step_id").uppercase())
                    val task = returnedTask
                    GuideDecision.Step(GuidanceStep(stage.name.lowercase(), stage, result.getString("instruction_vi").take(240), liveNode,
                        SamRegion(visibleRegion, input.screen.planningFingerprint,
                            if (bounded) "sam-3.1-ui-bounded" else "sam-3.1", if (bounded) region else null), VoiceOutput()), task)
                }
                else -> GuideDecision.Await(tr("Chưa chắc chắn bước tiếp theo. Cần hỏi lại.", "I am not sure about the next step. Please clarify."))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (requestEpoch != epoch) stale("request_cancelled")
            else GuideDecision.Unavailable("Backend", tr("Chưa nhận được bước hướng dẫn đầy đủ từ máy chủ. Đã dừng chỉ dẫn.", "The server could not return a complete step. Guidance is paused."))
        }
    }
    private fun tr(vi: String, en: String) = if (preferences.language == "en") en else vi
    private suspend fun post(url: String, token: String, body: JSONObject, requestEpoch: Long): JSONObject = withContext(Dispatchers.IO) {
        check(requestEpoch == epoch)
        val data = body.toString().toByteArray(Charsets.UTF_8)
        check(data.size <= 8_000_000)
        val http = URI(url).toURL().openConnection() as HttpURLConnection
        connection = http
        try {
            http.instanceFollowRedirects = false; http.connectTimeout = 8000; http.readTimeout = 45000
            http.requestMethod = "POST"; http.doOutput = true
            http.setRequestProperty("Content-Type", "application/json"); http.setRequestProperty("Authorization", "Bearer $token")
            http.setFixedLengthStreamingMode(data.size); http.outputStream.use { it.write(data) }
            check(http.responseCode == 200 && requestEpoch == epoch)
            val bytes = http.inputStream.use { source -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (output.size() <= 3_000_000) { val count = source.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                output.toByteArray()
            } }
            check(bytes.size <= 3_000_000)
            JSONObject(String(bytes, Charsets.UTF_8))
        } finally { http.disconnect(); if (connection === http) connection = null }
    }
    override suspend fun repeat(step: GuidanceStep) {
        val screen = checkNotNull(freshScreen())
        check(safe(screen) && screen.planningFingerprint == step.sam.frameFingerprint && preferences.screenConsent && DeviceVisibility.allowed(context))
        speech.speak(step.instructionVi) { current(screen) }
    }
    override suspend fun speakMessage(text: String, screen: ScreenState) = speech.speak(text) { current(screen) }
    override fun cancel() {
        epoch++; connection?.disconnect(); connection = null
        speech.stop()
    }
    override fun close() { cancel(); speech.close() }
}
