package com.customkey;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders and handles the keyboard. The very same view drives the live IME
 * ({@link #MODE_LIVE}) and the editor preview, where keys stay functional —
 * page switches, shift, popups — but never commit text.
 */
public class KeyboardView extends View {

    public static final int MODE_LIVE = 0;
    public static final int MODE_PREVIEW = 1;
    public static final int MODE_SELECT = 2;
    public static final int MODE_DRAG = 3;

    public interface Listener {
        void onKeyPress(KeyModel key);

        void onKeyLongPress(KeyModel key);

        void onLayoutChanged();

        void onSelectionChanged();

        void onImeSwitchRequested();
    }

    public static class Adapter implements Listener {
        @Override
        public void onKeyPress(KeyModel key) {
        }

        @Override
        public void onKeyLongPress(KeyModel key) {
        }

        @Override
        public void onLayoutChanged() {
        }

        @Override
        public void onSelectionChanged() {
        }

        @Override
        public void onImeSwitchRequested() {
        }
    }

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint();
    private final Matrix matrix = new Matrix();
    private final RectF tmp = new RectF();
    private final RectF candBar = new RectF();
    private final KeyStyle.Resolved resolved = new KeyStyle.Resolved();

    private List<List<KeyModel>> rows = new ArrayList<>();
    private final Set<String> selectedIds = new LinkedHashSet<>();

    private Config cfg;
    private Typeface typeface = Typeface.DEFAULT;

    private Listener listener;
    private int mode = MODE_LIVE;
    private int page = KeyboardLayout.PAGE_ABC;

    private boolean shifted;
    private KeyModel pressed;
    private boolean longPressFired;
    private boolean repeating;

    private KeyModel dragKey;
    private float dragX, dragY;
    private int dragFromRow = -1, dragFromIdx = -1;
    private int dropRow = -1, dropIdx = -1;

    private List<String> candidates;
    private int candIndex = -1;

    private final float density;
    private final float scaledDensity;
    private final int touchSlop;

    private final Runnable longPressTask = new Runnable() {
        @Override
        public void run() {
            onLongPress();
        }
    };

    private final Runnable repeatTask = new Runnable() {
        @Override
        public void run() {
            if (!repeating || pressed == null
                    || pressed.type != KeyModel.BACKSPACE) {
                return;
            }
            if (listener != null) {
                listener.onKeyPress(pressed);
            }
            postDelayed(this, 50);
        }
    };

    public KeyboardView(Context context) {
        this(context, null);
    }

    public KeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);

        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        textPaint.setTextAlign(Paint.Align.CENTER);
        borderPaint.setStyle(Paint.Style.STROKE);
        scrimPaint.setColor(Color.BLACK);

        Prefs.init(context);
        reload();
    }

    /* ==================================================================
     * Public API
     * ================================================================== */

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setMode(int mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        cancelAll();
        applySelectionFlags();
        invalidate();
    }

    public int getMode() {
        return mode;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        if (this.page == page) {
            return;
        }
        this.page = page;
        cancelAll();
        rebuild();
        if (listener != null) {
            listener.onLayoutChanged();
        }
    }

    public List<List<KeyModel>> rows() {
        return rows;
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

    public Set<String> selectedIds() {
        return selectedIds;
    }

    public void selectKey(String id, boolean on) {
        if (on) {
            selectedIds.add(id);
        } else {
            selectedIds.remove(id);
        }
        applySelectionFlags();
        invalidate();
        if (listener != null) {
            listener.onSelectionChanged();
        }
    }

    public void clearSelection() {
        selectedIds.clear();
        applySelectionFlags();
        invalidate();
        if (listener != null) {
            listener.onSelectionChanged();
        }
    }

    /** Re-reads config, rebuilds the grid, keeps the current page. */
    public void reload() {
        cfg = Prefs.get(getContext());
        typeface = FontManager.get(getContext());
        rebuild();
    }

    /** Redraws with the current config (used after a live style change). */
    public void refresh() {
        cfg = Prefs.get(getContext());
        typeface = FontManager.get(getContext());
        requestLayout();
        invalidate();
    }

    private void rebuild() {
        rows = KeyboardLayout.build(page);
        applySelectionFlags();
        requestLayout();
        invalidate();
    }

    private void applySelectionFlags() {
        for (List<KeyModel> row : rows) {
            for (KeyModel key : row) {
                key.selected = selectedIds.contains(key.id);
            }
        }
    }

    /* ==================================================================
     * Measuring
     * ================================================================== */

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }

        float gap = dp(cfg.spacing);
        float h = dp(cfg.keyHeight);
        int height = Math.round(rows.size() * h + (rows.size() + 1) * gap);

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys();
    }

    private void layoutKeys() {
        float gap = dp(cfg.spacing);
        float h = dp(cfg.keyHeight);
        float width = getWidth() > 0
                ? getWidth()
                : getMeasuredWidth();

        float y = gap;
        for (List<KeyModel> row : rows) {
            float total = 0f;
            for (KeyModel key : row) {
                total += key.width;
            }
            if (total <= 0f) {
                total = 1f;
            }

            float usable = Math.max(0f, width - gap * (row.size() + 1));
            float unit = usable / total;
            float x = gap;

            for (KeyModel key : row) {
                float w = unit * key.width;
                key.bounds.set(x, y, x + w, y + h);
                x += w + gap;
            }
            y += h + gap;
        }
    }

    /* ==================================================================
     * Drawing
     * ================================================================== */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() == 0) {
            layoutKeys();
        }

        drawBackground(canvas);

        for (List<KeyModel> row : rows) {
            for (KeyModel key : row) {
                if (key == dragKey) {
                    continue;
                }
                drawKey(canvas, key, key.bounds.left, key.bounds.top,
                        key.bounds.width(), key.bounds.height());
            }
        }

        drawDropMarker(canvas);
        drawCandidates(canvas);
        drawDragKey(canvas);
    }

    private void drawBackground(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        if (cfg.bgType == Config.BG_IMAGE && cfg.bgImage != null
                && cfg.bgImage.length() > 0) {

            Bitmap bitmap = Img.get(getContext(), cfg.bgImage,
                    (int) w, (int) h, this::invalidate);

            if (bitmap != null) {
                tmp.set(0, 0, w, h);
                drawBitmapInto(canvas, bitmap, tmp, 0f);
                if (cfg.bgImageDim > 0) {
                    scrimPaint.setAlpha(Math.min(220, cfg.bgImageDim));
                    canvas.drawRect(0, 0, w, h, scrimPaint);
                }
                return;
            }
        }
        canvas.drawColor(cfg.bgColor);
    }

    private void drawKey(Canvas canvas, KeyModel key,
                         float x, float y, float w, float h) {

        KeyStyle.resolve(cfg, cfg.styles.get(key.id), key.isSpecial(), resolved);

        tmp.set(x, y, x + w, y + h);
        float rad = dp(resolved.radiusDp);

        int bg = resolved.bg;
        if (key == pressed && dragKey == null) {
            bg = shade(bg, cfg.pressedBoost);
        } else if (key.type == KeyModel.SHIFT && shifted) {
            bg = shade(bg, cfg.pressedBoost + 10);
        }

        boolean drawn = false;
        if (resolved.bgImage != null && resolved.bgImage.length() > 0) {
            Bitmap bitmap = Img.get(getContext(), resolved.bgImage,
                    (int) Math.max(1, w), (int) Math.max(1, h), this::invalidate);
            if (bitmap != null) {
                drawBitmapInto(canvas, bitmap, tmp, rad);
                drawn = true;
            }
        }
        if (!drawn) {
            keyPaint.setShader(null);
            keyPaint.setColor(bg);
            canvas.drawRoundRect(tmp, rad, rad, keyPaint);
        }

        if (resolved.borderWidthDp > 0.05f) {
            float bw = dp(resolved.borderWidthDp);
            borderPaint.setColor(resolved.borderColor);
            borderPaint.setStrokeWidth(bw);
            tmp.inset(bw / 2f, bw / 2f);
            canvas.drawRoundRect(tmp, Math.max(0f, rad - bw / 2f),
                    Math.max(0f, rad - bw / 2f), borderPaint);
            tmp.inset(-bw / 2f, -bw / 2f);
        }

        String display = key.label == null ? "" : key.label;
        if (key.type == KeyModel.TEXT && shifted) {
            display = display.toUpperCase(Locale.ROOT);
        }

        float size = sp(resolved.textSizeSp);
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(size);

        float max = w - dp(6);
        if (max > 0 && textPaint.measureText(display) > max) {
            float shrink = size * max / textPaint.measureText(display);
            textPaint.setTextSize(Math.max(dp(6), Math.min(size, shrink)));
        }
        textPaint.setColor(resolved.textColor);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = tmp.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(display, tmp.centerX(), textY, textPaint);

        if (key.selected) {
            borderPaint.setColor(isLight(bg) ? Color.BLACK : Color.WHITE);
            borderPaint.setStrokeWidth(dp(2));
            tmp.inset(dp(1), dp(1));
            canvas.drawRoundRect(tmp, rad, rad, borderPaint);
            tmp.inset(-dp(1), -dp(1));
        }
    }

    private void drawBitmapInto(Canvas canvas, Bitmap bitmap, RectF dst, float rad) {
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        float scale = Math.max(dst.width() / bw, dst.height() / bh);
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate(
                dst.centerX() - bw * scale / 2f,
                dst.centerY() - bh * scale / 2f);

        BitmapShader shader = new BitmapShader(bitmap,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);

        keyPaint.setShader(shader);
        if (rad > 0f) {
            canvas.drawRoundRect(dst, rad, rad, keyPaint);
        } else {
            canvas.drawRect(dst, keyPaint);
        }
        keyPaint.setShader(null);
    }

    private void drawDragKey(Canvas canvas) {
        if (dragKey == null) {
            return;
        }
        float w = dragKey.bounds.width();
        float h = dragKey.bounds.height();
        canvas.save();
        canvas.translate(dragX - w / 2f, dragY - h / 2f);
        canvas.scale(1.06f, 1.06f, w / 2f, h / 2f);
        drawKey(canvas, dragKey, 0, 0, w, h);
        canvas.restore();
    }

    private void drawDropMarker(Canvas canvas) {
        if (dragKey == null || dropRow < 0 || dropRow >= rows.size()) {
            return;
        }
        List<KeyModel> row = rows.get(dropRow);
        if (row.isEmpty()) {
            return;
        }

        float gap = dp(cfg.spacing);
        float x;
        if (dropIdx <= 0) {
            x = row.get(0).bounds.left - gap / 2f;
        } else if (dropIdx >= row.size()) {
            x = row.get(row.size() - 1).bounds.right + gap / 2f;
        } else {
            x = row.get(dropIdx).bounds.left - gap / 2f;
        }

        tmp.set(x - dp(1.5f), row.get(0).bounds.top,
                x + dp(1.5f), row.get(0).bounds.bottom);
        keyPaint.setShader(null);
        keyPaint.setColor(isLight(cfg.bgColor) ? Color.BLACK : Color.WHITE);
        canvas.drawRoundRect(tmp, dp(1.5f), dp(1.5f), keyPaint);
    }

    private void drawCandidates(Canvas canvas) {
        if (candidates == null || candidates.isEmpty() || pressed == null) {
            return;
        }

        float gap = dp(cfg.spacing);
        float h = dp(46);
        int n = candidates.size();
        float cell = dp(50);
        float w = Math.min(getWidth() - gap * 2, cell * n);
        float x = (getWidth() - w) / 2f;
        float y = gap;

        candBar.set(x, y, x + w, y + h);
        float rad = dp(14);

        keyPaint.setShader(null);
        keyPaint.setColor(shade(cfg.keyColor, 6));
        canvas.drawRoundRect(candBar, rad, rad, keyPaint);

        borderPaint.setColor(cfg.borderColor);
        borderPaint.setStrokeWidth(dp(Math.max(0.6f, cfg.borderWidth)));
        canvas.drawRoundRect(candBar, rad, rad, borderPaint);

        float cw = w / n;
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(sp(cfg.textSize));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float ty = candBar.centerY() - (fm.ascent + fm.descent) / 2f;

        for (int i = 0; i < n; i++) {
            tmp.set(x + i * cw, y, x + (i + 1) * cw, y + h);
            if (i == candIndex) {
                keyPaint.setColor(shade(cfg.specialColor, 26));
                tmp.inset(dp(3), dp(3));
                canvas.drawRoundRect(tmp, dp(10), dp(10), keyPaint);
                tmp.inset(-dp(3), -dp(3));
            }
            textPaint.setColor(i == candIndex ? cfg.specialTextColor : cfg.keyTextColor);
            canvas.drawText(candidates.get(i), tmp.centerX(), ty, textPaint);
        }
    }

    /* ==================================================================
     * Touch
     * ================================================================== */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (rows.isEmpty() || cfg == null) {
            return true;
        }

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN: {
                KeyModel key = keyAt(event.getX(), event.getY());
                if (key == null) {
                    return true;
                }
                pressed = key;
                longPressFired = false;
                invalidate();

                if (mode == MODE_DRAG) {
                    beginDrag(key, event.getX(), event.getY());
                    return true;
                }

                if (mode == MODE_LIVE || mode == MODE_PREVIEW) {
                    pressFeedback(key);
                }

                if (mode == MODE_LIVE && key.type == KeyModel.BACKSPACE) {
                    repeating = true;
                    postDelayed(repeatTask, cfg.longPressDelay + 250L);
                }
                postDelayed(longPressTask, Math.max(120, cfg.longPressDelay));
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (dragKey != null) {
                    dragX = event.getX();
                    dragY = event.getY();
                    updateDropTarget();
                    invalidate();
                    return true;
                }
                if (candidates != null) {
                    updateCandidateIndex(event.getX());
                    invalidate();
                    return true;
                }
                KeyModel key = keyAt(event.getX(), event.getY());
                if (key != pressed) {
                    pressed = key;
                    removeCallbacks(longPressTask);
                    stopRepeat();
                    KeyPopup.hide();
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPressTask);

                if (dragKey != null) {
                    finishDrag(event.getX(), event.getY());
                    return true;
                }
                if (candidates != null) {
                    commitCandidate();
                    return true;
                }

                boolean repeated = repeating;
                stopRepeat();
                KeyPopup.hide();

                KeyModel up = keyAt(event.getX(), event.getY());
                if (!longPressFired && !repeated
                        && pressed != null && up == pressed) {
                    tap(pressed);
                }
                pressed = null;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                return true;

            default:
                return true;
        }
    }

    private void tap(KeyModel key) {
        if (mode == MODE_SELECT) {
            selectKey(key.id, !selectedIds.contains(key.id));
            return;
        }

        if (key.type == KeyModel.PAGE) {
            setPage(key.targetPage);
            return;
        }
        if (key.type == KeyModel.SHIFT) {
            toggleShift();
            return;
        }
        if (key.type == KeyModel.IME_SWITCH) {
            if (listener != null) {
                listener.onImeSwitchRequested();
            }
            return;
        }
        if (listener != null) {
            listener.onKeyPress(key);
        }
        if (key.type == KeyModel.TEXT && shifted) {
            shifted = false;
            invalidate();
        }
    }

    /** Haptics, click sound and the floating preview card — on key *down*. */
    private void pressFeedback(KeyModel key) {
        KeyStyle.resolve(cfg, cfg.styles.get(key.id), key.isSpecial(), resolved);

        if (cfg.hapticOnTap) {
            Haptics.tap(this, resolved.haptic);
        }
        SoundManager.play(getContext(), resolved.sound);

        if (cfg.keyPopup) {
            String display = key.label == null ? "" : key.label;
            if (key.type == KeyModel.TEXT && shifted) {
                display = display.toUpperCase(Locale.ROOT);
            }
            KeyPopup.show(this, key.bounds, display, resolved.bg,
                    resolved.textColor, resolved.radiusDp,
                    resolved.textSizeSp, typeface, density);
        }
    }

    private void onLongPress() {
        if (pressed == null) {
            return;
        }
        longPressFired = true;
        KeyPopup.hide();

        if (mode != MODE_LIVE) {
            Haptics.tap(this, cfg.hapticStrength);
            if (listener != null) {
                listener.onKeyLongPress(pressed);
            }
            return;
        }

        if (pressed.type == KeyModel.BACKSPACE) {
            Haptics.tap(this, cfg.hapticStrength);
            return;
        }
        if (pressed.type == KeyModel.SPACE) {
            if (listener != null) {
                listener.onImeSwitchRequested();
            }
            return;
        }

        List<String> list = cfg.longPress.get(pressed.id);
        if (cfg.showCandidates && list != null && !list.isEmpty()) {
            candidates = new ArrayList<>(list);
            candIndex = candidates.size() / 2;
            Haptics.tap(this, cfg.hapticStrength);
            invalidate();
            return;
        }
        Haptics.tap(this, cfg.hapticStrength);
    }

    private void updateCandidateIndex(float x) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        float cw = candBar.width() / candidates.size();
        if (cw <= 0) {
            return;
        }
        int index = (int) ((x - candBar.left) / cw);
        candIndex = Math.max(0, Math.min(candidates.size() - 1, index));
    }

    private void commitCandidate() {
        String value = null;
        if (candidates != null && candIndex >= 0
                && candIndex < candidates.size()) {
            value = candidates.get(candIndex);
        }
        candidates = null;
        candIndex = -1;

        if (value != null && listener != null && pressed != null) {
            KeyModel virtual = new KeyModel(pressed.id, value, value,
                    KeyModel.TEXT, 1f, -1);
            listener.onKeyPress(virtual);
            if (shifted) {
                shifted = false;
            }
        }
        pressed = null;
        invalidate();
    }

    private void beginDrag(KeyModel key, float x, float y) {
        dragKey = key;
        dragX = x;
        dragY = y;
        dragFromRow = -1;
        dragFromIdx = -1;
        dropRow = -1;
        dropIdx = -1;

        for (int r = 0; r < rows.size(); r++) {
            int i = rows.get(r).indexOf(key);
            if (i >= 0) {
                dragFromRow = r;
                dragFromIdx = i;
                break;
            }
        }
        Haptics.tap(this, cfg.hapticStrength);
        updateDropTarget();
        invalidate();
    }

    private void updateDropTarget() {
        dropRow = -1;
        dropIdx = -1;
        if (dragKey == null) {
            return;
        }
        float best = Float.MAX_VALUE;
        for (int r = 0; r < rows.size(); r++) {
            List<KeyModel> row = rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                KeyModel key = row.get(i);
                if (key == dragKey) {
                    continue;
                }
                float dx = key.bounds.centerX() - dragX;
                float dy = key.bounds.centerY() - dragY;
                float dist = dx * dx + dy * dy;
                if (dist < best) {
                    best = dist;
                    dropRow = r;
                    dropIdx = dragX > key.bounds.centerX() ? i + 1 : i;
                }
            }
        }
    }

    private void finishDrag(float x, float y) {
        boolean moved = dragFromRow >= 0 && dropRow >= 0
                && (Math.abs(x - dragKey.bounds.centerX()) > touchSlop
                || Math.abs(y - dragKey.bounds.centerY()) > touchSlop);

        KeyModel key = dragKey;
        dragKey = null;
        dropRow = -1;
        dropIdx = -1;
        pressed = null;

        if (moved) {
            KeyboardLayout.move(page, dragFromRow, dragFromIdx, dropRow, dropIdx);
            Prefs.save();
            rebuild();
            Haptics.tap(this, cfg.hapticStrength);
            if (listener != null) {
                listener.onLayoutChanged();
            }
        } else if (key != null && listener != null) {
            listener.onKeyLongPress(key);
        }
        invalidate();
    }

    private void stopRepeat() {
        repeating = false;
        removeCallbacks(repeatTask);
    }

    private void cancelAll() {
        removeCallbacks(longPressTask);
        stopRepeat();
        candidates = null;
        candIndex = -1;
        dragKey = null;
        dropRow = -1;
        dropIdx = -1;
        longPressFired = false;
        pressed = null;
        KeyPopup.hide();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAll();
    }

    public KeyModel keyAt(float x, float y) {
        for (List<KeyModel> row : rows) {
            for (KeyModel key : row) {
                if (key.bounds.contains(x, y)) {
                    return key;
                }
            }
        }
        return null;
    }

    /* ==================================================================
     * Small helpers
     * ================================================================== */

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }

    static int shade(int color, int delta) {
        boolean light = isLight(color);
        int d = light ? -delta : delta;
        return Color.argb(
                Color.alpha(color),
                clamp(Color.red(color) + d),
                clamp(Color.green(color) + d),
                clamp(Color.blue(color) + d));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    static boolean isLight(int color) {
        return (Color.red(color) * 299
                + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000 > 140;
    }
}
