package com.customkey;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Colour wheel + hex field + transparency, plus a few presets. Used for the
 * keyboard background, key colours, text colours and borders.
 */
public final class ColorPickerDialog {

    public interface Callback {
        void onColor(int color);
    }

    private static final int[] PRESETS = {
            0xFFFFFFFF, 0xFFF1F1F3, 0xFFE3E3E7, 0xFFBDBDC4,
            0xFF6C6C73, 0xFF2A2A2E, 0xFF151517, 0xFF0A0A0A,
            0x00000000
    };

    private ColorPickerDialog() {
    }

    public static void show(Context context, String title, int initial,
                            final Callback callback) {

        final int[] current = {initial};
        final boolean[] syncing = {false};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(context, 18);
        root.setPadding(pad, Ui.dp(context, 14), pad, 0);

        final ColorWheelView wheel = new ColorWheelView(context);
        wheel.setColor(initial);
        root.addView(wheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // hex + alpha readout
        LinearLayout hexRow = new LinearLayout(context);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(context, 14);
        hexRow.setLayoutParams(hp);

        final EditText hex = Ui.input(context, "#AARRGGBB", Ui.hex(initial));
        hex.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hexRow.addView(hex);

        final TextView alpha = new TextView(context);
        alpha.setTextColor(Ui.attr(context, R.attr.ckTextSecondary));
        alpha.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(context, 12);
        alpha.setLayoutParams(ap);
        alpha.setText(Math.round(Color.alpha(initial) / 255f * 100) + "% alpha");
        hexRow.addView(alpha);

        root.addView(hexRow);

        // presets
        TextView presetLabel = new TextView(context);
        presetLabel.setText("PRESETS");
        presetLabel.setTextColor(Ui.attr(context, R.attr.ckTextSecondary));
        presetLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        presetLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        presetLabel.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(context, 16);
        lp.bottomMargin = Ui.dp(context, 8);
        presetLabel.setLayoutParams(lp);
        root.addView(presetLabel);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(strip);
        root.addView(scroll);

        wheel.setOnColorChangedListener(color -> {
            current[0] = color;
            if (!syncing[0]) {
                syncing[0] = true;
                hex.setText(Ui.hex(color));
                hex.setSelection(hex.getText().length());
                syncing[0] = false;
            }
            alpha.setText(Math.round(Color.alpha(color) / 255f * 100) + "% alpha");
        });

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) {
                    return;
                }
                Integer parsed = parse(s.toString());
                if (parsed != null) {
                    current[0] = parsed;
                    syncing[0] = true;
                    wheel.setColor(parsed);
                    syncing[0] = false;
                    alpha.setText(Math.round(Color.alpha(parsed) / 255f * 100)
                            + "% alpha");
                }
            }
        });

        for (final int preset : PRESETS) {
            View chip = new View(context);
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(preset);
            bg.setCornerRadius(Ui.dp(context, 9));
            bg.setStroke(1, 0x33000000);
            chip.setBackground(bg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    Ui.dp(context, 34), Ui.dp(context, 34));
            cp.rightMargin = Ui.dp(context, 8);
            chip.setLayoutParams(cp);
            chip.setOnClickListener(v -> {
                current[0] = preset;
                syncing[0] = true;
                wheel.setColor(preset);
                hex.setText(Ui.hex(preset));
                syncing[0] = false;
                alpha.setText(Math.round(Color.alpha(preset) / 255f * 100)
                        + "% alpha");
            });
            strip.addView(chip);
        }

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    if (callback != null) {
                        callback.onColor(current[0]);
                    }
                })
                .show();
    }

    public static Integer parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() == 6) {
            value = "FF" + value;
        }
        if (value.length() != 8) {
            return null;
        }
        try {
            return (int) Long.parseLong(value, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
