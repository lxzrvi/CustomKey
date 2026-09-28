package com.customkey.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Five premium key-press sounds, synthesized on the fly as 16-bit PCM WAV
 * files and played through SoundPool. No audio assets, no internet, no libraries.
 *
 * 0 Tap    – crisp neutral press
 * 1 Pop    – soft low pop
 * 2 Click  – sharp mechanical click
 * 3 Wood   – warm wooden tap
 * 4 Bubble – playful rising blip
 */
class KeySounds(private val context: Context) {

    companion object {
        val NAMES = arrayOf("Tap", "Pop", "Click", "Wood", "Bubble")
        private const val SAMPLE_RATE = 44100
    }

    private var pool: SoundPool? = null
    private val soundIds = IntArray(NAMES.size)
    private val loaded = HashSet<Int>()

    private fun ensureLoaded() {
        if (pool != null) return
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attributes)
                .build()
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded.add(sampleId)
            }
            for (i in soundIds.indices) {
                soundIds[i] = soundPool.load(wavFile(i).absolutePath, 1)
            }
            pool = soundPool
        } catch (_: Exception) {
            pool = null
        }
    }

    fun play(style: Int, volumePercent: Int) {
        if (volumePercent <= 0) return
        ensureLoaded()
        val soundPool = pool ?: return
        val id = soundIds[style.coerceIn(0, soundIds.size - 1)]
        if (id > 0 && loaded.contains(id)) {
            val v = (volumePercent / 100f).coerceIn(0f, 1f)
            soundPool.play(id, v, v, 1, 0, 1f)
        }
    }

    fun release() {
        pool?.release()
        pool = null
        loaded.clear()
    }

    // ---------------- synthesis ----------------

    private fun wavFile(style: Int): File {
        val file = File(context.cacheDir, "key_sound_$style.wav")
        if (!file.exists() || file.length() == 0L) {
            writeWav(file, synth(style))
        }
        return file
    }

    private fun synth(style: Int): ShortArray {
        val seconds = when (style) {
            0 -> 0.040
            1 -> 0.055
            2 -> 0.024
            3 -> 0.065
            else -> 0.075
        }
        val decay = when (style) {
            0 -> 95.0
            1 -> 48.0
            2 -> 240.0
            3 -> 60.0
            else -> 32.0
        }
        val count = (SAMPLE_RATE * seconds).toInt()
        val data = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t * decay)
            val value = when (style) {
                0 -> sin(2 * PI * 1250 * t) * 0.8 + sin(2 * PI * 2500 * t) * 0.2
                1 -> sin(2 * PI * (300.0 - 2200.0 * t) * t)
                2 -> sin(2 * PI * 1900 * t) * 0.65 + noise(i) * 0.35
                3 -> sin(2 * PI * 820 * t) * 0.6 + sin(2 * PI * 1230 * t) * 0.3
                else -> sin(2 * PI * (350.0 + 5200.0 * t) * t) * 0.85
            }
            data[i] = (value * envelope * 0.62 * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun noise(i: Int): Double =
        ((i * 2654435761L).ushr(16) % 1000) / 500.0 - 1.0

    private fun writeWav(file: File, data: ShortArray) {
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
                val byteCount = data.size * 2
                out.writeBytes("RIFF")
                out.writeIntLe(36 + byteCount)
                out.writeBytes("WAVE")
                out.writeBytes("fmt ")
                out.writeIntLe(16)
                out.writeShortLe(1)
                out.writeShortLe(1)
                out.writeIntLe(SAMPLE_RATE)
                out.writeIntLe(SAMPLE_RATE * 2)
                out.writeShortLe(2)
                out.writeShortLe(16)
                out.writeBytes("data")
                out.writeIntLe(byteCount)
                for (sample in data) out.writeShortLe(sample.toInt())
            }
        } catch (_: Exception) {
        }
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
