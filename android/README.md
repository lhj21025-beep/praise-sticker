# 칭찬 스티커 — native Android

Android 8.0+ (minSdk 26), targetSdk 35, package `kr.family.praisesticker`, version 1.0.0 (code 1).

This is a native Java/Android View application. It contains no WebView, HTML assets, JavaScript engine, browser intent, or remote page wrapper. The 12 board themes and roulette wheel use Android Canvas; forms and dialogs use Android controls.

## Data and synchronization

The app uses the existing `praise-sticker-756b4` Firebase Auth / Firestore backend, anonymously authenticated using the same configuration as the web client. Parent password validation matches the existing web behavior; the remembered parent password is encrypted using Android Keystore AES-GCM. No user data is embedded in the APK.

Refresh occurs on opening/resuming the app, after writes, via the refresh control, and every 60 seconds while the app is foregrounded and no dialog is open. No background poll runs after leaving the app. The existing web client requires refresh to show changes made elsewhere. This is **not** continuous real-time streaming or offline mutation support.

Optimistic Firestore commits (read-only snapshots plus per-document update-time preconditions) atomically exchange retry rewards, split balances, save roulette outcomes, grant completion rewards and update rollover settlements. StoreTest uses an in-memory REST simulator and never writes production data. External edits by an old web session are governed by that web version's existing implementation.

## Build

Use Java 17 (runtime sufficient with ECJ), Android platform 35 (`platform-35_r02.zip`), Android build-tools 35 (`build-tools_r35_linux.zip`, unpacked directory `android-15`), and ECJ 3.37.0. `android-15` is the vendor archive's directory name, not API level 15.

```
ANDROID_TOOL_DIR=/absolute/android-tools \
SIGNING_DIR=/absolute/private-signing \
APK_OUTPUT=/absolute/PraiseSticker_Native_v1.0.0.apk \
bash build.sh
```

The tools directory contains `android-35/android.jar`, `android-15/`, `ecj.jar`. Signing directory contains `praise-sticker-release.jks` and `store-password.txt`; alias `praise-release`. Both are private, never committed. The build verifies the generated APK signature. Keep the original key for all updates.

Run fixture tests with:

```
ANDROID_TOOL_DIR=/absolute/android-tools bash test.sh
```

Tests additionally use org.json 20240303 as `json.jar` in the tools directory. They cover transactions, retry aliases, probability snapshots, conflicts, duplicate requests, game splits, failed rollover validation, bonus thresholds, lifetime score rules, completion rewards, and Firestore serialization.

## Validation for 1.0.0

- Java compilation and D8 dex generation passed.
- 25 deterministic data checks passed; no production writes.
- The actual Java HTTP client passed anonymous sign-in, token refresh, Firestore reads in a read-only snapshot, rollback, and a verify-only atomic commit. No production documents were written.
- APK signature and package metadata verification passed.
- No physical Android device or emulator was available in the creation environment. Installation, UI rendering, lifecycle, Android share/storage dialogs, and device networking must still be verified on a device. Do not describe these as already tested.

## Platform differences

The web's layout, colors, controls and flows are recreated, not pixel-identical. Font/emoji rendering, native save/share dialogs and individual Canvas theme animations differ. The app updates via a newly signed APK; web deployments do not replace installed native code. Both use the same family data. See PARITY.md before every improvement.
