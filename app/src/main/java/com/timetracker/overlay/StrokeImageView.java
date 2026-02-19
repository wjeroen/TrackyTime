package com.timetracker.overlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView with an icon stroke/outline (TV subtitle style).
 * When enabled, draws the icon at 8 offset positions in the contrasting color
 * (auto-contrast: black stroke for light tint, white stroke for dark tint),
 * then draws the icon normally on top — matching the look of StrokeTextView.
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

    /** Determine contrasting stroke color from the current image tint. */
    private int getStrokeColor() {
        ColorStateList tint = getImageTintList();
        int tintColor = (tint != null) ? tint.getDefaultColor() : 0xFF000000;
        int r = (tintColor >> 16) & 0xFF;
        int g = (tintColor >> 8) & 0xFF;
        int b = tintColor & 0xFF;
        // ITU BT.601 perceived brightness (same formula as StrokeTextView)
        float brightness = 0.299f * r + 0.587f * g + 0.114f * b;
        return brightness >= 128f ? 0xFF000000 : 0xFFFFFFFF;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable d = getDrawable();
        if (!strokeEnabled || d == null) {
            super.onDraw(canvas);
            return;
        }

        // Save the color filter already set by ImageView's tint mechanism
        ColorFilter originalFilter = d.getColorFilter();
        int strokeColor = getStrokeColor();
        PorterDuffColorFilter strokeFilter =
            new PorterDuffColorFilter(strokeColor, PorterDuff.Mode.SRC_IN);

        // Draw at 8 surrounding offsets to form a solid outline
        float offset = 2.5f;
        float[][] offsets = {
            {-offset, 0}, {offset, 0}, {0, -offset}, {0, offset},
            {-offset, -offset}, {offset, -offset}, {-offset, offset}, {offset, offset}
        };
        d.setColorFilter(strokeFilter);
        for (float[] off : offsets) {
            canvas.save();
            canvas.translate(off[0], off[1]);
            d.draw(canvas);
            canvas.restore();
        }

        // Restore original tint and draw the icon on top
        d.setColorFilter(originalFilter);
        super.onDraw(canvas);
    }
}
