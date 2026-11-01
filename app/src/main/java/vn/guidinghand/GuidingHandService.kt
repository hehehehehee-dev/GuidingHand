package vn.guidinghand

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume

/** Observe, gate, plan, wait, verify. This service has no gesture, typing or click-execution API. */
class GuidingHandService : AccessibilityService() {
    companion object { var instance: GuidingHandService? = null; private set }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private lateinit var engine: GuideEngine
    private lateinit var model: GuideModel
    private lateinit var overlay: GuideOverlay
    private lateinit var prefs: FamilyPreferences
    private var screen: ScreenState? = null
    var viewState = GuideViewState(GuideStatus.IDLE, ""); private set
    private var receiverRegistered = false
    @Volatile private var voicePermitUntil = 0L
    private var voiceSubmission: Job? = null
    private var voiceOpening = false
    private var voiceGeneration = 0L
    private var recordingPurpose = SpeechPurpose.GENERAL
    private val voiceDraft = VoiceDraftSession()
    private lateinit var copiedDraft: TemporaryClipboard
    private var sourceWindow = -1
    private var sourceGeneration = 0L
    /** The setup screens already contain full-size controls; the floating Help control must not cover them. */
    private fun ownSetupForeground(): Boolean = SetupActivity.foreground?.get() != null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!DeviceVisibility.allowed(this@GuidingHandService)) {
                voicePermitUntil = 0L; voiceSubmission?.cancel(); voiceDraft.clear()
                if (::copiedDraft.isInitialized) copiedDraft.clearIfOwned()
                model.cancel(); overlay.hide()
                scope.launch { engine.onScreen(ScreenState("locked", emptyList(), complete = false, isLocked = true)) }
            } else {
                if (ownSetupForeground() && !VoiceActivity.visible) overlay.hide()
                else overlay.render(viewState, engine.launcherStep != null)
                updates.trySend(Unit)
            }
        }
    }
    override fun onServiceConnected() {
        instance = this; prefs = FamilyPreferences(this)
        copiedDraft = temporaryClipboard(this)
        model = makeModel()
        GuideTrace.event(this, "model", code = if (model.developmentStandIn) "PHASE1_MOCK" else "META_CORE")
        overlay = GuideOverlay(this, { stopGuide() }, { scope.launch { readFresh()?.let { engine.repeat(it) } } },
            { openForElder() }, { callFamily() }, { openIntake() }, { answer(it) }, { resumeGuide() },
            { screen?.let { engine.inspectBeforeSpeech(it.copy(readIssue = "pointing_clipped")) } },
            { confirmVoiceDraft() }, { retryVoiceDraft() }, { copyVoiceDraft() }, { editVoiceDraft() })
        engine = newEngine()
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
        receiverRegistered = true
        if (ownSetupForeground()) overlay.hide() else overlay.showBubble()
        scope.launch {
            for (ignored in updates) {
                delay(120); while (updates.tryReceive().isSuccess) { /* coalesce UI updates */ }
                if (VoiceActivity.activeSession || voiceSubmission?.isActive == true) continue
                if (!DeviceVisibility.allowed(this@GuidingHandService)) { overlay.hide(); continue }
                if (ownSetupForeground()) { overlay.hide(); continue }
                if (engine.status == GuideStatus.IDLE) continue
                val fresh = readSettledForGuidance() ?: ScreenState("unavailable", emptyList(), complete = false, readIssue = "no_window")
                if (fresh.packageName == packageName) continue // Own setup/answer UI is never model input.
                screen = fresh
                val began = android.os.SystemClock.elapsedRealtime()
                engine.onScreen(fresh)
                GuideTrace.event(this@GuidingHandService, "screen_step", android.os.SystemClock.elapsedRealtime() - began, engine.status.name)
                // Engine emissions already render the card. Re-rendering on every Accessibility
                // event repeatedly detaches the ring and remeasures a card that has not changed.
                if (engine.status == GuideStatus.READING) updates.trySend(Unit)
            }
        }
    }
    private fun makeModel() = GuideModelFactory.create(this, prefs, { withContext(Dispatchers.IO) { readTree() } }, { expected -> captureForSam(expected) })
    private fun newEngine() = GuideEngine(model, { state ->
        if (state.status == GuideStatus.SAFETY_PAUSED) {
            voiceDraft.clear(); if (::copiedDraft.isInitialized) copiedDraft.clearIfOwned()
        }
        viewState = if (model.developmentStandIn && state.status != GuideStatus.IDLE) state.copy(instructionVi = "Phase 1 dev · ${state.instructionVi}") else state
        GuideTrace.event(this, "state", code = state.status.name)
        state.reasonCode?.let { GuideTrace.event(this, "guide_stop", code = it) }
        if (VoiceActivity.visible) overlay.showVoiceBusy(tr("Đang nghe…", "Listening…"))
        else if (ownSetupForeground()) overlay.hide()
        else overlay.render(viewState, engine.launcherStep != null)
    }, FamilyContext(prefs.contact, prefs.photo), allowDevStandIn = model.developmentStandIn, language = prefs.respectfulLanguage,
        locale = prefs.language, onTransition = { transition ->
            val reason = transition.reasonCode?.let { ":$it" }.orEmpty()
            GuideTrace.event(this, "transition", code = "${transition.from.name}.${transition.to.name}$reason",
                value = transition.sequence)
        })
    fun refreshLanguage() { engine.updateLanguage(prefs.respectfulLanguage, prefs.language); updates.trySend(Unit) }
    fun refreshPreferences() {
        engine.updateLanguage(prefs.respectfulLanguage, prefs.language)
        overlay.refreshPreferences()
        if (ownSetupForeground() && !VoiceActivity.visible) overlay.hide()
        updates.trySend(Unit)
    }
    fun reloadModel() { engine.stop(); model.close(); model = makeModel(); engine = newEngine() }
    fun startGoal(goal: String) {
        voiceDraft.clear(); if (::copiedDraft.isInitialized) copiedDraft.clearIfOwned()
        engine.stop(); engine = newEngine()
        GuideTrace.event(this, "goal_started") // Content-free timing anchor for real-phone evidence.
        engine.startGoal(goal); updates.trySend(Unit)
    }
    /** Starts the existing Zalo send-photo flow with a recipient supplied by setup/home UI.
     * The elder still performs every action in Zalo; this method only seeds task context. */
    fun startPhotoTaskForRecipient(recipient: String) {
        if (!::engine.isInitialized || !DeviceVisibility.allowed(this)) return
        voiceDraft.clear(); if (::copiedDraft.isInitialized) copiedDraft.clearIfOwned()
        engine.stop(); engine = newEngine()
        GuideTrace.event(this, "goal_started")
        engine.startGoal(tr("Gửi ảnh trên Zalo", "Send a photo on Zalo"), PhotoTask(recipient.trim(), prefs.photo))
        updates.trySend(Unit)
    }
    fun startPhotoDevTask() {
        if (!model.developmentStandIn) return
        engine.stop(); engine = newEngine()
        engine.startGoal("Gửi ảnh đã chọn cho người nhận trên Zalo", PhotoTask(prefs.contact, prefs.photo)); updates.trySend(Unit)
    }
    fun answer(text: String) { engine.answer(text); updates.trySend(Unit) }
    fun resumeGuide() { engine.resume(); updates.trySend(Unit) }
    fun stopGuide() { voiceGeneration++; voicePermitUntil = 0L; voiceSubmission?.cancel(); voiceDraft.clear(); if (::copiedDraft.isInitialized) copiedDraft.clearIfOwned(); engine.stop() }
    private var voiceAnswersCurrent = false
    private fun openIntake() = openVoice()
    fun openVoice() {
        if (voiceOpening || VoiceActivity.activeSession || !DeviceVisibility.allowed(this)) return
        voiceOpening = true; voiceGeneration++; voiceSubmission?.cancel()
        scope.launch {
          try {
            if (engine.status == GuideStatus.SAFETY_PAUSED) { overlay.render(viewState, false); return@launch }
            SetupActivity.foreground?.get()?.let { own -> own.moveTaskToBack(true); delay(350) }
            val current = withContext(Dispatchers.IO) { readTree(allowOwn = true) }
            val verdict = ScreenReadPolicy.verdict(current)
            if (verdict != ScreenReadVerdict.SAFE) {
                GuideTrace.event(this@GuidingHandService, "voice_gate", code = verdict.name)
                engine.inspectBeforeSpeech(current ?: ScreenState("unavailable", emptyList(), complete = false, readIssue = "no_window"))
                if (verdict == ScreenReadVerdict.CANNOT_READ) overlay.showVoiceNotice(tr("Tôi chưa đọc rõ màn hình này. Bác đóng bảng đang mở rồi thử lại nhé.", "I cannot read this screen. Close the open panel, then try again."))
                return@launch
            }
            if (engine.status == GuideStatus.SAFETY_PAUSED) { overlay.render(viewState, false); return@launch }
            if (!engine.inspectBeforeSpeech(current!!)) return@launch
            voiceAnswersCurrent = engine.acceptsSpokenRefinement
            val speechPurpose = if (voiceAnswersCurrent) viewState.speechPurpose else SpeechPurpose.GENERAL
            recordingPurpose = speechPurpose
            voiceDraft.begin(speechPurpose)
            voicePermitUntil = android.os.SystemClock.elapsedRealtime() + 180_000
            model.cancel(); overlay.showVoiceBusy(tr("Micro đã sẵn sàng", "Microphone ready"))
            try {
                startActivity(Intent(this@GuidingHandService, VoiceActivity::class.java)
                    .putExtra(VoiceActivity.EXTRA_SPEECH_PURPOSE, speechPurpose.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                android.util.Log.i("GuidingHandVoice", "voice_open_requested")
            } catch (_: Exception) {
                overlay.showVoiceNotice(tr("Chưa mở được micro. Bác bấm Nói để thử lại nhé.", "I could not open the microphone. Tap Speak to try again."))
                android.util.Log.w("GuidingHandVoice", "voice_open_failed")
            }
          } finally { voiceOpening = false }
        }
    }
    suspend fun speechScreen(): ScreenState? = if (engine.status == GuideStatus.SAFETY_PAUSED) null else
        withContext(Dispatchers.IO) { readTree(allowOwn = true) }?.takeIf { ScreenReadPolicy.verdict(it) == ScreenReadVerdict.SAFE }
    /** A pre-window permit enables local recording only, never an upload based on an old screen. */
    fun localRecordingAllowed() = android.os.SystemClock.elapsedRealtime() < voicePermitUntil &&
        DeviceVisibility.allowed(this) && engine.status != GuideStatus.SAFETY_PAUSED
    fun voiceRecordingStarted() { overlay.showVoiceBusy(tr("Đang nghe…", "Listening…")) }
    fun submitVoiceRecording(wave: ByteArray) {
        voicePermitUntil = 0L
        voiceSubmission?.cancel()
        val session = voiceGeneration
        voiceSubmission = scope.launch {
            val client = SpeechInputClient(prefs)
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                // The mic Activity must be gone before any fresh screen read or provider request.
                repeat(30) { if (VoiceActivity.activeSession) delay(50) }
                check(!VoiceActivity.activeSession)
                var fresh: ScreenState? = null
                repeat(8) {
                    if (fresh == null) {
                        val candidate = withContext(Dispatchers.IO) { readTree(allowOwn = true) }
                        if (ScreenReadPolicy.verdict(candidate) == ScreenReadVerdict.SAFE) fresh = candidate
                        else if (candidate != null && ScreenReadPolicy.verdict(candidate) in setOf(ScreenReadVerdict.SENSITIVE, ScreenReadVerdict.LOCKED)) {
                            engine.inspectBeforeSpeech(candidate); error("sensitive")
                        } else delay(100)
                    }
                }
                val current = fresh ?: error("cannot_read")
                check(engine.inspectBeforeSpeech(current))
                overlay.showWorking(tr("Đang chuyển lời nói thành chữ…", "Turning speech into text…"))
                val speechKeywords = buildList {
                    addAll(listOf("GuidingHand", "Zalo", "Instagram", "YouTube", "Messenger", "Facebook", "WhatsApp", "TikTok"))
                    prefs.contact.trim().takeIf { it.isNotBlank() }?.let(::add)
                    addAll(InstalledApps.read(this@GuidingHandService).asSequence().map { it.label }.filter { it.length in 2..64 }.take(23))
                }.distinctBy { it.lowercase() }.take(32)
                val transcript = client.transcribe(wave, current, speechKeywords) {
                    session == voiceGeneration && !VoiceActivity.activeSession && DeviceVisibility.allowed(this@GuidingHandService) && speechScreen() != null
                }
                GuideTrace.event(this@GuidingHandService, "transcribe", android.os.SystemClock.elapsedRealtime() - started)
                if (session == voiceGeneration) receiveSpeech(transcript, session)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val current = withContext(Dispatchers.IO) { readTree(allowOwn = true) }
                val verdict = ScreenReadPolicy.verdict(current)
                GuideTrace.event(this@GuidingHandService, "transcribe_stopped", code = verdict.name)
                if (failure is SpeechInputFailure && failure.category == "sensitive") {
                    engine.pauseForSensitive(); overlay.render(viewState, false)
                } else if (engine.status == GuideStatus.SAFETY_PAUSED || verdict in setOf(ScreenReadVerdict.SENSITIVE, ScreenReadVerdict.LOCKED)) {
                    current?.let { engine.inspectBeforeSpeech(it) }; overlay.render(viewState, false)
                } else overlay.showVoiceNotice(if (verdict == ScreenReadVerdict.CANNOT_READ)
                    tr("Tôi chưa nhìn rõ màn hình. Bác đóng bảng đang mở rồi thử lại nhé.", "I cannot read this screen. Close the open panel, then try again.")
                    else tr("Chưa nhận được lời nói. Bác bấm Nói để thử lại nhé.", "I could not transcribe that. Tap Speak to try again."))
            } finally { wave.fill(0); client.cancel() }
        }
    }
    /** Text fallback still re-checks the screen after the input window closes. It never types into another app. */
    fun submitTypedInput(text: String) {
        voicePermitUntil = 0L
        voiceSubmission?.cancel()
        val session = voiceGeneration
        voiceSubmission = scope.launch {
            try {
                repeat(30) { if (VoiceActivity.activeSession) delay(50) }
                check(!VoiceActivity.activeSession)
                val current = speechScreen() ?: error("cannot_read")
                check(engine.inspectBeforeSpeech(current))
                if (session == voiceGeneration) receiveSpeech(text, session)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val current = withContext(Dispatchers.IO) { readTree(allowOwn = true) }
                current?.let { engine.inspectBeforeSpeech(it) }
                if (engine.status != GuideStatus.SAFETY_PAUSED)
                    overlay.showVoiceNotice(tr("Tôi chưa đọc rõ màn hình. Bác giữ nguyên màn hình rồi thử lại nhé.",
                        "I cannot read this screen yet. Keep it open and try again."))
            }
        }
    }
    fun speakFromHome() = openVoice() // Kept as a source-compatible entry; never moves the elder to Home.
    fun receiveSpeech(text: String, session: Long = voiceGeneration) {
        scope.launch {
            delay(250)
            if (session != voiceGeneration || !DeviceVisibility.allowed(this@GuidingHandService) || engine.status == GuideStatus.SAFETY_PAUSED) return@launch
            if (!voiceDraft.capture(text)) {
                if (SafetyPolicy.sensitiveGoal(text)) engine.pauseForSensitive()
                else overlay.showVoiceNotice(tr("Tôi chưa nghe rõ. Bác bấm Nói lại nhé.", "I could not understand that. Tap Record again."))
                return@launch
            }
            voiceDraft.textForReview()?.let { overlay.showDraftReview(it, recordingPurpose) }
        }
    }
    private fun applyConfirmedSpeech(text: String) {
      scope.launch {
        when (folded(text).trim('.', '!', '?')) {
                "dung", "dung lai", "stop", "thoi" -> engine.stop()
                "nhac lai", "noi lai", "repeat" -> readFresh()?.let { engine.repeat(it) }
                "tiep tuc", "continue" -> engine.resume()
                else -> if (voiceAnswersCurrent) engine.answer(text) else startGoal(text)
        }
        if (engine.hasGoal) overlay.setHeardSpeech(text)
        updates.trySend(Unit)
      }
    }
    private fun confirmVoiceDraft() {
        if (!DeviceVisibility.allowed(this) || !voiceDraft.confirmReview()) { voiceDraft.clear(); return }
        if (voiceDraft.purpose == SpeechPurpose.MESSAGE_CONTENT)
            voiceDraft.textForExplicitCopy()?.let { overlay.showCopyConsent(it) }
        else scope.launch {
            val current = withContext(Dispatchers.IO) { readTree(allowOwn = true) }
            if (ScreenReadPolicy.verdict(current) != ScreenReadVerdict.SAFE || current == null || !engine.inspectBeforeSpeech(current)) {
                current?.let { engine.inspectBeforeSpeech(it) }
                if (engine.status != GuideStatus.SAFETY_PAUSED) overlay.showVoiceNotice(tr(
                    "Tôi chưa đọc rõ màn hình. Bác giữ nguyên màn hình rồi thử lại nhé.",
                    "I cannot read this screen yet. Keep it open and try again."
                ))
                return@launch
            }
            voiceDraft.consumeConfirmedInput()?.let(::applyConfirmedSpeech)
        }
    }
    private fun retryVoiceDraft() {
        val purpose = voiceDraft.purpose
        voiceDraft.begin(purpose)
        openVoice()
    }
    private fun editVoiceDraft() {
        val text = voiceDraft.textForReview() ?: return
        if (VoiceActivity.activeSession || !DeviceVisibility.allowed(this)) return
        voicePermitUntil = android.os.SystemClock.elapsedRealtime() + 180_000
        try {
            startActivity(Intent(this, VoiceActivity::class.java)
                .putExtra(VoiceActivity.EXTRA_SPEECH_PURPOSE, voiceDraft.purpose.name)
                .putExtra(VoiceActivity.EXTRA_REVIEW_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            overlay.showVoiceNotice(tr("Chưa mở được phần sửa câu. Bác thử Nói lại nhé.", "I could not open the editor. Please record again."))
        }
    }
    fun submitEditedVoiceText(text: String) {
        voicePermitUntil = 0L
        voiceDraft.begin(recordingPurpose)
        if (!voiceDraft.capture(text)) {
            overlay.showVoiceNotice(tr("Bác viết một câu trước nhé.", "Please enter a sentence first.")); return
        }
        voiceDraft.textForReview()?.let { overlay.showDraftReview(it, recordingPurpose) }
    }
    private fun copyVoiceDraft() {
        scope.launch {
            val current = withContext(Dispatchers.IO) { readTree(allowOwn = true) }
            if (!DeviceVisibility.allowed(this@GuidingHandService) || ScreenReadPolicy.verdict(current) != ScreenReadVerdict.SAFE) {
                voiceDraft.clear(); current?.let { engine.inspectBeforeSpeech(it) }; return@launch
            }
            val text = voiceDraft.textForExplicitCopy() ?: return@launch
            if (!copiedDraft.copyAfterExplicitConsent(text) || !voiceDraft.markCopied()) {
                voiceDraft.clear(); overlay.showVoiceNotice(tr("Chưa sao chép được nội dung. Bác thử lại nhé.", "I could not copy the message. Please try again.")); return@launch
            }
            engine.answer(text)
            voiceDraft.clear()
            updates.trySend(Unit)
        }
    }
    fun voiceClosed(message: String? = null) {
        voicePermitUntil = 0L
        if (DeviceVisibility.allowed(this)) {
            if (voiceSubmission?.isActive == true) overlay.showWorking(tr("Đang chuyển lời nói thành chữ…", "Turning speech into text…"))
            else if (message != null) overlay.showVoiceNotice(if (prefs.language == "vi") prefs.respectfulLanguage.message(message) else message)
            else overlay.render(viewState, engine.launcherStep != null)
        }
        updates.trySend(Unit)
    }
    private suspend fun readFresh() = withContext(Dispatchers.IO) { readTree() }
    /** Wait briefly for launcher/app transitions to settle before spending a model call or asking the elder. */
    private suspend fun readSettledForGuidance(): ScreenState? {
        var candidate = readFresh()
        val transition = engine.status in setOf(GuideStatus.WAITING_NAVIGATION, GuideStatus.READING, GuideStatus.PLANNING) ||
            ScreenReadPolicy.verdict(candidate) == ScreenReadVerdict.CANNOT_READ
        if (!transition) return candidate
        var consecutive = 0
        repeat(8) {
            delay(140)
            val next = readFresh() ?: return@repeat
            consecutive = if (ScreenReadPolicy.verdict(next) == ScreenReadVerdict.SAFE && next.planningFingerprint == candidate?.planningFingerprint) consecutive + 1 else 0
            candidate = next
            if (consecutive >= 1) return candidate
        }
        return candidate
    }
    private val resolvedLauncher by lazy { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivity(packageManager)?.packageName }
    private fun launcherPackage() = resolvedLauncher
    private fun safe(screen: ScreenState?) = ScreenReadPolicy.verdict(screen) == ScreenReadVerdict.SAFE
    private fun tr(vi: String, en: String) = if (prefs.language == "en") en else vi
    private fun openForElder() {
        val permitted = engine.launcherStep ?: return
        scope.launch {
            val fresh = readFresh() ?: return@launch
            if (!safe(fresh) || !fresh.isLauncher || !DeviceVisibility.allowed(this@GuidingHandService) ||
                fresh.planningFingerprint != permitted.sam.frameFingerprint || engine.launcherStep != permitted) return@launch
            val task = engine.task ?: return@launch
            val launch = packageManager.getLaunchIntentForPackage(task.appPackage) ?: return@launch
            model.cancel() // Only this explicit launcher-card button can start the target app.
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            updates.trySend(Unit) // A launch request is not success; the actual package must be observed.
        }
    }
    private fun callFamily() {
        if (engine.status != GuideStatus.SAFETY_PAUSED || !DeviceVisibility.allowed(this)) return
        val number = prefs.phone.filter { it.isDigit() || it == '+' }
        model.cancel(); overlay.hide()
        val dialer = if (number.isBlank()) Intent(Intent.ACTION_DIAL) else Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        startActivity(dialer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    /** Home action: opens the dialer only; never places a call. */
    fun callFamilyFromHome() {
        if (!DeviceVisibility.allowed(this)) return
        val number = prefs.phone.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) return
        startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!::engine.isInitialized) return
        if (!DeviceVisibility.allowed(this)) { model.cancel(); overlay.hide(); return }
        // System UI and overlay windows can emit events while our setup activity is still the
        // foreground app. Do not let those unrelated package events resurrect Help on top of it.
        if (ownSetupForeground() && !VoiceActivity.visible) { overlay.hide(); return }
        if (event.packageName?.toString() == packageName) {
            // Our accessibility overlay also emits events under this package. Hiding it for
            // those events creates a Help-bubble hide/show loop on top of another app.
            val ownActivityForeground = windows.any { window ->
                window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() == packageName &&
                    (window.id == event.windowId || window.isActive || window.isFocused)
            }
            if (!ownActivityForeground) return
            if (VoiceActivity.visible) overlay.showVoiceBusy(tr("Đang nghe…", "Listening…"))
            else overlay.hide() // The in-app setup has its own large controls; do not cover them with the floating guide.
            return
        }
        if (engine.status == GuideStatus.IDLE) overlay.showBubble()
        val source = event.source
        // Xiaomi can deliver null entries in AccessibilityEvent.text during transient
        // window changes. Treat them as absent; a null must never crash the service and
        // make the Help bubble disappear.
        val hints = event.text.mapNotNull { it?.toString()?.takeIf { text -> text.isNotBlank() } }
            .map { ScreenNode("hint", it, null, false) }.toMutableList()
        if (source?.isPassword == true) hints += ScreenNode("password", "", null, false, password = true)
        val hint = ScreenState(event.packageName?.toString().orEmpty(), hints)
        if (SafetyPolicy.sensitive(hint)) {
            voicePermitUntil = 0L; voiceSubmission?.cancel()
            model.cancel(); overlay.hide(); scope.launch {
                if (VoiceActivity.visible) engine.inspectBeforeSpeech(hint) else engine.onScreen(hint)
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val bounds = wmBounds()
            var node = source
            for (ignored in 0 until 8) {
                val actual = node
                if (actual != null) {
                    val read = readNode(actual, bounds, "event")
                    val match = screen?.nodes?.filter { it.region == read.region && it.className == read.className &&
                        (it.text == read.text || it.labels.any { label -> label in read.labels }) }?.singleOrNull()
                    if (match != null) { engine.onTap(match); GuideTrace.event(this, "elder_tap", code = viewState.step?.stage?.name); break }
                    node = actual.parent
                }
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) synchronized(this) { sourceGeneration++ }
        updates.trySend(Unit)
    }
    private fun wmBounds() = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
    private fun readNode(node: AccessibilityNodeInfo, display: Rect, path: String): ScreenNode {
        val rect = Rect(); node.getBoundsInScreen(rect)
        val left = (rect.left.toFloat() / display.width()).coerceIn(0f, 1f)
        val top = (rect.top.toFloat() / display.height()).coerceIn(0f, 1f)
        val right = (rect.right.toFloat() / display.width()).coerceIn(0f, 1f)
        val bottom = (rect.bottom.toFloat() / display.height()).coerceIn(0f, 1f)
        val region = if (left < right && top < bottom) Region(left, top, right, bottom) else null
        val labels = if (node.isPassword) emptyList() else listOfNotNull(node.text?.toString(), node.contentDescription?.toString(), node.hintText?.toString()).filter { it.isNotBlank() }.distinct()
        val launcherApp = if (node.packageName?.toString() == launcherPackage())
            InstalledApps.read(this).filter { app -> labels.any { folded(it) == folded(app.label) } }.singleOrNull()?.packageName else null
        return ScreenNode("${node.viewIdResourceName ?: node.className}@$path", labels.firstOrNull().orEmpty(), region,
            node.isClickable && node.isEnabled && node.isVisibleToUser, node.isSelected || node.isChecked, node.isPassword,
            node.className?.toString().orEmpty(), labels, node.isFocused, heading = node.isHeading, launchPackage = launcherApp)
    }
    private fun readTree(allowOwn: Boolean = false): ScreenState? {
        if (!DeviceVisibility.allowed(this)) return ScreenState("locked", emptyList(), complete = false, isLocked = true)
        if (VoiceActivity.activeSession) return ScreenState("voice_window", emptyList(), complete = false, readIssue = "voice_window_active")
        val available = windows
        val modal = available.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM && (it.isActive || it.isFocused) }
        val root = modal?.root ?: available.sortedByDescending { it.layer }
            .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }.firstOrNull() ?: return null
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName == this.packageName) return ScreenState(packageName, emptyList(), complete = false, readIssue = "own_setup")
        val display = wmBounds(); val nodes = mutableListOf<ScreenNode>(); val began = android.os.SystemClock.uptimeMillis()
        var complete = true; var examined = 0
        fun walk(node: AccessibilityNodeInfo, path: String, depth: Int) {
            if (depth > 96 || nodes.size >= 350 || ++examined > 2500 || android.os.SystemClock.uptimeMillis() - began > 1200) { complete = false; return }
            if (!node.isVisibleToUser) return
            val value = readNode(node, display, path)
            if (value.password || value.labels.isNotEmpty() || value.clickable || value.className.contains("EditText")) nodes += value
            if (!value.password) for (index in 0 until node.childCount) {
                if (!complete) break
                node.getChild(index)?.let { walk(it, "$path.$index", depth + 1) }
            }
        }
        walk(root, "0", 0)
        synchronized(this) { if (sourceWindow != root.windowId) { sourceWindow = root.windowId; sourceGeneration++ } }
        val result = ScreenState(packageName, nodes, complete && nodes.isNotEmpty(), packageName == launcherPackage(), !DeviceVisibility.allowed(this),
            windowId = root.windowId, navigationGeneration = sourceGeneration, readIssue = if (modal != null) "system_dialog" else if (!complete) "cluttered" else if (nodes.isEmpty()) "unlabeled" else null)
        GuideTrace.event(this, "screen_read", android.os.SystemClock.uptimeMillis() - began, ScreenReadPolicy.verdict(result).name)
        return result
    }
    @android.annotation.SuppressLint("NewApi") // Window capture is guarded by SDK >= 34 below.
    private suspend fun captureForSam(expected: ScreenState): Bitmap? {
        val before = readFresh() ?: return null
        if (!safe(before) || before.planningFingerprint != expected.planningFingerprint || !prefs.screenConsent) return null
        var overlaySuspended = false
        return try {
            val fresh = readFresh() ?: return null
            if (!safe(fresh) || fresh.planningFingerprint != expected.planningFingerprint) return null
            val targetWindow = windows.firstOrNull { it.id == expected.windowId } ?: return null
            val targetBounds = Rect().also { targetWindow.getBoundsInScreen(it) }
            val displayBounds = wmBounds()
            val preferWindow = android.os.Build.VERSION.SDK_INT >= 34
            suspend fun attempt(useWindow: Boolean): Bitmap? = suspendCancellableCoroutine { continuation ->
                val callback = object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = if (DeviceVisibility.allowed(this@GuidingHandService)) Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { raw ->
                            val copy = raw.copy(Bitmap.Config.ARGB_8888, false); raw.recycle()
                            if (useWindow && copy.width == displayBounds.width() && copy.height == displayBounds.height()) copy
                            else if ((useWindow && copy.width == targetBounds.width() && copy.height == targetBounds.height()) ||
                                (!useWindow && copy.width == displayBounds.width() && copy.height == displayBounds.height())) {
                                val frame = Bitmap.createBitmap(displayBounds.width(), displayBounds.height(), Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(frame); canvas.drawColor(android.graphics.Color.BLACK)
                                val source = if (useWindow) Rect(0, 0, copy.width, copy.height) else targetBounds
                                canvas.drawBitmap(copy, source, targetBounds, null); copy.recycle(); frame
                            } else {
                                GuideTrace.event(this@GuidingHandService, "capture_rejected", code = "window_dimensions")
                                copy.recycle(); null
                            }
                        } else null
                        buffer.close()
                        if (bitmap != null) GuideTrace.event(
                            this@GuidingHandService,
                            if (useWindow) "capture_window" else "capture_display",
                            code = "success"
                        )
                        if (continuation.isActive) continuation.resume(bitmap) else bitmap?.recycle()
                    }
                    override fun onFailure(errorCode: Int) {
                        GuideTrace.event(this@GuidingHandService, if (useWindow) "capture_failed_window" else "capture_failed_display", code = "android_$errorCode")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                if (useWindow) takeScreenshotOfWindow(expected.windowId, mainExecutor, callback)
                else takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
            }
            // Window capture excludes GuidingHand's overlay, so keep progress visible. Some
            // Xiaomi builds return ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR for launcher/Chrome;
            // fall back once to a display capture with only our overlay briefly suspended.
            var captured = if (preferWindow) attempt(true) else null
            if (captured == null) {
                overlay.suspendForCapture(); overlaySuspended = true
                delay(100)
                val stable = readFresh()
                if (stable == null || !safe(stable) || stable.planningFingerprint != expected.planningFingerprint) return null
                if (windows.any { window ->
                        window.id != expected.windowId && window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                            (window.isActive || window.isFocused) &&
                            Rect.intersects(Rect().also { window.getBoundsInScreen(it) }, targetBounds)
                    }) return null
                captured = attempt(false)
            }
            captured
        } finally {
            // Restore progress after every exit path, including a stale frame or Android capture failure.
            if (overlaySuspended && DeviceVisibility.allowed(this) && !VoiceActivity.activeSession)
                overlay.render(viewState, engine.launcherStep != null)
        }
    }
    override fun onInterrupt() { if (::model.isInitialized) model.cancel(); if (::overlay.isInitialized) overlay.hide() }
    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        voicePermitUntil = 0L; voiceSubmission?.cancel(); voiceDraft.clear(); if (::copiedDraft.isInitialized) copiedDraft.clearIfOwned()
        if (::model.isInitialized) model.close(); if (::overlay.isInitialized) overlay.hide()
        instance = null; scope.cancel(); super.onDestroy()
    }
}
