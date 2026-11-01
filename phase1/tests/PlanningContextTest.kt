package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class PlanningContextTest {
    private val button = ScreenNode("search", "Search", Region(.7f, .05f, .8f, .12f), true)
    private val header = ScreenNode("person", "An", Region(.1f, .05f, .4f, .12f), false)
    private fun screen(vararg extra: ScreenNode) = ScreenState("test.app", listOf(button, header) + extra)
    @Test fun countersTimestampsAndTraversalOrderDoNotInvalidateAnUnchangedControl() {
        val counter = ScreenNode("view_count", "1.2K views", Region(.1f, .2f, .3f, .24f), false)
        val time = ScreenNode("elapsed", "0:01", Region(.4f, .2f, .5f, .24f), false)
        val before = screen(counter, time)
        val after = screen(counter.copy(text = "1.3K views"), time.copy(text = "0:02")).let { it.copy(nodes = it.nodes.reversed()) }
        assertTrue(PlanningContext.compatible(before, after))
        assertEquals(button, PlanningContext.rebind(button, after))
    }
    @Test fun calculatorValuesAndFocusedInputRemainMeaningfulState() {
        val value = ScreenNode("value", "0", null, false)
        assertFalse(PlanningContext.compatible(screen(value), screen(value.copy(text = "2"))))
        val field = ScreenNode("input", "An", Region(.1f, .5f, .7f, .6f), false, className = "EditText", focused = true)
        assertFalse(PlanningContext.compatible(screen(field), screen(field.copy(text = "Binh"))))
    }
    @Test fun recipientSafetyPackageAndLargeLayoutChangesStillRejectTheResponse() {
        val before = screen()
        assertFalse(PlanningContext.compatible(before, before.copy(nodes = listOf(button, header.copy(text = "Binh")))))
        assertFalse(PlanningContext.compatible(before, before.copy(packageName = "other.app")))
        assertFalse(PlanningContext.compatible(before, before.copy(complete = false)))
        assertFalse(PlanningContext.compatible(before, screen(header.copy(id = "secret", text = "Password", password = true))))
        assertFalse(PlanningContext.compatible(before, before.copy(nodes = listOf(button.copy(region = Region(.2f, .6f, .4f, .7f)), header))))
    }
    @Test fun tinyLayoutJitterRebindsToFreshActualBoundsAndDuplicateTargetsAbstain() {
        val fresh = button.copy(region = Region(.701f, .051f, .801f, .121f))
        val after = ScreenState("test.app", listOf(fresh, header))
        assertTrue(PlanningContext.compatible(screen(), after))
        assertEquals(fresh.region, PlanningContext.rebind(button, after)?.region)
        assertNull(PlanningContext.rebind(button, after.copy(nodes = after.nodes + fresh.copy(region = Region(.1f, .7f, .2f, .8f)))))
    }
}
