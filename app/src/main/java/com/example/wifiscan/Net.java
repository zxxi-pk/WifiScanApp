package com.example.wifiscan;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

/** 网络探测工具集：ping / 吞吐 / ARP / 设备指纹。全部为静态方法，不持有 Activity。 */
final class Net {

    private Net() {}

    interface PingView { void onTick(PingStat s); }
    interface Meter { void onProgress(long done, long total, double mbps); }

    // ---------------- 数据结构 ----------------

    static class PingStat {
        int count, recv;
        double min = -1, max = 0, sum = 0, last = 0;
        double avg() { return recv == 0 ? 0 : sum / recv; }
        double jitter() { return recv == 0 ? 0 : max - min; }
        double loss() { return count == 0 ? 0 : (count - recv) * 100.0 / count; }
    }

    static class SpeedResult {
        double mbps;
        long ms, bytes;
        boolean ok;
        String err = "";
        static SpeedResult fail(Exception e) {
            SpeedResult r = new SpeedResult();
            r.ok = false;
            r.err = e.getClass().getSimpleName() + ": " + e.getMessage();
            return r;
        }
    }

    static class WifiBrief {
        String ssid = "";
        int rssi = 0, linkMbps = 0, freq = 0;
        String band = "?";
    }

    static class Dev {
        String ip = "", mac = "", vendor = "", host = "", type = "未知设备", ports = "";
        String mdns = "";
        int ttl = -1;
        boolean isGw, isMe;
        /** 疑似占用带宽等级：0 低 / 1 中 / 2 高。是推断，不是实测流量。 */
        int suspect = 0;
        /** 推断依据，给用户看的实话。 */
        String suspectWhy = "";
    }

    /** 用可观测的间接信号推断「这台设备像不像在占带宽」。
     *  说明：普通 App 拿不到局域网内其他设备的实时流量，这里只做可信推断，
     *  等级和依据一起显示，不编造 Mbps 数字。 */
    static void computeSuspect(Dev d, Set<Integer> open) {
        if (d.isGw) {
            d.suspect = 0;
            d.suspectWhy = "路由器/光猫本身";
            return;
        }
        if (d.isMe) {
            d.suspect = 0;
            d.suspectWhy = "本机";
            return;
        }
        int score = 0;
        StringBuilder why = new StringBuilder();

        if ("未知设备".equals(d.type)) {
            score += 2;
            why.append("认不出来");
        }
        if (open != null) {
            boolean media = open.contains(554) || open.contains(8009) || open.contains(5000) || open.contains(7000);
            if (media) {
                score += 2;
                if (why.length() > 0) why.append("、");
                why.append("开着投屏/流媒体端口");
            }
            boolean share = open.contains(445) || open.contains(139);
            if (share) {
                score += 1;
                if (why.length() > 0) why.append("、");
                why.append("开着文件共享");
            }
            boolean heavy = open.contains(8080) || open.contains(8008) || open.contains(9100);
            if (heavy) {
                score += 1;
                if (why.length() > 0) why.append("、");
                why.append("开着高流量服务端口");
            }
        }
        if (d.ttl > 0 && d.ttl <= 64 && "未知设备".equals(d.type)) {
            score += 1;
        }

        d.suspect = score >= 3 ? 2 : (score >= 2 ? 1 : 0);
        if (why.length() == 0) why.append("没有异常迹象");
        d.suspectWhy = why.toString();
    }

    static String suspectText(int lv) {
        return lv >= 2 ? "疑似占用高" : (lv >= 1 ? "疑似占用中" : "疑似占用低");
    }

    // ---------------- 版本化的口子 ----------------

