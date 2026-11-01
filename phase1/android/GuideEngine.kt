package vn.guidinghand

enum class GuideStatus { IDLE, PLANNING, WAITING, VERIFYING, SENSITIVE, UNCONFIRMED, DONE, NEEDS_INPUT, MODEL_QUESTION, REVIEW }

/** Event driven: elapsed time never completes or cancels a step. */
class GuideEngine(
    private val model: GuideModel,
    private val preferences: PathStore,
    private val present: (ModelStep?, String, GuideStatus) -> Unit,
    private val vibrate: () -> Unit,
    private val uiText: () -> UiText = { UiText() }
) {
    var status = GuideStatus.IDLE; private set
    private var task: GuideTask? = null
    private var current: ModelStep? = null
    private var before: ScreenState? = null
    private var clicked = false
    private val history = mutableListOf<Pair<String, Long>>()
    private val finished = mutableSetOf<String>()
    private var latest: ScreenState? = null
    private var generation = 0L

    fun start(value: GuideTask, preserveContext: Boolean = false) {
        stop(); if (!preserveContext) model.resetContext(); task = value; status = GuideStatus.PLANNING
        present(null, uiText().thinking, status)
    }
    fun stop() {
        generation++
        model.silence(); status = GuideStatus.IDLE; task = null; current = null
        before = null; latest = null; clicked = false; history.clear(); finished.clear()
        present(null, "", status)
    }
    fun replay() {
        if (status == GuideStatus.WAITING) current?.let { model.speak(it.instruction) }
    }
    fun suspendForInput() {
        generation++; model.silence()
        if (status !in listOf(GuideStatus.WAITING, GuideStatus.VERIFYING)) status = GuideStatus.IDLE
    }
    fun reshow(screen: ScreenState, speak: Boolean = true): Boolean {
        val step = current ?: return false
        val baseline = before ?: return false
        val active = task ?: return false
        if (status !in listOf(GuideStatus.WAITING, GuideStatus.VERIFYING) || !screen.complete || SafetyPolicy.sensitive(screen)) return false
        val same = if (active.id == "assist") PlanningContext.compatible(baseline, screen) else baseline.fingerprint == screen.fingerprint
        if (!same) return false
        val node = PlanningContext.rebind(step.node, screen) ?: return false
        current = step.copy(node = node); status = GuideStatus.WAITING
        present(current, step.instruction, status)
        if (speak) model.speak(step.instruction)
        return true
    }
    fun cannotGuide(message: String) {
        generation++
        status = GuideStatus.UNCONFIRMED; current = null; model.silence()
        present(null, message, status)
    }
    fun onClick(node: ScreenNode?) {
        if (status != GuideStatus.WAITING || clicked) return
        val expected = current?.node ?: return
        // The source must be the actual highlighted node, not a nearby unrelated tap.
        if (node != null && node.id == expected.id && node.region == expected.region) {
            clicked = true; status = GuideStatus.VERIFYING
        }
    }
    suspend fun onFrame(frame: GuideFrame) {
        val active = task ?: return
        val screen = frame.screen
        if (screen.packageName == "vn.guidinghand") return
        // Always inspect safety before state diffs, planning, screenshot requests, or speech.
        if (SafetyPolicy.sensitive(screen)) {
            generation++ // Invalidates a suspended backend response before it can show another ring.
            if (status != GuideStatus.SENSITIVE) {
                model.silence(); current = null; before = null; clicked = false
                status = GuideStatus.SENSITIVE
                present(null, uiText().sensitive, status)
                model.speak(uiText().sensitive)
            }
            latest = screen; return
        }
        if (!screen.complete) { unconfirmed(); return }
        if (status == GuideStatus.DONE) return
        if (active.id == "screen" && before != null && screen.fingerprint != before!!.fingerprint) {
            generation++; current = null; clicked = false; model.silence(); status = GuideStatus.NEEDS_INPUT
            present(null, uiText().pick("Screen changed. What next?", "Màn hình đã đổi. Cần giúp gì tiếp?"), status)
            return // Observation only; no task success or learned path is claimed.
        }
        if (status == GuideStatus.NEEDS_INPUT) return
        if (screen.packageName != active.appPackage && screen.packageName !in active.allowedPackages && !(active.id == "wifi" && screen.packageName.startsWith("com.android.settings"))) {
            current = null; before = null; clicked = false; latest = null; model.silence()
            if (active.id == "assist") { unconfirmed(); return }
            status = GuideStatus.PLANNING; present(null, uiText().returnTask, status); return
        }
        if (status == GuideStatus.SENSITIVE) { status = GuideStatus.PLANNING; latest = null }
        if (if (active.id == "assist") latest?.let { PlanningContext.key(it) == PlanningContext.key(screen) } == true else screen.fingerprint == latest?.fingerprint) return
        latest = screen
        val step = current
        val baseline = before
        if (active.id in listOf("assist", "open") && baseline != null) model.observeTransition(baseline, screen, step, clicked)
        if ((clicked || step?.goal?.completion in listOf("scan", "app", "focus")) && step != null && baseline != null) {
            if (Verification.confirms(step, baseline, screen)) {
                finished += step.goal.id
                history += step.goal.id to System.currentTimeMillis()
                vibrate()
                if (step.goal.id == active.steps.last().id) {
                    preferences.savePath(active, history)
                    status = GuideStatus.DONE; current = null; model.silence()
                    present(null, uiText().done, status); model.speak(uiText().pick("All done.", "Xong.")); return
                }
            }
        }
        // A wrong tap replans from this new tree without clearing verified history.
        clicked = false; status = GuideStatus.PLANNING; model.silence()
        val request = ++generation
        val next = try {
            model.getNextStep(frame, active.copy(steps = active.steps.filter { it.id !in finished }))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (request == generation) unconfirmed(); return }
        if (request != generation || task != active || status == GuideStatus.SENSITIVE) return
        if (next == null) {
            val feedback = model.feedback()
            if (active.id == "assist" && feedback?.question == "screen_changed") {
                current = null; latest = null; status = GuideStatus.PLANNING
                present(null, uiText().thinking, status); return
            }
            if (active.id == "assist" && feedback != null) {
                current = null; model.silence()
                status = if (feedback.review) GuideStatus.REVIEW else GuideStatus.MODEL_QUESTION
                present(null, if (feedback.review) uiText().pick("Does this look finished?", "Việc này đã xong chưa?")
                    else GeneralGuidePolicy.question(feedback.question, uiText()), status)
            } else unconfirmed()
            return
        }
        // Familiar paths keep the same verified targets and use a shorter Vietnamese sentence.
        current = if (preferences.familiar(active)) next.copy(instruction = next.instruction
            .replace("ở phía ", "phía ").replace("ở giữa", "giữa").replace("ở dưới", "dưới").replace("ở trên", "trên")
            .replace(" near the ", " ").replace(" at the ", " ")) else next
        before = screen; status = GuideStatus.WAITING
        present(current, current!!.instruction, status); vibrate(); model.speak(current!!.instruction)
    }
    private fun unconfirmed() {
        status = GuideStatus.UNCONFIRMED; current = null; model.silence()
        present(null, uiText().unknown, status)
        model.speak(uiText().unknown)
    }
}
