# Android 16 Emulator Stability Summary - 2026-05-11

Device: `emulator-5554` / Pixel 10 Pro XL API 36.1 / Android 16 / SDK 36 / 16 KB pages

## Verdict

Within emulator-only coverage, the app is stable for the tested target-hook flows. The verifier target launches under LSPosed, hooks register, configured spoof values are applied, and the checked logcat window has no fatal hook/runtime signatures.

This does not claim physical-device coverage.

## Fixed In This Pass

- Fixed WebView instance UA spoofing.
- Added verifier matrix rows for direct latitude and longitude getter probes.
- Added safe last-known-location null handling for enabled valid coordinates.

## Runtime Evidence

- Final verifier JSON: `logs/device/2026-05-11-final-all-emulator-latest.json`
- Final logcat: `logs/device/2026-05-11-final-all-emulator-logcat.txt`
- Final install logs:
  - `logs/device/2026-05-11-final-all-emulator-install-app.txt`
  - `logs/device/2026-05-11-final-all-emulator-install-verifier.txt`

Final verifier highlights:
- `WEBVIEW_DEFAULT_UA` contains `SM-S928B`
- `WEBVIEW_INSTANCE_UA` contains `SM-S928B`
- `LOCATION_LATITUDE=41.700052`
- `LOCATION_LONGITUDE=62.377687`
- `LOCATION_LAST_KNOWN=UNSUPPORTED` because Android returned no last-known provider object after reboot
- Spoof-event lines: 26
- Fatal hook/runtime signatures: 0
- Relevant Device Masker/LSPosed `SecurityException`: 0

## Static And R8 Gate

Command:

```powershell
.\gradlew.bat spotlessCheck detekt :xposed:testDebugUnitTest :app:assembleCiRelease :verifier:assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL`

Log: `logs/build/2026-05-11-final-emulator-stability-gate.txt`

## Remaining Emulator Caveat

`LocationManager.getLastKnownLocation()` is not deterministic on a freshly rebooted emulator. The current verifier correctly marks it `UNSUPPORTED` when Android has no last-known provider object. Direct `Location.getLatitude()` and `Location.getLongitude()` are the deterministic coordinate spoof proof for this matrix.
