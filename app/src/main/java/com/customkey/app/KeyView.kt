package com.customkey.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * One rounded rectangular keyboard key with premium styling:
 * per-key corner radius / border (per side) / offset shadow (hard, soft or blurred),
 * text size · weight · italic · position, opacity, press-zoom animation,
 * long-press hint character and optional vector icon.
 */
class KeyView(
    context: Context,
    private var key: KeyDef,
    private var keyHeightPx: Int
) : View(context) {

    interface Listener {
        fun onKeyTap(view: KeyView, key: KeyDef)
        fun onKeyLongPress(view: KeyView, key: KeyDef)
        fun onKeyRepeat(view: KeyView, key: KeyDef)
        fun onKeyTouchDown(view: KeyView, key: KeyDef)
        fun onKeyTouchUp(view: KeyView, key: KeyDef)
        /** Finger moved with the key pressed — raw screen coordinates. */
        fun onKeySlide(view: KeyView, key: KeyDef, rawX: Float, rawY: Float)
    }

    companion object {
        const val LONG_PRESS_MS = 420L
        const val REPEAT_DELAY = 380L
        const val REPEAT_INTERVAL = 50L

        private var cachedTypeface: Typeface? = null
        private var cachedTypefacePath: String? = null

        fun loadCustomFont(ctx: Context): Typeface? {
            val path = Prefs.customFontPath(ctx) ?: return null
            if (cachedTypefacePath == path) return cachedTypeface
            return try {
                val face = Typeface.createFromFile(path)
                cachedTypeface = face
                cachedTypefacePath = path
                face
            } catch (_: Exception) {
                null
            }
        }
    }

    var listener: Listener? = null

    /** Highlighted state (active shift / caps lock). */
    var active = false
    var activeBg = 0xFF0A84FF.toInt()
    var activeTextColor = Color.WHITE

    private var visual: KeyVisual = key.style ?: KeyVisual()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val shadowRect = RectF()

    private var keyColor = Color.DKGRAY
    private var labelColor = Color.WHITE
    private var labelSizeSp = 16f
    private var radiusPx = 0f
    private var icon: Drawable? = null
    private var showHint = false
    private var hintChar = ""

    private var pressed = false
    private var pressScale = 1f
    private var pressScaleTarget = 1f
    private var slideMode = false

    private val longPressRunnable = Runnable {
        slideMode = true
        cancelRepeat()
        performHapticLite()
        listener?.onKeyLongPress(this, key)
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onKeyRepeat(this@KeyView, key)
            postDelayed(this, REPEAT_INTERVAL)
        }
    }

    fun key(): KeyDef = key

    fun updateKey(newKey: KeyDef) {
        key = newKey
        visual = newKey.style ?: KeyVisual()
        refreshLook()
    }

    fun setKeyHeightPx(px: Int) {
        keyHeightPx = px
        requestLayout()
        invalidate()
    }

    fun setIcon(drawable: Drawable?) {
        icon = drawable?.mutate()
        invalidate()
    }

    /** Small long-press hint character (top-right corner). Empty = hidden. */
    fun setHintText(char: String) {
        hintChar = char
        showHint = char.isNotEmpty()
        invalidate()
    }

    fun hintCharacter(): String = hintChar

    // ---------------- look ----------------

    fun setColors(bg: Int, label: Int, sizeSp: Float, radiusPx: Float, hintColor: Int) {
        keyColor = bg
        labelColor = label
        labelSizeSp = sizeSp
        this.radiusPx = radiusPx
        hintPaint.color = hintColor
        refreshLook()
    }

    private fun refreshLook() {
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity

        // opacity override
        val opacity = visual.opacityPercent
        val alpha = if (opacity != null) (opacity.coerceIn(5, 100) / 100f) else 1f

        bgPaint.color = keyColor
        bgPaint.alpha = (Color.alpha(keyColor) * alpha).toInt().coerceIn(0, 255)

        // text
        var face = Typeface.DEFAULT
        val customFace = loadCustomFont(context)
        if (customFace != null) face = customFace
        val bold = visual.bold ?: false
        val italic = visual.italic ?: false
        val styleBits = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
        textPaint.typeface = if (styleBits == 0) face else Typeface.create(face, styleBits)
        textPaint.color = visual.textColor ?: labelColor
        textPaint.alpha = ((visual.textColor ?: labelColor).let { Color.alpha(it) } * alpha).toInt()
        textPaint.textSize = (visual.textSizeSp?.toFloat() ?: labelSizeSp) * scaledDensity

        // border
        val borderColor = visual.borderColor
        if (borderColor != null && (visual.borderWidthDp ?: 0) > 0) {
            borderPaint.color = borderColor
            borderPaint.alpha = (Color.alpha(borderColor) * alpha).toInt()
            borderPaint.strokeWidth = (visual.borderWidthDp ?: 1) * density
        }

        // shadow
        if (visual.shadow) {
            val angleRad = Math.toRadians(visual.shadowAngleDeg.toDouble())
            val dx = (visual.shadowDistanceDp * density) * cos(angleRad)
            val dy = (visual.shadowDistanceDp * density) * sin(angleRad)
            shadowPaint.color = Color.argb((120 * alpha).toInt(), 0, 0, 0)
            if (visual.shadowSoft) {
                shadowPaint.setShadowLayer(visual.shadowBlurDp * density * 1.6f, dx.toFloat(), dy.toFloat(), Color.argb((110 * alpha).toInt(), 0, 0, 0))
            } else {
                shadowPaint.clearShadowLayer()
            }
            shadowPaint.style = Paint.Style.FILL
            tag_shadowDx = dx.toFloat()
            tag_shadowDy = dy.toFloat()
        }

        icon?.setTint(textPaint.color)
        // Soft (blurred) shadows need a software layer to render.
        if (visual.shadow && visual.shadowSoft) setLayerType(LAYER_TYPE_SOFTWARE, null)
        else setLayerType(LAYER_TYPE_NONE, null)
        invalidate()
    }

    private var tag_shadowDx = 0f
    private var tag_shadowDy = 0f

    fun setPressedState(isPressed: Boolean, scalePercent: Int) {
        pressed = isPressed
        pressScaleTarget = if (isPressed) scalePercent.coerceIn(80, 100) / 100f else 1f
        if (!isPressed) pressScale = pressScaleTarget
        invalidate()
    }

    fun isPressedState(): Boolean = pressed

    // ---------------- layout ----------------

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val height = (keyHeightPx * key.heightFactor).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height)
    }

    // ---------------- draw ----------------

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())

        val rad = (visual.cornerRadiusDp?.toFloat() ?: -1f).let {
            if (it < 0) radiusPx else it * density
        }

        // scale animation towards target
        if (pressScale != pressScaleTarget) {
            pressScale += (pressScaleTarget - pressScale) * 0.55f
            if (kotlin.math.abs(pressScale - pressScaleTarget) < 0.004f) pressScale = pressScaleTarget
        }

        val saveCount = canvas.save()
        if (pressScale < 1f) {
            val cx = width / 2f
            val cy = height / 2f
            canvas.scale(pressScale, pressScale, cx, cy)
        }

        // shadow layer
        if (visual.shadow) {
            shadowRect.set(rect)
            shadowRect.offset(tag_shadowDx, tag_shadowDy)
            val shadowRadius = if (visual.shadowSoft) rad else rad * 0.6f
            canvas.drawRoundRect(shadowRect, shadowRadius, shadowRadius, shadowPaint)
        }

        // key body (active = highlighted shift/caps state)
        val opacityAlpha = (visual.opacityPercent?.coerceIn(5, 100) ?: 100) / 100f
        val bodyColor = if (active) activeBg else keyColor
        bgPaint.color = bodyColor
        bgPaint.alpha = (Color.alpha(bodyColor) * opacityAlpha).toInt().coerceIn(0, 255)
        if (active) {
            textPaint.color = activeTextColor
            textPaint.alpha = Color.alpha(activeTextColor)
        }
        canvas.drawRoundRect(rect, rad, rad, bgPaint)

        // border (per side)
        val borderColor = visual.borderColor
        if (borderColor != null && (visual.borderWidthDp ?: 0) > 0) {
            val sides = visual.borderSides ?: 15
            if (sides == 15) {
                canvas.drawRoundRect(rect, rad, rad, borderPaint)
            } else {
                drawPartialBorder(canvas, sides, density)
            }
        }

        // icon or label
        val textPos = visual.textPosition ?: 0
        if (icon != null) {
            val iconSize = min(width, height) * 0.46f
            val iconColor = textPaint.color
            icon?.setTint(iconColor)
            val left = (width - iconSize) / 2f
            val top = when (textPos) {
                1 -> height * 0.16f
                2 -> height * 0.52f
                else -> (height - iconSize) / 2f
            }
            icon?.setBounds(left.toInt(), top.toInt(), (left + iconSize).toInt(), (top + iconSize).toInt())
            icon?.draw(canvas)
        } else if (key.label.isNotEmpty()) {
            val textY = when (textPos) {
                1 -> height * 0.34f + textPaint.textSize * 0.3f
                2 -> height * 0.84f - textPaint.textSize * 0.2f
                else -> height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            }
            val textX = when (textPos) {
                3 -> 6f * density + (visual.borderWidthDp?.toFloat() ?: 0f) * density
                4 -> width - 4f * density
                else -> width / 2f
            }
            if (textPos == 3) textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(key.label, textX, textY, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }

        // long-press hint (small char in the top-right corner)
        if (showHint && hintChar.isNotEmpty()) {
            hintPaint.textSize = textPaint.textSize * 0.46f
            hintPaint.textAlign = Paint.Align.RIGHT
            val pad = 5f * density
            canvas.drawText(
                hintChar.substring(0, 1),
                width - pad,
                hintPaint.textSize + pad * 0.8f,
                hintPaint
            )
        }

        canvas.restoreToCount(saveCount)

        if (pressScale != pressScaleTarget) postInvalidateOnAnimation()
    }

    private val borderPath = Path()

    private fun drawPartialBorder(canvas: Canvas, sides: Int, density: Float) {
        borderPath.reset()
        val inset = borderPaint.strokeWidth / 2f
        if (sides and 1 != 0) { // top
            borderPath.moveTo(0f, inset)
            borderPath.lineTo(width.toFloat(), inset)
        }
        if (sides and 2 != 0) { // right
            borderPath.moveTo(width - inset, 0f)
            borderPath.lineTo(width - inset, height.toFloat())
        }
        if (sides and 4 != 0) { // bottom
            borderPath.moveTo(0f, height - inset)
            borderPath.lineTo(width.toFloat(), height - inset)
        }
        if (sides and 8 != 0) { // left
            borderPath.moveTo(inset, 0f)
            borderPath.lineTo(inset, height.toFloat())
        }
        canvas.drawPath(borderPath, borderPaint)
    }

    // ---------------- touch ----------------

    private var downX = 0f
    private var downY = 0f
    private var touchDownTime = 0L
    private var longPressFired = false

    fun beginSlide(rawX: Float, rawY: Float) {
        slideMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                slideMode = false
                longPressFired = false
                downX = event.x
                downY = event.y
                touchDownTime = System.currentTimeMillis()
                setPressedState(true, Prefs.pressScalePercent(context))
                listener?.onKeyTouchDown(this, key)
                if (key.repeatOnHold) {
                    postDelayed(repeatRunnable, REPEAT_DELAY)
                } else if (key.type != KeyType.SPACE) {
                    postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (slideMode || longPressFired) {
                    listener?.onKeySlide(this, key, event.rawX, event.rawY)
                } else if (kotlin.math.abs(event.x - downX) > width * 0.55f ||
                    kotlin.math.abs(event.y - downY) > height * 0.8f
                ) {
                    // finger slid off — cancel
                    cancelPressed()
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasSlide = slideMode || longPressFired
                cancelPressed()
                listener?.onKeyTouchUp(this, key)
                if (!wasSlide) {
                    listener?.onKeyTap(this, key)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPressed()
                listener?.onKeyTouchUp(this, key)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cancelPressed() {
        removeCallbacks(longPressRunnable)
        cancelRepeat()
        pressed = false
        slideMode = false
        longPressFired = true
        setPressedState(false, 100)
    }

    private fun cancelRepeat() {
        removeCallbacks(repeatRunnable)
    }

    private fun performHapticLite() {
        // The service handles the real haptic; this is only a fallback.
    }

    fun fireLongPressOnce() {
        if (!longPressFired) {
            longPressFired = true
            slideMode = true
            listener?.onKeyLongPress(this, key)
        }
    }
}
