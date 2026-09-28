package com.customkey.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for every user setting.
 * Everything is stored locally in SharedPreferences — nothing ever leaves the device.
 */
object Prefs {

    private const val FILE = "customkey_prefs"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------- feedback ----------------

    fun soundEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("sound_enabled", false)

    fun setSoundEnabled(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("sound_enabled", value).apply()
    }

    /** 0..9 presets · 10 = custom */
    fun soundStyle(ctx: Context): Int =
        sp(ctx).getInt("sound_style", 0)

    fun setSoundStyle(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("sound_style", value).apply()
    }

    fun customSoundPitch(ctx: Context): Int = sp(ctx).getInt("custom_sound_pitch", 100)
    fun setCustomSoundPitch(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("custom_sound_pitch", v).apply()
    }

    fun customSoundDuration(ctx: Context): Int = sp(ctx).getInt("custom_sound_duration", 40)
    fun setCustomSoundDuration(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("custom_sound_duration", v).apply()
    }

    fun soundVolume(ctx: Context): Int = sp(ctx).getInt("sound_volume", 60)

    fun setSoundVolume(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("sound_volume", value).apply()
    }

    fun vibrationEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("vibration_enabled", true)

    fun setVibrationEnabled(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("vibration_enabled", value).apply()
    }

    fun vibrationStrength(ctx: Context): Int =
        sp(ctx).getInt("vibration_strength", 40)

    fun setVibrationStrength(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("vibration_strength", value).apply()
    }

    /** Vibration length in ms (10 – 100). */
    fun vibrationDurationMs(ctx: Context): Int =
        sp(ctx).getInt("vibration_duration", 30)

    fun setVibrationDurationMs(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("vibration_duration", value).apply()
    }

    // ---------------- keyboard behaviour ----------------

    /** Long-press space → move the cursor like a trackpad. */
    fun trackpadEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("trackpad_enabled", true)

    fun setTrackpadEnabled(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("trackpad_enabled", value).apply()
    }

    /** Show the small long-press hint characters on keys. */
    fun showLongPressHints(ctx: Context): Boolean =
        sp(ctx).getBoolean("show_hints", true)

    fun setShowLongPressHints(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("show_hints", value).apply()
    }

    // ---------------- key press / preview ----------------

    /** Press animation scale in percent (80–100). */
    fun pressScalePercent(ctx: Context): Int =
        sp(ctx).getInt("press_scale", 97)

    fun setPressScalePercent(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("press_scale", value).apply()
    }

    fun previewEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("preview_enabled", true)

    fun setPreviewEnabled(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("preview_enabled", value).apply()
    }

    /** 0 = auto (theme decides), otherwise ARGB color. */
    fun previewColor(ctx: Context): Int =
        sp(ctx).getInt("preview_color", 0)

    fun setPreviewColor(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("preview_color", value).apply()
    }

