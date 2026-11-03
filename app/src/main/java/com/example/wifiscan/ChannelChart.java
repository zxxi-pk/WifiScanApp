package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 信道占用柱状图。支持 2.4G 的 13 个独立信道，也支持 5G 的四组信道。 */
public class ChannelChart extends View {

    private int[] counts = new int[13];
    private String[] labels = null;   // null 表示 2.4G，用 1..13
    private int suggest = -1;         // 索引
    private int current = -1;         // 索引

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ChannelChart(Context c) { super(c); }
    public ChannelChart(Context c, AttributeSet a) { super(c, a); }

    /** 2.4G：传 13 个信道的占用数，cur/sug 是信道号（1..13）。 */
    public void setData(int[] c13, int cur, int sug) {
        counts = (c13 == null || c13.length < 13) ? new int[13] : c13;
        labels = null;
        current = cur >= 1 && cur <= 13 ? cur - 1 : -1;
        suggest = sug >= 1 && sug <= 13 ? sug - 1 : -1;
        invalidate();
    }

    /** 5G：传分组占用数，labels 为分组名，cur/sug 是数组索引。 */
    public void setData5g(int[] groups, String[] labels, int curIdx, int sugIdx) {
        counts = groups == null ? new int[0] : groups;
        this.labels = labels;
        current = curIdx;
        suggest = sugIdx;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || counts.length == 0) return;

        final int n = counts.length;
        final boolean is5g = labels != null;

        float padTop = 32f, padBot = 24f;
        float plot = h - padTop - padBot;
        if (plot <= 0) return;
        float gap = w / n;
        float bw = gap * (n > 6 ? 0.60f : 0.52f);

        int max = 1;
        for (int v : counts) if (v > max) max = v;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0xFFE2E7EE);
        cv.drawLine(0, padTop + plot, w, padTop + plot, p);
        p.setColor(0xFFEDF0F5);
        cv.drawLine(0, padTop + plot * 0.5f, w, padTop + plot * 0.5f, p);

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
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
            if (i == current) col = 0xFF1E3A5F;

            p.setColor(col);
            cv.drawRect(left, bottom - bh, left + bw, bottom, p);

            // 当前所在：柱顶加白色小圆点做标记
            if (i == current) {
                p.setColor(0xFFFFFFFF);
                cv.drawCircle(left + bw / 2f, bottom - bh - 7f, 3f, p);
            }

            tp.setColor(i == current ? 0xFF17223B : 0xFF95A0B0);
            tp.setFakeBoldText(i == current);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(is5g ? 9f : 10f);
            String lb = is5g ? labels[i] : String.valueOf(i + 1);
            cv.drawText(lb, gap * i + gap / 2f, h - 6f, tp);
        }

        if (suggest >= 0 && suggest < n) {
            int i = suggest;
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
            tp.setTextSize(9f);
            cv.drawText("最空", gap * i + gap / 2f, padTop + plot - bh - 18f, tp);
        }
    }
}
