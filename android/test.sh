#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_TOOL_DIR:?Set Android tools root}"
mkdir -p build/test-classes
java -jar "$ANDROID_TOOL_DIR/ecj.jar" -8 -nowarn -encoding UTF-8 -bootclasspath "$ANDROID_TOOL_DIR/android-35/android.jar:$ANDROID_TOOL_DIR/android-15/core-lambda-stubs.jar" -d build/test-classes src/kr/family/praisesticker/*.java tests/kr/family/praisesticker/*.java
java -cp "$ANDROID_TOOL_DIR/json.jar:build/test-classes:$ANDROID_TOOL_DIR/android-35/android.jar" kr.family.praisesticker.StoreTest
