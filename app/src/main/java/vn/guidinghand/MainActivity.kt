package vn.guidinghand

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF233D34)
val Paper = Color(0xFFFBF7EF)
val Amber = Color(0xFFF3CE8B)
val Sage = Color(0xFFE8EDE3)
val Quiet = Color(0xFF536157)
val Line = Color(0xFFE6E2D8)

@Composable fun GuidingHandTheme(language: String = "en", content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUi provides UiText(language)) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Color.White,
            background = Paper, surface = Paper, onBackground = Ink, onSurface = Ink,
            onSurfaceVariant = Quiet, outline = Line, secondary = Amber), content = content)
    }
}
@Composable fun Buddy(modifier: Modifier = Modifier, dark: Boolean = false) {
    val ui = LocalUi.current
    Image(painterResource(R.drawable.guidinghand_icon), ui.pick("A hand pointing to the next step", "Bàn tay chỉ điểm dẫn đường"),
        modifier.clip(RoundedCornerShape(if (dark) 14.dp else 22.dp)))
}
@Composable fun TaskMark(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(w * .065f, cap = StrokeCap.Round)
        when (id) {
            "video" -> {
                drawRoundRect(Ink, Offset(w * .12f, h * .24f), Size(w * .51f, h * .5f), androidx.compose.ui.geometry.CornerRadius(w * .1f), style = stroke)
                drawLine(Ink, Offset(w * .67f, h * .39f), Offset(w * .86f, h * .28f), w * .065f, StrokeCap.Round)
                drawLine(Ink, Offset(w * .86f, h * .28f), Offset(w * .86f, h * .72f), w * .065f, StrokeCap.Round)
                drawLine(Ink, Offset(w * .86f, h * .72f), Offset(w * .67f, h * .61f), w * .065f, StrokeCap.Round)
            }
            "photo" -> {
                drawRoundRect(Ink, Offset(w * .15f, h * .18f), Size(w * .7f, h * .64f), androidx.compose.ui.geometry.CornerRadius(w * .09f), style = stroke)
                drawCircle(Ink, w * .055f, Offset(w * .65f, h * .36f))
                drawLine(Ink, Offset(w * .24f, h * .69f), Offset(w * .44f, h * .46f), w * .06f, StrokeCap.Round)
                drawLine(Ink, Offset(w * .44f, h * .46f), Offset(w * .7f, h * .7f), w * .06f, StrokeCap.Round)
            }
            "open" -> {
                drawCircle(Ink, w * .22f, Offset(w * .43f, h * .42f), style = stroke)
                drawLine(Ink, Offset(w * .6f, h * .6f), Offset(w * .82f, h * .82f), w * .07f, StrokeCap.Round)
            }
            "message" -> {
                drawRoundRect(Ink, Offset(w * .14f, h * .16f), Size(w * .72f, h * .56f), androidx.compose.ui.geometry.CornerRadius(w * .15f), style = stroke)
                drawLine(Ink, Offset(w * .3f, h * .73f), Offset(w * .24f, h * .86f), w * .06f, StrokeCap.Round)
                for (i in 0..2) drawCircle(Ink, w * .035f, Offset(w * (.34f + i * .16f), h * .44f))
            }
            else -> {
                drawCircle(Ink, w * .29f, center, style = stroke)
                drawLine(Ink, Offset(w * .5f, h * .32f), Offset(w * .5f, h * .68f), w * .06f, StrokeCap.Round)
                drawLine(Ink, Offset(w * .32f, h * .5f), Offset(w * .68f, h * .5f), w * .06f, StrokeCap.Round)
            }
        }
    }
}
@Composable fun LargeButton(label: String, secondary: Boolean = false, action: () -> Unit) {
    Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) Color.White else Ink,
            contentColor = if (secondary) Ink else Color.White),
        border = if (secondary) BorderStroke(1.dp, Line) else null,
        shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(20.dp)) {
        Text(label, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable fun LargeText(value: String) { Text(value, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Ink) }
@Composable fun Body(value: String, modifier: Modifier = Modifier) { Text(value, modifier, fontSize = 20.sp, lineHeight = 29.sp, color = Quiet) }
@Composable fun LanguagePicker(language: String, change: (String) -> Unit) {
    val ui = LocalUi.current
    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(4.dp)) {
        listOf("en", "vi").forEach { code ->
            Surface(onClick = { change(code) }, color = if (language == code) Ink else Color.White, shape = RoundedCornerShape(14.dp)) {
                Text(ui.languageName(code), Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 16.dp),
                    color = if (language == code) Color.White else Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable fun TaskCard(task: GuideTask, modifier: Modifier = Modifier, action: () -> Unit) {
    val ui = LocalUi.current
    Surface(onClick = action, modifier = modifier, shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(if (task.id in listOf("open", "photo")) Amber.copy(alpha = .45f) else Sage), contentAlignment = Alignment.Center) {
                TaskMark(task.id, Modifier.size(34.dp))
            }
            Text(ui.task(task), fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
            Text(ui.taskHint(task.id), fontSize = 18.sp, lineHeight = 26.sp, color = Quiet)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppLanguage.context(newBase)) }
    private lateinit var preferences: FamilyPreferences
    private var language by mutableStateOf("en")
    private var refresh by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); if (::preferences.isInitialized) language = preferences.language; refresh++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); preferences = FamilyPreferences(this); language = preferences.language
        AppLanguage.apply(this, language)
        setContent {
            GuidingHandTheme(language) {
                val ui = LocalUi.current
                val service = remember(refresh) { GuidingHandService.instance }
                fun assistant(task: GuideTask? = null) {
                    if (service == null) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)); return }
                    service.setBubbleVisible(true)
                    service.showAssistant(task)
                }
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
                        Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Buddy(Modifier.size(46.dp), dark = true)
                                Text("GuidingHand", Modifier.weight(1f).padding(start = 8.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }) {
                                    Text(ui.settings, fontSize = 17.sp, color = Ink)
                                }
                            }
                            Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFFF3EBDD)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(ui.pick("A LITTLE CLOSER, EVERY DAY", "GẦN NHAU HƠN, MỖI NGÀY"),
                                        fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Quiet)
                                    Text(ui.pick("A helping hand.\nA familiar feeling.", "Có người bên cạnh.\nTừng bước an tâm."),
                                        fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.Medium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, letterSpacing = (-.7).sp)
                                    Text(ui.pick("For the moments your family can't be right beside you.", "Khi con cháu chưa thể ở bên, vẫn có người kiên nhẫn chỉ từng bước."),
                                        fontSize = 19.sp, lineHeight = 28.sp, color = Quiet)
                                    Image(painterResource(R.drawable.family_together),
                                        ui.pick("Two generations holding hands, a heart between them", "Ông bà và con cháu nắm tay, cùng chia sẻ yêu thương"),
                                        Modifier.fillMaxWidth().height(136.dp))
                                    LargeButton(if (service != null) ui.start else ui.enable) { assistant() }
                                    Text(if (service != null) ui.pick("No rush. We can do this together.", "Không cần vội. Cùng làm từng bước nhé.")
                                        else ui.pick("Ask someone you trust to help set up.", "Nhờ người thân giúp thiết lập lần đầu nhé."),
                                        Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 16.sp, lineHeight = 24.sp, color = Quiet)
                                }
                            }
                            Surface(shape = RoundedCornerShape(24.dp), color = Sage) {
                                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(ui.pick("♡  A familiar voice", "♡  Giọng nói thân quen"), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (preferences.phone.isNotBlank()) ui.pick("When you want your family, they're a phone call away.", "Khi muốn nghe giọng người thân, chỉ cần một cuộc gọi.")
                                        else ui.pick("Keep someone you trust within reach, for the moments you want a little extra help.", "Lưu số người thân để dễ liên lạc khi cần thêm một chút giúp đỡ."),
                                        fontSize = 18.sp, lineHeight = 27.sp, color = Quiet)
                                    TextButton(onClick = {
                                        if (preferences.phone.isBlank()) startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                        else startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", preferences.phone.filter { it.isDigit() || it == '+' }, null)))
                                    }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(if (preferences.phone.isBlank()) ui.pick("Add a family number  →", "Lưu số người thân  →") else ui.pick("Call family  →", "Gọi người thân  →"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LargeText(ui.pick("Little things, together", "Việc nhỏ, cùng nhau làm"))
                                Body(ui.pick("Pick one. We'll go one step at a time.", "Chọn một việc. Mình cùng làm từng bước nhé."))
                            }
                            val quick = listOf(Tasks.openZalo, Tasks.openInstagram, Tasks.message, Tasks.messageIn("com.instagram.android", "Instagram"))
                            quick.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    pair.forEach { task -> TaskCard(task, Modifier.weight(1f).fillMaxHeight()) { assistant(task) } }
                                }
                            }
                            LargeText(ui.pick("Everyday things", "Những việc thường ngày"))
                            Tasks.all.drop(2).forEach { task ->
                                Surface(onClick = { assistant(task) }, shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TaskMark(task.id, Modifier.size(36.dp))
                                        Text(ui.task(task.id), Modifier.weight(1f).padding(horizontal = 16.dp), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                        Text("→", fontSize = 28.sp)
                                    }
                                }
                            }
                            LanguagePicker(language) { language = it; preferences.language = it; service?.refreshLanguage() }
                            Body(ui.pick("A little guidance, with the patience of someone who cares. Every tap is still yours.", "Một chút chỉ dẫn, một chút kiên nhẫn như người thân bên cạnh. Mọi thao tác vẫn do ông bà tự làm."))
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

class SettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppLanguage.context(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FamilyPreferences(this)
        setContent {
            var language by remember { mutableStateOf(prefs.language) }
            GuidingHandTheme(language) {
                val ui = LocalUi.current
                var phone by rememberSaveable { mutableStateOf(prefs.phone) }
                var contact by rememberSaveable { mutableStateOf(prefs.contact) }
                var network by rememberSaveable { mutableStateOf(prefs.network) }
                var photo by rememberSaveable { mutableStateOf(prefs.photo) }
                var address by rememberSaveable { mutableStateOf(prefs.address) }
                var speechLanguage by remember { mutableStateOf(prefs.speechLanguage) }
                var zaloAlias by rememberSaveable { mutableStateOf(prefs.zaloAlias) }
                var instagramAlias by rememberSaveable { mutableStateOf(prefs.instagramAlias) }
                var saved by rememberSaveable { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            TextButton(onClick = { finish() }) { Text("←  ${ui.back}", fontSize = 20.sp, color = Ink) }
                            Buddy(Modifier.size(76.dp), dark = true)
                            Text(ui.family, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
                            Body(ui.pick("A few minutes of your care. A little more confidence for someone you love.", "Con cháu dành vài phút thiết lập, để ông bà thêm tự tin mỗi ngày."))
                            LanguagePicker(language) { language = it; prefs.language = it; GuidingHandService.instance?.refreshLanguage() }
                            LargeText(ui.pick("The language you speak", "Ngôn ngữ ông bà nói"))
                            Body(ui.pick("Your screen can stay in English while you speak Vietnamese.", "Giao diện có thể bằng tiếng Anh trong khi ông bà nói tiếng Việt."))
                            LanguagePicker(speechLanguage) { speechLanguage = it; prefs.speechLanguage = it }
                            Surface(shape = RoundedCornerShape(24.dp), color = Sage) {
                                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LargeText(ui.pick("Make your guide available", "Bật trợ lý nổi"))
                                    Body(ui.pick("Enable GuidingHand in Accessibility. The bubble stays available over your apps, until you hide it.", "Bật GuidingHand trong Trợ năng. Bong bóng hiện trên các ứng dụng đến khi được ẩn."))
                                    LargeButton(ui.pick("Open Accessibility settings", "Mở cài đặt Trợ năng")) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                }
                            }
                            @Composable fun field(label: String, value: String, phoneInput: Boolean = false, update: (String) -> Unit) {
                                OutlinedTextField(value, update, label = { Text(label, fontSize = 18.sp) }, shape = RoundedCornerShape(18.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
                                    keyboardOptions = KeyboardOptions(keyboardType = if (phoneInput) KeyboardType.Phone else KeyboardType.Text),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp))
                            }
                            LargeText(ui.pick("A familiar person", "Một người thân quen"))
                            field(ui.pick("Family phone number", "Số điện thoại người thân"), phone, true) { phone = it; saved = false }
                            field(ui.pick("Saved contact's exact Zalo name", "Tên người thân trong Zalo"), contact) { contact = it; saved = false }
                            Body(ui.pick("A saved contact is a one-tap option. You can also say another name when offline voice is available.", "Người thân đã lưu có thể chọn bằng một lần bấm. Có thể nói tên khác khi máy hỗ trợ giọng nói ngoại tuyến."))
                            LargeText(ui.pick("Familiar names for apps", "Tên ứng dụng dễ nhớ"))
                            Body(ui.pick("Save a name your family already uses, like “the app for talking to my grandson”. GuidingHand will ask before opening it.", "Lưu cách ông bà hay gọi, ví dụ “ứng dụng nói chuyện với cháu”. GuidingHand sẽ hỏi lại trước khi hướng dẫn mở."))
                            field(ui.pick("A familiar name for Zalo", "Cách gọi quen thuộc của Zalo"), zaloAlias) { zaloAlias = it; saved = false }
                            field(ui.pick("A familiar name for Instagram", "Cách gọi quen thuộc của Instagram"), instagramAlias) { instagramAlias = it; saved = false }
                            LargeText(ui.pick("Optional task details", "Thông tin tùy chọn"))
                            field(ui.pick("Wi-Fi network name", "Tên mạng không dây"), network) { network = it; saved = false }
                            field(ui.pick("Photo's accessible label", "Tên ảnh trên màn hình"), photo) { photo = it; saved = false }
                            if (language == "vi") field("Cách xưng hô: bà, ông, cô, chú, mẹ", address) { address = it; saved = false }
                            LargeButton(if (saved) ui.pick("Saved ✓", "Đã lưu ✓") else ui.save) {
                                prefs.phone = phone; prefs.contact = contact; prefs.network = network; prefs.photo = photo; prefs.address = address
                                prefs.zaloAlias = zaloAlias; prefs.instagramAlias = instagramAlias; saved = true
                            }
                            LargeButton(ui.show, secondary = true) {
                                GuidingHandService.instance?.setBubbleVisible(true)
                                startActivity(Intent(this@SettingsActivity, MainActivity::class.java)); finish()
                            }
                            Body(ui.pick("Your screen stays on your phone. Speech starts offline. Online speech sends audio to the named service only after you confirm. Passwords and payments pause guidance.", "Màn hình được xử lý trong máy. Giọng nói mặc định ngoại tuyến. Chỉ gửi âm thanh đến dịch vụ được nêu khi ông bà xác nhận dùng nhận dạng qua mạng. Dừng hướng dẫn ở mật khẩu và thanh toán."))
                        }
                    }
                }
            }
        }
    }
}
