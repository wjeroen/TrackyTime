package com.timetracker.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.EditText;

/**
 * EditText with text stroke/outline (TV subtitle style).
 * Auto-contrast: black stroke for light text, white stroke for dark text.
 *
 * Uses Layout.draw() directly for the stroke pass to bypass the Editor's
 * hardware-acceleration cache, which ignores mid-onDraw paint changes.
 * The fill pass uses normal super.onDraw() so cursor/selection still work.
 */
public class StrokeEditText extends EditText {
    private boolean strokeEnabled = true;

    public StrokeEditText(Context context) {
        super(context);
    }

    public StrokeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StrokeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setStrokeEnabled(boolean enabled) {
        this.strokeEnabled = enabled;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!strokeEnabled) {
            super.onDraw(canvas);
            return;
        }

        // Calculate contrasting stroke color using ITU BT.601 perceived brightness
        int textColor = getCurrentTextColor();
        int r = (textColor >> 16) & 0xFF;
        int g = (textColor >> 8) & 0xFF;
        int b = textColor & 0xFF;
        float brightnessByte = (0.299f * r) + (0.587f * g) + (0.114f * b);
        int strokeColor = (brightnessByte >= 128f) ? 0xFF000000 : 0xFFFFFFFF;

        // --- Stroke pass: draw directly via Layout (bypasses Editor cache) ---
        Layout layout = getLayout();
        if (layout != null) {
            int saveCount = canvas.save();

            // Clip to the text area (same region super.onDraw() uses)
            int scrollX = getScrollX();
            int scrollY = getScrollY();
            canvas.clipRect(
                scrollX + getCompoundPaddingLeft(),
                scrollY + getCompoundPaddingTop(),
                scrollX + getWidth() - getCompoundPaddingRight(),
                scrollY + getHeight() - getCompoundPaddingBottom()
            );

            // Vertical offset for gravity (0 for TOP, which is the default)
            int voffsetText = 0;
            int gravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
            if (gravity != Gravity.TOP) {
                int boxHeight = getHeight() - getExtendedPaddingTop()
                        - getExtendedPaddingBottom();
                int textHeight = layout.getHeight();
                if (textHeight < boxHeight) {
                    if (gravity == Gravity.BOTTOM) {
                        voffsetText = boxHeight - textHeight;
                    } else { // CENTER_VERTICAL
                        voffsetText = (boxHeight - textHeight) >> 1;
                    }
                }
            }

            // Translate to text position (matches what super.onDraw() does internally)
            canvas.translate(getCompoundPaddingLeft(),
                    getExtendedPaddingTop() + voffsetText);

            // Configure paint for stroke and draw
            Paint paint = getPaint();
            int origColor = paint.getColor();
            Paint.Style origStyle = paint.getStyle();
            float origStrokeWidth = paint.getStrokeWidth();

            paint.setColor(strokeColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            layout.draw(canvas);

            // Restore paint state
            paint.setColor(origColor);
            paint.setStyle(origStyle);
            paint.setStrokeWidth(origStrokeWidth);

            canvas.restoreToCount(saveCount);
        }

        // --- Fill pass: normal EditText rendering (text + cursor + selection) ---
        super.onDraw(canvas);
    }
}
