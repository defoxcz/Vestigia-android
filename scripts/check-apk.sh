#!/bin/zsh
set -eu
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
variant="${1:?Zadejte debug nebo release}"
apk="${2:?Zadejte cestu APK}"
case "$variant" in
  debug) expected_debuggable=true ;;
  release) expected_debuggable=false ;;
  *) print -u2 "Neznámá varianta: $variant"; exit 1 ;;
esac
sdk="$ANDROID_HOME"
badging="$("$sdk/build-tools/36.0.0/aapt" dump badging "$apk")"
actual_id="$(print -r -- "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
actual_debuggable=false
if [[ "$badging" == *$'\napplication-debuggable'* ]]; then
  actual_debuggable=true
fi
if [[ "$actual_id" != "com.vestigiascriptorium.android.$variant" || "$actual_debuggable" != "$expected_debuggable" ]]; then
  print -u2 "Instalace odmítnuta: APK neodpovídá variantě $variant ($actual_id, debuggable=$actual_debuggable)."
  exit 1
fi
"$sdk/build-tools/36.0.0/apksigner" verify "$apk"
