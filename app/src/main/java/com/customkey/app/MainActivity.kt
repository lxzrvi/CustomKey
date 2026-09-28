package com.customkey.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var palette: Ui.Palette
    private lateinit var setupCard: LinearLayout

    private var soundSlider: IosSlider? = null
    private var vibrationSlider: IosSlider? = null
    private var styleRow: LinearLayout? = null

    private var sounds: KeySounds? = null

    private val handler = Handler(Looper.getMainLooper())
    private var pollingSetup = false

    // Keeps the setup card fresh while the system IME picker / settings are open.
    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshSetupCard()
            if (pollingSetup) handler.postDelayed(this, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        createScreen()
    }

    override fun onResume() {
        super.onResume()
        pollingSetup = true
        handler.post(pollRunnable)
    }

    override fun onPause() {
        pollingSetup = false
        handler.removeCallbacks(pollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        sounds?.release()
        sounds = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = Ui.dp(this, value)

    // ------------------------------------------------------------------
    // SCREEN
    // ------------------------------------------------------------------

    private fun createScreen() {

        val scroll = ScrollView(this).apply {
            setBackgroundColor(palette.bg)
            isFillViewport = true
        }
        Ui.applyNavBarInsetPadding(scroll)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(30))
        }

        // ---------- header ----------

        root.addView(ImageView(this).apply {
            setImageDrawable(Ui.roundedBitmap(this@MainActivity, R.drawable.ck_icon, 19))
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(78), dp(78)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        root.addView(TextView(this).apply {
            text = "CustomKey"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "Your keyboard, your way."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(20))
        })

        // ---------- setup (auto-detects state) ----------

        setupCard = Ui.addCard(this, palette, root)
        refreshSetupCard()

        // ---------- test ----------

        Ui.heading(this, palette, root, "TEST KEYBOARD")

        val testInput = EditText(this).apply {
            hint = "Tap here and start typing…"
            textSize = 16f
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            background = Ui.rounded(this@MainActivity, palette.inputBg, 14)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isSingleLine = false
            minLines = 3
            gravity = Gravity.TOP
        }
        root.addView(
            testInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ---------- keyboard ----------

        Ui.heading(this, palette, root, "KEYBOARD")

        Ui.button(this, palette, root, "Edit Keyboard") {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        // ---------- settings (wired + persistent) ----------

        Ui.heading(this, palette, root, "SETTINGS")

        val settingsCard = Ui.addCard(this, palette, root)

        Ui.switchRow(this, palette, settingsCard, "Key sound", Prefs.soundEnabled(this)) { enabled ->
            Prefs.setSoundEnabled(this, enabled)
            soundSlider?.isEnabled = enabled
            soundSlider?.alpha = if (enabled) 1f else 0.4f
            styleRow?.alpha = if (enabled) 1f else 0.4f
        }

        // 5 premium sound styles with instant preview
        styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(2))
        }
        settingsCard.addView(styleRow)

        val styleChips = ArrayList<TextView>()
        val restyleChips: () -> Unit = {
            val selected = Prefs.soundStyle(this)
            styleChips.forEachIndexed { i, chip ->
                chip.setTextColor(if (i == selected) Color.WHITE else palette.text)
                chip.background = Ui.rounded(
                    this,
                    if (i == selected) palette.accent else palette.tinted,
                    16
                )
            }
        }
        KeySounds.NAMES.forEachIndexed { i, name ->
            val chip = TextView(this).apply {
                text = name
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                isClickable = true
                setOnClickListener {
                    Prefs.setSoundStyle(this@MainActivity, i)
                    restyleChips()
                    if (sounds == null) sounds = KeySounds(this@MainActivity)
                    sounds?.play(i, Prefs.soundVolume(this@MainActivity))
                }
            }
            styleChips.add(chip)
            styleRow?.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
                ).apply { marginEnd = dp(8) }
            )
        }
        restyleChips()

        soundSlider = Ui.seekRow(
            this, palette, settingsCard,
            "Sound volume", 10, 100,
            Prefs.soundVolume(this), Prefs.soundEnabled(this),
            { "$it%" }
        ) { volume ->
            Prefs.setSoundVolume(this, volume)
        }

        Ui.switchRow(this, palette, settingsCard, "Vibration", Prefs.vibrationEnabled(this)) { enabled ->
            Prefs.setVibrationEnabled(this, enabled)
            vibrationSlider?.isEnabled = enabled
            vibrationSlider?.alpha = if (enabled) 1f else 0.4f
        }

        vibrationSlider = Ui.seekRow(
            this, palette, settingsCard,
            "Vibration strength", 10, 100,
            Prefs.vibrationStrength(this), Prefs.vibrationEnabled(this),
            { "$it%" }
        ) { strength ->
            Prefs.setVibrationStrength(this, strength)
        }

        // ---------- about ----------

        Ui.heading(this, palette, root, "ABOUT")

        Ui.button(this, palette, root, "Version ${versionLabel()}", filled = false) {
            showVersionDialog()
        }
        Ui.button(this, palette, root, "Privacy", filled = false) {
            showPrivacyDialog()
        }
        Ui.button(this, palette, root, "Share app", filled = false) {
            shareApp()
        }
        Ui.button(this, palette, root, "Feedback", filled = false) {
            sendFeedback()
        }

        // ---------- footer ----------

        root.addView(TextView(this).apply {
            text = "CustomKey ${versionLabel()}\nMade by lxzrvi"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(palette.secondary)
            setPadding(0, dp(36), 0, dp(10))
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    // ------------------------------------------------------------------
    // SETUP STATE — only the pending step is shown
    // ------------------------------------------------------------------

    private fun refreshSetupCard() {
        if (!::setupCard.isInitialized) return
        setupCard.removeAllViews()

        val enabled = isImeEnabled()
        val selected = enabled && isImeSelected()

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
        } else if (!selected) {
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
        } else {
            setupCard.addView(TextView(this).apply {
                text = "✓  CustomKey is active"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.good)
                setPadding(0, 0, 0, dp(4))
            })
            setupCard.addView(TextView(this).apply {
                text = "Your keyboard is set up and ready to use."
                textSize = 15f
                setTextColor(palette.secondary)
                setPadding(0, 0, 0, dp(6))
            })
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
        "1.2"
    }

    private fun showVersionDialog() {
        val dialog = Ui.CustomDialog(this, palette, "Version")
        dialog.body.addView(TextView(this).apply {
            text = "CustomKey ${versionLabel()}\n\n" +
                    "A fully offline, open-source custom keyboard.\n" +
                    "No ads • no tracking • no internet"
            textSize = 15f
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        })
        Ui.button(this, palette, dialog.body, "Close") {
            dialog.dialog.dismiss()
        }
        dialog.show()
    }

    private fun showPrivacyDialog() {
        val dialog = Ui.CustomDialog(this, palette, "Privacy")
        dialog.body.addView(TextView(this).apply {
            text = "CustomKey is completely private.\n\n" +
                    "• No data collection, analytics or tracking\n" +
                    "• No internet access — nothing you type ever leaves your device\n" +
                    "• Your layout and settings are stored locally, on your device only\n\n" +
                    "Everything is open source:\ngithub.com/lxzrvi/CustomKey"
            textSize = 15f
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        })
        Ui.button(this, palette, dialog.body, "Close") {
            dialog.dialog.dismiss()
        }
        dialog.show()
    }

    private fun shareApp() {
        val text = "CustomKey — your keyboard, your way. ⌨️\n" +
                "A fast, private and fully customizable Android keyboard.\n" +
                "https://github.com/lxzrvi/CustomKey"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share CustomKey"))
    }

    private fun sendFeedback() {
        val mail = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse(
                "mailto:thaparavi382@gmail.com" +
                        "?subject=" + Uri.encode("CustomKey Feedback")
            )
        )
        try {
            startActivity(mail)
        } catch (_: Exception) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
