package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 测速圆环：实时显示当前 Mbps，测速中显示旋转弧。 */
public class SpeedRing extends View {

    private double value = 0;
    private double fullScale = 100;
    private String big = "—";
    private String unit = "Mbps";
    private boolean running = false;
    private long phase = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedRing(Context c) { super(c); }
    public SpeedRing(Context c, AttributeSet a) { super(c, a); }

    public void setValue(double mbps, String bigText, String unitText) {
        value = mbps;
        big = bigText;
        unit = unitText;
        running = false;
        invalidate();
    }

    public void setRunning(boolean r, double mbps, String bigText) {
        running = r;
        value = mbps;
        big = bigText;
        invalidate();
    }

    public void pulse() { phase++; invalidate(); }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = Math.min(w, h) / 2f - 16f;
        if (r <= 0) return;
        float cx = w / 2f, cy = h / 2f;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(12f);
        p.setColor(0xFFEDF0F5);
        cv.drawCircle(cx, cy, r, p);

        double ratio = Math.max(0, Math.min(1, value / fullScale));
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        if (running && ratio <= 0.02) {
            p.setColor(0xFF9CA3AF);
            cv.drawArc(o, (phase * 24f) % 360f, 70f, false, p);
        } else if (ratio > 0) {
            int col = ratio <= 0.18 ? 0xFFEF4444 : (ratio <= 0.5 ? 0xFFF59E0B : 0xFF10B981);
            p.setColor(col);
            cv.drawArc(o, -90f, (float) (360 * ratio), false, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF17223B);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setTextSize(r * 0.52f);
        cv.drawText(big, cx, cy + r * 0.14f, p);

        p.setFakeBoldText(false);
        p.setTextSize(12f);
        p.setColor(0xFF8A94A6);
        cv.drawText(unit, cx, cy + r * 0.44f, p);
    }
}
