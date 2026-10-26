package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class DeviceVisibilityTest {
    @Test fun onlyAwakeAndFullyUnlockedMayShowGuide() {
        for (awake in listOf(false, true)) for (keyguard in listOf(false, true)) for (locked in listOf(false, true)) {
            assertEquals("awake=$awake keyguard=$keyguard locked=$locked", awake && !keyguard && !locked,
                DeviceVisibility.allowed(awake, keyguard, locked))
        }
    }
    @Test fun turningScreenOnDoesNotRevealGuideBeforeUnlock() {
        assertFalse(DeviceVisibility.allowed(false, true, true))
        assertFalse(DeviceVisibility.allowed(true, true, true))
        assertFalse(DeviceVisibility.allowed(true, true, false))
        assertTrue(DeviceVisibility.allowed(true, false, false))
    }
}
