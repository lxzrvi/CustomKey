package com.customkey;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** System font families plus user supplied .ttf / .otf files. */
public final class FontManager {

    public static final String[] FAMILIES = {
            "sans-serif",
            "sans-serif-medium",
            "sans-serif-light",
            "sans-serif-thin",
            "sans-serif-condensed",
            "serif",
            "monospace",
            "cursive"
    };

    private static Typeface cached;
    private static String cachedKey = "";

    private FontManager() {
    }

    public static String label(String family) {
        if (family == null) {
            return "Default";
        }
        return family.replace("sans-serif", "Sans").replace("-", " ");
    }

    public static Typeface get(Context context) {
        Config cfg = Prefs.get(context);
        String key = cfg.fontMode + "|" + cfg.fontFamily + "|" + cfg.fontUri;

        if (cached != null && key.equals(cachedKey)) {
            return cached;
        }

        Typeface face = null;
        if (cfg.fontMode == Config.FONT_CUSTOM && cfg.fontUri != null
                && cfg.fontUri.length() > 0) {
            face = fromUri(context, cfg.fontUri);
        }
        if (face == null) {
            try {
                face = Typeface.create(cfg.fontFamily, Typeface.NORMAL);
            } catch (Throwable t) {
                face = Typeface.DEFAULT;
            }
        }
        cached = face;
        cachedKey = key;
        return face;
    }

    public static void reset() {
        cached = null;
        cachedKey = "";
    }

    private static Typeface fromUri(Context context, String uri) {
        try {
            File target = new File(context.getCacheDir(),
                    "font_" + Math.abs(uri.hashCode()) + ".ttf");

            if (!target.exists()) {
                InputStream in = context.getContentResolver()
                        .openInputStream(android.net.Uri.parse(uri));
                if (in == null) {
                    return null;
                }
                OutputStream out = new FileOutputStream(target);
                byte[] buffer = new byte[8192];
                int read;
                try {
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                } finally {
                    out.close();
                    in.close();
                }
            }
            return Typeface.createFromFile(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
