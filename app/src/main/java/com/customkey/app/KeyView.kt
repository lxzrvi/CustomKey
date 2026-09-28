package com.customkey.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/** Visual style shared by the keys of one keyboard page. All sizes are pixels. */
class KbStyle(
    val keyBg: Int,
    val keyBgPressed: Int,
    val specialBg: Int,
    val specialBgPressed: Int,
    val activeBg: Int,
    val textColor: Int,
    val activeTextColor: Int,
    val radiusPx: Float,
    val textPx: Float,
    val specialTextPx: Float
)

/**
 * A single on-screen keyboard key, drawn manually: rounded background, label,
 * pressed state with a subtle scale animation, long-press support,
 * press-and-hold repeat (backspace) and accessibility descriptions.
 * No drawable assets or external libraries required.
 */
class KeyView(
    context: Context,
    var label: String,
    var style: KbStyle,
    var weight: Float,
    var heightPx: Int,
    var special: Boolean = false,
    var repeatable: Boolean = false
) : View(context) {

    interface Listener {
        fun onKeyTap(view: KeyView)
        fun onKeyLongPress(view: KeyView)
        fun onKeyRepeat(view: KeyView)
        fun onKeyTouchDown(view: KeyView)
        fun onKeyTouchUp(view: KeyView)
    }

    var listener: Listener? = null

    /** Highlighted state — active shift / caps lock. */
    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Label override while [active] is true (caps lock arrow). */
    var activeLabel: String? = null

    private val handler = Handler(Looper.getMainLooper())

    private var pressed = false
    private var longPressConsumed = false
    private var repeatStarted = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val rect = RectF()

    private val longPressRunnable = Runnable {
        if (pressed) {
            longPressConsumed = true
            pressed = false
            invalidate()
            listener?.onKeyLongPress(this)
        }
    }

    private val repeatKickRunnable = Runnable {
        if (pressed && repeatable) {
            repeatStarted = true
            listener?.onKeyRepeat(this)
            handler.postDelayed(repeatTickRunnable, REPEAT_INTERVAL_MS)
        }
    }

    private val repeatTickRunnable = Runnable {
        if (pressed && repeatable) {
            listener?.onKeyRepeat(this)
            handler.postDelayed(repeatTickRunnable, REPEAT_INTERVAL_MS)
        }
    }

    init {
        isClickable = true
        contentDescription = label
    }

    /** Give the key its width weight, height and gap margins inside a row. */
    fun applyRowLayout(halfGapPx: Int) {
        layoutParams = LinearLayout.LayoutParams(0, heightPx, weight).apply {
            setMargins(halfGapPx, halfGapPx, halfGapPx, halfGapPx)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Press animation — key shrinks slightly toward its center.
        val scale = if (pressed) 0.97f else 1f
        canvas.save()
        canvas.scale(scale, scale, w / 2f, h / 2f)

        rect.set(0f, 0f, w, h)
        bgPaint.color = when {
            active -> style.activeBg
            pressed -> if (special) style.specialBgPressed else style.keyBgPressed
            special -> style.specialBg
            else -> style.keyBg
        }
        canvas.drawRoundRect(rect, style.radiusPx, style.radiusPx, bgPaint)

        val text = if (active) activeLabel ?: label else label
        textPaint.color = if (active) style.activeTextColor else style.textColor
        textPaint.textSize = if (special) style.specialTextPx else style.textPx

        // Shrink long labels (Search, CustomKey…) so they always fit.
        val textWidth = textPaint.measureText(text)
        val maxWidth = w * 0.86f
        val textScale = if (textWidth > maxWidth && textWidth > 0f) maxWidth / textWidth else 1f

        val textY = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.save()
        canvas.scale(textScale, 1f, w / 2f, h / 2f)
        canvas.drawText(text, w / 2f, textY, textPaint)
        canvas.restore()

        canvas.restore()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                longPressConsumed = false
                repeatStarted = false
                invalidate()
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                if (repeatable) handler.postDelayed(repeatKickRunnable, REPEAT_DELAY_MS)
                listener?.onKeyTouchDown(this)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isInside(event.x, event.y)) cancelTouch()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasPressed = pressed
                val consumed = longPressConsumed
                val inside = isInside(event.x, event.y)
                cancelCallbacks()
                pressed = false
                invalidate()
                listener?.onKeyTouchUp(this)
                if (wasPressed && !consumed && !repeatStarted && inside) {
                    listener?.onKeyTap(this)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelTouch()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cancelTouch() {
        cancelCallbacks()
        if (pressed) {
            pressed = false
            invalidate()
        }
        listener?.onKeyTouchUp(this)
    }

    private fun cancelCallbacks() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatKickRunnable)
        handler.removeCallbacks(repeatTickRunnable)
    }

    private fun isInside(x: Float, y: Float): Boolean =
        x >= -SLOP && y >= -SLOP && x <= width + SLOP && y <= height + SLOP

    companion object {
        private const val LONG_PRESS_MS = 420L
        private const val REPEAT_DELAY_MS = 380L
        private const val REPEAT_INTERVAL_MS = 50L
        private const val SLOP = 8f
    }
}
