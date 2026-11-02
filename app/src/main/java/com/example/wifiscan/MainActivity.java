package com.example.wifiscan;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // ---------- 视图 ----------
    private ScrollView scroll;
    private LinearLayout boxCheck, boxScan, boxSpeed, boxDev, apList, devList;
    private ProgressBar pbCheck, pbScan, pbSpeed, pbDev;
    private TextView statusCheck, resultCheck, statusScan, resultScan,
            statusSpeed, resultSpeed, statusDev;
    private TextView tvTip;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private volatile boolean busyCheck, busyScan, busySpeed, busyDev;
    private String reportCheck = "", reportScan = "", reportSpeed = "", reportDev = "";

    private String gwIp = "";
    private String myIp = "";

    // ============================================================
    // OUI 厂商表
    // ============================================================
    private static final Map<String, String> OUI = new HashMap<>();

    private static void putOui(String hex, String name) {
        OUI.put(hex.replace(":", "").replace("-", "").toUpperCase(Locale.US), name);
    }

    static {
        // 网络设备 / 路由器
        putOui("001D0F", "TP-Link");   putOui("14CC20", "TP-Link");  putOui("A42BB0", "TP-Link");
        putOui("50642B", "TP-Link");   putOui("3C46D8", "TP-Link");  putOui("F4F26D", "TP-Link");
        putOui("C46E1F", "TP-Link");   putOui("B0BE76", "TP-Link");  putOui("00E0B8", "TP-Link");
        putOui("9C5322", "Compal/宽带猫"); putOui("001122", "Tenda"); putOui("C83A35", "Tenda");
        putOui("D8320E", "Tenda");     putOui("D46E5C", "华为/荣耀路由");
        putOui("F09FC2", "Ubiquiti");  putOui("788A20", "Ubiquiti"); putOui("0418D6", "Ubiquiti");
        putOui("000C29", "VMware");    putOui("005056", "VMware");    putOui("00155D", "微软虚拟");

        // 手机 / 平板
        putOui("04CF8C", "小米");      putOui("286C07", "小米");      putOui("64CC2E", "小米");
        putOui("7811DC", "小米");      putOui("8CBEBE", "小米");      putOui("009E11", "小米");
        putOui("F8A45F", "小米");      putOui("50EC50", "小米");
        putOui("00E0FC", "华为");      putOui("283CE4", "华为");      putOui("4846FB", "华为");
        putOui("70723C", "华为");      putOui("9C28EF", "华为");      putOui("F4C714", "华为");
        putOui("28CFE9", "苹果");      putOui("8C8590", "苹果");      putOui("001B63", "苹果");
        putOui("3C0754", "苹果");      putOui("A483E7", "苹果");      putOui("F0DBF8", "苹果");
        putOui("9C35EB", "苹果");      putOui("D0E140", "苹果");
        putOui("002637", "三星");      putOui("3423BA", "三星");      putOui("5C0A5B", "三星");
        putOui("8C7712", "三星");      putOui("E8508B", "三星");      putOui("C0BDD1", "三星");
        putOui("001A11", "Google");    putOui("3C5AB4", "Google");    putOui("F4F5D8", "Google");
        putOui("94DF4E", "OPPO");      putOui("C0EEFB", "OPPO");      putOui("7C1DD9", "vivo");
        putOui("E45D75", "vivo");      putOui("2C5BB8", "魅族");      putOui("FCECDA", "一加");

        // 电脑 / 网卡
        putOui("001B21", "Intel");     putOui("001E67", "Intel");     putOui("3C970E", "Intel");
        putOui("A4BB6D", "Intel");     putOui("00A0C9", "Intel");     putOui("00E04C", "Realtek");
        putOui("24A43C", "华硕");      putOui("1C872C", "华硕");      putOui("2C56DC", "华硕");
        putOui("B827EB", "树莓派");    putOui("DCA632", "树莓派");    putOui("E45F01", "树莓派");
        putOui("001132", "群晖 NAS");  putOui("0011D8", "群晖 NAS");

        // 电视 / 盒子 / 打印
        putOui("7C49EB", "小米电视");  putOui("F0B429", "小米盒子");  putOui("846569", "海信");
        putOui("0018DD", "Silicom/打印机"); putOui("3C2AF4", "Brother 打印");
        putOui("0021B7", "Lexmark 打印");   putOui("9C934E", "HP 打印");
        putOui("0000AA", "网络设备");  putOui("B0C554", "D-Link");    putOui("0024B2", "Netgear");
    }

    // ============================================================
    // 生命周期
    // ============================================================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        scroll = findViewById(R.id.scroll);
        boxCheck = findViewById(R.id.boxCheck);
        boxScan = findViewById(R.id.boxScan);
        boxSpeed = findViewById(R.id.boxSpeed);
        boxDev = findViewById(R.id.boxDev);
        apList = findViewById(R.id.apList);
        devList = findViewById(R.id.devList);
        pbCheck = findViewById(R.id.pbCheck);
        pbScan = findViewById(R.id.pbScan);
        pbSpeed = findViewById(R.id.pbSpeed);
        pbDev = findViewById(R.id.pbDev);
        statusCheck = findViewById(R.id.statusCheck);
        resultCheck = findViewById(R.id.resultCheck);
        statusScan = findViewById(R.id.statusScan);
        resultScan = findViewById(R.id.resultScan);
        statusSpeed = findViewById(R.id.statusSpeed);
        resultSpeed = findViewById(R.id.resultSpeed);
        statusDev = findViewById(R.id.statusDev);
        tvTip = findViewById(R.id.tvTip);

        gwIp = getGatewayIp();
        myIp = getLocalIp();

        findViewById(R.id.check).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startCheck(); }
        });
        findViewById(R.id.scan).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 7);
                    toast("请允许定位权限，扫描周边 WiFi 才能读到列表");
                    return;
                }
                startScan();
            }
        });
        findViewById(R.id.btnSpeed).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startSpeed(); }
        });
        findViewById(R.id.btnDevScan).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startDevScan(); }
        });

        findViewById(R.id.btnRouter).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String ip = gwIp == null || gwIp.length() == 0 ? "192.168.1.1" : gwIp;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + ip)));
                } catch (Exception e) {
                    toast("打不开浏览器：" + e.getMessage());
                }
            }
        });

        findViewById(R.id.btnDns).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < 28) {
                    toast("这台机器太老，不支持私有 DNS（需 Android 9+）");
                    return;
                }
                try {
                    startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));
                    toast("选「指定私有 DNS」，填 dns.alidns.com 或 dot.pub");
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception ignored) { }
                    toast("自己找：设置 → 网络 → 私有 DNS");
                }
            }
        });

        findViewById(R.id.btnCopy).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                StringBuilder all = new StringBuilder();
                if (reportCheck.length() > 0) all.append("【一键体检】\n").append(reportCheck).append("\n\n");
                if (reportScan.length() > 0) all.append("【周边 WiFi】\n").append(reportScan).append("\n\n");
                if (reportSpeed.length() > 0) all.append("【测网速】\n").append(reportSpeed).append("\n\n");
                if (reportDev.length() > 0) all.append("【局域网设备】\n").append(reportDev).append("\n\n");
                if (all.length() == 0) {
                    toast("还没跑过任何检测，先点①②③④任意一个");
                    return;
                }
                copyText(all.toString());
            }
        });

        findViewById(R.id.btnWifiSet).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    toast("打不开 WiFi 设置");
                }
            }
        });

        findViewById(R.id.btnBattery).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception e) {
                    toast("打不开电池设置");
                }
            }
        });

        tvTip.setText("路由器后台：" + (gwIp == null || gwIp.length() == 0 ? "没读到网段" : gwIp)
                + "（密码多半贴在路由器背面）\n本机 IP：" + (myIp == null ? "?" : myIp));
    }

    // ============================================================
    // ① 一键体检 —— 实时流式 ping
    // ============================================================
    private void startCheck() {
        if (busyCheck) { toast("体检正在跑，等一下"); return; }
        busyCheck = true;
        boxCheck.setVisibility(View.VISIBLE);
        pbCheck.setIndeterminate(false);
        pbCheck.setMax(16);
        pbCheck.setProgress(0);
        resultCheck.setText("");
        scrollTo(boxCheck);

        new Thread(new Runnable() {
            public void run() {
                long t0 = System.currentTimeMillis();
                final String gw = gwIp == null || gwIp.length() == 0 ? "" : gwIp;

                // --- 第一跳 ---
                if (gw.length() == 0) {
                    ui.post(new Runnable() {
                        public void run() {
                            statusCheck.setText("没读到网关 IP —— 可能没连上 WiFi");
                        }
                    });
                }
                if (gw.length() > 0) {
                    ui.post(new Runnable() {
                        public void run() { statusCheck.setText("第 1 段：手机 → 路由器 (" + gw + ")，发 8 个包…"); }
                    });
                    final PingStat g = pingHost(gw, 8, new PingView() {
                        public void onTick(PingStat s) {
                            final String t = "第 1 段 手机→路由器：" + s.recv + "/" + s.count
                                    + " 包已回，当前 " + String.format(Locale.US, "%.0f", s.last) + " ms";
                            final int p = s.recv;
                            ui.post(new Runnable() {
                                public void run() {
                                    statusCheck.setText(t);
                                    pbCheck.setProgress(Math.min(8, p));
                                }
                            });
                        }
                    });

                    // --- 出网 ---
                    ui.post(new Runnable() {
                        public void run() { statusCheck.setText("第 2 段：手机 → 外网 (223.5.5.5)，发 8 个包…"); }
                    });
                    final PingStat p = pingHost("223.5.5.5", 8, new PingView() {
                        public void onTick(PingStat s) {
                            final String t = "第 2 段 手机→外网：" + s.recv + "/" + s.count
                                    + " 包已回，当前 " + String.format(Locale.US, "%.0f", s.last) + " ms";
                            final int pr = 8 + s.recv;
                            ui.post(new Runnable() {
                                public void run() {
                                    statusCheck.setText(t);
                                    pbCheck.setProgress(Math.min(16, pr));
                                }
                            });
                        }
                    });

                    final WifiBrief wb = readWifi();
                    final long cost = System.currentTimeMillis() - t0;
                    final String rep = buildCheckReport(gw, g, p, wb, cost);
                    reportCheck = rep;
                    ui.post(new Runnable() {
                        public void run() {
                            statusCheck.setText("体检完成，用时 " + cost / 1000.0 + " 秒");
                            pbCheck.setProgress(16);
                            resultCheck.setText(rep);
                        }
                    });
                } else {
                    final String rep = "读不到网关地址。先确认手机连的是房东的 WiFi，而不是流量。";
                    reportCheck = rep;
                    ui.post(new Runnable() {
                        public void run() {
                            statusCheck.setText("体检中断");
                            resultCheck.setText(rep);
                        }
                    });
                }
                busyCheck = false;
            }
        }).start();
    }

    private String buildCheckReport(String gw, PingStat g, PingStat p, WifiBrief wb, long cost) {
        StringBuilder sb = new StringBuilder();
        sb.append("网关 ").append(gw).append("    本机 ").append(myIp).append("\n");
        sb.append(String.format(Locale.US,
                "第1段 手机→路由器：回%d/%d  平均%.0fms  抖动%.0fms  丢包%.0f%%\n",
                g.recv, g.count, g.avg(), g.jitter(), g.loss()));
        sb.append(String.format(Locale.US,
                "第2段 手机→外网：  回%d/%d  平均%.0fms  抖动%.0fms  丢包%.0f%%\n",
                p.recv, p.count, p.avg(), p.jitter(), p.loss()));
        if (wb != null) {
            sb.append(String.format(Locale.US,
                    "信号 %d dBm   频段 %s   连接速率 %d Mbps\n",
                    wb.rssi, wb.band, wb.linkMbps));
        }
        sb.append("\n");

        double gLoss = g.loss(), pLoss = p.loss();
        boolean firstHopBad = g.recv == 0 || gLoss > 15 || g.avg() > 40 || g.jitter() > 60;
        boolean wanBad = pLoss > 10 || p.avg() > 120 || p.jitter() > 80;

        if (firstHopBad) {
            sb.append("结论：手机 ↔ 路由器这一跳就不行。\n");
            sb.append("这类卡顿是你自己能修的，跟运营商无关。\n\n");
            sb.append("按顺序试：\n");
            sb.append("1. 走到路由器 2 米内再测一遍。若明显变好 = 隔墙/距离问题，换个位置或加个中继。\n");
            sb.append("2. 点「② 扫周边 WiFi」看信道。2.4G 上 5、6、7、8 这几个数是挤在一起的，\n");
            sb.append("   周围有 5 个以上强信号占着 6 信道，就进路由后台把 2.4G 改成 1 或 11。\n");
            sb.append("3. 手机支持 5G 就连 5G（点「📶 打开 WiFi 设置」看有没有 -5G 的名字）。\n");
            sb.append("   5G 穿墙差但没人抢，隔一堵墙通常还是比 2.4G 快。\n");
            sb.append("4. 点「🔋 关掉电池限制」，防止锁屏后 WiFi 被系统掐。\n");
            sb.append("5. 还不行就点「🔧 打开路由器后台」，看「已连接设备」里有没有你不认识的，禁掉。\n");
        } else if (wanBad) {
            sb.append("结论：手机到路由器很干净，问题在路由器往外这一段。\n");
            sb.append("这一段你改不了，但可以拿出证据去谈。\n\n");
            sb.append("先排除自己：\n");
            sb.append("1. 用「③ 测网速」看真实带宽。若只有几 Mbps，而房东说是 100M/200M，那就是被分掉了。\n");
            sb.append("2. 点「④ 扫局域网设备」看有几台设备在连。十几台同时在线，带宽必然被瓜分。\n");
            sb.append("3. 高峰时段（晚 8–11 点）再测一次。白天快、晚上慢 = 整栋楼在共享一条线。\n\n");
            sb.append("跟房东讲的时候别说「网慢」，直接给这三个数：\n");
            sb.append("晚 9 点实测下行 __ Mbps，合同/宣传是 __ Mbps；\n");
            sb.append("延迟 __ ms，丢包 __%；同一时间连着的设备 __ 台。\n");
            sb.append("（把这几项填上，比说一百句「卡」有用）\n");
        } else {
            sb.append("结论：两段延迟都正常，卡的原因是带宽不够，不是信号也不是线路。\n");
            sb.append("也就是说 ping 很漂亮，但一开视频/下载就卡。\n\n");
            sb.append("1. 点「③ 测网速」确认下行。低于 8 Mbps 基本没法看高清；\n");
            sb.append("   25 Mbps 以上才算够用；低于 25 就是套餐/分流问题。\n");
            sb.append("2. 点「④ 扫局域网设备」看是不是有人在下东西/挂机。\n");
            sb.append("3. 路由器如果还是老 2.4G 单频，瓶颈就在路由器本身，让房东换。\n");
        }
        sb.append("\n（体检总耗时 ").append(cost / 1000.0).append(" 秒）");
        return sb.toString();
    }

    // ============================================================
    // ② 扫周边 WiFi —— 每个 AP 一条强度条
    // ============================================================
    private void startScan() {
        if (busyScan) { toast("正在扫，等一下"); return; }
        busyScan = true;
        boxScan.setVisibility(View.VISIBLE);
        pbScan.setIndeterminate(true);
        apList.removeAllViews();
        resultScan.setText("");
        scrollTo(boxScan);

        final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        new Thread(new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() { statusScan.setText("正在扫描周边 WiFi…"); }
                });
                if (wm != null) {
                    try { wm.startScan(); } catch (Exception ignored) { }
                }
                try { Thread.sleep(2200); } catch (InterruptedException ignored) { }

                List<ScanResult> rs = null;
                if (wm != null) {
                    try { rs = wm.getScanResults(); } catch (Exception ignored) { }
                }
                final List<ScanResult> list = rs == null ? new ArrayList<ScanResult>() : rs;
                Collections.sort(list, new Comparator<ScanResult>() {
                    public int compare(ScanResult a, ScanResult b) {
                        return b.level - a.level;
                    }
                });

                if (list.isEmpty()) {
                    final String rep = "没扫到任何 AP。可能是系统限制了扫描频率，等 30 秒再点一次；"
                            + "或定位权限/定位开关没开。";
                    reportScan = rep;
                    ui.post(new Runnable() {
                        public void run() {
                            pbScan.setIndeterminate(false);
                            pbScan.setProgress(100);
                            statusScan.setText("扫描结束，0 个");
                            resultScan.setText(rep);
                        }
                    });
                    busyScan = false;
                    return;
                }

                // 统计 2.4G 各信道
                final int[] chCount = new int[15];
                int n24 = 0, n5 = 0;
                for (ScanResult r : list) {
                    if (r.frequency > 4000) n5++;
                    else {
                        n24++;
                        if (r.frequency >= 2412 && r.frequency <= 2484) {
                            int ch = (r.frequency - 2407) / 5;
                            if (ch >= 1 && ch <= 14) chCount[ch]++;
                        }
                    }
                }

                for (int i = 0; i < list.size(); i++) {
                    final ScanResult r = list.get(i);
                    final int idx = i + 1;
                    final int total = list.size();
                    ui.post(new Runnable() {
                        public void run() {
                            addApRow(r);
                            pbScan.setIndeterminate(false);
                            pbScan.setMax(total);
                            pbScan.setProgress(idx);
                            statusScan.setText("已扫到 " + idx + "/" + total + " 个");
                        }
                    });
                }

                StringBuilder sb = new StringBuilder();
                sb.append("共 ").append(list.size()).append(" 个 AP：2.4G ").append(n24)
                        .append(" 个，5G ").append(n5).append(" 个\n");
                sb.append("2.4G 各信道被占用情况：\n");
                int best = 1;
                for (int c = 1; c <= 13; c++) {
                    if (chCount[c] == 0) continue;
                    sb.append("  信道 ").append(c).append("：").append(bar(chCount[c])).append(" ")
                            .append(chCount[c]).append(" 个\n");
                }
                // 找最空信道（1/6/11 里选）
                int bestVal = 999;
                for (int c : new int[]{1, 6, 11}) {
                    if (chCount[c] < bestVal) { bestVal = chCount[c]; best = c; }
                }
                sb.append("\n建议把路由器 2.4G 信道改成 ").append(best)
                        .append("（1/6/11 里最空的，相互不重叠）\n");
                if (n5 > 0) {
                    sb.append("周围有 5G 信号，能连 5G 就优先连 5G。\n");
                }
                final String rep = sb.toString();
                reportScan = rep;

                ui.post(new Runnable() {
                    public void run() {
                        pbScan.setIndeterminate(false);
                        pbScan.setMax(list.size());
                        pbScan.setProgress(list.size());
                        statusScan.setText("扫描完成，" + list.size() + " 个 AP");
                        resultScan.setText(rep);
                    }
                });
                busyScan = false;
            }
        }).start();
    }

    private String bar(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(n, 12); i++) b.append("█");
        return b.toString();
    }

    private void addApRow(ScanResult r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(4);
        row.setPadding(0, pad, 0, pad);

        String band = r.frequency > 4000 ? "5G" : "2.4G";
        int ch = r.frequency > 4000 ? (r.frequency - 5000) / 5 : (r.frequency - 2407) / 5;
        String cap = (r.capabilities != null && r.capabilities.contains("WPA3")) ? "WPA3"
                : (r.capabilities != null && r.capabilities.contains("WPA2")) ? "WPA2"
                : (r.capabilities != null && r.capabilities.contains("WPA")) ? "WPA" : "开放";
        int q = Math.max(0, Math.min(100, 2 * (r.level + 100)));

        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setText(r.SSID == null || r.SSID.length() == 0 ? "(隐藏网络)" : r.SSID);
        row.addView(t);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(q);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(110), dp(10));
        lp.rightMargin = dp(8);
        pb.setLayoutParams(lp);
        line.addView(pb);

        TextView d = new TextView(this);
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        d.setText(r.level + " dBm  " + band + "  ch" + ch + "  " + cap);
        line.addView(d);

        row.addView(line);
        apList.addView(row);
    }

    // ============================================================
    // ③ 测网速 —— 实时显示已下载 MB 与瞬时速度
    // ============================================================
    private void startSpeed() {
        if (busySpeed) { toast("测速正在跑，等一下"); return; }
        busySpeed = true;
        boxSpeed.setVisibility(View.VISIBLE);
        pbSpeed.setIndeterminate(false);
        pbSpeed.setMax(1000);
        pbSpeed.setProgress(0);
        resultSpeed.setText("");
        scrollTo(boxSpeed);

        new Thread(new Runnable() {
            public void run() {
                StringBuilder sb = new StringBuilder();
                // ---------- 下行 ----------
                ui.post(new Runnable() {
                    public void run() { statusSpeed.setText("① 下行为主：正在下载 8 MB…"); }
                });
                SpeedResult down;
                try {
                    down = measureDownload(new Meter() {
                        public void onProgress(long got, long total, double mbps) {
                            final String t = String.format(Locale.US,
                                    "↓ 已下载 %.1f / %.1f MB    当前 %.1f Mbps",
                                    got / 1e6, total / 1e6, mbps);
                            final int p = (int) (got * 500 / Math.max(1, total));
                            ui.post(new Runnable() {
                                public void run() {
                                    statusSpeed.setText(t);
                                    pbSpeed.setProgress(Math.min(500, p));
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    down = SpeedResult.fail(e);
                }
                final SpeedResult fd = down;
                sb.append(String.format(Locale.US, "下行：%.2f Mbps（%.1f MB / %.2f 秒）\n",
                        down.mbps, down.bytes / 1e6, down.ms / 1000.0));

                // ---------- 上行 ----------
                ui.post(new Runnable() {
                    public void run() {
                        statusSpeed.setText(String.format(Locale.US,
                                "↓ 下行 %.1f Mbps 完成，② 正在上传 3 MB…", fd.mbps));
                        pbSpeed.setProgress(500);
                    }
                });
                SpeedResult up;
                try {
                    up = measureUpload(new Meter() {
                        public void onProgress(long sent, long total, double mbps) {
                            final String t = String.format(Locale.US,
                                    "↑ 已上传 %.1f / %.1f MB    当前 %.1f Mbps",
                                    sent / 1e6, total / 1e6, mbps);
                            final int p = 500 + (int) (sent * 500 / Math.max(1, total));
                            ui.post(new Runnable() {
                                public void run() {
                                    statusSpeed.setText(t);
                                    pbSpeed.setProgress(Math.min(1000, p));
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    up = SpeedResult.fail(e);
                }
                final SpeedResult fu = up;
                sb.append(String.format(Locale.US, "上行：%.2f Mbps（%.1f MB / %.2f 秒）\n",
                        up.mbps, up.bytes / 1e6, up.ms / 1000.0));

                // ---------- 判定 ----------
                sb.append("\n");
                double v = down.mbps;
                if (!down.ok) {
                    sb.append("下行没测出来：").append(down.err).append("\n").append(errHint(down.err));
                } else if (v < 8) {
                    sb.append("判定：下行 ").append(f1(v)).append(" Mbps，很低。\n");
                    sb.append("看 1080p 要 5–8 Mbps，这个数基本就是「转圈」的原因。\n\n");
                    sb.append("这不是你手机的问题。按顺序查：\n");
                    sb.append("1. 点「④ 扫局域网设备」，看有几台在同时用。十几台共享一条线必然这样。\n");
                    sb.append("2. 换到路由器旁边再测一次。只差一点 = 信号不是主因，是总带宽不足。\n");
                    sb.append("3. 问房东：这条线是多少兆的、几个人分。若号称 100M 你只测到 5M，\n");
                    sb.append("   那就是被分掉了，或者路由器本身很老。\n");
                } else if (v < 25) {
                    sb.append("判定：下行 ").append(f1(v)).append(" Mbps，勉强够用。\n");
                    sb.append("刷视频可以，多人同时看 1080p 或下大文件就卡。\n\n");
                    sb.append("1. 点「② 扫周边 WiFi」看信道挤不挤，挤就改 1/6/11。\n");
                    sb.append("2. 手机能连 5G 就换 5G。\n");
                    sb.append("3. 晚 9 点再测，若明显更低 = 整栋共享。\n");
                } else {
                    sb.append("判定：下行 ").append(f1(v)).append(" Mbps，带宽够用。\n");
                    sb.append("那么卡就不是带宽，而是：\n");
                    sb.append("· 延迟/丢包 → 回到「① 一键体检」看两段数据\n");
                    sb.append("· DNS 慢 → 点「⚡ 换私有 DNS」\n");
                    sb.append("· 有人在下东西 → 点「④ 扫局域网设备」\n");
                }
                if (up.ok && up.mbps < 1.0) {
                    sb.append("\n上行只有 ").append(f1(up.mbps))
                            .append(" Mbps，发图/视频通话/游戏会卡（上行被限速了）。\n");
                }
                sb.append("\n说明：测的是云端节点（Cloudflare），不是国内 CDN。\n");
                sb.append("所以实际刷国内视频通常比这个数好看一些。");

                final String rep = sb.toString();
                reportSpeed = rep;
                ui.post(new Runnable() {
                    public void run() {
                        pbSpeed.setProgress(1000);
                        statusSpeed.setText(String.format(Locale.US, "完成：↓ %.1f Mbps   ↑ %.1f Mbps",
                                fd.mbps, fu.mbps));
                        resultSpeed.setText(rep);
                    }
                });
                busySpeed = false;
            }
        }).start();
    }

    private String errHint(String err) {
        if (err == null) return "";
        if (err.contains("timed out") || err.contains("timeout"))
            return "超时说明这条线此刻基本没吞吐，或者被墙/被限速。";
        if (err.contains("Unable to resolve") || err.contains("UnknownHost"))
            return "域名都解析不了 —— 先点「⚡ 换私有 DNS」。";
        if (err.contains("Connection refused") || err.contains("reset"))
            return "连接被断 —— 可能被路由器 QoS 限速了。";
        return "换个时间再测；仍失败就把「① 一键体检」的数据一起给房东看。";
    }

    private String f1(double v) { return String.format(Locale.US, "%.1f", v); }

    private interface Meter {
        void onProgress(long done, long total, double mbps);
    }

    private static class SpeedResult {
        double mbps; long ms; long bytes; boolean ok; String err;

        static SpeedResult fail(Exception e) {
            SpeedResult r = new SpeedResult();
            r.ok = false;
            r.err = shortErr(e);
            return r;
        }
    }

    private static String shortErr(Exception e) {
        String m = e.getMessage();
        String n = e.getClass().getSimpleName();
        if (m == null) return n;
        return m.length() > 90 ? n + ": " + m.substring(0, 90) : n + ": " + m;
    }

    private SpeedResult measureDownload(Meter m) throws Exception {
        final long TOTAL = 8_000_000L;
        final long WARM = 1_000_000L;
        SpeedResult r = new SpeedResult();
        HttpURLConnection c = (HttpURLConnection)
                new URL("https://speed.cloudflare.com/__down?bytes=" + TOTAL).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "WifiCheck/1.3");
        c.connect();
        int code = c.getResponseCode();
        if (code / 100 != 2) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        byte[] buf = new byte[16384];
        long got = 0, measured = 0, t0 = 0, lastUi = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            got += n;
            if (t0 == 0) {
                if (got < WARM) continue;
                t0 = System.currentTimeMillis();
                measured = 0;
            }
            measured += n;
            long now = System.currentTimeMillis();
            if (m != null && now - lastUi > 180) {
                lastUi = now;
                long el = Math.max(1, now - t0);
                m.onProgress(got, TOTAL, (measured * 8.0 / 1e6) / (el / 1000.0));
            }
        }
        try { in.close(); } catch (Exception ignored) { }
        c.disconnect();
        r.ms = Math.max(1, System.currentTimeMillis() - t0);
        r.bytes = measured;
        r.mbps = (measured * 8.0 / 1e6) / (r.ms / 1000.0);
        r.ok = measured > 0;
        if (!r.ok) r.err = "下载到 0 字节";
        return r;
    }

    private SpeedResult measureUpload(Meter m) throws Exception {
        final int TOTAL = 3_000_000;
        SpeedResult r = new SpeedResult();
        HttpURLConnection c = (HttpURLConnection)
                new URL("https://speed.cloudflare.com/__up?bytes=" + TOTAL).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setFixedLengthStreamingMode(TOTAL);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setRequestProperty("User-Agent", "WifiCheck/1.3");
        c.connect();
        OutputStream out = c.getOutputStream();
        byte[] buf = new byte[32768];
        for (int i = 0; i < buf.length; i++) buf[i] = 0x41;
        long t0 = System.currentTimeMillis(), sent = 0, lastUi = 0;
        while (sent < TOTAL) {
            int k = (int) Math.min(buf.length, TOTAL - sent);
            out.write(buf, 0, k);
            sent += k;
            long now = System.currentTimeMillis();
            if (m != null && now - lastUi > 180) {
                lastUi = now;
                long el = Math.max(1, now - t0);
                m.onProgress(sent, TOTAL, (sent * 8.0 / 1e6) / (el / 1000.0));
            }
        }
        out.flush();
        long ms = Math.max(1, System.currentTimeMillis() - t0);
        try { out.close(); } catch (Exception ignored) { }
        int code = c.getResponseCode();
        c.disconnect();
        if (code / 100 != 2 && code / 100 != 3) throw new Exception("HTTP " + code);
        r.ms = ms;
        r.bytes = sent;
        r.mbps = (sent * 8.0 / 1e6) / (ms / 1000.0);
        r.ok = true;
        return r;
    }

    // ============================================================
    // ④ 扫局域网设备 —— TTL + 端口 + 主机名 + 标题 交叉判型
    // ============================================================
    private void startDevScan() {
        if (busyDev) { toast("正在扫，等一下"); return; }
        String gw = gwIp == null ? "" : gwIp;
        final String base = subnetBase(gw.length() > 0 ? gw : myIp);
        if (base == null) {
            toast("读不到本机网段，先连上 WiFi");
            return;
        }
        busyDev = true;
        boxDev.setVisibility(View.VISIBLE);
        pbDev.setIndeterminate(false);
        pbDev.setMax(254);
        pbDev.setProgress(0);
        devList.removeAllViews();
        scrollTo(boxDev);

        final String gwFinal = gw;
        new Thread(new Runnable() {
            public void run() {
                ui.post(new Runnable() {
                    public void run() { statusDev.setText("第 1 步：扫描 " + base + ".1–254 存活主机…"); }
                });

                // ---------- 存活探测 ----------
                ExecutorService pool = Executors.newFixedThreadPool(40);
                List<Future<String>> fs = new ArrayList<>();
                for (int i = 1; i <= 254; i++) {
                    final String ip = base + "." + i;
                    final int idx = i;
                    fs.add(pool.submit(new Callable<String>() {
                        public String call() {
                            boolean up = pingOnce(ip);
                            final int p = idx;
                            ui.post(new Runnable() {
                                public void run() {
                                    pbDev.setProgress(p);
                                    if (p % 25 == 0) statusDev.setText("扫描进度 " + p + "/254…");
                                }
                            });
                            return up ? ip : null;
                        }
                    }));
                }
                Set<String> alive = new HashSet<>();
                for (Future<String> f : fs) {
                    try {
                        String v = f.get(6, TimeUnit.SECONDS);
                        if (v != null) alive.add(v);
                    } catch (Exception ignored) { }
                }
                // ARP 表补充（有些设备不响应 ping）
                Map<String, String> arp = readArp();
                for (String ip : arp.keySet()) {
                    if (ip.startsWith(base + ".")) alive.add(ip);
                }
                pool.shutdownNow();

                final int aliveN = alive.size();
                ui.post(new Runnable() {
                    public void run() {
                        statusDev.setText("第 2 步：存活 " + aliveN + " 台，正在识别类型/型号…");
                    }
                });

                // ---------- 逐台识别，边识别边出结果 ----------
                List<String> ips = new ArrayList<>(alive);
                Collections.sort(ips, new Comparator<String>() {
                    public int compare(String a, String b) { return Integer.compare(ipInt(a), ipInt(b)); }
                });

                ExecutorService pool2 = Executors.newFixedThreadPool(12);
                List<Future<?>> f2 = new ArrayList<>();
                final List<Dev> found = Collections.synchronizedList(new ArrayList<Dev>());
                for (final String ip : ips) {
                    f2.add(pool2.submit(new Runnable() {
                        public void run() {
                            Dev d = identify(ip, arp.get(ip), ip.equals(gwFinal), ip.equals(myIp));
                            found.add(d);
                            ui.post(new Runnable() {
                                public void run() {
                                    statusDev.setText("已识别 " + found.size() + "/" + aliveN + " 台…");
                                }
                            });
                            ui.post(new Runnable() {
                                public void run() { addDevRow(d); }
                            });
                        }
                    }));
                }
                for (Future<?> f : f2) {
                    try { f.get(25, TimeUnit.SECONDS); } catch (Exception ignored) { }
                }
                pool2.shutdownNow();

                // ---------- 汇总 ----------
                List<Dev> all = new ArrayList<>(found);
                Collections.sort(all, new Comparator<Dev>() {
                    public int compare(Dev a, Dev b) {
                        if (a.isGw != b.isGw) return a.isGw ? -1 : 1;
                        return Integer.compare(ipInt(a.ip), ipInt(b.ip));
                    }
                });
                StringBuilder sb = new StringBuilder();
                sb.append("网段 ").append(base).append(".0/24，共发现 ").append(all.size()).append(" 台在线设备\n\n");
                int phones = 0, pcs = 0, others = 0, unknown = 0;
                for (Dev d : all) {
                    if (d.type.contains("手机") || d.type.contains("iPhone")) phones++;
                    else if (d.type.contains("电脑")) pcs++;
                    else if (d.type.contains("未知")) unknown++;
                    else others++;
                }
                sb.append("手机/平板 ").append(phones).append(" 台，电脑 ").append(pcs)
                        .append(" 台，其他 ").append(others).append(" 台，没认出来 ").append(unknown).append(" 台\n\n");
                for (Dev d : all) {
                    sb.append(d.ip).append("  ").append(d.type);
                    if (d.isGw) sb.append("  [网关]");
                    if (d.isMe) sb.append("  [本机]");
                    sb.append("\n");
                    if (d.vendor != null && d.vendor.length() > 0) sb.append("   厂商 ").append(d.vendor).append("\n");
                    if (d.host != null && d.host.length() > 0) sb.append("   主机名 ").append(d.host).append("\n");
                    if (d.model != null && d.model.length() > 0) sb.append("   型号/标题 ").append(d.model).append("\n");
                    if (d.ports != null && d.ports.length() > 0) sb.append("   开放端口 ").append(d.ports).append("\n");
                }
                sb.append("\n");
                sb.append("怎么用这份表：\n");
                sb.append("1. 先数总台数。一间出租屋超过 8–10 台同时在线，带宽必然被瓜分。\n");
                sb.append("2. 找「未知」和你不认识的电脑。那可能就是别人蹭网或者隔壁在用。\n");
                sb.append("3. 手机 App 踢不了人 —— 踢人只能在路由器后台做。\n");
                sb.append("   点「🔧 打开路由器后台」→ 找「已连接设备 / DHCP 客户端」→ 对上表的 IP 或 MAC → 禁用。\n");
                sb.append("4. Android 10 以后系统禁止普通 App 读别人的 MAC，所以很多行 MAC 是空的，\n");
                sb.append("   这是系统限制，不是没扫到。路由器后台能看到完整 MAC。\n");
                final String rep = sb.toString();
                reportDev = rep;

                ui.post(new Runnable() {
                    public void run() {
                        statusDev.setText("扫描完成，" + all.size() + " 台在线");
                        pbDev.setProgress(254);
                    }
                });
                busyDev = false;
            }
        }).start();
    }

    private static class Dev {
        String ip, mac, vendor, host, type = "❓ 未知设备", model = "", ports = "";
        int ttl = -1;
        boolean isGw, isMe;
    }

    private Dev identify(String ip, String mac, boolean isGw, boolean isMe) {
        Dev d = new Dev();
        d.ip = ip;
        d.mac = mac;
        d.isGw = isGw;
        d.isMe = isMe;

        // 1) TTL
        d.ttl = pingTtl(ip);
        // 2) 主机名
        d.host = resolveHost(ip);
        // 3) MAC 厂商
        if (mac != null && mac.length() >= 8) {
            String key = mac.replace(":", "").replace("-", "").toUpperCase(Locale.US);
            if (key.length() >= 6) d.vendor = OUI.get(key.substring(0, 6));
        }
        // 4) 端口
        Set<Integer> open = probePorts(ip);
        StringBuilder ps = new StringBuilder();
        for (Integer p : open) {
            if (ps.length() > 0) ps.append(",");
            ps.append(p);
        }
        d.ports = ps.toString();
        // 5) HTTP 标题（型号最靠谱的来源）
        if (open.contains(80)) d.model = httpTitle(ip, 80);
        else if (open.contains(8080)) d.model = httpTitle(ip, 8080);
        else if (open.contains(443)) d.model = httpTitle(ip, 443);

        d.type = inferType(d, open);
        return d;
    }

    private String inferType(Dev d, Set<Integer> open) {
        String h = d.host == null ? "" : d.host.toLowerCase(Locale.US);
        String m = d.model == null ? "" : d.model.toLowerCase(Locale.US);
        String v = d.vendor == null ? "" : d.vendor;

        if (d.isGw) return "📡 路由器 / 光猫";
        if (d.isMe) return "📱 本机（这台手机）";

        // 端口强信号
        if (open.contains(9100) || open.contains(631) || open.contains(515))
            return "🖨 打印机";
        if (open.contains(62078))
            return "📱 iPhone / iPad";
        if (open.contains(3389) || open.contains(445) || open.contains(139))
            return "💻 Windows 电脑";
        if (open.contains(554) || open.contains(8009) || open.contains(7000) || open.contains(5000))
            return "📺 电视 / 盒子 / 摄像头";
        if (open.contains(22) || open.contains(443) && open.contains(22))
            return "💾 NAS / 服务器";

        // 主机名线索
        String[] pcKeys = {"pc", "desktop", "laptop", "thinkpad", "macbook", "windows", "win-", "pc-", "computer"};
        String[] phoneKeys = {"phone", "iphone", "ipad", "android", "redmi", "mi-", "huawei", "honor", "oppo", "vivo", "galaxy", "pixel", "oneplus", "realme", "meizu"};
        String[] tvKeys = {"tv", "box", "mi-box", "appletv", "firetv", "chromecast", "投影"};
        String[] printKeys = {"printer", "hp-", "canon", "epson", "brother", "lexmark"};

        for (String k : printKeys) if (h.contains(k) || m.contains(k)) return "🖨 打印机";
        for (String k : tvKeys) if (h.contains(k) || m.contains(k)) return "📺 电视 / 盒子";
        for (String k : pcKeys) if (h.contains(k) || m.contains(k)) return "💻 电脑";
        for (String k : phoneKeys) if (h.contains(k) || m.contains(k)) return (h.contains("iphone") || h.contains("ipad")) ? "📱 iPhone / iPad" : "📱 手机";

        // TTL 兜底（很准）
        if (d.ttl == 128) return "💻 电脑（Windows）";
        if (d.ttl == 64) return "📱 手机 / 平板（Android/iOS）";
        if (d.ttl == 255) return "📡 网络设备";
        if (d.ttl > 0 && d.ttl <= 64) return "📱 手机 / 平板";
        if (d.ttl > 64 && d.ttl <= 128) return "💻 电脑";

        // 厂商兜底
        String[] phoneBrand = {"小米", "华为", "苹果", "三星", "OPPO", "vivo", "魅族", "一加", "Google"};
        for (String b : phoneBrand) if (v.equals(b)) return "📱 手机 / 平板（" + v + "）";

        return "❓ 未知设备";
    }

    private void addDevRow(Dev d) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#0F000000"));
        int p = dp(8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(6);
        card.setLayoutParams(cp);

        TextView t1 = new TextView(this);
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        String head = d.type + "   " + d.ip;
        if (d.isGw) head += "   [网关]";
        if (d.isMe) head += "   [本机]";
        t1.setText(head);
        card.addView(t1);

        StringBuilder sb = new StringBuilder();
        if (d.vendor != null && d.vendor.length() > 0) sb.append("厂商 ").append(d.vendor);
        if (d.host != null && d.host.length() > 0) {
            if (sb.length() > 0) sb.append("   ");
            sb.append("主机名 ").append(d.host);
        }
        if (sb.length() > 0) {
            TextView t2 = new TextView(this);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t2.setText(sb.toString());
            card.addView(t2);
        }
        if (d.model != null && d.model.length() > 0) {
            TextView t3 = new TextView(this);
            t3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t3.setText("型号/页面标题：" + d.model);
            card.addView(t3);
        }
        if (d.ports != null && d.ports.length() > 0) {
            TextView t4 = new TextView(this);
            t4.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            t4.setText("开放端口 " + d.ports);
            card.addView(t4);
        }
        TextView t5 = new TextView(this);
        t5.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t5.setText("MAC " + (d.mac == null || d.mac.length() < 8 ? "读不到（Android 10+ 系统限制）" : d.mac)
                + (d.ttl > 0 ? "   TTL " + d.ttl : ""));
        card.addView(t5);

        devList.addView(card);
    }

    private boolean pingOnce(String ip) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/ping", "-c", "1", "-W", "1", ip});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean got = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("1 received") || line.contains("1 packets received")) got = true;
                else if (line.contains("time=")) got = true;
            }
            p.waitFor();
            return got;
        } catch (Exception e) {
            try {
                return InetAddress.getByName(ip).isReachable(600);
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private int pingTtl(String ip) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/ping", "-c", "1", "-W", "1", ip});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern pt = Pattern.compile("ttl=(\\d+)", Pattern.CASE_INSENSITIVE);
            while ((line = br.readLine()) != null) {
                Matcher m = pt.matcher(line);
                if (m.find()) {
                    p.destroy();
                    return Integer.parseInt(m.group(1));
                }
            }
            p.waitFor();
        } catch (Exception ignored) { }
        return -1;
    }

    private String resolveHost(String ip) {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> f = ex.submit(new Callable<String>() {
                public String call() {
                    try {
                        InetAddress a = InetAddress.getByName(ip);
                        String h = a.getCanonicalHostName();
                        if (h == null || h.equals(ip)) return "";
                        return h;
                    } catch (Exception e) {
                        return "";
                    }
                }
            });
            String h = f.get(1500, TimeUnit.MILLISECONDS);
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        } finally {
            ex.shutdownNow();
        }
    }

    private static final int[] PORTS = {22, 23, 53, 80, 139, 443, 445, 554, 631, 3389,
            5000, 7000, 8008, 8009, 8080, 9100, 5555, 62078};

    private Set<Integer> probePorts(String ip) {
        Set<Integer> open = new HashSet<>();
        for (int port : PORTS) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(ip, port), 260);
                open.add(port);
            } catch (Exception ignored) {
            } finally {
                try { s.close(); } catch (Exception ignored) { }
            }
        }
        return open;
    }

    private String httpTitle(String ip, int port) {
        HttpURLConnection c = null;
        try {
            URL u = new URL((port == 443 ? "https" : "http") + "://" + ip + ":" + port + "/");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(1200);
            c.setReadTimeout(1500);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            if (code >= 400) return "";
            String server = c.getHeaderField("Server");
            String powered = c.getHeaderField("X-Powered-By");
            String loc = c.getHeaderField("Location");
            StringBuilder sb = new StringBuilder();
            if (server != null && server.length() > 0) sb.append(server);
            if (powered != null && powered.length() > 0) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(powered);
            }
            InputStream in = c.getInputStream();
            byte[] buf = new byte[4096];
            int n = in.read(buf);
            in.close();
            if (n > 0) {
                String html = new String(buf, 0, n, "UTF-8");
                Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
                if (m.find()) {
                    String t = m.group(1).replaceAll("\\s+", " ").trim();
                    if (t.length() > 0) {
                        if (sb.length() > 0) sb.append(" · ");
                        sb.append(t.length() > 60 ? t.substring(0, 60) : t);
                    }
                }
            }
            if (loc != null && loc.length() > 0 && sb.length() == 0) sb.append("跳转 ").append(loc);
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Map<String, String> readArp() {
        Map<String, String> m = new HashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream("/proc/net/arp")));
            String line = br.readLine(); // 表头
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\s+");
                if (p.length >= 4) {
                    String ip = p[0];
                    String mac = p[3];
                    if (mac != null && mac.length() >= 17 && !mac.startsWith("00:00:00")) {
                        m.put(ip, mac.toUpperCase(Locale.US));
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (br != null) try { br.close(); } catch (Exception ignored) { }
        }
        return m;
    }

    // ============================================================
    // ping / 网络工具
    // ============================================================
    private interface PingView {
        void onTick(PingStat s);
    }

    private static class PingStat {
        int count, recv;
        double min = -1, max = 0, sum = 0, last = 0;

        double avg() { return recv == 0 ? 0 : sum / recv; }
        double jitter() { return recv < 2 ? 0 : max - min; }
        double loss() { return count == 0 ? 0 : (count - recv) * 100.0 / count; }
    }

    private PingStat pingHost(String host, int count, PingView view) {
        PingStat s = new PingStat();
        s.count = count;
        boolean parsed = false;
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/ping", "-c", String.valueOf(count), "-i", "0.2", "-W", "2", host});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            Pattern pat = Pattern.compile("time=([0-9.]+)\\s*ms");
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find()) {
                    double v = Double.parseDouble(m.group(1));
                    parsed = true;
                    s.recv++;
                    s.sum += v;
                    s.last = v;
                    if (s.min < 0 || v < s.min) s.min = v;
                    if (v > s.max) s.max = v;
                    if (view != null) view.onTick(s);
                }
            }
            try { p.waitFor(); } catch (Exception ignored) { }
        } catch (Exception e) {
            parsed = false;
        } finally {
            if (p != null) try { p.destroy(); } catch (Exception ignored) { }
        }

        if (!parsed || s.recv == 0) {
            // 回退：isReachable 逐个试
            int got = 0;
            double sum = 0;
            double mn = -1, mx = 0;
            for (int i = 0; i < Math.min(count, 4); i++) {
                long t0 = System.currentTimeMillis();
                try {
                    InetAddress a = InetAddress.getByName(host);
                    if (a.isReachable(1500)) {
                        double v = System.currentTimeMillis() - t0;
                        got++; sum += v; s.last = v;
                        if (mn < 0 || v < mn) mn = v;
                        if (v > mx) mx = v;
                        s.recv = got;
                        s.sum = sum;
                        s.min = mn;
                        s.max = mx;
                        if (view != null) view.onTick(s);
                    }
                } catch (Exception ignored) { }
            }
        }
        return s;
    }

    private static class WifiBrief {
        int rssi = 0, linkMbps = 0;
        String band = "?";
    }

    private WifiBrief readWifi() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo wi = wm.getConnectionInfo();
            if (wi == null) return null;
            WifiBrief b = new WifiBrief();
            b.rssi = wi.getRssi();
            int speed = wi.getLinkSpeed();
            b.linkMbps = speed;
            if (Build.VERSION.SDK_INT >= 21) {
                int f = wi.getFrequency();
                b.band = f > 4000 ? "5G(" + f + "MHz)" : (f > 0 ? "2.4G(" + f + "MHz)" : "?");
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private String getGatewayIp() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "";
            LinkProperties lp = cm.getLinkProperties(ConnectivityManager.TYPE_WIFI);
            if (lp == null) {
                android.net.Network n = cm.getActiveNetwork();
                if (n != null) lp = cm.getLinkProperties(n);
            }
            if (lp == null) return "";
            for (RouteInfo r : lp.getRoutes()) {
                if (r.isDefaultRoute() && r.getGateway() instanceof java.net.Inet4Address) {
                    return r.getGateway().getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private String getDnsList() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "";
            LinkProperties lp = cm.getLinkProperties(ConnectivityManager.TYPE_WIFI);
            if (lp == null) return "";
            StringBuilder sb = new StringBuilder();
            for (InetAddress a : lp.getDnsServers()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.getHostAddress());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getLocalIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "";
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) return "";
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) {
            return "";
        }
    }

    private String subnetBase(String ip) {
        if (ip == null || ip.length() == 0) return null;
        int idx = ip.lastIndexOf('.');
        if (idx <= 0) return null;
        return ip.substring(0, idx);
    }

    private static int ipInt(String ip) {
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return 0;
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
            int c = Integer.parseInt(p[2]), d = Integer.parseInt(p[3]);
            return ((a & 0xff) << 24) | ((b & 0xff) << 16) | ((c & 0xff) << 8) | (d & 0xff);
        } catch (Exception e) {
            return 0;
        }
    }

    // ============================================================
    // UI 小工具
    // ============================================================
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void copyText(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("WiFi体检报告", s));
            toast("已复制，可以直接发给房东");
        } catch (Exception e) {
            toast("复制失败：" + e.getMessage());
        }
    }

    private void scrollTo(final View v) {
        ui.postDelayed(new Runnable() {
            public void run() {
                try { scroll.smoothScrollTo(0, v.getTop()); } catch (Exception ignored) { }
            }
        }, 120);
    }
}
