package vn.guidinghand

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/** Require both an awake display and a dismissed keyguard, including swipe-only locks. */
object DeviceVisibility {
    fun allowed(interactive: Boolean, keyguardLocked: Boolean, deviceLocked: Boolean) =
        interactive && !keyguardLocked && !deviceLocked

    fun allowed(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return allowed(context.getSystemService(PowerManager::class.java).isInteractive,
            keyguard.isKeyguardLocked, keyguard.isDeviceLocked)
    }
}
