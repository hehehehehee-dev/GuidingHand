package vn.guidinghand

/** Elapsed-wait copy for the busy guide card. Pure text helper: unit-tested, never announces by itself. */
internal fun busyElapsedText(seconds: Long, vietnamese: Boolean): String {
    val s = seconds.coerceAtLeast(0)
    return if (vietnamese) "Đã chờ $s giây…" else "Waited ${s}s…"
}
