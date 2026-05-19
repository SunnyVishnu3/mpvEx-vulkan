# UI Audit Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remediate the confirmed high-value findings from `docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md` without implementing findings that the report already corrected as non-issues.

**Architecture:** Keep this surgical: move shared app models out of UI packages, add real diagnostics failure handling, make navigation mutation private, make active-group switching one config transform, and fix the AMOLED/status-token theme gaps. Do not refactor broad screens, rewrite repositories, or add abstractions unless a task below requires it.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose, Material 3 Expressive, Navigation 3, kotlinx.coroutines, kotlinx.collections.immutable, Robolectric, Turbine, Gradle wrapper on Windows.

---

## Non-Goals

Do not implement these audit items because the same report marks them corrected or weak:

- Lambda-field or lambda-allocation rewrites for Compose strong skipping.
- `navigationBackHandler` `remember` wrapping.
- Animation-label string changes.
- Compact icon button touch-target changes.
- Global uncaught exception handler for Xposed/module context.
- Broad collector rewrites whose only claim is "multiple collectors are always wrong."
- Whole-screen splits based only on line count.

## File Structure

- `app/src/main/kotlin/com/astrixforge/devicemasker/diagnostics/DiagnosticsModels.kt`  
  Shared app-side diagnostics result models used by service and UI layers.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsState.kt`  
  UI state only; imports diagnostics models instead of declaring them.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/IDiagnosticsProvider.kt`  
  Service boundary returning diagnostics models from the non-UI package.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/DefaultDiagnosticsProvider.kt`  
  Default app-side diagnostics provider.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt`  
  Uses provider injection and catches diagnostics failures.
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModelTest.kt`  
  Regression coverage for loading reset and error state.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/models/ThemeMode.kt`  
  Theme preference enum moved out of UI theme package.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeMode.kt`  
  Delete after imports are migrated.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ISettingsDataStore.kt`  
  Imports `ThemeMode` from data models.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/SettingsDataStore.kt`  
  Imports `ThemeMode` from data models and names persisted values.
- `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeSettingsDataStore.kt`  
  Imports moved `ThemeMode`.
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt`  
  Imports moved `ThemeMode`.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/**/*.kt`  
  Only update `ThemeMode` imports where already used.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt`  
  Keeps mutable Navigation 3 back stack private; exposes read-only stack.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainNavigationContent.kt`  
  Uses the internal mutable back stack for `NavDisplay`.
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigatorTest.kt`  
  Regression coverage that public visible back stack cannot mutate navigation.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/IConfigManager.kt`  
  Adds an atomic default-group operation to the config boundary.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`  
  Implements default-group switching through one `updateConfig` transform.
- `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeConfigManager.kt`  
  Test fake implementation of the same atomic operation.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`  
  Delegates `setActiveGroup()` to the atomic config operation.
- `app/src/test/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepositoryTest.kt`  
  Regression coverage for exactly one default group after active-group switch.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt`  
  Completes dynamic AMOLED surface overrides.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/StatusColors.kt`  
  New theme-aware status color helper.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Color.kt`  
  Keep legacy status constants only if still needed after migration.
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeColorTest.kt`  
  JVM coverage for status color helper and AMOLED surface transformation.
- `memory-bank/activeContext.md`, `memory-bank/progress.md`  
  Record completed remediation and verification.

## Execution Rules

Before editing a function, class, or method, run GitNexus upstream impact on the symbol and record the risk in the task notes. If GitNexus reports HIGH or CRITICAL risk, stop and report the blast radius before changing code.

Use Windows commands from repo root. After Kotlin or Compose changes, run Spotless and Detekt together. Run tests for touched modules after each task. Use frequent commits.

---

### Task 1: Move Diagnostics Models Out Of UI Package

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/diagnostics/DiagnosticsModels.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/IDiagnosticsProvider.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/DefaultDiagnosticsProvider.kt`
- Modify imports in diagnostics UI files that reference `DiagnosticResult`, `DiagnosticStatus`, or `AntiDetectionTest`

