# 出租屋 WiFi 体检

房东宽带慢、卡、转圈，但手上只有手机？这个 App 用手机把「到底哪一段慢」测出来。

**下载安装包（v2.3）**

https://github.com/zxxi-pk/WifiScanApp/releases/download/v2.3/WiFiCheck-v2.3.apk

> Android 手机。装之前如果装过旧版，**先卸载旧版**（调试签名每次重新生成，覆盖安装会报「应用未安装」）。

---

## v2.3 改了什么

| 改动 | 为什么 |
|---|---|
| 测速改走国内镜像 | 原来走 Cloudflare，国内没有节点，测的是国际出口，比你真实宽带低一大截 |
| 测速源自动降级 | 北外 → 清华 TUNA → 中科大 → Cloudflare，前一个不通自动换下一个 |
| 阈值按国内带宽重定标 | 原来 <8 Mbps 判「严重不足」太严，国内百兆宽带能轻松测出 80+ |
| 界面显示用了哪个源 | 结果里直接写「源 北外镜像」，测出来偏低时一眼知道是不是走了境外 |

实测（2026-11，教育网出口）：

| 源 | 速度 |
|---|---|
| `mirrors.bfsu.edu.cn/speedtest/1000mb.bin` | **2008 KB/s**（最快，支持 Range） |
| `mirrors.tuna.tsinghua.edu.cn/speedtest/1000mb.bin` | 786 KB/s（支持 Range） |
| `speed.cloudflare.com/__down` | 107 KB/s（境外，慢 20 倍） |

---

## v2.2 修了什么（v2.1 的真 bug）

| 问题 | 根因 | 修法 |
|---|---|---|
| 点检测没反应，四项都不动 | 后台线程直接改 UI，`CalledFromWrongThreadException`，线程第 0 秒就死了 | `refreshHead()` 拆成取数据 + `applyHead()` 碰 UI，后台只 `ui.post` |
| 设备显示 0 台，还提示「全部认识」 | Android 9+ 收回 `/system/bin/ping` 的 raw socket 权限，254 个地址全判离线 | 三级兜底：ping → TCP 探活（80/443/53/5555/8080/62078/9100）→ isReachable |
| 网关读不到 | Android 12+ 部分机型 `getDhcpInfo()` 失效 | 兜底：本机 IP → 同网段 `.1` |
| 看不出有没有在扫 | 没有进度反馈 | 总览加横向进度条 + 四步指示，设备页加 ping 进度 `已 ping 87/254，回应 5 台` |

---

## 先记住一句话

> **满格还慢 = 带宽问题；格少还慢 = 信号/信道问题。**

---

## 总分怎么算

四项各 25 分，加起来 100：

| 项 | 满分条件 | 零分条件 |
|---|---|---|
| 延迟 | 平均 ≤ 20 ms | ≥ 200 ms |
| 丢包 | 0% | ≥ 20% |
| 带宽 | 下行 ≥ 100 Mbps | ≤ 10 Mbps |
| 设备 | 没有陌生设备 | ≥ 3 台陌生 |

中间线性插值。**<50 红 / 50–75 黄 / ≥75 绿**。

---

## 四个页面

### 总览
- 头部：WiFi 名 · 频段 · dBm · 协商速率
- 圆环总分 + 一句结论 + 状态徽章
- 2×2 卡片：延迟 / 丢包 / 下行带宽 / 在线设备，点任一张跳诊断对应页
- 「开始全面检测」一次跑完四项，带横向进度条 + 四步指示

### 诊断
顶部分段控件切四项，每项一个图 + 一个结论卡 + 一个虚线折叠的「原始数据」。

| 分段 | 图形 | 说什么 |
|---|---|---|
| 延迟 | 信号强度时间轴（每秒采样）+ 两条横向色带 + 抖动/丢包分栏 | 卡在手机↔路由器，还是路由器↔外网；信号稳不稳 |
| 信道 | 13 根柱子，当前信道深蓝、建议信道绿框「用这个」 | 2.4G 该改成 1/6/11 哪个 |
| 带宽 | 170px 测速圆环 + 三行对比条 | 下行/上行/耗时实测值 |
| 设备 | 统计条 + 2 列设备卡 + 扫描进度 | 谁在线、认不认识、要不要踢 |

### 记录
最近 30 次检测，点一条展开看当时完整报告，可清空。

### 工具
打开路由器后台 / 改加密 DNS / 关电池限制 / 打开 WiFi 设置 / 复制完整体检报告。

---

## 三档归因（自动判）

