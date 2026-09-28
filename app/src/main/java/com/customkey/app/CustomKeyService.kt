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
    private lateinit var overlay: FrameLayout

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
            setPadding(dp(3), dp(4), dp(3), dp(3))
        }
        overlay = FrameLayout(this).apply { visibility = View.GONE }
        root = FrameLayout(this).apply {
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                overlay,
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
        return try {
            val wm = getSystemService(android.view.WindowManager::class.java) ?: return 0
            val realBounds = wm.maximumWindowMetrics.bounds
            val decor = window?.window?.decorView ?: return 0
            val loc = IntArray(2)
            decor.getLocationOnScreen(loc)
            val decorBottom = loc[1] + decor.height
            val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId <= 0) return 0
            val navH = resources.getDimensionPixelSize(resId)
            if (navH <= 0) return 0
            if (decorBottom >= realBounds.bottom() - navH / 2) navH else 0
        } catch (_: Exception) {
            0
        }
    }

    private fun applyBottomInset(reported: Int) {
        if (!::root.isInitialized) return
        val navBottom = if (reported > 0) reported else navBarHeuristic()
        lastNavBottom = navBottom
        content.setPadding(
            dp(3), dp(4), dp(3),
            navBottom + dp(3) + dp(Prefs.extraBottomDp(this).coerceIn(0, 20))
        )
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // Fallback for ROMs where the decor listener never fires.
        try {
            val decor = window?.window?.decorView ?: return
            val insets = decor.rootWindowInsets ?: return
            applyBottomInset(insetsBottom(insets))
        } catch (_: Exception) {
        }
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
        exitTrackpad()

        val palette = KeyboardTheme.palette(this)
        val bgDrawable = KeyboardTheme.backgroundDrawable(this)
        if (bgDrawable != null) root.background = bgDrawable
        else root.setBackgroundColor(palette.bg)

        content.setPadding(
            dp(3), dp(4), dp(3),
            lastNavBottom + dp(3) + dp(Prefs.extraBottomDp(this).coerceIn(0, 20))
        )
        content.removeAllViews()

        when (page) {
            Page.LETTERS -> {
                val def = Layouts.current(this)
                def.rows.forEach { row -> content.addView(buildRow(row.keys)) }
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

    private fun buildRow(keys: List<KeyDef>): LinearLayout {
        val palette = KeyboardTheme.palette(this)
        val style = palette.style
        val heightPx = dp(Prefs.keyHeightDp(this).coerceIn(40, 62))
        val gapPx = dp(Prefs.keyGapDp(this).coerceIn(2, 8))
        val halfGapPx = (gapPx / 2f).toInt().coerceAtLeast(0)

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
            lp.setMargins(halfGapPx, halfGapPx, halfGapPx, halfGapPx)
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
        rows.forEach { row -> content.addView(buildRow(row.keys)) }
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
        rows.forEach { row -> content.addView(buildRow(row.keys)) }
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
        rows.forEach { row -> content.addView(buildRow(row.keys)) }
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
        val perRow = 8
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until perRow) {
                val index = i + j
                val cell = TextView(this).apply {
                    textSize = 24f
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
                row.addView(cell, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            grid.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42))
            )
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
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(158))
        )

        content.addView(
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
        feedback()
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

    override fun onKeyLongPress(view: KeyView, key: KeyDef) {
        // Space long-press → cursor trackpad.
        if (key.type == KeyType.SPACE && Prefs.trackpadEnabled(this)) {
            feedback()
            enterTrackpad()
            return
        }

        // Custom keys with a configured long-press shortcut (no alternates set).
        if (key.type == KeyType.CUSTOM && key.longPressOutput.isNotEmpty() && key.alternates.isEmpty()) {
            feedback()
            typeText(key.longPressOutput)
            return
        }

        val alternates = alternatesFor(key)
        if (alternates.isEmpty()) return
        feedback()
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
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, now, now, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
            }
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, now, now, keyCode, 0, meta))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, now, now, keyCode, 0, meta))
            if (shift) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, now, now, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
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
            setPadding(0, dp(4), 0, dp(10))
        })

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

        overlay.removeAllViews()
        overlay.addView(
            sheet,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        overlay.setOnTouchListener { _, event -> trackpadTouch(event) }
        overlay.visibility = View.VISIBLE
    }

    private fun exitTrackpad() {
        if (!trackpadActive) return
        trackpadActive = false
        overlay.visibility = View.GONE
        overlay.removeAllViews()
        overlay.setOnTouchListener(null)
    }

    private var trackpadDownTime = 0L
    private var trackpadMoved = false

    private fun trackpadTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackpadDownTime = System.currentTimeMillis()
                trackpadMoved = false
                trackpadLastX = event.rawX
                trackpadLastY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - trackpadLastX
                val dy = event.rawY - trackpadLastY
                if (kotlin.math.abs(dx) > 2f || kotlin.math.abs(dy) > 2f) trackpadMoved = true
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

        overlay.removeAllViews()
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        overlay.addView(row, lp)
        overlay.visibility = View.VISIBLE

        // measure to center over the anchor
        overlay.post {
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
            overlay.visibility = View.GONE
            overlay.removeAllViews()
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

    private fun feedback() {
        if (Prefs.soundEnabled(this)) {
            try {
                if (sounds == null) sounds = KeySounds(this)
                sounds?.play(Prefs.soundStyle(this), Prefs.soundVolume(this))
            } catch (_: Exception) {
            }
        }
        vibrate()
    }

    /** Fixed vibration: correct vibrator on Android 12+, amplitude support check. */
    private fun vibrate() {
        if (!Prefs.vibrationEnabled(this)) return
        val strength = Prefs.vibrationStrength(this)
        if (strength <= 0) return
        try {
            val vibrator: Vibrator? =
                if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Vibrator::class.java)
                } ?: return
            val ms = 30L
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
