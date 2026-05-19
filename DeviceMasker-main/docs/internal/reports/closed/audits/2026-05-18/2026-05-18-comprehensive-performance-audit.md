# Comprehensive Performance Audit

Created: 2026-05-18  
Updated: 2026-05-18 after user correction: this report is code-first and does not require adding a benchmark module, baseline-profile module, or new app module.

Scope: whole Device Masker repo, focused on concrete in-place optimizations for startup, scoped-app loading, Compose smoothness, config sync, Xposed target-process overhead, diagnostics/export cost, and APK/resource size.

## Findings

### High 1. Scoped apps load late because Home waits for a full installed-app scan

Evidence:
- Home starts full app loading during `HomeViewModel` init: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:42` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:43`.
- Home state combines `repository.appScopeRepository.installedApps` before building scoped apps: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:49` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:86`.
- `AppScopeRepository.loadApps()` serializes the full load through a mutex: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt:32` through `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt:43`.
- `queryInstalledApps()` calls `getInstalledApplications(GET_META_DATA)`, maps every app, and sorts all labels: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt:60` through `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt:67`.
- Each app also does `getPackageInfo()` for `versionName`: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt:73` through `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt:78`.
- Home only needs packages from LSPosed scope joined with app config and group names: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt:12` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt:40`.

Problem:
This is the likely direct cause of the user-visible “scoped apps are loading late” behavior. The common case is a small LSPosed scope, but Home pays for scanning, label lookup, version lookup, and sorting every installed app before it can show scoped rows.

Root cause:
Wrong data shape. Home needs targeted metadata for `scopePackages`, but the repository only exposes an all-installed-app cache.

Fix direction:
Add a scoped-package metadata path inside the existing `AppScopeRepository`/`IAppScopeRepository`; do not add a new module.

Suggested implementation shape:
```kotlin
interface IAppScopeRepository {
    val installedApps: StateFlow<List<InstalledApp>>
    val scopedAppMetadata: StateFlow<Map<String, InstalledApp>>
    suspend fun loadApps(forceRefresh: Boolean = false)
    suspend fun loadScopedApps(packages: Set<String>, forceRefresh: Boolean = false)
    fun invalidateCache()
}
```

Implementation notes:
- `loadScopedApps()` should call `getApplicationInfo(packageName, 0)` and `getApplicationLabel()` only for scoped packages.
- Do not call `getPackageInfo()` for Home scoped rows unless the UI actually displays version.
- Keep `loadApps()` for Group Spoofing Apps tab search/assignment.
- Cache scoped metadata by package name and refresh only when scope changes or user pulls to refresh.

Verification:
1. Add `AppScopeRepositoryTest` cases for scoped-only loading -> verify: only scoped packages are resolved.
2. Add `HomeViewModelTest` for LSPosed scope arriving before full app scan -> verify: Home scoped apps render from scoped metadata.
3. Run `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.data.repository.AppScopeRepositoryTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --no-daemon`.

### High 2. App icons are decoded independently per row with no shared cache

Evidence:
- Group Spoofing rows load and convert icons in `AppIcon()` with `produceState`, `getApplicationIcon()`, and `toBitmap()`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt:180` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt:198`.
- Home scoped rows repeat the same pattern in `HomeScopedAppIcon()`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt:168` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt:183`.
- The two paths do not share results.

Problem:
Scrolling or opening screens triggers repeated PackageManager icon loads and bitmap conversion. That is avoidable row-level work. The code is not wrong, but it is too expensive for a list that can contain many apps.

Root cause:
Icon data is owned by composable row functions instead of by a cache/repository. This pushes I/O and bitmap conversion into composition-adjacent code.

Fix direction:
Add one in-process icon cache under the existing app data/repository layer, not a new dependency-heavy system.

Suggested implementation shape:
```kotlin
class AppIconCache(
    private val packageManager: PackageManager,
    private val maxEntries: Int = 128,
) {
    suspend fun getIcon(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        // LRU lookup first, PackageManager decode only on miss.
    }

    fun clear() { ... }
    fun remove(packageName: String) { ... }
}
```

Implementation notes:
- Use `LruCache<String, ImageBitmap>` or a small synchronized `LinkedHashMap`.
- Put conversion in one place.
- Reuse it from both `AppListItem` and `HomeScopedAppsSection`.
- Invalidate on full app refresh; package-specific invalidation can be added later if package broadcast handling is introduced.

Verification:
1. Unit test cache hit/miss/invalidate behavior.
2. Run `.\gradlew.bat :app:testDebugUnitTest --tests *AppIcon* --no-daemon` after adding tests.
3. Manual check: open Home, open Apps tab, return Home; icons should not visibly reload for already seen packages.

### High 3. Home scoped-app section eagerly composes every scoped row

Evidence:
- `HomeScopedAppsSection` uses `Column` plus `forEach` for every scoped app: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt:83` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt:89`.
- Each row also starts icon loading work through `produceState`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt:171` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt:183`.

Problem:
If LSPosed scope contains many user apps, Home composes every card and starts every visible/non-visible icon load immediately. This is special-case insanity caused by assuming the scoped list stays tiny.

Root cause:
The UI uses an eager layout for unbounded user-controlled data.

Fix direction:
Use a bounded lazy list inside the existing Home section.

Practical implementation:
- Replace the inner `Column` with `LazyColumn` or a height-bounded `LazyColumn`.
- Use `items(scopedApps, key = { it.packageName })`.
- Keep the existing card UI and section header.
- If nested scroll becomes awkward on Home, cap the first N rows and provide “show all” expansion within the same screen.

Verification:
1. Add Compose/UI-state test or preview with 100 scoped apps.
2. Run `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --no-daemon`.

### High 4. Config sync does broad full-sync work for narrow user actions

Evidence:
- Full sync builds and commits a full snapshot: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt:62` through `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt:89`.
- Snapshot build iterates every app config and every spoof type: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt:124` through `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt:180`.
- `syncStateFor()` generates a persona for each enabled app during sync: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt:84` through `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt:113`.
- `ConfigManager.updateConfig()` calls `saveConfig()` for every mutation: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:244` through `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:246`.

Problem:
Toggling one app or changing one group value can trigger work over all app configs and all spoof types. That is not scalable and can delay RemotePreferences sync under larger configs.

Root cause:
The persistence path lacks dirty-scope information. The data model is correct; the sync shape is too broad.

Fix direction:
Keep the existing modules and RemotePreferences path, but add scoped sync operations:
- App enable toggle -> update JSON, then `ConfigSync.syncApp()` for that package plus update `enabled_apps`.
- Group value change -> sync only packages assigned to that group.
- Group enable/default changes -> sync affected assigned packages.
- Full sync stays for init, service bind, import, and repair.

Important:
Do not blindly replace `commit()` with `apply()`. That would be lint-driven voodoo programming. The project needs committed config writes before claiming sync success. Reduce how much gets committed.

Verification:
1. Add `ConfigSyncTest` cases for package-scoped sync and group-scoped sync.
2. Run `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.ConfigSyncTest --no-daemon`.
3. Runtime verification later: update a single app and confirm target selection/hook values still reflect RemotePreferences.

### High 5. Xposed hook callbacks can parse persona JSON on the hot path

Evidence:
- Hook callbacks use `getConfiguredSpoofValue()` from `BaseSpoofHooker`: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt:112` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt:116`.
- `PrefsHelper.getStoredSpoofValue()` falls back to `getPersonaSpoofValue()` if a flat value is blank/missing: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt:33` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt:43`.
- Fallback parses `DevicePersona` JSON: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt:84` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt:92`.
- Telephony and system property hooks call this inside callbacks: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt:114` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt:120`, `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt:290` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt:299`.

