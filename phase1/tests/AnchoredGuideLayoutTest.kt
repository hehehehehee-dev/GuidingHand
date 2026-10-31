package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class AnchoredGuideLayoutTest {
    private fun layout(x: Int, y: Int, target: AnchoredGuideLayout.Box? = null) =
        requireNotNull(AnchoredGuideLayout.place(390, 840, x, y, 64, 310, 230, 12, 6, target, 40))
    private fun checkAttached(p: AnchoredGuideLayout.Placement) {
        assertFalse(p.card.overlaps(p.bubble))
        assertTrue(p.card.x >= 12 && p.card.x + p.card.width <= 378)
        assertTrue(p.card.y >= 40 && p.card.y + p.card.height <= 800)
        assertTrue(p.bubble.y >= 40 && p.bubble.y + p.bubble.height <= 800)
        assertTrue(p.card.y + p.card.height + 6 == p.bubble.y || p.bubble.y + p.bubble.height + 6 == p.card.y)
    }
    @Test fun staysAttachedAtAllScreenCorners() {
        for (x in listOf(0, 326)) for (y in listOf(0, 776)) checkAttached(layout(x, y))
    }
    @Test fun draggingMovesTheCardWithTheBubble() {
        val first = layout(310, 450); val second = layout(20, 550)
        checkAttached(first); checkAttached(second)
        assertNotEquals(first.card.x, second.card.x)
        assertEquals(100, second.card.y - first.card.y)
    }
    @Test fun movesTheWholeGroupToAvoidTheHighlightedControl() {
        val target = AnchoredGuideLayout.Box(0, 280, 390, 100)
        val result = layout(310, 400, target)
        checkAttached(result)
        assertFalse(result.card.overlaps(target)); assertFalse(result.bubble.overlaps(target))
    }
    @Test fun refusesToCoverATargetWhenThereIsNoRoom() {
        assertNull(AnchoredGuideLayout.place(390, 840, 310, 400, 64, 310, 230, 12, 6,
            AnchoredGuideLayout.Box(0, 0, 390, 840), 40))
    }
    @Test fun narrowScreensKeepTheCardInsideTheDisplay() {
        val result = requireNotNull(AnchoredGuideLayout.place(320, 640, 250, 410, 64, 296, 230, 12, 6, verticalMargin = 40))
        assertEquals(12, result.card.x)
        assertTrue(result.card.x + result.card.width <= 308)
    }
    @Test fun compactSwipeCardAndBubbleStayClearOfTheArrowOnPhone() {
        for (direction in LauncherSwipe.entries) {
            val line = direction.line
            val arrow = AnchoredGuideLayout.Box(((minOf(line.startX, line.endX) - .025f) * 390).toInt() - 24,
                ((minOf(line.startY, line.endY) - .025f) * 848).toInt() - 24,
                ((kotlin.math.abs(line.endX - line.startX) + .05f) * 390).toInt() + 48,
                ((kotlin.math.abs(line.endY - line.startY) + .05f) * 848).toInt() + 48)
            val placement = requireNotNull(AnchoredGuideLayout.place(390, 848, 326, 550, 52, 300, 146, 12, 6, arrow, 40))
            assertFalse(placement.card.overlaps(arrow)); assertFalse(placement.bubble.overlaps(arrow))
        }
    }
}
