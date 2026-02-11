package com.timetracker.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

public class PieChartView extends View {

    private List<ActivityEntry> entries;
    private Paint paint;
    private Paint textPaint;
    private RectF rect;
    private float padding;

    public PieChartView(Context context) {
        super(context);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        padding = 24f;
        rect = new RectF();
    }

    public void setEntries(List<ActivityEntry> entries) {
        this.entries = entries;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int size = Math.min(width, 600);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        rect.set(padding, padding, size - padding, size - padding);

        if (entries == null || entries.isEmpty()) {
            paint.setColor(0xFF333344);
            canvas.drawArc(rect, 0, 360, true, paint);
            textPaint.setTextSize(size / 15f);
            textPaint.setColor(0xFF888899);
            canvas.drawText("No data", size / 2f, size / 2f + 10f, textPaint);
            return;
        }

        int totalSeconds = 0;
        for (ActivityEntry e : entries) totalSeconds += e.getDurationSeconds();
        if (totalSeconds == 0) return;

        // Draw slices
        float startAngle = -90f;
        for (ActivityEntry e : entries) {
            float sweep = (e.getDurationSeconds() / (float) totalSeconds) * 360f;
            paint.setColor(e.getColor());
            canvas.drawArc(rect, startAngle, sweep, true, paint);

            // Draw label on slices that are big enough
            if (sweep > 20f) {
                float midAngle = (float) Math.toRadians(startAngle + sweep / 2f);
                float radius = (size - padding * 2) / 3f;
                float cx = size / 2f + (float) Math.cos(midAngle) * radius;
                float cy = size / 2f + (float) Math.sin(midAngle) * radius;

                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(size / 22f);
                textPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);

                String label = e.getName();
                if (label.length() > 10) label = label.substring(0, 9) + "…";
                canvas.drawText(label, cx, cy - 6f, textPaint);

                int pct = Math.round((e.getDurationSeconds() / (float) totalSeconds) * 100f);
                textPaint.setTextSize(size / 28f);
                canvas.drawText(pct + "%", cx, cy + size / 22f, textPaint);
                textPaint.setShadowLayer(0, 0, 0, 0);
            }

            startAngle += sweep;
        }

        // Draw thin gap lines between slices for clarity
        paint.setColor(0xFF1E1E2E);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        startAngle = -90f;
        float cx = size / 2f, cy = size / 2f;
        float r = (size - padding * 2) / 2f;
        for (ActivityEntry e : entries) {
            float sweep = (e.getDurationSeconds() / (float) totalSeconds) * 360f;
            float rad = (float) Math.toRadians(startAngle);
            canvas.drawLine(cx, cy,
                cx + (float) Math.cos(rad) * r,
                cy + (float) Math.sin(rad) * r, paint);
            startAngle += sweep;
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
