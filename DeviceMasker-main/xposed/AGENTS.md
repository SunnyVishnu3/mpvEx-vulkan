# :xposed Module Guide

libxposed module entry, hook layer, RemotePreferences reader, anti-detection, and hook diagnostics. This code runs INSIDE target app processes and system_server. Every line must be safe for hostile process contexts.

## Module Structure

```
xposed/src/main/
├── kotlin/.../xposed/
│   ├── hooker/             Target-process hook implementations and shared hook helpers
│   ├── diagnostics/        Hook-side diagnostics and health tracking
│   └── (root)              Module entry, preference reader, logging, deoptimization support
│
└── resources/META-INF/xposed/
    ├── java_init.list      XposedEntry class name
    ├── module.prop         minApiVersion=101, targetApiVersion=101
    └── scope.list          android, system
```

## Critical Constraints

- **No Timber** — use `DualLog` (logcat + diagnostics sink)
- **No random identifier generation** — read values from RemotePreferences only
- **No reading app-private JSON** from target processes
- **No hardcoded key strings** — use `PrefsKeys` (delegates to `SharedPrefsKeys` in `:common`)
- **No Compose imports** — pure Kotlin + Android framework only
- **No StrictMode installation** in target app or system_server hook paths. StrictMode is app-process debug tooling only.
- **No direct Kotlin SAM `.intercept { ... }` callbacks** — use `stableHooker { ... }` or explicit named `XposedInterface.Hooker`
- `libxposed:api` is `compileOnly` — LSPosed provides runtime
- Rethrow `XposedFrameworkError` before generic `Throwable` in all catch blocks
- Target app hook selection must require both current `enabled_apps` app-config package-set membership and the per-package enabled key. A stale `app_enabled_*` key alone must never register hooks.

## Xposed Safety Rules

- Runtime hooks must use `intercept(stableHooker { ... })` or explicit named `XposedInterface.Hooker` implementations.
- Never use direct Kotlin SAM callbacks like `.intercept { ... }` in runtime hookers.
- Separate method discovery, hook registration, and callback behavior.
- Use data tables for repeated framework getter hooks instead of copy-pasted hook blocks.
- In hook code, name argument indexes, API constants, identifier lengths, byte sizes, and limits before use.
- Rethrow `XposedFrameworkError` before generic catches.
- Return original values for disabled, missing, blank, malformed, unsafe, or unsupported config.
- Do not generate random fallback identifiers inside target processes.
- Do not use Timber, Compose, app-private JSON reads, or hardcoded RemotePreferences keys in `:xposed`.
- Preserve hook isolation: one failed hook must not block unrelated hooks.

## Entry Point — XposedEntry

Extends `XposedModule` (libxposed API 101). No-arg constructor, framework-instantiated.

### Lifecycle

1. **`onModuleLoaded`** — sets singleton `instance`, logs process name
2. **`onSystemServerStarting`** — logs system-server module load only. Every line try-caught (crash = bootloop).
3. **`onPackageReady`** — main hook path:
   - Skips `android`, own package, SystemUI, Phone, GMS
   - Gets `RemotePreferences` via `getRemotePreferences(PREFS_GROUP)`
   - Checks global kill-switch + current app-config package set + per-app enable
   - Builds `HookConfigSnapshot` once for the selected package
   - Deduplicates per ClassLoader (`ConcurrentHashMap` of identity hashes)
   - Registers target hookers in the project-defined order and passes the snapshot to value hookers. Keep anti-detection first unless a fresh runtime validation proves a different order safe.

### Package Selection

`selectHookPackage()` derives base package from `processName` (before `:`), checks both loaded and base package against the current app-config package set plus RemotePreferences enable keys, and returns the first enabled candidate.

## Hook Registration Pattern

Every hook follows this pattern:

```kotlin
safeHook("methodName") {
    class.methodOrNull(params)?.let { m ->
        xi.hook(m).intercept(stableHooker { chain ->
            val original = chain.proceed()           // get real value first
            val spoofed = snapshot.valueOrNull(...)  // from per-package snapshot
            if (spoofed != null) { reportSpoof(); return@stableHooker spoofed }
            original                                 // fallback to real value
        })
        xi.deoptimize(m)    // prevent ART JIT inlining
    }
}
```

Direct Kotlin SAM intercept callbacks are forbidden in runtime hookers unless release R8/runtime validation explicitly proves them safe.

