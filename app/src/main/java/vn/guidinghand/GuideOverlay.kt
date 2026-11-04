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
import android.util.TypedValue
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
    private val copyDraft: () -> Unit = {},
    private val editDraft: () -> Unit = {},
    private val suppressOverlay: () -> Boolean = { false }
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val prefs = FamilyPreferences(context)
    private val ink = Color.parseColor(GuideDesign.INK_HEX)
    private val cream = Color.parseColor(GuideDesign.CARD_HEX)
    private val accent get() = Color.parseColor(prefs.visionProfile.accentHex)
    private fun dp(value: Int) = ceil(value * density).toInt()
    private fun px(value: Float) = value * density
    private fun sp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
    private fun tr(vi: String, en: String) = if (prefs.language == "en") en else vi
    private fun narration(vi: String, en: String) = if (prefs.language == "en") en else prefs.respectfulLanguage.message(vi)
    private fun feedbackBackground(primary: Boolean, radiusDp: Float = 14f, strokeDp: Int = 1): android.graphics.drawable.Drawable {
        fun shape(fill: Int, stroke: Int) = GradientDrawable().apply {
            setColor(fill); cornerRadius = px(radiusDp); setStroke(dp(strokeDp), stroke)
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(Color.parseColor("#C7CEC9"), Color.parseColor("#66766F")))
            addState(intArrayOf(android.R.attr.state_pressed), shape(accent, ink))
            addState(intArrayOf(android.R.attr.state_focused), shape(Color.parseColor("#FFF3A0"), ink))
            addState(intArrayOf(), if (primary) GuideSurfaces.help(density, radiusDp) else GuideSurfaces.card(density, radiusDp))
        }
        return if (prefs.reduceMotion) states else RippleDrawable(ColorStateList.valueOf(accent and 0x00FFFFFF or 0x66000000), states, null)
    }
    private fun feedbackText(primary: Boolean) = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(Color.parseColor("#43534C"), ink, ink, if (primary) cream else ink)
    )
    private var region: Region? = null
    private var navigationCue = NavigationCue.NONE
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
    private val companionPointer by lazy {
        context.getDrawable(vn.guidinghand.R.drawable.guidinghand_companion_pointer)?.mutate()
    }
    private fun drawCompanionCue(canvas: Canvas, pointer: GuidePointerPlacement, label: String) {
        val box = pointer.bounds
        val characterSize = minOf(dp(58), box.width, box.height)
        val labelWidth = minOf(dp(92), box.width)
        val labelHeight = minOf(dp(38), box.height)
        val character: GuideBox
        val labelBox: GuideBox
        when (pointer.rotationDegrees) {
            180f -> {
                character = GuideBox(box.left + (box.width - characterSize) / 2, box.top,
                    box.left + (box.width + characterSize) / 2, box.top + characterSize)
                labelBox = GuideBox(box.left + (box.width - labelWidth) / 2, box.bottom - labelHeight,
                    box.left + (box.width + labelWidth) / 2, box.bottom)
            }
            270f -> {
                labelBox = GuideBox(box.left, box.top + (box.height - labelHeight) / 2,
                    box.left + labelWidth, box.top + (box.height + labelHeight) / 2)
                character = GuideBox(box.right - characterSize, box.top + (box.height - characterSize) / 2,
                    box.right, box.top + (box.height + characterSize) / 2)
            }
            90f -> {
                character = GuideBox(box.left, box.top + (box.height - characterSize) / 2,
                    box.left + characterSize, box.top + (box.height + characterSize) / 2)
                labelBox = GuideBox(box.right - labelWidth, box.top + (box.height - labelHeight) / 2,
                    box.right, box.top + (box.height + labelHeight) / 2)
            }
            else -> {
                labelBox = GuideBox(box.left + (box.width - labelWidth) / 2, box.top,
                    box.left + (box.width + labelWidth) / 2, box.top + labelHeight)
                character = GuideBox(box.left + (box.width - characterSize) / 2, box.bottom - characterSize,
                    box.left + (box.width + characterSize) / 2, box.bottom)
            }
        }
        companionPointer?.let { drawable ->
            drawable.bounds = Rect(character.left, character.top, character.right, character.bottom)
            drawable.draw(canvas)
        }
        val callout = RectF(labelBox.left.toFloat(), labelBox.top.toFloat(), labelBox.right.toFloat(), labelBox.bottom.toFloat())
        paint.style = Paint.Style.FILL; paint.color = cream
        canvas.drawRoundRect(callout, px(13f), px(13f), paint)
        paint.style = Paint.Style.STROKE; paint.color = Color.BLACK; paint.strokeWidth = px(2f)
        canvas.drawRoundRect(callout, px(13f), px(13f), paint)
        paint.style = Paint.Style.FILL; paint.color = ink; paint.textSize = sp(20f)
        paint.textAlign = Paint.Align.CENTER; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val baseline = callout.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, callout.centerX(), baseline, paint)
        paint.typeface = null; paint.textAlign = Paint.Align.LEFT
    }
    private fun pointerSizeDp() = if (prefs.mascot == GuideMascot.YOUNG_HELPER) 104 else 56
    private fun pointerOverlapDp() = if (prefs.mascot == GuideMascot.YOUNG_HELPER) 0 else 12
    private fun drawGuidePointer(canvas: Canvas, target: GuideBox, display: GuideBox) {
        val placement = GuideRingGeometry.pointerPlacement(
            target, display, dp(pointerSizeDp()), dp(pointerOverlapDp()))
        if (prefs.mascot == GuideMascot.YOUNG_HELPER)
            drawCompanionCue(canvas, placement, tr("Bấm đây", "Tap here"))
        else drawPointer(canvas, placement)
    }
    private val ring = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val target = region
            if (target == null) {
                if (navigationCue == NavigationCue.HOME_SWIPE_UP) drawHomeSwipeCue(canvas)
                return
            }
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
            if (prefs.showPointer) drawGuidePointer(canvas, targetBox, GuideBox(0, 0, width, height))
        }
    }
    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GuideSurfaces.card(density)
        elevation = px(6f)
    }
    private val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val compactInstruction = TextView(context).apply {
        textSize = GuideDesign.INSTRUCTION_SP; setTextColor(ink)
        setLineSpacing(px(3f), 1f); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val brand = TextView(context).apply {
        text = "GuidingHand"; textSize = GuideDesign.BODY_SP; setTextColor(ink)
        gravity = Gravity.CENTER_VERTICAL; minHeight = dp(GuideDesign.SECONDARY_TARGET_DP)
        setPadding(dp(8), 0, dp(8), 0); compoundDrawablePadding = dp(6)
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
        maxLines = 3
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }
    private val draftRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
        background = GradientDrawable().apply { setColor(Color.parseColor("#F2EEE3")); cornerRadius = px(10f) }
        addView(View(context).apply { setBackgroundColor(Color.parseColor(GuideDesign.YELLOW_HEX)) }, LinearLayout.LayoutParams(dp(5), -1))
        addView(draftValue, LinearLayout.LayoutParams(0, -2, 1f))
    }
    private val draftContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; visibility = View.GONE
        addView(draftRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    private val heardSpeech = TextView(context).apply {
        textSize = GuideDesign.BODY_SP; setTextColor(ink); maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END; visibility = View.GONE
        setPadding(0, 0, 0, dp(8))
    }
    private val heardSpeechMemory = EphemeralDisplayText()
    fun setHeardSpeech(value: String) {
        heardSpeechMemory.replace(narration("B\u00e1c v\u1eeba n\u00f3i:", "You just said:") + " \u201c" + value.trim() + "\u201d")
        heardSpeech.text = heardSpeechMemory.value().orEmpty()
        heardSpeech.visibility = View.VISIBLE
        savedState?.let { render(it, savedCanOpen) }
    }
    fun clearHeardSpeech() {
        heardSpeechMemory.clear()
        heardSpeech.text = ""
        heardSpeech.visibility = View.GONE
    }
    private val primaryActions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val footer = LinearLayout(context)
    private val draftPagerBack = makeButton(tr("Quay lại", "Back"), false) { moveDraftPage(-1) }
    private val draftPagerNext = makeButton(tr("Tiếp", "Next"), true) { moveDraftPage(1) }
    private val draftPagerIndicator = TextView(context).apply {
        textSize = GuideDesign.BODY_SP; setTextColor(ink); gravity = Gravity.CENTER
        minHeight = dp(GuideDesign.SECONDARY_TARGET_DP)
    }
    private val draftPager = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
        addView(draftPagerBack, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        addView(draftPagerIndicator, LinearLayout.LayoutParams(-2, -2).apply {
            marginStart = dp(4); marginEnd = dp(4)
        })
        addView(draftPagerNext, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
    }
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
    private var intakeDismissed = false
    private var autoCollapsed = false
    private var savedState: GuideViewState? = null
    private var savedCanOpen = false
    private var cardPosition: WindowManager.LayoutParams? = null
    private var lastCardContentKey: String? = null
    private var lastRenderKey: String? = null
    private var pagedDraft: String? = null
    private var draftPages: List<String> = emptyList()
    private var draftPageIndex = 0
    private var draggedCard: GuideBox? = null
    private var draggedBubble: GuideBox? = null
    private var criticalActionBoxes: List<GuideBox> = emptyList()
    private val bubbleAnchor = GuideAnchorState()
    private var lastLog = ""
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastBusyKey: String? = null
    private var busyStartElapsed: Long? = null
    private var lastBusyText: String? = null
    private val busyElapsed = TextView(context).apply {
        textSize = GuideDesign.BODY_SP
        setTextColor(ink)
        setPadding(0, dp(6), 0, 0)
        visibility = View.GONE
    }
    private fun updateBusyElapsed() {
        val start = busyStartElapsed ?: return
        val seconds = ((android.os.SystemClock.elapsedRealtime() - start) / 1000).coerceAtLeast(0)
        val text = busyElapsedText(seconds, prefs.language == "vi")
        if (lastBusyText != text) {
            lastBusyText = text
            busyElapsed.text = text
        }
    }
    private val busyTick = object : Runnable {
        override fun run() {
            val busy = savedState?.status in setOf(GuideStatus.READING, GuideStatus.PLANNING, GuideStatus.VERIFYING, GuideStatus.RECOVERING)
            if (busyStartElapsed == null || !busy || !cardAttached) return
            updateBusyElapsed()
            uiHandler.postDelayed(this, 1000)
        }
    }
    private fun stopBusyTick() {
        lastBusyKey = null; busyStartElapsed = null; lastBusyText = null
        uiHandler.removeCallbacks(busyTick)
        busyElapsed.visibility = View.GONE
    }
    private var lastRejectedFrame: String? = null
    private var pointingClipped = false
    private var bubbleIconKey: String? = null
    private val bubbleCompanion = CompanionDrawable()
    private val cardCompanion = CompanionDrawable()

    private fun moveDraftPage(delta: Int) {
        val next = (draftPageIndex + delta).coerceIn(0, (draftPages.size - 1).coerceAtLeast(0))
        if (next == draftPageIndex) return
        draftPageIndex = next
        lastRenderKey = null
        savedState?.let { render(it, savedCanOpen) }
    }

    private fun bubbleBounds(): GuideBox? {
        if (!bubbleAttached) return null
        val p = bubble.layoutParams as? WindowManager.LayoutParams ?: return null
        return GuideBox(p.x, p.y, p.x + bubble.width.coerceAtLeast(p.width), p.y + bubble.height.coerceAtLeast(p.height))
    }

    /** Snapshot before the focusable microphone window removes every accessibility overlay. */
    fun helpAnchor(): GuideBox? = bubbleBounds() ?: draggedBubble ?: bubbleAnchor.current()

    data class Measurement(val panel: GuideBox?, val pointedArea: GuideBox?, val coverage: Double,
                           val collapsed: Boolean, val scrollContent: Boolean, val pointingClipped: Boolean = false)
    var lastMeasurement: Measurement? = null; private set

    init {
        header.addView(compactInstruction, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(collapse, LinearLayout.LayoutParams(-2, -2))
        content.addView(heardSpeech, LinearLayout.LayoutParams(-1, -2))
        content.addView(instruction, LinearLayout.LayoutParams(-1, -2))
        content.addView(draftContainer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(primaryActions, LinearLayout.LayoutParams(-1, -2))
        card.addView(header, LinearLayout.LayoutParams(-1, -2))
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        card.addView(busyElapsed, LinearLayout.LayoutParams(-1, -2))
        card.addView(draftPager, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        card.addView(footer, LinearLayout.LayoutParams(-1, -2))
        // Help is the single movable handle while guidance is open. Keeping the card itself
        // non-draggable prevents the two overlay windows from being pulled apart.
        brand.setOnClickListener {
            userCollapsed = true
            savedState?.let { render(it, savedCanOpen) }
        }
        draggable(bubble, bubble) { bubble.layoutParams as? WindowManager.LayoutParams }
        bubble.setOnClickListener {
            if (intakeDismissed) { intakeDismissed = false; openIntake(); return@setOnClickListener }
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

    private var lastOverlayTokenFailure = 0L
    private fun addWindow(view: View, params: WindowManager.LayoutParams): Boolean = try {
        wm.addView(view, params)
        true
    } catch (_: WindowManager.BadTokenException) {
        // Xiaomi can revoke an Accessibility overlay token while a screen is being reread.
        // A later service event can attach it again; the invalid token must not kill the app.
        lastRenderKey = null
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastOverlayTokenFailure > 5_000L) {
            GuideTrace.event(context, "overlay_token_unavailable")
            lastOverlayTokenFailure = now
        }
        false
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

    private fun gestureCueBox(): GuideBox? {
        if (navigationCue != NavigationCue.HOME_SWIPE_UP) return null
        val safe = safeArea()
        val width = minOf(dp(112), safe.width)
        val height = minOf(dp(if (prefs.mascot == GuideMascot.YOUNG_HELPER) 200 else 152), safe.height)
        val left = safe.left + (safe.width - width) / 2
        return GuideBox(left, safe.bottom - height, left + width, safe.bottom)
    }

    private fun drawHomeSwipeCue(canvas: Canvas) {
        val box = gestureCueBox() ?: return
        val centerX = box.left + box.width / 2f
        val arrowTop = box.top + px(8f)
        val arrowBottom = box.bottom - px(if (prefs.mascot == GuideMascot.YOUNG_HELPER) 108f else 58f)
        val arrow = Path().apply {
            moveTo(centerX, arrowBottom); lineTo(centerX, arrowTop)
            moveTo(centerX - px(18f), arrowTop + px(20f)); lineTo(centerX, arrowTop)
            lineTo(centerX + px(18f), arrowTop + px(20f))
        }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.BLACK; paint.strokeWidth = px(12f); canvas.drawPath(arrow, paint)
        paint.color = accent; paint.strokeWidth = px(6f); canvas.drawPath(arrow, paint)
        if (prefs.showPointer) {
            if (prefs.mascot == GuideMascot.YOUNG_HELPER) {
                val size = minOf(dp(104), box.width)
                drawCompanionCue(canvas, GuidePointerPlacement(
                    GuideBox((centerX - size / 2).toInt(), box.bottom - size,
                        (centerX + size / 2).toInt(), box.bottom), 0f), tr("Vuốt lên", "Swipe up"))
            } else drawPointer(canvas, GuidePointerPlacement(
                GuideBox((centerX - px(28f)).toInt(), box.bottom - dp(56),
                    (centerX + px(28f)).toInt(), box.bottom), 0f))
        }
    }

    private fun pointedBox(): GuideBox? {
        gestureCueBox()?.let { return it }
        return region?.let {
            val bounds = wm.currentWindowMetrics.bounds
            val display = GuideBox(0, 0, bounds.width(), bounds.height())
            val target = GuideBox(floor(it.left * bounds.width()).toInt(), floor(it.top * bounds.height()).toInt(),
                ceil(it.right * bounds.width()).toInt(), ceil(it.bottom * bounds.height()).toInt())
            if (!display.contains(target)) return@let null
            // Match what is actually drawn. At a legitimate display edge both stroke centers move
            // inward, so panel placement must not reserve an imaginary off-screen rectangle.
            val ringBounds = GuideRingGeometry.visibleBands(target, display, density).blackCenter
            if (prefs.showPointer) ringBounds.union(GuideRingGeometry.pointerPlacement(
                target, display, dp(pointerSizeDp()), dp(pointerOverlapDp())).bounds)
            else ringBounds
        }
    }

    /** Keep Help and its card clear of visible final-action buttons, even before the
     * planner chooses one as the current ring target. Only local geometry is retained. */
    fun updateScreenAvoidance(screen: ScreenState) {
        val bounds = wm.currentWindowMetrics.bounds
        val labels = setOf("send", "gui", "next", "tiep", "done", "xong", "share", "chia se", "post", "dang")
        val next = screen.nodes.asSequence().filter { it.clickable && !it.password && it.region != null &&
            it.labels.any { label -> folded(label) in labels } }.mapNotNull { node ->
            node.region?.let { region -> GuideBox(
                floor(region.left * bounds.width()).toInt(), floor(region.top * bounds.height()).toInt(),
                ceil(region.right * bounds.width()).toInt(), ceil(region.bottom * bounds.height()).toInt()) }
        }.filter { it.area > 0 }.distinct().toList()
        if (next == criticalActionBoxes) return
        criticalActionBoxes = next
        if (bubbleAttached) {
            showBubble()
            if (cardAttached) savedState?.let { layoutCard(it) }
        }
    }

    fun showVoiceNotice(text: String) { render(GuideViewState(GuideStatus.ASKING,
        if (prefs.language == "vi") prefs.respectfulLanguage.message(text) else text), false) }
    fun beginIntake() { intakeDismissed = false }
    fun dismissIntakeToHelp() {
        intakeDismissed = true; userCollapsed = false; autoCollapsed = false
        clearCard(); clearRing(); lastRenderKey = null
        if (savedState?.status == GuideStatus.SAFETY_PAUSED) {
            intakeDismissed = false; savedState?.let { render(it, savedCanOpen) }
        } else showBubble()
    }
    /** In-flight work is informative, not a recoverable error. Only Stop remains available. */
    fun showWorking(text: String) { render(GuideViewState(GuideStatus.PLANNING, text), false) }
    fun showDraftReview(text: String, purpose: SpeechPurpose) = render(GuideViewState(GuideStatus.CONFIRMING_INPUT,
        if (prefs.language == "vi") prefs.respectfulLanguage.reviewLead(purpose == SpeechPurpose.MESSAGE_CONTENT)
        else if (purpose == SpeechPurpose.MESSAGE_CONTENT) "I heard what you would like to write:"
        else "I heard you say:", speechPurpose = purpose, draftText = text), false)
    fun showCopyConsent(text: String) = render(GuideViewState(GuideStatus.CONFIRMING_COPY,
        narration("Sao chép tạm nội dung này? Bác vẫn tự bấm Dán và Gửi trong ứng dụng.",
            "Copy this message temporarily? You will still tap Paste and Send in the app."), draftText = text), false)

    fun showBubble() {
        if (suppressOverlay()) { suspendForCapture(); return }
        if (!DeviceVisibility.allowed(context)) return
        val label = when {
            autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED -> tr("Gọi người thân", "Call family")
            userCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED -> tr("Tạm dừng", "Paused")
            intakeDismissed -> if (prefs.language == "vi") prefs.respectfulLanguage.helpLabel else "Help"
            autoCollapsed -> tr("Nói", "Speak")
            userCollapsed -> tr("Tiếp tục", "Continue")
            else -> if (prefs.language == "vi") prefs.respectfulLanguage.helpLabel else "Help"
        }
        val visibleLabel = if (prefs.characterOnlyBubble) "" else label
        if (bubble.text.toString() != visibleLabel) bubble.text = visibleLabel
        bubble.compoundDrawablePadding = if (prefs.characterOnlyBubble) 0 else dp(8)
        bubble.setPadding(dp(14), dp(10), dp(if (prefs.characterOnlyBubble) 14 else 18), dp(10))
        val showIcon = prefs.characterOnlyBubble || !(autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED)
        val iconKey = if (!showIcon) "none" else prefs.mascot.key
        if (bubbleIconKey != iconKey) {
            val icon = if (showIcon) (if (prefs.mascot == GuideMascot.YOUNG_HELPER)
                bubbleCompanion else context.getDrawable(vn.guidinghand.R.drawable.guidinghand_icon)?.mutate())?.apply {
                setBounds(0, 0, dp(40), dp(40))
            } else null
            bubble.setCompoundDrawablesRelative(icon, null, null, null)
            bubbleIconKey = iconKey
        }
        val enabled = true
        if (bubble.isEnabled != enabled) bubble.isEnabled = enabled
        val alpha = if (enabled) 1f else .88f
        if (bubble.alpha != alpha) bubble.alpha = alpha
        val description = when {
            autoCollapsed && savedState?.status == GuideStatus.SAFETY_PAUSED -> tr("Đã tạm dừng. Bấm để mở trình quay số cho người thân.", "Paused. Tap to open the family contact in the dialer.")
            autoCollapsed -> tr("Không đủ chỗ hiện thẻ mà không che nút. Bấm để nói hoặc yêu cầu nhắc lại.", "The guide is compact to keep the target clear. Tap to speak or ask to repeat.")
            userCollapsed -> tr("Mở lại hướng dẫn", "Show the guide")
            else -> tr("Bấm để nói việc cần giúp", "Tap to speak about what you need")
        }
        if (bubble.contentDescription != description) bubble.contentDescription = description
        bubble.measure(View.MeasureSpec.makeMeasureSpec(safeArea().width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val placement = GuidePanelLayout.place(safeArea(), bubble.measuredWidth.coerceAtLeast(dp(72)),
            bubble.measuredHeight.coerceAtLeast(dp(72)), dp(72), pointedBox(), dp(8),
            draggedBubble ?: bubbleAnchor.preferred(bubbleBounds()), extraExclusions = criticalActionBoxes)
        if (placement == null) {
            // Never leave the old Help position covering a newly selected target.
            removeBubble()
            return
        }
        val box = placement.bounds
        bubbleAnchor.remember(box)
        val p = params(box.width, box.height, false).apply { x = box.left; y = box.top }
        if (!bubbleAttached) {
            if (!addWindow(bubble, p)) return
            bubbleAttached = true; GuideTrace.event(context, "bubble_attached")
        }
        else {
            val current = bubble.layoutParams as WindowManager.LayoutParams
            if (current.x != p.x || current.y != p.y || current.width != p.width || current.height != p.height) wm.updateViewLayout(bubble, p)
        }
        if (prefs.mascot == GuideMascot.YOUNG_HELPER) bubbleCompanion.show(when (savedState?.status) {
            GuideStatus.READING, GuideStatus.PLANNING, GuideStatus.VERIFYING -> CompanionMood.THINKING
            GuideStatus.WAITING_TAP, GuideStatus.WAITING_NAVIGATION -> CompanionMood.GUIDING
            GuideStatus.CLARIFYING, GuideStatus.ASKING, GuideStatus.CONFIRMING_INPUT, GuideStatus.CONFIRMING_COPY -> CompanionMood.QUESTION
            GuideStatus.DONE -> CompanionMood.DONE
            GuideStatus.SAFETY_PAUSED, GuideStatus.UNAVAILABLE, GuideStatus.UNSUPPORTED -> CompanionMood.PAUSED
            else -> CompanionMood.READY
        }, prefs.reduceMotion)
    }

    /** A safety pause stays latched, but Help must return after the elder leaves a sensitive screen. */
    fun restorePausedIfHidden(state: GuideViewState) {
        if (state.status == GuideStatus.SAFETY_PAUSED && !bubbleAttached && !suppressOverlay())
            render(state, false)
    }

    fun render(state: GuideViewState, canOpenLauncher: Boolean) {
        // IDLE is a privacy boundary. Clear before computing identity so repeated IDLE events
        // stay idempotent after the one intentional transcript-removal reflow.
        if (state.status == GuideStatus.IDLE) clearHeardSpeech()
        if (suppressOverlay()) {
            savedState = state; savedCanOpen = canOpenLauncher
            stopBusyTick()
            hide()
            return
        }
        if (state.status == GuideStatus.SAFETY_PAUSED) intakeDismissed = false
        if (intakeDismissed) {
            savedState = state; savedCanOpen = canOpenLauncher
            stopBusyTick()
            clearCard(); clearRing(); showBubble(); return
        }
        val safeNow = safeArea()
        val renderKey = GuideRenderIdentity.key(state, canOpenLauncher, userCollapsed, safeNow,
            GuideRenderPreferences(prefs.language, prefs.reduceMotion, prefs.cardSize, prefs.visionProfile,
                prefs.showPointer, prefs.mascot, context.resources.configuration.fontScale, prefs.characterOnlyBubble), heardSpeechMemory.revision)
        val expectedRing = (state.status == GuideStatus.WAITING_TAP && state.step?.sam?.region != null) ||
            (state.status == GuideStatus.WAITING_NAVIGATION && state.navigationCue != NavigationCue.NONE)
        val mounted = bubbleAttached && (userCollapsed || autoCollapsed || state.status == GuideStatus.IDLE || cardAttached) &&
            (!expectedRing || ringAttached)
        if (renderKey == lastRenderKey && mounted && DeviceVisibility.allowed(context)) {
            savedState = state; savedCanOpen = canOpenLauncher
            return
        }
        lastRenderKey = renderKey
        val previousStatus = savedState?.status
        savedState = state; savedCanOpen = canOpenLauncher
        val busyNow = state.status in setOf(GuideStatus.READING, GuideStatus.PLANNING, GuideStatus.VERIFYING, GuideStatus.RECOVERING)
        val busyKey = if (busyNow) state.status.name + " " + state.instructionVi else null
        if (busyNow && busyKey != lastBusyKey) {
            lastBusyKey = busyKey
            busyStartElapsed = android.os.SystemClock.elapsedRealtime()
        }
        if (busyNow) {
            busyElapsed.visibility = View.VISIBLE
            updateBusyElapsed()
            uiHandler.removeCallbacks(busyTick)
            uiHandler.postDelayed(busyTick, 1000)
        } else stopBusyTick()
        // Keep the same non-touchable ring mounted while the elder is looking for the target.
        // Window churn here made it appear to blink during ordinary screen events.
        val sameVisual = (state.status == GuideStatus.WAITING_TAP && region == state.step?.sam?.region) ||
            (state.status == GuideStatus.WAITING_NAVIGATION && navigationCue == state.navigationCue)
        if (!sameVisual) clearRing()
        pointingClipped = false
        if (!DeviceVisibility.allowed(context)) { stopBusyTick(); hide(); return }
        if (state.status == GuideStatus.IDLE) {
            stopBusyTick()
            userCollapsed = false; autoCollapsed = false; draggedCard = null; lastRejectedFrame = null; clearCard(); showBubble(); return
        }
        if (state.status == GuideStatus.SAFETY_PAUSED && previousStatus != GuideStatus.SAFETY_PAUSED) userCollapsed = false
        if (state.status == GuideStatus.WAITING_NAVIGATION) navigationCue = state.navigationCue
        // Help remains the visible anchor while the card is open. The card is placed beside
        // this handle and follows it, so they always read as one movable assistant.
        showBubble()
        instruction.text = state.instructionVi
        compactInstruction.text = state.instructionVi
        val companionReview = state.status in setOf(GuideStatus.CONFIRMING_INPUT, GuideStatus.CONFIRMING_COPY)
        val speaker = if (!companionReview) null else if (prefs.mascot == GuideMascot.YOUNG_HELPER)
            cardCompanion.apply { setBounds(0, 0, dp(52), dp(52)); show(CompanionMood.QUESTION, prefs.reduceMotion) }
        else context.getDrawable(vn.guidinghand.R.drawable.guidinghand_icon)?.mutate()?.apply {
            setBounds(0, 0, dp(48), dp(48))
        }
        instruction.setCompoundDrawablesRelative(speaker, null, null, null)
        instruction.compoundDrawablePadding = if (speaker == null) 0 else dp(10)
        instruction.gravity = if (speaker == null) Gravity.START else Gravity.CENTER_VERTICAL
        instruction.accessibilityLiveRegion = when (state.status) {
            GuideStatus.SAFETY_PAUSED, GuideStatus.UNAVAILABLE, GuideStatus.ASKING ->
                View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            else -> View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val hasDraft = !state.draftText.isNullOrBlank()
        if (state.draftText != pagedDraft) {
            pagedDraft = state.draftText
            draftPageIndex = 0
        }
        draftPages = TranscriptPagination.pages(state.draftText,
            TranscriptPagination.pageCharacters(context.resources.configuration.fontScale))
        draftPageIndex = draftPageIndex.coerceIn(0, (draftPages.size - 1).coerceAtLeast(0))
        draftValue.text = draftPages.getOrNull(draftPageIndex)?.let { "“$it”" }.orEmpty()
        draftContainer.visibility = if (hasDraft) View.VISIBLE else View.GONE
        draftRow.visibility = if (hasDraft) View.VISIBLE else View.GONE
        val pagedReview = state.status == GuideStatus.CONFIRMING_INPUT && draftPages.size > 1
        val lastDraftPage = draftPageIndex >= draftPages.lastIndex
        draftPager.visibility = if (pagedReview) View.VISIBLE else View.GONE
        if (pagedReview) {
            draftPagerBack.text = tr("Quay lại", "Back")
            draftPagerBack.setTextColor(feedbackText(false)); draftPagerBack.background = feedbackBackground(false)
            draftPagerBack.visibility = if (draftPageIndex > 0) View.VISIBLE else View.INVISIBLE
            draftPagerNext.text = tr("Tiếp", "Next")
            draftPagerNext.setTextColor(feedbackText(true)); draftPagerNext.background = feedbackBackground(true)
            draftPagerNext.visibility = if (!lastDraftPage) View.VISIBLE else View.INVISIBLE
            draftPagerIndicator.text = "${draftPageIndex + 1} / ${draftPages.size}"
            draftPagerBack.contentDescription = tr("Xem phần trước của câu", "Read the previous part")
            draftPagerNext.contentDescription = tr("Xem phần tiếp theo của câu", "Read the next part")
        }
        heardSpeech.visibility = if (heardSpeech.text.isNotBlank() && ConfirmedSpeechPresentation.showIn(state.status))
            View.VISIBLE else View.GONE
        val compact = GuideCardMode.compact(state.status)
        collapse.text = tr("Thu gọn", "Hide")
        compactInstruction.visibility = if (compact) View.VISIBLE else View.GONE
        scroll.visibility = if (compact) View.GONE else View.VISIBLE
        collapse.visibility = if (compact) View.GONE else View.VISIBLE
        header.orientation = LinearLayout.HORIZONTAL
        if (compact) {
            brand.text = tr("Ẩn", "Hide")
            brand.textSize = GuideDesign.LABEL_SP
            brand.contentDescription = tr("Chạm để thu gọn hướng dẫn.", "Tap to hide the guide.")
            brand.setCompoundDrawablesRelative(null, null, null, null)
            brand.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
        } else {
            brand.text = "GuidingHand"; brand.textSize = GuideDesign.BODY_SP
            brand.contentDescription = tr("Chạm để thu gọn hướng dẫn", "Tap to hide the guide")
            brand.setCompoundDrawablesRelative(null, null, null, null)
            brand.layoutParams = if (context.resources.configuration.fontScale > 1.3f) LinearLayout.LayoutParams(-1, -2)
                else LinearLayout.LayoutParams(0, -2, 1f)
            header.orientation = if (context.resources.configuration.fontScale > 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        primaryActions.removeAllViews(); footer.removeAllViews()
        if (state.status != GuideStatus.SAFETY_PAUSED) {
            if (state.status in setOf(GuideStatus.CLARIFYING, GuideStatus.UNSUPPORTED)) state.options.forEach { option -> addPrimary(option) { answer(option) } }
            if (canOpenLauncher && state.status == GuideStatus.WAITING_TAP && state.step?.stage == PhotoStage.LAUNCHER)
                addPrimary(tr("Mở giúp tôi", "Open for me"), openLauncher)
        }
        // Retry must never live in the clipped/scrolling card body. Every recoverable
        // read/model failure gets the same fixed, full-width action near the elder's thumb.
        val pinnedRetry = state.status in setOf(GuideStatus.ASKING, GuideStatus.UNAVAILABLE)
        footer.orientation = if (state.status == GuideStatus.SAFETY_PAUSED || pinnedRetry ||
            state.status in setOf(GuideStatus.CONFIRMING_INPUT, GuideStatus.CONFIRMING_COPY) ||
            context.resources.configuration.fontScale > 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        fun footerAction(label: String, primary: Boolean = false, callback: () -> Unit) {
            val p = if (footer.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f)
                else LinearLayout.LayoutParams(-1, -2)
            p.setMargins(dp(2), dp(4), dp(2), 0)
            footer.addView(makeButton(label, primary, callback), p)
        }
        var stopAdded = false
        if (state.status == GuideStatus.SAFETY_PAUSED) footer.addView(makeButton(tr("Gọi người thân", "Call family"), true, callFamily),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        else if (state.status == GuideStatus.CONFIRMING_INPUT) {
            if (lastDraftPage) footerAction(tr("Đúng rồi", "Use this"), true, confirmDraft)
            val secondaryRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(makeButton(tr("Sửa câu này", "Edit this"), false, editDraft),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4); topMargin = dp(4) })
                addView(makeButton(tr("Dừng", "Stop"), false, stop),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); topMargin = dp(4) })
            }
            footer.addView(secondaryRow, LinearLayout.LayoutParams(-1, -2))
            stopAdded = true
        } else if (state.status == GuideStatus.CONFIRMING_COPY) {
            footerAction(tr("Sao chép nội dung", "Copy message"), true, copyDraft)
        }
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
                if (!compact) {
                    footerAction(tr("Nói", "Speak"), true, openIntake)
                    footer.getChildAt(footer.childCount - 1).contentDescription =
                        if (state.speechPurpose == SpeechPurpose.MESSAGE_CONTENT)
                            tr("Nói nội dung tin nhắn", "Dictate the message")
                        else tr("Nói việc cần giúp", "Speak your request")
                }
                footerAction(tr("Nghe", "Replay"), callback = repeat)
                footer.getChildAt(footer.childCount - 1).contentDescription =
                    tr("Nghe lại hướng dẫn", "Replay the instruction")
            }
        }
        if (!stopAdded) footerAction(tr("Dừng", "Stop"), callback = stop)
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
                if (!ringAttached) ringAttached = addWindow(ring, params(-1, -1, true))
                ring.invalidate()
                // The target is known only now. Re-anchor Help before placing the card.
                showBubble()
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
        if (state.status == GuideStatus.WAITING_NAVIGATION && navigationCue == NavigationCue.HOME_SWIPE_UP) {
            if (!ringAttached) ringAttached = addWindow(ring, params(-1, -1, true))
            ring.invalidate()
            // Re-anchor Help and its card around the gesture cue without touching the app below.
            showBubble()
        }
        autoCollapsed = false
        if (userCollapsed) { stopBusyTick(); clearCard(); showBubble(); recordMeasurement(null, false, state); return }
        layoutCard(state)
    }

    private fun layoutCard(state: GuideViewState) {
        val contentKey = listOf(state.status, state.instructionVi, state.draftText, state.options,
            if (state.status == GuideStatus.CONFIRMING_INPUT) draftPageIndex else -1).joinToString("\u001f")
        val contentChanged = contentKey != lastCardContentKey
        val compact = GuideCardMode.compact(state.status)
        val safe = safeArea(); val width = minOf(safe.width, dp(if (compact) 320 else 344))
        if (width <= 0) { clearCard(); return }
        scroll.layoutParams = LinearLayout.LayoutParams(-1, -2)
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val minimumHeight = header.measuredHeight + footer.measuredHeight + card.paddingTop + card.paddingBottom +
            if (compact) 0 else dp(72)
        val naturalHeight = card.measuredHeight
        // Cap screen coverage, not text size. Large text and choices scroll inside a solid card;
        // Repeat/Stop stay outside the scroll area. Accessibility minima win over the cap.
        val desiredHeight = minOf(naturalHeight, maxOf(minimumHeight, (safe.height * prefs.cardSize.fraction).toInt()))
        val anchor = bubbleBounds()
        if (anchor == null) {
            clearCard(); autoCollapsed = true; recordMeasurement(null, false, state); return
        }
        val placement = GuidePanelLayout.placeAttachedToHelp(
            safe, width, desiredHeight, minimumHeight, pointedBox(), 0, anchor,
            extraExclusions = criticalActionBoxes
        )
        if (placement == null) {
            clearCard(); autoCollapsed = true; showBubble(); recordMeasurement(null, false, state); return
        }
        val box = placement.bounds
        val scrolling = placement.scrollContent || box.height < naturalHeight
        if (scrolling) scroll.layoutParams = LinearLayout.LayoutParams(-1,
            (box.height - header.measuredHeight - footer.measuredHeight - card.paddingTop - card.paddingBottom).coerceAtLeast(dp(72)))
        // Re-measure after constraining the scroll viewport so the fixed footer cannot
        // cover the last action (the clipped "Use this" state seen in the recording).
        card.measure(View.MeasureSpec.makeMeasureSpec(box.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(box.height, View.MeasureSpec.EXACTLY))
        val p = params(box.width, box.height, false).apply { x = box.left; y = box.top }
        cardPosition = p
        if (!cardAttached) cardAttached = addWindow(card, p) else wm.updateViewLayout(card, p)
        // Review must begin with the first words. Opening at the bottom forced the elder to
        // discover a hidden scroll gesture before they could understand what was heard.
        if (contentChanged) scroll.post { scroll.scrollTo(0, 0) }
        lastCardContentKey = contentKey
        recordMeasurement(box, scrolling, state)
    }

    private fun recordMeasurement(box: GuideBox?, scrolling: Boolean, state: GuideViewState) {
        val target = pointedBox()
        val coverage = if (box != null && target != null) GuidePanelLayout.coverageRatio(box, target) else 0.0
        lastMeasurement = Measurement(box, target, coverage, box == null, scrolling, pointingClipped)
        // Geometry and enum only. No screen labels, recipient, goal, audio or images in logs.
        val value = "status=${state.status},panel=$box,target=$target,coverage=$coverage,collapsed=${box == null},scroll=$scrolling,pointingClipped=$pointingClipped,fontScale=${context.resources.configuration.fontScale}"
        if (lastLog != value) {
            Log.i("GuidingHandOverlay", value)
            GuideTrace.event(context, "panel_layout",
                code = "${state.status.name}:${if (box == null) "collapsed" else "visible"}:${if (scrolling) "scroll" else "fixed"}",
                value = (coverage * 1_000_000).toLong().coerceIn(0, 1_000_000))
            lastLog = value
        }
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
                        if (GuidePanelLayout.acceptsDrag(candidate, pointedBox(), dp(8)) &&
                            criticalActionBoxes.none { it.inflate(dp(8)).intersects(candidate) }) {
                            p.x = left; p.y = top; wm.updateViewLayout(window, p)
                            if (window === card) {
                                draggedCard = candidate
                                savedState?.let { recordMeasurement(candidate, scroll.height < content.height, it) }
                            } else if (window === bubble) {
                                draggedBubble = candidate
                                bubbleAnchor.remember(candidate)
                                // Help is the anchor. Move the card in the same frame so it never
                                // lags behind or becomes detached from the control being dragged.
                                if (cardAttached) savedState?.let { layoutCard(it) }
                            }
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
    private fun clearRing() { region = null; navigationCue = NavigationCue.NONE; if (ringAttached) { wm.removeView(ring); ringAttached = false } }
    private fun clearCard() { cardCompanion.stop(); if (cardAttached) { wm.removeView(card); cardAttached = false }; lastCardContentKey = null }
    private fun removeBubble() { bubbleCompanion.stop(); if (bubbleAttached) { wm.removeView(bubble); bubbleAttached = false; GuideTrace.event(context, "bubble_detached") } }
    fun suspendForCapture() { clearRing(); clearCard(); removeBubble() }
    fun refreshPreferences() {
        bubbleIconKey = null
        bubble.setTextColor(feedbackText(true)); bubble.background = feedbackBackground(true, 28f, 2)
        collapse.setTextColor(feedbackText(false)); collapse.background = feedbackBackground(false)
        savedState?.let { render(it, savedCanOpen) } ?: showBubble()
    }
    fun hide() { lastRenderKey = null; uiHandler.removeCallbacksAndMessages(null); suspendForCapture() }
}
