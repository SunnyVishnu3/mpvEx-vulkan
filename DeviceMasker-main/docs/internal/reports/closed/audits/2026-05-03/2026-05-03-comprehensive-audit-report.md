# DeviceMasker Comprehensive Audit Report

**Date:** 2026-05-03  
**Auditor:** OpenCode (Multi-Agent Comprehensive Review)  
**Scope:** Entire project excluding `common/` and `xposed/` libxposed modules  
**Modules Reviewed:** `:app` (UI, data, service, diagnostics), build system, tests  

---

## Executive Summary

This report represents a deep-dive comprehensive audit of the DeviceMasker Android application using the full `claude-android-ninja`, `navigation-3`, `edge-to-edge`, `r8-analyzer`, and `context7-mcp` skill references. The audit covered **56+ source files**, **24 test files**, **4 build files**, and the Android manifest.

**Overall Grade: C+ (Functional but requires significant hardening)**

The app has a solid architectural foundation with clear separation between UI (Compose), data (Repository + ConfigManager), and service layers. However, there are **5 CRITICAL issues** requiring immediate attention, **15 HIGH-severity issues**, and numerous medium/low findings across concurrency safety, build configuration, testing coverage, Compose performance, and accessibility.

**Top 5 Critical Issues:**
1. **Config lost-update race condition** — Concurrent config modifications can silently overwrite each other, corrupting `config.json`
2. **Synchronous file I/O on logging hot path** — Every `Timber.*` call triggers `@Synchronized` disk append; ANR vector on main thread
3. **No ViewModel or Repository tests** — 0% coverage on all 5 ViewModels and 2 Repositories
4. **Release builds have minification completely disabled** — `isMinifyEnabled = false` leaves ProGuard rules untested and APK bloated
5. **Exported XposedProvider without permission gate** — Any third-party app can query the provider

---

## Table of Contents

