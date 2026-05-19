# Device Masker Validation Status

Date: 2026-05-11

This file is the public validation status matrix for Device Masker. It summarizes what has been proven, where the proof lives, and what is still empty or pending. Raw internal reports remain under `docs/internal/reports/`; this public folder keeps curated status plus copied evidence files.

Values in emulator sections are a snapshot of the current test profile and can change after config regeneration.

## Evidence Rules

Runtime hook success requires all of these:

- Device Masker installed and enabled as an LSPosed module.
- LSPosed scope includes `android`, `system`, and the target package.
- Target app is force-stopped and relaunched after module, scope, or config changes.
- Logcat/LSPosed shows module load, target selection, hook registration, and spoof events.
- Target-app evidence confirms actual returned values where possible.

App launch alone and app-side Xposed service connection are not hook proof.

## Evidence Folder

```text
docs/public/validation/
├── DEVICE_MASKER_VALIDATION_STATUS.md
└── evidence/
    ├── emulator/
    │   ├── android-16/
    │   │   ├── build-gate.txt
    │   │   ├── config.json
    │   │   ├── latest.json
    │   │   └── logcat.txt
    │   └── android-13/
    │       └── README.md
    └── real-device/
        ├── android-16/
        │   └── README.md
        └── android-13/
            └── README.md
```

## Emulator

### Android 16

Environment:

| Field | Value |
| --- | --- |
| Device | Pixel 10 Pro XL API 36.1 emulator |
| ADB serial | `emulator-5554` |
| Android SDK | 36 |
| Page size | 16 KB |
| Target package | `com.astrixforge.devicemasker.verifier` |
| Profile | `TestingA16` |
| Build gate | `spotlessCheck detekt :xposed:testDebugUnitTest :app:assembleCiRelease :verifier:assembleDebug` |
| Build gate result | `BUILD SUCCESSFUL` |

Evidence:

| Type | Public evidence path |
| --- | --- |
| Verifier JSON | `docs/public/validation/evidence/emulator/android-16/latest.json` |
| Logcat | `docs/public/validation/evidence/emulator/android-16/logcat.txt` |
| Build gate | `docs/public/validation/evidence/emulator/android-16/build-gate.txt` |
| Config snapshot | `docs/public/validation/evidence/emulator/android-16/config.json` |

Runtime summary:

| Check | Result |
| --- | --- |
| LSPosed module load | PASS |
| Target package selection | PASS |
| Hook registration | PASS |
| Spoof events in logcat | PASS |
| Fatal hook/runtime signatures | PASS, none found |
| Relevant Device Masker/LSPosed `SecurityException` | PASS, none found |

Value matrix:

| Surface | Expected | Actual | Status |
| --- | --- | --- | --- |
| `ANDROID_ID` | `0a59bff3890b2e72` | `0a59bff3890b2e72` | PASS |
| `SERIAL` | `R37Z63804219` | `R37Z63804219` | PASS |
| `IMEI` | `352798117087133` | `352798117087133` | PASS |
| `IMEI_SLOT_0` | `352798117087133` | `352798117087133` | PASS |
| `IMSI` | `310200146134894` | `310200146134894` | PASS |
| `ICCID` | `8901206824170650549` | `8901206824170650549` | PASS |
| `PHONE_NUMBER` | `+17064971337` | `+17064971337` | PASS |
| `SIM_COUNTRY_ISO` | `us` | `us` | PASS |
| `NETWORK_COUNTRY_ISO` | `us` | `us` | PASS |
| `SIM_OPERATOR_NAME` | `T-Mobile` | `T-Mobile` | PASS |
| `NETWORK_OPERATOR` | `310200` | `310200` | PASS |
| `WIFI_MAC` | `BE:C1:74:76:48:A1` | `BE:C1:74:76:48:A1` | PASS |
| `WIFI_SSID` | `"eero-AE1D"` | `"eero-AE1D"` | PASS |
| `WIFI_BSSID` | `32:EB:54:2D:94:31` | `32:EB:54:2D:94:31` | PASS |
| `BLUETOOTH_MAC` | `7E:09:85:98:B6:A0` | `7E:09:85:98:B6:A0` | PASS |
| `ADVERTISING_ID` | `96b6bba2-10db-4fdb-87f5-09be422d985f` | `96b6bba2-10db-4fdb-87f5-09be422d985f` | PASS |
| `GSF_ID` | `461b38855b8b8900` | `461b38855b8b8900` | PASS |
| `MEDIA_DRM_ID` | `98297181f41374bc2edf7fc3ec849c05a895e2717928c142e7e7f3fea1919d06` | `98297181f41374bc2edf7fc3ec849c05a895e2717928c142e7e7f3fea1919d06` | PASS |
| `DEVICE_MODEL` | `SM-S928B` | `SM-S928B` | PASS |
| `DEVICE_MANUFACTURER` | `samsung` | `samsung` | PASS |
| `DEVICE_FINGERPRINT` | `samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys` | `samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys` | PASS |
| `TIMEZONE` | `America/Phoenix` | `America/Phoenix` | PASS |
| `LOCALE` | `es_US` | `es_US` | PASS |
| `LOCATION_LATITUDE` | `41.700052` | `41.700052` | PASS |
| `LOCATION_LONGITUDE` | `62.377687` | `62.377687` | PASS |
| `LOCATION_LAST_KNOWN` | Provider object with configured coordinates when Android has a last-known provider object | `null` | UNSUPPORTED |
| `SENSOR_DEFAULT_ACCELEROMETER` | generic normalized name | `Accelerometer` | PASS |
| `WEBVIEW_DEFAULT_UA` | contains `SM-S928B` | `Mozilla/5.0 (Linux; Android 16; SM-S928B Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36` | PASS |
| `WEBVIEW_INSTANCE_UA` | contains `SM-S928B` | `Mozilla/5.0 (Linux; Android 16; SM-S928B Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36` | PASS |

