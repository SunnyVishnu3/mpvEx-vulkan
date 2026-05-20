# 2026-05-18 Performance Optimization And Warning Cleanup Summary

Status: complete for local implementation and static/build verification.

Source plan: `docs/superpowers/plans/2026-05-18-performance-optimization-and-warning-cleanup.md`

Related audit: `docs/internal/reports/closed/audits/2026-05-18/2026-05-18-comprehensive-performance-audit.md`

No commit or push was performed.

## Scope

The implementation focused on existing hot paths and warning cleanup:
- Home scoped-app loading.
- App icon decode reuse.
- RemotePreferences sync write size.
- Xposed value lookup in hook callbacks.
- Hook registration diagnostic volume.
- Support bundle JSONL memory use.
- Startup root-capture contention.
- Group Spoofing Apps tab row derivation.
- Lint/detekt warnings introduced or exposed by this work.

No new spoof surfaces, config delivery paths, custom Binder/AIDL paths, or broad Xposed behavior were added.

## Data Shape Changes

- Home scoped-app UI now uses scoped package metadata keyed by package name. It does not depend on a full installed-app scan at startup.
- `AppIconCache` centralizes bounded icon bitmap reuse for app rows.
- `ConfigManager` carries dirty package sync hints so app/group mutations can sync only affected packages.
- `ConfigSync.syncPackages()` writes the current enabled-app allowlist/config version and only the requested package keys.
- `HookConfigSnapshot` is built once per selected package in `XposedEntry.onPackageReady()` and passed to value hookers.
- `AppRowModel` precomputes Apps tab display/search/sort fields in `GroupSpoofingViewModel`.

## App Changes

- `AppScopeRepository.loadScopedApps()` resolves labels/icons for LSPosed-scoped packages only.
- `HomeViewModel` observes LSPosed scope and builds scoped rows from a package metadata map.
- Home scoped rows use bounded lazy rendering with stable package keys.
- `CachedAppIcon` replaces repeated direct icon decoding in touched app lists.
- Production ViewModel factories create lifecycle `SavedStateHandle` instances instead of detached default handles.
- Startup root capture waits until after first frame plus 1500 ms before requesting capture.
- `SupportBundleBuilder` streams app/xposed JSONL entries line by line into the ZIP.
- Unused string resources reported by lint were removed after the resource cleanup was verified.

## Xposed Changes

- Value hookers read `HookConfigSnapshot` in hot callbacks instead of repeatedly reading RemotePreferences.
- Persona fallback remains allowed only after the spoof type is enabled.
- Anti-detect and proc-maps policy still read their own app-level preference keys.
- Hook registration keeps health counters, disabled/failure events, and one final `All hooks registered` event.
- Per-hook debug start/success spam was removed from the common path.
- Runtime hook callbacks continue to use `stableHooker`/named hooker shapes.

## Verifier Changes

- The verifier manifest is lint-clean with explicit launcher icon, backup, and data-extraction rules.
- `SystemProperties` reflection remains a verifier evidence probe only and has a narrow `PrivateApi` suppression.

## Warning Decisions

- `ApplySharedPref`/`UseKtx` around config sync writes are intentional. The code needs explicit `commit()` result checks; the KTX edit helper would not preserve that boolean result in this path.
- `PrivateApi` suppressions are intentional for Xposed and verifier platform-reflection evidence paths.
- `OldTargetApi` is intentionally suppressed. Target SDK behavior changes need a separate validation plan, not a warning-only bump.
- Version-catalog dependency availability lint is suppressed because an attempted coroutine version bump could not be verified in this environment and caused dependency resolution failure.
- The launcher-looking foreground vector is retained through a narrow lint suppression instead of being deleted without icon review.
- Final module lint reports for app/common/xposed/verifier say `No issues found`.

## Verification

Passed:

```powershell
.\gradlew.bat lint --no-daemon --no-configuration-cache
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon --no-configuration-cache
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon --no-configuration-cache
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.groups.GroupsViewModelTest lint --no-daemon --no-configuration-cache
```

Log files:
- `logs/build/2026-05-18-warning-cleanup-local-gate.txt`
- `logs/build/2026-05-18-warning-cleanup-release-r8-gate.txt`
- `logs/build/2026-05-18-warning-cleanup-final-focused.txt`

GitNexus `detect_changes(scope=all)` reported critical aggregate risk because the planned scope was broad:
- 52 changed files.
- 217 changed symbols.
- 80 affected symbols.

The affected flows matched the expected areas: Home, app scope loading, config sync, support bundle/root capture, group app rows, and Xposed hook registration/value lookup.

## Evidence Boundary

This work has unit/static/build/R8 proof. It does not add fresh target-device LSPosed runtime hook proof. Do not claim new target-app hook success from this summary alone.

## Follow-Ups

- Rerun target-device LSPosed validation before making new runtime hook success claims.
- Treat target SDK upgrades as a separate behavior-validation task.
- Treat dependency upgrades as a separate dependency-verification task.
