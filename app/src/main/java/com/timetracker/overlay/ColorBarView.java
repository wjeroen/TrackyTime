package com.timetracker.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horizontal stacked bar chart that groups activity time by color.
 * Activities sharing the same color are lumped into one segment.
 * Shows percentage labels on segments that are wide enough.
 */
public class ColorBarView extends View {

    private List<ActivityEntry> entries;
    private Paint paint;
    private Paint textPaint;
    private RectF rect;

    public ColorBarView(Context context) {
        super(context);
        init();
    }

    public ColorBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);

        rect = new RectF();
    }

    public void setEntries(List<ActivityEntry> entries) {
        this.entries = entries;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int parentWidth = MeasureSpec.getSize(widthSpec);
        // Pie chart caps at 600px; bar is ~1.4x that, then shrinks on small screens
        int maxBarWidth = (int) (600 * 1.4f);
        int width = Math.min(parentWidth, maxBarWidth);
        float density = getResources().getDisplayMetrics().density;
        int height = (int) (32 * density); // 32dp tall
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float cornerRadius = 6 * density;

        if (entries == null || entries.isEmpty()) return;

        // Group durations by color
        int totalSeconds = 0;
        Map<Integer, Integer> colorDurations = new LinkedHashMap<>();
        for (ActivityEntry e : entries) {
            int color = e.getColor();
            int dur = e.getDurationSeconds();
            totalSeconds += dur;
            colorDurations.put(color, colorDurations.getOrDefault(color, 0) + dur);
        }
        if (totalSeconds == 0) return;

        // Sort by color similarity (hue), then by duration as fallback
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(colorDurations.entrySet());
        sorted.sort((a, b) -> {
            // Convert colors to HSV to get hue
            float[] hsvA = new float[3];
            float[] hsvB = new float[3];
            Color.colorToHSV(a.getKey(), hsvA);
            Color.colorToHSV(b.getKey(), hsvB);

            // Primary sort: by hue (groups similar colors together)
            float hueDiff = hsvA[0] - hsvB[0];
            if (Math.abs(hueDiff) > 1.0f) {
                return Float.compare(hsvA[0], hsvB[0]);
            }

            // Secondary sort: by duration (larger segments first within same hue)
            return b.getValue() - a.getValue();
        });

        // Draw rounded background
        paint.setColor(0xFF333344);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // Draw each color segment
        float x = 0;
        int segCount = sorted.size();
        for (int i = 0; i < segCount; i++) {
            Map.Entry<Integer, Integer> seg = sorted.get(i);
            float segWidth = (seg.getValue() / (float) totalSeconds) * w;
            if (segWidth < 1f) continue;

            paint.setColor(seg.getKey());
            // Clip to rounded rect using save/restore
            canvas.save();
            canvas.clipRect(x, 0, x + segWidth, h);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            canvas.restore();

            // Percentage label on segments wide enough
            int pct = Math.round((seg.getValue() / (float) totalSeconds) * 100f);
            textPaint.setTextSize(h * 0.42f);
            textPaint.setColor(Color.WHITE);
            textPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK);
            float labelWidth = textPaint.measureText(pct + "%");
            if (segWidth > labelWidth + 8 * density) {
                float cy = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
                canvas.drawText(pct + "%", x + segWidth / 2f, cy, textPaint);
            }
            textPaint.setShadowLayer(0, 0, 0, 0);

            x += segWidth;
        }
    }
}