Notes:

- `LOCATION_LAST_KNOWN` is unsupported in the latest run because Android returned no last-known provider object after reboot. This is not a configured-value mismatch.
- Direct `Location.getLatitude()` and `Location.getLongitude()` are the deterministic coordinate spoof proof in this matrix.
- WebView instance UA is validated through concrete WebView provider settings discovery from `WebView.getSettings()`.

### Android 13

No current Android 13 value matrix is copied into this public validation package yet.

| Surface | Expected | Actual | Status |
| --- | --- | --- | --- |
| `ANDROID_ID` |  |  |  |
| `SERIAL` |  |  |  |
| `IMEI` |  |  |  |
| `IMSI` |  |  |  |
| `ICCID` |  |  |  |
| `PHONE_NUMBER` |  |  |  |
| `DEVICE_MODEL` |  |  |  |
| `LOCATION_LATITUDE` |  |  |  |
| `LOCATION_LONGITUDE` |  |  |  |
| `WEBVIEW_DEFAULT_UA` |  |  |  |
| `WEBVIEW_INSTANCE_UA` |  |  |  |

## Real Device

### Android 16

No real-device Android 16 value matrix is recorded in this public validation package yet.

| Surface | Expected | Actual | Status |
| --- | --- | --- | --- |
| `ANDROID_ID` |  |  |  |
| `SERIAL` |  |  |  |
| `IMEI` |  |  |  |
| `IMSI` |  |  |  |
| `ICCID` |  |  |  |
| `PHONE_NUMBER` |  |  |  |
| `DEVICE_MODEL` |  |  |  |
| `LOCATION_LATITUDE` |  |  |  |
| `LOCATION_LONGITUDE` |  |  |  |
| `WEBVIEW_DEFAULT_UA` |  |  |  |
| `WEBVIEW_INSTANCE_UA` |  |  |  |

### Android 13

No real-device Android 13 value matrix is recorded in this public validation package yet.

| Surface | Expected | Actual | Status |
| --- | --- | --- | --- |
| `ANDROID_ID` |  |  |  |
| `SERIAL` |  |  |  |
| `IMEI` |  |  |  |
| `IMSI` |  |  |  |
| `ICCID` |  |  |  |
| `PHONE_NUMBER` |  |  |  |
| `DEVICE_MODEL` |  |  |  |
| `LOCATION_LATITUDE` |  |  |  |
| `LOCATION_LONGITUDE` |  |  |  |
| `WEBVIEW_DEFAULT_UA` |  |  |  |
| `WEBVIEW_INSTANCE_UA` |  |  |  |

## Not Claimed

This validation package does not claim:

- Physical-device stability from emulator evidence.
- Native `/proc/self/maps` scanner coverage.
- Play Integrity, SafetyNet, or hardware attestation bypass.
- Exhaustive disabled, missing, blank, malformed, or unsafe config pass-through coverage for every hook.
- Broad target-app category coverage beyond the listed evidence.
