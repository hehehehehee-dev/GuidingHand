package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class ExactScreenDecisionCacheTest {
    @Test fun exactEntriesExpireRemainBoundedAndClearOnSensitiveOrNewGoalBoundary() {
        var now = 100L
        val cache = ExactScreenDecisionCache<String>(ttlMillis = 50, capacity = 2) { now }
        cache.put("screen-a:goal-a", "first")
        assertEquals("first", cache.get("screen-a:goal-a"))
        assertNull(cache.get("screen-a:goal-b"))
        cache.put("screen-b:goal-a", "second")
        cache.put("screen-c:goal-a", "third")
        assertNull(cache.get("screen-a:goal-a"))
        assertEquals(2, cache.size())
        now += 51
        assertNull(cache.get("screen-b:goal-a"))
        assertEquals(0, cache.size())
        cache.put("screen-d:goal-a", "private")
        cache.clear()
        assertEquals(0, cache.size())
    }
}
