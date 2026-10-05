#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_TOOL_DIR:?Set Android tools root (contains android-35, android-15 and ecj.jar)}"
: "${SIGNING_DIR:?Set private signing directory}"
: "${APK_OUTPUT:?Set output APK absolute path}"
PLATFORM_JAR="$ANDROID_TOOL_DIR/android-35/android.jar"
BUILD_TOOLS="$ANDROID_TOOL_DIR/android-15"
mkdir -p build/release-classes build/dex
java -jar "$ANDROID_TOOL_DIR/ecj.jar" -8 -nowarn -encoding UTF-8 -bootclasspath "$PLATFORM_JAR:$BUILD_TOOLS/core-lambda-stubs.jar" -d build/release-classes src/kr/family/praisesticker/*.java
python3 - <<'PY'
import pathlib,zipfile
root=pathlib.Path('build/release-classes')
with zipfile.ZipFile('build/release-classes.jar','w') as out:
 for path in root.rglob('*.class'):out.write(path,path.relative_to(root))
PY
"$BUILD_TOOLS/d8" --lib "$PLATFORM_JAR" --min-api 26 --output build/dex build/release-classes.jar
"$BUILD_TOOLS/aapt2" compile --dir res -o build/resources.zip
"$BUILD_TOOLS/aapt2" link -o build/unsigned.apk -I "$PLATFORM_JAR" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 35 build/resources.zip
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write('build/dex/classes.dex','classes.dex')
PY
"$BUILD_TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BUILD_TOOLS/apksigner" sign --ks "$SIGNING_DIR/praise-sticker-release.jks" --ks-key-alias praise-release --ks-pass "file:$SIGNING_DIR/store-password.txt" --out "$APK_OUTPUT" build/aligned.apk
"$BUILD_TOOLS/apksigner" verify --verbose "$APK_OUTPUT"
"$BUILD_TOOLS/aapt" dump badging "$APK_OUTPUT" | head -6
