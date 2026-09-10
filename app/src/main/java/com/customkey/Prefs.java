package com.customkey;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single in-memory {@link Config} shared by the app UI and the IME service
 * (they live in the same process), plus the storage it is persisted to.
 */
public final class Prefs {

    private static final String FILE = "customkey_v2";
    private static final String KEY = "config";

    private static Context app;
    private static Config cached;

    private Prefs() {
    }

    /** Call from any Activity / Service onCreate. Safe to call repeatedly. */
    public static void init(Context context) {
        if (context != null && app == null) {
            app = context.getApplicationContext();
        }
    }

    public static Context context() {
        if (app == null) {
            throw new IllegalStateException("Prefs.init(context) was not called");
        }
        return app;
    }

    public static synchronized Config get(Context context) {
        init(context);
        return get();
    }

    public static synchronized Config get() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    public static synchronized void save(Context context) {
        init(context);
        save();
    }

    public static synchronized void save() {
        if (cached == null) {
            return;
        }
        sp().edit().putString(KEY, cached.toJson()).apply();
    }

    public static synchronized void saveNow() {
        if (cached == null) {
            return;
        }
        sp().edit().putString(KEY, cached.toJson()).commit();
    }

    public static synchronized void reload(Context context) {
        init(context);
        cached = load();
    }

    public static synchronized void reset(Context context) {
        init(context);
        cached = new Config();
        save();
    }

    public static synchronized void replace(Context context, Config incoming) {
        init(context);
        if (cached == null) {
            cached = new Config();
        }
        cached.replaceWith(incoming);
        save();
    }

    private static Config load() {
        return Config.fromJson(sp().getString(KEY, null));
    }

    private static SharedPreferences sp() {
        return context().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
