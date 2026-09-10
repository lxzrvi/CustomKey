package com.customkey;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Per-key overrides. Any field left null / empty means "inherit the global
 * value from {@link Config}".
 */
public class KeyStyle {

    public Integer bg;
    public Integer textColor;
    public Integer radius;
    public Integer borderColor;
    public Float textSize;
    public Float borderWidth;
    public String bgImage = "";
    public Integer haptic;      // 0..100, -1 = off for this key
    public Integer sound;       // 0..100, -1 = off for this key

    public boolean isEmpty() {
        return bg == null && textColor == null && radius == null
                && borderColor == null && textSize == null && borderWidth == null
                && (bgImage == null || bgImage.length() == 0)
                && haptic == null && sound == null;
    }

    public KeyStyle copy() {
        KeyStyle s = new KeyStyle();
        s.bg = bg;
        s.textColor = textColor;
        s.radius = radius;
        s.borderColor = borderColor;
        s.textSize = textSize;
        s.borderWidth = borderWidth;
        s.bgImage = bgImage;
        s.haptic = haptic;
        s.sound = sound;
        return s;
    }

    /** Copies every non-null field of {@code from} onto this style. */
    public void applyFrom(KeyStyle from) {
        if (from == null) {
            return;
        }
        if (from.bg != null) {
            bg = from.bg;
        }
        if (from.textColor != null) {
            textColor = from.textColor;
        }
        if (from.radius != null) {
            radius = from.radius;
        }
        if (from.borderColor != null) {
            borderColor = from.borderColor;
        }
        if (from.textSize != null) {
            textSize = from.textSize;
        }
        if (from.borderWidth != null) {
            borderWidth = from.borderWidth;
        }
        if (from.bgImage != null && from.bgImage.length() > 0) {
            bgImage = from.bgImage;
        }
        if (from.haptic != null) {
            haptic = from.haptic;
        }
        if (from.sound != null) {
            sound = from.sound;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        if (bg != null) {
            o.put("bg", bg.intValue());
        }
        if (textColor != null) {
            o.put("txt", textColor.intValue());
        }
        if (radius != null) {
            o.put("rad", radius.intValue());
        }
        if (borderColor != null) {
            o.put("bc", borderColor.intValue());
        }
        if (textSize != null) {
            o.put("ts", textSize.doubleValue());
        }
        if (borderWidth != null) {
            o.put("bw", borderWidth.doubleValue());
        }
        if (bgImage != null && bgImage.length() > 0) {
            o.put("img", bgImage);
        }
        if (haptic != null) {
            o.put("hap", haptic.intValue());
        }
        if (sound != null) {
            o.put("snd", sound.intValue());
        }
        return o;
    }

    public static KeyStyle fromJson(JSONObject o) {
        KeyStyle s = new KeyStyle();
        if (o.has("bg")) {
            s.bg = o.optInt("bg");
        }
        if (o.has("txt")) {
            s.textColor = o.optInt("txt");
        }
        if (o.has("rad")) {
            s.radius = o.optInt("rad");
        }
        if (o.has("bc")) {
            s.borderColor = o.optInt("bc");
        }
        if (o.has("ts")) {
            s.textSize = (float) o.optDouble("ts");
        }
        if (o.has("bw")) {
            s.borderWidth = (float) o.optDouble("bw");
        }
        s.bgImage = o.optString("img", "");
        if (o.has("hap")) {
            s.haptic = o.optInt("hap");
        }
        if (o.has("snd")) {
            s.sound = o.optInt("snd");
        }
        return s;
    }

    /** Concrete, fully resolved paint values for one key. */
    public static class Resolved {
        public int bg;
        public int textColor;
        public int borderColor;
        public float radiusDp;
        public float borderWidthDp;
        public float textSizeSp;
        public String bgImage = "";
        public int haptic;
        public int sound;
    }

    /**
     * Merges the global config with the per-key override.
     *
     * @param special true for non-text keys (shift, enter, tools…)
     */
    public static void resolve(Config c, KeyStyle s, boolean special, Resolved out) {
        out.bg = special ? c.specialColor : c.keyColor;
        out.textColor = special ? c.specialTextColor : c.keyTextColor;
        out.radiusDp = c.radius;
        out.borderWidthDp = c.borderWidth;
        out.borderColor = c.borderColor;
        out.textSizeSp = c.textSize;
        out.bgImage = "";
        out.haptic = c.haptics ? c.hapticStrength : -1;
        out.sound = c.sound ? c.soundVolume : -1;

        if (s != null) {
            if (s.bg != null) {
                out.bg = s.bg;
            }
            if (s.textColor != null) {
                out.textColor = s.textColor;
            }
            if (s.radius != null) {
                out.radiusDp = s.radius;
            }
            if (s.borderWidth != null) {
                out.borderWidthDp = s.borderWidth;
            }
            if (s.borderColor != null) {
                out.borderColor = s.borderColor;
            }
            if (s.textSize != null) {
                out.textSizeSp = s.textSize;
            }
            if (s.bgImage != null) {
                out.bgImage = s.bgImage;
            }
            if (s.haptic != null) {
                out.haptic = s.haptic;
            }
            if (s.sound != null) {
                out.sound = s.sound;
            }
        }
    }
}
