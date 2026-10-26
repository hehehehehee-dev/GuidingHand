// MOCK: Synthetic trees and an in-memory backend exercise the engine, not a physical phone.
package vn.guidinghand

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class GuideEngineTest {
    private val target = ScreenNode("video", "Gọi video", Region(.1f, .2f, .3f, .4f), true)
    private val task = GuideTask("video", "Gọi hình", listOf(StepGoal("video", listOf("Gọi video"), "Bấm Gọi video.", listOf("Đang gọi"))))
    private fun screen(vararg nodes: ScreenNode) = ScreenState("com.zing.zalo", nodes.toList())
    private class MemoryPaths : PathStore {
        var saved = 0
        override fun familiar(task: GuideTask) = false
        override fun savePath(task: GuideTask, steps: List<Pair<String, Long>>) { saved++; assertTrue(steps.isNotEmpty()) }
    }
    private inner class Backend : GuideModel {
        var calls = 0
        override suspend fun getNextStep(screenshot: GuideFrame, goal: GuideTask): ModelStep? {
            calls++
            return screenshot.screen.nodes.firstOrNull { it.id == "video" }?.let { ModelStep(task.steps.first(), it, "Bấm Gọi video.") }
        }
        override fun speak(text: String) {}
        override fun silence() {}
        override fun close() {}
    }
    @Test fun duplicateTapAndSameFrameDoNotAdvance() = runBlocking {
        val paths = MemoryPaths(); val backend = Backend()
        val engine = GuideEngine(backend, paths, { _, _, _ -> }, {})
        engine.start(task); engine.onFrame(GuideFrame(screen(target), null))
        engine.onClick(target); engine.onClick(target)
        engine.onFrame(GuideFrame(screen(target), null))
        assertEquals(GuideStatus.VERIFYING, engine.status); assertEquals(0, paths.saved); assertEquals(1, backend.calls)
    }
    @Test fun matchingTapAndNewExpectedStateSavePath() = runBlocking {
        val paths = MemoryPaths(); val engine = GuideEngine(Backend(), paths, { _, _, _ -> }, {})
        engine.start(task); engine.onFrame(GuideFrame(screen(target), null)); engine.onClick(target)
        engine.onFrame(GuideFrame(screen(target.copy(id = "call", text = "Đang gọi")), null))
        assertEquals(GuideStatus.DONE, engine.status); assertEquals(1, paths.saved)
    }
    @Test fun wrongTapReplansWithoutClaimingSuccess() = runBlocking {
        val paths = MemoryPaths(); val backend = Backend(); val engine = GuideEngine(backend, paths, { _, _, _ -> }, {})
        engine.start(task); engine.onFrame(GuideFrame(screen(target), null))
        engine.onClick(target.copy(id = "other"))
        engine.onFrame(GuideFrame(screen(target, target.copy(id = "notice", text = "Thông báo")), null))
        assertEquals(GuideStatus.WAITING, engine.status); assertEquals(0, paths.saved); assertEquals(2, backend.calls)
    }
    @Test fun paymentAndPasswordStopBeforeModelAndResumeOnlyAfterLeaving() = runBlocking {
        for (label in listOf("Nhập mật khẩu", "Xác nhận thanh toán")) {
            val backend = Backend(); val paths = MemoryPaths(); val engine = GuideEngine(backend, paths, { _, _, _ -> }, {})
            engine.start(task)
            engine.onFrame(GuideFrame(screen(target.copy(text = label)), null))
            assertEquals(GuideStatus.SENSITIVE, engine.status); assertEquals(0, backend.calls)
            engine.onClick(target); engine.replay()
            engine.onFrame(GuideFrame(screen(target.copy(text = label)), null))
            assertEquals(GuideStatus.SENSITIVE, engine.status); assertEquals(0, paths.saved)
            engine.onFrame(GuideFrame(screen(target), null))
            assertEquals(GuideStatus.WAITING, engine.status); assertEquals(1, backend.calls)
        }
    }
    @Test fun disappearingTargetStopsWithoutSaving() = runBlocking {
        val paths = MemoryPaths(); val engine = GuideEngine(Backend(), paths, { _, _, _ -> }, {})
        engine.start(task); engine.onFrame(GuideFrame(screen(target), null)); engine.onClick(target)
        engine.onFrame(GuideFrame(screen(target.copy(id = "unknown", text = "Màn hình khác")), null))
        assertEquals(GuideStatus.UNCONFIRMED, engine.status); assertEquals(0, paths.saved)
    }
    @Test fun lateBackendResponseCannotOverrideSensitiveStop() = runBlocking {
        val deferred = CompletableDeferred<ModelStep?>()
        val backend = object : GuideModel {
            override suspend fun getNextStep(screenshot: GuideFrame, goal: GuideTask) = deferred.await()
            override fun speak(text: String) {}
            override fun silence() {}
            override fun close() {}
        }
        val engine = GuideEngine(backend, MemoryPaths(), { _, _, _ -> }, {})
        engine.start(task)
        val planning = launch { engine.onFrame(GuideFrame(screen(target), null)) }
        yield()
        engine.onFrame(GuideFrame(screen(target.copy(text = "Mật khẩu")), null))
        deferred.complete(ModelStep(task.steps.first(), target, "Bấm Gọi video.")); planning.join()
        assertEquals(GuideStatus.SENSITIVE, engine.status)
    }
    @Test fun genericScreenChangesAskForInputWithoutClaimingSuccess() = runBlocking {
        val paths = MemoryPaths()
        val engine = GuideEngine(Backend(), paths, { _, _, _ -> }, {})
        val before = ScreenState("any.browser.app", listOf(target))
        engine.start(LocalScreenGuide.task(before, target))
        engine.onFrame(GuideFrame(before, null)); engine.onClick(target)
        engine.onFrame(GuideFrame(ScreenState("another.app", listOf(target.copy(text = "New screen"))), null))
        assertEquals(GuideStatus.NEEDS_INPUT, engine.status)
        assertEquals(0, paths.saved)
    }
    @Test fun partialTreeStopsBeforePlanningOrSaving() = runBlocking {
        val backend = Backend(); val paths = MemoryPaths()
        val engine = GuideEngine(backend, paths, { _, _, _ -> }, {})
        engine.start(task); engine.onFrame(GuideFrame(screen(target).copy(complete = false), null))
        assertEquals(GuideStatus.UNCONFIRMED, engine.status)
        assertEquals(0, backend.calls); assertEquals(0, paths.saved)
    }
    @Test fun unreadablePageCanRecoverOnNextReadableScreenWithoutRestarting() = runBlocking {
        val backend = Backend(); val paths = MemoryPaths()
        val engine = GuideEngine(backend, paths, { _, _, _ -> }, {})
        engine.start(task)
        engine.onFrame(GuideFrame(screen(target.copy(id = "unknown", text = "Loading")), null))
        assertEquals(GuideStatus.UNCONFIRMED, engine.status)
        engine.onFrame(GuideFrame(screen(target), null))
        assertEquals(GuideStatus.WAITING, engine.status)
        assertEquals(2, backend.calls); assertEquals(0, paths.saved)
    }
    @Test fun partialSnapshotCanRecoverWhenTheSameNodesBecomeComplete() = runBlocking {
        val backend = Backend()
        val engine = GuideEngine(backend, MemoryPaths(), { _, _, _ -> }, {})
        engine.start(task)
        engine.onFrame(GuideFrame(screen(target).copy(complete = false), null))
        engine.onFrame(GuideFrame(screen(target), null))
        assertEquals(GuideStatus.WAITING, engine.status)
        assertEquals(1, backend.calls)
    }
}
