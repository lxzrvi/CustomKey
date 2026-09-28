package com.customkey.app

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout

class CustomKeyService : InputMethodService() {

    override fun onCreateInputView(): View {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.DKGRAY)
        }

        val button = Button(this).apply {
            text = "CustomKey Test"

            setOnClickListener {
                currentInputConnection?.commitText("CustomKey", 1)
            }
        }

        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(60)
            )
        )

        root.setOnApplyWindowInsetsListener { view, insets ->

            val bottomInset =
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(
                        WindowInsets.Type.navigationBars()
                    ).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }

            view.setPadding(
                0,
                0,
                0,
                bottomInset
            )

            insets
        }

        return root
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
