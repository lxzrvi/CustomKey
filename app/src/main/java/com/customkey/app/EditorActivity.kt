package com.customkey.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
 * The Keyboard Editor — tabbed, live and fully customizable.
 *
 * Structure: Header → interactive preview → horizontal category tabs →
 * dynamic formatting section → fixed bottom action bar
 * (Reset · Cancel · Preset · Apply).
 *
 * Tabs: Key · Selected · All Keys · Keyboard · Emoji · Toolbar · Sound ·
 * Vibration · Layout. Every change updates the preview live; switching tabs
 * never resets the selection or discards changes.
 */
class EditorActivity : Activity(), KeyView.Listener {

    private lateinit var palette: Ui.Palette

    // Working layout (persisted on Apply)
    private lateinit var def: KeyboardDef
    private var nextKeyId = 1L

    // Single-key editing target
    private var activeKey: KeyDef? = null

    // Batch selection
    private var selectMode = false
    private val selectedIds = HashSet<Long>()
    private var batchBar: LinearLayout? = null
    private var batchCountView: TextView? = null

    // Move mode
    private var moveMode = false
    private var moveSnapshotJson: String? = null
    private var moveBar: LinearLayout? = null

    // Drag-move
    private var dragGhost: TextView? = null
    private var dragKey: KeyDef? = null
    private var dragLp: FrameLayout.LayoutParams? = null
    private var contentOrigin = IntArray(2)

    // Unsaved changes
    private var dirty = false
    private var prefsSnapshot: HashMap<String, Any>? = null

    // Trash
    private data class TrashItem(val key: KeyDef, val row: Int, val col: Int)
    private val trash = ArrayList<TrashItem>()
    private var trashStrip: LinearLayout? = null

    // Preview
    private lateinit var previewHolder: FrameLayout
    private lateinit var preview: LinearLayout
    private var previewKeyViews = ArrayList<KeyView>()

    // Tabs
    private val tabNames = listOf(
        "Key", "Selected", "All Keys", "Keyboard", "Emoji",
        "Toolbar", "Sound", "Vibration", "Layout"
    )
    private lateinit var tabsRow: LinearLayout
    private lateinit var tabPagesHolder: LinearLayout
    private val tabPages = HashMap<String, LinearLayout>()
    private var currentTab = "Key"

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

    private val toolbarButtonNames = mapOf(
        "cl" to "Cursor left", "cr" to "Cursor right",
        "cu" to "Cursor up", "cd" to "Cursor down",
        "copy" to "Copy", "cut" to "Cut", "paste" to "Paste",
        "all" to "Select all", "emoji" to "Emoji",
        "next" to "Next field", "hide" to "Hide keyboard"
    )

