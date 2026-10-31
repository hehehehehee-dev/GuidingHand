package vn.guidinghand

/** Structured speech intent from the planner. Native code never derives this from copy. */
enum class SpeechPurpose { GENERAL, RECIPIENT, MESSAGE_CONTENT }

enum class VoiceDraftPhase { EMPTY, REVIEW, CONFIRMED, COPIED }

/**
 * Volatile message content for the current guide session only.
 *
 * This type has no persistence, serialization or logging surface. The backing character
 * array is overwritten whenever the session is cleared or replaced. Target-app input is
 * deliberately outside this state machine.
 */
class VoiceDraftSession {
    var purpose: SpeechPurpose = SpeechPurpose.GENERAL; private set
    var phase: VoiceDraftPhase = VoiceDraftPhase.EMPTY; private set
    private var characters: CharArray? = null
    private var active = false

    val hasDraft get() = characters?.isNotEmpty() == true

    fun begin(nextPurpose: SpeechPurpose) {
        clear()
        purpose = nextPurpose
        active = true
    }

    fun capture(transcript: String): Boolean {
        if (!active) return false
        val clean = transcript.trim()
        if (clean.isEmpty() || clean.length > 500 || clean.contains('\n') || SafetyPolicy.sensitiveGoal(clean)) {
            clear()
            return false
        }
        characters?.fill('\u0000')
        characters = clean.toCharArray()
        phase = VoiceDraftPhase.REVIEW
        return true
    }

    fun confirmReview(): Boolean {
        if (phase != VoiceDraftPhase.REVIEW || !hasDraft) return false
        phase = VoiceDraftPhase.CONFIRMED
        return true
    }

    /** Clipboard writing is implemented separately and only after this explicit transition. */
    fun markCopied(): Boolean {
        if (purpose != SpeechPurpose.MESSAGE_CONTENT || phase != VoiceDraftPhase.CONFIRMED || !hasDraft) return false
        phase = VoiceDraftPhase.COPIED
        return true
    }

    fun textForReview(): String? = if (phase == VoiceDraftPhase.REVIEW) characters?.concatToString() else null
    fun textForExplicitCopy(): String? = if (purpose == SpeechPurpose.MESSAGE_CONTENT && phase == VoiceDraftPhase.CONFIRMED) characters?.concatToString() else null

    /** General goals and recipient answers leave the review state only after one explicit tap. */
    fun consumeConfirmedInput(): String? {
        if (purpose == SpeechPurpose.MESSAGE_CONTENT || phase != VoiceDraftPhase.CONFIRMED) return null
        val value = characters?.concatToString() ?: return null
        clear()
        return value
    }

    fun clear() {
        characters?.fill('\u0000')
        characters = null
        phase = VoiceDraftPhase.EMPTY
        purpose = SpeechPurpose.GENERAL
        active = false
    }
}
