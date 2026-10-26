package vn.guidinghand

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

enum class OfflineLanguageState { UNKNOWN, CHECKING, READY, DOWNLOADABLE, DOWNLOADING, PENDING, UNSUPPORTED, UNAVAILABLE }
data class OnlineSpeechProvider(val component: ComponentName, val label: String)

/** Offline by default. Each online utterance needs separately confirmed, bound-provider consent. */
class OfflineVoiceInput(private val activity: ComponentActivity, private val speechLanguage: () -> String,
                        private val uiLanguage: () -> String,
                        private val listening: (Boolean) -> Unit, private val status: (String) -> Unit,
                        private val result: (List<String>) -> Unit,
                        private val support: (OfflineLanguageState) -> Unit = {},
                        private val mode: (OnlineSpeechProvider?) -> Unit = {}) {
    companion object { private const val TAG = "GuidingHandSpeech" }
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0L
    private var state = OfflineLanguageState.UNKNOWN
    private var checkedTag = ""
    private var lastMissingTag: String? = null
    private data class PermissionAttempt(val provider: OnlineSpeechProvider?, val tag: String, val generation: Long)
    private var permissionAttempt: PermissionAttempt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ui get() = UiText(uiLanguage())
    private val languageTag get() = if (speechLanguage() == "vi") "vi-VN" else "en-US"
    val available get() = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
    private fun active(request: Long) = request == generation && !activity.isFinishing &&
        !activity.isDestroyed && DeviceVisibility.allowed(activity)
    private fun setState(value: OfflineLanguageState, message: String) {
        state = value; support(value); status(message)
        Log.d(TAG, "event=state language=$languageTag state=$value")
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val attempt = permissionAttempt
        permissionAttempt = null
        if (attempt != null && active(attempt.generation) && attempt.tag == languageTag) {
            if (allowed) start(attempt.provider)
            else {
                mode(null)
                status(ui.pick("Microphone is off. Choose instead is still available.", "Micrô chưa bật. Vẫn có thể chọn bằng nút."))
            }
        }
    }
    private fun requestIntent(tag: String = languageTag, offline: Boolean = true) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    fun defaultOnlineProvider(): OnlineSpeechProvider? = try {
        val configured = Settings.Secure.getString(activity.contentResolver, "voice_recognition_service")
        val component = configured?.let(ComponentName::unflattenFromString)
        if (component == null) null else {
            val info = activity.packageManager.getServiceInfo(component, 0)
            if (!info.enabled || !info.applicationInfo.enabled) null else {
                val label = info.loadLabel(activity.packageManager).toString().trim()
                    .ifBlank { info.applicationInfo.loadLabel(activity.packageManager).toString() }
                OnlineSpeechProvider(component, label)
            }
        }
    } catch (_: Exception) { null }

    /** Query metadata only; no microphone or model download starts here. */
    fun checkLanguage() {
        if (activity.isFinishing || activity.isDestroyed || !DeviceVisibility.allowed(activity)) return
        stop(); checkedTag = languageTag
        if (!available) { setState(OfflineLanguageState.UNAVAILABLE, ui.noSpeech); return }
        if (Build.VERSION.SDK_INT < 33) {
            setState(OfflineLanguageState.UNKNOWN, ui.pick("This phone can't check offline languages. You can try offline or choose instead.", "Máy chưa kiểm tra được ngôn ngữ ngoại tuyến. Có thể thử hoặc chọn bằng nút.")); return
        }
        val request = generation; val tag = checkedTag
        setState(OfflineLanguageState.CHECKING, ui.pick("Checking offline language…", "Đang kiểm tra ngôn ngữ ngoại tuyến…"))
        Log.d(TAG, "event=support_start language=$tag provider=on_device")
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            recognizer!!.checkRecognitionSupport(requestIntent(tag), activity.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    if (!active(request)) return
                    val reported = SpeechLanguagePolicy.classify(tag, recognitionSupport.installedOnDeviceLanguages,
                        recognitionSupport.pendingOnDeviceLanguages, recognitionSupport.supportedOnDeviceLanguages,
                        recognitionSupport.onlineLanguages)
                    Log.d(TAG, "event=support_result language=$tag state=$reported installed=${recognitionSupport.installedOnDeviceLanguages.size} pending=${recognitionSupport.pendingOnDeviceLanguages.size} downloadable=${recognitionSupport.supportedOnDeviceLanguages.size} online=${recognitionSupport.onlineLanguages.size}")
                    stop()
                    val resolved = if (reported == OfflineLanguageState.READY && lastMissingTag == tag) OfflineLanguageState.UNKNOWN else reported
                    setState(resolved, languageStatus(resolved))
                }
                override fun onError(error: Int) {
                    if (!active(request)) return
                    Log.d(TAG, "event=support_error language=$tag code=$error")
                    stop()
                    val resolved = if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) OfflineLanguageState.UNSUPPORTED else OfflineLanguageState.UNKNOWN
                    setState(resolved, languageStatus(resolved))
                }
            })
            handler.postDelayed({
                if (active(request) && state == OfflineLanguageState.CHECKING) {
                    Log.d(TAG, "event=support_timeout language=$tag")
                    stop(); setState(OfflineLanguageState.UNKNOWN, languageStatus(OfflineLanguageState.UNKNOWN))
                }
            }, 4000)
        } catch (_: Exception) {
            stop(); setState(OfflineLanguageState.UNKNOWN, languageStatus(OfflineLanguageState.UNKNOWN))
        }
    }
    private fun languageStatus(value: OfflineLanguageState) = when (value) {
        OfflineLanguageState.READY -> ui.pick("Offline language ready. Tap to speak.", "Ngôn ngữ ngoại tuyến đã sẵn sàng. Bấm để nói.")
        OfflineLanguageState.DOWNLOADABLE -> ui.pick("Download this language for offline speech.", "Cần tải ngôn ngữ này để nhận giọng nói ngoại tuyến.")
        OfflineLanguageState.PENDING -> ui.pick("Language download is scheduled. Check again when it finishes.", "Ngôn ngữ đang chờ tải. Kiểm tra lại khi tải xong nhé.")
        OfflineLanguageState.UNSUPPORTED -> ui.pick("This provider doesn't report offline support for this language. Switch language or choose instead.", "Bộ nhận dạng chưa báo hỗ trợ ngôn ngữ này ngoại tuyến. Đổi ngôn ngữ hoặc chọn bằng nút.")
        else -> ui.pick("Offline support couldn't be confirmed. You can try offline or choose instead.", "Chưa xác nhận được hỗ trợ ngoại tuyến. Có thể thử hoặc chọn bằng nút.")
    }
    /** Only called by the explicit Download language action. No recorded audio is involved. */
    fun downloadLanguage() {
        if (activity.isFinishing || activity.isDestroyed || !DeviceVisibility.allowed(activity)) return
        if (state != OfflineLanguageState.DOWNLOADABLE || checkedTag != languageTag || Build.VERSION.SDK_INT < 33) {
            checkLanguage(); return
        }
        stop(); val request = generation; val tag = languageTag
        setState(OfflineLanguageState.DOWNLOADING, ui.pick("Requesting offline language download…", "Đang yêu cầu tải ngôn ngữ ngoại tuyến…"))
        Log.d(TAG, "event=download_requested language=$tag provider=on_device")
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            if (Build.VERSION.SDK_INT >= 34) recognizer!!.triggerModelDownload(requestIntent(tag), activity.mainExecutor, object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    if (!active(request)) return
                    status(ui.pick("Downloading language… ${completedPercent.coerceIn(0, 100)}%", "Đang tải ngôn ngữ… ${completedPercent.coerceIn(0, 100)}%"))
                }
                override fun onSuccess() {
                    if (!active(request)) return
                    Log.d(TAG, "event=download_success language=$tag")
                    lastMissingTag = null; stop()
                    setState(OfflineLanguageState.READY, languageStatus(OfflineLanguageState.READY))
                    // A completed download never starts the microphone.
                }
                override fun onScheduled() {
                    if (!active(request)) return
                    Log.d(TAG, "event=download_scheduled language=$tag")
                    stop(); setState(OfflineLanguageState.PENDING, languageStatus(OfflineLanguageState.PENDING))
                }
                override fun onError(error: Int) {
                    if (!active(request)) return
                    Log.d(TAG, "event=download_error language=$tag code=$error")
                    if (error == SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS) requestDownloadWithoutListener(request, tag)
                    else {
                        stop(); setState(OfflineLanguageState.UNKNOWN,
                            ui.pick("The language download couldn't finish. Check your connection or choose instead.", "Chưa tải xong ngôn ngữ. Kiểm tra mạng hoặc chọn bằng nút."))
                    }
                }
            }) else requestDownloadWithoutListener(request, tag)
        } catch (_: Exception) {
            stop(); setState(OfflineLanguageState.UNKNOWN, ui.pick("The provider couldn't start this download. Choose instead is available.", "Bộ nhận dạng chưa tải được ngôn ngữ. Vẫn có thể chọn bằng nút."))
        }
    }
    @androidx.annotation.RequiresApi(33)
    private fun requestDownloadWithoutListener(request: Long, tag: String) {
        if (!active(request)) return
        try {
            recognizer?.triggerModelDownload(requestIntent(tag))
            setState(OfflineLanguageState.PENDING,
                ui.pick("Download requested. Check language again after it finishes.", "Đã yêu cầu tải. Kiểm tra ngôn ngữ lại sau khi tải xong nhé."))
            Log.d(TAG, "event=download_requested_without_listener language=$tag")
        } catch (_: Exception) {
            stop(); setState(OfflineLanguageState.UNKNOWN, ui.pick("Couldn't confirm the download. Choose instead is available.", "Chưa xác nhận được việc tải. Vẫn có thể chọn bằng nút."))
        }
    }
    fun listen(provider: OnlineSpeechProvider? = null) {
        if (activity.isFinishing || activity.isDestroyed || !DeviceVisibility.allowed(activity)) return
        if (provider != null && !SpeechRecognizer.isRecognitionAvailable(activity)) {
            status(ui.pick("The selected speech provider isn't available. Choose instead is still available.", "Bộ nhận dạng đã chọn chưa sẵn sàng. Vẫn có thể chọn bằng nút.")); return
        }
        if (provider != null) {
            // Consent for this attempt supersedes a pending offline metadata query.
            // The query must not later cancel the permission dialog or its captured target.
            stop()
            if (state == OfflineLanguageState.CHECKING)
                setState(OfflineLanguageState.UNKNOWN, languageStatus(OfflineLanguageState.UNKNOWN))
        }
        if (provider == null) {
            if (!available) { setState(OfflineLanguageState.UNAVAILABLE, ui.noSpeech); return }
            if (checkedTag != languageTag) { checkLanguage(); return }
            if (state !in listOf(OfflineLanguageState.READY, OfflineLanguageState.UNKNOWN)) {
                status(languageStatus(state)); return
            }
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start(provider)
        else {
            permissionAttempt = PermissionAttempt(provider, languageTag, generation)
            mode(provider)
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    private fun start(provider: OnlineSpeechProvider?) {
        if (activity.isFinishing || activity.isDestroyed || !DeviceVisibility.allowed(activity)) return
        if (provider == null && (Build.VERSION.SDK_INT < 31 || !available)) { setState(OfflineLanguageState.UNAVAILABLE, ui.noSpeech); return }
        stop(); mode(provider); status(ui.listening)
        val request = generation; val tag = languageTag
        Log.d(TAG, "event=listen_start language=$tag mode=${if (provider == null) "offline" else "online"} provider=${provider?.component?.flattenToShortString() ?: "on_device"}")
        try {
            recognizer = (if (provider == null) SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
                else SpeechRecognizer.createSpeechRecognizer(activity, provider.component)).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (active(request)) { listening(true); status(ui.listening) } }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { if (active(request)) listening(false) }
                    override fun onError(error: Int) {
                        if (!active(request)) return
                        Log.d(TAG, "event=listen_error language=$tag mode=${if (provider == null) "offline" else "online"} code=$error")
                        stop()
                        if (provider == null && error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) { lastMissingTag = tag; checkLanguage() }
                        else if (provider == null && error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                            setState(OfflineLanguageState.UNSUPPORTED, languageStatus(OfflineLanguageState.UNSUPPORTED))
                        else status(ui.pick("I didn't catch that. You can choose instead.", "Chưa nghe rõ. Mình có thể chọn bằng nút nhé."))
                    }
                    override fun onResults(results: Bundle?) {
                        if (!active(request)) return
                        val hypotheses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty().map { it.trim().take(240) }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(5)
                        Log.d(TAG, "event=listen_result language=$tag mode=${if (provider == null) "offline" else "online"} hypotheses=${hypotheses.size}")
                        stop()
                        if (hypotheses.isEmpty()) status(ui.pick("I didn't catch that. You can choose instead.", "Chưa nghe rõ. Mình có thể chọn bằng nút nhé."))
                        else { lastMissingTag = null; status(""); result(hypotheses) }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!active(request)) return
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.take(120)
                        if (!partial.isNullOrBlank()) status(partial)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(requestIntent(tag, offline = provider == null))
            }
            listening(true)
        } catch (_: Exception) {
            stop(); status(if (provider == null) ui.noSpeech else ui.pick("This speech provider couldn't start. You can choose instead.", "Chưa mở được bộ nhận dạng này. Có thể chọn bằng nút."))
        }
    }
    fun stop() {
        generation++; handler.removeCallbacksAndMessages(null)
        permissionAttempt = null; mode(null)
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null; listening(false)
    }
}
