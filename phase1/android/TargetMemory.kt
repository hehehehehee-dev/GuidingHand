package vn.guidinghand

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object TargetBinding {
    fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun context(goal: String, screen: ScreenState) = hash(goal + ":" + screen.fingerprint)
    fun node(node: ScreenNode) = hash(node.id + ":" + node.text)
    fun resolve(goal: String, screen: ScreenState, savedContext: String, savedPackage: String, savedNode: String, region: Region): ScreenNode? {
        if (!screen.complete || SafetyPolicy.sensitive(screen) || savedPackage != screen.packageName || context(goal, screen) != savedContext) return null
        return screen.nodes.singleOrNull { it.clickable && !it.password && it.region == region && node(it) == savedNode }
    }
}

/** Stores hashes and bounds, not screen text or images. A remembered position alone never authorizes a target. */
class TargetMemory(context: Context) {
    private val prefs = context.getSharedPreferences("GuidingHandTargets", Context.MODE_PRIVATE)
    fun remember(goal: String, screen: ScreenState, node: ScreenNode) {
        val region = node.region ?: return
        val records = try { JSONArray(prefs.getString("targets", "[]")) } catch (_: Exception) { JSONArray() }
        val key = TargetBinding.context(goal, screen)
        val entry = JSONObject().put("key", key).put("package", screen.packageName)
            .put("node", TargetBinding.node(node)).put("region", JSONArray(listOf(region.left, region.top, region.right, region.bottom)))
        val kept = (0 until records.length()).map { records.getJSONObject(it) }.filter { it.optString("key") != key }.takeLast(63)
        prefs.edit().putString("targets", JSONArray(kept + entry).toString()).apply()
    }
    fun find(goal: String, screen: ScreenState): ScreenNode? {
        if (!screen.complete || SafetyPolicy.sensitive(screen)) return null
        return try {
            val records = JSONArray(prefs.getString("targets", "[]")); val key = TargetBinding.context(goal, screen)
            val record = (0 until records.length()).map { records.getJSONObject(it) }.lastOrNull { it.optString("key") == key && it.optString("package") == screen.packageName } ?: return null
            val bounds = record.getJSONArray("region")
            val region = Region(bounds.getDouble(0).toFloat(), bounds.getDouble(1).toFloat(), bounds.getDouble(2).toFloat(), bounds.getDouble(3).toFloat())
            TargetBinding.resolve(goal, screen, key, record.optString("package"), record.optString("node"), region)
        } catch (_: Exception) { null }
    }
    fun clear() { prefs.edit().remove("targets").apply() }
}
