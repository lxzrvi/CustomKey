package com.customkey.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Colors + key style shared by the IME service and the editor's live preview. */
class KbPalette(
    val bg: Int,
    val style: KbStyle,
    val popupBg: Int,
    val previewBg: Int,
    val previewText: Int,
    private val opacityPercent: Int
) {
    /** Apply the global keyboard opacity to a color (text stays opaque). */
    fun withAlpha(color: Int): Int {
        val fraction = opacityPercent.coerceIn(30, 100) / 100f
        return Color.argb(
            (fraction * Color.alpha(color)).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}

object KeyboardTheme {

    /**
     * Premium neutral-grey theme (no blue tint) that follows the system
     * Light/Dark mode and applies the user's keyboard transparency setting.
     */
    fun palette(
        context: Context,
        opacityPercent: Int = Prefs.keyboardOpacity(context)
    ): KbPalette {
        val dark = Ui.isDark(context)
        val density = context.resources.displayMetrics.density
        val scaled = context.resources.displayMetrics.scaledDensity
        val opacity = opacityPercent.coerceIn(30, 100) / 100f

        fun alpha(color: Int): Int = Color.argb(
            (opacity * Color.alpha(color)).toInt(),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

        val accentBg = 0xFF0A84FF.toInt()

        val style = KbStyle(
            keyBg = alpha(if (dark) 0xFF2E2E2E.toInt() else Color.WHITE),
            keyBgPressed = alpha(if (dark) 0xFF232323.toInt() else 0xFFDCDCDC.toInt()),
            specialBg = alpha(if (dark) 0xFF242424.toInt() else 0xFFD9D9D9.toInt()),
            specialBgPressed = alpha(if (dark) 0xFF1D1D1D.toInt() else 0xFFC6C6C6.toInt()),
            activeBg = accentBg,
            textColor = if (dark) 0xFFF2F2F2.toInt() else 0xFF1C1C1E.toInt(),
            activeTextColor = Color.WHITE,
            radiusPx = 9f * density,
            textPx = 17f * scaled,
            specialTextPx = 13f * scaled
        )

        return KbPalette(
            bg = alpha(if (dark) 0xFF141414.toInt() else 0xFFE8E8E8.toInt()),
            style = style,
            popupBg = if (dark) 0xFF262626.toInt() else 0xFFF2F2F2.toInt(),
            previewBg = if (dark) 0xFFF2F2F2.toInt() else 0xFF3A3A3C.toInt(),
            previewText = if (dark) 0xFF141414.toInt() else Color.WHITE,
            opacityPercent = opacityPercent
        )
    }

    /** User-picked keyboard background image (center-crop + readability scrim + optional blur). */
    fun backgroundDrawable(context: Context): Drawable? {
        val path = Prefs.bgImagePath(context) ?: return null
        return try {
            val raw = BitmapFactory.decodeFile(path) ?: return null
            val blur = Prefs.bgBlur(context)
            val bitmap = if (blur > 0) blurBitmap(raw, blur) else raw
            KeyboardBgDrawable(bitmap, Ui.isDark(context))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Cheap, dependency-free blur: heavy downscale + bilinear upscale, repeated
     * for very strong blur. Looks like a gaussian at keyboard resolutions.
     */
    private fun blurBitmap(source: Bitmap, radius: Int): Bitmap {
        return try {
            val strength = radius.coerceIn(1, 25)
            // downscale factor grows with blur strength
            val factor = 1 + (strength / 4f)
            var w = (source.width / factor).toInt().coerceAtLeast(1)
            var h = (source.height / factor).toInt().coerceAtLeast(1)
            var small = Bitmap.createScaledBitmap(source, w, h, true)
            // second pass for strong blur
            if (strength > 12) {
                w = (w / 2f).toInt().coerceAtLeast(1)
                h = (h / 2f).toInt().coerceAtLeast(1)
                small = Bitmap.createScaledBitmap(small, w, h, true)
            }
            Bitmap.createScaledBitmap(small, source.width, source.height, true)
        } catch (_: Exception) {
            source
        }
    }
}

/** Center-crops a bitmap to any bounds and adds a light/dark readability scrim. */
class KeyboardBgDrawable(
    private val bitmap: Bitmap,
    private val dark: Boolean
) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        rect.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        canvas.drawBitmap(bitmap, null, rect, paint)

        scrimPaint.color = if (dark) {
            Color.argb(150, 0, 0, 0)
        } else {
            Color.argb(110, 255, 255, 255)
        }
        canvas.drawRect(bounds, scrimPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
