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
    private TextView tvOvDownUnit;
    private Button btnRun;
    private TextView tvRunStatus, tvScoreBreak;
    private android.widget.ProgressBar pbRun;
    private View runSteps;
    private TextView st1, st2, st3, st4;

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
    private TextView tvDevScanState, tvDevPinged;
    private android.widget.ProgressBar pbDev;
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

    private SignalChart chartSignal;
    private TextView tvSignalNow, tvSignalHint, tvSpeedTier;
    private TextView unitMbps, unitMBs;
    private boolean showMBs = false;
    private static final String K_UNIT = "unit_mbs";
    private String tierLabel = "";
    private final Handler sigHandler = new Handler(Looper.getMainLooper());
    private boolean sigSampling = false;
    private Runnable sigTask;

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
        startSignalSampling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSignalSampling();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSignalSampling();
        pool.shutdownNow();
    }

    // ==================== 信号采样 ====================

    private void startSignalSampling() {
        if (sigSampling) return;
        sigSampling = true;
        sigTask = new Runnable() {
            @Override public void run() {
                if (!sigSampling) return;
                Net.WifiBrief w = Net.readWifi(MainActivity.this);
                if (w.rssi < 0) {
                    chartSignal.push(w.rssi);
                    tvSignalNow.setText(w.rssi + " dBm");
                    int sp = chartSignal.spread();
                    if (sp >= 15) {
                        tvSignalHint.setTextColor(0xFFB45309);
                        tvSignalHint.setText("信号在 " + sp + " dBm 内来回跳，说明连接不稳 —— 换位置或换信道");
                    } else if (sp > 0) {
                        tvSignalHint.setTextColor(0xFF95A0B0);
                        tvSignalHint.setText("采样 " + chartSignal.size() + " 次，波动 " + sp + " dBm，算稳定");
                    }
                }
                sigHandler.postDelayed(this, 1000);
            }
        };
        sigHandler.post(sigTask);
    }

    private void stopSignalSampling() {
        sigSampling = false;
        if (sigTask != null) sigHandler.removeCallbacks(sigTask);
        sigTask = null;
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
        tvOvDownUnit = findViewById(R.id.tvOvDownUnit);
        tvOvDownState = findViewById(R.id.tvOvDownState);
        tvOvDev = findViewById(R.id.tvOvDev);
        tvOvDevState = findViewById(R.id.tvOvDevState);
        btnRun = findViewById(R.id.btnRun);
        tvRunStatus = findViewById(R.id.tvRunStatus);
        tvScoreBreak = findViewById(R.id.tvScoreBreak);
        pbRun = findViewById(R.id.pbRun);
        runSteps = findViewById(R.id.runSteps);
        st1 = findViewById(R.id.st1);
        st2 = findViewById(R.id.st2);
        st3 = findViewById(R.id.st3);
        st4 = findViewById(R.id.st4);

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
        tvSpeedTier = findViewById(R.id.tvSpeedTier);

        // Mbps / MB/s 切换
        unitMbps = findViewById(R.id.unitMbps);
        unitMBs = findViewById(R.id.unitMBs);
        unitMbps.setOnClickListener(v -> setUnit(false));
        unitMBs.setOnClickListener(v -> setUnit(true));
        showMBs = prefs().getBoolean(K_UNIT, false);
        paintUnitSwitch();
        // 点总览上的下行数字也能切单位，比找小开关更快
        tvOvDown.setOnClickListener(v -> setUnit(!showMBs));
        chartSignal = findViewById(R.id.chartSignal);
        tvSignalNow = findViewById(R.id.tvSignalNow);
        tvSignalHint = findViewById(R.id.tvSignalHint);
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
        tvDevScanState = findViewById(R.id.tvDevScanState);
        pbDev = findViewById(R.id.pbDev);
        tvDevPinged = findViewById(R.id.tvDevPinged);
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
            // 三级兜底：不少国产 ROM 没有 PRIVATE_DNS_SETTINGS 这个页面
            boolean ok = false;
            try {
                startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));
                ok = true;
            } catch (Throwable ignored) {}
            if (!ok) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    ok = true;
                } catch (Throwable ignored) {}
            }
            if (ok) {
                toast("找「私人 DNS / 私有 DNS」，填 dns.alidns.com 或 dns.qq.com");
            } else {
                toast("请手动：设置 → 网络和互联网 → 私人 DNS → 填 dns.alidns.com");
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
        if (s == 2) refreshSpeedPage();
    }

    /**
     * 进带宽页时，用内存里的真实结果重画一遍。
     * 圆环曾经被「上传阶段」借用过，离开页面时可能残留上传值，
     * 这里做一次兜底刷新，保证看到的一定是下行结果。
     */
    private void refreshSpeedPage() {
        if (downMbps > 0) {
            ringSpeed.setValue(downMbps, fmt("%.1f", spd(downMbps)), spdUnit());
            tvDownVal.setText(spdText(downMbps));
            bandDown.setRatio(Math.min(1, downMbps / 100.0));
            if (downMs > 0) {
                tvCostVal.setText(fmt("%.1f 秒", downMs / 1000.0));
                bandCost.setRatio(Math.min(1, downMs / 15000.0));
            }
            tvSpeedStatus.setText(upMbps > 0
                    ? fmt("下行 %s · 上行 %s", spdText(downMbps), spdText(upMbps))
                    : fmt("下载 %s%s", spdText(downMbps),
                        TextUtils.isEmpty(Net.lastMirror) ? "" : " · 源 " + Net.lastMirror));
        }
        if (upMbps > 0) {
            tvUpVal.setText(spdText(upMbps));
            bandUp.setRatio(Math.min(1, upMbps / 30.0));
        }
        if (!TextUtils.isEmpty(tierLabel)) tvSpeedTier.setText("分档测速 · " + tierLabel);
        paintUnitSwitch();
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
        applyHead();
    }

    /** 只碰 UI，必须在主线程调用。 */
    private void applyHead() {
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
        pbRun.setVisibility(View.VISIBLE);
        pbRun.setProgress(0);
        runSteps.setVisibility(View.VISIBLE);
        resetSteps();

        pool.execute(() -> {
            // 后台线程只碰数据，界面一律 post 回主线程
            final Net.WifiBrief wb = Net.readWifi(this);
            final String gw = Net.getGatewayIp(this);
            final String me = Net.getLocalIp(this);
            wifi = wb;
            gateway = gw;
            localIp = me;
            ui.post(this::applyHead);

            // 1) 延迟 0→25
            progress(2, "第 1/4 步：测第一跳延迟…", 1);
            final Net.PingStat g = TextUtils.isEmpty(gw) ? new Net.PingStat()
                    : Net.pingHost(gw, 8, s -> {
                        ui.post(() -> {
                            tvGwVal.setText(fmt("%.0f ms", s.avg()));
                            tvLossBig.setText(fmt("%.0f%%", s.loss()));
                            bandGw.setRatio(Math.min(1, s.avg() / 120.0));
                            tvOvLat.setText(fmt("%.0f", s.avg()));
                            pbRun.setProgress(2 + s.recv);
                        });
                    });
            progress(12, "测出网延迟…", 1);
            final Net.PingStat w = Net.pingHost("223.5.5.5", 8, s -> {
                ui.post(() -> {
                    tvWanVal.setText(fmt("%.0f ms", s.avg()));
                    bandWan.setRatio(Math.min(1, s.avg() / 300.0));
                    pbRun.setProgress(12 + s.recv);
                });
            });
            statGw = g;
            statWan = w;
            ui.post(() -> paintLatency(g, w));
            stepDone(1);

            // 2) 信道 25→50
            progress(26, "第 2/4 步：扫周边 WiFi…", 2);
            final int[] cc = new int[13];
            final int[] g5 = new int[4];
            final int[] r = new int[]{0, -1, -1, -1, -1};
            final List<ScanResult> found = new ArrayList<>();
            try {
                if (wm != null) {
                    wm.startScan();
                    for (int k = 0; k < 12; k++) {
                        Thread.sleep(200);
                        final int pr = 26 + k * 2;
                        ui.post(() -> pbRun.setProgress(pr));
                    }
                    List<ScanResult> rs = wm.getScanResults();
                    if (rs != null) {
                        found.addAll(rs);
                        Collections.sort(found, (a, b2) -> Integer.compare(b2.level, a.level));
                        for (ScanResult sr : found) {
                            int ch = freqToCh(sr.frequency);
                            if (ch >= 1 && ch <= 13 && sr.level > -85) cc[ch - 1]++;
                            int gi = freqTo5gGroup(sr.frequency);
                            if (gi >= 0 && sr.level > -85) g5[gi]++;
                            r[0]++;
                        }
                        int cur = chFromFreq(wb.freq);
                        int gcur = freqTo5gGroup(wb.freq);
                        int sug;
                        int gsug = -1;
                        if (gcur >= 0) {
                            sug = -1;
                            gsug = emptiest5g(g5);
                        } else {
                            sug = bestChannel(cc);
                        }
                        r[1] = cur;
                        r[2] = sug;
                        r[3] = gcur;
                        r[4] = gsug;
                        final List<ScanResult> copy = new ArrayList<>(found);
                        final int fcur = cur, fsug = sug, fgcur = gcur, fgsug = gsug;
                        ui.post(() -> paintAps(copy, cc, g5, fcur, fsug, fgcur, fgsug));
                    }
                }
            } catch (Exception ignored) {
            }
            chCounts = cc;
            apCount = r[0];
            curChannel = r[1];
            sugChannel = r[2];
            ui.post(() -> pbRun.setProgress(50));
            stepDone(2);

            // 3) 带宽 50→80
            progress(52, "第 3/4 步：测下载…", 3);
            startPulse();
            Net.SpeedResult d = Net.measureDownloadTiered((done, total, mbps) -> ui.post(() -> {
                ringSpeed.setRunning(true, mbps, fmt("%.1f", spd(mbps)));
                tvSpeedStatus.setText("已下载 " + fmt("%.1f", done / 1048576.0) + " / " + fmt("%.0f", total / 1048576.0) + " MB");
                pbRun.setProgress((int) Math.min(72, 52 + 20 * done / Math.max(1, total)));
            }), (idx, bytes) -> ui.post(() -> {
                String lbl;
                if (bytes <= 1000000) lbl = "试水 1 MB";
                else if (bytes <= 8000000) lbl = "1 MB 够快，加码到 8 MB";
                else lbl = "8 MB 还快，加码到 20 MB";
                tvSpeedTier.setText("分档测速 · " + lbl);
                tierLabel = lbl;
                pbRun.setProgress(52 + idx * 6);
            }));
            stopPulse();
            downMbps = d.ok ? d.mbps : -1;
            downMs = d.ok ? d.ms : -1;
            ui.post(() -> paintDown(d));
            ui.post(() -> pbRun.setProgress(74));

            progress(76, "测上传…", 3);
            Net.SpeedResult u = Net.measureUpload((done, total, mbps) -> ui.post(() -> {
                ringSpeed.setRunning(true, mbps, fmt("%.1f", spd(mbps)));
                tvSpeedStatus.setText("已上传 " + fmt("%.1f", done / 1048576.0) + " / " + fmt("%.0f", total / 1048576.0) + " MB");
                pbRun.setProgress((int) Math.min(80, 76 + 4 * done / Math.max(1, total)));
            }));
            upMbps = u.ok ? u.mbps : -1;
            ui.post(() -> {
                paintUp(u);
                // 上传阶段复用了同一个圆环，结束后必须把环还原成「下行」的值，
                // 否则诊断页带宽圆环会一直停在上传速度上（bug: 要手动切单位才会变回正确值）
                ui.postDelayed(this::restoreDownRing, 60);
            });
            stepDone(3);

            // 4) 设备 80→100
            progress(82, "最后一趟：扫局域网设备…", 4);
            final List<Net.Dev> devs = scanLan(gw, me);
            devices = devs;
            int stray = 0;
            for (Net.Dev x : devs) if (!x.isGw && !x.isMe && "未知设备".equals(x.type)) stray++;
            strayCount = stray;
            ui.post(() -> paintDevices(devs));
            stepDone(4);
            ui.post(() -> pbRun.setProgress(100));

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

    // ---- 进度条与分步指示 ----

    private void progress(int pct, String s, int step) {
        ui.post(() -> {
            pbRun.setProgress(pct);
            tvRunStatus.setText(s);
            highlightStep(step);
        });
    }

    private void resetSteps() {
        for (TextView t : new TextView[]{st1, st2, st3, st4}) {
            t.setTextColor(0xFFB0B8C4);
            t.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void highlightStep(int idx) {
        TextView[] all = {st1, st2, st3, st4};
        for (int i = 0; i < 4; i++) {
            boolean done = i + 1 < idx;
            boolean now = i + 1 == idx;
            all[i].setTextColor(done ? C_OK : (now ? C_INK : 0xFFB0B8C4));
            all[i].setTypeface(null, (done || now) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void stepDone(int idx) {
        ui.post(() -> {
            TextView[] all = {st1, st2, st3, st4};
            all[idx - 1].setTextColor(C_OK);
            all[idx - 1].setTypeface(null, android.graphics.Typeface.BOLD);
        });
    }

    private final Runnable pulseTask = new Runnable() {
        @Override public void run() {
            ringSpeed.pulse();
            if (busy) ui.postDelayed(this, 120);
        }
    };

    private void startPulse() { ui.post(pulseTask); }

    /** 真正停掉脉冲，避免它一直空转。 */
    private void stopPulse() {
        ui.removeCallbacks(pulseTask);
        ringSpeed.pulse();
    }

    /** 上传阶段借用了测速圆环，测完必须把环还原成下行值。 */
    private void restoreDownRing() {
        if (downMbps > 0) {
            ringSpeed.setValue(downMbps, fmt("%.1f", spd(downMbps)), spdUnit());
            tvSpeedStatus.setText(fmt("下行 %s · 上行 %s", spdText(downMbps),
                    upMbps > 0 ? spdText(upMbps) : "失败"));
        } else {
            ringSpeed.setValue(0, "—", "失败");
        }
    }

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
            tvLatAdvice.setText("这段你自己改不了，但能拿数据说话：\n1. 换个时段（晚 8–11 点）再测一次对比\n2. 点「工具 → 复制完整体检报告」保存下来对比\n3. 上游线路多半是百兆老套餐或光猫老旧");
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

    private void paintAps(List<ScanResult> rs, int[] cc, int[] g5, int cur, int sug, int gcur, int gsug) {
        apList.removeAllViews();
        int show = Math.min(rs.size(), 14);
        for (int i = 0; i < show; i++) apList.addView(apRow(rs.get(i)));

        boolean on5g = gcur >= 0;
        if (on5g) {
            chartCh.setData5g(g5, G5_LABELS, gcur, gsug);
            tvCurCh.setText("5G 组 " + (gcur + 1));
            tvSugCh.setText(gsug >= 0 ? "组 " + (gsug + 1) : "—");
            int load = g5[gcur];
            if (gsug >= 0 && gsug != gcur) {
                tint(chVerdictBox, R.drawable.tint_amber);
                tvChVerdict.setTextColor(C_WARN);
                tvChVerdict.setText("你在 5G " + G5_LABELS[gcur] + "，这一组有 " + load + " 个强信号，" + G5_LABELS[gsug] + " 更空");
                tvChAdvice.setTextColor(0xFF7A5410);
                tvChAdvice.setText("进路由器后台 → 无线设置 → 5G 信道 → 选 " + G5_LABELS[gsug] + " 里的一个 → 保存重启。\n5G 组之间隔得远，同组内的信道（如 36/40/44/48）其实在争同一段频谱。");
            } else {
                tint(chVerdictBox, R.drawable.tint_green);
                tvChVerdict.setTextColor(C_OK);
                tvChVerdict.setText("你在 5G " + G5_LABELS[gcur] + "，这一组最空");
                tvChAdvice.setTextColor(0xFF0B6B4C);
                tvChAdvice.setText("5G 不用改信道了。卡的话去看「带宽」和「设备」两项。");
            }
            return;
        }

        chartCh.setData(cc, cur, sug);
        tvCurCh.setText(cur > 0 ? String.valueOf(cur) : "—");
        tvSugCh.setText(sug > 0 ? String.valueOf(sug) : "—");

        int curLoad = (cur >= 1 && cur <= 13) ? cc[cur - 1] : 0;
        if (sug > 0 && sug != cur) {
            tint(chVerdictBox, R.drawable.tint_amber);
            tvChVerdict.setTextColor(C_WARN);
            tvChVerdict.setText("信道 " + cur + " 上有 " + curLoad + " 个强信号，建议改到 " + sug);
            tvChAdvice.setTextColor(0xFF7A5410);
            tvChAdvice.setText("进路由器后台 → 无线设置 → 2.4G 信道 → 选 " + sug + " → 保存重启。\n改完回来再扫一次，看柱子有没有降下来。");
        } else {
            tint(chVerdictBox, R.drawable.tint_green);
            tvChVerdict.setTextColor(C_OK);
            tvChVerdict.setText("当前信道 " + cur + " 已经是最空的");
            tvChAdvice.setTextColor(0xFF0B6B4C);
            tvChAdvice.setText("不用改信道了。信号格数少的话，换个位置或拉近路由器。");
        }
    }

    /** 5G 信道分四组：低频段 / 中频段 / 高频段 / 最高频段。同组内信道互相干扰。 */
    private static final String[] G5_LABELS = {"36-48", "52-64", "100-144", "149-165"};

    private static int freqTo5gGroup(int f) {
        if (f < 4900) return -1;
        if (f >= 5170 && f <= 5250) return 0;   // ch36-48
        if (f >= 5260 && f <= 5340) return 1;   // ch52-64
        if (f >= 5500 && f <= 5720) return 2;   // ch100-144
        if (f >= 5735 && f <= 5835) return 3;   // ch149-165
        return -1;
    }

    private static int emptiest5g(int[] g5) {
        int best = -1, bv = Integer.MAX_VALUE;
        for (int i = 0; i < g5.length; i++) {
            if (g5[i] < bv) { bv = g5[i]; best = i; }
        }
        return best;
    }

    private void paintDown(Net.SpeedResult d) {
        if (d.ok) {
            ringSpeed.setValue(d.mbps, fmt("%.1f", spd(d.mbps)), spdUnit());
            tvDownVal.setText(spdText(d.mbps));
            bandDown.setRatio(Math.min(1, d.mbps / 100.0));
            tvCostVal.setText(fmt("%.1f 秒", d.ms / 1000.0));
            bandCost.setRatio(Math.min(1, d.ms / 15000.0));
            tvSpeedStatus.setText(fmt("下载 %s · 用时 %.1f 秒 · 源 %s", spdText(d.mbps), d.ms / 1000.0,
                    TextUtils.isEmpty(Net.lastMirror) ? "—" : Net.lastMirror));
            tint(speedVerdictBox, d.mbps < 20 ? R.drawable.tint_red : (d.mbps < 50 ? R.drawable.tint_amber : R.drawable.tint_green));
            tvSpeedVerdict.setTextColor(d.mbps < 20 ? C_BAD : (d.mbps < 50 ? C_WARN : C_OK));
            tvSpeedVerdict.setText(d.mbps < 20 ? "带宽严重不足" : (d.mbps < 50 ? "带宽偏低" : "带宽够用"));
            tvSpeedAdvice.setTextColor(d.mbps < 20 ? 0xFF7A3B36 : (d.mbps < 50 ? 0xFF7A5410 : 0xFF0B6B4C));
            tvSpeedAdvice.setText(d.mbps < 20
                    ? "20 Mbps 以下：1080p 都卡。先看是不是有人在下片/挂机，再拿数据判断上游带宽。"
                    : (d.mbps < 50 ? "50 Mbps 以下：单看 1080p 够，多人同时用就卡，尽量错峰。"
                    : "够看 4K 还有余。卡的话不是带宽问题，去看「设备」那一项。"));
            tvOvDown.setText(fmt("%.1f", spd(d.mbps)));
            tvOvDownState.setText(d.mbps < 20 ? "严重不足" : (d.mbps < 50 ? "偏低" : "够用"));
            tvOvDownState.setTextColor(d.mbps < 20 ? C_BAD : (d.mbps < 50 ? C_WARN : C_OK));
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
            tvUpVal.setText(spdText(u.mbps));
            bandUp.setRatio(Math.min(1, u.mbps / 30.0));
            if (downMbps > 0) tvSpeedStatus.setText(fmt("下行 %s · 上行 %s", spdText(downMbps), spdText(u.mbps)));
        } else {
            tvUpVal.setText("失败");
        }
    }

    // ---- 速度单位换算 ----

    private void setUnit(boolean mbs) {
        showMBs = mbs;
        prefs().edit().putBoolean(K_UNIT, mbs).apply();
        paintUnitSwitch();
        // 立即把已有数字换算刷新
        if (downMbps > 0) {
            ringSpeed.setValue(downMbps, fmt("%.1f", spd(downMbps)), spdUnit());
            tvDownVal.setText(spdText(downMbps));
            tvOvDown.setText(fmt("%.1f", spd(downMbps)));
            if (upMbps > 0) tvSpeedStatus.setText(fmt("下行 %s · 上行 %s", spdText(downMbps), spdText(upMbps)));
        }
        if (upMbps > 0) tvUpVal.setText(spdText(upMbps));
    }

    private void paintUnitSwitch() {
        if (tvOvDownUnit != null) tvOvDownUnit.setText(spdUnit());
        if (unitMbps == null || unitMBs == null) return;
        unitMbps.setBackgroundResource(showMBs ? 0 : R.drawable.seg_on);
        unitMbps.setTextColor(showMBs ? C_SUB : C_INK);
        unitMbps.setTypeface(null, showMBs ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        unitMBs.setBackgroundResource(showMBs ? R.drawable.seg_on : 0);
        unitMBs.setTextColor(showMBs ? C_INK : C_SUB);
        unitMBs.setTypeface(null, showMBs ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /** Mbps → 当前单位下的数值。 */
    private double spd(double mbps) { return showMBs ? mbps / 8.0 : mbps; }

    private String spdUnit() { return showMBs ? "MB/s" : "Mbps"; }

    private String spdText(double mbps) { return fmt("%.1f %s", spd(mbps), spdUnit()); }

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
        if (list.isEmpty()) {
            tvOvDevState.setText("一台都没扫到");
            tvOvDevState.setTextColor(C_BAD);
        } else if (strayCount == 0) {
            tvOvDevState.setText("全部认识");
            tvOvDevState.setTextColor(C_OK);
        } else {
            tvOvDevState.setText(strayCount + " 台陌生");
            tvOvDevState.setTextColor(C_BAD);
        }

        pbDev.setVisibility(View.GONE);
        tvDevPinged.setText("");

        if (list.isEmpty()) {
            tvDevScanState.setText("扫描完成，但一台都没扫到");
            tvDevScanState.setTextColor(C_BAD);
            tint(devVerdictBox, R.drawable.tint_red);
            tvDevVerdict.setTextColor(C_BAD);
            tvDevVerdict.setText("局域网里一台设备都没扫到");
            tvDevAdvice.setTextColor(0xFF7A3B36);
            tvDevAdvice.setText("不是「全部认识」，是真的没扫到。常见原因：\n1. 手机没连 WiFi，或连的是访客网络（AP 隔离，设备之间互相看不见）\n2. 路由器开了「AP 隔离 / 客户端隔离」\n3. 网关地址读不到（先回总览看头部有没有显示网关）\n先试：总览页看网关是不是 192.168.x.1 这类地址，再去路由器后台关掉「AP 隔离」。");
        } else if (strayCount == 0) {
            tvDevScanState.setText("扫描完成 · " + list.size() + " 台在线");
            tvDevScanState.setTextColor(C_OK);
            tint(devVerdictBox, R.drawable.tint_green);
            tvDevVerdict.setTextColor(C_OK);
            tvDevVerdict.setText("没有陌生设备");
            tvDevAdvice.setTextColor(0xFF0B6B4C);
            tvDevAdvice.setText("在线设备都认得出。如果还是卡，问题不在蹭网，去看「带宽」。");
        } else {
            tvDevScanState.setText("扫描完成 · " + list.size() + " 台在线，" + strayCount + " 台陌生");
            tvDevScanState.setTextColor(C_WARN);
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

        String sub = Net.devTag(d);
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

        // 判定依据：告诉用户「凭什么说它是 XX」，认不出时也说明为什么
        if (!TextUtils.isEmpty(d.reason)) {
            TextView rs = new TextView(this);
            rs.setText(d.reason);
            rs.setTextSize(9);
            rs.setTextColor(C_DIM);
            rs.setMaxLines(2);
            rs.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(3);
            rs.setLayoutParams(rlp);
            c.addView(rs);
        }

        // 疑似占用带宽：低/中/高 + 推断依据（不给假 Mbps）
        if (!d.isGw && !d.isMe) {
            int col = d.suspect >= 2 ? C_BAD : (d.suspect >= 1 ? C_WARN : C_OK);
            TextView sp = new TextView(this);
            sp.setText(Net.suspectText(d.suspect) + " · " + d.suspectWhy);
            sp.setTextSize(9);
            sp.setTextColor(col);
            sp.setSingleLine(true);
            sp.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(4);
            sp.setLayoutParams(slp);
            c.addView(sp);
        }
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
        int sDown = downMbps < 0 ? 0 : clampScoreInv(downMbps, 10, 100);
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
                downMbps < 0 ? "失败" : spdText(downMbps),
                devices == null ? 0 : devices.size());
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("【网络体检报告】\n");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date())).append("\n\n");
        sb.append("WiFi：").append(wifi.ssid).append("  ").append(wifi.band)
                .append("  ").append(wifi.rssi).append(" dBm\n");
        sb.append("网关：").append(gateway).append("   本机：").append(localIp).append("\n");
        sb.append("总分：").append(lastScore).append("/100\n\n");

        sb.append("—— 信号 ——\n");
        sb.append("当前 ").append(wifi.rssi).append(" dBm");
        if (chartSignal != null && chartSignal.size() >= 2) {
            sb.append("，采样 ").append(chartSignal.size()).append(" 次，波动 ").append(chartSignal.spread()).append(" dBm");
        }
        sb.append("\n\n");

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
        sb.append("下行：").append(downMbps < 0 ? "失败" : spdText(downMbps)).append("\n");
        sb.append("上行：").append(upMbps < 0 ? "失败" : spdText(upMbps)).append("\n");
        if (!TextUtils.isEmpty(tierLabel)) sb.append("档位：").append(tierLabel).append("\n");
        sb.append(tvSpeedVerdict.getText()).append("\n\n");

        sb.append("—— 设备 ——\n");
        sb.append("在线 ").append(devices == null ? 0 : devices.size()).append(" 台，陌生 ").append(strayCount).append(" 台\n");
        if (devices != null) {
            for (Net.Dev d : devices) {
                sb.append("  ").append(d.ip).append("  ").append(d.type);
                String tg = Net.devTag(d);
                if (!TextUtils.isEmpty(tg)) sb.append("  ").append(tg);
                sb.append("\n");
                if (!TextUtils.isEmpty(d.reason)) sb.append("      依据：").append(d.reason).append("\n");
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
        toast("报告已复制，可直接分享");
    }

    // ==================== 工具方法 ====================

    private List<Net.Dev> scanLan(String gw, String me) {
        final List<Net.Dev> out = new ArrayList<>();
        if (TextUtils.isEmpty(gw)) {
            ui.post(() -> {
                tvDevScanState.setText("拿不到网关地址，扫不了局域网。先确认手机连着 WiFi。");
                tvDevScanState.setTextColor(C_BAD);
            });
            return out;
        }
        String base = Net.subnetBase(gw);
        if (TextUtils.isEmpty(base)) return out;

        ui.post(() -> {
            tvDevScanState.setText("正在扫 " + base + "0/24 网段…");
            tvDevScanState.setTextColor(C_SUB);
            pbDev.setVisibility(View.VISIBLE);
            pbDev.setProgress(0);
            tvDevPinged.setText("");
        });

        // ① ARP 表优先：已经出现过的 IP 直接算在线，不 ping 不 TCP
        java.util.Map<String, String> arpPre = Net.readArp();

        final List<String> live = Collections.synchronizedList(new ArrayList<>());
        for (String ip : arpPre.keySet()) live.add(ip);

        final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(0);
        // ② 并发 64 + 单地址 800ms 硬上限，254 台压到 8 秒内
        ExecutorService p = Executors.newFixedThreadPool(64);
        final java.util.Set<String> toProbe = Collections.synchronizedSet(new java.util.HashSet<>());
        for (int i = 1; i <= 254; i++) {
            // 注意：Net.subnetBase() 返回值已带尾点（"192.168.1."），这里不能再加 "."
            final String ip = base + i;
            if (arpPre.containsKey(ip)) { done.incrementAndGet(); continue; }
            toProbe.add(ip);
            p.execute(() -> {
                try {
                    if (Net.pingOnce(ip)) live.add(ip);
                } catch (Throwable ignored) {
                } finally {
                    int d = done.incrementAndGet();
                    if (d % 16 == 0 || d == 254) {
                        final int dd = d, ll = live.size();
                        ui.post(() -> {
                            pbDev.setProgress(dd);
                            tvDevPinged.setText("已探 " + dd + "/254，回应 " + ll + " 台");
                        });
                    }
                }
            });
        }
        p.shutdown();
        // ③ 到点就截断，但已有结果照用（v2.4：不再因为超时丢结果）
        try { p.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
        p.shutdownNow();

        java.util.Map<String, String> arp = Net.readArp();
        for (String ip : new ArrayList<>(live)) {
            if (!arp.containsKey(ip)) arp.put(ip, "");
        }
        for (String ip : arpPre.keySet()) {
            if (!arp.containsKey(ip)) arp.put(ip, arpPre.get(ip));
        }
        List<String> ips = new ArrayList<>(arp.keySet());
        Collections.sort(ips, (a, b) -> Integer.compare(Net.ipInt(a), Net.ipInt(b)));

        final int foundN = ips.size();
        ui.post(() -> tvDevScanState.setText("找到 " + foundN + " 个地址，正在认设备…"));

        // mDNS：认出 Chromecast / AirPlay / 打印机 / 智能家居这类靠端口认不出的设备
        WifiManager.MulticastLock ml = null;
        final java.util.Map<String, String> mdns;
        try {
            if (wm != null) {
                ml = wm.createMulticastLock("wifiscan-mdns");
                ml.setReferenceCounted(false);
                ml.acquire();
            }
        } catch (Throwable ignored) {
        }
        try {
            mdns = Net.mdnsScan(2000);
        } finally {
            if (ml != null) {
                try { if (ml.isHeld()) ml.release(); } catch (Throwable ignored) {}
            }
        }
        if (!mdns.isEmpty()) {
            final int m = mdns.size();
            ui.post(() -> tvDevScanState.setText("mDNS 认出 " + m + " 台，正在补全信息…"));
        }

        // UPnP / SSDP：这一步是「认出型号」的关键，电视/盒子/路由/打印机都会应答
        ui.post(() -> tvDevScanState.setText("正在探测设备型号（UPnP）…"));
        final java.util.Map<String, Net.SsdpInfo> ssdp = Net.ssdpScan(2200);
        if (!ssdp.isEmpty()) {
            final int s2 = ssdp.size();
            ui.post(() -> tvDevScanState.setText("UPnP 认出 " + s2 + " 台设备型号，正在补全…"));
        }

        // ④ 识别阶段也限时，超时就用手上已有的结果，绝不空手而归
        ExecutorService idp = Executors.newFixedThreadPool(24);
        for (String ip : ips) {
            idp.execute(() -> {
                try {
                    Net.Dev d = new Net.Dev();
                    d.ip = ip;
                    d.mac = arp.get(ip) == null ? "" : arp.get(ip);
                    d.macHidden = TextUtils.isEmpty(d.mac);
                    d.isGw = ip.equals(gw);
                    d.isMe = ip.equals(me);
                    d.mdns = mdns.get(ip) == null ? "" : mdns.get(ip);

                    // UPnP / SSDP：电视、盒子、路由、NAS、打印机靠这个认型号
                    Net.SsdpInfo si = ssdp.get(ip);
                    if (si != null) {
                        d.upnpName = si.name;
                        d.model = si.model;
                        if (TextUtils.isEmpty(d.vendor) && !TextUtils.isEmpty(si.brand)) d.vendor = si.brand;
                        if (TextUtils.isEmpty(d.model) && !TextUtils.isEmpty(si.server)) d.model = firstWord(si.server);
                    }

                    // NetBIOS：Windows 电脑的机器名，比反查 DNS 靠谱
                    if (!d.isGw && !d.isMe) d.netbios = Net.netbiosName(ip, 400);

                    Set<Integer> open = Net.probePorts(ip);
                    d.ttl = Net.pingTtl(ip);
                    d.host = Net.resolveHost(ip);
                    if (TextUtils.isEmpty(d.vendor)) d.vendor = Net.ouiVendor(d.mac);
                    d.type = Net.inferType(d, open);
                    Net.computeSuspect(d, open);
                    if (open != null && !open.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Integer x : open) { if (sb.length() > 0) sb.append(","); sb.append(x); }
                        d.ports = sb.toString();
                        if (open.contains(80) && TextUtils.isEmpty(d.model)) {
                            String t = Net.httpTitle(ip, 80);
                            if (!TextUtils.isEmpty(t) && TextUtils.isEmpty(d.vendor)) d.vendor = t;
                        }
                    }
                    synchronized (out) { out.add(d); }
                } catch (Throwable ignored) {
                }
            });
        }
        idp.shutdown();
        try { idp.awaitTermination(14, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
        idp.shutdownNow();

        // 没来得及识别的地址也补一个"未知设备"条目，别让它们凭空消失
        java.util.Set<String> got = new java.util.HashSet<>();
        synchronized (out) { for (Net.Dev d : out) got.add(d.ip); }
        for (String ip : ips) {
            if (got.contains(ip)) continue;
            Net.Dev d = new Net.Dev();
            d.ip = ip;
            d.mac = arp.get(ip) == null ? "" : arp.get(ip);
            d.isGw = ip.equals(gw);
            d.isMe = ip.equals(me);
            d.mdns = mdns.get(ip) == null ? "" : mdns.get(ip);
            d.vendor = Net.ouiVendor(d.mac);
            synchronized (out) { out.add(d); }
        }

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

    /** 从 "Linux/3.14 UPnP/1.0 MiBox/1.0" 里抠出有意义的那个词，跳过 Linux/UPnP。 */
    private static String firstWord(String server) {
        if (TextUtils.isEmpty(server)) return "";
        String[] parts = server.trim().split("[\\s/]+");
        for (String p : parts) {
            String lp = p.toLowerCase(Locale.US);
            if (lp.equals("linux") || lp.equals("upnp") || lp.equals("http") || lp.equals("android")
                    || lp.equals("darwin") || lp.equals("windows") || lp.equals("1.0") || lp.equals("1.1")) continue;
            if (p.length() >= 3 && !p.matches("[0-9.]+")) return p.length() > 24 ? p.substring(0, 24) : p;
        }
        return server.length() > 24 ? server.substring(0, 24) : server;
    }

    private static int freqToCh(int freq) {        if (freq >= 2412 && freq <= 2484) return (freq - 2407) / 5;
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
