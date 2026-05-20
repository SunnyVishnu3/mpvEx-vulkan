# Device Masker Architecture And Runtime Guide

Date: 2026-05-18

## Summary

Device Masker is an Android LSPosed/libxposed module that lets a user configure stable alternate identities for selected apps. The app owns configuration and value generation. The Xposed module reads the synchronized config from libxposed RemotePreferences inside target app processes and hooks selected Android framework APIs.

## Architecture Goals

- Keep target apps alive.
- Deliver spoof config through libxposed RemotePreferences.
- Generate stable identities app-side, not inside hooks.
- Keep shared contracts in `:common`.
- Keep target-process hook logic in `:xposed`.
- Keep UI, local JSON, and config sync in `:app`.
- Keep local validation in `:verifier`, separate from the production APK.
- Use LSPosed logs as the reliable proof of runtime hook behavior.
- Do not use a custom diagnostics Binder.
- Keep release R8 enabled; runtime hook callbacks must use the `StableHooker` path.

## High-Level Architecture

```mermaid
flowchart TD
    User["User"]
    App[":app"]
    UI["Compose UI"]
    VM["ViewModels"]
    Repo["SpoofRepository"]
    Config["ConfigManager"]
    Json["filesDir/config.json"]
    Sync["ConfigSync"]
    XPrefs["XposedPrefs"]
    Scope["LSPosed module scope"]
    Apps["Scoped app metadata"]
    Remote["libxposed RemotePreferences"]
    Common[":common"]
    Verifier[":verifier"]
    Entry[":xposed XposedEntry"]
    Hookers["Spoof hookers"]
    Anti["AntiDetectHooker"]
    Target["Target app process"]
    Framework["Android framework APIs"]
    LSLogs["LSPosed Manager logs"]
    AppLogs["Rootless app logs"]
    DiagUI["Diagnostics UI"]

    User --> UI
    UI --> VM --> Repo --> Config
    Config --> Json
    Config --> Sync --> XPrefs --> Remote
    XPrefs --> Scope --> VM
    Repo --> Apps --> VM
    App --> Common
    Verifier --> Target
    Entry --> Common
    Entry --> Anti
    Entry --> Hookers
    Target --> Framework
    Hookers --> Framework
    Hookers --> LSLogs
    Anti --> LSLogs
    App --> AppLogs
    LSLogs --> DiagUI
```

## Module Responsibilities

| Module | Responsibility | Must Not Do |
| --- | --- | --- |
| `:app` | UI, local config, config persistence, RemotePreferences writes, rootless logs, diagnostics UI | Run target-process hook logic |
| `:common` | Shared models, generators, `SharedPrefsKeys`, `DevicePersona`, config contracts | Depend on Compose or Xposed runtime |
| `:xposed` | libxposed entry, hooks, anti-detection, LSPosed logging | Generate fresh spoof identities or read app-private JSON |
| `:verifier` | Local target app that reads framework surfaces and writes `files/verifier/latest.json` | Ship as the production app |

## Configuration Model

```mermaid
flowchart LR
    JsonConfig["JsonConfig"]
    AppConfigs["appConfigs"]
    Groups["spoofGroups"]
    Values["Spoof values"]
    Persona["DevicePersona blob"]
    Keys["SharedPrefsKeys"]
    Remote["RemotePreferences"]
    Hook["Target hook"]

    JsonConfig --> AppConfigs
    JsonConfig --> Groups
    Groups --> Values
    Groups --> Persona
    AppConfigs --> Keys
    Values --> Keys
    Persona --> Keys
    Keys --> Remote --> Hook
```

Rules:
- `JsonConfig.appConfigs` is canonical.
- `SpoofGroup.assignedApps` is legacy/display compatibility only.
- Runtime sync uses explicit enabled `AppConfig.groupId` assignment. Default-group fallback is not valid hook eligibility for an unassigned package.
- `AppConfig.isEnabled` is standalone app-level spoof eligibility. Group assignment and unassignment preserve it unless the user explicitly toggles the app.
- LSPosed scope is an app-side observation source for UI and refresh state. It is not runtime hook eligibility by itself.
- `SharedPrefsKeys` builds all RemotePreferences keys.
- `ConfigSync` writes flattened per-app keys plus a coherent per-package `DevicePersona` blob/version.
- Full sync clears stale package keys.
- Dirty package sync writes the current app-config package set/config version and only rewrites requested package keys.
- `ConfigSync` publishes the current app-config package set; `:xposed` requires package-set membership plus the per-package enabled key before registering target hooks.
- Hookers read stored values only; persona fallback is allowed only after a spoof type is enabled.

