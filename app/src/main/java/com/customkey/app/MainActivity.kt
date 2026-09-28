package com.customkey.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.View

class MainActivity : android.app.Activity() {

    private lateinit var palette: Ui.Palette
    private lateinit var setupCard: LinearLayout
    private lateinit var setupHolder: LinearLayout
    private lateinit var testField: EditText

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshSetupCard()
            if (!isImeSelected()) pollHandler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        createScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupCard()
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.postDelayed(pollRunnable, 700)
    }

    override fun onPause() {
        pollHandler.removeCallbacks(pollRunnable)
        super.onPause()
    }

    private fun dp(value: Int): Int = Ui.dp(this, value)

    // ------------------------------------------------------------------
    // SCREEN
    // ------------------------------------------------------------------

    private fun createScreen() {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(16))
        }
        Ui.applySystemBarsPadding(scroll)
        scroll.setFillViewport(true)

        // ---- app icon ----
        val iconHolder = FrameLayout(this)
        val icon = ImageView(this).apply {
            setImageDrawable(Ui.roundedBitmap(this@MainActivity, R.drawable.ck_icon, 22))
            contentDescription = "CustomKey app icon"
        }
        iconHolder.addView(
            icon,
            FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER)
        )
        screen.addView(
            iconHolder,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); bottomMargin = dp(8) }
        )

        // ---- title ----
        screen.addView(TextView(this).apply {
            text = "CustomKey"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(palette.text)
        })
        screen.addView(TextView(this).apply {
            text = "Build your own keyboard"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(4))
        })

        // ---- setup card (hidden when everything is done) ----
        setupHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        setupCard = Ui.addCard(this, palette, setupHolder)
        screen.addView(
            setupHolder,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        // ---- test keyboard ----
        Ui.heading(this, palette, screen, "TEST KEYBOARD")
        val testCard = Ui.addCard(this, palette, screen)
        testField = Ui.editText(this, palette, "Tap here to type…")
        testField.inputType = InputType.TYPE_CLASS_TEXT
        testCard.addView(testField)

        // ---- edit keyboard ----
        val editCard = Ui.addCard(this, palette, screen)
        editCard.addView(TextView(this).apply {
            text = "Make it yours — keys, layout, colors, sounds."
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(10))
        })
        Ui.button(this, palette, editCard, "Edit Keyboard") {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        // ---- keyboard options ----
        Ui.heading(this, palette, screen, "KEYBOARD OPTIONS")
        val optionsCard = Ui.addCard(this, palette, screen)
        optionsCard.setPadding(dp(16), dp(10), dp(16), dp(6))
        Ui.switchRow(
            this, palette, optionsCard, "Space trackpad",
            Prefs.trackpadEnabled(this)
        ) { checked ->
            Prefs.setTrackpadEnabled(this, checked)
        }

        // ---- about ----
        Ui.heading(this, palette, screen, "ABOUT")
        val aboutCard = Ui.addCard(this, palette, screen)
        aboutCard.addView(TextView(this).apply {
            text = "CustomKey ${versionLabel()}\nNo ads · No internet · No tracking\n" +
                    "Your layout and settings never leave this device."
            textSize = 13f
            setTextColor(palette.secondary)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, 0, 0, dp(8))
        })
        aboutCard.addView(
            Ui.compactButton(this, palette, "View version history") {
                showVersionDialog()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        scroll.addView(
            screen,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)
    }

    // ------------------------------------------------------------------
    // SETUP STATE
    // ------------------------------------------------------------------

    private fun refreshSetupCard() {
        if (!::setupCard.isInitialized) return
        setupCard.removeAllViews()

        val enabled = isImeEnabled()
        val selected = enabled && isImeSelected()

        if (selected) {
            // Fully set up — hide the card entirely.
            setupHolder.visibility = View.GONE
            return
        }
        setupHolder.visibility = View.VISIBLE

        if (!enabled) {
            setupCard.addView(TextView(this).apply {
                text = "Set up CustomKey"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.text)
                setPadding(0, 0, 0, dp(4))
            })
            setupCard.addView(TextView(this).apply {
                text = "Step 1 of 2 — turn on CustomKey in your Android keyboard settings."
                textSize = 15f
                setTextColor(palette.secondary)
                setPadding(0, 0, 0, dp(4))
            })
            Ui.button(this, palette, setupCard, "Enable CustomKey") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        } else {
            setupCard.addView(TextView(this).apply {
                text = "Almost there!"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.text)
                setPadding(0, 0, 0, dp(4))
            })
            setupCard.addView(TextView(this).apply {
                text = "Step 2 of 2 — make CustomKey your active keyboard."
                textSize = 15f
                setTextColor(palette.secondary)
                setPadding(0, 0, 0, dp(4))
            })
            Ui.button(this, palette, setupCard, "Select CustomKey") {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
    }

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isImeSelected(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        val componentName = ComponentName(this, CustomKeyService::class.java)
        return current == componentName.flattenToString() ||
                current == componentName.flattenToShortString()
    }

    // ------------------------------------------------------------------
    // ABOUT
    // ------------------------------------------------------------------

    private fun versionLabel(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
        "${info.versionName} ($code)"
    } catch (_: Exception) {
        "1.4"
    }

    private fun showVersionDialog() {
        val dialog = Ui.CustomDialog(this, palette, "Version history")
        dialog.body.addView(TextView(this).apply {
            text = """
                1.4 — Keyboard Editor Pro: 9-tab live editor, multi-select batch edit, gradients, images, fonts, glow & inner shadow, text effects, custom key sounds, per-key vibration, optional toolbar, emoji grid controls.
                1.3 — Key editor pro: styles, shadows, drag-move, presets, trackpad, 10 premium sounds, clipboard & cursor keys.

                1.2 — Emoji page & picker, long-press symbols, key colors, background image, transparency, sticky preview, batch edit.

                1.1 — First public release. Custom layouts, sounds, vibration, symbols & extra pages.

                1.0 — Original internal build.
            """.trimIndent()
            textSize = 14f
            setTextColor(palette.text)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        dialog.show()
    }
}
