package com.customkey.app

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Small programmatic-UI toolkit shared by all screens.
 * The whole app is built without XML layouts, AppCompat or any library —
 * this keeps every screen consistent and automatically dark-mode aware.
 */
object Ui {

    class Palette(val dark: Boolean) {
        val bg: Int = if (dark) Color.rgb(18, 18, 18) else Color.rgb(250, 250, 250)
        val card: Int = if (dark) Color.rgb(32, 32, 36) else Color.rgb(238, 238, 241)
        val inputBg: Int = if (dark) Color.rgb(45, 45, 50) else Color.WHITE
        val text: Int = if (dark) Color.WHITE else Color.rgb(20, 20, 20)
        val secondary: Int = if (dark) Color.rgb(168, 170, 176) else Color.rgb(98, 100, 106)
        val accent: Int = Color.rgb(26, 115, 232)
        val accentText: Int = Color.WHITE
        val good: Int = Color.rgb(52, 168, 83)
        val danger: Int = Color.rgb(220, 88, 60)
        val stroke: Int = if (dark) Color.rgb(58, 58, 64) else Color.rgb(220, 221, 225)
        val chipSystem: Int = if (dark) Color.rgb(52, 52, 58) else Color.rgb(210, 213, 218)
        val chipCustom: Int = if (dark) Color.rgb(38, 50, 74) else Color.rgb(222, 233, 251)
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

    fun heading(context: Context, p: Palette, parent: LinearLayout, title: String): TextView {
        val tv = TextView(context).apply {
            text = title
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(p.secondary)
            setPadding(0, dp(context, 26), 0, dp(context, 10))
        }
        parent.addView(tv)
        return tv
    }

    fun card(context: Context, p: Palette): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 8))
            background = rounded(context, p.card, 18)
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
                background = rounded(context, p.accent, 14)
                setTextColor(p.accentText)
            } else {
                background = rounded(context, p.card, 14, p.stroke)
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

    @Suppress("DEPRECATION")
    fun switchRow(
        context: Context,
        p: Palette,
        parent: LinearLayout,
        title: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): Switch {
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
        val switch = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(switch)
        parent.addView(row)
        return switch
    }

    fun seekRow(
        context: Context,
        p: Palette,
        parent: LinearLayout,
        title: String,
        min: Int,
        max: Int,
        value: Int,
        enabled: Boolean,
        format: (Int) -> String,
        onChange: (Int) -> Unit
    ): SeekBar {
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

        return SeekBar(context).apply {
            max = (max - min).coerceAtLeast(0)
            progress = (value - min).coerceIn(0, (max - min).coerceAtLeast(0))
            isEnabled = enabled
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progressValue: Int, fromUser: Boolean) {
                    val v = progressValue + min
                    valueView.text = format(v)
                    onChange(v)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            parent.addView(
                this,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    fun editText(context: Context, p: Palette, hint: String): EditText =
        EditText(context).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(p.text)
            setHintTextColor(p.secondary)
            background = rounded(context, p.inputBg, 12, p.stroke)
            setPadding(
                dp(context, 14),
                dp(context, 12),
                dp(context, 14),
                dp(context, 12)
            )
            isSingleLine = true
        }

    /** Dark-aware dialog shell with a rounded card body. */
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
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
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
