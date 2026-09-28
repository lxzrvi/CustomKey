package com.customkey.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What a key does. */
enum class KeyType {
    LETTER,
    CUSTOM,

    SHIFT,
    DELETE,
    SPACE,
    ENTER,
    TO_SYMBOLS,
    TO_EXTRA,
    TO_LETTERS,
    EMOJI,
    TO_CURSOR,

    // clipboard / cursor page
    CLIP_COPY,
    CLIP_CUT,
    CLIP_PASTE,
    CLIP_ALL,
    SEL_TOGGLE,
    SEL_START_LEFT,
    SEL_END_RIGHT,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    NEXT_FIELD
}

/**
 * Per-key visual overrides from the Keyboard Editor.
 * Every field is optional — null means "use the theme default".
 * Batch edits only copy the properties the user explicitly changed.
 */
data class KeyVisual(
    // ---- base style (v1.3) ----
    val cornerRadiusDp: Int? = null,
    val opacityPercent: Int? = null,
    val borderColor: Int? = null,
    /** Border thickness in dp (null = default 1). */
    val borderWidthDp: Int? = null,
    /** 1 top · 2 right · 4 bottom · 8 left · 15 = all */
    val borderSides: Int? = null,
    val shadow: Boolean = false,
    val shadowSoft: Boolean = true,
    val shadowAngleDeg: Int = 315,
    val shadowDistanceDp: Int = 3,
    val shadowBlurDp: Int = 4,
    val textColor: Int? = null,
    val textSizeSp: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    /** 0 center · 1 top · 2 bottom · 3 left · 4 right */
    val textPosition: Int? = null,

    // ---- background (v1.4) ----
    /** Linear gradient from the key color to [gradientColor]. */
    val gradient: Boolean = false,
    val gradientColor: Int? = null,
    val gradientAngleDeg: Int = 0,
    /** Imported image asset id used as the key background. */
    val imageId: String? = null,
    /** Image zoom around the center (50 – 300 %). */
    val imageScalePercent: Int = 100,
    /** Image opacity over the key color (0 – 100 %). */
    val imageAlphaPercent: Int = 100,
    val imageBlurDp: Int = 0,

    // ---- shape effects (v1.4) ----
    val glow: Boolean = false,
    val glowColor: Int? = null,
    val glowBlurDp: Int = 8,
    val innerShadow: Boolean = false,

    // ---- text extras (v1.4) ----
    /** Imported font asset id for this key. */
    val fontId: String? = null,
    /** Letter spacing in 1/100 em (e.g. 5 = 0.05 em). */
    val letterSpacing: Int? = null,
    val textRotationDeg: Int? = null,
    val textShadow: Boolean = false,
    val textShadowColor: Int? = null,
    val textShadowBlurDp: Int = 2,
    val textShadowDx: Int = 1,
    val textShadowDy: Int = 1,
    val textOpacityPercent: Int? = null,

    // ---- size & position (v1.4) ----
    /** Whole-key rotation in degrees. */
    val rotationDeg: Int? = null,
    /** Whole-key scale (50 – 150 %). */
    val scalePercent: Int? = null,
    /** Extra horizontal text inset (dp). */
    val paddingH: Int? = null,
    /** Extra vertical text inset (dp). */
    val paddingV: Int? = null,

    // ---- feedback (v1.4) ----
    /** Per-key sound style; null = follow the global setting. */
    val soundStyle: Int? = null,
    /** Imported sound asset id; overrides [soundStyle] when set. */
    val soundAssetId: String? = null,
    /** Per-key vibration strength %; null = follow the global setting. */
    val vibrationPercent: Int? = null,

    // ---- pressed state (v1.4) ----
    val pressedColor: Int? = null,
    val pressedScalePercent: Int? = null
)

