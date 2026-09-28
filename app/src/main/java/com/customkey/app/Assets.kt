package com.customkey.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Custom asset import system — fonts (.ttf/.otf), images (.png/.jpg/.webp) and
 * sounds (.mp3/.wav/.ogg) picked with the Android system file picker.
 *
 * Every asset is validated, copied into app-private storage
 * (files/assets/<id>.<ext>) and registered in SharedPreferences, so it survives
 * app restarts. No permissions needed, nothing ever leaves the device.
 */
object Assets {

    const val KIND_FONT = "font"
    const val KIND_IMAGE = "image"
    const val KIND_SOUND = "sound"

    data class Asset(
        val id: String,
        val name: String,
        val kind: String,
        val file: String
    )

    // ---------------- registry ----------------

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences("customkey_assets", Context.MODE_PRIVATE)

    private fun readAll(ctx: Context): MutableList<Asset> {
        val list = mutableListOf<Asset>()
        try {
            val arr = JSONArray(sp(ctx).getString("assets_json", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Asset(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        kind = o.optString("kind"),
                        file = o.optString("file")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun writeAll(ctx: Context, list: List<Asset>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("kind", it.kind)
                    .put("file", it.file)
            )
        }
        sp(ctx).edit().putString("assets_json", arr.toString()).apply()
    }

    fun list(ctx: Context, kind: String): List<Asset> =
        readAll(ctx).filter { it.kind == kind && File(path(ctx, it)).exists() }

    fun byId(ctx: Context, id: String?): Asset? {
        if (id.isNullOrEmpty()) return null
        return readAll(ctx).firstOrNull { it.id == id && File(path(ctx, it)).exists() }
    }

    fun path(ctx: Context, asset: Asset): File = File(File(ctx.filesDir, "assets"), asset.file)

    // ---------------- import ----------------

    private fun extensionOf(uri: Uri): String {
        val s = (uri.lastPathSegment ?: uri.toString()).lowercase()
        return s.substringAfterLast('.', "")
    }

    private fun displayNameOf(uri: Uri, fallback: String): String {
        val s = uri.lastPathSegment ?: return fallback
        val name = s.substringAfterLast('/')
        return if (name.length > 1) name.substringBeforeLast('.') else fallback
    }

    /** Validates and imports the picked file. Returns the new asset, or null. */
    fun import(ctx: Context, uri: Uri, kind: String): Asset? {
        return try {
            val ext = extensionOf(uri)
            val valid = when (kind) {
                KIND_FONT -> ext == "ttf" || ext == "otf"
                KIND_IMAGE -> ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "webp"
                KIND_SOUND -> ext == "mp3" || ext == "wav" || ext == "ogg"
                else -> false
            }
            if (!valid) return null

            val dir = File(ctx.filesDir, "assets")
            if (!dir.exists()) dir.mkdirs()

            val id = "${kind}_${System.currentTimeMillis()}"
            val fileBase = "$id.$ext"
            val target = File(dir, fileBase)

            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return null
            if (target.length() <= 0L) {
                target.delete()
                return null
            }

            // content validation
            when (kind) {
                KIND_FONT -> {
                    val face = try {
                        Typeface.createFromFile(target)
                    } catch (_: Exception) {
                        null
                    }
                    if (face == null) {
                        target.delete()
                        return null
                    }
                }
                KIND_IMAGE -> {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, opts)
                    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                        target.delete()
                        return null
                    }
                }
                // sounds are validated lazily by SoundPool
            }

            val asset = Asset(
                id = id,
                name = displayNameOf(uri, defaultName(kind)),
                kind = kind,
                file = fileBase
            )
            val all = readAll(ctx)
            all.add(asset)
            writeAll(ctx, all)
            asset
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultName(kind: String) = when (kind) {
        KIND_FONT -> "My font"
        KIND_IMAGE -> "My image"
        else -> "My sound"
    }

    fun rename(ctx: Context, asset: Asset, newName: String) {
        val name = newName.trim()
        if (name.isEmpty()) return
        val all = readAll(ctx)
        val idx = all.indexOfFirst { it.id == asset.id }
        if (idx >= 0) {
            all[idx] = asset.copy(name = name)
            writeAll(ctx, all)
        }
    }

    fun delete(ctx: Context, asset: Asset) {
        val all = readAll(ctx)
        all.removeAll { it.id == asset.id }
        writeAll(ctx, all)
        try {
            path(ctx, asset).delete()
        } catch (_: Exception) {
        }
        if (asset.kind == KIND_FONT) fontCache.remove(asset.id)
        if (asset.kind == KIND_IMAGE) bitmapCache.remove(cacheKey(asset.id, 100, 0))
    }

    // ---------------- typed accessors (cached) ----------------

    private val fontCache = HashMap<String, Typeface>()

    fun typefaceById(ctx: Context, id: String?): Typeface? {
        if (id.isNullOrEmpty()) return null
        fontCache[id]?.let { return it }
        val asset = byId(ctx, id) ?: return null
        return try {
            val face = Typeface.createFromFile(path(ctx, asset))
            fontCache[id] = face
            face
        } catch (_: Exception) {
            null
        }
    }

    private val bitmapCache = HashMap<String, Bitmap>()

    private fun cacheKey(id: String, scalePercent: Int, blur: Int) = "$id|$scalePercent|$blur"

    /** Decoded + optionally blurred key-background bitmap, cached per setting. */
    fun keyBitmap(ctx: Context, id: String?, scalePercent: Int, blurDp: Int): Bitmap? {
        if (id.isNullOrEmpty()) return null
        val key = cacheKey(id, scalePercent, blurDp)
        bitmapCache[key]?.let { return it }
        val asset = byId(ctx, id) ?: return null
        return try {
            val file = path(ctx, asset)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return null
            // decode at a sensible size for a key (~256px)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 128) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            if (blurDp > 0) {
                bmp = KeyboardTheme.blurBitmap(bmp, blurDp)
            }
            bitmapCache[key] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /** Small preview bitmap for the editor's image chips. */
    fun previewBitmap(ctx: Context, asset: Asset): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path(ctx, asset).absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}
