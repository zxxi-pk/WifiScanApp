package com.example.wifiscan;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ---- 配色 ----
    private static final int C_INK = 0xFF17223B;
    private static final int C_SUB = 0xFF5C6B80;
    private static final int C_DIM = 0xFF8A94A6;
    private static final int C_FAINT = 0xFF95A0B0;
    private static final int C_OK = 0xFF0E9F6E;
    private static final int C_WARN = 0xFFB45309;
    private static final int C_BAD = 0xFFB3261E;

    private static final int REQ_SCAN = 7;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WifiManager wm;

    // ---- 页面 ----
    private View pageOverview, pageDiag, pageHist, pageTools;
    private View iconOverview, iconDiag, iconHist, iconTools;
    private TextView labelOverview, labelDiag, labelHist, labelTools;
    private int page = 0;

    // ---- 总览 ----
    private TextView tvSsid, tvNetMeta, tvVerdict, tvVerdictSub, tvTag;
    private ScoreRing ringScore;
    private TextView tvOvLat, tvOvLatState, tvOvLoss, tvOvLossState;
    private TextView tvOvDown, tvOvDownState, tvOvDev, tvOvDevState;
    private Button btnRun;
    private TextView tvRunStatus, tvScoreBreak;

    // ---- 诊断 ----
    private TextView tvDiagMeta;
    private TextView segLat, segCh, segSpeed, segDev;
    private View diagLat, diagCh, diagSpeed, diagDev;
    private int seg = 0;

    private Band bandGw, bandWan;
    private TextView tvGwVal, tvWanVal, tvJitter, tvJitterState, tvLossBig, tvLossState;
    private View latVerdictBox;
    private TextView tvLatVerdict, tvLatAdvice;
    private View foldLat;
    private TextView tvFoldLatTitle, tvLatDetail;
    private boolean foldLatOpen = false;

    private ChannelChart chartCh;
    private TextView tvCurCh, tvSugCh, tvChVerdict, tvChAdvice, tvFoldChTitle;
    private View chVerdictBox, foldCh;
    private LinearLayout apList;
    private boolean foldChOpen = false;

    private SpeedRing ringSpeed;
    private TextView tvSpeedStatus, tvDownVal, tvUpVal, tvCostVal, tvSpeedVerdict, tvSpeedAdvice;
    private Band bandDown, bandUp, bandCost;
    private View speedVerdictBox;

    private TextView tvDevTotal, tvDevKnown, tvDevUnknown, tvDevVerdict, tvDevAdvice;
    private LinearLayout devList;
    private View devVerdictBox;

    // ---- 记录 ----
    private LinearLayout histList;
    private TextView tvHistEmpty, btnHistClear;

    // ---- 工具 ----
    private View btnRouter, btnDns, btnBattery, btnWifiSet;
    private TextView tvRouterIp;
    private Button btnCopy;

    // ---- 数据 ----
    private static final String PREF = "wifiscan";
    private static final String K_HIST = "hist_v2";
    private static final String SEP = "\u0001";
    private static final int HIST_MAX = 30;

    private String gateway = "";
    private String localIp = "";
    private Net.WifiBrief wifi = new Net.WifiBrief();
    private Net.PingStat statGw, statWan;
    private int[] chCounts;
    private int curChannel = -1, sugChannel = -1, apCount = 0;
    private double downMbps = -1, upMbps = -1, downMs = -1;
    private List<Net.Dev> devices;
    private int strayCount = 0;
    private long lastCheckAt = 0;
    private String lastReport = "";
    private int lastScore = 0;

    private boolean busy = false;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bind();
        refreshHead();
        loadHistory();
        setPage(0);
        setSeg(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHead();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }

    // ==================== 绑定 ====================

    private void bind() {
        pageOverview = findViewById(R.id.pageOverview);
        pageDiag = findViewById(R.id.pageDiag);
        pageHist = findViewById(R.id.pageHist);
        pageTools = findViewById(R.id.pageTools);

        iconOverview = findViewById(R.id.iconOverview);
        iconDiag = findViewById(R.id.iconDiag);
        iconHist = findViewById(R.id.iconHist);
        iconTools = findViewById(R.id.iconTools);
        labelOverview = findViewById(R.id.labelOverview);
        labelDiag = findViewById(R.id.labelDiag);
        labelHist = findViewById(R.id.labelHist);
        labelTools = findViewById(R.id.labelTools);

        findViewById(R.id.tabOverview).setOnClickListener(v -> setPage(0));
        findViewById(R.id.tabDiag).setOnClickListener(v -> setPage(1));
        findViewById(R.id.tabHist).setOnClickListener(v -> setPage(2));
        findViewById(R.id.tabTools).setOnClickListener(v -> setPage(3));

        tvSsid = findViewById(R.id.tvSsid);
        tvNetMeta = findViewById(R.id.tvNetMeta);
        tvVerdict = findViewById(R.id.tvVerdict);
        tvVerdictSub = findViewById(R.id.tvVerdictSub);
        tvTag = findViewById(R.id.tvTag);
        ringScore = findViewById(R.id.ringScore);
        ringScore.setDark(true);

        tvOvLat = findViewById(R.id.tvOvLat);
        tvOvLatState = findViewById(R.id.tvOvLatState);
        tvOvLoss = findViewById(R.id.tvOvLoss);
        tvOvLossState = findViewById(R.id.tvOvLossState);
        tvOvDown = findViewById(R.id.tvOvDown);
        tvOvDownState = findViewById(R.id.tvOvDownState);
        tvOvDev = findViewById(R.id.tvOvDev);
        tvOvDevState = findViewById(R.id.tvOvDevState);
        btnRun = findViewById(R.id.btnRun);
        tvRunStatus = findViewById(R.id.tvRunStatus);
        tvScoreBreak = findViewById(R.id.tvScoreBreak);

        findViewById(R.id.ovLat).setOnClickListener(v -> jumpDiag(0));
        findViewById(R.id.ovLoss).setOnClickListener(v -> jumpDiag(0));
        findViewById(R.id.ovDown).setOnClickListener(v -> jumpDiag(2));
        findViewById(R.id.ovDev).setOnClickListener(v -> jumpDiag(3));
        btnRun.setOnClickListener(v -> runAll());

        tvDiagMeta = findViewById(R.id.tvDiagMeta);
        segLat = findViewById(R.id.segLat);
        segCh = findViewById(R.id.segCh);
        segSpeed = findViewById(R.id.segSpeed);
        segDev = findViewById(R.id.segDev);
        diagLat = findViewById(R.id.diagLat);
        diagCh = findViewById(R.id.diagCh);
        diagSpeed = findViewById(R.id.diagSpeed);
        diagDev = findViewById(R.id.diagDev);
        segLat.setOnClickListener(v -> setSeg(0));
        segCh.setOnClickListener(v -> setSeg(1));
        segSpeed.setOnClickListener(v -> setSeg(2));
        segDev.setOnClickListener(v -> setSeg(3));

        bandGw = findViewById(R.id.bandGw);
        bandWan = findViewById(R.id.bandWan);
        tvGwVal = findViewById(R.id.tvGwVal);
        tvWanVal = findViewById(R.id.tvWanVal);
        tvJitter = findViewById(R.id.tvJitter);
        tvJitterState = findViewById(R.id.tvJitterState);
        tvLossBig = findViewById(R.id.tvLossBig);
        tvLossState = findViewById(R.id.tvLossState);
        latVerdictBox = findViewById(R.id.latVerdictBox);
        tvLatVerdict = findViewById(R.id.tvLatVerdict);
        tvLatAdvice = findViewById(R.id.tvLatAdvice);
        foldLat = findViewById(R.id.foldLat);
        tvFoldLatTitle = findViewById(R.id.tvFoldLatTitle);
        tvLatDetail = findViewById(R.id.tvLatDetail);
        foldLat.setOnClickListener(v -> {
            foldLatOpen = !foldLatOpen;
            tvLatDetail.setVisibility(foldLatOpen ? View.VISIBLE : View.GONE);
            tvFoldLatTitle.setText(foldLatOpen ? "原始数据  ▴" : "原始数据  ▾");
        });

        chartCh = findViewById(R.id.chartCh);
        tvCurCh = findViewById(R.id.tvCurCh);
        tvSugCh = findViewById(R.id.tvSugCh);
        chVerdictBox = findViewById(R.id.chVerdictBox);
        tvChVerdict = findViewById(R.id.tvChVerdict);
        tvChAdvice = findViewById(R.id.tvChAdvice);
        foldCh = findViewById(R.id.foldCh);
        tvFoldChTitle = findViewById(R.id.tvFoldChTitle);
        apList = findViewById(R.id.apList);
        foldCh.setOnClickListener(v -> {
            foldChOpen = !foldChOpen;
            apList.setVisibility(foldChOpen ? View.VISIBLE : View.GONE);
            tvFoldChTitle.setText(foldChOpen ? "周边 WiFi 列表  ▴" : "周边 WiFi 列表  ▾");
        });

        ringSpeed = findViewById(R.id.ringSpeed);
        tvSpeedStatus = findViewById(R.id.tvSpeedStatus);
        bandDown = findViewById(R.id.bandDown);
        bandUp = findViewById(R.id.bandUp);
        bandCost = findViewById(R.id.bandCost);
        tvDownVal = findViewById(R.id.tvDownVal);
        tvUpVal = findViewById(R.id.tvUpVal);
        tvCostVal = findViewById(R.id.tvCostVal);
        speedVerdictBox = findViewById(R.id.speedVerdictBox);
        tvSpeedVerdict = findViewById(R.id.tvSpeedVerdict);
        tvSpeedAdvice = findViewById(R.id.tvSpeedAdvice);

        tvDevTotal = findViewById(R.id.tvDevTotal);
        tvDevKnown = findViewById(R.id.tvDevKnown);
        tvDevUnknown = findViewById(R.id.tvDevUnknown);
        devList = findViewById(R.id.devList);
        devVerdictBox = findViewById(R.id.devVerdictBox);
        tvDevVerdict = findViewById(R.id.tvDevVerdict);
        tvDevAdvice = findViewById(R.id.tvDevAdvice);

        histList = findViewById(R.id.histList);
        tvHistEmpty = findViewById(R.id.tvHistEmpty);
        btnHistClear = findViewById(R.id.btnHistClear);
        btnHistClear.setOnClickListener(v -> {
            prefs().edit().remove(K_HIST).apply();
            loadHistory();
            toast("记录已清空");
        });

        btnRouter = findViewById(R.id.btnRouter);
        btnDns = findViewById(R.id.btnDns);
        btnBattery = findViewById(R.id.btnBattery);
        btnWifiSet = findViewById(R.id.btnWifiSet);
        btnCopy = findViewById(R.id.btnCopy);
        tvRouterIp = findViewById(R.id.tvRouterIp);

        btnRouter.setOnClickListener(v -> {
            String gw = Net.getGatewayIp(this);
            if (TextUtils.isEmpty(gw)) { toast("没拿到网关地址"); return; }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + gw)));
            } catch (Exception e) {
                toast("打不开浏览器：" + e.getClass().getSimpleName());
            }
        });
        btnDns.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < 28) { toast("这台手机不支持私有 DNS（需 Android 9+）"); return; }
            try {
                startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));
                toast("私有 DNS 提供商填 dns.alidns.com");
            } catch (Exception e) {
                toast("打不开设置页");
            }
        });
        btnBattery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                toast("打不开设置页");
            }
        });
        btnWifiSet.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Exception e) {
                toast("打不开设置页");
            }
        });
        btnCopy.setOnClickListener(v -> copy());
    }

    // ==================== 页面切换 ====================

    private void setPage(int p) {
        page = p;
        pageOverview.setVisibility(p == 0 ? View.VISIBLE : View.GONE);
        pageDiag.setVisibility(p == 1 ? View.VISIBLE : View.GONE);
        pageHist.setVisibility(p == 2 ? View.VISIBLE : View.GONE);
        pageTools.setVisibility(p == 3 ? View.VISIBLE : View.GONE);
        tabStyle(iconOverview, labelOverview, p == 0);
        tabStyle(iconDiag, labelDiag, p == 1);
        tabStyle(iconHist, labelHist, p == 2);
        tabStyle(iconTools, labelTools, p == 3);
    }

    private void tabStyle(View icon, TextView label, boolean on) {
        icon.setBackgroundResource(on ? R.drawable.bar_on : R.drawable.badge_soft);
        label.setTextColor(on ? C_INK : C_DIM);
        label.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        icon.getLayoutParams().width = on ? dp(18) : dp(14);
        icon.getLayoutParams().height = on ? dp(18) : dp(14);
        icon.requestLayout();
    }

    private void jumpDiag(int s) {
        setPage(1);
        setSeg(s);
    }

    private void setSeg(int s) {
        seg = s;
        diagLat.setVisibility(s == 0 ? View.VISIBLE : View.GONE);
        diagCh.setVisibility(s == 1 ? View.VISIBLE : View.GONE);
        diagSpeed.setVisibility(s == 2 ? View.VISIBLE : View.GONE);
        diagDev.setVisibility(s == 3 ? View.VISIBLE : View.GONE);
        segStyle(segLat, s == 0);
        segStyle(segCh, s == 1);
        segStyle(segSpeed, s == 2);
        segStyle(segDev, s == 3);
    }

    private void segStyle(TextView t, boolean on) {
        t.setBackgroundResource(on ? R.drawable.seg_on : 0);
        t.setTextColor(on ? C_INK : C_SUB);
        t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    // ==================== 头部刷新 ====================

    private void refreshHead() {
        wifi = Net.readWifi(this);
        gateway = Net.getGatewayIp(this);
        localIp = Net.getLocalIp(this);

        String name = TextUtils.isEmpty(wifi.ssid) ? "（未连 WiFi 或读不到名称）" : wifi.ssid;
        tvSsid.setText(name);
        tvNetMeta.setText(wifi.band + " · " + wifi.rssi + " dBm · 协商 " + wifi.linkMbps + " Mbps");
        tvRouterIp.setText(TextUtils.isEmpty(gateway) ? "—" : gateway);
        tvDiagMeta.setText(TextUtils.isEmpty(gateway) ? "—" : gateway + "  ·  本机 " + localIp);
    }

    // ==================== 一键全检 ====================

    private void runAll() {
        if (busy) { toast("正在检测，稍等"); return; }
        if (needScanPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_SCAN);
        }
        busy = true;
        btnRun.setEnabled(false);
        btnRun.setText("检测中…");
        lastReport = "";
        statGw = null;
        statWan = null;
        chCounts = null;
        downMbps = -1;
        upMbps = -1;
        ringSpeed.setValue(0, "—", "Mbps");

        pool.execute(() -> {
            refreshHead();
            final String gw = gateway;
            final String me = localIp;

            // 1) 延迟
            status("第 1/4 步：测延迟…");
            final Net.PingStat g = TextUtils.isEmpty(gw) ? new Net.PingStat()
                    : Net.pingHost(gw, 8, s -> ui.post(() -> {
                        tvGwVal.setText(fmt("%.0f ms", s.avg()));
                        tvLossBig.setText(fmt("%.0f%%", s.loss()));
                        bandGw.setRatio(Math.min(1, s.avg() / 120.0));
                    }));
            final Net.PingStat w = Net.pingHost("223.5.5.5", 8, s -> ui.post(() -> {
                tvWanVal.setText(fmt("%.0f ms", s.avg()));
                bandWan.setRatio(Math.min(1, s.avg() / 300.0));
            }));
            statGw = g;
            statWan = w;
            ui.post(() -> paintLatency(g, w));

            // 2) 信道
            status("第 2/4 步：扫周边 WiFi…");
            final int[] cc = new int[13];
            final int[] r = new int[]{0, -1, -1};
            try {
                if (wm != null) {
                    wm.startScan();
                    Thread.sleep(2400);
                    List<ScanResult> rs = wm.getScanResults();
                    if (rs != null) {
                        Collections.sort(rs, (a, b2) -> Integer.compare(b2.level, a.level));
                        final int total = rs.size();
                        for (int i = 0; i < total; i++) {
                            ScanResult sr = rs.get(i);
                            int ch = freqToCh(sr.frequency);
                            if (ch >= 1 && ch <= 13 && sr.level > -85) cc[ch - 1]++;
                            r[0]++;
                        }
                        int cur = chFromFreq(wifi.freq);
                        r[1] = cur;
                        r[2] = bestChannel(cc);
                        final List<ScanResult> copy = rs;
                        ui.post(() -> paintAps(copy, cc, cur, r[2]));
                    }
                }
            } catch (Exception ignored) {
            }
            chCounts = cc;
            apCount = r[0];
            curChannel = r[1];
            sugChannel = r[2];

            // 3) 带宽
            status("第 3/4 步：测下载…");
            startPulse();
            Net.SpeedResult d = Net.measureDownload((done, total, mbps) -> ui.post(() -> {
                ringSpeed.setRunning(true, mbps, fmt("%.1f", mbps));
                tvSpeedStatus.setText("已下载 " + fmt("%.1f", done / 1048576.0) + " / " + fmt("%.0f", total / 1048576.0) + " MB");
            }));
            stopPulse();
            downMbps = d.ok ? d.mbps : -1;
            downMs = d.ok ? d.ms : -1;
            ui.post(() -> paintDown(d));

            status("第 4/4 步：测上传…");
            Net.SpeedResult u = Net.measureUpload((done, total, mbps) -> ui.post(() -> {
                ringSpeed.setRunning(true, mbps, fmt("%.1f", mbps));
                tvSpeedStatus.setText("已上传 " + fmt("%.1f", done / 1048576.0) + " / " + fmt("%.0f", total / 1048576.0) + " MB");
            }));
            upMbps = u.ok ? u.mbps : -1;
            ui.post(() -> paintUp(u));

            // 4) 设备
            status("最后一趟：扫局域网设备…");
            final List<Net.Dev> found = scanLan(gw, me);
            devices = found;
            int stray = 0;
            for (Net.Dev x : found) if (!x.isGw && !x.isMe && "未知设备".equals(x.type)) stray++;
            strayCount = stray;
            ui.post(() -> paintDevices(found));

            lastCheckAt = System.currentTimeMillis();
            ui.post(() -> {
                paintScore();
                String sum = sumLine();
                saveHist("全面体检", sum);
                busy = false;
                btnRun.setEnabled(true);
                btnRun.setText("再测一次");
                status("");
                toast("检测完成");
            });
        });
    }

    private void startPulse() {
        ui.post(new Runnable() {
            @Override public void run() {
                ringSpeed.pulse();
                if (busy) ui.postDelayed(this, 120);
            }
        });
    }

    private void stopPulse() { ringSpeed.pulse(); }

    private void status(String s) { ui.post(() -> tvRunStatus.setText(s)); }

    // ==================== 各页绘制 ====================

    private void paintLatency(Net.PingStat g, Net.PingStat w) {
        double gv = g.recv == 0 ? -1 : g.avg();
        double wv = w.recv == 0 ? -1 : w.avg();
        tvGwVal.setText(gv < 0 ? "超时" : fmt("%.0f ms", gv));
        tvWanVal.setText(wv < 0 ? "超时" : fmt("%.0f ms", wv));
        bandGw.setRatio(gv < 0 ? 1 : Math.min(1, gv / 120.0));
        bandWan.setRatio(wv < 0 ? 1 : Math.min(1, wv / 300.0));

        double jit = Math.max(g.jitter(), w.jitter());
        double loss = Math.max(g.loss(), w.loss());
        tvJitter.setText(fmt("%.0f ms", jit));
        tvLossBig.setText(fmt("%.0f%%", loss));

        boolean firstBad = g.recv == 0 || g.loss() > 15 || g.avg() > 40 || g.jitter() > 60;
        boolean wanBad = w.loss() > 10 || w.avg() > 120 || w.jitter() > 80;

        if (firstBad) {
            tint(latVerdictBox, R.drawable.tint_red);
            tvLatVerdict.setTextColor(C_BAD);
            tvLatVerdict.setText("卡在手机和路由器之间");
            tvLatAdvice.setTextColor(0xFF7A3B36);
            tvLatAdvice.setText("这一跳是你自己能修的：\n1. 拿手机走到路由器 2 米内复测\n2. 路由器后台把 2.4G 信道改成最空的 1/6/11\n3. 能连 5G 就连 5G\n4. 关掉本 App 的电池限制再测一次");
            tvJitterState.setTextColor(C_BAD);
            tvJitterState.setText(jit > 60 ? "抖动大" : "");
        } else if (wanBad) {
            tint(latVerdictBox, R.drawable.tint_amber);
            tvLatVerdict.setTextColor(C_WARN);
            tvLatVerdict.setText("手机到路由器没问题，卡在出网这一段");
            tvLatAdvice.setTextColor(0xFF7A5410);
            tvLatAdvice.setText("这段你自己改不了，但能拿数据说话：\n1. 换个时段（晚 8–11 点）再测一次对比\n2. 点「工具 → 复制完整体检报告」发给房东\n3. 房东那条线多半是百兆老套餐或光猫老旧");
            tvJitterState.setTextColor(C_WARN);
            tvJitterState.setText(jit > 80 ? "抖动大" : "");
        } else {
            tint(latVerdictBox, R.drawable.tint_green);
            tvLatVerdict.setTextColor(C_OK);
            tvLatVerdict.setText("两段延迟都正常");
            tvLatAdvice.setTextColor(0xFF0B6B4C);
            tvLatAdvice.setText("延迟没问题，那卡就是带宽被占或套餐不够。去看「带宽」和「设备」两项。");
            tvJitterState.setTextColor(C_OK);
            tvJitterState.setText(jit <= 30 ? "稳定" : "");
        }
        tvLossState.setTextColor(loss > 5 ? C_BAD : C_OK);
        tvLossState.setText(loss > 5 ? "丢包" : "无丢包");

        tvLatDetail.setText("第一跳 " + gwText(g) + "\n出网    " + gwText(w));
        tvOvLat.setText(gv < 0 ? "超时" : fmt("%.0f", Math.max(gv, wv < 0 ? gv : wv)));
        tvOvLatState.setText(firstBad ? "第一跳偏高" : (wanBad ? "出网偏高" : "两段正常"));
        tvOvLatState.setTextColor(firstBad ? C_BAD : (wanBad ? C_WARN : C_OK));
        tvOvLoss.setText(fmt("%.0f", loss));
        tvOvLossState.setText(loss > 5 ? "有丢包" : "无丢包");
        tvOvLossState.setTextColor(loss > 5 ? C_BAD : C_OK);
    }

    private String gwText(Net.PingStat s) {
        if (s.recv == 0) return "全部超时";
        return fmt("平均 %.0fms  抖动 %.0fms  丢包 %.0f%%", s.avg(), s.jitter(), s.loss());
    }

    private void paintAps(List<ScanResult> rs, int[] cc, int cur, int sug) {
        chartCh.setData(cc, cur, sug);
        tvCurCh.setText(cur > 0 ? String.valueOf(cur) : "—");
        tvSugCh.setText(sug > 0 ? String.valueOf(sug) : "—");
        apList.removeAllViews();
        int show = Math.min(rs.size(), 14);
        for (int i = 0; i < show; i++) apList.addView(apRow(rs.get(i)));

        int curLoad = (cur >= 1 && cur <= 13) ? cc[cur - 1] : 0;
        if (sug > 0 && sug != cur) {
            tint(chVerdictBox, R.drawable.tint_amber);
            tvChVerdict.setTextColor(C_WARN);
            tvChVerdict.setText("信道 " + cur + " 上有 " + curLoad + " 个强信号，建议改到 " + sug);
            tvChAdvice.setTextColor(0xFF7A5410);
            tvChAdvice.setText("进路由器后台 → 无线设置 → 2.4G 信道 → 选 " + sug + " → 保存重启。\n改完回来再扫一次，看柱子有没有降下来。");
        } else if (cur > 13) {
            tint(chVerdictBox, R.drawable.tint_green);
            tvChVerdict.setTextColor(C_OK);
            tvChVerdict.setText("当前在 5G 频段，不挤");
            tvChAdvice.setTextColor(0xFF0B6B4C);
            tvChAdvice.setText("5G 本来就不拥堵，卡的话去看「带宽」和「设备」。");
        } else {
            tint(chVerdictBox, R.drawable.tint_green);
            tvChVerdict.setTextColor(C_OK);
            tvChVerdict.setText("当前信道 " + cur + " 已经是最空的");
            tvChAdvice.setTextColor(0xFF0B6B4C);
            tvChAdvice.setText("不用改信道了。信号格数少的话，换个位置或拉近路由器。");
        }
    }

    private void paintDown(Net.SpeedResult d) {
        if (d.ok) {
            ringSpeed.setValue(d.mbps, fmt("%.1f", d.mbps), "Mbps 下行");
            tvDownVal.setText(fmt("%.1f Mbps", d.mbps));
            bandDown.setRatio(Math.min(1, d.mbps / 60.0));
            tvCostVal.setText(fmt("%.1f 秒", d.ms / 1000.0));
            bandCost.setRatio(Math.min(1, d.ms / 15000.0));
            tvSpeedStatus.setText(fmt("下载 %.1f Mbps · 用时 %.1f 秒", d.mbps, d.ms / 1000.0));
            tint(speedVerdictBox, d.mbps < 8 ? R.drawable.tint_red : (d.mbps < 25 ? R.drawable.tint_amber : R.drawable.tint_green));
            tvSpeedVerdict.setTextColor(d.mbps < 8 ? C_BAD : (d.mbps < 25 ? C_WARN : C_OK));
            tvSpeedVerdict.setText(d.mbps < 8 ? "带宽严重不足" : (d.mbps < 25 ? "带宽偏低" : "带宽够用"));
            tvSpeedAdvice.setTextColor(d.mbps < 8 ? 0xFF7A3B36 : (d.mbps < 25 ? 0xFF7A5410 : 0xFF0B6B4C));
            tvSpeedAdvice.setText(d.mbps < 8
                    ? "10 Mbps 以下：1080p 都卡。先看是不是有人在下片/挂机，再拿数据找房东。"
                    : (d.mbps < 25 ? "25 Mbps 以下：看 1080p 勉强，多人同时用就卡。尽量错峰。"
                    : "够看 4K。卡的话不是带宽问题，去看「设备」那一项。"));
            tvOvDown.setText(fmt("%.1f", d.mbps));
            tvOvDownState.setText(d.mbps < 8 ? "严重不足" : (d.mbps < 25 ? "偏低" : "够用"));
            tvOvDownState.setTextColor(d.mbps < 8 ? C_BAD : (d.mbps < 25 ? C_WARN : C_OK));
        } else {
            ringSpeed.setValue(0, "—", "失败");
            tvSpeedStatus.setText("下载没测出来：" + Net.errHint(d.err));
            tvDownVal.setText("失败");
            tvOvDown.setText("—");
            tvOvDownState.setText("测不出");
            tvOvDownState.setTextColor(C_BAD);
        }
    }

    private void paintUp(Net.SpeedResult u) {
        if (u.ok) {
            tvUpVal.setText(fmt("%.1f Mbps", u.mbps));
            bandUp.setRatio(Math.min(1, u.mbps / 30.0));
            if (downMbps > 0) tvSpeedStatus.setText(fmt("下行 %.1f · 上行 %.1f Mbps", downMbps, u.mbps));
        } else {
            tvUpVal.setText("失败");
        }
    }

    private void paintDevices(List<Net.Dev> list) {
        devList.removeAllViews();
        int known = 0;
        for (Net.Dev d : list) if (!"未知设备".equals(d.type)) known++;
        tvDevTotal.setText(String.valueOf(list.size()));
        tvDevKnown.setText(String.valueOf(known));
        tvDevUnknown.setText(String.valueOf(strayCount));

        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                row.setLayoutParams(lp);
                devList.addView(row);
            }
            View card = devCard(list.get(i));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 1) cp.leftMargin = dp(8);
            card.setLayoutParams(cp);
            row.addView(card);
        }

        tvOvDev.setText(String.valueOf(list.size()));
        tvOvDevState.setText(strayCount == 0 ? "全部认识" : (strayCount + " 台陌生"));
        tvOvDevState.setTextColor(strayCount == 0 ? C_OK : C_BAD);

        if (strayCount == 0) {
            tint(devVerdictBox, R.drawable.tint_green);
            tvDevVerdict.setTextColor(C_OK);
            tvDevVerdict.setText("没有陌生设备");
            tvDevAdvice.setTextColor(0xFF0B6B4C);
            tvDevAdvice.setText("在线设备都认得出。如果还是卡，问题不在蹭网，去看「带宽」。");
        } else {
            tint(devVerdictBox, R.drawable.tint_amber);
            tvDevVerdict.setTextColor(C_WARN);
            tvDevVerdict.setText("有 " + strayCount + " 台认不出来的设备");
            tvDevAdvice.setTextColor(0xFF7A5410);
            tvDevAdvice.setText("点「工具 → 打开路由器后台」→ 找「已连接设备 / DHCP 客户端」→ 对上 IP 拉黑。\nAndroid 10 以后系统不让 App 读别人的 MAC，所以有些行看起来是「未知设备」，路由后台能看到完整信息。");
        }
    }

    private View devCard(Net.Dev d) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card);
        c.setPadding(dp(11), dp(11), dp(11), dp(11));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = new TextView(this);
        String code = Net.typeIcon(d.type);
        icon.setText(code);
        icon.setTextSize(10);
        icon.setTextColor(0xFFFFFFFF);
        icon.setTypeface(null, android.graphics.Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundResource(d.isGw ? R.drawable.dot_blue
                : ("未知设备".equals(d.type) ? R.drawable.dot_red : R.drawable.dot_green));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
        icon.setLayoutParams(ilp);
        top.addView(icon);

        TextView name = new TextView(this);
        name.setText(d.type);
        name.setTextSize(12);
        name.setTextColor(C_INK);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(7);
        name.setLayoutParams(nlp);
        top.addView(name);
        c.addView(top);

        TextView ip = new TextView(this);
        ip.setText(d.ip);
        ip.setTextSize(10);
        ip.setTypeface(android.graphics.Typeface.MONOSPACE);
        ip.setTextColor(C_DIM);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(6);
        ip.setLayoutParams(plp);
        c.addView(ip);

        String sub = !TextUtils.isEmpty(d.vendor) ? d.vendor
                : (!TextUtils.isEmpty(d.host) ? d.host : "厂商读不到");
        TextView vd = new TextView(this);
        vd.setText(sub);
        vd.setTextSize(10);
        vd.setTextColor(C_FAINT);
        vd.setSingleLine(true);
        vd.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(2);
        vd.setLayoutParams(vlp);
        c.addView(vd);
        return c;
    }

    private View apRow(ScanResult r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        row.setLayoutParams(lp);

        TextView nm = new TextView(this);
        nm.setText(TextUtils.isEmpty(r.SSID) ? "(隐藏)" : r.SSID);
        nm.setTextSize(11);
        nm.setTextColor(C_INK);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nm.setLayoutParams(nlp);
        row.addView(nm);

        int ch = freqToCh(r.frequency);
        TextView meta = new TextView(this);
        meta.setText((ch > 13 ? "5G" : "ch" + ch) + " · " + r.level + "dBm");
        meta.setTextSize(10);
        meta.setTypeface(android.graphics.Typeface.MONOSPACE);
        meta.setTextColor(r.level > -60 ? C_OK : (r.level > -75 ? C_SUB : C_FAINT));
        row.addView(meta);
        return row;
    }

    // ==================== 分数 ====================

    private void paintScore() {
        double gwAvg = statGw == null || statGw.recv == 0 ? 300 : statGw.avg();
        double wanAvg = statWan == null || statWan.recv == 0 ? 300 : statWan.avg();
        double avg = Math.max(gwAvg, wanAvg);
        double loss = Math.max(statGw == null ? 0 : statGw.loss(), statWan == null ? 0 : statWan.loss());

        int sLat = clampScore(avg, 20, 200);
        int sLoss = clampScore(loss, 0, 20);
        int sDown = downMbps < 0 ? 0 : clampScoreInv(downMbps, 5, 50);
        int sDev = strayCount == 0 ? 25 : (strayCount == 1 ? 17 : (strayCount == 2 ? 8 : 0));

        lastScore = sLat + sLoss + sDown + sDev;
        ringScore.setScore(lastScore);
        tvScoreBreak.setText("延迟 " + sLat + "/25 · 丢包 " + sLoss + "/25 · 带宽 " + sDown + "/25 · 设备 " + sDev + "/25");

        String tag, v, sub;
        int bg, fg;
        if (lastScore >= 75) {
            tag = "状态良好"; v = "网没问题"; bg = R.drawable.badge_soft; fg = 0xFF0E9F6E;
            sub = "四项都在正常范围。还卡就是具体服务或设备的事。";
        } else if (lastScore >= 50) {
            tag = "需要优化"; v = "能用但偏弱"; bg = R.drawable.tint_amber; fg = 0xFFB45309;
            sub = "有一项拖后腿，点下面四张卡看是哪一项。";
        } else {
            tag = "问题明显"; v = "有明显短板"; bg = R.drawable.tint_red; fg = 0xFFB3261E;
            sub = "按下面四张卡的结论逐条处理，优先看红色的那张。";
        }
        tvTag.setText(tag);
        tvTag.setTextColor(fg);
        tvTag.setBackgroundResource(bg);
        tvVerdict.setText(v);
        tvVerdictSub.setText(sub);
        lastReport = buildReport();
    }

    /** 越小越好：lo 分以下满分 25，hi 以上 0 分。 */
    private int clampScore(double v, double lo, double hi) {
        if (v <= lo) return 25;
        if (v >= hi) return 0;
        return (int) Math.round(25 * (hi - v) / (hi - lo));
    }

    /** 越大越好：lo 以下 0 分，hi 以上满分 25。 */
    private int clampScoreInv(double v, double lo, double hi) {
        if (v <= lo) return 0;
        if (v >= hi) return 25;
        return (int) Math.round(25 * (v - lo) / (hi - lo));
    }

    private String sumLine() {
        return fmt("总分 %d · 延迟 %s · 下行 %s · 设备 %d 台",
                lastScore,
                statWan == null || statWan.recv == 0 ? "超时" : fmt("%.0fms", statWan.avg()),
                downMbps < 0 ? "失败" : fmt("%.1fMbps", downMbps),
                devices == null ? 0 : devices.size());
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("【出租屋 WiFi 体检报告】\n");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date())).append("\n\n");
        sb.append("WiFi：").append(wifi.ssid).append("  ").append(wifi.band)
                .append("  ").append(wifi.rssi).append(" dBm\n");
        sb.append("网关：").append(gateway).append("   本机：").append(localIp).append("\n");
        sb.append("总分：").append(lastScore).append("/100\n\n");

        sb.append("—— 延迟 ——\n");
        sb.append("第一跳（手机→路由器）：").append(statGw == null ? "无数据" : gwText(statGw)).append("\n");
        sb.append("出网（手机→外网）：").append(statWan == null ? "无数据" : gwText(statWan)).append("\n");
        sb.append(tvLatVerdict.getText()).append("\n\n");

        sb.append("—— 信道 ——\n");
        sb.append("当前信道：").append(curChannel > 0 ? curChannel : "未知")
                .append("   建议：").append(sugChannel > 0 ? sugChannel : "—")
                .append("   周边 AP：").append(apCount).append(" 个\n");
        sb.append(tvChVerdict.getText()).append("\n\n");

        sb.append("—— 带宽 ——\n");
        sb.append("下行：").append(downMbps < 0 ? "失败" : fmt("%.1f Mbps", downMbps)).append("\n");
        sb.append("上行：").append(upMbps < 0 ? "失败" : fmt("%.1f Mbps", upMbps)).append("\n");
        sb.append(tvSpeedVerdict.getText()).append("\n\n");

        sb.append("—— 设备 ——\n");
        sb.append("在线 ").append(devices == null ? 0 : devices.size()).append(" 台，陌生 ").append(strayCount).append(" 台\n");
        if (devices != null) {
            for (Net.Dev d : devices) {
                sb.append("  ").append(d.ip).append("  ").append(d.type);
                if (!TextUtils.isEmpty(d.vendor)) sb.append("  ").append(d.vendor);
                sb.append("\n");
            }
        }
        sb.append(tvDevVerdict.getText()).append("\n\n");
        sb.append("—— 一句话 ——\n");
        sb.append("满格还慢=带宽问题；格少还慢=信号问题。\n");
        return sb.toString();
    }

    // ==================== 历史 ====================

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

    private void saveHist(String kind, String sum) {
        String rep = lastReport == null ? "" : lastReport.replace("\n", "\\n").replace("|", "/");
        String one = System.currentTimeMillis() + "|" + kind + "|" + sum.replace("|", "/") + "|" + rep;
        String all = prefs().getString(K_HIST, "");
        List<String> items = new ArrayList<>();
        if (!all.isEmpty()) for (String s : all.split(SEP)) if (!s.isEmpty()) items.add(s);
        items.add(0, one);
        while (items.size() > HIST_MAX) items.remove(items.size() - 1);
        prefs().edit().putString(K_HIST, TextUtils.join(SEP, items)).apply();
        loadHistory();
    }

    private void loadHistory() {
        histList.removeAllViews();
        String all = prefs().getString(K_HIST, "");
        List<String> items = new ArrayList<>();
        if (!all.isEmpty()) for (String s : all.split(SEP)) if (!s.isEmpty()) items.add(s);
        tvHistEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        SimpleDateFormat df = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        for (String it : items) {
            String[] p = it.split("\\|", 4);
            if (p.length < 3) continue;
            long t;
            try { t = Long.parseLong(p[0]); } catch (Exception e) { t = 0; }
            histList.addView(histRow(df.format(new Date(t)), p[1], p[2],
                    p.length > 3 ? p[3].replace("\\n", "\n") : ""));
        }
    }

    private View histRow(String time, String kind, String sum, String full) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card);
        c.setPadding(dp(13), dp(12), dp(13), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView k = new TextView(this);
        k.setText(kind);
        k.setTextSize(13);
        k.setTextColor(C_INK);
        k.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        k.setLayoutParams(klp);
        top.addView(k);

        TextView tm = new TextView(this);
        tm.setText(time);
        tm.setTextSize(10);
        tm.setTypeface(android.graphics.Typeface.MONOSPACE);
        tm.setTextColor(C_FAINT);
        top.addView(tm);
        c.addView(top);

        TextView sm = new TextView(this);
        sm.setText(sum);
        sm.setTextSize(11);
        sm.setTextColor(C_SUB);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(5);
        sm.setLayoutParams(slp);
        c.addView(sm);

        TextView detail = new TextView(this);
        detail.setText(full);
        detail.setTextSize(10);
        detail.setTypeface(android.graphics.Typeface.MONOSPACE);
        detail.setTextColor(C_DIM);
        detail.setLineSpacing(dp(2), 1f);
        detail.setVisibility(View.GONE);
        detail.setTextIsSelectable(true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(8);
        detail.setLayoutParams(dlp);
        c.addView(detail);

        c.setOnClickListener(v -> detail.setVisibility(
                detail.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return c;
    }

    // ==================== 复制 ====================

    private void copy() {
        if (TextUtils.isEmpty(lastReport)) { toast("先跑一次检测"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("wifi-report", lastReport));
        toast("报告已复制，直接发给房东");
    }

    // ==================== 工具方法 ====================

    private List<Net.Dev> scanLan(String gw, String me) {
        final List<Net.Dev> out = new ArrayList<>();
        if (TextUtils.isEmpty(gw)) return out;
        String base = Net.subnetBase(gw);
        if (TextUtils.isEmpty(base)) return out;

        final List<String> live = Collections.synchronizedList(new ArrayList<>());
        final int[] done = {0};
        ExecutorService p = Executors.newFixedThreadPool(40);
        for (int i = 1; i <= 254; i++) {
            final String ip = base + "." + i;
            p.execute(() -> {
                if (Net.pingOnce(ip)) live.add(ip);
                done[0]++;
                if (done[0] % 25 == 0) status("已扫 " + done[0] + "/254…");
            });
        }
        p.shutdown();
        try { p.awaitTermination(40, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}

        java.util.Map<String, String> arp = Net.readArp();
        for (String ip : new ArrayList<>(live)) {
            if (!arp.containsKey(ip)) arp.put(ip, "");
        }
        List<String> ips = new ArrayList<>(arp.keySet());
        Collections.sort(ips, (a, b) -> Integer.compare(Net.ipInt(a), Net.ipInt(b)));

        ExecutorService idp = Executors.newFixedThreadPool(12);
        for (String ip : ips) {
            idp.execute(() -> {
                Net.Dev d = new Net.Dev();
                d.ip = ip;
                d.mac = arp.get(ip) == null ? "" : arp.get(ip);
                d.isGw = ip.equals(gw);
                d.isMe = ip.equals(me);
                Set<Integer> open = Net.probePorts(ip);
                d.ttl = Net.pingTtl(ip);
                d.host = Net.resolveHost(ip);
                d.vendor = Net.ouiVendor(d.mac);
                d.type = Net.inferType(d, open);
                if (open != null && !open.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Integer x : open) { if (sb.length() > 0) sb.append(","); sb.append(x); }
                    d.ports = sb.toString();
                    if (open.contains(80)) {
                        String t = Net.httpTitle(ip, 80);
                        if (!TextUtils.isEmpty(t) && TextUtils.isEmpty(d.vendor)) d.vendor = t;
                    }
                }
                out.add(d);
            });
        }
        idp.shutdown();
        try { idp.awaitTermination(35, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}

        Collections.sort(out, new Comparator<Net.Dev>() {
            @Override public int compare(Net.Dev a, Net.Dev b) {
                if (a.isGw != b.isGw) return a.isGw ? -1 : 1;
                if (a.isMe != b.isMe) return a.isMe ? -1 : 1;
                boolean au = "未知设备".equals(a.type), bu = "未知设备".equals(b.type);
                if (au != bu) return au ? 1 : -1;
                return Integer.compare(Net.ipInt(a.ip), Net.ipInt(b.ip));
            }
        });
        return out;
    }

    private static int freqToCh(int freq) {
        if (freq >= 2412 && freq <= 2484) return (freq - 2407) / 5;
        if (freq >= 5000) return 100 + (freq - 5000) / 5;
        return -1;
    }

    private static int chFromFreq(int freq) {
        if (freq >= 2412 && freq <= 2484) return (freq - 2407) / 5;
        if (freq >= 5000) return 100 + (freq - 5000) / 5;
        return -1;
    }

    private static int bestChannel(int[] cc) {
        int best = -1, bestVal = Integer.MAX_VALUE;
        int[] cand = {1, 6, 11, 3, 8, 13};
        for (int c : cand) {
            if (c < 1 || c > 13) continue;
            int v = cc[c - 1];
            int lv = c > 1 ? cc[c - 2] : 0;
            int rv = c < 13 ? cc[c] : 0;
            int score = v * 3 + lv + rv;
            if (score < bestVal) { bestVal = score; best = c; }
        }
        return best;
    }

    private void tint(View v, int drawable) { v.setBackgroundResource(drawable); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static String fmt(String f, Object... a) { return String.format(Locale.US, f, a); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == REQ_SCAN) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) toast("已授权，可以扫了");
            else toast("不给定位权限，系统不让扫周边 WiFi");
        }
    }

    private boolean needScanPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED;
    }
}
