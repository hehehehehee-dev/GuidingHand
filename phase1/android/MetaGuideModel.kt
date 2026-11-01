package vn.guidinghand

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import android.util.Log

/** Debug USB gateway only. The provider API key never enters Android. */
class MetaGuideModel(private val context: Context, private val preferences: FamilyPreferences,
                     private val freshScreen: () -> ScreenState?, private val epoch: () -> Long,
                     private val appCatalog: () -> List<SpokenApp> = { emptyList() },
                     private val captureScreen: suspend (ScreenState) -> Bitmap? = { null }) : GuideModel {
    private val local = MockModel(context, preferences)
    private val memory = TargetMemory(context)
    private var lastGoal = ""
    private var lastSuggestedLabel = ""
    private var staleCount = 0
    private fun changedScreen() {
        staleCount++; lastFeedback = ModelFeedback(false, if (staleCount <= 3) "screen_changed" else "moving")
        Log.d("GuidingHandTiming", "screen_refresh=$staleCount")
    }
    override fun needsScreenshot(screen: ScreenState, task: GuideTask) = false
    private fun appsJson(apps: List<SpokenApp>) = JSONArray(apps.mapIndexed { i, app ->
        JSONObject().put("id", "a$i").put("label", app.label.take(100)).put("package", app.packageName)
    })
    private suspend fun post(path: String, payload: JSONObject, requestEpoch: Long): JSONObject? = withContext(Dispatchers.IO) {
        val started = android.os.SystemClock.elapsedRealtime()
        if (requestEpoch != epoch() || !preferences.metaGuideEnabled || !DeviceVisibility.allowed(context)) return@withContext null
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > 2_000_000) return@withContext null
        val connection = URL("http://127.0.0.1:8766$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 4000; connection.readTimeout = 35000
            connection.instanceFollowRedirects = false; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${preferences.metaClientToken}")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            if (connection.responseCode != 200) return@withContext null
            val response = connection.inputStream.use { input -> ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(1024)
                while (output.size() <= 16_000) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                output.toByteArray()
            } }
            if (response.size > 16_000 || requestEpoch != epoch() || !preferences.metaGuideEnabled || !DeviceVisibility.allowed(context)) return@withContext null
            JSONObject(String(response, Charsets.UTF_8))
        } catch (_: Exception) { null } finally {
            connection.disconnect()
            Log.d("GuidingHandTiming", "gateway_${path.removePrefix("/")}_ms=${android.os.SystemClock.elapsedRealtime() - started}")
        }
    }
    suspend fun routeGoal(goal: String, apps: List<SpokenApp>, currentPackage: String): GoalRoute? {
        if (!BuildConfig.DEBUG || !preferences.metaGuideEnabled || GeneralGuidePolicy.sensitiveGoal(goal)) return null
        val requestEpoch = epoch(); val requestId = UUID.randomUUID().toString()
        val body = JSONObject().put("request_id", requestId).put("goal", goal.take(500)).put("language", preferences.language)
            .put("package_name", currentPackage).put("apps", appsJson(apps)).put("consent", true).put("unlocked", true)
        val result = post("/route", body, requestEpoch) ?: return null
        if (result.optString("request_id") != requestId) return null
        if (result.optString("status") == "clarify") return GoalRoute(null, question = result.optString("question_code", "app"))
        val app = if (result.optString("status") == "current") apps.firstOrNull { it.packageName == currentPackage }
            else GoalRouting.resolve(apps, result.optString("app_id"))
        return app?.let { GoalRoute(it, result.optString("action", "launch"), result.optString("query").take(180)) }
    }
    private var lastFeedback: ModelFeedback? = null
    private var goalIdentity = ""
    private val observations = ArrayDeque<JSONObject>()
    override fun feedback() = lastFeedback
    override fun resetContext() { observations.clear(); goalIdentity = ""; lastFeedback = null; staleCount = 0; lastSuggestedLabel = "" }
    override fun observeTransition(before: ScreenState, after: ScreenState, step: ModelStep?, matchedTap: Boolean) {
        if (before.fingerprint == after.fingerprint || step == null) return
        if (matchedTap && !SafetyPolicy.sensitive(after)) memory.remember(lastGoal, before, step.node)
        observations.addLast(JSONObject().put("target", step.node.text.take(120))
            .put("matched_tap", matchedTap).put("screen_changed", true))
        while (observations.size > 5) observations.removeFirst()
    }
    override suspend fun getNextStep(screenshot: GuideFrame, goal: GuideTask): ModelStep? {
        lastFeedback = null
        val general = goal.id == "assist"
        val launcher = goal.id == "open"
        val adaptive = general || launcher
        val identity = "${goal.label}:${preferences.language}:${goal.recipient.orEmpty()}"
        lastGoal = identity + ":" + goal.clarification
        if (identity != goalIdentity) { goalIdentity = identity; observations.clear() }
        // Obvious controls and existing verified templates stay instant and do not spend API credit.
        if (!general) {
            val fast = local.getNextStep(screenshot, goal)
            if ((fast != null && (!launcher || fast.goal.id != "recover")) || !preferences.metaGuideEnabled || goal.id == "screen") return fast
            if (launcher && preferences.drawerLauncher == screenshot.screen.packageName &&
                !LauncherNavigation.drawer(screenshot.screen) && !LauncherNavigation.openFolder(screenshot.screen)) {
                lastFeedback = ModelFeedback(false, "scroll_down"); return null
            }
        } else if (!preferences.metaGuideEnabled) { lastFeedback = ModelFeedback(false, "connection"); return null }
        if (!BuildConfig.DEBUG || !DeviceVisibility.allowed(context)) return null
        val screen = screenshot.screen
        if (adaptive && ((screen.packageName != goal.appPackage && screen.packageName !in goal.allowedPackages) || GeneralGuidePolicy.sensitiveGoal(goal.label))) return null
        val choices = if (launcher) LauncherNavigation.choices(screen, goal, appCatalog(), preferences.language)
            else if (general) GeneralGuidePolicy.choices(screen, preferences.language)
            else MetaChoices.build(screen, goal, GuideFamily(preferences.contact, preferences.network,
                preferences.photo, preferences.address), preferences.language)
        if (general && observations.isEmpty() && goal.clarification.isBlank()) {
            val remembered = memory.find(lastGoal, screen)
            choices.singleOrNull { it.node == remembered }?.let {
                Log.d("GuidingHandTiming", "verified_target_cache_hit=1")
                lastSuggestedLabel = it.node.text.take(120)
                return ModelStep(it.goal, it.node, it.instruction)
            }
        }
        val installed = appCatalog().take(250)
        if ((!adaptive && choices.isEmpty()) || !preferences.metaClientToken.matches(Regex("[a-f0-9]{48}"))) return null
        val requestEpoch = epoch()
        val requestId = UUID.randomUUID().toString()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(screen.fingerprint.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        suspend fun request(bitmap: Bitmap?): JSONObject? {
            if (requestEpoch != epoch() || !preferences.metaGuideEnabled || !DeviceVisibility.allowed(context)) return null
            if (!PlanningContext.compatible(screen, withContext(Dispatchers.IO) { freshScreen() })) { changedScreen(); return null }
            val payload = JSONObject().put("request_id", requestId).put("fingerprint", fingerprint)
                .put("mode", if (adaptive) "goal" else "choice").put("history", if (adaptive) JSONArray(observations.toList()) else JSONArray())
                .put("language", preferences.language).put("goal", UiText(preferences.language).task(goal).take(500))
                .put("package_name", screen.packageName).put("complete", screen.complete).put("unlocked", true).put("consent", true)
                .put("nodes", JSONArray(screen.nodes.distinctBy { it.password to (it.aliases + it.text).distinct() }.map { node -> JSONObject()
                    .put("text", (node.aliases + node.text).distinct().joinToString(" | ").take(300)).put("password", node.password) }))
                .put("choices", JSONArray(choices.map { choice -> JSONObject().put("id", choice.id).put("step", choice.goal.id)
                    .put("label", choice.node.text.take(200).ifBlank { "control" }).put("instruction", if (general) "tap" else choice.instruction.take(200))
                    .put("region", JSONArray(listOf(choice.node.region!!.left, choice.node.region.top, choice.node.region.right, choice.node.region.bottom))) }))
            if (general && installed.isNotEmpty()) payload.put("cross_app", true).put("apps", appsJson(installed))
            if (launcher) payload.put("launcher_navigation", true)
            goal.recipient?.let { payload.put("recipient", it.take(100)) }
            if (goal.clarification.isNotBlank()) payload.put("clarification", goal.clarification.take(200)).put("last_suggestion", lastSuggestedLabel)
            if (bitmap == null) payload.put("tree_only", true) else {
                val encoded = withContext(Dispatchers.IO) {
                    val scale = minOf(1f, 768f / maxOf(bitmap.width, bitmap.height))
                    val image = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                    try { ByteArrayOutputStream().use { bytes -> image.compress(Bitmap.CompressFormat.JPEG, 70, bytes)
                        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP) } }
                    finally { if (image !== bitmap) image.recycle() }
                }
                payload.put("image", "data:image/jpeg;base64,$encoded")
            }
            return post("/plan", payload, requestEpoch)
        }
        var decision = if (adaptive) request(null) else null
        if (!adaptive || decision?.optString("status") == "needs_image") {
            val image = captureScreen(screen)
            if (image != null) try { decision = request(image) } finally { image.recycle() }
        }
        if (requestEpoch != epoch() || !preferences.metaGuideEnabled || !DeviceVisibility.allowed(context)) return null
        val result = decision ?: run { if (adaptive && lastFeedback == null) lastFeedback = ModelFeedback(false, "connection"); return null }
        Log.d("GuidingHandTiming", "model_status=${result.optString("status").take(24)}")
        val fresh = withContext(Dispatchers.IO) { freshScreen() }
        if (!PlanningContext.compatible(screen, fresh)) { changedScreen(); return null }
        if (result.optString("request_id") != requestId || result.optString("fingerprint") != fingerprint) return null
        if (general && result.optString("status") == "open_app") {
            val app = GoalRouting.resolve(installed, result.optString("app_id")) ?: return null
            lastFeedback = ModelFeedback(false, "app", app.packageName); return null
        }
        if (adaptive && result.optString("status") in listOf("needs_input", "review")) {
            staleCount = 0
            lastFeedback = ModelFeedback(!launcher && result.optString("status") == "review", result.optString("question_code", "goal")); return null
        }
        val choice = MetaChoices.resolve(choices, result.optString("status"), result.optString("choice_id"),
            requestId, result.optString("request_id"), fingerprint, result.optString("fingerprint")) ?: return null
        val currentNode = fresh?.let { PlanningContext.rebind(choice.node, it) } ?: run { changedScreen(); return null }
        staleCount = 0
        lastSuggestedLabel = currentNode.text.take(120)
        return ModelStep(choice.goal, currentNode, choice.instruction)
    }
    fun voiceReady() = local.voiceReady()
    override fun speak(text: String) = local.speak(text)
    override fun silence() = local.silence()
    override fun close() = local.close()
}
