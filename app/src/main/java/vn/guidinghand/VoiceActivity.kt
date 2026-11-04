package vn.guidinghand

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.text.InputFilter
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Small, movable, user-invoked input sheet. It never performs an action in the target app. */
class VoiceActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SPEECH_PURPOSE = "vn.guidinghand.extra.SPEECH_PURPOSE"
        const val EXTRA_REVIEW_TEXT = "vn.guidinghand.extra.REVIEW_TEXT"
        const val EXTRA_WINDOW_ID = "vn.guidinghand.extra.WINDOW_ID"
        const val EXTRA_ANCHOR_LEFT = "vn.guidinghand.extra.ANCHOR_LEFT"
        const val EXTRA_ANCHOR_TOP = "vn.guidinghand.extra.ANCHOR_TOP"
        const val EXTRA_ANCHOR_RIGHT = "vn.guidinghand.extra.ANCHOR_RIGHT"
        const val EXTRA_ANCHOR_BOTTOM = "vn.guidinghand.extra.ANCHOR_BOTTOM"
        @Volatile var visible = false; private set
        @Volatile var activeSession = false; private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recording = AtomicBoolean(false)
    private lateinit var prefs: FamilyPreferences
    private lateinit var status: TextView
    private lateinit var example: TextView
    private lateinit var waveform: TextView
    private lateinit var editor: EditText
    private lateinit var primary: Button
    private lateinit var secondary: Button
    private lateinit var cancel: Button
    private var purpose = SpeechPurpose.GENERAL
    private var reviewText: String? = null
    private var submitting = false
    private var delivered = false
    private var cancelledByUser = false
    private var awaitingPermission = false
    private var windowId = 0L
    private val companion = CompanionDrawable()
    private fun companionMood(mood: CompanionMood) {
        if (prefs.mascot != GuideMascot.YOUNG_HELPER) return
        companion.setBounds(0, 0, dp(40), dp(40))
        status.compoundDrawablePadding = dp(8)
        status.setCompoundDrawablesRelative(companion, null, null, null)
        companion.show(mood, prefs.reduceMotion)
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        awaitingPermission = false
        if (allowed) startRecording() else showError(tr(
            "Cần cho phép micro để nói. Chưa có âm thanh nào được gửi.",
            "Microphone permission is needed. No audio has been shared."
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visible = true; activeSession = true
        windowId = intent.getLongExtra(EXTRA_WINDOW_ID, 0L)
        GuidingHandService.instance?.voiceWindowOpened(windowId)
        prefs = FamilyPreferences(this)
        purpose = runCatching { SpeechPurpose.valueOf(intent.getStringExtra(EXTRA_SPEECH_PURPOSE).orEmpty()) }
            .getOrDefault(SpeechPurpose.GENERAL)
        reviewText = intent.getStringExtra(EXTRA_REVIEW_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GuideSurfaces.card(resources.displayMetrics.density)
            elevation = dp(6).toFloat()
        }
        val dragHandle = TextView(this).apply {
            text = tr("⋮⋮  Kéo để di chuyển", "⋮⋮  Drag to move")
            textSize = GuideDesign.BODY_SP; gravity = Gravity.CENTER; minHeight = dp(48)
            setTextColor(ink())
            contentDescription = tr("Giữ và kéo để di chuyển cửa sổ nghe", "Hold and drag to move the listening window")
        }
        status = TextView(this).apply {
            textSize = GuideDesign.INSTRUCTION_SP; setTextColor(ink()); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.12f); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        example = TextView(this).apply {
            textSize = GuideDesign.BODY_SP; setTextColor(ink()); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }
        waveform = TextView(this).apply {
            textSize = GuideDesign.BODY_SP; setTextColor(ink()); gravity = Gravity.CENTER
            minHeight = dp(48); visibility = View.GONE
            contentDescription = tr("Mức âm thanh và thời gian đang nghe", "Sound level and listening time")
        }
        editor = EditText(this).apply {
            textSize = GuideDesign.INSTRUCTION_SP; setTextColor(ink()); setHintTextColor(Color.rgb(98, 109, 103))
            hint = tr("Gõ điều cần giúp", "Type what you need help with")
            minHeight = dp(120); minLines = 3; maxLines = 7; gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = true; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setHorizontallyScrolling(false); filters = arrayOf(InputFilter.LengthFilter(500)); visibility = View.GONE
            background = outlinedBackground(); setPadding(dp(14), dp(10), dp(14), dp(10))
            imeHintLocales = android.os.LocaleList(java.util.Locale(prefs.language))
            contentDescription = tr("Nội dung nhận từ giọng nói. Kéo lên hoặc xuống để đọc và sửa.",
                "Transcribed speech. Scroll up or down to review and edit.")
        }
        primary = Button(this).apply { isAllCaps = false; textSize = GuideDesign.LABEL_SP; minHeight = dp(GuideDesign.MAIN_TARGET_DP) }
        secondary = Button(this).apply { isAllCaps = false; textSize = GuideDesign.LABEL_SP; minHeight = dp(GuideDesign.SECONDARY_TARGET_DP) }
        cancel = Button(this).apply {
            text = tr("Hủy", "Cancel"); isAllCaps = false; textSize = GuideDesign.LABEL_SP
            minHeight = dp(GuideDesign.SECONDARY_TARGET_DP)
            minimumWidth = dp(80); minWidth = dp(80)
            setOnClickListener { pressed(this); cancelledByUser = true; recording.set(false); finish() }
        }
        stylePrimary(primary); styleSecondary(secondary); styleSecondary(cancel)

        val secondaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(secondary, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            addView(cancel, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        }

        layout.addView(dragHandle)
        layout.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        layout.addView(example, LinearLayout.LayoutParams(-1, -2))
        layout.addView(waveform, LinearLayout.LayoutParams(-1, -2))
        layout.addView(editor, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        layout.addView(primary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        layout.addView(secondaryRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        setContentView(layout)
        example.visibility = View.GONE

        val sheetWidth = minOf(resources.displayMetrics.widthPixels - dp(32), dp(336))
        window.setLayout(sheetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sheetWidth - dp(16); y = dp(80)
        }
        // Keep the first frame invisible until the measured sheet can be placed beside Help.
        // The old post-draw move made the whole panel visibly fall to the bottom on every open.
        window.decorView.alpha = 0f
        makeDraggable(dragHandle, dp(12))
        window.decorView.post {
            val metrics = getSystemService(WindowManager::class.java).currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val safe = GuideBox(bars.left + dp(12), bars.top + dp(12),
                metrics.bounds.width() - bars.right - dp(12), metrics.bounds.height() - bars.bottom - dp(12))
            val anchor = intent.takeIf { it.hasExtra(EXTRA_ANCHOR_LEFT) }?.let {
                GuideBox(it.getIntExtra(EXTRA_ANCHOR_LEFT, 0), it.getIntExtra(EXTRA_ANCHOR_TOP, 0),
                    it.getIntExtra(EXTRA_ANCHOR_RIGHT, 0), it.getIntExtra(EXTRA_ANCHOR_BOTTOM, 0))
            }?.takeIf { safe.contains(it) }
            val placed = VoiceWindowPlacement.place(safe, sheetWidth, window.decorView.height.coerceAtLeast(dp(240)), anchor)
            window.attributes = window.attributes.apply {
                x = placed.left; y = placed.top
            }
            // Let WindowManager apply x/y before revealing the sheet. This removes the
            // one-frame flash at the provisional position without adding a timed delay.
            window.decorView.postOnAnimation { window.decorView.alpha = 1f }
        }

        if (reviewText != null) showTranscriptEditor(reviewText!!)
        else if (BackendEndpoint.base(prefs) == null) showError(tr(
            "Chưa kết nối trợ lý nhận giọng nói. Bác vẫn có thể chọn Gõ chữ.",
            "Voice guidance is not connected. You can still choose Type instead."
        )) else showReady()
    }

    /** Review happens in a focusable Activity so the elder can correct transcription safely.
     * The edited sentence is sent back to the overlay for a second explicit confirmation;
     * it never enters the target app automatically. */
    private fun showTranscriptEditor(text: String) {
        recording.set(false); submitting = false
        status.text = tr("Kiểm tra và sửa câu này", "Review and edit this sentence")
        example.text = tr("Câu bắt đầu từ dòng đầu. Bác có thể sửa trước khi dùng.",
            "The sentence starts at the beginning. You can edit before using it.")
        example.visibility = View.VISIBLE; waveform.visibility = View.GONE
        editor.visibility = View.VISIBLE; editor.setText(text); editor.setSelection(0)
        editor.post { editor.scrollTo(0, 0) }
        primary.visibility = View.VISIBLE; primary.isEnabled = true
        primary.text = tr("Dùng câu đã sửa", "Use edited text")
        primary.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        primary.setOnClickListener {
            pressed(primary)
            val value = editor.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) { status.text = tr("Vui lòng viết một câu trước.", "Please enter a sentence first."); return@setOnClickListener }
            val service = GuidingHandService.instance
            if (service == null || !service.localRecordingAllowed()) {
                showError(tr("Phiên hỗ trợ đã dừng. Bác bấm Giúp để mở lại nhé.", "This help session has stopped. Tap Help again.")); return@setOnClickListener
            }
            submitting = true; delivered = true; hideKeyboard(); service.submitEditedVoiceText(value); finish()
        }
        secondary.visibility = View.VISIBLE; secondary.isEnabled = true
        secondary.text = tr("Nói lại", "Record again")
        secondary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_gh_mic, 0, 0, 0)
        secondary.setOnClickListener { pressed(secondary); hideKeyboard(); reviewText = null; showReady() }
        editor.post { editor.requestFocus(); getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun showReady() {
        companionMood(CompanionMood.READY)
        recording.set(false); submitting = false
        status.text = readyText(); waveform.visibility = View.GONE; editor.visibility = View.GONE
        // Keep the listening choice compact. Examples add reading load and duplicate the
        // purpose-specific prompt above; reserve this row for transcript review guidance.
        example.visibility = View.GONE
        primary.visibility = View.VISIBLE; secondary.visibility = View.VISIBLE
        primary.isEnabled = true; primary.text = tr("Chạm để nói", "Tap to speak")
        primary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_gh_mic, 0, 0, 0)
        primary.compoundDrawablePadding = dp(10)
        primary.contentDescription = tr("Chạm một lần để bắt đầu nghe", "Tap once to start listening")
        primary.setOnClickListener { pressed(primary); beginRecording() }
        secondary.text = tr("Gõ chữ", "Type instead")
        secondary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_gh_keyboard, 0, 0, 0)
        secondary.compoundDrawablePadding = dp(10)
        secondary.setOnClickListener { pressed(secondary); showTyping() }
    }

    private fun beginRecording() {
        if (submitting || recording.get()) return
        if (!prefs.voiceConsent || !prefs.screenConsent) {
            android.app.AlertDialog.Builder(this)
                .setTitle(tr("Cho phép trợ lý lắng nghe?", "Allow voice guidance?"))
                .setMessage(narration(
                    "Chỉ khi bác chạm nút micro, bản ghi mới được gửi tới Meta qua máy chủ GuidingHand. Màn hình mật khẩu, thanh toán hoặc OTP luôn dừng chia sẻ.",
                    "Only after you tap the microphone is the recording sent to Meta through GuidingHand. Password, payment and OTP screens always stop sharing."
                ))
                .setPositiveButton(tr("Đồng ý", "Allow")) { _, _ ->
                    prefs.voiceConsent = true; prefs.screenConsent = true; requestMicrophonePermission()
                }
                .setNegativeButton(tr("Để sau", "Later"), null).show().also { dialog ->
                    dialog.findViewById<TextView>(android.R.id.message)?.textSize = GuideDesign.BODY_SP
                    for (id in listOf(android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEGATIVE))
                        dialog.getButton(id)?.apply { textSize = GuideDesign.LABEL_SP; minHeight = dp(72) }
                }
        } else requestMicrophonePermission()
    }

    private fun requestMicrophonePermission() {
        if (isFinishing || isDestroyed) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else { awaitingPermission = true; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startRecording() {
        if (isFinishing || isDestroyed || recording.getAndSet(true) || submitting) return
        val service = GuidingHandService.instance
        if (service == null || !service.localRecordingAllowed()) {
            recording.set(false)
            showError(tr("Phiên nói đã dừng. Bác đóng cửa sổ rồi bấm Giúp để mở lại nhé.",
                "This listening session has stopped. Close it and tap Help again.")); return
        }
        service.voiceRecordingStarted()
        companionMood(CompanionMood.LISTENING)
        status.text = listeningText(); waveform.visibility = View.VISIBLE
        primary.text = tr("Dừng nghe", "Stop listening")
        primary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_gh_stop, 0, 0, 0)
        primary.contentDescription = tr("Chạm để dừng nghe và chuyển giọng nói thành chữ", "Tap to stop listening and transcribe")
        primary.setOnClickListener {
            pressed(primary); recording.set(false); primary.isEnabled = false
            primary.text = tr("Đang dừng…", "Stopping…")
        }
        secondary.visibility = View.GONE
        scope.launch {
            try {
                val (pcm, capturedRate) = withContext(Dispatchers.IO) { captureAudio(service) }
                check(pcm.isNotEmpty()); check(service.localRecordingAllowed())
                status.text = tr("Đang chuyển giọng nói thành chữ…", "Turning speech into text…")
                companionMood(CompanionMood.THINKING)
                waveform.text = tr("Xin chờ một chút", "Please wait")
                val wave = try { PcmWave.encode(pcm, capturedRate) } finally { pcm.fill(0) }
                submitting = true; primary.isEnabled = false
                delivered = true; service.submitVoiceRecording(wave); finish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("GuidingHandVoice", "voice_failed:${error.javaClass.simpleName}")
                showError(tr("Chưa ghi được lời nói. Bác chạm Thử lại hoặc chọn Gõ chữ nhé.",
                    "I could not record that. Tap Try again or choose Type instead."))
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun captureAudio(service: GuidingHandService): Pair<ByteArray, Int> {
        val rate = listOf(PcmWave.MODEL_NATIVE_RATE, 16000).firstOrNull {
            AudioRecord.getMinBufferSize(it, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: error("unsupported_recording_rate")
        val maximumBytes = rate * 2 * 20
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096))
        val started = SystemClock.elapsedRealtime(); var lastUpdate = 0L
        var peak = 0; var sumSquares = 0.0; var sampleCount = 0
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED); recorder.startRecording()
            val output = ByteArrayOutputStream(); val buffer = ByteArray(2560)
            while (recording.get() && output.size() < maximumBytes && service.localRecordingAllowed()) {
                val count = recorder.read(buffer, 0, minOf(buffer.size, maximumBytes - output.size()))
                check(count > 0); output.write(buffer, 0, count)
                var index = 0
                while (index + 1 < count) {
                    val sample = ((buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)).toShort().toInt()
                    val magnitude = abs(sample)
                    peak = maxOf(peak, magnitude)
                    sumSquares += sample.toDouble() * sample.toDouble()
                    sampleCount++; index += 2
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 120) {
                    lastUpdate = now; val level = pcmLevel(buffer, count); val elapsed = now - started
                    runOnUiThread { updateListeningVisual(level, elapsed) }
                }
            }
            return output.toByteArray() to rate
        } finally {
            val rms = if (sampleCount == 0) 0.0 else kotlin.math.sqrt(sumSquares / sampleCount) / 32767.0
            // Privacy-safe diagnostics only: no audio bytes or transcript are logged.
            android.util.Log.i("GuidingHandVoice", "audio_capture_samples=$sampleCount peak=$peak rms=${"%.4f".format(Locale.ROOT, rms)}")
            recording.set(false); try { recorder.stop() } catch (_: Exception) {}; recorder.release()
        }
    }

    private fun pcmLevel(bytes: ByteArray, count: Int): Float {
        var peak = 0; var index = 0
        while (index + 1 < count) {
            val sample = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
            peak = maxOf(peak, abs(sample.toShort().toInt())); index += 2
        }
        return (peak / 32767f).coerceIn(0f, 1f)
    }

    private fun updateListeningVisual(level: Float, elapsedMs: Long) {
        if (!recording.get() || isFinishing) return
        val activeBars = (1 + level * 7).toInt().coerceIn(1, 8)
        val bars = (0 until 8).joinToString(" ") { if (it < activeBars) "▮" else "▯" }
        val seconds = elapsedMs / 1000
        waveform.text = String.format(Locale.ROOT, "%s   %02d:%02d", bars, seconds / 60, seconds % 60)
    }

    private fun showTyping() {
        if (recording.get() || submitting) return
        status.text = tr("Gõ điều cần giúp", "Type what you need help with")
        example.visibility = View.GONE; waveform.visibility = View.GONE; editor.visibility = View.VISIBLE; editor.requestFocus()
        primary.text = tr("Dùng nội dung này", "Use this text"); primary.contentDescription = primary.text
        primary.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        primary.isEnabled = true
        primary.setOnClickListener {
            pressed(primary)
            val value = editor.text?.toString()?.trim().orEmpty()
            if (value.isEmpty()) { status.text = tr("Vui lòng viết một câu trước.", "Type a sentence first."); return@setOnClickListener }
            val service = GuidingHandService.instance
            if (service == null || !service.localRecordingAllowed()) {
                showError(tr("Phiên hỗ trợ đã dừng. Bác đóng cửa sổ rồi bấm Giúp để mở lại nhé.",
                    "This help session has stopped. Close it and tap Help again.")); return@setOnClickListener
            }
            submitting = true; primary.isEnabled = false; editor.text?.clear()
            delivered = true; service.submitTypedInput(value); finish()
        }
        secondary.visibility = View.VISIBLE; secondary.text = tr("Quay lại nói", "Back to voice")
        secondary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_gh_mic, 0, 0, 0)
        secondary.setOnClickListener { pressed(secondary); editor.text?.clear(); hideKeyboard(); showReady() }
        editor.post { getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun showError(message: String) {
        companionMood(CompanionMood.QUESTION)
        recording.set(false); submitting = false
        status.text = if (prefs.language == "vi") prefs.respectfulLanguage.message(message) else message
        example.visibility = View.GONE
        waveform.visibility = View.GONE; editor.visibility = View.GONE
        primary.visibility = View.VISIBLE; primary.isEnabled = true
        primary.text = tr("Thử lại", "Try again"); primary.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        primary.setOnClickListener { pressed(primary); showReady() }
        secondary.visibility = View.VISIBLE; secondary.text = tr("Gõ chữ", "Type instead")
        secondary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_gh_keyboard, 0, 0, 0)
        secondary.setOnClickListener { pressed(secondary); showTyping() }
    }

    private fun stylePrimary(button: Button) {
        button.setTextColor(Color.WHITE); button.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
        button.background = stateBackground(Color.parseColor("#2D5547"), ink(), 0)
    }
    private fun styleSecondary(button: Button) {
        button.setTextColor(ink()); button.compoundDrawableTintList = ColorStateList.valueOf(ink())
        button.background = stateBackground(Color.parseColor("#EEEADF"), Color.TRANSPARENT, dp(2))
    }
    private fun stateBackground(pressed: Int, normal: Int, stroke: Int): StateListDrawable = StateListDrawable().apply {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(18).toFloat(); if (stroke > 0) setStroke(stroke, ink())
        }
        addState(intArrayOf(android.R.attr.state_pressed), shape(pressed)); addState(intArrayOf(), shape(normal))
    }
    private fun outlinedBackground() = GradientDrawable().apply {
        setColor(Color.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(2), ink())
    }
    private fun pressed(view: View) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    private fun hideKeyboard() { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(editor.windowToken, 0) }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(handle: View, margin: Int) {
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragging = false
        handle.setOnTouchListener { _, event ->
            val attrs = window.attributes
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originX = attrs.x; originY = attrs.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > ViewConfiguration.get(this).scaledTouchSlop) dragging = true
                    if (dragging) {
                        val metrics = getSystemService(WindowManager::class.java).currentWindowMetrics
                        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                        val width = window.decorView.width.coerceAtLeast(1); val height = window.decorView.height.coerceAtLeast(1)
                        attrs.x = (originX + dx.toInt()).coerceIn(bars.left + margin, maxOf(bars.left + margin, metrics.bounds.width() - bars.right - width - margin))
                        attrs.y = (originY + dy.toInt()).coerceIn(bars.top + margin, maxOf(bars.top + margin, metrics.bounds.height() - bars.bottom - height - margin))
                        window.attributes = attrs
                    }; true
                }
                MotionEvent.ACTION_UP -> { if (!dragging) handle.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onPause() { super.onPause(); if (!awaitingPermission) { recording.set(false); finish() } }
    override fun onStop() { visible = false; super.onStop() }
    override fun onDestroy() {
        companion.stop()
        recording.set(false); scope.cancel(); visible = false; activeSession = false
        GuidingHandService.instance?.voiceClosed(windowId, if (delivered || cancelledByUser) null else tr(
            "Đã đóng cửa sổ nghe. Bác có thể bấm Nói khi sẵn sàng.",
            "Listening window closed. Tap Speak when you are ready."
        ), cancelled = cancelledByUser)
        super.onDestroy()
    }

    private fun readyText() = when (purpose) {
        SpeechPurpose.MESSAGE_CONTENT -> tr("Chạm micro rồi nói nội dung tin nhắn", "Tap the microphone and say the message")
        SpeechPurpose.RECIPIENT -> tr("Chạm micro rồi nói tên người nhận", "Tap the microphone and say the recipient's name")
        SpeechPurpose.GENERAL -> if (prefs.language == "vi") prefs.respectfulLanguage.helpQuestion else "What can I help you with?"
    }
    private fun listeningText() = when (purpose) {
        SpeechPurpose.MESSAGE_CONTENT -> tr("Đang nghe nội dung tin nhắn…", "Listening for the message…")
        SpeechPurpose.RECIPIENT -> tr("Đang nghe tên người nhận…", "Listening for the recipient's name…")
        SpeechPurpose.GENERAL -> tr("Đang nghe…", "Listening…")
    }
    // Action labels are literal UI text. Only narration receives address rewriting.
    private fun tr(vi: String, en: String) = if (prefs.language == "en") en else vi
    private fun narration(vi: String, en: String) = if (prefs.language == "en") en else prefs.respectfulLanguage.message(vi)
    private fun ink() = Color.parseColor(GuideDesign.INK_HEX)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