Problem:
The flat preference lookup is acceptable. JSON parsing inside hook callbacks is not. If a flat key is missing, malformed, or intentionally absent, target apps can pay parsing cost repeatedly on identifier calls.

Root cause:
Hook-side config access is value-by-value instead of snapshot-based.

Fix direction:
Add a small `HookConfigSnapshot` in `:xposed`, built once during hook registration:
```kotlin
internal data class HookConfigSnapshot(
    val packageName: String,
    val version: Long,
    val enabled: Set<SpoofType>,
    val values: Map<SpoofType, String>,
    val deviceProfile: DeviceProfilePreset?,
)
```

Implementation notes:
- Build from flat keys first.
- Parse persona once as fallback while building the snapshot.
- Pass snapshot to hookers or wrap it in a `HookConfigReader`.
- If live updates must remain, refresh snapshot when `KEY_CONFIG_VERSION` changes, not on every value read.

Verification:
1. Add `PrefsHelperTest` or new `HookConfigSnapshotTest` proving persona parses once per snapshot.
2. Run `.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.* --no-daemon`.
3. Run R8 ABI guard after callback signature changes: `.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon`.

### Medium 1. Xposed registration logs do too much structured logging during target startup

Evidence:
- `onPackageReady()` logs target selection and every hooker start/success/failure through `XposedDiagnosticEventSink.log()`: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:123` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:180`, `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:241` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:287`.
- `XposedDiagnosticEventSink.log()` writes Android logcat, builds a `DiagnosticEvent`, serializes JSON, then calls module logging twice: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt:17` through `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt:33`.

