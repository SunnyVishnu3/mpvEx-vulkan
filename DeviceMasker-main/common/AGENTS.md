# :common Module Guide

Shared contracts, data models, identity generators, preference key builder, and diagnostics schema. No Android UI or Xposed runtime dependencies. Mirrored Android framework constants are allowed only as named constants.

## Module Structure

```
common/src/main/
├── kotlin/.../common/
│   ├── models/             Shared data models and value objects
│   ├── generators/         Config-time identity generators
│   ├── diagnostics/        Shared diagnostics schema and redaction
│   └── (root)              JsonConfig, AppConfig, SpoofGroup, SpoofType, DeviceIdentifier,
│                             DeviceProfilePreset, DevicePersona, PersonaGenerator and helpers,
│                             SharedPrefsKeys, Constants, Utils, NetworkTypeMapper, SecureRandomUtils
```

## Source Of Truth Rules

- `JsonConfig.appConfigs` is canonical for per-app scope. `SpoofGroup.assignedApps` is legacy/display-only.
- `SharedPrefsKeys` is the ONLY place to build RemotePreferences key strings.
- Generators live here and run at config time, never at runtime in `:xposed`; target-process hooks only consume stored values.
- All generators use `SecureRandom` for cryptographic-quality randomness.
- Detekt baselines are currently empty; keep common logic small enough to avoid new baseline debt.
- Runtime group resolution must require an explicit enabled `AppConfig.groupId`. Keep any default-group fallback out of hook eligibility and config sync paths.

## Data Model Hierarchy

```
JsonConfig (root, @Serializable)
├── version: Int (CONFIG_VERSION = 1)
├── isModuleEnabled: Boolean
├── groups: Map<String, SpoofGroup>           // JSON key: "profiles"
│   └── SpoofGroup
│       ├── id, name, description, isEnabled, isDefault
│       ├── identifiers: Map<SpoofType, DeviceIdentifier>
│       ├── assignedApps: Set<String>         // LEGACY/display only
│       ├── personaSeed, personaGeneratedAt
│       └── selectedCarrierMccMnc
└── appConfigs: Map<String, AppConfig>        // CANONICAL per-app scope
    └── AppConfig
        ├── packageName
        ├── groupId?                          // JSON key: "profileId"
        └── isEnabled
```

Migration: `withDerivedAppConfigsFromAssignedApps()` moves legacy group assignments into `appConfigs`.

## SpoofType Enum

| Category | Types | CorrelationGroup |
|----------|-------|-----------------|
| DEVICE | IMEI, IMSI, SERIAL, ICCID, PHONE_NUMBER, SIM_COUNTRY_ISO, SIM_OPERATOR_NAME, NETWORK_COUNTRY_ISO | DEVICE_HARDWARE / SIM_CARD |
| NETWORK | NETWORK_OPERATOR, WIFI_MAC, BLUETOOTH_MAC, WIFI_SSID, WIFI_BSSID, CARRIER_NAME, CARRIER_MCC_MNC | SIM_CARD / DEVICE_HARDWARE |
| ADVERTISING | ANDROID_ID, GSF_ID, ADVERTISING_ID, MEDIA_DRM_ID | NONE |
| SYSTEM | DEVICE_PROFILE | NONE |
| LOCATION | LOCATION_LATITUDE, LOCATION_LONGITUDE, TIMEZONE, LOCALE | LOCATION |

## Correlation Groups

Values that MUST be generated together to avoid detection:
- **SIM_CARD**: IMSI, ICCID, carrier name, MCC/MNC, phone number, country ISOs — all from same `Carrier`
- **DEVICE_HARDWARE**: IMEI, serial, WiFi MAC — all from same `DeviceProfilePreset`
- **LOCATION**: Timezone, locale, GPS coordinates — all from same `Country`
- **NONE**: Independent values (Android ID, advertising ID, Bluetooth MAC, etc.)

## Generators

