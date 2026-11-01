// MOCK: Local semantic matching and bilingual canned instructions. No AI or network calls.
package vn.guidinghand

import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

class MockModel(private val context: Context, private val preferences: FamilyPreferences) : GuideModel {
    @Volatile private var ready = false
    @Volatile private var initialized = false
    private var pending: Pair<Long, String>? = null
    @Volatile private var voiceLanguage = ""
    private val speechGeneration = AtomicLong()
    private val speech = Executors.newSingleThreadExecutor { Thread(it, "GuidingHand-voice").apply { isDaemon = true } }
    private fun enqueue(action: () -> Unit) {
        try { speech.execute(action) } catch (_: RejectedExecutionException) { /* already closed */ }
    }
    private val tts = TextToSpeech(context) { status ->
        initialized = status == TextToSpeech.SUCCESS
        if (initialized) enqueue { configureVoice() }
    }
    private fun configureVoice() {
        voiceLanguage = preferences.language
        val locale = if (voiceLanguage == "vi") "vi-VN" else "en-US"
        val result = tts.setLanguage(Locale.forLanguageTag(locale))
        ready = result >= TextToSpeech.LANG_AVAILABLE
        tts.setSpeechRate(0.85f)
        tts.setPitch(0.9f)
        tts.voices?.firstOrNull { it.locale.language == voiceLanguage && !it.isNetworkConnectionRequired }?.let { tts.voice = it }
        if (tts.voice?.isNetworkConnectionRequired != false) ready = false
        pending?.let { request ->
            pending = null
            if (ready && request.first == speechGeneration.get() && DeviceVisibility.allowed(context))
                tts.speak(request.second, TextToSpeech.QUEUE_FLUSH, null, "GuidingHand-step")
        }
    }
    override suspend fun getNextStep(screenshot: GuideFrame, goal: GuideTask): ModelStep? {
        // Re-plan from the visible state, preferring the furthest reachable semantic goal.
        for (step in goal.steps.asReversed()) {
            val recipient = goal.recipient ?: preferences.contact
            // A generic Send or video icon must never target a different conversation or a text draft.
            if (step.id in listOf("video", "attach", "compose") && !screenshot.screen.has(recipient.takeIf { it.isNotBlank() } ?: "\u0000")) continue
            if (step.id == "send" && screenshot.screen.nodes.none { node -> node.selected &&
                    preferences.photo.isNotBlank() && (node.aliases + node.text).any { folded(it) == folded(preferences.photo) } }) continue
            val labels = step.labels.map { when (it) {
                "{family}" -> recipient
                "{network}" -> preferences.network
                "{photo}" -> preferences.photo
                else -> it
            } }.filter { it.isNotBlank() }
            val matches = if (step.id == "openApp") listOfNotNull(LauncherTargets.icon(screenshot.screen, goal.appLabel)) else screenshot.screen.nodes.filter { node ->
                node.region != null && (node.clickable || step.completion == "scan" || (step.completion == "focus" && node.className.contains("EditText"))) && labels.any { label -> (node.aliases + node.text).any { folded(it) == folded(label) } }
            }.distinctBy { it.region }
            // Never guess between recipients, images, or duplicate controls.
            if (matches.size == 1) {
                val node = matches.single()
                val sentence = GuidanceText.instruction(step, node, goal.appLabel, recipient,
                    preferences.language, preferences.address)
                return ModelStep(step, node, sentence)
            }
        }
        if (goal.id == "open") {
            LauncherNavigation.folder(screenshot.screen, goal.appLabel)?.let {
                return LauncherNavigation.step(it, goal.appLabel, preferences.language, true)
            }
            LauncherNavigation.drawerButton(screenshot.screen)?.let {
                return LauncherNavigation.step(it, goal.appLabel, preferences.language, false)
            }
        }
        val recipient = if (goal.id == "open") goal.appLabel else goal.recipient ?: preferences.contact
        if (recipient.isNotBlank() && goal.id in listOf("open", "message", "video", "photo")) {
            val searchNodes = screenshot.screen.nodes.filter { node -> node.region != null &&
                (node.clickable || node.className.contains("EditText")) &&
                (node.aliases + node.text).any { folded(it) in listOf("search", "search apps", "search for apps", "search your phone", "search apps and content", "search zalo", "tim kiem", "tim kiem ung dung") } }
                .distinctBy { it.region }
            val focused = screenshot.screen.nodes.filter { it.focused && it.className.contains("EditText") && it.region != null }
            val search = if (goal.id == "open") LauncherTargets.search(screenshot.screen) else focused.singleOrNull() ?: searchNodes.singleOrNull()
            if (search != null) {
                val ui = UiText(preferences.language)
                val instruction = if (search.focused) ui.pick("Type $recipient in this search box.", "Gõ $recipient vào ô tìm kiếm.")
                    else ui.pick("Tap the highlighted search box.", "Bấm ô Tìm kiếm được đánh dấu.")
                val goalStep = StepGoal(if (search.focused) "enterRecipient" else "search", listOf(search.text), instruction,
                    listOf(recipient), if (search.focused) "text" else "focus")
                return ModelStep(goalStep, search, instruction)
            }
        }
        val back = screenshot.screen.nodes.filter { it.clickable && it.region != null &&
            folded(it.text) in listOf("quay lai", "tro lai", "back", "navigate up") }.distinctBy { it.region }
        if (back.size == 1) {
            val recovery = StepGoal("recover", listOf(back.single().text), UiText(preferences.language).pick("Tap Back near the top.", "Bấm Quay lại ở phía trên."), goal.steps.flatMap { it.labels })
            return ModelStep(recovery, back.single(), recovery.instruction)
        }
        return null
    }
    override fun speak(text: String) {
        if (!DeviceVisibility.allowed(context)) return
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        if (accessibility.isTouchExplorationEnabled) return // Overlay live region supplies TalkBack speech.
        val generation = speechGeneration.get()
        enqueue {
            if (generation != speechGeneration.get() || !DeviceVisibility.allowed(context)) return@enqueue
            if (initialized && voiceLanguage != preferences.language) configureVoice()
            if (!ready) { pending = generation to text; return@enqueue }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuidingHand-step")
        }
    }
    fun voiceReady(): Boolean {
        if (initialized && (voiceLanguage != preferences.language || !ready)) enqueue { configureVoice() }
        return ready && voiceLanguage == preferences.language
    }
    override fun silence() { speechGeneration.incrementAndGet(); enqueue { pending = null; tts.stop() } }
    override fun close() { speechGeneration.incrementAndGet(); enqueue { pending = null; tts.stop(); tts.shutdown() }; speech.shutdown() }
}
