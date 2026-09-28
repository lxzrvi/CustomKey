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

    /** Press animation scale in percent (90–100). */
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

    fun keyGapDp(ctx: Context): Int = sp(ctx).getInt("key_gap_dp", 3)

    fun setKeyGapDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("key_gap_dp", value).apply()
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

    fun savePreset(ctx: Context, preset: Preset) {
        val list = presets(ctx)
        // Replace presets with the same name
        list.removeAll { it.name == preset.name }
        list.add(preset)
        while (list.size > 12) list.removeAt(0)
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("layout", it.layoutJson)
                    .put("h", it.keyHeightDp)
                    .put("g", it.keyGapDp)
                    .put("o", it.opacity)
                    .put("b", it.extraBottomDp)
                    .put("bl", it.bgBlur)
            )
        }
        setPresetsJson(ctx, array.toString())
    }

    fun deletePreset(ctx: Context, name: String) {
        val list = presets(ctx)
        list.removeAll { it.name == name }
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("layout", it.layoutJson)
                    .put("h", it.keyHeightDp)
                    .put("g", it.keyGapDp)
                    .put("o", it.opacity)
                    .put("b", it.extraBottomDp)
                    .put("bl", it.bgBlur)
            )
        }
        setPresetsJson(ctx, array.toString())
    }
}
