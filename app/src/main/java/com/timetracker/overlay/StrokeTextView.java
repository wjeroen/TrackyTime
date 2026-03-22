package com.timetracker.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * TextView with text stroke/outline (TV subtitle style).
 * Auto-contrast: black stroke for light text, white stroke for dark text.
 */
public class StrokeTextView extends TextView {
    private boolean strokeEnabled = true;
    private float strokeWidthSetting = 4f;

    public StrokeTextView(Context context) {
        super(context);
    }

    public StrokeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StrokeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setStrokeEnabled(boolean enabled) {
        this.strokeEnabled = enabled;
        invalidate();
    }

    /**
     * Set stroke width setting (1-10). Actual pixel width is computed in onDraw()
     * using quadratic scaling (width² / 4) and proportional to text size so the
     * stroke scales with overlay size (anchored at 16sp Medium = original default).
     */
    public void setStrokeWidth(float width) {
        this.strokeWidthSetting = width;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!strokeEnabled) {
            super.onDraw(canvas);
            return;
        }

        // Get text color and calculate brightness
        int textColor = getCurrentTextColor();

        // CRITICAL: Extract RGB from the color (ignoring alpha)
        int r = (textColor >> 16) & 0xFF;
        int g = (textColor >> 8) & 0xFF;
        int b = textColor & 0xFF;

        // ITU BT.601 weighted brightness (0-255 range)
        float brightnessByte = (0.299f * r) + (0.587f * g) + (0.114f * b);

        // Auto-contrast: black stroke for light text (>=128), white for dark text (<128)
        // Force FULL OPACITY for stroke color
        int strokeColor = (brightnessByte >= 128f) ? 0xFF000000 : 0xFFFFFFFF;

        // Save paint AND view state
        Paint paint = getPaint();
        Paint.Style originalStyle = paint.getStyle();
        float originalStrokeWidth = paint.getStrokeWidth();

        // Scale stroke with text size so it's proportional at all overlay sizes.
        // Anchored at 16sp (Medium): setting 4 → 4px at 16sp, scales up for larger text.
        float basePx = (strokeWidthSetting * strokeWidthSetting) / 4f;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float referencePx = 16f * scaledDensity;
        float strokeWidth = basePx * (getTextSize() / referencePx);

        // Draw stroke: change VIEW's color so super.onDraw() uses it
        setTextColor(strokeColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        super.onDraw(canvas);

        // Draw fill: restore VIEW's original color
        setTextColor(textColor);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(originalStrokeWidth);
        super.onDraw(canvas);

        // Restore paint state
        paint.setStyle(originalStyle);
    }
}
