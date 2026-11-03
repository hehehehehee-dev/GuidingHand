package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class VoiceWindowLeaseTest {
    @Test fun pendingAndActiveMicrophoneWindowsAlwaysSuppressTheOverlay() {
        val lease = VoiceWindowLease()
        val id = lease.reserve()!!
        assertTrue(lease.suppressOverlay(false))
        assertNull(lease.reserve())
        assertTrue(lease.opened(id))
        assertTrue(lease.suppressOverlay(false))
        assertTrue(lease.closed(id))
        assertFalse(lease.suppressOverlay(false))
    }

    @Test fun staleCloseCannotReleaseANewerWindow() {
        val lease = VoiceWindowLease()
        val first = lease.reserve()!!
        assertTrue(lease.failed(first))
        val second = lease.reserve()!!
        assertFalse(lease.closed(first))
        assertTrue(lease.suppressOverlay(false))
        assertTrue(lease.closed(second))
    }
}
