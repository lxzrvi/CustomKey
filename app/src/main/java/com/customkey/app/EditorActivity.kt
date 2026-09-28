package com.customkey.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Keyboard Editor.
 *
 * Top: live preview — tap a key to edit it, long-press & drag to move it.
 * Sections: Appearance (first) → Keys (layout, trash) → Sound & Vibration → Typing.
 * Bottom bar (always visible): Save · user presets · Reset.
 */
class EditorActivity : Activity(), KeyView.Listener {

    private lateinit var palette: Ui.Palette

    // Working layout (persisted only when the user taps Save)
    private lateinit var def: KeyboardDef
    private var nextKeyId = 1L

    // Preview
    private lateinit var previewHolder: FrameLayout
    private lateinit var preview: LinearLayout
    private var previewKeyViews = ArrayList<KeyView>()

    // Batch selection
    private var selectMode = false
    private val selectedIds = HashSet<Long>()
    private var batchBar: LinearLayout? = null
    private var batchCountView: TextView? = null

    // Drag-move
    private var dragGhost: TextView? = null
    private var dragKey: KeyDef? = null
    private var dragLp: FrameLayout.LayoutParams? = null
    private var contentOrigin = IntArray(2)

    // Trash
    private data class TrashItem(val key: KeyDef, val row: Int, val col: Int)
    private val trash = ArrayList<TrashItem>()
    private var trashStrip: LinearLayout? = null

    // Bottom bar
    private lateinit var presetsRow: LinearLayout

    // Sound preview
    private var sounds: KeySounds? = null

    private val swatchColors = listOf<Int?>(
        null, 0xFF8E8E93.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C1C1E.toInt(),
        0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
        0xFF00C7BE.toInt(), 0xFF0A84FF.toInt(), 0xFF5E5CE6.toInt(), 0xFFAF52DE.toInt(),
        0xFFFF2D55.toInt(), 0xFFA2845E.toInt()
    )

