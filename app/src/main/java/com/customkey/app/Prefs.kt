package com.customkey.app

import android.content.Context

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

    /** 0 Tap · 1 Pop · 2 Click · 3 Wood · 4 Bubble */
    fun soundStyle(ctx: Context): Int =
        sp(ctx).getInt("sound_style", 0)

    fun setSoundStyle(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("sound_style", value).apply()
    }

    fun soundVolume(ctx: Context): Int =
        sp(ctx).getInt("sound_volume", 60)

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

    /** Visual gap between keys in dp (2 – 8). */
    fun keyGapDp(ctx: Context): Int =
        sp(ctx).getInt("key_gap_dp", 3)

    fun setKeyGapDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("key_gap_dp", value).apply()
    }

    /** Keyboard opacity in percent (40 – 100). Lower = more transparent. */
    fun keyboardOpacity(ctx: Context): Int =
        sp(ctx).getInt("keyboard_opacity", 100)

    fun setKeyboardOpacity(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("keyboard_opacity", value).apply()
    }

    /** Manual extra bottom padding in dp (0 – 20) for stubborn ROMs. */
    fun extraBottomDp(ctx: Context): Int =
        sp(ctx).getInt("extra_bottom_dp", 0)

    fun setExtraBottomDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("extra_bottom_dp", value).apply()
    }

    /** Path of the chosen keyboard background image, if any. */
    fun bgImagePath(ctx: Context): String? =
        sp(ctx).getString("bg_image_path", null)

    fun setBgImagePath(ctx: Context, path: String?) {
        sp(ctx).edit().putString("bg_image_path", path).apply()
    }

    // ---------------- custom layout ----------------

    fun layoutJson(ctx: Context): String? =
        sp(ctx).getString("layout_json", null)

    fun setLayoutJson(ctx: Context, json: String) {
        sp(ctx).edit().putString("layout_json", json).apply()
    }

    fun clearLayout(ctx: Context) {
        sp(ctx).edit().remove("layout_json").apply()
    }

    /**
     * Monotonic counter — bumped every time the saved layout or sizing changes.
     * The IME service compares it to decide when the keyboard must be rebuilt.
     */
    fun layoutVersion(ctx: Context): Int =
        sp(ctx).getInt("layout_version", 1)

    fun bumpLayoutVersion(ctx: Context) {
        sp(ctx).edit().putInt("layout_version", layoutVersion(ctx) + 1).apply()
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
}
