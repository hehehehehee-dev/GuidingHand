package vn.guidinghand

/** A failed optional second screenshot must not be retried on every guide step. */
class SecondCaptureBackoff(private val cooldownMs: Long = 30_000L) {
    private var failedWindow = -1
    private var retryAt = 0L

    fun shouldSkip(windowId: Int, nowMs: Long) = windowId == failedWindow && nowMs < retryAt

    fun internalError(windowId: Int, nowMs: Long) {
        failedWindow = windowId
        retryAt = nowMs + cooldownMs
    }

    fun succeeded(windowId: Int) {
        if (failedWindow == windowId) { failedWindow = -1; retryAt = 0L }
    }
}
