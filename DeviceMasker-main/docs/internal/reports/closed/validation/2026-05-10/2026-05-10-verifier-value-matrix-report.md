# Verifier Matrix Report - 2026-05-10

Device: emulator-5554 / Pixel 10 Pro XL API 36.1 / Android 16
Package: com.astrixforge.devicemasker.verifier
Profile: TestingA16
Captured: 1778434047972

Summary: PASS=25, FAIL=1, NOT_CONFIGURED=2

## Rerun After Latitude/Longitude Enabled

Captured: 1778435412214

Summary: PASS=27, FAIL=1, NOT_CONFIGURED=0

| Surface | Expected | Actual | Status |
| --- | --- | --- | --- |
| ANDROID_ID | 0a59bff3890b2e72 | 0a59bff3890b2e72 | PASS |
| SERIAL | R37Z63804219 | R37Z63804219 | PASS |
| IMEI | 352798117087133 | 352798117087133 | PASS |
| IMEI_SLOT_0 | 352798117087133 | 352798117087133 | PASS |
| IMSI | 310200146134894 | 310200146134894 | PASS |
| ICCID | 8901206824170650549 | 8901206824170650549 | PASS |
| PHONE_NUMBER | +17064971337 | +17064971337 | PASS |
| SIM_COUNTRY_ISO | us | us | PASS |
| NETWORK_COUNTRY_ISO | us | us | PASS |
| SIM_OPERATOR_NAME | T-Mobile | T-Mobile | PASS |
| NETWORK_OPERATOR | 310200 | 310200 | PASS |
| WIFI_MAC | BE:C1:74:76:48:A1 | BE:C1:74:76:48:A1 | PASS |
| WIFI_SSID | "eero-AE1D" | "eero-AE1D" | PASS |
| WIFI_BSSID | 32:EB:54:2D:94:31 | 32:EB:54:2D:94:31 | PASS |
| BLUETOOTH_MAC | 7E:09:85:98:B6:A0 | 7E:09:85:98:B6:A0 | PASS |
| ADVERTISING_ID | 96b6bba2-10db-4fdb-87f5-09be422d985f | 96b6bba2-10db-4fdb-87f5-09be422d985f | PASS |
| GSF_ID | 461b38855b8b8900 | 461b38855b8b8900 | PASS |
| MEDIA_DRM_ID | 98297181f41374bc2edf7fc3ec849c05a895e2717928c142e7e7f3fea1919d06 | 98297181f41374bc2edf7fc3ec849c05a895e2717928c142e7e7f3fea1919d06 | PASS |
| DEVICE_MODEL | SM-S928B | SM-S928B | PASS |
| DEVICE_MANUFACTURER | samsung | samsung | PASS |
| DEVICE_FINGERPRINT | samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys | samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys | PASS |
| TIMEZONE | America/Phoenix | America/Phoenix | PASS |
| LOCALE | es_US | es_US | PASS |
| LOCATION_LATITUDE | 41.700052 | 41.700052 | PASS |
| LOCATION_LONGITUDE | 62.377687 | 62.377687 | PASS |
| SENSOR_DEFAULT_ACCELEROMETER | generic normalized name | Accelerometer | PASS |
| WEBVIEW_DEFAULT_UA | contains SM-S928B | Mozilla/5.0 (Linux; Android 16; SM-S928B Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36 | PASS |
| WEBVIEW_INSTANCE_UA | contains SM-S928B | Mozilla/5.0 (Linux; Android 16; sdk_gphone16k_x86_64 Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36 | FAIL |

Hook evidence:
- `logs/device/2026-05-10-verifier-matrix-latlong-enabled-logcat.txt` shows `XposedEntry loaded`, `All hooks registered`, and 42 `Spoof event` lines for the verifier package.
- Spoof events include `LOCATION_LATITUDE` and `LOCATION_LONGITUDE`.
- Checked fatal/runtime signatures count: 0 for `FATAL EXCEPTION`, `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, `HookFailedError`, `Cannot hook abstract`, and `SecurityException`.

Superseded issue:
- This run showed `WEBVIEW_INSTANCE_UA` leaking `sdk_gphone16k_x86_64`. The 2026-05-11 rerun below fixes that path through concrete WebView provider settings discovery.

## 2026-05-11 WebView Instance Fix Rerun

Evidence:
- `logs/device/2026-05-11-final-all-emulator-latest.json`
- `logs/device/2026-05-11-final-all-emulator-logcat.txt`
- `logs/build/2026-05-11-final-emulator-stability-gate.txt`

Summary:
- Configured spoof values checked by verifier: PASS
- `WEBVIEW_DEFAULT_UA`: PASS, contains `SM-S928B`
- `WEBVIEW_INSTANCE_UA`: PASS, contains `SM-S928B`
- `LOCATION_LATITUDE`: PASS, synthetic `Location.getLatitude()` returned `41.700052`
- `LOCATION_LONGITUDE`: PASS, synthetic `Location.getLongitude()` returned `62.377687`
- `LOCATION_LAST_KNOWN`: UNSUPPORTED, Android returned no last-known provider object after reboot
- Hook fatal/runtime signatures: 0
- Relevant Device Masker/LSPosed `SecurityException`: 0
- Spoof-event lines in checked log window: 26

Root cause fixed:
- The previous WebView instance failure came from hooking only the abstract `android.webkit.WebSettings` instance methods. The fix discovers the concrete WebView provider settings class through `WebView.getSettings()` and hooks the inherited/concrete user-agent methods.

| Surface | Expected | Actual | Status |
| --- | --- | --- | --- |
| ANDROID_ID | 0a59bff3890b2e72 | 0a59bff3890b2e72 | PASS |
| SERIAL | R37Z63804219 | R37Z63804219 | PASS |
| IMEI | 352798117087133 | 352798117087133 | PASS |
| IMEI_SLOT_0 | 352798117087133 | 352798117087133 | PASS |
| IMSI | 310200146134894 | 310200146134894 | PASS |
| ICCID | 8901206824170650549 | 8901206824170650549 | PASS |
| PHONE_NUMBER | +17064971337 | +17064971337 | PASS |
| SIM_COUNTRY_ISO | us | us | PASS |
| NETWORK_COUNTRY_ISO | us | us | PASS |
| SIM_OPERATOR_NAME | T-Mobile | T-Mobile | PASS |
| NETWORK_OPERATOR | 310200 | 310200 | PASS |
| WIFI_MAC | BE:C1:74:76:48:A1 | BE:C1:74:76:48:A1 | PASS |
| WIFI_SSID | "eero-AE1D" | "eero-AE1D" | PASS |
| WIFI_BSSID | 32:EB:54:2D:94:31 | 32:EB:54:2D:94:31 | PASS |
| BLUETOOTH_MAC | 7E:09:85:98:B6:A0 | 7E:09:85:98:B6:A0 | PASS |
| ADVERTISING_ID | 96b6bba2-10db-4fdb-87f5-09be422d985f | 96b6bba2-10db-4fdb-87f5-09be422d985f | PASS |
| GSF_ID | 461b38855b8b8900 | 461b38855b8b8900 | PASS |
| MEDIA_DRM_ID | 98297181f41374bc2edf7fc3ec849c05a895e2717928c142e7e7f3fea1919d06 | 98297181f41374bc2edf7fc3ec849c05a895e2717928c142e7e7f3fea1919d06 | PASS |
| DEVICE_MODEL | SM-S928B | SM-S928B | PASS |
| DEVICE_MANUFACTURER | samsung | samsung | PASS |
| DEVICE_FINGERPRINT | samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys | samsung/dm3qxxx/dm3q:14/UP1A.231005.007/S928BXXS2AXL5:user/release-keys | PASS |
| TIMEZONE | America/Phoenix | America/Phoenix | PASS |
| LOCALE | es_US | es_US | PASS |
| LOCATION_LATITUDE | 30.004176 | 37.4219983333333 | NOT_CONFIGURED |
| LOCATION_LONGITUDE | -95.395230 | -122.084 | NOT_CONFIGURED |
| SENSOR_DEFAULT_ACCELEROMETER | generic normalized name | Accelerometer | PASS |
| WEBVIEW_DEFAULT_UA | contains SM-S928B | Mozilla/5.0 (Linux; Android 16; SM-S928B Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36 | PASS |
| WEBVIEW_INSTANCE_UA | contains SM-S928B | Mozilla/5.0 (Linux; Android 16; sdk_gphone16k_x86_64 Build/BE4B.251210.005; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Mobile Safari/537.36 | FAIL |

Evidence:
- logs/device/2026-05-10-verifier-matrix-post-reboot-latest.json
- logs/device/2026-05-10-verifier-matrix-post-reboot-logcat.txt
- logs/build/2026-05-10-verifier-matrix-build-2.txt