| 条件 | 结论 |
|---|---|
| 第一跳 `丢包>15%` 或 `平均>40ms` 或 `抖动>60ms` | 手机 ↔ 路由器这一跳不行，你自己能修 |
| 第一跳正常，出网 `丢包>10%` 或 `平均>120ms` 或 `抖动>80ms` | 问题在路由器往外，只能拿数据谈 |
| 两段都正常 | ping 漂亮但一用就卡 = 带宽不够 |

---

## 设备识别靠五条判据

1. **mDNS 服务广播**：`_googlecast._tcp`→Chromecast/小米盒子、`_airplay._tcp`→苹果投屏、`_ipp._tcp`/`_printer._tcp`→打印机、`_smb._tcp`→电脑/NAS、`_hap._tcp`→HomeKit
2. **端口**：9100/631/515→打印机；62078→iPhone/iPad；3389/445/139→Windows；554/8009/7000/5000→电视盒子；22→NAS
3. **主机名**：`iphone`/`redmi`/`macbook`/`DESKTOP-xxx` 关键词
4. **TTL**：≥250→网络设备；≥120→Windows；≥60→手机
5. **HTTP 标题 / Server 头**：路由、打印机、盒子后台页面标题里有型号

**踢人只能在路由器后台做。** 工具页点「打开路由器后台」→ 找「已连接设备 / DHCP 客户端」→ 对上 IP 或 MAC → 禁用。

> Android 10 以后系统禁止普通 App 读别人的 MAC，很多行显示读不到，路由后台能看到完整信息。

---

## 权限

| 权限 | 为什么 |
|---|---|
| `INTERNET` | 测速、读设备 HTTP 标题、ping |
| `ACCESS_NETWORK_STATE` | 读网关、DNS、频段 |
| `ACCESS_WIFI_STATE` | 扫周边 WiFi |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS 组播发现设备 |
| `ACCESS_FINE_LOCATION` | Android 强制：扫 WiFi 列表必须给定位权限（不是用来定位你） |

---

## 能力边界

- **没有绑定任何 IP / 网段 / SSID**：网关和网段都是每次运行时从系统读的，装在任何人的任何手机、任何城市都能用。
- **手机端能改的**：换位置、切 5G、改加密 DNS、关电池限制 —— 收益有限但免费。
- **路由器端收益最大**：改 2.4G 信道到最空的、踢掉陌生设备、换掉老单频路由 —— 需要后台密码（多半 `admin/admin` 或贴在路由器背面）。
- **带宽套餐**：只能拿数据谈，工具页「复制完整体检报告」就是给你一份带数字的证据。

---

## 同类开源项目对照（2026-11 检索）

| 项目 | ★ | 语言/协议 | 覆盖 | 与本 App 差异 |
|---|---|---|---|---|
| `VREMSoftwareDevelopment/WiFiAnalyzer` | 5060 | Kotlin GPL-3.0 | 信号曲线、信道图、AP 列表 | 只做周边信号分析，不测带宽、不扫设备、无归因 |
| `librespeed/speedtest-android` | 445 | Java LGPL-3.0 | 完整测速 | 需自建 LibreSpeed 服务器，不做 WiFi 层 |
| `david-x3d/Flare` | 0 | Kotlin MIT | ping/jitter/上下行，走 Cloudflare | 只测速，无信道/设备 |
| `sxi9/samurai-scan` | 0 | Kotlin MIT | WiFi/LAN 扫描 + 设备指纹 | 无测速、无归因 |
| `tdr2d/Wifi-Monitor` | 3 | Java MIT | 列同网段设备 | 2017 停更 |

**结论：没有把「信道分析 + 两段 ping 归因 + 测速 + 设备扫描 + 总分」拼一起的项目**，主流全是单点工具。本 App 借鉴的三个思路都是自己重写的（未复制 GPL/LGPL 源码）：

- 信号强度时间轴曲线 ← WiFiAnalyzer 思路
- 分档测速（先 1 MB，够快才加码 8 MB / 20 MB）← Flare 思路
- mDNS 设备发现 ← samurai-scan 思路

---

## 附带的另两个形态

- **网页测速版**：https://zxxi-pk.github.io/WifiScanApp/
- **Termux 脚本**：仓库里的 `wifi_scan.sh`，需要 `termux-api` + `jq`

---

## 自己编译

| 项 | 值 |
|---|---|
| compileSdk / targetSdk | 33 |
| minSdk | 23 |
| AGP | 8.1.4 |
| Gradle | 8.4 |
| JDK | 17 (temurin) |
| 依赖 | androidx.appcompat:appcompat:1.6.1 |

GitHub Actions 自动出包（`.github/workflows/build.yml`），推 main 即编译。

自绘控件全部不依赖图表库：`ScoreRing.java` / `Band.java` / `ChannelChart.java` / `SpeedRing.java` / `SignalChart.java`。
