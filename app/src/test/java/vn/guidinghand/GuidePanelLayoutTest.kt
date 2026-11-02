package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class GuidePanelLayoutTest {
    @Test fun longTranscriptGetsScrollAffordanceButShortDraftDoesNot() {
        assertFalse(TranscriptPresentation.needsScrollHint("Gửi lời chào cho Minh"))
        assertTrue(TranscriptPresentation.needsScrollHint("a".repeat(121)))
        assertTrue(TranscriptPresentation.needsScrollHint("một\nhai\nba\nbốn"))
    }

    @Test fun boundedVisualGateIgnoresTinyChangesButRejectsAnimation() {
        val first = IntArray(100) { 0xff202020.toInt() }
        val tiny = first.copyOf().also { it[0] = 0xff303030.toInt() }
        val moving = first.copyOf().also { values -> repeat(20) { values[it] = 0xffffffff.toInt() } }
        assertTrue(VisualFrameStability.isStable(first, tiny))
        assertFalse(VisualFrameStability.isStable(first, moving))
        assertFalse(VisualFrameStability.isStable(first, IntArray(99)))
    }

    @Test fun ordinaryCoachingIsCompactButQuestionsAndSafetyStayExpanded() {
        for (status in listOf(GuideStatus.READING, GuideStatus.PLANNING, GuideStatus.VERIFYING,
            GuideStatus.RECOVERING, GuideStatus.WAITING_TAP, GuideStatus.WAITING_NAVIGATION))
            assertTrue(status.name, GuideCardMode.compact(status))
        for (status in listOf(GuideStatus.CLARIFYING, GuideStatus.CONFIRMING_INPUT,
            GuideStatus.CONFIRMING_COPY, GuideStatus.ASKING, GuideStatus.UNAVAILABLE,
            GuideStatus.UNSUPPORTED, GuideStatus.SAFETY_PAUSED, GuideStatus.DONE))
            assertFalse(status.name, GuideCardMode.compact(status))
    }

    @Test fun measuredCreamTextExceedsSevenToOne() {
        val ratio = GuideDesign.contrastRatio(GuideDesign.INK_HEX, GuideDesign.CARD_HEX)
        assertEquals(13.2583, ratio, .001)
        assertTrue(ratio >= 7.0)
        assertEquals(1.0, GuideDesign.contrastRatio("#FFFFFF", "#FFFFFF"), .0001)
        assertEquals(21.0, GuideDesign.contrastRatio("#000000", "#FFFFFF"), .0001)
    }
    @Test fun everyVisionProfileAccentExceedsSevenToOneAgainstGuideInk() {
        for (profile in VisionProfile.entries) {
            val ratio = GuideDesign.contrastRatio(GuideDesign.INK_HEX, profile.accentHex)
            assertTrue("${profile.key} contrast was $ratio", ratio >= 7.0)
        }
        assertEquals(VisionProfile.HIGH_CONTRAST, VisionProfile.from("unknown-profile"))
    }
    @Test fun bottomTargetMovesPanelAboveInsteadOfCoveringIt() {
        val safe = GuideBox(12, 30, 348, 760); val target = GuideBox(100, 680, 200, 740)
        val placement = GuidePanelLayout.place(safe, 336, 320, 200, target, 24)!!
        assertTrue(safe.contains(placement.bounds)); assertFalse(placement.bounds.intersects(target.inflate(24)))
        assertEquals(0.0, GuidePanelLayout.coverageRatio(placement.bounds, target.inflate(24)), .0)
    }
    @Test fun largeTextScrollsWithinFreeLaneWithoutShrinkingOrClippingFooter() {
        val safe = GuideBox(12, 30, 348, 760); val target = GuideBox(100, 260, 200, 350)
        val placement = GuidePanelLayout.place(safe, 336, 900, 240, target, 24)!!
        assertTrue(placement.scrollContent); assertTrue(placement.bounds.height >= 240)
        assertFalse(placement.bounds.intersects(target.inflate(24)))
    }
    @Test fun keyboardSafeBoundsAreNeverCrossed() {
        val safe = GuideBox(12, 30, 348, 470); val target = GuideBox(120, 100, 240, 150)
        val placement = GuidePanelLayout.place(safe, 336, 300, 200, target, 20)!!
        assertTrue(safe.contains(placement.bounds)); assertTrue(placement.bounds.bottom <= 470)
    }
    @Test fun impossiblePlacementCollapsesRatherThanCoversTarget() {
        val safe = GuideBox(12, 30, 348, 420)
        assertNull(GuidePanelLayout.place(safe, 336, 700, 240, GuideBox(20, 190, 340, 240), 24))
    }
    @Test fun unsafeDraggedPositionIsRejectedAndRepositioned() {
        val safe = GuideBox(12, 30, 348, 760); val target = GuideBox(100, 400, 200, 500)
        val preferred = GuideBox(12, 350, 348, 650)
        val placement = GuidePanelLayout.place(safe, 336, 300, 200, target, 24, preferred)!!
        assertFalse(placement.bounds.intersects(target.inflate(24)))
    }
    @Test fun ownWindowDragAcceptsClearCandidateAndRejectsPointedArea() {
        val target = GuideBox(100, 400, 200, 500)
        assertTrue(GuidePanelLayout.acceptsDrag(GuideBox(240, 120, 340, 220), target, 8))
        assertFalse(GuidePanelLayout.acceptsDrag(GuideBox(190, 390, 290, 490), target, 8))
        assertTrue(GuidePanelLayout.acceptsDrag(GuideBox(240, 120, 340, 220), null, 8))
        assertFalse(GuidePanelLayout.acceptsDrag(GuideBox(0, 0, 0, 0), null, 8))
    }
    @Test fun overlayIdentityChangesForLanguageAndReduceMotionButNotDuplicateEvents() {
        val state = GuideViewState(GuideStatus.IDLE, "")
        val safe = GuideBox(12, 30, 348, 760)
        val base = GuideRenderPreferences("vi", true, GuideCardSize.STANDARD,
            VisionProfile.HIGH_CONTRAST, true, GuideMascot.HAND, 1f)
        val key = GuideRenderIdentity.key(state, false, false, safe, base)
        assertEquals(key, GuideRenderIdentity.key(state, false, false, safe, base))
        assertNotEquals(key, GuideRenderIdentity.key(state, false, false, safe, base.copy(language = "en")))
        assertNotEquals(key, GuideRenderIdentity.key(state, false, false, safe, base.copy(reduceMotion = false)))
        assertNotEquals(key, GuideRenderIdentity.key(state, false, false, safe, base.copy(mascot = GuideMascot.YOUNG_HELPER)))
        assertNotEquals(key, GuideRenderIdentity.key(state, false, false, safe, base, ephemeralContentRevision = 1))
    }
    @Test fun overlayIdentityChangesWhenSpeakPurposeChangesWithoutParsingCopy() {
        val safe = GuideBox(12, 30, 348, 760)
        val preferences = GuideRenderPreferences("en", true, GuideCardSize.STANDARD,
            VisionProfile.HIGH_CONTRAST, true, GuideMascot.HAND, 1f)
        val general = GuideViewState(GuideStatus.CLARIFYING, "Please tell me more.",
            speechPurpose = SpeechPurpose.GENERAL)
        val message = general.copy(speechPurpose = SpeechPurpose.MESSAGE_CONTENT)
        assertNotEquals(GuideRenderIdentity.key(general, false, false, safe, preferences),
            GuideRenderIdentity.key(message, false, false, safe, preferences))
        val swipe = general.copy(status = GuideStatus.WAITING_NAVIGATION,
            navigationCue = NavigationCue.HOME_SWIPE_UP)
        assertNotEquals(GuideRenderIdentity.key(general, false, false, safe, preferences),
            GuideRenderIdentity.key(swipe, false, false, safe, preferences))
    }
    @Test fun placementSweepAcrossScreenAndLargeFontHeightsHasNoTargetCoverage() {
        val safe = GuideBox(12, 30, 348, 760)
        for (top in 40..680 step 40) for (height in listOf(220, 380, 600, 900)) {
            val target = GuideBox(120, top, 210, top + 48)
            val result = GuidePanelLayout.place(safe, 336, height, 210, target, 24) ?: continue
            assertTrue(safe.contains(result.bounds)); assertFalse(result.bounds.intersects(target.inflate(24)))
        }
    }
    @Test fun rawPaddingDetectsDisplayEdgeBeforeEdgeAwareRendering() {
        val display = GuideBox(0, 0, 360, 800)
        assertFalse(GuideRingGeometry.fitsDisplay(GuideBox(8, 400, 64, 456), display, 1f))
        assertFalse(GuideRingGeometry.fitsDisplay(GuideBox(300, 400, 352, 456), display, 1f))
        assertTrue(GuideRingGeometry.fitsDisplay(GuideBox(24, 24, 336, 776), display, 1f))
    }
    @Test fun fractionalDensityNeverRoundsPaddingAndStrokesBelowTheMinimum() {
        val target = GuideBox(100, 200, 180, 280)
        val padded = GuideRingGeometry.paddedBounds(target, 2.63f)
        assertEquals(64, target.left - padded.left)
        assertEquals(64, padded.bottom - target.bottom)
        assertFalse(GuideRingGeometry.fitsDisplay(GuideBox(63, 200, 180, 280), GuideBox(0, 0, 1000, 1000), 2.63f))
    }
    @Test fun oversizedSamHeaderMayUseVerifiedClickableBounds() {
        val display = GuideBox(0, 0, 1268, 2756)
        val sam = GuideBox(0, 105, 930, 285)
        val searchControl = GuideBox(118, 125, 910, 270)
        assertFalse(GuideRingGeometry.fitsDisplay(sam, display, 2.63f))
        assertEquals(searchControl, GuideRingGeometry.fuseTarget(sam, searchControl, display, 2.63f)?.target)
    }
    @Test fun edgeFusionRequiresStrongSamOverlapButNotImaginaryOffscreenPadding() {
        val display = GuideBox(0, 0, 1000, 1000)
        assertNull(GuideRingGeometry.fuseTarget(GuideBox(0, 100, 100, 200), GuideBox(300, 100, 400, 200), display, 1f))
        val accepted = GuideRingGeometry.fuseTarget(GuideBox(0, 100, 100, 200), GuideBox(5, 100, 100, 200), display, 1f)!!
        assertEquals("sam_ui_edge_bounded", accepted.mode)
        assertTrue(accepted.edgeAdjusted)
        assertEquals(GuideBox(5, 100, 100, 200), accepted.target)
        assertEquals(1000, accepted.overlapPermille)
    }
    @Test fun realEdgeTargetKeepsBothStrokeCentersInsideDisplay() {
        val display = GuideBox(0, 0, 360, 800)
        val bands = GuideRingGeometry.visibleBands(GuideBox(0, 0, 120, 80), display, 1f)
        assertTrue(bands.edgeAdjusted)
        assertEquals(3, bands.blackCenter.left)
        assertEquals(3, bands.blackCenter.top)
        assertEquals(9, bands.yellowCenter.left)
        assertEquals(9, bands.yellowCenter.top)
        assertTrue(display.contains(bands.blackCenter))
        assertTrue(display.contains(bands.yellowCenter))
    }
    @Test fun ordinaryTargetRetainsFullRingSpacing() {
        val display = GuideBox(0, 0, 360, 800)
        val bands = GuideRingGeometry.visibleBands(GuideBox(100, 200, 180, 280), display, 1f)
        assertFalse(bands.edgeAdjusted)
        assertEquals(79, bands.blackCenter.left)
        assertEquals(85, bands.yellowCenter.left)
    }
    @Test fun handPointerChoosesAVisibleSideAndOnlyOverlapsTargetSlightly() {
        val display = GuideBox(0, 0, 360, 800)
        val middle = GuideRingGeometry.pointerPlacement(GuideBox(120, 200, 220, 260), display, 56, 12)
        assertEquals(0f, middle.rotationDegrees)
        assertEquals(248, middle.bounds.top)
        assertTrue(display.contains(middle.bounds))
        val bottom = GuideRingGeometry.pointerPlacement(GuideBox(120, 740, 220, 800), display, 56, 12)
        assertEquals(180f, bottom.rotationDegrees)
        assertTrue(display.contains(bottom.bounds))
    }
    @Test fun panelStaysAttachedToHelpAnchorWhenSpaceAllows() {
        val safe = GuideBox(0, 0, 1080, 1920)
        val anchor = GuideBox(760, 1200, 960, 1280)
        val placement = GuidePanelLayout.placeAttachedToHelp(safe, 344, 520, 240, null, 16, anchor)!!
        assertFalse(placement.bounds.intersects(anchor.inflate(16)))
        assertTrue(placement.bounds.bottom == anchor.top - 16 || placement.bounds.top == anchor.bottom + 16 ||
            placement.bounds.right == anchor.left - 16 || placement.bounds.left == anchor.right + 16)
    }
    @Test fun oversizedPanelScrollsAboveHelpInsteadOfDroppingToBottom() {
        val safe = GuideBox(12, 30, 348, 760)
        val anchor = GuideBox(250, 560, 336, 632)
        val placement = GuidePanelLayout.placeAttachedToHelp(safe, 336, 600, 200, null, 12, anchor)!!
        assertTrue(placement.scrollContent)
        assertTrue(placement.bounds.bottom <= anchor.top - 12 || placement.bounds.top >= anchor.bottom + 12)
        assertFalse(placement.bounds.intersects(anchor.inflate(12)))
    }
    @Test fun attachedCoachNeverFallsBackToBottomWhenNoAdjacentLaneIsSafe() {
        val safe = GuideBox(0, 0, 360, 800)
        val help = GuideBox(270, 360, 350, 440)
        val targetBlocksEveryAdjacentLane = GuideBox(0, 0, 360, 800)
        assertNull(GuidePanelLayout.placeAttachedToHelp(
            safe, 320, 360, 200, targetBlocksEveryAdjacentLane, 8, help
        ))
    }
}