data class KeyDef(
    val type: KeyType,
    val label: String,
    val output: String = "",
    val weight: Float = 1f,
    val color: Int? = null,
    val longPressOutput: String = "",
    val repeatOnHold: Boolean = false,
    /** Custom long-press characters (also shown as the key's hint). */
    val alternates: String = "",
    /** Per-key height multiplier (0.7 – 1.4). */
    val heightFactor: Float = 1f,
    val style: KeyVisual? = null,
    val id: Long = 0L
)

data class RowDef(val keys: MutableList<KeyDef>)

data class KeyboardDef(val rows: MutableList<RowDef>)

/**
 * Layout model for the letters page. Serialized to JSON (framework org.json —
 * no external dependencies) so the Keyboard Editor and the IME service share
 * the exact same definition.
 */
object Layouts {

    fun defaultLetters(): KeyboardDef {

        fun letterRow(letters: String) =
            RowDef(letters.map { KeyDef(KeyType.LETTER, it.toString(), it.toString()) }.toMutableList())

        return KeyboardDef(
            mutableListOf(
                letterRow("qwertyuiop"),
                letterRow("asdfghjkl"),
                RowDef(
                    mutableListOf(
                        KeyDef(KeyType.SHIFT, "Shift", "", 1.4f),
                        KeyDef(KeyType.LETTER, "z", "z"),
                        KeyDef(KeyType.LETTER, "x", "x"),
                        KeyDef(KeyType.LETTER, "c", "c"),
                        KeyDef(KeyType.LETTER, "v", "v"),
                        KeyDef(KeyType.LETTER, "b", "b"),
                        KeyDef(KeyType.LETTER, "n", "n"),
                        KeyDef(KeyType.LETTER, "m", "m"),
                        KeyDef(KeyType.DELETE, "Delete", "", 1.4f)
                    )
                ),
                RowDef(
                    mutableListOf(
                        KeyDef(KeyType.TO_SYMBOLS, "?123", "", 1.2f),
                        KeyDef(KeyType.EMOJI, "Emoji", "", 1f),
                        KeyDef(KeyType.TO_CURSOR, "Cursor", "", 1f),
                        KeyDef(KeyType.CUSTOM, ",", ","),
                        KeyDef(KeyType.SPACE, "CustomKey", "", 3.4f),
                        KeyDef(KeyType.CUSTOM, ".", "."),
                        KeyDef(KeyType.ENTER, "Enter", "", 1.2f)
                    )
                )
            )
        )
    }

    /** Currently saved layout (with stable ids), or the default. */
    fun current(ctx: Context): KeyboardDef {
        val json = Prefs.layoutJson(ctx) ?: return withIds(defaultLetters())
        return withIds(fromJson(json) ?: defaultLetters())
    }

    private fun withIds(def: KeyboardDef): KeyboardDef {
        var next = 1L
        def.rows.forEach { row ->
            val withId = row.keys.map { key ->
                if (key.id == 0L) key.copy(id = System.nanoTime() + (next++)) else key
            }
            row.keys.clear()
            row.keys.addAll(withId)
        }
        return def
    }

    fun save(ctx: Context, def: KeyboardDef) {
        Prefs.setLayoutJson(ctx, toJson(def))
        Prefs.bumpLayoutVersion(ctx)
    }

    fun reset(ctx: Context) {
        Prefs.clearLayout(ctx)
        Prefs.bumpLayoutVersion(ctx)
    }

    // ---------------- JSON ----------------

    private fun JSONObject.putOpt(key: String, value: Int?): JSONObject {
        if (value != null) put(key, value) else put(key, -1)
        return this
    }

    private fun JSONObject.putOptBool(key: String, value: Boolean?, fallback: Boolean = false): JSONObject {
        if (value != null) put(key, value) else put(key, fallback)
        return this
    }

    private fun JSONObject.putOptLong(key: String, value: Int?): JSONObject {
        if (value != null) put(key, value.toLong()) else put(key, -1L)
        return this
    }

