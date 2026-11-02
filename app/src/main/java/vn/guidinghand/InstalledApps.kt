package vn.guidinghand

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Labels/packages only. Icon positions always come from the current screen, never cached coordinates. */
object InstalledApps {
    data class App(val label: String, val packageName: String)
    @Volatile private var cached: List<App> = emptyList()
    @Volatile private var refreshed = 0L
    @Synchronized fun read(context: Context): List<App> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - refreshed > 60_000 || cached.isEmpty()) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            cached = context.packageManager.queryIntentActivities(intent, 0).map {
                App(it.loadLabel(context.packageManager).toString().take(100), it.activityInfo.packageName)
            }.filter { it.packageName != context.packageName }.distinctBy { it.packageName }.take(300)
            refreshed = now
        }
        return cached
    }

    /** Grounded device preference only. Spark still decides whether a browser fits the goal. */
    fun defaultBrowser(context: Context, installed: List<App> = read(context)): App? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val packageName = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName ?: return null
        return installed.singleOrNull { it.packageName == packageName }
    }
}
