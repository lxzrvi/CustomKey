package com.customkey.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What a key does. */
enum class KeyType {
    /** Single character, shift-aware (a → A). */
    LETTER,

    /** Free-form key — types any text/snippet. */
    CUSTOM,

    SHIFT,
    DELETE,
    SPACE,
    ENTER,
    TO_SYMBOLS,
    TO_EXTRA,
    TO_LETTERS,
    EMOJI
}

data class KeyDef(
    val type: KeyType,
    val label: String,
    val output: String = "",
    val weight: Float = 1f,
    val color: Int? = null,
    val longPressOutput: String = "",
    val repeatOnHold: Boolean = false,
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
                        KeyDef(KeyType.TO_SYMBOLS, "?123", "", 1.4f),
                        KeyDef(KeyType.EMOJI, "😀", "", 1f),
                        KeyDef(KeyType.CUSTOM, ",", ","),
                        KeyDef(KeyType.SPACE, "CustomKey", "", 4f),
                        KeyDef(KeyType.CUSTOM, ".", "."),
                        KeyDef(KeyType.ENTER, "Enter", "", 1.4f)
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

    /** Make sure every key has a unique stable id (used for batch selection). */
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
                    .put("id", key.id)
                if (key.color != null) o.put("color", key.color.toLong())
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

    // ---------------- validation ----------------

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
