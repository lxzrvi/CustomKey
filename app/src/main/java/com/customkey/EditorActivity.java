package com.customkey;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The keyboard editor: a live preview on top, everything else below it.
 *
 * <p>In the preview every key still works — pages, shift, popups — but
 * nothing is typed anywhere. Long press a key for Edit / Move / Cancel.</p>
 */
public class EditorActivity extends Activity {

    private static final int REQ_BG_IMAGE = 51;
    private static final int REQ_KEY_IMAGE = 52;
    private static final int REQ_FONT = 53;
    private static final int REQ_SOUND = 54;

    private KeyboardView preview;
    private LinearLayout sections;
    private LinearLayout pageChips;
    private ScrollView scroll;
    private TextView selectMode;
    private final List<TextView> chips = new ArrayList<>();

    private Config cfg;
    private LinearLayout keyPanel;
    private KeyShapeView shape;
    private TextView panelTitle;

    private List<String> targets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Prefs.init(this);
        ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        cfg = Prefs.get(this);
        Ui.applyInsets(findViewById(R.id.root));
        findViewById(R.id.previewFrame).setClipToOutline(true);

        preview = findViewById(R.id.preview);
        preview.setMode(KeyboardView.MODE_PREVIEW);
        preview.setListener(new KeyboardView.Adapter() {
            @Override
            public void onKeyLongPress(KeyModel key) {
                keyMenu(key);
            }

            @Override
            public void onLayoutChanged() {
                syncChips();
            }

            @Override
            public void onSelectionChanged() {
                rebuild();
            }
        });

        sections = findViewById(R.id.sections);
        pageChips = findViewById(R.id.pageChips);
        scroll = findViewById(R.id.scroll);

        selectMode = findViewById(R.id.selectMode);
        selectMode.setOnClickListener(v -> toggleSelectMode());
        findViewById(R.id.resetAll).setOnClickListener(v -> confirmReset());
        findViewById(R.id.back).setOnClickListener(v -> finish());

