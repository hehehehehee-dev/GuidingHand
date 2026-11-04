package vn.guidinghand

import kotlinx.coroutines.CancellationException

/** One task session; only Stop/new goal clears verified progress. Models never perform UI actions. */
internal const val VERIFY_TIMEOUT_MS = 15_000L

/**
 * Monotonic clock that stays unit-test safe. Production reads the Android
 * monotonic clock; plain JVM tests fall back to wall time (only used to show
 * the timeout never fires within a fast test, never as evidence).
 */
internal fun monotimeMs(): Long =
    try { android.os.SystemClock.elapsedRealtime() } catch (_: RuntimeException) { System.currentTimeMillis() }

/** True once a tap has waited past the deadline with no meaningful screen change. */
internal fun shouldConcludeVerify(verifyStartElapsed: Long?, nowElapsed: Long): Boolean {
    if (verifyStartElapsed == null) return false
    return nowElapsed - verifyStartElapsed > VERIFY_TIMEOUT_MS
}

class GuideEngine(
    private val model: GuideModel, private val present: (GuideViewState) -> Unit,
    private val family: FamilyContext = FamilyContext(), private val allowDevStandIn: Boolean = false,
    private var language: RespectfulLanguage = RespectfulLanguage(), private var locale: String = "vi",
    private val onTransition: (GuideTransition) -> Unit = {},
    private val nowElapsed: () -> Long = { monotimeMs() }
) {
    var status = GuideStatus.IDLE; private set
    var task: PhotoTask? = null; private set
    val hasGoal: Boolean get() = goal.isNotBlank()
    val acceptsSpokenRefinement: Boolean get() = hasGoal && status !in setOf(GuideStatus.IDLE, GuideStatus.DONE, GuideStatus.SAFETY_PAUSED)
    val verifiedStepIds: Set<String> get() = verified.toSet()
    val launcherStep: GuidanceStep? get() = current?.takeIf {
        status == GuideStatus.WAITING_TAP && it.stage == PhotoStage.LAUNCHER && baseline?.isLauncher == true &&
            !it.target.launcherFolder && !it.target.className.contains("EditText") &&
            !it.target.className.contains("AutoCompleteTextView") && it.target.launchPackage == task?.appPackage && task?.valid == true
    }
    private var goal = ""
    private val answers = mutableListOf<String>()
    private val dialogue = mutableListOf<ClarificationTurn>()
    private val userUpdates = mutableListOf<String>()
    private val verified = linkedSetOf<String>()
    // Current facts authorize the next action; they are not claimed as performed/verified transitions.
    private var recipientContext = false
    private var photoContext = false
    private val observations = ArrayDeque<GuideObservation>()
    private var current: GuidanceStep? = null
    private var baseline: ScreenState? = null
    private var sendBaseline: ScreenState? = null
    private var conversationBeforeAttachment: ScreenState? = null
    private var attachmentInProgress = false
    private var selectedThumbnail: ScreenNode? = null
    private var latest: ScreenState? = null
    private var lastPlanned: String? = null
    private var clicked = false
    private var tapObserved = false
    private var verifyStartElapsed: Long? = null
    private var awaitingAnswer = false
    private var generation = 0L
    private var staleResponses = 0
    private var transitionSequence = 0L
    private val transitions = ArrayDeque<GuideTransition>()
    val transitionHistory: List<GuideTransition> get() = transitions.toList()
    private var utteranceRevision = 0L
    private var boundUtteranceRevision = 0L
    private val canUpdateGeneralTask get() = task?.id == "guide_app" && utteranceRevision > boundUtteranceRevision
    private fun bindTask(next: PhotoTask): Boolean {
        if (!next.valid) return false
        if (task?.acceptsRefinement(next) == false) {
            if (!canUpdateGeneralTask) return false
            // A new spoken request may extend a general goal. A changing screen alone never can.
            verified.clear(); observations.clear(); recipientContext = false; photoContext = false
            sendBaseline = null; conversationBeforeAttachment = null; attachmentInProgress = false; selectedThumbnail = null
        }
        task = next; boundUtteranceRevision = utteranceRevision
        return true
    }
    private var rawView = GuideViewState(GuideStatus.IDLE, "")
    private fun protectedLabels() = listOf(family.recipient, family.photo, task?.recipient.orEmpty(), task?.photo.orEmpty(), task?.content.orEmpty(), "Zalo")
    fun updateLanguage(value: RespectfulLanguage, locale: String = this.locale) {
        val changed = this.locale != locale
        language = value; this.locale = if (locale == "en") "en" else "vi"
        if (changed && hasGoal && status !in setOf(GuideStatus.SAFETY_PAUSED, GuideStatus.DONE, GuideStatus.UNSUPPORTED)) {
            generation++; model.cancel(); current = null; baseline = null; awaitingAnswer = false; lastPlanned = null
            emit(GuideStatus.READING, "Đang đọc lại màn hình…"); return
        }
        emit(rawView.status, rawView.instructionVi, rawView.step, rawView.options,
            rawView.reasonCode, rawView.speechPurpose)
    }

    private fun wording(text: String): String = if (locale == "en") english[text] ?: text
        else language.message(text, protectedLabels())
    private fun wording(step: GuidanceStep) = step.copy(instructionVi = wording(step.instructionVi))
    private val english = mapOf(
        "Ông bà muốn làm việc gì?" to "What would you like help with?",
        "Cần xác nhận người nhận và ảnh muốn gửi." to "Who should receive the photo, and which photo would you like to share?",
        "Đang đọc màn hình…" to "Reading this screen…",
        "Đang đọc lại màn hình…" to "Reading the current screen again…",
        "Màn hình đang đổi liên tục. Bác chờ nội dung đứng yên rồi chọn Kiểm tra lại nhé." to "The screen keeps changing. Let it settle, then choose Check again.",
        "Màn hình đang đổi. Tôi sẽ tự chờ rồi hướng dẫn tiếp." to "The screen is changing. I will wait and continue automatically.",
        "Đang kiểm tra lại màn hình…" to "Checking the screen again…",
        "Đã tạm dừng để bảo vệ thông tin. Ông bà có thể chọn Gọi người thân hoặc Dừng." to "Guidance is paused to protect private information. Choose Call family or Stop.",
        "Chưa đọc rõ màn hình. Kiểm tra lại rồi chọn Tiếp tục nhé." to "I cannot read this screen clearly. Check the screen, then choose Continue.",
        "Đang kiểm tra bước vừa làm…" to "Checking the step you just took…",
        "Đã xác minh ảnh được gửi. Xong rồi." to "The photo was sent to the verified recipient.",
        "Mình sẽ hướng dẫn tiếp từ màn hình này." to "I will continue from the screen you are on.",
        "Đang tìm bước tiếp theo…" to "Finding the next step…",
        "Chưa kết nối được trợ lý. Chọn Tiếp tục để thử lại." to "The assistant is not connected. Choose Continue to try again.",
        "Màn hình đã đổi. Đang đọc lại để hướng dẫn tiếp…" to "The screen changed. Reading the new screen to continue…",
        "Cần xác nhận đúng người nhận và ảnh trước khi tiếp tục." to "Please confirm the recipient and photo before continuing.",
        "Tôi chưa hiểu rõ việc cần làm tiếp. Bác nói lại giúp tôi nhé." to "I am not sure what you want to do next. Please tell me again.",
        "Chưa có chỉ dẫn rõ ràng. Vui lòng nói rõ việc cần giúp." to "I do not have a clear step yet. Please tell me what you need help with.",
        "Chưa đủ bằng chứng xác minh ảnh đã gửi. Kiểm tra lại giúp mình nhé." to "I cannot yet verify a new photo was sent. Please check the conversation.",
        "Chưa xác minh được vùng cần bấm. Bác đợi tôi một chút rồi chọn Kiểm tra lại nhé." to "I cannot verify the button yet. Please wait a moment, then choose Check again.",
        "Chưa phát được giọng hướng dẫn. Mình dừng lại, không đổi sang giọng khác." to "The offline guide voice is unavailable. Guidance is paused.",
        "Chưa phát được giọng hướng dẫn. Nhờ người thân kiểm tra giọng đọc ngoại tuyến đã cài nhé." to "The offline guide voice is unavailable. Please check the installed voice before continuing."
    )

    private fun emit(next: GuideStatus, instruction: String, step: GuidanceStep? = null, options: List<String> = emptyList(),
                     reasonCode: String? = null, speechPurpose: SpeechPurpose = SpeechPurpose.GENERAL,
                     navigationCue: NavigationCue = NavigationCue.NONE) {
        val previous = status
        if (previous != next || reasonCode != null) {
            val transition = GuideTransition(++transitionSequence, previous, next, reasonCode, step?.stage, task?.id)
            transitions += transition
            while (transitions.size > 64) transitions.removeFirst()
            onTransition(transition)
        }
        status = next; rawView = GuideViewState(next, instruction, step, options, reasonCode, speechPurpose,
            navigationCue = navigationCue)
        present(rawView.copy(instructionVi = wording(instruction), step = step?.let(::wording),
            options = options.map(::wording)))
    }
    fun startGoal(goal: String, task: PhotoTask? = null) {
        stop(); this.goal = goal.trim().take(1000); this.task = task
        if (this.goal.isBlank()) { ask("Ông bà muốn làm việc gì?"); return }
        if (task != null && !task.valid) { ask("Cần xác nhận người nhận và ảnh muốn gửi."); return }
        emit(GuideStatus.READING, "Đang đọc màn hình…")
    }
    fun stop() {
        generation++; staleResponses = 0; model.cancel(); model.clearSessionMemory(); goal = ""; task = null; answers.clear(); dialogue.clear(); userUpdates.clear(); verified.clear(); observations.clear()
        current = null; baseline = null; sendBaseline = null; latest = null; lastPlanned = null; clicked = false; tapObserved = false; awaitingAnswer = false
        recipientContext = false; photoContext = false
        utteranceRevision = 0; boundUtteranceRevision = 0
        conversationBeforeAttachment = null; attachmentInProgress = false; selectedThumbnail = null
        transitions.clear(); transitionSequence = 0
        emit(GuideStatus.IDLE, "")
    }
    fun answer(text: String) {
        if (goal.isBlank() || text.isBlank() || status == GuideStatus.SAFETY_PAUSED) return
        utteranceRevision++
        if (status !in setOf(GuideStatus.CLARIFYING, GuideStatus.UNSUPPORTED)) {
            userUpdates += text.trim().take(200)
            while (userUpdates.size > 5) userUpdates.removeAt(0)
        }
        if (status in setOf(GuideStatus.CLARIFYING, GuideStatus.ASKING, GuideStatus.UNSUPPORTED) && rawView.instructionVi.isNotBlank()) {
            dialogue += ClarificationTurn(rawView.instructionVi.take(200), text.trim().take(200))
            while (dialogue.size > 8) dialogue.removeAt(0)
        }
        generation++; staleResponses = 0; model.cancel(); answers += text.trim().take(300)
        while (answers.size > 5) answers.removeAt(0)
        current = null; baseline = null; clicked = false; tapObserved = false; awaitingAnswer = false; lastPlanned = null
        emit(GuideStatus.READING, "Đang đọc lại màn hình…")
    }
    /** Retry applies to unreadable/unavailable screens. A hard safety pause ends only with Stop/new goal. */
    fun resume() {
        if (goal.isBlank() || status !in setOf(GuideStatus.ASKING, GuideStatus.UNAVAILABLE)) return
        generation++; staleResponses = 0; model.cancel(); current = null; baseline = null; sendBaseline = null; clicked = false; tapObserved = false
        recipientContext = false; photoContext = false
        conversationBeforeAttachment = null; attachmentInProgress = false; selectedThumbnail = null
        awaitingAnswer = false; lastPlanned = null
        emit(GuideStatus.READING, "Đang kiểm tra lại màn hình…")
    }
    fun pauseForSensitive(messageVi: String? = null) {
        // Accessibility can repeat the same protected-screen event. The first pause already
        // cancelled work and cleared the ring; do not regenerate overlays/events in a loop.
        if (status == GuideStatus.SAFETY_PAUSED) return
        generation++; model.cancel(); model.clearSessionMemory(); current = null; baseline = null; clicked = false; tapObserved = false; awaitingAnswer = true
        recipientContext = false; photoContext = false; sendBaseline = null
        conversationBeforeAttachment = null; attachmentInProgress = false; selectedThumbnail = null
        val message = messageVi?.takeIf { it.isNotBlank() && it.length <= 200 && !it.contains('\n') }
            ?: "Đã tạm dừng để bảo vệ thông tin. Ông bà có thể chọn Gọi người thân hoặc Dừng."
        emit(GuideStatus.SAFETY_PAUSED, message, reasonCode = "safety_gate")
    }
    private fun ask(message: String, reasonCode: String? = null) {
        current = null; baseline = null; clicked = false; tapObserved = false; awaitingAnswer = true
        emit(GuideStatus.ASKING, message, reasonCode = reasonCode)
    }
    private fun askAboutTask(candidate: PhotoTask? = null) {
        val photo = task?.isPhotoTask == true || candidate?.isPhotoTask == true
        ask(if (photo) "Cần xác nhận đúng người nhận và ảnh trước khi tiếp tục."
            else "Tôi chưa hiểu rõ việc cần làm tiếp. Bác nói lại giúp tôi nhé.", "task_refinement")
    }
    private suspend fun sayMessage(text: String, screen: ScreenState) {
        if (!gate(screen)) return
        try { model.speakMessage(wording(text), screen) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the visible message available when an offline voice is unavailable. */ }
    }
    private fun gate(screen: ScreenState): Boolean {
        val verdict = ScreenReadPolicy.verdict(screen)
        if (verdict in setOf(ScreenReadVerdict.LOCKED, ScreenReadVerdict.SENSITIVE) ||
            SafetyPolicy.sensitiveGoal(goal) || answers.any(SafetyPolicy::sensitiveGoal)) {
            pauseForSensitive()
            return false
        }
        // A missing/incomplete frame must never turn a previously latched safety pause into ASKING.
        if (status == GuideStatus.SAFETY_PAUSED) return false
        if (verdict == ScreenReadVerdict.CANNOT_READ) {
            generation++; model.cancel(); ask(generalUnreadableGuidance(screen), "cannot_read")
            return false
        }
        return true
    }
    private fun generalUnreadableGuidance(screen: ScreenState): String {
        val active = task
        if (active?.isPhotoTask == true && recipientContext && attachmentInProgress &&
            screen.packageName in setOf(active.appPackage, "com.google.android.providers.media.module",
                "com.android.providers.media.module", "com.google.android.documentsui", "com.android.documentsui"))
            return "Nếu đang xem thư viện ảnh, Bác chọn ảnh muốn gửi rồi chọn Kiểm tra lại nhé. Tôi sẽ kiểm tra trước khi gửi."
        return "Tôi chưa đọc rõ màn hình này. Bác đợi một chút rồi chọn Kiểm tra lại nhé."
    }
    fun inspectBeforeSpeech(screen: ScreenState): Boolean = gate(screen)
    fun onTap(node: ScreenNode?) {
        if (status != GuideStatus.WAITING_TAP) return
        val step = current ?: return
        generation++; model.cancel() // Stop old speech on either a correct tap or a wrong tap.
        tapObserved = node != null
        clicked = node != null && node.id == step.target.id && node.region == step.target.region
        if (clicked) {
            verifyStartElapsed = nowElapsed()
            if (task?.isPhotoTask == true && step.stage == PhotoStage.ATTACH) {
                val active = task; val before = baseline
                if (active != null && before != null && PhotoVerification.conversation(before, active) &&
                    !PhotoVerification.selectedPhoto(before, active) && !PhotoVerification.photoPreview(before, active) && !PhotoVerification.gallery(before))
                    conversationBeforeAttachment = before
                attachmentInProgress = true
            }
            if (task?.isPhotoTask == true && step.stage == PhotoStage.SEND) sendBaseline = baseline
            emit(GuideStatus.VERIFYING, "Đang kiểm tra bước vừa làm…")
        }
    }
    private fun record(stage: PhotoStage) { if (stage != PhotoStage.RECOVER) verified += stage.name.lowercase() }
    private fun observeContext(screen: ScreenState, task: PhotoTask) {
        val systemPicker = screen.packageName in setOf("com.google.android.providers.media.module", "com.android.providers.media.module", "com.google.android.documentsui", "com.android.documentsui")
        if ((PhotoVerification.hasComposer(screen) && !PhotoVerification.conversation(screen, task)) ||
            (screen.packageName != task.appPackage && !systemPicker)) {
            recipientContext = false; photoContext = false; sendBaseline = null
            conversationBeforeAttachment = null; attachmentInProgress = false; selectedThumbnail = null
        }
        val selected = PhotoVerification.selectedPhoto(screen, task)
        val preview = PhotoVerification.photoPreview(screen, task)
        val gallery = PhotoVerification.gallery(screen)
        if (PhotoVerification.conversation(screen, task)) {
            recipientContext = true
            if (!attachmentInProgress && !selected && !preview && !gallery) conversationBeforeAttachment = screen
        }
        if (gallery || selected || preview) attachmentInProgress = true
        if (recipientContext && (selected || preview)) photoContext = true
    }
    private fun verifiedTransition(step: GuidanceStep, before: ScreenState, after: ScreenState, task: PhotoTask): Boolean {
        if (before.fingerprint == after.fingerprint) return false
        return when (step.stage) {
            PhotoStage.LAUNCHER -> after.packageName == task.appPackage && before.packageName != task.appPackage
            PhotoStage.RECIPIENT -> clicked && PhotoVerification.conversation(after, task)
            PhotoStage.ATTACH -> clicked && PhotoVerification.gallery(after)
            PhotoStage.PHOTO -> clicked && ((PhotoVerification.selectedPhoto(after, task) && !PhotoVerification.selectedPhoto(before, task)) ||
                PhotoVerification.selectedTarget(before, after, step.target)).also { selected ->
                    if (selected && recipientContext && attachmentInProgress) { selectedThumbnail = step.target; photoContext = true }
                }
            PhotoStage.SEND, PhotoStage.DELIVERY -> clicked && conversationBeforeAttachment?.let {
                PhotoVerification.delivered(it, after, task) && PhotoVerification.delivered(before, after, task)
            } == true
            PhotoStage.RECOVER, PhotoStage.ACTION -> false
        }
    }
    private fun allowedStage(step: GuidanceStep, screen: ScreenState, task: PhotoTask): Boolean = when (step.stage) {
        PhotoStage.LAUNCHER -> screen.isLauncher
        PhotoStage.RECIPIENT -> screen.packageName == task.appPackage
        PhotoStage.ATTACH -> PhotoVerification.conversation(screen, task)
        PhotoStage.PHOTO -> recipientContext && PhotoVerification.gallery(screen)
        PhotoStage.SEND -> task.ready && step.target.labels.any { folded(it) in setOf("send", "gui", "gui anh", "send photo") } &&
            screen.packageName == task.appPackage && recipientContext && photoContext && conversationBeforeAttachment?.let { PhotoVerification.conversation(it, task) } == true && attachmentInProgress &&
            (PhotoVerification.recipientTitle(screen, task) || PhotoVerification.gallery(screen)) &&
            (PhotoVerification.selectedPhoto(screen, task) || PhotoVerification.photoPreview(screen, task) || PhotoVerification.hasSelectedTarget(screen, selectedThumbnail))
        PhotoStage.DELIVERY -> false // Waiting for delivery has no fabricated tappable region.
        // The core planner may point out a real Home/Back/search affordance in another app.
        // The same native safety gate and actual-node/SAM validation still apply.
        PhotoStage.RECOVER -> ScreenReadPolicy.verdict(screen) == ScreenReadVerdict.SAFE
        PhotoStage.ACTION -> !task.isPhotoTask && (screen.packageName == task.appPackage || screen.packageName in setOf(
            "com.google.android.providers.media.module", "com.android.providers.media.module", "com.google.android.documentsui", "com.android.documentsui"))
    }
    private var stepRejection = "invalid_step"
    private fun validateStep(step: GuidanceStep, screen: ScreenState, task: PhotoTask): Boolean {
        stepRejection = "target_not_in_frame"
        val actual = screen.nodes.singleOrNull { it.id == step.target.id && it.region == step.target.region && it == step.target } ?: return false
        val region = actual.region ?: return false
        val outsideTask = Regex("(^|[^a-z0-9])(call|dial|delete|remove|erase|buy|purchase|pay|install|uninstall|confirm|allow|accept|goi|xoa|mua|thanh toan|xac nhan|cho phep|dong y)([^a-z0-9]|$)")
        val sending = actual.labels.any { folded(it) in setOf("send", "gui", "gui anh", "send photo") }
        val photoRules = !task.isPhotoTask || (actual.labels.none { outsideTask.containsMatchIn(folded(it)) } && (!sending || step.stage == PhotoStage.SEND))
        val generalStage = task.isPhotoTask || step.stage in setOf(PhotoStage.LAUNCHER, PhotoStage.RECOVER, PhotoStage.ACTION)
        val generalRecipient = task.isPhotoTask || !sending || (task.recipient.isNotBlank() && PhotoVerification.recipientTitle(screen, task))
        val generalContent = task.isPhotoTask || !sending || PhotoVerification.composerContains(screen, task)
        val boundedSam = step.sam.source == "sam-3.1-ui-bounded" && !model.developmentStandIn &&
            step.sam.rawRegion != null && step.sam.region == region && run {
                val raw = step.sam.rawRegion
                val overlapWidth = (minOf(raw.right, region.right) - maxOf(raw.left, region.left)).coerceAtLeast(0f)
                val overlapHeight = (minOf(raw.bottom, region.bottom) - maxOf(raw.top, region.top)).coerceAtLeast(0f)
                val overlap = overlapWidth * overlapHeight
                val rawArea = (raw.right - raw.left) * (raw.bottom - raw.top)
                val uiArea = (region.right - region.left) * (region.bottom - region.top)
                overlap * 2 >= minOf(rawArea, uiArea)
            }
        val samProvenance = step.sam.source == "sam-3.1" || boundedSam ||
            (allowDevStandIn && model.developmentStandIn && step.sam.source == "phase1-mock")
        stepRejection = when {
            !actual.clickable || actual.password -> "target_not_actionable"
            !photoRules -> "photo_action_guard"
            !generalStage -> "general_stage"
            !generalRecipient -> "recipient_title"
            !generalContent -> "message_content"
            !region.containsCenter(step.sam.region) -> "sam_node_mismatch"
            !allowedStage(step, screen, task) -> "stage_context"
            else -> "invalid_step"
        }
        return actual.clickable && !actual.password && photoRules && generalStage && generalRecipient && generalContent && step.instructionVi.isNotBlank() && step.instructionVi.length <= 200 &&
            !step.instructionVi.contains('\n') && samProvenance && step.sam.frameFingerprint == screen.planningFingerprint &&
            region.containsCenter(step.sam.region) && step.voice.provider == "Android system TTS" &&
            step.voice.rate == .85f && allowedStage(step, screen, task)
    }
    suspend fun onScreen(screen: ScreenState) {
        latest = screen
        if (goal.isBlank() || status in setOf(GuideStatus.IDLE, GuideStatus.DONE, GuideStatus.UNSUPPORTED)) return
        if (!gate(screen)) return
        if (status == GuideStatus.SAFETY_PAUSED) return
        val active = task
        if (active?.isPhotoTask == true) {
            observeContext(screen, active)
            val sentBefore = sendBaseline
            val conversationBefore = conversationBeforeAttachment
            if (sentBefore != null && conversationBefore != null && recipientContext && photoContext &&
                PhotoVerification.delivered(conversationBefore, screen, active) && PhotoVerification.delivered(sentBefore, screen, active)) {
                record(PhotoStage.SEND); record(PhotoStage.DELIVERY); generation++; model.cancel(); current = null
                emit(GuideStatus.DONE, "Đã xác minh ảnh được gửi. Xong rồi.")
                sayMessage(rawView.instructionVi, screen); return
            }
        }
        if (awaitingAnswer) return
        if (status == GuideStatus.VERIFYING && shouldConcludeVerify(verifyStartElapsed, nowElapsed())) {
            verifyStartElapsed = null
            ask("Tôi chưa thấy màn hình đổi sau khi Bác bấm. Bác thử lại thao tác đó hoặc chọn Kiểm tra lại nhé.", "verify_timeout")
            return
        }
        if (status == GuideStatus.WAITING_NAVIGATION) {
            if (baseline?.fingerprint == screen.fingerprint) return
            observations += GuideObservation("user-navigation", false, true)
            while (observations.size > 5) observations.removeFirst()
            baseline = null
        }
        val old = current; val before = baseline
        val targetStillCurrent = old?.let { step -> screen.nodes.any { node -> node.id == step.target.id &&
            node.region == step.target.region && node.className == step.target.className && node.labels == step.target.labels &&
            node.clickable == step.target.clickable && node.selected == step.target.selected && node.password == step.target.password } } == true
        val meaningfulStepChange = old != null && before != null &&
            (screen.planningFingerprint != before.planningFingerprint || !targetStillCurrent ||
                (tapObserved && screen.fingerprint != before.fingerprint))
        if (meaningfulStepChange) {
            val matchedGeneralAction = active?.isPhotoTask == false && clicked &&
                old.stage in setOf(PhotoStage.ACTION, PhotoStage.RECOVER)
            val success = active != null && (if (active.isPhotoTask) verifiedTransition(old, before, screen, active) else
                old.stage == PhotoStage.LAUNCHER && before.packageName != active.appPackage && screen.packageName == active.appPackage)
            if (success) record(old.stage)
            observations += GuideObservation(old.target.id.take(120), clicked, true,
                old.instructionVi.take(180), old.target.labels.firstOrNull().orEmpty().take(120), before.packageName.take(160))
            while (observations.size > 5) observations.removeFirst()
            current = null; baseline = null
            // A matched general-app tap is interaction evidence, not completion proof. Replan
            // from the resulting screen without labelling the elder's correct tap as recovery.
            if (!success && !matchedGeneralAction) emit(GuideStatus.RECOVERING, "Mình sẽ hướng dẫn tiếp từ màn hình này.",
                reasonCode = if (tapObserved) "wrong_tap" else "unexpected_screen")
        } else if (old != null) return
        if (lastPlanned == screen.planningFingerprint && status == GuideStatus.PLANNING) return
        clicked = false; tapObserved = false
        val request = ++generation; lastPlanned = screen.planningFingerprint
        emit(GuideStatus.PLANNING, "Đang tìm bước tiếp theo…")
        val decision = try { model.next(GuideInput(screen, goal, answers.toList(), task, verified.toSet(), observations.toList(), family, dialogue.toList(), canUpdateGeneralTask, userUpdates.toList())) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (generation == request) { awaitingAnswer = true; emit(GuideStatus.UNAVAILABLE, "Chưa kết nối được trợ lý. Chọn Tiếp tục để thử lại.") }
            return
        }
        if (generation != request || latest?.planningFingerprint != screen.planningFingerprint || status == GuideStatus.SAFETY_PAUSED) return
        when (decision) {
            GuideDecision.StateChanged -> {
                generation++; model.cancel(); current = null; baseline = null; clicked = false; tapObserved = false
                staleResponses++
                awaitingAnswer = false; lastPlanned = null
                emit(GuideStatus.READING, "Màn hình đang đổi. Tôi sẽ tự chờ rồi hướng dẫn tiếp.", reasonCode = "screen_settling")
            }
            is GuideDecision.Clarify -> {
                awaitingAnswer = true; emit(GuideStatus.CLARIFYING, decision.questionVi,
                    options = decision.options.take(4), speechPurpose = decision.speechPurpose)
                if (generation == request) sayMessage(decision.questionVi, screen)
            }
            is GuideDecision.Unsupported -> { awaitingAnswer = true; emit(GuideStatus.UNSUPPORTED, decision.messageVi, options = decision.options.take(4)); sayMessage(decision.messageVi, screen) }
            is GuideDecision.Unavailable -> { awaitingAnswer = true; emit(GuideStatus.UNAVAILABLE, decision.messageVi); sayMessage(decision.messageVi, screen) }
            is GuideDecision.Sensitive -> {
                pauseForSensitive()
                // No model or TTS request after a provider-side sensitive verdict.
            }
            is GuideDecision.Await -> {
                val guidance = if (decision.reasonCode in setOf("snapshot_unavailable", "secure_window") &&
                    ScreenReadPolicy.verdict(screen) !in setOf(ScreenReadVerdict.SENSITIVE, ScreenReadVerdict.LOCKED))
                    if (decision.reasonCode == "secure_window" &&
                        !(task?.isPhotoTask == true && recipientContext && attachmentInProgress)) decision.messageVi
                    else generalUnreadableGuidance(screen) else decision.messageVi
                ask(guidance, decision.reasonCode); sayMessage(guidance, screen)
            }
            is GuideDecision.Navigate -> {
                val resolved = decision.task ?: task
                if (resolved != null && !bindTask(resolved)) {
                    askAboutTask(resolved); return
                }
                if (decision.messageVi.isBlank() || decision.messageVi.length > 200 || decision.messageVi.contains('\n')) {
                    ask("Chưa có chỉ dẫn rõ ràng. Vui lòng nói rõ việc cần giúp."); return
                }
                current = null; baseline = screen; clicked = false; tapObserved = false; awaitingAnswer = false
                emit(GuideStatus.WAITING_NAVIGATION, decision.messageVi, navigationCue = decision.navigationCue)
                if (generation == request) sayMessage(decision.messageVi, screen)
            }
            is GuideDecision.DoneProposal -> ask("Chưa đủ bằng chứng xác minh ảnh đã gửi. Kiểm tra lại giúp mình nhé.")
            is GuideDecision.Step -> {
                staleResponses = 0
                val resolved = decision.task ?: task
                if (resolved == null || !bindTask(resolved)) { askAboutTask(resolved); return }
                if (resolved.isPhotoTask) observeContext(screen, resolved)
                if (!validateStep(decision.step, screen, resolved)) { ask("Chưa xác minh được vùng cần bấm. Bác đợi tôi một chút rồi chọn Kiểm tra lại nhé.", stepRejection); return }
                current = decision.step; baseline = screen
                emit(GuideStatus.WAITING_TAP, decision.step.instructionVi, decision.step)
                if (generation == request && latest?.planningFingerprint == screen.planningFingerprint && gate(screen)) {
                    try { model.repeat(wording(decision.step)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (generation == request) { model.cancel(); current = null; awaitingAnswer = true; emit(GuideStatus.UNAVAILABLE, "Chưa phát được giọng hướng dẫn. Mình dừng lại, không đổi sang giọng khác.") }
                    }
                }
            }
        }
    }

    /** Keep the current goal while the service waits for a stable native screen. */
    fun waitForStableScreen() {
        if (!hasGoal || status == GuideStatus.SAFETY_PAUSED) return
        current = null; baseline = null; awaitingAnswer = false; lastPlanned = null
        val text = "Màn hình đang đổi. Tôi sẽ tự chờ rồi hướng dẫn tiếp."
        if (status != GuideStatus.READING || rawView.instructionVi != wording(text))
            emit(GuideStatus.READING, text, reasonCode = "screen_settling")
    }
    suspend fun repeat(screen: ScreenState) {
        latest = screen
        if (status in setOf(GuideStatus.CLARIFYING,GuideStatus.ASKING,GuideStatus.UNAVAILABLE,GuideStatus.UNSUPPORTED,GuideStatus.DONE,GuideStatus.WAITING_NAVIGATION)) {
            sayMessage(rawView.instructionVi, screen); return
        }
        if (!gate(screen) || status != GuideStatus.WAITING_TAP) return
        val step = current ?: return
        if (step.sam.frameFingerprint != screen.planningFingerprint) { onScreen(screen); return }
        try { model.repeat(wording(step)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { model.cancel(); ask("Chưa phát được giọng hướng dẫn. Nhờ người thân kiểm tra giọng đọc ngoại tuyến đã cài nhé.") }
    }
}
