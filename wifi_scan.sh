#!/data/data/com.termux/files/usr/bin/bash
# 出租屋 WiFi 信道/信号扫描脚本（Termux 版）
# 需要：Termux + Termux:API 插件
# 安装：应用商店或 F-Droid 装 Termux；再装 Termux:API；打开一次 Termux:API 授权
# 依赖：jq（首次运行会自动装）
# 用法：把本文件传到手机，在 Termux 里 bash wifi_scan.sh

if ! command -v termux-wifi-scaninfo >/dev/null 2>&1; then
  echo "缺少 termux-wifi-scaninfo，请先装 Termux:API 插件（pkg install termux-api）"
  exit 1
fi
command -v jq >/dev/null 2>&1 || pkg install -y jq >/dev/null 2>&1

freq_to_chan() {
  local f=$1
  if   [ "$f" -ge 2412 ] && [ "$f" -le 2484 ]; then
        [ "$f" -eq 2484 ] && echo 14 || echo $(( (f - 2407) / 5 ))
  elif [ "$f" -ge 5035 ] && [ "$f" -le 5980 ]; then echo $(( (f - 5000) / 5 ))
  elif [ "$f" -ge 5955 ] && [ "$f" -le 7115 ]; then echo $(( (f - 5950) / 5 ))
  else echo "?"; fi
}

band_of() {
  local f=$1
  if   [ "$f" -lt 3000 ]; then echo "2.4G"
  elif [ "$f" -lt 5950 ]; then echo "5G"
  else echo "6G"; fi
}

scan=$(termux-wifi-scaninfo)
conn=$(termux-wifi-connectioninfo 2>/dev/null)
mybssid=$(echo "$conn" | jq -r '.bssid // empty')
total=$(echo "$scan" | jq 'length')

echo "===== 周边 WiFi 共 $total 个 ====="
printf "%-26s %-5s %-5s %-6s %s\n" "SSID" "信道" "频段" "dBm" "加密"
echo "$scan" | jq -c '.[]' | while read -r ap; do
  ssid=$(echo "$ap" | jq -r '.ssid // "<隐藏>"')
  freq=$(echo "$ap" | jq -r '.frequency_mhz')
  level=$(echo "$ap" | jq -r '.level')
  cap=$(echo "$ap" | jq -r '.capabilities')
  chan=$(freq_to_chan "$freq")
  band=$(band_of "$freq")
  enc="OPEN"
  echo "$cap" | grep -q "WPA3" && enc="WPA3"
  echo "$cap" | grep -q "WPA2" && enc="WPA2"
  echo "$cap" | grep -q "WPA"  && enc="WPA"
  echo "$cap" | grep -q "WEP"  && enc="WEP"
  mark=""
  [ -n "$mybssid" ] && [ "$(echo "$ap" | jq -r '.bssid')" = "$mybssid" ] && mark=" <==你"
  printf "%-26s %-5s %-5s %-6s %s%s\n" "${ssid:0:26}" "$chan" "$band" "$level" "$enc" "$mark"
done

echo
echo "===== 2.4GHz 信道拥堵统计 ====="
echo "$scan" | jq -r '.[] | select(.frequency_mhz < 3000) | .frequency_mhz' \
  | while read -r f; do freq_to_chan "$f"; done \
  | sort -n | uniq -c | sort -k2n \
  | while read -r cnt chan; do printf "信道 %-3s : %s 个 AP\n" "$chan" "$cnt"; done

echo
echo "===== 诊断结论 ====="
if [ -z "$mybssid" ]; then
  echo "未识别到你连接的 AP，请确认已连上房东 WiFi 后重跑"
  exit 0
fi
mychan=$(echo "$scan" | jq -c '.[]' | while read -r ap; do
  [ "$(echo "$ap" | jq -r '.bssid')" = "$mybssid" ] && freq_to_chan "$(echo "$ap" | jq -r '.frequency_mhz')"
done | head -1)
mylevel=$(echo "$scan" | jq -r --arg b "$mybssid" '.[] | select(.bssid==$b) | .level' | head -1)

if [ "$mychan" -gt 14 ] 2>/dev/null; then
  echo "你在 5G 频段（信道 $mychan），干扰通常较少，卡顿更可能是带宽/限速问题"
  exit 0
fi

# 2.4G 拥堵判定
onmine=$(echo "$scan" | jq -r --arg c "$mychan" '.[] | select((.frequency_mhz-2407)/5==($c|tonumber)) | .frequency_mhz' 2>/dev/null | wc -l)
adj=0
for d in -4 -3 -2 -1 1 2 3 4; do
  c=$((mychan+d)); [ "$c" -lt 1 ] && continue; [ "$c" -gt 14 ] && continue
  adj=$((adj + $(echo "$scan" | jq -r --argjson c "$c" '.[] | select((.frequency_mhz-2407)/5==$c) | .frequency_mhz' 2>/dev/null | wc -l)))
done
echo "你的 AP 在 2.4G 信道 $mychan，信号 $mylevel dBm"
echo "同信道 AP：$onmine 个，相邻重叠信道 AP：$adj 个"
if [ $((onmine+adj)) -ge 4 ]; then
  echo "⚠️ 信道拥挤！建议让房东把路由固定到最空的 1/6/11 之一"
  best=1; bestc=99
  for c in 1 6 11; do
    cc=$(( $(echo "$scan" | jq -r --argjson c "$c" '.[] | select((.frequency_mhz-2407)/5==$c) | .frequency_mhz' 2>/dev/null | wc -l) + adj ))
    [ "$cc" -lt "$bestc" ] && { bestc=$cc; best=$c; }
  done
  echo "最空信道建议：信道 $best"
else
  echo "✅ 信道不算挤，卡顿更可能是带宽被挤/限速，或隔墙信号差"
fi
echo "口诀：满格慢=带宽问题；少格慢=信号问题"
