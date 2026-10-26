package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class LauncherTargetsTest {
    private fun node(text: String, id: String = text, left: Float = .1f, clickable: Boolean = true,
                     className: String = "android.widget.TextView", focused: Boolean = false,
                     aliases: List<String> = emptyList()) = ScreenNode(id, text,
        Region(left, .2f, left + .15f, .3f), clickable, className = className, aliases = aliases, focused = focused)
    private fun screen(vararg nodes: ScreenNode) = ScreenState("com.miui.home", nodes.toList())

    @Test fun exactAccessibleAppLabelIsRequired() {
        val zalo = node("Zalo")
        val current = screen(node("Zalo tools", left = .6f), zalo)
        assertEquals(zalo, LauncherTargets.icon(current, "  Zalo "))
        assertNull(LauncherTargets.icon(current, "Zal"))
        assertNull(LauncherTargets.icon(screen(node("Zalo", clickable = false)), "Zalo"))
        assertTrue(LauncherTargets.hasTarget(current, "Zalo"))
    }

    @Test fun distinctDuplicateIconsAreNotGuessedButSameBoundsAliasesDeduplicate() {
        val first = node("Zalo", id = "first")
        assertNull(LauncherTargets.icon(screen(first, node("Zalo", id = "second", left = .6f)), "Zalo"))
        assertEquals(first, LauncherTargets.icon(screen(first, first.copy(id = "child", text = "", aliases = listOf("Zalo"))), "Zalo"))
        assertNull(LauncherTargets.search(screen(node("Search", id = "first"), node("Search", id = "second", left = .6f))))
    }

    @Test fun searchCanBeIdentifiedByAnActualBilingualLabelOrContentDescription() {
        val vietnamese = node("Tìm kiếm ứng dụng")
        assertEquals(vietnamese, LauncherTargets.search(screen(vietnamese)))
        val described = node("", aliases = listOf("Search apps and content"))
        assertEquals(described, LauncherTargets.search(screen(described)))
        assertTrue(LauncherTargets.hasTarget(screen(described), "Instagram"))
    }

    @Test fun editableSearchResourceIdentitySurvivesEnteredQueryText() {
        val field = node("Za", id = "com.mi.appfinder:id/searchEditText", clickable = false,
            className = "android.widget.EditText", focused = true)
        assertEquals(field, LauncherTargets.search(screen(field)))
        assertEquals(field, LauncherTargets.search(screen(node("Search", left = .6f), field)))
    }

    @Test fun arbitraryFocusedMessageAndPackageNameAreNotSearchEvidence() {
        val composer = node("Hello", id = "com.search.example:id/message", className = "android.widget.EditText", focused = true)
        assertNull(LauncherTargets.search(screen(composer)))
        assertNull(LauncherTargets.search(screen(composer.copy(id = "draft_query_preview", className = "android.widget.ImageView"))))
    }

    @Test fun partialSensitiveAndOwnAppScreensCannotExposeTargets() {
        val current = screen(node("Search"), node("Zalo", left = .6f))
        for (blocked in listOf(current.copy(complete = false), current.copy(packageName = "vn.guidinghand"),
            current.copy(nodes = current.nodes + node("Password")),
            current.copy(nodes = current.nodes + node("", id = "secret").copy(password = true)))) {
            assertNull(LauncherTargets.search(blocked))
            assertNull(LauncherTargets.icon(blocked, "Zalo"))
            assertFalse(LauncherTargets.hasTarget(blocked, "Zalo"))
        }
    }

    @Test fun sameBoundsParentAndEditableChildKeepTheEditableTarget() {
        val parent = node("Search", id = "parent")
        val child = parent.copy(id = "search_src_text", text = "", className = "android.widget.EditText", focused = true)
        assertEquals(child, LauncherTargets.search(screen(parent, child)))
    }

    @Test fun missingBoundsOrAnEditableAppQueryDoesNotBecomeAnIcon() {
        assertNull(LauncherTargets.icon(screen(node("Zalo").copy(region = null)), "Zalo"))
        assertNull(LauncherTargets.icon(screen(node("Zalo", className = "android.widget.EditText", focused = true)), "Zalo"))
        assertNull(LauncherTargets.icon(screen(node("Zalo", className = "android.widget.AutoCompleteTextView", focused = true)), "Zalo"))
    }
}
