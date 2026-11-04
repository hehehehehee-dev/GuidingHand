package vn.guidinghand

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GuideTransitionGraphTest {
    private val target = ScreenNode("search", "Search", Region(.1f, .1f, .8f, .2f), true, className = "Button")
    private val wrong = ScreenNode("menu", "Menu", Region(.8f, .1f, .95f, .2f), true, className = "Button")
    private val task = PhotoTask("", "", "guide_app", "test.app")

    @Test fun modelUncertaintyOnReadableScreenKeepsItsActualGuidance() = runBlocking {
        val screen = ScreenState("test.app", listOf(target, wrong), windowId = 1)
        val message = "Bà nói giúp cháu nút bà muốn bấm nhé."
        val model = object : GuideModel {
            override suspend fun next(input: GuideInput) = GuideDecision.Await(message, "cannot_read")
            override suspend fun repeat(step: GuidanceStep) = Unit
            override fun cancel() = Unit
        }
        var shown: GuideViewState? = null
        val engine = GuideEngine(model, { shown = it })
        engine.startGoal("Mở tin nhắn")
        engine.onScreen(screen)
        assertEquals(GuideStatus.ASKING, engine.status)
        assertTrue(shown?.instructionVi.orEmpty().contains("nút bác muốn bấm"))
        assertFalse(shown?.instructionVi.orEmpty().contains("chưa đọc rõ màn hình"))
    }

    @Test fun wrongTapKeepsGoalAndCreatesAReasonedRecoveryTransition() = runBlocking {
        val first = ScreenState("test.app", listOf(target, wrong), windowId = 1)
        val changed = ScreenState("test.app", listOf(target.copy(text = "Find"), wrong), windowId = 2)
        var plans = 0
        val model = object : GuideModel {
            override suspend fun next(input: GuideInput): GuideDecision {
                plans++
                return if (plans == 1) GuideDecision.Step(GuidanceStep("action", PhotoStage.ACTION, "Tap Search", target,
                    SamRegion(target.region!!, first.planningFingerprint), VoiceOutput()), task)
                else GuideDecision.Await("I will continue from this screen", "replanned")
            }
            override suspend fun repeat(step: GuidanceStep) = Unit
            override fun cancel() = Unit
        }
        val engine = GuideEngine(model, {})
        engine.startGoal("Find something")
        engine.onScreen(first)
        assertEquals(GuideStatus.WAITING_TAP, engine.status)
        engine.onTap(wrong)
        engine.onScreen(changed)
        assertEquals(2, plans)
        assertEquals("Find something".isNotBlank(), engine.hasGoal)
        assertTrue(engine.transitionHistory.any { it.to == GuideStatus.RECOVERING && it.reasonCode == "wrong_tap" })
        assertTrue(engine.transitionHistory.zipWithNext().all { (a, b) -> b.sequence > a.sequence })
    }

    @Test fun sensitivePauseAndStopClearModelSessionMemory() {
        var clears = 0
        val model = object : GuideModel {
            override suspend fun next(input: GuideInput) = GuideDecision.Await("wait")
            override suspend fun repeat(step: GuidanceStep) = Unit
            override fun cancel() = Unit
            override fun clearSessionMemory() { clears++ }
        }
        val engine = GuideEngine(model, {})
        engine.startGoal("Open news")
        val afterNewGoal = clears
        engine.pauseForSensitive()
        assertTrue(clears > afterNewGoal)
        assertEquals("safety_gate", engine.transitionHistory.last().reasonCode)
        engine.stop()
        assertTrue(clears > afterNewGoal + 1)
    }
}
