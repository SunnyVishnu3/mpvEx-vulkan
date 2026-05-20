# :verifier Module Guide

Local target app for Android emulator/device validation. This APK is not production code; it exists to prove what a real target process sees after LSPosed hooks are applied.

## Responsibilities

- Read Android framework surfaces from inside the target app process.
- Write machine-readable evidence to `files/verifier/latest.json`.
- Keep probes simple, deterministic, and safe to run repeatedly.
- Classify unsupported API shapes, permission denial, and platform restrictions separately from real spoof failures.

## Must Not Do

- Do not read Device Masker app-private JSON directly.
- Do not write RemotePreferences or change Device Masker config.
- Do not add production app dependencies or UI complexity.
- Do not hide failures by replacing probe errors with fake pass values.

## Evidence Rules

- Expected values come from a separate Device Masker config snapshot, not from verifier internals.
- Actual values come from `files/verifier/latest.json`.
- Hook-load proof comes from LSPosed/logcat: `XposedEntry loaded`, hook registration, and spoof events.
- Runtime reports must include the verifier JSON, logcat file, and config snapshot under `logs/device/`.
- Android 16 value checks must compare the Device Masker config snapshot against `files/verifier/latest.json` expected vs actual values.

## Android 16 Notes

- Normal target-SDK 29+ apps can hit Android platform restrictions for persistent telephony identifiers and `Build.getSerial()`. If a supported hooked path returns the configured value, the restriction is not a verifier failure.
- Latitude and longitude require both config values enabled and runtime location permissions granted to the verifier.
- WebView UA coverage requires both static default UA and instance `WebSettings.getUserAgentString()` evidence. The Android 16 emulator verifier currently proves both through the public validation package; rerun after hook changes.
- `SystemProperties` reflection exists only as target-process evidence collection. Keep `PrivateApi` lint suppression narrow and local to that probe.
- The verifier manifest must stay lint-clean with explicit launcher icon, backup, and data-extraction rules even though the APK is not production.

## Build And Run

```powershell
.\gradlew.bat :verifier:assembleDebug --no-daemon
adb -s emulator-5554 install -r verifier\build\outputs\apk\debug\verifier-debug.apk
adb -s emulator-5554 shell monkey -p com.astrixforge.devicemasker.verifier -c android.intent.category.LAUNCHER 1
adb -s emulator-5554 shell run-as com.astrixforge.devicemasker.verifier cat files/verifier/latest.json
```
