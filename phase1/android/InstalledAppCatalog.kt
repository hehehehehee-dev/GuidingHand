package vn.guidinghand

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** Disk-backed launchable-app index. Refresh after package changes; never trust a cached launch intent. */
class InstalledAppCatalog(private val context: Context) {
    private val prefs = context.getSharedPreferences("GuidingHandAppIndex", Context.MODE_PRIVATE)
    @Volatile private var items: List<SpokenApp> = try {
        val data = JSONArray(prefs.getString("apps", "[]"))
        (0 until data.length()).map { data.getJSONObject(it) }.map { SpokenApp(it.getString("label"), it.getString("package")) }
    } catch (_: Exception) { emptyList() }
    fun snapshot() = items
    fun refresh() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val fresh = context.packageManager.queryIntentActivities(query, 0).map {
            SpokenApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName)
        }.filter { it.packageName != context.packageName }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        items = fresh
        prefs.edit().putString("apps", JSONArray(fresh.map { JSONObject().put("label", it.label).put("package", it.packageName) }).toString()).apply()
    }
}
