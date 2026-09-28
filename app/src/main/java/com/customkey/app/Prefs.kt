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

    /** Key height multiplier (0.8 – 1.3). */
    fun heightFactor(ctx: Context): Float =
        sp(ctx).getFloat("height_factor", 1.0f)

    fun setHeightFactor(ctx: Context, value: Float) {
        sp(ctx).edit().putFloat("height_factor", value).apply()
    }

    /** Visual gap between keys in dp (2 – 8). */
    fun keyGapDp(ctx: Context): Int =
        sp(ctx).getInt("key_gap_dp", 3)

    fun setKeyGapDp(ctx: Context, value: Int) {
        sp(ctx).edit().putInt("key_gap_dp", value).apply()
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
}
