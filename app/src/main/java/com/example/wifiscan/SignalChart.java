package com.example.wifiscan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 信号强度时间轴：把每秒采到的 dBm 画成折线，能看出信号是稳还是忽高忽低。 */
public class SignalChart extends View {

    private static final int MAX_POINTS = 90;   // 90 个点 × 2 秒 ≈ 3 分钟

    private final List<Integer> points = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final Path area = new Path();

    public SignalChart(Context c) { super(c); }
    public SignalChart(Context c, AttributeSet a) { super(c, a); }

    public void push(int rssi) {
        if (rssi >= 0) return;
        points.add(rssi);
        while (points.size() > MAX_POINTS) points.remove(0);
        invalidate();
    }

    public void clear() {
        points.clear();
        invalidate();
    }

    public int size() { return points.size(); }

    public int spread() {
        if (points.size() < 2) return 0;
        int lo = 0, hi = -200;
        for (int v : points) {
            if (v < lo) lo = v;
            if (v > hi) hi = v;
        }
        return hi - lo;
    }

    public int last() {
        return points.isEmpty() ? 0 : points.get(points.size() - 1);
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float padL = 30f, padR = 8f, padT = 12f, padB = 20f;
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;
        if (plotW <= 0 || plotH <= 0) return;

        // 纵轴 -25(顶) .. -95(底)
        final int TOP_DBM = -25, BOT_DBM = -95;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        tp.setTextSize(9f);
        tp.setTextAlign(Paint.Align.RIGHT);

        int[] grid = {-40, -55, -70, -85};
        for (int g : grid) {
            float y = padT + (TOP_DBM - g) * plotH / (float) (TOP_DBM - BOT_DBM);
            p.setColor(g == -70 ? 0xFFE2E7EE : 0xFFEFF2F6);
            cv.drawLine(padL, y, padL + plotW, y, p);
            tp.setColor(0xFFB0B8C4);
            cv.drawText(String.valueOf(g), padL - 4f, y + 3f, tp);
        }

        if (points.size() < 2) {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(0xFFB0B8C4);
            tp.setTextSize(11f);
            cv.drawText("正在采样…", padL + plotW / 2f, padT + plotH / 2f, tp);
            return;
        }

        float stepX = plotW / (MAX_POINTS - 1);
        int n = points.size();
        float startX = padL + plotW - stepX * (n - 1);

        line.reset();
        area.reset();
        float baseY = padT + plotH;
        for (int i = 0; i < n; i++) {
            int dbm = points.get(i);
            int clamped = Math.max(BOT_DBM, Math.min(TOP_DBM, dbm));
            float x = startX + stepX * i;
            float y = padT + (TOP_DBM - clamped) * plotH / (float) (TOP_DBM - BOT_DBM);
            if (i == 0) { line.moveTo(x, y); area.moveTo(x, baseY); area.lineTo(x, y); }
            else { line.lineTo(x, y); area.lineTo(x, y); }
        }
        area.lineTo(startX + stepX * (n - 1), baseY);
        area.close();

        int last = points.get(n - 1);
        int col = last >= -55 ? 0xFF10B981 : (last >= -70 ? 0xFFF59E0B : 0xFFEF4444);

        p.setStyle(Paint.Style.FILL);
        p.setColor((col & 0x00FFFFFF) | 0x22000000);
        cv.drawPath(area, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(col);
        p.setStrokeJoin(Paint.Join.ROUND);
        cv.drawPath(line, p);

        float lx = startX + stepX * (n - 1);
        int lc = Math.max(BOT_DBM, Math.min(TOP_DBM, last));
        float ly = padT + (TOP_DBM - lc) * plotH / (float) (TOP_DBM - BOT_DBM);
        p.setStyle(Paint.Style.FILL);
        cv.drawCircle(lx, ly, 3.5f, p);

        tp.setTextAlign(Paint.Align.LEFT);
        tp.setColor(0xFF95A0B0);
        tp.setTextSize(9f);
        cv.drawText((MAX_POINTS / 2) + " 秒前", padL, h - 5f, tp);
        tp.setTextAlign(Paint.Align.RIGHT);
        cv.drawText("现在", padL + plotW, h - 5f, tp);
    }
}
