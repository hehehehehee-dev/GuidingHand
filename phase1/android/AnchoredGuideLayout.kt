package vn.guidinghand

/** Pixel geometry shared by the card and its draggable bubble. */
object AnchoredGuideLayout {
    data class Box(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun overlaps(other: Box) = x < other.x + other.width && x + width > other.x &&
            y < other.y + other.height && y + height > other.y
    }
    data class Placement(val bubble: Box, val card: Box)
    fun place(width: Int, height: Int, bubbleX: Int, bubbleY: Int, size: Int,
              cardWidth: Int, cardHeight: Int, margin: Int, gap: Int, target: Box? = null,
              verticalMargin: Int = margin): Placement? {
        val top = verticalMargin; val bottom = height - verticalMargin
        if (cardHeight + size + gap > bottom - top || cardWidth > width - 2 * margin) return null
        val bx = bubbleX.coerceIn(margin, width - margin - size)
        val cx = (if (bx + size / 2 < width / 2) bx else bx + size - cardWidth)
            .coerceIn(margin, width - margin - cardWidth)
        fun candidate(y: Int, above: Boolean): Placement {
            val by = if (above) y.coerceIn(top + cardHeight + gap, bottom - size)
                else y.coerceIn(top, bottom - size - gap - cardHeight)
            return Placement(Box(bx, by, size, size), Box(cx, if (above) by - gap - cardHeight else by + size + gap, cardWidth, cardHeight))
        }
        return listOf(candidate(bubbleY, true), candidate(bubbleY, false), candidate(top, false), candidate(bottom - size, true))
            .filter { target == null || (!it.card.overlaps(target) && !it.bubble.overlaps(target)) }
            .minByOrNull { kotlin.math.abs(it.bubble.y - bubbleY) }
    }
}