1. [CRITICAL Findings](#1-critical-findings)
2. [HIGH Findings](#2-high-findings)
3. [MEDIUM Findings](#3-medium-findings)
4. [LOW / INFO Findings](#4-low--info-findings)
5. [Navigation3 Migration Plan](#5-navigation3-migration-plan)
6. [Dependency Upgrade Recommendations](#6-dependency-upgrade-recommendations)
7. [Build Optimization Recommendations](#7-build-optimization-recommendations)
8. [Testing Roadmap](#8-testing-roadmap)
9. [Appendix: Positive Patterns](#9-appendix-positive-patterns)

---

## 1. CRITICAL Findings

### CRIT-001: ConfigManager Lost-Update Race on `_config.value`
**File:** `service/ConfigManager.kt:149-153`  
**Severity:** CRITICAL  
**Category:** Concurrency / Data Integrity

`updateConfig()` performs a non-atomic read-modify-write on `_config.value`:

```kotlin
private fun updateConfig(transform: (JsonConfig) -> JsonConfig) {
    val newConfig = transform(_config.value)   // read
    _config.value = newConfig                   // write
    saveConfig()
}
```

If two threads call `updateConfig` concurrently (rapid UI toggles + background sync), the second read can stale-read the old config, causing the first update to be silently overwritten. The launched save jobs then race on the same `AtomicFile`.

**Fix:** Use `MutableStateFlow.update { transform(it) }` (atomic CAS loop) and serialize file writes with a `Mutex`.

---

### CRIT-002: PersistentAppLogTree Performs Synchronized File I/O on Every Log Call
**File:** `service/AppLogStore.kt:33-41, 148-178`  
**Severity:** CRITICAL  
**Category:** Performance / ANR

`PersistentAppLogTree.log()` is invoked synchronously for every `Timber.*` call. It enters a `@Synchronized` block on `AppLogStore`, which delegates to `JsonlDiagnosticStore.append()`, another `@Synchronized` block that performs **synchronous file I/O** (`file.appendText`). If `Timber.d()` is called from the main thread, the UI blocks until the disk append completes.

**Fix:** Replace direct file append with an in-memory `Channel` / `ConcurrentLinkedQueue` drained by a dedicated coroutine/background thread, or use a non-blocking ring buffer.

---

### CRIT-003: Zero ViewModel and Repository Test Coverage
**Files:** All `*ViewModel.kt`, `SpoofRepository.kt`, `AppScopeRepository.kt`  
**Severity:** CRITICAL  
**Category:** Testing

All five ViewModels (`HomeViewModel`, `GroupsViewModel`, `GroupSpoofingViewModel`, `SettingsViewModel`, `DiagnosticsViewModel`) and both Repositories (`SpoofRepository`, `AppScopeRepository`) have **zero unit tests**. They contain significant business logic including:
- Flow combination and state derivation
- Correlation-aware value generation
- Carrier/timezone consistency logic
- Import/export JSON handling
- PackageManager queries and caching

**Fix:** Add comprehensive ViewModel tests using `kotlinx-coroutines-test` `TestDispatcher`, `runTest`, and `Turbine` for Flow assertions.

---

### CRIT-004: Release Minification Completely Disabled
**File:** `app/build.gradle.kts:59`  
**Severity:** CRITICAL  
**Category:** Build / Security / Performance

```kotlin
release {
    isMinifyEnabled = false
    isShrinkResources = false
}
```

While documented as a workaround for LSPosed `AbstractMethodError` from R8-synthesized lambdas, this means **none of the extensive keep rules** are validated during CI builds. The APK is significantly larger than necessary, and when shrinking is eventually re-enabled, latent rule gaps will surface in production LSPosed processes.

**Fix:** Add a separate CI build type or job with `isMinifyEnabled = true` (no signing required) that validates ProGuard rules without producing a release artifact.

---

### CRIT-005: Exported XposedProvider Without Permission Gate
**File:** `app/src/main/AndroidManifest.xml:78-82`  
**Severity:** CRITICAL  
**Category:** Security

```xml
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="${applicationId}.XposedService"
    android:exported="true"
    tools:ignore="ExportedContentProvider" />
```

**Any third-party app can query this provider** to read module metadata or interact with RemotePreferences. While LSPosed requires cross-process access, investigate whether LSPosed enforces a signature-level permission.

**Fix:** Document as an accepted architectural risk if LSPosed enforces signature checks. Otherwise, investigate adding a custom permission.

---

### CRIT-006: SpoofRepository Correlation Caches Are Not Thread-Safe
**File:** `data/repository/SpoofRepository.kt:53-55`  
**Severity:** CRITICAL  
**Category:** Concurrency

```kotlin
private var cachedSIMConfig: SIMConfig? = null
private var cachedLocationConfig: LocationConfig? = null
private var cachedDeviceHardwareConfig: DeviceHardwareConfig? = null
```

`generateValue()` (a non-suspend function) reads and writes these caches without synchronization. Concurrent calls could observe partially constructed caches, producing mismatched IMSI/ICCID pairs that break anti-detection consistency.

**Fix:** Use `AtomicReference` or confine cache access to a single coroutine/actor.

---

### CRIT-007: RootShell Can Deadlock on Timeout
**File:** `service/diagnostics/RootShell.kt:93-109`  
**Severity:** CRITICAL  
**Category:** Concurrency / ANR

```kotlin
val process = ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()
val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
if (!completed) {
    process.destroyForcibly()
}
val stdout = process.inputStream.bufferedReader().use { it.readText() }
```

If `waitFor()` times out, `destroyForcibly()` is called, but then the code attempts to read from a destroyed process. On Android, this can hang indefinitely.

**Fix:** Read stdout/stderr in separate threads BEFORE calling `waitFor()`, or use `ProcessBuilder.redirectOutput(File)`.

---

### CRIT-008: AndroidIdGeneratorTest Tests Its Own Helper, Not Production Code
**File:** `common/src/test/.../generators/AndroidIdGeneratorTest.kt:67-72`  
**Severity:** CRITICAL  
**Category:** Testing

The test file defines its own `generateAndroidId()` helper instead of importing and testing the actual production `UUIDGenerator.generateAndroidId()`.

**Fix:** Remove the local helper and test the actual production generator.

---

### CRIT-009: No SavedStateHandle in Any ViewModel — Process Death Loses All UI State
**Files:** All `*ViewModel.kt`  
**Severity:** CRITICAL  
**Category:** UI / State Management

None of the ViewModels accept or use `SavedStateHandle`. This means:
- Selected tab in `GroupSpoofingScreen` resets to 0 on process death
- Expanded/collapsed category sections reset
- Group edit/create dialog visibility resets
- Export mode selection resets
- Search queries reset

**Fix:** Inject `SavedStateHandle` into ViewModels and persist/restore critical UI state.

---

### CRIT-010: ConfigManager Double-Init Race Launches Duplicate Loaders
**File:** `service/ConfigManager.kt:59-73`  
**Severity:** CRITICAL  
**Category:** Concurrency

`init()` checks `_isInitialized.value` outside of a synchronized block. Because `_isInitialized.value = true` is set **after** `loadConfig()` inside the launched coroutine, a second thread calling `init()` milliseconds later will see `false` and re-launch a second loader.

**Fix:** Use an `atomic` boolean / `synchronized` guard, or set `_isInitialized.value = true` atomically before launching the coroutine.

---

## 2. HIGH Findings

### HIGH-001: Full-Screen Loading Overlays Replace Content
**File:** `screens/home/HomeScreen.kt:202`; `screens/groupspoofing/GroupSpoofingScreen.kt:156`  
**Severity:** HIGH  
**Category:** UI / UX

`AnimatedLoadingOverlay` fades the entire screen to show a centered spinner. This violates the "stable layout" principle — skeleton screens or inline indicators are preferred to preserve context.

**Fix:** Show inline `CircularProgressIndicator` or skeleton placeholders while preserving the layout structure.

---

### HIGH-002: Touch Targets Below 48dp Minimum
**File:** `components/expressive/ExpressiveIconButton.kt:63-64`; `components/expressive/CompactExpressiveIconButton.kt:106-117`  
**Severity:** HIGH  
**Category:** Accessibility

`ExpressiveIconButton` default `buttonSize = 40.dp`. `CompactExpressiveIconButton` uses `36.dp`. Material accessibility guidelines require 48dp x 48dp minimum touch targets.

**Fix:** Increase default sizes or add `minimumInteractiveComponentSize()` modifier.

---

### HIGH-003: Compose State Classes Not Marked Immutable
**Files:** `screens/home/HomeState.kt`, `screens/groups/GroupsState.kt`, `screens/groupspoofing/GroupSpoofingState.kt`, `screens/settings/SettingsState.kt`, `screens/diagnostics/DiagnosticsState.kt`  
**Severity:** HIGH  
**Category:** Compose / Performance

All `State` data classes use standard `List<T>` and lack `@Immutable` / `@Stable`. In Strong Skipping Mode, Compose cannot prove stability, causing unnecessary recompositions of entire screens when any field changes.

**Fix:** Add `@Immutable` annotation and use `kotlinx.collections.immutable.ImmutableList` for list fields.

---

### HIGH-004: String-Based Navigation Without Type Safety
**File:** `MainActivity.kt:290-302`; `navigation/NavDestination.kt`  
**Severity:** HIGH  
**Category:** Navigation

Routes are plain strings (`"group_spoofing/{groupId}"`). Arguments are extracted manually via `backStackEntry.arguments?.getString("groupId")`. No compile-time route validation.

**Fix:** Migrate to Compose Navigation type-safe APIs (Navigation 2.8+ `Serializable`/`Parcelable` destinations or Kotlin DSL), or full Navigation3.

---

### HIGH-005: No IME Inset Handling With Edge-to-Edge
**File:** `MainActivity.kt` (Scaffold/NavHost setup)  
**Severity:** HIGH  
**Category:** UI / Edge-to-Edge

App calls `enableEdgeToEdge()` but nowhere in the Compose tree is `WindowInsets.ime` consumed or padded. Text fields may be obscured by the soft keyboard.

**Fix:** Add `Modifier.imePadding()` or `WindowInsets.ime` handling to scrollable containers that contain text fields. Also add `android:windowSoftInputMode="adjustResize"` to `MainActivity` in manifest.

---

### HIGH-006: Race Condition in Bidirectional Pager/Tab Sync
**File:** `screens/groupspoofing/GroupSpoofingScreen.kt:70-79`  
**Severity:** HIGH  
**Category:** UI / State Management

Two `LaunchedEffect` blocks create a feedback loop:
- Effect A: `selectedTab` -> `pagerState.animateScrollToPage()`
- Effect B: `pagerState.currentPage` -> `viewModel.setSelectedTab()`

Rapid user swipes or interrupted animations can cause jitter or out-of-sync states.

**Fix:** Use `snapshotFlow { pagerState.currentPage }` with `distinctUntilChanged()` and debounce, or make the pager the single source of truth.

---

### HIGH-007: Custom ToggleButton Lacks All Accessibility Semantics
**File:** `components/ToggleButton.kt:46-115`  
**Severity:** HIGH  
**Category:** Accessibility

`ToggleButton` is a fully custom toggle implementation with zero accessibility support:
- No `Modifier.semantics { role = Role.Switch }`
- No `stateDescription` for on/off state
- No `toggleable` modifier; uses raw `clickable`

**Fix:** Add `Modifier.semantics { role = Role.Switch; stateDescription = ... }` or replace with `Modifier.toggleable`.

---

### HIGH-008: `allowBackup=true` Exposes Sensitive Config
**File:** `app/src/main/AndroidManifest.xml:20`  
**Severity:** HIGH  
**Category:** Security

`android:allowBackup="true"` permits `adb backup` to extract `JsonConfig`, spoofed identities, and group assignments. For a privacy tool, this should be `false`.

**Fix:** Set `android:allowBackup="false"` or use `android:fullBackupContent` to exclude `filesDir/config.json`.

---

### HIGH-009: LogManager File I/O on Caller Thread (No Dispatcher Switch)
**File:** `service/LogManager.kt:79-131, 133-152`  
**Severity:** HIGH  
**Category:** Concurrency / Performance

`buildSupportBundle`, `getLogCount`, and `hasAnyLogs` are `suspend` but **lack `withContext(Dispatchers.IO)`**. They call blocking file I/O and binder operations. When invoked from a `ViewModel` running on `Dispatchers.Main`, this executes blocking operations on the main thread.

**Fix:** Wrap the body of every public `suspend` function that touches disk or binder in `withContext(Dispatchers.IO)`.

---

### HIGH-010: AppScopeRepository Cache Invalidation Lacks Synchronization
**File:** `data/repository/AppScopeRepository.kt:27, 88-90`  
**Severity:** HIGH  
**Category:** Concurrency

`isCacheValid` is a plain `var`. `invalidateCache()` writes it without acquiring `cacheMutex` or using `volatile`. The loading thread may see a stale `true` indefinitely after invalidation.

**Fix:** Make `isCacheValid` an `AtomicBoolean` or protect it with the same `Mutex`.

---

### HIGH-011: RootShell `execute()` Blocks Calling Thread Without Dispatcher Guard
**File:** `service/diagnostics/RootShell.kt:93-109`  
**Severity:** HIGH  
**Category:** Concurrency / ANR

`RootShell.execute()` calls `process.waitFor()` and reads streams synchronously. `RootLogCollector.collect()` invokes this directly without coroutines or background threads. If invoked from the main thread, it will ANR.

**Fix:** Wrap `RootShell.run()` in `withContext(Dispatchers.IO)` at all call sites, or make `execute()` a `suspend` function.

---

### HIGH-012: RootLogCollector Unescaped Shell Interpolation
**File:** `service/diagnostics/RootLogCollector.kt:18, 29`  
**Severity:** HIGH  
**Category:** Security

```kotlin
"logcat -d -v threadtime | grep -i -E '...|$target'"
```

`targetPackage` is interpolated into a shell command without escaping. A malformed package name containing shell metacharacters can inject arbitrary commands when executed by `su -c`.

**Fix:** Use `ProcessBuilder` with argument lists instead of string concatenation, or shell-escape `targetPackage` with a whitelist regex.

---

### HIGH-013: JsonlDiagnosticStore Unsynchronized Concurrent Read/Write
**File:** `service/diagnostics/JsonlDiagnosticStore.kt:41-44`  
**Severity:** HIGH  
**Category:** Concurrency

`append()` is `@Synchronized`, but `readEvents()` has **no synchronization**. While `append()` atomically appends lines, `readEvents()` iterates files and calls `readLines()` concurrently.

**Fix:** Mark `readEvents()` `@Synchronized` or use a `ReentrantReadWriteLock`.

---

### HIGH-014: GroupSpoofingScreen Has No State Restoration or Deletion Handling
**File:** `ui/screens/groupspoofing/GroupSpoofingScreen.kt:56-162`  
**Severity:** HIGH  
**Category:** UI / State Management

The screen uses `rememberPagerState()` without `rememberSaveable`. On process death, tab index is lost. Also, if the group is deleted while this screen is open, `group` becomes `null` and `AnimatedLoadingOverlay` shows infinite loading. User is stuck.

**Fix:** Use `rememberSaveable` for pager state. Add a `LaunchedEffect` to observe `group == null` and trigger `onNavigateBack()`.

---

### HIGH-015: ConfigSync Public Synchronous-Commit APIs Block Caller Thread
**File:** `data/ConfigSync.kt:93, 151`  
**Severity:** HIGH  
**Category:** Performance

`syncApp()` and `clearApp()` call `SharedPreferences.edit().commit()`, which is **synchronous disk I/O**. If called directly from the UI thread, they will block.

**Fix:** Document that they must be called from `Dispatchers.IO`, or provide `suspend` variants that switch dispatchers.

---

## 3. MEDIUM Findings

### MED-001: Hardcoded Category Colors Outside Theme
**File:** `screens/groupspoofing/model/UIDisplayCategory.kt:39, 58, 67, 81, 95`  
**Severity:** MEDIUM  
**Category:** UI / Theming

Each category has a hardcoded hex color. These do not adapt to dynamic color, light/dark mode, or user contrast preferences.

**Fix:** Derive category tints from `MaterialTheme.colorScheme`.

---

### MED-002: Hardcoded Status Colors Used Directly in Composables
**File:** `screens/diagnostics/DiagnosticsScreen.kt:229`; `screens/home/HomeScreen.kt:220`  
**Severity:** MEDIUM  
**Category:** UI / Theming

`StatusActive`, `StatusInactive`, `StatusWarning` from `Color.kt` are used directly instead of semantic theme colors.

**Fix:** Map status states to theme semantic colors or create a `LocalStatusColors` composition local.

---

### MED-003: `scale()` Modifier Used Instead of `graphicsLayer`
**File:** `components/expressive/ExpressiveCard.kt:87`; `components/expressive/ExpressiveIconButton.kt:78`; `components/ToggleButton.kt:110`  
**Severity:** MEDIUM  
**Category:** Compose / Performance

`Modifier.scale()` triggers layout re-measurement on every animation frame. `graphicsLayer { scaleX = ...; scaleY = ... }` is GPU-accelerated and cheaper.

**Fix:** Replace `scale()` with `graphicsLayer` transforms for press/selection animations.

---

### MED-004: No Contrast Preference Support (API 34+)
**File:** `theme/Theme.kt`  
**Severity:** MEDIUM  
**Category:** UI / Theming

Android 14+ allows users to request high or medium contrast. The theme does not check `ContrastLevel` or provide contrast-adjusted color schemes.

**Fix:** Use `androidx.compose.material3.dynamicDarkColorScheme(context, contrastLevel)` when available.

---

### MED-005: Inline Lambdas in Screen Content Cause Unstable Recompositions
**File:** `MainActivity.kt:241-253`; `screens/home/HomeScreen.kt:106`; `screens/groups/GroupsScreen.kt:169-183`  
**Severity:** MEDIUM  
**Category:** Compose / Performance

Callback lambdas like `{ mode -> settingsViewModel.setThemeMode(mode) }` are recreated on every recomposition of the parent. In Strong Skipping Mode, this makes child composables non-skippable.

**Fix:** Hoist stable callback references with `remember` or use event handler pattern.

---

### MED-006: AppLogStore Inefficient `takeLast` After Full Read
**File:** `service/AppLogStore.kt:44-45`  
**Severity:** MEDIUM  
**Category:** Performance

```kotlin
fun readEntries(): List<AppLogEntry> =
    readDiagnosticEvents().takeLast(maxEntries).map { ... }
```

This reads the **entire** event history from disk into memory, then drops all but the last `maxEntries` (default 500).

**Fix:** Read files in reverse chronological order and stop once `maxEntries` are collected.

---

### MED-007: ConfigManager No Corrupted-Config Recovery / Backup Preservation
**File:** `service/ConfigManager.kt:82-101`  
**Severity:** MEDIUM  
**Category:** Data Integrity

On parse failure, `loadConfig()` silently falls back to a default config and overwrites the existing file. The corrupted file is lost, making post-mortem debugging impossible.

**Fix:** Rename the corrupted file (`config.json.corrupted.$timestamp`) before writing the default, and surface the error to the UI.

---

### MED-008: SupportBundleBuilder OOM Risk Reading Large Root Artifacts
**File:** `service/diagnostics/SupportBundleBuilder.kt:62-67`  
**Severity:** MEDIUM  
**Category:** Performance

`artifact.readText()` loads the entire file into a `String`. Root artifacts such as full logcat or tombstones can be multiple megabytes.

**Fix:** Stream files into the `ZipOutputStream` with a buffered pipe instead of `readText()`.

---

### MED-009: SpoofRepository `suspend` Modifiers with No Actual Suspension
**File:** `data/repository/SpoofRepository.kt:37, 106, 111, 293, 322, 378, 398, 521, etc.`  
**Severity:** MEDIUM  
**Category:** Architecture

The class is annotated `@Suppress("RedundantSuspendModifier")`, and nearly every public method is `suspend`. However, they call `ConfigManager` methods that perform synchronous in-memory state updates and fire-and-forget IO launches. The `suspend` keyword gives callers a false assurance that these methods are main-safe and cancellable.

**Fix:** Remove `suspend` until the underlying layer actually suspends, or switch to `Dispatchers.IO` explicitly inside each method.

---

### MED-010: SpoofRepository.importGroups() Silently Swallows All Exceptions
**File:** `data/repository/SpoofRepository.kt:595-603`  
**Severity:** MEDIUM  
**Category:** Error Handling

```kotlin
suspend fun importGroups(jsonString: String): Boolean {
    return try {
        val config = com.astrixforge.devicemasker.common.JsonConfig.parse(jsonString)
        config.getAllGroups().forEach { group -> ConfigManager.updateGroup(group) }
        true
    } catch (e: Exception) {
        false
    }
}
```

No logging of the parse error. No validation that imported groups don't conflict. No atomicity. Returns `false` for ANY exception, including `OutOfMemoryError`.

**Fix:** Distinguish parse errors from save errors. Log failures. Test with malformed JSON.

---

### MED-011: ViewModels Collect Redundant/Overlapping Flows
**File:** `screens/home/HomeViewModel.kt:38-78`  
**Severity:** MEDIUM  
**Category:** Architecture

`repository.groups` and `repository.activeGroup` are collected separately. Both update `selectedGroup`, `enabledAppsCount`, and `maskedIdentifiersCount`, causing double state emissions and recompositions.

**Fix:** Combine flows with `Flow.combine` or derive values in the UI layer using `derivedStateOf`.

---

### MED-012: Missing `contentDescription` on Multiple Icons
**Files:** `screens/home/HomeScreen.kt:273`; `components/AppListItem.kt:152`  
**Severity:** MEDIUM  
**Category:** Accessibility

The hero shield icon in the status card and app icons in `AppListItem` have `contentDescription = null`. Screen readers miss critical context.

**Fix:** Provide dynamic `contentDescription` based on state.

---

### MED-013: `SimpleDateFormat` Instantiated in Composable Scope
**File:** `components/GroupCard.kt:313`; `screens/groups/GroupsScreen.kt:178`  
**Severity:** MEDIUM  
**Category:** Compose / Performance

`SimpleDateFormat` is created on every recomposition. It is not thread-safe and relatively expensive.

**Fix:** Cache with `remember` or move formatting to ViewModel.

---

### MED-014: RootLogCollector `grep -E '...|'` Regex Matches Everything When Target Empty
**File:** `service/diagnostics/RootLogCollector.kt:18`  
**Severity:** MEDIUM  
**Category:** Logic Bug

When `targetPackage` is null/empty, the grep pattern ends with a trailing `|`. In `grep -E`, an empty alternative matches the empty string at every position, so **every line matches**.

**Fix:** Build the regex conditionally to avoid empty alternatives.

---

### MED-015: DiagnosticSnapshotBuilderTest Only Tests REDACTED Mode
**File:** `app/src/test/.../DiagnosticSnapshotBuilderTest.kt:10-52`  
**Severity:** MEDIUM  
**Category:** Testing

Only `RedactionMode.REDACTED` is tested. `RedactionMode.UNREDACTED` is untested.

**Fix:** Add a test for `UNREDACTED` mode verifying raw identifiers are preserved.

---

## 4. LOW / INFO Findings

### LOW-001: Missing Previews for Tab Content and Item Components
**Files:** `screens/groupspoofing/tabs/SpoofTabContent.kt`, `screens/groupspoofing/tabs/AppsTabContent.kt`, etc.  
**Severity:** LOW  
**Category:** Development Experience

Many reusable content composables lack `@Preview`.

**Fix:** Add lightweight `@Preview` wrappers for key states.

---

### LOW-002: `remember` Used for State That Should Survive Config Change
**Files:** `screens/home/HomeScreen.kt:361`; `screens/groupspoofing/categories/SIMCardContent.kt:65`  
**Severity:** LOW  
**Category:** UI

Dialog/dropdown visibility resets on rotation because they use `remember { mutableStateOf(...) }` instead of `rememberSaveable`.

**Fix:** Use `rememberSaveable` for boolean visibility flags.

---

### LOW-003: `Modifier.alpha()` on Entire Column for Loading State
**File:** `screens/home/HomeScreen.kt:147`  
**Severity:** LOW  
**Category:** Compose / Performance

`Modifier.alpha(if (isLoading) 0f else 1f)` still composes and measures all children when loading.

**Fix:** Use `AnimatedVisibility(visible = !isLoading)` or `Box` with `if (isLoading)` branches.

---

### LOW-004: `NavController` Passed as Default Parameter in Composable
**File:** `MainActivity.kt:151`  
**Severity:** LOW  
**Category:** UI

`navController: NavHostController = rememberNavController()` as a parameter default means a new NavController could be created if the composable is recomposed before the caller provides one.

**Fix:** Hoist `val navController = rememberNavController()` in the `setContent` block and pass it explicitly.

---

### LOW-005: SettingsScreen Pending Export Mode May Drift From Prop
**File:** `screens/settings/SettingsScreen.kt:133`  
**Severity:** LOW  
**Category:** UI

`var pendingExportMode by remember { mutableStateOf(exportMode) }` initializes from prop but never updates if the parent prop changes later.

**Fix:** Use `LaunchedEffect(exportMode) { pendingExportMode = exportMode }`.

---

### LOW-006: Missing `contentType` in Some LazyColumns
**File:** `screens/diagnostics/DiagnosticsScreen.kt`  
**Severity:** LOW  
**Category:** Compose / Performance

`LazyColumn` items use `key` but not `contentType`. Different item types benefit from `contentType` for composition recycling.

**Fix:** Add `contentType = { "header" / "section" / "card" }` to `item` and `items` calls.

---

### LOW-007: Redundant Daemon Property
**File:** `gradle.properties:24`  
**Severity:** LOW  
**Category:** Build

`org.gradle.daemon=true` is redundant; Gradle 7.0+ enables the daemon by default.

**Fix:** Remove the property.

---

### LOW-008: IDE-Specific Build Logic
**File:** `build.gradle.kts:62-68`  
**Severity:** LOW  
**Category:** Build

The `idea { module { excludeDirs... } }` block should live in `.idea/` or version-control ignore files, not the shared build script.

**Fix:** Move to `.idea/` or remove.

---

### LOW-009: Missing Build-Logic/Convention Plugins
**Files:** `app/build.gradle.kts`, `common/build.gradle.kts`, `xposed/build.gradle.kts`  
**Severity:** LOW  
**Category:** Build

`compileSdk`, `minSdk`, `jvmToolchain(17)`, `JavaVersion.VERSION_17`, and lint config are duplicated across modules.

**Fix:** A `build-logic` module with convention plugins would eliminate duplication.

---

### LOW-010: Spotless ktfmt Not in Version Catalog
**File:** `build.gradle.kts:35`  
**Severity:** LOW  
**Category:** Build

`ktfmt("0.54")` is hardcoded. Moving this to `libs.versions.toml` allows automated updates.

**Fix:** Move to version catalog.

---

## 5. Navigation3 Migration Plan

### 5.1 Current State

The app uses Navigation 2.x with string-based routes:
- `NavHost` with `rememberNavController()`
- Manual `navArgument` extraction
- `popUpTo(NavRoutes.HOME) { saveState = true }` for bottom-nav state
- Single back stack

### 5.2 Target State (Navigation3)

Navigation3 (`androidx.navigation3`) provides:
- **True multiple back stacks** per tab
- **Type-safe routes** via `@Serializable`
- `NavDisplay`-based host with adaptive layouts
- Better process death survival

### 5.3 Migration Steps

| Step | Action | Files | Details |
|------|--------|-------|---------|
| 1 | **Add dependency** | `gradle/libs.versions.toml` | Add `androidx.navigation3:navigation3-compose` (or equivalent). Keep `navigation-compose` during transition if needed. |
| 2 | **Define route types** | `ui/navigation/NavDestination.kt` | Replace string constants with `@Serializable` data objects/classes:<br>`@Serializable data object Home`<br>`@Serializable data class GroupSpoofing(val groupId: String)` |
| 3 | **Replace NavHost** | `MainActivity.kt` | Replace `NavHost(...)` with `NavDisplay(...)` or Navigation3 `NavHost`. Provide `List<NavDestination>` and `NavBackStack`. |
| 4 | **Update bottom nav** | `BottomNavBar.kt`, `MainActivity.kt` | Each tab gets its own `NavBackStack`. Navigation becomes `navController.navigate(Home)` with type safety. |
| 5 | **Update ViewModel scoping** | `MainActivity.kt` | Verify `viewModel()` behaves correctly with new back stack entries. |
| 6 | **Migrate animations** | `MainActivity.kt` | Replace `AnimatedContentTransitionScope` with Navigation3 transition APIs. |
| 7 | **Remove navArgument boilerplate** | `MainActivity.kt` | With serializable routes, `groupId` is a typed constructor property. |
| 8 | **Remove currentRoute string inspection** | `MainActivity.kt` | Bottom bar visibility derived from back stack destination type. |

### 5.4 Risk Mitigation

- Perform in a feature branch
- Validate LSPosed hook registration after build changes
- Run full gate: `spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug`

---

## 6. Dependency Upgrade Recommendations

| Dependency | Current | Recommended | Notes |
|---|---|---|---|
| AGP | `9.2.0` | **Verify latest stable** | Ensure stable and plugin-compatible |
| Kotlin | `2.3.0` | **Verify latest** | Compose compiler integrated in Kotlin 2.0+ |
| Gradle | `9.4.1` | **Verify latest** | Check wrapper URL resolves |
| Compose BOM | `2026.02.01` | `2026.05.x` (or latest) | ~3 months old; check for M3 improvements |
| Navigation Compose | `2.9.7` | **Verify latest** | If migrating to Navigation3, plan deprecation |
| DataStore | `1.2.0` | **Verify latest** | Check for bug fixes |
| Coroutines | `1.10.2` | **Verify latest** | Check release notes |
| Coil | `3.4.0` | **Verify latest** | Active 3.x branch |
| Timber | `5.0.1` | `5.0.1` (no newer) | Consider `kotlin-logging` for future |
| Spotless | `8.3.0` | **Verify latest** | ktfmt compatibility with Kotlin 2.3.0 |
| Foojay Resolver | `1.0.0` | `1.1.x` | Better toolchain resolution |
| libxposed api | `101.0.1` | `101.0.1` | **KEEP AS-IS** per requirements |
| libxposed service | `101.0.0` | `101.0.1` | Align patch with api |
| hiddenapibypass | `6.1` | **Verify latest** | Check for updates |

**Important:** Before upgrading, verify versions resolve on `maven.google.com` and `repo1.maven.org`. Some versions in the catalog appear aggressively new or future-dated.

---

## 7. Build Optimization Recommendations

1. **Add CI ProGuard validation job:** Create a temporary build type with `isMinifyEnabled = true` (no signing) to exercise keep rules without producing release artifacts.
2. **Remove dead R8 properties** from `gradle.properties` or enable minification in a test build.
3. **Secure the manifest:** Set `android:allowBackup="false"` and add `android:windowSoftInputMode="adjustResize"` to `MainActivity`.
4. **Fix signing config robustness:** Change conditional to check `storeFile?.exists() == true`.
5. **Add Compose compiler metrics:** Enable `reportsDestination` and `metricsDestination` to diagnose recomposition hotspots.
6. **Consider build-logic/convention plugins** to deduplicate `compileSdk`, `minSdk`, `jvmToolchain` across modules.
7. **Move Spotless ktfmt version** to `libs.versions.toml`.

---

## 8. Testing Roadmap

### Priority 1: Coroutine and Flow Infrastructure
- Add `Turbine` dependency
- Create `MainDispatcherRule` using `UnconfinedTestDispatcher`
- Rewrite existing async tests with `runTest`

### Priority 2: ViewModel Tests (All 5 ViewModels)
| ViewModel | Key Scenarios |
|-----------|--------------|
| `HomeViewModel` | Module toggle, group selection, regeneration, Xposed connection state |
| `GroupsViewModel` | Create/delete/export/import groups, error handling |
| `GroupSpoofingViewModel` | Tab switching, value regeneration, carrier update, app assignment, group deletion |
| `SettingsViewModel` | Theme change, export success/failure, clear result |
| `DiagnosticsViewModel` | Service connection, refresh, anti-detection tests |

### Priority 3: Repository Tests
| Repository | Key Scenarios |
|------------|--------------|
| `SpoofRepository` | Correlation consistency, SIM-only regeneration, carrier/timezone sync, import malformed JSON |
| `AppScopeRepository` | PackageManager failure, cache invalidation, system app filtering |

### Priority 4: Config and Sync Tests
| Component | Key Scenarios |
|-----------|--------------|
| `ConfigManager` | Concurrent modifications, corrupted JSON recovery, file write failure |
| `ConfigSync` | Commit failure, null prefs, empty config, large config snapshot |
| `XposedPrefs` | Service bind/unbind, callback delivery, `getPrefs()` failure |

### Priority 5: Service and Diagnostics Tests
| Component | Key Scenarios |
|-----------|--------------|
| `ServiceClient` | Connection timeout, binder null, retry exhaustion |
| `LogManager` | Export with no logs, PackageManager failure, URI open failure |
| `RootShell` | Process timeout, stdout truncation, root unavailable |

### Priority 6: UI Instrumented Tests
| Screen | Key Scenarios |
|--------|--------------|
| `HomeScreen` | Group selection, module toggle, regeneration |
| `GroupsScreen` | Create group, delete group, set default |
| `GroupSpoofingScreen` | Tab switch, regenerate value, assign app, navigate back on deletion |
| `SettingsScreen` | Theme change, export logs |

---

## 9. Appendix: Positive Patterns

The following patterns are well-implemented and should be preserved:

1. **AtomicFile usage in ConfigManager:** Correct `startWrite()` / `finishWrite()` / `failWrite()` pattern (`service/ConfigManager.kt:129-136`)
2. **CopyOnWriteArrayList in XposedPrefs:** Safe callback list management (`data/XposedPrefs.kt:45`)
3. **SettingsDataStore separation:** Properly uses DataStore for UI settings, keeping hook config in JSON (`data/SettingsDataStore.kt`)
4. **ServiceClient dispatcher switching:** All public methods correctly use `withContext(Dispatchers.IO)` (`service/ServiceClient.kt`)
5. **StateFlow exposure via `asStateFlow()`:** Proper immutable exposure in multiple ViewModels
6. **Redaction-by-default in diagnostics:** Support bundles default to `RedactionMode.REDACTED`
7. **Edge-to-edge setup in MainActivity:** Proper `enableEdgeToEdge()` with dynamic system bar styling
8. **Motion policy for accessibility:** `AppMotion.shouldReduceMotion()` respects system settings

---

*End of Comprehensive Audit Report*
