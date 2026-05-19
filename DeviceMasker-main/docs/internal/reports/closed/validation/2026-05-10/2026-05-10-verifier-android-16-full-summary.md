# Android 16 Verifier Full Summary - 2026-05-10

Device: `emulator-5554` / Pixel 10 Pro XL API 36.1 / Android 16
Verifier package: `com.astrixforge.devicemasker.verifier`
Profile: `TestingA16`

## 2026-05-10 Result

Latest verifier rerun after enabling latitude and longitude on 2026-05-10: PASS=27, FAIL=1, NOT_CONFIGURED=0.

The failure in that run was `WEBVIEW_INSTANCE_UA`. It is superseded by the 2026-05-11 emulator stabilization result below.

## 2026-05-11 Emulator Stabilization Result

Latest emulator rerun after the WebView instance fix: all configured verifier values passed. `WEBVIEW_INSTANCE_UA` now contains the spoofed model `SM-S928B`.

Verifier matrix status from `logs/device/2026-05-11-final-all-emulator-latest.json`:

- `OBSERVED=26`
- `UNSUPPORTED=1`
- `ERROR=0`
- `PLATFORM_RESTRICTED=0`
- `PERMISSION_DENIED=0`

The only non-observed row is `LOCATION_LAST_KNOWN`, where Android returned no last-known GPS object after reboot. Direct `Location.getLatitude()` and `Location.getLongitude()` probes passed with the configured spoof coordinates, so coordinate spoofing is still confirmed.

## Evidence Files

- Config snapshot: `logs/device/2026-05-10-config-after-latlong-enabled.json`
- Verifier JSON: `logs/device/2026-05-10-verifier-matrix-latlong-enabled-latest.json`
- Logcat: `logs/device/2026-05-10-verifier-matrix-latlong-enabled-logcat.txt`
- Earlier post-reboot JSON: `logs/device/2026-05-10-verifier-matrix-post-reboot-latest.json`
- Earlier post-reboot logcat: `logs/device/2026-05-10-verifier-matrix-post-reboot-logcat.txt`
- Build verification: `logs/build/2026-05-10-verifier-matrix-final-verification.txt`
- Final emulator verifier JSON: `logs/device/2026-05-11-final-all-emulator-latest.json`
- Final emulator logcat: `logs/device/2026-05-11-final-all-emulator-logcat.txt`
- Final static/R8 gate: `logs/build/2026-05-11-final-emulator-stability-gate.txt`

## Confirmed Working

- LSPosed loaded the module into the verifier process.
- All hook families registered for the verifier process.
- Logcat contains 42 spoof-event lines for the verifier package.
- No checked fatal/runtime signatures appeared in the latest logcat window.
- Android ID, serial, IMEI, IMSI, ICCID, phone number, SIM/network values, Wi-Fi, Bluetooth MAC, Advertising ID, GSF ID, MediaDRM ID, device profile fields, timezone, locale, latitude, longitude, default accelerometer normalization, and static WebView UA matched the expected verifier result.

## Location Verification

Configured values:

- `LOCATION_LATITUDE=41.700052`
- `LOCATION_LONGITUDE=62.377687`

Verifier values:

- `location.gpsLastKnown.latitude=41.700052`
- `location.gpsLastKnown.longitude=62.377687`
- `location.syntheticLocationLatitude=41.700052`
- `location.syntheticLocationLongitude=62.377687`

Status: PASS.

## Fixed Failure

Before the 2026-05-11 fix, `WEBVIEW_INSTANCE_UA` returned:

`Mozilla/5.0 (Linux; Android 16; sdk_gphone16k_x86_64 Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36`

Expected: the value should contain spoofed model `SM-S928B`.

Root cause: `android.webkit.WebSettings` declares abstract instance methods, so direct base-class hooking skipped `getUserAgentString()`. The fix hooks `WebView.getSettings()`, discovers the concrete provider settings class, and hooks the inherited/concrete user-agent methods there.

Final result: `WEBVIEW_INSTANCE_UA` now returns:

`Mozilla/5.0 (Linux; Android 16; SM-S928B Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36`

## Notes

- `telephony.subscriberIdSlot0` reports `NoSuchMethodException` because the verifier tried a platform method shape that is not present on this API. This is not counted as a spoof failure because the normal `getSubscriberId()` path passes.
- `procMaps.javaFileInputStream.suspiciousLineCount=2` remains expected for this run because byte/NIO maps redaction is explicit opt-in and was not enabled. BufferedReader and RandomAccessFile path-aware probes reported zero suspicious lines.
- Package visibility hiding for Device Masker and LSPosed Manager passed in the verifier JSON. Magisk remained visible in this run and is not counted as a value-spoof failure.

## Remaining Emulator Caveat

`LOCATION_LAST_KNOWN` can still be `UNSUPPORTED` when Android has no last-known provider object after reboot. This is not a configured-value mismatch. The direct latitude/longitude getter probes are the deterministic coordinate-spoof evidence.
