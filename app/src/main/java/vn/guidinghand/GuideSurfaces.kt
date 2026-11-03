package vn.guidinghand

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/** Light-catching edges, with opaque surfaces underneath every word. No capture or blur. */
object GuideSurfaces {
    fun card(density: Float, radiusDp: Float = 24f): Drawable = layered(density, radiusDp,
        intArrayOf(Color.parseColor(GuideDesign.CARD_HEX)), Color.parseColor(GuideDesign.SURFACE_EDGE_HEX),
        Color.WHITE)

    fun help(density: Float, radiusDp: Float = 28f): Drawable = layered(density, radiusDp,
        intArrayOf(Color.parseColor(GuideDesign.HELP_HIGHLIGHT_HEX), Color.parseColor(GuideDesign.INK_HEX)),
        Color.parseColor(GuideDesign.SURFACE_EDGE_HEX), Color.parseColor("#658371"))

    private fun layered(density: Float, radiusDp: Float, colors: IntArray, edge: Int, innerEdge: Int): Drawable {
        val stroke = density.coerceAtLeast(1f).toInt()
        val base = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors.takeIf { it.size > 1 }
            ?: intArrayOf(colors[0], colors[0])).apply {
            cornerRadius = radiusDp * density
            setStroke(stroke, edge)
        }
        val highlight = GradientDrawable().apply {
            setColor(Color.TRANSPARENT); cornerRadius = (radiusDp - 2f) * density
            setStroke(stroke, innerEdge)
        }
        return object : LayerDrawable(arrayOf(base, highlight)) {
            override fun getPadding(padding: Rect): Boolean { padding.set(0, 0, 0, 0); return false }
        }.apply {
            setLayerInset(1, stroke * 2, stroke * 2, stroke * 2, stroke * 2)
            // Layers are purely decorative; retain the owner's measured padding/anchor.
            paddingMode = LayerDrawable.PADDING_MODE_STACK
        }
    }
}
