package vn.guidinghand
import android.content.Context
import android.graphics.Bitmap

object GuideModelFactory {
    fun create(context: Context, prefs: FamilyPreferences, fresh: suspend () -> ScreenState?, capture: suspend (ScreenState) -> Bitmap?): GuideModel =
        CoreBackendModel(context, prefs, fresh, capture)
}
