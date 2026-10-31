package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class MetaChoicesTest {
    private val family = GuideFamily("An", "Test network", "Synthetic photo", "bà")
    private fun node(text: String, left: Float = .1f) = ScreenNode(text, text, Region(left, .2f, left + .1f, .4f), true)
    private val settings = Tasks.all.first { it.id == "font" }

    @Test fun picksOnlyActualMatchingNodesAndPreservesClientVerificationRules() {
        val target = node("Settings")
        val choices = MetaChoices.build(ScreenState("com.zing.zalo", listOf(target, node("Unrelated", .6f))), settings, family, "vi")
        assertEquals(1, choices.size)
        assertEquals(target, choices.single().node)
        assertEquals(settings.steps.first { it.id == "settings" }, choices.single().goal)
        assertTrue(choices.single().instruction.startsWith("Bà, bấm Cài đặt"))
    }
    @Test fun sensitiveIncompleteAndWrongPackageScreensOfferNoExportChoices() {
        val safe = ScreenState("com.zing.zalo", listOf(node("Settings")))
        assertTrue(MetaChoices.build(safe.copy(complete = false), settings, family, "en").isEmpty())
        assertTrue(MetaChoices.build(safe.copy(packageName = "other.app"), settings, family, "en").isEmpty())
        assertTrue(MetaChoices.build(safe.copy(nodes = safe.nodes + node("OTP", .6f)), settings, family, "en").isEmpty())
    }
    @Test fun duplicateRecipientNamesAreNeverChoicesForTheModelToGuess() {
        val screen = ScreenState("com.zing.zalo", listOf(node("An"), node("An", .6f)))
        assertTrue(MetaChoices.build(screen, Tasks.message, family, "vi").isEmpty())
    }
    @Test fun messageBoxAndPhotoAttachRequireTheConfirmedRecipientOnScreen() {
        val compose = node("Message").copy(className = "android.widget.EditText", clickable = false)
        val screen = ScreenState("com.zing.zalo", listOf(compose))
        assertTrue(MetaChoices.build(screen, Tasks.message, family, "vi").isEmpty())
        val choices = MetaChoices.build(screen.copy(nodes = screen.nodes + node("An", .6f)), Tasks.message, family, "vi")
        assertTrue(choices.any { it.goal.id == "compose" && it.node == compose })
    }
    @Test fun sendRequiresTheActuallySelectedConfiguredPhoto() {
        val send = node("Send")
        val task = Tasks.all.first { it.id == "photo" }
        val screen = ScreenState("com.zing.zalo", listOf(send))
        assertTrue(MetaChoices.build(screen, task, family, "en").isEmpty())
        val photo = node("Synthetic photo", .6f).copy(selected = true)
        val choices = MetaChoices.build(screen.copy(nodes = listOf(send, photo)), task, family, "en")
        assertTrue(choices.any { it.goal.id == "send" && it.node == send })
    }
    @Test fun staleResponsesUnknownIdsAndAbstentionsCannotProduceATarget() {
        val choices = MetaChoices.build(ScreenState("com.zing.zalo", listOf(node("Settings"))), settings, family, "en")
        assertNull(MetaChoices.resolve(choices, "guide", "c0", "new", "old", "same", "same"))
        assertNull(MetaChoices.resolve(choices, "guide", "c0", "same", "same", "new", "old"))
        assertNull(MetaChoices.resolve(choices, "guide", "c99", "same", "same", "same", "same"))
        assertNull(MetaChoices.resolve(choices, "done", "c0", "same", "same", "same", "same"))
        assertNull(MetaChoices.resolve(choices, "needs_input", null, "same", "same", "same", "same"))
        assertEquals(choices.single(), MetaChoices.resolve(choices, "guide", "c0", "same", "same", "same", "same"))
    }
    @Test fun searchFallbackIsGroundedAndTypingRemainsTheUsersAction() {
        val search = node("Search apps").copy(focused = true, className = "android.widget.EditText")
        val task = Tasks.openApp("Test App", "test.target").copy(allowedPackages = setOf("test.launcher"))
        val choice = MetaChoices.build(ScreenState("test.launcher", listOf(search)), task, family, "en").single()
        assertEquals("enterRecipient", choice.goal.id)
        assertEquals(listOf("Test App"), choice.goal.expected)
        assertEquals("Type Test App in this search box.", choice.instruction)
    }
    @Test fun localScreenChoicesKeepTheSelectedTargetAndLanguage() {
        val target = node("Downloads")
        val screen = ScreenState("test.browser", listOf(target))
        val task = LocalScreenGuide.task(screen, target)
        val choice = MetaChoices.build(screen, task, family, "vi").single()
        assertEquals("inspect", choice.goal.completion)
        assertEquals(target, choice.node)
        assertFalse(choice.instruction.contains("Downloads"))
    }
}