    static WifiBrief readWifi(Context c) {
        WifiBrief wb = new WifiBrief();
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return wb;
            WifiInfo wi = wm.getConnectionInfo();
            if (wi == null) return wb;
            String s = wi.getSSID();
            if (s != null) {
                if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
                if (!"<unknown ssid>".equals(s)) wb.ssid = s;
            }
            wb.rssi = wi.getRssi();
            wb.linkMbps = wi.getLinkSpeed();
            wb.freq = wi.getFrequency();
            if (wb.freq >= 4900) wb.band = "5GHz";
            else if (wb.freq >= 2400) wb.band = "2.4GHz";
        } catch (Throwable ignored) {}
        return wb;
    }

    static String getGatewayIp(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "";
            Network n = cm.getActiveNetwork();
            if (n == null) return "";
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) return "";
            for (android.net.RouteInfo r : lp.getRoutes()) {
                if (r.isDefaultRoute() && r.getGateway() instanceof java.net.Inet4Address) {
                    String g = r.getGateway().getHostAddress();
                    if (g != null && !"0.0.0.0".equals(g)) return g;
                }
            }
        } catch (Throwable ignored) {}
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                android.net.DhcpInfo d = wm.getDhcpInfo();
                if (d != null && d.gateway != 0) return intToIp(d.gateway);
            }
        } catch (Throwable ignored) {}
        // 最后兜底：拿本机 IP 推同网段 .1（Android 12+ 有些机型 getDhcpInfo 已失效）
        try {
            String me = getLocalIp(c);
            if (me != null && me.contains(".")) {
                String base = subnetBase(me);
                if (!base.isEmpty()) return base + "1";
            }
        } catch (Throwable ignored) {}
        return "";
    }

    static List<String> getDnsList(Context c) {
        List<String> out = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return out;
            Network n = cm.getActiveNetwork();
            if (n == null) return out;
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) return out;
            for (InetAddress a : lp.getDnsServers()) {
                String h = a.getHostAddress();
                if (h != null && !out.contains(h)) out.add(h);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    static String getLocalIp(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "";
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) return "";
            return intToIp(ip);
        } catch (Throwable ignored) { return ""; }
    }

    static String intToIp(int ip) {
        return ((ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff));
    }

    static int ipInt(String ip) {
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return 0;
            return ((Integer.parseInt(p[0]) & 0xff) << 24) | ((Integer.parseInt(p[1]) & 0xff) << 16)
                    | ((Integer.parseInt(p[2]) & 0xff) << 8) | (Integer.parseInt(p[3]) & 0xff);
        } catch (Exception e) { return 0; }
    }

    static String subnetBase(String gw) {
        if (gw == null || gw.indexOf('.') < 0) return "";
        int p = gw.lastIndexOf('.');
        return gw.substring(0, p + 1);
    }

    // ---------------- ping ----------------

    private static final Pattern TIME_RE = Pattern.compile("time=([0-9.]+)\\s*ms");
    private static final Pattern TTL_RE = Pattern.compile("ttl=(\\d+)");

    static PingStat pingHost(String host, int count, PingView view) {
        PingStat s = new PingStat();
        s.count = count;
        if (host == null || host.length() == 0) return s;
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"/system/bin/ping", "-c", String.valueOf(count), "-i", "0.2", "-W", "2", host});
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = TIME_RE.matcher(line);
                if (m.find()) {
                    double t = Double.parseDouble(m.group(1));
                    s.recv++;
                    s.last = t;
                    s.sum += t;
                    if (s.min < 0 || t < s.min) s.min = t;
                    if (t > s.max) s.max = t;
                    if (view != null) view.onTick(s);
                }
            }
            br.close();
            proc.waitFor();
        } catch (Throwable ignored) {
        } finally {
            if (proc != null) try { proc.destroy(); } catch (Throwable ignored) {}
        }
        if (s.recv == 0) {
            int n = Math.min(count, 4);
            for (int i = 0; i < n; i++) {
                try {
                    long t0 = System.nanoTime();
                    boolean ok = InetAddress.getByName(host).isReachable(1500);
                    double ms = (System.nanoTime() - t0) / 1e6;
                    if (ok) {
                        s.recv++;
                        s.last = ms;
                        s.sum += ms;
                        if (s.min < 0 || ms < s.min) s.min = ms;
                        if (ms > s.max) s.max = ms;
                        if (view != null) view.onTick(s);
                    }
                } catch (Throwable ignored) {}
            }
        }
        return s;
    }

    /** ping 一旦确认不可用就永久降级，避免 254 个地址各浪费 2 秒。 */
    private static volatile boolean pingBroken = false;

    /** 判断主机是否在线。优先 TCP（快、无需权限），ping 只在确认可用时用。
     *  v2.4 关键改动：v2.2 的"ping 优先"导致每个死地址最坏 3.5 秒，
     *  40 线程跑 254 个地址在 45 秒超时点被截断，高 IP 段（手机/电视）全丢。 */
    static boolean pingOnce(String ip) {
        if (tcpProbe(ip)) return true;
        if (!pingBroken && pingShell(ip)) return true;
        try {
            return InetAddress.getByName(ip).isReachable(500);
        } catch (Throwable e) { return false; }
    }

    /** 只用 ping（给 TTL 探测用，不做全局探活）。 */
    private static boolean pingShell(String ip) {
        Process proc = null;
        BufferedReader br = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"/system/bin/ping", "-c", "1", "-W", "1", ip});
            br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            boolean hit = false;
            while ((line = br.readLine()) != null) {
                if (line.contains("not permitted") || line.contains("Permission denied")) {
                    pingBroken = true;
                    return false;
                }
                if (line.contains("1 received") || line.contains("1 packets received") || line.contains("time=")) hit = true;
            }
            proc.waitFor();
            return hit;
        } catch (Throwable ignored) {
            pingBroken = true;
            return false;
        } finally {
            if (br != null) try { br.close(); } catch (Throwable ignored) {}
            if (proc != null) try { proc.destroy(); } catch (Throwable ignored) {}
        }
    }

    /** 第一批：最常见的三个端口，命中率最高、超时最短。 */
    private static final int[] PROBE_FAST = {80, 443, 62078};
    /** 第二批：慢一点但能认出特殊设备（DNS/ADB/Web/打印机）。 */
    private static final int[] PROBE_SLOW = {53, 5555, 8080, 9100};
    /** 兼容旧引用。 */
    private static final int[] PROBE_PORTS = {80, 443, 62078, 53, 5555, 8080, 9100};

    /** 只要有任意常见端口接受连接，就算这台设备活着。不依赖 root 或 ping 权限。
     *  分批探测：先 3 个高效端口（400ms），全部失败才试剩余 4 个。 */
    static boolean tcpProbe(String ip) {
        for (int port : PROBE_FAST) {
            if (tryPort(ip, port, 400)) return true;
        }
        for (int port : PROBE_SLOW) {
            if (tryPort(ip, port, 400)) return true;
        }
        return false;
    }

    private static boolean tryPort(String ip, int port, int timeoutMs) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    static int pingTtl(String ip) {
        if (pingBroken) return -1;
        Process proc = null;
        BufferedReader br = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"/system/bin/ping", "-c", "1", "-W", "1", ip});
            br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = TTL_RE.matcher(line);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            proc.waitFor();
        } catch (Throwable ignored) {
            pingBroken = true;
        } finally {
            if (br != null) try { br.close(); } catch (Throwable ignored) {}
            if (proc != null) try { proc.destroy(); } catch (Throwable ignored) {}
        }
        return -1;
    }

    // ---------------- 吞吐 ----------------

    private static final int DOWN_BYTES = 8000000;
    private static final int UP_BYTES = 3000000;
    private static final int WARM_BYTES = 1000000;

    static SpeedResult measureDownload(Meter m) {
        return measureDownloadTiered(m, null);
    }

    static SpeedResult measureUpload(Meter m) {
        SpeedResult r = new SpeedResult();
        HttpURLConnection c = null;
        try {
            URL u = new URL("https://speed.cloudflare.com/__up?bytes=" + UP_BYTES);
            c = (HttpURLConnection) u.openConnection();
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setRequestMethod("POST");
            c.setFixedLengthStreamingMode(UP_BYTES);
            c.setRequestProperty("Content-Type", "application/octet-stream");
            c.setRequestProperty("User-Agent", "WifiCheck/2.0");
            byte[] chunk = new byte[32768];
            java.util.Arrays.fill(chunk, (byte) 0x41);
            OutputStream os = c.getOutputStream();
            long t0 = System.currentTimeMillis();
            long sent = 0, lastTick = 0;
            while (sent < UP_BYTES) {
                int n = (int) Math.min(chunk.length, UP_BYTES - sent);
                os.write(chunk, 0, n);
                sent += n;
                long el = System.currentTimeMillis() - t0;
                if (m != null && el - lastTick >= 180) {
                    lastTick = el;
                    double mbps = el <= 0 ? 0 : (sent * 8.0 / 1e6) / (el / 1000.0);
                    m.onProgress(sent, UP_BYTES, mbps);
                }
            }
            os.flush();
            os.close();
            int code = c.getResponseCode();
            r.ms = System.currentTimeMillis() - t0;
            r.bytes = sent;
            r.ok = code >= 200 && code < 400;
            r.mbps = r.ok && r.ms > 0 ? (sent * 8.0 / 1e6) / (r.ms / 1000.0) : 0;
            if (!r.ok) r.err = "HTTP " + code;
        } catch (Exception e) {
            return SpeedResult.fail(e);
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
        }
        return r;
    }

    // ---------------- 分档测速 ----------------

    /**
     * 分档测速：先用 1 MB 试水，够快就加码到 8 MB、20 MB，
     * 这样慢网不至于卡在下载 8 MB 上干等 40 秒。
     */
    static SpeedResult measureDownloadTiered(Meter m, TierView tv) {
        int[] tiers = {1000000, 8000000, 20000000};
        SpeedResult best = null;
        for (int i = 0; i < tiers.length; i++) {
            if (tv != null) tv.onTier(i, tiers[i]);
            SpeedResult r = downloadOnce(tiers[i], m);
            if (!r.ok) {
                if (best == null) return r;
                break;
            }
            best = r;
            if (r.mbps < 6) break;                        // 太慢，别加码了
            if (i == tiers.length - 1) break;             // 已经最大
            if (r.ms < 2500) continue;                    // 测得太短，数据不可信，加码
            if (r.mbps < 30) break;                       // 中速，8 MB 够准了
        }
        return best == null ? SpeedResult.fail(new Exception("无吞吐")) : best;
    }

    interface TierView { void onTier(int index, int bytes); }

    /**
     * 测速源。Cloudflare 在国内没有节点，测出来的是国际出口带宽，
     * 会比自己宽带低一大截，所以国内教育网镜像优先，Cloudflare 只做兜底。
     * 镜像都用 Range 请求只取需要的字节数，不整文件下载。
     */
    private static final String[][] MIRRORS = {
            {"https://mirrors.bfsu.edu.cn/speedtest/1000mb.bin", "北外镜像"},
            {"https://mirrors.tuna.tsinghua.edu.cn/speedtest/1000mb.bin", "清华 TUNA"},
            {"https://mirrors.ustc.edu.cn/ubuntu-releases/22.04/ubuntu-22.04.5-desktop-amd64.iso", "中科大镜像"},
            {"https://speed.cloudflare.com/__down?bytes=", "Cloudflare"},
    };

    /** 当前用的测速源名称，给界面显示。 */
    static volatile String lastMirror = "";

    static SpeedResult downloadOnce(int bytes, Meter m) {
        SpeedResult last = null;
        for (String[] mir : MIRRORS) {
            SpeedResult r = fetchBytes(mir[0], mir[1], bytes, m);
            if (r.ok) {
                lastMirror = mir[1];
                return r;
            }
            last = r;
        }
        return last == null ? SpeedResult.fail(new Exception("全部测速源不可达")) : last;
    }

    private static SpeedResult fetchBytes(String base, String label, int bytes, Meter m) {
        SpeedResult r = new SpeedResult();
        HttpURLConnection c = null;
        try {
            boolean isCf = base.endsWith("bytes=");
            URL u = new URL(isCf ? base + bytes : base);
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "WifiCheck/2.3");
            c.setRequestProperty("Cache-Control", "no-cache");
            if (!isCf) {
                c.setRequestProperty("Range", "bytes=0-" + (bytes - 1));
            }
            c.connect();
            int code = c.getResponseCode();
            if (code < 200 || code >= 400) {
                r.err = label + " HTTP " + code;
                return r;
            }
            InputStream in = c.getInputStream();
            byte[] buf = new byte[16384];
            long got = 0, measured = 0;
            long t0 = System.currentTimeMillis(), lastTick = 0;
            int warm = Math.min(WARM_BYTES, bytes / 4);
            int n;
            while ((n = in.read(buf)) > 0) {
                got += n;
                if (got > warm) {
                    measured += n;
                    long el = System.currentTimeMillis() - t0;
                    if (m != null && el - lastTick >= 160) {
                        lastTick = el;
                        double mbps = el <= 0 ? 0 : (measured * 8.0 / 1e6) / (el / 1000.0);
                        m.onProgress(Math.min(got, bytes), bytes, mbps);
                    }
                }
                if (got >= bytes) break;
            }
            in.close();
            r.ms = System.currentTimeMillis() - t0;
            r.bytes = got;
            r.ok = measured > 0;
            r.mbps = r.ok && r.ms > 0 ? (measured * 8.0 / 1e6) / (r.ms / 1000.0) : 0;
            if (!r.ok) r.err = label + " 无数据";
        } catch (Exception e) {
            r.ok = false;
            r.err = label + " " + e.getClass().getSimpleName();
            return r;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
        }
        return r;
    }

    // ---------------- mDNS 设备发现 ----------------

    /** 常见服务类型：认出 Chromecast、AirPlay、打印机、SMB 共享等。 */
    private static final String[][] MDNS_SERVICES = {
            {"_googlecast._tcp.local", "Chromecast / 小米盒子"},
            {"_airplay._tcp.local", "AirPlay（苹果设备 / 支持投屏的电视）"},
            {"_raop._tcp.local", "AirPlay 音频"},
            {"_ipp._tcp.local", "网络打印机（IPP）"},
            {"_printer._tcp.local", "网络打印机"},
            {"_smb._tcp.local", "SMB 共享（电脑 / NAS）"},
            {"_afpovertcp._tcp.local", "苹果文件共享"},
            {"_spotify-connect._tcp.local", "Spotify 音箱"},
            {"_hap._tcp.local", "HomeKit 智能设备"},
            {"_miio._udp.local", "小米 IoT 设备"},
            {"_http._tcp.local", "网页管理界面"},
            {"_workstation._tcp.local", "局域网工作站"},
    };

    /**
     * 发一轮 mDNS 查询，收集应答里的「服务类型 → 主机名」。
     * 只做 PTR/SRV 的浅解析，够用来判断设备是什么，不实现完整 DNS 解析器。
     * 返回 key = 服务类型，value = 该服务出现过的提示文案。
     */
    static Map<String, String> mdnsScan(int timeoutMs) {
        Map<String, String> found = new LinkedHashMap<>();
        MulticastSocket sock = null;
        try {
            sock = new MulticastSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(5353));
            sock.setSoTimeout(400);
            InetAddress group = InetAddress.getByName("224.0.0.251");
            sock.joinGroup(group);

            for (String[] svc : MDNS_SERVICES) {
                byte[] q = buildMdnsQuery(svc[0]);
                sock.send(new DatagramPacket(q, q.length, group, 5353));
            }

            long deadline = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                try {
                    sock.receive(pkt);
                } catch (SocketTimeoutException te) {
                    continue;
                } catch (Throwable t) {
                    break;
                }
                String from = pkt.getAddress() == null ? "" : pkt.getAddress().getHostAddress();
                String txt = extractNames(pkt.getData(), pkt.getLength());
                if (TextUtils_isEmpty(txt) || from.isEmpty()) continue;
                for (String[] svc : MDNS_SERVICES) {
                    String key = svc[0].replace(".local", "");
                    if (txt.contains(key)) {
                        String cur = found.get(from);
                        found.put(from, TextUtils_isEmpty(cur) ? svc[1] : (cur + " / " + svc[1]));
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (sock != null) {
                try { sock.leaveGroup(InetAddress.getByName("224.0.0.251")); } catch (Throwable ignored) {}
                try { sock.close(); } catch (Throwable ignored) {}
            }
        }
        return found;
    }

    private static boolean TextUtils_isEmpty(String s) { return s == null || s.length() == 0; }

    /** 构造一个最小可用的 mDNS 查询包（单问题，PTR，class IN）。 */
    private static byte[] buildMdnsQuery(String name) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(0); bo.write(0);            // ID = 0
        bo.write(0); bo.write(0);            // flags = 0
        bo.write(0); bo.write(1);            // QDCOUNT = 1
        bo.write(0); bo.write(0);            // ANCOUNT
        bo.write(0); bo.write(0);            // NSCOUNT
        bo.write(0); bo.write(0);            // ARCOUNT
        for (String part : name.split("\\.")) {
            byte[] pb = part.getBytes(java.nio.charset.Charset.forName("UTF-8"));
            bo.write(pb.length);
            bo.write(pb, 0, pb.length);
        }
        bo.write(0);                         // 名字结束
        bo.write(0); bo.write(12);           // TYPE = PTR
        bo.write(0); bo.write(1);            // CLASS = IN
        return bo.toByteArray();
    }

    /** 把应答包里的可打印片段拼起来，用来做关键词匹配。 */
    private static String extractNames(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        StringBuilder cur = new StringBuilder();
        for (int i = 12; i < len; i++) {
            int b = data[i] & 0xFF;
            if (b >= 32 && b < 127) {
                cur.append((char) b);
            } else {
                if (cur.length() >= 3) sb.append(cur).append('\n');
                cur.setLength(0);
            }
        }
        if (cur.length() >= 3) sb.append(cur);
        return sb.toString();
    }

    static String errHint(String err) {
        if (err == null) return "";
        String e = err.toLowerCase(Locale.US);
        if (e.contains("timeout") || e.contains("timed out")) return "超时 —— 几乎没有吞吐，或被限速";
        if (e.contains("resolve") || e.contains("unknownhost")) return "解析失败 —— 先去「工具」换私有 DNS";
        if (e.contains("refused") || e.contains("reset") || e.contains("broken pipe")) return "连接被重置 —— 可能被路由器 QoS 掐了";
        return err;
    }

    // ---------------- ARP ----------------

    static Map<String, String> readArp() {
        Map<String, String> map = new LinkedHashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new java.io.FileReader("/proc/net/arp"));
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; if (line.contains("IP address")) continue; }
                String[] p = line.trim().split("\\s+");
                if (p.length < 4) continue;
                String ip = p[0], flag = p[2], mac = p[3];
                if (ip == null || !ip.contains(".")) continue;
                if (mac == null || mac.startsWith("00:00:00")) continue;
                // flags 0x0 = 未完成，0x2 = 有效
                if ("0x0".equalsIgnoreCase(flag)) continue;
                map.put(ip, mac.toUpperCase(Locale.US));
            }
        } catch (Throwable ignored) {
        } finally {
            if (br != null) try { br.close(); } catch (Throwable ignored) {}
        }
        return map;
    }

    // ---------------- 指纹 ----------------

    static String resolveHost(String ip) {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> f = ex.submit(new Callable<String>() {
                public String call() {
                    try { return InetAddress.getByName(ip).getCanonicalHostName(); } catch (Throwable e) { return ""; }
                }
            });
            String h = f.get(1500, TimeUnit.MILLISECONDS);
            if (h == null || h.equals(ip)) return "";
            return h;
        } catch (Throwable e) {
            return "";
        } finally {
            ex.shutdownNow();
        }
    }

    static Set<Integer> probePorts(String ip) {
        Set<Integer> open = new java.util.HashSet<>();
        // 先扫最可能开的高价值端口（短超时），命中率高，能省下大量等待
        for (int port : PORT_HOT) {
            if (tryPort(ip, port, 180)) open.add(port);
        }
        for (int port : PORTS) {
            if (open.contains(port)) continue;
            if (tryPort(ip, port, 180)) open.add(port);
        }
        return open;
    }

    /** 高价值端口：绝大多数在线设备会命中其中之一或几个。 */
    private static final int[] PORT_HOT = {80, 443, 62078, 8009, 445};

    static final int[] PORTS = {22, 23, 53, 80, 139, 443, 445, 554, 631, 3389, 5000, 7000, 8008, 8009, 8080, 9100, 5555, 62078};

    static String httpTitle(String ip, int port) {
        HttpURLConnection c = null;
        try {
            URL u = new URL("http://" + ip + ":" + port + "/");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(900);
            c.setReadTimeout(1200);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 WifiCheck/2.0");
            c.connect();
            StringBuilder sb = new StringBuilder();
            String server = c.getHeaderField("Server");
            if (server != null) sb.append(server).append(" ");
            String loc = c.getHeaderField("Location");
            if (loc != null) sb.append(loc).append(" ");
            InputStream in = c.getInputStream();
            byte[] buf = new byte[4096];
            int n, total = 0;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            while ((n = in.read(buf)) > 0 && total < 8192) {
                bo.write(buf, 0, n);
                total += n;
            }
            in.close();
            String html = new String(bo.toByteArray(), "UTF-8");
            Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
            if (m.find()) {
                String t = m.group(1).replaceAll("\\s+", " ").trim();
                if (t.length() > 0) sb.append(t);
            }
            String out = sb.toString().trim();
            if (out.length() > 60) out = out.substring(0, 60);
            return out;
        } catch (Throwable e) {
            return "";
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
        }
    }

    // ---------------- OUI 厂商 ----------------

    private static final Map<String, String> OUI = new HashMap<>();

    private static void oui(String hex, String name) {
        OUI.put(hex.replace(":", "").replace("-", "").toUpperCase(Locale.US), name);
    }

    static {
        // 路由 / 网络设备
        for (String h : new String[]{"001D0F", "14CC20", "A42BB0", "50642B", "3C46D8", "F4F26D", "C46E1F", "B0BE76", "00E0B8", "50C7BF", "9C5322"}) oui(h, "TP-Link");
        for (String h : new String[]{"001122", "C83A35", "D8320E", "C8:3A:35"}) oui(h, "Tenda 腾达");
        for (String h : new String[]{"F09FC2", "788A20", "0418D6", "44D9E7", "24A43C"}) oui(h, "Ubiquiti");
        for (String h : new String[]{"0018E7", "0024B2", "20E52A", "9CD643"}) oui(h, "Netgear");
        for (String h : new String[]{"001CF0", "0022B0", "1CAFF7", "340804"}) oui(h, "D-Link 友讯");
        oui("24A43C", "ASUS 华硕");
        oui("1C872C", "ASUS 华硕");
        oui("2C56DC", "ASUS 华硕");
        oui("002722", "ZTE 中兴");
        oui("344B50", "ZTE 中兴");
        oui("001E10", "Huawei 华为网关");
        oui("00259E", "Huawei 华为网关");
        oui("D4B7C1", "Huawei 华为网关");
        // 小米 / 华为 / 苹果 / 三星 ...
        for (String h : new String[]{"04CF8C", "286C07", "64CC2E", "7811DC", "8CBEBE", "009E11", "F8A45F", "50EC50"}) oui(h, "Xiaomi 小米");
        for (String h : new String[]{"00E0FC", "283CE4", "4846FB", "70723C", "9C28EF", "F4C714", "B0F1EC", "5C7D5E"}) oui(h, "Huawei 华为");
        for (String h : new String[]{"28CFE9", "8C8590", "001B63", "3C0754", "A483E7", "F0DBF8", "AC293A", "F01898", "E0B52D"}) oui(h, "Apple 苹果");
        for (String h : new String[]{"002637", "3423BA", "5C0A5B", "8C7712", "E8508B", "C0BDD1", "B0D09C", "2C4402"}) oui(h, "Samsung 三星");
        for (String h : new String[]{"001A11", "3C5AB4", "F4F5D8", "94EB2C", "6466B3"}) oui(h, "Google");
        for (String h : new String[]{"10C61F", "3C5188", "5CE0C5", "9C99A0"}) oui(h, "OPPO");
        for (String h : new String[]{"20B9A0", "3C5A37", "D4A02A", "8C4B14"}) oui(h, "vivo");
        for (String h : new String[]{"387B47", "7C0CF6", "8CB84A"}) oui(h, "Meizu 魅族");
        for (String h : new String[]{"6C5C14", "C067AF", "34C6BA"}) oui(h, "OnePlus 一加");
        for (String h : new String[]{"60AB67", "F4D9FB", "AC7BA4"}) oui(h, "Honor 荣耀");
        oui("3C2EFF", "realme");
        // 电脑 / 虚拟化
        for (String h : new String[]{"001B21", "001E67", "3C970E", "A4BB6D", "00A0C9", "8C1645", "D8FC93"}) oui(h, "Intel 网卡");
        oui("00E04C", "Realtek 网卡");
        oui("000C29", "VMware 虚拟机");
        oui("005056", "VMware 虚拟机");
        oui("00155D", "Microsoft 虚拟网卡");
        oui("080027", "VirtualBox 虚拟机");
        for (String h : new String[]{"001B24", "D4BEDC", "E0D55E"}) oui(h, "Lenovo 联想");
        for (String h : new String[]{"001C23", "0CD292", "C8D3A3"}) oui(h, "Dell 戴尔");
        for (String h : new String[]{"001F16", "2C27D7", "A08CF8"}) oui(h, "HP 惠普");
        // 嵌入式 / NAS
        for (String h : new String[]{"B827EB", "DCA632", "E45F01", "2CCF67"}) oui(h, "Raspberry Pi 树莓派");
        oui("001132", "Synology 群晖");
        oui("0011D8", "ASUSTOR");
        // 打印机
        oui("3C2AF4", "Brother 打印机");
        oui("0021B7", "Lexmark 打印机");
        oui("001B78", "HP 打印机");
        oui("0080:77", "Brother 打印机");
        oui("5CB8CB", "Epson 打印机");
        oui("0000AA", "Xerox 打印机");
        // 电视盒 / 智能屏
        oui("28D127", "Mi TV 小米电视");
        oui("00:9E:C8", "Mi TV 小米电视");
        oui("1C1BB3", "Hisense 海信电视");
        oui("3C0E23", "Sony 索尼电视");
        for (String h : new String[]{"D0574D", "E0B94D"}) oui(h, "创维 Skyworth");
        // 物联网
        oui("18FE34", "ESP8266 / ESP32 模块");
        oui("3C71BF", "ESP8266 / ESP32 模块");
        oui("B4E62D", "宜家 / IoT 设备");
        oui("34CE00", "智能插座 / IoT");
    }

    static String ouiVendor(String mac) {
        if (mac == null || mac.length() < 8) return "";
        String key = mac.replace(":", "").replace("-", "").toUpperCase(Locale.US);
        if (key.length() < 6) return "";
        String hit = OUI.get(key.substring(0, 6));
        return hit == null ? "" : hit;
    }

    static String inferType(Dev d, Set<Integer> open) {
        if (d.isGw) return "路由器 / 光猫";
        if (d.isMe) return "本机（你）";
        if (!TextUtils_isEmpty(d.mdns)) {
            String t = d.mdns;
            if (t.contains("Chromecast")) return "电视 / 盒子（Chromecast）";
            if (t.contains("AirPlay") || t.contains("苹果")) return "电视 / 盒子（AirPlay）";
            if (t.contains("打印机")) return "打印机";
            if (t.contains("SMB") || t.contains("苹果文件共享")) return "电脑 / NAS";
            if (t.contains("HomeKit") || t.contains("小米 IoT")) return "智能家居";
            if (t.contains("Spotify")) return "智能音箱";
        }
        if (open.contains(9100) || open.contains(631) || open.contains(515)) return "打印机";
        if (open.contains(62078)) return "iPhone / iPad";
        if (open.contains(3389) || open.contains(445) || open.contains(139)) return "Windows 电脑";
        if (open.contains(554) || open.contains(8009) || open.contains(7000) || open.contains(5000)) return "电视 / 盒子";
        if (open.contains(22)) return "NAS / 服务器";
        String h = (d.host == null ? "" : d.host).toLowerCase(Locale.US);
        if (h.contains("iphone") || h.contains("ipad") || h.contains("android") || h.contains("redmi")
                || h.contains("huawei") || h.contains("oppo") || h.contains("vivo") || h.contains("xiaomi")
                || h.contains("honor") || h.contains("phone")) return "手机";
        if (h.contains("desktop") || h.contains("laptop") || h.contains("pc") || h.contains("macbook")
                || h.contains("windows") || h.contains("thinkpad")) return "电脑";
        if (h.contains("tv") || h.contains("box") || h.contains("mi-") || h.contains("appletv")) return "电视 / 盒子";
        if (h.contains("print") || h.contains("hp-") || h.contains("brother") || h.contains("epson")) return "打印机";
        if (h.contains("router") || h.contains("gateway") || h.contains("miwifi") || h.contains("tplink")) return "路由器";
        if (h.contains("nas") || h.contains("diskstation") || h.contains("synology")) return "NAS";
        if (d.ttl >= 0) {
            if (d.ttl >= 250) return "网络设备";
            if (d.ttl >= 120) return "Windows 电脑";
            if (d.ttl >= 60) return "手机 / Linux";
            if (d.ttl >= 30) return "网络设备";
        }
        String v = d.vendor == null ? "" : d.vendor;
        if (v.contains("Apple") || v.contains("Xiaomi") || v.contains("Huawei") || v.contains("Samsung")
                || v.contains("OPPO") || v.contains("vivo") || v.contains("Honor") || v.contains("realme")
                || v.contains("Meizu") || v.contains("OnePlus")) return "手机";
        if (v.contains("打印机")) return "打印机";
        if (v.contains("电视")) return "电视 / 盒子";
        if (v.contains("树莓派") || v.contains("群晖") || v.contains("NAS")) return "NAS";
        if (v.contains("虚拟机") || v.contains("网卡")) return "电脑";
        if (v.contains("TP-Link") || v.contains("Tenda") || v.contains("Netgear") || v.contains("D-Link")
                || v.contains("Ubiquiti") || v.contains("路由") || v.contains("中兴")) return "路由器";
        return "未知设备";
    }

    static String typeIcon(String type) {
        if (type == null) return "??";
        if (type.contains("路由") || type.contains("光猫")) return "RT";
        if (type.contains("本机")) return "ME";
        if (type.contains("手机") || type.contains("iPhone")) return "PH";
        if (type.contains("电脑") || type.contains("Windows")) return "PC";
        if (type.contains("电视")) return "TV";
        if (type.contains("打印")) return "PR";
        if (type.contains("NAS") || type.contains("服务器")) return "NS";
        if (type.contains("智能家居") || type.contains("音箱")) return "SH";
        return "??";
    }

    /** 设备名的短标签，用于卡片副标题。 */
    static String devTag(Dev d) {
        if (!TextUtils_isEmpty(d.mdns)) return d.mdns;
        if (!TextUtils_isEmpty(d.vendor)) return d.vendor;
        if (!TextUtils_isEmpty(d.host)) return d.host;
        return "厂商读不到";
    }
}
