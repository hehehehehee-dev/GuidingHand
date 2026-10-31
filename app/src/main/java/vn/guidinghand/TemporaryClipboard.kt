package vn.guidinghand

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

interface ClipboardPort {
    fun write(text: String)
    fun read(): String?
    fun clear()
}

/** Explicit, temporary clipboard ownership. It never pastes into a target app. */
class TemporaryClipboard(
    private val port: ClipboardPort,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val timeoutMs: Long = 120_000L
) {
    private var owned: CharArray? = null

    fun copyAfterExplicitConsent(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > 500 || clean.contains('\n') || SafetyPolicy.sensitiveGoal(clean)) return false
        clearIfOwned()
        return try {
            port.write(clean)
            owned = clean.toCharArray()
            schedule(timeoutMs) { clearIfOwned() }
            true
        } catch (_: Exception) { owned?.fill('\u0000'); owned = null; false }
    }

    fun clearIfOwned(): Boolean {
        val expected = owned ?: return false
        val same = try { port.read()?.toCharArray()?.contentEquals(expected) == true } catch (_: Exception) { false }
        if (same) try { port.clear() } catch (_: Exception) { /* Never clear an unverified clipboard. */ }
        expected.fill('\u0000'); owned = null
        return same
    }
}

class AndroidClipboard(context: Context) : ClipboardPort {
    private val app = context.applicationContext
    private val manager = app.getSystemService(ClipboardManager::class.java)
    override fun write(text: String) = manager.setPrimaryClip(ClipData.newPlainText("GuidingHand temporary message", text))
    override fun read(): String? = manager.primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.coerceToText(app)?.toString()
    override fun clear() = manager.clearPrimaryClip()
}

fun temporaryClipboard(context: Context) = TemporaryClipboard(AndroidClipboard(context), { delay, action ->
    Handler(Looper.getMainLooper()).postDelayed(action, delay)
})
