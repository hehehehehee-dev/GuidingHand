package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class TargetBindingTest {
    private val key = ScreenNode("search", "Search", Region(.1f, .2f, .3f, .4f), true)
    private val screen = ScreenState("test.browser", listOf(key))
    private fun resolve(after: ScreenState = screen, goal: String = "Read news") = TargetBinding.resolve(goal, after,
        TargetBinding.context("Read news", screen), screen.packageName, TargetBinding.node(key), key.region!!)
    @Test fun positionIsReusedOnlyAfterTheActualNodeAndWholeContextMatch() {
        assertEquals(key, resolve())
        assertNull(resolve(screen.copy(packageName = "test.other")))
        assertNull(resolve(screen.copy(nodes = listOf(key.copy(text = "Send")))))
        assertNull(resolve(screen.copy(nodes = listOf(key.copy(id = "other")))))
        assertNull(resolve(screen.copy(nodes = listOf(key.copy(region = Region(.6f, .2f, .8f, .4f))))))
        assertNull(resolve(goal = "Send a message"))
    }
    @Test fun incompleteSensitiveAndChangedBackgroundsInvalidateTheHint() {
        assertNull(resolve(screen.copy(complete = false)))
        assertNull(resolve(screen.copy(nodes = screen.nodes + key.copy(id = "notice", text = "Password"))))
        assertNull(resolve(screen.copy(nodes = screen.nodes + key.copy(id = "person", text = "Different recipient"))))
    }
}
