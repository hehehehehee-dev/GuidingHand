package vn.guidinghand

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable private fun GuideTheme(content: @Composable () -> Unit) {
    val ink = Color(0xFF18332A); val cream = Color(0xFFFFFCF5)
    MaterialTheme(colorScheme = lightColorScheme(primary = ink, onPrimary = cream, surface = cream,
        onSurface = ink, background = cream, onBackground = ink, secondary = ink),
        typography = Typography(bodyLarge = TextStyle(fontSize = 20.sp), bodyMedium = TextStyle(fontSize = 20.sp),
            bodySmall = TextStyle(fontSize = 20.sp), labelLarge = TextStyle(fontSize = 22.sp)), content = content)
}

/** The sheen lives behind opaque content cards, never behind floating instructions. */
@Composable private fun GuideBackdrop(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFFFFCF5)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFFEDF2EB), Color(0xFFFFFCF5), Color(0xFFF6F0E3))))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .9f), Color.Transparent),
                    center = Offset(size.width * .85f, size.height * .13f), radius = size.width * .65f),
                    radius = size.width * .65f, center = Offset(size.width * .85f, size.height * .13f))
            }) { content() }
    }
}

/** An explicit Help tap can dismiss our own setup task; it never navigates another app. */
open class SetupActivity : ComponentActivity() {
    companion object {
        var foreground: java.lang.ref.WeakReference<SetupActivity>? = null
        internal val foregroundVisible = kotlinx.coroutines.flow.MutableStateFlow(false)
    }
    override fun onResume() {
        super.onResume(); foreground = java.lang.ref.WeakReference(this); foregroundVisible.value = true
    }
    override fun onPause() {
        if (foreground?.get() === this) foreground = null
        foregroundVisible.value = foreground?.get() != null
        super.onPause()
    }
}