| Generator | Output | Algorithm |
|-----------|--------|-----------|
| `IMEIGenerator` | 15-digit IMEI | TAC + serial + Luhn check using realistic TAC data. |
| `IMSIGenerator` | 15-digit IMSI | MCC/MNC + MSIN using carrier-aware data. |
| `ICCIDGenerator` | 19-digit ICCID | MII(89) + Country + Issuer + Account + Luhn. Carrier-specific. |
| `MACGenerator` | `XX:XX:XX:XX:XX:XX` | Real OUIs for Samsung/Apple/Google/Xiaomi/etc. Unicast + locally-administered bits. |
| `SerialGenerator` | Manufacturer-specific | Samsung: `R`+2+year+8. Pixel: 16 hex. Xiaomi: 12-16 alphanumeric. |
| `UUIDGenerator` | Android ID, GAID, GSF ID, Media DRM ID, Instance ID | `SecureRandom` hex/UUID |
| `PhoneNumberGenerator` | `+{code}{number}` | Country/carrier-aware phone number formats |
| `SIMGenerator` | Complete `SIMConfig` | Orchestrates IMSI+ICCID+Phone+Carrier |
| `DeviceHardwareGenerator` | Complete `DeviceHardwareConfig` | Orchestrates IMEI+Serial+WiFiMAC+BTMAC |
| `FingerprintGenerator` | Build fingerprint + Build.* properties | Device profile presets |

## PersonaGenerator — Deterministic Coherent Identity

`PersonaGenerator.generate(group, packageName)` builds a complete `DevicePersona` using SHA-256 seeded derivation from `rootSeed`. Priority: explicit override > stored value > deterministic derivation.

Persona generation is intentionally split across focused helpers:
- `PersonaGenerationConstants.kt` — seed/version/constants
- `PersonaDeterministic.kt` — deterministic seeded primitives
- `PersonaIdentityValues.kt` — identity/tracking/browser values
- `PersonaNetworkValues.kt` — SIM/network/location values
- `SpoofGroupExtensions.kt` and `SpoofGroupDisplayExtensions.kt` — group operations and display helpers

`DevicePersona` contains: `HardwarePersona`, `List<SubscriptionPersona>`, `LocationPersona`, `NetworkEnvironmentPersona`, `TrackingPersona`, `BrowserPersona`.

`materializeGroup()` writes persona values back into a `SpoofGroup`'s identifier map.

## Sub-Models

| Model | Key Fields | Validation |
|-------|-----------|------------|
| `Carrier` | name, mccMnc, countryCode, countryIso, iccidIssuerCode | Real carrier metadata |
| `SIMConfig` | carrier, imsi, iccid, phoneNumber, simCountryIso, networkCountryIso | IMSI must start with carrier MCC/MNC. Constructor throws on mismatch. |
| `DeviceHardwareConfig` | deviceProfile, imei, serial, wifiMAC, bluetoothMAC | IMEI must be 15 digits |
| `LocationConfig` | country, timezone, locale, latitude, longitude | Country/city GPS bounds |
| `Country` | iso, name, emoji, phoneCode | Supported country metadata |
| `DeviceProfilePreset` | id, name, brand, model, device, fingerprint, tacPrefixes, simCount | Device profile presets |

## SharedPrefsKeys — Key Patterns

| Pattern | Example |
|---------|---------|
| `module_enabled` | global toggle |
| `enabled_apps` | current canonical package allowlist |
| `app_enabled_{sanitizedPkg}` | `app_enabled_com_example_app` |
| `spoof_enabled_{sanitizedPkg}_{TYPE}` | `spoof_enabled_com_example_app_IMEI` |
| `spoof_{sanitizedPkg}_{TYPE}` | `spoof_com_example_app_ANDROID_ID` |
| `hook_family_enabled_{sanitizedPkg}_{family}` | `hook_family_enabled_com_example_app_anti_detect` |
| `java_proc_maps_byte_redaction_enabled_{sanitizedPkg}` | opt-in Java byte maps redaction |
| `java_proc_maps_nio_redaction_enabled_{sanitizedPkg}` | opt-in Java NIO maps redaction |
| `persona_blob_{sanitizedPkg}` | persona JSON blob |
| `persona_version_{sanitizedPkg}` | persona version counter |

Package names: `.` replaced with `_`. Validation regex in `isValidKey()`.

## Diagnostics Schema

`DiagnosticEvent`: shared structured event schema with typed sources, severities, and event IDs. Event ID format: `evt_{wallMillis}_{sequence6digits}`.

`DiagnosticRedactor`: Two modes (REDACTED/UNREDACTED). Redacts IMEI, IMSI, ICCID, MAC, Android ID, phone numbers, GPS coordinates, package names (SHA-256 prefix).

## Build

- `android.library`, `kotlin.serialization`
- `aidl = false`; no custom Binder contract
- No Compose, no UI dependencies
- Dependencies: `kotlinx-serialization-json`, `kotlinx-coroutines-core`

## Testing

Coverage should include: JsonConfig parsing/migration, PersonaGenerator stability, identity generators, DiagnosticEvent serialization, DiagnosticRedactor, and DeviceHardwareConfig validation.
