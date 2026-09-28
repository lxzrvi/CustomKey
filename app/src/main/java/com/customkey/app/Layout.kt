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
 */
data class KeyVisual(
    val cornerRadiusDp: Int? = null,
    val opacityPercent: Int? = null,
    val borderColor: Int? = null,
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
    val textPosition: Int? = null
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

    fun visualToJson(v: KeyVisual): JSONObject = JSONObject()
        .put("rad", v.cornerRadiusDp ?: -1)
        .put("op", v.opacityPercent ?: -1)
        .put("bc", v.borderColor?.toLong() ?: -1L)
        .put("bw", v.borderWidthDp ?: -1)
        .put("bs", v.borderSides ?: -1)
        .put("sh", v.shadow)
        .put("shs", v.shadowSoft)
        .put("sha", v.shadowAngleDeg)
        .put("shd", v.shadowDistanceDp)
        .put("shb", v.shadowBlurDp)
        .put("tc", v.textColor?.toLong() ?: -1L)
        .put("ts", v.textSizeSp ?: -1)
        .put("b", v.bold ?: false)
        .put("bi", v.italic ?: false)
        .put("bp", v.textPosition ?: -1)

    fun visualFromJson(o: JSONObject): KeyVisual = KeyVisual(
        cornerRadiusDp = o.optInt("rad", -1).let { if (it < 0) null else it },
        opacityPercent = o.optInt("op", -1).let { if (it < 0) null else it },
        borderColor = o.optLong("bc", -1L).let { if (it < 0) null else it.toInt() },
        borderWidthDp = o.optInt("bw", -1).let { if (it < 0) null else it },
        borderSides = o.optInt("bs", -1).let { if (it < 0) null else it },
        shadow = o.optBoolean("sh", false),
        shadowSoft = o.optBoolean("shs", true),
        shadowAngleDeg = o.optInt("sha", 315),
        shadowDistanceDp = o.optInt("shd", 3),
        shadowBlurDp = o.optInt("shb", 4),
        textColor = o.optLong("tc", -1L).let { if (it < 0) null else it.toInt() },
        textSizeSp = o.optInt("ts", -1).let { if (it < 0) null else it },
        bold = if (o.has("b")) o.getBoolean("b") else null,
        italic = if (o.has("bi")) o.getBoolean("bi") else null,
        textPosition = o.optInt("bp", -1).let { if (it < 0) null else it }
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

    /** Essential keys every usable layout must still contain. */
    fun missingEssentials(def: KeyboardDef): List<KeyType> {
        val present = HashSet<KeyType>()
        def.rows.forEach { row -> row.keys.forEach { present.add(it.type) } }
        val essentials = listOf(
            KeyType.SHIFT, KeyType.DELETE, KeyType.SPACE, KeyType.ENTER, KeyType.TO_SYMBOLS
        )
        return essentials.filter { !present.contains(it) }
    }
}