## BaseSpoofHooker — Shared Helpers

| Method | Purpose |
|--------|---------|
| `safeHook(name, block)` | Try-catch per method. One failure never blocks others. Rethrows `XposedFrameworkError`. |
| `ClassLoader.loadClassOrNull(name)` | Returns null on `ClassNotFoundException` |
| `Class.methodOrNull(name, params)` | Returns null on `NoSuchMethodException`, auto-sets accessible |
| `getSpoofValue(snapshot, type, fallback)` | Read snapshot value with fallback |
| `getConfiguredSpoofValue(snapshot, type)` | Returns null if not configured |
| `reportSpoofEvent(pkg, type)` | Records hook metrics and LSPosed/logcat evidence |

## PrefsHelper — Config Reader

`PrefsHelper` is the low-level RemotePreferences compatibility reader. `HookConfigSnapshot.fromPrefs(prefs, pkg)` builds the per-package enabled value/persona snapshot. Hot value callbacks should use `HookConfigSnapshot`, not repeat preference reads.

| Method | Returns |
|--------|---------|
| `getStoredSpoofValue(prefs, pkg, type)` | `null` if disabled/blank/absent |
| `getSpoofValue(prefs, pkg, type, fallback)` | value or `fallback()` |
| `isSpoofTypeEnabled(prefs, pkg, type)` | `false` default (opt-in) |

## Hooker Summary

| Hooker | APIs Hooked | SpoofType |
|--------|-------------|-----------|
| **DeviceHooker** | TelephonyManager getter tables, Build.getSerial(), Settings.Secure.getString(android_id), SystemProperties serial reads | IMEI, IMSI, ICCID, SIM_COUNTRY_ISO, NETWORK_COUNTRY_ISO, SIM_OPERATOR_NAME, CARRIER_MCC_MNC, NETWORK_OPERATOR, PHONE_NUMBER, SERIAL, ANDROID_ID |
| **SubscriptionHooker** | SubscriptionInfo (getIccId, getCountryIso, getCarrierName, getDisplayName, getMcc, getMnc, getMccString, getMncString, getNumber), SubscriptionManager.getActiveSubscriptionInfoList (pass-through) | ICCID, SIM_COUNTRY_ISO, CARRIER_NAME, CARRIER_MCC_MNC, PHONE_NUMBER |
| **NetworkHooker** | WifiInfo (getMacAddress, getSSID, getBSSID), NetworkInterface.getHardwareAddress (wlan/wifi/p2p only), BluetoothAdapter.getAddress, TelephonyManager (getNetworkOperatorName, getNetworkOperator) | WIFI_MAC, WIFI_SSID, WIFI_BSSID, BLUETOOTH_MAC, CARRIER_NAME, CARRIER_MCC_MNC |
| **SystemHooker** | Build static fields (direct mutation: FINGERPRINT, MODEL, MANUFACTURER, BRAND, DEVICE, PRODUCT, BOARD, HARDWARE, TAGS, TYPE), SystemProperties.get (16 ro.* keys) | DEVICE_PROFILE |
| **LocationHooker** | Location (getLatitude, getLongitude), LocationManager.getLastKnownLocation, TimeZone.getDefault, Locale.getDefault | LOCATION_LATITUDE, LOCATION_LONGITUDE, TIMEZONE, LOCALE |
| **SensorHooker** | SensorManager.getSensorList (TYPE_ALL only, filters when >10), Sensor (getVendor, getVersion, getName) | DEVICE_PROFILE |
| **AdvertisingHooker** | AdvertisingIdClient.Info.getId, Gservices (getString, getLong for android_id), MediaDrm.getPropertyByteArray (deviceUniqueId) | ADVERTISING_ID, GSF_ID, MEDIA_DRM_ID |
| **WebViewHooker** | WebSettings.getDefaultUserAgent(Context), WebView.getSettings() concrete settings discovery, WebSettings set/user-agent surfaces where safely hookable | DEVICE_PROFILE |
| **PackageManagerHooker** | ApplicationPackageManager (getPackageInfo, getApplicationInfo, getInstalledPackages, getInstalledApplications, queryIntentActivities) — throws NameNotFoundException for self | Self-hiding |
| **ProcMapsHooker** | Path-aware Java `/proc/self/maps`, `/proc/<pid>/maps`, `/proc/self/smaps`, `/proc/<pid>/smaps` reader filtering; byte/NIO redaction only when explicit per-app policy enables it | Anti-detection |

