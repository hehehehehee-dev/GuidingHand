package vn.guidinghand

import kotlin.math.ceil

/** Pixel geometry, separate from Android views so collision handling can be checked at large text sizes. */
data class GuideBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)
    val area get() = width.toLong() * height
    fun intersects(other: GuideBox) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    fun contains(other: GuideBox) = other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
    fun inflate(pixels: Int) = GuideBox(left - pixels, top - pixels, right + pixels, bottom + pixels)
    fun union(other: GuideBox) = GuideBox(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
    fun intersection(other: GuideBox): GuideBox? {
        val box = GuideBox(maxOf(left, other.left), maxOf(top, other.top), minOf(right, other.right), minOf(bottom, other.bottom))
        return box.takeIf { it.width > 0 && it.height > 0 }
    }
}

data class GuidePlacement(val bounds: GuideBox, val scrollContent: Boolean)
data class GuideRingBands(val blackCenter: GuideBox, val yellowCenter: GuideBox, val edgeAdjusted: Boolean)
data class GuidePointerPlacement(val bounds: GuideBox, val rotationDegrees: Float)
data class GuideTargetFusion(val target: GuideBox, val mode: String, val overlapPermille: Int,
                             val edgeAdjusted: Boolean)
data class GuideRenderPreferences(val language: String, val reduceMotion: Boolean,
                                  val cardSize: GuideCardSize, val visionProfile: VisionProfile,
                                  val showPointer: Boolean, val fontScale: Float)

/** Content-free identity for suppressing duplicate renders without suppressing preference changes. */
object GuideRenderIdentity {
    fun key(state: GuideViewState, canOpenLauncher: Boolean, userCollapsed: Boolean,
            safe: GuideBox, preferences: GuideRenderPreferences): String = listOf(
        state.status, state.instructionVi, state.draftText, state.options,
        state.step?.stage, state.step?.sam?.region, state.step?.sam?.frameFingerprint,
        canOpenLauncher, userCollapsed, safe, preferences
    ).joinToString("\u001f")
}

object GuideRingGeometry {
    /** Full outside edge: 12dp gap + 6dp yellow band + 6dp black band. Never round the minimum down. */
    fun paddedBounds(target: GuideBox, density: Float): GuideBox {
        require(density > 0 && density.isFinite())
        val extent = ceil((GuideDesign.RING_PADDING_DP + GuideDesign.RING_INNER_DP + GuideDesign.RING_OUTER_DP) * density).toInt()
        return target.inflate(extent)
    }
    fun fitsDisplay(target: GuideBox, display: GuideBox, density: Float) = display.contains(paddedBounds(target, density))

    /** Keep both 6dp strokes fully visible when a real control touches a display edge. */
    fun visibleBands(target: GuideBox, display: GuideBox, density: Float): GuideRingBands {
        require(display.contains(target) && density > 0 && density.isFinite())
        val padding = ceil(GuideDesign.RING_PADDING_DP * density).toInt()
        val inner = ceil(GuideDesign.RING_INNER_DP * density).toInt()
        val outer = ceil(GuideDesign.RING_OUTER_DP * density).toInt()
        val blackHalf = ceil(outer / 2.0).toInt()
        val yellowHalf = ceil(inner / 2.0).toInt()
        fun center(box: GuideBox, edgeInset: Int) = GuideBox(
            box.left.coerceAtLeast(display.left + edgeInset),
            box.top.coerceAtLeast(display.top + edgeInset),
            box.right.coerceAtMost(display.right - edgeInset),
            box.bottom.coerceAtMost(display.bottom - edgeInset)
        )
        val rawBlack = target.inflate(padding + inner + blackHalf)
        val rawYellow = target.inflate(padding + yellowHalf)
        val black = center(rawBlack, blackHalf)
        // Keep the yellow stroke fully inside the black stroke at an adjusted edge.
        val yellow = center(rawYellow, outer + yellowHalf)
        return GuideRingBands(black, yellow, black != rawBlack || yellow != rawYellow)
    }

