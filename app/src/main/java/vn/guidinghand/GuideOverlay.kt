package vn.guidinghand

import android.content.Context
import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.floor

/** One solid, readable guide card. The separate black/yellow ring never receives a touch. */
class GuideOverlay(
    private val context: Context,
    private val stop: () -> Unit,
    private val repeat: () -> Unit,
    private val openLauncher: () -> Unit,
    private val callFamily: () -> Unit,
    private val openIntake: () -> Unit,
    private val answer: (String) -> Unit = {},
    private val continueGuide: () -> Unit = {},
    private val cannotPoint: () -> Unit = {},
    private val confirmDraft: () -> Unit = {},
    private val retryDraft: () -> Unit = {},
    private val copyDraft: () -> Unit = {}
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val prefs = FamilyPreferences(context)
    private val ink = Color.parseColor(GuideDesign.INK_HEX)
    private val cream = Color.parseColor(GuideDesign.CARD_HEX)
    private val accent get() = Color.parseColor(prefs.visionProfile.accentHex)
    private fun dp(value: Int) = ceil(value * density).toInt()
    private fun px(value: Float) = value * density
    private fun tr(vi: String, en: String) = if (prefs.language == "en") en else vi
    private fun feedbackBackground(primary: Boolean, radiusDp: Float = 14f, strokeDp: Int = 1): android.graphics.drawable.Drawable {
        fun shape(fill: Int, stroke: Int) = GradientDrawable().apply {
            setColor(fill); cornerRadius = px(radiusDp); setStroke(dp(strokeDp), stroke)
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(Color.parseColor("#C7CEC9"), Color.parseColor("#66766F")))
            addState(intArrayOf(android.R.attr.state_pressed), shape(accent, ink))
            addState(intArrayOf(android.R.attr.state_focused), shape(Color.parseColor("#FFF3A0"), ink))
            addState(intArrayOf(), shape(if (primary) ink else cream, ink))
        }
        return if (prefs.reduceMotion) states else RippleDrawable(ColorStateList.valueOf(accent and 0x00FFFFFF or 0x66000000), states, null)
    }
    private fun feedbackText(primary: Boolean) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(Color.parseColor("#43534C"), ink, ink, if (primary) cream else ink)
    )
    private var region: Region? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun drawPointer(canvas: Canvas, pointer: GuidePointerPlacement) {
        val path = Path().apply {
            moveTo(40f,65f); lineTo(40f,40f); cubicTo(40f,34f,49f,34f,49f,40f); lineTo(49f,56f)
            cubicTo(51f,50f,59f,50f,61f,56f); cubicTo(66f,51f,74f,54f,74f,61f); lineTo(74f,69f)
            cubicTo(74f,82f,66f,90f,54f,90f); lineTo(50f,90f); cubicTo(43f,90f,38f,85f,34f,79f)
            lineTo(25f,65f); cubicTo(22f,60f,28f,56f,32f,60f); lineTo(40f,68f)
        }
        canvas.save()
        canvas.rotate(pointer.rotationDegrees, pointer.bounds.left + pointer.bounds.width / 2f,
            pointer.bounds.top + pointer.bounds.height / 2f)
        canvas.translate(pointer.bounds.left.toFloat(), pointer.bounds.top.toFloat())
        canvas.scale(pointer.bounds.width / 108f, pointer.bounds.height / 108f)
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.BLACK; paint.strokeWidth = 11f; canvas.drawPath(path, paint)
        paint.color = accent; paint.strokeWidth = 5f; canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.BLACK; canvas.drawCircle(44.5f, 18f, 11f, paint)
        paint.color = accent; canvas.drawCircle(44.5f, 18f, 7f, paint)
        canvas.restore()
    }
    private val ring = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val target = region ?: return
            val actual = RectF(target.left * width, target.top * height, target.right * width, target.bottom * height)
            // The inside edge of yellow remains 12dp away from the pointed control.
            val inner = px(GuideDesign.RING_INNER_DP)
            val outer = px(GuideDesign.RING_OUTER_DP)
            val targetBox = GuideBox(floor(actual.left).toInt(), floor(actual.top).toInt(),
                ceil(actual.right).toInt(), ceil(actual.bottom).toInt())
            val bands = GuideRingGeometry.visibleBands(targetBox, GuideBox(0, 0, width, height), density)
            val black = RectF(bands.blackCenter.left.toFloat(), bands.blackCenter.top.toFloat(),
                bands.blackCenter.right.toFloat(), bands.blackCenter.bottom.toFloat())
            val yellow = RectF(bands.yellowCenter.left.toFloat(), bands.yellowCenter.top.toFloat(),
                bands.yellowCenter.right.toFloat(), bands.yellowCenter.bottom.toFloat())
            paint.style = Paint.Style.STROKE
            paint.color = Color.BLACK; paint.strokeWidth = outer
            canvas.drawRoundRect(black, px(18f), px(18f), paint)
            paint.color = accent; paint.strokeWidth = inner
            canvas.drawRoundRect(yellow, px(12f), px(12f), paint)
            if (prefs.showPointer) drawPointer(canvas,
                GuideRingGeometry.pointerPlacement(targetBox, GuideBox(0, 0, width, height), dp(56), dp(12)))
        }
    }
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            setColor(cream); cornerRadius = px(20f)
            setStroke(dp(1), ink)
        }
        elevation = px(4f)
    }
    private val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val brand = TextView(context).apply {
        text = "GuidingHand"; textSize = GuideDesign.BODY_SP; setTextColor(ink)
        setPadding(0, 0, dp(8), 0)
    }
    private val instruction = TextView(context).apply {
        textSize = GuideDesign.INSTRUCTION_SP; setTextColor(ink)
        setLineSpacing(px(3f), 1f)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        // No fixed line limit: large system text reflows into the scrollable content area.
    }
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val draftValue = TextView(context).apply {
        textSize = GuideDesign.INSTRUCTION_SP; setTextColor(ink); setLineSpacing(px(3f), 1f)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }
    private val draftRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
        background = GradientDrawable().apply { setColor(Color.parseColor("#F2EEE3")); cornerRadius = px(10f) }
        addView(View(context).apply { setBackgroundColor(Color.parseColor(GuideDesign.YELLOW_HEX)) }, LinearLayout.LayoutParams(dp(5), -1))
        addView(draftValue, LinearLayout.LayoutParams(0, -2, 1f))
    }
    private val heardSpeech = TextView(context).apply {
        textSize = GuideDesign.BODY_SP; setTextColor(ink); maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END; visibility = View.GONE
        setPadding(0, 0, 0, dp(8))
    }
    fun setHeardSpeech(value: String) {
        heardSpeech.text = tr("Bác nói: ", "You said: ") + value
        heardSpeech.visibility = View.VISIBLE
        savedState?.let { render(it, savedCanOpen) }
    }
    private val primaryActions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val footer = LinearLayout(context)
    private val scroll = ScrollView(context).apply {
        isFillViewport = false; isVerticalScrollBarEnabled = true
        setPadding(0, dp(8), 0, dp(8))
        addView(content, FrameLayout.LayoutParams(-1, -2))
    }
    private val collapse = makeButton(tr("Thu gọn", "Hide"), false) {
        userCollapsed = true; savedState?.let { render(it, savedCanOpen) }
    }
    private val bubble = TextView(context).apply {
        gravity = Gravity.CENTER; textSize = GuideDesign.LABEL_SP; setTextColor(feedbackText(true))
        minWidth = dp(GuideDesign.MAIN_TARGET_DP); minHeight = dp(GuideDesign.MAIN_TARGET_DP)
        setPadding(dp(14), dp(10), dp(18), dp(10)); compoundDrawablePadding = dp(8)
        backgroundTintList = null; background = feedbackBackground(true, 28f, 2)
        elevation = px(7f)
    }
    private var ringAttached = false
    private var cardAttached = false
    private var bubbleAttached = false
    private var userCollapsed = false
    private var autoCollapsed = false
    private var savedState: GuideViewState? = null
    private var savedCanOpen = false
    private var cardPosition: WindowManager.LayoutParams? = null
    private var draggedCard: GuideBox? = null
    private var draggedBubble: GuideBox? = null
    private var lastLog = ""
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastRejectedFrame: String? = null
    private var pointingClipped = false
    private var busyLabel: String? = null
    private var bubbleHasHand: Boolean? = null

    data class Measurement(val panel: GuideBox?, val pointedArea: GuideBox?, val coverage: Double,
                           val collapsed: Boolean, val scrollContent: Boolean, val pointingClipped: Boolean = false)
    var lastMeasurement: Measurement? = null; private set

    init {
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(collapse, LinearLayout.LayoutParams(-2, -2))
        content.addView(heardSpeech, LinearLayout.LayoutParams(-1, -2))
        content.addView(instruction, LinearLayout.LayoutParams(-1, -2))
        content.addView(draftRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(primaryActions, LinearLayout.LayoutParams(-1, -2))
        card.addView(header, LinearLayout.LayoutParams(-1, -2))
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        card.addView(footer, LinearLayout.LayoutParams(-1, -2))
        draggable(brand, card) { cardPosition }
        draggable(bubble, bubble) { bubble.layoutParams as? WindowManager.LayoutParams }
        bubble.setOnClickListener {
            if (autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED) callFamily()
            else if (autoCollapsed) openIntake()
            else if (userCollapsed) { userCollapsed = false; savedState?.let { render(it, savedCanOpen) } }
            else openIntake()
        }
    }

    private fun makeButton(label: String, primary: Boolean, callback: () -> Unit) = Button(context).apply {
        text = label; textSize = GuideDesign.LABEL_SP; isAllCaps = false
        minHeight = dp(if (primary) GuideDesign.MAIN_TARGET_DP else GuideDesign.SECONDARY_TARGET_DP)
        minimumHeight = minHeight; minimumWidth = 0; minWidth = 0
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setTextColor(feedbackText(primary)); backgroundTintList = null
        background = feedbackBackground(primary)
        setOnClickListener { callback() }
    }

    private fun addPrimary(label: String, callback: () -> Unit) {
        primaryActions.addView(makeButton(label, true, callback), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
    }
    private fun addSecondary(label: String, callback: () -> Unit) {
        primaryActions.addView(makeButton(label, false, callback), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
    }

    private fun params(width: Int, height: Int, nonTouchable: Boolean) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (nonTouchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0), android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun safeArea(): GuideBox {
        val metrics = wm.currentWindowMetrics
        val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val keyboard = metrics.windowInsets.getInsets(WindowInsets.Type.ime())
        // Service contexts can report zero IME insets. Its interactive-window bounds remain a second safe source.
        val keyboardTop = (context as? AccessibilityService)?.windows
            ?.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?.map { window -> Rect().also { window.getBoundsInScreen(it) }.top }
            ?.minOrNull() ?: metrics.bounds.height()
        return GuideBox(bars.left + dp(12), bars.top + dp(12), metrics.bounds.width() - bars.right - dp(12),
            minOf(metrics.bounds.height() - maxOf(bars.bottom, keyboard.bottom), keyboardTop) - dp(12))
    }

    private fun pointedBox(): GuideBox? = region?.let {
        val bounds = wm.currentWindowMetrics.bounds
        val display = GuideBox(0, 0, bounds.width(), bounds.height())
        val target = GuideBox(floor(it.left * bounds.width()).toInt(), floor(it.top * bounds.height()).toInt(),
            ceil(it.right * bounds.width()).toInt(), ceil(it.bottom * bounds.height()).toInt())
        val ringBounds = GuideRingGeometry.paddedBounds(target, density)
        if (prefs.showPointer) ringBounds.union(GuideRingGeometry.pointerPlacement(target, display, dp(56), dp(12)).bounds)
        else ringBounds
    }

    fun showVoiceNotice(text: String) { render(GuideViewState(GuideStatus.ASKING, text), false) }
    fun showVoiceBusy(text: String) {
        if (busyLabel == text && bubbleAttached && !cardAttached && !ringAttached) return
        busyLabel = text
        clearRing(); clearCard()
        showBubble()
    }
    fun showDraftReview(text: String, purpose: SpeechPurpose) = render(GuideViewState(GuideStatus.CONFIRMING_INPUT,
        if (purpose == SpeechPurpose.MESSAGE_CONTENT) tr("Bác muốn viết:", "You would like to write:")
        else tr("Bác nói:", "You said:"), speechPurpose = purpose, draftText = text), false)
    fun showCopyConsent(text: String) = render(GuideViewState(GuideStatus.CONFIRMING_COPY,
        tr("Sao chép tạm nội dung này? Bác vẫn tự bấm Dán và Gửi trong ứng dụng.",
            "Copy this message temporarily? You will still tap Paste and Send in the app."), draftText = text), false)

    fun showBubble() {
        if (!DeviceVisibility.allowed(context)) return
        if (cardAttached) { removeBubble(); return }
        val label = when {
            busyLabel != null -> busyLabel!!
            autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED -> tr("Gọi người thân", "Call family")
            userCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED -> tr("Tạm dừng", "Paused")
            autoCollapsed -> tr("Nói", "Speak")
            userCollapsed -> tr("Tiếp tục", "Continue")
            else -> tr("Giúp", "Help")
        }
        if (bubble.text.toString() != label) bubble.text = label
        val showHand = busyLabel == null && !(autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED)
        if (bubbleHasHand != showHand) {
            val hand = if (showHand) context.getDrawable(vn.guidinghand.R.drawable.guidinghand_icon)?.mutate()?.apply {
                setBounds(0, 0, dp(40), dp(40))
            } else null
            bubble.setCompoundDrawablesRelative(hand, null, null, null)
            bubbleHasHand = showHand
        }
        val enabled = busyLabel == null
        if (bubble.isEnabled != enabled) bubble.isEnabled = enabled
        val alpha = if (enabled) 1f else .88f
        if (bubble.alpha != alpha) bubble.alpha = alpha
        val description = when {
            busyLabel != null -> label
            autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED -> tr("Đã tạm dừng. Bấm để mở trình quay số cho người thân.", "Paused. Tap to open the family contact in the dialer.")
            autoCollapsed -> tr("Không đủ chỗ hiện thẻ mà không che nút. Bấm để nói hoặc yêu cầu nhắc lại.", "The guide is compact to keep the target clear. Tap to speak or ask to repeat.")
            userCollapsed -> tr("Mở lại hướng dẫn", "Show the guide")
            else -> tr("Bấm để nói việc cần giúp", "Tap to speak about what you need")
        }
        if (bubble.contentDescription != description) bubble.contentDescription = description
        bubble.measure(View.MeasureSpec.makeMeasureSpec(safeArea().width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val placement = GuidePanelLayout.place(safeArea(), bubble.measuredWidth.coerceAtLeast(dp(72)),
            bubble.measuredHeight.coerceAtLeast(dp(72)), dp(72), pointedBox(), dp(8), draggedBubble) ?: return
        val box = placement.bounds
        val p = params(box.width, box.height, false).apply { x = box.left; y = box.top }
        if (!bubbleAttached) { wm.addView(bubble, p); bubbleAttached = true; GuideTrace.event(context, "bubble_attached") }
        else {
            val current = bubble.layoutParams as WindowManager.LayoutParams
            if (current.x != p.x || current.y != p.y || current.width != p.width || current.height != p.height) wm.updateViewLayout(bubble, p)
        }
    }

    fun render(state: GuideViewState, canOpenLauncher: Boolean) {
        busyLabel = null
        val previousStatus = savedState?.status
        savedState = state; savedCanOpen = canOpenLauncher
        // Keep the same non-touchable ring mounted while the elder is looking for the target.
        // Window churn here made it appear to blink during ordinary screen events.
        if (state.status != GuideStatus.WAITING_TAP || region != state.step?.sam?.region) clearRing()
        pointingClipped = false
        if (!DeviceVisibility.allowed(context)) { hide(); return }
        if (state.status == GuideStatus.IDLE) {
            heardSpeech.text = ""; heardSpeech.visibility = View.GONE
            userCollapsed = false; autoCollapsed = false; draggedCard = null; lastRejectedFrame = null; clearCard(); showBubble(); return
        }
        if (state.status == GuideStatus.SAFETY_PAUSED && previousStatus != GuideStatus.SAFETY_PAUSED) userCollapsed = false
        removeBubble()
        instruction.text = state.instructionVi
        instruction.accessibilityLiveRegion = when (state.status) {
            GuideStatus.SAFETY_PAUSED, GuideStatus.UNAVAILABLE, GuideStatus.ASKING ->
                View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            else -> View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        draftValue.text = state.draftText?.let { "“$it”" }.orEmpty()
        draftRow.visibility = if (state.draftText.isNullOrBlank()) View.GONE else View.VISIBLE
        heardSpeech.visibility = if (heardSpeech.text.isNotBlank() && state.status !in setOf(GuideStatus.WAITING_TAP, GuideStatus.WAITING_NAVIGATION)) View.VISIBLE else View.GONE
        collapse.text = tr("Thu gọn", "Hide")
        header.orientation = if (context.resources.configuration.fontScale > 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        brand.layoutParams = if (header.orientation == LinearLayout.VERTICAL) LinearLayout.LayoutParams(-1, -2)
            else LinearLayout.LayoutParams(0, -2, 1f)
        primaryActions.removeAllViews(); footer.removeAllViews()
        if (state.status != GuideStatus.SAFETY_PAUSED) {
            if (state.status == GuideStatus.CONFIRMING_INPUT) {
                addPrimary(tr("Đúng rồi", "Use this"), confirmDraft)
                addSecondary(tr("Nói lại", "Record again"), retryDraft)
            }
            if (state.status == GuideStatus.CONFIRMING_COPY)
                addPrimary(tr("Sao chép nội dung", "Copy message"), copyDraft)
            if (state.status in setOf(GuideStatus.CLARIFYING, GuideStatus.UNSUPPORTED)) state.options.forEach { option -> addPrimary(option) { answer(option) } }
            if (canOpenLauncher && state.status == GuideStatus.WAITING_TAP && state.step?.stage == PhotoStage.LAUNCHER)
                addPrimary(tr("Mở giúp tôi", "Open for me"), openLauncher)
        }
        // Retry must never live in the clipped/scrolling card body. Every recoverable
        // read/model failure gets the same fixed, full-width action near the elder's thumb.
        val pinnedRetry = state.status in setOf(GuideStatus.ASKING, GuideStatus.UNAVAILABLE)
        footer.orientation = if (state.status == GuideStatus.SAFETY_PAUSED || pinnedRetry || context.resources.configuration.fontScale > 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        fun footerAction(label: String, primary: Boolean = false, callback: () -> Unit) {
            val p = if (footer.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f)
                else LinearLayout.LayoutParams(-1, -2)
            p.setMargins(dp(2), dp(4), dp(2), 0)
            footer.addView(makeButton(label, primary, callback), p)
        }
        if (state.status == GuideStatus.SAFETY_PAUSED) footer.addView(makeButton(tr("Gọi người thân", "Call family"), true, callFamily),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        else if (pinnedRetry) footerAction(tr("Kiểm tra lại", "Check again"), true, continueGuide)
        else {
            val busy = state.status in setOf(
                GuideStatus.READING,
                GuideStatus.PLANNING,
                GuideStatus.VERIFYING,
                GuideStatus.RECOVERING
            )
            if (!busy && state.status !in setOf(GuideStatus.CONFIRMING_INPUT, GuideStatus.CONFIRMING_COPY)) {
                // The three 22sp labels share one row; short visible names keep them on one
                // line at normal font scale. Screen readers still get the full action.
                footerAction(tr("Nói", "Speak"), true, openIntake)
                footer.getChildAt(footer.childCount - 1).contentDescription =
                    if (state.speechPurpose == SpeechPurpose.MESSAGE_CONTENT)
                        tr("Nói nội dung tin nhắn", "Dictate the message")
                    else tr("Nói việc cần giúp", "Speak your request")
                footerAction(tr("Nghe", "Replay"), callback = repeat)
                footer.getChildAt(footer.childCount - 1).contentDescription =
                    tr("Nghe lại hướng dẫn", "Replay the instruction")
            }
        }
        footerAction(tr("Dừng", "Stop"), callback = stop)
        val target = state.step?.sam?.region
        if (state.status == GuideStatus.WAITING_TAP && target != null) {
            region = target
            val bounds = wm.currentWindowMetrics.bounds
            val ringBounds = pointedBox()
            val targetPixels = GuideBox(floor(target.left * bounds.width()).toInt(), floor(target.top * bounds.height()).toInt(),
                ceil(target.right * bounds.width()).toInt(), ceil(target.bottom * bounds.height()).toInt())
            val display = GuideBox(0, 0, bounds.width(), bounds.height())
            if (ringBounds != null && display.contains(targetPixels)) {
                pointingClipped = !display.contains(ringBounds)
                if (!ringAttached) { wm.addView(ring, params(-1, -1, true)); ringAttached = true }
                ring.invalidate()
            } else {
                // Invalid/off-display geometry still stops. Legitimate edge targets are
                // rendered above with both strokes pulled fully inside the display.
                pointingClipped = true; region = null; userCollapsed = false
                instruction.text = tr("Tôi chưa thể khoanh rõ vị trí sát mép màn hình. Bác kiểm tra lại hoặc dừng giúp tôi nhé.",
                    "The target is too close to the screen edge to outline clearly. Please check again or stop.")
                primaryActions.removeAllViews()
                addPrimary(tr("Kiểm tra lại", "Check again"), continueGuide)
                val frame = state.step?.sam?.frameFingerprint.orEmpty()
                if (lastRejectedFrame != frame) {
                    lastRejectedFrame = frame
                    // Present the readable error first; never recursively re-enter render from this stack.
                    uiHandler.post {
                        if (savedState?.status == GuideStatus.WAITING_TAP && savedState?.step?.sam?.frameFingerprint == frame)
                            cannotPoint()
                    }
                }
            }
        }
        autoCollapsed = false
        if (userCollapsed) { clearCard(); showBubble(); recordMeasurement(null, false, state); return }
        layoutCard(state)
    }

    private fun layoutCard(state: GuideViewState) {
        val safe = safeArea(); val width = minOf(safe.width, dp(344))
        if (width <= 0) { clearCard(); return }
        scroll.layoutParams = LinearLayout.LayoutParams(-1, -2)
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val minimumHeight = header.measuredHeight + footer.measuredHeight + card.paddingTop + card.paddingBottom + dp(72)
        val naturalHeight = card.measuredHeight
        // Cap screen coverage, not text size. Large text and choices scroll inside a solid card;
        // Repeat/Stop stay outside the scroll area. Accessibility minima win over the cap.
        val desiredHeight = minOf(naturalHeight, maxOf(minimumHeight, (safe.height * GuideDesign.CARD_SCREEN_FRACTION).toInt()))
        val placement = GuidePanelLayout.place(safe, width, desiredHeight, minimumHeight, pointedBox(), dp(8), draggedCard)
        if (placement == null) {
            clearCard(); autoCollapsed = true; showBubble(); recordMeasurement(null, false, state); return
        }
        val box = placement.bounds
        val scrolling = placement.scrollContent || box.height < naturalHeight
        if (scrolling) scroll.layoutParams = LinearLayout.LayoutParams(-1,
            (box.height - header.measuredHeight - footer.measuredHeight - card.paddingTop - card.paddingBottom).coerceAtLeast(dp(72)))
        val p = params(box.width, box.height, false).apply { x = box.left; y = box.top }
        cardPosition = p
        if (!cardAttached) { wm.addView(card, p); cardAttached = true } else wm.updateViewLayout(card, p)
        recordMeasurement(box, scrolling, state)
    }

    private fun recordMeasurement(box: GuideBox?, scrolling: Boolean, state: GuideViewState) {
        val target = pointedBox()
        val coverage = if (box != null && target != null) GuidePanelLayout.coverageRatio(box, target) else 0.0
        lastMeasurement = Measurement(box, target, coverage, box == null, scrolling, pointingClipped)
        // Geometry and enum only. No screen labels, recipient, goal, audio or images in logs.
        val value = "status=${state.status},panel=$box,target=$target,coverage=$coverage,collapsed=${box == null},scroll=$scrolling,pointingClipped=$pointingClipped,fontScale=${context.resources.configuration.fontScale}"
        if (lastLog != value) { Log.i("GuidingHandOverlay", value); lastLog = value }
    }

    /** The only drag operation moves our own guide window; it never injects input into the target app. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun draggable(handle: View, window: View, layout: () -> WindowManager.LayoutParams?) {
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragging = false
        handle.setOnTouchListener { _, event ->
            val p = layout() ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { handle.isPressed = true; downX = event.rawX; downY = event.rawY; originX = p.x; originY = p.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > ViewConfiguration.get(context).scaledTouchSlop) dragging = true
                    if (dragging) {
                        handle.isPressed = false
                        val safe = safeArea()
                        val left = (originX + dx.toInt()).coerceIn(safe.left, maxOf(safe.left, safe.right - window.width))
                        val top = (originY + dy.toInt()).coerceIn(safe.top, maxOf(safe.top, safe.bottom - window.height))
                        val candidate = GuideBox(left, top, left + window.width, top + window.height)
                        if (pointedBox()?.inflate(dp(8))?.intersects(candidate) != true) {
                            p.x = left; p.y = top; wm.updateViewLayout(window, p)
                            if (window === card) { draggedCard = candidate; savedState?.let { recordMeasurement(candidate, scroll.height < content.height, it) } }
                            else draggedBubble = candidate
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { handle.isPressed = false; if (!dragging) handle.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { handle.isPressed = false; true }
                else -> false
            }
        }
    }
    private fun clearRing() { region = null; if (ringAttached) { wm.removeView(ring); ringAttached = false } }
    private fun clearCard() { if (cardAttached) { wm.removeView(card); cardAttached = false } }
    private fun removeBubble() { if (bubbleAttached) { wm.removeView(bubble); bubbleAttached = false; GuideTrace.event(context, "bubble_detached") } }
    fun suspendForCapture() { clearRing(); clearCard(); removeBubble() }
    fun refreshPreferences() {
        bubble.setTextColor(feedbackText(true)); bubble.background = feedbackBackground(true, 28f, 2)
        collapse.setTextColor(feedbackText(false)); collapse.background = feedbackBackground(false)
        savedState?.let { render(it, savedCanOpen) } ?: showBubble()
    }
    fun hide() { busyLabel = null; uiHandler.removeCallbacksAndMessages(null); suspendForCapture() }
}
