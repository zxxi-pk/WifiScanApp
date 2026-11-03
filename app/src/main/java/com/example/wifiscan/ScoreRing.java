package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 总分圆环：0–100 分，深色底白字。 */
public class ScoreRing extends View {

    private int score = 0;
    private int ringBg = 0xFF2C3A57;
    private boolean dark = true;

    public ScoreRing(Context c) { super(c); }
    public ScoreRing(Context c, AttributeSet a) { super(c, a); }

    public void setScore(int s) { score = Math.max(0, Math.min(100, s)); invalidate(); }

    public void setDark(boolean d) {
        dark = d;
        ringBg = d ? 0xFF2C3A57 : 0xFFE6EAF0;
        invalidate();
    }

    private int scoreColor() {
        if (score >= 75) return 0xFF10B981;
        if (score >= 50) return 0xFFF59E0B;
        return 0xFFEF4444;
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = Math.min(w, h) / 2f - 10f;
        float cx = w / 2f, cy = h / 2f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7f);
        p.setColor(ringBg);
        cv.drawCircle(cx, cy, r, p);

        if (score > 0) {
            p.setColor(scoreColor());
            p.setStrokeCap(Paint.Cap.ROUND);
            RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
            cv.drawArc(oval, -90f, 360f * score / 100f, false, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(dark ? 0xFFFFFFFF : 0xFF17223B);
        p.setTextSize(r * 0.82f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        Paint.FontMetrics fm = p.getFontMetrics();
        float ty = cy - (fm.ascent + fm.descent) / 2f;
        cv.drawText(String.valueOf(score), cx, ty, p);
    }
}