## AntiDetectHooker — Detection Vectors

| Vector | Status | Method |
|--------|--------|--------|
| Stack trace filtering | **ACTIVE** | Hooks `Thread.getStackTrace()`, `Throwable.getStackTrace()`. Filters legacy and modern hook-framework patterns, including XposedBridge, LSPosed, EdXposed, and libxposed. ThreadLocal reentrant guard. |
| `/proc/self/maps` | **DELEGATED** | `ProcMapsHooker` handles path-aware maps/smaps filtering. Hidden lines are skipped, not replaced with blank strings. |
| Package hiding | **ACTIVE** | Hides 6 packages (LSPosed Manager 3 variants, Magisk, VirtualXposed, EdXposed) via PM hooks. `ExceptionMode.PASSTHROUGH` for throws. |
| `Class.forName` | **DISABLED** | Defined but not called. Caused startup instability. |
| `ClassLoader.loadClass` | **DISABLED** | Defined but not called. Caused startup instability. |

Safe class prefixes (never hidden): `android.`, `androidx.`, `com.android.`, `com.google.`, `dalvik.`, `java.`, `javax.`, `kotlin.`, `kotlinx.`, `org.jetbrains.`

## DeoptimizeManager

Prevents ART JIT/AOT from inlining hooked methods. Short methods (<20 bytecodes) get inlined, bypassing hooks.

- `deoptimizeWithCallers(xi, target, callers?)` — primary + optional callers
- `deoptimizeAll(xi, methods)` — batch with per-method guarding
- **Always call AFTER `xi.hook()`** — deoptimizing before hooking is a no-op

## Diagnostics

- `DualLog` — logs to module logger/LSPosed-logcat + `XposedDiagnosticEventSink`
- `HookMetrics` — success/failure counts via `HookHealthRegistry`
- `HookHealthRegistry` — thread-safe AtomicLong counters, per-method health, spoof event throttling (first 5, then 10/100/1000 milestones)
- `XposedDiagnosticEventSink` — builds `DiagnosticEvent`, forwards to module logger/LSPosed-logcat
- Hook registration keeps disabled/failure events and one final `All hooks registered` event. Do not re-add per-hook debug success spam without measured need.

## Metadata

- `META-INF/xposed/java_init.list`: `com.astrixforge.devicemasker.xposed.XposedEntry`
- `META-INF/xposed/module.prop`: `minApiVersion=101`, `targetApiVersion=101`, `staticScope=false`
- `META-INF/xposed/scope.list`: `android`, `system`
- `src/main/resources/` must be in `resources.srcDirs` (Java resources, not Android assets)

## Build

- `android.library`, `kotlin.serialization`, JVM 17
- `buildConfig = false`, `aidl = false`
- `compileOnly(libs.libxposed.api)` — LSPosed provides runtime
- `implementation(libs.kotlinx.coroutines.core)`, `implementation(libs.kotlinx.serialization.json)`
- Do not add HiddenApiBypass, Frida, DexKit, ByteHook, ShadowHook, xHook, or Dobby as production dependencies without a separate evidence-backed plan.
- `testImplementation(libs.libxposed.api)` — for unit tests that reference API classes

## ProGuard (consumer-rules.pro)

Keep rules must preserve the libxposed entry point, hooker classes, preference helpers, hook-side logging/metrics, libxposed service bridge types, and the R8-safe callback package `xposed.hooker.callback.**`.

## Testing

Coverage should include:
- `PrefsHelperTest` — null for disabled/blank, returns configured value
- `HookSafetyTest` — WebView UA parsing, AntiDetect safe-prefix pass-through, all hookers use safeHook, PM overload discovery, Location copy safety
- `HookHealthRegistryTest` — registration counters, spoof aggregation, rate-limiting
- `R8HookerAbiTest` — forbids direct runtime `.intercept { ... }` callbacks and checks R8 callback keep coverage

## Runtime Validation Caveats

- WebView UA has two separate evidence paths. Static `WebSettings.getDefaultUserAgent(Context)` and instance `WebSettings.getUserAgentString()` both need target-app evidence. Instance coverage depends on safe concrete settings discovery through `WebView.getSettings()`; do not replace it with broad classloader hooks.
- Do not add broad classloader hooks to fix WebView instance UA without a separate safety plan and Android 16 runtime evidence.
