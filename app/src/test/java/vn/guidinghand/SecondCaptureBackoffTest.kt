package vn.guidinghand

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondCaptureBackoffTest {
    @Test fun optional_second_capture_retries_after_cooldown_or_window_change() {
        val backoff = SecondCaptureBackoff(30_000)
        backoff.internalError(8, 100)
        assertTrue(backoff.shouldSkip(8, 101))
        assertFalse(backoff.shouldSkip(9, 101))
        assertFalse(backoff.shouldSkip(8, 30_100))
        backoff.succeeded(8)
        assertFalse(backoff.shouldSkip(8, 102))
    }
}
