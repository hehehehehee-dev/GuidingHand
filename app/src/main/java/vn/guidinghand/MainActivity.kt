package vn.guidinghand

import android.content.Intent
import android.content.ComponentName
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable private fun GuideTheme(content: @Composable () -> Unit) {
    val ink = Color(0xFF18332A); val cream = Color(0xFFFFFCF5)
    MaterialTheme(colorScheme = lightColorScheme(primary = ink, onPrimary = cream, surface = cream,
        onSurface = ink, background = cream, onBackground = ink, secondary = ink),
        typography = Typography(bodyLarge = TextStyle(fontSize = 20.sp), bodyMedium = TextStyle(fontSize = 20.sp),
            bodySmall = TextStyle(fontSize = 20.sp), labelLarge = TextStyle(fontSize = 22.sp)), content = content)
}

/** An explicit Help tap can dismiss our own setup task; it never navigates another app. */
open class SetupActivity : ComponentActivity() {
    companion object { var foreground: java.lang.ref.WeakReference<SetupActivity>? = null }
    override fun onResume() { super.onResume(); foreground = java.lang.ref.WeakReference(this) }
    override fun onPause() { if (foreground?.get() === this) foreground = null; super.onPause() }
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
        if (source.getBooleanExtra("configureCoreDev", false) && source.getStringExtra("devPairNonce") == prefs.devPairNonce) {
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
        }
        source.removeExtra("configureCoreDev"); source.removeExtra("devPairNonce"); source.removeExtra("backendSession"); source.removeExtra("devLanguage")
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); configureDevelopment(intent) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); configureDevelopment(intent)
        val prefs = FamilyPreferences(this)
        setContent { GuideTheme {
            val vi = remember(preferenceRevision) { prefs.language == "vi" }
            fun tr(v: String, e: String) = if (vi) v else e
            var detailsOpen by remember { mutableStateOf(false) }
            var guideReady by remember { mutableStateOf(GuidingHandService.instance != null) }
            val accessibilityOn = accessibilityEnabled()
            LaunchedEffect(Unit) {
                repeat(24) {
                    guideReady = GuidingHandService.instance != null
                    if (guideReady) return@LaunchedEffect
                    delay(250)
                }
            }
            Surface(Modifier.fillMaxSize()) {
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
                                accessibilityOn -> tr("… Đang nối", "… Connecting")
                                else -> tr("! Cần bật", "! Enable")
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F0E6)),
                        shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(R.drawable.family_together),
                                contentDescription = tr("Người lớn tuổi và người thân đồng hành", "An elder and family member together"),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 176.dp))
                            Text(tr("Bác nói. Tôi chỉ. Bác tự bấm.", "You speak. I point. You tap."),
                                fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HomeJourney(vi, accentColor(prefs.visionProfile))

                    Button(onClick = {
                        val service = GuidingHandService.instance
                        if (service == null && !accessibilityOn) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        else if (service != null) { moveTaskToBack(true); window.decorView.postDelayed({ service.openVoice() }, 350) }
                    }, enabled = guideReady || !accessibilityOn,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp), shape = RoundedCornerShape(24.dp)) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(when {
                            guideReady -> tr("Nói việc cần giúp", "Speak to get help")
                            accessibilityOn -> tr("Đang chuẩn bị…", "Getting ready…")
                            else -> tr("Bật hỗ trợ nổi", "Enable floating help")
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

@Composable private fun SettingsSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F0E6)),
        shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
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
            var visionProfile by remember { mutableStateOf(prefs.visionProfile) }
            var showPointer by remember { mutableStateOf(prefs.showPointer) }
            var reduceMotion by remember { mutableStateOf(prefs.reduceMotion) }
            var technical by remember { mutableStateOf(false) }
            var backend by remember { mutableStateOf(prefs.backendUrl) }
            var session by remember { mutableStateOf(prefs.backendSession) }
            var screenConsent by remember { mutableStateOf(prefs.screenConsent) }
            var voiceConsent by remember { mutableStateOf(prefs.voiceConsent) }
            var devStandIn by remember { mutableStateOf(prefs.devStandIn) }
            var saved by remember { mutableStateOf(false) }
            fun tr(v: String, e: String) = if (language == "vi") v else e
            val preview = RespectfulLanguage(user, assistant, tone)
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(tr("Thiết lập GuidingHand", "GuidingHand settings"), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("Người nhà có thể cùng thiết lập một lần. Bác vẫn là người quyết định và tự thao tác.",
                        "A family member can help set this up once. The elder stays in control and performs every action."))
                    SettingsSection(tr("Ngôn ngữ", "Language"), tr("Toàn bộ lời hướng dẫn chỉ dùng ngôn ngữ đã chọn.", "All guidance uses only the selected language.")) {
                        for ((key, label) in listOf("en" to "English", "vi" to "Tiếng Việt")) {
                            ChoiceButton(language == key, label, onClick = { language = key; saved = false })
                        }
                    }
                    if (language == "vi") {
                        SettingsSection("Cách trò chuyện", "Chọn cách xưng hô khiến bác cảm thấy tự nhiên và được tôn trọng.") {
                            Text("Trợ lý gọi bác là", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            for (value in UserAddress.entries) ChoiceButton(user == value, value.label, onClick = { user = value; saved = false })
                            Text("Trợ lý tự xưng là", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            for (value in AssistantAddress.entries) ChoiceButton(assistant == value, value.label, onClick = { assistant = value; saved = false })
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
                    } else SettingsSection("How the assistant speaks") {
                        Text("The assistant speaks respectfully as itself. It never pretends to be a grandchild.")
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
                        ToggleSetting(tr("Hiện bàn tay chỉ", "Show pointing hand"),
                            tr("Thêm bàn tay bên cạnh vòng đôi. Tắt nếu bác thấy rối mắt.", "Adds a hand beside the double ring. Turn it off if it feels distracting."),
                            showPointer) { showPointer = it; saved = false }
                        ToggleSetting(tr("Giảm chuyển động", "Reduce motion"),
                            tr("Tắt hiệu ứng gợn và chuyển động không cần thiết.", "Removes non-essential ripple and motion effects."),
                            reduceMotion) { reduceMotion = it; saved = false }
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
                    Button(onClick = {
                        val reload = prefs.backendUrl != backend.trim().trimEnd('/') || prefs.backendSession != session.trim() || prefs.screenConsent != screenConsent || prefs.contact != contact || prefs.devStandIn != devStandIn
                        prefs.language = language; prefs.phone = phone; prefs.contact = contact; prefs.respectfulLanguage = preview
                        prefs.visionProfile = visionProfile; prefs.showPointer = showPointer; prefs.reduceMotion = reduceMotion
                        prefs.backendUrl = backend; prefs.backendSession = session; prefs.screenConsent = screenConsent; prefs.voiceConsent = voiceConsent
                        if (BuildConfig.DEBUG) prefs.devStandIn = devStandIn
                        if (reload) GuidingHandService.instance?.reloadModel()
                        GuidingHandService.instance?.refreshPreferences()
                        saved = true
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text(if (saved) tr("Đã lưu", "Saved") else tr("Lưu thiết lập", "Save setup"), fontSize = 22.sp) }
                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                        Text(tr("Mở cài đặt Trợ năng", "Open Accessibility settings"), fontSize = 22.sp)
                    }
                    TextButton(onClick = { finish() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("Quay lại", "Back"), fontSize = 22.sp) }
                }
            }
        } }
    }
}
