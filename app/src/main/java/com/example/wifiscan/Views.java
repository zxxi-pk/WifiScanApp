package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 自绘图形，不依赖任何图表库。 */
public final class Views {

    private Views() {}

    public static final int C_OK = 0xFF10B981;
    public static final int C_WARN = 0xFFF59E0B;
    public static final int C_BAD = 0xFFEF4444;
    public static final int C_TRACK = 0xFFEDF0F5;

    public static int colorFor(double ratio) {
        if (ratio <= 0.34) return C_OK;
        if (ratio <= 0.67) return C_WARN;
        return C_BAD;
    }

    // ==================== 圆环评分 ====================
    public static class ScoreRing extends View {
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
            if (score >= 75) return C_OK;
            if (score >= 50) return C_WARN;
            return C_BAD;
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
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

    // ==================== 横向色带 ====================
    public static class Band extends View {
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
            float bar = h;
            p.reset();
            p.setAntiAlias(true);
            p.setColor(C_TRACK);
            RectF rf = new RectF(0, 0, w, bar);
            cv.drawRoundRect(rf, bar / 2f, bar / 2f, p);

            if (ratio > 0) {
                p.setColor(colorFor(ratio));
                RectF fill = new RectF(0, 0, Math.max(bar * 0.6f, (float) (w * ratio)), bar);
                cv.drawRoundRect(fill, bar / 2f, bar / 2f, p);
            }
        }
    }

    // ==================== 信道柱状图 ====================
    public static class ChannelChart extends View {
        private int[] counts = new int[13];
        private int suggest = -1;
        private int current = -1;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ChannelChart(Context c) { super(c); }
        public ChannelChart(Context c, AttributeSet a) { super(c, a); }

        public void setData(int[] c13, int cur, int sug) {
            counts = (c13 == null || c13.length < 13) ? new int[13] : c13;
            current = cur;
            suggest = sug;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float padTop = 30f, padBot = 22f;
            float plot = h - padTop - padBot;
            float gap = w / 13f;
            float bw = gap * 0.60f;
            int max = 1;
            for (int v : counts) if (v > max) max = v;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(0xFFE2E7EE);
            cv.drawLine(0, padTop + plot, w, padTop + plot, p);
            p.setColor(0xFFEDF0F5);
            cv.drawLine(0, padTop + plot * 0.5f, w, padTop + plot * 0.5f, p);

            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 13; i++) {
                int v = counts[i];
                float bh = plot * v / max;
                if (bh < 3f) bh = 3f;
                float left = gap * i + (gap - bw) / 2f;
                float bottom = padTop + plot;

                int col;
                if (v == 0) col = 0xFFE2E8F0;
                else if (v <= 1) col = 0xFFCBD5E1;
                else if (v <= 2) col = 0xFF94A3B8;
                else if (v <= 3) col = 0xFFF59E0B;
                else col = 0xFFEF4444;
                if (i + 1 == current && v > 0) col = 0xFF1E3A5F;

                p.setColor(col);
                cv.drawRect(left, bottom - bh, left + bw, bottom, p);

                tp.setColor(0xFF95A0B0);
                tp.setFakeBoldText(false);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(10f);
                cv.drawText(String.valueOf(i + 1), gap * i + gap / 2f, h - 6f, tp);
            }

            if (suggest >= 1 && suggest <= 13) {
                int i = suggest - 1;
                float left = gap * i + (gap - bw) / 2f;
                float bh = plot * counts[i] / max;
                if (bh < 3f) bh = 3f;
                float bottom = padTop + plot;

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setColor(C_OK);
                cv.drawRect(left - 2, bottom - bh - 2, left + bw + 2, bottom, p);
                p.setStyle(Paint.Style.FILL);

                tp.setColor(0xFF0E9F6E);
                tp.setFakeBoldText(true);
                tp.setTextSize(10f);
                cv.drawText("用这个", gap * i + gap / 2f, padTop + plot - bh - 7f, tp);
            }
        }
    }

    // ==================== 测速圆环 ====================
    public static class SpeedRing extends View {
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
            float cx = w / 2f, cy = h / 2f;

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(12f);
            p.setColor(C_TRACK);
            cv.drawCircle(cx, cy, r, p);

            double ratio = Math.max(0, Math.min(1, value / fullScale));
            p.setStrokeCap(Paint.Cap.ROUND);
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            if (running && ratio <= 0.02) {
                p.setColor(0xFF9CA3AF);
                cv.drawArc(o, (phase * 24f) % 360f, 70f, false, p);
            } else if (ratio > 0) {
                int col = ratio <= 0.18 ? C_BAD : (ratio <= 0.5 ? C_WARN : C_OK);
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
}
