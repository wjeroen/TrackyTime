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
     * - Full-hour: 10dp tall (fills entire view height, extending 2dp above/below 6dp timeline), 100% white, 2dp wide
     * - Half-hour: half of timeline height (bottom-aligned within timeline area), 100% white, 2dp wide
     * - Half-hour marks are dropped once total tracked time exceeds 5 hours
     *
     * View layout: 2dp padding top + 6dp timeline + 2dp padding bottom = 10dp total
     */
    private void drawTickMarks(Canvas canvas, int totalDuration, float w, float h) {
        if (totalDuration < 1800) return; // no marks if less than 30 min

        boolean showHalfHour = totalDuration <= 5 * 3600;

        // Convert dp to px
        float density = getResources().getDisplayMetrics().density;
        float paddingDp = 2f * density; // 2dp padding on top and bottom
        float timelineHeight = 6f * density; // 6dp timeline height in center

        // Timeline sits in the middle of the view
        float timelineTop = paddingDp;
        float timelineBottom = paddingDp + timelineHeight;
        float halfTimelineH = timelineHeight / 2f;

        tickPaint.setStrokeWidth(2f * density); // 2dp

        // Walk through every 30-min interval up to totalDuration
        for (int sec = 1800; sec < totalDuration; sec += 1800) {
            boolean isFullHour = (sec % 3600 == 0);
            if (!isFullHour && !showHalfHour) continue;

            float tickX = (sec / (float) totalDuration) * w;

            if (isFullHour) {
                // Hour marks: full height (0 to h = entire 10dp view)
                tickPaint.setColor(0xFFFFFFFF); // 100% white
                canvas.drawLine(tickX, 0, tickX, h, tickPaint);
            } else {
                // Half-hour marks: bottom half of timeline area
                tickPaint.setColor(0xFFFFFFFF); // fully opaque white
                canvas.drawLine(tickX, timelineTop + halfTimelineH, tickX, timelineBottom, tickPaint);
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

        // Calculate timeline area (view is 10dp total: 2dp padding + 6dp timeline + 2dp padding)
        float density = getResources().getDisplayMetrics().density;
        float paddingDp = 2f * density;
        float timelineHeight = 6f * density;
        float timelineTop = paddingDp;
        float timelineBottom = paddingDp + timelineHeight;

        // Clip to rounded rect so first/last segments get rounded edges
        // Timeline bar sits in the middle 6dp of the 10dp view
        clipPath.reset();
        bounds.set(0, timelineTop, w, timelineBottom);
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

            // Draw segment in the middle 6dp area
            canvas.drawRect(x, timelineTop, x + segWidth, timelineBottom, paint);
            x += segWidth;
        }

        canvas.restore();

        // Draw time tick marks on top of segments (outside clipping so hour marks can extend)
        drawTickMarks(canvas, totalDuration, w, h);
    }
}
