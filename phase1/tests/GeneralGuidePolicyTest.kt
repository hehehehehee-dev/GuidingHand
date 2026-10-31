package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class GeneralGuidePolicyTest {
    private fun node(id: String, label: String, x: Float = .1f) = ScreenNode(id, label, Region(x, .2f, x + .1f, .3f), true)
    @Test fun goalChoicesAreNotLimitedToPrewrittenTasks() {
        val screen = ScreenState("test.calculator", listOf(node("two", "2"), node("four", "4", .4f)))
        val task = GeneralGuidePolicy.task(screen, "Calculate 24 times 7")
        assertEquals("assist", task.id); assertEquals(screen.packageName, task.appPackage)
        assertEquals("Calculate 24 times 7", UiText("en").task(task))
        assertEquals(listOf("2", "4"), GeneralGuidePolicy.choices(screen, "en").map { it.node.text })
        assertTrue(GeneralGuidePolicy.choices(screen, "vi").all { it.instruction.startsWith("Bấm ") && it.goal.completion == "inspect" })
    }
    @Test fun sensitiveIncompleteAndOwnScreensNeverOfferChoices() {
        for (screen in listOf(ScreenState("test", listOf(node("password", "Password"))),
            ScreenState("test", listOf(node("two", "2")), false), ScreenState("vn.guidinghand", listOf(node("two", "2")))))
            assertTrue(GeneralGuidePolicy.choices(screen, "en").isEmpty())
        assertTrue(GeneralGuidePolicy.sensitiveGoal("enter my OTP"))
    }
    @Test fun consequentialActionsDuplicatesAndHiddenControlsAreExcluded() {
        for (label in listOf("Send", "Delete photo", "Call An", "Allow", "Xác nhận", "Gửi", "Mua"))
            assertTrue(label, GeneralGuidePolicy.choices(ScreenState("test", listOf(node("action", label))), "en").isEmpty())
        val duplicates = ScreenState("test", listOf(node("a", "Photos"), node("b", "Photos", .4f), node("c", "Search", .7f).copy(clickable = false)))
        assertTrue(GeneralGuidePolicy.choices(duplicates, "en").isEmpty())
    }
    @Test fun questionsUseOnlySelectedUiLanguage() {
        assertTrue(GeneralGuidePolicy.question("recipient", UiText("vi")).startsWith("Muốn"))
        assertTrue(GeneralGuidePolicy.question("connection", UiText("en")).startsWith("I couldn't"))
    }
}
