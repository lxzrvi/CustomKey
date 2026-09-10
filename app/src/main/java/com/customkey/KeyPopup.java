package com.customkey;

import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * The little card that floats above a key while it is pressed, the way most
 * stock keyboards do it.
 */
public final class KeyPopup {

    private static PopupWindow window;

    private KeyPopup() {
    }

    public static void show(View parent, RectF bounds, String label,
                            int bgColor, int textColor, float radiusDp,
                            float textSizeSp, Typeface typeface, float density) {

        hide();

        if (parent == null || !parent.isAttachedToWindow() || label == null
                || label.length() == 0) {
            return;
        }

        try {
            float d = density;
            int width = (int) Math.min(140 * d,
                    Math.max(46 * d, bounds.width() * 1.05f));
            int height = (int) Math.max(44 * d, bounds.height() * 1.15f);

            TextView text = new TextView(parent.getContext());
            text.setText(label);
            text.setGravity(Gravity.CENTER);
            text.setSingleLine(true);
            text.setTextColor(textColor);
            text.setTypeface(typeface);
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                    textSizeSp * 1.5f);

            GradientDrawable background = new GradientDrawable();
            background.setColor(shade(bgColor, 18));
            background.setCornerRadius(radiusDp * d);
            background.setStroke(Math.max(1, (int) (1 * d)),
                    Color.argb(40, 128, 128, 128));
            text.setBackground(background);
            text.setElevation(10 * d);

            int[] location = new int[2];
            parent.getLocationOnScreen(location);

            int x = (int) (location[0] + bounds.centerX() - width / 2f);
            int y = (int) (location[1] + bounds.top - height - 6 * d);

            int screenW = parent.getResources().getDisplayMetrics().widthPixels;
            x = Math.max(0, Math.min(x, Math.max(0, screenW - width)));
            y = Math.max(0, y);

            PopupWindow popup = new PopupWindow(text, width, height, false);
            popup.setTouchable(false);
            popup.setFocusable(false);
            popup.setOutsideTouchable(false);
            popup.setClippingEnabled(false);
            popup.showAtLocation(parent, Gravity.TOP | Gravity.START, x, y);

            window = popup;
        } catch (Throwable ignored) {
            window = null;
        }
    }

    public static void hide() {
        if (window != null) {
            try {
                if (window.isShowing()) {
                    window.dismiss();
                }
            } catch (Throwable ignored) {
            }
            window = null;
        }
    }

    private static int shade(int color, int delta) {
        boolean light = (Color.red(color) * 299
                + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000 > 140;
        int d = light ? -delta : delta;
        return Color.argb(
                Color.alpha(color),
                Math.max(0, Math.min(255, Color.red(color) + d)),
                Math.max(0, Math.min(255, Color.green(color) + d)),
                Math.max(0, Math.min(255, Color.blue(color) + d)));
    }
}