    /** Place an upward-pointing hand outside the control, rotating only when space requires it. */
    fun pointerPlacement(target: GuideBox, display: GuideBox, size: Int, overlap: Int): GuidePointerPlacement {
        require(display.contains(target) && size > overlap && overlap >= 0)
        fun centeredX() = ((target.left + target.right - size) / 2).coerceIn(display.left, display.right - size)
        fun centeredY() = ((target.top + target.bottom - size) / 2).coerceIn(display.top, display.bottom - size)
        return when {
            target.bottom - overlap + size <= display.bottom -> GuidePointerPlacement(
                GuideBox(centeredX(), target.bottom - overlap, centeredX() + size, target.bottom - overlap + size), 0f)
            target.top + overlap - size >= display.top -> GuidePointerPlacement(
                GuideBox(centeredX(), target.top + overlap - size, centeredX() + size, target.top + overlap), 180f)
            target.right - overlap + size <= display.right -> GuidePointerPlacement(
                GuideBox(target.right - overlap, centeredY(), target.right - overlap + size, centeredY() + size), 270f)
            else -> {
                val left = (target.left + overlap - size).coerceIn(display.left, display.right - size)
                GuidePointerPlacement(GuideBox(left, centeredY(), left + size, centeredY() + size), 90f)
            }
        }
    }

    /**
     * Fuse SAM's visual region with the exact clickable Accessibility bounds. Display-edge
     * proximity is never uncertainty by itself: a real edge-mounted control is accepted when
     * both sources overlap strongly, then both ring strokes are pulled inward by visibleBands.
     */
    fun fuseTarget(sam: GuideBox, ui: GuideBox?, display: GuideBox, density: Float): GuideTargetFusion? {
        if (!display.contains(sam) || density <= 0 || !density.isFinite()) return null
        val control = ui ?: return null
        if (!display.contains(control) || sam.area <= 0 || control.area <= 0) return null
        val overlap = sam.intersection(control)?.area ?: return null
        val denominator = minOf(sam.area, control.area)
        val overlapPermille = ((overlap * 1000) / denominator).toInt().coerceIn(0, 1000)
        if (overlapPermille < 500) return null
        if (fitsDisplay(sam, display, density))
            return GuideTargetFusion(sam, "sam_visual", overlapPermille, false)
        val edge = !fitsDisplay(control, display, density)
        return GuideTargetFusion(control, if (edge) "sam_ui_edge_bounded" else "sam_ui_bounded",
            overlapPermille, edge)
    }

    @Deprecated("Use fuseTarget so acceptance provenance and edge adjustment are retained")
    fun displayTarget(sam: GuideBox, ui: GuideBox?, display: GuideBox, density: Float): GuideBox? =
        fuseTarget(sam, ui, display, density)?.target
}

object GuidePanelLayout {
    /** GuidingHand may move only its own window, and never onto the highlighted target. */
    fun acceptsDrag(candidate: GuideBox, target: GuideBox?, gap: Int): Boolean {
        require(gap >= 0)
        return candidate.area > 0 && target?.inflate(gap)?.intersects(candidate) != true
    }

