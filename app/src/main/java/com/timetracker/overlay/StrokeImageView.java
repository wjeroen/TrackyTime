package com.timetracker.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView with an icon stroke/outline (TV subtitle style).
 *
 * The icon is first rendered into an off-screen Bitmap via super.onDraw() so that
 * ImageView's full scale/center matrix is applied correctly at every size. That bitmap
 * is then stamped at 8 offset positions in the contrasting color, and finally drawn
 * once more normally on top — matching the look of StrokeTextView at every overlay size.
 *
 * Auto-contrast: black stroke for light tint, white stroke for dark tint (ITU BT.601).
 * Offset scales proportionally with the view's pixel width so the stroke weight is
 * consistent across small / medium / large / extra-large overlay sizes.
 */
public class StrokeImageView extends ImageView {
    private boolean strokeEnabled = false;

    public StrokeImageView(Context context) {
        super(context);
    }

    public StrokeImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StrokeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setStrokeEnabled(boolean enabled) {
        this.strokeEnabled = enabled;
        invalidate();
    }

    /** Contrasting stroke color from the current image tint (same logic as StrokeTextView). */
    private int getStrokeColor() {
        ColorStateList tint = getImageTintList();
        int tintColor = (tint != null) ? tint.getDefaultColor() : 0xFF000000;
        int r = (tintColor >> 16) & 0xFF;
        int g = (tintColor >> 8) & 0xFF;
        int b = tintColor & 0xFF;
        float brightness = 0.299f * r + 0.587f * g + 0.114f * b; // ITU BT.601
        return brightness >= 128f ? 0xFF000000 : 0xFFFFFFFF;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable d = getDrawable();
        int w = getWidth();
        int h = getHeight();
        if (!strokeEnabled || d == null || w <= 0 || h <= 0) {
            super.onDraw(canvas);
            return;
        }

        // Render the icon into a correctly-sized bitmap via super.onDraw().
        // This lets ImageView apply its full scale/center matrix, so the bitmap
        // always matches the view's actual pixel size — not the drawable's natural size.
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas bmpCanvas = new Canvas(bmp);
        super.onDraw(bmpCanvas);

        // Offset scales with view width so stroke weight is proportional at all overlay sizes.
        // ~3.5% of icon width: calibrated so it matches the text stroke weight at "large".
        float offset = Math.max(1f, w * 0.035f);

        int strokeColor = getStrokeColor();
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        strokePaint.setColorFilter(
            new PorterDuffColorFilter(strokeColor, PorterDuff.Mode.SRC_IN));

        // 8 surrounding stamps → solid outline in contrasting color
        float[][] offsets = {
            {-offset, 0}, {offset, 0}, {0, -offset}, {0, offset},
            {-offset, -offset}, {offset, -offset}, {-offset, offset}, {offset, offset}
        };
        for (float[] off : offsets) {
            canvas.drawBitmap(bmp, off[0], off[1], strokePaint);
        }

        // Draw the icon normally on top (no color filter — bitmap already has the tint)
        canvas.drawBitmap(bmp, 0, 0, null);
        bmp.recycle();
    }
}
