# System Patterns: Device Masker

## Module Layout

```text
:app
  Compose UI, ViewModels, repositories, JSON persistence, RemotePreferences writer,
  rootless app logs, diagnostics client.

:common
  Shared models, config contracts, preference keys, generators.

:xposed
  libxposed module entry, hookers, RemotePreferences reader, hook safety helpers,
  anti-detection, LSPosed logging.

:verifier
  Local validation target app. Reads Android framework identity surfaces and writes
  machine-readable evidence to files/verifier/latest.json.
```

## Architecture

```mermaid
flowchart TD
    User["User"]
    UI[":app Compose UI"]
    VM["ViewModels"]
    Repo["SpoofRepository"]
    Config["ConfigManager"]
    Json["filesDir/config.json"]
    Sync["ConfigSync"]
    XPrefs["XposedPrefs"]
    Remote["libxposed RemotePreferences"]
    Common[":common contracts"]
    Entry[":xposed XposedEntry"]
    Hookers["Spoof Hookers"]
    Anti["AntiDetectHooker"]
    Target["Target app process"]
    APIs["Android framework APIs"]
    LSLogs["LSPosed logs"]
    DiagUI["Diagnostics UI"]

    User --> UI --> VM --> Repo --> Config
    Config --> Json
    Config --> Sync --> XPrefs --> Remote
    Common --> UI
    Common --> Entry
    Entry --> Anti
    Entry --> Hookers
    Target --> APIs
    Hookers --> APIs
    Hookers --> Remote
    Hookers --> LSLogs
    Anti --> LSLogs
    Entry --> LSLogs
    LSLogs --> DiagUI
```

## Configuration Flow

```mermaid
sequenceDiagram
    participant User
    participant UI as Compose UI
    participant Repo as SpoofRepository
    participant Config as ConfigManager
    participant File as config.json
    participant Sync as ConfigSync
    participant Prefs as RemotePreferences
    participant Hook as Target-process hooker
    participant API as Android framework API

    User->>UI: Change app/group/type/value
    UI->>Repo: Update config intent
    Repo->>Config: Mutate JsonConfig
    Config->>File: Persist local JSON
    Config->>Sync: Sync latest config
    Sync->>Prefs: Write flattened per-app keys
    Hook->>Prefs: Read current app/type config
    Hook->>API: Call or intercept framework method
    alt Valid enabled stored spoof value
        Hook-->>API: Return spoofed value
    else Disabled, missing, blank, malformed, unsafe
        Hook-->>API: Return original value
    end
```

Dirty sync pattern:
- Full config sync remains the default for module/global/default-group operations and recovery.
- App/group mutations that only affect known packages should pass a dirty package set through
  `ConfigManager` to `ConfigSync.syncPackages()`.
- Dirty package sync still writes the current enabled-app allowlist and config version, then updates
  only requested package keys or disables removed packages.
- Keep explicit `commit()` for RemotePreferences sync writes where the app reports sync success.

Scoped app metadata pattern:
- Home Scoped Apps observes LSPosed scope packages and loads only those package labels/icons through
  `IAppScopeRepository.loadScopedApps()`.
- `android` and `system` are scope markers and must be filtered from app metadata lookup.
- Full installed-app scans are reserved for the Apps tab and explicit refresh paths, not Home startup.

## Source Of Truth Rules

- `JsonConfig.appConfigs` is the canonical app assignment and enablement table.
- `SpoofGroup.assignedApps` is legacy/display compatibility only.
- `SharedPrefsKeys` in `:common` is the only source for RemotePreferences key names.
- Runtime sync must resolve only explicit enabled `AppConfig.groupId` assignments. Default-group fallback is not a hook-scope rule.
- Generators live in `:common`.
- Hookers read stored values; hookers do not generate new identities at runtime.
- `ConfigSync` must clear stale package keys on full sync.
- `ConfigSync` publishes flat legacy keys plus per-package `DevicePersona` blob/version.
- `ConfigSync` also publishes per-package hook-family policy keys and default-off proc-maps byte/NIO policy keys.
- `ConfigSync` publishes the current enabled-app allowlist; `XposedEntry` must require allowlist membership plus per-package enablement before hook registration.
- Config delivery is RemotePreferences-first.
- Do not add custom AIDL/Binder config or hook-evidence paths.

## Hook Loading Rules

