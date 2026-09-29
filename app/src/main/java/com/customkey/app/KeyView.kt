package com.customkey.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One rounded rectangular keyboard key with premium styling:
 * per-key corner radius / border (per side) / offset shadow (hard, soft or blurred),
 * gradient + image backgrounds, glow + inner shadow, full text styling
 * (custom font, size, weight, italic, letter spacing, rotation, shadow, opacity,
 * position), key rotation / scale / padding, press-zoom animation, per-key
 * pressed color, long-press hint character and optional vector icon.
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
        /**
         * Tap on a special zone of a wide spacebar: 1 = left emoji chip,
         * 2 = right trackpad chip. Default: ignored.
         */
        fun onKeyZoneTap(view: KeyView, key: KeyDef, zone: Int) {}
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

    /** Highlighted state (active shift / caps lock / editor selection). */
    var active = false
    var activeBg = 0xFF0A84FF.toInt()
    var activeTextColor = Color.WHITE
    /**
     * Selection/edit outline used by the editor: draws a crisp accent stroke
     * AROUND the key instead of recolouring it, so the user's own formatting
     * stays fully visible.
     */
    var outline = false
    var outlineColor = 0xFF0A84FF.toInt()

    private var visual: KeyVisual = key.style ?: KeyVisual()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val shadowRect = RectF()
    private val clipPath = Path()
    private var clipBitmap: Bitmap? = null
    private var clipImageId: String? = null

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
        if (clipImageId != visual.imageId) clipBitmap = null
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
        bgPaint.shader = null

        // text font: per-key imported font → global custom font → default
        var face: Typeface = Typeface.DEFAULT
        val keyFont = Assets.typefaceById(context, visual.fontId)
        if (keyFont != null) {
            face = keyFont
        } else {
            val customFace = loadCustomFont(context)
            if (customFace != null) face = customFace
        }
        val bold = visual.bold ?: false
        val italic = visual.italic ?: false
        val styleBits = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
        textPaint.typeface = if (styleBits == 0) face else Typeface.create(face, styleBits)
        textPaint.color = visual.textColor ?: labelColor
        textPaint.alpha = ((visual.textColor ?: labelColor).let { Color.alpha(it) } * alpha).toInt()
        textPaint.textSize = (visual.textSizeSp?.toFloat() ?: labelSizeSp) * scaledDensity
        textPaint.letterSpacing = (visual.letterSpacing ?: 0) / 100f
        // text opacity (independent of key opacity)
        visual.textOpacityPercent?.let {
            textPaint.alpha = (Color.alpha(textPaint.color) * it.coerceIn(0, 100) / 100f).toInt()
        }
        // text shadow
        if (visual.textShadow) {
            textPaint.setShadowLayer(
                (visual.textShadowBlurDp * density).coerceAtLeast(0.1f),
                visual.textShadowDx * density,
                visual.textShadowDy * density,
                visual.textShadowColor ?: Color.DKGRAY
            )
        } else {
            textPaint.clearShadowLayer()
        }

        // border
        val borderColor = visual.borderColor
        if (borderColor != null && (visual.borderWidthDp ?: 0) > 0) {
            borderPaint.color = borderColor
            borderPaint.alpha = (Color.alpha(borderColor) * alpha).toInt()
            borderPaint.strokeWidth = (visual.borderWidthDp ?: 1) * density
        }

        // drop shadow
        if (visual.shadow) {
            val angleRad = Math.toRadians(visual.shadowAngleDeg.toDouble())
            val dx = (visual.shadowDistanceDp * density) * cos(angleRad)
            val dy = (visual.shadowDistanceDp * density) * sin(angleRad)
            shadowPaint.color = Color.argb((120 * alpha).toInt(), 0, 0, 0)
            if (visual.shadowSoft) {
                shadowPaint.setShadowLayer(
                    visual.shadowBlurDp * density * 1.6f,
                    dx.toFloat(), dy.toFloat(),
                    Color.argb((110 * alpha).toInt(), 0, 0, 0)
                )
            } else {
                shadowPaint.clearShadowLayer()
            }
            shadowPaint.style = Paint.Style.FILL
            tagShadowDx = dx.toFloat()
            tagShadowDy = dy.toFloat()
        }

        icon?.setTint(textPaint.color)

        // soft shadow + glow need a software layer to render
        if ((visual.shadow && visual.shadowSoft) || visual.glow) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        } else {
            setLayerType(LAYER_TYPE_NONE, null)
        }
        invalidate()
    }

    private var tagShadowDx = 0f
    private var tagShadowDy = 0f
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val outlineRect = RectF()
    /** Key-body inset caused by shadows/glow (computed during draw). */
    private var inset = 0f

    fun setPressedState(isPressed: Boolean, scalePercent: Int) {
        pressed = isPressed
        val effectiveScale = visual.pressedScalePercent ?: scalePercent
        pressScaleTarget = if (isPressed) effectiveScale.coerceIn(70, 100) / 100f else 1f
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
        if (width <= 0 || height <= 0) return
        rect.set(inset, inset, width - inset, height - inset)

        val rad = (visual.cornerRadiusDp?.toFloat() ?: -1f).let {
            if (it < 0) radiusPx else it * density
        }

        // Reserve room INSIDE the view so shadows / glow are never clipped:
        // the key body is shrunk by the maximum shadow extent.
        inset = 0f
        if (visual.shadow) {
            inset = maxOf(
                inset,
                visual.shadowDistanceDp * density + visual.shadowBlurDp * density * 1.6f
            )
        }
        if (visual.glow) inset = maxOf(inset, visual.glowBlurDp * density)
        if (visual.textShadow) {
            inset = maxOf(
                inset,
                maxOf(
                    kotlin.math.abs(visual.textShadowDx * density),
                    kotlin.math.abs(visual.textShadowDy * density)
                ) + visual.textShadowBlurDp * density
            )
        }
        inset = inset.coerceIn(0f, minOf(width, height) * 0.32f)

        // press animation easing
        if (pressScale != pressScaleTarget) {
            pressScale += (pressScaleTarget - pressScale) * 0.55f
            if (kotlin.math.abs(pressScale - pressScaleTarget) < 0.004f) pressScale = pressScaleTarget
        }

        val saveCount = canvas.save()
        val cx = width / 2f
        val cy = height / 2f

        // press zoom + per-key scale
        val keyScale = (visual.scalePercent?.coerceIn(50, 150) ?: 100) / 100f
        val totalScale = pressScale * keyScale
        if (totalScale != 1f) canvas.scale(totalScale, totalScale, cx, cy)
        // per-key rotation
        visual.rotationDeg?.let { if (it != 0) canvas.rotate(it.toFloat(), cx, cy) }

        // glow (behind everything)
        if (visual.glow) {
            val glowColor = visual.glowColor ?: 0xFF0A84FF.toInt()
            glowPaint.color = Color.argb(70, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            glowPaint.setShadowLayer(
                visual.glowBlurDp * density,
                0f, 0f, glowColor
            )
            canvas.drawRoundRect(rect, rad, rad, glowPaint)
        }

        // drop shadow
        if (visual.shadow) {
            shadowRect.set(rect)
            shadowRect.offset(tagShadowDx, tagShadowDy)
            val shadowRadius = if (visual.shadowSoft) rad else rad * 0.6f
            canvas.drawRoundRect(shadowRect, shadowRadius, shadowRadius, shadowPaint)
        }

        // ---- key body ----
        val opacityAlpha = (visual.opacityPercent?.coerceIn(5, 100) ?: 100) / 100f
        val bodyColor = when {
            pressed && visual.pressedColor != null -> visual.pressedColor!!
            active -> activeBg
            else -> keyColor
        }

        if (visual.gradient && visual.gradientColor != null) {
            val angleRad = Math.toRadians(visual.gradientAngleDeg.toDouble())
            val half = max(width, height) / 2f
            val sx = cx - (cos(angleRad) * half).toFloat()
            val sy = cy - (sin(angleRad) * half).toFloat()
            val ex = cx + (cos(angleRad) * half).toFloat()
            val ey = cy + (sin(angleRad) * half).toFloat()
            bgPaint.shader = LinearGradient(
                sx, sy, ex, ey,
                withAlpha(bodyColor, opacityAlpha),
                withAlpha(visual.gradientColor!!, opacityAlpha),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, rad, rad, bgPaint)
            bgPaint.shader = null
        } else {
            bgPaint.color = bodyColor
            bgPaint.alpha = (Color.alpha(bodyColor) * opacityAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, rad, rad, bgPaint)
        }
        if (active) {
            textPaint.color = activeTextColor
            textPaint.alpha = Color.alpha(activeTextColor)
        }

        // image background (clipped to the rounded key)
        if (visual.imageId != null) {
            if (clipImageId != visual.imageId || clipBitmap == null) {
                clipBitmap = Assets.keyBitmap(
                    context, visual.imageId,
                    visual.imageScalePercent, visual.imageBlurDp
                )
                clipImageId = visual.imageId
            }
            val bmp = clipBitmap
            if (bmp != null) {
                val imgSave = canvas.save()
                clipPath.reset()
                clipPath.addRoundRect(rect, rad, rad, Path.Direction.CW)
                canvas.clipPath(clipPath)
                val zoom = visual.imageScalePercent.coerceIn(50, 300) / 100f
                val scale = max(width.toFloat() / bmp.width, height.toFloat() / bmp.height) * zoom
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                bmpPaint.alpha = (visual.imageAlphaPercent.coerceIn(0, 100) * 2.55f).toInt()
                canvas.drawBitmap(bmp, (width - dw) / 2f, (height - dh) / 2f, bmpPaint)
                bmpPaint.alpha = 255
                canvas.restoreToCount(imgSave)
            }
        }

        // inner shadow
        if (visual.innerShadow) {
            innerPaint.color = Color.argb(70, 0, 0, 0)
            innerPaint.strokeWidth = 1.5f * density
            val inset = innerPaint.strokeWidth / 2f + 0.5f
            val ir = RectF(rect).also { it.inset(inset, inset) }
            canvas.drawRoundRect(ir, (rad - inset).coerceAtLeast(0f), (rad - inset).coerceAtLeast(0f), innerPaint)
        }

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

        // ---- icon or label ----
        val textPos = visual.textPosition ?: 0
        val padH = (visual.paddingH ?: 0) * density
        val padV = (visual.paddingV ?: 0) * density
        if (icon != null) {
            val iconSize = min(width, height) * 0.46f
            icon?.setTint(textPaint.color)
            val left = (width - iconSize) / 2f
            val top = when (textPos) {
                1 -> height * 0.16f + padV
                2 -> height * 0.52f + padV
                else -> (height - iconSize) / 2f
            }
            icon?.setBounds(
                (left + padH).toInt(), top.toInt(),
                (left + iconSize - padH).toInt(), (top + iconSize).toInt()
            )
            icon?.draw(canvas)
        } else if (key.label.isNotEmpty()) {
            val textY = when (textPos) {
                1 -> height * 0.34f + textPaint.textSize * 0.3f + padV
                2 -> height * 0.84f - textPaint.textSize * 0.2f - padV
                else -> height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            }
            val textX = when (textPos) {
                3 -> 6f * density + padH + (visual.borderWidthDp?.toFloat() ?: 0f) * density
                4 -> width - 4f * density - padH
                else -> width / 2f
            }
            if (textPos == 3) textPaint.textAlign = Paint.Align.LEFT

            // per-key text rotation
            val textRot = visual.textRotationDeg ?: 0
            if (textRot != 0) {
                canvas.save()
                canvas.rotate(textRot.toFloat(), width / 2f, height / 2f)
                drawFittingText(canvas, key.label, textX, textY)
                canvas.restore()
            } else {
                drawFittingText(canvas, key.label, textX, textY)
            }
            textPaint.textAlign = Paint.Align.CENTER
        }

        // Spacebar quick chips: emoji (left) + trackpad (right).
        if (key.type == KeyType.SPACE && width >= height * 2.2f) {
            val chipAlpha = textPaint.alpha
            val iconSize = height * 0.42f
            val cy = (height - iconSize) / 2f
            val emojiIcon = context.getDrawable(R.drawable.ic_emoji)?.mutate()
            val cursorIcon = context.getDrawable(R.drawable.ic_cursor)?.mutate()
            if (emojiIcon != null && cursorIcon != null) {
                emojiIcon.setTint(textPaint.color)
                cursorIcon.setTint(textPaint.color)
                val cxLeft = height * 0.52f
                emojiIcon.setBounds(
                    (cxLeft - iconSize / 2f).toInt(), cy.toInt(),
                    (cxLeft + iconSize / 2f).toInt(), (cy + iconSize).toInt()
                )
                emojiIcon.alpha = chipAlpha
                emojiIcon.draw(canvas)
                val cxRight = width - height * 0.52f
                cursorIcon.setBounds(
                    (cxRight - iconSize / 2f).toInt(), cy.toInt(),
                    (cxRight + iconSize / 2f).toInt(), (cy + iconSize).toInt()
                )
                cursorIcon.alpha = chipAlpha
                cursorIcon.draw(canvas)
            }
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

        // editor selection outline — on top of everything, never rotated
        if (outline) {
            val density = resources.displayMetrics.density
            val strokeInset = 2f * density
            val radOut = (visual.cornerRadiusDp?.toFloat()?.times(density) ?: radiusPx) + 2f * density
            outlinePaint.strokeWidth = 2.5f * density
            outlinePaint.color = outlineColor
            outlineRect.set(
                strokeInset, strokeInset,
                width - strokeInset, height - strokeInset
            )
            canvas.drawRoundRect(outlineRect, radOut, radOut, outlinePaint)
        }

        if (pressScale != pressScaleTarget) postInvalidateOnAnimation()
    }

    private fun withAlpha(color: Int, fraction: Float): Int =
        Color.argb(
            (Color.alpha(color) * fraction).toInt().coerceIn(0, 255),
            Color.red(color), Color.green(color), Color.blue(color)
        )

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** Draws text, shrinking it when it would not fit the key width. */
    private fun drawFittingText(canvas: Canvas, text: String, x: Float, y: Float) {
        val density = resources.displayMetrics.density
        val available = (width - dp(6) - inset * 2f).coerceAtLeast(dp(4).toFloat())
        val measured = textPaint.measureText(text)
        if (measured > available && measured > 0f) {
            val keepSize = textPaint.textSize
            textPaint.textSize = keepSize * (available / measured)
            canvas.drawText(text, x, y, textPaint)
            textPaint.textSize = keepSize
        } else {
            canvas.drawText(text, x, y, textPaint)
        }
    }

    // ---------------- touch ----------------

    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false

    /**
     * Quick-access zones on a wide spacebar: 1 = emoji chip (left),
     * 2 = trackpad chip (right), 0 = normal space behaviour.
     */
    private fun spaceZone(x: Float): Int {
        if (key.type != KeyType.SPACE) return 0
        if (width < height * 2.2f) return 0
        val edge = height * 1.15f
        return when {
            x < edge -> 1
            x > width - edge -> 2
            else -> 0
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                slideMode = false
                longPressFired = false
                downX = event.x
                downY = event.y
                setPressedState(true, Prefs.pressScalePercent(context))
                listener?.onKeyTouchDown(this, key)
                if (key.repeatOnHold) {
                    postDelayed(repeatRunnable, REPEAT_DELAY)
                } else {
                    postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (slideMode || longPressFired) {
                    listener?.onKeySlide(this, key, event.rawX, event.rawY)
                } else {
                    // finger slid clearly outside the key — cancel
                    val slop = dp(14)
                    if (event.x < -slop || event.x > width + slop ||
                        event.y < -slop || event.y > height + slop
                    ) {
                        cancelPressed()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasSlide = slideMode || longPressFired
                val zone = spaceZone(event.x)
                cancelPressed()
                listener?.onKeyTouchUp(this, key)
                if (!wasSlide) {
                    if (zone != 0) listener?.onKeyZoneTap(this, key, zone)
                    else listener?.onKeyTap(this, key)
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
}
