package com.customkey.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The Keyboard Editor — edit the letters layout of CustomKey:
 * add/edit/remove custom keys, change key width, add/remove rows,
 * adjust keyboard height & key spacing, save or reset to default.
 */
class EditorActivity : Activity() {

    private lateinit var palette: Ui.Palette
    private lateinit var rowsBox: LinearLayout

    private lateinit var def: KeyboardDef
    private var heightPercent = 100
    private var gapDp = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        def = Layouts.current(this)
        heightPercent = (Prefs.heightFactor(this) * 100f).toInt().coerceIn(80, 130)
        gapDp = Prefs.keyGapDp(this).coerceIn(2, 8)
        createScreen()
    }

    private fun dp(value: Int): Int = Ui.dp(this, value)

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------
    // SCREEN
    // ------------------------------------------------------------------

    private fun createScreen() {

        val scroll = ScrollView(this).apply {
            setBackgroundColor(palette.bg)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(30))
        }

        // ---------- header ----------

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(palette.text)
            setPadding(dp(6), dp(8), dp(14), dp(8))
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = "Keyboard Editor"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "Customize your CustomKey layout — add keys, change sizes, save and test."
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, dp(6), 0, dp(2))
        })

        // ---------- live test ----------

        Ui.heading(this, palette, root, "TEST")

        val testCard = Ui.addCard(this, palette, root)
        testCard.addView(TextView(this).apply {
            text = "Save your changes, then tap below and switch to CustomKey to try them."
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        testCard.addView(EditText(this).apply {
            hint = "Try your keyboard here…"
            textSize = 16f
            setTextColor(palette.text)
            setHintTextColor(palette.secondary)
            background = Ui.rounded(this@EditorActivity, palette.inputBg, 12, palette.stroke)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = false
            minLines = 2
            gravity = Gravity.TOP
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // ---------- layout rows ----------

        Ui.heading(this, palette, root, "LAYOUT")

        val layoutCard = Ui.addCard(this, palette, root)
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

        // ---------- appearance ----------

        Ui.heading(this, palette, root, "APPEARANCE")

        val appearanceCard = Ui.addCard(this, palette, root)
        Ui.seekRow(
            this, palette, appearanceCard,
            "Keyboard height", 80, 130, heightPercent, true,
            { "$it%" }
        ) { heightPercent = it }

        Ui.seekRow(
            this, palette, appearanceCard,
            "Key spacing", 2, 8, gapDp, true,
            { "${it}dp" }
        ) { gapDp = it }

        // ---------- actions ----------

        Ui.heading(this, palette, root, "ACTIONS")

        Ui.button(this, palette, root, "💾  Save keyboard") { saveLayout() }
        Ui.button(this, palette, root, "Reset to default", filled = false) { confirmReset() }

        scroll.addView(root)
        setContentView(scroll)

        refreshRows()
    }

    // ------------------------------------------------------------------
    // ROWS / CHIPS
    // ------------------------------------------------------------------

    private fun refreshRows() {
        if (!::rowsBox.isInitialized) return
        rowsBox.removeAllViews()

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
    }

    private fun chipColor(keyDef: KeyDef): Int = when (keyDef.type) {
        KeyType.LETTER -> palette.inputBg
        KeyType.CUSTOM -> palette.chipCustom
        else -> palette.chipSystem
    }

    private fun keyChip(keyDef: KeyDef, rowIndex: Int, keyIndex: Int): TextView {
        return TextView(this).apply {
            text = chipLabel(keyDef)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            background = Ui.rounded(this@EditorActivity, chipColor(keyDef), 10)
            minWidth = dp(52)
            minHeight = dp(44)
            setPadding(dp(12), 0, dp(12), 0)
            contentDescription = "Edit key ${chipLabel(keyDef)}"
            setOnClickListener { editKeyDialog(rowIndex, keyIndex) }
        }
    }

    private fun addChip(rowIndex: Int): TextView {
        return TextView(this).apply {
            text = "＋"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.accent)
            background = Ui.rounded(this@EditorActivity, Color.TRANSPARENT, 10, palette.stroke)
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

    private fun addDialogButton(
        row: LinearLayout,
        button: Button
    ) {
        row.addView(
            button,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) }
        )
    }

    // ------------------------------------------------------------------
    // EDIT / ADD KEY DIALOGS
    // ------------------------------------------------------------------

    private fun editKeyDialog(rowIndex: Int, keyIndex: Int) {
        val keyDef = def.rows[rowIndex].keys[keyIndex]
        val system = keyDef.type != KeyType.LETTER && keyDef.type != KeyType.CUSTOM

        val dialog = Ui.CustomDialog(
            this, palette,
            if (system) systemName(keyDef.type) else "Edit key"
        )
        val body = dialog.body

        var letterField: EditText? = null
        var labelField: EditText? = null
        var outputField: EditText? = null

        if (keyDef.type == KeyType.LETTER) {
            body.addView(fieldLabel("Letter"))
            letterField = Ui.editText(this, palette, "a").apply { setText(keyDef.label) }
            body.addView(letterField)
        } else if (keyDef.type == KeyType.CUSTOM) {
            body.addView(fieldLabel("Key label (shown on key)"))
            labelField = Ui.editText(this, palette, "email").apply { setText(keyDef.label) }
            body.addView(labelField)
            body.addView(fieldLabel("Text it types"))
            outputField = Ui.editText(this, palette, "you@example.com").apply { setText(keyDef.output) }
            body.addView(outputField)
        } else {
            body.addView(TextView(this).apply {
                text = "${systemName(keyDef.type)} — a system key. " +
                        "You can change its width, but it can't be edited or removed."
                textSize = 14f
                setTextColor(palette.secondary)
                setPadding(0, 0, 0, dp(4))
            })
        }

        var weight = keyDef.weight
        Ui.seekRow(
            this, palette, body,
            "Width", 5, 30,
            (keyDef.weight * 10).toInt().coerceIn(5, 30), true,
            { String.format("%.1f×", it / 10f) }
        ) { weight = it / 10f }

        val buttons = buttonRow()

        if (!system) {
            addDialogButton(
                buttons,
                dialogButton("Delete", palette.danger, Color.TRANSPARENT, palette.danger) {
                    def.rows[rowIndex].keys.removeAt(keyIndex)
                    dialog.dialog.dismiss()
                    refreshRows()
                }
            )
        }

        addDialogButton(
            buttons,
            dialogButton("Cancel", palette.text, palette.card, palette.stroke) {
                dialog.dialog.dismiss()
            }
        )

        addDialogButton(
            buttons,
            dialogButton("Save", palette.accentText, palette.accent, null) {
                when (keyDef.type) {
                    KeyType.LETTER -> {
                        val ch = letterField?.text?.toString()?.trim() ?: ""
                        if (ch.length != 1) {
                            toast("Enter a single letter")
                            return@dialogButton
                        }
                        def.rows[rowIndex].keys[keyIndex] =
                            KeyDef(KeyType.LETTER, ch, ch, weight)
                    }
                    KeyType.CUSTOM -> {
                        val output = outputField?.text?.toString() ?: ""
                        if (output.isEmpty()) {
                            toast("Enter the text this key should type")
                            return@dialogButton
                        }
                        val label = labelField?.text?.toString()?.trim()
                            ?.ifEmpty { output.take(6) }
                            ?: output.take(6)
                        def.rows[rowIndex].keys[keyIndex] =
                            KeyDef(KeyType.CUSTOM, label, output, weight)
                    }
                    else -> {
                        def.rows[rowIndex].keys[keyIndex] =
                            KeyDef(keyDef.type, keyDef.label, keyDef.output, weight)
                    }
                }
                dialog.dialog.dismiss()
                refreshRows()
            }
        )

        body.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        dialog.show()
    }

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

        var weight = 1f
        Ui.seekRow(
            this, palette, body,
            "Width", 5, 30, 10, true,
            { String.format("%.1f×", it / 10f) }
        ) { weight = it / 10f }

        val buttons = buttonRow()

        addDialogButton(
            buttons,
            dialogButton("Cancel", palette.text, palette.card, palette.stroke) {
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
                def.rows[rowIndex].keys.add(KeyDef(KeyType.CUSTOM, label, output, weight))
                dialog.dialog.dismiss()
                refreshRows()
            }
        )

        body.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
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
        def.rows.add(RowDef(mutableListOf(KeyDef(KeyType.CUSTOM, "🙂", "🙂"))))
        refreshRows()
    }

    private fun removeRow() {
        if (def.rows.size <= 1) {
            toast("At least one row is required")
            return
        }
        def.rows.removeAt(def.rows.size - 1)
        refreshRows()
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
        Prefs.setHeightFactor(this, heightPercent / 100f)
        Prefs.setKeyGapDp(this, gapDp)
        Layouts.save(this, def)
        toast("Saved! Switch to CustomKey and start typing to see it.")
    }

    private fun confirmReset() {
        val dialog = Ui.CustomDialog(this, palette, "Reset keyboard?")
        dialog.body.addView(TextView(this).apply {
            text = "This restores the default QWERTY layout, height and spacing."
            textSize = 15f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(4))
        })
        Ui.button(this, palette, dialog.body, "Reset to default") {
            dialog.dialog.dismiss()
            def = Layouts.defaultLetters()
            heightPercent = 100
            gapDp = 3
            Prefs.setHeightFactor(this, 1.0f)
            Prefs.setKeyGapDp(this, 3)
            Layouts.reset(this)
            refreshRows()
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
        else -> "Key"
    }
}
