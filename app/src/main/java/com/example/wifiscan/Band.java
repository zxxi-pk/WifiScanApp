package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 横向色带：越靠近满格越差。 */
public class Band extends View {

    private double ratio = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Band(Context c) { super(c); }
    public Band(Context c, AttributeSet a) { super(c, a); }

    public void setRatio(double r) {
        ratio = Math.max(0, Math.min(1, r));
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float bar = h;
        p.reset();
        p.setAntiAlias(true);
        p.setColor(0xFFEDF0F5);
        cv.drawRoundRect(new RectF(0, 0, w, bar), bar / 2f, bar / 2f, p);

        if (ratio > 0) {
            int col = ratio <= 0.34 ? 0xFF10B981 : (ratio <= 0.67 ? 0xFFF59E0B : 0xFFEF4444);
            p.setColor(col);
            RectF fill = new RectF(0, 0, Math.max(bar * 0.6f, (float) (w * ratio)), bar);
            cv.drawRoundRect(fill, bar / 2f, bar / 2f, p);
        }
    }
}
