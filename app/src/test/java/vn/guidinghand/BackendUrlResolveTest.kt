package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class BackendUrlResolveTest {
    private val cloud = "https://guidinghand-backend-gquf6kjoea-uc.a.run.app"
    private val loop = "http://127.0.0.1:8768"

    @Test fun blankFallsBackToCloud() {
        assertEquals(cloud, resolveBackendUrl(null, true, true, cloud))
        assertEquals(cloud, resolveBackendUrl("   ", false, false, cloud))
    }
    @Test fun cloudAlwaysPassesThrough() {
        assertEquals(cloud, resolveBackendUrl(cloud, true, false, cloud))
        assertEquals(cloud, resolveBackendUrl(cloud, false, true, cloud))
    }
    @Test fun candidateHonorsExplicitUsbPairing() {
        assertEquals(loop, resolveBackendUrl(loop, true, true, cloud))
    }
    @Test fun candidateIgnoresStaleLoopbackWithoutBridge() {
        assertEquals(cloud, resolveBackendUrl(loop, true, false, cloud))
    }
    @Test fun nonCandidateLoopbackUnchanged() {
        assertEquals(loop, resolveBackendUrl(loop, false, false, cloud))
    }
    @Test fun trimsTrailingSlash() {
        assertEquals(loop, resolveBackendUrl("$loop/", true, true, cloud))
    }
}
