package com.customkey;

import android.graphics.RectF;

/** One key on one page. Instances are rebuilt whenever the layout changes. */
public class KeyModel {

    public static final int TEXT = 0;
    public static final int SHIFT = 1;
    public static final int BACKSPACE = 2;
    public static final int ENTER = 3;
    public static final int SPACE = 4;

    public static final int CURSOR_LEFT = 5;
    public static final int CURSOR_RIGHT = 6;

    public static final int COPY = 7;
    public static final int CUT = 8;
    public static final int PASTE = 9;
    public static final int SELECT_ALL = 10;

    public static final int PAGE = 11;
    public static final int IME_SWITCH = 12;
    public static final int SETTINGS = 13;
    public static final int HIDE = 14;

    public final String id;
    public final int type;
    public final int targetPage;

    public String label;
    public String output;
    public float width;

    public final RectF bounds = new RectF();

    public boolean selected;

    public KeyModel(
            String id,
            String label,
            String output,
            int type,
            float width,
            int targetPage
    ) {
        this.id = id;
        this.label = label;
        this.output = output;
        this.type = type;
        this.width = width;
        this.targetPage = targetPage;
    }

    public boolean isSpecial() {
        return type != TEXT;
    }

    public KeyModel copy() {
        KeyModel k = new KeyModel(id, label, output, type, width, targetPage);
        k.selected = selected;
        return k;
    }
}
