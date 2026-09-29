package com.customkey.app

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

class CustomKeyService : InputMethodService(), KeyView.Listener {

    private enum class Page { LETTERS, SYMBOLS, EXTRA, EMOJI, CURSOR }

    // ---------------- state ----------------

    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var overlayLayer: FrameLayout

    private var page = Page.LETTERS
    private var shift = false
    private var capsLock = false
    private var lastShiftTapTime = 0L
    private var emojiCategory = 0

    private var enterLabelNow = "↵"

    private var previewPopup: PopupWindow? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hidePreviewRunnable = Runnable { hidePreviewPopup() }

    // Alternates (in-window slide-to-select popup)
    private var altChips: List<Pair<TextView, String>> = emptyList()
    private var altSelected: String? = null
    private var altSourceKey: KeyDef? = null

    // Trackpad (long-press space)
    private var trackpadActive = false
    private var trackpadSelecting = false
    private var trackpadLastX = -1f
    private var trackpadLastY = -1f
    private var trackpadAccumX = 0f
    private var trackpadAccumY = 0f

    private var sounds: KeySounds? = null
    private var lastNavBottom = 0

    // Snapshot of what was built — to know when a rebuild is needed.
    private var builtVersion = -1
    private var builtNightMode = -1
    private var lastBuiltPage = Page.LETTERS
    private var lastBuiltShift = false
    private var lastBuiltCaps = false
    private var lastBuiltEnter = ""
    private var lastBuiltSelMode = false

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
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        overlayLayer = FrameLayout(this).apply { visibility = View.GONE }
        root = FrameLayout(this).apply {
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                overlayLayer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setupWindowInsets()
        buildKeyboard()
        return root
    }

