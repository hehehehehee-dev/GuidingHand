package vn.guidinghand

/** A fingertip touches the target edge; the hand body stays outside whenever space permits. */
object PointerPlacement {
    data class Placement(val x: Float, val y: Float, val rotation: Float,
                         val left: Float, val top: Float, val right: Float, val bottom: Float)
    fun place(target: Region, width: Int, height: Int, density: Float): Placement? {
        if (width <= 0 || height <= 0 || !density.isFinite() || density <= 0) return null
        val left = target.left * width; val right = target.right * width
        val top = target.top * height; val bottom = target.bottom * height
        val cx = (left + right) / 2; val cy = (top + bottom) / 2
        val insetX = minOf(3 * density, (right - left) / 4)
        val insetY = minOf(3 * density, (bottom - top) / 4)
        val margin = 4 * density
        fun candidate(x: Float, y: Float, rotation: Float): Placement {
            val bounds = when (rotation) {
                180f -> floatArrayOf(-24f, -49f, 16f, 3f)
                90f -> floatArrayOf(-49f, -16f, 3f, 24f)
                270f -> floatArrayOf(-3f, -24f, 49f, 16f)
                else -> floatArrayOf(-16f, -3f, 24f, 49f)
            }
            return Placement(x, y, rotation, x + bounds[0] * density, y + bounds[1] * density,
                x + bounds[2] * density, y + bounds[3] * density)
        }
        fun fits(p: Placement) = p.left >= margin && p.top >= margin && p.right <= width - margin && p.bottom <= height - margin
        val options = listOf(candidate(cx, bottom - insetY, 0f), candidate(cx, top + insetY, 180f),
            candidate(right - insetX, cy, 270f), candidate(left + insetX, cy, 90f))
        options.firstOrNull(::fits)?.let { return it }
        // Very large edge targets may need the body inside, but the fingertip must remain on-target.
        if (width < 48 * density || height < 60 * density) return null
        val x = cx.coerceIn(20 * density, width - 28 * density)
        val y = cy.coerceIn(7 * density, height - 53 * density)
        return candidate(x, y, 0f).takeIf { fits(it) && x in left..right && y in top..bottom }
    }
}
