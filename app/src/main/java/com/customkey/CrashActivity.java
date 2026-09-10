package com.customkey;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Shown in a separate process after a crash, with the full stack trace.
 * Deliberately built from framework widgets and a framework theme so it
 * cannot fail for the same reason the app did.
 */
public class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String trace = readTrace();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int pad = dp(18);
        root.setPadding(pad, dp(24), pad, pad);

        TextView title = new TextView(this);
        title.setText("CustomKey crashed");
        title.setTextColor(Color.BLACK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Screenshot ya Copy karke bhej do — isi se fix ho jayega.");
        sub.setTextColor(0xFF666666);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(14);
        sub.setLayoutParams(subParams);
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF3F3F5);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView body = new TextView(this);
        body.setText(trace);
        body.setTextColor(Color.BLACK);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        body.setTextIsSelectable(true);
        body.setMovementMethod(new ScrollingMovementMethod());
        int bodyPad = dp(12);
        body.setPadding(bodyPad, bodyPad, bodyPad, bodyPad);
        scroll.addView(body);
        root.addView(scroll);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(14);
        buttons.setLayoutParams(buttonParams);

        Button close = new Button(this);
        close.setText("Close");
        close.setTextColor(Color.BLACK);
        close.setBackgroundColor(0xFFE8E8EC);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.rightMargin = dp(10);
        close.setLayoutParams(closeParams);
        close.setOnClickListener(v -> finishAffinity());
        buttons.addView(close);

        Button copy = new Button(this);
        copy.setText("Copy details");
        copy.setTextColor(Color.WHITE);
        copy.setBackgroundColor(Color.BLACK);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(
                        ClipData.newPlainText("CustomKey crash", trace));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            }
        });
        buttons.addView(copy);

        root.addView(buttons);
        setContentView(root);
    }

    private String readTrace() {
        File file = new File(getFilesDir(), App.CRASH_FILE);
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (Throwable t) {
            builder.append("No crash details could be read.\n").append(t);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return builder.toString();
    }

    private int dp(float value) {
        return Math.round(value
                * getResources().getDisplayMetrics().density);
    }
}