- [ ] **Step 1: Run GitNexus impact**

Run:

```powershell
# REQUIRED before touching these symbols
# Use MCP: mcp__gitnexus__impact target="DiagnosticResult" direction="upstream" repo="DeviceMasker"
# Use MCP: mcp__gitnexus__impact target="AntiDetectionTest" direction="upstream" repo="DeviceMasker"
```

Expected: LOW or MEDIUM risk. If HIGH or CRITICAL, stop and report before editing.

- [ ] **Step 2: Create shared diagnostics model file**

Create `app/src/main/kotlin/com/astrixforge/devicemasker/diagnostics/DiagnosticsModels.kt`:

```kotlin
package com.astrixforge.devicemasker.diagnostics

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.common.SpoofType

/** Data class representing a diagnostic result. */
@Immutable
data class DiagnosticResult(
    val type: SpoofType,
    val realValue: String?,
    val spoofedValue: String?,
    val isActive: Boolean,
    val isSpoofed: Boolean,
) {
    val status: DiagnosticStatus
        get() =
            when {
                !isActive -> DiagnosticStatus.INACTIVE
                isSpoofed -> DiagnosticStatus.SUCCESS
                else -> DiagnosticStatus.WARNING
            }
}

enum class DiagnosticStatus {
    SUCCESS,
    WARNING,
    INACTIVE,
}

/** Anti-detection test result. */
@Immutable
data class AntiDetectionTest(val nameRes: Int, val descriptionRes: Int, val isPassed: Boolean)
```

- [ ] **Step 3: Reduce DiagnosticsState to UI state only**

In `DiagnosticsState.kt`, remove `SpoofType` import and the model declarations at the bottom. Add imports:

```kotlin
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
```

The file should end after:

```kotlin
enum class ReproCaptureState {
    IDLE,
    CAPTURING,
    STOPPING,
    EXPORT_READY,
    ERROR,
}
```

- [ ] **Step 4: Update service imports**

In `IDiagnosticsProvider.kt`, replace UI imports with:

```kotlin
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
```

In `DefaultDiagnosticsProvider.kt`, replace UI imports with:

```kotlin
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
```

- [ ] **Step 5: Update diagnostics UI imports**

Run:

```powershell
rg -n "ui\.screens\.diagnostics\.(AntiDetectionTest|DiagnosticResult|DiagnosticStatus)|\\bAntiDetectionTest\\b|\\bDiagnosticResult\\b|\\bDiagnosticStatus\\b" app/src/main/kotlin app/src/test/kotlin
```

For every non-`DiagnosticsState.kt` file that uses those types, import from:

```kotlin
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
import com.astrixforge.devicemasker.diagnostics.DiagnosticStatus
```

- [ ] **Step 6: Verify no service-to-ui diagnostics import remains**

Run:

```powershell
rg -n "import com\\.astrixforge\\.devicemasker\\.ui\\." app/src/main/kotlin/com/astrixforge/devicemasker/service
```

Expected: no matches for diagnostics result types. If other UI imports appear, report them before expanding scope.

- [ ] **Step 7: Run focused compile/test**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsViewModelTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/diagnostics/DiagnosticsModels.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics app/src/main/kotlin/com/astrixforge/devicemasker/service/IDiagnosticsProvider.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/DefaultDiagnosticsProvider.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModelTest.kt
git commit -m "refactor(diagnostics): move models out of ui layer"
```

---

### Task 2: Move ThemeMode Out Of UI Theme Package

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/data/models/ThemeMode.kt`
- Delete: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeMode.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ISettingsDataStore.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/SettingsDataStore.kt`
- Modify all imports currently using `com.astrixforge.devicemasker.ui.theme.ThemeMode`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeSettingsDataStore.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Run GitNexus impact**

Run:

```powershell
# REQUIRED before touching this symbol
# Use MCP: mcp__gitnexus__impact target="ThemeMode" direction="upstream" repo="DeviceMasker"
```

Expected: MEDIUM risk because imports are broad. If HIGH or CRITICAL, stop and report.

- [ ] **Step 2: Create data-layer ThemeMode**

Create `app/src/main/kotlin/com/astrixforge/devicemasker/data/models/ThemeMode.kt` with the same enum values:

```kotlin
package com.astrixforge.devicemasker.data.models