    /** A missing result means collapse safely rather than obscure the pointed region or clip controls. */
    fun place(safe: GuideBox, desiredWidth: Int, desiredHeight: Int, minimumHeight: Int,
              target: GuideBox?, gap: Int, preferred: GuideBox? = null, anchor: GuideBox? = null): GuidePlacement? {
        require(desiredWidth > 0 && desiredHeight > 0 && minimumHeight > 0 && gap >= 0)
        if (safe.width <= 0 || safe.height < minimumHeight) return null
        val width = minOf(desiredWidth, safe.width)
        val exclusions = listOfNotNull(target?.inflate(gap), anchor?.inflate(gap))
        fun valid(box: GuideBox) = safe.contains(box) && exclusions.none { it.intersects(box) }
        if (anchor != null) {
            val near = listOf(
                GuideBox(anchor.left, anchor.bottom + gap, anchor.left + width, anchor.bottom + gap + desiredHeight),
                GuideBox(anchor.left, anchor.top - gap - desiredHeight, anchor.left + width, anchor.top - gap),
                GuideBox(anchor.right + gap - width, anchor.top, anchor.right + gap, anchor.top + desiredHeight),
                GuideBox(anchor.left - gap - width, anchor.top, anchor.left - gap, anchor.top + desiredHeight)
            ).map { box ->
                val dx = when {
                    box.right > safe.right -> safe.right - box.right
                    box.left < safe.left -> safe.left - box.left
                    else -> 0
                }
                val dy = when {
                    box.bottom > safe.bottom -> safe.bottom - box.bottom
                    box.top < safe.top -> safe.top - box.top
                    else -> 0
                }
                GuideBox(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
            }
            near.firstOrNull { valid(it) }?.let { return GuidePlacement(it, it.height < desiredHeight) }

            // When the full card cannot fit, keep it attached to Help and let its content
            // scroll. This is preferable to dropping the card to the bottom of the screen.
            val attached = listOf(
                GuideBox(anchor.left, anchor.bottom + gap, anchor.left + width,
                    minOf(safe.bottom, anchor.bottom + gap + desiredHeight)),
                GuideBox(anchor.left, maxOf(safe.top, anchor.top - gap - desiredHeight), anchor.left + width,
                    anchor.top - gap),
                GuideBox(anchor.right + gap - width, anchor.top, anchor.right + gap,
                    minOf(safe.bottom, anchor.top + desiredHeight)),
                GuideBox(anchor.left - gap - width, anchor.top, anchor.left - gap,
                    minOf(safe.bottom, anchor.top + desiredHeight))
            ).map { box ->
                val dx = when {
                    box.right > safe.right -> safe.right - box.right
                    box.left < safe.left -> safe.left - box.left
                    else -> 0
                }
                val dy = when {
                    box.bottom > safe.bottom -> safe.bottom - box.bottom
                    box.top < safe.top -> safe.top - box.top
                    else -> 0
                }
                GuideBox(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
            }.filter { it.height >= minimumHeight }
            attached.firstOrNull { valid(it) }?.let { return GuidePlacement(it, it.height < desiredHeight) }
        }
        if (preferred != null && preferred.width == width && preferred.height == minOf(desiredHeight, safe.height) &&
            valid(preferred))
            return GuidePlacement(preferred, preferred.height < desiredHeight)
        val lanes = mutableListOf(safe)
        val exclusion = target?.inflate(gap)
        if (exclusion != null && safe.intersects(exclusion)) {
            lanes.clear()
            // Prefer the lower space when a full panel fits; otherwise choose the largest usable lane.
            lanes += GuideBox(safe.left, maxOf(safe.top, exclusion.bottom), safe.right, safe.bottom)
            lanes += GuideBox(safe.left, safe.top, safe.right, minOf(safe.bottom, exclusion.top))
            lanes += GuideBox(safe.left, safe.top, minOf(safe.right, exclusion.left), safe.bottom)
            lanes += GuideBox(maxOf(safe.left, exclusion.right), safe.top, safe.right, safe.bottom)
        }
        val candidates = lanes.filter { it.width >= width && it.height >= minimumHeight }
        val lane = candidates.firstOrNull { it.height >= desiredHeight } ?: candidates.maxByOrNull { it.height } ?: return null
        val height = minOf(desiredHeight, lane.height)
        val box = GuideBox(lane.right - width, lane.bottom - height, lane.right, lane.bottom)
        if (!valid(box)) return null
        return GuidePlacement(box, height < desiredHeight)
    }

    /** Fraction of target area hidden by a panel; use the padded ring bounds as the target for stricter checks. */
    fun coverageRatio(panel: GuideBox, target: GuideBox): Double {
        if (target.width == 0 || target.height == 0) return 0.0
        val width = (minOf(panel.right, target.right) - maxOf(panel.left, target.left)).coerceAtLeast(0)
        val height = (minOf(panel.bottom, target.bottom) - maxOf(panel.top, target.top)).coerceAtLeast(0)
        return width.toDouble() * height / (target.width.toDouble() * target.height)
    }
}
