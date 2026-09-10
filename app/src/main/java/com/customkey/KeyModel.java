package com.customkey;

import android.graphics.RectF;

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

    public static final int SYMBOLS = 11;

    public final String label;
    public final String output;
    public final int type;
    public final float width;

    public final RectF bounds = new RectF();

    public KeyModel(
            String label,
            String output,
            int type,
            float width
    ) {
        this.label = label;
        this.output = output;
        this.type = type;
        this.width = width;
    }
}
