package com.customkey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Everything the keyboard and the app know about themselves.
 * One instance is kept in memory by {@link Prefs} and shared by the
 * activity process and the IME service (same process), so edits made in
 * the editor are visible to a live keyboard instantly.
 */
public class Config {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int BG_COLOR = 0;
    public static final int BG_IMAGE = 1;

    public static final int FONT_SYSTEM = 0;
    public static final int FONT_CUSTOM = 1;

    /* ---------------- theme ---------------- */
    public int theme = THEME_SYSTEM;

    /* ---------------- geometry (dp) ---------------- */
    public int keyHeight = 46;
    public int spacing = 3;
    public int radius = 10;
    public float textSize = 17f;
    public float borderWidth = 0f;
    public int borderColor = 0xFF000000;
    public int pressedBoost = 26;

    /* ---------------- keyboard background ---------------- */
    public int bgType = BG_COLOR;
    public int bgColor = 0xFFFFFFFF;
    public String bgImage = "";
    public int bgImageDim = 0;      // 0..200 dark scrim over the image

    /* ---------------- key colours ---------------- */
    public int keyColor = 0xFFF1F1F3;
    public int keyTextColor = 0xFF0A0A0A;
    public int specialColor = 0xFFE3E3E7;
    public int specialTextColor = 0xFF0A0A0A;

    /* ---------------- rows ---------------- */
    public boolean numberRow = true;
    public boolean toolsRow = true;
    public boolean emojiKey = true;
    public boolean bottomBar = true;

    /* ---------------- font ---------------- */
    public int fontMode = FONT_SYSTEM;
    public String fontFamily = "sans-serif-medium";
    public String fontUri = "";

    /* ---------------- long press & popup ---------------- */
    public int longPressDelay = 400;
    public boolean keyPopup = true;
    public boolean showCandidates = true;

    /* ---------------- haptics & sound ---------------- */
    public boolean haptics = true;
    public int hapticStrength = 45;     // 0..100
    public boolean hapticOnTap = true;
    public boolean sound = false;
    public int soundVolume = 60;        // 0..100
    public String soundUri = "";

    /* ---------------- per key ---------------- */
    public final Map<String, KeyStyle> styles = new HashMap<>();
    public final Map<String, List<String>> longPress = new HashMap<>();
    /** key id -> renamed label (also becomes the output for text keys) */
    public final Map<String, String> labels = new HashMap<>();
    /** page -> rows -> key ids */
    public final Map<Integer, List<List<String>>> order = new HashMap<>();

