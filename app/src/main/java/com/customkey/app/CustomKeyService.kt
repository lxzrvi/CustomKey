package com.customkey.app

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.Button

class CustomKeyService : InputMethodService() {

    override fun onCreateInputView(): View {
        return Button(this).apply {
            text = "CustomKey Test"

            setOnClickListener {
                currentInputConnection?.commitText("CustomKey", 1)
            }
        }
    }
}
