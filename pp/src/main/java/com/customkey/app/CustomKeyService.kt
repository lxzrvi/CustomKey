package com.customkey.app

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class CustomKeyService : InputMethodService() {

    private var isShift = false

    override fun onCreateInputView(): View {
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))

            val nightMode =
                resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK

            setBackgroundColor(
                if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    Color.rgb(25, 25, 25)
                else
                    Color.rgb(235, 235, 235)
            )
        }

        addLetterRow(keyboard, "qwertyuiop")
        addLetterRow(keyboard, "asdfghjkl")
        addLetterRow(keyboard, "zxcvbnm")

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        bottom.addView(keyButton("⇧", 1f) {
            isShift = !isShift
        })

        bottom.addView(keyButton("Space", 4f) {
            currentInputConnection?.commitText(" ", 1)
        })

        bottom.addView(keyButton("⌫", 1f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })

        bottom.addView(keyButton("↵", 1f) {
            currentInputConnection?.commitText("\n", 1)
        })

        keyboard.addView(bottom)

        return keyboard
    }

    private fun addLetterRow(
        parent: LinearLayout,
        letters: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        letters.forEach { char ->
            row.addView(keyButton(char.toString(), 1f) {
                val text =
                    if (isShift)
                        char.uppercaseChar().toString()
                    else
                        char.toString()

                currentInputConnection?.commitText(text, 1)

                if (isShift) {
                    isShift = false
                }
            })
        }

        parent.addView(row)
    }

    private fun keyButton(
        text: String,
        weight: Float,
        action: () -> Unit
    ): Button {

        return Button(this).apply {
            this.text = text
            textSize = 16f
            isAllCaps = false

            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(52),
                weight
            ).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }

            setOnClickListener {
                action()
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
