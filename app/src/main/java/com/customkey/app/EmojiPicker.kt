package com.customkey.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Full Android emoji picker (categories + recents) used by the Keyboard Editor. */
class EmojiPicker(private val context: Context, private val palette: Ui.Palette) {

    fun show(onPick: (String) -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                Ui.dp(context, 10),
                Ui.dp(context, 16),
                Ui.dp(context, 10),
                Ui.dp(context, 16)
            )
            background = Ui.rounded(context, palette.card, 24)
        }

        content.addView(TextView(context).apply {
            text = "Emoji"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, Ui.dp(context, 10))
        })

        val tabsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val grid = GridView(context).apply {
            numColumns = 8
            verticalSpacing = Ui.dp(context, 4)
            horizontalSpacing = Ui.dp(context, 2)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }

        val recents = Prefs.recentEmojis(context)
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

        labels.forEachIndexed { index, label ->
            tabs.addView(TextView(context).apply {
                text = label
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(
                    Ui.dp(context, 10), Ui.dp(context, 6),
                    Ui.dp(context, 10), Ui.dp(context, 6)
                )
                setOnClickListener { grid.adapter = EmojiAdapter(categories[index]) }
            })
        }
        tabsScroll.addView(tabs)
        content.addView(tabsScroll)

        content.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(context, 330)
            )
        )

        grid.setOnItemClickListener { _, _, position, _ ->
            val emoji = grid.adapter.getItem(position) as String
            Prefs.pushRecentEmoji(context, emoji)
            onPick(emoji)
            dialog.dismiss()
        }

        val wrapper = FrameLayout(context).apply {
            setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0)
        }
        wrapper.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        dialog.setContentView(wrapper)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            context.resources.displayMetrics.widthPixels - Ui.dp(context, 40),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        grid.adapter = EmojiAdapter(categories[0])
    }

    private class EmojiAdapter(private val items: List<String>) : BaseAdapter() {

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(parent.context).apply {
                textSize = 26f
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(parent.context, 4), 0, Ui.dp(parent.context, 4))
            }
            tv.text = items[position]
            return tv
        }
    }
}