    fun previewRadiusDp(ctx: Context): Int = sp(ctx).getInt("preview_radius", 10)
    fun setPreviewRadiusDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("preview_radius", v).apply()
    }

    fun previewSizeDp(ctx: Context): Int = sp(ctx).getInt("preview_size", 56)
    fun setPreviewSizeDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("preview_size", v).apply()
    }

    /** How long the preview lingers after the finger lifts (ms). */
    fun previewLingerMs(ctx: Context): Int = sp(ctx).getInt("preview_linger", 120)
    fun setPreviewLingerMs(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("preview_linger", v).apply()
    }

    // ---------------- keyboard appearance ----------------

    /** Absolute key height in dp (40 – 62). Migrates the old height factor. */
    fun keyHeightDp(ctx: Context): Int {
        val prefs = sp(ctx)
        if (prefs.contains("key_height_dp")) return prefs.getInt("key_height_dp", 48)
        val factor = prefs.getFloat("height_factor", 1.0f)
        return (48f * factor).toInt().coerceIn(40, 62)
    }

    fun setKeyHeightDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("key_height_dp", value).apply()
    }

    /** Horizontal gap between keys, dp. */
    fun keyGapDp(ctx: Context): Int = sp(ctx).getInt("key_gap_dp", 3)

    fun setKeyGapDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("key_gap_dp", value).apply()
    }

    /** Vertical gap between rows, dp. -1 = follow [keyGapDp]. */
    fun rowGapDp(ctx: Context): Int {
        val v = sp(ctx).getInt("row_gap_dp", -1)
        return if (v < 0) keyGapDp(ctx) else v
    }

    fun setRowGapDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("row_gap_dp", value).apply()
    }

    fun keyboardOpacity(ctx: Context): Int = sp(ctx).getInt("keyboard_opacity", 100)

    fun setKeyboardOpacity(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("keyboard_opacity", value).apply()
    }

    fun extraBottomDp(ctx: Context): Int = sp(ctx).getInt("extra_bottom_dp", 0)

    fun setExtraBottomDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("extra_bottom_dp", value).apply()
    }

    /** Blur radius for the keyboard background image (0 – 25). */
    fun bgBlur(ctx: Context): Int = sp(ctx).getInt("bg_blur", 0)

    fun setBgBlur(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("bg_blur", value).apply()
    }

    fun bgImagePath(ctx: Context): String? = sp(ctx).getString("bg_image_path", null)

    fun setBgImagePath(ctx: Context, path: String?) {
        sp(ctx).edit().putString("bg_image_path", path).apply()
    }

    /** Optional custom .ttf used for every key label. */
    fun customFontPath(ctx: Context): String? = sp(ctx).getString("custom_font_path", null)

    fun setCustomFontPath(ctx: Context, path: String?) {
        sp(ctx).edit().putString("custom_font_path", path).apply()
    }

    // ---------------- keyboard background (v1.4) ----------------

    /** 0 = theme default, else ARGB color. */
    fun kbBgColor(ctx: Context): Int = sp(ctx).getInt("kb_bg_color", 0)
    fun setKbBgColor(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_bg_color", v).apply()
    }

    fun kbGradient(ctx: Context): Boolean = sp(ctx).getBoolean("kb_gradient", false)
    fun setKbGradient(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("kb_gradient", v).apply()
    }

    fun kbGradientColor2(ctx: Context): Int = sp(ctx).getInt("kb_gradient_color2", 0xFF2C2C2E.toInt())
    fun setKbGradientColor2(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_gradient_color2", v).apply()
    }

    /** 0 – 360 degrees. */
    fun kbGradientAngle(ctx: Context): Int = sp(ctx).getInt("kb_gradient_angle", 0)
    fun setKbGradientAngle(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_gradient_angle", v).apply()
    }

    /** 0 = none, else ARGB. */
    fun kbBorderColor(ctx: Context): Int = sp(ctx).getInt("kb_border_color", 0)
    fun setKbBorderColor(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_border_color", v).apply()
    }

    fun kbBorderWidthDp(ctx: Context): Int = sp(ctx).getInt("kb_border_width", 0)
    fun setKbBorderWidthDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_border_width", v).apply()
    }

    /** Rounded corners of the whole keyboard surface, dp (0 = square). */
    fun kbRadiusDp(ctx: Context): Int = sp(ctx).getInt("kb_radius", 0)
    fun setKbRadiusDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_radius", v).apply()
    }

    /** Readability scrim over the keyboard background image, 0 – 100 %. */
    fun kbBgOverlayPercent(ctx: Context): Int = sp(ctx).getInt("kb_bg_overlay", 55)
    fun setKbBgOverlayPercent(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_bg_overlay", v).apply()
    }

    // ---------------- layout (v1.4) ----------------

    /** Keyboard content width, 60 – 100 % of the screen. */
    fun keyboardWidthPercent(ctx: Context): Int = sp(ctx).getInt("kb_width_percent", 100)
    fun setKeyboardWidthPercent(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("kb_width_percent", v).apply()
    }

    /** 0 left · 1 center · 2 right. */
    fun rowAlignment(ctx: Context): Int = sp(ctx).getInt("row_alignment", 1)
    fun setRowAlignment(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("row_alignment", v).apply()
    }

    fun contentPaddingHDp(ctx: Context): Int = sp(ctx).getInt("content_pad_h", 3)
    fun setContentPaddingHDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("content_pad_h", v).apply()
    }

    fun contentPaddingVDp(ctx: Context): Int = sp(ctx).getInt("content_pad_v", 4)
    fun setContentPaddingVDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("content_pad_v", v).apply()
    }

    /** Extra 1234567890 row on top of the letters page. */
    fun numberRowEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("number_row", false)
    fun setNumberRowEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("number_row", v).apply()
    }

    // ---------------- emoji panel (v1.4) ----------------

    fun emojiSizeSp(ctx: Context): Int = sp(ctx).getInt("emoji_size", 24)
    fun setEmojiSizeSp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("emoji_size", v).apply()
    }

    fun emojiColumns(ctx: Context): Int = sp(ctx).getInt("emoji_columns", 8)
    fun setEmojiColumns(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("emoji_columns", v).apply()
    }

    fun emojiRowHeightDp(ctx: Context): Int = sp(ctx).getInt("emoji_row_height", 42)
    fun setEmojiRowHeightDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("emoji_row_height", v).apply()
    }

    fun emojiSpacingDp(ctx: Context): Int = sp(ctx).getInt("emoji_spacing", 2)
    fun setEmojiSpacingDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("emoji_spacing", v).apply()
    }

    // ---------------- toolbar (v1.4) ----------------

    fun toolbarEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("toolbar_enabled", false)
    fun setToolbarEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean("toolbar_enabled", v).apply()
    }

    fun toolbarHeightDp(ctx: Context): Int = sp(ctx).getInt("toolbar_height", 36)
    fun setToolbarHeightDp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("toolbar_height", v).apply()
    }

    fun toolbarIconSizeSp(ctx: Context): Int = sp(ctx).getInt("toolbar_icon_size", 13)
    fun setToolbarIconSizeSp(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("toolbar_icon_size", v).apply()
    }

    /** 0 = auto (theme), else ARGB. */
    fun toolbarIconColor(ctx: Context): Int = sp(ctx).getInt("toolbar_icon_color", 0)
    fun setToolbarIconColor(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("toolbar_icon_color", v).apply()
    }

    /** 0 = transparent over the keyboard background, else ARGB. */
    fun toolbarBgColor(ctx: Context): Int = sp(ctx).getInt("toolbar_bg_color", 0)
    fun setToolbarBgColor(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("toolbar_bg_color", v).apply()
    }

    data class ToolbarButton(val id: String, val on: Boolean)

    fun toolbarButtons(ctx: Context): MutableList<ToolbarButton> {
        val defaults = listOf(
            "cl", "cr", "copy", "paste", "all", "emoji", "hide"
        )
        val list = mutableListOf<ToolbarButton>()
        try {
            val arr = JSONArray(sp(ctx).getString("toolbar_buttons", "") ?: "")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ToolbarButton(o.optString("id"), o.optBoolean("on", true)))
            }
        } catch (_: Exception) {
        }
        if (list.isEmpty()) {
            defaults.forEach { list.add(ToolbarButton(it, true)) }
        } else {
            // keep any new built-in buttons that are missing from the stored list
            listOf("cu", "cd", "cut", "next").forEach { id ->
                if (list.none { it.id == id }) list.add(ToolbarButton(id, false))
            }
        }
        return list
    }

    fun setToolbarButtons(ctx: Context, buttons: List<ToolbarButton>) {
        val arr = JSONArray()
        buttons.forEach { arr.put(JSONObject().put("id", it.id).put("on", it.on)) }
        sp(ctx).edit().putString("toolbar_buttons", arr.toString()).apply()
    }

    fun resetToolbarButtons(ctx: Context) {
        sp(ctx).edit().remove("toolbar_buttons").apply()
    }

    // ---------------- custom layout ----------------

    fun layoutJson(ctx: Context): String? = sp(ctx).getString("layout_json", null)

    fun setLayoutJson(ctx: Context, json: String) {
        sp(ctx).edit().putString("layout_json", json).apply()
    }

    fun clearLayout(ctx: Context) {
        sp(ctx).edit().remove("layout_json").apply()
    }

    fun layoutVersion(ctx: Context): Int = sp(ctx).getInt("layout_version", 1)

    fun bumpLayoutVersion(ctx: Context) {
        sp(ctx).edit().putInt("layout_version", layoutVersion(ctx) + 1).apply()
    }

    // ---------------- presets ----------------

    fun presetsJson(ctx: Context): String = sp(ctx).getString("presets_json", "[]") ?: "[]"

    fun setPresetsJson(ctx: Context, json: String) {
        sp(ctx).edit().putString("presets_json", json).apply()
    }

    // ---------------- deleted keys (trash) ----------------

    fun deletedKeysJson(ctx: Context): String = sp(ctx).getString("deleted_keys_json", "[]") ?: "[]"

    fun setDeletedKeysJson(ctx: Context, json: String) {
        sp(ctx).edit().putString("deleted_keys_json", json).apply()
    }

    // ---------------- emoji recents ----------------

    fun recentEmojis(ctx: Context): List<String> =
        (sp(ctx).getString("recent_emojis", "") ?: "").split(",").filter { it.isNotEmpty() }

    fun pushRecentEmoji(ctx: Context, emoji: String) {
        val list = ArrayList(recentEmojis(ctx))
        list.remove(emoji)
        list.add(0, emoji)
        while (list.size > 24) list.removeAt(list.size - 1)
        sp(ctx).edit().putString("recent_emojis", list.joinToString(",")).apply()
    }

    // ---------------- editor snapshot (Cancel support) ----------------

    /** Full snapshot of every setting — restored by the editor's Cancel button. */
    fun snapshot(ctx: Context): HashMap<String, Any> {
        val out = HashMap<String, Any>()
        sp(ctx).all.forEach { (k, v) ->
            if (v != null) out[k] = v
        }
        return out
    }

    fun restore(ctx: Context, snap: Map<String, Any>) {
        val e = sp(ctx).edit().clear()
        snap.forEach { (k, v) ->
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Set<*> -> @Suppress("UNCHECKED_CAST") e.putStringSet(k, v as Set<String>)
            }
        }
        e.apply()
    }

    // ---------------- presets helpers ----------------

    data class Preset(
        val name: String,
        val layoutJson: String,
        val keyHeightDp: Int,
        val keyGapDp: Int,
        val opacity: Int,
        val extraBottomDp: Int,
        val bgBlur: Int
    )

    fun presets(ctx: Context): MutableList<Preset> {
        val list = mutableListOf<Preset>()
        return try {
            val array = JSONArray(presetsJson(ctx))
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list.add(
                    Preset(
                        name = o.optString("name", "Preset"),
                        layoutJson = o.optString("layout"),
                        keyHeightDp = o.optInt("h", 48),
                        keyGapDp = o.optInt("g", 3),
                        opacity = o.optInt("o", 100),
                        extraBottomDp = o.optInt("b", 0),
                        bgBlur = o.optInt("bl", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            list
        }
    }

    private fun presetToJson(preset: Preset): JSONObject = JSONObject()
        .put("name", preset.name)
        .put("layout", preset.layoutJson)
        .put("h", preset.keyHeightDp)
        .put("g", preset.keyGapDp)
        .put("o", preset.opacity)
        .put("b", preset.extraBottomDp)
        .put("bl", preset.bgBlur)

    fun savePreset(ctx: Context, preset: Preset) {
        val list = presets(ctx)
        // Replace presets with the same name
        list.removeAll { it.name == preset.name }
        list.add(preset)
        while (list.size > 12) list.removeAt(0)
        val array = JSONArray()
        list.forEach { array.put(presetToJson(it)) }
        setPresetsJson(ctx, array.toString())
    }

    fun deletePreset(ctx: Context, name: String) {
        val list = presets(ctx)
        list.removeAll { it.name == name }
        val array = JSONArray()
        list.forEach { array.put(presetToJson(it)) }
        setPresetsJson(ctx, array.toString())
    }
}