class MainActivity : SetupActivity() {
    /** SharedPreferences change in SettingsActivity; this revision makes Compose read it again on return. */
    private var preferenceRevision by mutableIntStateOf(0)
    override fun onResume() {
        super.onResume()
        preferenceRevision++
    }
    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.let { name ->
            name.packageName == packageName && name.className == GuidingHandService::class.java.name
        } == true }
    }
    private fun configureDevelopment(source: Intent) {
        if (!BuildConfig.DEBUG) return
        val prefs = FamilyPreferences(this)
        val authenticated = source.getStringExtra("devPairNonce") == prefs.devPairNonce
        if (source.getBooleanExtra("configureCoreDev", false) && authenticated) {
            source.getStringExtra("backendSession")?.takeIf { it.matches(Regex("[a-f0-9]{48}")) }?.let {
                val port = BuildConfig.DEV_BACKEND_PORT
                prefs.backendUrl = "http://127.0.0.1:$port"; prefs.backendSession = it
                source.getStringExtra("devLanguage")?.takeIf { language -> language in setOf("vi", "en") }?.let { language ->
                    prefs.language = language
                }
                prefs.devUsbBridge = true; prefs.devStandIn = false
                prefs.devPairNonce = java.util.UUID.randomUUID().toString().replace("-", "")
                GuidingHandService.instance?.reloadModel()
            }
        } else if (source.getBooleanExtra("configureCloudDev", false) && authenticated) {
            // Return from the temporary USB transport without exposing, replacing, or clearing the
            // app-session token. This authenticated debug-only action is used by the developer tool.
            prefs.useCloudBackend(); prefs.devStandIn = false
            prefs.devPairNonce = java.util.UUID.randomUUID().toString().replace("-", "")
            GuidingHandService.instance?.reloadModel()
        }
        source.removeExtra("configureCoreDev"); source.removeExtra("configureCloudDev"); source.removeExtra("devPairNonce")
        source.removeExtra("backendSession"); source.removeExtra("devLanguage")
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); configureDevelopment(intent) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); configureDevelopment(intent)
        val prefs = FamilyPreferences(this)
        setContent { GuideTheme {
            val vi = remember(preferenceRevision) { prefs.language == "vi" }
            fun tr(v: String, e: String) = if (vi) v else e
            var detailsOpen by remember { mutableStateOf(false) }
            var serviceConnected by remember { mutableStateOf(GuidingHandService.instance != null) }
            var accessibilityOn by remember { mutableStateOf(accessibilityEnabled()) }
            var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
            var backendHealth by remember(preferenceRevision) { mutableStateOf(BackendHealthStatus.NOT_CONFIGURED) }
            LaunchedEffect(preferenceRevision) {
                var backendAttempts = 0
                repeat(12) {
                    accessibilityOn = accessibilityEnabled()
                    micGranted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    serviceConnected = GuidingHandService.instance != null
                    val localReady = accessibilityOn && micGranted && serviceConnected
                    if (localReady && backendAttempts < 2) {
                        backendHealth = BackendHealth.check(prefs)
                        backendAttempts++
                    }
                    if (accessibilityOn && micGranted && serviceConnected && backendHealth == BackendHealthStatus.READY) return@LaunchedEffect
                    delay(if (localReady) 4_000 else 500)
                }
            }
            val guideReady = accessibilityOn && micGranted && serviceConnected && backendHealth == BackendHealthStatus.READY
            val contact = prefs.contact.trim()
            fun startPhoto(recipient: String) {
                val service = GuidingHandService.instance
                if (service != null && guideReady) {
                    service.startPhotoTaskForRecipient(recipient)
                    moveTaskToBack(true)
                } else if (!accessibilityOn) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            GuideBackdrop {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.guidinghand_icon), contentDescription = null,
                                modifier = Modifier.size(50.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("GuidingHand", fontSize = 29.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                        }
                        Surface(color = if (guideReady) Color(0xFFE6F0E8) else Color(0xFFF1EEE4),
                            shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, guideInk)) {
                            Text(when {
                                guideReady -> tr("✓ Sẵn sàng", "✓ Ready")
                                !accessibilityOn -> tr("! Cần bật Trợ năng", "! Accessibility needed")
                                !micGranted -> tr("! Cần cho phép micro", "! Microphone needed")
                                !serviceConnected -> tr("… Đang kết nối", "… Connecting")
                                backendHealth == BackendHealthStatus.NOT_CONFIGURED -> tr("! Cần kết nối trợ lý", "! Assistant setup needed")
                                backendHealth == BackendHealthStatus.AUTH_REQUIRED -> tr("! Cần ghép lại trợ lý", "! Reconnect assistant")
                                backendHealth == BackendHealthStatus.UNREACHABLE -> tr("! Trợ lý đang ngoại tuyến", "! Assistant offline")
                                backendHealth == BackendHealthStatus.INCOMPATIBLE -> tr("! Cần cập nhật máy chủ", "! Server update needed")
                                else -> tr("! Chưa sẵn sàng", "! Not ready")
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = guideCream),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            FamilyPortrait(prefs.respectfulLanguage.user,
                                tr("Người dùng và người đồng hành", "The user and a companion"))
                            val wording = prefs.respectfulLanguage
                            Text(tr(wording.homeMessage, "A little guidance, so you can use your phone with confidence."),
                                fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    HomeJourney(vi, accentColor(prefs.visionProfile))

                    Button(onClick = {
                        val service = GuidingHandService.instance
                        if (!accessibilityOn) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        else if (!micGranted) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
                        else if (service == null) Unit
                        else if (backendHealth != BackendHealthStatus.READY) startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        else if (guideReady) service.openVoice()
                    }, enabled = guideReady || !accessibilityOn || !micGranted || (serviceConnected && backendHealth != BackendHealthStatus.READY),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(when {
                            guideReady -> tr("Nói việc cần giúp", "Speak to get help")
                            !accessibilityOn -> tr("Bật hỗ trợ nổi", "Enable floating help")
                            !micGranted -> tr("Cho phép micro", "Allow microphone")
                            !serviceConnected -> tr("Đang chuẩn bị…", "Getting ready…")
                            backendHealth != BackendHealthStatus.READY -> tr("Kết nối trợ lý", "Connect assistant")
                            else -> tr("Đang chuẩn bị…", "Getting ready…")
                        }, fontSize = 23.sp)
                    }
                    OutlinedButton(onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp)) {
                        Text(tr("⚙  Thiết lập", "⚙  Settings"), fontSize = 22.sp)
                    }

                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text((if (detailsOpen) "⌃  " else "⌄  ") + tr("Xem cách hoạt động", "How it works"), fontSize = 20.sp)
                    }
                    if (detailsOpen) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFCBD3CE)),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(tr("GuidingHand chỉ khoanh và hướng dẫn. Bác vẫn tự bấm, nhập và gửi.",
                                    "GuidingHand points and guides. You still tap, type and send."), fontSize = 20.sp)
                                Text(tr("Ngôn ngữ: ${if (prefs.language == "vi") "Tiếng Việt" else "English"} · Chỉ dẫn: ${visionLabel(prefs.visionProfile, true)}",
                                    "Language: ${if (prefs.language == "vi") "Vietnamese" else "English"} · Guide: ${visionLabel(prefs.visionProfile, false)}"), fontSize = 18.sp)
                                Text(tr("Phiên bản ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"), fontSize = 18.sp)
                                if (BuildConfig.DEBUG) Text(tr("Bản phát triển: ${if (prefs.devStandIn) "MockModel" else "Muse Spark + SAM"}",
                                    "Development: ${if (prefs.devStandIn) "MockModel" else "Muse Spark + SAM"}"), fontSize = 18.sp)
                            }
                        }
                    }

                    SettingsSection(tr("Việc bạn hay làm", "Things you often do"),
                        tr("Bác vẫn tự bấm từng bước trong ứng dụng.", "You still tap every step inside the app.")) {
                        if (contact.isNotBlank()) {
                            Button(onClick = { startPhoto(contact) }, enabled = guideReady,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(22.dp)) {
                                Text(tr("Gửi ảnh cho $contact", "Send a photo to $contact"), fontSize = 22.sp)
                            }
                            OutlinedButton(onClick = { startPhoto("") }, enabled = guideReady,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(22.dp)) {
                                Text(tr("Gửi ảnh trên Zalo", "Send a photo on Zalo"), fontSize = 22.sp)
                            }
                        } else {
                            Button(onClick = { startPhoto("") }, enabled = guideReady,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(22.dp)) {
                                Text(tr("Gửi ảnh trên Zalo", "Send a photo on Zalo"), fontSize = 22.sp)
                            }
                        }
                        Button(onClick = { GuidingHandService.instance?.callFamilyFromHome() },
                            enabled = contact.isNotBlank() && prefs.phone.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(22.dp)) {
                            Text(tr("Gọi cho gia đình", "Call family"), fontSize = 22.sp)
                        }
                        if (contact.isBlank() || prefs.phone.isBlank()) {
                            Text(tr("Hãy thêm tên và số điện thoại gia đình trong Thiết lập để bật nút gọi.",
                                "Add a family name and phone number in Settings to enable calling."), fontSize = 20.sp)
                        }
                    }
                }
            }
        } }
    }
}