    // ------------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Ui.palette(this)
        def = Layouts.current(this)
        prefsSnapshot = Prefs.snapshot(this)
        loadTrash()
        createScreen()
    }

    override fun onDestroy() {
        sounds?.release()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            dragGhost != null -> cancelDrag()
            moveMode -> cancelMove()
            dirty -> showExitDialog()
            else -> super.onBackPressed()
        }
    }

    private fun dp(value: Int): Int = Ui.dp(this, value)

    private fun markDirty() {
        dirty = true
        // lets the IME pick up appearance changes next time it is shown
        Prefs.bumpLayoutVersion(this)
    }

    // ------------------------------------------------------------------
    // SCREEN
    // ------------------------------------------------------------------

    private fun createScreen() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.bg)
        }
        Ui.applySystemBarsPadding(rootLayout)

        buildHeader(rootLayout)
        buildPreviewArea(rootLayout)
        buildBatchBar(rootLayout)
        buildTabsRow(rootLayout)
        buildTabScroll(rootLayout)
        buildMoveBar(rootLayout)
        buildBottomBar(rootLayout)

        setContentView(rootLayout)
        rebuildPreview()
        switchTab("Key")
    }

    // ---------------- header ----------------

    private fun buildHeader(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
        }

        val back = TextView(this).apply {
            text = "‹"
            textSize = 22f
            typeface = Ui.fontMedium(this@EditorActivity)
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            background = Ui.rounded(this@EditorActivity, palette.tinted, 12)
            contentDescription = "Back"
            setOnClickListener { onBackPressed() }
        }
        header.addView(
            back,
            LinearLayout.LayoutParams(dp(38), dp(38))
        )

        val title = TextView(this).apply {
            text = "Keyboard Editor"
            textSize = 18f
            typeface = Ui.fontSemibold(this@EditorActivity)
            setTextColor(palette.text)
            gravity = Gravity.CENTER
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10); marginEnd = dp(10) }
        )

        val more = TextView(this).apply {
            text = "⋯"
            textSize = 20f
            typeface = Ui.fontMedium(this@EditorActivity)
            gravity = Gravity.CENTER
            setTextColor(palette.text)
            background = Ui.rounded(this@EditorActivity, palette.tinted, 12)
            contentDescription = "More options"
            setOnClickListener { showMoreOptions() }
        }
        header.addView(
            more,
            LinearLayout.LayoutParams(dp(38), dp(38))
        )

        parent.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun showMoreOptions() {
        val dialog = Ui.CustomDialog(this, palette, "More options")

        fun option(title: String, danger: Boolean = false, action: () -> Unit) {
            Ui.button(this, palette, dialog.body, title, filled = !danger) {
                dialog.dialog.dismiss()
                action()
            }
        }
        option("Reset all key formatting") { confirmResetAllStyles() }
        option("Clear trash", danger = true) {
            trash.clear()
            saveTrash()
            rebuildTrashStrip()
            Toast.makeText(this, "Trash emptied", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // ---------------- preview ----------------

    private fun buildPreviewArea(parent: LinearLayout) {
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
        parent.addView(
            previewHolder,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun rebuildPreview() {
        preview.removeAllViews()
        previewKeyViews.clear()

        val theme = KeyboardTheme.palette(this)
        val style = theme.style
        val heightPx = dp(Prefs.keyHeightDp(this).coerceIn(40, 62))
        val gapPx = dp(Prefs.keyGapDp(this).coerceIn(0, 8))
        val rowGapPx = dp(Prefs.rowGapDp(this).coerceIn(0, 14))
        val halfGap = (gapPx / 2f).toInt()
        val halfRowGap = (rowGapPx / 2f).toInt()

        val bg = KeyboardTheme.backgroundDrawable(this)
        if (bg != null) previewHolder.background = bg
        else {
            val color = Prefs.kbBgColor(this)
            if (color != 0 || Prefs.kbGradient(this)) applyKbBackgroundTo(theme)
            else previewHolder.setBackgroundColor(theme.bg)
        }

        def.rows.forEach { rowDef ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = when (Prefs.rowAlignment(this@EditorActivity)) {
                    0 -> Gravity.LEFT
                    2 -> Gravity.RIGHT
                    else -> Gravity.CENTER
                }
            }
            rowDef.keys.forEach { key ->
                val view = KeyView(this, key, heightPx)
                view.listener = this
                val special = key.type != KeyType.LETTER && key.type != KeyType.CUSTOM
                val keyBg = key.color ?: if (special) style.specialBg else style.keyBg
                val isActiveEdit = !selectMode && key.id == activeKey?.id
                val isSelected = selectMode && selectedIds.contains(key.id)
                view.setColors(
                    bg = if (isActiveEdit || isSelected) 0xFF0A84FF.toInt() else theme.withAlpha(keyBg),
                    label = if (isActiveEdit || isSelected) 0xFFFFFFFF.toInt() else style.textColor,
                    sizeSp = style.textPx,
                    radiusPx = style.radiusPx,
                    hintColor = theme.withAlpha(style.textColor)
                )
                if (isActiveEdit || isSelected) view.active = true
                view.setHintText(
                    if (Prefs.showLongPressHints(this))
                        key.alternates.take(1).ifEmpty { builtInHint(key) } else ""
                )
                when (key.type) {
                    KeyType.EMOJI -> view.setIcon(getDrawable(R.drawable.ic_emoji)?.mutate())
                    KeyType.TO_CURSOR -> view.setIcon(getDrawable(R.drawable.ic_cursor)?.mutate())
                    else -> Unit
                }
                val lp = LinearLayout.LayoutParams(0, heightPx, key.weight)
                lp.setMargins(halfGap, halfRowGap, halfGap, halfRowGap)
                row.addView(view, lp)
                previewKeyViews.add(view)
            }
            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLp.gravity = when (Prefs.rowAlignment(this)) {
                0 -> Gravity.LEFT
                2 -> Gravity.RIGHT
                else -> Gravity.CENTER_HORIZONTAL
            }
            val widthPct = Prefs.keyboardWidthPercent(this).coerceIn(60, 100)
            if (widthPct < 100) {
                rowLp.width = (resources.displayMetrics.widthPixels * widthPct / 100f).toInt()
            }
            preview.addView(row, rowLp)
        }

        // Scale down very tall previews so tabs stay reachable.
        preview.post {
            val maxH = (resources.displayMetrics.heightPixels * 0.38f).toInt()
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

    private fun builtInHint(key: KeyDef): String {
        if (key.type != KeyType.LETTER && key.type != KeyType.CUSTOM) return ""
        return key.label.take(1)
    }

    /** Mirrors the IME's custom keyboard surface in the preview. */
    private fun applyKbBackgroundTo(theme: com.customkey.app.KbPalette) {
        val color = Prefs.kbBgColor(this)
        val base = if (color != 0) color else theme.bg
        val c2 = Prefs.kbGradientColor2(this)
        previewHolder.background = android.graphics.drawable.GradientDrawable().apply {
            if (Prefs.kbGradient(this@EditorActivity)) {
                orientation = gradientOrientation(Prefs.kbGradientAngle(this@EditorActivity))
                colors = intArrayOf(theme.withAlpha(base), theme.withAlpha(c2))
            } else {
                setColor(theme.withAlpha(base))
            }
            val border = Prefs.kbBorderColor(this@EditorActivity)
            val bw = Prefs.kbBorderWidthDp(this@EditorActivity)
            if (bw > 0 && border != 0) setStroke(dp(bw), border)
            val radius = Prefs.kbRadiusDp(this@EditorActivity)
            if (radius > 0) cornerRadius = dp(radius).toFloat()
        }
    }

    private fun gradientOrientation(angle: Int): android.graphics.drawable.GradientDrawable.Orientation {
        val octant = ((angle % 360 + 360) % 360) / 45
        return when (octant) {
            0 -> android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            1 -> android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            2 -> android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            3 -> android.graphics.drawable.GradientDrawable.Orientation.TR_BL
            4 -> android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT
            5 -> android.graphics.drawable.GradientDrawable.Orientation.BR_TL
            6 -> android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP
            else -> android.graphics.drawable.GradientDrawable.Orientation.BL_TR
        }
    }

    // ---------------- batch bar ----------------

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
            typeface = Ui.fontMedium(this@EditorActivity)
            setTextColor(palette.text)
        }
        batchCountView = countView
        bar.addView(
            countView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        fun chip(label: String, danger: Boolean = false, onClick: () -> Unit) {
            val c = Ui.compactButton(this, palette, label, danger, onClick)
            bar.addView(
                c,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }

        chip("Style…") { switchTab("Selected") }
        chip("◀") { batchMoveHorizontal(-1) }
        chip("▲") { batchMoveVertical(-1) }
        chip("▼") { batchMoveVertical(1) }
        chip("▶") { batchMoveHorizontal(1) }
        chip("All") {
            def.rows.forEach { r -> r.keys.forEach { selectedIds.add(it.id) } }
            updateBatchBar()
            rebuildPreview()
            refreshTab("Selected")
        }
        chip("Clear") {
            selectedIds.clear()
            updateBatchBar()
            rebuildPreview()
            refreshTab("Selected")
        }
        chip("✕", danger = true) {
            selectedIds.toList().forEach { id -> findKeyById(id)?.let { moveToTrash(it) } }
            selectedIds.clear()
            updateBatchBar()
            rebuildPreview()
        }
        chip("Done") { exitSelectMode() }

        parent.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun updateBatchBar() {
        batchBar?.visibility = if (selectMode) View.VISIBLE else View.GONE
        batchCountView?.text =
            if (selectedIds.isEmpty()) "Select keys in the preview"
            else "${selectedIds.size} selected"
    }

    // ---------------- tabs ----------------

    private fun buildTabsRow(parent: LinearLayout) {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(10), dp(4), dp(10), dp(2))
        }
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(tabsRow)
        tabNames.forEach { name ->
            tabsRow.addView(
                TextView(this).apply {
                    text = name
                    textSize = 13f
                    typeface = Ui.fontMedium(this@EditorActivity)
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    setTextColor(palette.text)
                    background = Ui.rounded(this@EditorActivity, palette.tinted, 14)
                    setOnClickListener { switchTab(name) }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }
        parent.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun buildTabScroll(parent: LinearLayout) {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        tabPagesHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(2), dp(10), dp(6))
        }
        scroll.addView(tabPagesHolder)
        parent.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )
    }

    private fun switchTab(name: String) {
        currentTab = name
        tabPagesHolder.removeAllViews()
        val page = tabPages[name] ?: buildPage(name).also { tabPages[name] = it }
        tabPagesHolder.addView(
            page,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        // restyle tab chips
        for (i in 0 until tabsRow.childCount) {
            val chip = tabsRow.getChildAt(i) as TextView
            val on = chip.text.toString() == name
            chip.background = Ui.rounded(
                this,
                if (on) palette.accent else palette.tinted, 14
            )
            chip.setTextColor(if (on) palette.accentText else palette.text)
        }
    }

    /** Rebuilds a cached page (e.g. after selection / asset changes). */
    private fun refreshTab(name: String) {
        tabPages.remove(name)
        if (currentTab == name) switchTab(name)
    }

    private fun buildPage(name: String): LinearLayout = when (name) {
        "Key" -> buildKeyPage()
        "Selected" -> buildSelectedPage()
        "All Keys" -> buildAllKeysPage()
        "Keyboard" -> buildKeyboardPage()
        "Emoji" -> buildEmojiPage()
        "Toolbar" -> buildToolbarPage()
        "Sound" -> buildSoundPage()
        "Vibration" -> buildVibrationPage()
        else -> buildLayoutPage()
    }

    // ------------------------------------------------------------------
    // PREVIEW KEY LISTENER
    // ------------------------------------------------------------------

    override fun onKeyTap(view: KeyView, key: KeyDef) {
        if (dragGhost != null || moveMode) return
        if (selectMode) {
            if (selectedIds.contains(key.id)) selectedIds.remove(key.id)
            else selectedIds.add(key.id)
            updateBatchBar()
            rebuildPreview()
            refreshTab("Selected")
            return
        }
        showKeyActionBox(key)
    }

    /** Floating action box: Edit This · Select · Move · Cancel. */
    private fun showKeyActionBox(key: KeyDef) {
        val dialog = Ui.CustomDialog(this, palette, "“${key.label.take(10)}”")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dialog.body.addView(grid)

        fun row(titles: List<Pair<String, Boolean>>, actions: List<() -> Unit>) {
            val r = LinearLayout(this)
            titles.forEachIndexed { i, (title, filled) ->
                Ui.button(this, palette, r, title, filled = filled, matchWidth = false) {
                    dialog.dialog.dismiss()
                    actions[i]()
                }
            }
            grid.addView(r)
        }

        row(
            listOf("✏️  Edit This" to true, "☑  Select" to false),
            listOf(
                {
                    activeKey = key
                    rebuildPreview()
                    refreshTab("Key")
                    switchTab("Key")
                },
                {
                    activeKey = null
                    selectMode = true
                    selectedIds.clear()
                    selectedIds.add(key.id)
                    updateBatchBar()
                    rebuildPreview()
                    refreshTab("Selected")
                    switchTab("Selected")
                }
            )
        )
        row(
            listOf("✥  Move" to false, "Cancel" to false),
            listOf(
                { enterMoveMode() },
                { }
            )
        )
        dialog.show()
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
    // SELECTION
    // ------------------------------------------------------------------

    private fun enterSelectMode() {
        selectMode = true
        selectedIds.clear()
        activeKey = null
        updateBatchBar()
        rebuildPreview()
    }

    private fun exitSelectMode() {
        selectMode = false
        selectedIds.clear()
        updateBatchBar()
        rebuildPreview()
        refreshTab("Selected")
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
        markDirty()
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
        markDirty()
        rebuildPreview()
    }

    private fun findKeyById(id: Long): KeyDef? {
        def.rows.forEach { row ->
            row.keys.forEach { if (it.id == id) return it }
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
    // MOVE MODE + DRAG
    // ------------------------------------------------------------------

    private fun buildMoveBar(parent: LinearLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(palette.card)
            visibility = View.GONE
        }
        moveBar = bar

        bar.addView(
            TextView(this).apply {
                text = "Move mode — hold & drag keys · drop on Trash to delete"
                textSize = 12f
                typeface = Ui.font(this@EditorActivity)
                setTextColor(palette.secondary)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        fun chip(label: String, danger: Boolean = false, onClick: () -> Unit) {
            bar.addView(
                Ui.compactButton(this, palette, label, danger, onClick),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(6) }
            )
        }
        chip("Cancel", danger = true) { cancelMove() }
        chip("Done") { exitMoveMode() }

        parent.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun enterMoveMode() {
        moveMode = true
        moveSnapshotJson = Layouts.toJson(def)
        moveBar?.visibility = View.VISIBLE
        Toast.makeText(this, "Hold a key and drag it to a new position", Toast.LENGTH_SHORT).show()
    }

    private fun exitMoveMode() {
        moveMode = false
        moveSnapshotJson = null
        moveBar?.visibility = View.GONE
    }

    private fun cancelMove() {
        moveSnapshotJson?.let { json ->
            Layouts.fromJson(json)?.let { restored ->
                def = restored
                markDirty()
                rebuildPreview()
            }
        }
        exitMoveMode()
        Toast.makeText(this, "Move cancelled — positions restored", Toast.LENGTH_SHORT).show()
    }

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
            typeface = Ui.fontMedium(this@EditorActivity)
            setTextColor(0xFFFFFFFF.toInt())
            background = Ui.rounded(this@EditorActivity, 0xFF0A84FF.toInt(), 10)
            elevation = dp(20).toFloat()
        }
        dragLp = FrameLayout.LayoutParams(source.width, source.height).apply {
            leftMargin = loc[0] - contentOrigin[0]
            topMargin = loc[1] - contentOrigin[1]
        }
        contentView.addView(dragGhost, dragLp)
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
                markDirty()
                rebuildPreview()
                return
            }
        }

        // Group move: dragging one selected key moves the whole group.
        val groupMove = selectedIds.contains(key.id) && selectedIds.size > 1
        val groupKeys = if (groupMove) {
            def.rows.flatMap { r -> r.keys.filter { selectedIds.contains(it.id) } }
        } else listOf(key)

        val (srcRow, srcIdx) = positionOf(key) ?: return
        val target = findKeyViewAt(rawX, rawY)

        groupKeys.forEach { gk ->
            val pos = positionOf(gk) ?: return@forEach
            def.rows[pos.first].keys.removeAt(pos.second)
        }

        if (target != null) {
            val targetKey = target.key()
            val targetPos = positionOf(targetKey)
            if (targetPos != null) {
                var insertAt = targetPos.second
                val targetLoc = IntArray(2)
                target.getLocationOnScreen(targetLoc)
                if (rawX > targetLoc[0] + target.width / 2f) insertAt += 1
                val dstRow = def.rows[targetPos.first]
                groupKeys.asReversed().forEach { gk ->
                    dstRow.keys.add(insertAt.coerceIn(0, dstRow.keys.size), gk)
                }
            } else {
                def.rows.last().keys.addAll(groupKeys)
            }
        } else {
            def.rows.last().keys.addAll(groupKeys)
        }
        if (groupKeys.isNotEmpty()) markDirty()
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
        if (activeKey?.id == key.id) {
            activeKey = null
            refreshTab("Key")
        }
        Toast.makeText(this, "“${key.label}” moved to Trash", Toast.LENGTH_SHORT).show()
    }

    private fun restoreFromTrash(item: TrashItem) {
        trash.remove(item)
        saveTrash()
        val rowIdx = item.row.coerceIn(0, def.rows.size - 1)
        def.rows[rowIdx].keys.add(item.col.coerceIn(0, def.rows[rowIdx].keys.size), item.key)
        markDirty()
        rebuildTrashStrip()
        rebuildPreview()
        Toast.makeText(this, "“${item.key.label}” restored", Toast.LENGTH_SHORT).show()
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

    private fun rebuildTrashStrip() {
        val strip = trashStrip ?: return
        strip.removeAllViews()
        if (trash.isEmpty()) {
            strip.addView(TextView(this).apply {
                text = "Nothing here yet — deleted keys wait here"
                textSize = 13f
                typeface = Ui.font(this@EditorActivity)
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

    // ------------------------------------------------------------------
    // SHARED UI HELPERS
    // ------------------------------------------------------------------

    private fun cardTitle(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 13f
            typeface = Ui.fontSemibold(this@EditorActivity)
            letterSpacing = 0.06f
            setTextColor(palette.secondary)
            setPadding(dp(4), dp(14), dp(4), dp(2))
        })
    }

    private fun sectionCard(parent: LinearLayout, title: String): LinearLayout {
        cardTitle(parent, title)
        return Ui.addCard(this, palette, parent)
    }

    private fun smallLabel(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.text)
            setPadding(0, dp(8), 0, dp(2))
        })
    }

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

    /**
     * Chips for imported assets (fonts / images / sounds) plus a "+" import
     * chip that opens the Android system file picker.
     */
    private fun addAssetChips(
        parent: LinearLayout,
        kind: String,
        includeNone: Boolean,
        currentId: String?,
        onPick: (String?) -> Unit
    ) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(row)

        if (includeNone) {
            row.addView(
                Ui.compactButton(this, palette, if (kind == "font") "Default" else "None") {
                    onPick(null)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }

        Assets.list(this, kind).forEach { asset ->
            val selected = asset.id == currentId
            val chip = Ui.compactButton(this, palette, asset.name.take(14)) {
                onPick(asset.id)
            }
            if (selected) {
                chip.background = Ui.rounded(this, palette.accent, 14)
                chip.setTextColor(palette.accentText)
            }
            row.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }

        // "+" import chip (system file picker)
        val addChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = Ui.rounded(this@EditorActivity, palette.chipCustom, 14)
            setOnClickListener { importAsset(kind) }
            val img = ImageView(this@EditorActivity).apply {
                setImageResource(R.drawable.ic_add)
                drawable?.setTint(palette.accent)
            }
            addView(img, LinearLayout.LayoutParams(dp(15), dp(15)))
        }
        row.addView(
            addChip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        parent.addView(scroll)
    }

    // ------------------------------------------------------------------
    // KEY TAB (single key)
    // ------------------------------------------------------------------

    private fun buildKeyPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val key = activeKey

        if (key == null) {
            page.addView(TextView(this).apply {
                text = "Tap any key from preview to start editing."
                textSize = 15f
                typeface = Ui.font(this@EditorActivity)
                gravity = Gravity.CENTER
                setTextColor(palette.secondary)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
            return page
        }

        val style = key.style ?: KeyVisual()

        // ---- header row ----
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        head.addView(
            TextView(this).apply {
                text = "Editing “${key.label.take(12)}”"
                textSize = 16f
                typeface = Ui.fontSemibold(this@EditorActivity)
                setTextColor(palette.text)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        head.addView(
            Ui.compactButton(this, palette, "Done") {
                activeKey = null
                refreshTab("Key")
                rebuildPreview()
            }
        )
        page.addView(head)

        // ================= BASICS =================
        val basics = sectionCard(page, "BASICS")

        val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labelInput = Ui.editText(this, palette, "Label")
        labelInput.setText(key.label)
        labelRow.addView(
            labelInput,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
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
        basics.addView(labelRow)

        val outputInput = Ui.editText(this, palette, "Types text (empty = label)")
        outputInput.setText(key.output)
        basics.addView(outputInput)

        val longPressInput = Ui.editText(this, palette, "Long-press types")
        longPressInput.setText(key.longPressOutput)
        basics.addView(longPressInput)

        val alternatesInput = Ui.editText(this, palette, "Long-press chars (hint + popup)")
        alternatesInput.setText(key.alternates)
        basics.addView(alternatesInput)

        // apply text fields when the user finishes editing them
        labelInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateKeyDef { k -> k.copy(label = labelInput.text.toString()) }
        }
        outputInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateKeyDef { k -> k.copy(output = outputInput.text.toString()) }
        }
        longPressInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateKeyDef { k -> k.copy(longPressOutput = longPressInput.text.toString()) }
        }
        alternatesInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateKeyDef { k -> k.copy(alternates = alternatesInput.text.toString()) }
        }

        var widthPercent = (key.weight * 100).toInt().coerceIn(40, 400)
        Ui.seekRow(this, palette, basics, "Width", 40, 400, widthPercent, true, { "$it%" }, {
            widthPercent = it
            updateKeyDef { k -> k.copy(weight = it / 100f) }
        }, default = 100)

        var heightPercent = (key.heightFactor * 100).toInt().coerceIn(70, 140)
        Ui.seekRow(this, palette, basics, "Height", 70, 140, heightPercent, true, { "$it%" }, {
            heightPercent = it
            updateKeyDef { k -> k.copy(heightFactor = it / 100f) }
        }, default = 100)

        Ui.switchRow(this, palette, basics, "Repeat while held", key.repeatOnHold) {
            updateKeyDef { k -> k.copy(repeatOnHold = it) }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        actionRow.addView(
            Ui.compactButton(this, palette, "＋ Add new key…") { showAddKeyDialog(key) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        actionRow.addView(
            Ui.compactButton(this, palette, "🗑 Delete", danger = true) {
                moveToTrash(key)
                markDirty()
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        basics.addView(actionRow)

        val moveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        moveRow.setPadding(0, dp(8), 0, 0)
        listOf("◀" to (0 to -1), "▲" to (-1 to 0), "▼" to (1 to 0), "▶" to (0 to 1))
            .forEach { (label, delta) ->
                moveRow.addView(
                    Ui.compactButton(this, palette, label) {
                        moveKey(key, delta.first, delta.second)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6)
                    }
                )
            }
        basics.addView(moveRow)

        // ================= BACKGROUND =================
        val bgCard = sectionCard(page, "BACKGROUND")

        smallLabel(bgCard, "Key color (first = theme)")
        addSwatchRow(bgCard, swatchColors) { picked ->
            updateKeyDef { k -> k.copy(color = picked) }
        }

        // ================= SHAPE & BORDER =================
        val shapeCard = sectionCard(page, "SHAPE & BORDER")
        // filled in by fillShapeCard (shared with batch pages)

        // ================= TEXT =================
        val textCard = sectionCard(page, "TEXT")

        // ================= EFFECTS =================
        val fxCard = sectionCard(page, "EFFECTS")

        // ================= FEEDBACK =================
        val fbCard = sectionCard(page, "KEY SOUND & VIBRATION")

        fillBackgroundCard(bgCard, key)
        fillShapeCard(shapeCard, key)
        fillTextCard(textCard, key)
        fillEffectsCard(fxCard, key)
        fillFeedbackCard(fbCard, key)

        return page
    }

    /** Applies a KeyDef-level change to the active key (live). */
    private fun updateKeyDef(transform: (KeyDef) -> KeyDef) {
        val key = activeKey ?: return
        val pos = positionOf(key) ?: return
        val updated = transform(def.rows[pos.first].keys[pos.second])
        def.rows[pos.first].keys[pos.second] = updated
        activeKey = updated
        markDirty()
        rebuildPreview()
    }

    /** Applies a style-level change to the active key (live). */
    private fun updateStyle(transform: (KeyVisual) -> KeyVisual) {
        updateKeyDef { k -> k.copy(style = transform(k.style ?: KeyVisual())) }
    }

    private fun fillBackgroundCard(card: LinearLayout, key: KeyDef) {
        val style = key.style ?: KeyVisual()

        Ui.switchRow(this, palette, card, "Gradient", style.gradient) {
            updateStyle { s -> s.copy(gradient = it) }
        }
        smallLabel(card, "Gradient second color")
        addSwatchRow(card, swatchColors.filter { it != null }) { c ->
            updateStyle { s -> s.copy(gradientColor = c, gradient = true) }
        }
        Ui.seekRow(this, palette, card, "Gradient angle", 0, 355, style.gradientAngleDeg, true,
            { "$it°" }, { updateStyle { s -> s.copy(gradientAngleDeg = it) } }, default = 0)

        smallLabel(card, "Background image")
        addAssetChips(card, Assets.KIND_IMAGE, true, style.imageId) { id ->
            updateStyle { s -> s.copy(imageId = id) }
        }
        Ui.seekRow(this, palette, card, "Image zoom", 50, 300, style.imageScalePercent, true,
            { "$it%" }, { updateStyle { s -> s.copy(imageScalePercent = it) } }, default = 100)
        Ui.seekRow(this, palette, card, "Image opacity", 0, 100, style.imageAlphaPercent, true,
            { "$it%" }, { updateStyle { s -> s.copy(imageAlphaPercent = it) } }, default = 100)
        Ui.seekRow(this, palette, card, "Image blur", 0, 14, style.imageBlurDp, true,
            { "$it dp" }, { updateStyle { s -> s.copy(imageBlurDp = it) } }, default = 0)

        Ui.seekRow(this, palette, card, "Key transparency", 10, 100,
            style.opacityPercent ?: 100, true, { "$it%" }, {
                updateStyle { s -> s.copy(opacityPercent = it) }
            }, default = 100)
    }

    private fun fillShapeCard(card: LinearLayout, key: KeyDef) {
        val style = key.style ?: KeyVisual()

        Ui.seekRow(this, palette, card, "Corner radius", 0, 22, style.cornerRadiusDp ?: 8, true,
            { "$it dp" }, { updateStyle { s -> s.copy(cornerRadiusDp = it) } }, default = 8)

        smallLabel(card, "Border color (first = none)")
        addSwatchRow(card, swatchColors) { c ->
            updateStyle { s -> s.copy(borderColor = c) }
        }
        Ui.seekRow(this, palette, card, "Border thickness", 0, 5, style.borderWidthDp ?: 1, true,
            { "$it dp" }, { updateStyle { s -> s.copy(borderWidthDp = it) } }, default = 1)

        smallLabel(card, "Border sides")
        val sidesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sideDefs = listOf("All" to 15, "Top" to 1, "Right" to 2, "Bottom" to 4, "Left" to 8)
        val sideChips = ArrayList<TextView>()
        fun restyleSides() {
            val sides = (activeKey?.style ?: KeyVisual()).borderSides ?: 15
            sideDefs.forEachIndexed { i, (_, bit) ->
                val on = if (bit == 15) sides == 15 else (sides and bit) != 0
                sideChips[i].background = Ui.rounded(this, if (on) palette.accent else palette.tinted, 12)
                sideChips[i].setTextColor(if (on) palette.accentText else palette.text)
            }
        }
        sideDefs.forEach { (name, bit) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 12f
                typeface = Ui.fontMedium(this@EditorActivity)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setTextColor(palette.text)
                setOnClickListener {
                    val current = (activeKey?.style ?: KeyVisual()).borderSides ?: 15
                    val next = if (bit == 15) 15 else current xor bit
                    updateStyle { s -> s.copy(borderSides = next) }
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
        card.addView(sidesRow)

        Ui.switchRow(this, palette, card, "Drop shadow", style.shadow) {
            updateStyle { s -> s.copy(shadow = it) }
        }
        val softRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val softChips = ArrayList<TextView>()
        fun restyleSoft() {
            val soft = (activeKey?.style ?: KeyVisual()).shadowSoft
            softChips[0].background = Ui.rounded(this, if (soft) palette.accent else palette.tinted, 12)
            softChips[0].setTextColor(if (soft) palette.accentText else palette.text)
            softChips[1].background = Ui.rounded(this, if (!soft) palette.accent else palette.tinted, 12)
            softChips[1].setTextColor(if (!soft) palette.accentText else palette.text)
        }
        listOf("Soft (blurred)" to true, "Hard (solid)" to false).forEach { (name, soft) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 13f
                typeface = Ui.fontMedium(this@EditorActivity)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(9), dp(12), dp(9))
                setTextColor(palette.text)
                setOnClickListener {
                    updateStyle { s -> s.copy(shadowSoft = soft, shadow = true) }
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
        card.addView(softRow)
        Ui.seekRow(this, palette, card, "Shadow angle", 0, 355, style.shadowAngleDeg, true,
            { "$it°" }, { updateStyle { s -> s.copy(shadowAngleDeg = it) } }, default = 315)
        Ui.seekRow(this, palette, card, "Shadow distance", 0, 10, style.shadowDistanceDp, true,
            { "$it dp" }, { updateStyle { s -> s.copy(shadowDistanceDp = it) } }, default = 3)
        Ui.seekRow(this, palette, card, "Shadow blur", 0, 14, style.shadowBlurDp, true,
            { "$it dp" }, { updateStyle { s -> s.copy(shadowBlurDp = it) } }, default = 4)

        Ui.switchRow(this, palette, card, "Glow", style.glow) {
            updateStyle { s -> s.copy(glow = it) }
        }
        smallLabel(card, "Glow color")
        addSwatchRow(card, swatchColors.filter { it != null }) { c ->
            updateStyle { s -> s.copy(glowColor = c, glow = true) }
        }
        Ui.seekRow(this, palette, card, "Glow blur", 2, 24, style.glowBlurDp, true,
            { "$it dp" }, { updateStyle { s -> s.copy(glowBlurDp = it) } }, default = 8)

        Ui.switchRow(this, palette, card, "Inner shadow", style.innerShadow) {
            updateStyle { s -> s.copy(innerShadow = it) }
        }
    }

    private fun fillTextCard(card: LinearLayout, key: KeyDef) {
        val style = key.style ?: KeyVisual()

        smallLabel(card, "Font")
        addAssetChips(card, Assets.KIND_FONT, true, style.fontId) { id ->
            updateStyle { s -> s.copy(fontId = id) }
        }

        Ui.seekRow(this, palette, card, "Text size", 10, 30, style.textSizeSp ?: 16, true,
            { "$it sp" }, { updateStyle { s -> s.copy(textSizeSp = it) } }, default = 16)

        Ui.switchRow(this, palette, card, "Bold", style.bold ?: false) {
            updateStyle { s -> s.copy(bold = it) }
        }
        Ui.switchRow(this, palette, card, "Italic", style.italic ?: false) {
            updateStyle { s -> s.copy(italic = it) }
        }

        smallLabel(card, "Text color (first = theme)")
        addSwatchRow(card, swatchColors) { c ->
            updateStyle { s -> s.copy(textColor = c) }
        }

        Ui.seekRow(this, palette, card, "Letter spacing", -5, 30, style.letterSpacing ?: 0, true,
            { "${it / 100f} em" }, { updateStyle { s -> s.copy(letterSpacing = it) } }, default = 0)
        Ui.seekRow(this, palette, card, "Text rotation", -180, 180, style.textRotationDeg ?: 0, true,
            { "$it°" }, { updateStyle { s -> s.copy(textRotationDeg = it) } }, default = 0)
        Ui.seekRow(this, palette, card, "Text opacity", 10, 100, style.textOpacityPercent ?: 100, true,
            { "$it%" }, { updateStyle { s -> s.copy(textOpacityPercent = it) } }, default = 100)

        Ui.switchRow(this, palette, card, "Text shadow", style.textShadow) {
            updateStyle { s -> s.copy(textShadow = it) }
        }
        smallLabel(card, "Text shadow color")
        addSwatchRow(card, swatchColors.filter { it != null }) { c ->
            updateStyle { s -> s.copy(textShadowColor = c, textShadow = true) }
        }
        Ui.seekRow(this, palette, card, "Text shadow blur", 0, 12, style.textShadowBlurDp, true,
            { "$it dp" }, { updateStyle { s -> s.copy(textShadowBlurDp = it) } }, default = 2)
        Ui.seekRow(this, palette, card, "Text shadow X", -8, 8, style.textShadowDx, true,
            { "$it dp" }, { updateStyle { s -> s.copy(textShadowDx = it) } }, default = 1)
        Ui.seekRow(this, palette, card, "Text shadow Y", -8, 8, style.textShadowDy, true,
            { "$it dp" }, { updateStyle { s -> s.copy(textShadowDy = it) } }, default = 1)

        smallLabel(card, "Text position")
        val posRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val posDefs = listOf("●" to 0, "▲" to 1, "▼" to 2, "◀" to 3, "▶" to 4)
        val posChips = ArrayList<TextView>()
        fun restylePos() {
            val pos = (activeKey?.style ?: KeyVisual()).textPosition ?: 0
            posDefs.forEachIndexed { i, (_, value) ->
                val on = pos == value
                posChips[i].background = Ui.rounded(this, if (on) palette.accent else palette.tinted, 12)
                posChips[i].setTextColor(if (on) palette.accentText else palette.text)
            }
        }
        posDefs.forEach { (name, value) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 14f
                typeface = Ui.fontMedium(this@EditorActivity)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(9), dp(10), dp(9))
                setTextColor(palette.text)
                setOnClickListener {
                    updateStyle { s -> s.copy(textPosition = value) }
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
        card.addView(posRow)
    }

    private fun fillEffectsCard(card: LinearLayout, key: KeyDef) {
        val style = key.style ?: KeyVisual()

        Ui.seekRow(this, palette, card, "Key rotation", -45, 45, style.rotationDeg ?: 0, true,
            { "$it°" }, { updateStyle { s -> s.copy(rotationDeg = it) } }, default = 0)
        Ui.seekRow(this, palette, card, "Key scale", 50, 150, style.scalePercent ?: 100, true,
            { "$it%" }, { updateStyle { s -> s.copy(scalePercent = it) } }, default = 100)
        Ui.seekRow(this, palette, card, "Horizontal padding", 0, 14, style.paddingH ?: 0, true,
            { "$it dp" }, { updateStyle { s -> s.copy(paddingH = it) } }, default = 0)
        Ui.seekRow(this, palette, card, "Vertical padding", 0, 14, style.paddingV ?: 0, true,
            { "$it dp" }, { updateStyle { s -> s.copy(paddingV = it) } }, default = 0)

        smallLabel(card, "Pressed color")
        addSwatchRow(card, swatchColors) { c ->
            updateStyle { s -> s.copy(pressedColor = c) }
        }
        Ui.seekRow(this, palette, card, "Pressed scale", 70, 100, style.pressedScalePercent ?: 97, true,
            { "$it%" }, { updateStyle { s -> s.copy(pressedScalePercent = it) } }, default = 97)
    }

    private fun fillFeedbackCard(card: LinearLayout, key: KeyDef) {
        val style = key.style ?: KeyVisual()

        smallLabel(card, "Sound style (first = default)")
        val soundScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val soundRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        soundScroll.addView(soundRow)
        val currentSound = style.soundStyle

        soundRow.addView(
            Ui.compactButton(this, palette, "Default") {
                updateStyle { s -> s.copy(soundStyle = null, soundAssetId = null) }
                refreshTab("Key")
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        )
        KeySounds.NAMES.forEachIndexed { index, name ->
            val chip = Ui.compactButton(this, palette, name) {
                updateStyle { s -> s.copy(soundStyle = index, soundAssetId = null) }
                sampleSound(index)
                refreshTab("Key")
            }
            if (currentSound == index && style.soundAssetId == null) {
                chip.background = Ui.rounded(this, palette.accent, 14)
                chip.setTextColor(palette.accentText)
            }
            soundRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }
        card.addView(soundScroll)

        smallLabel(card, "Imported sounds")
        addAssetChips(card, Assets.KIND_SOUND, true, style.soundAssetId) { id ->
            updateStyle { s -> s.copy(soundAssetId = id) }
            id?.let { assetId ->
                Assets.byId(this, assetId)?.let {
                    sounds?.playAsset(Assets.path(this, it).absolutePath, Prefs.soundVolume(this).coerceAtLeast(30))
                }
            }
        }

        val hasCustomVib = style.vibrationPercent != null
        Ui.switchRow(this, palette, card, "Custom vibration", hasCustomVib) { on ->
            updateStyle { s -> s.copy(vibrationPercent = if (on) Prefs.vibrationStrength(this) else null) }
        }
        Ui.seekRow(this, palette, card, "Vibration strength", 0, 100,
            style.vibrationPercent ?: Prefs.vibrationStrength(this), hasCustomVib, { "$it%" }, {
                updateStyle { s -> s.copy(vibrationPercent = it) }
            }, default = Prefs.vibrationStrength(this))
    }

    // ------------------------------------------------------------------
    // SELECTED KEYS / ALL KEYS TABS (batch, live, property-wise)
    // ------------------------------------------------------------------

    /** Keys the batch tab currently targets. */
    private fun batchTargets(all: Boolean): List<KeyDef> =
        if (all) def.rows.flatMap { r -> r.keys.toList() }
        else def.rows.flatMap { r -> r.keys.filter { selectedIds.contains(it.id) } }

    /** Applies a style transform to every target key — only the changed property is written. */
    private fun applyToTargets(all: Boolean, transform: (KeyVisual) -> KeyVisual) {
        val targets = if (all) null else selectedIds
        def.rows.forEach { row ->
            row.keys.forEachIndexed { i, k ->
                if (targets == null || targets.contains(k.id)) {
                    row.keys[i] = k.copy(style = transform(k.style ?: KeyVisual()))
                }
            }
        }
        if (activeKey != null) {
            activeKey = findKeyById(activeKey!!.id)
        }
        markDirty()
        rebuildPreview()
    }

    /** Key color lives on KeyDef (not KeyVisual) — batch version. */
    private fun applyKeyColorToTargets(all: Boolean, color: Int?) {
        val targets = if (all) null else selectedIds
        def.rows.forEach { row ->
            row.keys.forEachIndexed { i, k ->
                if (targets == null || targets.contains(k.id)) {
                    row.keys[i] = k.copy(color = color)
                }
            }
        }
        if (activeKey != null) activeKey = findKeyById(activeKey!!.id)
        markDirty()
        rebuildPreview()
    }

    private fun buildSelectedPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (selectedIds.isEmpty()) {
            page.addView(TextView(this).apply {
                text = "Select multiple keys from preview to start batch editing."
                textSize = 15f
                typeface = Ui.font(this@EditorActivity)
                gravity = Gravity.CENTER
                setTextColor(palette.secondary)
                setPadding(dp(8), dp(24), dp(8), dp(24))
            })
            return page
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        head.addView(
            TextView(this).apply {
                text = "${selectedIds.size} keys selected"
                textSize = 16f
                typeface = Ui.fontSemibold(this@EditorActivity)
                setTextColor(palette.text)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        head.addView(Ui.compactButton(this, palette, "All") {
            def.rows.forEach { r -> r.keys.forEach { selectedIds.add(it.id) } }
            updateBatchBar(); rebuildPreview(); refreshTab("Selected")
        })
        head.addView(Ui.compactButton(this, palette, "Clear") {
            selectedIds.clear()
            updateBatchBar(); rebuildPreview(); refreshTab("Selected")
        })
        page.addView(head)

        page.addView(TextView(this).apply {
            text = "Only the properties you change below are overwritten — every key keeps its own actions, sounds and other settings."
            textSize = 12f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        })

        buildBatchControls(page, all = false)
        return page
    }

    private fun buildAllKeysPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        page.addView(TextView(this).apply {
            text = "Customize every key at once — individual settings are kept unless you change that property here."
            textSize = 12f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(dp(4), dp(12), dp(4), dp(4))
        })

        buildBatchControls(page, all = true)

        val dangerCard = Ui.addCard(this, palette, page)
        dangerCard.addView(
            Ui.compactButton(this, palette, "Restore default formatting for all keys", danger = true) {
                confirmResetAllStyles()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return page
    }

    private fun buildBatchControls(page: LinearLayout, all: Boolean) {
        // ---- background ----
        val bgCard = sectionCard(page, if (all) "BACKGROUND — ALL KEYS" else "BACKGROUND")

        smallLabel(bgCard, "Key color (first = keep theme)")
        addSwatchRow(bgCard, swatchColors) { c ->
            applyKeyColorToTargets(all, c)
        }

        Ui.switchRow(this, palette, bgCard, "Gradient", false) { on ->
            if (on) applyToTargets(all) { s -> s.copy(gradient = true) }
            else applyToTargets(all) { s -> s.copy(gradient = false) }
        }
        smallLabel(bgCard, "Gradient second color")
        addSwatchRow(bgCard, swatchColors.filter { it != null }) { c ->
            applyToTargets(all) { s -> s.copy(gradientColor = c, gradient = true) }
        }

        smallLabel(bgCard, "Background image")
        addAssetChips(bgCard, Assets.KIND_IMAGE, true, null) { id ->
            if (id == null) applyToTargets(all) { s -> s.copy(imageId = null) }
            else applyToTargets(all) { s -> s.copy(imageId = id) }
        }

        Ui.seekRow(this, palette, bgCard, "Image zoom", 50, 300, 100, true, { "$it%" }, {
            applyToTargets(all) { s -> s.copy(imageScalePercent = it) }
        }, default = 100)
        Ui.seekRow(this, palette, bgCard, "Image opacity", 0, 100, 100, true, { "$it%" }, {
            applyToTargets(all) { s -> s.copy(imageAlphaPercent = it) }
        }, default = 100)
        Ui.seekRow(this, palette, bgCard, "Image blur", 0, 14, 0, true, { "$it dp" }, {
            applyToTargets(all) { s -> s.copy(imageBlurDp = it) }
        }, default = 0)

        Ui.seekRow(this, palette, bgCard, "Key transparency", 10, 100, 100, true, { "$it%" }, {
            applyToTargets(all) { s -> s.copy(opacityPercent = it) }
        }, default = 100)

        // ---- shape ----
        val shapeCard = sectionCard(page, if (all) "SHAPE & BORDER — ALL KEYS" else "SHAPE & BORDER")
        Ui.seekRow(this, palette, shapeCard, "Corner radius", 0, 22, 8, true, { "$it dp" }, {
            applyToTargets(all) { s -> s.copy(cornerRadiusDp = it) }
        }, default = 8)
        smallLabel(shapeCard, "Border color (first = none)")
        addSwatchRow(shapeCard, swatchColors) { c ->
            applyToTargets(all) { s -> s.copy(borderColor = c) }
        }
        Ui.seekRow(this, palette, shapeCard, "Border thickness", 0, 5, 1, true, { "$it dp" }, {
            applyToTargets(all) { s -> s.copy(borderWidthDp = it) }
        }, default = 1)
        Ui.switchRow(this, palette, shapeCard, "Drop shadow", false) {
            applyToTargets(all) { s -> s.copy(shadow = it) }
        }
        Ui.switchRow(this, palette, shapeCard, "Glow", false) {
            applyToTargets(all) { s -> s.copy(glow = it) }
        }

        // ---- text ----
        val textCard = sectionCard(page, if (all) "TEXT — ALL KEYS" else "TEXT")
        smallLabel(textCard, "Font")
        addAssetChips(textCard, Assets.KIND_FONT, true, null) { id ->
            applyToTargets(all) { s -> s.copy(fontId = id) }
        }
        Ui.seekRow(this, palette, textCard, "Text size", 10, 30, 16, true, { "$it sp" }, {
            applyToTargets(all) { s -> s.copy(textSizeSp = it) }
        }, default = 16)
        Ui.switchRow(this, palette, textCard, "Bold", false) {
            applyToTargets(all) { s -> s.copy(bold = it) }
        }
        Ui.switchRow(this, palette, textCard, "Italic", false) {
            applyToTargets(all) { s -> s.copy(italic = it) }
        }
        smallLabel(textCard, "Text color (first = theme)")
        addSwatchRow(textCard, swatchColors) { c ->
            applyToTargets(all) { s -> s.copy(textColor = c) }
        }

        // ---- feedback ----
        val fbCard = sectionCard(page, if (all) "SOUND & VIBRATION — ALL KEYS" else "SOUND & VIBRATION")
        smallLabel(fbCard, "Sound style (first = default)")
        val soundScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val soundRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        soundScroll.addView(soundRow)
        soundRow.addView(
            Ui.compactButton(this, palette, "Default") {
                applyToTargets(all) { s -> s.copy(soundStyle = null, soundAssetId = null) }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        )
        KeySounds.NAMES.forEachIndexed { index, name ->
            soundRow.addView(
                Ui.compactButton(this, palette, name) {
                    applyToTargets(all) { s -> s.copy(soundStyle = index, soundAssetId = null) }
                    sampleSound(index)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )
        }
        fbCard.addView(soundScroll)

        smallLabel(fbCard, "Imported sounds")
        addAssetChips(fbCard, Assets.KIND_SOUND, true, null) { id ->
            if (id == null) {
                applyToTargets(all) { s -> s.copy(soundAssetId = null) }
            } else {
                applyToTargets(all) { s -> s.copy(soundAssetId = id) }
                Assets.byId(this, id)?.let {
                    sounds?.playAsset(Assets.path(this, it).absolutePath, Prefs.soundVolume(this).coerceAtLeast(30))
                }
            }
        }

        Ui.switchRow(this, palette, fbCard, "Custom vibration", false) { on ->
            if (on) {
                applyToTargets(all) { s -> s.copy(vibrationPercent = Prefs.vibrationStrength(this)) }
            } else {
                applyToTargets(all) { s -> s.copy(vibrationPercent = null) }
            }
        }
        Ui.seekRow(this, palette, fbCard, "Vibration strength", 0, 100,
            Prefs.vibrationStrength(this), true, { "$it%" }, {
                applyToTargets(all) { s -> s.copy(vibrationPercent = it) }
            }, default = Prefs.vibrationStrength(this))
    }

    // ------------------------------------------------------------------
    // KEYBOARD TAB
    // ------------------------------------------------------------------

    private fun buildKeyboardPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- keyboard background ----
        val bgCard = sectionCard(page, "KEYBOARD BACKGROUND")

        smallLabel(bgCard, "Background color (first = theme)")
        addSwatchRow(bgCard, swatchColors) { c ->
            Prefs.setKbBgColor(this, c ?: 0)
            markDirty()
            rebuildPreview()
        }

        Ui.switchRow(this, palette, bgCard, "Gradient", Prefs.kbGradient(this)) {
            Prefs.setKbGradient(this, it)
            markDirty()
            rebuildPreview()
        }
        smallLabel(bgCard, "Gradient second color")
        addSwatchRow(bgCard, swatchColors.filter { it != null }) { c ->
            Prefs.setKbGradientColor2(this, c ?: 0xFF2C2C2E.toInt())
            Prefs.setKbGradient(this, true)
            markDirty()
            rebuildPreview()
        }
        Ui.seekRow(this, palette, bgCard, "Gradient angle", 0, 355, Prefs.kbGradientAngle(this), true,
            { "$it°" }, {
                Prefs.setKbGradientAngle(this, it)
                markDirty()
                rebuildPreview()
            }, default = 0)

        val imageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        imageRow.addView(
            Ui.compactButton(this, palette, "Choose image") { pickKeyboardImage() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        imageRow.addView(
            Ui.compactButton(this, palette, "Remove", danger = true) {
                Prefs.setBgImagePath(this, null)
                Prefs.setBgBlur(this, 0)
                markDirty()
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        bgCard.addView(imageRow)

        smallLabel(bgCard, "Image from library")
        addAssetChips(bgCard, Assets.KIND_IMAGE, false, null) { id ->
            val asset = Assets.byId(this, id)
            if (asset != null) {
                Prefs.setBgImagePath(this, Assets.path(this, asset).absolutePath)
                markDirty()
                rebuildPreview()
            }
        }

        val hasImage = Prefs.bgImagePath(this) != null
        Ui.seekRow(this, palette, bgCard, "Image blur", 0, 25, Prefs.bgBlur(this), hasImage,
            { "$it" }, {
                Prefs.setBgBlur(this, it)
                markDirty()
                rebuildPreview()
            }, default = 0)
        Ui.seekRow(this, palette, bgCard, "Image overlay (readability)", 0, 100,
            Prefs.kbBgOverlayPercent(this), hasImage, { "$it%" }, {
                Prefs.setKbBgOverlayPercent(this, it)
                markDirty()
                rebuildPreview()
            }, default = 55)
        Ui.seekRow(this, palette, bgCard, "Keyboard transparency", 30, 100,
            Prefs.keyboardOpacity(this), true, { "$it%" }, {
                Prefs.setKeyboardOpacity(this, it)
                markDirty()
                rebuildPreview()
            }, default = 100)

        // ---- surface ----
        val surfaceCard = sectionCard(page, "SURFACE")
        smallLabel(surfaceCard, "Border color (first = none)")
        addSwatchRow(surfaceCard, swatchColors) { c ->
            Prefs.setKbBorderColor(this, c ?: 0)
            markDirty()
            rebuildPreview()
        }
        Ui.seekRow(this, palette, surfaceCard, "Border width", 0, 6, Prefs.kbBorderWidthDp(this), true,
            { "$it dp" }, {
                Prefs.setKbBorderWidthDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 0)
        Ui.seekRow(this, palette, surfaceCard, "Corner radius", 0, 26, Prefs.kbRadiusDp(this), true,
            { "$it dp" }, {
                Prefs.setKbRadiusDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 0)

        // ---- typing ----
        val typingCard = sectionCard(page, "TYPING")
        Ui.switchRow(this, palette, typingCard, "Long-press hints", Prefs.showLongPressHints(this)) {
            Prefs.setShowLongPressHints(this, it)
            markDirty()
            rebuildPreview()
        }
        Ui.seekRow(this, palette, typingCard, "Press zoom", 80, 100,
            Prefs.pressScalePercent(this), true, { "$it%" }, {
                Prefs.setPressScalePercent(this, it)
                markDirty()
            }, default = 97)
        Ui.switchRow(this, palette, typingCard, "Press preview popup", Prefs.previewEnabled(this)) {
            Prefs.setPreviewEnabled(this, it)
            markDirty()
        }
        Ui.seekRow(this, palette, typingCard, "Preview size", 36, 96,
            Prefs.previewSizeDp(this), true, { "$it dp" }, {
                Prefs.setPreviewSizeDp(this, it)
                markDirty()
            }, default = 56)
        Ui.seekRow(this, palette, typingCard, "Preview corner radius", 0, 30,
            Prefs.previewRadiusDp(this), true, { "$it dp" }, {
                Prefs.setPreviewRadiusDp(this, it)
                markDirty()
            }, default = 10)
        Ui.seekRow(this, palette, typingCard, "Preview linger", 0, 800,
            Prefs.previewLingerMs(this), true, { "$it ms" }, {
                Prefs.setPreviewLingerMs(this, it)
                markDirty()
            }, default = 120)
        smallLabel(typingCard, "Preview color (first = auto)")
        addSwatchRow(typingCard, listOf<Int?>(
            0, 0xFF3A3A3C.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C1C1E.toInt(),
            0xFF0A84FF.toInt(), 0xFF34C759.toInt(), 0xFFFFCC00.toInt(),
            0xFFFF3B30.toInt(), 0xFFAF52DE.toInt()
        )) { color ->
            Prefs.setPreviewColor(this, color ?: 0)
            markDirty()
        }

        // ---- default key font ----
        val fontCard = sectionCard(page, "DEFAULT KEY FONT")
        addAssetChips(fontCard, Assets.KIND_FONT, true, null) { id ->
            val asset = Assets.byId(this, id)
            Prefs.setCustomFontPath(this, asset?.let { Assets.path(this, it).absolutePath })
            markDirty()
            rebuildPreview()
        }

        return page
    }

    // ------------------------------------------------------------------
    // EMOJI TAB
    // ------------------------------------------------------------------

    private fun buildEmojiPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val previewCard = sectionCard(page, "EMOJI PREVIEW")
        val emojiPreview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        previewCard.addView(emojiPreview)

        fun rebuildEmojiPreview() {
            emojiPreview.removeAllViews()
            val columns = Prefs.emojiColumns(this).coerceIn(4, 12)
            val rowH = dp(Prefs.emojiRowHeightDp(this).coerceIn(30, 64))
            val spacing = dp(Prefs.emojiSpacingDp(this).coerceIn(0, 12))
            val sp = Prefs.emojiSizeSp(this).coerceIn(12, 40).toFloat()
            val samples = Emojis.CATEGORIES.first().second.take(columns * 2)
            var i = 0
            while (i < samples.size) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (j in 0 until columns) {
                    val idx = i + j
                    row.addView(
                        TextView(this).apply {
                            textSize = sp
                            gravity = Gravity.CENTER
                            if (idx < samples.size) text = samples[idx]
                        },
                        LinearLayout.LayoutParams(0, rowH, 1f).apply {
                            setMargins(spacing / 2, spacing / 2, spacing / 2, spacing / 2)
                        }
                    )
                }
                emojiPreview.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, rowH
                    )
                )
                i += columns
            }
        }
        rebuildEmojiPreview()

        val controlsCard = sectionCard(page, "EMOJI SETTINGS")
        Ui.seekRow(this, palette, controlsCard, "Emoji size", 12, 40, Prefs.emojiSizeSp(this), true,
            { "$it sp" }, {
                Prefs.setEmojiSizeSp(this, it)
                markDirty()
                rebuildEmojiPreview()
            }, default = 24)
        Ui.seekRow(this, palette, controlsCard, "Grid columns", 4, 12, Prefs.emojiColumns(this), true,
            { "$it" }, {
                Prefs.setEmojiColumns(this, it)
                markDirty()
                rebuildEmojiPreview()
            }, default = 8)
        Ui.seekRow(this, palette, controlsCard, "Row height", 30, 64, Prefs.emojiRowHeightDp(this), true,
            { "$it dp" }, {
                Prefs.setEmojiRowHeightDp(this, it)
                markDirty()
                rebuildEmojiPreview()
            }, default = 42)
        Ui.seekRow(this, palette, controlsCard, "Spacing", 0, 12, Prefs.emojiSpacingDp(this), true,
            { "$it dp" }, {
                Prefs.setEmojiSpacingDp(this, it)
                markDirty()
                rebuildEmojiPreview()
            }, default = 2)

        page.addView(TextView(this).apply {
            text = "Recents and categories stay as they are — these settings apply to the keyboard's emoji page."
            textSize = 12f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        })
        return page
    }

    // ------------------------------------------------------------------
    // TOOLBAR TAB
    // ------------------------------------------------------------------

    private fun buildToolbarPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val mainCard = sectionCard(page, "TOOLBAR")
        mainCard.addView(TextView(this).apply {
            text = "An optional strip above the keys with quick actions."
            textSize = 13f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(6))
        })

        Ui.switchRow(this, palette, mainCard, "Show toolbar", Prefs.toolbarEnabled(this)) {
            Prefs.setToolbarEnabled(this, it)
            markDirty()
        }
        Ui.seekRow(this, palette, mainCard, "Toolbar height", 24, 64, Prefs.toolbarHeightDp(this), true,
            { "$it dp" }, {
                Prefs.setToolbarHeightDp(this, it)
                markDirty()
            }, default = 36)
        Ui.seekRow(this, palette, mainCard, "Icon size", 9, 22, Prefs.toolbarIconSizeSp(this), true,
            { "$it sp" }, {
                Prefs.setToolbarIconSizeSp(this, it)
                markDirty()
            }, default = 13)

        smallLabel(mainCard, "Icon color (first = auto)")
        addSwatchRow(mainCard, swatchColors) { c ->
            Prefs.setToolbarIconColor(this, c ?: 0)
            markDirty()
        }
        smallLabel(mainCard, "Toolbar background (first = transparent)")
        addSwatchRow(mainCard, swatchColors) { c ->
            Prefs.setToolbarBgColor(this, c ?: 0)
            markDirty()
        }

        // ---- button order & visibility ----
        val orderCard = sectionCard(page, "BUTTONS")
        val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        orderCard.addView(listHolder)

        fun rebuildButtonList() {
            listHolder.removeAllViews()
            val buttons = Prefs.toolbarButtons(this)
            buttons.forEachIndexed { index, btn ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                fun miniChip(label: String, onClick: () -> Unit) {
                    row.addView(
                        Ui.compactButton(this, palette, label, false, onClick),
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                miniChip("◀") {
                    if (index > 0) {
                        val list = Prefs.toolbarButtons(this)
                        val moved = list.removeAt(index)
                        list.add(index - 1, moved)
                        Prefs.setToolbarButtons(this, list)
                        markDirty()
                        rebuildButtonList()
                    }
                }
                row.addView(
                    TextView(this).apply {
                        text = toolbarButtonNames[btn.id] ?: btn.id
                        textSize = 15f
                        typeface = Ui.font(this@EditorActivity)
                        setTextColor(palette.text)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(8); marginEnd = dp(8)
                    }
                )
                miniChip(if (btn.on) "On" else "Off") {
                    val list = Prefs.toolbarButtons(this)
                    list[index] = Prefs.ToolbarButton(btn.id, !btn.on)
                    Prefs.setToolbarButtons(this, list)
                    markDirty()
                    rebuildButtonList()
                }
                miniChip("▶") {
                    if (index < buttons.size - 1) {
                        val list = Prefs.toolbarButtons(this)
                        val moved = list.removeAt(index)
                        list.add(index + 1, moved)
                        Prefs.setToolbarButtons(this, list)
                        markDirty()
                        rebuildButtonList()
                    }
                }
                listHolder.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
        rebuildButtonList()

        orderCard.addView(
            Ui.compactButton(this, palette, "Restore default toolbar", danger = true) {
                Prefs.resetToolbarButtons(this)
                markDirty()
                rebuildButtonList()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        return page
    }

    // ------------------------------------------------------------------
    // SOUND TAB
    // ------------------------------------------------------------------

    private fun buildSoundPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val card = sectionCard(page, "KEY SOUNDS")
        Ui.switchRow(this, palette, card, "Key sounds", Prefs.soundEnabled(this)) {
            Prefs.setSoundEnabled(this, it)
            markDirty()
        }
        Ui.seekRow(this, palette, card, "Volume", 0, 100, Prefs.soundVolume(this), true, { "$it%" }, {
            Prefs.setSoundVolume(this, it)
            markDirty()
        }, default = 60)

        smallLabel(card, "Sound style — tap to hear a sample")
        val stylesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val stylesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stylesScroll.addView(stylesRow)
        card.addView(stylesScroll)

        val customPitchHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val customDurationHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun restyleStyleChips() {
            stylesRow.removeAllViews()
            val current = Prefs.soundStyle(this)
            KeySounds.NAMES.forEachIndexed { index, name ->
                val selected = current == index
                val chip = Ui.compactButton(this, palette, name) {
                    Prefs.setSoundStyle(this, index)
                    markDirty()
                    if (Prefs.soundEnabled(this)) sampleSound(index)
                    restyleStyleChips()
                }
                if (selected) {
                    chip.background = Ui.rounded(this, palette.accent, 14)
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
            // "+" custom synth slot
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
                    markDirty()
                    if (Prefs.soundEnabled(this@EditorActivity)) sampleSound(KeySounds.STYLE_CUSTOM)
                    restyleStyleChips()
                }
                val img = ImageView(this@EditorActivity).apply {
                    setImageResource(R.drawable.ic_add)
                    drawable?.setTint(if (customSelected) palette.accentText else palette.text)
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
            Prefs.customSoundPitch(this), true, { "$it%" }, {
                Prefs.setCustomSoundPitch(this, it)
                markDirty()
                refreshCustomSound()
            }, default = 100)
        Ui.seekRow(this, palette, customDurationHolder, "Custom length", 15, 90,
            Prefs.customSoundDuration(this), true, { "$it ms" }, {
                Prefs.setCustomSoundDuration(this, it)
                markDirty()
                refreshCustomSound()
            }, default = 40)
        card.addView(customPitchHolder)
        card.addView(customDurationHolder)
        restyleStyleChips()

        // ---- imported sounds ----
        val importCard = sectionCard(page, "IMPORTED SOUNDS")
        importCard.addView(TextView(this).apply {
            text = "MP3 · WAV · OGG — apply per key in the Key, Selected or All Keys tab."
            textSize = 12f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(4))
        })

        val listHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        importCard.addView(listHolder)

        fun rebuildSoundList() {
            listHolder.removeAllViews()
            val soundsList = Assets.list(this, Assets.KIND_SOUND)
            if (soundsList.isEmpty()) {
                listHolder.addView(TextView(this).apply {
                    text = "No imported sounds yet"
                    textSize = 13f
                    typeface = Ui.font(this@EditorActivity)
                    setTextColor(palette.secondary)
                    setPadding(0, dp(6), 0, dp(6))
                })
            }
            soundsList.forEach { asset ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(
                    TextView(this).apply {
                        text = asset.name
                        textSize = 15f
                        typeface = Ui.font(this@EditorActivity)
                        setTextColor(palette.text)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(
                    Ui.compactButton(this, palette, "▶") {
                        sounds?.playAsset(
                            Assets.path(this, asset).absolutePath,
                            Prefs.soundVolume(this).coerceAtLeast(30)
                        )
                    }
                )
                row.addView(
                    Ui.compactButton(this, palette, "Rename") { showRenameAssetDialog(asset) { rebuildSoundList() } },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(6) }
                )
                row.addView(
                    Ui.compactButton(this, palette, "Delete", danger = true) {
                        Assets.delete(this, asset)
                        rebuildSoundList()
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(6) }
                )
                listHolder.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }
        rebuildSoundList()

        Ui.button(this, palette, importCard, "＋ Import sound", filled = false) {
            importAsset(Assets.KIND_SOUND)
        }

        return page
    }

    // ------------------------------------------------------------------
    // VIBRATION TAB
    // ------------------------------------------------------------------

    private fun buildVibrationPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val card = sectionCard(page, "VIBRATION")
        Ui.switchRow(this, palette, card, "Vibration", Prefs.vibrationEnabled(this)) {
            Prefs.setVibrationEnabled(this, it)
            markDirty()
        }
        Ui.seekRow(this, palette, card, "Intensity", 5, 100, Prefs.vibrationStrength(this), true,
            { "$it%" }, {
                Prefs.setVibrationStrength(this, it)
                markDirty()
            }, default = 40)
        Ui.seekRow(this, palette, card, "Duration", 10, 100, Prefs.vibrationDurationMs(this), true,
            { "$it ms" }, {
                Prefs.setVibrationDurationMs(this, it)
                markDirty()
            }, default = 30)

        smallLabel(card, "Patterns")
        val patternRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "Light" to (18 to 18),
            "Medium" to (40 to 30),
            "Strong" to (80 to 55)
        ).forEach { (name, pair) ->
            patternRow.addView(
                Ui.compactButton(this, palette, name) {
                    Prefs.setVibrationStrength(this, pair.first)
                    Prefs.setVibrationDurationMs(this, pair.second)
                    markDirty()
                    previewVibration()
                    refreshTab("Vibration")
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
            )
        }
        card.addView(patternRow)

        Ui.button(this, palette, card, "Preview vibration") { previewVibration() }

        page.addView(TextView(this).apply {
            text = "Per-key vibration is available in the Key, Selected and All Keys tabs."
            textSize = 12f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        })
        return page
    }

    private fun previewVibration() {
        try {
            val vibrator = (
                if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Vibrator::class.java)
                }
                ) ?: return
            val strength = Prefs.vibrationStrength(this)
            val ms = Prefs.vibrationDurationMs(this).coerceIn(10, 100).toLong()
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(ms, (strength * 255 / 100).coerceIn(60, 255))
                )
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // LAYOUT TAB
    // ------------------------------------------------------------------

    private fun buildLayoutPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val sizeCard = sectionCard(page, "SIZE & SPACING")
        Ui.seekRow(this, palette, sizeCard, "Key height", 40, 62, Prefs.keyHeightDp(this), true,
            { "$it dp" }, {
                Prefs.setKeyHeightDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 48)
        Ui.seekRow(this, palette, sizeCard, "Key spacing", 0, 8, Prefs.keyGapDp(this), true,
            { "$it dp" }, {
                Prefs.setKeyGapDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 3)
        Ui.seekRow(this, palette, sizeCard, "Row spacing", 0, 14, Prefs.rowGapDp(this), true,
            { "$it dp" }, {
                Prefs.setRowGapDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 3)
        Ui.seekRow(this, palette, sizeCard, "Horizontal padding", 0, 24,
            Prefs.contentPaddingHDp(this), true, { "$it dp" }, {
                Prefs.setContentPaddingHDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 3)
        Ui.seekRow(this, palette, sizeCard, "Vertical padding", 0, 24,
            Prefs.contentPaddingVDp(this), true, { "$it dp" }, {
                Prefs.setContentPaddingVDp(this, it)
                markDirty()
                rebuildPreview()
            }, default = 4)
        Ui.seekRow(this, palette, sizeCard, "Keyboard width", 60, 100,
            Prefs.keyboardWidthPercent(this), true, { "$it%" }, {
                Prefs.setKeyboardWidthPercent(this, it)
                markDirty()
            }, default = 100)

        smallLabel(sizeCard, "Row alignment")
        val alignRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Left" to 0, "Center" to 1, "Right" to 2).forEach { (name, value) ->
            alignRow.addView(
                Ui.compactButton(this, palette, name) {
                    Prefs.setRowAlignment(this, value)
                    markDirty()
                    rebuildPreview()
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                }
            )
        }
        sizeCard.addView(alignRow)

        val rowsCard = sectionCard(page, "ROWS")
        Ui.switchRow(this, palette, rowsCard, "Number row (123…)", Prefs.numberRowEnabled(this)) {
            Prefs.setNumberRowEnabled(this, it)
            markDirty()
        }
        val rowButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        rowButtons.addView(
            Ui.compactButton(this, palette, "+ Row") {
                def.rows.add(RowDef(mutableListOf(newKey("a"))))
                markDirty()
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        )
        rowButtons.addView(
            Ui.compactButton(this, palette, "− Row", danger = true) {
                if (def.rows.size <= 1) {
                    Toast.makeText(this, "At least one row is required", Toast.LENGTH_SHORT).show()
                    return@compactButton
                }
                val last = def.rows.removeAt(def.rows.size - 1)
                last.keys.forEach { moveToTrashWithoutRemove(it, def.rows.size, 0) }
                saveTrash()
                rebuildTrashStrip()
                markDirty()
                rebuildPreview()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        rowsCard.addView(rowButtons)

        val trashCard = sectionCard(page, "TRASH")
        trashCard.addView(TextView(this).apply {
            text = "Tap to restore a deleted key"
            textSize = 12f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(4))
        })
        val trashScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        trashStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        trashScroll.addView(trashStrip)
        trashCard.addView(trashScroll)
        rebuildTrashStrip()

        return page
    }

    private fun moveToTrashWithoutRemove(key: KeyDef, row: Int, col: Int) {
        trash.add(0, TrashItem(key, row, col))
        if (trash.size > 30) trash.removeAt(trash.size - 1)
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
        markDirty()
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
                typeface = Ui.fontMedium(this@EditorActivity)
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
            text = "Where should it go? (relative to “${sourceKey.label.take(6)}”)"
            textSize = 13f
            typeface = Ui.font(this@EditorActivity)
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
                typeface = Ui.fontMedium(this@EditorActivity)
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
                    type, label.ifEmpty { "•" }, outputInput.text.toString(), 1f,
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
            markDirty()
            rebuildPreview()
            Toast.makeText(this, "Key added — tap Apply to keep", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    // ------------------------------------------------------------------
    // SOUND HELPERS
    // ------------------------------------------------------------------

    private fun refreshCustomSound() {
        if (sounds == null) sounds = KeySounds(this)
        sounds?.refreshCustom()
    }

    private fun sampleSound(style: Int) {
        if (sounds == null) sounds = KeySounds(this)
        sounds?.play(style, Prefs.soundVolume(this).coerceAtLeast(30))
    }

    // ------------------------------------------------------------------
    // ASSET IMPORT
    // ------------------------------------------------------------------

    private fun importAsset(kind: String) {
        val mime = when (kind) {
            Assets.KIND_FONT -> "*/*"
            Assets.KIND_IMAGE -> "image/*"
            else -> "audio/*"
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mime
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val code = when (kind) {
            Assets.KIND_FONT -> 110
            Assets.KIND_IMAGE -> 111
            else -> 112
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "Choose a file"), code)
    }

    private fun pickKeyboardImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, "Choose background image"), 101)
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
                    markDirty()
                    rebuildPreview()
                    Toast.makeText(this, "Background updated", Toast.LENGTH_SHORT).show()
                }
            }
            110 -> importResult(uri, Assets.KIND_FONT)
            111 -> importResult(uri, Assets.KIND_IMAGE)
            112 -> importResult(uri, Assets.KIND_SOUND)
        }
    }

    private fun importResult(uri: Uri, kind: String) {
        val asset = Assets.import(this, uri, kind)
        if (asset == null) {
            val valid = when (kind) {
                Assets.KIND_FONT -> ".ttf / .otf"
                Assets.KIND_IMAGE -> ".png / .jpg / .webp"
                else -> ".mp3 / .wav / .ogg"
            }
            Toast.makeText(this, "Could not import — please pick a $valid file", Toast.LENGTH_SHORT).show()
            return
        }
        markDirty()
        Toast.makeText(this, "“${asset.name}” imported ✓", Toast.LENGTH_SHORT).show()
        // rebuild the current tab so the new chip appears
        refreshTab(currentTab)
    }

    private fun showRenameAssetDialog(asset: Assets.Asset, onChange: () -> Unit) {
        val dialog = Ui.CustomDialog(this, palette, "Rename")
        val input = Ui.editText(this, palette, "New name")
        input.setText(asset.name)
        dialog.body.addView(input)
        Ui.button(this, palette, dialog.body, "Save") {
            Assets.rename(this, asset, input.text.toString())
            dialog.dialog.dismiss()
            onChange()
        }
        dialog.show()
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

    // ------------------------------------------------------------------
    // BOTTOM ACTION BAR: RESET · CANCEL · PRESET · APPLY
    // ------------------------------------------------------------------

    private fun buildBottomBar(parent: LinearLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.card)
            setPadding(dp(10), dp(6), dp(10), dp(8))
        }

        // presets row (existing feature, kept visible)
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

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        Ui.button(this, palette, actionsRow, "Reset", filled = false, matchWidth = false) {
            showResetDialog()
        }
        actionsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        Ui.button(this, palette, actionsRow, "Cancel", filled = false, matchWidth = false) {
            showCancelDialog()
        }
        actionsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        Ui.button(this, palette, actionsRow, "Preset", filled = false, matchWidth = false) {
            showPresetPicker()
        }
        actionsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        Ui.button(this, palette, actionsRow, "Apply", filled = true, matchWidth = false) {
            applyChanges()
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

    private fun applyChanges() {
        Layouts.save(this, def)
        dirty = false
        Toast.makeText(this, "Applied ✓", Toast.LENGTH_SHORT).show()
    }

    private fun showResetDialog() {
        val dialog = Ui.CustomDialog(this, palette, "Reset")
        dialog.body.addView(TextView(this).apply {
            text = "What should be restored?"
            textSize = 14f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        Ui.button(this, palette, dialog.body, "Reset layout (QWERTY)") {
            dialog.dialog.dismiss()
            confirmResetLayout()
        }
        Ui.button(this, palette, dialog.body, "Reset all key formatting", filled = false) {
            dialog.dialog.dismiss()
            confirmResetAllStyles()
        }
    }

    private fun confirmResetLayout() {
        val dialog = Ui.CustomDialog(this, palette, "Reset layout?")
        dialog.body.addView(TextView(this).apply {
            text = "This restores the default QWERTY layout. Presets and the Trash are kept."
            textSize = 14f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        val row = LinearLayout(this)
        Ui.button(this, palette, row, "Reset", filled = true, matchWidth = false) {
            def = Layouts.defaultLetters()
            var next = 1L
            def.rows.forEach { rowDef ->
                val withIds = rowDef.keys.map {
                    if (it.id == 0L) it.copy(id = System.nanoTime() + (next++)) else it
                }
                rowDef.keys.clear()
                rowDef.keys.addAll(withIds)
            }
            activeKey = null
            markDirty()
            rebuildPreview()
            refreshTab("Key")
            dialog.dialog.dismiss()
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        Ui.button(this, palette, row, "Cancel", filled = false, matchWidth = false) {
            dialog.dialog.dismiss()
        }
        dialog.body.addView(row)
        dialog.show()
    }

    private fun confirmResetAllStyles() {
        val dialog = Ui.CustomDialog(this, palette, "Reset all key formatting?")
        dialog.body.addView(TextView(this).apply {
            text = "Every key goes back to the theme's default look. Actions, labels and layout stay."
            textSize = 14f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        val row = LinearLayout(this)
        Ui.button(this, palette, row, "Reset", filled = true, matchWidth = false) {
            def.rows.forEach { rowDef ->
                rowDef.keys.forEachIndexed { i, k ->
                    rowDef.keys[i] = k.copy(color = null, style = null)
                }
            }
            markDirty()
            rebuildPreview()
            refreshTab("Key")
            refreshTab("Selected")
            dialog.dialog.dismiss()
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        Ui.button(this, palette, row, "Cancel", filled = false, matchWidth = false) {
            dialog.dialog.dismiss()
        }
        dialog.body.addView(row)
        dialog.show()
    }

    private fun showCancelDialog() {
        if (!dirty) {
            Toast.makeText(this, "No unsaved changes", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Ui.CustomDialog(this, palette, "Discard changes?")
        dialog.body.addView(TextView(this).apply {
            text = "This restores the keyboard to its last applied state."
            textSize = 14f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(8))
        })
        val row = LinearLayout(this)
        Ui.button(this, palette, row, "Discard", filled = true, matchWidth = false) {
            dialog.dialog.dismiss()
            performCancel()
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        Ui.button(this, palette, row, "Keep editing", filled = false, matchWidth = false) {
            dialog.dialog.dismiss()
        }
        dialog.body.addView(row)
        dialog.show()
    }

    private fun performCancel() {
        prefsSnapshot?.let { Prefs.restore(this, it) }
        dirty = false
        Toast.makeText(this, "Changes discarded", Toast.LENGTH_SHORT).show()
        // recreate the editor so every control re-reads the restored state
        recreate()
    }

    // ---------------- presets ----------------

    private fun rebuildPresetsRow() {
        presetsRow.removeAllViews()
        val presets = Prefs.presets(this)

        val addChip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = Ui.rounded(this@EditorActivity, palette.accent, 14)
            setOnClickListener { showNewPresetDialog() }
            val img = ImageView(this@EditorActivity).apply {
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
                Ui.compactButton(this, palette, preset.name) { showPresetActions(preset) },
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
                typeface = Ui.font(this@EditorActivity)
                setTextColor(palette.secondary)
                setPadding(0, dp(9), 0, dp(9))
            })
        }
    }

    private fun showPresetPicker() {
        val presets = Prefs.presets(this)
        if (presets.isEmpty()) {
            showNewPresetDialog()
            return
        }
        val dialog = Ui.CustomDialog(this, palette, "Presets")
        presets.forEach { preset ->
            Ui.button(this, palette, dialog.body, preset.name, filled = false) {
                dialog.dialog.dismiss()
                showPresetActions(preset)
            }
        }
        Ui.button(this, palette, dialog.body, "＋ Save current as preset") {
            dialog.dialog.dismiss()
            showNewPresetDialog()
        }
        dialog.show()
    }

    private fun showPresetActions(preset: Prefs.Preset) {
        val dialog = Ui.CustomDialog(this, palette, preset.name)
        dialog.body.addView(TextView(this).apply {
            text = "Load this preset into the editor, or delete it."
            textSize = 14f
            typeface = Ui.font(this@EditorActivity)
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

    private fun loadPreset(preset: Prefs.Preset) {
        Layouts.fromJson(preset.layoutJson)?.let { loaded ->
            def = loaded
        }
        Prefs.setKeyHeightDp(this, preset.keyHeightDp)
        Prefs.setKeyGapDp(this, preset.keyGapDp)
        Prefs.setKeyboardOpacity(this, preset.opacity)
        Prefs.setExtraBottomDp(this, preset.extraBottomDp)
        Prefs.setBgBlur(this, preset.bgBlur)
        activeKey = null
        selectedIds.clear()
        selectMode = false
        updateBatchBar()
        markDirty()
        rebuildPreview()
        tabPages.clear()
        switchTab("Key")
        Toast.makeText(this, "Loaded \"${preset.name}\" — tap Apply to keep", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------
    // EXIT DIALOG
    // ------------------------------------------------------------------

    private fun showExitDialog() {
        val dialog = Ui.CustomDialog(this, palette, "Save your changes?")
        dialog.body.addView(TextView(this).apply {
            text = "You have unsaved changes."
            textSize = 15f
            typeface = Ui.font(this@EditorActivity)
            setTextColor(palette.secondary)
            setPadding(0, 0, 0, dp(10))
        })
        val row = LinearLayout(this)
        Ui.button(this, palette, row, "No", filled = false, matchWidth = false) {
            dialog.dialog.dismiss()
            finish()
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        Ui.button(this, palette, row, "Yes, Save", filled = true, matchWidth = false) {
            dialog.dialog.dismiss()
            Layouts.save(this, def)
            dirty = false
            finish()
        }
        dialog.body.addView(row)
        dialog.show()
    }
}
