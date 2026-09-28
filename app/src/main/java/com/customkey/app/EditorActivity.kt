package com.customkey.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

/**
 * The Keyboard Editor:
 *  - sticky EXACT live preview at the top (never scrolls away)
 *  - tap a key to edit (label, text, width, color, long-press action)
 *  - long-press keys for batch (bunch) editing — width & delete
 *  - move keys left/right/up/down
 *  - full Android emoji picker for custom keys
 *  - key height, spacing, transparency, extra bottom padding, background image
 *  - save / reset to default
 */
class EditorActivity : Activity() {

    private lateinit var palette: Ui.Palette
    private lateinit var previewWrapper: FrameLayout
    private lateinit var previewContainer: LinearLayout
    private lateinit var rowsBox: LinearLayout
    private lateinit var batchBar: LinearLayout
    private lateinit var batchCount: TextView

    private lateinit var def: KeyboardDef

    private var keyHeightDp = 48
    private var gapDp = 3
    private var opacity = 100
    private var extraBottomDp = 0

    private var selectionMode = false
    private val selectedIds = mutableSetOf<Long>()

    companion object {
        private const val REQUEST_BG_IMAGE = 9001

        private val SYSTEM_TYPES = setOf(
            KeyType.SHIFT, KeyType.DELETE, KeyType.SPACE, KeyType.ENTER,
            KeyType.TO_SYMBOLS, KeyType.TO_EXTRA, KeyType.TO_LETTERS, KeyType.EMOJI
        )

        private val COLOR_SWATCHES = listOf<Int?>(
            null,
            0xFF8E8E93.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C1C1E.toInt(),
            0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(),
            0xFF34C759.toInt(), 0xFF00C7BE.toInt(), 0xFF0A84FF.toInt(),
            0xFF5E5CE6.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D55.toInt(),
            0xFFA2845E.toInt()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        def = Layouts.current(this)
        keyHeightDp = Prefs.keyHeightDp(this).coerceIn(40, 62)
        gapDp = Prefs.keyGapDp(this).coerceIn(2, 8)
        opacity = Prefs.keyboardOpacity(this).coerceIn(40, 100)
        extraBottomDp = Prefs.extraBottomDp(this).coerceIn(0, 20)
        createScreen()
    }

    private fun dp(value: Int): Int = Ui.dp(this, value)

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------
    // SCREEN — header + sticky preview + scrollable options
    // ------------------------------------------------------------------

    private fun createScreen() {

        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }

        // ---------- header (fixed, circle back button) ----------

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(18), dp(4))
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            background = Ui.rounded(this@EditorActivity, palette.tinted, 20)
            isClickable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))

        header.addView(TextView(this).apply {
            text = "Keyboard Editor"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
            setPadding(dp(12), 0, 0, 0)
        })
        base.addView(header)

        // ---------- sticky exact preview (never scrolls) ----------

        previewWrapper = FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        previewContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        previewWrapper.addView(
            previewContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        base.addView(
            previewWrapper,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ---------- scrollable options ----------

        val scroll = ScrollView(this)
        Ui.applyNavBarInsetPadding(scroll)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(30))
        }

        // ===== TEST =====

        Ui.heading(this, palette, root, "TEST")

        val testCard = Ui.addCard(this, palette, root)
        testCard.addView(TextView(this).apply {
            text = "Save, then tap below and switch to CustomKey to type with your layout."
            textSize = 13f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        testCard.addView(EditText(this).apply {
            hint = "Try your keyboard here…"
            textSize = 16f
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            background = Ui.rounded(this@EditorActivity, palette.inputBg, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = false
            minLines = 2
            gravity = Gravity.TOP
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ===== LAYOUT =====

        Ui.heading(this, palette, root, "LAYOUT")

        val layoutCard = Ui.addCard(this, palette, root)

        // Batch (bunch) edit bar — visible while keys are selected
        batchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4), 0, dp(8))
        }
        batchCount = TextView(this).apply {
            textSize = 14f
            setTextColor(palette.accent)
        }
        batchBar.addView(
            batchCount,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        batchBar.addView(
            Ui.compactButton(this, palette, "Width") { batchWidthDialog() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
            ).apply { marginEnd = dp(8) }
        )
        batchBar.addView(
            Ui.compactButton(this, palette, "Delete", danger = true) { batchDelete() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
            ).apply { marginEnd = dp(8) }
        )
        batchBar.addView(
            Ui.compactButton(this, palette, "✕") { exitSelection() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36))
        )
        layoutCard.addView(batchBar)

        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layoutCard.addView(rowsBox)

        val rowButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        Ui.button(this, palette, rowButtons, "＋ Add row", matchWidth = false) { addRow() }
        Ui.button(this, palette, rowButtons, "Remove last row", filled = false, matchWidth = false) {
            removeRow()
        }
        layoutCard.addView(
            rowButtons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        layoutCard.addView(TextView(this).apply {
            text = "Tap a key to edit • long-press a key to select multiple"
            textSize = 12f
            setTextColor(palette.secondary)
            setPadding(0, dp(10), 0, 0)
        })

        // ===== APPEARANCE =====

        Ui.heading(this, palette, root, "APPEARANCE")

        val appearanceCard = Ui.addCard(this, palette, root)

        Ui.seekRow(
            this, palette, appearanceCard,
            "Key height", 40, 62, keyHeightDp, true,
            { "${it}dp" }
        ) {
            keyHeightDp = it
            refreshPreview()
        }

        Ui.seekRow(
            this, palette, appearanceCard,
            "Key spacing", 2, 8, gapDp, true,
            { "${it}dp" }
        ) {
            gapDp = it
            refreshPreview()
        }

        Ui.seekRow(
            this, palette, appearanceCard,
            "Keyboard transparency", 40, 100, opacity, true,
            { "${100 - it}%" }
        ) {
            opacity = it
            refreshPreview()
        }

        Ui.seekRow(
            this, palette, appearanceCard,
            "Extra bottom padding", 0, 20, extraBottomDp, true,
            { "${it}dp" }
        ) {
            extraBottomDp = it
        }

        appearanceCard.addView(TextView(this).apply {
            text = "Keyboard background image"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(14), 0, 0)
        })
        val bgButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        Ui.button(this, palette, bgButtons, "Choose image", matchWidth = false) {
            pickBackgroundImage()
        }
        Ui.button(this, palette, bgButtons, "Remove", filled = false, matchWidth = false) {
            removeBackgroundImage()
        }
        appearanceCard.addView(
            bgButtons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ===== ACTIONS =====

        Ui.heading(this, palette, root, "ACTIONS")

        Ui.button(this, palette, root, "💾  Save keyboard") { saveLayout() }
        Ui.button(this, palette, root, "Reset to default", filled = false) { confirmReset() }

        scroll.addView(
            root,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        base.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(base)

        refreshPreview()
        refreshRows()
    }

    // ------------------------------------------------------------------
    // STICKY EXACT PREVIEW
    // ------------------------------------------------------------------

    private fun refreshPreview() {
        if (!::previewContainer.isInitialized) return

        val theme = KeyboardTheme.palette(this, opacity)
        val bgDrawable = KeyboardTheme.backgroundDrawable(this)
        if (bgDrawable != null) previewWrapper.background = bgDrawable
        else previewWrapper.setBackgroundColor(theme.bg)

        previewContainer.removeAllViews()

        val heightPx = dp(keyHeightDp)
        val gapPx = dp(gapDp)
        val halfGap = (gapPx / 2f).toInt().coerceAtLeast(0)

        def.rows.forEach { rowDef ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowDef.keys.forEach { keyDef ->
                val view = KeyView(
                    context = this,
                    label = previewLabel(keyDef),
                    style = theme.style,
                    weight = keyDef.weight,
                    heightPx = heightPx,
                    special = keyDef.type != KeyType.LETTER && keyDef.type != KeyType.CUSTOM,
                    interactive = false
                )
                keyDef.color?.let { view.colorOverride = theme.withAlpha(it) }
                view.applyRowLayout(halfGap)
                row.addView(view)
            }
            previewContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Shrink the preview only if it would eat too much of the screen.
        val naturalHeight = def.rows.size * (heightPx + gapPx) + dp(16)
        val maxHeight = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        val scale = if (naturalHeight > maxHeight) maxHeight.toFloat() / naturalHeight else 1f
        previewContainer.pivotX = 0f
        previewContainer.pivotY = 0f
        previewContainer.scaleX = scale
        previewContainer.scaleY = scale

        val lp = previewWrapper.layoutParams
        lp.height = (naturalHeight * scale + dp(16)).toInt()
        previewWrapper.layoutParams = lp
    }

    private fun previewLabel(keyDef: KeyDef): String = when (keyDef.type) {
        KeyType.SHIFT -> "⇧"
        KeyType.DELETE -> "⌫"
        KeyType.ENTER -> "↵"
        else -> keyDef.label
    }

    // ------------------------------------------------------------------
    // ROWS / CHIPS
    // ------------------------------------------------------------------

    private fun refreshRows() {
        if (!::rowsBox.isInitialized) return
        rowsBox.removeAllViews()

        batchBar.visibility = if (selectedIds.isEmpty()) View.GONE else View.VISIBLE
        batchCount.text = "${selectedIds.size} selected"

        def.rows.forEachIndexed { rowIndex, rowDef ->
            rowsBox.addView(TextView(this).apply {
                text = "Row ${rowIndex + 1}  ·  ${rowDef.keys.size} keys"
                textSize = 13f
                setTextColor(palette.secondary)
                setPadding(0, dp(10), 0, dp(4))
            })

            val scrollRow = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, dp(4), 0)
            }
            rowDef.keys.forEachIndexed { keyIndex, keyDef ->
                line.addView(keyChip(keyDef, rowIndex, keyIndex))
            }
            line.addView(addChip(rowIndex))
            scrollRow.addView(line)
            rowsBox.addView(
                scrollRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun chipLabel(keyDef: KeyDef): String = when (keyDef.type) {
        KeyType.LETTER -> keyDef.label
        KeyType.CUSTOM -> keyDef.label
        KeyType.SHIFT -> "⇧"
        KeyType.DELETE -> "⌫"
        KeyType.SPACE -> "space"
        KeyType.ENTER -> "↵"
        KeyType.TO_SYMBOLS -> "?123"
        KeyType.TO_EXTRA -> "=\\<"
        KeyType.TO_LETTERS -> "ABC"
        KeyType.EMOJI -> "😀"
    }

    private fun chipColor(keyDef: KeyDef): Int = when (keyDef.type) {
        KeyType.LETTER -> palette.inputBg
        KeyType.CUSTOM -> palette.chipCustom
        else -> palette.chipSystem
    }

    private fun keyChip(keyDef: KeyDef, rowIndex: Int, keyIndex: Int): TextView {
        val selected = selectionMode && selectedIds.contains(keyDef.id)
        return TextView(this).apply {
            text = chipLabel(keyDef)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (selected) Color.WHITE else palette.text)
            background = Ui.rounded(
                this@EditorActivity,
                if (selected) palette.accent else chipColor(keyDef),
                10
            )
            minWidth = dp(52)
            minHeight = dp(44)
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (selectionMode) toggleSelect(keyDef)
                else editKeyDialog(rowIndex, keyIndex)
            }
            setOnLongClickListener {
                selectionMode = true
                toggleSelect(keyDef)
                true
            }
        }
    }

    private fun addChip(rowIndex: Int): TextView {
        return TextView(this).apply {
            text = "＋"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.accent)
            background = Ui.rounded(this@EditorActivity, Color.TRANSPARENT, 10, palette.hairline)
            minWidth = dp(44)
            minHeight = dp(44)
            setPadding(dp(10), 0, dp(10), 0)
            contentDescription = "Add key to row ${rowIndex + 1}"
            setOnClickListener { addKeyDialog(rowIndex) }
        }
    }

    private fun fieldLabel(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(palette.secondary)
            setPadding(0, dp(12), 0, dp(6))
        }

    private fun dialogButton(
        title: String,
        textColor: Int,
        bg: Int,
        stroke: Int?,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = title
        textSize = 15f
        isAllCaps = false
        minHeight = 0
        minWidth = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        setTextColor(textColor)
        background = Ui.rounded(this@EditorActivity, bg, 12, stroke)
        setOnClickListener { onClick() }
    }

    private fun buttonRow(): LinearLayout =
        LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun addDialogButton(row: LinearLayout, button: Button) {
        row.addView(
            button,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) }
        )
    }

    // ------------------------------------------------------------------
    // SELECTION (bunch editing)
    // ------------------------------------------------------------------

    private fun toggleSelect(keyDef: KeyDef) {
        if (selectedIds.contains(keyDef.id)) selectedIds.remove(keyDef.id)
        else selectedIds.add(keyDef.id)
        if (selectedIds.isEmpty()) selectionMode = false
        refreshRows()
    }

    private fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        refreshRows()
    }

    private fun batchWidthDialog() {
        if (selectedIds.isEmpty()) return
        var w = 10
        val dialog = Ui.CustomDialog(this, palette, "Width — ${selectedIds.size} keys")
        Ui.seekRow(
            this, palette, dialog.body,
            "Key width", 5, 30, 10, true,
            { String.format("%.1f×", it / 10f) }
        ) { w = it }
        Ui.button(this, palette, dialog.body, "Apply") {
            val weight = w / 10f
            def.rows.forEach { row ->
                val updated = row.keys.map { key ->
                    if (selectedIds.contains(key.id)) key.copy(weight = weight) else key
                }
                row.keys.clear()
                row.keys.addAll(updated)
            }
            dialog.dialog.dismiss()
            refreshRows()
            refreshPreview()
        }
        dialog.show()
    }

    private fun batchDelete() {
        if (selectedIds.isEmpty()) return
        def.rows.forEach { row ->
            val kept = row.keys.filterNot { key ->
                selectedIds.contains(key.id) && !SYSTEM_TYPES.contains(key.type)
            }
            row.keys.clear()
            row.keys.addAll(kept)
        }
        val nonEmpty = def.rows.filter { it.keys.isNotEmpty() }
        def.rows.clear()
        def.rows.addAll(nonEmpty)
        if (def.rows.isEmpty()) def = Layouts.defaultLetters()
        exitSelection()
        refreshRows()
        refreshPreview()
    }

    // ------------------------------------------------------------------
    // EDIT KEY DIALOG
    // ------------------------------------------------------------------

    private fun editKeyDialog(rowIndex: Int, keyIndex: Int) {
        val keyDef = def.rows[rowIndex].keys[keyIndex]
        val system = SYSTEM_TYPES.contains(keyDef.type)

        val dialog = Ui.CustomDialog(
            this, palette,
            if (system) systemName(keyDef.type) else "Edit key"
        )
        val body = dialog.body

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        var letterField: EditText? = null
        var labelField: EditText? = null
        var outputField: EditText? = null
        var lpField: EditText? = null
        var lpMode = 0
        var chosenColor = keyDef.color

        if (keyDef.type == KeyType.LETTER) {
            content.addView(fieldLabel("Letter"))
            letterField = Ui.editText(this, palette, "a").apply { setText(keyDef.label) }
            content.addView(letterField)
        } else if (keyDef.type == KeyType.CUSTOM) {
            content.addView(fieldLabel("Key label (shown on key)"))
            labelField = Ui.editText(this, palette, "email").apply { setText(keyDef.label) }
            content.addView(labelField)

            content.addView(fieldLabel("Text it types"))
            outputField = Ui.editText(this, palette, "you@example.com").apply { setText(keyDef.output) }
            content.addView(outputField)

            val emojiRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            emojiRow.addView(
                Ui.compactButton(this, palette, "😀 Insert emoji") {
                    EmojiPicker(this@EditorActivity, palette).show { emoji ->
                        labelField?.append(emoji)
                        outputField?.append(emoji)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)
                )
            )
            content.addView(emojiRow)

            // ---- long-press behaviour ----
            lpMode = when {
                keyDef.repeatOnHold -> 1
                keyDef.longPressOutput.isNotEmpty() -> 2
                else -> 0
            }
            content.addView(fieldLabel("On long press"))

            lpField = Ui.editText(this, palette, "Text or shortcut to type…").apply {
                setText(keyDef.longPressOutput)
                isEnabled = lpMode == 2
                alpha = if (lpMode == 2) 1f else 0.45f
            }

            val lpChips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(2))
            }
            val lpChipViews = ArrayList<TextView>()
            val restyleLp: () -> Unit = {
                lpChipViews.forEachIndexed { i, chip ->
                    chip.setTextColor(if (i == lpMode) Color.WHITE else palette.text)
                    chip.background = Ui.rounded(
                        this@EditorActivity,
                        if (i == lpMode) palette.accent else palette.tinted,
                        17
                    )
                }
                lpField?.isEnabled = lpMode == 2
                lpField?.alpha = if (lpMode == 2) 1f else 0.45f
            }
            arrayOf("Default", "Repeat", "Type text").forEachIndexed { index, option ->
                val chip = TextView(this).apply {
                    text = option
                    textSize = 13f
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener {
                        lpMode = index
                        restyleLp()
                    }
                }
                lpChipViews.add(chip)
                lpChips.addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
                    ).apply { marginEnd = dp(8) }
                )
            }
            restyleLp()
            content.addView(lpChips)
            content.addView(
                lpField,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            content.addView(TextView(this).apply {
                text = "${systemName(keyDef.type)} — a system key. " +
                        "You can change its width, but it can't be edited or removed."
                textSize = 14f
                setTextColor(palette.secondary)
                setPadding(0, 0, 0, dp(4))
            })
        }

        // ---- color swatches (letter & custom keys) ----
        if (!system) {
            content.addView(fieldLabel("Key color"))
            val swatchScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val swatchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
            }
            val swatchViews = ArrayList<TextView>()
            COLOR_SWATCHES.forEach { c ->
                val swatch = TextView(this).apply {
                    textSize = 14f
                    gravity = Gravity.CENTER
                    isClickable = true
                    setTextColor(
                        when {
                            c == null -> palette.text
                            isLightColor(c) -> Color.BLACK
                            else -> Color.WHITE
                        }
                    )
                    background = Ui.rounded(
                        this@EditorActivity,
                        c ?: palette.inputBg,
                        16,
                        if (c == null) palette.hairline else null
                    )
                    setOnClickListener {
                        chosenColor = c
                        swatchViews.forEachIndexed { i, v ->
                            v.text = if (COLOR_SWATCHES[i] == chosenColor) "✓" else ""
                        }
                    }
                }
                swatchViews.add(swatch)
                swatchRow.addView(
                    swatch,
                    LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        marginEnd = dp(10)
                    }
                )
            }
            // Initial selection marks
            swatchViews.forEachIndexed { i, v ->
                v.text = if (COLOR_SWATCHES[i] == chosenColor) "✓" else ""
            }
            swatchScroll.addView(swatchRow)
            content.addView(
                swatchScroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // ---- width ----
        var weight = keyDef.weight
        Ui.seekRow(
            this, palette, content,
            "Width", 5, 30,
            (keyDef.weight * 10).toInt().coerceIn(5, 30), true,
            { String.format("%.1f×", it / 10f) }
        ) { weight = it / 10f }

        // ---- move controls ----
        if (!system) {
            content.addView(fieldLabel("Move key"))
            val moveRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            listOf(
                "◀" to -1, "▲" to -2, "▼" to 2, "▶" to 1
            ).forEach { (arrow, dir) ->
                moveRow.addView(TextView(this).apply {
                    text = arrow
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(palette.text)
                    background = Ui.rounded(this@EditorActivity, palette.tinted, 16)
                    isClickable = true
                    setOnClickListener {
                        if (moveKey(rowIndex, keyIndex, dir)) {
                            dialog.dialog.dismiss()
                            refreshRows()
                            refreshPreview()
                        } else {
                            toast("Can't move further")
                        }
                    }
                }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    marginEnd = dp(8)
                })
            }
            content.addView(
                moveRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // ---- action buttons ----
        val buttons = buttonRow()

        if (!system) {
            addDialogButton(
                buttons,
                dialogButton("Delete", palette.danger, Color.TRANSPARENT, palette.danger) {
                    def.rows[rowIndex].keys.removeAt(keyIndex)
                    dialog.dialog.dismiss()
                    refreshRows()
                    refreshPreview()
                }
            )
        }

        addDialogButton(
            buttons,
            dialogButton("Cancel", palette.text, palette.tinted, null) {
                dialog.dialog.dismiss()
            }
        )

        addDialogButton(
            buttons,
            dialogButton("Save", palette.accentText, palette.accent, null) {
                when {
                    keyDef.type == KeyType.LETTER -> {
                        val ch = letterField?.text?.toString()?.trim() ?: ""
                        if (ch.length != 1) {
                            toast("Enter a single letter")
                            return@dialogButton
                        }
                        def.rows[rowIndex].keys[keyIndex] = KeyDef(
                            type = KeyType.LETTER,
                            label = ch,
                            output = ch,
                            weight = weight,
                            color = chosenColor,
                            id = keyDef.id
                        )
                    }
                    keyDef.type == KeyType.CUSTOM -> {
                        val output = outputField?.text?.toString() ?: ""
                        if (output.isEmpty()) {
                            toast("Enter the text this key should type")
                            return@dialogButton
                        }
                        val label = labelField?.text?.toString()?.trim()
                            ?.ifEmpty { output.take(6) }
                            ?: output.take(6)
                        def.rows[rowIndex].keys[keyIndex] = KeyDef(
                            type = KeyType.CUSTOM,
                            label = label,
                            output = output,
                            weight = weight,
                            color = chosenColor,
                            longPressOutput = if (lpMode == 2) lpField?.text?.toString() ?: "" else "",
                            repeatOnHold = lpMode == 1,
                            id = keyDef.id
                        )
                    }
                    else -> {
                        def.rows[rowIndex].keys[keyIndex] = keyDef.copy(weight = weight)
                    }
                }
                dialog.dialog.dismiss()
                refreshRows()
                refreshPreview()
            }
        )

        body.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        body.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        dialog.show()
    }

    private fun isLightColor(color: Int): Boolean =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 150

    private fun moveKey(rowIndex: Int, keyIndex: Int, dir: Int): Boolean {
        val list = def.rows[rowIndex].keys
        return when (dir) {
            -1 -> if (keyIndex > 0) {
                Collections.swap(list, keyIndex, keyIndex - 1); true
            } else false
            1 -> if (keyIndex < list.size - 1) {
                Collections.swap(list, keyIndex, keyIndex + 1); true
            } else false
            -2 -> if (rowIndex > 0) {
                val key = list.removeAt(keyIndex)
                def.rows[rowIndex - 1].keys.add(key); true
            } else false
            2 -> if (rowIndex < def.rows.size - 1) {
                val key = list.removeAt(keyIndex)
                def.rows[rowIndex + 1].keys.add(key); true
            } else false
            else -> false
        }
    }

    // ------------------------------------------------------------------
    // ADD KEY DIALOG
    // ------------------------------------------------------------------

    private fun addKeyDialog(rowIndex: Int) {
        if (def.rows[rowIndex].keys.size >= 12) {
            toast("This row is full — remove a key first")
            return
        }

        val dialog = Ui.CustomDialog(this, palette, "Add key to row ${rowIndex + 1}")
        val body = dialog.body

        body.addView(fieldLabel("Key label (shown on key)"))
        val labelField = Ui.editText(this, palette, "email")
        body.addView(labelField)

        body.addView(fieldLabel("Text it types"))
        val outputField = Ui.editText(this, palette, "you@example.com")
        body.addView(outputField)

        val emojiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        emojiRow.addView(
            Ui.compactButton(this, palette, "😀 Insert emoji") {
                EmojiPicker(this@EditorActivity, palette).show { emoji ->
                    labelField.append(emoji)
                    outputField.append(emoji)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)
            )
        )
        body.addView(emojiRow)

        var weight = 1f
        Ui.seekRow(
            this, palette, body,
            "Width", 5, 30, 10, true,
            { String.format("%.1f×", it / 10f) }
        ) { weight = it / 10f }

        val buttons = buttonRow()

        addDialogButton(
            buttons,
            dialogButton("Cancel", palette.text, palette.tinted, null) {
                dialog.dialog.dismiss()
            }
        )

        addDialogButton(
            buttons,
            dialogButton("Add key", palette.accentText, palette.accent, null) {
                val output = outputField.text.toString()
                if (output.isEmpty()) {
                    toast("Enter the text this key should type")
                    return@dialogButton
                }
                val label = labelField.text.toString().trim().ifEmpty { output.take(6) }
                def.rows[rowIndex].keys.add(
                    KeyDef(
                        type = KeyType.CUSTOM,
                        label = label,
                        output = output,
                        weight = weight,
                        id = System.nanoTime()
                    )
                )
                dialog.dialog.dismiss()
                refreshRows()
                refreshPreview()
            }
        )

        body.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        dialog.show()
    }

    // ------------------------------------------------------------------
    // ROWS
    // ------------------------------------------------------------------

    private fun addRow() {
        if (def.rows.size >= 6) {
            toast("Maximum 6 rows")
            return
        }
        def.rows.add(RowDef(mutableListOf(KeyDef(KeyType.CUSTOM, "🙂", "🙂", 1f, id = System.nanoTime()))))
        refreshRows()
        refreshPreview()
    }

    private fun removeRow() {
        if (def.rows.size <= 1) {
            toast("At least one row is required")
            return
        }
        def.rows.removeAt(def.rows.size - 1)
        refreshRows()
        refreshPreview()
    }

    // ------------------------------------------------------------------
    // BACKGROUND IMAGE
    // ------------------------------------------------------------------

    private fun pickBackgroundImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_BG_IMAGE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_BG_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        val bitmap = decodeScaled(uri, 1080)
        if (bitmap == null) {
            toast("Couldn't read that image")
            return
        }
        try {
            val file = File(filesDir, "kb_bg.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            Prefs.setBgImagePath(this, file.absolutePath)
            Prefs.bumpLayoutVersion(this)
            refreshPreview()
            toast("Background image set — it applies instantly")
        } catch (_: Exception) {
            toast("Couldn't save that image")
        }
    }

    private fun removeBackgroundImage() {
        Prefs.setBgImagePath(this, null)
        try {
            File(filesDir, "kb_bg.jpg").delete()
        } catch (_: Exception) {
        }
        Prefs.bumpLayoutVersion(this)
        refreshPreview()
        toast("Background image removed")
    }

    private fun decodeScaled(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim ||
                bounds.outHeight / (sample * 2) >= maxDim
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // SAVE / RESET
    // ------------------------------------------------------------------

    private fun saveLayout() {
        val missing = Layouts.missingEssentials(def)
        if (missing.isNotEmpty()) {
            toast("Your layout is missing: ${missing.joinToString { systemName(it) }}")
            return
        }
        Prefs.setKeyHeightDp(this, keyHeightDp)
        Prefs.setKeyGapDp(this, gapDp)
        Prefs.setKeyboardOpacity(this, opacity)
        Prefs.setExtraBottomDp(this, extraBottomDp)
        Layouts.save(this, def)
        toast("Saved! Switch to CustomKey and start typing to see it.")
    }

    private fun confirmReset() {
        val dialog = Ui.CustomDialog(this, palette, "Reset keyboard?")
        dialog.body.addView(TextView(this).apply {
            text = "This restores the default QWERTY layout, height, spacing and transparency."
            textSize = 15f
            setTextColor(palette.secondary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })
        Ui.button(this, palette, dialog.body, "Reset to default") {
            dialog.dialog.dismiss()
            def = Layouts.defaultLetters()
            keyHeightDp = 48
            gapDp = 3
            opacity = 100
            extraBottomDp = 0
            Prefs.setKeyHeightDp(this, 48)
            Prefs.setKeyGapDp(this, 3)
            Prefs.setKeyboardOpacity(this, 100)
            Prefs.setExtraBottomDp(this, 0)
            Layouts.reset(this)
            exitSelection()
            refreshRows()
            refreshPreview()
            toast("Keyboard reset to default")
        }
        Ui.button(this, palette, dialog.body, "Cancel", filled = false) {
            dialog.dialog.dismiss()
        }
        dialog.show()
    }

    private fun systemName(type: KeyType): String = when (type) {
        KeyType.SHIFT -> "Shift key"
        KeyType.DELETE -> "Backspace key"
        KeyType.SPACE -> "Space key"
        KeyType.ENTER -> "Enter key"
        KeyType.TO_SYMBOLS -> "Symbols (?123) key"
        KeyType.TO_EXTRA -> "More symbols key"
        KeyType.TO_LETTERS -> "Letters (ABC) key"
        KeyType.EMOJI -> "Emoji key"
        else -> "Key"
    }
}
