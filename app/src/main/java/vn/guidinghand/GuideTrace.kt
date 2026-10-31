package vn.guidinghand

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject

/** Only timings, state identifiers and verdicts. Never screen text, images, audio or tokens. */
object GuideTrace {
    private var sequence = 0L
    @Synchronized fun event(context: Context, event: String, millis: Long? = null,
                            code: String? = null, requestId: String? = null) {
        val row = JSONObject().put("sequence", ++sequence).put("uptime_ms", SystemClock.elapsedRealtime())
            .put("event", event.filter { it.isLetterOrDigit() || it == '_' }.take(48))
        millis?.let { row.put("duration_ms", it.coerceAtLeast(0)) }
        code?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,100}")) }?.let { row.put("code", it) }
        requestId?.takeIf { it.matches(Regex("[a-fA-F0-9-]{1,64}")) }?.let { row.put("request_id", it) }
        Log.i("GuidingHandTrace", row.toString())
        if (BuildConfig.DEBUG) try {
            val file = java.io.File(context.filesDir, "guide-trace.jsonl")
            if (file.length() > 512_000) file.writeText("")
            file.appendText(row.toString() + "\n")
        } catch (_: Exception) { /* Diagnostics never block guidance. */ }
    }
}
