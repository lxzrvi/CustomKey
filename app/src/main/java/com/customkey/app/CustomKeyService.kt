package com.customkey.app

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

class CustomKeyService : InputMethodService() {

    private lateinit var root: LinearLayout

    private var shift = false
    private var symbols = false

    override fun onCreateInputView(): View {

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.DKGRAY)

            // Keep current working behavior
            setPadding(dp(3), dp(5), dp(3), 0)
        }

        showLetters()

        return root
    }

    // -------------------------
    // LETTER KEYBOARD
    // -------------------------

    private fun showLetters() {

        root.removeAllViews()
        symbols = false

        addLetterRow("qwertyuiop")
        addLetterRow("asdfghjkl")

        val thirdRow = createRow()

        thirdRow.addView(
            createKey(
                text = if (shift) "⇧" else "↑",
                weight = 1.4f
            ) {
                shift = !shift
                showLetters()
            }
        )

        "zxcvbnm".forEach { char ->
            thirdRow.addView(
                createKey(char.toString(), 1f) {
                    typeLetter(char)
                }
            )
        }

        thirdRow.addView(
            createKey("⌫", 1.4f) {
                delete()
            }
        )

        root.addView(thirdRow)

        addLetterBottomRow()
    }

    private fun addLetterRow(letters: String) {

        val row = createRow()

        letters.forEach { char ->

            val label =
                if (shift)
                    char.uppercaseChar().toString()
                else
                    char.toString()

            row.addView(
                createKey(label, 1f) {
                    typeLetter(char)
                }
            )
        }

        root.addView(row)
    }

    private fun addLetterBottomRow() {

        val row = createRow()

        row.addView(
            createKey("?123", 1.5f) {
                showSymbols()
            }
        )

        row.addView(
            createKey(",", 1f) {
                typeText(",")
            }
        )

        row.addView(
            createKey("CustomKey", 4f) {
                typeText(" ")
            }
        )

        row.addView(
            createKey(".", 1f) {
                typeText(".")
            }
        )

        row.addView(
            createKey("↵", 1.5f) {
                performEnter()
            }
        )

        root.addView(row)
    }

    // -------------------------
    // SYMBOL KEYBOARD
    // -------------------------

    private fun showSymbols() {

        root.removeAllViews()
        symbols = true

        addSymbolRow("1234567890")
        addSymbolRow("@#₹_%&-+()")

        val thirdRow = createRow()

        val symbolsList = arrayOf(
            "*", "\"", "'", ":", ";", "!",
            "?", "/", "\\"
        )

        symbolsList.forEach {
            thirdRow.addView(
                createKey(it, 1f) {
                    typeText(it)
                }
            )
        }

        thirdRow.addView(
            createKey("⌫", 1.4f) {
                delete()
            }
        )

        root.addView(thirdRow)

        val bottom = createRow()

        bottom.addView(
            createKey("ABC", 1.5f) {
                showLetters()
            }
        )

        bottom.addView(
            createKey(",", 1f) {
                typeText(",")
            }
        )

        bottom.addView(
            createKey("CustomKey", 4f) {
                typeText(" ")
            }
        )

        bottom.addView(
            createKey(".", 1f) {
                typeText(".")
            }
        )

        bottom.addView(
            createKey("↵", 1.5f) {
                performEnter()
            }
        )

        root.addView(bottom)
    }

    private fun addSymbolRow(keys: String) {

        val row = createRow()

        keys.forEach { char ->
            row.addView(
                createKey(char.toString(), 1f) {
                    typeText(char.toString())
                }
            )
        }

        root.addView(row)
    }

    // -------------------------
    // KEY ACTIONS
    // -------------------------

    private fun typeLetter(char: Char) {

        val value =
            if (shift)
                char.uppercaseChar().toString()
            else
                char.toString()

        currentInputConnection?.commitText(value, 1)

        // Shift applies only to one letter
        if (shift) {
            shift = false
            showLetters()
        }
    }

    private fun typeText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun delete() {
        currentInputConnection
            ?.deleteSurroundingText(1, 0)
    }

    private fun performEnter() {

        val info = currentInputEditorInfo

        when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {

            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND -> {

                currentInputConnection
                    ?.performEditorAction(
                        info.imeOptions and
                            EditorInfo.IME_MASK_ACTION
                    )
            }

            else -> {
                currentInputConnection
                    ?.commitText("\n", 1)
            }
        }
    }

    // -------------------------
    // UI
    // -------------------------

    private fun createRow(): LinearLayout {

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
    }

    private fun createKey(
        text: String,
        weight: Float,
        action: () -> Unit
    ): Button {

        return Button(this).apply {

            this.text = text
            textSize = 15f
            isAllCaps = false

            setPadding(0, 0, 0, 0)

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    dp(52),
                    weight
                ).apply {
                    setMargins(
                        dp(2),
                        dp(2),
                        dp(2),
                        dp(2)
                    )
                }

            setOnClickListener {
                action()
            }
        }
    }

    private fun dp(value: Int): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
