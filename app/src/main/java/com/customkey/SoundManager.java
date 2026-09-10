package com.customkey;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;

/** Key click sounds: the system keypress tick, or a user supplied file. */
public final class SoundManager {

    private static SoundPool pool;
    private static int sampleId = -1;
    private static String loadedUri = "";
    private static AssetFileDescriptor descriptor;

    private SoundManager() {
    }

    /** @param volume 0..100, or a negative value to stay silent. */
    public static void play(Context context, int volume) {
        if (context == null || volume <= 0) {
            return;
        }
        float level = Math.min(1f, volume / 100f);
        Config cfg = Prefs.get(context);

        if (cfg.soundUri != null && cfg.soundUri.length() > 0) {
            playCustom(context, cfg.soundUri, level);
            return;
        }
        try {
            AudioManager audio =
                    (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) {
                audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, level);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void preview(Context context, int volume) {
        play(context, volume);
    }

    public static void reset() {
        if (pool != null) {
            try {
                pool.release();
            } catch (Throwable ignored) {
            }
        }
        closeDescriptor();
        pool = null;
        sampleId = -1;
        loadedUri = "";
    }

    private static void playCustom(Context context, String uri, float level) {
        try {
            if (pool == null) {
                pool = new SoundPool.Builder().setMaxStreams(2).build();
            }
            if (!uri.equals(loadedUri)) {
                closeDescriptor();
                // SoundPool has no Uri overload; it wants a descriptor, and
                // the descriptor has to stay open for the async load.
                descriptor = context.getContentResolver()
                        .openAssetFileDescriptor(Uri.parse(uri), "r");
                if (descriptor == null) {
                    return;
                }
                loadedUri = uri;
                sampleId = pool.load(descriptor, 1);
            }
            if (sampleId > 0) {
                pool.play(sampleId, level, level, 1, 0, 1f);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void closeDescriptor() {
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (Throwable ignored) {
            }
            descriptor = null;
        }
    }
}
