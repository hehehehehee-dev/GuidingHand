package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class VerifyTimeoutTest {
    @Test fun noStartNeverExpires() {
        assertFalse(shouldConcludeVerify(null, 999_999L))
    }
    @Test fun withinWindowKeepsWaiting() {
        assertFalse(shouldConcludeVerify(1_000L, 1_000L + VERIFY_TIMEOUT_MS - 1))
    }
    @Test fun pastDeadlineConcludes() {
        assertTrue(shouldConcludeVerify(1_000L, 1_000L + VERIFY_TIMEOUT_MS + 1))
    }
}
