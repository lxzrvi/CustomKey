package com.customkey;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Programmatic building blocks for the premium black &amp; white UI. Keeping
 * these in code means the editor screens stay short and every row behaves
 * identically.
 */
public final class Ui {

    public interface OnSlide {
        void onSlide(int value);
    }

    public interface OnToggle {
        void onToggle(boolean value);
    }

    /** Handle returned by the add* helpers so callers can refresh values. */
    public static class Row {
        public View root;
        public TextView label;
        public TextView sub;
        public TextView value;
        public ImageView icon;
        public View swatch;
        public Switch toggle;
        public SeekBar seek;

        public void value(String text) {
            if (value != null) {
                value.setText(text);
            }
        }

        public void sub(String text) {
            if (sub != null) {
                sub.setText(text);
                sub.setVisibility(text == null || text.length() == 0
                        ? View.GONE : View.VISIBLE);
            }
        }

        public void swatch(int color) {
            if (swatch != null) {
                swatch.setBackgroundColor(color);
            }
        }
    }

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int attr(Context context, int attrRes) {
        TypedValue out = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, out, true);
        return out.data;
    }

    /* ---------------- containers ---------------- */

    public static LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.bg_card);
        int pad = dp(context, 6);
        layout.setPadding(pad, dp(context, 8), pad, dp(context, 8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 14);
        layout.setLayoutParams(params);
        return layout;
    }

    public static TextView header(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(attr(context, R.attr.ckTextSecondary));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setLetterSpacing(0.12f);
        view.setAllCaps(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(context, 16);
        params.topMargin = dp(context, 4);
        params.bottomMargin = dp(context, 8);
        view.setLayoutParams(params);
        return view;
    }

    public static TextView cardTitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(attr(context, R.attr.ckTextPrimary));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(context, 12);
        params.rightMargin = dp(context, 12);
        params.topMargin = dp(context, 8);
        params.bottomMargin = dp(context, 4);
        view.setLayoutParams(params);
        return view;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(attr(context, R.attr.ckBorder));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1)));
        params.leftMargin = dp(context, 14);
        params.rightMargin = dp(context, 14);
        view.setLayoutParams(params);
        return view;
    }

    /* ---------------- rows ---------------- */

    public static Row addAction(LinearLayout parent, int iconRes,
                                String label, String valueText,
                                View.OnClickListener onClick) {

        Context context = parent.getContext();
        Row row = new Row();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundResource(R.drawable.bg_row_ripple);
        root.setClickable(true);
        root.setFocusable(true);
        root.setMinimumHeight(dp(context, 54));
        int pad = dp(context, 14);
        root.setPadding(pad, dp(context, 10), pad, dp(context, 10));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(attr(context, R.attr.ckTextPrimary));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    dp(context, 21), dp(context, 21));
            ip.rightMargin = dp(context, 14);
            icon.setLayoutParams(ip);
            root.addView(icon);
            row.icon = icon;
        }

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(tp);

        row.label = new TextView(context);
        row.label.setText(label);
        row.label.setTextColor(attr(context, R.attr.ckTextPrimary));
        row.label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.addView(row.label);

        if (valueText != null) {
            row.sub = new TextView(context);
            row.sub.setText(valueText);
            row.sub.setTextColor(attr(context, R.attr.ckTextSecondary));
            row.sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            text.addView(row.sub);
        }
        root.addView(text);

        if (valueText != null) {
            row.value = new TextView(context);
            row.value.setTextColor(attr(context, R.attr.ckTextSecondary));
            row.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.value.setVisibility(View.GONE);
            root.addView(row.value);
        }

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setColorFilter(attr(context, R.attr.ckTextSecondary));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                dp(context, 18), dp(context, 18));
        cp.leftMargin = dp(context, 8);
        chevron.setLayoutParams(cp);
        root.addView(chevron);

        if (onClick != null) {
            root.setOnClickListener(onClick);
        }

        parent.addView(root);
        row.root = root;
        return row;
    }

    public static Row addSwitch(LinearLayout parent, String label, String sub,
                                boolean value, final OnToggle onToggle) {

        Context context = parent.getContext();
        Row row = new Row();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundResource(R.drawable.bg_row_ripple);
        root.setMinimumHeight(dp(context, 52));
        int pad = dp(context, 14);
        root.setPadding(pad, dp(context, 8), pad, dp(context, 8));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.label = new TextView(context);
        row.label.setText(label);
        row.label.setTextColor(attr(context, R.attr.ckTextPrimary));
        row.label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.addView(row.label);

        if (sub != null) {
            row.sub = new TextView(context);
            row.sub.setText(sub);
            row.sub.setTextColor(attr(context, R.attr.ckTextSecondary));
            row.sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            text.addView(row.sub);
        }
        root.addView(text);

        Switch toggle = new Switch(context);
        toggle.setChecked(value);
        toggle.setOnCheckedChangeListener((v, checked) -> {
            if (onToggle != null) {
                onToggle.onToggle(checked);
            }
        });
        root.addView(toggle);
        root.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));

        row.toggle = toggle;
        row.root = root;
        parent.addView(root);
        return row;
    }

    public static Row addSlider(LinearLayout parent, String label, int max,
                                int value, final OnSlide onSlide) {

        Context context = parent.getContext();
        Row row = new Row();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 14);
        root.setPadding(pad, dp(context, 10), pad, dp(context, 6));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.label = new TextView(context);
        row.label.setText(label);
        row.label.setTextColor(attr(context, R.attr.ckTextPrimary));
        row.label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.label.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(row.label);

        row.value = new TextView(context);
        row.value.setTextColor(attr(context, R.attr.ckTextSecondary));
        row.value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        row.value.setText(String.valueOf(value));
        head.addView(row.value);

        root.addView(head);

        SeekBar seek = new SeekBar(context);
        seek.setMax(Math.max(1, max));
        seek.setProgress(Math.max(0, Math.min(max, value)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(context, 2);
        seek.setLayoutParams(sp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                row.value.setText(String.valueOf(progress));
                if (onSlide != null) {
                    onSlide.onSlide(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        root.addView(seek);

        row.seek = seek;
        row.root = root;
        parent.addView(root);
        return row;
    }

    public static Row addColor(LinearLayout parent, String label, int color,
                               View.OnClickListener onClick) {

        Context context = parent.getContext();
        Row row = addAction(parent, 0, label, null, onClick);

        // replace the chevron with a live swatch
        LinearLayout root = (LinearLayout) row.root;
        root.removeViewAt(root.getChildCount() - 1);

        FrameLayout holder = new FrameLayout(context);
        holder.setBackgroundResource(R.drawable.bg_checker);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                dp(context, 34), dp(context, 24));
        hp.rightMargin = dp(context, 8);
        holder.setLayoutParams(hp);

        View swatch = new View(context);
        swatch.setBackgroundColor(color);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(color);
        outline.setCornerRadius(dp(context, 8));
        outline.setStroke(1, 0x33000000);
        swatch.setBackground(outline);
        holder.addView(swatch, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(holder, root.getChildCount());
        row.swatch = swatch;
        return row;
    }

    public static EditText input(Context context, String hint, String value) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setText(value);
        field.setSingleLine(true);
        field.setTextColor(attr(context, R.attr.ckTextPrimary));
        field.setHintTextColor(attr(context, R.attr.ckTextSecondary));
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        field.setBackgroundResource(R.drawable.bg_input);
        field.setPadding(dp(context, 14), dp(context, 12),
                dp(context, 14), dp(context, 12));
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        return field;
    }

    /** Status bar / navigation bar padding, applied once. */
    public static void applyInsets(View root) {
        final int startLeft = root.getPaddingLeft();
        final int startTop = root.getPaddingTop();
        final int startRight = root.getPaddingRight();
        final int startBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    startLeft + insets.getSystemWindowInsetLeft(),
                    startTop + insets.getSystemWindowInsetTop(),
                    startRight + insets.getSystemWindowInsetRight(),
                    startBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    public static int mix(int color, float amount) {
        int a = Color.alpha(color);
        return Color.argb(a,
                Math.max(0, Math.min(255, Math.round(Color.red(color) * amount))),
                Math.max(0, Math.min(255, Math.round(Color.green(color) * amount))),
                Math.max(0, Math.min(255, Math.round(Color.blue(color) * amount))));
    }

    public static String hex(int color) {
        return String.format("#%08X", color);
    }
}
