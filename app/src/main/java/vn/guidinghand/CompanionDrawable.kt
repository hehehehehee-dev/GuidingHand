package vn.guidinghand

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.sin

enum class CompanionMood { READY, LISTENING, THINKING, GUIDING, QUESTION, DONE, PAUSED }

/** A fictional helper. Finite gestures animate the illustration, never overlay geometry. */
class CompanionDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Color.parseColor("#18332A")
    private val cream = Color.parseColor("#F7F5EF")
    private val gold = Color.parseColor("#FFD35E")
    private var mood = CompanionMood.READY
    private var motion = 0f
    private var animator: ValueAnimator? = null
    private var initialized = false
    private var reduced = true

    fun show(next: CompanionMood, reduceMotion: Boolean) {
        if (initialized && mood == next && reduced == reduceMotion) return
        initialized = true; mood = next; reduced = reduceMotion
        stop()
        if (!reduceMotion && next != CompanionMood.PAUSED && ValueAnimator.areAnimatorsEnabled()) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (next == CompanionMood.DONE) 700 else 500
                addUpdateListener { motion = it.animatedValue as Float; invalidateSelf() }
                start()
            }
        }
        invalidateSelf()
    }

    fun stop() { animator?.cancel(); animator = null; motion = 0f; invalidateSelf() }

    private fun line(canvas: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE; paint.color = color; paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND; canvas.drawLine(x, y, x2, y2, paint)
    }

    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 108f, bounds.height() / 108f)
        canvas.clipRect(0f, 0f, 108f, 108f)
        val gesture = sin(motion * Math.PI).toFloat()
        paint.style = Paint.Style.FILL; paint.color = gold
        canvas.drawRoundRect(26f, 73f, 82f, 116f, 25f, 25f, paint)
        // A raised hand becomes a listening hand, a pointer, or a brief greeting.
        val handX = when (mood) { CompanionMood.LISTENING -> 79f; CompanionMood.GUIDING -> 95f; else -> 88f }
        val handY = when (mood) { CompanionMood.THINKING -> 66f; CompanionMood.PAUSED -> 82f; else -> 44f }
        line(canvas, 78f, 87f, handX + gesture * 4f, handY, cream, 9f)
        canvas.save()
        canvas.rotate(when (mood) {
            CompanionMood.QUESTION -> -9f - gesture * 3f
            CompanionMood.LISTENING -> 6f + gesture * 4f
            CompanionMood.THINKING -> gesture * 5f
            else -> -gesture * 4f
        }, 54f, 60f)
        paint.style = Paint.Style.FILL; paint.color = cream
        canvas.drawCircle(54f, 43f, 27f, paint)
        val hair = Path().apply {
            moveTo(29f, 40f); cubicTo(26f, 6f, 80f, 5f, 80f, 41f)
            quadTo(65f, 34f, 57f, 27f); quadTo(42f, 40f, 29f, 40f)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = cream
        canvas.drawPath(hair, paint)
        paint.style = Paint.Style.FILL; paint.color = ink; canvas.drawPath(hair, paint)
        if (mood == CompanionMood.DONE) {
            line(canvas, 42f, 45f, 47f, 42f, ink, 3f); line(canvas, 47f, 42f, 51f, 45f, ink, 3f)
            line(canvas, 60f, 45f, 65f, 42f, ink, 3f); line(canvas, 65f, 42f, 69f, 45f, ink, 3f)
        } else {
            paint.style = Paint.Style.FILL; paint.color = ink
            canvas.drawCircle(46f, 44f, 3f, paint); canvas.drawCircle(64f, 44f, 3f, paint)
        }
        val smile = Path().apply { moveTo(46f, 55f); quadTo(55f, if (mood == CompanionMood.PAUSED) 55f else 65f, 64f, 55f) }
        paint.style = Paint.Style.STROKE; paint.color = ink; paint.strokeWidth = 3f
        canvas.drawPath(smile, paint)
        canvas.restore()
        // Shape cues supplement the existing readable status text.
        when (mood) {
            CompanionMood.THINKING -> for (i in 0..2) {
                paint.style = Paint.Style.FILL; paint.color = gold
                canvas.drawCircle(86f + i * 7f, 16f, 2.5f, paint)
            }
            CompanionMood.DONE -> {
                line(canvas, 83f, 20f, 88f, 25f, gold, 4f)
                line(canvas, 88f, 25f, 100f, 11f, gold, 4f)
            }
            CompanionMood.LISTENING -> {
                line(canvas, 90f, 35f, 96f, 30f, gold, 3f)
                line(canvas, 92f, 45f, 101f, 45f, gold, 3f)
            }
            else -> Unit
        }
        canvas.restoreToCount(saved)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
