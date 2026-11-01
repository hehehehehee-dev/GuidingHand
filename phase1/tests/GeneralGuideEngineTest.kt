package vn.guidinghand

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GeneralGuideEngineTest {
    private val key = ScreenNode("2", "2", Region(.2f, .5f, .3f, .6f), true)
    private fun screen(value: String) = ScreenState("test.calculator", listOf(key, ScreenNode("value", value, null, false)))
    private class Paths : PathStore {
        var saved = 0
        override fun familiar(task: GuideTask) = false
        override fun savePath(task: GuideTask, steps: List<Pair<String, Long>>) { saved++ }
    }
    private inner class Model : GuideModel {
        var calls = 0; var report: ModelFeedback? = null; val taps = mutableListOf<Boolean>()
        override suspend fun getNextStep(screenshot: GuideFrame, goal: GuideTask): ModelStep? {
            calls++
            return if (report != null) null else ModelStep(goal.steps.first(), key, "Tap the highlighted control.")
        }
        override fun feedback() = report
        override fun observeTransition(before: ScreenState, after: ScreenState, step: ModelStep?, matchedTap: Boolean) { taps += matchedTap }
        override fun speak(text: String) {}; override fun silence() {}; override fun close() {}
    }
    @Test fun newScreenAfterTapReplansWithoutDeclaringGoalDone() = runBlocking {
        val model = Model(); val paths = Paths(); val engine = GuideEngine(model, paths, { _, _, _ -> }, {})
        engine.start(GeneralGuidePolicy.task(screen("0"), "Calculate 24 times 7"))
        engine.onFrame(GuideFrame(screen("0"), null)); engine.onClick(key)
        engine.onFrame(GuideFrame(screen("2"), null))
        assertEquals(2, model.calls); assertEquals(listOf(true), model.taps)
        assertEquals(GuideStatus.WAITING, engine.status); assertEquals(0, paths.saved)
    }
    @Test fun wrongTapBecomesObservationRatherThanVerifiedProgress() = runBlocking {
        val model = Model(); val engine = GuideEngine(model, Paths(), { _, _, _ -> }, {})
        engine.start(GeneralGuidePolicy.task(screen("0"), "Calculate 24 times 7"))
        engine.onFrame(GuideFrame(screen("0"), null)); engine.onClick(key.copy(id = "wrong"))
        engine.onFrame(GuideFrame(screen("9"), null))
        assertEquals(listOf(false), model.taps); assertEquals(2, model.calls)
    }
    @Test fun modelReviewNeedsUserConfirmationAndSavesNoSuccess() = runBlocking {
        val model = Model().apply { report = ModelFeedback(true) }; val paths = Paths()
        val engine = GuideEngine(model, paths, { _, _, _ -> }, {})
        engine.start(GeneralGuidePolicy.task(screen("168"), "Calculate 24 times 7"))
        engine.onFrame(GuideFrame(screen("168"), null))
        assertEquals(GuideStatus.REVIEW, engine.status); assertEquals(0, paths.saved)
    }
    @Test fun clarificationHasItsOwnStatusAndSensitiveScreenStillWins() = runBlocking {
        val model = Model().apply { report = ModelFeedback(false, "recipient") }
        val engine = GuideEngine(model, Paths(), { _, _, _ -> }, {})
        engine.start(GeneralGuidePolicy.task(screen("0"), "Find a person"))
        engine.onFrame(GuideFrame(screen("0"), null)); assertEquals(GuideStatus.MODEL_QUESTION, engine.status)
        engine.onFrame(GuideFrame(screen("Password"), null)); assertEquals(GuideStatus.SENSITIVE, engine.status)
    }
    @Test fun allowedAppSwitchKeepsTheGoalAndObservesTheNewScreen() = runBlocking {
        val model = Model(); val paths = Paths(); val engine = GuideEngine(model, paths, { _, _, _ -> }, {})
        engine.start(GeneralGuidePolicy.task(screen("0"), "Watch YouTube", setOf("test.youtube")))
        engine.onFrame(GuideFrame(screen("0"), null))
        engine.onFrame(GuideFrame(screen("Videos").copy(packageName = "test.youtube"), null))
        assertEquals(2, model.calls); assertEquals(GuideStatus.WAITING, engine.status); assertEquals(0, paths.saved)
        engine.onFrame(GuideFrame(screen("Unknown").copy(packageName = "unknown.system.dialog"), null))
        assertEquals(GuideStatus.UNCONFIRMED, engine.status); assertEquals(2, model.calls)
    }
}