Problem:
LSPosed evidence is required, but debug start/success logs for every hooker are too much work in target app startup. This is not worth the overhead on the common success path.

Root cause:
Normal success logging is treated like evidence-heavy diagnostics instead of counters plus one summary.

Fix direction:
- Keep INFO evidence for `XposedEntry loaded`, target selected, and `All hooks registered`.
- Keep structured ERROR/WARN logs for failures/skips.
- Record per-hooker success in `HookHealthRegistry` only.
- Emit one summary event after registration.

Verification:
1. Static test: required evidence strings still exist.
2. Runtime logcat: assigned verifier still shows load, target selection, all hooks registered, spoof events.

### Medium 2. Apps tab filtering is acceptable but still does full list sorting on every app-config change

Evidence:
- `rememberFilteredApps()` recomputes filter/sort when `appConfigs` changes: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:148` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:173`.
- Sorting lowercases labels and checks assignment state: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:159` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:170`.
- Lazy keys are already correct: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:329`.

Problem:
This is not a disaster. Calling it critical would be bogus shit. But toggling one app changes `appConfigs`, causing O(n log n) list work again.

Root cause:
The row list is derived in composition from broad inputs.

Fix direction:
Move normalized app row derivation into `GroupSpoofingViewModel`:
- Store `normalizedLabel` and `normalizedPackageName` in a UI row model.
- Precompute assigned-to-current-group and assigned-to-other-group fields.
- Let the composable render a ready list.

Verification:
1. Add `GroupSpoofingViewModelTest` with large fake app list and assignment toggle.
2. Run `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.groupspoofing.GroupSpoofingViewModelTest --no-daemon`.

### Medium 3. Startup root capture competes with first usable UI

Evidence:
- Root capture is requested from first composition: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffects.kt:18` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffects.kt:20`.
- It calls root access and starts capture service when granted: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffects.kt:58` through `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffects.kt:63`.
- `RootAccessManager.requestRootAccess()` may initialize shell/root work on IO: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootAccessManager.kt:34` through `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootAccessManager.kt:58`.

Problem:
This is not main-thread I/O, but it is non-essential startup work. It can still contend with first-screen rendering and app process warmup.

Root cause:
Support/evidence capture is coupled to first composition.

Fix direction:
Delay startup root capture until after Home state is usable, or make startup capture opt-in. Keep the export-time root snapshot path; that is the more user-driven time to spend on root/logcat collection.

Verification:
1. Unit test root warning behavior still works.
2. Manual smoke: root-granted startup still captures when enabled.

### Medium 4. Support export builds large intermediate strings

Evidence:
- `LogManager.buildSupportBundle()` reads app events and passes them as encoded strings: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:96` through `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:151`.
- `SupportBundleBuilder` joins all app events before writing: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt:31`.
- Root artifacts are streamed line-by-line, which is the better shape: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt:64` through `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt:76`.