        buildChips();
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        preview.reload();
        syncChips();
    }

    /* ==================================================================
     * Page chips
     * ================================================================== */

    private void buildChips() {
        pageChips.removeAllViews();
        chips.clear();

        int[] pages = {
                KeyboardLayout.PAGE_ABC, KeyboardLayout.PAGE_NUM,
                KeyboardLayout.PAGE_SYM, KeyboardLayout.PAGE_EMOJI
        };

        for (final int page : pages) {
            TextView chip = new TextView(this);
            chip.setText(KeyboardLayout.pageName(page));
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(12);
            chip.setTextColor(getColorStateList(R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(Ui.dp(this, 16), Ui.dp(this, 8),
                    Ui.dp(this, 16), Ui.dp(this, 8));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Ui.dp(this, 8);
            chip.setLayoutParams(params);
            chip.setTag(page);
            chip.setOnClickListener(v -> {
                preview.setPage(page);
                syncChips();
            });

            pageChips.addView(chip);
            chips.add(chip);
        }
        syncChips();
    }

    private void syncChips() {
        int page = preview.getPage();
        for (TextView chip : chips) {
            chip.setSelected(((Integer) chip.getTag()) == page);
        }
        int mode = preview.getMode();
        boolean busy = mode == KeyboardView.MODE_SELECT
                || mode == KeyboardView.MODE_DRAG;
        selectMode.setText(busy ? "Done" : getString(R.string.ed_select));
        selectMode.setSelected(busy);
    }

    private void toggleSelectMode() {
        int mode = preview.getMode();
        if (mode == KeyboardView.MODE_SELECT || mode == KeyboardView.MODE_DRAG) {
            preview.setMode(KeyboardView.MODE_PREVIEW);
        } else {
            closeKeyPanel();
            preview.setMode(KeyboardView.MODE_SELECT);
            toast("Tap keys to select them, then apply one style to all");
        }
        syncChips();
        rebuild();
    }

    /* ==================================================================
     * Long press menu: Edit / Move / Cancel
     * ================================================================== */

    private void keyMenu(final KeyModel key) {
        String name = key.label == null ? key.id : key.label;
        new AlertDialog.Builder(this)
                .setTitle("“" + name + "” key")
                .setItems(new String[]{
                                getString(R.string.act_edit),
                                getString(R.string.act_move),
                                getString(R.string.act_cancel)
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                openKeyPanel(Collections.singletonList(key.id));
                            } else if (which == 1) {
                                preview.setMode(KeyboardView.MODE_DRAG);
                                syncChips();
                                rebuild();
                                toast("Hold a key and drag it anywhere");
                            }
                        })
                .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset keyboard?")
                .setMessage("Key positions, per-key styles, renames and "
                        + "long-press shortcuts go back to default. Colours, "
                        + "sizes, fonts and sounds are kept.")
                .setNegativeButton(getString(R.string.act_cancel), null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    KeyboardLayout.resetAll();
                    Prefs.save(this);
                    closeKeyPanel();
                    preview.setPage(KeyboardLayout.PAGE_ABC);
                    preview.reload();
                    rebuild();
                    syncChips();
                })
                .show();
    }

    /* ==================================================================
     * Sections
     * ================================================================== */

    private void rebuild() {
        sections.removeAllViews();

        if (keyPanel != null && !targets.isEmpty()) {
            sections.addView(keyPanel);
        }

        int selected = preview.selectedIds().size();
        if (selected > 0) {
            sections.addView(selectionCard(selected));
        }

        sections.addView(backgroundCard());
        sections.addView(rowsCard());
        sections.addView(keysCard());
        sections.addView(fontCard());
        sections.addView(pressCard());
        sections.addView(soundCard());
    }

    private LinearLayout selectionCard(int count) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_selected)));

        Ui.addAction(card, R.drawable.ic_layers,
                count + (count == 1 ? " key selected" : " keys selected"),
                "Apply one style, sound and vibration to all of them",
                v -> openKeyPanel(new ArrayList<>(preview.selectedIds())));

        card.addView(Ui.divider(this));

        Ui.addAction(card, 0, "Clear selection", null, v -> {
            preview.clearSelection();
            rebuild();
        });
        return card;
    }

    /* ---------------- background ---------------- */

    private LinearLayout backgroundCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_background)));

        Ui.addAction(card, 0, "Background type",
                cfg.bgType == Config.BG_IMAGE ? "Image" : "Colour",
                v -> new AlertDialog.Builder(this)
                        .setTitle("Background type")
                        .setItems(new String[]{"Colour", "Image"}, (d, which) -> {
                            cfg.bgType = which == 1 ? Config.BG_IMAGE : Config.BG_COLOR;
                            save();
                            rebuild();
                        })
                        .show());

        card.addView(Ui.divider(this));

        final Ui.Row bgRow = Ui.addColor(card, "Background colour", cfg.bgColor, null);
        bgRow.root.setOnClickListener(v ->
                ColorPickerDialog.show(this, "Background colour", cfg.bgColor, color -> {
                    cfg.bgColor = color;
                    cfg.bgType = Config.BG_COLOR;
                    save();
                    bgRow.swatch(color);
                    rebuild();
                }));

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Transparency (alpha)", 255,
                Color.alpha(cfg.bgColor), value -> {
                    cfg.bgColor = Color.argb(value,
                            Color.red(cfg.bgColor),
                            Color.green(cfg.bgColor),
                            Color.blue(cfg.bgColor));
                    save();
                    bgRow.swatch(cfg.bgColor);
                });

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_image, "Background image",
                isEmpty(cfg.bgImage) ? "None" : "Selected",
                v -> pick(REQ_BG_IMAGE));

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Image dim", 200, cfg.bgImageDim, value -> {
            cfg.bgImageDim = value;
            save();
        });

        if (!isEmpty(cfg.bgImage)) {
            card.addView(Ui.divider(this));
            Ui.addAction(card, 0, "Remove image", null, v -> {
                cfg.bgImage = "";
                cfg.bgType = Config.BG_COLOR;
                Img.clear();
                save();
                rebuild();
            });
        }
        return card;
    }

    /* ---------------- rows & layout ---------------- */

    private LinearLayout rowsCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_rows)));

        Ui.addSwitch(card, "Number row", "1 – 0 above the letters",
                cfg.numberRow, value -> toggleRow(KeyboardLayout.ROLE_NUMBER, value));

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Top tool row", "Copy, cut, paste, cursor, switch",
                cfg.toolsRow, value -> toggleRow(KeyboardLayout.ROLE_TOOLS, value));

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Emoji key", "Sits in the bottom row",
                cfg.emojiKey, value -> {
                    cfg.emojiKey = value;
                    for (int page = 0; page <= KeyboardLayout.PAGE_EMOJI; page++) {
                        KeyboardLayout.setEmojiKey(page, value);
                    }
                    save();
                    preview.reload();
                });

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Bottom bar", "The row with space and enter",
                cfg.bottomBar, value -> toggleRow(KeyboardLayout.ROLE_BOTTOM, value));

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Key height (dp)", 70, cfg.keyHeight - 30, value -> {
            cfg.keyHeight = value + 30;
            save();
            preview.reload();
        });

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Key spacing (dp)", 20, cfg.spacing, value -> {
            cfg.spacing = value;
            save();
            preview.reload();
        });
        return card;
    }

    private void toggleRow(int role, boolean on) {
        for (int page = 0; page <= KeyboardLayout.PAGE_EMOJI; page++) {
            if (role == KeyboardLayout.ROLE_NUMBER
                    && page != KeyboardLayout.PAGE_ABC) {
                continue;
            }
            KeyboardLayout.setRowEnabled(page, role, on);
        }
        save();
        preview.reload();
    }

    /* ---------------- key defaults ---------------- */

    private LinearLayout keysCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_keys)));

        addGlobalColor(card, "Key colour", () -> cfg.keyColor, c -> cfg.keyColor = c);
        addGlobalColor(card, "Key text colour", () -> cfg.keyTextColor,
                c -> cfg.keyTextColor = c);
        addGlobalColor(card, "Special key colour", () -> cfg.specialColor,
                c -> cfg.specialColor = c);
        addGlobalColor(card, "Special text colour", () -> cfg.specialTextColor,
                c -> cfg.specialTextColor = c);
        addGlobalColor(card, "Border colour", () -> cfg.borderColor,
                c -> cfg.borderColor = c);

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Corner radius (dp)", 40, cfg.radius, value -> {
            cfg.radius = value;
            save();
        });

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Border width", 60, Math.round(cfg.borderWidth * 10),
                value -> {
                    cfg.borderWidth = value / 10f;
                    save();
                });

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Text size (sp)", 30, Math.round(cfg.textSize), value -> {
            cfg.textSize = value;
            save();
        });

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Press highlight", 80, cfg.pressedBoost, value -> {
            cfg.pressedBoost = value;
            save();
        });
        return card;
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }

    private void addGlobalColor(LinearLayout card, String label,
                                final IntGetter getter, final IntSetter setter) {
        final Ui.Row row = Ui.addColor(card, label, getter.get(), null);
        row.root.setOnClickListener(v ->
                ColorPickerDialog.show(this, label, getter.get(), color -> {
                    setter.set(color);
                    save();
                    row.swatch(color);
                }));
    }

    /* ---------------- font ---------------- */

    private LinearLayout fontCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_font)));

        Ui.addAction(card, R.drawable.ic_type, "Font",
                cfg.fontMode == Config.FONT_CUSTOM ? "Custom file"
                        : FontManager.label(cfg.fontFamily),
                v -> chooseFont());

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Use a custom .ttf / .otf", null,
                cfg.fontMode == Config.FONT_CUSTOM, value -> {
                    if (value) {
                        pick(REQ_FONT);
                    } else {
                        cfg.fontMode = Config.FONT_SYSTEM;
                        FontManager.reset();
                        save();
                        rebuild();
                    }
                });
        return card;
    }

    private void chooseFont() {
        final String[] families = FontManager.FAMILIES;
        String[] labels = new String[families.length + 1];
        for (int i = 0; i < families.length; i++) {
            labels[i] = FontManager.label(families[i]);
        }
        labels[families.length] = "Custom .ttf / .otf file…";

        new AlertDialog.Builder(this)
                .setTitle("Font")
                .setItems(labels, (dialog, which) -> {
                    if (which < families.length) {
                        cfg.fontMode = Config.FONT_SYSTEM;
                        cfg.fontFamily = families[which];
                        FontManager.reset();
                        save();
                        rebuild();
                    } else {
                        pick(REQ_FONT);
                    }
                })
                .show();
    }

    /* ---------------- long press & popup ---------------- */

    private LinearLayout pressCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_press)));

        Ui.addSlider(card, "Long press delay (ms)", 900, cfg.longPressDelay,
                value -> {
                    cfg.longPressDelay = Math.max(120, value);
                    Prefs.save(this);
                });

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Key preview card", "Floats above the key on tap",
                cfg.keyPopup, value -> {
                    cfg.keyPopup = value;
                    Prefs.save(this);
                });

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Long press shortcuts", "Hold, slide, release",
                cfg.showCandidates, value -> {
                    cfg.showCandidates = value;
                    Prefs.save(this);
                });
        return card;
    }

    /* ---------------- sound & vibration ---------------- */

    private LinearLayout soundCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.cardTitle(this, getString(R.string.ed_sound)));

        Ui.addSwitch(card, "Vibration", null, cfg.haptics, value -> {
            cfg.haptics = value;
            Prefs.save(this);
            if (value) {
                Haptics.test(this, cfg.hapticStrength);
            }
        });

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Vibration strength", 100, cfg.hapticStrength, value -> {
            cfg.hapticStrength = value;
            Prefs.save(this);
        });

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Vibrate on tap", null, cfg.hapticOnTap, value -> {
            cfg.hapticOnTap = value;
            Prefs.save(this);
        });

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_vibrate, "Test vibration", null,
                v -> Haptics.test(this, cfg.hapticStrength));

        card.addView(Ui.divider(this));

        Ui.addSwitch(card, "Key sound", null, cfg.sound, value -> {
            cfg.sound = value;
            Prefs.save(this);
            if (value) {
                SoundManager.play(this, cfg.soundVolume);
            }
        });

        card.addView(Ui.divider(this));

        Ui.addSlider(card, "Sound volume", 100, cfg.soundVolume, value -> {
            cfg.soundVolume = value;
            Prefs.save(this);
        });

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_wave, "Custom click sound",
                isEmpty(cfg.soundUri) ? "System tick" : "Selected",
                v -> pick(REQ_SOUND));

        if (!isEmpty(cfg.soundUri)) {
            card.addView(Ui.divider(this));
            Ui.addAction(card, 0, "Remove custom sound", null, v -> {
                cfg.soundUri = "";
                SoundManager.reset();
                Prefs.save(this);
                rebuild();
            });
        }
        return card;
    }

    /* ==================================================================
     * Per key panel
     * ================================================================== */

    private void openKeyPanel(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        targets = new ArrayList<>(ids);
        preview.setMode(KeyboardView.MODE_PREVIEW);
        refreshPanel();
        syncChips();
        scroll.scrollTo(0, 0);
    }

    private void closeKeyPanel() {
        targets = new ArrayList<>();
        keyPanel = null;
    }

    private void refreshPanel() {
        keyPanel = buildKeyPanel();
        rebuild();
    }

    private LinearLayout buildKeyPanel() {
        LinearLayout card = Ui.card(this);

        panelTitle = new TextView(this);
        panelTitle.setTextColor(Ui.attr(this, R.attr.ckTextPrimary));
        panelTitle.setTextSize(16);
        panelTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = Ui.dp(this, 14);
        titleParams.rightMargin = Ui.dp(this, 14);
        titleParams.topMargin = Ui.dp(this, 8);
        card.addView(panelTitle, titleParams);
        updatePanelTitle();

        shape = new KeyShapeView(this);
        LinearLayout.LayoutParams shapeParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 132), Ui.dp(this, 62));
        shapeParams.gravity = Gravity.CENTER_HORIZONTAL;
        shapeParams.topMargin = Ui.dp(this, 12);
        shapeParams.bottomMargin = Ui.dp(this, 10);
        card.addView(shape, shapeParams);
        refreshShape();

        if (targets.size() == 1) {
            final String id = targets.get(0);
            EditText labelField = Ui.input(this, "Key label", labelOf(id));
            labelField.setInputType(InputType.TYPE_CLASS_TEXT);
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            fieldParams.leftMargin = Ui.dp(this, 12);
            fieldParams.rightMargin = Ui.dp(this, 12);
            fieldParams.bottomMargin = Ui.dp(this, 6);
            labelField.setLayoutParams(fieldParams);
            labelField.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    setLabel(id, s.toString());
                }
            });
            card.addView(labelField);
        }

        addKeyColor(card, "Key background", 0);
        addKeyColor(card, "Text colour", 1);
        addKeyColor(card, "Border colour", 2);

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_image, "Key image",
                imageOf() == null ? "None" : "Selected",
                v -> pick(REQ_KEY_IMAGE));

        if (imageOf() != null) {
            card.addView(Ui.divider(this));
            Ui.addAction(card, 0, "Remove key image", null, v -> {
                apply(style -> style.bgImage = "");
                Img.clear();
                refreshPanel();
            });
        }

        card.addView(Ui.divider(this));

        addKeySlider(card, "Text size (sp)", 30, Math.round(resolved().textSizeSp),
                value -> apply(style -> style.textSize = (float) value));

        addKeySlider(card, "Corner radius (dp)", 40, Math.round(resolved().radiusDp),
                value -> apply(style -> style.radius = value));

        addKeySlider(card, "Border width", 60,
                Math.round(resolved().borderWidthDp * 10),
                value -> apply(style -> style.borderWidth = value / 10f));

        addKeySlider(card, "Vibration for these keys", 100,
                Math.max(0, resolved().haptic),
                value -> apply(style -> style.haptic = value));

        addKeySlider(card, "Sound for these keys", 100,
                Math.max(0, resolved().sound),
                value -> apply(style -> style.sound = value));

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_bolt, "Long press shortcut",
                longPressSummary(), v -> editLongPress());

        card.addView(Ui.divider(this));

        Ui.addAction(card, 0,
                "Reset " + (targets.size() == 1 ? "this key" : "these keys"),
                null, v -> {
                    for (String id : targets) {
                        cfg.styles.remove(id);
                        cfg.longPress.remove(id);
                        cfg.labels.remove(id);
                    }
                    Prefs.save(this);
                    preview.reload();
                    refreshPanel();
                });

        card.addView(Ui.divider(this));

        Ui.addAction(card, R.drawable.ic_check, "Done", null, v -> {
            closeKeyPanel();
            rebuild();
        });

        return card;
    }

    private void updatePanelTitle() {
        if (panelTitle == null) {
            return;
        }
        panelTitle.setText(targets.size() == 1
                ? "Editing “" + labelOf(targets.get(0)) + "”"
                : "Editing " + targets.size() + " keys");
    }

    private void refreshShape() {
        if (shape == null || targets.isEmpty()) {
            return;
        }
        KeyStyle.Resolved r = resolved();
        shape.set(labelOf(targets.get(0)), r.bg, r.textColor, r.borderColor,
                r.borderWidthDp, r.radiusDp, r.textSizeSp, FontManager.get(this));
    }

    private void addKeyColor(LinearLayout card, String label, final int field) {
        final Ui.Row row = Ui.addColor(card, label, colorOf(field), null);
        row.root.setOnClickListener(v -> ColorPickerDialog.show(this, label,
                colorOf(field), color -> {
                    apply(style -> {
                        if (field == 0) {
                            style.bg = color;
                        } else if (field == 1) {
                            style.textColor = color;
                        } else {
                            style.borderColor = color;
                        }
                    });
                    row.swatch(color);
                }));
    }

    private void addKeySlider(LinearLayout card, String label, int max, int value,
                              final Ui.OnSlide onSlide) {
        Ui.addSlider(card, label, max, value, progress -> {
            onSlide.onSlide(progress);
            refreshShape();
        });
        card.addView(Ui.divider(this));
    }

    /** Applies a change to every targeted key, then persists and redraws. */
    private void apply(Consumer<KeyStyle> change) {
        for (String id : targets) {
            KeyStyle style = cfg.styles.get(id);
            if (style == null) {
                style = new KeyStyle();
                cfg.styles.put(id, style);
            }
            change.accept(style);
            if (style.isEmpty()) {
                cfg.styles.remove(id);
            }
        }
        Prefs.save(this);
        preview.refresh();
        refreshShape();
        updatePanelTitle();
    }

    private void editLongPress() {
        if (targets.size() != 1) {
            toast("Long press shortcuts are set one key at a time");
            return;
        }
        final String id = targets.get(0);
        List<String> current = cfg.longPress.get(id);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        box.setPadding(pad, Ui.dp(this, 10), pad, 0);

        final EditText field = Ui.input(this, "é, è, ê, ë",
                current == null ? "" : join(current));
        box.addView(field);

        TextView hint = new TextView(this);
        hint.setText("Comma separated. Shown as a card when the key is held.");
        hint.setTextColor(Ui.attr(this, R.attr.ckTextSecondary));
        hint.setTextSize(12);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = Ui.dp(this, 8);
        hint.setLayoutParams(hintParams);
        box.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle("Long press shortcut")
                .setView(box)
                .setNegativeButton("Clear", (dialog, which) -> {
                    cfg.longPress.remove(id);
                    Prefs.save(this);
                    refreshPanel();
                })
                .setNeutralButton(getString(R.string.act_cancel), null)
                .setPositiveButton("Save", (dialog, which) -> {
                    List<String> values = split(field.getText().toString());
                    if (values.isEmpty()) {
                        cfg.longPress.remove(id);
                    } else {
                        cfg.longPress.put(id, values);
                    }
                    Prefs.save(this);
                    refreshPanel();
                })
                .show();
    }

    private String longPressSummary() {
        if (targets.size() != 1) {
            return "One key at a time";
        }
        List<String> list = cfg.longPress.get(targets.get(0));
        return list == null || list.isEmpty() ? "None" : join(list);
    }

    /* ---------------- readers ---------------- */

    private KeyStyle.Resolved resolved() {
        String id = targets.isEmpty() ? "" : targets.get(0);
        KeyStyle.Resolved out = new KeyStyle.Resolved();
        KeyStyle.resolve(cfg, cfg.styles.get(id), isSpecial(id), out);
        return out;
    }

    private int colorOf(int field) {
        KeyStyle.Resolved r = resolved();
        if (field == 1) {
            return r.textColor;
        }
        if (field == 2) {
            return r.borderColor;
        }
        return r.bg;
    }

    private String imageOf() {
        KeyStyle style = targets.isEmpty() ? null : cfg.styles.get(targets.get(0));
        return style == null || isEmpty(style.bgImage) ? null : style.bgImage;
    }

    private String labelOf(String id) {
        String custom = cfg.labels.get(id);
        if (custom != null) {
            return custom;
        }
        for (List<KeyModel> row : preview.rows()) {
            for (KeyModel key : row) {
                if (key.id.equals(id)) {
                    return key.label;
                }
            }
        }
        for (int page = 0; page <= KeyboardLayout.PAGE_EMOJI; page++) {
            for (List<KeyModel> row : KeyboardLayout.build(page)) {
                for (KeyModel key : row) {
                    if (key.id.equals(id)) {
                        return key.label;
                    }
                }
            }
        }
        return id;
    }

    private boolean isSpecial(String id) {
        for (List<KeyModel> row : preview.rows()) {
            for (KeyModel key : row) {
                if (key.id.equals(id)) {
                    return key.isSpecial();
                }
            }
        }
        return !id.startsWith("k_") && !id.startsWith("e_");
    }

    private void setLabel(String id, String label) {
        if (label == null) {
            return;
        }
        if (label.length() == 0) {
            cfg.labels.remove(id);
        } else {
            cfg.labels.put(id, label);
        }
        Prefs.save(this);
        preview.reload();
        refreshShape();
        updatePanelTitle();
    }

    /* ---------------- pickers ---------------- */

    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (request == REQ_FONT) {
            intent.setType("*/*");
        } else if (request == REQ_SOUND) {
            intent.setType("audio/*");
        } else {
            intent.setType("image/*");
        }
        try {
            startActivityForResult(intent, request);
        } catch (Throwable t) {
            toast("No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        Backup.persist(this, uri);
        final String value = uri.toString();

        switch (requestCode) {
            case REQ_BG_IMAGE:
                cfg.bgImage = value;
                cfg.bgType = Config.BG_IMAGE;
                Img.clear();
                save();
                rebuild();
                break;

            case REQ_KEY_IMAGE:
                apply(style -> style.bgImage = value);
                refreshPanel();
                break;

            case REQ_FONT:
                cfg.fontUri = value;
                cfg.fontMode = Config.FONT_CUSTOM;
                FontManager.reset();
                save();
                rebuild();
                break;

            case REQ_SOUND:
                cfg.soundUri = value;
                cfg.sound = true;
                SoundManager.reset();
                Prefs.save(this);
                SoundManager.play(this, cfg.soundVolume);
                rebuild();
                break;

            default:
                break;
        }
    }

    /* ---------------- helpers ---------------- */

    private void save() {
        Prefs.save(this);
        preview.refresh();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static boolean isEmpty(String text) {
        return text == null || text.length() == 0;
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() > 0) {
                out.add(trimmed);
            }
        }
        return out;
    }

    @Override
    public void onBackPressed() {
        if (!targets.isEmpty()) {
            closeKeyPanel();
            rebuild();
            return;
        }
        if (preview.getMode() != KeyboardView.MODE_PREVIEW) {
            preview.setMode(KeyboardView.MODE_PREVIEW);
            syncChips();
            return;
        }
        super.onBackPressed();
    }
}