    private fun JSONObject.getIntOpt(key: String): Int? {
        val v = optInt(key, -1)
        return if (v < 0) null else v
    }

    private fun JSONObject.getLongColor(key: String): Int? {
        val v = optLong(key, -1L)
        return if (v < 0) null else v.toInt()
    }

    fun visualToJson(v: KeyVisual): JSONObject = JSONObject()
        // base
        .putOpt("rad", v.cornerRadiusDp)
        .putOpt("op", v.opacityPercent)
        .putOptLong("bc", v.borderColor)
        .putOpt("bw", v.borderWidthDp)
        .putOpt("bs", v.borderSides)
        .put("sh", v.shadow)
        .put("shs", v.shadowSoft)
        .put("sha", v.shadowAngleDeg)
        .put("shd", v.shadowDistanceDp)
        .put("shb", v.shadowBlurDp)
        .putOptLong("tc", v.textColor)
        .putOpt("ts", v.textSizeSp)
        .putOptBool("b", v.bold)
        .putOptBool("bi", v.italic)
        .putOpt("bp", v.textPosition)
        // background
        .put("gr", v.gradient)
        .putOptLong("grc", v.gradientColor)
        .put("gra", v.gradientAngleDeg)
        .put("gid", v.imageId ?: "")
        .put("gsc", v.imageScalePercent)
        .put("gal", v.imageAlphaPercent)
        .put("gbl", v.imageBlurDp)
        // shape
        .put("glo", v.glow)
        .putOptLong("glc", v.glowColor)
        .put("glb", v.glowBlurDp)
        .put("ins", v.innerShadow)
        // text
        .put("fnt", v.fontId ?: "")
        .putOpt("ls", v.letterSpacing)
        .putOpt("trx", v.textRotationDeg)
        .put("tsw", v.textShadow)
        .putOptLong("tsc", v.textShadowColor)
        .put("tsb", v.textShadowBlurDp)
        .put("tsx", v.textShadowDx)
        .put("tsy", v.textShadowDy)
        .putOpt("top", v.textOpacityPercent)
        // size & position
        .putOpt("rot", v.rotationDeg)
        .putOpt("scl", v.scalePercent)
        .putOpt("kph", v.paddingH)
        .putOpt("kpv", v.paddingV)
        // feedback
        .putOpt("snd", v.soundStyle)
        .put("sna", v.soundAssetId ?: "")
        .putOpt("vib", v.vibrationPercent)
        // pressed
        .putOptLong("pcl", v.pressedColor)
        .putOpt("psc", v.pressedScalePercent)

    fun visualFromJson(o: JSONObject): KeyVisual = KeyVisual(
        cornerRadiusDp = o.getIntOpt("rad"),
        opacityPercent = o.getIntOpt("op"),
        borderColor = o.getLongColor("bc"),
        borderWidthDp = o.getIntOpt("bw"),
        borderSides = o.getIntOpt("bs"),
        shadow = o.optBoolean("sh", false),
        shadowSoft = o.optBoolean("shs", true),
        shadowAngleDeg = o.optInt("sha", 315),
        shadowDistanceDp = o.optInt("shd", 3),
        shadowBlurDp = o.optInt("shb", 4),
        textColor = o.getLongColor("tc"),
        textSizeSp = o.getIntOpt("ts"),
        bold = if (o.has("b")) o.getBoolean("b") else null,
        italic = if (o.has("bi")) o.getBoolean("bi") else null,
        textPosition = o.getIntOpt("bp"),
        gradient = o.optBoolean("gr", false),
        gradientColor = o.getLongColor("grc"),
        gradientAngleDeg = o.optInt("gra", 0),
        imageId = o.optString("gid", "").ifEmpty { null },
        imageScalePercent = o.optInt("gsc", 100),
        imageAlphaPercent = o.optInt("gal", 100),
        imageBlurDp = o.optInt("gbl", 0),
        glow = o.optBoolean("glo", false),
        glowColor = o.getLongColor("glc"),
        glowBlurDp = o.optInt("glb", 8),
        innerShadow = o.optBoolean("ins", false),
        fontId = o.optString("fnt", "").ifEmpty { null },
        letterSpacing = o.getIntOpt("ls"),
        textRotationDeg = o.getIntOpt("trx"),
        textShadow = o.optBoolean("tsw", false),
        textShadowColor = o.getLongColor("tsc"),
        textShadowBlurDp = o.optInt("tsb", 2),
        textShadowDx = o.optInt("tsx", 1),
        textShadowDy = o.optInt("tsy", 1),
        textOpacityPercent = o.getIntOpt("top"),
        rotationDeg = o.getIntOpt("rot"),
        scalePercent = o.getIntOpt("scl"),
        paddingH = o.getIntOpt("kph"),
        paddingV = o.getIntOpt("kpv"),
        soundStyle = o.getIntOpt("snd"),
        soundAssetId = o.optString("sna", "").ifEmpty { null },
        vibrationPercent = o.getIntOpt("vib"),
        pressedColor = o.getLongColor("pcl"),
        pressedScalePercent = o.getIntOpt("psc")
    )

