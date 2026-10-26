package vn.guidinghand

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.annotation.SuppressLint
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.HorizontalScrollView
import kotlin.math.min
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class FloatingChoice(val label: String, val action: () -> Unit, val primary: Boolean = false, val iconPackage: String? = null)

/** All conversation and coaching stays in trusted overlays over the current app. */
class GuideOverlay(private val context: Context, private val preferences: FamilyPreferences,
                   private val stop: () -> Unit, private val replay: () -> Unit,
                   private val dial: () -> Unit, private val open: () -> Unit,
                   private val covered: () -> Unit, private val minimized: () -> Unit = {}, private val resumed: () -> Unit = {}) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val ink = Color.rgb(35, 61, 52)
    private val amber = Color.rgb(243, 206, 139)
    private val cream = Color.rgb(255, 252, 246)
    private val ui get() = UiText(preferences.language)
    private var bubbleAttached = false
    private var panelAttached = false
    private var ringAttached = false
    private var destroyed = false
    private var deviceSuspended = false
    private fun deviceAllowed() = !deviceSuspended && DeviceVisibility.allowed(context)
    private var revision = 0L
    private var collapsed = false
    private var busy = false
    private var swipeAnimator: ValueAnimator? = null
    private var swipeProgress = 0f
    private var autoSwipeAnimation = false
    private var drawingWidth = context.resources.displayMetrics.widthPixels
    private var drawingHeight = context.resources.displayMetrics.heightPixels
    val hasSwipeCue get() = ringAttached && ring.swipe != null
    val isExpanded get() = panelAttached && !collapsed && !destroyed && preferences.bubbleVisible
    private var onLeft = false
    private fun bg(color: Int, radius: Int, border: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); border?.let { setStroke(dp(1), it) }
    }
    private fun text(size: Float, color: Int = ink) = TextView(context).apply {
        textSize = size; setTextColor(color); setTypeface(null, Typeface.BOLD); includeFontPadding = false
    }
    private val ring = object : View(context) {
        var target: RectF? = null
        var swipe: LauncherSwipe? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val location = IntArray(2)
        private val rect = RectF()
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val display = manager.currentWindowMetrics.bounds
            drawingWidth = display.width(); drawingHeight = display.height()
            place(revision)
        }
        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(location)
            swipe?.let { cue ->
                val line = cue.line
                val x1 = line.startX * drawingWidth - location[0]; val y1 = line.startY * drawingHeight - location[1]
                val x2 = line.endX * drawingWidth - location[0]; val y2 = line.endY * drawingHeight - location[1]
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.argb(95, 255, 253, 246); paint.strokeWidth = 3f * density
                canvas.drawLine(x1, y1, x2, y2, paint)
                paint.color = Color.argb(75, 35, 61, 52); paint.strokeWidth = 1.2f * density
                canvas.drawLine(x1, y1, x2, y2, paint)
                val angle = atan2(y2 - y1, x2 - x1)
                for (offset in listOf(-.65f, .65f)) {
                    val endX = x2 - cos(angle + offset) * dp(11)
                    val endY = y2 - sin(angle + offset) * dp(11)
                    paint.color = Color.argb(95, 255, 253, 246); paint.strokeWidth = 3f * density
                    canvas.drawLine(x2, y2, endX, endY, paint)
                    paint.color = Color.argb(95, 35, 61, 52); paint.strokeWidth = 1.2f * density
                    canvas.drawLine(x2, y2, endX, endY, paint)
                }
                val progress = (swipeProgress / if (autoSwipeAnimation) 1f else .76f).coerceIn(0f, 1f)
                val travel = if (autoSwipeAnimation) progress else progress * progress * (3f - 2f * progress)
                val opacity = if (swipeAnimator != null)
                    minOf((swipeProgress / .08f).coerceIn(0f, 1f), ((1f - swipeProgress) / .16f).coerceIn(0f, 1f)) else 1f
                val x = x1 + (x2 - x1) * travel; val y = y1 + (y2 - y1) * travel
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((90 * opacity).toInt(), 243, 206, 139)
                canvas.drawCircle(x, y, dp(8).toFloat(), paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.argb((190 * opacity).toInt(), 255, 253, 246); paint.strokeWidth = 3f * density
                canvas.drawCircle(x, y, dp(8).toFloat(), paint)
                paint.color = Color.argb((165 * opacity).toInt(), 63, 92, 75); paint.strokeWidth = 1.5f * density
                canvas.drawCircle(x, y, dp(8).toFloat(), paint)
                paint.strokeCap = Paint.Cap.BUTT
            }
            val target = target ?: return
            rect.set(target.left * drawingWidth - location[0], target.top * drawingHeight - location[1],
                target.right * drawingWidth - location[0], target.bottom * drawingHeight - location[1])
            rect.inset(-dp(18).toFloat(), -dp(18).toFloat())
            paint.color = Color.BLACK; paint.strokeWidth = dp(12).toFloat()
            canvas.drawRoundRect(rect, dp(18).toFloat(), dp(18).toFloat(), paint)
            rect.inset(dp(3).toFloat(), dp(3).toFloat())
            paint.color = amber; paint.strokeWidth = dp(6).toFloat()
            canvas.drawRoundRect(rect, dp(15).toFloat(), dp(15).toFloat(), paint)
        }
        override fun onDetachedFromWindow() { autoSwipeAnimation = false; stopSwipeMotion(); super.onDetachedFromWindow() }
    }
    private fun stopSwipeMotion() {
        val animator = swipeAnimator; swipeAnimator = null
        animator?.removeAllUpdateListeners(); animator?.cancel(); swipeProgress = 0f
    }
    private fun startSwipeMotion(durationMs: Long = 2100L) {
        stopSwipeMotion()
        if (!ValueAnimator.areAnimatorsEnabled()) { ring.invalidate(); return }
        swipeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs; repeatCount = if (autoSwipeAnimation) 0 else ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener {
                if (!ringAttached || ring.swipe == null || (!isExpanded && !autoSwipeAnimation) || destroyed) { stopSwipeMotion(); return@addUpdateListener }
                swipeProgress = it.animatedValue as Float; ring.postInvalidateOnAnimation()
            }
            start()
        }
    }
    private val face = ImageView(context).apply { setImageResource(R.drawable.guidinghand_icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val bubbleLabel = text(9f, Color.WHITE).apply { gravity = Gravity.CENTER }
    private val bubble = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = bg(ink, 22, Color.argb(180, 255, 255, 255)); elevation = dp(3).toFloat()
        addView(face, LinearLayout.LayoutParams(dp(32), dp(30)))
        addView(bubbleLabel, LinearLayout.LayoutParams(-1, dp(14)))
        isFocusable = true; isClickable = true; setOnClickListener { toggle() }
    }
    init { enableBubbleDrag() }
    @SuppressLint("ClickableViewAccessibility")
    private fun enableBubbleDrag() {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        bubble.setOnTouchListener { view, event ->
            val layout = view.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = layout.x; startY = layout.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (dx * dx + dy * dy > slop * slop) dragging = true
                    if (dragging) {
                        val bounds = manager.currentWindowMetrics.bounds
                        layout.x = (startX + dx.toInt()).coerceIn(0, (bounds.width() - dp(52)).coerceAtLeast(0))
                        layout.y = (startY + dy.toInt()).coerceIn(dp(32), (bounds.height() - dp(96)).coerceAtLeast(dp(32)))
                        manager.updateViewLayout(view, layout)
                        anchorPanel()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val width = manager.currentWindowMetrics.bounds.width()
                        onLeft = layout.x + dp(26) < width / 2
                        layout.x = if (onLeft) dp(6) else width - dp(58)
                        manager.updateViewLayout(view, layout)
                        anchorPanel()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    dragging = false; true
                }
                else -> false
            }
        }
    }
    private val message = text(18f).apply {
        setLineSpacing(0f, 1.15f); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; background = bg(Color.argb(236, 255, 253, 249), 24, Color.argb(210, 213, 221, 212))
        elevation = dp(4).toFloat(); setPadding(dp(12), dp(4), dp(12), dp(8))
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) anchorPanel()
        }
    }
    private fun params(width: Int, height: Int, touchable: Boolean) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; title = "GuidingHand" }
    private fun button(choice: FloatingChoice) = Button(context).apply {
        text = choice.label; textSize = 16f; isAllCaps = false; minHeight = dp(48); minimumWidth = 0
        minimumHeight = dp(48); stateListAnimator = null
        setTextColor(if (choice.primary) Color.WHITE else ink); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = bg(if (choice.primary) ink else Color.argb(210, 242, 245, 237), 24, if (choice.primary) null else Color.rgb(215, 224, 211))
        choice.iconPackage?.let { packageName ->
            try {
                val icon = context.packageManager.getApplicationIcon(packageName).mutate()
                icon.setBounds(0, 0, dp(22), dp(22)); compoundDrawablePadding = dp(6)
                setCompoundDrawables(icon, null, null, null)
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) { /* app was removed */ }
        }
        setOnClickListener { choice.action() }
    }
    private fun row(choices: List<FloatingChoice>) {
        if (choices.isEmpty()) return
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        choices.forEachIndexed { index, choice ->
            if (index > 0) row.addView(View(context), LinearLayout.LayoutParams(dp(8), 1))
            row.addView(button(choice), LinearLayout.LayoutParams(0, -2, 1f))
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
    }
    private fun chatHome(body: String, primary: List<FloatingChoice>, secondary: List<FloatingChoice>) {
        val speak = primary.firstOrNull()
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(text(17f).apply {
                text = body; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                if (speak != null) { contentDescription = "${speak.label}. $body"; setOnClickListener { speak.action() } }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (speak != null) addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_mic); setPadding(dp(13), dp(13), dp(13), dp(13))
                background = bg(ink, 24); contentDescription = ui.talk; isFocusable = true
                setOnClickListener { speak.action() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(button(FloatingChoice("×", { minimize() })).apply {
                contentDescription = ui.collapse; textSize = 23f; background = null; setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }, LinearLayout.LayoutParams(-1, -2))
        val suggestions = primary.drop(1) + secondary
        panel.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                suggestions.forEachIndexed { index, choice ->
                    addView(button(choice), LinearLayout.LayoutParams(-2, dp(48)).apply { if (index > 0) leftMargin = dp(6) })
                }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
    }
    private fun card(body: String, primary: List<FloatingChoice>, secondary: List<FloatingChoice>, home: Boolean = false) {
        panel.removeAllViews()
        if (home) chatHome(body, primary, secondary) else {
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            if (home) addView(text(13f).apply { text = "♡  GuidingHand" }, LinearLayout.LayoutParams(0, -2, 1f))
            else addView(Button(context).apply {
                text = ui.helpMenu; textSize = 14f; isAllCaps = false
                contentDescription = ui.pick("Back to What can I help with", "Quay về Cần giúp việc gì")
                minHeight = dp(40); minimumWidth = 0; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, 0, dp(6), 0); setTextColor(ink); background = bg(cream, 10)
                setOnClickListener { open() }
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(Button(context).apply {
                text = ui.pick("Close", "Thu gọn"); textSize = 14f; isAllCaps = false
                minHeight = dp(40); minimumWidth = 0; setPadding(dp(6), 0, dp(6), 0)
                setTextColor(ink); background = bg(cream, 10); setOnClickListener { minimize() }
            }, LinearLayout.LayoutParams(-2, dp(40)))
        })
        (message.parent as? LinearLayout)?.removeView(message)
        message.text = body
        panel.addView(message, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        row(primary); row(secondary)
        }
        if (!panelAttached) {
            val display = manager.currentWindowMetrics.bounds
            manager.addView(panel, params(min(dp(300), display.width() - dp(24)), -2, true).apply {
                x = if (onLeft) dp(12) else display.width() - min(dp(300), display.width() - dp(24)) - dp(12)
                y = dp(64)
            }); panelAttached = true
        }
        collapsed = false; panel.visibility = View.VISIBLE
        val layout = panel.layoutParams as WindowManager.LayoutParams
        layout.flags = layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        manager.updateViewLayout(panel, layout)
    }
    private fun place(request: Long) {
        panel.post {
            if (!panelAttached || revision != request) return@post
            anchorPanel()
        }
    }
    private fun anchorPanel() {
        if (!deviceAllowed()) { hideAll(); return }
        if (!isExpanded || !bubbleAttached || panel.height == 0) return
        val display = manager.currentWindowMetrics.bounds
        drawingWidth = display.width(); drawingHeight = display.height()
        val buddy = bubble.layoutParams as WindowManager.LayoutParams
        val target = (ring.target ?: ring.swipe?.let { cue ->
            val line = cue.line
            RectF(minOf(line.startX, line.endX) - .025f, minOf(line.startY, line.endY) - .025f,
                maxOf(line.startX, line.endX) + .025f, maxOf(line.startY, line.endY) + .025f)
        })?.let {
            AnchoredGuideLayout.Box((it.left * display.width()).toInt() - dp(24),
                (it.top * display.height()).toInt() - dp(24),
                ((it.right - it.left) * display.width()).toInt() + dp(48),
                ((it.bottom - it.top) * display.height()).toInt() + dp(48))
        }
        val placement = AnchoredGuideLayout.place(display.width(), display.height(), buddy.x, buddy.y,
            dp(52), panel.width, panel.height, dp(12), dp(6), target, dp(40))
        if (placement == null) {
            if (ring.swipe != null) { clearRing(); place(revision); return }
            if (target != null) { ring.target = null; ring.swipe = null; ring.invalidate(); covered() }
            return
        }
        buddy.x = placement.bubble.x; buddy.y = placement.bubble.y
        manager.updateViewLayout(bubble, buddy)
        val layout = panel.layoutParams as WindowManager.LayoutParams
        layout.x = placement.card.x; layout.y = placement.card.y
        manager.updateViewLayout(panel, layout)
    }
    fun showConversation(body: String, primary: List<FloatingChoice>, secondary: List<FloatingChoice> = emptyList(), home: Boolean = false) {
        if (destroyed || !deviceAllowed()) { hideAll(); return }
        busy = primary.isEmpty() && secondary.isEmpty()
        val request = ++revision; clearRing(); showBubble()
        card(body, primary.take(2), secondary.take(2), home); place(request)
    }
    fun show(step: ModelStep?, body: String, status: GuideStatus) {
        if (destroyed || !deviceAllowed()) { hideAll(); return }
        if (status == GuideStatus.IDLE) { clearGuide(); showBubble(); return }
        stopSwipeMotion()
        val display = manager.currentWindowMetrics.bounds
        drawingWidth = display.width(); drawingHeight = display.height()
        val request = ++revision; showBubble()
        busy = false
        if (!ringAttached) { manager.addView(ring, params(-1, -1, false)); ringAttached = true }
        val actions = if (status in listOf(GuideStatus.SENSITIVE, GuideStatus.UNCONFIRMED))
            listOf(FloatingChoice(ui.call, dial, true), FloatingChoice(ui.stop, stop))
        else listOfNotNull(if (status == GuideStatus.WAITING) FloatingChoice(ui.replay, replay, true) else null, FloatingChoice(ui.stop, stop))
        card(body, actions, emptyList())
        ring.swipe = null; ring.target = step?.target; ring.invalidate(); place(request)
    }
    fun showSwipeCue(direction: LauncherSwipe) {
        if (destroyed || !deviceAllowed() || !isExpanded) return
        if (ringAttached && ring.swipe == direction) return
        val display = manager.currentWindowMetrics.bounds
        drawingWidth = display.width(); drawingHeight = display.height()
        if (!ringAttached) { manager.addView(ring, params(-1, -1, false)); ringAttached = true }
        autoSwipeAnimation = false
        ring.target = null; ring.swipe = direction; startSwipeMotion(); ring.invalidate(); place(revision)
    }
    /** Keep only the non-touchable cue while the requested launcher stroke runs. */
    fun showAutoSwipe(direction: LauncherSwipe, durationMs: Long) {
        hideAll()
        if (destroyed || !deviceAllowed() || !preferences.bubbleVisible) return
        val display = manager.currentWindowMetrics.bounds
        drawingWidth = display.width(); drawingHeight = display.height()
        manager.addView(ring, params(-1, -1, false)); ringAttached = true
        autoSwipeAnimation = true; ring.swipe = direction; startSwipeMotion(durationMs); ring.invalidate()
    }
    fun showBubble() {
        if (destroyed || !preferences.bubbleVisible || !deviceAllowed()) { hideAll(); return }
        bubbleLabel.text = ui.pick("Guide", "Giúp")
        bubble.contentDescription = ui.pick("GuidingHand: tap for help, drag to move", "GuidingHand: bấm để được giúp, kéo để di chuyển")
        if (!bubbleAttached) {
            val display = manager.currentWindowMetrics.bounds
            manager.addView(bubble, params(dp(52), dp(52), true).apply {
                x = if (onLeft) dp(10) else display.width() - dp(62); y = (display.height() * .65f).toInt()
            }); bubbleAttached = true
        }
    }
    private fun minimize() {
        minimized()
        if (ring.swipe != null) clearRing()
        collapsed = true; panel.visibility = View.INVISIBLE
        if (panelAttached) {
            val layout = panel.layoutParams as WindowManager.LayoutParams
            layout.flags = layout.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; manager.updateViewLayout(panel, layout)
        }
    }
    private fun toggle() {
        if (!deviceAllowed()) { hideAll(); return }
        if (panelAttached) {
            if (!collapsed) { minimize(); return }
            if (busy) { clearGuide(); open(); return }
            collapsed = false; panel.visibility = View.VISIBLE
            val layout = panel.layoutParams as WindowManager.LayoutParams
            layout.flags = layout.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); manager.updateViewLayout(panel, layout)
            place(revision)
            resumed()
        } else open()
    }
    fun moveBubble() {
        onLeft = !onLeft
        if (bubbleAttached) {
            val layout = bubble.layoutParams as WindowManager.LayoutParams
            layout.x = if (onLeft) dp(10) else manager.currentWindowMetrics.bounds.width() - dp(62); manager.updateViewLayout(bubble, layout)
            anchorPanel()
        }
    }
    private fun clearRing() { autoSwipeAnimation = false; stopSwipeMotion(); if (ringAttached) { manager.removeView(ring); ringAttached = false }; ring.target = null; ring.swipe = null }
    fun clearGuide() { revision++; clearRing(); if (panelAttached) { manager.removeView(panel); panelAttached = false } }
    fun hideAll() { clearGuide(); if (bubbleAttached) { manager.removeView(bubble); bubbleAttached = false } }
    fun suspendForLock() { deviceSuspended = true; hideAll() }
    fun resumeAfterUnlock() { deviceSuspended = false; showBubble() }
    fun destroy() { destroyed = true; hideAll() }
    fun announceReplay() { message.announceForAccessibility(message.text) }
}
