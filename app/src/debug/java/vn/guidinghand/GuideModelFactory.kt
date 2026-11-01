package vn.guidinghand
import android.content.Context
import android.graphics.Bitmap

object GuideModelFactory {
    fun create(context: Context, prefs: FamilyPreferences, fresh: suspend () -> ScreenState?, capture: suspend (ScreenState) -> Bitmap?): GuideModel =
        if (prefs.devStandIn) MockModel(context, fresh) else CoreBackendModel(context, prefs, fresh, capture)
}