`XposedEntry`:
- Runs system-server setup from `onSystemServerStarting`.
- Runs target app hooks from `onPackageReady`.
- Skips `android` in app hook path because system server has its own lifecycle.
- Skips own app and critical system packages.
- Hooks only the first package load per classloader.
- Requires global module enabled and per-app enabled preferences before registering app hooks.
- Requires current enabled-app allowlist membership before trusting any per-app enabled preference.
- Logs `All hooks registered` to LSPosed when target hook registration completes.
- Builds one `HookConfigSnapshot` per selected hook package before value hook registration.
- Passes that snapshot to value hookers so hot callbacks do not repeatedly read RemotePreferences.
- Emits one final structured hook registration event instead of per-hook DEBUG success spam; disabled
  and failed hook registrations still produce diagnostics.

## Hook Safety Rules

Every hook should:
- Resolve classes and methods defensively.
- Use libxposed API 101 through `intercept(stableHooker { ... })` or explicit named
  `XposedInterface.Hooker` classes. Direct `.intercept { ... }` Kotlin SAM callbacks are forbidden
  in runtime hookers because release R8 caused `AbstractMethodError` in LSPosed target processes.
- Use one `safeHook` block per method or method family.
- Call `xi.deoptimize(m)` for methods that are hooked.
- Call `chain.proceed()` when original values are needed for fallback.
- Return original values for disabled, missing, blank, malformed, unsafe, or unsupported config.
- Skip abstract methods and other unhookable framework declarations.
- Avoid static initializers that can throw inside target processes.
- Prefer snapshot reads for app/type spoof values in hook callbacks. Hook callbacks should use the
  package-local `HookConfigSnapshot` for enabled-value checks and persona fallback.

Forbidden in target-process hook callbacks:
- Random fallback identifier generation.
- Hardcoded fake defaults for malformed config.
- Direct Kotlin SAM callback registration with `xi.hook(m).intercept { ... }`.
- Direct reads of app-private JSON config.
- Timber usage in `:xposed`.
- Hardcoded RemotePreferences key strings.
- In-place mutation of framework-returned lists.
- Custom `ServiceManager` lookup for Device Masker diagnostics.

## Anti-Detection Pattern

Current safer anti-detection surfaces:
- Stack trace filtering.
- Path-aware Java `/proc/self/maps` and `/proc/*/smaps` filtering through `ProcMapsHooker`.
- Package visibility hiding through PackageManager hooks.
- Package list filtering with copied lists.

Proc maps rules:
- `AntiDetectHooker` must not own broad global `BufferedReader.readLine()` maps filtering.
- `ProcMapsHooker` tracks only maps/smaps readers and streams created from sensitive proc paths.
- Hidden maps lines are skipped, not replaced with blank strings.
- Java byte-stream and Java NIO maps redaction stay opt-in per package until target evidence proves they are safe.
- Native maps reads are not claimed covered unless a separate native hook track is implemented and verified.

## Android 16 Compatibility Pattern

- Android 16 evidence is tracked separately from Android 13 emulator smoke.
- Hook-family RemotePreferences keys are the crash-isolation switchboard for target-specific triage.
- Android 16 emulator evidence proves only that emulator/build combination. Physical-device claims require physical-device LSPosed/logcat and target-app value evidence.
- Release/R8 hook evidence must come from an installed minified APK and must explicitly check for `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, and fatal target-process crashes.
- 16 KB page-size support is verified with `scripts/verify-16kb-page-support.ps1` against debug, release, and `ciRelease` APKs before Android 16 compatibility claims.

Global class lookup hiding is currently disabled by default:
- `Class.forName` and `ClassLoader.loadClass` hooks are too invasive for target startup.
- They caused or contributed to startup instability in `com.mantle.verify`.
- The helper still has safe-prefix pass-through rules for opt-in use.
- Reintroduction now requires both per-app risky hooks and per-app class lookup hiding to be enabled.

Intentional app-visible throws for package/class hiding must use `ExceptionMode.PASSTHROUGH`.

## Diagnostics Pattern

```mermaid
sequenceDiagram
    participant Hook as Hooker
    participant LS as LSPosed log
    participant App as Device Masker app
    participant Store as JSONL store
    participant Root as Root collector
    participant Export as Support bundle

    Hook->>LS: Log hook registration and spoof events
    App->>Store: Write structured app events
    Store->>Export: Include app-owned JSONL events
    Root->>Export: Include opt-in root/logcat artifacts
