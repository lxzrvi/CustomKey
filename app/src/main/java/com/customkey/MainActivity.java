package com.customkey;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** App hub: permissions → trial → editor → language → backup → share → exit. */
public class MainActivity extends Activity {

    private static final int REQ_EXPORT = 41;
    private static final int REQ_IMPORT = 42;
    private static final int REQ_NOTIFICATIONS = 43;

    private LinearLayout content;
    private TextView themeBadge;

    private Ui.Row enableRow;
    private Ui.Row selectRow;
    private Ui.Row notifyRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.init(this);
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setContentView(R.layout.activity_main);

        Ui.applyInsets(findViewById(R.id.root));

        content = findViewById(R.id.content);
        themeBadge = findViewById(R.id.themeBadge);
        themeBadge.setText(ThemeManager.label(Prefs.get(this).theme));
        themeBadge.setOnClickListener(v -> chooseTheme());

        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    /* ==================================================================
     * Hub
     * ================================================================== */

    private void build() {
        content.removeAllViews();

        content.addView(permissionCard());
        content.addView(trialCard());
        content.addView(menuCard());
        content.addView(themeCard());
        content.addView(footer());
    }

    private LinearLayout permissionCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.hub_permissions)));

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.hub_permissions_sub));
        sub.setTextColor(Ui.attr(this, R.attr.ckTextSecondary));
        sub.setTextSize(12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Ui.dp(this, 14);
        p.rightMargin = Ui.dp(this, 14);
        p.bottomMargin = Ui.dp(this, 6);
        sub.setLayoutParams(p);
        card.addView(sub);

        enableRow = Ui.addAction(card, R.drawable.ic_lock,
                getString(R.string.perm_enable), getString(R.string.status_pending),
                v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        card.addView(Ui.divider(this));

        selectRow = Ui.addAction(card, R.drawable.ic_keyboard,
                getString(R.string.perm_select), getString(R.string.status_pending),
                v -> pickKeyboard());

        if (Build.VERSION.SDK_INT >= 33) {
            card.addView(Ui.divider(this));
            notifyRow = Ui.addAction(card, R.drawable.ic_bolt,
                    getString(R.string.perm_notifications),
                    getString(R.string.status_optional),
                    v -> askNotifications());
        }
        return card;
    }

    private LinearLayout trialCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.trial_title)));

        EditText field = new EditText(this);
        field.setHint(getString(R.string.trial_hint));
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setTextColor(Ui.attr(this, R.attr.ckTextPrimary));
        field.setHintTextColor(Ui.attr(this, R.attr.ckTextSecondary));
        field.setTextSize(15);
        field.setBackgroundResource(R.drawable.bg_input);
        field.setPadding(Ui.dp(this, 16), Ui.dp(this, 15),
                Ui.dp(this, 16), Ui.dp(this, 15));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Ui.dp(this, 12);
        p.rightMargin = Ui.dp(this, 12);
        p.topMargin = Ui.dp(this, 6);
        field.setLayoutParams(p);
        card.addView(field);

        TextView hint = new TextView(this);
        hint.setText("Tap the field, then switch to CustomKey with the 🌐 key.");
        hint.setTextColor(Ui.attr(this, R.attr.ckTextSecondary));
        hint.setTextSize(12);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = Ui.dp(this, 14);
        hp.rightMargin = Ui.dp(this, 14);
        hp.topMargin = Ui.dp(this, 8);
        hp.bottomMargin = Ui.dp(this, 8);
        hint.setLayoutParams(hp);
        card.addView(hint);
        return card;
    }

    private LinearLayout menuCard() {
        LinearLayout card = Ui.card(this);

        Ui.addAction(card, R.drawable.ic_brush, getString(R.string.menu_edit),
                getString(R.string.menu_edit_sub),
                v -> startActivity(new Intent(this, EditorActivity.class)));

        card.addView(Ui.divider(this));

        Ui.Row language = Ui.addAction(card, R.drawable.ic_globe,
                getString(R.string.menu_language), getString(R.string.menu_language_sub),
                v -> toast(getString(R.string.coming_later)));
        language.root.setAlpha(0.45f);

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_backup, getString(R.string.menu_backup),
                getString(R.string.menu_backup_sub), v -> backupDialog());

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_share, getString(R.string.menu_share),
                getString(R.string.menu_share_sub), v -> share());

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_theme, getString(R.string.menu_theme),
                getString(R.string.menu_theme_sub), v -> chooseTheme());

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_exit, getString(R.string.menu_exit),
                getString(R.string.menu_exit_sub), v -> finishAffinity());

        return card;
    }

    private LinearLayout themeCard() {
        final LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.menu_theme)));

        final LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Ui.dp(this, 12);
        p.rightMargin = Ui.dp(this, 12);
        p.topMargin = Ui.dp(this, 6);
        p.bottomMargin = Ui.dp(this, 10);
        chips.setLayoutParams(p);

        final Config cfg = Prefs.get(this);
        String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };

        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13);
            chip.setTextColor(getColorStateList(R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(Ui.dp(this, 6), Ui.dp(this, 10),
                    Ui.dp(this, 6), Ui.dp(this, 10));
            chip.setSelected(cfg.theme == mode);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.rightMargin = i == labels.length - 1 ? 0 : Ui.dp(this, 8);
            chip.setLayoutParams(cp);
            chip.setOnClickListener(v -> {
                if (Prefs.get(this).theme == mode) {
                    return;
                }
                ThemeManager.setMode(MainActivity.this, mode);
            });
            chips.addView(chip);
        }

        card.addView(chips);
        return card;
    }

    private TextView footer() {
        TextView view = new TextView(this);
        view.setText("CustomKey 2.0 · everything is stored on this device");
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Ui.attr(this, R.attr.ckTextSecondary));
        view.setTextSize(11);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(this, 2);
        view.setLayoutParams(p);
        return view;
    }

    /* ==================================================================
     * Actions
     * ================================================================== */

    /**
     * Wrapped end to end: Android 14+ throws SecurityException for some
     * Settings.Secure keys, and a status line must never take the app down.
     */
    private void refreshStatuses() {
        try {
            if (enableRow != null) {
                boolean enabled = isImeEnabled();
                enableRow.sub.setText(enabled
                        ? getString(R.string.status_done)
                        : getString(R.string.status_pending));
                enableRow.root.setAlpha(enabled ? 0.55f : 1f);
            }
            if (selectRow != null) {
                Boolean active = isImeActive();
                selectRow.sub.setText(active == null
                        ? getString(R.string.status_unknown)
                        : active
                        ? getString(R.string.status_done)
                        : getString(R.string.status_pending));
                selectRow.root.setAlpha(
                        Boolean.TRUE.equals(active) ? 0.55f : 1f);
            }
            if (notifyRow != null && Build.VERSION.SDK_INT >= 33) {
                boolean on = areNotificationsEnabled();
                notifyRow.sub.setText(on
                        ? getString(R.string.status_done)
                        : getString(R.string.status_optional));
            }
            if (themeBadge != null) {
                themeBadge.setText(ThemeManager.label(Prefs.get(this).theme));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Settings.Secure.ENABLED_INPUT_METHODS is unreadable for apps targeting
     * API 34+, so use the sanctioned InputMethodManager API instead.
     */
    private boolean isImeEnabled() {
        try {
            InputMethodManager manager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager == null) {
                return false;
            }
            for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
                if (getPackageName().equals(info.getPackageName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** @return TRUE / FALSE, or null when the OS refuses to tell us. */
    private Boolean isImeActive() {
        try {
            String current = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (current == null) {
                return null;
            }
            ComponentName name = ComponentName.unflattenFromString(current);
            if (name == null) {
                return null;
            }
            return getPackageName().equals(name.getPackageName())
                    && CustomKeyService.class.getName().equals(name.getClassName());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean areNotificationsEnabled() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    private void pickKeyboard() {
        InputMethodManager manager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showInputMethodPicker();
        }
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_NOTIFICATIONS);
    }

    private void chooseTheme() {
        final Config cfg = Prefs.get(this);
        String[] labels = {"System default", "Light", "Dark"};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_theme))
                .setSingleChoiceItems(labels, cfg.theme, (dialog, which) -> {
                    dialog.dismiss();
                    if (which != cfg.theme) {
                        ThemeManager.setMode(MainActivity.this, which);
                    }
                })
                .setNegativeButton(getString(R.string.act_cancel), null)
                .show();
    }

    private void backupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_backup))
                .setItems(new String[]{"Export to file", "Import from file"},
                        (dialog, which) -> {
                            if (which == 0) {
                                startActivityForResult(Backup.exportIntent(), REQ_EXPORT);
                            } else {
                                startActivityForResult(Backup.importIntent(), REQ_IMPORT);
                            }
                        })
                .setNegativeButton(getString(R.string.act_cancel), null)
                .show();
    }

    private void share() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        intent.putExtra(Intent.EXTRA_TEXT,
                "CustomKey — build your keyboard, your way.\n"
                        + "https://github.com/lxzrvi/CustomKey");
        startActivity(Intent.createChooser(intent, getString(R.string.menu_share)));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();

        if (requestCode == REQ_EXPORT) {
            boolean ok = Backup.write(this, uri, Prefs.get(this).toJson());
            toast(ok ? "Backup exported" : "Export failed");
        } else if (requestCode == REQ_IMPORT) {
            String json = Backup.read(this, uri);
            Config incoming = Config.fromJson(json);
            if (json == null || json.trim().length() == 0) {
                toast("Import failed");
                return;
            }
            Prefs.replace(this, incoming);
            Img.clear();
            FontManager.reset();
            ThemeManager.apply(this);
            recreate();
        }
    }
}