    // ------------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        def = Layouts.current(this)
        loadTrash()
        createScreen()
    }

    override fun onDestroy() {
        sounds?.release()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (dragGhost != null) {
            cancelDrag()
            return
        }
        if (selectMode) {
            exitSelectMode()
            return
        }
        super.onBackPressed()
    }

    private fun dp(value: Int): Int = Ui.dp(this, value)

    // ------------------------------------------------------------------
    // SCREEN
    // ------------------------------------------------------------------

    private fun createScreen() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }
        Ui.applySystemBarsPadding(rootLayout)

        // ---- top hint ----
        rootLayout.addView(TextView(this).apply {
            text = "Tap any key in the preview to edit it · hold & drag to move"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(palette.secondary)
            setPadding(dp(10), dp(10), dp(10), dp(4))
        })

        // ---- live preview (fixed under the hint) ----
        previewHolder = FrameLayout(this)
        val previewScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(4), dp(3), dp(4))
        }
        previewScroll.addView(preview)
        previewHolder.addView(
            previewScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(
            previewHolder,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ---- batch bar (shown in select mode) ----
        buildBatchBar(rootLayout)

        // ---- scrollable sections ----
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val sections = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(10))
        }

        buildAppearanceSection(sections)
        buildKeysSection(sections)
        buildFeedbackSection(sections)
        buildBehaviourSection(sections)

        scroll.addView(
            sections,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        // ---- bottom bar: presets · Save | Reset ----
        buildBottomBar(rootLayout)

        setContentView(rootLayout)
        rebuildPreview()
    }

    // ------------------------------------------------------------------
    // PREVIEW
    // ------------------------------------------------------------------

    private fun rebuildPreview() {
        preview.removeAllViews()
        previewKeyViews.clear()

        val theme = KeyboardTheme.palette(this)
        val style = theme.style
        val heightPx = dp(Prefs.keyHeightDp(this).coerceIn(40, 62))
        val gapPx = dp(Prefs.keyGapDp(this).coerceIn(2, 8))
        val halfGap = (gapPx / 2f).toInt()

        val bg = KeyboardTheme.backgroundDrawable(this)
        if (bg != null) previewHolder.background = bg
        else previewHolder.setBackgroundColor(theme.bg)

        def.rows.forEach { rowDef ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowDef.keys.forEach { key ->
                val view = KeyView(this, key, heightPx)
                view.listener = this
                val special = key.type != KeyType.LETTER && key.type != KeyType.CUSTOM
                val keyBg = key.color ?: if (special) style.specialBg else style.keyBg
                val selected = selectMode && selectedIds.contains(key.id)
                view.setColors(
                    bg = if (selected) 0xFF0A84FF.toInt() else theme.withAlpha(keyBg),
                    label = if (selected) 0xFFFFFFFF.toInt() else style.textColor,
                    sizeSp = style.textPx,
                    radiusPx = style.radiusPx,
                    hintColor = theme.withAlpha(style.textColor)
                )
                if (selected) view.active = true
                view.setHintText(if (Prefs.showLongPressHints(this)) key.alternates.take(1) else "")
                when (key.type) {
                    KeyType.EMOJI -> view.setIcon(getDrawable(R.drawable.ic_emoji)?.mutate())
                    KeyType.TO_CURSOR -> view.setIcon(getDrawable(R.drawable.ic_cursor)?.mutate())
                    else -> Unit
                }
                val lp = LinearLayout.LayoutParams(0, heightPx, key.weight)
                lp.setMargins(halfGap, halfGap, halfGap, halfGap)
                row.addView(view, lp)
                previewKeyViews.add(view)
            }
            preview.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Scale down very tall previews so the sections stay reachable.
        preview.post {
            val maxH = (resources.displayMetrics.heightPixels * 0.42f).toInt()
            if (preview.height > maxH && preview.height > 0) {
                val scale = maxH.toFloat() / preview.height
                preview.pivotX = 0f
                preview.pivotY = 0f
                preview.scaleX = scale
                preview.scaleY = scale
                previewHolder.layoutParams.height = maxH
                previewHolder.requestLayout()
            } else {
                preview.scaleX = 1f
                preview.scaleY = 1f
                previewHolder.layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                previewHolder.requestLayout()
            }
        }
    }

    // ------------------------------------------------------------------
    // KEY VIEW LISTENER (preview)
    // ------------------------------------------------------------------

    override fun onKeyTap(view: KeyView, key: KeyDef) {
        if (dragGhost != null) return
        if (selectMode) {
            if (selectedIds.contains(key.id)) selectedIds.remove(key.id)
            else selectedIds.add(key.id)
            updateBatchBar()
            rebuildPreview()
            return
        }
        showKeyDialog(key)
    }

    override fun onKeyLongPress(view: KeyView, key: KeyDef) {
        if (selectMode) return
        startDrag(view, key)
    }

    override fun onKeyRepeat(view: KeyView, key: KeyDef) {}

    override fun onKeyTouchDown(view: KeyView, key: KeyDef) {}

    override fun onKeySlide(view: KeyView, key: KeyDef, rawX: Float, rawY: Float) {
        val ghost = dragGhost ?: return
        dragLp?.let {
            it.leftMargin = (rawX - contentOrigin[0] - (ghost.width / 2f)).toInt()
            it.topMargin = (rawY - contentOrigin[1] - (ghost.height / 2f)).toInt()
            ghost.layoutParams = it
        }
    }

    override fun onKeyTouchUp(view: KeyView, key: KeyDef) {
        if (dragGhost != null && dragLp != null) {
            val lp = dragLp!!
            val cx = lp.leftMargin + (dragGhost?.width ?: 0) / 2f
            val cy = lp.topMargin + (dragGhost?.height ?: 0) / 2f
            dropDrag(cx + contentOrigin[0], cy + contentOrigin[1])
        }
    }

    // ------------------------------------------------------------------
    // DRAG-MOVE (ghost over the activity content)
    // ------------------------------------------------------------------

    private fun startDrag(source: KeyView, key: KeyDef) {
        if (dragGhost != null) return
        val contentView = findViewById<FrameLayout>(android.R.id.content) ?: return
        contentView.getLocationOnScreen(contentOrigin)

        val loc = IntArray(2)
        source.getLocationOnScreen(loc)

        dragKey = key
        dragGhost = TextView(this).apply {
            text = key.label
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            background = Ui.rounded(this@EditorActivity, 0xFF0A84FF.toInt(), 10)
            elevation = dp(20).toFloat()
        }
        dragLp = FrameLayout.LayoutParams(source.width, source.height).apply {
            leftMargin = loc[0] - contentOrigin[0]
            topMargin = loc[1] - contentOrigin[1]
        }
        contentView.addView(dragGhost, dragLp)

        Toast.makeText(this, "Drop on a key to move · drop on Trash to delete", Toast.LENGTH_SHORT).show()
    }

    private fun dropDrag(rawX: Float, rawY: Float) {
        val key = dragKey
        cancelDragGhost()
        dragKey = null
        if (key == null) return

        // Dropped on the trash strip?
        val strip = trashStrip
        if (strip != null) {
            val loc = IntArray(2)
            strip.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX <= loc[0] + strip.width &&
                rawY >= loc[1] && rawY <= loc[1] + strip.height
            ) {
                moveToTrash(key)
                rebuildPreview()
                return
            }
        }

        val (srcRow, srcIdx) = positionOf(key) ?: return
        val target = findKeyViewAt(rawX, rawY)

        if (target != null) {
            val targetKey = target.key()
            val (dstRow, dstIdx) = positionOf(targetKey) ?: return
            if (dstRow == srcRow && dstIdx == srcIdx) return

            def.rows[srcRow].keys.removeAt(srcIdx)

            var insertAt = dstIdx
            if (dstRow == srcRow && dstIdx > srcIdx) insertAt = dstIdx - 1
            val targetLoc = IntArray(2)
            target.getLocationOnScreen(targetLoc)
            if (rawX > targetLoc[0] + target.width / 2f) insertAt += 1
            def.rows[dstRow].keys.add(insertAt.coerceIn(0, def.rows[dstRow].keys.size), key)
        } else {
            // No target: move to the end of the last row.
            def.rows[srcRow].keys.removeAt(srcIdx)
            def.rows.last().keys.add(key)
        }
        rebuildPreview()
    }

    private fun cancelDrag() {
        cancelDragGhost()
        dragKey = null
    }

    private fun cancelDragGhost() {
        dragGhost?.let { g -> (g.parent as? android.view.ViewGroup)?.removeView(g) }
        dragGhost = null
        dragLp = null
    }

    private fun findKeyViewAt(rawX: Float, rawY: Float): KeyView? {
        val loc = IntArray(2)
        for (view in previewKeyViews) {
            view.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX <= loc[0] + view.width &&
                rawY >= loc[1] && rawY <= loc[1] + view.height
            ) return view
        }
        return null
    }

    private fun positionOf(key: KeyDef): Pair<Int, Int>? {
        def.rows.forEachIndexed { r, row ->
            row.keys.forEachIndexed { i, k ->
                if (k.id == key.id) return r to i
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // TRASH
    // ------------------------------------------------------------------

    private fun moveToTrash(key: KeyDef) {
        val pos = positionOf(key) ?: return
        def.rows[pos.first].keys.removeAt(pos.second)
        trash.add(0, TrashItem(key, pos.first, pos.second))
        if (trash.size > 30) trash.removeAt(trash.size - 1)
        saveTrash()
        rebuildTrashStrip()
        Toast.makeText(this, "\"${key.label}\" moved to Trash", Toast.LENGTH_SHORT).show()
    }

    private fun restoreFromTrash(item: TrashItem) {
        trash.remove(item)
        saveTrash()
        val rowIdx = item.row.coerceIn(0, def.rows.size - 1)
        def.rows[rowIdx].keys.add(item.col.coerceIn(0, def.rows[rowIdx].keys.size), item.key)
        rebuildTrashStrip()
        rebuildPreview()
        Toast.makeText(this, "\"${item.key.label}\" restored", Toast.LENGTH_SHORT).show()
    }

    private fun saveTrash() {
        try {
            val arr = JSONArray()
            trash.forEach { t ->
                arr.put(keyToJson(t.key).put("row", t.row).put("col", t.col))
            }
            Prefs.setDeletedKeysJson(this, arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun loadTrash() {
        trash.clear()
        try {
            val arr = JSONArray(Prefs.deletedKeysJson(this))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                trash.add(TrashItem(keyFromJson(o), o.optInt("row", 0), o.optInt("col", 0)))
            }
        } catch (_: Exception) {
        }
    }

    /** Serialize a single key by wrapping it in a one-key keyboard def. */
    private fun keyToJson(key: KeyDef): JSONObject {
        val json = Layouts.toJson(KeyboardDef(mutableListOf(RowDef(mutableListOf(key)))))
        return JSONObject(json).getJSONArray("rows")
            .getJSONObject(0).getJSONArray("keys").getJSONObject(0)
    }

    private fun keyFromJson(o: JSONObject): KeyDef {
        val wrapped = JSONObject()
            .put("rows", JSONArray().put(JSONObject().put("keys", JSONArray().put(o))))
        return Layouts.fromJson(wrapped.toString())?.rows?.firstOrNull()?.keys?.firstOrNull()
            ?: KeyDef(KeyType.CUSTOM, o.optString("label"), o.optString("output"))
    }

    // ------------------------------------------------------------------
    // SECTIONS
    // ------------------------------------------------------------------

    // ---------------- appearance (FIRST) ----------------

    private fun buildAppearanceSection(parent: LinearLayout) {
        Ui.heading(this, palette, parent, "APPEARANCE")
        val card = Ui.addCard(this, palette, parent)

        Ui.seekRow(this, palette, card, "Key height", 40, 62,
            Prefs.keyHeightDp(this), true, { "$it dp" }) {
            Prefs.setKeyHeightDp(this, it)
            rebuildPreview()
        }

        Ui.seekRow(this, palette, card, "Key gap", 2, 8,
            Prefs.keyGapDp(this), true, { "$it dp" }) {
            Prefs.setKeyGapDp(this, it)
            rebuildPreview()
        }

        Ui.seekRow(this, palette, card, "Keyboard transparency", 30, 100,
            Prefs.keyboardOpacity(this), true, { "$it%" }) {
            Prefs.setKeyboardOpacity(this, it)
            rebuildPreview()
        }

        Ui.seekRow(this, palette, card, "Background blur", 0, 25,
            Prefs.bgBlur(this), Prefs.bgImagePath(this) != null, { "$it" }) {
            Prefs.setBgBlur(this, it)
            rebuildPreview()
        }

        val bgRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        bgRow.addView(
            Ui.compactButton(this, palette, "Choose image") { pickImage() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        bgRow.addView(
            Ui.compactButton(this, palette, "Remove", danger = true) {
                Prefs.setBgImagePath(this, null)
                Prefs.setBgBlur(this, 0)
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(bgRow)

        val fontRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        fontRow.addView(
            Ui.compactButton(this, palette, "Custom font (.ttf)") { pickFont() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        fontRow.addView(
            Ui.compactButton(this, palette, "Reset font", danger = true) {
                Prefs.setCustomFontPath(this, null)
                rebuildPreview()
                Toast.makeText(this, "Default font restored", Toast.LENGTH_SHORT).show()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(fontRow)

        Ui.seekRow(this, palette, card, "Extra bottom padding", 0, 20,
            Prefs.extraBottomDp(this), true, { "$it dp" }) {
            Prefs.setExtraBottomDp(this, it)
        }
    }

    // ---------------- keys / layout ----------------

    private fun buildKeysSection(parent: LinearLayout) {
        Ui.heading(this, palette, parent, "KEYS")
        val card = Ui.addCard(this, palette, parent)

        card.addView(TextView(this).apply {
            text = "Add keys from any key's editor — \"＋ Add new key\"."
            textSize = 13f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(6))
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
        }
        row.addView(
            Ui.compactButton(this, palette, "Select keys") {
                if (selectMode) exitSelectMode() else enterSelectMode()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        row.addView(
            Ui.compactButton(this, palette, "+ Row") {
                def.rows.add(RowDef(mutableListOf(newKey("a"))))
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        row.addView(
            Ui.compactButton(this, palette, "− Row", danger = true) {
                if (def.rows.size <= 1) {
                    Toast.makeText(this, "At least one row is required", Toast.LENGTH_SHORT).show()
                    return@compactButton
                }
                val last = def.rows.removeAt(def.rows.size - 1)
                last.keys.forEach { moveToTrashWithoutRemove(it, def.rows.size, 0) }
                saveTrash()
                rebuildTrashStrip()
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(row)

        // trash strip
        card.addView(TextView(this).apply {
            text = "Trash — tap to restore a deleted key"
            textSize = 13f
            setTextColor(palette.secondary)
            setPadding(0, dp(8), 0, dp(4))
        })
        val trashScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        trashStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        trashScroll.addView(trashStrip)
        card.addView(trashScroll)
        rebuildTrashStrip()
    }

    private fun moveToTrashWithoutRemove(key: KeyDef, row: Int, col: Int) {
        trash.add(0, TrashItem(key, row, col))
        if (trash.size > 30) trash.removeAt(trash.size - 1)
    }

    private fun rebuildTrashStrip() {
        val strip = trashStrip ?: return
        strip.removeAllViews()
        if (trash.isEmpty()) {
            strip.addView(TextView(this).apply {
                text = "Nothing here yet"
                textSize = 13f
                setTextColor(palette.secondary)
                setPadding(0, dp(6), 0, dp(6))
            })
            return
        }
        trash.forEach { item ->
            strip.addView(
                Ui.compactButton(this, palette, item.key.label.ifEmpty { "•" }) {
                    restoreFromTrash(item)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }
        strip.addView(
            Ui.compactButton(this, palette, "Empty", danger = true) {
                trash.clear()
                saveTrash()
                rebuildTrashStrip()
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    // ---------------- feedback ----------------

    private fun buildFeedbackSection(parent: LinearLayout) {
        Ui.heading(this, palette, parent, "SOUND & VIBRATION")
        val card = Ui.addCard(this, palette, parent)

        Ui.switchRow(this, palette, card, "Key sounds", Prefs.soundEnabled(this)) {
            Prefs.setSoundEnabled(this, it)
        }

        card.addView(TextView(this).apply {
            text = "Sound style — tap to hear a sample"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })

        val stylesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val stylesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stylesScroll.addView(stylesRow)
        card.addView(stylesScroll)

        val customPitchHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val customDurationHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun restyleStyleChips() {
            stylesRow.removeAllViews()
            val current = Prefs.soundStyle(this@EditorActivity)
            KeySounds.NAMES.forEachIndexed { index, name ->
                val selected = current == index
                val chip = Ui.compactButton(this, palette, name) {
                    Prefs.setSoundStyle(this, index)
                    if (Prefs.soundEnabled(this)) sampleSound(index)
                    restyleStyleChips()
                }
                if (selected) {
                    chip.background = Ui.rounded(this@EditorActivity, palette.accent, 14)
                    chip.setTextColor(palette.accentText)
                }
                stylesRow.addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                )
            }
            // "+" custom sound slot (icon chip)
            val customSelected = current == KeySounds.STYLE_CUSTOM
            val addChip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = Ui.rounded(
                    this@EditorActivity,
                    if (customSelected) palette.accent else palette.tinted, 14
                )
                setOnClickListener {
                    Prefs.setSoundStyle(this@EditorActivity, KeySounds.STYLE_CUSTOM)
                    if (Prefs.soundEnabled(this@EditorActivity)) sampleSound(KeySounds.STYLE_CUSTOM)
                    restyleStyleChips()
                }
                val img = ImageView(this@EditorActivity).apply {
                    setImageResource(R.drawable.ic_add)
                    drawable?.setTint(
                        if (customSelected) palette.accentText else palette.text
                    )
                }
                addView(img, LinearLayout.LayoutParams(dp(16), dp(16)))
            }
            stylesRow.addView(
                addChip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val isCustom = current == KeySounds.STYLE_CUSTOM
            customPitchHolder.visibility = if (isCustom) View.VISIBLE else View.GONE
            customDurationHolder.visibility = if (isCustom) View.VISIBLE else View.GONE
        }

        Ui.seekRow(this, palette, customPitchHolder, "Custom pitch", 60, 160,
            Prefs.customSoundPitch(this), true, { "$it%" }) {
            Prefs.setCustomSoundPitch(this, it)
            refreshCustomSound()
        }
        Ui.seekRow(this, palette, customDurationHolder, "Custom length", 15, 90,
            Prefs.customSoundDuration(this), true, { "$it ms" }) {
            Prefs.setCustomSoundDuration(this, it)
            refreshCustomSound()
        }
        card.addView(customPitchHolder)
        card.addView(customDurationHolder)

        restyleStyleChips()

        Ui.seekRow(this, palette, card, "Sound volume", 0, 100,
            Prefs.soundVolume(this), true, { "$it%" }) {
            Prefs.setSoundVolume(this, it)
        }

        Ui.switchRow(this, palette, card, "Vibration", Prefs.vibrationEnabled(this)) {
            Prefs.setVibrationEnabled(this, it)
        }

        Ui.seekRow(this, palette, card, "Vibration strength", 5, 100,
            Prefs.vibrationStrength(this), true, { "$it%" }) {
            Prefs.setVibrationStrength(this, it)
        }
    }

    private fun refreshCustomSound() {
        if (sounds == null) sounds = KeySounds(this)
        sounds?.refreshCustom()
    }

    private fun sampleSound(style: Int) {
        if (sounds == null) sounds = KeySounds(this)
        sounds?.play(style, Prefs.soundVolume(this).coerceAtLeast(30))
    }

    // ---------------- behaviour ----------------

    private fun buildBehaviourSection(parent: LinearLayout) {
        Ui.heading(this, palette, parent, "TYPING")
        val card = Ui.addCard(this, palette, parent)

        Ui.switchRow(this, palette, card, "Long-press hints", Prefs.showLongPressHints(this)) {
            Prefs.setShowLongPressHints(this, it)
            rebuildPreview()
        }

        Ui.seekRow(this, palette, card, "Press zoom", 80, 100,
            Prefs.pressScalePercent(this), true, { "$it%" }) {
            Prefs.setPressScalePercent(this, it)
        }

        Ui.switchRow(this, palette, card, "Press preview popup", Prefs.previewEnabled(this)) {
            Prefs.setPreviewEnabled(this, it)
        }

        Ui.seekRow(this, palette, card, "Preview size", 36, 96,
            Prefs.previewSizeDp(this), true, { "$it dp" }) {
            Prefs.setPreviewSizeDp(this, it)
        }

        Ui.seekRow(this, palette, card, "Preview corner radius", 0, 30,
            Prefs.previewRadiusDp(this), true, { "$it dp" }) {
            Prefs.setPreviewRadiusDp(this, it)
        }

        Ui.seekRow(this, palette, card, "Preview linger", 0, 800,
            Prefs.previewLingerMs(this), true, { "$it ms" }) {
            Prefs.setPreviewLingerMs(this, it)
        }

        card.addView(TextView(this).apply {
            text = "Preview color (first = auto)"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        addSwatchRow(
            card,
            listOf<Int?>(
                0, 0xFF3A3A3C.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C1C1E.toInt(),
                0xFF0A84FF.toInt(), 0xFF34C759.toInt(), 0xFFFFCC00.toInt(),
                0xFFFF3B30.toInt(), 0xFFAF52DE.toInt()
            )
        ) { color ->
            Prefs.setPreviewColor(this, color ?: 0)
        }
    }

    // ------------------------------------------------------------------
    // SWATCHES
    // ------------------------------------------------------------------

    /** One row of color chips; null = automatic / theme default. */
    private fun addSwatchRow(
        parent: LinearLayout,
        colors: List<Int?>,
        onPick: (Int?) -> Unit
    ) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(row)
        colors.forEach { color ->
            val isAuto = color == null || color == 0
            val chip = View(this).apply {
                background = if (isAuto) {
                    Ui.rounded(this@EditorActivity, palette.inputBg, 10, palette.secondary)
                } else {
                    Ui.rounded(this@EditorActivity, color!!, 10)
                }
                isClickable = true
                setOnClickListener { onPick(color) }
            }
            row.addView(
                chip,
                LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginEnd = dp(8); topMargin = dp(2); bottomMargin = dp(2)
                }
            )
        }
        parent.addView(scroll)
    }

    // ------------------------------------------------------------------
    // BATCH SELECT
    // ------------------------------------------------------------------

    private fun buildBatchBar(parent: LinearLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(palette.card)
            visibility = View.GONE
        }
        batchBar = bar

        val countView = TextView(this).apply {
            textSize = 14f
            setTextColor(palette.text)
        }
        batchCountView = countView
        bar.addView(
            countView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        fun batchChip(label: String, danger: Boolean = false, onClick: () -> Unit) {
            val chip = Ui.compactButton(this, palette, label, danger, onClick)
            bar.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }

        batchChip("Style…") { showBatchStyleDialog() }
        batchChip("◀") { batchMoveHorizontal(-1) }
        batchChip("▲") { batchMoveVertical(-1) }
        batchChip("▼") { batchMoveVertical(1) }
        batchChip("▶") { batchMoveHorizontal(1) }
        batchChip("✕", danger = true) {
            selectedIds.toList().forEach { id ->
                findKeyById(id)?.let { moveToTrash(it) }
            }
            selectedIds.clear()
            updateBatchBar()
            rebuildPreview()
        }
        batchChip("Done") { exitSelectMode() }

        parent.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun enterSelectMode() {
        selectMode = true
        selectedIds.clear()
        updateBatchBar()
        rebuildPreview()
    }

    private fun exitSelectMode() {
        selectMode = false
        selectedIds.clear()
        updateBatchBar()
        rebuildPreview()
    }

    private fun updateBatchBar() {
        batchBar?.visibility = if (selectMode) View.VISIBLE else View.GONE
        batchCountView?.text =
            if (selectedIds.isEmpty()) "Select keys below"
            else "${selectedIds.size} selected"
    }

    private fun batchMoveHorizontal(dir: Int) {
        def.rows.forEach { row ->
            val indices = row.keys.withIndex()
                .filter { selectedIds.contains(it.value.id) }
                .map { it.index }
            if (dir < 0) {
                indices.sorted().forEach { i ->
                    if (i > 0 && !selectedIds.contains(row.keys[i - 1].id)) {
                        val k = row.keys.removeAt(i)
                        row.keys.add(i - 1, k)
                    }
                }
            } else {
                indices.sortedDescending().forEach { i ->
                    if (i < row.keys.size - 1 && !selectedIds.contains(row.keys[i + 1].id)) {
                        val k = row.keys.removeAt(i)
                        row.keys.add(i + 1, k)
                    }
                }
            }
        }
        rebuildPreview()
    }

    private fun batchMoveVertical(dir: Int) {
        val moving = ArrayList<Pair<Int, Int>>()
        def.rows.forEachIndexed { r, row ->
            row.keys.forEachIndexed { i, k ->
                if (selectedIds.contains(k.id)) moving.add(r to i)
            }
        }
        if (dir < 0) {
            moving.sortedByDescending { it.first }.forEach { (r, i) ->
                if (r > 0) {
                    val k = def.rows[r].keys.removeAt(i)
                    def.rows[r - 1].keys.add(k)
                }
            }
        } else {
            moving.sortedBy { it.first }.forEach { (r, i) ->
                if (r < def.rows.size - 1) {
                    val k = def.rows[r].keys.removeAt(i)
                    def.rows[r + 1].keys.add(k)
                }
            }
        }
        rebuildPreview()
    }

    private fun findKeyById(id: Long): KeyDef? {
        def.rows.forEach { row ->
            row.keys.forEach { if (it.id == id) return it }
        }
        return null
    }

    // ------------------------------------------------------------------
    // BOTTOM BAR: PRESETS · SAVE | RESET
    // ------------------------------------------------------------------

    private fun buildBottomBar(parent: LinearLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.card)
            setPadding(dp(10), dp(6), dp(10), dp(8))
        }

        // presets row (between the action buttons and the section content)
        val presetsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        presetsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetsScroll.addView(presetsRow)
        bar.addView(
            presetsScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rebuildPresetsRow()

        // Save | Reset
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        Ui.button(this, palette, actionsRow, "Save", filled = true, matchWidth = false) {
            Layouts.save(this, def)
            Toast.makeText(this, "Keyboard saved ✓", Toast.LENGTH_SHORT).show()
        }
        actionsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        Ui.button(this, palette, actionsRow, "Reset", filled = false, matchWidth = false) {
            confirmReset()
        }
        bar.addView(actionsRow)

        parent.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun rebuildPresetsRow() {
        presetsRow.removeAllViews()
        val presets = Prefs.presets(this)

        // "+" chip — saves the CURRENT state as a new preset (nothing else changes)
        val addChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = Ui.rounded(this, palette.accent, 14)
            setOnClickListener { showNewPresetDialog() }
            val img = ImageView(this).apply {
                setImageResource(R.drawable.ic_add)
                drawable?.setTint(palette.accentText)
            }
            addView(img, LinearLayout.LayoutParams(dp(15), dp(15)))
        }
        presetsRow.addView(
            addChip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        )

        presets.forEach { preset ->
            presetsRow.addView(
                Ui.compactButton(this, palette, preset.name) { showPresetDialog(preset) },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }

        if (presets.isEmpty()) {
            presetsRow.addView(TextView(this).apply {
                text = "No presets yet — tap + to save your current keyboard"
                textSize = 12f
                setTextColor(palette.secondary)
                setPadding(0, dp(9), 0, dp(9))
            })
        }
    }

    private fun showNewPresetDialog() {
        val dialog = Ui.CustomDialog(this, palette, "Save preset")
        val nameInput = Ui.editText(this, palette, "Preset name")
        nameInput.setText("My keyboard ${Prefs.presets(this).size + 1}")
        dialog.body.addView(nameInput)

        Ui.button(this, palette, dialog.body, "Save preset") {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Give the preset a name", Toast.LENGTH_SHORT).show()
            } else {
                // Snapshot of the current in-editor state — the preview and
                // the layout itself are NOT touched by saving a preset.
                Prefs.savePreset(
                    this,
                    Prefs.Preset(
                        name = name,
                        layoutJson = Layouts.toJson(def),
                        keyHeightDp = Prefs.keyHeightDp(this),
                        keyGapDp = Prefs.keyGapDp(this),
                        opacity = Prefs.keyboardOpacity(this),
                        extraBottomDp = Prefs.extraBottomDp(this),
                        bgBlur = Prefs.bgBlur(this)
                    )
                )
                rebuildPresetsRow()
                Toast.makeText(this, "Preset \"$name\" saved", Toast.LENGTH_SHORT).show()
                dialog.dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showPresetDialog(preset: Prefs.Preset) {
        val dialog = Ui.CustomDialog(this, palette, preset.name)
        dialog.body.addView(TextView(this).apply {
            text = "Load this preset into the editor, or delete it."
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        val row = LinearLayout(this)
        Ui.button(this, palette, row, "Load", filled = true, matchWidth = false) {
            loadPreset(preset)
            dialog.dialog.dismiss()
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        Ui.button(this, palette, row, "Delete", filled = false, matchWidth = false) {
            Prefs.deletePreset(this, preset.name)
            rebuildPresetsRow()
            dialog.dialog.dismiss()
        }
        dialog.body.addView(row)
        dialog.show()
    }

    private fun loadPreset(preset: Prefs.Preset) {
        Layouts.fromJson(preset.layoutJson)?.let { loaded ->
            def = loaded
        }
        Prefs.setKeyHeightDp(this, preset.keyHeightDp)
        Prefs.setKeyGapDp(this, preset.keyGapDp)
        Prefs.setKeyboardOpacity(this, preset.opacity)
        Prefs.setExtraBottomDp(this, preset.extraBottomDp)
        Prefs.setBgBlur(this, preset.bgBlur)
        rebuildPreview()
        Toast.makeText(this, "Loaded \"${preset.name}\" — tap Save to keep it", Toast.LENGTH_SHORT).show()
    }

    private fun confirmReset() {
        val dialog = Ui.CustomDialog(this, palette, "Reset keyboard?")
        dialog.body.addView(TextView(this).apply {
            text = "This restores the default QWERTY layout. Presets and the Trash are kept."
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        val row = LinearLayout(this)
        Ui.button(this, palette, row, "Reset", filled = true, matchWidth = false) {
            def = Layouts.defaultLetters()
            // give every key a stable id
            var next = 1L
            def.rows.forEach { rowDef ->
                val withIds = rowDef.keys.map {
                    if (it.id == 0L) it.copy(id = System.nanoTime() + (next++)) else it
                }
                rowDef.keys.clear()
                rowDef.keys.addAll(withIds)
            }
            rebuildPreview()
            dialog.dialog.dismiss()
            Toast.makeText(this, "Layout reset (tap Save to keep)", Toast.LENGTH_SHORT).show()
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        Ui.button(this, palette, row, "Cancel", filled = false, matchWidth = false) {
            dialog.dialog.dismiss()
        }
        dialog.body.addView(row)
        dialog.show()
    }

    // ------------------------------------------------------------------
    // PER-KEY DIALOG (tabbed: Basic · Style · Text · Shadow)
    // ------------------------------------------------------------------

    private fun showKeyDialog(key: KeyDef) {
        val dialog = Ui.CustomDialog(this, palette, "Edit \"${key.label.take(10)}\"")
        val tabs = arrayOf("Basic", "Style", "Text", "Shadow")

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dialog.body.addView(tabRow)

        // pages live inside a scroll so tall content stays reachable
        val pagesScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val pagesHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pages = tabs.map { name ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                tag = name
            }
        }
        pages.forEach { page ->
            pagesHolder.addView(
                page,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        pagesScroll.addView(pagesHolder)
        dialog.body.addView(
            pagesScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.52f).toInt()
            )
        )

        fun selectTab(index: Int) {
            pages.forEachIndexed { i, page -> page.visibility = if (i == index) View.VISIBLE else View.GONE }
            for (i in 0 until tabRow.childCount) {
                val chip = tabRow.getChildAt(i) as TextView
                if (i == index) {
                    chip.background = Ui.rounded(this, palette.accent, 14)
                    chip.setTextColor(palette.accentText)
                } else {
                    chip.background = Ui.rounded(this, palette.tinted, 14)
                    chip.setTextColor(palette.text)
                }
            }
        }

        tabs.forEachIndexed { index, name ->
            val chip = TextView(this).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setTextColor(palette.text)
                background = Ui.rounded(this, palette.tinted, 14)
                setOnClickListener { selectTab(index) }
            }
            tabRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (index < tabs.size - 1) dp(6) else 0
                }
            )
        }

        // ================= BASIC PAGE =================
        val basic = pages[0]

        val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labelInput = Ui.editText(this, palette, "Label")
        labelInput.setText(key.label)
        labelRow.addView(
            labelInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        // emoji ICON button (vector icon, not an emoji character)
        val emojiBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_emoji)
            drawable?.setTint(palette.text)
            background = Ui.rounded(this@EditorActivity, palette.tinted, 12)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "Insert emoji"
            setOnClickListener {
                EmojiPicker(this@EditorActivity, palette).show { emoji -> labelInput.setText(emoji) }
            }
        }
        labelRow.addView(
            emojiBtn,
            LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) }
        )
        basic.addView(labelRow)

        val outputInput = Ui.editText(this, palette, "Types text (empty = label)")
        outputInput.setText(key.output)
        basic.addView(outputInput)

        var widthPercent = (key.weight * 100).toInt().coerceIn(40, 400)
        Ui.seekRow(this, palette, basic, "Width", 40, 400, widthPercent, true, { "$it%" }) {
            widthPercent = it
        }

        var heightPercent = (key.heightFactor * 100).toInt().coerceIn(70, 140)
        Ui.seekRow(this, palette, basic, "Height", 70, 140, heightPercent, true, { "$it%" }) {
            heightPercent = it
        }

        val longPressInput = Ui.editText(this, palette, "Long-press types")
        longPressInput.setText(key.longPressOutput)
        basic.addView(longPressInput)

        val alternatesInput = Ui.editText(this, palette, "Long-press chars (hint + popup)")
        alternatesInput.setText(key.alternates)
        basic.addView(alternatesInput)

        var repeatOnHold = key.repeatOnHold
        Ui.switchRow(this, palette, basic, "Repeat while held", repeatOnHold) { repeatOnHold = it }

        basic.addView(TextView(this).apply {
            text = "Key color (first = theme)"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        var pickedColor = key.color
        addSwatchRow(basic, swatchColors) { pickedColor = it }

        basic.addView(TextView(this).apply {
            text = "Move this key"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(12), 0, dp(4))
        })
        val moveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("◀" to (0 to -1), "▲" to (-1 to 0), "▼" to (1 to 0), "▶" to (0 to 1))
            .forEach { (label, delta) ->
                moveRow.addView(
                    Ui.compactButton(this, palette, label) {
                        dialog.dialog.dismiss()
                        moveKey(key, delta.first, delta.second)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6)
                    }
                )
            }
        basic.addView(moveRow)

        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        addRow.addView(
            Ui.compactButton(this, palette, "＋ Add new key…") {
                dialog.dialog.dismiss()
                showAddKeyDialog(key)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        addRow.addView(
            Ui.compactButton(this, palette, "🗑 Delete", danger = true) {
                dialog.dialog.dismiss()
                moveToTrash(key)
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        basic.addView(addRow)

        // ================= STYLE PAGE =================
        val stylePage = pages[1]
        val style = key.style

        var useCustomStyle = style != null
        Ui.switchRow(this, palette, stylePage, "Custom style", useCustomStyle) { useCustomStyle = it }

        val styleControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stylePage.addView(styleControls)

        var radius = style?.cornerRadiusDp ?: 8
        var opacity = style?.opacityPercent ?: 100
        var borderColor = style?.borderColor
        var borderWidth = style?.borderWidthDp ?: 1
        var borderSides = style?.borderSides ?: 15

        Ui.seekRow(this, palette, styleControls, "Corner radius", 0, 22, radius, true, { "$it dp" }) {
            radius = it
        }
        Ui.seekRow(this, palette, styleControls, "Key transparency", 10, 100, opacity, true, { "$it%" }) {
            opacity = it
        }

        styleControls.addView(TextView(this).apply {
            text = "Border color (first = none)"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        addSwatchRow(styleControls, swatchColors) { borderColor = it }

        Ui.seekRow(this, palette, styleControls, "Border thickness", 0, 5, borderWidth, true, { "$it dp" }) {
            borderWidth = it
        }

        styleControls.addView(TextView(this).apply {
            text = "Border sides"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        val sidesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sideDefs = listOf("All" to 15, "Top" to 1, "Right" to 2, "Bottom" to 4, "Left" to 8)
        val sideChips = ArrayList<TextView>()
        fun restyleSides() {
            sideDefs.forEachIndexed { i, (_, bit) ->
                val on = if (bit == 15) borderSides == 15 else (borderSides and bit) != 0
                sideChips[i].background = Ui.rounded(this, if (on) palette.accent else palette.tinted, 12)
                sideChips[i].setTextColor(if (on) palette.accentText else palette.text)
            }
        }
        sideDefs.forEach { (name, bit) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setTextColor(palette.text)
                setOnClickListener {
                    borderSides = if (bit == 15) 15 else borderSides xor bit
                    restyleSides()
                }
            }
            sideChips.add(chip)
            sidesRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
            )
        }
        restyleSides()
        styleControls.addView(sidesRow)

        // ================= TEXT PAGE =================
        val textPage = pages[2]

        var textColor = style?.textColor
        var textSize = style?.textSizeSp ?: 16
        var bold = style?.bold ?: false
        var italic = style?.italic ?: false
        var textPosition = style?.textPosition ?: 0

        textPage.addView(TextView(this).apply {
            text = "Text color (first = theme)"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(4), 0, dp(4))
        })
        addSwatchRow(textPage, swatchColors) { textColor = it }

        Ui.seekRow(this, palette, textPage, "Text size", 10, 30, textSize, true, { "$it sp" }) {
            textSize = it
        }

        Ui.switchRow(this, palette, textPage, "Bold", bold) { bold = it }
        Ui.switchRow(this, palette, textPage, "Italic", italic) { italic = it }

        textPage.addView(TextView(this).apply {
            text = "Text position"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        val posRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val posDefs = listOf("●" to 0, "▲" to 1, "▼" to 2, "◀" to 3, "▶" to 4)
        val posChips = ArrayList<TextView>()
        fun restylePos() {
            posDefs.forEachIndexed { i, (_, value) ->
                val on = textPosition == value
                posChips[i].background = Ui.rounded(this, if (on) palette.accent else palette.tinted, 12)
                posChips[i].setTextColor(if (on) palette.accentText else palette.text)
            }
        }
        posDefs.forEach { (name, value) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setTextColor(palette.text)
                setOnClickListener {
                    textPosition = value
                    restylePos()
                }
            }
            posChips.add(chip)
            posRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
            )
        }
        restylePos()
        textPage.addView(posRow)

        // ================= SHADOW PAGE =================
        val shadowPage = pages[3]

        var useShadow = style?.shadow ?: false
        var shadowSoft = style?.shadowSoft ?: true
        var shadowAngle = style?.shadowAngleDeg ?: 315
        var shadowDistance = style?.shadowDistanceDp ?: 3
        var shadowBlur = style?.shadowBlurDp ?: 4

        Ui.switchRow(this, palette, shadowPage, "Drop shadow", useShadow) { useShadow = it }

        val softRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val softChips = ArrayList<TextView>()
        fun restyleSoft() {
            softChips[0].background = Ui.rounded(this, if (shadowSoft) palette.accent else palette.tinted, 12)
            softChips[0].setTextColor(if (shadowSoft) palette.accentText else palette.text)
            softChips[1].background = Ui.rounded(this, if (!shadowSoft) palette.accent else palette.tinted, 12)
            softChips[1].setTextColor(if (!shadowSoft) palette.accentText else palette.text)
        }
        listOf("Soft (blurred)" to true, "Hard (solid)" to false).forEach { (name, soft) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(9), dp(12), dp(9))
                setTextColor(palette.text)
                setOnClickListener {
                    shadowSoft = soft
                    restyleSoft()
                }
            }
            softChips.add(chip)
            softRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
            )
        }
        restyleSoft()
        shadowPage.addView(softRow)

        Ui.seekRow(this, palette, shadowPage, "Shadow angle", 0, 355, shadowAngle, true, { "$it°" }) {
            shadowAngle = it
        }
        Ui.seekRow(this, palette, shadowPage, "Shadow distance", 0, 10, shadowDistance, true, { "$it dp" }) {
            shadowDistance = it
        }
        Ui.seekRow(this, palette, shadowPage, "Shadow blur", 0, 14, shadowBlur, shadowSoft, { "$it dp" }) {
            shadowBlur = it
        }

        // ================= APPLY =================
        Ui.button(this, palette, dialog.body, "Apply") {
            val newStyle = if (useCustomStyle) {
                KeyVisual(
                    cornerRadiusDp = radius,
                    opacityPercent = opacity,
                    borderColor = borderColor,
                    borderWidthDp = if (borderWidth <= 0) null else borderWidth,
                    borderSides = borderSides,
                    shadow = useShadow,
                    shadowSoft = shadowSoft,
                    shadowAngleDeg = shadowAngle,
                    shadowDistanceDp = shadowDistance,
                    shadowBlurDp = shadowBlur,
                    textColor = textColor,
                    textSizeSp = textSize,
                    bold = bold,
                    italic = italic,
                    textPosition = textPosition
                )
            } else null

            val pos = positionOf(key)
            if (pos != null) {
                val (row, idx) = pos
                def.rows[row].keys[idx] = key.copy(
                    label = labelInput.text.toString(),
                    output = outputInput.text.toString(),
                    weight = widthPercent / 100f,
                    heightFactor = heightPercent / 100f,
                    longPressOutput = longPressInput.text.toString(),
                    alternates = alternatesInput.text.toString(),
                    repeatOnHold = repeatOnHold,
                    color = pickedColor,
                    style = newStyle
                )
            }
            dialog.dialog.dismiss()
            rebuildPreview()
        }

        selectTab(0)
        dialog.show()
    }

    // ------------------------------------------------------------------
    // MOVE / ADD KEYS
    // ------------------------------------------------------------------

    private fun moveKey(key: KeyDef, rowDelta: Int, colDelta: Int) {
        val pos = positionOf(key) ?: return
        val (row, idx) = pos

        if (colDelta != 0) {
            val sameRow = def.rows[row]
            val newIdx = (idx + colDelta).coerceIn(0, sameRow.keys.size - 1)
            sameRow.keys.removeAt(idx)
            sameRow.keys.add(newIdx, key)
        } else if (rowDelta != 0) {
            val targetRow = (row + rowDelta).coerceIn(0, def.rows.size - 1)
            if (targetRow == row) return
            def.rows[row].keys.removeAt(idx)
            def.rows[targetRow].keys.add(key)
        }
        rebuildPreview()
    }

    private fun newKey(label: String): KeyDef =
        KeyDef(KeyType.CUSTOM, label, label, id = System.nanoTime() + (nextKeyId++))

    private fun showAddKeyDialog(sourceKey: KeyDef) {
        val dialog = Ui.CustomDialog(this, palette, "Add new key")

        var type = KeyType.CUSTOM
        val typeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val typeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        typeScroll.addView(typeRow)

        val typeDefs = listOf(
            "Text" to KeyType.CUSTOM, "Letter" to KeyType.LETTER,
            "Space" to KeyType.SPACE, "Delete" to KeyType.DELETE,
            "Enter" to KeyType.ENTER, "Shift" to KeyType.SHIFT,
            "Emoji" to KeyType.EMOJI, "Cursor" to KeyType.TO_CURSOR,
            "Next field" to KeyType.NEXT_FIELD,
            "Copy" to KeyType.CLIP_COPY, "Paste" to KeyType.CLIP_PASTE
        )
        val typeChips = ArrayList<TextView>()
        fun restyleTypes() {
            typeDefs.forEachIndexed { i, (_, value) ->
                val on = type == value
                typeChips[i].background = Ui.rounded(this, if (on) palette.accent else palette.tinted, 12)
                typeChips[i].setTextColor(if (on) palette.accentText else palette.text)
            }
        }
        typeDefs.forEach { (name, value) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setTextColor(palette.text)
                setOnClickListener {
                    type = value
                    restyleTypes()
                }
            }
            typeChips.add(chip)
            typeRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }
        restyleTypes()
        dialog.body.addView(typeScroll)

        val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labelInput = Ui.editText(this, palette, "Label")
        labelRow.addView(
            labelInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val emojiBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_emoji)
            drawable?.setTint(palette.text)
            background = Ui.rounded(this@EditorActivity, palette.tinted, 12)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setOnClickListener {
                EmojiPicker(this@EditorActivity, palette).show { emoji -> labelInput.setText(emoji) }
            }
        }
        labelRow.addView(
            emojiBtn,
            LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) }
        )
        dialog.body.addView(labelRow)

        val outputInput = Ui.editText(this, palette, "Types text")
        dialog.body.addView(outputInput)

        dialog.body.addView(TextView(this).apply {
            text = "Where should it go? (relative to \"${sourceKey.label.take(6)}\")"
            textSize = 13f
            setTextColor(palette.secondary)
            setPadding(0, dp(10), 0, dp(4))
        })
        var position = 1 // 0 left · 1 right · 2 above · 3 below · 4 end
        val posRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val posDefs = listOf("◀ Left" to 0, "▶ Right" to 1, "▲ Above" to 2, "▼ Below" to 3, "⏭ End" to 4)
        val posChips = ArrayList<TextView>()
        fun restyleAddPos() {
            posDefs.forEachIndexed { i, (_, value) ->
                val on = position == value
                posChips[i].background = Ui.rounded(this, if (on) palette.accent else palette.tinted, 12)
                posChips[i].setTextColor(if (on) palette.accentText else palette.text)
            }
        }
        posDefs.forEach { (name, value) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(9), dp(8), dp(9))
                setTextColor(palette.text)
                setOnClickListener {
                    position = value
                    restyleAddPos()
                }
            }
            posChips.add(chip)
            posRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
            )
        }
        restyleAddPos()
        dialog.body.addView(posRow)

        Ui.button(this, palette, dialog.body, "Add key") {
            val label = labelInput.text.toString()
            val made = when (type) {
                KeyType.SPACE -> KeyDef(type, "CustomKey", "", 3.4f, id = System.nanoTime())
                KeyType.SHIFT -> KeyDef(type, "⇧", "", 1.4f, id = System.nanoTime())
                KeyType.DELETE -> KeyDef(type, "⌫", "", 1.4f, id = System.nanoTime())
                KeyType.ENTER -> KeyDef(type, "↵", "", 1.2f, id = System.nanoTime())
                KeyType.EMOJI -> KeyDef(type, "Emoji", "", 1f, id = System.nanoTime())
                KeyType.TO_CURSOR -> KeyDef(type, "Cursor", "", 1f, id = System.nanoTime())
                KeyType.NEXT_FIELD -> KeyDef(type, "Next ⏭", "", 1.3f, id = System.nanoTime())
                KeyType.CLIP_COPY -> KeyDef(type, "Copy", "", 1f, id = System.nanoTime())
                KeyType.CLIP_PASTE -> KeyDef(type, "Paste", "", 1f, id = System.nanoTime())
                else -> KeyDef(
                    type,
                    label.ifEmpty { "•" },
                    outputInput.text.toString(),
                    1f,
                    id = System.nanoTime()
                )
            }
            val srcPos = positionOf(sourceKey)
                ?: (def.rows.size - 1 to def.rows.last().keys.size)
            when (position) {
                0 -> def.rows[srcPos.first].keys.add(srcPos.second, made)
                1 -> def.rows[srcPos.first].keys.add(srcPos.second + 1, made)
                2 -> def.rows.add(srcPos.first.coerceIn(0, def.rows.size), RowDef(mutableListOf(made)))
                3 -> def.rows.add(srcPos.first + 1, RowDef(mutableListOf(made)))
                else -> def.rows.last().keys.add(made)
            }
            dialog.dialog.dismiss()
            rebuildPreview()
            Toast.makeText(this, "Key added — tap Save to keep", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // ------------------------------------------------------------------
    // BATCH STYLE DIALOG
    // ------------------------------------------------------------------

    private fun showBatchStyleDialog() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Select keys first by tapping them", Toast.LENGTH_SHORT).show()
            return
        }
        val first = selectedIds.firstOrNull()?.let { findKeyById(it) }
        val dialog = Ui.CustomDialog(this, palette, "Style ${selectedIds.size} keys")
        val body = dialog.body

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        body.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.52f).toInt()
            )
        )

        val template = first?.style
        var radius = template?.cornerRadiusDp ?: 8
        var opacity = template?.opacityPercent ?: 100
        var borderColor = template?.borderColor
        var borderWidth = template?.borderWidthDp ?: 1
        var useShadow = template?.shadow ?: false
        var textColor = template?.textColor
        var textSize = template?.textSizeSp ?: 16
        var bold = template?.bold ?: false
        var italic = template?.italic ?: false

        Ui.seekRow(this, palette, content, "Corner radius", 0, 22, radius, true, { "$it dp" }) { radius = it }
        Ui.seekRow(this, palette, content, "Key transparency", 10, 100, opacity, true, { "$it%" }) { opacity = it }
        Ui.seekRow(this, palette, content, "Border thickness", 0, 5, borderWidth, true, { "$it dp" }) { borderWidth = it }
        Ui.seekRow(this, palette, content, "Shadow", 0, 1, if (useShadow) 1 else 0, true, {
            if (it == 1) "On" else "Off"
        }) { useShadow = it == 1 }
        Ui.seekRow(this, palette, content, "Text size", 10, 30, textSize, true, { "$it sp" }) { textSize = it }
        Ui.switchRow(this, palette, content, "Bold", bold) { bold = it }
        Ui.switchRow(this, palette, content, "Italic", italic) { italic = it }

        content.addView(TextView(this).apply {
            text = "Border color (first = none)"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        addSwatchRow(content, swatchColors) { borderColor = it }

        content.addView(TextView(this).apply {
            text = "Text color (first = theme)"
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(10), 0, dp(4))
        })
        addSwatchRow(content, swatchColors) { textColor = it }

        Ui.button(this, palette, body, "Apply to ${selectedIds.size} keys") {
            def.rows.forEach { row ->
                row.keys.forEachIndexed { i, k ->
                    if (selectedIds.contains(k.id)) {
                        val merged = (k.style ?: KeyVisual()).copy(
                            cornerRadiusDp = radius,
                            opacityPercent = opacity,
                            borderColor = borderColor,
                            borderWidthDp = if (borderWidth <= 0) null else borderWidth,
                            shadow = useShadow,
                            textColor = textColor,
                            textSizeSp = textSize,
                            bold = bold,
                            italic = italic
                        )
                        row.keys[i] = k.copy(style = merged)
                    }
                }
            }
            dialog.dialog.dismiss()
            rebuildPreview()
        }
        dialog.show()
    }

    // ------------------------------------------------------------------
    // PICKERS
    // ------------------------------------------------------------------

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "Choose background image"), 101)
    }

    private fun pickFont() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "Choose a .ttf font"), 102)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            101 -> {
                copyUri(uri, "keyboard_bg.jpg")?.let { path ->
                    Prefs.setBgImagePath(this, path)
                    rebuildPreview()
                    Toast.makeText(this, "Background updated", Toast.LENGTH_SHORT).show()
                }
            }
            102 -> {
                if (uri.toString().contains("ttf", ignoreCase = true) ||
                    (uri.lastPathSegment ?: "").contains("ttf", ignoreCase = true)
                ) {
                    copyUri(uri, "custom_font.ttf")?.let { path ->
                        Prefs.setCustomFontPath(this, path)
                        rebuildPreview()
                        Toast.makeText(this, "Custom font applied", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please pick a .ttf file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyUri(uri: Uri, fileName: String): String? {
        return try {
            val out = java.io.File(filesDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return null
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
