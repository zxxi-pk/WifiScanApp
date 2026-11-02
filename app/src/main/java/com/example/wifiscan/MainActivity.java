package com.example.wifiscan;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private ListView listView;
    private TextView tvVerdict;
    private Button btnScan, btnCheck, btnRouter, btnCopy, btnWifiSet, btnBattery, btnDns, btnSpeed, btnDevScan;
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String gatewayIp = "192.168.1.1";
    private String lastAdvice = "";
    private volatile boolean checking = false;
    private volatile boolean scanningDevices = false;

    // ===== 厂商 OUI 表（常见家用设备，够认出陌生设备）=====
    private static final Map<String, String> OUI = new HashMap<>();
    private static void putOui(String hex, String name) {
        OUI.put(hex.toUpperCase().replace(":", "").replace("-", ""), name);
    }
    static {
        putOui("001D0F", "TP-Link"); putOui("14CC20", "TP-Link"); putOui("A42BB0", "TP-Link");
        putOui("50642B", "TP-Link"); putOui("3C46D8", "TP-Link"); putOui("F4F26D", "TP-Link");
        putOui("04CF8C", "小米"); putOui("286C07", "小米"); putOui("64CC2E", "小米");
        putOui("7811DC", "小米"); putOui("8CBEBE", "小米"); putOui("009E11", "小米");
        putOui("00E0FC", "华为"); putOui("283CE4", "华为"); putOui("4846FB", "华为");
        putOui("70723C", "华为"); putOui("9C28EF", "华为"); putOui("F4C714", "华为");
        putOui("28CFE9", "苹果"); putOui("8C8590", "苹果"); putOui("001B63", "苹果");
        putOui("3C0754", "苹果"); putOui("A483E7", "苹果"); putOui("F0DBF8", "苹果");
        putOui("002637", "三星"); putOui("3423BA", "三星"); putOui("5C0A5B", "三星");
        putOui("8C7712", "三星"); putOui("E8508B", "三星"); putOui("C0BDD1", "三星");
        putOui("001A11", "Google"); putOui("3C5AB4", "Google"); putOui("F4F5D8", "Google");
        putOui("B827EB", "树莓派"); putOui("DCA632", "树莓派"); putOui("E45F01", "树莓派");
        putOui("F09FC2", "Ubiquiti"); putOui("788A20", "Ubiquiti"); putOui("0418D6", "Ubiquiti");
        putOui("001132", "群晖"); putOui("001B21", "Intel"); putOui("001E67", "Intel");
        putOui("3C970E", "Intel"); putOui("A4BB6D", "Intel"); putOui("00E04C", "Realtek");
        putOui("000C29", "VMware"); putOui("005056", "VMware"); putOui("00155D", "微软");
        putOui("F4F26D", "TP-Link"); putOui("C46E1F", "TP-Link"); putOui("B0BE76", "TP-Link");
        putOui("24A43C", "华硕"); putOui("1C872C", "华硕"); putOui("2C56DC", "华硕");
        putOui("001122", "Tenda"); putOui("C83A35", "Tenda"); putOui("D8320E", "Tenda");
        putOui("00A0C9", "Intel"); putOui("00E0B8", "TP-Link");
    }

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            boolean ok = i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (ok) showScanResults();
            else Toast.makeText(MainActivity.this, "扫描失败，请重试", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        listView = findViewById(R.id.list);
        tvVerdict = findViewById(R.id.verdict);
        btnScan = findViewById(R.id.scan);
        btnCheck = findViewById(R.id.check);
        btnRouter = findViewById(R.id.btnRouter);
        btnCopy = findViewById(R.id.btnCopy);
        btnWifiSet = findViewById(R.id.btnWifiSet);
        btnBattery = findViewById(R.id.btnBattery);
        btnDns = findViewById(R.id.btnDns);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnDevScan = findViewById(R.id.btnDevScan);

        tvVerdict.setMovementMethod(new ScrollingMovementMethod());
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        listView.setAdapter(adapter);

        btnScan.setOnClickListener(v -> startScan());
        btnCheck.setOnClickListener(v -> runCheck());
        btnSpeed.setOnClickListener(v -> runSpeedTest());
        btnDevScan.setOnClickListener(v -> runDeviceScan());

        btnRouter.setOnClickListener(v -> openRouter());
        btnCopy.setOnClickListener(v -> copyAdvice());
        btnWifiSet.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
            catch (Exception e) { toast("打不开 WiFi 设置"); }
        });
        btnBattery.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e) { toast("打不开省电设置"); }
        });
        btnDns.setOnClickListener(v -> openDns());

        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    // ================= 一键体检 =================

    private void runCheck() {
        if (checking) { toast("正在体检，稍等"); return; }
        checking = true;
        tvVerdict.setText("体检中…约 10 秒\n正在测：到路由器(第一跳) / 到外网 / 丢包与抖动");
        new Thread(() -> {
            String gw = getGatewayIp();
            List<String> dns = getDnsList();
            PingStat g = pingHost(gw, 8);
            PingStat p = pingHost("223.5.5.5", 8);
            String report = buildReport(gw, dns, g, p);
            runOnUiThread(() -> { tvVerdict.setText(report); tvVerdict.scrollTo(0, 0); });
            checking = false;
        }).start();
    }

    private String getGatewayIp() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return gatewayIp;
            Network active = cm.getActiveNetwork();
            if (active == null) return gatewayIp;
            LinkProperties lp = cm.getLinkProperties(active);
            if (lp == null) return gatewayIp;
            for (RouteInfo r : lp.getRoutes()) {
                if (r.isDefaultRoute() && r.getGateway() != null
                        && !r.getGateway().isAnyLocalAddress()
                        && r.getGateway() instanceof java.net.Inet4Address) {
                    gatewayIp = r.getGateway().getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return gatewayIp;
    }

    private List<String> getDnsList() {
        List<String> out = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return out;
            Network active = cm.getActiveNetwork();
            if (active == null) return out;
            LinkProperties lp = cm.getLinkProperties(active);
            if (lp == null) return out;
            for (InetAddress a : lp.getDnsServers()) out.add(a.getHostAddress());
        } catch (Exception ignored) {}
        return out;
    }

    private static class PingStat {
        int count = 0, recv = 0;
        double min = -1, max = -1, sum = 0;
        double avg() { return recv > 0 ? sum / recv : -1; }
        double jitter() { return (recv > 1 && min >= 0 && max >= 0) ? (max - min) : 0; }
        double loss() { return count > 0 ? (count - recv) * 100.0 / count : 100; }
        String line() {
            if (recv == 0) return "  全部超时（丢包 100%），链路可能已断\n";
            return String.format("  平均 %s ms · 抖动 %s ms · 丢包 %s%%（%d/%d 通）\n",
                    f(avg()), f(jitter()), f(loss()), recv, count);
        }
    }

    private PingStat pingHost(String host, int count) {
        PingStat st = new PingStat();
        st.count = count;
        Process proc = null;
        try {
            proc = new ProcessBuilder("/system/bin/ping", "-c", String.valueOf(count),
                    "-i", "0.2", "-W", "2", host)
                    .redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            Pattern pat = Pattern.compile("time=([0-9.]+)\\s*ms");
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = pat.matcher(line);
                if (m.find()) {
                    double t = Double.parseDouble(m.group(1));
                    st.recv++;
                    st.sum += t;
                    if (st.min < 0 || t < st.min) st.min = t;
                    if (t > st.max) st.max = t;
                }
            }
            proc.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (proc != null) { try { proc.destroy(); } catch (Exception ignored) {} }
        }
        if (st.recv == 0) {
            try {
                InetAddress ia = InetAddress.getByName(host);
                for (int i = 0; i < count; i++) {
                    long s = System.nanoTime();
                    boolean ok = ia.isReachable(1200);
                    long ms = (System.nanoTime() - s) / 1000000L;
                    if (ok) {
                        st.recv++; st.sum += ms;
                        if (st.min < 0 || ms < st.min) st.min = ms;
                        if (ms > st.max) st.max = ms;
                    }
                }
            } catch (Exception ignored) {}
        }
        return st;
    }

    private static String f(double v) { return String.format("%.1f", v); }
    private static String fMB(long bytes) { return String.format("%.1f MB", bytes / 1048576.0); }

    private static String joinList(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(list.get(i));
        }
        return b.toString();
    }

    private static String levelText(int rssi) {
        if (rssi >= -55) return "很强";
        if (rssi >= -67) return "良好";
        if (rssi >= -75) return "一般";
        if (rssi >= -85) return "弱";
        return "极弱";
    }

    private String buildReport(String gw, List<String> dns, PingStat g, PingStat p) {
        StringBuilder sb = new StringBuilder();

        WifiInfo info = wifiManager.getConnectionInfo();
        int rssi = info != null ? info.getRssi() : 0;
        int freq = 0;
        try { freq = info != null ? info.getFrequency() : 0; } catch (Exception ignored) {}
        String band = freq >= 2400 && freq < 3000 ? "2.4G"
                : (freq >= 4900 ? "5G" : "未知");

        sb.append("【当前连接】\n");
        sb.append("信号 ").append(rssi).append(" dBm（").append(levelText(rssi))
          .append("） 频段 ").append(band).append("\n");
        if (!dns.isEmpty()) sb.append("DNS ").append(joinList(dns)).append("\n");
        sb.append("\n【第一跳：你的手机 → 路由器 ").append(gw).append("】\n");
        sb.append(g.line());
        sb.append("\n【出网：你的手机 → 外网 223.5.5.5】\n");
        sb.append(p.line());

        double gAvg = g.avg(), pAvg = p.avg();
        double gLoss = g.loss(), pLoss = p.loss();
        double gJit = g.jitter(), pJit = p.jitter();

        String verdict, advice;
        String action;
        if (g.recv == 0 || gLoss > 15 || gAvg > 40 || gJit > 60) {
            verdict = "问题出在【你和路由器之间】\n"
                    + "第一跳就丢包/高延迟，说明是信号差（隔着墙）或信道被邻居挤爆。"
                    + "这一层你自己能改的部分最多。";
            action = "✅ 你能直接做的：\n"
                    + "1. 点下面「扫描周边 WiFi」→ 看该改到哪个信道\n"
                    + "2. 点「打开路由器后台」→ 无线设置 → 信道改成建议值\n"
                    + "3. 能进 5G 就点「打开 WiFi 设置」连 5G 那个名字\n"
                    + "4. 别贴着路由器，也别隔两堵墙";
            advice = "WiFi 体检报告\n"
                    + "信号 " + rssi + "dBm · 第一跳丢包 " + f(gLoss) + "% · 延迟 " + f(gAvg)
                    + "ms · 抖动 " + f(gJit) + "ms\n"
                    + "结论：到路由器这一跳就不稳，原因是信号衰减或信道干扰。\n"
                    + "需要你那边做的：把无线信道改成 1 或 11，或把路由器挪到更靠中间的位置。";
        } else if (pLoss > 10 || pAvg > 120 || pJit > 80) {
            verdict = "问题出在【路由器往外（宽带侧）】\n"
                    + "到路由器这一跳很好，一出网就丢包/高延迟，是宽带本身不行、被限速，"
                    + "或者晚上被同住的人占满了。这一层你手机改不了，只能谈或自己接流量。";
            action = "✅ 你能直接做的：\n"
                    + "1. 换私有 DNS（点下面按钮），排掉 DNS 慢导致的「假卡」\n"
                    + "2. 避开晚上 8–11 点高峰期再测一次对比\n"
                    + "3. 点「测网速」量上下行 Mbps，把报告发给房东/运营商";
            advice = "WiFi 体检报告\n"
                    + "第一跳正常，但出网延迟 " + f(pAvg) + "ms、丢包 " + f(pLoss)
                    + "%、抖动 " + f(pJit) + "ms\n"
                    + "结论：宽带侧问题（带宽不足/被限速/被占满）。\n"
                    + "需要你那边做的：确认宽带套餐是多少兆，检查是否被限速，"
                    + "或路由器是否被其他设备占满。";
        } else {
            verdict = "链路健康：延迟和丢包都正常\n"
                    + "说明不是信号也不是干扰，卡只可能是【带宽不够】。"
                    + "点下面「测网速」量一下上下行到底多少 Mbps，再看是不是被限速。";
            action = "✅ 你能直接做的：\n"
                    + "1. 点「测网速」量实际上下行 Mbps\n"
                    + "2. 点「扫局域网设备」看有没有陌生设备在蹭网\n"
                    + "3. 换私有 DNS，看网页打开是否变快\n"
                    + "4. 若下行只有几兆 → 就是套餐带宽问题，手机端改不了";
            advice = "WiFi 体检报告\n"
                    + "链路延迟丢包均正常，信号 " + rssi + "dBm。\n"
                    + "结论：带宽不足，非信号/干扰问题。\n"
                    + "需要你那边做的：确认宽带套餐带宽，检查限速设置。";
        }

        sb.append("\n【结论】\n").append(verdict).append("\n");
        sb.append("\n").append(action).append("\n");

        lastAdvice = advice;
        return sb.toString();
    }

    // ================= 测速（上下行 Mbps） =================

    private static class SpeedResult {
        double mbps = -1;
        long ms = 0;
        long bytes = 0;
        boolean ok = false;
        String err = "";
    }

    private void runSpeedTest() {
        if (checking) { toast("正在测速或体检，稍等"); return; }
        checking = true;
        tvVerdict.setText("测速中…\n下载 8MB + 上传 3MB，请保持 WiFi 连接，约 10–30 秒");
        new Thread(() -> {
            SpeedResult down = measureDownload();
            SpeedResult up = measureUpload();
            StringBuilder sb = new StringBuilder();
            sb.append("【网速实测】\n");
            if (down.ok) {
                sb.append("下载 ").append(f(down.mbps)).append(" Mbps")
                  .append("（").append(down.ms).append(" ms / ").append(fMB(down.bytes)).append("）\n");
            } else {
                sb.append("下载失败：").append(down.err).append("\n");
            }
            if (up.ok) {
                sb.append("上传 ").append(f(up.mbps)).append(" Mbps")
                  .append("（").append(up.ms).append(" ms / ").append(fMB(up.bytes)).append("）\n");
            } else {
                sb.append("上传失败：").append(up.err).append("\n");
            }
            if (down.ok) {
                double d = down.mbps;
                if (d < 8) {
                    sb.append("\n结论：下行只有 ").append(f(d)).append(" Mbps，偏低。看 1080P 会卡，属带宽/限速问题。");
                } else if (d < 25) {
                    sb.append("\n结论：下行 ").append(f(d)).append(" Mbps，基本够刷视频，多人同时用会卡，属带宽不足。");
                } else {
                    sb.append("\n结论：下行 ").append(f(d)).append(" Mbps，带宽够用。卡就不是带宽问题，回到「一键体检」看延迟/丢包。");
                }
            }
            StringBuilder a = new StringBuilder("WiFi 测速报告\n");
            a.append("下载 ").append(down.ok ? f(down.mbps) + " Mbps" : "失败").append(" / ");
            a.append("上传 ").append(up.ok ? f(up.mbps) + " Mbps" : "失败").append("\n");
            a.append("测速节点 speed.cloudflare.com\n");
            a.append("说明：若下行远低于套餐带宽，基本可判定限速或带宽不足。");
            lastAdvice = a.toString();
            String text = sb.toString();
            runOnUiThread(() -> { tvVerdict.setText(text); tvVerdict.scrollTo(0, 0); });
            checking = false;
        }).start();
    }

    private SpeedResult measureDownload() {
        SpeedResult r = new SpeedResult();
        final int WARM = 1_000_000;
        final int TOTAL = 8_000_000;
        HttpURLConnection c = null;
        try {
            URL u = new URL("https://speed.cloudflare.com/__down?bytes=" + TOTAL);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent", "WifiCheck/1.2");
            c.connect();
            InputStream in = new BufferedInputStream(c.getInputStream(), 32768);
            byte[] buf = new byte[16384];
            long totalRead = 0, measured = 0, tStart = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                totalRead += n;
                if (tStart == 0) {
                    if (totalRead >= WARM) tStart = System.nanoTime();
                } else {
                    measured += n;
                }
            }
            in.close();
            r.bytes = measured;
            if (tStart == 0) { r.err = "下载量不足，测速样本太小"; return r; }
            r.ms = (System.nanoTime() - tStart) / 1_000_000L;
            if (r.ms <= 0) r.ms = 1;
            r.mbps = (measured * 8.0 / 1_000_000.0) / (r.ms / 1000.0);
            r.ok = true;
        } catch (Exception e) {
            r.err = shortErr(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private SpeedResult measureUpload() {
        SpeedResult r = new SpeedResult();
        final int TOTAL = 3_000_000;
        HttpURLConnection c = null;
        try {
            URL u = new URL("https://speed.cloudflare.com/__up?bytes=" + TOTAL);
            c = (HttpURLConnection) u.openConnection();
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            c.setFixedLengthStreamingMode(TOTAL);
            c.setRequestProperty("Content-Type", "application/octet-stream");
            c.setRequestProperty("User-Agent", "WifiCheck/1.2");
            byte[] chunk = new byte[32768];
            Arrays.fill(chunk, (byte) 0x41);
            long tStart = System.nanoTime();
            OutputStream os = c.getOutputStream();
            int sent = 0;
            while (sent < TOTAL) {
                int n = Math.min(chunk.length, TOTAL - sent);
                os.write(chunk, 0, n);
                sent += n;
            }
            os.flush();
            int code = c.getResponseCode();
            r.ms = (System.nanoTime() - tStart) / 1_000_000L;
            if (r.ms <= 0) r.ms = 1;
            if (code >= 200 && code < 400) {
                r.bytes = TOTAL;
                r.mbps = (TOTAL * 8.0 / 1_000_000.0) / (r.ms / 1000.0);
                r.ok = true;
            } else {
                r.err = "HTTP " + code;
            }
            os.close();
        } catch (Exception e) {
            r.err = shortErr(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private static String shortErr(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        if (m.length() > 60) m = m.substring(0, 60);
        return e.getClass().getSimpleName() + ": " + m;
    }

    // ================= 局域网设备扫描 =================

    private void runDeviceScan() {
        if (scanningDevices) { toast("正在扫描，稍等"); return; }
        scanningDevices = true;
        rows.clear();
        adapter.notifyDataSetChanged();
        tvVerdict.setText("扫描局域网设备…约 10–15 秒\n正在逐个探测同网段主机（不影响上网）");
        new Thread(() -> {
            String gw = getGatewayIp();
            String base = subnetBase(gw);
            String myIp = getLocalIp();
            final Set<String> up = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

            ExecutorService pool = Executors.newFixedThreadPool(32);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 1; i <= 254; i++) {
                final String ip = base + i;
                futures.add(pool.submit(() -> { if (pingOnce(ip)) up.add(ip); }));
            }
            for (Future<?> fu : futures) { try { fu.get(); } catch (Exception ignored) {} }
            pool.shutdown();

            Map<String, String> arp = readArp();
            for (String ip : arp.keySet()) {
                if (ip.startsWith(base)) up.add(ip);
            }

            List<String> ips = new ArrayList<>(up);
            Collections.sort(ips, new Comparator<String>() {
                public int compare(String a, String b) { return Integer.compare(ipInt(a), ipInt(b)); }
            });

            List<String> lines = new ArrayList<>();
            int unknown = 0;
            for (String ip : ips) {
                String mac = arp.get(ip);
                String vendor = ouiVendor(mac);
                String tags = "";
                if (ip.equals(gw)) tags += " 网关";
                if (myIp != null && ip.equals(myIp)) tags += " 本机";
                boolean stranger = "未知厂商".equals(vendor)
                        && !ip.equals(gw)
                        && !(myIp != null && ip.equals(myIp));
                if (stranger) unknown++;
                lines.add(ip + "  " + vendor + "  " + (mac != null ? mac : "无MAC")
                        + (tags.isEmpty() ? "" : ("  ←" + tags.trim()))
                        + (stranger ? "  ⚠" : ""));
            }

            StringBuilder head = new StringBuilder();
            head.append("局域网设备 ").append(ips.size()).append(" 台");
            if (unknown > 0) head.append("\n其中 ").append(unknown).append(" 台厂商未知（⚠）最可能是陌生设备");
            head.append("\n共网段：").append(base).append("x\n\n");

            final List<String> flines = lines;
            final String fhead = head.toString();
            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(flines);
                adapter.notifyDataSetChanged();
                tvVerdict.setText(fhead
                        + "\n怎么用：看到 ⚠ 陌生 MAC，就点「打开路由器后台」→ 已连接设备 → 禁用\n"
                        + "口诀：满格慢=带宽问题；少格慢=信号问题");
                tvVerdict.scrollTo(0, 0);
            });
            scanningDevices = false;
        }).start();
    }

    private static boolean pingOnce(String ip) {
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", ip)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) { try { p.destroy(); } catch (Exception ignored) {} }
        }
    }

    private static String subnetBase(String gw) {
        if (gw == null || gw.isEmpty()) return "192.168.1.";
        String[] p = gw.split("\\.");
        if (p.length == 4) return p[0] + "." + p[1] + "." + p[2] + ".";
        if (p.length == 3) return gw + ".";
        return "192.168.1.";
    }

    private static int ipInt(String ip) {
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return 0;
            return (Integer.parseInt(p[0]) << 24) | (Integer.parseInt(p[1]) << 16)
                    | (Integer.parseInt(p[2]) << 8) | Integer.parseInt(p[3]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Map<String, String> readArp() {
        Map<String, String> m = new HashMap<>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/net/arp"));
            String line = r.readLine();
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4) {
                    String ip = p[0];
                    String mac = p[3];
                    if (mac != null && mac.length() >= 11
                            && !"00:00:00:00:00:00".equals(mac)
                            && !"00:00:00:00:00:00".equalsIgnoreCase(mac)) {
                        m.put(ip, mac.toUpperCase());
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) { try { r.close(); } catch (Exception ignored) {} }
        }
        return m;
    }

    private static String ouiVendor(String mac) {
        if (mac == null) return "未知厂商";
        String h = mac.toUpperCase().replace(":", "").replace("-", "");
        if (h.length() < 6) return "未知厂商";
        String v = OUI.get(h.substring(0, 6));
        return v != null ? v : "未知厂商";
    }

    private String getLocalIp() {
        try {
            WifiInfo wi = wifiManager.getConnectionInfo();
            if (wi == null) return null;
            int ip = wi.getIpAddress();
            if (ip == 0) return null;
            return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        } catch (Exception e) {
            return null;
        }
    }

    // ================= 可执行动作 =================

    private void openRouter() {
        getGatewayIp();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + gatewayIp));
            startActivity(i);
            toast("打开 " + gatewayIp + "（未登录就用路由器背面写的密码）");
        } catch (Exception e) {
            toast("没有浏览器可用");
        }
    }

    private void copyAdvice() {
        if (lastAdvice == null || lastAdvice.isEmpty()) {
            toast("先点「一键体检」或「测网速」生成报告");
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("WiFi报告", lastAdvice));
            toast("报告已复制，可直接发给房东/运营商");
        } catch (Exception e) {
            toast("复制失败");
        }
    }

    private void openDns() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));
                toast("选「私人 DNS 提供商」→ 填 dns.alidns.com 或 1dot1dot1dot1.cloudflare-dns.com");
            } else {
                toast("系统低于 9.0，没有私人 DNS 选项");
            }
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
            catch (Exception e2) { toast("打不开网络设置"); }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ================= 周边扫描（信道分析） =================

    private void startScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            toast("请先打开 WiFi");
            return;
        }
        rows.clear();
        adapter.notifyDataSetChanged();
        tvVerdict.setText("扫描中…");
        boolean started = wifiManager.startScan();
        if (!started) toast("系统限流，等两秒再点");
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startScan();
        else if (req == 1) toast("不给定位权限就扫不到周边 WiFi");
    }

    private static int freqToChan(int f) {
        if (f >= 2412 && f <= 2484) return (f == 2484) ? 14 : (f - 2407) / 5;
        if (f >= 5035 && f <= 5980) return (f - 5000) / 5;
        if (f >= 5955 && f <= 7115) return (f - 5950) / 5;
        return -1;
    }

    private void showScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        WifiInfo info = wifiManager.getConnectionInfo();
        String myBssid = info != null ? info.getBSSID() : null;
        Map<Integer, Integer> chCount = new HashMap<>();
        int myChan = -1, myLevel = 0;

        for (ScanResult r : results) {
            int ch = freqToChan(r.frequency);
            if (ch <= 0) continue;
            chCount.put(ch, chCount.getOrDefault(ch, 0) + 1);
            if (myBssid != null && myBssid.equalsIgnoreCase(r.BSSID)) {
                myChan = ch; myLevel = r.level;
            }
        }

        rows.add("周边 AP 共 " + results.size() + " 个");
        for (ScanResult r : results) {
            int ch = freqToChan(r.frequency);
            String band = r.frequency < 3000 ? "2.4G" : (r.frequency < 5950 ? "5G" : "6G");
            String enc = "OPEN";
            if (r.capabilities.contains("WPA3")) enc = "WPA3";
            else if (r.capabilities.contains("WPA2")) enc = "WPA2";
            else if (r.capabilities.contains("WPA")) enc = "WPA";
            else if (r.capabilities.contains("WEP")) enc = "WEP";
            boolean mine = myBssid != null && myBssid.equalsIgnoreCase(r.BSSID);
            rows.add(String.format("%s | 信道%d %s | %ddBm %s | %s",
                    (r.SSID == null || r.SSID.isEmpty()) ? "<隐藏>" : r.SSID,
                    ch, band, r.level, mine ? "★你" : "", enc));
        }

        StringBuilder sb = new StringBuilder();
        if (myChan > 0 && myChan <= 14) {
            int onMine = chCount.getOrDefault(myChan, 0);
            int adj = countAdjacent(chCount, myChan);
            sb.append("你的 AP 在 2.4G 信道 ").append(myChan)
              .append("，信号 ").append(myLevel).append("dBm\n");
            sb.append("同信道 AP：").append(onMine)
              .append(" 个，相邻重叠信道 AP：").append(adj).append(" 个\n\n");
            if (onMine + adj >= 4) {
                int best = bestChannel(chCount);
                sb.append("⚠️ 信道拥挤，这是卡顿元凶之一\n");
                sb.append("✅ 该改到：信道 ").append(best).append("\n");
                sb.append("改法：点下面「开路由后台」→ 无线设置 → 信道 → 选 ")
                  .append(best).append(" → 保存重启");
            } else {
                sb.append("✅ 信道不算挤\n卡顿更可能是带宽、限速，或隔墙信号衰减");
            }
        } else if (myChan > 14) {
            sb.append("你在 5G 频段（信道 ").append(myChan).append("）\n");
            sb.append("5G 干扰少，卡顿基本是带宽/距离问题，不用折腾信道");
        } else {
            sb.append("没识别到你连的 AP，确认已连上房东 WiFi");
        }
        sb.append("\n\n口诀：满格慢=带宽问题；少格慢=信号问题");
        tvVerdict.setText(sb.toString());
        tvVerdict.scrollTo(0, 0);
        adapter.notifyDataSetChanged();
    }

    private int countAdjacent(Map<Integer, Integer> m, int ch) {
        int sum = 0;
        for (int d = -4; d <= 4; d++) {
            if (d == 0) continue;
            int c = ch + d;
            if (c >= 1 && c <= 14) sum += m.getOrDefault(c, 0);
        }
        return sum;
    }

    private int bestChannel(Map<Integer, Integer> m) {
        int best = 1, bestC = Integer.MAX_VALUE;
        for (int c : new int[]{1, 6, 11}) {
            int load = m.getOrDefault(c, 0) + countAdjacent(m, c);
            if (load < bestC) { bestC = load; best = c; }
        }
        return best;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
    }
}
