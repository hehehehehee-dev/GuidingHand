package vn.guidinghand

import java.net.URI

object BackendEndpoint {
    fun base(prefs: FamilyPreferences): String? = try {
        val uri = URI(prefs.backendUrl)
        val secure = uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.host !in setOf("localhost", "127.0.0.1", "::1")
        val port = BuildConfig.DEV_BACKEND_PORT
        val usbDevelopment = BuildConfig.DEBUG && prefs.devUsbBridge && uri.toString().trimEnd('/') == "http://127.0.0.1:$port"
        if ((secure || usbDevelopment) && uri.userInfo == null && uri.query == null && uri.fragment == null) uri.toString().trimEnd('/') else null
    } catch (_: Exception) { null }
}
