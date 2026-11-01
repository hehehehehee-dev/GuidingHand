package vn.guidinghand

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface PathStore {
    fun familiar(task: GuideTask): Boolean
    fun savePath(task: GuideTask, steps: List<Pair<String, Long>>)
}
class FamilyPreferences(private val context: Context) : PathStore {
    private val prefs = context.getSharedPreferences("GuidingHand", Context.MODE_PRIVATE)
    var language: String
        get() = prefs.getString("language", "en")!!
        set(value) {
            val selected = if (value == "vi") "vi" else "en"
            prefs.edit().putString("language", selected).apply()
            AppLanguage.apply(context, selected)
        }
    var bubbleVisible: Boolean
        get() = prefs.getBoolean("bubbleVisible", true)
        set(value) { prefs.edit().putBoolean("bubbleVisible", value).apply() }
    var metaGuideEnabled: Boolean
        get() = prefs.getBoolean("metaGuideEnabled", false)
        set(value) { prefs.edit().putBoolean("metaGuideEnabled", value).apply() }
    var metaConsentRevision: Int
        get() = prefs.getInt("metaConsentRevision", 0)
        set(value) { prefs.edit().putInt("metaConsentRevision", value).apply() }
    var drawerLauncher: String
        get() = prefs.getString("drawerLauncher", "").orEmpty()
        set(value) { prefs.edit().putString("drawerLauncher", value).apply() }
    var metaClientToken: String
        get() = prefs.getString("metaClientToken", "").orEmpty()
        set(value) { prefs.edit().putString("metaClientToken", value).apply() }
    var speechLanguage: String
        get() = prefs.getString("speechLanguage", language)!!
        set(value) { prefs.edit().putString("speechLanguage", if (value == "vi") "vi" else "en").apply() }
    var zaloAlias: String
        get() = prefs.getString("zaloAlias", "").orEmpty()
        set(value) { prefs.edit().putString("zaloAlias", value.trim()).apply() }
    var instagramAlias: String
        get() = prefs.getString("instagramAlias", "").orEmpty()
        set(value) { prefs.edit().putString("instagramAlias", value.trim()).apply() }
    var phone: String
        get() = prefs.getString("phone", "")!!
        set(value) { prefs.edit().putString("phone", value).apply() }
    var contact: String
        get() = prefs.getString("contact", "")!!
        set(value) { prefs.edit().putString("contact", value).apply() }
    var network: String
        get() = prefs.getString("network", "")!!
        set(value) { prefs.edit().putString("network", value).apply() }
    var photo: String
        get() = prefs.getString("photo", "")!!
        set(value) { prefs.edit().putString("photo", value).apply() }
    var address: String
        get() = prefs.getString("address", "")!!
        set(value) { prefs.edit().putString("address", value).apply() }
    override fun familiar(task: GuideTask) = prefs.contains("path-${task.id}")
    override fun savePath(task: GuideTask, steps: List<Pair<String, Long>>) {
        val json = JSONObject().put("task", task.label).put("completedAt", System.currentTimeMillis())
            .put("steps", JSONArray(steps.map { JSONObject().put("goal", it.first).put("at", it.second) }))
        prefs.edit().putString("path-${task.id}", json.toString()).apply()
    }
}
