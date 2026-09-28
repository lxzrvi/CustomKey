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
 * Ten premium key-press sounds, synthesized on the fly as 16-bit PCM WAV
 * files and played through SoundPool. No audio assets, no internet, no libraries.
 *
 * Every sound has a soft ~1.5 ms attack and a final fade-out window so there is
 * never a harsh "chirp" or click at the start or the end — just a smooth,
 * satisfying press.
 *
 * 0 Tap     5 Thock
 * 1 Pop     6 Snap
 * 2 Click   7 Mellow
 * 3 Wood    8 Crystal
 * 4 Bubble  9 Feather   · 10 = user-custom (pitch + duration)
 */
class KeySounds(private val context: Context) {

    companion object {
        val NAMES = arrayOf(
            "Tap", "Pop", "Click", "Wood", "Bubble",
            "Thock", "Snap", "Mellow", "Crystal", "Feather"
        )
        const val STYLE_CUSTOM = 10
        private const val SAMPLE_RATE = 44100
        private const val ATTACK = 0.0015   // soft 1.5 ms attack
        private const val RELEASE = 0.004   // 4 ms fade-out — kills end clicks
    }

    private var pool: SoundPool? = null
    private val soundIds = IntArray(NAMES.size + 1)
    private val loaded = HashSet<Int>()

    // Imported sound files (custom assets), loaded lazily and cached by path.
    private val assetSoundIds = HashMap<String, Int>()

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
            for (i in 0 until NAMES.size) {
                soundIds[i] = soundPool.load(wavFile(i).absolutePath, 1)
            }
            soundIds[STYLE_CUSTOM] = soundPool.load(customWavFile().absolutePath, 1)
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

    /** Re-synthesize the custom sound after its pitch/duration changed. */
    fun refreshCustom() {
        // Always drop the cached file so the next load uses the new params.
        File(context.cacheDir, "key_sound_custom.wav").delete()
        val soundPool = pool ?: return
        val id = soundIds[STYLE_CUSTOM]
        if (id > 0) {
            try {
                soundPool.unload(id)
            } catch (_: Exception) {
            }
        }
        soundIds[STYLE_CUSTOM] = soundPool.load(customWavFile().absolutePath, 1)
    }

    /** Plays an imported sound file (mp3/wav/ogg). Loads it on first use. */
    fun playAsset(path: String, volumePercent: Int) {
        if (volumePercent <= 0) return
        ensureLoaded()
        val soundPool = pool ?: return
        val file = java.io.File(path)
        if (!file.exists()) return
        val id = assetSoundIds[path] ?: run {
            val newId = soundPool.load(path, 1)
            assetSoundIds[path] = newId
            newId
        }
        if (id > 0) {
            val v = (volumePercent / 100f).coerceIn(0f, 1f)
            soundPool.play(id, v, v, 1, 0, 1f)
        }
    }

    fun release() {
        pool?.release()
        pool = null
        loaded.clear()
        assetSoundIds.clear()
    }

    // ---------------- synthesis ----------------

    private fun wavFile(style: Int): File {
        val file = File(context.cacheDir, "key_sound_$style.wav")
        if (!file.exists() || file.length() == 0L) {
            writeWav(file, synth(style))
        }
        return file
    }

    private fun customWavFile(): File {
        val file = File(context.cacheDir, "key_sound_custom.wav")
        if (!file.exists() || file.length() == 0L) {
            writeWav(file, synthCustom())
        }
        return file
    }

    /**
     * Partial-based synthesis with smooth attack + exponential decay +
     * fade-out window. [frequency] may sweep over the note.
     */
    private fun synthOne(
        partials: List<Pair<Double, Double>>,   // (frequency, amplitude)
        durationSec: Double,
        decay: Double,
        sweep: Double = 1.0,                    // end frequency multiplier
        overall: Double = 0.60
    ): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt().coerceAtLeast(8)
        val data = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durationSec

            // envelope: soft attack → exponential decay → final fade window
            var envelope = exp(-t * decay)
            if (t < ATTACK) envelope *= t / ATTACK
            val releaseStart = durationSec - RELEASE
            if (t > releaseStart) envelope *= ((durationSec - t) / RELEASE).coerceIn(0.0, 1.0)

            var value = 0.0
            for ((baseFreq, amp) in partials) {
                val f = baseFreq * (1.0 + (sweep - 1.0) * progress)
                value += sin(2 * PI * f * t) * amp
            }
            data[i] = (value * envelope * overall * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun synth(style: Int): ShortArray = when (style) {
        0 -> synthOne(listOf(1100.0 to 0.85, 2200.0 to 0.15), 0.042, 115.0, 0.94)
        1 -> synthOne(listOf(280.0 to 1.0), 0.058, 55.0, 0.62)
        2 -> synthOne(listOf(1700.0 to 0.9, 850.0 to 0.1), 0.022, 260.0, 1.0)
        3 -> synthOne(listOf(780.0 to 0.7, 1170.0 to 0.3), 0.062, 68.0, 0.97)
        4 -> synthOne(listOf(380.0 to 1.0), 0.072, 36.0, 2.1)
        5 -> synthOne(listOf(190.0 to 1.0, 95.0 to 0.25), 0.050, 62.0, 0.8)
        6 -> synthOne(listOf(2000.0 to 0.8, 1000.0 to 0.2), 0.016, 330.0, 1.0)
        7 -> synthOne(listOf(520.0 to 0.65, 780.0 to 0.35), 0.072, 46.0, 0.98)
        8 -> synthOne(listOf(1560.0 to 0.6, 2340.0 to 0.4), 0.034, 140.0, 1.0, 0.5)
        else -> synthOne(listOf(640.0 to 1.0), 0.046, 52.0, 0.7, 0.45)
    }

    private fun synthCustom(): ShortArray {
        val pitch = Prefs.customSoundPitch(context).coerceIn(60, 160) / 100.0
        val durationMs = Prefs.customSoundDuration(context).coerceIn(15, 90)
        val base = 1100.0 * pitch
        return synthOne(
            partials = listOf(base to 0.85, base * 2.0 to 0.15),
            durationSec = durationMs / 1000.0,
            decay = 4.6 * 1000.0 / durationMs,   // decay scales with length
            sweep = 0.94,
            overall = 0.60
        )
    }

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