    /**
     * Robust navigation-bar handling — the keyboard always stays ABOVE the
     * system bar (gesture bar / keyboard switcher), on gesture AND 3-button nav:
     *
     * 1. Lay the IME window out edge-to-edge (behind the nav bar) so the system
     *    reports REAL insets instead of a consumed/empty value.
     * 2. Listen for insets on the window's decor view.
     * 3. Re-apply in onWindowShown() + a display-size heuristic fallback for
     *    ROMs that report 0 insets while the window still overlaps the bar.
     * 4. The editor's "Extra bottom padding" slider covers any stubborn ROM.
     */
    private fun setupWindowInsets() {
        val imeDialog = window ?: return
        val win = imeDialog.window ?: return
        val decor = win.decorView ?: return

        if (Build.VERSION.SDK_INT >= 30) {
            win.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        decor.setOnApplyWindowInsetsListener { _, insets ->
            applyBottomInset(insetsBottom(insets))
            insets
        }
    }

    private fun insetsBottom(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }

    /**
     * Heuristic fallback: some ROMs report 0 nav-bar insets to IME windows even
     * though the window is laid out behind the gesture bar. If our decor view
     * reaches the real bottom of the display, the difference IS the nav bar —
     * pad by the system's navigation_bar_height.
     */
    private fun navBarHeuristic(): Int {
        if (Build.VERSION.SDK_INT < 30) return 0
        if (!::root.isInitialized) return 0
        return try {
            val wm = getSystemService(android.view.WindowManager::class.java) ?: return 0
            val realBounds = wm.maximumWindowMetrics.bounds
            val loc = IntArray(2)
            root.getLocationOnScreen(loc)
            val decorBottom = loc[1] + root.height
            val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId <= 0) return 0
            val navH = resources.getDimensionPixelSize(resId)
            if (navH <= 0) return 0
            if (decorBottom >= realBounds.bottom - navH / 2) navH else 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Visible-frame fallback: the gap between the window's visible frame and
     * the real display bottom IS the nav bar — works even on ROMs that report
     * 0 insets to IME windows. Cross-checked against the system's
     * navigation_bar_height so a bogus frame can never over-pad.
     */
    private fun navBarFromVisibleFrame(): Int {
        if (!::root.isInitialized) return 0
        return try {
            val visible = Rect()
            root.getWindowVisibleDisplayFrame(visible)
            val realH = if (Build.VERSION.SDK_INT >= 30) {
                getSystemService(android.view.WindowManager::class.java)
                    ?.maximumWindowMetrics?.bounds?.height() ?: return 0
            } else {
                val size = android.graphics.Point()
                @Suppress("DEPRECATION")
                val wm = getSystemService(android.view.WindowManager::class.java) ?: return 0
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(size)
                size.y
            }
            val diff = realH - visible.bottom
            if (diff <= 0) return 0
            val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val sysNav = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            if (sysNav > 0) {
                if (diff >= sysNav / 2) sysNav else 0
            } else {
                diff.coerceIn(0, dp(48))
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun applyBottomInset(reported: Int) {
        if (!::root.isInitialized) return
        // belt & braces: best of insets, decor-bounds heuristic, visible frame
        val navBottom = maxOf(reported, navBarHeuristic(), navBarFromVisibleFrame())
        lastNavBottom = navBottom
        applyContentPadding(navBottom)
    }

    /** Horizontal/vertical padding + bottom inset, all user-configurable. */
    private fun applyContentPadding(navBottom: Int) {
        val padH = dp(Prefs.contentPaddingHDp(this).coerceIn(0, 24))
        val padV = dp(Prefs.contentPaddingVDp(this).coerceIn(0, 24))
        content.setPadding(
            padH, padV, padH,
            navBottom + padV + dp(Prefs.extraBottomDp(this).coerceIn(0, 20))
        )
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // Fallback for ROMs where the decor listener never fires or fires late.
        try {
            val decor = window?.window?.decorView ?: return
            fun reapply() {
                val insets = decor.rootWindowInsets
                applyBottomInset(if (insets != null) insetsBottom(insets) else 0)
            }
            reapply()
            decor.post { reapply() }
            decor.postDelayed({ reapply() }, 350)
        } catch (_: Exception) {
        }
    }

    /** Never use the ugly fullscreen/extract mode in landscape. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)

        // A brand-new field ends the trackpad; a restart of the SAME field
        // (cursor/selection updates) must keep it open.
        if (!restarting) exitTrackpad()

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
        exitTrackpad()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        sounds?.release()
        sounds = null
        super.onDestroy()
    }

    // ---------------- keyboard construction ----------------

    private fun buildKeyboard() {
        if (!::root.isInitialized) return
        hidePopups()
        // NOTE: the trackpad overlay is independent of the key rows and must
        // survive rebuilds (page switches, selection toggles, layout edits).

        val palette = KeyboardTheme.palette(this)
        applyKeyboardBackground(palette)

        applyContentPadding(lastNavBottom)
        content.removeAllViews()

        // Optional customizable toolbar above the keys
        if (Prefs.toolbarEnabled(this)) {
            content.addView(buildToolbar())
        }

        when (page) {
            Page.LETTERS -> {
                val def = Layouts.current(this)
                if (Prefs.numberRowEnabled(this)) {
                    addStyledRow(content, buildRow(customRow("1234567890").keys))
                }
                def.rows.forEach { row -> addStyledRow(content, buildRow(row.keys)) }
            }
            Page.SYMBOLS -> buildSymbolsPage()
            Page.EXTRA -> buildExtraPage()
            Page.EMOJI -> buildEmojiPage()
            Page.CURSOR -> buildCursorPage()
        }

        builtVersion = Prefs.layoutVersion(this)
        builtNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        lastBuiltPage = page
        lastBuiltShift = shift
        lastBuiltCaps = capsLock
        lastBuiltEnter = enterLabelNow
        lastBuiltSelMode = trackpadSelecting
    }

    private fun refreshIfNeeded() {
        val version = Prefs.layoutVersion(this)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (version != builtVersion || night != builtNightMode) {
            buildKeyboard()
        }
    }

    private fun rebuildIfStateChanged() {
        if (page != lastBuiltPage || shift != lastBuiltShift ||
            capsLock != lastBuiltCaps || enterLabelNow != lastBuiltEnter ||
            trackpadSelecting != lastBuiltSelMode
        ) {
            buildKeyboard()
        }
    }

    /** Adds a key row with the configured width % and alignment. */
    private fun addStyledRow(parent: LinearLayout, row: LinearLayout) {
        val pct = Prefs.keyboardWidthPercent(this).coerceIn(60, 100)
        val gravity = when (Prefs.rowAlignment(this)) {
            0 -> Gravity.LEFT
            2 -> Gravity.RIGHT
            else -> Gravity.CENTER_HORIZONTAL
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = gravity
        parent.addView(row, lp)
        if (pct < 100) {
            row.post {
                val targetW = (resources.displayMetrics.widthPixels * pct / 100f).toInt()
                if (row.width != targetW) {
                    row.layoutParams.width = targetW
                    row.requestLayout()
                }
            }
        }
    }

    /** Optional toolbar strip above the keys — fully customizable in the editor. */
    private fun buildToolbar(): View {
        val height = dp(Prefs.toolbarHeightDp(this).coerceIn(24, 64))
        val iconColor = Prefs.toolbarIconColor(this)
        val sizeSp = Prefs.toolbarIconSizeSp(this).coerceIn(9, 22).toFloat()
        val palette = KeyboardTheme.palette(this)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val bg = Prefs.toolbarBgColor(this@CustomKeyService)
            if (bg != 0) setBackgroundColor(bg)
        }

        Prefs.toolbarButtons(this).filter { it.on }.forEach { btn ->
            val action = toolbarAction(btn.id)
            row.addView(
                TextView(this).apply {
                    text = action.first
                    textSize = sizeSp
                    typeface = Ui.fontMedium(this@CustomKeyService)
                    setTextColor(if (iconColor != 0) iconColor else palette.style.textColor)
                    gravity = Gravity.CENTER
                    setPadding(dp(10), 0, dp(10), 0)
                    contentDescription = action.second
                    setOnClickListener { action.third() }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, height)
            )
        }
        return row
    }

    /** id → (label, description, action) */
    private fun toolbarAction(id: String): Triple<String, String, () -> Unit> = when (id) {
        "cl" -> Triple("←", "Cursor left") { tapKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        "cr" -> Triple("→", "Cursor right") { tapKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        "cu" -> Triple("↑", "Cursor up") { tapKey(KeyEvent.KEYCODE_DPAD_UP) }
        "cd" -> Triple("↓", "Cursor down") { tapKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        "copy" -> Triple("Copy", "Copy") { currentInputConnection?.performContextMenuAction(android.R.id.copy) }
        "cut" -> Triple("Cut", "Cut") { currentInputConnection?.performContextMenuAction(android.R.id.cut) }
        "paste" -> Triple("Paste", "Paste") { currentInputConnection?.performContextMenuAction(android.R.id.paste) }
        "all" -> Triple("All", "Select all") { currentInputConnection?.performContextMenuAction(android.R.id.selectAll) }
        "emoji" -> Triple("☺", "Emoji") { page = Page.EMOJI; buildKeyboard() }
        "next" -> Triple("Next", "Next field") { performNextField() }
        else -> Triple("▾", "Hide keyboard") { requestHideSelf(0) }
    }

    /** Solid / gradient / bordered keyboard surface (separate from key colors). */
    private fun applyKeyboardBackground(palette: KbPalette) {
        val bgDrawable = KeyboardTheme.backgroundDrawable(this)
        if (bgDrawable != null) {
            root.background = bgDrawable
            return
        }
        val color = Prefs.kbBgColor(this)
        val gradient = Prefs.kbGradient(this)
        val c2 = Prefs.kbGradientColor2(this)
        val border = Prefs.kbBorderColor(this)
        val bw = Prefs.kbBorderWidthDp(this)
        val radius = Prefs.kbRadiusDp(this)
        if (color == 0 && !gradient && border == 0 && radius == 0) {
            root.setBackgroundColor(palette.bg)
            return
        }
        val base = if (color != 0) color else palette.bg
        root.background = android.graphics.drawable.GradientDrawable().apply {
            if (gradient) {
                orientation = gradientOrientation(Prefs.kbGradientAngle(this@CustomKeyService))
                colors = intArrayOf(palette.withAlpha(base), palette.withAlpha(c2))
            } else {
                setColor(palette.withAlpha(base))
            }
            if (bw > 0 && border != 0) setStroke(dp(bw), border)
            if (radius > 0) cornerRadius = dp(radius).toFloat()
        }
    }

    private fun gradientOrientation(angle: Int): android.graphics.drawable.GradientDrawable.Orientation {
        val octant = ((angle % 360 + 360) % 360) / 45
        return when (octant) {
            0 -> android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            1 -> android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            2 -> android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            3 -> android.graphics.drawable.GradientDrawable.Orientation.TR_BL
            4 -> android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT
            5 -> android.graphics.drawable.GradientDrawable.Orientation.BR_TL
            6 -> android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP
            else -> android.graphics.drawable.GradientDrawable.Orientation.BL_TR
        }
    }

    private fun buildRow(keys: List<KeyDef>): LinearLayout {
        val palette = KeyboardTheme.palette(this)
        val style = palette.style
        val heightPx = dp(Prefs.keyHeightDp(this).coerceIn(40, 62))
        val gapPx = dp(Prefs.keyGapDp(this).coerceIn(0, 8))
        val rowGapPx = dp(Prefs.rowGapDp(this).coerceIn(0, 14))
        val halfGapPx = (gapPx / 2f).toInt().coerceAtLeast(0)
        val halfRowGapPx = (rowGapPx / 2f).toInt().coerceAtLeast(0)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val showHints = Prefs.showLongPressHints(this)

        keys.forEach { keyDef ->
            val view = KeyView(this, keyDef, heightPx)
            view.listener = this

            val special = isSpecialKey(keyDef)
            val bg = keyDef.color ?: if (special) style.specialBg else style.keyBg
            view.setColors(
                bg = palette.withAlpha(bg),
                label = style.textColor,
                sizeSp = style.textPx,
                radiusPx = style.radiusPx,
                hintColor = dimColor(style.textColor)
            )
            view.active = keyDef.type == KeyType.SHIFT && (shift || capsLock)
            view.activeBg = style.activeBg
            view.activeTextColor = style.activeTextColor

            when (keyDef.type) {
                KeyType.EMOJI -> view.setIcon(appIcon(R.drawable.ic_emoji))
                KeyType.TO_CURSOR -> view.setIcon(appIcon(R.drawable.ic_cursor))
                else -> Unit
            }

            view.setHintText(if (showHints) hintTextFor(keyDef) else "")
            view.contentDescription = contentDescriptionFor(keyDef)

            val lp = LinearLayout.LayoutParams(0, heightPx, keyDef.weight)
            lp.setMargins(halfGapPx, halfRowGapPx, halfGapPx, halfRowGapPx)
            row.addView(view, lp)
        }
        return row
    }

    private fun appIcon(resId: Int) = getDrawable(resId)?.mutate()

    private fun dimColor(color: Int): Int =
        Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))

    private fun isSpecialKey(keyDef: KeyDef): Boolean =
        keyDef.type != KeyType.LETTER && keyDef.type != KeyType.CUSTOM

    /** Characters offered on long-press (custom alternates win over the built-in map). */
    private fun alternatesFor(keyDef: KeyDef): String = when {
        keyDef.alternates.isNotEmpty() -> keyDef.alternates
        keyDef.type == KeyType.LETTER ->
            letterAlternates[keyDef.output.ifEmpty { keyDef.label }] ?: ""
        keyDef.type == KeyType.CUSTOM ->
            customAlternates[keyDef.output] ?: ""
        else -> ""
    }

    private fun hintTextFor(keyDef: KeyDef): String = alternatesFor(keyDef).take(1)

    private fun contentDescriptionFor(keyDef: KeyDef): String = when (keyDef.type) {
        KeyType.LETTER -> keyDef.output.ifEmpty { keyDef.label }
        KeyType.SHIFT -> if (capsLock) "Caps lock" else "Shift"
        KeyType.DELETE -> "Backspace"
        KeyType.ENTER -> "Enter"
        KeyType.SPACE -> "Space"
        KeyType.TO_SYMBOLS -> "Symbols"
        KeyType.TO_EXTRA -> "More symbols"
        KeyType.TO_LETTERS -> "Letters"
        KeyType.EMOJI -> "Emoji"
        KeyType.TO_CURSOR -> "Cursor and clipboard"
        KeyType.CLIP_COPY -> "Copy"
        KeyType.CLIP_CUT -> "Cut"
        KeyType.CLIP_PASTE -> "Paste"
        KeyType.CLIP_ALL -> "Select all"
        KeyType.SEL_TOGGLE -> "Toggle selection mode"
        KeyType.SEL_START_LEFT -> "Extend selection start left"
        KeyType.SEL_END_RIGHT -> "Extend selection end right"
        KeyType.ARROW_LEFT -> "Move cursor left"
        KeyType.ARROW_RIGHT -> "Move cursor right"
        KeyType.ARROW_UP -> "Move cursor up"
        KeyType.ARROW_DOWN -> "Move cursor down"
        KeyType.NEXT_FIELD -> "Next input field"
        KeyType.CUSTOM -> keyDef.label
    }

    // ---------------- built-in pages ----------------

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
                    KeyDef(KeyType.DELETE, "Delete", "", 1.4f, repeatOnHold = true)
                )
            ),
            bottomRow()
        )
        rows.forEach { row -> addStyledRow(content, buildRow(row.keys)) }
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
                    KeyDef(KeyType.DELETE, "Delete", "", 1.4f, repeatOnHold = true)
                )
            ),
            bottomRow()
        )
        rows.forEach { row -> addStyledRow(content, buildRow(row.keys)) }
    }

    private fun customRow(chars: String): RowDef =
        RowDef(chars.map { KeyDef(KeyType.CUSTOM, it.toString(), it.toString()) }.toMutableList())

    private fun bottomRow(): RowDef = RowDef(
        mutableListOf(
            KeyDef(KeyType.TO_LETTERS, "ABC", "", 1.3f),
            KeyDef(KeyType.TO_CURSOR, "Cursor", "", 1f),
            KeyDef(KeyType.EMOJI, "Emoji", "", 1f),
            KeyDef(KeyType.CUSTOM, ",", ","),
            KeyDef(KeyType.SPACE, "CustomKey", "", 3.2f),
            KeyDef(KeyType.CUSTOM, ".", "."),
            KeyDef(KeyType.ENTER, "↵", "", 1.3f)
        )
    )

    // ---------------- cursor / clipboard page ----------------

    private fun buildCursorPage() {
        val rows = listOf(
            RowDef(
                mutableListOf(
                    KeyDef(KeyType.CLIP_COPY, "Copy", "", 1f),
                    KeyDef(KeyType.CLIP_CUT, "Cut", "", 1f),
                    KeyDef(KeyType.CLIP_PASTE, "Paste", "", 1f),
                    KeyDef(KeyType.CLIP_ALL, "All", "", 1f)
                )
            ),
            RowDef(
                mutableListOf(
                    KeyDef(
                        KeyType.SEL_TOGGLE,
                        if (trackpadSelecting) "Select: ON" else "Select: off",
                        "", 1.3f
                    ),
                    KeyDef(KeyType.SEL_START_LEFT, "⇤", "", 1f, repeatOnHold = true),
                    KeyDef(KeyType.SEL_END_RIGHT, "⇥", "", 1f, repeatOnHold = true),
                    KeyDef(KeyType.NEXT_FIELD, "Next ⏭", "", 1.3f)
                )
            ),
            RowDef(
                mutableListOf(
                    KeyDef(KeyType.ARROW_LEFT, "◀", "", 1f, repeatOnHold = true),
                    KeyDef(KeyType.ARROW_DOWN, "▼", "", 1f, repeatOnHold = true),
                    KeyDef(KeyType.ARROW_UP, "▲", "", 1f, repeatOnHold = true),
                    KeyDef(KeyType.ARROW_RIGHT, "▶", "", 1f, repeatOnHold = true)
                )
            ),
            RowDef(
                mutableListOf(
                    KeyDef(KeyType.TO_LETTERS, "ABC", "", 1.3f),
                    KeyDef(KeyType.EMOJI, "Emoji", "", 1f),
                    KeyDef(KeyType.SPACE, "CustomKey", "", 3.2f),
                    KeyDef(KeyType.DELETE, "Delete", "", 1.3f, repeatOnHold = true)
                )
            )
        )
        rows.forEach { row -> addStyledRow(content, buildRow(row.keys)) }
    }

    // ---------------- emoji page ----------------

    private fun buildEmojiPage() {
        val palette = KeyboardTheme.palette(this)

        val recents = Prefs.recentEmojis(this)
        val categories = ArrayList<List<String>>()
        val labels = ArrayList<String>()
        if (recents.isNotEmpty()) {
            categories.add(recents)
            labels.add("🕘")
        }
        Emojis.CATEGORIES.forEach { (label, list) ->
            categories.add(list)
            labels.add(label)
        }
        if (emojiCategory >= categories.size) emojiCategory = 0

        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }
        labels.forEachIndexed { index, label ->
            tabs.addView(TextView(this).apply {
                text = label
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = if (index == emojiCategory) {
                    Ui.rounded(this@CustomKeyService, palette.style.specialBg, 14)
                } else {
                    null
                }
                setOnClickListener {
                    emojiCategory = index
                    buildKeyboard()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
            ).apply { marginEnd = dp(4) })
        }
        tabsScroll.addView(tabs)
        content.addView(
            tabsScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val gridScroll = ScrollView(this)
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val items = categories[emojiCategory]
        val perRow = Prefs.emojiColumns(this).coerceIn(4, 12)
        val rowH = dp(Prefs.emojiRowHeightDp(this).coerceIn(30, 64))
        val cellSpacing = dp(Prefs.emojiSpacingDp(this).coerceIn(0, 12))
        val emojiSp = Prefs.emojiSizeSp(this).coerceIn(12, 40).toFloat()
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until perRow) {
                val index = i + j
                val cell = TextView(this).apply {
                    textSize = emojiSp
                    gravity = Gravity.CENTER
                    if (index < items.size) {
                        val emoji = items[index]
                        text = emoji
                        contentDescription = "Emoji $emoji"
                        setOnClickListener {
                            feedback()
                            Prefs.pushRecentEmoji(this@CustomKeyService, emoji)
                            typeText(emoji)
                        }
                    }
                }
                val clp = LinearLayout.LayoutParams(0, rowH, 1f)
                clp.setMargins(cellSpacing / 2, cellSpacing / 2, cellSpacing / 2, cellSpacing / 2)
                row.addView(cell, clp)
            }
            val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH)
            grid.addView(row, rlp)
            i += perRow
        }
        gridScroll.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            gridScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (rowH * 3.6f).toInt().coerceAtLeast(dp(120))
            )
        )

        addStyledRow(
            content,
            buildRow(
                listOf(
                    KeyDef(KeyType.TO_LETTERS, "ABC", "", 1.4f),
                    KeyDef(KeyType.SPACE, "CustomKey", "", 5f),
                    KeyDef(KeyType.DELETE, "Delete", "", 1.4f, repeatOnHold = true)
                )
            )
        )
    }

    // ---------------- key actions ----------------

    override fun onKeyTap(view: KeyView, key: KeyDef) {
        feedback(key)
        when (key.type) {
            KeyType.LETTER -> typeLetter(key)
            KeyType.CUSTOM -> {
                typeText(key.output.ifEmpty { key.label })
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
            KeyType.EMOJI -> {
                page = Page.EMOJI
                buildKeyboard()
            }
            KeyType.TO_CURSOR -> {
                page = Page.CURSOR
                buildKeyboard()
            }
            KeyType.CLIP_COPY -> currentInputConnection?.performContextMenuAction(android.R.id.copy)
            KeyType.CLIP_CUT -> currentInputConnection?.performContextMenuAction(android.R.id.cut)
            KeyType.CLIP_PASTE -> currentInputConnection?.performContextMenuAction(android.R.id.paste)
            KeyType.CLIP_ALL -> currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            KeyType.SEL_TOGGLE -> {
                trackpadSelecting = !trackpadSelecting
                buildKeyboard()
            }
            KeyType.SEL_START_LEFT -> moveSelectionBoundary(startSide = true, delta = -1)
            KeyType.SEL_END_RIGHT -> moveSelectionBoundary(startSide = false, delta = 1)
            KeyType.ARROW_LEFT -> tapKey(KeyEvent.KEYCODE_DPAD_LEFT, trackpadSelecting)
            KeyType.ARROW_RIGHT -> tapKey(KeyEvent.KEYCODE_DPAD_RIGHT, trackpadSelecting)
            KeyType.ARROW_UP -> tapKey(KeyEvent.KEYCODE_DPAD_UP, trackpadSelecting)
            KeyType.ARROW_DOWN -> tapKey(KeyEvent.KEYCODE_DPAD_DOWN, trackpadSelecting)
            KeyType.NEXT_FIELD -> performNextField()
        }
    }

    override fun onKeyRepeat(view: KeyView, key: KeyDef) {
        when (key.type) {
            KeyType.DELETE ->
                currentInputConnection?.deleteSurroundingText(1, 0)
            KeyType.ARROW_LEFT -> tapKey(KeyEvent.KEYCODE_DPAD_LEFT, trackpadSelecting)
            KeyType.ARROW_RIGHT -> tapKey(KeyEvent.KEYCODE_DPAD_RIGHT, trackpadSelecting)
            KeyType.ARROW_UP -> tapKey(KeyEvent.KEYCODE_DPAD_UP, trackpadSelecting)
            KeyType.ARROW_DOWN -> tapKey(KeyEvent.KEYCODE_DPAD_DOWN, trackpadSelecting)
            KeyType.SEL_START_LEFT -> moveSelectionBoundary(startSide = true, delta = -1)
            KeyType.SEL_END_RIGHT -> moveSelectionBoundary(startSide = false, delta = 1)
            KeyType.CUSTOM ->
                if (key.repeatOnHold) {
                    currentInputConnection?.commitText(key.output.ifEmpty { key.label }, 1)
                }
            else -> Unit
        }
    }

    /** Spacebar quick chips: emoji page (left) and trackpad (right). */
    override fun onKeyZoneTap(view: KeyView, key: KeyDef, zone: Int) {
        if (key.type != KeyType.SPACE) return
        when (zone) {
            1 -> {
                feedback(key)
                page = Page.EMOJI
                buildKeyboard()
            }
            2 -> if (Prefs.trackpadEnabled(this)) {
                feedback(key)
                enterTrackpad()
            }
        }
    }

    override fun onKeyLongPress(view: KeyView, key: KeyDef) {
        // Space long-press → cursor trackpad.
        if (key.type == KeyType.SPACE && Prefs.trackpadEnabled(this)) {
            feedback(key)
            enterTrackpad()
            return
        }

        // Custom keys with a configured long-press shortcut (no alternates set).
        if (key.type == KeyType.CUSTOM && key.longPressOutput.isNotEmpty() && key.alternates.isEmpty()) {
            feedback(key)
            typeText(key.longPressOutput)
            return
        }

        val alternates = alternatesFor(key)
        if (alternates.isEmpty()) return
        feedback(key)
        showAlternatesOverlay(view, alternates)
    }

    override fun onKeySlide(view: KeyView, key: KeyDef, rawX: Float, rawY: Float) {
        if (trackpadActive) {
            if (trackpadLastX < 0f) {
                trackpadLastX = rawX
                trackpadLastY = rawY
            }
            trackpadMove(rawX - trackpadLastX, rawY - trackpadLastY)
            trackpadLastX = rawX
            trackpadLastY = rawY
        } else if (altChips.isNotEmpty()) {
            highlightAlternate(rawX, rawY)
        }
    }

    override fun onKeyTouchDown(view: KeyView, key: KeyDef) {
        val showable = key.type == KeyType.LETTER ||
                (key.type == KeyType.CUSTOM && key.label.length <= 2)
        if (showable && Prefs.previewEnabled(this)) showPreviewPopup(view, key)
    }

    override fun onKeyTouchUp(view: KeyView, key: KeyDef) {
        // Commit a slide-selected alternate.
        if (altChips.isNotEmpty() && altSourceKey == key) {
            altSelected?.let { typeText(it) }
            hideAlternatesOverlay()
        }
        hidePreviewWithLinger()
    }

    private fun typeLetter(key: KeyDef) {
        val raw = key.output.ifEmpty { key.label }
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
                capsLock = true
                shift = true
            }
            else -> shift = !shift
        }
        lastShiftTapTime = now
        buildKeyboard()
    }

    // ---------------- enter / next field / auto-capitalization ----------------

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

    /** Jumps to the next input field on screen (Next action, or Tab as fallback). */
    private fun performNextField() {
        val info = currentInputEditorInfo
        val options = info?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_NEXT &&
            (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
        ) {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_NEXT)
        } else {
            tapKey(KeyEvent.KEYCODE_TAB, shift = false)
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

    // ---------------- cursor & selection helpers ----------------

    /** Sends a full key press (optionally with Shift held) to the focused field. */
    private fun tapKey(keyCode: Int, shift: Boolean = false) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        try {
            if (shift) {
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
            }
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
            if (shift) {
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Moves one boundary of the current selection.
     * Left key grows/shrinks the START, right key the END.
     * With no selection yet, it seeds one character.
     */
    private fun moveSelectionBoundary(startSide: Boolean, delta: Int) {
        val ic = currentInputConnection ?: return
        try {
            val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            var start = et.selectionStart
            var end = et.selectionEnd
            val len = et.text?.length ?: 0
            if (start < 0 || end < 0) return
            if (start == end) {
                // Seed a selection from the caret.
                if (startSide) start = (start + delta).coerceIn(0, len)
                else end = (end + delta).coerceIn(0, len)
            } else if (startSide) {
                start = (start + delta).coerceIn(0, end)
            } else {
                end = (end + delta).coerceIn(start, len)
            }
            ic.setSelection(minOf(start, end), maxOf(start, end))
        } catch (_: Exception) {
        }
    }

    /**
     * Jumps to the very start / end of the text. While a selection is active
     * it EXTENDS the selection to that edge (selection stays open); otherwise
     * the caret simply jumps there.
     */
    private fun jumpSelectionToEdge(toStart: Boolean) {
        val ic = currentInputConnection ?: return
        try {
            val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            val len = et.text?.length ?: 0
            val start = et.selectionStart
            val end = et.selectionEnd
            if (start < 0 || end < 0) return
            if (trackpadSelecting && start != end) {
                if (toStart) ic.setSelection(0, maxOf(start, end))
                else ic.setSelection(minOf(start, end), len)
            } else {
                val edge = if (toStart) 0 else len
                ic.setSelection(edge, edge)
            }
        } catch (_: Exception) {
        }
    }

    private fun currentSelection(): ExtractedText? = try {
        currentInputConnection?.getExtractedText(ExtractedTextRequest(), 0)
    } catch (_: Exception) {
        null
    }

    // ---------------- trackpad overlay ----------------

    private fun enterTrackpad() {
        if (trackpadActive) return
        trackpadActive = true
        trackpadLastX = -1f
        trackpadLastY = -1f
        trackpadAccumX = 0f
        trackpadAccumY = 0f

        val dark = isDarkMode
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (dark) Color.argb(235, 20, 20, 20) else Color.argb(235, 242, 242, 242))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_cursor)
            drawable?.setTint(if (dark) Color.WHITE else Color.BLACK)
        }
        sheet.addView(
            icon,
            LinearLayout.LayoutParams(dp(34), dp(34)).apply { bottomMargin = dp(8) }
        )

        sheet.addView(TextView(this).apply {
            text = "Trackpad"
            textSize = 16f
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            letterSpacing = 0.06f
        })
        sheet.addView(TextView(this).apply {
            text = "Slide to move the cursor · tap to exit"
            textSize = 12f
            setTextColor(if (dark) 0xFFB0B0B0.toInt() else 0xFF6B6B6B.toInt())
            setPadding(0, dp(4), 0, dp(8))
        })

        // ---- cursor control buttons ----
        val chipBg = if (dark) 0xFF3A3A3A.toInt() else 0xFFDEDEDE.toInt()

        fun cursorChip(label: String, description: String, action: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                textSize = 15f
                typeface = Ui.fontMedium(this@CustomKeyService)
                gravity = Gravity.CENTER
                setTextColor(if (dark) Color.WHITE else Color.BLACK)
                background = roundedDrawable(chipBg, dp(10))
                contentDescription = description
                setOnClickListener { action() }
            }

        val arrowsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        listOf(
            "◀" to ("Cursor left" to { tapKey(KeyEvent.KEYCODE_DPAD_LEFT) }),
            "▲" to ("Cursor up" to { tapKey(KeyEvent.KEYCODE_DPAD_UP) }),
            "▼" to ("Cursor down" to { tapKey(KeyEvent.KEYCODE_DPAD_DOWN) }),
            "▶" to ("Cursor right" to { tapKey(KeyEvent.KEYCODE_DPAD_RIGHT) })
        ).forEach { (label, pair) ->
            arrowsRow.addView(
                cursorChip(label, pair.first, pair.second),
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
            )
        }
        sheet.addView(arrowsRow)

        val selectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        listOf(
            "⇤" to ("Selection start left" to { moveSelectionBoundary(startSide = true, delta = -1) }),
            "⇥" to ("Selection end right" to { moveSelectionBoundary(startSide = false, delta = 1) }),
            "◧" to ("Select left" to { tapKey(KeyEvent.KEYCODE_DPAD_LEFT, true) }),
            "◨" to ("Select right" to { tapKey(KeyEvent.KEYCODE_DPAD_RIGHT, true) })
        ).forEach { (label, pair) ->
            selectionRow.addView(
                cursorChip(label, pair.first, pair.second),
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
            )
        }
        sheet.addView(selectionRow)

        // Jump to the very start / end — keeps the selection (and the
        // trackpad) open, no need to close anything.
        val jumpRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        listOf(
            "⇤ Start" to ("Jump to text start" to { jumpSelectionToEdge(toStart = true) }),
            "End ⇥" to ("Jump to text end" to { jumpSelectionToEdge(toStart = false) })
        ).forEach { (label, pair) ->
            jumpRow.addView(
                cursorChip(label, pair.first, pair.second),
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
            )
        }
        sheet.addView(jumpRow)

        // Select toggle: after this, slides EXTEND the selection.
        val selectChip = TextView(this).apply {
            text = if (trackpadSelecting) "Selecting: ON" else "Select text"
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setTextColor(if (trackpadSelecting) Color.WHITE else Color.BLACK)
            background = roundedDrawable(
                if (trackpadSelecting) 0xFF0A84FF.toInt()
                else if (dark) 0xFF3A3A3A.toInt() else 0xFFDEDEDE.toInt(),
                dp(18)
            )
            setOnClickListener {
                trackpadSelecting = !trackpadSelecting
                text = if (trackpadSelecting) "Selecting: ON" else "Select text"
                setTextColor(if (trackpadSelecting) Color.WHITE else Color.BLACK)
                background = roundedDrawable(
                    if (trackpadSelecting) 0xFF0A84FF.toInt()
                    else if (dark) 0xFF3A3A3A.toInt() else 0xFFDEDEDE.toInt(),
                    dp(18)
                )
            }
        }
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        chipRow.addView(selectChip)
        sheet.addView(chipRow)

        overlayLayer.removeAllViews()
        overlayLayer.addView(
            sheet,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        overlayLayer.setOnTouchListener { _, event -> trackpadTouch(event) }
        overlayLayer.visibility = View.VISIBLE
    }

    private fun exitTrackpad() {
        if (!trackpadActive) return
        trackpadActive = false
        overlayLayer.visibility = View.GONE
        overlayLayer.removeAllViews()
        overlayLayer.setOnTouchListener(null)
    }

    private var trackpadDownTime = 0L
    private var trackpadMoved = false
    private var trackpadTravel = 0f

    private fun trackpadTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackpadDownTime = System.currentTimeMillis()
                trackpadMoved = false
                trackpadTravel = 0f
                trackpadLastX = event.rawX
                trackpadLastY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - trackpadLastX
                val dy = event.rawY - trackpadLastY
                // total travelled distance — slow deliberate drags count too
                trackpadTravel += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                if (trackpadTravel > dp(6)) trackpadMoved = true
                trackpadMove(dx, dy)
                trackpadLastX = event.rawX
                trackpadLastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val held = System.currentTimeMillis() - trackpadDownTime
                if (!trackpadMoved && held < 280L) exitTrackpad()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    /** Converts pixel slides into cursor (or selection) movement. */
    private fun trackpadMove(dx: Float, dy: Float) {
        val pxPerChar = dp(22).coerceAtLeast(1)
        val pxPerLine = dp(46).coerceAtLeast(1)

        trackpadAccumX += dx
        trackpadAccumY += dy

        var stepsX = 0
        while (kotlin.math.abs(trackpadAccumX) >= pxPerChar) {
            val dir = if (trackpadAccumX > 0) 1 else -1
            trackpadAccumX -= dir * pxPerChar
            stepsX += dir
        }
        while (stepsX != 0) {
            tapKey(
                if (stepsX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT,
                trackpadSelecting
            )
            stepsX += if (stepsX > 0) -1 else 1
        }

        var stepsY = 0
        while (kotlin.math.abs(trackpadAccumY) >= pxPerLine) {
            val dir = if (trackpadAccumY > 0) 1 else -1
            trackpadAccumY -= dir * pxPerLine
            stepsY += dir
        }
        while (stepsY != 0) {
            tapKey(
                if (stepsY > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP,
                trackpadSelecting
            )
            stepsY += if (stepsY > 0) -1 else 1
        }
    }

    // ---------------- alternates overlay (slide to select) ----------------

    private fun showAlternatesOverlay(anchor: KeyView, alternates: String) {
        hideAlternatesOverlay()
        val palette = KeyboardTheme.palette(this)
        val style = palette.style
        val chipSize = dp(46)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = roundedDrawable(palette.popupBg, dp(14))
        }

        val chips = ArrayList<Pair<TextView, String>>()
        alternates.forEach { ch ->
            val chip = TextView(this).apply {
                text = ch.toString()
                gravity = Gravity.CENTER
                textSize = 19f
                setTextColor(style.textColor)
                background = roundedDrawable(palette.withAlpha(style.keyBg), dp(9))
                contentDescription = ch.toString()
            }
            chips.add(chip to ch.toString())
            row.addView(
                chip,
                LinearLayout.LayoutParams(chipSize, chipSize).apply { marginEnd = dp(4) }
            )
        }

        // Position: centered above the anchor key, inside the window.
        val anchorLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        val anchorLeftInWindow = anchorLoc[0] - rootLoc[0]
        val anchorTopInWindow = anchorLoc[1] - rootLoc[1]

        overlayLayer.removeAllViews()
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        overlayLayer.addView(row, lp)
        overlayLayer.visibility = View.VISIBLE

        // measure to center over the anchor
        overlayLayer.post {
            val w = row.width
            val screenW = root.width
            lp.leftMargin = (anchorLeftInWindow + anchor.width / 2 - w / 2)
                .coerceIn(dp(4), (screenW - w - dp(4)).coerceAtLeast(dp(4)))
            lp.topMargin = (anchorTopInWindow - row.height - dp(10)).coerceAtLeast(dp(4))
            row.layoutParams = lp
        }

        altChips = chips
        altSelected = null
        altSourceKey = anchor.key()
    }

    private fun highlightAlternate(rawX: Float, rawY: Float) {
        val loc = IntArray(2)
        var found: TextView? = null
        var foundChar: String? = null
        for ((chip, ch) in altChips) {
            chip.getLocationOnScreen(loc)
            val rect = Rect(loc[0], loc[1], loc[0] + chip.width, loc[1] + chip.height)
            if (rect.contains(rawX.toInt(), rawY.toInt())) {
                found = chip
                foundChar = ch
                break
            }
        }
        val palette = KeyboardTheme.palette(this)
        for ((chip, _) in altChips) {
            chip.background = if (chip === found) {
                roundedDrawable(0xFF0A84FF.toInt(), dp(9))
            } else {
                roundedDrawable(palette.withAlpha(palette.style.keyBg), dp(9))
            }
            if (chip === found) chip.setTextColor(Color.WHITE)
            else chip.setTextColor(palette.style.textColor)
        }
        altSelected = foundChar
    }

    private fun hideAlternatesOverlay() {
        altChips = emptyList()
        altSelected = null
        altSourceKey = null
        if (!trackpadActive) {
            overlayLayer.visibility = View.GONE
            overlayLayer.removeAllViews()
        } else {
            // keep the trackpad sheet
        }
    }

    // ---------------- preview popup ----------------

    private fun showPreviewPopup(anchor: KeyView, key: KeyDef) {
        hidePreviewPopup()
        mainHandler.removeCallbacks(hidePreviewRunnable)

        val palette = KeyboardTheme.palette(this)
        val color = Prefs.previewColor(this).let { if (it == 0) palette.previewBg else it }
        val size = dp(Prefs.previewSizeDp(this).coerceIn(36, 96))
        val radius = dp(Prefs.previewRadiusDp(this).coerceIn(0, 30))

        val label = if (key.type == KeyType.LETTER && (shift || capsLock)) {
            key.label.uppercase()
        } else {
            key.label
        }

        val textView = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(palette.previewText)
            textSize = 24f
            background = roundedDrawable(color, radius)
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
        val y = location[1] - size - dp(6)

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        previewPopup = popup
    }

    private fun hidePreviewWithLinger() {
        val linger = Prefs.previewLingerMs(this).coerceIn(0, 800)
        mainHandler.removeCallbacks(hidePreviewRunnable)
        if (linger == 0) hidePreviewPopup()
        else mainHandler.postDelayed(hidePreviewRunnable, linger.toLong())
    }

    private fun hidePreviewPopup() {
        previewPopup?.dismiss()
        previewPopup = null
    }

    private fun hidePopups() {
        hidePreviewPopup()
        hideAlternatesOverlay()
    }

    // ---------------- sound / vibration ----------------

    private fun feedback(key: KeyDef? = null) {
        val style = key?.style
        if (Prefs.soundEnabled(this)) {
            try {
                if (sounds == null) sounds = KeySounds(this)
                val assetId = style?.soundAssetId
                if (assetId != null) {
                    val asset = Assets.byId(this, assetId)
                    if (asset != null) {
                        sounds?.playAsset(Assets.path(this, asset).absolutePath, Prefs.soundVolume(this))
                    } else {
                        sounds?.play(Prefs.soundStyle(this), Prefs.soundVolume(this))
                    }
                } else {
                    val keyStyle = style?.soundStyle
                    if (keyStyle != null && keyStyle in 0..10) {
                        sounds?.play(keyStyle, Prefs.soundVolume(this))
                    } else {
                        sounds?.play(Prefs.soundStyle(this), Prefs.soundVolume(this))
                    }
                }
            } catch (_: Exception) {
            }
        }
        vibrate(style?.vibrationPercent)
    }

    /** Fixed vibration: correct vibrator on Android 12+, per-key strength override. */
    private fun vibrate(strengthOverride: Int? = null) {
        if (!Prefs.vibrationEnabled(this)) return
        val strength = strengthOverride ?: Prefs.vibrationStrength(this)
        if (strength <= 0) return
        try {
            val vibrator: Vibrator = (
                if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Vibrator::class.java)
                }
                ) ?: return
            val ms = Prefs.vibrationDurationMs(this).coerceIn(10, 100).toLong()
            if (vibrator.hasAmplitudeControl()) {
                val amplitude = (strength * 255 / 100).coerceIn(60, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    // ---------------- helpers ----------------

    private fun roundedDrawable(color: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DOUBLE_TAP_MS = 350L
    }
}
