package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class LauncherSwipeTest {
    private val home = "com.miui.home"
    private val icon = ScreenNode("icon", "Zalo", Region(.1f, .3f, .25f, .45f), true)
    private val launcher = ScreenState(home, listOf(icon))
    private fun allowed(screen: ScreenState? = launcher, packageName: String? = home,
                        unlocked: Boolean = true, capable: Boolean = true) =
        LauncherSwipePolicy.allowed(screen, packageName, unlocked, capable)

    @Test fun completeUnlockedHomeWithCapabilityAllowsARequestedSwipe() {
        assertTrue(allowed())
    }
    @Test fun searchResultsAndOtherAppsCannotReceiveLauncherSwipes() {
        for (packageName in listOf("com.mi.appfinder", "com.google.android.googlequicksearchbox",
            "com.zing.zalo", "com.android.systemui", "vn.guidinghand")) {
            assertFalse(packageName, allowed(launcher.copy(packageName = packageName)))
        }
    }
    @Test fun missingLauncherOrIncompleteTreeCannotAuthorizeSwipe() {
        assertFalse(allowed(screen = null))
        assertFalse(allowed(packageName = null))
        assertFalse(allowed(packageName = ""))
        assertFalse(allowed(launcher.copy(complete = false)))
    }
    @Test fun lockAndMissingCapabilityPreventDispatch() {
        assertFalse(allowed(unlocked = false))
        assertFalse(allowed(capable = false))
    }
    @Test fun passwordOrPaymentStopsEvenInsideTheLauncherPackage() {
        assertFalse(allowed(launcher.copy(nodes = listOf(icon.copy(password = true)))))
        assertFalse(allowed(launcher.copy(nodes = listOf(icon.copy(text = "Confirm payment")))))
    }
    @Test fun focusedSearchEditorPreventsSwipingThroughTyping() {
        val search = icon.copy(id = "search", text = "Search apps", focused = true,
            className = "android.widget.EditText")
        assertFalse(allowed(launcher.copy(nodes = listOf(search))))
        assertTrue(allowed(launcher.copy(nodes = listOf(search.copy(focused = false)))))
    }
    @Test fun gestureDirectionsAreSwipesAndStayInsideWorkspaceMargins() {
        for (swipe in LauncherSwipe.entries) {
            val line = swipe.line
            listOf(line.startX, line.startY, line.endX, line.endY).forEach { assertTrue(it in .2f.. .8f) }
            assertTrue(kotlin.math.abs(line.endX - line.startX) + kotlin.math.abs(line.endY - line.startY) >= .3f)
        }
        assertTrue(LauncherSwipe.ALL_APPS_UP.line.endY < LauncherSwipe.ALL_APPS_UP.line.startY)
        assertTrue(LauncherSwipe.NEXT_PAGE_LEFT.line.endX < LauncherSwipe.NEXT_PAGE_LEFT.line.startX)
        assertTrue(LauncherSwipe.PREVIOUS_PAGE_RIGHT.line.endX > LauncherSwipe.PREVIOUS_PAGE_RIGHT.line.startX)
    }
    @Test fun homeNavigationStartsHorizontallyAndNeverOffersDownIntoSystemPanels() {
        var direction = LauncherSwipe.initial
        assertEquals(LauncherSwipe.NEXT_PAGE_LEFT, direction)
        val seen = mutableSetOf<LauncherSwipe>()
        repeat(6) {
            seen += direction
            assertTrue(direction.line.endY <= direction.line.startY)
            direction = direction.next()
        }
        assertEquals(LauncherSwipe.entries.toSet(), seen)
    }
}
