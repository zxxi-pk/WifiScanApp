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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private ListView listView;
    private TextView tvVerdict;
    private Button btnScan, btnCheck, btnRouter, btnCopy, btnWifiSet, btnBattery, btnDns;
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String gatewayIp = "192.168.1.1";
    private String lastAdvice = "";
    private volatile boolean checking = false;

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

        tvVerdict.setMovementMethod(new ScrollingMovementMethod());
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        listView.setAdapter(adapter);

        btnScan.setOnClickListener(v -> startScan());
        btnCheck.setOnClickListener(v -> runCheck());

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
                    + "2. 避开晚上 8–11 点高峰期测一次对比\n"
                    + "3. 报告发给房东/运营商，让他查套餐带宽与被限速";
            advice = "WiFi 体检报告\n"
                    + "第一跳正常，但出网延迟 " + f(pAvg) + "ms、丢包 " + f(pLoss)
                    + "%、抖动 " + f(pJit) + "ms\n"
                    + "结论：宽带侧问题（带宽不足/被限速/被占满）。\n"
                    + "需要你那边做的：确认宽带套餐是多少兆，检查是否被限速，"
                    + "或路由器是否被其他设备占满。";
        } else {
            verdict = "链路健康：延迟和丢包都正常\n"
                    + "说明不是信号也不是干扰，卡只可能是【带宽不够】。"
                    + "去网页版测一下下行到底有多少 Mbps，再看是不是被限速。";
            action = "✅ 你能直接做的：\n"
                    + "1. 打开网页测速版量实际下行 Mbps\n"
                    + "2. 换私有 DNS，看网页打开是否变快\n"
                    + "3. 若下行只有几兆 → 就是套餐带宽问题，手机端改不了";
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
            toast("先点「一键体检」生成报告");
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("WiFi体检报告", lastAdvice));
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