import com.astrixforge.devicemasker.R

/** User-selected app theme preference stored by SettingsDataStore. */
enum class ThemeMode(val displayNameRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}
```

- [ ] **Step 3: Update imports mechanically**

Run:

```powershell
rg -l "com\\.astrixforge\\.devicemasker\\.ui\\.theme\\.ThemeMode" app/src/main/kotlin app/src/test/kotlin
```

In every returned Kotlin file, replace:

```kotlin
import com.astrixforge.devicemasker.ui.theme.ThemeMode
```

with:

```kotlin
import com.astrixforge.devicemasker.data.models.ThemeMode
```

- [ ] **Step 4: Remove the old UI ThemeMode file**

Delete:

```text
app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeMode.kt
```

- [ ] **Step 5: Name persisted integer values**

In `SettingsDataStore.kt`, add constants inside `Keys`:

```kotlin
private const val THEME_SYSTEM = 0
private const val THEME_LIGHT = 1
private const val THEME_DARK = 2
```

Replace theme reads with:

```kotlin
when (prefs[Keys.THEME_MODE]) {
    THEME_SYSTEM -> ThemeMode.SYSTEM
    THEME_LIGHT -> ThemeMode.LIGHT
    THEME_DARK -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}
```

Replace theme writes with:

```kotlin
prefs[Keys.THEME_MODE] =
    when (mode) {
        ThemeMode.SYSTEM -> THEME_SYSTEM
        ThemeMode.LIGHT -> THEME_LIGHT
        ThemeMode.DARK -> THEME_DARK
    }
```

- [ ] **Step 6: Verify the layer violation is gone**

Run:

```powershell
rg -n "import com\\.astrixforge\\.devicemasker\\.ui\\.theme\\.ThemeMode" app/src/main/kotlin/com/astrixforge/devicemasker/data app/src/test/kotlin/com/astrixforge/devicemasker/testing
```

Expected: no matches.

- [ ] **Step 7: Run settings tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModelTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/data app/src/main/kotlin/com/astrixforge/devicemasker/ui app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeSettingsDataStore.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt
git add -u app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeMode.kt
git commit -m "refactor(settings): move theme mode out of ui layer"
```

---

### Task 3: Add Diagnostics Exception Boundary

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModelTest.kt`

- [ ] **Step 1: Run GitNexus impact**

Run:

```powershell
# REQUIRED before touching this symbol
# Use MCP: mcp__gitnexus__impact target="DiagnosticsViewModel" direction="upstream" repo="DeviceMasker"
```

Expected: LOW or MEDIUM risk.

- [ ] **Step 2: Write failing test for diagnostics failure**

Add imports to `DiagnosticsViewModelTest.kt`:

```kotlin
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.diagnostics.AntiDetectionTest
import com.astrixforge.devicemasker.diagnostics.DiagnosticResult
import com.astrixforge.devicemasker.service.IDiagnosticsProvider
```

Change `createViewModel()` signature:

```kotlin
private fun createViewModel(
    repository: FakeSpoofRepository = FakeSpoofRepository(),
    isXposedActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
    diagnosticsProvider: IDiagnosticsProvider? = null,
): DiagnosticsViewModel {
    val app = RuntimeEnvironment.getApplication()
    return DiagnosticsViewModel(
        application = app,
        repository = repository,
        isXposedActiveFlow = isXposedActiveFlow,
        diagnosticsProvider = diagnosticsProvider ?: DefaultTestDiagnosticsProvider(),
    )
}
```

Add the fake providers at the bottom of the test file:

```kotlin
private class DefaultTestDiagnosticsProvider : IDiagnosticsProvider {
    override suspend fun runDiagnosticTests(repository: ISpoofRepository): List<DiagnosticResult> =
        emptyList()

