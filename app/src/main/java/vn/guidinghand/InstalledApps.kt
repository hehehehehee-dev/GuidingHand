package vn.guidinghand

import android.content.Context
import android.content.Intent

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
}
