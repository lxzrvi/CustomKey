package com.customkey;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public final class AppSettings {

    private static final String PREFS = "customkey_settings";

    private AppSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }

    public static int getKeyHeight(Context context) {
        return prefs(context).getInt("key_height", 52);
    }

    public static void setKeyHeight(Context context, int value) {
        prefs(context)
                .edit()
                .putInt("key_height", value)
                .apply();
    }

    public static int getSpacing(Context context) {
        return prefs(context).getInt("spacing", 4);
    }

    public static void setSpacing(Context context, int value) {
        prefs(context)
                .edit()
                .putInt("spacing", value)
                .apply();
    }

    public static int getRadius(Context context) {
        return prefs(context).getInt("radius", 8);
    }

    public static void setRadius(Context context, int value) {
        prefs(context)
                .edit()
                .putInt("radius", value)
                .apply();
    }

    public static int getKeyboardColor(Context context) {
        return prefs(context).getInt(
                "keyboard_color",
                Color.rgb(25, 25, 28)
        );
    }

    public static int getKeyColor(Context context) {
        return prefs(context).getInt(
                "key_color",
                Color.rgb(69, 69, 74)
        );
    }

    public static int getSpecialKeyColor(Context context) {
        return prefs(context).getInt(
                "special_color",
                Color.rgb(48, 48, 53)
        );
    }
}
