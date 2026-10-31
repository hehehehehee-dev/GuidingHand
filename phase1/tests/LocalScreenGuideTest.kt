// MOCK: Synthetic controls for arbitrary packages; no phone interaction.
package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class LocalScreenGuideTest {
    private fun node(text: String, id: String = text, left: Float = .1f) = ScreenNode(id, text, Region(left, .2f, left + .1f, .4f), true)
    @Test fun visibleControlsCanBeFoundInAnyPackage() {
        val control = node("Downloads")
        val screen = ScreenState("a.browser.app", listOf(control))
        assertEquals(control, LocalScreenGuide.match(screen, "Tap Downloads"))
        assertEquals(control, LocalScreenGuide.match(screen, "where is Downloads?"))
        val task = LocalScreenGuide.task(screen, control)
        assertEquals("a.browser.app", task.appPackage)
        assertEquals(setOf("a.browser.app"), task.allowedPackages)
        assertEquals("inspect", task.steps.single().completion)
    }
    @Test fun duplicateLabelsAndAmbiguousPartialMatchesAreNotGuessed() {
        val screen = ScreenState("any.app", listOf(node("Save", "first"), node("Save", "second", .6f)))
        assertNull(LocalScreenGuide.match(screen, "Save"))
        assertTrue(LocalScreenGuide.candidates(screen).isEmpty())
        assertNull(LocalScreenGuide.match(ScreenState("any.app", listOf(node("Save draft"), node("Save copy", left = .6f))), "Save"))
    }
    @Test fun sensitiveScreensNeverExposeGenericSuggestions() {
        val screen = ScreenState("any.app", listOf(node("Confirm"), node("Payment", left = .6f)))
        assertTrue(LocalScreenGuide.candidates(screen).isEmpty())
        assertNull(LocalScreenGuide.match(screen, "Confirm"))
    }
    @Test fun unlabeledInvisibleAndNonclickableControlsAreNotInvented() {
        val screen = ScreenState("any.app", listOf(node(""), node("Label").copy(clickable = false), node("Missing bounds").copy(region = null)))
        assertTrue(LocalScreenGuide.candidates(screen).isEmpty())
        assertNull(LocalScreenGuide.match(screen, "Settings"))
    }
    @Test fun arbitraryAppsAndInstagramUseActualPackages() {
        val task = Tasks.openApp("Camera", "any.camera.app")
        assertEquals(listOf("any.camera.app"), task.steps.single().expected)
        assertEquals("com.instagram.android", Tasks.openInstagram.appPackage)
        assertEquals("com.instagram.android", Tasks.messageIn("com.instagram.android", "Instagram").appPackage)
        assertEquals("Instagram", Tasks.messageIn("com.instagram.android", "Instagram").appLabel)
    }
    @Test fun partialTreesDoNotExposePotentiallyUnsafeTargets() {
        val screen = ScreenState("any.app", listOf(node("Continue")), complete = false)
        assertTrue(LocalScreenGuide.candidates(screen).isEmpty())
        assertNull(LocalScreenGuide.match(screen, "Continue"))
    }
    @Test fun speakingATranslatedSuggestionFindsTheOriginalControlAndStillRejectsAmbiguity() {
        val settings = node("Settings")
        assertEquals(settings, LocalScreenGuide.match(ScreenState("any.app", listOf(settings)), "bấm Cài đặt"))
        val vietnamese = node("Tải xuống")
        assertEquals(vietnamese, LocalScreenGuide.match(ScreenState("any.app", listOf(vietnamese)), "Tap Downloads"))
        val duplicate = ScreenState("any.app", listOf(settings, node("Cài đặt", "second", .6f)))
        assertNull(LocalScreenGuide.match(duplicate, "bấm Cài đặt"))
    }
}
