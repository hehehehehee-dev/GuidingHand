package vn.guidinghand

import android.content.Context

enum class VisionProfile(val key: String, val accentHex: String) {
    HIGH_CONTRAST("high_contrast", "#FFE600"),
    PROTANOPIA("protanopia", "#00E5FF"),
    DEUTERANOPIA("deuteranopia", "#FFD166"),
    TRITANOPIA("tritanopia", "#FFB3E6"),
    MONOCHROME("monochrome", "#FFFFFF");
    companion object { fun from(value: String) = entries.firstOrNull { it.key == value } ?: HIGH_CONTRAST }
}

enum class GuideCardSize(val key: String, val fraction: Float) {
    COMPACT("compact", .34f), STANDARD("standard", .42f), LARGE("large", .56f);
    companion object { fun from(value: String) = entries.firstOrNull { it.key == value } ?: STANDARD }
}

/** Family slots and a user session for our backend. No Meta/provider credential belongs here. */
class FamilyPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("GuidingHand", Context.MODE_PRIVATE)
    var voiceConsent: Boolean
        get() = prefs.getBoolean("voiceInputConsent", false)
        set(value) { prefs.edit().putBoolean("voiceInputConsent", value).apply() }
    var devPairNonce: String
        get() = prefs.getString("devPairNonce", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("devPairNonce", it).apply()
        }
        set(value) { prefs.edit().putString("devPairNonce", value).apply() }
    var devUsbBridge: Boolean
        get() = prefs.getBoolean("devUsbBridge", false)
        set(value) { prefs.edit().putBoolean("devUsbBridge", value).apply() }
    var respectfulLanguage: RespectfulLanguage
        get() = RespectfulLanguage(UserAddress.from(prefs.getString("userAddress", "bac").orEmpty()),
            AssistantAddress.from(prefs.getString("assistantAddress", "toi").orEmpty()),
            GuidanceTone.from(prefs.getString("guidanceTone", "gentle").orEmpty()))
        set(value) { prefs.edit().putString("userAddress", value.user.key).putString("assistantAddress", value.assistant.key)
            .putString("guidanceTone", value.tone.key).apply() }
    var devStandIn: Boolean
        get() = prefs.getBoolean("phase1DevStandIn", false)
        set(value) { prefs.edit().putBoolean("phase1DevStandIn", value).apply() }
    var language: String
        get() = prefs.getString("language", "en").orEmpty()
        set(value) { prefs.edit().putString("language", if (value == "en") "en" else "vi").apply() }
    var phone: String
        get() = prefs.getString("phone", "").orEmpty()
        set(value) { prefs.edit().putString("phone", value.trim()).apply() }
    var contact: String
        get() = prefs.getString("contact", "").orEmpty()
        set(value) { prefs.edit().putString("contact", value.trim()).apply() }
    var photo: String
        get() = prefs.getString("photo", "").orEmpty()
        set(value) { prefs.edit().putString("photo", value.trim()).apply() }
    var backendUrl: String
        get() = prefs.getString("coreBackendUrl", "").orEmpty()
        set(value) { prefs.edit().putString("coreBackendUrl", value.trim().trimEnd('/')).apply() }
    var backendSession: String
        get() = prefs.getString("coreBackendSession", "").orEmpty()
        set(value) { prefs.edit().putString("coreBackendSession", value.trim()).apply() }
    var screenConsent: Boolean
        get() = prefs.getBoolean("coreScreenConsent", false)
        set(value) { prefs.edit().putBoolean("coreScreenConsent", value).apply() }
    var visionProfile: VisionProfile
        get() = VisionProfile.from(prefs.getString("visionProfile", VisionProfile.HIGH_CONTRAST.key).orEmpty())
        set(value) { prefs.edit().putString("visionProfile", value.key).apply() }
    var showPointer: Boolean
        get() = prefs.getBoolean("showGuidePointer", true)
        set(value) { prefs.edit().putBoolean("showGuidePointer", value).apply() }
    var reduceMotion: Boolean
        get() = prefs.getBoolean("reduceGuideMotion", true)
        set(value) { prefs.edit().putBoolean("reduceGuideMotion", value).apply() }
    var cardSize: GuideCardSize
        get() = GuideCardSize.from(prefs.getString("guideCardSize", GuideCardSize.STANDARD.key).orEmpty())
        set(value) { prefs.edit().putString("guideCardSize", value.key).apply() }
}
