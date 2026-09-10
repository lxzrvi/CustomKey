package com.customkey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class CustomKeyboardView extends View {

    public interface Listener {
        void onKeyPressed(KeyModel key);
    }

    private final Paint keyPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint textPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<List<KeyModel>> rows =
            new ArrayList<>();

    private Listener listener;

    private final float density;

    private int keyHeight;
    private int spacing;
    private int radius;

    private boolean shifted = false;
    private boolean symbols = false;

    private KeyModel pressedKey;

    private boolean repeatingDelete = false;

    private final Runnable deleteRepeater =
            new Runnable() {
                @Override
                public void run() {
                    if (repeatingDelete
                            && pressedKey != null
                            && pressedKey.type == KeyModel.BACKSPACE) {

                        if (listener != null) {
                            listener.onKeyPressed(pressedKey);
                        }

                        postDelayed(this, 55);
                    }
                }
            };

    public CustomKeyboardView(Context context) {
        super(context);

        density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );

        reloadSettings();
        showLetters();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reloadSettings() {
        keyHeight =
                AppSettings.getKeyHeight(getContext());

        spacing =
                AppSettings.getSpacing(getContext());

        radius =
                AppSettings.getRadius(getContext());

        requestLayout();
        invalidate();
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private KeyModel text(String value) {
        return new KeyModel(
                value,
                value,
                KeyModel.TEXT,
                1f
        );
    }

    private KeyModel action(
            String label,
            int type,
            float width
    ) {
        return new KeyModel(
                label,
                "",
                type,
                width
        );
    }

    private void showLetters() {
        rows.clear();

        List<KeyModel> r1 = new ArrayList<>();

        for (String key :
                new String[]{
                        "q", "w", "e", "r", "t",
                        "y", "u", "i", "o", "p"
                }) {

            r1.add(text(key));
        }


        List<KeyModel> r2 = new ArrayList<>();

        for (String key :
                new String[]{
                        "a", "s", "d", "f", "g",
                        "h", "j", "k", "l"
                }) {

            r2.add(text(key));
        }


        List<KeyModel> r3 = new ArrayList<>();

        r3.add(
                action(
                        "⇧",
                        KeyModel.SHIFT,
                        1.3f
                )
        );

        for (String key :
                new String[]{
                        "z", "x", "c", "v",
                        "b", "n", "m"
                }) {

            r3.add(text(key));
        }

        r3.add(
                action(
                        "⌫",
                        KeyModel.BACKSPACE,
                        1.3f
                )
        );


        List<KeyModel> r4 = new ArrayList<>();

        r4.add(
                action(
                        "?123",
                        KeyModel.SYMBOLS,
                        1.5f
                )
        );

        r4.add(text(","));

        r4.add(
                action(
                        "space",
                        KeyModel.SPACE,
                        4f
                )
        );

        r4.add(text("."));

        r4.add(
                action(
                        "↵",
                        KeyModel.ENTER,
                        1.5f
                )
        );


        List<KeyModel> tools = createToolsRow();

        rows.add(r1);
        rows.add(r2);
        rows.add(r3);
        rows.add(r4);
        rows.add(tools);

        requestLayout();
        invalidate();
    }

    private void showSymbols() {
        rows.clear();

        String[][] data = {
                {
                        "1", "2", "3", "4", "5",
                        "6", "7", "8", "9", "0"
                },
                {
                        "@", "#", "$", "%", "&",
                        "-", "+", "(", ")", "/"
                },
                {
                        "*", "\"", "'", ":",
                        ";", "!", "?", "=", "_"
                }
        };

        for (String[] dataRow : data) {
            List<KeyModel> row =
                    new ArrayList<>();

            for (String key : dataRow) {
                row.add(text(key));
            }

            rows.add(row);
        }


        List<KeyModel> bottom =
                new ArrayList<>();

        bottom.add(
                action(
                        "ABC",
                        KeyModel.SYMBOLS,
                        1.5f
                )
        );

        bottom.add(text(","));

        bottom.add(
                action(
                        "space",
                        KeyModel.SPACE,
                        4f
                )
        );

        bottom.add(text("."));

        bottom.add(
                action(
                        "↵",
                        KeyModel.ENTER,
                        1.5f
                )
        );

        rows.add(bottom);
        rows.add(createToolsRow());

        requestLayout();
        invalidate();
    }

    private List<KeyModel> createToolsRow() {
        List<KeyModel> row =
                new ArrayList<>();

        row.add(
                action(
                        "ALL",
                        KeyModel.SELECT_ALL,
                        1.2f
                )
        );

        row.add(
                action(
                        "CUT",
                        KeyModel.CUT,
                        1.2f
                )
        );

        row.add(
                action(
                        "COPY",
                        KeyModel.COPY,
                        1.4f
                )
        );

        row.add(
                action(
                        "PASTE",
                        KeyModel.PASTE,
                        1.5f
                )
        );

        row.add(
                action(
                        "←",
                        KeyModel.CURSOR_LEFT,
                        1f
                )
        );

        row.add(
                action(
                        "→",
                        KeyModel.CURSOR_RIGHT,
                        1f
                )
        );

        return row;
    }

    public boolean isShifted() {
        return shifted;
    }

    public void toggleShift() {
        shifted = !shifted;
        invalidate();
    }

    public void clearShift() {
        shifted = false;
        invalidate();
    }

    public void toggleSymbols() {
        symbols = !symbols;

        if (symbols) {
            showSymbols();
        } else {
            showLetters();
        }
    }

    @Override
    protected void onMeasure(
            int widthMeasureSpec,
            int heightMeasureSpec
    ) {

        int width =
                MeasureSpec.getSize(widthMeasureSpec);

        int h =
                rows.size() * dp(keyHeight)
                        + (rows.size() + 1)
                        * dp(spacing);

        setMeasuredDimension(width, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(
                AppSettings.getKeyboardColor(
                        getContext()
                )
        );

        float gap = dp(spacing);
        float h = dp(keyHeight);
        float corner = dp(radius);

        textPaint.setTextSize(dp(16));

        float y = gap;

        for (List<KeyModel> row : rows) {

            float totalWeight = 0;

            for (KeyModel key : row) {
                totalWeight += key.width;
            }

            float usable =
                    getWidth()
                            - gap
                            * (row.size() + 1);

            float oneUnit =
                    usable / totalWeight;

            float x = gap;

            for (KeyModel key : row) {

                float w =
                        oneUnit * key.width;

                key.bounds.set(
                        x,
                        y,
                        x + w,
                        y + h
                );

                boolean special =
                        key.type != KeyModel.TEXT;

                int color =
                        special
                                ? AppSettings
                                .getSpecialKeyColor(
                                        getContext()
                                )
                                : AppSettings
                                .getKeyColor(
                                        getContext()
                                );

                if (pressedKey == key) {
                    color = brighten(color);
                }

                keyPaint.setColor(color);

                canvas.drawRoundRect(
                        key.bounds,
                        corner,
                        corner,
                        keyPaint
                );

                String display = key.label;

                if (key.type == KeyModel.TEXT
                        && shifted) {

                    display =
                            display.toUpperCase();
                }

                Paint.FontMetrics fm =
                        textPaint.getFontMetrics();

                float textY =
                        key.bounds.centerY()
                                - (fm.ascent
                                + fm.descent)
                                / 2f;

                canvas.drawText(
                        display,
                        key.bounds.centerX(),
                        textY,
                        textPaint
                );

                x += w + gap;
            }

            y += h + gap;
        }
    }

    private int brighten(int color) {
        return Color.rgb(
                Math.min(
                        255,
                        Color.red(color) + 35
                ),
                Math.min(
                        255,
                        Color.green(color) + 35
                ),
                Math.min(
                        255,
                        Color.blue(color) + 35
                )
        );
    }

    private KeyModel findKey(float x, float y) {
        for (List<KeyModel> row : rows) {

            for (KeyModel key : row) {

                if (key.bounds.contains(x, y)) {
                    return key;
                }
            }
        }

        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                pressedKey =
                        findKey(
                                event.getX(),
                                event.getY()
                        );

                invalidate();

                if (pressedKey != null
                        && pressedKey.type
                        == KeyModel.BACKSPACE) {

                    repeatingDelete = true;

                    postDelayed(
                            deleteRepeater,
                            450
                    );
                }

                return true;


            case MotionEvent.ACTION_UP:

                KeyModel released =
                        findKey(
                                event.getX(),
                                event.getY()
                        );

                repeatingDelete = false;
                removeCallbacks(deleteRepeater);

                if (pressedKey != null
                        && pressedKey == released
                        && listener != null) {

                    listener.onKeyPressed(
                            pressedKey
                    );
                }

                pressedKey = null;
                invalidate();

                return true;


            case MotionEvent.ACTION_CANCEL:

                repeatingDelete = false;

                removeCallbacks(
                        deleteRepeater
                );

                pressedKey = null;
                invalidate();

                return true;
        }

        return true;
    }
}