    public Config copy() {
        Config c = new Config();
        c.theme = theme;
        c.keyHeight = keyHeight;
        c.spacing = spacing;
        c.radius = radius;
        c.textSize = textSize;
        c.borderWidth = borderWidth;
        c.borderColor = borderColor;
        c.pressedBoost = pressedBoost;
        c.bgType = bgType;
        c.bgColor = bgColor;
        c.bgImage = bgImage;
        c.bgImageDim = bgImageDim;
        c.keyColor = keyColor;
        c.keyTextColor = keyTextColor;
        c.specialColor = specialColor;
        c.specialTextColor = specialTextColor;
        c.numberRow = numberRow;
        c.toolsRow = toolsRow;
        c.emojiKey = emojiKey;
        c.bottomBar = bottomBar;
        c.fontMode = fontMode;
        c.fontFamily = fontFamily;
        c.fontUri = fontUri;
        c.longPressDelay = longPressDelay;
        c.keyPopup = keyPopup;
        c.showCandidates = showCandidates;
        c.haptics = haptics;
        c.hapticStrength = hapticStrength;
        c.hapticOnTap = hapticOnTap;
        c.sound = sound;
        c.soundVolume = soundVolume;
        c.soundUri = soundUri;

        for (Map.Entry<String, KeyStyle> e : styles.entrySet()) {
            c.styles.put(e.getKey(), e.getValue().copy());
        }
        for (Map.Entry<String, List<String>> e : longPress.entrySet()) {
            c.longPress.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        c.labels.putAll(labels);
        for (Map.Entry<Integer, List<List<String>>> e : order.entrySet()) {
            List<List<String>> rows = new ArrayList<>();
            for (List<String> r : e.getValue()) {
                rows.add(new ArrayList<>(r));
            }
            c.order.put(e.getKey(), rows);
        }
        return c;
    }

    /* ================= (de)serialisation ================= */

    public String toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("theme", theme);
            o.put("keyHeight", keyHeight);
            o.put("spacing", spacing);
            o.put("radius", radius);
            o.put("textSize", (double) textSize);
            o.put("borderWidth", (double) borderWidth);
            o.put("borderColor", borderColor);
            o.put("pressedBoost", pressedBoost);

            o.put("bgType", bgType);
            o.put("bgColor", bgColor);
            o.put("bgImage", bgImage);
            o.put("bgImageDim", bgImageDim);

            o.put("keyColor", keyColor);
            o.put("keyTextColor", keyTextColor);
            o.put("specialColor", specialColor);
            o.put("specialTextColor", specialTextColor);

            o.put("numberRow", numberRow);
            o.put("toolsRow", toolsRow);
            o.put("emojiKey", emojiKey);
            o.put("bottomBar", bottomBar);

            o.put("fontMode", fontMode);
            o.put("fontFamily", fontFamily);
            o.put("fontUri", fontUri);

            o.put("longPressDelay", longPressDelay);
            o.put("keyPopup", keyPopup);
            o.put("showCandidates", showCandidates);

            o.put("haptics", haptics);
            o.put("hapticStrength", hapticStrength);
            o.put("hapticOnTap", hapticOnTap);
            o.put("sound", sound);
            o.put("soundVolume", soundVolume);
            o.put("soundUri", soundUri);

            JSONObject st = new JSONObject();
            for (Map.Entry<String, KeyStyle> e : styles.entrySet()) {
                st.put(e.getKey(), e.getValue().toJson());
            }
            o.put("styles", st);

            JSONObject lp = new JSONObject();
            for (Map.Entry<String, List<String>> e : longPress.entrySet()) {
                JSONArray a = new JSONArray();
                for (String s : e.getValue()) {
                    a.put(s);
                }
                lp.put(e.getKey(), a);
            }
            o.put("longPress", lp);

            JSONObject lb = new JSONObject();
            for (Map.Entry<String, String> e : labels.entrySet()) {
                lb.put(e.getKey(), e.getValue());
            }
            o.put("labels", lb);

            JSONObject od = new JSONObject();
            for (Map.Entry<Integer, List<List<String>>> e : order.entrySet()) {
                JSONArray rows = new JSONArray();
                for (List<String> r : e.getValue()) {
                    JSONArray row = new JSONArray();
                    for (String s : r) {
                        row.put(s);
                    }
                    rows.put(row);
                }
                od.put(String.valueOf(e.getKey()), rows);
            }
            o.put("order", od);
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    public static Config fromJson(String json) {
        Config c = new Config();
        if (json == null || json.trim().length() == 0) {
            return c;
        }
        try {
            JSONObject o = new JSONObject(json);

            c.theme = o.optInt("theme", c.theme);
            c.keyHeight = o.optInt("keyHeight", c.keyHeight);
            c.spacing = o.optInt("spacing", c.spacing);
            c.radius = o.optInt("radius", c.radius);
            c.textSize = (float) o.optDouble("textSize", c.textSize);
            c.borderWidth = (float) o.optDouble("borderWidth", c.borderWidth);
            c.borderColor = o.optInt("borderColor", c.borderColor);
            c.pressedBoost = o.optInt("pressedBoost", c.pressedBoost);

            c.bgType = o.optInt("bgType", c.bgType);
            c.bgColor = o.optInt("bgColor", c.bgColor);
            c.bgImage = o.optString("bgImage", c.bgImage);
            c.bgImageDim = o.optInt("bgImageDim", c.bgImageDim);

            c.keyColor = o.optInt("keyColor", c.keyColor);
            c.keyTextColor = o.optInt("keyTextColor", c.keyTextColor);
            c.specialColor = o.optInt("specialColor", c.specialColor);
            c.specialTextColor = o.optInt("specialTextColor", c.specialTextColor);

            c.numberRow = o.optBoolean("numberRow", c.numberRow);
            c.toolsRow = o.optBoolean("toolsRow", c.toolsRow);
            c.emojiKey = o.optBoolean("emojiKey", c.emojiKey);
            c.bottomBar = o.optBoolean("bottomBar", c.bottomBar);

            c.fontMode = o.optInt("fontMode", c.fontMode);
            c.fontFamily = o.optString("fontFamily", c.fontFamily);
            c.fontUri = o.optString("fontUri", c.fontUri);

            c.longPressDelay = o.optInt("longPressDelay", c.longPressDelay);
            c.keyPopup = o.optBoolean("keyPopup", c.keyPopup);
            c.showCandidates = o.optBoolean("showCandidates", c.showCandidates);

            c.haptics = o.optBoolean("haptics", c.haptics);
            c.hapticStrength = o.optInt("hapticStrength", c.hapticStrength);
            c.hapticOnTap = o.optBoolean("hapticOnTap", c.hapticOnTap);
            c.sound = o.optBoolean("sound", c.sound);
            c.soundVolume = o.optInt("soundVolume", c.soundVolume);
            c.soundUri = o.optString("soundUri", c.soundUri);

            JSONObject st = o.optJSONObject("styles");
            if (st != null) {
                Iterator<String> it = st.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject so = st.optJSONObject(k);
                    if (so != null) {
                        c.styles.put(k, KeyStyle.fromJson(so));
                    }
                }
            }

            JSONObject lp = o.optJSONObject("longPress");
            if (lp != null) {
                Iterator<String> it = lp.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray a = lp.optJSONArray(k);
                    if (a != null) {
                        List<String> list = new ArrayList<>();
                        for (int i = 0; i < a.length(); i++) {
                            list.add(a.optString(i));
                        }
                        c.longPress.put(k, list);
                    }
                }
            }

            JSONObject lb = o.optJSONObject("labels");
            if (lb != null) {
                Iterator<String> it = lb.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    c.labels.put(k, lb.optString(k));
                }
            }

