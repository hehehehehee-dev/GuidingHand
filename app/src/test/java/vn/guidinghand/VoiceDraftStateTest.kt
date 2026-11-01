package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class VoiceDraftStateTest {
    @Test fun visibleTranscriptIsReplacedAndClearedAtAPrivacyBoundary() {
        val visible = EphemeralDisplayText()
        val emptyRevision = visible.revision
        visible.replace("Bác nói: nội dung cũ")
        assertTrue(visible.revision > emptyRevision)
        assertEquals("Bác nói: nội dung cũ", visible.value())
        val firstRevision = visible.revision
        visible.replace("Bác nói: nội dung mới")
        assertTrue(visible.revision > firstRevision)
        assertEquals("Bác nói: nội dung mới", visible.value())
        val secondRevision = visible.revision
        visible.clear()
        assertTrue(visible.revision > secondRevision)
        assertTrue(visible.isEmpty)
        assertNull(visible.value())
        val clearedRevision = visible.revision
        visible.clear()
        assertEquals(clearedRevision, visible.revision)
    }

    @Test fun structuredPurposeIsRequiredAndCopyNeedsTwoExplicitTransitions() {
        val state = VoiceDraftSession()
        assertFalse(state.capture("Xin chào Minh"))
        state.begin(SpeechPurpose.MESSAGE_CONTENT)
        assertTrue(state.capture("  Xin chào Minh  "))
        assertEquals("Xin chào Minh", state.textForReview())
        assertNull(state.textForExplicitCopy())
        assertFalse(state.markCopied())
        assertTrue(state.confirmReview())
        assertNull(state.textForReview())
        assertEquals("Xin chào Minh", state.textForExplicitCopy())
        assertTrue(state.markCopied())
        assertEquals(VoiceDraftPhase.COPIED, state.phase)
    }

    @Test fun replacementDoesNotAppendAndClearRemovesTheDraft() {
        val state = VoiceDraftSession()
        state.begin(SpeechPurpose.MESSAGE_CONTENT)
        assertTrue(state.capture("Bản đầu"))
        assertTrue(state.capture("Bản nói lại"))
        assertEquals("Bản nói lại", state.textForReview())
        state.clear()
        assertFalse(state.hasDraft)
        assertEquals(VoiceDraftPhase.EMPTY, state.phase)
        assertEquals(SpeechPurpose.GENERAL, state.purpose)
    }

    @Test fun sensitiveMultilineEmptyAndOversizedDraftsFailClosed() {
        for (value in listOf("", "Mã OTP là 123456", "Dòng một\nDòng hai", "a".repeat(501))) {
            val state = VoiceDraftSession()
            state.begin(SpeechPurpose.MESSAGE_CONTENT)
            assertFalse(state.capture(value))
            assertFalse(state.hasDraft)
            assertEquals(VoiceDraftPhase.EMPTY, state.phase)
        }
    }

    @Test fun generalAndRecipientSpeechRequireReviewButCanNeverBecomeCopyContent() {
        val general = VoiceDraftSession()
        general.begin(SpeechPurpose.GENERAL)
        assertTrue(general.capture("Mở trình duyệt"))
        assertEquals("Mở trình duyệt", general.textForReview())
        assertTrue(general.confirmReview())
        assertNull(general.textForExplicitCopy())
        assertEquals("Mở trình duyệt", general.consumeConfirmedInput())
        assertFalse(general.hasDraft)

        val state = VoiceDraftSession()
        state.begin(SpeechPurpose.RECIPIENT)
        assertTrue(state.capture("Minh"))
        assertTrue(state.confirmReview())
        assertNull(state.textForExplicitCopy())
        assertFalse(state.markCopied())
        assertEquals("Minh", state.consumeConfirmedInput())
    }
}
