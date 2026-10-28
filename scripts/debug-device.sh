#!/bin/zsh
set -eu
cd "${0:A:h:h}"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
state_file=".gradle/adb-debug-device"
port=5005
if [[ "${1:-}" == cleanup ]]; then
  if [[ -f "$state_file" ]]; then
    serial="$(cat "$state_file")"
    adb -s "$serial" forward --remove "tcp:$port" 2>/dev/null || true
    rm -f "$state_file"
  fi
  exit 0
fi
serial="${ANDROID_SERIAL:-}"
if [[ -z "$serial" ]]; then
  device_list="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  devices=(${(f)device_list})
  if [[ -z "$device_list" ]] || (( ${#devices} != 1 )); then
    print -u2 "Připojte a autorizujte jeden telefon. Při více zařízeních nastavte ANDROID_SERIAL."
    adb devices -l
    exit 1
  fi
  serial="$devices[1]"
fi
if [[ "$serial" == emulator-* ]]; then
  print -u2 "Použijte skutečné zařízení."
  exit 1
fi
./gradlew assembleDebug
./scripts/check-apk.sh debug app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
./scripts/debug-device.sh cleanup
adb -s "$serial" shell am force-stop com.vestigiascriptorium.android.debug
adb -s "$serial" shell am start -D -n com.vestigiascriptorium.android.debug/com.vestigiascriptorium.android.MainActivity
pid=""
for attempt in {1..50}; do
  pid="$(adb -s "$serial" shell pidof -s com.vestigiascriptorium.android.debug | tr -d '\r')" || true
  [[ -n "$pid" ]] && break
  sleep 0.2
done
if [[ -z "$pid" ]]; then
  print -u2 "Proces aplikace se nespustil. Zkontrolujte adb logcat."
  exit 1
fi
adb -s "$serial" forward --no-rebind "tcp:$port" "jdwp:$pid"
mkdir -p .gradle
print -r -- "$serial" > "$state_file"
print "Aplikace čeká na debugger na localhost:$port (PID $pid)."
