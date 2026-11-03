# 出租屋 WiFi 体检

房东宽带慢、卡、转圈，但手上只有手机？这个 App 用手机把「到底哪一段慢」测出来。

**下载安装包（v2.1）**

https://github.com/zxxi-pk/WifiScanApp/releases/download/v2.1/WiFiCheck-v2.1.apk

> Android 手机。装之前如果装过旧版，**先卸载旧版**（调试签名每次重新生成，覆盖安装会报「应用未安装」）。

---

## v2.1 新增（借鉴开源项目）

| 功能 | 学自 | 做什么 |
|---|---|---|
| 信号强度时间轴 | WiFiAnalyzer | 每秒采一次 dBm 画折线，波动 ≥15 dBm 就提示"连接不稳" |
| 分档测速 | Flare | 先下 1 MB 试水，够快才加码 8 MB、20 MB；慢网不用干等 40 秒 |
| mDNS 设备发现 | samurai-scan | 问 `_googlecast` / `_airplay` / `_ipp` / `_miio` 等 12 类服务，认出 Chromecast、投屏电视、网络打印机、智能家居 |

> 只借鉴思路自己重写，没抄代码。WiFiAnalyzer 是 GPL-3.0、LibreSpeed 是 LGPL-3.0，直接搬会传染许可证。

---

## v2.0 改了什么

| 改动 | 说明 |
|---|---|
| 拆成 4 个页面 | 总览 / 诊断 / 记录 / 工具，底部 Tab 切换，不再是一页长条 |
| 一行两卡 | 总览页 2×2 白卡网格，不再一行一条 |
| 结果图形化 | 延迟色带、13 信道柱状图、测速圆环；数字大、文字少 |
| 一个大总结 | 顶部 0–100 分圆环 + 一句结论 + 一句建议 + 状态徽章 |
| 去掉 emoji | 换成两字母代码方块（RT 路由 / ME 本机 / PC 电脑 / TV 盒子 / PH 手机 / PR 打印机 / NS NAS） |

界面走专业仪表盘路线：深藏青头部、浅灰页面、白卡片 1px 细边框、语义色只有绿/琥珀/红三种。

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
| 带宽 | 下行 ≥ 50 Mbps | ≤ 5 Mbps |
| 设备 | 没有陌生设备 | ≥ 3 台陌生 |

中间线性插值。**<50 红 / 50–75 黄 / ≥75 绿**。

---

## 四个页面

### 总览
- 头部：WiFi 名 · 频段 · dBm · 协商速率
- 圆环总分 + 一句结论 + 状态徽章
- 2×2 卡片：延迟 / 丢包 / 下行带宽 / 在线设备，点任一张跳进诊断对应页
- 底部「开始全面检测」一次跑完四项

### 诊断
顶部分段控件切四项，每项一个图 + 一个结论卡 + 一个虚线折叠的「原始数据」。

| 分段 | 图形 | 说什么 |
|---|---|---|
| 延迟 | 两条横向色带（第一跳 / 出网）+ 抖动/丢包分栏 + 信号时间轴折线 | 卡在手机↔路由器，还是路由器↔外网；信号稳不稳 |
| 信道 | 13 根柱子，当前信道深蓝、建议信道绿框 | 2.4G 该改成 1/6/11 哪个 |
| 带宽 | 170px 测速圆环 + 三行对比条 | 下行/上行/耗时实测值 |
| 设备 | 统计条 + 2 列设备卡 | 谁在线、认不认识、要不要踢 |

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

1. **mDNS 服务**：`_googlecast`→Chromecast/盒子；`_airplay`→投屏电视；`_ipp`/`_printer`→打印机；`_smb`→电脑/NAS；`_hap`/`_miio`→智能家居
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
| `ACCESS_FINE_LOCATION` | Android 强制：扫 WiFi 列表必须给定位权限（不是用来定位你） |

---

## 能力边界

- **手机端能改的**：换位置、切 5G、改加密 DNS、关电池限制 —— 收益有限但免费。
- **路由器端收益最大**：改 2.4G 信道到最空的、踢掉陌生设备、换掉老单频路由 —— 需要后台密码（多半 `admin/admin` 或贴在路由器背面）。
- **带宽套餐**：只能拿数据谈，工具页「复制完整体检报告」就是给你一份带数字的证据。
- 测速走 Cloudflare 节点，不是国内 CDN，实际刷国内视频通常比这个数好看。

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

自绘控件全部不依赖图表库：`ScoreRing.java` / `Band.java` / `ChannelChart.java` / `SpeedRing.java`。

---

## 同类开源项目对照（2026-11 检索）

| 项目 | 星 | 语言 | 它做什么 | 和本 App 的差别 |
|---|---|---|---|---|
| [VREMSoftwareDevelopment/WiFiAnalyzer](https://github.com/VREMSoftwareDevelopment/WiFiAnalyzer) | 5060 | Kotlin | 信号强度曲线、信道图、AP 列表 | 只做「周边信号分析」，没有延迟/测速/设备扫描；GPL-3.0 |
| [librespeed/speedtest-android](https://github.com/librespeed/speedtest-android) | 445 | Java | 完整测速 App 模板，要自建服务器 | 只测带宽，要配 LibreSpeed 后端 |
| [david-x3d/Flare](https://github.com/david-x3d/Flare) | 0 | Kotlin | ping/jitter/上下行，走 Cloudflare | 思路和本 App 测速部分几乎一样（MIT） |
| [sxi9/samurai-scan](https://github.com/sxi9/samurai-scan) | 0 | Kotlin | WiFi/LAN 扫描 + 设备指纹 | 和本 App 设备页同类，更偏渗透（MIT） |
| [tdr2d/Wifi-Monitor](https://github.com/tdr2d/Wifi-Monitor) | 3 | Java | 列同网段设备 | 2017 年后没更新 |
| [kenarkerim/bufferbloat-test-tools](https://github.com/kenarkerim/bufferbloat-test-tools) | 5 | TypeScript | bufferbloat 与延迟波动测试 | 网页版，不是 App |

**结论**：把「周边信道分析 + 两段 ping 归因 + 测速 + 局域网设备 + 100 分总分」拼成一个 App 的，没找到同类。主流都是单点工具（要么只分析信号，要么只测速，要么只扫设备）。