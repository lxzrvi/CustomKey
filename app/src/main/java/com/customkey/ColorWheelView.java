package com.customkey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Hue/saturation wheel + value bar + alpha bar. Pure framework drawing, no
 * dependencies, works the same inside the app and inside a dialog.
 */
public class ColorWheelView extends View {

    public interface OnColorChanged {
        void onColorChanged(int color);
    }

    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint alphaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF valueRect = new RectF();
    private final RectF alphaRect = new RectF();
    private final RectF wheelRect = new RectF();

    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;
    private int alpha = 255;

    private float density;
    private OnColorChanged listener;

    private static final int[] HUES = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
            0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    public ColorWheelView(Context context) {
        this(context, null);
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(3 * density);
        markerPaint.setColor(Color.WHITE);
        darkPaint.setColor(Color.BLACK);
        checkerPaint.setColor(0xFFDDDDDD);
        setMinimumHeight((int) (260 * density));
    }

    public void setOnColorChangedListener(OnColorChanged listener) {
        this.listener = listener;
    }

    public int getColor() {
        return Color.HSVToColor(alpha, new float[]{hue, sat, val});
    }

    public void setColor(int color) {
        alpha = Color.alpha(color);
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = (int) (300 * density);
        }
        int bars = (int) (76 * density);
        int wheel = width - (int) (24 * density);
        int height = wheel + bars;
        setMeasuredDimension(width,
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float pad = 12 * density;
        float barH = 22 * density;
        float wheelSize = w - pad * 2;

        wheelRect.set(pad, pad, pad + wheelSize, pad + wheelSize);
        valueRect.set(pad, wheelRect.bottom + 14 * density,
                w - pad, wheelRect.bottom + 14 * density + barH);
        alphaRect.set(pad, valueRect.bottom + 10 * density,
                w - pad, valueRect.bottom + 10 * density + barH);

        float cx = wheelRect.centerX();
        float cy = wheelRect.centerY();
        float r = wheelRect.width() / 2f;

        SweepGradient sweep = new SweepGradient(cx, cy, HUES, null);
        RadialGradient radial = new RadialGradient(cx, cy, r,
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        wheelPaint.setShader(new android.graphics.ComposeShader(
                sweep, radial, android.graphics.PorterDuff.Mode.SRC_OVER));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float r = wheelRect.width() / 2f;
        if (r <= 0) {
            return;
        }

        canvas.drawCircle(wheelRect.centerX(), wheelRect.centerY(), r, wheelPaint);

        // value: black overlay, stronger as value drops
        darkPaint.setAlpha((int) ((1f - val) * 255));
        canvas.drawCircle(wheelRect.centerX(), wheelRect.centerY(), r, darkPaint);
        darkPaint.setAlpha(255);

        // wheel marker
        float angle = (float) Math.toRadians(hue);
        float mx = wheelRect.centerX() + (float) Math.cos(angle) * sat * r;
        float my = wheelRect.centerY() + (float) Math.sin(angle) * sat * r;
        markerPaint.setColor(isLight(Color.HSVToColor(new float[]{hue, sat, val}))
                ? Color.BLACK : Color.WHITE);
        canvas.drawCircle(mx, my, 8 * density, markerPaint);

        // value bar
        int pure = Color.HSVToColor(new float[]{hue, sat, 1f});
        valuePaint.setShader(new android.graphics.LinearGradient(
                valueRect.left, 0, valueRect.right, 0,
                Color.BLACK, pure, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(valueRect, 11 * density, 11 * density, valuePaint);
        drawBarMarker(canvas, valueRect, val);

        // alpha bar over a light track
        canvas.drawRoundRect(alphaRect, 11 * density, 11 * density, checkerPaint);
        alphaPaint.setShader(new android.graphics.LinearGradient(
                alphaRect.left, 0, alphaRect.right, 0,
                Color.argb(0, Color.red(pure), Color.green(pure), Color.blue(pure)),
                Color.argb(255, Color.red(pure), Color.green(pure), Color.blue(pure)),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(alphaRect, 11 * density, 11 * density, alphaPaint);
        drawBarMarker(canvas, alphaRect, alpha / 255f);
    }

    private void drawBarMarker(Canvas canvas, RectF rect, float fraction) {
        float x = rect.left + rect.width() * Math.max(0f, Math.min(1f, fraction));
        markerPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, rect.centerY(), rect.height() / 2f + 2 * density,
                markerPaint);
        markerPaint.setColor(Color.BLACK);
        canvas.drawCircle(x, rect.centerY(), rect.height() / 2f, markerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float pad = 12 * density;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                handleTouch(x, y, pad);
                return true;

            case MotionEvent.ACTION_MOVE:
                handleTouch(x, y, pad);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handleTouch(x, y, pad);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;

            default:
                return true;
        }
    }

    private void handleTouch(float x, float y, float pad) {
        if (nearBand(y, valueRect, pad)) {
            val = fraction(x, valueRect);
        } else if (nearBand(y, alphaRect, pad)) {
            alpha = Math.round(fraction(x, alphaRect) * 255);
        } else if (y <= valueRect.top - pad) {
            updateWheel(x, y);
        } else {
            return;
        }
        notifyChanged();
    }

    private boolean nearBand(float y, RectF rect, float pad) {
        return rect.height() > 0 && y >= rect.top - pad && y <= rect.bottom + pad;
    }

    private void updateWheel(float x, float y) {
        float dx = x - wheelRect.centerX();
        float dy = y - wheelRect.centerY();
        float r = wheelRect.width() / 2f;
        if (r <= 0) {
            return;
        }
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        hue = ((float) Math.toDegrees(Math.atan2(dy, dx)) + 360f) % 360f;
        sat = Math.max(0f, Math.min(1f, dist / r));
    }

    private float fraction(float x, RectF rect) {
        if (rect.width() <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, (x - rect.left) / rect.width()));
    }

    private void notifyChanged() {
        invalidate();
        if (listener != null) {
            listener.onColorChanged(getColor());
        }
    }

    private static boolean isLight(int color) {
        return (Color.red(color) * 299
                + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000 > 140;
    }
}
