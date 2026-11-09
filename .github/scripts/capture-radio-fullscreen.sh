#!/usr/bin/env bash
set -euo pipefail
cd "$GITHUB_WORKSPACE/android-app"
REPORT=app/build/reports/androidTests/fullscreen-review
mkdir -p "$REPORT"
# Synthetic layout only: no account, network, media library or user content.
# Reinstall because connectedAndroidTest may clean up its application packages.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class 'mx.sntss1puebla.credenciales.RadioFullscreenControlsTest#onlyFullscreenChromeFadesAndLyricRemainsVisible' \
  mx.sntss1puebla.credenciales.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$REPORT/instrumentation.txt"
grep -Fq 'OK (1 test)' "$REPORT/instrumentation.txt"
for name in fullscreen-controls-visible.png fullscreen-controls-hidden.png; do
  adb exec-out run-as mx.sntss1puebla.credenciales cat "cache/radio-fullscreen-review/$name" > "$REPORT/$name"
  test -s "$REPORT/$name"
done
