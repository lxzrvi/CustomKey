package com.customkey.app

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small programmatic-UI toolkit shared by all screens — premium neutral-grey
 * light/dark theme with minimal iOS-style buttons, toggles and sliders.
 * The whole app is built without XML layouts, AppCompat or any library.
 */
object Ui {

    class Palette(val dark: Boolean) {
        // Neutral premium greys — no blue tint.
        val bg: Int = if (dark) 0xFF141414.toInt() else 0xFFF5F5F5.toInt()
        val card: Int = if (dark) 0xFF1F1F1F.toInt() else Color.WHITE
        val inputBg: Int = if (dark) 0xFF2A2A2A.toInt() else 0xFFEFEFEF.toInt()
        val text: Int = if (dark) 0xFFF5F5F5.toInt() else 0xFF111111.toInt()
        val secondary: Int = if (dark) 0xFF9C9C9C.toInt() else 0xFF8A8A8A.toInt()
        val hairline: Int = if (dark) 0xFF333333.toInt() else 0xFFE6E6E6.toInt()
        val tinted: Int = if (dark) 0xFF2C2C2E.toInt() else 0xFFE9E9E9.toInt()
        val accent: Int = 0xFF0A84FF.toInt()
        val accentText: Int = Color.WHITE
        val good: Int = 0xFF30D158.toInt()
        val danger: Int = 0xFFFF453A.toInt()
        val chipSystem: Int = if (dark) 0xFF3A3A3C.toInt() else 0xFFDEDEDE.toInt()
        val chipCustom: Int = if (dark) 0xFF1E3A5F.toInt() else 0xFFDDEBFF.toInt()
        val switchOn: Int = 0xFF34C759.toInt()
    }

    fun palette(context: Context): Palette = Palette(isDark(context))

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun rounded(
        context: Context,
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null) {
            drawable.setStroke(dp(context, 1), strokeColor)
        }
        return drawable
    }

    /** Rounded version of any drawable resource (e.g. the app logo). */
    fun roundedBitmap(context: Context, resId: Int, radiusDp: Int): Drawable {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
        val radius = dp(context, radiusDp).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        return object : Drawable() {
            private val rect = RectF()

            override fun draw(canvas: Canvas) {
                rect.set(bounds)
                canvas.drawRoundRect(rect, radius, radius, paint)
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
    }

    /** Keeps activity content above the system navigation bar (edge-to-edge safe). */
    fun applyNavBarInsetPadding(view: View) {
        val baseBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottom + bottom)
            insets
        }
    }

    /** Keeps activity content below the status bar AND above the navigation bar. */
    fun applySystemBarsPadding(view: View) {
        val baseTop = view.paddingTop
        val baseBottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(v.paddingLeft, baseTop + bars.top, v.paddingRight, baseBottom + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    v.paddingLeft,
                    baseTop + insets.systemWindowInsetTop,
                    v.paddingRight,
                    baseBottom + insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    fun heading(context: Context, p: Palette, parent: LinearLayout, title: String): TextView {
        val tv = TextView(context).apply {
            text = title
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(p.secondary)
            setPadding(dp(context, 4), dp(context, 26), dp(context, 4), dp(context, 10))
        }
        parent.addView(tv)
        return tv
    }

    fun card(context: Context, p: Palette): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 8))
            background = rounded(context, p.card, 16)
        }

    fun addCard(context: Context, p: Palette, parent: LinearLayout): LinearLayout {
        val card = card(context, p)
        parent.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return card
    }

    /** iOS-style button: filled (accent) or tinted (grey fill). */
    fun button(
        context: Context,
        p: Palette,
        parent: LinearLayout,
        title: String,
        filled: Boolean = true,
        matchWidth: Boolean = true,
        onClick: () -> Unit
    ): Button {
        val button = Button(context).apply {
            text = title
            textSize = 16f
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            setOnClickListener { onClick() }
            if (filled) {
                background = rounded(context, p.accent, 12)
                setTextColor(p.accentText)
            } else {
                background = rounded(context, p.tinted, 12)
                setTextColor(p.text)
            }
        }
        val params = if (matchWidth) {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 50)
            )
        } else {
            LinearLayout.LayoutParams(0, dp(context, 50), 1f)
        }
        params.topMargin = dp(context, 8)
        parent.addView(button, params)
        return button
    }

    /** Small iOS-style tinted chip used in toolbars. Caller adds it to a parent. */
    fun compactButton(
        context: Context,
        p: Palette,
        title: String,
        danger: Boolean = false,
        onClick: () -> Unit
    ): TextView =
        TextView(context).apply {
            text = title
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (danger) p.danger else p.text)
            background = rounded(context, if (danger) Color.TRANSPARENT else p.tinted, 14, if (danger) p.danger else null)
            minHeight = dp(context, 36)
            minWidth = dp(context, 36)
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun switchRow(
        context: Context,
        p: Palette,
        parent: LinearLayout,
        title: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): IosSwitch {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))
        }
        val label = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(p.text)
        }
        row.addView(
            label,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val switch = IosSwitch(context).apply {
            onColor = p.switchOn
            offColor = if (p.dark) 0xFF3A3A3C.toInt() else 0xFFE9E9E9.toInt()
            strokeColor = if (p.dark) 0xFF4A4A4E.toInt() else 0xFFD8D8DC.toInt()
            setChecked(checked, animate = false)
        }
        switch.onCheckedChange = { value -> onChange(value) }
        row.addView(switch)
        parent.addView(row)
        return switch
    }

    fun seekRow(
        context: Context,
        p: Palette,
        parent: LinearLayout,
        title: String,
        minValue: Int,
        maxValue: Int,
        value: Int,
        enabled: Boolean,
        format: (Int) -> String,
        onChange: (Int) -> Unit
    ): IosSlider {
        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 10), 0, 0)
        }
        val titleView = TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(p.text)
        }
        labelRow.addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val valueView = TextView(context).apply {
            text = format(value)
            textSize = 15f
            setTextColor(p.secondary)
        }
        labelRow.addView(valueView)
        parent.addView(labelRow)

        val slider = IosSlider(context).apply {
            this.minValue = minValue
            this.maxValue = maxValue
            trackFillColor = p.accent
            trackColor = if (p.dark) 0xFF3A3A3C.toInt() else 0xFFE3E3E3.toInt()
            knobStrokeColor = if (p.dark) 0xFF4A4A4E.toInt() else 0xFFD8D8DC.toInt()
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setValue(value, notify = false)
            onValueChange = { v ->
                valueView.text = format(v)
                onChange(v)
            }
        }
        parent.addView(
            slider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return slider
    }

    fun editText(context: Context, p: Palette, hint: String): EditText =
        EditText(context).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(p.text)
            setHintTextColor(p.secondary)
            background = rounded(context, p.inputBg, 12)
            setPadding(
                dp(context, 14),
                dp(context, 12),
                dp(context, 14),
                dp(context, 12)
            )
            isSingleLine = true
        }

    /** iOS-alert-style dialog shell: rounded card, centered bold title. */
    class CustomDialog(context: Context, p: Palette, title: String) {
        val dialog: Dialog = Dialog(context)
        val body: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 16))
            background = rounded(context, p.card, 22)
        }

        init {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            body.addView(TextView(context).apply {
                text = title
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(p.text)
                setPadding(0, 0, 0, dp(context, 14))
            })
            val wrapper = FrameLayout(context).apply {
                setPadding(dp(context, 16), 0, dp(context, 16), 0)
            }
            wrapper.addView(
                body,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            dialog.setContentView(wrapper)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        fun show() {
            dialog.show()
            dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
