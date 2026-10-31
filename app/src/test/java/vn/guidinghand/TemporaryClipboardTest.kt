package vn.guidinghand

import org.junit.Assert.*
import org.junit.Test

class TemporaryClipboardTest {
    private class Port : ClipboardPort {
        var value: String? = null
        override fun write(text: String) { value = text }
        override fun read() = value
        override fun clear() { value = null }
    }

    @Test fun copyRequiresExplicitCallAndTimeoutClearsOnlyTheOwnedText() {
        val port = Port(); var timeout: (() -> Unit)? = null
        val clipboard = TemporaryClipboard(port, { _, action -> timeout = action })
        assertNull(port.value)
        assertTrue(clipboard.copyAfterExplicitConsent("Xin chào Minh"))
        assertEquals("Xin chào Minh", port.value)
        timeout!!.invoke()
        assertNull(port.value)
    }

    @Test fun changedClipboardIsNeverErasedByGuidingHand() {
        val port = Port(); var timeout: (() -> Unit)? = null
        val clipboard = TemporaryClipboard(port, { _, action -> timeout = action })
        assertTrue(clipboard.copyAfterExplicitConsent("Tin của bác"))
        port.value = "Nội dung khác của người dùng"
        timeout!!.invoke()
        assertEquals("Nội dung khác của người dùng", port.value)
        assertFalse(clipboard.clearIfOwned())
    }

    @Test fun sensitiveMultilineAndOversizedContentCannotReachClipboard() {
        val port = Port(); val clipboard = TemporaryClipboard(port, { _, _ -> })
        for (value in listOf("Mã OTP 123456", "a\nb", "a".repeat(501))) assertFalse(clipboard.copyAfterExplicitConsent(value))
        assertNull(port.value)
    }
}
