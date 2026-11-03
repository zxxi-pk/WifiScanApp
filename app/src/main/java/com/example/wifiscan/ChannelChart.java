package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 2.4G 13 信道占用柱状图。 */
public class ChannelChart extends View {

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
        if (plot <= 0) return;
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
            p.setColor(0xFF10B981);
            cv.drawRect(left - 2, bottom - bh - 2, left + bw + 2, bottom, p);
            p.setStyle(Paint.Style.FILL);

            tp.setColor(0xFF0E9F6E);
            tp.setFakeBoldText(true);
            tp.setTextSize(10f);
            cv.drawText("用这个", gap * i + gap / 2f, padTop + plot - bh - 7f, tp);
        }
    }
}