Problem:
For larger logs, `joinToString()` creates unnecessary memory pressure.

Root cause:
The app event zip entry is batch/string-oriented instead of stream-oriented.

Fix direction:
Make app/xposed JSONL zip entries stream line-by-line like root artifact files.

Verification:
1. Add `SupportBundleBuilderTest` with many events.
2. Run `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon`.

### Low 1. Lint warning volume shows resource/build cleanup opportunity

Evidence:
- Existing lint output reports `0 errors, 1127 warnings`.
- It flags many unused resources and states unused resources make apps larger and slow down builds.

Problem:
This is not the first performance fix. It is a later size/build-time cleanup.

Fix direction:
After runtime/UI optimizations, do a scoped resource cleanup. Do not delete strings/resources blindly because this project has many UI iterations and public docs/tests may rely on names indirectly.

Verification:
1. Run lint before/after.
2. Build release with resource shrinking.
3. Smoke launch key screens.

### Info 1. Older synchronous logging finding is no longer current

Evidence:
- `AppLogStore` writes through a buffered channel and IO writer scope: `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt:43` through `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt:58`.

Problem:
Do not waste time refixing the old May 3 synchronous `PersistentAppLogTree` issue. It is already structurally improved.

Fix direction:
Focus on the current bottlenecks above.

## Executive Summary

The best performance work here is not adding a benchmark module first. The code already shows concrete fixable bottlenecks:

- Home scoped apps should not depend on scanning all installed apps.
- App icons need one shared cache instead of per-row decode.
- Home scoped list needs lazy/bounded rendering.
- Config sync needs dirty-scope writes instead of broad full-sync for narrow actions.
- Xposed hooks need a prebuilt per-process config snapshot so hot callbacks do not parse persona JSON.
- Xposed registration logging should keep required evidence but stop serializing debug success logs for every hooker.
- Support export should stream JSONL entries instead of joining huge strings.

This is a code/data-shape problem. Fix the data structures first.

## Scope

Audited:
- `:app` Home, scoped apps, app metadata loading, app icons, Group Spoofing app list, config sync, startup root capture, support export.
- `:xposed` target selection, registration logging, preference reads, hook callback value access.
- `:common` only where persona generation and config sync shape affect performance.

Not included:
- No request to implement fixes in this report update.
- No new benchmark module recommendation as a required step.
- No device run or Gradle build was performed during this report update.

## Source Inventory

Project-local evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Existing lint output from `app/build/reports/lint-results-debug.txt`

## Project Rule Violations

No hard Device Masker rule violation was found in the audited code.

Performance gap:
- Several paths are safe but shaped poorly for performance. This is not a reason to rewrite modules. It is a reason to tighten data flow.

## AGENTS.md And Rule Drift Audit

Recommended future rule update, not applied by this report:

```text
- Home scoped-app UI must not require a full installed-app scan. Resolve LSPosed-scoped package metadata through a scoped package path and reserve full installed-app scans for app assignment/search screens.
- Performance patches must define a local before/after check, but they do not require a new benchmark module unless the user explicitly asks for benchmark infrastructure.
```

## Root Cause Analysis

The core issues are:
- Home uses the wrong repository output for scoped apps.
- Icon loading is duplicated in row composables.
- Config sync lacks dirty scopes.
- Xposed callbacks use value-by-value preference reads instead of a snapshot.
- Logging/export paths favor complete diagnostics over cheap common-case operation.

None of this needs a new app module or enterprise sludge.

## Recommended Fix Order

```text
1. Add scoped metadata loading to AppScopeRepository -> verify: HomeViewModel tests show scoped apps without full scan.
2. Add shared app icon cache -> verify: app icon tests and manual Home/Apps navigation show no repeat reload.
3. Make Home scoped apps lazy/bounded -> verify: 100 scoped-app fake state composes/scrolls correctly.
4. Add scoped ConfigSync writes -> verify: ConfigSync tests cover app/group dirty scopes.
5. Add Xposed HookConfigSnapshot -> verify: xposed unit tests and R8 hook ABI guard pass.
6. Coalesce Xposed registration logging -> verify: required LSPosed evidence remains.
7. Stream support bundle event writes -> verify: SupportBundleBuilder tests pass with large event count.
8. Clean unused resources later -> verify: lint warnings drop and release resource shrink still works.
```

