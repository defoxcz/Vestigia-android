#!/bin/zsh
# Run only explicitly selected tests and preserve both application and test APK data.
set -euo pipefail
cd "${0:A:h:h}"
classes="${1:?Zadejte úplné názvy testovacích tříd oddělené čárkou}"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
serial="${ANDROID_SERIAL:-}"
if [[ -z "$serial" ]]; then
  device_list="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  devices=(${(f)device_list})
  if [[ -z "$device_list" ]] || (( ${#devices} != 1 )); then
    print -u2 "Připojte jeden autorizovaný telefon nebo nastavte ANDROID_SERIAL."
    exit 1
  fi
  serial="$devices[1]"
fi
[[ "$serial" != emulator-* ]] || { print -u2 "Použijte skutečný telefon."; exit 1; }
./gradlew assembleDebug assembleDebugAndroidTest
./scripts/check-apk.sh debug app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result="$(mktemp)"
trap 'rm -f "$result"' EXIT
adb -s "$serial" shell am instrument -w -r -e class "$classes" com.vestigiascriptorium.android.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "$result"
# am instrument can exit zero even when a test fails.
rg -q '^OK \([0-9]+ tests?\)' "$result"