private enum class HomeStep { SPEAK, LOOK, TAP }

@Composable private fun HomeJourney(vi: Boolean, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center) {
        HomeJourneyStep(HomeStep.SPEAK, if (vi) "Nói" else "Speak", accent, Modifier.weight(1f))
        Text("›", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        HomeJourneyStep(HomeStep.LOOK, if (vi) "Nhìn" else "Look", accent, Modifier.weight(1f))
        Text("›", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
        HomeJourneyStep(HomeStep.TAP, if (vi) "Tự bấm" else "You tap", accent, Modifier.weight(1f))
    }
}

@Composable private fun HomeJourneyStep(step: HomeStep, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(shape = CircleShape, color = Color(0xFF192A30), modifier = Modifier.size(68.dp)) {
            Box(contentAlignment = Alignment.Center) {
                when (step) {
                    HomeStep.SPEAK -> Icon(painterResource(R.drawable.ic_mic), contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(34.dp))
                    HomeStep.LOOK -> Canvas(Modifier.size(48.dp)) {
                        drawRoundRect(Color.Black, style = Stroke(width = 6.dp.toPx()))
                        drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(7.dp.toPx(), 7.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width - 14.dp.toPx(), size.height - 14.dp.toPx()),
                            style = Stroke(width = 5.dp.toPx()))
                    }
                    HomeStep.TAP -> Image(painterResource(R.drawable.guidinghand_icon), contentDescription = null,
                        modifier = Modifier.size(68.dp))
                }
            }
        }
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

private val guideInk = Color(0xFF18332A)
private val guideCream = Color(0xFFFFFCF5)

private fun accentColor(profile: VisionProfile) = Color(android.graphics.Color.parseColor(profile.accentHex))

private fun visionLabel(profile: VisionProfile, vi: Boolean) = when (profile) {
    VisionProfile.HIGH_CONTRAST -> if (vi) "Tương phản cao" else "High contrast"
    VisionProfile.PROTANOPIA -> if (vi) "Mù màu đỏ" else "Protanopia"
    VisionProfile.DEUTERANOPIA -> if (vi) "Mù màu xanh lá" else "Deuteranopia"
    VisionProfile.TRITANOPIA -> if (vi) "Mù màu xanh lam–vàng" else "Tritanopia"
    VisionProfile.MONOCHROME -> if (vi) "Đơn sắc" else "Monochrome"
}

private fun cardSizeLabel(size: GuideCardSize, vi: Boolean) = when (size) {
    GuideCardSize.COMPACT -> if (vi) "Gọn — che ít màn hình" else "Compact — covers less screen"
    GuideCardSize.STANDARD -> if (vi) "Vừa — dễ đọc" else "Standard — easier to read"
    GuideCardSize.LARGE -> if (vi) "Lớn — chữ và nút thoáng hơn" else "Large — more room for text and buttons"
}

@Composable private fun SettingsSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = guideCream),
        border = BorderStroke(1.dp, Color(0xFFDFE5DB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, fontSize = 20.sp) }
            content()
        }
    }
}