    override fun runAntiDetectionTests(): List<AntiDetectionTest> = emptyList()
}

private class ThrowingDiagnosticsProvider : IDiagnosticsProvider {
    override suspend fun runDiagnosticTests(repository: ISpoofRepository): List<DiagnosticResult> {
        error("diagnostics exploded")
    }

    override fun runAntiDetectionTests(): List<AntiDetectionTest> = emptyList()
}
```

Add the test:

```kotlin
@Test
fun `diagnostics failure clears loading and records error`() = runTest {
    val viewModel = createViewModel(diagnosticsProvider = ThrowingDiagnosticsProvider())

    viewModel.state.test {
        val state = awaitItem()
        assertFalse(state.isLoading)
        assertTrue(state.diagnosticsErrorMessage?.contains("diagnostics exploded") == true)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsViewModelTest --no-daemon
```

Expected: FAIL because `DiagnosticsState.diagnosticsErrorMessage` and `DiagnosticsViewModel.diagnosticsProvider` do not exist.

- [ ] **Step 4: Add diagnostics error state**

In `DiagnosticsState.kt`, add a new property:

```kotlin
val diagnosticsErrorMessage: String? = null,
```

Place it after `antiDetectionResults`.

- [ ] **Step 5: Inject provider and guard diagnostics execution**

In `DiagnosticsViewModel.kt`, add imports:

```kotlin
import com.astrixforge.devicemasker.service.DefaultDiagnosticsProvider
import com.astrixforge.devicemasker.service.IDiagnosticsProvider
import timber.log.Timber
```

Update constructor:

```kotlin
class DiagnosticsViewModel(
    application: Application,
    private val repository: ISpoofRepository,
    isXposedActiveFlow: StateFlow<Boolean> = XposedPrefs.isServiceConnected,
    private val diagnosticsProvider: IDiagnosticsProvider = DefaultDiagnosticsProvider(application),
    @Suppress("unused") private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : AndroidViewModel(application) {
```

Replace `runDiagnostics()` with:

```kotlin
private fun runDiagnostics() {
    viewModelScope.launch {
        runCatching {
                val diagnosticResults = diagnosticsProvider.runDiagnosticTests(repository)
                val antiDetectionResults = diagnosticsProvider.runAntiDetectionTests()

                _state.update {
                    it.copy(
                        diagnosticResults = diagnosticResults.toImmutableList(),
                        antiDetectionResults = antiDetectionResults.toImmutableList(),
                        diagnosticsErrorMessage = null,
                        isLoading = false,
                    )
                }
            }
            .onFailure { error ->
                Timber.tag(TAG).e(error, "Diagnostics run failed")
                _state.update {
                    it.copy(
                        diagnosticsErrorMessage =
                            error.message ?: "Diagnostics failed without a message",
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
    }
}
```

Remove the private `runDiagnosticTests()` and `runAntiDetectionTests()` methods from `DiagnosticsViewModel`; their logic now lives in `DefaultDiagnosticsProvider`.

Update companion object:

```kotlin
private companion object {
    private const val TAG = "DiagnosticsViewModel"
    private const val MIN_REFRESH_DURATION_MILLIS = 400L
}
```

- [ ] **Step 6: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsViewModelTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsState.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModelTest.kt
git commit -m "fix(diagnostics): recover from diagnostics failures"
```

---

### Task 4: Hide Mutable Navigation Back Stack

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainNavigationContent.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigatorTest.kt`

- [ ] **Step 1: Run GitNexus impact**

Run:

```powershell
# REQUIRED before touching this symbol
# Use MCP: mcp__gitnexus__impact target="DeviceMaskerNavigationState" direction="upstream" repo="DeviceMasker"
```

Expected: MEDIUM risk because navigation rendering consumes the stack.

- [ ] **Step 2: Write failing mutation-protection test**

Add this test to `DeviceMaskerNavigatorTest.kt`:

```kotlin
@Test
fun visibleBackStackCannotMutateNavigationState() {
    val state = DeviceMaskerNavigationState()

    val exposedStack = state.visibleBackStack

    assertFalse(exposedStack is MutableList<*>)
    assertEquals(listOf(NavDestination.Home), exposedStack)
    assertEquals(NavDestination.Home, state.currentDestination)
}
```

- [ ] **Step 3: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
```

Expected: FAIL because current `visibleBackStack` is a mutable state list.

- [ ] **Step 4: Make public stack read-only and keep NavDisplay stack internal**

In `DeviceMaskerNavigationState.kt`, add import:

```kotlin
import androidx.compose.runtime.snapshots.SnapshotStateList
```

Replace:

```kotlin
val visibleBackStack = mutableStateListOf<NavDestination>().apply { addAll(currentBackStack) }
```

with:

```kotlin
private val mutableVisibleBackStack =
    mutableStateListOf<NavDestination>().apply { addAll(currentBackStack) }

val visibleBackStack: List<NavDestination>
    get() = mutableVisibleBackStack.toList()

internal val navDisplayBackStack: SnapshotStateList<NavDestination>
    get() = mutableVisibleBackStack
```

Replace `syncVisibleBackStack()` with:

```kotlin
private fun syncVisibleBackStack() {
    mutableVisibleBackStack.clear()
    mutableVisibleBackStack.addAll(currentBackStack)
}
```

- [ ] **Step 5: Update NavDisplay call site**

In `MainNavigationContent.kt`, replace:

```kotlin
backStack = navigationState.visibleBackStack,
```

with:

```kotlin
backStack = navigationState.navDisplayBackStack,
```

- [ ] **Step 6: Run navigation tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainNavigationContent.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigatorTest.kt
git commit -m "fix(navigation): hide mutable back stack"
```

---

### Task 5: Make Active Group Switching Atomic

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/IConfigManager.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeConfigManager.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepositoryTest.kt`

- [ ] **Step 1: Run GitNexus impact**

Run:

```powershell
# REQUIRED before touching these symbols
# Use MCP: mcp__gitnexus__impact target="setActiveGroup" direction="upstream" repo="DeviceMasker"
# Use MCP: mcp__gitnexus__impact target="ConfigManager" direction="upstream" repo="DeviceMasker"
```

Expected: MEDIUM risk. If HIGH or CRITICAL, stop and report.

- [ ] **Step 2: Write failing repository test**

Add this test to `SpoofRepositoryTest.kt`:

```kotlin
@Test
fun `setActiveGroup leaves exactly one default group`() = runTest {
    val first = configManager.createGroup("First")
    val second = configManager.createGroup("Second")
    configManager.updateGroup(first.copy(isDefault = true))
    configManager.updateGroup(second.copy(isDefault = false))

    repository.setActiveGroup(second.id)

    val groups = configManager.getAllGroups()
    assertEquals(listOf(second.id), groups.filter { it.isDefault }.map { it.id })
}
```

- [ ] **Step 3: Run test to verify the new config API is missing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.data.repository.SpoofRepositoryTest --no-daemon
```

Expected before implementation: existing behavior may pass semantically, but the later implementation still removes the multi-write path. This test becomes the regression guard for exactly one default group.

- [ ] **Step 4: Add config boundary method**

In `ConfigGroupStore` inside `IConfigManager.kt`, add:

```kotlin
fun setDefaultGroup(groupId: String)
```

Place it after `updateGroup(group: SpoofGroup)`.

- [ ] **Step 5: Implement single-transform default-group switch in ConfigManager**

In `ConfigManager.kt`, add this method near `updateGroup()`:

```kotlin
override fun setDefaultGroup(groupId: String) {
    updateConfig { config ->
        if (config.getGroup(groupId) == null) return@updateConfig config

        config.copy(
            groups =
                config.groups.mapValues { (id, group) ->
                    group.copy(
                        isDefault = id == groupId,
                        updatedAt =
                            if (id == groupId || group.isDefault) {
                                System.currentTimeMillis()
                            } else {
                                group.updatedAt
                            },
                    )
                }
        )
    }
}
```

- [ ] **Step 6: Implement fake config method**

In `FakeConfigManager.kt`, add:

```kotlin
override fun setDefaultGroup(groupId: String) {
    if (_config.value.getGroup(groupId) == null) return

    _config.value =
        _config.value.copy(
            groups =
                _config.value.groups.mapValues { (id, group) ->
                    group.copy(isDefault = id == groupId)
                }
        )
}
```

- [ ] **Step 7: Delegate repository active group change**

In `SpoofRepository.setActiveGroup()`, replace the whole body with:

```kotlin
override suspend fun setActiveGroup(groupId: String) {
    configManager.setDefaultGroup(groupId)
}
```

- [ ] **Step 8: Run repository tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.data.repository.SpoofRepositoryTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/IConfigManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeConfigManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt app/src/test/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepositoryTest.kt
git commit -m "fix(config): switch active group atomically"
```

---

### Task 6: Complete AMOLED Surface Tokens And Theme-Aware Status Colors

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/StatusColors.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt`
- Modify status-color call sites found by `rg -n "StatusActive|StatusInactive|StatusWarning|Color\\.White" app/src/main/kotlin/com/astrixforge/devicemasker/ui`
- Create: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeColorTest.kt`

- [ ] **Step 1: Run GitNexus impact**

Run:

```powershell
# REQUIRED before touching these symbols
# Use MCP: mcp__gitnexus__impact target="dynamicAmoledColorScheme" direction="upstream" repo="DeviceMasker"
# Use MCP: mcp__gitnexus__impact target="StatusActive" direction="upstream" repo="DeviceMasker"
```

Expected: LOW or MEDIUM risk.

- [ ] **Step 2: Write theme token tests**

Create `app/src/test/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeColorTest.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.theme

import androidx.compose.material3.darkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorTest {
    @Test
    fun amoledCopyOverridesEverySurfaceContainer() {
        val scheme = darkColorScheme().withAmoledSurfaces()

        assertEquals(AmoledBlack, scheme.background)
        assertEquals(AmoledBlack, scheme.surface)
        assertEquals(AmoledBlack, scheme.surfaceContainerLowest)
        assertEquals(AmoledSurface, scheme.surfaceContainerLow)
        assertEquals(AmoledSurfaceContainer, scheme.surfaceContainer)
        assertEquals(AmoledSurfaceContainerHigh, scheme.surfaceContainerHigh)
        assertEquals(AmoledSurfaceContainerHighest, scheme.surfaceContainerHighest)
    }

    @Test
    fun statusColorsUseSchemeRoles() {
        val scheme = darkColorScheme()

        assertEquals(scheme.primary, scheme.statusActive)
        assertEquals(scheme.error, scheme.statusInactive)
        assertEquals(scheme.tertiary, scheme.statusWarning)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.theme.ThemeColorTest --no-daemon
```

Expected: FAIL because `withAmoledSurfaces()` and status extensions do not exist.

- [ ] **Step 4: Add status color extensions**

Create `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/StatusColors.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val ColorScheme.statusActive: Color
    get() = primary

val ColorScheme.statusOnActive: Color
    get() = onPrimary

val ColorScheme.statusInactive: Color
    get() = error

val ColorScheme.statusOnInactive: Color
    get() = onError

val ColorScheme.statusWarning: Color
    get() = tertiary

val ColorScheme.statusOnWarning: Color
    get() = onTertiary
```

- [ ] **Step 5: Add reusable AMOLED surface copy**

In `Theme.kt`, add:

```kotlin
internal fun ColorScheme.withAmoledSurfaces(): ColorScheme =
    copy(
        background = AmoledBlack,
        surface = AmoledBlack,
        surfaceContainer = AmoledSurfaceContainer,
        surfaceContainerHigh = AmoledSurfaceContainerHigh,
        surfaceContainerHighest = AmoledSurfaceContainerHighest,
        surfaceContainerLow = AmoledSurface,
        surfaceContainerLowest = AmoledBlack,
    )
```

Replace `dynamicAmoledColorScheme()` body with:

```kotlin
private fun dynamicAmoledColorScheme(context: Context): ColorScheme =
    dynamicDarkColorScheme(context).withAmoledSurfaces()
```

- [ ] **Step 6: Migrate status call sites**

Run:

```powershell
rg -n "StatusActive|StatusInactive|StatusWarning|Color\\.White" app/src/main/kotlin/com/astrixforge/devicemasker/ui
```

For status surfaces, replace constants with `MaterialTheme.colorScheme.statusActive`, `statusInactive`, or `statusWarning`. For icon/text on those status surfaces, replace `Color.White` with `MaterialTheme.colorScheme.statusOnActive`, `statusOnInactive`, or `statusOnWarning` to match the status background.

Example replacement:

```kotlin
containerColor = MaterialTheme.colorScheme.statusActive
contentColor = MaterialTheme.colorScheme.statusOnActive
```

- [ ] **Step 7: Run theme test and app tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.theme.ThemeColorTest --no-daemon
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --no-daemon
```

Expected: both commands return `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme app/src/main/kotlin/com/astrixforge/devicemasker/ui app/src/test/kotlin/com/astrixforge/devicemasker/ui/theme/ThemeColorTest.kt
git commit -m "fix(theme): complete amoled and status color tokens"
```

---

### Task 7: Mechanical Compose Polish From Confirmed Findings

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt`
- Modify dialog composables missing `modifier: Modifier = Modifier`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/LocationContent.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/SectionHeader.kt`
- Modify string resources: `app/src/main/res/values/strings.xml`
- Modify tests only if compile or existing tests require signature updates

- [ ] **Step 1: Run GitNexus impact**

Run targeted impact before touching each symbol:

```powershell
# Use MCP impact before edits:
# target="GroupsScreenBody" direction="upstream" repo="DeviceMasker"
# target="SectionHeader" direction="upstream" repo="DeviceMasker"
# target="LocationCategoryContent" or matching LocationContent symbol direction="upstream" repo="DeviceMasker"
```

Expected: LOW or MEDIUM risk.

- [ ] **Step 2: Fix ignored GroupsScreenBody modifier**

In `GroupsScreen.kt`, find `GroupsScreenBody`. The root layout must use the passed modifier:

```kotlin
Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
```

If the current root is a `LazyColumn`, use:

```kotlin
LazyColumn(
    modifier = modifier,
    contentPadding = contentPadding,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
```

Use the version matching the current root container. Do not add a wrapper layout.

- [ ] **Step 3: Add saveable location sheet state**

In `LocationContent.kt`, replace:

```kotlin
var showTimezoneSheet by remember { mutableStateOf(false) }
```

with:

```kotlin
var showTimezoneSheet by rememberSaveable { mutableStateOf(false) }
```

Add import if missing:

```kotlin
import androidx.compose.runtime.saveable.rememberSaveable
```

- [ ] **Step 4: Add expand/collapse strings**

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="action_expand">Expand</string>
<string name="action_collapse">Collapse</string>
```

In `SectionHeader.kt`, replace hardcoded content descriptions:

```kotlin
contentDescription =
    stringResource(
        if (isExpanded) R.string.action_collapse else R.string.action_expand
    )
```

Add imports if missing:

```kotlin
import androidx.compose.ui.res.stringResource
import com.astrixforge.devicemasker.R
```

- [ ] **Step 5: Add modifier parameters to dialog composables**

For each dialog composable reported by Detekt or the audit, add `modifier: Modifier = Modifier` as the last optional parameter and pass it to the top-level dialog content container.

Use this exact shape:

```kotlin
@Composable
fun ExampleDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        confirmButton = { /* existing confirm button */ },
        text = { /* existing content */ },
    )
}
```

Do not change call-site behavior unless the compiler requires a named-argument update.

- [ ] **Step 6: Run UI compile and app tests**

Run:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui app/src/main/res/values/strings.xml app/src/test/kotlin/com/astrixforge/devicemasker
git commit -m "fix(ui): apply focused compose polish"
```

---

### Task 8: Final Verification, Memory Bank, And Audit Lifecycle

**Files:**
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Optionally move: `docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md` to `docs/internal/reports/closed/audits/2026-05-16/2026-05-16-comprehensive-ui-audit.md` only after implementation is complete and the report no longer has open remediation.

- [ ] **Step 1: Run full app quality gate**

Run:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run GitNexus detect changes**

Run:

```powershell
# Use MCP: mcp__gitnexus__detect_changes scope="all" repo="DeviceMasker"
```

Expected: risk is LOW or MEDIUM and affected processes match the edited app-side UI/config areas. If HIGH or CRITICAL appears, inspect before committing.

- [ ] **Step 3: Update Memory Bank**

Append to `memory-bank/activeContext.md` under a `2026-05-16 UI Audit Remediation` heading:

```markdown
## 2026-05-16 UI Audit Remediation

- Remediated confirmed findings from the active comprehensive UI audit while excluding findings the report corrected as Compose strong-skipping or platform non-issues.
- Moved diagnostics result models out of UI packages so service code no longer imports UI diagnostics types.
- Moved `ThemeMode` out of `ui/theme` into data models so settings persistence no longer depends on UI theme packages.
- Added diagnostics failure recovery so loading state clears and failures are modeled in state.
- Hid mutable Navigation 3 back-stack state from public callers while preserving the internal mutable stack required by `NavDisplay`.
- Made active-group switching a single config transform through `ConfigManager.setDefaultGroup()`.
- Completed dynamic AMOLED surface-container overrides and routed status colors through theme-aware color-scheme helpers.
- Verification passed: `.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon`.
```

Append the same concise facts to `memory-bank/progress.md`.

- [ ] **Step 4: Close or leave audit report active**

If all tasks above are complete, move the report:

```powershell
New-Item -ItemType Directory -Force docs/internal/reports/closed/audits/2026-05-16
Move-Item docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md docs/internal/reports/closed/audits/2026-05-16/2026-05-16-comprehensive-ui-audit.md
```

If any task remains unimplemented, keep the report active and do not move it.

- [ ] **Step 5: Commit final docs**

For completed remediation:

```powershell
git add memory-bank/activeContext.md memory-bank/progress.md docs/internal/reports/closed/audits/2026-05-16/2026-05-16-comprehensive-ui-audit.md
git add -u docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md
git commit -m "docs(memory): record ui audit remediation"
```

For partial remediation:

```powershell
git add memory-bank/activeContext.md memory-bank/progress.md
git commit -m "docs(memory): record ui audit remediation progress"
```

---

## Final Review Checklist

- [ ] No code implements audit findings marked corrected/non-issues.
- [ ] `rg -n "import com\\.astrixforge\\.devicemasker\\.ui\\." app/src/main/kotlin/com/astrixforge/devicemasker/service app/src/main/kotlin/com/astrixforge/devicemasker/data` shows no service/data dependency on UI packages except intentionally reviewed app UI entrypoints outside data/service.
- [ ] `rg -n "com\\.astrixforge\\.devicemasker\\.ui\\.theme\\.ThemeMode" app/src/main app/src/test` returns no matches.
- [ ] `DeviceMaskerNavigatorTest` proves callers cannot mutate `visibleBackStack`.
- [ ] `DiagnosticsViewModelTest` proves diagnostics failures clear loading and expose error state.
- [ ] `SpoofRepositoryTest` proves active group switching leaves exactly one default group.
- [ ] `ThemeColorTest` proves all AMOLED surface containers are overridden and status colors use scheme roles.
- [ ] Full verification command passes.
