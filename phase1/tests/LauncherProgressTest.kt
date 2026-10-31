package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class LauncherProgressTest {
    private fun page(number: Int, count: Int = 5, label: String = "App $number") = ScreenState("com.miui.home", listOf(
        ScreenNode("indicator", "", Region(0f, .75f, 1f, .8f), false,
            className = "android.widget.SeekBar", aliases = listOf("Page $number of $count pages")),
        ScreenNode("icon", label, Region(.1f, .2f, .25f, .3f), true)
    ))

    @Test fun readsRealXiaomiPageIndicatorInEitherLanguage() {
        assertEquals(LauncherPage(2, 5), LauncherPage.read(page(3)))
        val vi = page(3).let { it.copy(nodes = listOf(it.nodes.first().copy(aliases = listOf("Trang 3 trên 5 trang")))) }
        assertEquals(LauncherPage(2, 5), LauncherPage.read(vi))
    }
    @Test fun learnedDrawerIsPreferredButFailedUpwardSwipeDoesNotRepeatForever() {
        val progress = LauncherProgress()
        progress.reset(preferAppList = true)
        progress.observe(page(3))
        assertEquals(LauncherSwipe.ALL_APPS_UP, progress.direction)
        progress.observe(page(3), attempted = true)
        assertEquals(LauncherSwipe.NEXT_PAGE_LEFT, progress.direction)
        progress.preferAppList()
        assertEquals(LauncherSwipe.NEXT_PAGE_LEFT, progress.direction)
    }
    @Test fun rememberingAnIconClearsOldSearchCycles() {
        val progress = LauncherProgress()
        progress.observe(page(1)); progress.observe(page(2)); progress.observe(page(3))
        progress.observe(page(2)); progress.rememberTarget(page(2, label = "Zalo"))
        progress.observe(page(3))
        assertEquals(LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
    }

    @Test fun rejectsUntrustedAmbiguousOrIncompletePageLabels() {
        assertNull(LauncherPage.read(page(0)))
        assertNull(LauncherPage.read(page(6)))
        assertNull(LauncherPage.read(page(3).copy(complete = false)))
        val current = page(3)
        assertNull(LauncherPage.read(current.copy(nodes = listOf(current.nodes.first().copy(className = "android.widget.TextView")))))
        assertNull(LauncherPage.read(current.copy(nodes = current.nodes + page(4).nodes.first())))
        assertNull(LauncherPage.read(current.copy(nodes = current.nodes + ScreenNode("secret", "", null, false, password = true))))
    }

    @Test fun lastPageImmediatelyReversesWithoutNeedingAScrollEvent() {
        val progress = LauncherProgress()
        progress.observe(page(5))
        assertEquals(LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
        assertFalse(progress.exhausted)
    }

    @Test fun swipingPastKnownIconReturnsTowardItsActualPage() {
        val progress = LauncherProgress()
        progress.rememberTarget(page(2, label = "Zalo"))
        for (number in listOf(3, 4, 5, 4, 3)) {
            progress.observe(page(number))
            assertEquals("Page $number", LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
            assertFalse(progress.exhausted)
        }
    }

    @Test fun remembersTargetIndexFromTheTargetFrameRatherThanPreviousEvent() {
        val progress = LauncherProgress()
        progress.observe(page(1))
        progress.rememberTarget(page(4, label = "Zalo"))
        progress.observe(page(3))
        assertEquals(LauncherSwipe.NEXT_PAGE_LEFT, progress.direction)
    }

    @Test fun firstPageBoundaryDoesNotPermanentlyDisableReturningRightLater() {
        val progress = LauncherProgress()
        progress.noteBoundary(true, false, 0, 0)
        progress.observe(page(1))
        progress.rememberTarget(page(3, label = "Zalo"))
        progress.observe(page(5))
        assertEquals(LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
    }

    @Test fun currentIndicatorOverridesStaleBoundaryEvent() {
        val progress = LauncherProgress()
        progress.noteBoundary(false, true, 4, 1)
        progress.observe(page(2))
        assertEquals(LauncherSwipe.NEXT_PAGE_LEFT, progress.direction)
    }

    @Test fun duplicateScrollEventsCannotReverseTheRouteBackToTheIcon() {
        val progress = LauncherProgress()
        progress.rememberTarget(page(2, label = "Zalo"))
        progress.observe(page(4))
        progress.noteBoundary(false, false, 3, 100)
        progress.observe(page(4))
        assertEquals(LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
        progress.observe(page(3))
        assertEquals(LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
    }

    @Test fun unsuccessfulExplicitSwipeStillTriesAnotherRouteAndStops() {
        val progress = LauncherProgress()
        progress.observe(page(3))
        progress.observe(page(3), attempted = true)
        assertEquals(LauncherSwipe.PREVIOUS_PAGE_RIGHT, progress.direction)
        progress.observe(page(3), attempted = true)
        assertEquals(LauncherSwipe.ALL_APPS_UP, progress.direction)
        progress.observe(page(3), attempted = true)
        assertTrue(progress.exhausted)
        progress.rememberTarget(page(3, label = "Zalo"))
        assertFalse(progress.exhausted)
    }
}
