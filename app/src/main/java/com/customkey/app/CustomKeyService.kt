package com.customkey.app

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class CustomKeyService : InputMethodService(), KeyView.Listener {

    private enum class Page { LETTERS, SYMBOLS, EXTRA }

    private class KbPalette(
        val bg: Int,
        val style: KbStyle,
        val popupBg: Int,
        val previewBg: Int,
        val previewText: Int
    )

    // ---------------- state ----------------

    private lateinit var root: LinearLayout

    private var page = Page.LETTERS
    private var shift = false
    private var capsLock = false
    private var lastShiftTapTime = 0L

    private var enterLabelNow = "↵"

    private var previewPopup: PopupWindow? = null
    private var alternatesPopup: PopupWindow? = null

    private var toneGenerator: ToneGenerator? = null
    private var toneVolume = -1

    // Snapshot of what was built — to know when a rebuild is needed.
    private var builtVersion = -1
    private var builtHeightFactor = -1f
    private var builtGapDp = -1
    private var builtNightMode = -1
    private var lastBuiltPage = Page.LETTERS
    private var lastBuiltShift = false
    private var lastBuiltCaps = false
    private var lastBuiltEnter = ""

    private val isDarkMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    // Long-press alternatives for letters (digits on the top row, accents below).
    private val letterAlternates = mapOf(
        "q" to "1", "w" to "2", "e" to "3èéêë", "r" to "4", "t" to "5",
        "y" to "6ýÿ", "u" to "7ùúûü", "i" to "8ìíîï", "o" to "9òóôöõ", "p" to "0",
        "a" to "àáâäãå", "s" to "ß", "c" to "ç", "n" to "ñ", "z" to "źż"
    )

    private val customAlternates = mapOf(
        "." to "!?:;…—\"'()",
        "," to ";:!?-/"
    )

    // ---------------- lifecycle ----------------

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(3))
        }

        // Keep every key ABOVE the system navigation bar — works for both
        // gesture navigation and 3-button navigation. The bar itself stays
        // fully system-controlled (hide-arrow / keyboard switcher).
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(dp(3), dp(4), dp(3), bottom)
            insets
        }

        buildKeyboard()
        return root
    }

    /** Never use the ugly fullscreen/extract mode in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)

        page = Page.LETTERS
        enterLabelNow = computeEnterLabel()
        capsLock = false
        shift = false
        applyInitialCapsState(editorInfo)

        refreshIfNeeded()
        rebuildIfStateChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshIfNeeded()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hidePopups()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    // ---------------- keyboard construction ----------------

    private fun buildKeyboard() {
        if (!::root.isInitialized) return
        hidePopups()

        val palette = palette()
        root.setBackgroundColor(palette.bg)
        root.removeAllViews()

        when (page) {
            Page.LETTERS -> {
                val def = Layouts.current(this)
                def.rows.forEach { row -> root.addView(buildRow(row.keys)) }
            }
            Page.SYMBOLS -> buildSymbolsPage()
            Page.EXTRA -> buildExtraPage()
        }

        builtVersion = Prefs.layoutVersion(this)
        builtHeightFactor = Prefs.heightFactor(this)
        builtGapDp = Prefs.keyGapDp(this)
        builtNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        lastBuiltPage = page
        lastBuiltShift = shift
        lastBuiltCaps = capsLock
        lastBuiltEnter = enterLabelNow
    }

    /** Rebuild when the saved layout, sizing or system theme changed. */
    private fun refreshIfNeeded() {
        val version = Prefs.layoutVersion(this)
        val factor = Prefs.heightFactor(this)
        val gap = Prefs.keyGapDp(this)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (version != builtVersion || factor != builtHeightFactor ||
            gap != builtGapDp || night != builtNightMode
        ) {
            buildKeyboard()
        }
    }

    private fun rebuildIfStateChanged() {
        if (page != lastBuiltPage || shift != lastBuiltShift ||
            capsLock != lastBuiltCaps || enterLabelNow != lastBuiltEnter
        ) {
            buildKeyboard()
        }
    }

    private fun buildRow(keys: List<KeyDef>): LinearLayout {
        val palette = palette()
        val heightPx = dp((BASE_KEY_HEIGHT_DP * Prefs.heightFactor(this)).toInt())
        val gapPx = dp(Prefs.keyGapDp(this))
        val halfGapPx = (gapPx / 2f).toInt().coerceAtLeast(0)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { keyDef ->
            val view = KeyView(
                context = this,
                label = labelFor(keyDef),
                style = palette.style,
                weight = keyDef.weight,
                heightPx = heightPx,
                special = isSpecialKey(keyDef),
                repeatable = keyDef.type == KeyType.DELETE
            )
            view.tag = keyDef
            view.listener = this@CustomKeyService
            view.active = keyDef.type == KeyType.SHIFT && (shift || capsLock)
            if (capsLock) view.activeLabel = "⇪"
            view.contentDescription = contentDescriptionFor(keyDef)
            view.applyRowLayout(halfGapPx)
            row.addView(view)
        }
        return row
    }

    private fun isSpecialKey(keyDef: KeyDef): Boolean =
        keyDef.type != KeyType.LETTER && keyDef.type != KeyType.CUSTOM

    private fun labelFor(keyDef: KeyDef): String = when (keyDef.type) {
        KeyType.LETTER -> if (shift || capsLock) keyDef.label.uppercase() else keyDef.label
        KeyType.SHIFT -> "⇧"
        KeyType.DELETE -> "⌫"
        KeyType.ENTER -> enterLabelNow
        else -> keyDef.label
    }

    private fun contentDescriptionFor(keyDef: KeyDef): String = when (keyDef.type) {
        KeyType.LETTER -> keyDef.output.ifEmpty { keyDef.label }
        KeyType.SHIFT -> if (capsLock) "Caps lock" else "Shift"
        KeyType.DELETE -> "Backspace"
        KeyType.ENTER -> "Enter"
        KeyType.SPACE -> "Space"
        KeyType.TO_SYMBOLS -> "Symbols"
        KeyType.TO_EXTRA -> "More symbols"
        KeyType.TO_LETTERS -> "Letters"
        KeyType.CUSTOM -> keyDef.label
    }

    // ---------------- symbol pages (built-in) ----------------

    private fun buildSymbolsPage() {
        val rows = mutableListOf(
            customRow("1234567890"),
            customRow("@#₹_%&-+()"),
            RowDef(
                mutableListOf(
                    KeyDef(KeyType.TO_EXTRA, "=\\<", "", 1.4f),
                    KeyDef(KeyType.CUSTOM, "*", "*"),
                    KeyDef(KeyType.CUSTOM, "\"", "\""),
                    KeyDef(KeyType.CUSTOM, "'", "'"),
                    KeyDef(KeyType.CUSTOM, ":", ":"),
                    KeyDef(KeyType.CUSTOM, ";", ";"),
                    KeyDef(KeyType.CUSTOM, "!", "!"),
                    KeyDef(KeyType.CUSTOM, "?", "?"),
                    KeyDef(KeyType.CUSTOM, "/", "/"),
                    KeyDef(KeyType.DELETE, "Delete", "", 1.4f)
                )
            ),
            bottomRow()
        )
        rows.forEach { row -> root.addView(buildRow(row.keys)) }
    }

    private fun buildExtraPage() {
        val rows = mutableListOf(
            customRow("`~|•€£¥¢°§"),
            customRow("[]{}<>=±×÷"),
            RowDef(
                mutableListOf(
                    KeyDef(KeyType.TO_SYMBOLS, "?123", "", 1.4f),
                    KeyDef(KeyType.CUSTOM, "\\", "\\"),
                    KeyDef(KeyType.CUSTOM, ";", ";"),
                    KeyDef(KeyType.CUSTOM, ":", ":"),
                    KeyDef(KeyType.CUSTOM, "“", "“"),
                    KeyDef(KeyType.CUSTOM, "”", "”"),
                    KeyDef(KeyType.CUSTOM, "‘", "‘"),
                    KeyDef(KeyType.CUSTOM, "’", "’"),
                    KeyDef(KeyType.DELETE, "Delete", "", 1.4f)
                )
            ),
            bottomRow()
        )
        rows.forEach { row -> root.addView(buildRow(row.keys)) }
    }

    private fun customRow(chars: String): RowDef =
        RowDef(chars.map { KeyDef(KeyType.CUSTOM, it.toString(), it.toString()) }.toMutableList())

    private fun bottomRow(): RowDef = RowDef(
        mutableListOf(
            KeyDef(KeyType.TO_LETTERS, "ABC", "", 1.5f),
            KeyDef(KeyType.CUSTOM, ",", ","),
            KeyDef(KeyType.SPACE, "CustomKey", "", 4f),
            KeyDef(KeyType.CUSTOM, ".", "."),
            KeyDef(KeyType.ENTER, "↵", "", 1.5f)
        )
    )

    // ---------------- key actions ----------------

    override fun onKeyTap(view: KeyView) {
        val keyDef = view.tag as? KeyDef ?: return
        feedback()
        when (keyDef.type) {
            KeyType.LETTER -> typeLetter(keyDef)
            KeyType.CUSTOM -> {
                typeText(keyDef.output.ifEmpty { keyDef.label })
                autoShift()
                rebuildIfStateChanged()
            }
            KeyType.SHIFT -> toggleShift()
            KeyType.DELETE -> deleteAndRefresh()
            KeyType.SPACE -> {
                typeText(" ")
                autoShift()
                rebuildIfStateChanged()
            }
            KeyType.ENTER -> performEnterKey()
            KeyType.TO_SYMBOLS -> {
                page = Page.SYMBOLS
                buildKeyboard()
            }
            KeyType.TO_EXTRA -> {
                page = Page.EXTRA
                buildKeyboard()
            }
            KeyType.TO_LETTERS -> {
                page = Page.LETTERS
                buildKeyboard()
            }
        }
    }

    override fun onKeyRepeat(view: KeyView) {
        val keyDef = view.tag as? KeyDef ?: return
        // Backspace hold — fast deletes without re-evaluating caps every tick.
        if (keyDef.type == KeyType.DELETE) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    override fun onKeyLongPress(view: KeyView) {
        val keyDef = view.tag as? KeyDef ?: return
        val alternates = when (keyDef.type) {
            KeyType.LETTER -> letterAlternates[keyDef.output.ifEmpty { keyDef.label }]
            KeyType.CUSTOM -> customAlternates[keyDef.output]
            else -> null
        } ?: return
        if (alternates.isEmpty()) return
        showAlternatesPopup(view, alternates)
    }

    private fun typeLetter(keyDef: KeyDef) {
        val raw = keyDef.output.ifEmpty { keyDef.label }
        val ch = raw.firstOrNull() ?: return
        val value = if (shift || capsLock) ch.uppercaseChar().toString() else ch.toString()
        currentInputConnection?.commitText(value, 1)
        if (!capsLock) shift = false
        autoShift()
        rebuildIfStateChanged()
    }

    private fun typeText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteAndRefresh() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        autoShift()
        rebuildIfStateChanged()
    }

    private fun toggleShift() {
        val now = SystemClock.elapsedRealtime()
        when {
            capsLock -> {
                capsLock = false
                shift = false
            }
            now - lastShiftTapTime < DOUBLE_TAP_MS -> {
                // Double-tap on shift → caps lock.
                capsLock = true
                shift = true
            }
            else -> shift = !shift
        }
        lastShiftTapTime = now
        buildKeyboard()
    }

    // ---------------- enter / auto-capitalization ----------------

    private fun performEnterKey() {
        val info = currentInputEditorInfo
        val options = info?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val hasAction = action == EditorInfo.IME_ACTION_DONE ||
                action == EditorInfo.IME_ACTION_GO ||
                action == EditorInfo.IME_ACTION_SEARCH ||
                action == EditorInfo.IME_ACTION_SEND ||
                action == EditorInfo.IME_ACTION_NEXT ||
                action == EditorInfo.IME_ACTION_PREVIOUS

        if (hasAction && (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            currentInputConnection?.performEditorAction(action)
        } else {
            typeText("\n")
            autoShift()
            rebuildIfStateChanged()
        }
    }

    /** Enter key label follows the current field: ↵ / Done / Go / Search / Send / Next. */
    private fun computeEnterLabel(): String {
        val info = currentInputEditorInfo ?: return "↵"
        val options = info.imeOptions
        if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "↵"
        return when (options and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_DONE -> "Done"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            else -> "↵"
        }
    }

    /** Raise shift automatically at sentence starts, name fields, all-caps fields… */
    private fun autoShift() {
        if (capsLock) return
        val connection = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val inputType = info.inputType
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
        shift = connection.getCursorCapsMode(inputType) != 0
    }

    private fun applyInitialCapsState(info: EditorInfo?) {
        val inputType = info?.inputType ?: InputType.TYPE_NULL
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
            capsLock = true
            shift = true
        } else {
            autoShift()
        }
    }

    // ---------------- popups (preview + long-press alternatives) ----------------

    override fun onKeyTouchDown(view: KeyView) {
        val keyDef = view.tag as? KeyDef ?: return
        val showable = keyDef.type == KeyType.LETTER ||
                (keyDef.type == KeyType.CUSTOM && keyDef.label.length <= 2)
        if (showable) showPreviewPopup(view)
    }

    override fun onKeyTouchUp(view: KeyView) {
        hidePreviewPopup()
    }

    private fun showPreviewPopup(anchor: KeyView) {
        hidePreviewPopup()
        val palette = palette()
        val size = dp(56)

        val textView = TextView(this).apply {
            text = anchor.label
            gravity = Gravity.CENTER
            setTextColor(palette.previewText)
            textSize = 24f
            background = roundedDrawable(palette.previewBg, dp(10))
        }

        val popup = PopupWindow(textView, size, size, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(6).toFloat()
        }

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val screenW = resources.displayMetrics.widthPixels
        val x = (location[0] + anchor.width / 2 - size / 2)
            .coerceIn(0, (screenW - size).coerceAtLeast(0))
        var y = location[1] - size - dp(6)
        if (y < 0) y = location[1] + anchor.height + dp(6)

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        previewPopup = popup
    }

    private fun showAlternatesPopup(anchor: KeyView, alternates: String) {
        hidePopups()
        val palette = palette()
        val keyHeight = dp(44)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedDrawable(palette.popupBg, dp(12))
        }

        alternates.forEach { ch ->
            val key = KeyView(
                context = this,
                label = ch.toString(),
                style = palette.style,
                weight = 0f,
                heightPx = keyHeight
            )
            key.contentDescription = ch.toString()
            key.listener = object : KeyView.Listener {
                override fun onKeyTap(view: KeyView) {
                    feedback()
                    typeText(view.label)
                    hidePopups()
                }

                override fun onKeyLongPress(view: KeyView) {}
                override fun onKeyRepeat(view: KeyView) {}
                override fun onKeyTouchDown(view: KeyView) {}
                override fun onKeyTouchUp(view: KeyView) {}
            }
            key.layoutParams = LinearLayout.LayoutParams(dp(42), keyHeight).apply {
                marginEnd = dp(3)
            }
            row.addView(key)
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }

        val popup = PopupWindow(
            scroll,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(6).toFloat()
        }

        scroll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val screenW = resources.displayMetrics.widthPixels
        val w = scroll.measuredWidth.coerceAtMost(screenW - dp(8))
        val h = scroll.measuredHeight

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val x = (location[0] + anchor.width / 2 - w / 2)
            .coerceIn(dp(4), (screenW - w - dp(4)).coerceAtLeast(dp(4)))
        var y = location[1] - h - dp(8)
        if (y < 0) y = location[1] + anchor.height + dp(8)

        popup.width = w
        popup.height = h
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        alternatesPopup = popup
    }

    private fun hidePreviewPopup() {
        previewPopup?.dismiss()
        previewPopup = null
    }

    private fun hideAlternatesPopup() {
        alternatesPopup?.dismiss()
        alternatesPopup = null
    }

    private fun hidePopups() {
        hidePreviewPopup()
        hideAlternatesPopup()
    }

    // ---------------- sound / vibration ----------------

    private fun feedback() {
        if (Prefs.soundEnabled(this)) {
            try {
                val volume = Prefs.soundVolume(this)
                if (volume > 0) {
                    if (toneGenerator == null || toneVolume != volume) {
                        toneGenerator?.release()
                        toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, volume)
                        toneVolume = volume
                    }
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 35)
                }
            } catch (_: Exception) {
                toneGenerator = null
            }
        }
        if (Prefs.vibrationEnabled(this)) {
            try {
                val vibrator = getSystemService(Vibrator::class.java) ?: return
                val strength = Prefs.vibrationStrength(this)
                if (strength > 0) {
                    val amplitude = (strength * 255 / 100).coerceIn(1, 255)
                    vibrator.vibrate(VibrationEffect.createOneShot(20, amplitude))
                }
            } catch (_: Exception) {
            }
        }
    }

    // ---------------- theme ----------------

    private fun palette(): KbPalette {
        val dark = isDarkMode
        val density = resources.displayMetrics.density
        val scaled = resources.displayMetrics.scaledDensity
        val accentBg = if (dark) 0xFF8AB4F8.toInt() else 0xFF1A73E8.toInt()
        val accentText = if (dark) 0xFF10141C.toInt() else Color.WHITE

        val style = KbStyle(
            keyBg = if (dark) 0xFF35353B.toInt() else Color.WHITE,
            keyBgPressed = if (dark) 0xFF26262B.toInt() else 0xFFDADCE0.toInt(),
            specialBg = if (dark) 0xFF24242A.toInt() else 0xFFD2D5DA.toInt(),
            specialBgPressed = if (dark) 0xFF1C1C21.toInt() else 0xFFBFC3C9.toInt(),
            activeBg = accentBg,
            textColor = if (dark) 0xFFE8EAED.toInt() else 0xFF202124.toInt(),
            activeTextColor = accentText,
            radiusPx = 9f * density,
            textPx = 17f * scaled,
            specialTextPx = 13f * scaled
        )

        return KbPalette(
            bg = if (dark) 0xFF1B1B1F.toInt() else 0xFFE8EAED.toInt(),
            style = style,
            popupBg = if (dark) 0xFF26262B.toInt() else 0xFFF1F3F4.toInt(),
            previewBg = if (dark) 0xFFE8EAED.toInt() else 0xFF3C4043.toInt(),
            previewText = if (dark) 0xFF202124.toInt() else Color.WHITE
        )
    }

    private fun roundedDrawable(color: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BASE_KEY_HEIGHT_DP = 48
        private const val DOUBLE_TAP_MS = 350L
    }
}
