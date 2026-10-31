package vn.guidinghand

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small separate-task microphone window, never the main GuidingHand screen. */
class AssistantActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppLanguage.context(newBase)) }
    companion object {
        var isVoiceVisible = false; private set
        private var active: AssistantActivity? = null
        fun stopForDeviceLock() { active?.closeForDeviceLock() }
        fun stopForLanguageChange() { active?.closeForDeviceLock() }
    }
    private lateinit var voice: OfflineVoiceInput
    private var listening by mutableStateOf(false)
    private var note by mutableStateOf("")
    private var speechLanguage by mutableStateOf("en")
    private var modelState by mutableStateOf(OfflineLanguageState.UNKNOWN)
    private var onlineAttempt by mutableStateOf<OnlineSpeechProvider?>(null)
    private var onlineConsent by mutableStateOf<OnlineSpeechProvider?>(null)
    private var returned = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceVisibility.allowed(this)) { returned = true; finish(); return }
        active = this
        if (!intent.getBooleanExtra("voiceRequested", false)) {
            returned = true; finish(); GuidingHandService.instance?.voiceCancelled(); return
        }
        val prefs = FamilyPreferences(this)
        val ui = UiText(prefs.language)
        speechLanguage = prefs.speechLanguage
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        voice = OfflineVoiceInput(this, { speechLanguage }, { prefs.language }, { listening = it }, { note = it }, { hypotheses ->
            returned = true; finish(); GuidingHandService.instance?.voiceResults(hypotheses)
        }, { modelState = it }, { onlineAttempt = it })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { cancel() }
        })
        setContent {
            GuidingHandTheme(prefs.language) {
                Surface(shape = RoundedCornerShape(24.dp), color = Paper, shadowElevation = 8.dp) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                returned = true; voice.stop(); finish(); GuidingHandService.instance?.showAssistant()
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text(ui.helpMenu, fontSize = 15.sp, color = Ink)
                            }
                            TextButton(onClick = { cancel() }, contentPadding = PaddingValues(0.dp)) {
                                Text(ui.pick("Close", "Đóng"), fontSize = 16.sp, color = Ink)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.pick("Speak in", "Ngôn ngữ nói"), Modifier.weight(1f), fontSize = 13.sp, color = Quiet)
                            listOf("en", "vi").forEach { code ->
                                TextButton(onClick = {
                                    voice.stop(); onlineConsent = null
                                    speechLanguage = code; prefs.speechLanguage = code
                                    voice.checkLanguage()
                                }, modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (speechLanguage == code) Sage else Color.Transparent,
                                        contentColor = Ink)) {
                                    Text(ui.languageName(code), fontSize = 14.sp, fontWeight = if (speechLanguage == code) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        Text(if (note.isNotBlank()) note else if (listening) ui.listening else ui.pick("Ready to listen", "Sẵn sàng nghe"),
                            fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, maxLines = 4)
                        if (onlineAttempt != null) Text(ui.pick("Online permitted · ${onlineAttempt!!.label}", "Đã cho phép qua mạng · ${onlineAttempt!!.label}"),
                            fontSize = 13.sp, lineHeight = 18.sp, color = Quiet, maxLines = 2)
                        else if (modelState in listOf(OfflineLanguageState.DOWNLOADABLE, OfflineLanguageState.DOWNLOADING, OfflineLanguageState.PENDING))
                            Text(ui.pick("One-time internet download. Speech stays offline.", "Cần mạng để tải một lần. Nhận giọng nói vẫn ngoại tuyến."),
                                fontSize = 13.sp, lineHeight = 18.sp, color = Quiet)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                when {
                                    listening -> { voice.stop(); note = ui.pick("Paused. Tap to speak when ready.", "Đã dừng. Bấm để nói khi sẵn sàng nhé.") }
                                    modelState == OfflineLanguageState.DOWNLOADABLE -> voice.downloadLanguage()
                                    modelState in listOf(OfflineLanguageState.READY, OfflineLanguageState.UNKNOWN) -> voice.listen()
                                    else -> voice.checkLanguage()
                                }
                            }, enabled = listening || modelState != OfflineLanguageState.CHECKING,
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)) {
                                val actionLabel = when {
                                    listening -> ui.stop
                                    modelState == OfflineLanguageState.READY -> ui.talk
                                    modelState == OfflineLanguageState.DOWNLOADABLE -> ui.pick("Download language", "Tải ngôn ngữ")
                                    modelState == OfflineLanguageState.UNKNOWN -> ui.pick("Try offline", "Thử ngoại tuyến")
                                    modelState == OfflineLanguageState.CHECKING -> ui.pick("Checking…", "Đang kiểm tra…")
                                    else -> ui.pick("Check language", "Kiểm tra lại")
                                }
                                Text(actionLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                returned = true; voice.stop(); finish(); GuidingHandService.instance?.voiceFallback()
                            }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(4.dp)) {
                                Text(ui.pick("Choose instead", "Chọn bằng nút"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            }
                        }
                        TextButton(onClick = {
                            val provider = voice.defaultOnlineProvider()
                            if (provider == null) note = ui.pick("No default speech provider could be identified. Choose instead is available.", "Chưa xác định được bộ nhận dạng mặc định. Vẫn có thể chọn bằng nút.")
                            else onlineConsent = provider
                        }, enabled = !listening, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(0.dp)) {
                            Text(ui.pick("Use online speech", "Nhận giọng nói qua mạng"),
                                fontSize = 15.sp, color = Ink)
                        }
                    }
                }
                onlineConsent?.let { provider ->
                    AlertDialog(onDismissRequest = { onlineConsent = null },
                        title = { Text(ui.pick("Use online speech?", "Nhận giọng nói qua mạng?")) },
                        text = { Text(ui.pick(
                            "Your voice may be sent to ${provider.label} (${provider.component.packageName}), your selected Android speech provider. Internet is needed. Permission is for this one attempt; screens are not sent. Continue?",
                            "Giọng nói có thể được gửi đến ${provider.label} (${provider.component.packageName}), bộ nhận dạng Android đang được chọn. Cần có mạng. Chỉ cho phép lần nói này; không gửi màn hình. Tiếp tục nhé?")) },
                        confirmButton = { TextButton(onClick = {
                            onlineConsent = null
                            voice.listen(provider)
                        }) { Text(ui.pick("Allow & speak", "Đồng ý và nói")) } },
                        dismissButton = { TextButton(onClick = { onlineConsent = null }) { Text(ui.pick("Stay offline", "Giữ ngoại tuyến")) } })
                }
            }
        }
        window.setLayout((320 * resources.displayMetrics.density).toInt().coerceAtMost(resources.displayMetrics.widthPixels - (24 * resources.displayMetrics.density).toInt()), WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (64 * resources.displayMetrics.density).toInt() }
        window.decorView.post { if (!isFinishing && DeviceVisibility.allowed(this)) voice.checkLanguage() }
    }
    private fun closeForDeviceLock() {
        returned = true
        if (::voice.isInitialized) voice.stop()
        finish()
    }
    private fun cancel() {
        returned = true; voice.stop(); finish(); GuidingHandService.instance?.voiceCancelled()
    }
    override fun onStart() { super.onStart(); isVoiceVisible = true }
    override fun onResume() {
        super.onResume()
        if (::voice.isInitialized && onlineAttempt == null && modelState in listOf(OfflineLanguageState.DOWNLOADING, OfflineLanguageState.PENDING, OfflineLanguageState.CHECKING))
            voice.checkLanguage()
    }
    override fun onStop() { isVoiceVisible = false; if (::voice.isInitialized) voice.stop(); super.onStop() }
    override fun onDestroy() {
        if (active === this) active = null
        if (::voice.isInitialized) voice.stop()
        isVoiceVisible = false
        if (!returned && !isChangingConfigurations) GuidingHandService.instance?.voiceCancelled()
        super.onDestroy()
    }
}
