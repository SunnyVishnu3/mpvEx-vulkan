# Device Profile Runtime Coverage Matrix

Date: 2026-05-09

Scope: `DEVICE_PROFILE` values generated in `:common`, synced by `:app`, and consumed by `:xposed`.

## Current Verdict

The high-signal profile fields now have explicit runtime coverage for app-process reads. Unsupported or dangerous security posture surfaces are diagnostics-only and are not spoofed.

## Coverage

| Field | Generated | Synced | Runtime Surface | Status |
| --- | --- | --- | --- | --- |
| `brand` | Yes, `DeviceProfilePreset` | Flat `DEVICE_PROFILE` + persona blob | `Build.BRAND`, `ro.product.brand`, `ro.vendor.product.brand` | Hooked |
| `manufacturer` | Yes | Flat + persona | `Build.MANUFACTURER`, `ro.product.manufacturer`, `ro.vendor.product.manufacturer` | Hooked |
| `model` | Yes | Flat + persona | `Build.MODEL`, `ro.product.model`, `ro.vendor.product.model` | Hooked |
| `device` | Yes | Flat + persona | `Build.DEVICE`, `ro.product.device`, `ro.vendor.product.device` | Hooked |
| `product` | Yes | Flat + persona | `Build.PRODUCT`, `ro.product.name`, `ro.build.product` | Hooked |
| `board` | Yes | Flat + persona | `Build.BOARD`, `Build.HARDWARE`, `ro.product.board` | Hooked |
| `fingerprint` | Yes | Flat + persona | `Build.FINGERPRINT`, build fingerprint system properties | Hooked |
| `buildId` | Yes | Flat + persona | `Build.ID`, `ro.build.id` | Hooked |
| `buildTime` | Yes | Flat + persona | `Build.TIME`, `ro.build.date.utc` | Hooked |
| `incremental` | Yes | Flat + persona | `Build.VERSION.INCREMENTAL`, `ro.build.version.incremental` | Hooked |
| `securityPatch` | Yes | Flat + persona | `Build.VERSION.SECURITY_PATCH`, `ro.build.version.security_patch` | Hooked |
| `supportedAbis` | Yes | Flat + persona | `Build.SUPPORTED_ABIS`, `SUPPORTED_32_BIT_ABIS`, `SUPPORTED_64_BIT_ABIS`, `ro.product.cpu.abilist*` | Hooked |
| `simCount` | Yes | Flat + persona | `TelephonyManager.getSimCount`, `getPhoneCount`, `getActiveModemCount`, `SubscriptionManager` count methods | Hooked |
| `hasNfc` | Yes | Flat + persona | `PackageManager.hasSystemFeature` NFC feature names | Hooked |
| `has5G` | Yes | Flat + persona | No stable public `hasSystemFeature` value exists for 5G specifically | Not spoofed |

## Diagnostics-Only Surfaces

| Surface | Reason |
| --- | --- |
| Android Advanced Protection Mode | Security posture, not identity. Captured in `security_state_snapshot.json`; not spoofed. |
| Biometric Identity Check authenticator constant | Security posture, not identity. Captured in `security_state_snapshot.json`; not spoofed. |

## Validation Target

The local verifier app `:verifier` reads Build, Settings, Telephony, SubscriptionManager, PackageManager feature checks, Wi-Fi, and Bluetooth surfaces and writes machine-readable evidence to:

```text
/data/data/com.astrixforge.devicemasker.verifier/files/verifier/latest.json
```

Use it as the controlled target package for LSPosed runtime proof alongside third-party apps such as Mantle and DevCheck.
