package com.example.wifiscan;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private WifiManager wifiManager;
    private ListView listView;
    private TextView verdictView;
    private Button scanBtn;
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            boolean ok = i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (ok) showResults();
            else Toast.makeText(MainActivity.this, "扫描失败，请重试", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        listView = findViewById(R.id.list);
        verdictView = findViewById(R.id.verdict);
        scanBtn = findViewById(R.id.scan);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        listView.setAdapter(adapter);
        scanBtn.setOnClickListener(v -> startScan());
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    private void startScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "请先打开 WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        rows.clear();
        adapter.notifyDataSetChanged();
        verdictView.setText("扫描中…");
        boolean started = wifiManager.startScan();
        if (!started) Toast.makeText(this, "startScan 返回 false，稍后重试", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startScan();
    }

    private static int freqToChan(int f) {
        if (f >= 2412 && f <= 2484) return (f == 2484) ? 14 : (f - 2407) / 5;
        if (f >= 5035 && f <= 5980) return (f - 5000) / 5;
        if (f >= 5955 && f <= 7115) return (f - 5950) / 5;
        return -1;
    }

    private void showResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        WifiInfo info = wifiManager.getConnectionInfo();
        String myBssid = info.getBSSID();
        Map<Integer, Integer> chCount = new HashMap<>();
        int myChan = -1, myLevel = 0;
        for (ScanResult r : results) {
            int ch = freqToChan(r.frequency);
            if (ch <= 0) continue;
            chCount.put(ch, chCount.getOrDefault(ch, 0) + 1);
            if (myBssid != null && myBssid.equalsIgnoreCase(r.BSSID)) { myChan = ch; myLevel = r.level; }
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
            sb.append("同信道 AP：").append(onMine).append(" 个，相邻重叠信道 AP：").append(adj).append(" 个\n");
            if (onMine + adj >= 4) {
                sb.append("⚠️ 信道拥挤！建议让房东把路由改到最空的 1/6/11 之一\n");
                sb.append("最空信道建议：信道 ").append(bestChannel(chCount));
            } else {
                sb.append("✅ 信道不算挤，卡顿更可能是带宽/限速或隔墙信号差");
            }
        } else if (myChan > 14) {
            sb.append("你在 5G 频段（信道 ").append(myChan).append("），干扰通常少，卡顿多为带宽问题");
        } else {
            sb.append("未识别到你连接的 AP 信道，请确认已连上房东 WiFi");
        }
        sb.append("\n口诀：满格慢=带宽问题；少格慢=信号问题");
        verdictView.setText(sb.toString());
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
        int best = 1, bestC = 99;
        for (int c : new int[]{1, 6, 11}) {
            int c2 = m.getOrDefault(c, 0) + countAdjacent(m, c);
            if (c2 < bestC) { bestC = c2; best = c; }
        }
        return best;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
    }
}