@Composable private fun ChoiceButton(selected: Boolean, label: String, accent: Color = Color.Transparent,
    supporting: String? = null, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp), border = BorderStroke(if (selected) 4.dp else 1.dp, if (selected) guideInk else Color(0xFF66766F)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color.White else guideCream, contentColor = guideInk),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        if (accent != Color.Transparent) Box(Modifier.size(30.dp).background(Color.Black, CircleShape).padding(5.dp)) {
            Box(Modifier.fillMaxSize().background(accent, CircleShape))
        }
        Spacer(Modifier.width(if (accent != Color.Transparent) 12.dp else 0.dp))
        Column(Modifier.weight(1f)) {
            Text((if (selected) "✓  " else "") + label, fontSize = 22.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            supporting?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Normal) }
        }
    }
}

@Composable private fun ToggleSetting(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(onClick = { onCheckedChange(!checked) }, color = Color.White, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF66766F)), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(description, fontSize = 20.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable private fun MascotChoiceButton(selected: Boolean, label: String, supporting: String,
                                            drawable: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 4.dp else 1.dp, if (selected) guideInk else Color(0xFF66766F)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = guideInk),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Image(painterResource(drawable), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text((if (selected) "✓  " else "") + label, fontSize = 22.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(supporting, fontSize = 20.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable private fun GuidePreview(profile: VisionProfile, showPointer: Boolean, vi: Boolean) {
    val accent = accentColor(profile)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (vi) "Xem trước chỉ dẫn" else "Guide preview", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFE0E4E2), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center) {
            Box(Modifier.width(190.dp).height(82.dp).border(6.dp, Color.Black, RoundedCornerShape(20.dp)).padding(9.dp)
                .border(6.dp, accent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(if (vi) "Nút cần bấm" else "Button to tap", fontSize = 20.sp, color = guideInk)
            }
            if (showPointer) Text("☝", fontSize = 44.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 3.dp))
        }
        Text(if (vi) "Vòng đôi và chữ vẫn cho biết vị trí, kể cả khi không phân biệt được màu."
            else "The double ring and label still identify the target without relying on color.", fontSize = 20.sp)
    }
}

class SettingsActivity : SetupActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FamilyPreferences(this)
        setContent { GuideTheme {
            var language by remember { mutableStateOf(prefs.language) }
            var phone by remember { mutableStateOf(prefs.phone) }
            var contact by remember { mutableStateOf(prefs.contact) }
            var user by remember { mutableStateOf(prefs.respectfulLanguage.user) }
            var assistant by remember { mutableStateOf(prefs.respectfulLanguage.assistant) }
            var tone by remember { mutableStateOf(prefs.respectfulLanguage.tone) }
            var customUser by remember { mutableStateOf(prefs.respectfulLanguage.customUser) }
            var customAssistant by remember { mutableStateOf(prefs.respectfulLanguage.customAssistant) }
            var visionProfile by remember { mutableStateOf(prefs.visionProfile) }
            var showPointer by remember { mutableStateOf(prefs.showPointer) }
            var reduceMotion by remember { mutableStateOf(prefs.reduceMotion) }
            var cardSize by remember { mutableStateOf(prefs.cardSize) }
            var mascot by remember { mutableStateOf(prefs.mascot) }
            var characterOnly by remember { mutableStateOf(prefs.characterOnlyBubble) }
            var technical by remember { mutableStateOf(false) }
            var backend by remember { mutableStateOf(prefs.backendUrl) }
            var session by remember { mutableStateOf(prefs.backendSession) }
            var screenConsent by remember { mutableStateOf(prefs.screenConsent) }
            var voiceConsent by remember { mutableStateOf(prefs.voiceConsent) }
            var devStandIn by remember { mutableStateOf(prefs.devStandIn) }
            var saved by remember { mutableStateOf(true) }
            var connectionEffectStarted by remember { mutableStateOf(false) }
            fun tr(v: String, e: String) = if (language == "vi") v else e
            val preview = RespectfulLanguage(user, assistant, tone, customUser, customAssistant)
            // Ordinary preferences persist as soon as they change. This removes the easy-to-miss
            // Save step while keeping the user's current task and explicit safety choices intact.
            LaunchedEffect(language, phone, contact, user, assistant, tone, customUser, customAssistant, visionProfile,
                showPointer, reduceMotion, cardSize, mascot, characterOnly, backend, session, screenConsent,
                voiceConsent, devStandIn) {
                prefs.language = language; prefs.phone = phone; prefs.contact = contact
                prefs.respectfulLanguage = preview; prefs.visionProfile = visionProfile
                prefs.showPointer = showPointer; prefs.reduceMotion = reduceMotion
                prefs.cardSize = cardSize; prefs.mascot = mascot
                prefs.characterOnlyBubble = characterOnly
                prefs.backendUrl = backend; prefs.backendSession = session
                prefs.screenConsent = screenConsent; prefs.voiceConsent = voiceConsent
                if (BuildConfig.DEBUG) prefs.devStandIn = devStandIn
                GuidingHandService.instance?.refreshPreferences()
                saved = true
            }
            // Model construction depends on these fields. Debounce typing so a URL, session,
            // or family name does not restart the model after every character.
            LaunchedEffect(backend, session, screenConsent, contact, devStandIn) {
                if (!connectionEffectStarted) connectionEffectStarted = true
                else {
                    delay(250)
                    GuidingHandService.instance?.reloadModel()
                }
            }
            GuideBackdrop {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(tr("Thiết lập GuidingHand", "GuidingHand settings"), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (saved) tr("✓ Thay đổi được tự động lưu", "✓ Changes save automatically")
                        else tr("Đang lưu thay đổi…", "Saving changes…"), fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold)
                    SettingsSection(tr("Ngôn ngữ", "Language"), tr(
                        "Đây là ngôn ngữ ban đầu. Sau khi bác nói, trợ lý theo ngôn ngữ của câu vừa nói.",
                        "This is the starting language. After you speak, guidance follows the language you used."
                    )) {
                        for ((key, label) in listOf("en" to "English", "vi" to "Tiếng Việt")) {
                            ChoiceButton(language == key, label, onClick = { language = key; saved = false })
                        }
                    }
                    if (language == "vi") {
                        SettingsSection("Cách trò chuyện", "Chọn cách xưng hô khiến bác cảm thấy tự nhiên và được tôn trọng.") {
                            Text("Trợ lý gọi bác là", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            for (value in UserAddress.entries) ChoiceButton(user == value, value.label, onClick = { user = value; saved = false })
                            if (user == UserAddress.CUSTOM) OutlinedTextField(
                                value = customUser, onValueChange = { customUser = it.take(32); saved = false },
                                label = { Text("Muốn được gọi là", fontSize = 20.sp) },
                                placeholder = { Text("Ví dụ: Ông Sáu", fontSize = 20.sp) },
                                supportingText = { Text("Tối đa 32 ký tự. Để trống sẽ dùng Bác.", fontSize = 20.sp) },
                                singleLine = true, textStyle = TextStyle(fontSize = 24.sp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
                            Text("Trợ lý tự xưng là", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            for (value in AssistantAddress.entries) ChoiceButton(assistant == value, value.label, onClick = { assistant = value; saved = false })
                            if (assistant == AssistantAddress.CUSTOM) OutlinedTextField(
                                value = customAssistant, onValueChange = { customAssistant = it.take(32); saved = false },
                                label = { Text("Trợ lý tự xưng là", fontSize = 20.sp) },
                                placeholder = { Text("Ví dụ: Em", fontSize = 20.sp) },
                                supportingText = { Text("Tối đa 32 ký tự. Để trống sẽ dùng Tôi.", fontSize = 20.sp) },
                                singleLine = true, textStyle = TextStyle(fontSize = 24.sp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
                            Text("Cách hướng dẫn", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            for (value in GuidanceTone.entries) ChoiceButton(tone == value, value.label, onClick = { tone = value; saved = false })
                            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Nghe thử cách nói", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    Text(preview.goalQuestion, fontSize = 24.sp)
                                    Text(preview.waitingExample, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    SettingsSection(tr("Người đồng hành", "Companion"), tr(
                        "Chọn hình xuất hiện trên nút hỗ trợ nổi. Cả hai đều là trợ lý GuidingHand.",
                        "Choose the character shown on the Help button. Both represent the GuidingHand assistant.")) {
                        MascotChoiceButton(mascot == GuideMascot.HAND, tr("Bàn tay", "Guiding hand"),
                            tr("Đơn giản, quen thuộc và tập trung vào thao tác.", "Simple, familiar, and focused on the next action."),
                            R.drawable.guidinghand_icon) { mascot = GuideMascot.HAND; saved = false }
                        MascotChoiceButton(mascot == GuideMascot.YOUNG_HELPER, tr("Người cháu đồng hành", "Young helper"),
                            tr("Tạo cảm giác ấm áp. Đây vẫn là trợ lý, không giả làm người thân thật.",
                                "Adds warmth while remaining an assistant, never a real family member."),
                            R.drawable.guidinghand_companion) { mascot = GuideMascot.YOUNG_HELPER; saved = false }
                        ToggleSetting(tr("Chỉ hiện nhân vật", "Character only"),
                            tr("Ẩn chữ trên nút nổi. Vẫn chạm để nói và kéo để di chuyển.",
                                "Hide the floating label. Tap to speak and drag to move."),
                            characterOnly) { characterOnly = it; saved = false }
                    }
                    SettingsSection(tr("Thị lực và chuyển động", "Vision and motion"),
                        tr("Chọn kiểu dễ nhìn nhất. GuidingHand luôn dùng thêm hình dạng, đường viền và chữ — không chỉ dùng màu.",
                            "Choose what is easiest to see. GuidingHand always uses shapes, borders and text—not color alone.")) {
                        for (value in VisionProfile.entries) ChoiceButton(visionProfile == value, visionLabel(value, language == "vi"),
                            accentColor(value), when (value) {
                                VisionProfile.HIGH_CONTRAST -> tr("Vàng sáng trên viền đen", "Bright yellow with black outline")
                                VisionProfile.PROTANOPIA -> tr("Tối ưu khi khó phân biệt màu đỏ", "For reduced red sensitivity")
                                VisionProfile.DEUTERANOPIA -> tr("Tối ưu khi khó phân biệt màu xanh lá", "For reduced green sensitivity")
                                VisionProfile.TRITANOPIA -> tr("Tối ưu khi khó phân biệt xanh lam–vàng", "For reduced blue–yellow sensitivity")
                                VisionProfile.MONOCHROME -> tr("Chỉ trắng, đen và hình dạng", "White, black and shapes only")
                            }) { visionProfile = value; saved = false }
                        ToggleSetting(tr("Hiện người chỉ dẫn", "Show guide pointer"),
                            tr("Thêm bàn tay hoặc nhân vật bên cạnh vòng đôi. Tắt nếu bác thấy rối mắt.",
                                "Adds the hand or helper beside the double ring. Turn it off if it feels distracting."),
                            showPointer) { showPointer = it; saved = false }
                        ToggleSetting(tr("Giảm chuyển động", "Reduce motion"),
                            tr("Tắt hiệu ứng gợn và chuyển động không cần thiết.", "Removes non-essential ripple and motion effects."),
                            reduceMotion) { reduceMotion = it; saved = false }
                        Text(tr("Kích thước thẻ hướng dẫn", "Guide card size"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr("Bác chọn mức vừa mắt. Thẻ luôn tự tránh vòng chỉ dẫn và thanh hệ thống.",
                            "Choose the size that feels comfortable. The card still avoids the ring and system bars."), fontSize = 20.sp)
                        for (value in GuideCardSize.entries) ChoiceButton(cardSize == value, cardSizeLabel(value, language == "vi"),
                            supporting = when (value) {
                                GuideCardSize.COMPACT -> tr("Ít che màn hình nhất; nội dung có thể cuộn.", "Least coverage; content can scroll.")
                                GuideCardSize.STANDARD -> tr("Cân bằng giữa dễ đọc và không gian phía sau.", "Balanced readability and screen space.")
                                GuideCardSize.LARGE -> tr("Dễ đọc hơn khi cần chữ lớn hoặc nhiều lựa chọn.", "More room for large text or extra choices.")
                            }) { cardSize = value; saved = false }
                        GuidePreview(visionProfile, showPointer, language == "vi")
                    }
                    SettingsSection(tr("Liên hệ gia đình", "Family contact"),
                        tr("Chỉ dùng khi GuidingHand phải dừng vì mật khẩu, OTP hoặc thanh toán.",
                            "Used only when GuidingHand must stop for a password, OTP or payment.")) {
                        OutlinedTextField(contact, { contact = it; saved = false }, label = { Text(tr("Tên người thân", "Family member’s name")) },
                            textStyle = TextStyle(fontSize = 20.sp), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it; saved = false }, label = { Text(tr("Số điện thoại người thân", "Family phone number")) },
                            textStyle = TextStyle(fontSize = 20.sp), modifier = Modifier.fillMaxWidth())
                        Text(tr("Gọi người thân chỉ mở trình quay số. GuidingHand không tự gọi.", "Call family only opens the dialer. GuidingHand never places the call."))
                    }
                    OutlinedButton(onClick = { technical = !technical }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text((if (technical) "− " else "+ ") + tr("Kết nối và quyền chia sẻ", "Connection and sharing"), fontSize = 22.sp)
                    }
                    if (technical) {
                        Text(tr("Khóa API Meta chỉ nằm trên máy chủ. Không nhập khóa API vào ứng dụng.", "The Meta API key stays on the server. Never enter a provider key in the app."))
                        OutlinedTextField(backend, { backend = it; saved = false }, label = { Text(tr("Máy chủ HTTPS GuidingHand", "GuidingHand HTTPS server")) },
                            textStyle = TextStyle(fontSize = 20.sp), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(session, { session = it; saved = false }, label = { Text(tr("Phiên kết nối GuidingHand", "GuidingHand session")) },
                            visualTransformation = PasswordVisualTransformation(), textStyle = TextStyle(fontSize = 20.sp), modifier = Modifier.fillMaxWidth())
                        Row { Checkbox(screenConsent, { screenConsent = it; saved = false }); Text(tr("Cho phép gửi một ảnh màn hình đã kiểm tra an toàn và dữ liệu giao diện để hướng dẫn.", "Allow one safety-checked screenshot and UI data for guidance.")) }
                        Row { Checkbox(voiceConsent, { voiceConsent = it; saved = false }); Text(tr("Cho phép gửi bản ghi khi bấm Nói tới Meta qua máy chủ GuidingHand để chuyển thành chữ.", "Allow recordings made by tapping Speak to be transcribed by Meta through GuidingHand.")) }
                        if (BuildConfig.DEBUG) Row { Checkbox(devStandIn, { devStandIn = it; saved = false }); Text(tr("Chỉ phát triển: MockModel thay cho Meta. Không dùng trong bản chính thức.", "Development only: MockModel instead of Meta. Not the submission path.")) }
                    }
                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Text(tr("Mở cài đặt Trợ năng", "Open Accessibility settings"), fontSize = 22.sp)
                    }
                    TextButton(onClick = { finish() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Quay lại", "Back"), fontSize = 22.sp) }
                }
            }
        } }
    }
}
