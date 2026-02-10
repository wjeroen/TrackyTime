package com.timetracker.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * A thin horizontal bar that draws colored segments proportional to their duration.
 * Used as a mini day-timeline on the overlay pill.
 *
 * Each segment = one activity session (color + duration). The last segment can
 * "pulse" independently via setPulseAlpha().
 */
public class TimelineBarView extends View {

    public static class Segment {
        public int color;
        public int durationSeconds;

        public Segment(int color, int durationSeconds) {
            this.color = color;
            this.durationSeconds = durationSeconds;
        }
    }

    private List<Segment> segments = new ArrayList<>();
    private int pulseIndex = -1;       // which segment pulses (-1 = none)
    private float pulseAlpha = 1.0f;   // alpha multiplier for the pulsing segment
    private float cornerRadius = 0f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF bounds = new RectF();

    public TimelineBarView(Context context) {
        super(context);
    }

    public TimelineBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TimelineBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        invalidate();
    }

    /** Set the segments to display. pulseIndex = index of the segment that should pulse. */
    public void setSegments(List<Segment> segments, int pulseIndex) {
        this.segments = segments != null ? segments : new ArrayList<>();
        this.pulseIndex = pulseIndex;
        invalidate();
    }

    /** Set the alpha multiplier for the pulsing segment (0.0–1.0). */
    public void setPulseAlpha(float alpha) {
        this.pulseAlpha = alpha;
        invalidate();
    }

    /**
     * Draw vertical tick marks at hour and half-hour intervals of tracked time.
     * - Full-hour: full bar height, 100% white, 1px wide
     * - Half-hour: half bar height (bottom-aligned), 50% white, 1px wide
     * - Half-hour marks are dropped once total tracked time exceeds 5 hours
     */
    private void drawTickMarks(Canvas canvas, int totalDuration, float w, float h) {
        if (totalDuration < 1800) return; // no marks if less than 30 min

        boolean showHalfHour = totalDuration <= 5 * 3600;
        float halfH = h / 2f;

        tickPaint.setStrokeWidth(1f); // 1px

        // Walk through every 30-min interval up to totalDuration
        for (int sec = 1800; sec < totalDuration; sec += 1800) {
            boolean isFullHour = (sec % 3600 == 0);
            if (!isFullHour && !showHalfHour) continue;

            float tickX = (sec / (float) totalDuration) * w;

            if (isFullHour) {
                tickPaint.setColor(0xFFFFFFFF); // 100% white
                canvas.drawLine(tickX, 0, tickX, h, tickPaint);
            } else {
                tickPaint.setColor(0x80FFFFFF); // 50% white
                canvas.drawLine(tickX, halfH, tickX, h, tickPaint);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (segments.isEmpty()) return;

        int totalDuration = 0;
        for (Segment s : segments) totalDuration += s.durationSeconds;
        if (totalDuration <= 0) return;

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Clip to rounded rect so first/last segments get rounded edges
        clipPath.reset();
        bounds.set(0, 0, w, h);
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        float x = 0;
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            float segWidth;

            // Last segment fills remaining space (avoids float-rounding gaps)
            if (i == segments.size() - 1) {
                segWidth = w - x;
            } else {
                segWidth = (s.durationSeconds / (float) totalDuration) * w;
            }

            paint.setColor(s.color);
            if (i == pulseIndex) {
                int baseAlpha = (s.color >>> 24) & 0xFF;
                paint.setAlpha((int) (baseAlpha * pulseAlpha));
            }

            canvas.drawRect(x, 0, x + segWidth, h, paint);
            x += segWidth;
        }

        // Draw time tick marks on top of segments
        drawTickMarks(canvas, totalDuration, w, h);

        canvas.restore();
    }
}
