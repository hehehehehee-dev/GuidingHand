package vn.guidinghand

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SafetyEvaluationTest {
    private data class Case(val id: String, val expected: ScreenReadVerdict, val kind: String, val label: String)

    private fun cases(): List<Case> = checkNotNull(javaClass.getResourceAsStream("/evaluation/safety_cases.tsv"))
        .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val values = line.split('|', limit = 4)
            Case(values[0], ScreenReadVerdict.valueOf(values[1]), values[2], values.getOrElse(3) { "" })
        }.toList() }

    private fun screen(case: Case): ScreenState {
        val label = ScreenNode("label", case.label, Region(.1f, .1f, .9f, .2f), false, className = "TextView")
        val input = ScreenNode("input", if (case.kind == "input_label") case.label else "", Region(.1f, .3f, .9f, .4f),
            true, className = "EditText", focused = true)
        return when (case.kind) {
            "password" -> ScreenState("test.app", listOf(input.copy(text = "", password = true)))
            "input_label" -> ScreenState("test.app", listOf(input))
            "label_with_input" -> ScreenState("test.app", listOf(label, input.copy(text = "")))
            "interactive_label" -> ScreenState("test.app", listOf(label.copy(clickable = true, className = "Button")))
            "plain_label" -> ScreenState("test.app", listOf(label))
            "normal_button" -> ScreenState("test.app", listOf(label.copy(clickable = true, className = "Button")))
            "locked" -> ScreenState("test.app", listOf(label), isLocked = true)
            "empty" -> ScreenState("test.app", emptyList())
            "incomplete" -> ScreenState("test.app", listOf(label), complete = false)
            "read_issue" -> ScreenState("test.app", listOf(label), readIssue = "cluttered")
            else -> error("Unknown fixture kind ${case.kind}")
        }
    }

    @Test fun fixtureVerdictsMatchTheNativePreModelGate() {
        val fixtures = cases()
        assertTrue(fixtures.size >= 20)
        fixtures.forEach { fixture -> assertEquals(fixture.id, fixture.expected, ScreenReadPolicy.verdict(screen(fixture))) }
    }

    @Test fun unsafeAndUnreadableFixturesMakeZeroPlannerCalls() = runBlocking {
        cases().forEach { fixture ->
            var calls = 0
            val model = object : GuideModel {
                override suspend fun next(input: GuideInput): GuideDecision { calls++; return GuideDecision.Await("pause") }
                override suspend fun repeat(step: GuidanceStep) = Unit
                override fun cancel() = Unit
            }
            val engine = GuideEngine(model, {})
            engine.startGoal("Open the requested app")
            engine.onScreen(screen(fixture))
            if (fixture.expected == ScreenReadVerdict.SAFE) assertEquals(fixture.id, 1, calls)
            else assertEquals("${fixture.id} reached the planner", 0, calls)
            when (fixture.expected) {
                ScreenReadVerdict.SENSITIVE, ScreenReadVerdict.LOCKED -> assertEquals(fixture.id, GuideStatus.SAFETY_PAUSED, engine.status)
                ScreenReadVerdict.CANNOT_READ -> assertEquals(fixture.id, GuideStatus.ASKING, engine.status)
                ScreenReadVerdict.SAFE -> assertEquals(fixture.id, GuideStatus.ASKING, engine.status)
            }
        }
    }

    @Test fun spokenSecretsRemainStrictEvenWithoutUiRoleContext() {
        for (text in listOf("OTP 123456", "mã xác thực", "password", "CVV", "payment card"))
            assertTrue(text, SafetyPolicy.sensitiveGoal(text))
        for (text in listOf("Pinterest", "đọc báo", "tìm thơ Tố Hữu"))
            assertFalse(text, SafetyPolicy.sensitiveGoal(text))
    }
}