## Config Flow

```mermaid
sequenceDiagram
    participant User
    participant UI as Compose UI
    participant Repo as SpoofRepository
    participant Config as ConfigManager
    participant File as config.json
    participant Sync as ConfigSync
    participant Prefs as RemotePreferences
    participant Hook as Target hooker
    participant API as Android API

    User->>UI: Enable target app or spoof type
    UI->>Repo: Update domain config
    Repo->>Config: Mutate JsonConfig
    Config->>File: Persist app-local JSON
    Config->>Sync: Request full sync or dirty package sync
    Sync->>Prefs: Write allowlist/version and full or dirty package keys
    Hook->>Prefs: Read enabled/value keys
    Hook->>API: Intercept framework call
    alt Safe enabled stored value
        Hook-->>API: Return spoofed value
    else Disabled, missing, blank, malformed, or unsafe
        Hook-->>API: Return original value
    end
```

## Home Scoped Apps Flow

The Home screen has a separate app-side flow for the "Scoped Apps" section. It shows apps currently selected in LSPosed scope and lets the user toggle app-level spoof eligibility without changing group assignment.

```mermaid
flowchart LR
    LSPosed["LSPosed service scope"] --> XPrefs["XposedPrefs.scopedPackages"]
    PackageManager["PackageManager scoped package metadata"] --> ScopeRepo["AppScopeRepository.loadScopedApps"]
    XPrefs --> HomeVM["HomeViewModel"]
    ScopeRepo --> HomeVM
    HomeVM --> Builder["buildHomeScopedApps"]
    Builder --> HomeUI["Home Scoped Apps section"]
    HomeUI --> Toggle["Per-app switch"]
    Toggle --> Repo["SpoofRepository.setAppEnabled"]
    Repo --> Config["ConfigManager.setAppEnabled"]
    Config --> AppConfig["JsonConfig.appConfigs[pkg].isEnabled"]
```

Rules:

- The list is derived from `XposedPrefs.scopedPackages` joined with scoped package metadata, not from spoof groups or `appConfigs`.
- Home startup must use `AppScopeRepository.loadScopedApps()` and must not trigger a full installed-app scan.
- `android` and `system` are filtered out of the Home list because they are framework scope entries, not user target apps.
- Packages missing from installed-app metadata are omitted instead of rendered as raw package names.
- Pull-to-refresh force-refreshes scoped package metadata and rereads LSPosed scope.
- Disabling an app here only sets `AppConfig.isEnabled = false`; it does not remove LSPosed scope or clear group assignment.
- Runtime hooks still require synced `enabled_apps` package-set membership, the per-package enabled key, an explicit enabled group assignment, and enabled spoof type values.

## Runtime Hook Flow

```mermaid
sequenceDiagram
    participant LS as LSPosed/libxposed
    participant Entry as XposedEntry
    participant Prefs as RemotePreferences
    participant Snapshot as HookConfigSnapshot
    participant Anti as AntiDetectHooker
    participant Hook as Spoof hookers
    participant Target as Target app
    participant Logs as LSPosed logs

    LS->>Entry: onModuleLoaded
    LS->>Entry: onPackageReady
    Entry->>Prefs: getRemotePreferences
    Entry->>Prefs: Check module enabled, app-config package-set membership, and app enabled
    alt Enabled target app
        Entry->>Prefs: Build per-package config snapshot
        Entry->>Anti: Register safer anti-detection hooks
        Entry->>Hook: Register spoof hooks with snapshot
        Hook->>Logs: All hooks registered
        Target->>Hook: Calls hooked API
        Hook->>Snapshot: Read enabled values/persona fallback
        Hook->>Logs: Spoof event
    else Disabled or unconfigured
        Entry-->>LS: Skip hooks
    end
```

## Diagnostics And Logs

```mermaid
flowchart TD
    AppTimber["App Timber logs"]
    AppStore["AppLogStore"]
    Export["LogManager export"]
    Hook["Target hookers"]
    LS["LSPosed logs"]
    Health["Parsed hook health"]
    UI["Diagnostics UI"]
    Root["Root/logcat support artifacts"]

    AppTimber --> AppStore --> Export
    Hook --> LS
    LS --> UI
    LS --> Root --> Export
    Root --> Health --> Export
```

