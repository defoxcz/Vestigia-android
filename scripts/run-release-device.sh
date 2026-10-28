#!/bin/zsh
set -eu
cd "${0:A:h:h}"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
serial="${1:-${ANDROID_SERIAL:-}}"
if [[ -z "$serial" ]]; then
  device_list="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  devices=(${(f)device_list})
  if [[ -z "$device_list" ]] || (( ${#devices} != 1 )); then
    print -u2 "Připojte a autorizujte jeden telefon, nebo zadejte sériové číslo: ./scripts/run-device.sh SERIAL"
    adb devices -l
    exit 1
  fi
  serial="$devices[1]"
fi
if [[ "$serial" == emulator-* ]]; then
  print -u2 "Použijte skutečné zařízení."
  exit 1
fi
./gradlew assembleRelease
./scripts/check-apk.sh release app/build/outputs/apk/release/app-release.apk
adb -s "$serial" install -r app/build/outputs/apk/release/app-release.apk
adb -s "$serial" shell am start -W -n com.vestigiascriptorium.android.release/com.vestigiascriptorium.android.MainActivity
