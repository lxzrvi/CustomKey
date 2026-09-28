package com.customkey.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.res.Configuration

class MainActivity : Activity() {

    private lateinit var setupBox: LinearLayout

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val bgColor: Int
        get() = if (isDark) Color.rgb(18, 18, 18) else Color.rgb(250, 250, 250)

    private val cardColor: Int
        get() = if (isDark) Color.rgb(35, 35, 35) else Color.rgb(238, 238, 238)

    private val textColor: Int
        get() = if (isDark) Color.WHITE else Color.rgb(20, 20, 20)

    private val secondaryColor: Int
        get() = if (isDark) Color.LTGRAY else Color.DKGRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createScreen()
    }

    override fun onResume() {
        super.onResume()

        if (::setupBox.isInitialized) {
            refreshSetup()
        }
    }

    private fun createScreen() {

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(30))
        }

        // APP HEADER

        val logo = ImageView(this).apply {
            setImageResource(com.customkey.app.R.drawable.ck_icon)
            adjustViewBounds = true
        }

        root.addView(
            logo,
            LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        root.addView(TextView(this).apply {
            text = "CustomKey"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(0, dp(8), 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "Your keyboard, your way."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(secondaryColor)
            setPadding(0, 0, 0, dp(28))
        })

        // SETUP

        setupBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(setupBox)

        // TEST KEYBOARD

        addHeading(root, "Test keyboard")

        val testInput = EditText(this).apply {
            hint = "Tap here and start typing..."
            textSize = 17f
            setTextColor(textColor)
            setHintTextColor(secondaryColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setSingleLine(false)
            minLines = 3
            gravity = Gravity.TOP
            setBackgroundColor(cardColor)
        }

        root.addView(
            testInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // EDITOR

        addHeading(root, "Keyboard")

        addButton(root, "Edit Keyboard") {
            Toast.makeText(
                this,
                "Keyboard editor coming next",
                Toast.LENGTH_SHORT
            ).show()
        }

        // SETTINGS

        addHeading(root, "Settings")

        addSwitch(root, "Key sound", false)
        addSwitch(root, "Vibration", true)

        // ABOUT

        addHeading(root, "CustomKey")

        addButton(root, "Share app") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Try CustomKey — my custom Android keyboard."
                )
            }

            startActivity(
                Intent.createChooser(intent, "Share CustomKey")
            )
        }

        addButton(root, "Feedback") {

            val mail = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse(
                    "mailto:thaparavi382@gmail.com" +
                    "?subject=" +
                    Uri.encode("CustomKey Feedback")
                )
            )

            try {
                startActivity(mail)
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "No email app found",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // FOOTER

        root.addView(TextView(this).apply {
            text = "CustomKey\nlxzrvi developer"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(secondaryColor)
            setPadding(0, dp(35), 0, dp(10))
        })

        scroll.addView(root)
        setContentView(scroll)

        refreshSetup()
    }

    private fun refreshSetup() {

        setupBox.removeAllViews()

        /*
         * Actual keyboard service next step mein register hoga.
         * Filhaal Android keyboard settings open hoti hain.
         */

        addHeading(setupBox, "Setup")

        addButton(setupBox, "Enable CustomKey") {
            startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            )
        }

        addButton(setupBox, "Select CustomKey") {

            val imm =
                getSystemService(INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager

            imm.showInputMethodPicker()
        }
    }

    private fun addHeading(
        parent: LinearLayout,
        title: String
    ) {

        parent.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(secondaryColor)
            setPadding(0, dp(25), 0, dp(9))
        })
    }

    private fun addButton(
        parent: LinearLayout,
        title: String,
        action: () -> Unit
    ) {

        val button = Button(this).apply {
            text = title
            textSize = 16f
            isAllCaps = false
            setOnClickListener { action() }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            ).apply {
                bottomMargin = dp(8)
            }
        )
    }

    private fun addSwitch(
        parent: LinearLayout,
        title: String,
        enabled: Boolean
    ) {

        val item = Switch(this).apply {
            text = title
            textSize = 16f
            isChecked = enabled
            setTextColor(textColor)
            setPadding(dp(8), 0, dp(8), 0)
        }

        parent.addView(
            item,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