    fun toJson(def: KeyboardDef): String {
        val rowsArray = JSONArray()
        def.rows.forEach { row ->
            val keysArray = JSONArray()
            row.keys.forEach { key ->
                val o = JSONObject()
                    .put("type", key.type.name)
                    .put("label", key.label)
                    .put("output", key.output)
                    .put("weight", key.weight.toDouble())
                    .put("lp", key.longPressOutput)
                    .put("rep", key.repeatOnHold)
                    .put("alt", key.alternates)
                    .put("hf", key.heightFactor.toDouble())
                    .put("id", key.id)
                if (key.color != null) o.put("color", key.color.toLong())
                if (key.style != null) o.put("vs", visualToJson(key.style!!))
                keysArray.put(o)
            }
            rowsArray.put(JSONObject().put("keys", keysArray))
        }
        return JSONObject().put("rows", rowsArray).toString()
    }

    fun fromJson(json: String): KeyboardDef? {
        return try {
            val rowsArray = JSONObject(json).getJSONArray("rows")
            val rows = mutableListOf<RowDef>()
            for (i in 0 until rowsArray.length()) {
                val keysArray = rowsArray.getJSONObject(i).getJSONArray("keys")
                val keys = mutableListOf<KeyDef>()
                for (j in 0 until keysArray.length()) {
                    val o = keysArray.getJSONObject(j)
                    val type = try {
                        KeyType.valueOf(o.getString("type"))
                    } catch (_: IllegalArgumentException) {
                        KeyType.CUSTOM
                    }
                    keys.add(
                        KeyDef(
                            type = type,
                            label = o.optString("label", ""),
                            output = o.optString("output", ""),
                            weight = o.optDouble("weight", 1.0).toFloat().coerceIn(0.4f, 4.0f),
                            color = if (o.has("color")) o.getLong("color").toInt() else null,
                            longPressOutput = o.optString("lp", ""),
                            repeatOnHold = o.optBoolean("rep", false),
                            alternates = o.optString("alt", ""),
                            heightFactor = o.optDouble("hf", 1.0).toFloat().coerceIn(0.7f, 1.4f),
                            style = if (o.has("vs")) visualFromJson(o.getJSONObject("vs")) else null,
                            id = o.optLong("id", 0L)
                        )
                    )
                }
                if (keys.isNotEmpty()) rows.add(RowDef(keys))
            }
            if (rows.isEmpty()) null else KeyboardDef(rows)
        } catch (_: Exception) {
            null
        }
    }

    /** Deep copy via JSON round-trip (used for move-mode snapshots). */
    fun copyOf(def: KeyboardDef): KeyboardDef =
        withIds(fromJson(toJson(def)) ?: defaultLetters())
}
