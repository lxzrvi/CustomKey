package com.customkey;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/** System / light / dark, applied before setContentView(). */
public final class ThemeManager {

    private ThemeManager() {
    }

    public static void apply(Activity activity) {
        int mode = Prefs.get(activity).theme;
        switch (mode) {
            case Config.THEME_LIGHT:
                activity.setTheme(R.style.Theme_CustomKey_Light);
                break;
            case Config.THEME_DARK:
                activity.setTheme(R.style.Theme_CustomKey_Dark);
                break;
            case Config.THEME_SYSTEM:
            default:
                activity.setTheme(R.style.Theme_CustomKey);
                break;
        }
    }

    public static void setMode(Activity activity, int mode) {
        Prefs.get(activity).theme = mode;
        Prefs.save(activity);
        activity.recreate();
    }

    public static boolean isDark(Context context) {
        int mode = Prefs.get(context).theme;
        if (mode == Config.THEME_LIGHT) {
            return false;
        }
        if (mode == Config.THEME_DARK) {
            return true;
        }
        int ui = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    public static String label(int mode) {
        switch (mode) {
            case Config.THEME_LIGHT:
                return "Light";
            case Config.THEME_DARK:
                return "Dark";
            case Config.THEME_SYSTEM:
            default:
                return "System";
        }
    }
}