Important facts:
- `HookConfigSnapshot` is built once in `XposedEntry.onPackageReady()` for the selected package. Value hookers read the snapshot in callbacks; anti-detect and proc-maps policy still read their app-level preference keys.
- Hook registration keeps one final `All hooks registered` event plus health counters. Per-hook debug start/success spam is not part of the common path.
- App logs are stored as app-owned structured JSONL.
- Hook-side logs remain LSPosed/logcat-owned; the app does not use a custom Binder path for hook evidence.
- Support export has one user-facing path: Export Logs.
- Export Logs builds the maximum local support bundle.
- Export copies app JSONL, real redacted snapshots, LSPosed log files or log directories copied from known root paths when available, and root/logcat artifacts with sidecar manifests.
- `xposed/xposed_events.jsonl` is generated only from parsed copied logcat/LSPosed lines. If no matching hook lines are captured, the bundle manifest and root sidecars explain the capture state.
- `diagnostics/hook_health.json` is derived from parsed Xposed export events. It summarizes module load, target selection, hook registration, hook failures/skips, and spoof events; it is not target-value proof.
- Logs Monitor is a user-started live root logcat capture screen for debugging target launches. It is not proof of hook success unless LSPosed/logcat lines and target-app values are present.
- Support bundle JSONL entries are streamed line by line into the ZIP; do not join large log files into memory.
- Redacted support JSONL must stay valid JSONL so support tooling can parse it after export.
- If root is unavailable, export still creates a ZIP with app logs, snapshots, and a root-unavailable manifest.
- There is no custom Device Masker Binder service in system_server.
- App export should stay structured, bounded, redacted, and useful for support.

## Android 16 Compatibility

Android 16 support is validated separately from Android 13 emulator smoke. Hook families can be isolated per target through RemotePreferences keys when a crash needs triage.

Java `/proc/self/maps` hardening is path-aware and owned by `ProcMapsHooker`. It covers tracked Java reader paths and keeps byte/NIO redaction behind explicit per-app policy keys. Native scanner coverage is not claimed unless a later native probe and target-app evidence prove it.

For the current validation matrix, emulator evidence, and evidence boundaries, see `docs/public/validation/DEVICE_MASKER_VALIDATION_STATUS.md`.

## Verifier Evidence

`:verifier` is the local target app for value-by-value checks. It stays simple and machine-readable: launch the target, read Android framework surfaces, and write `files/verifier/latest.json`.

Current validation rules:
- Treat LSPosed/logcat hook registration as hook-load evidence.
- Treat verifier JSON as target-app value evidence.
- Compare verifier values against the live Device Masker config snapshot for expected-vs-actual reports.
- Keep Android platform restrictions and unsupported verifier probe shapes separate from real spoof failures.
- Do not claim complete WebView UA spoofing from static UA checks alone; instance `WebSettings.getUserAgentString()` must also be verified.
- WebView instance UA is handled through `WebView.getSettings()` concrete settings discovery; broad classloader hooks remain disabled by default.

## Forbidden Patterns

Do not:
- Add a custom AIDL/Binder path for spoof config or hook evidence.
- Generate identifiers inside target-process hooks.
- Return fake fallback values for malformed config.
- Read app-private JSON from target apps.
- Use Timber in `:xposed`.
- Hardcode preference keys outside `SharedPrefsKeys`.
- Hook abstract methods.
- Mutate framework-returned lists in place.
- Add static regex or parsing initializers that can throw in hooker objects.
- Use direct Kotlin SAM intercept callbacks in runtime hookers; use `StableHooker` instead.
- Re-enable global class lookup anti-detection without a per-app kill switch and runtime validation.
- Claim a target is hooked because the app-side Xposed service is connected.

## Required Hook Fallback Behavior

```mermaid
flowchart TD
    Call["Hooked API call"]
    Module{"Module enabled?"}
    AppList{"Package in current app-config package set?"}
    App{"Target app enabled?"}
    Type{"Spoof type enabled?"}
    Value{"Stored value valid?"}
    Original["Return original"]
    Spoof["Return spoofed value"]

    Call --> Module
    Module -->|No| Original
    Module -->|Yes| AppList
    AppList -->|No| Original
    AppList -->|Yes| App
    App -->|No| Original
    App -->|Yes| Type
    Type -->|No| Original
    Type -->|Yes| Value
    Value -->|No| Original
    Value -->|Yes| Spoof
```
