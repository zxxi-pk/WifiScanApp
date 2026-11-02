# 出租屋 WiFi 体检（WifiScanApp）

一个安卓 App，专治「出租屋用房东宽带，说慢说卡，不知道是信号还是带宽」的问题。
不用电脑、不用网线，一部手机装完就能测。

## 现在能做的四件事

| 按钮 | 干什么 | 能得出什么结论 |
|---|---|---|
| ① 一键体检 | ping 路由器（第一跳）+ ping 外网（223.5.5.5），统计延迟/抖动/丢包 | 卡在**信号**还是在**宽带** |
| ② 扫描周边 WiFi | 扫 2.4G/5G 全部 AP，统计信道拥堵 | 该改到**信道 1/6/11** 哪一个 |
| ③ 测网速 | App 内直接测**下载/上传 Mbps**（speed.cloudflare.com） | 到底**多少兆**，是不是被限速 |
| ④ 扫局域网设备 | 并发 ping 全网段 + 读 ARP 表，列 IP/MAC/厂商 | 有几台设备在连，哪台是**陌生设备** |

另带四个可执行动作：打开路由器后台、换私有 DNS、打开 WiFi 设置、关电池限制。

## 判断口诀

- **满格还慢** → 带宽问题（套餐不够 / 被限速 / 被人占满）
- **格数少还慢** → 信号问题（隔墙 / 距离远 / 信道挤）

## 下载安装

最新版：[WiFiCheck-v1.2.apk](https://github.com/zxxi-pk/WifiScanApp/releases/download/v1.2/WiFiCheck-v1.2.apk)

> debug 签名每次构建都不同，**装新版前先卸载旧版**，否则报「应用未安装」。

## 权限说明

- `ACCESS_FINE_LOCATION`：安卓 6.0+ 扫周边 WiFi 的硬性要求（系统限制，不是用来定位你）
- `INTERNET` / `ACCESS_NETWORK_STATE`：测速、ping 外网

## 说明

- 局域网设备扫描靠 ping + `/proc/net/arp`，部分机型或系统限制下 MAC 可能读不到，会显示「无MAC」。
- 「踢掉陌生设备」只能在路由器后台做，手机 App 无权操作 —— 点「打开路由器后台」进去踢。

## 网页版

`index.html` / `wifi-diagnose.html`：纯网页测速（不用装 App），线上地址
https://zxxi-pk.github.io/WifiScanApp/

## 构建

GitHub Actions 自动编译（`.github/workflows/build.yml`），推送即触发，产物在 Actions artifact 里。

```bash
./gradlew assembleDebug
```

## Termux 命令行版

`wifi_scan.sh`：在 Termux 里跑，扫信道 + 拥堵建议，适合喜欢命令行的人。
