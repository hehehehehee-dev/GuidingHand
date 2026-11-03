package vn.guidinghand

/** Main-thread lease that prevents two microphone windows and suppresses every overlay until close. */
class VoiceWindowLease {
    private var sequence = 0L
    var activeId: Long = 0L; private set
    var opening: Boolean = false; private set

    fun reserve(): Long? {
        if (opening || activeId != 0L) return null
        opening = true
        activeId = ++sequence
        return activeId
    }

    fun opened(id: Long): Boolean {
        if (id == 0L || id != activeId) return false
        opening = false
        return true
    }

    fun pending(id: Long) = id != 0L && id == activeId && opening

    fun failed(id: Long): Boolean {
        if (id == 0L || id != activeId) return false
        opening = false; activeId = 0L
        return true
    }

    fun closed(id: Long): Boolean {
        if (id == 0L || id != activeId) return false
        opening = false; activeId = 0L
        return true
    }

    fun suppressOverlay(activityActive: Boolean) = opening || activeId != 0L || activityActive
}