## Architecture Improvement Opportunities

Minimal types that would improve shape:

```kotlin
data class ScopedAppMetadata(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

data class AppRowModel(
    val packageName: String,
    val label: String,
    val normalizedLabel: String,
    val normalizedPackageName: String,
    val isSystemApp: Boolean,
    val isAssignedToCurrentGroup: Boolean,
    val assignedToOtherGroupName: String?,
    val isAppEnabled: Boolean,
)

data class HookConfigSnapshot(
    val packageName: String,
    val version: Long,
    val values: Map<SpoofType, String>,
    val enabledTypes: Set<SpoofType>,
    val deviceProfile: DeviceProfilePreset?,
)
```

## UI/UX Review Audit

Performance-related UI fixes:
- Show a real scoped-app loading state instead of falling through to empty while metadata is unresolved.
- Keep LSPosed disconnected, empty scope, metadata loading, and package-not-installed as distinct states.
- Avoid loading every icon before the user scrolls.
- Keep current visual design; optimize the data/rendering path underneath.

## Best Solution Direction

Best path:
1. Fix scoped metadata fast path.
2. Cache icons.
3. Lazy-render scoped rows.
4. Scope config sync.
5. Snapshot Xposed config.

That is the direct route to improving actual app performance without removing features.

## Optional Improvements

Optional later:
- Add lightweight timing logs around `loadApps`, `loadScopedApps`, config sync, and support export.
- Add dependency upgrades only after the core code-path fixes.
- Add profile-guided startup later if the user explicitly wants packaging/profile optimization.

## Rejected Or Risky Approaches

- Requiring a benchmark module before code fixes: rejected for this request.
- Requiring Baseline/Startup Profiles as first step: rejected for this request.
- Replacing `commit()` with `apply()` globally: this is voodoo programming because config sync correctness depends on committed writes.
- Adding Hilt/Room/new architecture layers for performance: enterprise sludge.
- Removing diagnostics evidence from Xposed: violates project validation needs.
- Broad rewrites of Home or Group Spoofing UI: random churn.

## Verification Plan

```text
1. Scoped metadata path -> verify: AppScopeRepositoryTest + HomeViewModelTest.
2. Icon cache -> verify: cache unit tests + manual repeated navigation.
3. Lazy Home scoped list -> verify: compile + home UI/unit tests with large fake list.
4. Scoped ConfigSync -> verify: ConfigSyncTest for app/group dirty scopes.
5. HookConfigSnapshot -> verify: xposed unit tests + R8HookerAbiTest.
6. Xposed log coalescing -> verify: logcat still has module load, target selected, all hooks registered, spoof events.
7. Streaming support export -> verify: SupportBundleBuilderTest with large event count.
8. Final gate -> verify: `.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon`.
```

## Residual Risks And Unknowns

- No runtime profiler trace was captured in this report update.
- App icon cache must handle package uninstall/update invalidation.
- Hook config snapshot must preserve live RemotePreferences behavior or explicitly refresh by config version.
- Scoped metadata path must not reintroduce raw package-name fallback in Home if project UX still wants missing packages omitted.

## Suggested Next Tasks

1. Implement scoped metadata loading in `AppScopeRepository` and wire Home to it.
2. Add shared app icon cache.
3. Convert Home scoped apps to lazy/bounded rendering.
4. Scope ConfigSync writes by dirty package/group.
5. Add Xposed hook config snapshot.

## Report File Path

`docs/internal/reports/active/audits/2026-05-18/2026-05-18-comprehensive-performance-audit.md`

## Write Boundary Confirmation

This update modified exactly one report file. It did not edit source, tests, public docs, Memory Bank, build files, logs, commits, branches, or tags.
