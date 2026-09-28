package com.customkey.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Minimal iOS-style toggle switch, drawn and animated manually. */
class IosSwitch(context: Context) : View(context) {

    var onColor: Int = 0xFF34C759.toInt()
    var offColor: Int = 0xFF787880.toInt()
    var strokeColor: Int = 0xFF5A5A5E.toInt()

    var isChecked: Boolean = false
        private set

    var onCheckedChange: ((Boolean) -> Unit)? = null

    private var position = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rect = RectF()

    private val animator = ValueAnimator()

    init {
        animator.duration = 160
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            position = animation.animatedValue as Float
            invalidate()
        }
        isClickable = true
    }

    fun setChecked(value: Boolean, animate: Boolean = true) {
        if (isChecked == value) return
        isChecked = value
        if (!animate) {
            position = if (value) 1f else 0f
            invalidate()
            return
        }
        animator.setFloatValues(position, if (value) 1f else 0f)
        animator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(51), dp(31))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        rect.set(0f, 0f, w, h)
        trackPaint.color = lerpColor(offColor, onColor, position)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, trackPaint)

        val knobD = h - dp(4)
        val x = lerp(dp(2).toFloat(), w - knobD - dp(2), position)
        val cx = x + knobD / 2f

        knobPaint.color = Color.WHITE
        canvas.drawCircle(cx, h / 2f, knobD / 2f, knobPaint)

        strokePaint.color = strokeColor
        strokePaint.strokeWidth = dp(1).toFloat()
        canvas.drawCircle(cx, h / 2f, knobD / 2f, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                setChecked(!isChecked)
                onCheckedChange?.invoke(isChecked)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpColor(from: Int, to: Int, t: Float): Int = Color.argb(
        (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t).toInt(),
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

/** Minimal iOS-style slider: thin track, large white knob, accent fill. */
class IosSlider(context: Context) : View(context) {

    var minValue: Int = 0
    var maxValue: Int = 100

    var trackFillColor: Int = 0xFF0A84FF.toInt()
    var trackColor: Int = 0xFFD6D6D6.toInt()
    var knobStrokeColor: Int = 0xFFD1D1D1.toInt()

    var value: Int = 0
        private set

    var onValueChange: ((Int) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rect = RectF()
    private var active = false

    fun setValue(v: Int, notify: Boolean = true) {
        value = v.coerceIn(minValue, maxValue)
        invalidate()
        if (notify) onValueChange?.invoke(value)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(if (w > 0) w else dp(220), dp(28))
    }

    private fun knobRadius() = dp(10)

    private fun knobX(): Float {
        val pad = knobRadius() + dp(2)
        val usable = (width - 2 * pad).coerceAtLeast(1)
        val fraction =
            if (maxValue > minValue) (value - minValue).toFloat() / (maxValue - minValue) else 0f
        return pad + usable * fraction
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val pad = knobRadius() + dp(2)
        val thickness = dp(4)
        if (width <= 0) return

        trackPaint.color = trackColor
        rect.set(pad.toFloat(), cy - thickness / 2f, (width - pad).toFloat(), cy + thickness / 2f)
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, trackPaint)

        trackPaint.color = trackFillColor
        rect.set(pad.toFloat(), cy - thickness / 2f, knobX(), cy + thickness / 2f)
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, trackPaint)

        val r = knobRadius() * if (active) 1.15f else 1f
        knobPaint.color = Color.WHITE
        canvas.drawCircle(knobX(), cy, r, knobPaint)
        strokePaint.color = knobStrokeColor
        strokePaint.strokeWidth = dp(1).toFloat()
        canvas.drawCircle(knobX(), cy, r, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = true
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float) {
        val pad = knobRadius() + dp(2)
        val usable = (width - 2 * pad).coerceAtLeast(1)
        val fraction = ((x - pad) / usable).coerceIn(0f, 1f)
        val v = minValue + Math.round((maxValue - minValue) * fraction)
        if (v != value) {
            value = v
            onValueChange?.invoke(value)
        }
        invalidate()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
