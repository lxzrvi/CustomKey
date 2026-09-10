package com.customkey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Single-key preview used by the per-key editor panel. */
public class KeyShapeView extends View {

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String label = "A";
    private int bg = Color.WHITE;
    private int textColor = Color.BLACK;
    private int borderColor = Color.BLACK;
    private float borderWidthDp = 0f;
    private float radiusDp = 10f;
    private float textSizeSp = 17f;
    private Typeface typeface = Typeface.DEFAULT;
    private float density;

    public KeyShapeView(Context context) {
        this(context, null);
    }

    public KeyShapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    public void set(String label, int bg, int textColor, int borderColor,
                    float borderWidthDp, float radiusDp, float textSizeSp,
                    Typeface typeface) {
        this.label = label == null ? "" : label;
        this.bg = bg;
        this.textColor = textColor;
        this.borderColor = borderColor;
        this.borderWidthDp = borderWidthDp;
        this.radiusDp = radiusDp;
        this.textSizeSp = textSizeSp;
        if (typeface != null) {
            this.typeface = typeface;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = (int) (96 * density);
        }
        int height = (int) (52 * density);
        setMeasuredDimension(width,
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float inset = dp(4);
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float rad = dp(radiusDp);

        keyPaint.setColor(bg);
        canvas.drawRoundRect(rect, rad, rad, keyPaint);

        if (borderWidthDp > 0.05f) {
            float bw = dp(borderWidthDp);
            borderPaint.setColor(borderColor);
            borderPaint.setStrokeWidth(bw);
            rect.inset(bw / 2f, bw / 2f);
            canvas.drawRoundRect(rect, Math.max(0f, rad - bw / 2f),
                    Math.max(0f, rad - bw / 2f), borderPaint);
            rect.inset(-bw / 2f, -bw / 2f);
        }

        textPaint.setTypeface(typeface);
        textPaint.setTextSize(sp(textSizeSp));
        textPaint.setColor(textColor);

        float max = rect.width() - dp(8);
        if (max > 0 && textPaint.measureText(label) > max) {
            textPaint.setTextSize(textPaint.getTextSize() * max
                    / textPaint.measureText(label));
        }

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText(label, rect.centerX(),
                rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
