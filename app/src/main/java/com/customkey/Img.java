package com.customkey;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny disk/uri → bitmap cache with async decoding, so picking a background
 * or per-key image never blocks the draw pass.
 */
public final class Img {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final Set<String> PENDING = new HashSet<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Img() {
    }

    public static void clear() {
        CACHE.evictAll();
        PENDING.clear();
    }

    /**
     * @return the bitmap if it is already decoded, otherwise null — decoding
     *         starts in the background and {@code onReady} runs when it lands.
     */
    public static Bitmap get(final Context context, String uri,
                             final int width, final int height,
                             final Runnable onReady) {

        if (uri == null || uri.length() == 0) {
            return null;
        }
        String key = uri + "@" + width + "x" + height;
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        synchronized (PENDING) {
            if (PENDING.contains(key)) {
                return null;
            }
            PENDING.add(key);
        }

        final Context app = context.getApplicationContext();
        EXEC.execute(() -> {
            Bitmap bmp = decode(app, uri, width, height);
            synchronized (PENDING) {
                PENDING.remove(key);
            }
            if (bmp != null) {
                CACHE.put(key, bmp);
                if (onReady != null) {
                    MAIN.post(onReady);
                }
            }
        });
        return null;
    }

    public static Bitmap decode(Context context, String uri, int width, int height) {
        int target = Math.max(1, Math.max(width, height));
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            read(context, uri, probe);

            int sample = 1;
            int max = Math.max(probe.outWidth, probe.outHeight);
            while (max / (sample * 2) >= target) {
                sample *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return read(context, uri, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap read(Context context, String uri, BitmapFactory.Options opts) {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(Uri.parse(uri));
            if (in == null) {
                return null;
            }
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Throwable t) {
            return null;
        } finally {
            close(in);
        }
    }

    private static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
