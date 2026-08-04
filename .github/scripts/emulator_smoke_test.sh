#!/usr/bin/env bash
# reactivecircus/android-emulator-runner chạy MỖI DÒNG của trường `script:` YAML như một
# lệnh `sh -c` RIÊNG BIỆT (không phải một khối shell liền mạch) — nên mọi cấu trúc nhiều
# dòng (if/then/fi...) phải nằm trong MỘT file script như thế này, gọi qua "bash <file>".
set -euo pipefail

adb wait-for-device
adb install -r apk/app-debug.apk
adb logcat -c
adb shell am start -n com.kap.record.debug/com.kap.record.MainActivity
sleep 10
adb logcat -d > logcat.txt

PID=$(adb shell pidof com.kap.record.debug | tr -d '\r')
if [ -z "$PID" ]; then
  echo "::error::App process not found after launch — có thể đã crash. Xem logcat.txt."
  grep -i -E "FATAL EXCEPTION|AndroidRuntime" logcat.txt || true
  exit 1
fi
echo "App mở thành công, PID: $PID"