            JSONObject od = o.optJSONObject("order");
            if (od != null) {
                Iterator<String> it = od.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray rowsA = od.optJSONArray(k);
                    if (rowsA == null) {
                        continue;
                    }
                    List<List<String>> rows = new ArrayList<>();
                    for (int i = 0; i < rowsA.length(); i++) {
                        JSONArray rowA = rowsA.optJSONArray(i);
                        if (rowA == null) {
                            continue;
                        }
                        List<String> row = new ArrayList<>();
                        for (int j = 0; j < rowA.length(); j++) {
                            row.add(rowA.optString(j));
                        }
                        rows.add(row);
                    }
                    c.order.put(Integer.parseInt(k), rows);
                }
            }
        } catch (Exception ignored) {
        }
        return c;
    }

    /** Replaces everything in this instance with the contents of {@code other}. */
    public void replaceWith(Config other) {
        styles.clear();
        longPress.clear();
        labels.clear();
        order.clear();

        Config c = other.copy();
        theme = c.theme;
        keyHeight = c.keyHeight;
        spacing = c.spacing;
        radius = c.radius;
        textSize = c.textSize;
        borderWidth = c.borderWidth;
        borderColor = c.borderColor;
        pressedBoost = c.pressedBoost;
        bgType = c.bgType;
        bgColor = c.bgColor;
        bgImage = c.bgImage;
        bgImageDim = c.bgImageDim;
        keyColor = c.keyColor;
        keyTextColor = c.keyTextColor;
        specialColor = c.specialColor;
        specialTextColor = c.specialTextColor;
        numberRow = c.numberRow;
        toolsRow = c.toolsRow;
        emojiKey = c.emojiKey;
        bottomBar = c.bottomBar;
        fontMode = c.fontMode;
        fontFamily = c.fontFamily;
        fontUri = c.fontUri;
        longPressDelay = c.longPressDelay;
        keyPopup = c.keyPopup;
        showCandidates = c.showCandidates;
        haptics = c.haptics;
        hapticStrength = c.hapticStrength;
        hapticOnTap = c.hapticOnTap;
        sound = c.sound;
        soundVolume = c.soundVolume;
        soundUri = c.soundUri;
        styles.putAll(c.styles);
        longPress.putAll(c.longPress);
        labels.putAll(c.labels);
        order.putAll(c.order);
    }
}
