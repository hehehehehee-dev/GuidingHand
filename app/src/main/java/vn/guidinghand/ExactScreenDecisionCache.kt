package vn.guidinghand

/** Small RAM-only cache. Keys contain hashes, never screenshots, speech, or provider credentials. */
internal class ExactScreenDecisionCache<T>(
    private val ttlMillis: Long = 30_000,
    private val capacity: Int = 8,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    private data class Entry<T>(val value: T, val expiresAt: Long)
    private val entries = object : LinkedHashMap<String, Entry<T>>(capacity, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<T>>?) = size > capacity
    }

    init { require(ttlMillis > 0 && capacity > 0) }

    @Synchronized fun get(key: String): T? {
        val now = clock()
        entries.entries.removeAll { it.value.expiresAt <= now }
        return entries[key]?.value
    }

    @Synchronized fun put(key: String, value: T) {
        entries[key] = Entry(value, clock() + ttlMillis)
    }

    @Synchronized fun clear() = entries.clear()
    @Synchronized fun size() = entries.size
}