```

Diagnostics facts:
- App logs are rootless structured `DiagnosticEvent` JSONL stored in app sandbox.
- Support export has one user-facing `Export Logs` path backed by the maximum root/logcat bundle.
- Exports are redacted by default; raw identifiers must not be logged by default.
- Root/logcat collection uses bounded fixed command templates during boot/startup capture and export-time fresh snapshot, with sidecar manifests for every artifact.
- Export copies known LSPosed persisted log files from `/data/adb/lspd/` when root is granted.
- LSPosed logs are the authoritative source for target-process hook events.
- There is no custom Device Masker Binder service in system_server.
- Diagnostics UI does not read custom service status; target hook proof comes from LSPosed/logcat.
- Support bundle JSONL entries are written line-by-line to the zip; do not join large log lists into
  one string before writing.

Logs Monitor pattern:
- User-started only; no always-on logcat tailing.
- Root required for full logcat capture.
- Stores a bounded in-memory row tail and JSONL raw monitor file.
- Export remains the durable evidence path.
- Hook success still requires LSPosed/logcat hook lines plus target-app value evidence.

## Verifier Matrix Pattern

Runtime value validation uses three artifacts:
- A live Device Masker config snapshot for expected values.
- `:verifier` `files/verifier/latest.json` for actual target-app values.
- LSPosed/logcat for module load, hook registration, spoof events, and runtime error signatures.

Android platform restrictions and unsupported verifier probe shapes are not counted as hook failures unless a supported configured path also fails. WebView UA has two separate surfaces: static `WebSettings.getDefaultUserAgent(Context)` and instance `WebSettings.getUserAgentString()`. Static success alone is not full WebView UA coverage. Instance WebView UA is handled through safe concrete settings discovery from `WebView.getSettings()`, not broad classloader hooks.

## Current Hook Areas

- `AntiDetectHooker`
- `DeviceHooker`
- `NetworkHooker`
- `AdvertisingHooker`
- `SystemHooker`
- `SystemFeatureHooker`
- `LocationHooker`
- `SensorHooker`
- `WebViewHooker`
- `SubscriptionHooker`
- `PackageManagerHooker`
- `ProcMapsHooker`

## Value Correlation

Values must remain coherent across related APIs:
- SIM/carrier: IMSI, ICCID, phone number, carrier name, MCC, MNC, SIM country, network country.
- Device profile: manufacturer, brand, model, device, product, board, hardware, fingerprint, serial.
- Location profile: latitude, longitude, timezone, locale.
- Network profile: Wi-Fi MAC, SSID, BSSID, operator, Bluetooth address.

## WebView Pattern

WebView UA spoofing is defensive:
- Do not use static regex initializers.
- Parse UA strings with safe string operations.
- Replace only recognizable Android model segments.
- Pass through unknown formats.
- Skip abstract `WebSettings` methods.
- Use `WebView.getSettings()` to discover concrete WebView provider settings classes for instance UA hooks; do not use broad classloader hooks.
- When changing `setUserAgentString(String)` arguments, copy `chain.args` and call
  `chain.proceed(Object[])`; libxposed returns an immutable args list.

## libxposed Error Handling Pattern

- `HookFailedError` extends `XposedFrameworkError`, which extends `Error`.
- Hook registration and deoptimization wrappers must rethrow `XposedFrameworkError` before any
  generic `Throwable` catch.
- Normal reflection/OEM API variation failures can still be logged and skipped so one missing method
  does not block unrelated hooks.

## Process Package Selection Pattern

- `onModuleLoaded` captures the process name for later package selection.
- `onPackageReady` considers the loaded package and process base package, then picks the first
  package present in the current enabled-app allowlist and enabled in RemotePreferences.
- Hooks still register once per classloader to avoid duplicated hook chains.
- This improves secondary package handling but does not make one classloader support multiple
  simultaneous per-package identities.

## RemotePreferences Write Pattern

- Config delivery remains RemotePreferences-first.
- App-side config sync uses explicit `commit()` where sync success matters.
- Async `apply()` should not be used for config writes that the UI or diagnostics report as synced.

## Build Pattern

- Release minification and resource shrinking are enabled.
- Release R8 safety depends on the `StableHooker` adapter in `:xposed`; keep rules must preserve
  `io.github.libxposed.api.XposedInterface$Hooker`, `Chain`, `HookBuilder`, `HookHandle`, and
  `com.astrixforge.devicemasker.xposed.hooker.callback.**`.
- `ciRelease` build type validates ProGuard rules without affecting debug builds.
- Lint is fail-fast.
- App/common/xposed/verifier lint reports should stay at `No issues found`. Suppress only narrow false-positive or intentionally deferred warnings with a reason tied to runtime behavior.
- Spotless covers Kotlin and Gradle Kotlin files, excluding docs and generated/build folders.
- Detekt runs for `:app`, `:common`, and `:xposed` using `config/detekt.yml`, module overrides, and per-module baselines.
- Detekt runs with `allRules=true`; module baselines are currently empty and should stay empty.
- `:verifier` is included in Spotless, Detekt, lint, and local build gates.
- Compose compiler reports/metrics are opt-in through `enableComposeCompilerReports` and `enableComposeCompilerMetrics`.
- Memory Bank must be updated after architecture or runtime behavior changes.

## App Contract Pattern

- `IConfigManager` and `ISpoofRepository` are compatibility facades over smaller workflow-focused interfaces.
- Prefer narrow interfaces for new call sites when practical.
- `ConfigManager` and `SpoofRepository` carry targeted `TooManyFunctions` suppressions only because they are compatibility facades; do not treat those suppressions as permission to grow random APIs.

## Testing Pattern

- `MainDispatcherRule` swaps `Dispatchers.Main` for a `TestDispatcher` in ViewModel tests.
- Hand-written fakes preferred over mocking libraries per module rules.
- Turbine used for Flow emission testing.
- MockK permitted only for Navigation 3 framework types.
- Fake repositories carry real state and test hooks.
- `advanceUntilIdle()` required after async operations in `runTest`.

## ViewModel State Pattern

- `SavedStateHandle` injected for process-death survival of critical UI state.
- Production screen factories create lifecycle `SavedStateHandle` instances through Navigation/ViewModel factory APIs; default production constructors must not silently allocate detached handles.
- Unit tests pass explicit `SavedStateHandle()` values.
- State classes annotated `@Immutable` with `kotlinx.collections.immutable.ImmutableList`.
- `Flow.combine` used to merge multiple repository flows into single UI state.
- Redundant `suspend` modifiers removed from non-suspending methods.
- Expensive UI row derivation for large lists should happen in the ViewModel. The Group Spoofing
  Apps tab uses `AppRowModel` to precompute normalized labels/package names, current assignment,
  app enabled state, other-group label, and sorted order before composition.

## App Icon Pattern

- App icon decoding is centralized through bounded `AppIconCache`.
- Compose app rows use `CachedAppIcon` and stable package keys instead of decoding drawables in each
  row composition.
- Icon decode work belongs on `Dispatchers.IO`; fallbacks render when package icons are missing.

## Navigation Pattern

- Navigation uses Navigation 3, not Navigation Compose 2.x.
- `NavDestination` is the typed `NavKey` sealed interface for Home, Groups, GroupSpoofing, Settings, and Diagnostics.
- `DeviceMaskerNavigationState` owns separate top-level Home, Groups, and Settings back stacks.
- The selected top-level destination is saved with `rememberSaveable`; individual stacks are saved with `rememberNavBackStack`.
- `DeviceMaskerNavigator` is the narrow imperative API screens use for navigation intents.
- `NavDisplay` renders the active stack through typed `entryProvider` entries.
- `rememberSaveableStateHolderNavEntryDecorator` and `rememberViewModelStoreNavEntryDecorator` preserve entry state and ViewModel stores.
- Navigation motion is configured through `NavDisplay` transition specs and uses M3E motion tokens with reduced-motion fallback.
- Window size class adaptation: `NavigationRail` for medium/expanded, bottom navigation for compact, both hidden on focus/detail screens.
- Groups and GroupSpoofing use adaptive Navigation 3 list-detail scene metadata.
- Compact width intentionally uses Navigation 3's default single-pane scene; the Material list-detail scene strategy is only passed on medium/expanded widths after compact runtime validation showed excessive scene startup cost.
- Deep links use explicit `devicemasker://open/...` URI parsing and synthetic stacks. `groups/{groupId}` opens Groups -> GroupSpoofing, and `diagnostics` opens Settings -> Diagnostics so Back returns to the parent top-level destination.
