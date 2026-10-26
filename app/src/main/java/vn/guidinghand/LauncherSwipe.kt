package vn.guidinghand

/** Fixed launcher-only gestures. Coordinates stay inside the workspace, away from system edges. */
data class SwipeLine(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

enum class LauncherSwipe {
    NEXT_PAGE_LEFT, PREVIOUS_PAGE_RIGHT, ALL_APPS_UP;

    companion object { val initial = NEXT_PAGE_LEFT }
    fun next() = when (this) {
        NEXT_PAGE_LEFT -> PREVIOUS_PAGE_RIGHT
        PREVIOUS_PAGE_RIGHT -> ALL_APPS_UP
        ALL_APPS_UP -> NEXT_PAGE_LEFT
    }

    val line: SwipeLine get() = when (this) {
        NEXT_PAGE_LEFT -> SwipeLine(.78f, .52f, .22f, .52f)
        PREVIOUS_PAGE_RIGHT -> SwipeLine(.22f, .52f, .78f, .52f)
        ALL_APPS_UP -> SwipeLine(.46f, .68f, .46f, .38f)
    }
}

/** A fresh visible launcher tree is required before a user-requested synthetic swipe. */
object LauncherSwipePolicy {
    fun allowed(screen: ScreenState?, homePackage: String?, unlocked: Boolean,
                gestureCapability: Boolean): Boolean {
        if (!unlocked || !gestureCapability || homePackage.isNullOrBlank() || screen == null) return false
        return screen.complete && screen.packageName == homePackage && !SafetyPolicy.sensitive(screen) &&
            screen.nodes.none { it.focused && (it.className.contains("EditText") || it.className.contains("AutoCompleteTextView")) }
    }
}
