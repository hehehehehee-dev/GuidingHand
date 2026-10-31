package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class PointerPlacementTest {
    @Test fun fingertipStaysOnTargetAndBodyFitsAtEveryCorner() {
        for (density in listOf(1f, 2.75f)) for (x in listOf(.01f, .45f, .88f)) for (y in listOf(.01f, .45f, .91f)) {
            val width = (400 * density).toInt(); val height = (800 * density).toInt()
            val target = Region(x, y, x + .1f, y + .07f)
            val hand = PointerPlacement.place(target, width, height, density) ?: error("No placement for $target")
            assertTrue(hand.x in target.left * width..target.right * width)
            assertTrue(hand.y in target.top * height..target.bottom * height)
            assertTrue(hand.left >= 0 && hand.top >= 0 && hand.right <= width && hand.bottom <= height)
        }
    }
    @Test fun bottomTargetsPointDownFromAboveInsteadOfOffscreen() {
        val hand = PointerPlacement.place(Region(.4f, .9f, .6f, .98f), 400, 800, 1f)!!
        assertEquals(180f, hand.rotation); assertTrue(hand.top < .9f * 800)
    }
    @Test fun normalTargetHasBodyBelowItsEdge() {
        val hand = PointerPlacement.place(Region(.4f, .4f, .6f, .5f), 400, 800, 1f)!!
        assertEquals(0f, hand.rotation); assertTrue(hand.bottom > 400)
    }
    @Test fun impossibleSurfaceDoesNotCreateAnOffscreenPointer() {
        assertNull(PointerPlacement.place(Region(0f, 0f, 1f, 1f), 20, 20, 1f))
        assertNull(PointerPlacement.place(Region(0f, 0f, 1f, 1f), 400, 800, Float.NaN))
    }
}
