<p align="center">
  <img src="docs/assets/brand/devicemasker-icon.png" alt="Device Masker app icon" width="200" height="200">
</p>

# Device Masker

**Android LSPosed module for per-app device identity spoofing with anti-detection.**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![LSPosed](https://img.shields.io/badge/LSPosed-1.10.2%2B-orange.svg)](https://github.com/LSPosed/LSPosed)

---

Device Masker intercepts Android framework APIs inside selected app processes to present controlled, spoofed device identities. It uses libxposed API 101 with RemotePreferences for live configuration delivery — no target app restart required for config reads.

---

## Features

### Identifier Spoofing

- **Android ID**, **Advertising ID**, **GSF ID**, **Media DRM ID**
- **IMEI**, **IMSI**, **ICCID**, **phone number**, **serial number**
- **SIM/carrier**: carrier name, MCC/MNC, country ISO, network operator
- **Network**: WiFi MAC, SSID, BSSID, Bluetooth MAC
- **Device profile**: manufacturer, model, brand, device, board, hardware, fingerprint
- **Location**: GPS coordinates, timezone, locale
- **Sensor**: sensor list filtering, vendor/version normalization
- **WebView**: user-agent string spoofing matching active device profile

### Anti-Detection

- Stack trace filtering (removes Xposed/libxposed frames)
- `/proc/self/maps` line filtering
- Package visibility hiding (LSPosed, Magisk, Device Masker self-hide from target app queries)

### Configuration

- **Per-app spoof groups**: create multiple identity profiles, assign different apps to different groups
- **Correlated values**: SIM/carrier values stay internally consistent; device hardware values match the same preset; location/timezone/locale derive from the same country
- **10 device presets**: Pixel 8 Pro, Samsung S24 Ultra, OnePlus 12, Xiaomi 14 Pro, Nothing Phone 2, and more
- **Live config delivery**: changes sync to hooked processes via libxposed RemotePreferences — force-stop target app to pick up new values

### Diagnostics

- Structured JSONL app-side logs (rootless)
- LSPosed log integration for hook registration and spoof event verification
- Support bundle export (Basic, Full Debug, Root Maximum)
- Opt-in root evidence collection with bounded artifact capture

---

## Requirements

- Android 8.0 — 16 (API 26 — 36)
- Rooted device (Magisk, KernelSU, or APatch)
- LSPosed 1.10.2+ (Zygisk version recommended)

---

## Installation

1. Install and activate LSPosed framework.
2. Install the Device Masker APK.
3. In LSPosed Manager, enable Device Masker and select scope:
   - **Required**: `android` (system framework) + `system` (system_server)
   - **Add**: each target app you want to spoof
   - **Do not** scope Device Masker itself
4. Open Device Masker and wait for the service connection indicator.
5. Create a spoof group, assign apps, enable spoof types, and verify generated values.
6. Force-stop and relaunch target apps.

> **Note**: Config changes require a target app restart. Use Android Settings → Apps → [App Name] → Force Stop.

---

## Architecture

```
:app          Compose UI, ViewModels, config persistence, RemotePreferences writer,
              rootless logs, diagnostics, root evidence collection

:common       Shared models (JsonConfig, SpoofType, SharedPrefsKeys), identity
              generators, DevicePersona, config contracts

:xposed       libxposed module entry, 11 hookers, RemotePreferences reader,
              anti-detection, LSPosed/logcat hook diagnostics
```

**Config delivery flow:**

```
UI → SpoofRepository → ConfigManager (config.json)
    → ConfigSync → XposedPrefs → RemotePreferences (LSPosed bridge)
                                        ↓
                              Hookers read in target process
```

Spoof config is delivered exclusively through RemotePreferences. Hook evidence comes from LSPosed/logcat and optional root-captured logs.

---

## Build from Source

### Prerequisites

- JDK 17
- Android SDK (compile SDK 37)
- Gradle (wrapper included)

### Commands

```bash
# Clone
git clone https://github.com/mohdakil2426/DeviceMasker.git
cd devicemasker

# Debug build
./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Full quality gate (formatting, lint, tests, debug + release builds)
./gradlew spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest \
  :xposed:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon
```

---

## Technology Stack

| Component    | Library              | Version      |
|-------------|----------------------|-------------|
| Language     | Kotlin               | 2.3.0       |
| Android      | compileSdk / targetSdk | 37 / 36   |
| Hooking      | libxposed API        | 101.0.1     |
| UI           | Jetpack Compose + Material 3 | BOM 2026.04.01 |
| Navigation   | Navigation 3         | 1.1.1       |
| Serialization | kotlinx.serialization | 1.10.0    |
| Async        | kotlinx.coroutines   | 1.10.2      |
| Persistence  | DataStore + JSON     | 1.2.0       |
| Root access  | libsu                | 6.0.0       |

---

## Contributing

Contributions are welcome. Please follow conventional commit standards and ensure all code passes the project's quality gate before submitting.

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

---

## Disclaimer

This module is intended for **security research and privacy education only**. Users are solely responsible for compliance with applicable laws. The developers assume no liability for misuse.
