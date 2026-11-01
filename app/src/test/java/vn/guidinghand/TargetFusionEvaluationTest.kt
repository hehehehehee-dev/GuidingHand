package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class TargetFusionEvaluationTest {
    private data class Case(val id: String, val expected: String, val sam: GuideBox, val ui: GuideBox,
                            val display: GuideBox, val density: Float)
    private fun box(value: String): GuideBox {
        val v = value.split(',').map(String::toInt)
        return GuideBox(v[0], v[1], v[2], v[3])
    }
    private fun cases() = checkNotNull(javaClass.getResourceAsStream("/evaluation/fusion_cases.tsv"))
        .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val v = line.split('|')
            Case(v[0], v[1], box(v[2]), box(v[3]), box(v[4]), v[5].toFloat())
        }.toList() }

    @Test fun sparkSamAccessibilityFusionMatchesTheFixtureContract() {
        val fixtures = cases()
        assertTrue(fixtures.size >= 8)
        fixtures.forEach { fixture ->
            val result = GuideRingGeometry.fuseTarget(fixture.sam, fixture.ui, fixture.display, fixture.density)
            if (fixture.expected == "REJECT") assertNull(fixture.id, result)
            else {
                assertNotNull(fixture.id, result)
                assertEquals(fixture.id, fixture.expected, result!!.mode)
                assertTrue("${fixture.id} had weak overlap", result.overlapPermille >= 500)
                assertTrue("${fixture.id} escaped display", fixture.display.contains(result.target))
                val bands = GuideRingGeometry.visibleBands(result.target, fixture.display, fixture.density)
                assertTrue("${fixture.id} black stroke clipped", fixture.display.contains(bands.blackCenter))
                assertTrue("${fixture.id} yellow stroke clipped", fixture.display.contains(bands.yellowCenter))
            }
        }
    }
}
