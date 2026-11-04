package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class BusyElapsedTest {
    @Test fun vietnameseCountsUpFromZero() {
        assertEquals("Đã chờ 0 giây…", busyElapsedText(0, true))
        assertEquals("Đã chờ 5 giây…", busyElapsedText(5, true))
        assertEquals("Đã chờ 12 giây…", busyElapsedText(12, true))
    }
    @Test fun englishCountsUpFromZero() {
        assertEquals("Waited 0s…", busyElapsedText(0, false))
        assertEquals("Waited 5s…", busyElapsedText(5, false))
        assertEquals("Waited 12s…", busyElapsedText(12, false))
    }
    @Test fun negativeClampsToZero() {
        assertEquals("Đã chờ 0 giây…", busyElapsedText(-3, true))
        assertEquals("Waited 0s…", busyElapsedText(-3, false))
    }
}
