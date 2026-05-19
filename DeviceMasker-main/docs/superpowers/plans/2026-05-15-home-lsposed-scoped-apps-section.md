# Home LSPosed Scoped Apps Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current wrong Home "Targeted Apps" implementation with a Home section that lists apps currently present in LSPosed module scope, shows each app as a card, and lets the user globally enable/disable Device Masker spoofing for scoped user apps.

**Architecture:** LSPosed scope and Device Masker config are separate gates. The Home section is sourced from `XposedService.getScope()` plus installed app metadata, while the per-app switch writes `AppConfig.isEnabled` through the existing config pipeline. Group assignment remains `AppConfig.groupId`; app enablement remains `AppConfig.isEnabled`; hook proof remains LSPosed/logcat, not Home UI.

**Tech Stack:** Kotlin 2.3.21, Android app module, Jetpack Compose, Material 3 Expressive, libxposed service API 101, kotlinx immutable collections, coroutine `StateFlow`, JVM unit tests.

---

## Current Wrong Implementation To Replace

The current uncommitted Home implementation derives `targetedInstalledApps` from:

```text
JsonConfig.appConfigs + AppScopeRepository.installedApps
```

That is not an LSPosed scoped app list. It is a Device Masker configured-app list. The replacement must derive the list from:

```text
XposedService.getScope() + AppScopeRepository.installedApps + JsonConfig.appConfigs
```

The UI must not claim hooks are active. LSPosed scoped means the module is allowed to load in that package process after process restart. Actual hook success still requires LSPosed/logcat evidence.

## File Structure

Create:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedScopeState.kt`
  - App-side LSPosed scope snapshot model.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedApp.kt`
  - Home-only UI model for an LSPosed scoped user app.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt`
  - Pure data join: LSPosed scope packages + installed apps + app configs + groups.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt`
  - Section composable and per-app cards.

- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilderTest.kt`
  - Unit tests for the pure join logic.

Modify:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`
  - Expose `scopeState: StateFlow<XposedScopeState>` and refresh scope from `XposedService.getScope()`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/ISpoofRepository.kt`
  - Add `setAppEnabled(packageName: String, enabled: Boolean)`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
  - Implement `setAppEnabled()` via `ConfigManager.setAppEnabled()`.

- `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeSpoofRepository.kt`
  - Implement `setAppEnabled()` for ViewModel tests.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt`
  - Remove `TargetedApp`; add `scopedApps: ImmutableList<HomeScopedApp>` and `isScopeAvailable`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`
  - Remove `computeTargetedInstalledApps()`.
  - Combine LSPosed scope state.
  - Add `setScopedAppEnabled(packageName, enabled)`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`
  - Replace `TargetedAppsSection` with `HomeScopedAppsSection`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
  - Split assignment and enabled predicates so disabled apps still appear assigned.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt`
  - Add disabled-assigned display support without changing assignment data.

- `app/src/main/res/values/strings.xml`
  - Replace targeted-app strings with scoped-app strings and disabled messaging.

Delete:

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt`
  - Replace with `HomeScopedAppsSection.kt`.

Do not modify:

- `:xposed` runtime hookers.
- `ConfigSync`.
- `SharedPrefsKeys`.
- `JsonConfig` schema.
- `SpoofGroup.assignedApps`.

---

### Task 1: Replace the stale plan target and write failing pure builder tests

**Files:**
- Create: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilderTest.kt`

- [ ] **Step 1: Write the failing builder test file**

Create `HomeScopedAppsBuilderTest.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.screens.home

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.data.models.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScopedAppsBuilderTest {

    @Test
    fun `includes only installed user apps present in LSPosed scope`() {
        val group = SpoofGroup.createNew(name = "Research", isDefault = true)
        val result =
            buildHomeScopedApps(
                scopedPackages = setOf("android", "system", "com.example.scoped", "missing.app"),
                installedApps =
                    listOf(
                        InstalledApp(
                            packageName = "com.example.scoped",
                            label = "Scoped App",
                            isSystemApp = false,
                            versionName = "1.0",
                        ),
                        InstalledApp(
                            packageName = "com.example.unscoped",
                            label = "Unscoped App",
                            isSystemApp = false,
                            versionName = "1.0",
                        ),
                        InstalledApp(
                            packageName = "android",
                            label = "Android System",
                            isSystemApp = true,
                            versionName = null,
                        ),
                    ),
                appConfigs =
                    mapOf(
                        "com.example.scoped" to
                            AppConfig(
                                packageName = "com.example.scoped",
                                groupId = group.id,
                                isEnabled = true,
                            )
                    ),
                groups = listOf(group),
            )

        assertEquals(listOf("com.example.scoped"), result.map { it.packageName })
        assertEquals("Scoped App", result.single().label)
    }

    @Test
    fun `configured disabled scoped app remains visible but disabled`() {
        val group = SpoofGroup.createNew(name = "Research", isDefault = true)
        val result =
            buildHomeScopedApps(
                scopedPackages = setOf("com.example.disabled"),
                installedApps =
                    listOf(
                        InstalledApp(
                            packageName = "com.example.disabled",
                            label = "Disabled App",
                            isSystemApp = false,
                            versionName = "2.0",
                        )
                    ),
                appConfigs =
                    mapOf(
                        "com.example.disabled" to
                            AppConfig(
                                packageName = "com.example.disabled",
                                groupId = group.id,
                                isEnabled = false,
                            )
                    ),
                groups = listOf(group),
            )

        val app = result.single()
        assertFalse(app.isAppEnabled)
        assertTrue(app.isAssigned)
        assertEquals("Research", app.groupName)
        assertFalse(app.canSpoof)
        assertEquals(HomeScopedAppStatus.DISABLED, app.status)
    }

    @Test
    fun `unconfigured scoped app remains visible as not configured`() {
        val result =
            buildHomeScopedApps(
                scopedPackages = setOf("com.example.rawscope"),
                installedApps =
                    listOf(
                        InstalledApp(
                            packageName = "com.example.rawscope",
                            label = "Raw Scope App",
                            isSystemApp = false,
                            versionName = "3.0",
                        )
                    ),
                appConfigs = emptyMap(),
                groups = emptyList(),
            )

        val app = result.single()
        assertFalse(app.isAssigned)
        assertTrue(app.isAppEnabled)
        assertNull(app.groupName)
        assertFalse(app.canSpoof)
        assertEquals(HomeScopedAppStatus.NOT_CONFIGURED, app.status)
    }

    @Test
    fun `group disabled makes scoped app visible but not spoofable`() {
        val group = SpoofGroup.createNew(name = "Paused", isDefault = true).copy(isEnabled = false)
        val result =
            buildHomeScopedApps(
                scopedPackages = setOf("com.example.groupdisabled"),
                installedApps =
                    listOf(
                        InstalledApp(
                            packageName = "com.example.groupdisabled",
                            label = "Group Disabled App",
                            isSystemApp = false,
                            versionName = "4.0",
                        )
                    ),
                appConfigs =
                    mapOf(
                        "com.example.groupdisabled" to
                            AppConfig(
                                packageName = "com.example.groupdisabled",
                                groupId = group.id,
                                isEnabled = true,
                            )
                    ),
                groups = listOf(group),
            )

        val app = result.single()
        assertTrue(app.isAppEnabled)
        assertTrue(app.isAssigned)
        assertFalse(app.isGroupEnabled)
        assertFalse(app.canSpoof)
        assertEquals(HomeScopedAppStatus.GROUP_DISABLED, app.status)
    }

    @Test
    fun `sorts spoofable apps first then alphabetically`() {
        val group = SpoofGroup.createNew(name = "Research", isDefault = true)
        val result =
            buildHomeScopedApps(
                scopedPackages = setOf("pkg.z", "pkg.a", "pkg.m"),
                installedApps =
                    listOf(
                        InstalledApp("pkg.z", "Zulu", isSystemApp = false),
                        InstalledApp("pkg.a", "Alpha", isSystemApp = false),
                        InstalledApp("pkg.m", "Middle", isSystemApp = false),
                    ),
                appConfigs =
                    mapOf(
                        "pkg.z" to AppConfig("pkg.z", groupId = group.id, isEnabled = true),
                        "pkg.a" to AppConfig("pkg.a", groupId = group.id, isEnabled = false),
                        "pkg.m" to AppConfig("pkg.m", groupId = group.id, isEnabled = true),
                    ),
                groups = listOf(group),
            )

        assertEquals(listOf("pkg.m", "pkg.z", "pkg.a"), result.map { it.packageName })
    }
}
```

- [ ] **Step 2: Run the test and verify it fails because production types/functions do not exist**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --no-daemon
```

Expected:

```text
Compilation error: Unresolved reference 'buildHomeScopedApps'
Compilation error: Unresolved reference 'HomeScopedAppStatus'
```

- [ ] **Step 3: Commit the failing test only if your workflow allows red commits**

If executing with strict green commits, skip this commit and continue to Task 2 before committing.

---

### Task 2: Add the Home scoped app UI model and pure builder

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedApp.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt`

- [ ] **Step 1: Create `HomeScopedApp.kt`**

```kotlin
package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.runtime.Immutable

@Immutable
data class HomeScopedApp(
    val packageName: String,
    val label: String,
    val groupName: String?,
    val isAssigned: Boolean,
    val isAppEnabled: Boolean,
    val isGroupEnabled: Boolean,
    val canSpoof: Boolean,
    val status: HomeScopedAppStatus,
)

enum class HomeScopedAppStatus {
    READY,
    DISABLED,
    GROUP_DISABLED,
    NOT_CONFIGURED,
}
```

- [ ] **Step 2: Create `HomeScopedAppsBuilder.kt`**

```kotlin
package com.astrixforge.devicemasker.ui.screens.home

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.models.InstalledApp
import com.astrixforge.devicemasker.data.models.SpoofGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val BASE_SCOPE_PACKAGES = setOf("android", "system")

internal fun buildHomeScopedApps(
    scopedPackages: Set<String>,
    installedApps: List<InstalledApp>,
    appConfigs: Map<String, AppConfig>,
    groups: List<SpoofGroup>,
): ImmutableList<HomeScopedApp> {
    val installedByPackage = installedApps.associateBy { it.packageName }
    val groupsById = groups.associateBy { it.id }

    return scopedPackages
        .asSequence()
        .filterNot { packageName -> packageName in BASE_SCOPE_PACKAGES }
        .mapNotNull { packageName ->
            val installedApp = installedByPackage[packageName] ?: return@mapNotNull null
            if (installedApp.isSystemApp) return@mapNotNull null

            val appConfig = appConfigs[packageName]
            val group = appConfig?.groupId?.let { groupId -> groupsById[groupId] }
            val isAssigned = appConfig?.groupId != null
            val isAppEnabled = appConfig?.isEnabled ?: true
            val isGroupEnabled = group?.isEnabled ?: false
            val canSpoof = isAssigned && isAppEnabled && isGroupEnabled
            HomeScopedApp(
                packageName = packageName,
                label = installedApp.label,
                groupName = group?.name,
                isAssigned = isAssigned,
                isAppEnabled = isAppEnabled,
                isGroupEnabled = isGroupEnabled,
                canSpoof = canSpoof,
                status =
                    when {
                        !isAssigned -> HomeScopedAppStatus.NOT_CONFIGURED
                        !isAppEnabled -> HomeScopedAppStatus.DISABLED
                        !isGroupEnabled -> HomeScopedAppStatus.GROUP_DISABLED
                        else -> HomeScopedAppStatus.READY
                    },
            )
        }
        .sortedWith(
            compareByDescending<HomeScopedApp> { it.canSpoof }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )
        .toList()
        .toImmutableList()
}
```

- [ ] **Step 3: Modify `HomeState.kt`**

Replace the current file content with:

```kotlin
package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.data.models.SpoofGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * UI state for the Home screen.
 *
 * Represents all the state needed to render the home screen, collected from the repository flows by
 * the ViewModel.
 */
@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val isXposedActive: Boolean = false,
    val isModuleEnabled: Boolean = false,
    val isScopeAvailable: Boolean = false,
    val groups: ImmutableList<SpoofGroup> = persistentListOf(),
    val appConfigs: ImmutableMap<String, AppConfig> = persistentMapOf(),
    val selectedGroup: SpoofGroup? = null,
    val enabledAppsCount: Int = 0,
    val maskedIdentifiersCount: Int = 0,
    val scopedApps: ImmutableList<HomeScopedApp> = persistentListOf(),
)
```

- [ ] **Step 4: Run the builder tests and verify they pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedApp.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilderTest.kt
git commit -m "test(home): define scoped app state builder"
```

---

### Task 3: Expose LSPosed scope state from `XposedPrefs`

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedScopeState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`

- [ ] **Step 1: Create `XposedScopeState.kt`**

```kotlin
package com.astrixforge.devicemasker.data

import androidx.compose.runtime.Immutable

@Immutable
data class XposedScopeState(
    val isAvailable: Boolean = false,
    val packages: Set<String> = emptySet(),
)
```

- [ ] **Step 2: Modify `XposedPrefs.kt` imports**

Add:

```kotlin
import kotlinx.coroutines.flow.update
```

- [ ] **Step 3: Add scope state properties in `XposedPrefs`**

Inside `object XposedPrefs`, near `serviceConnectedState`, add:

```kotlin
internal val scopeStateFlow = MutableStateFlow(XposedScopeState())
val scopeState: StateFlow<XposedScopeState> = scopeStateFlow.asStateFlow()
```

- [ ] **Step 4: Reset scope state in `reset()`**

Update `reset()`:

```kotlin
internal fun reset() {
    xposedService = null
    initialized = false
    serviceConnectedState.value = false
    scopeStateFlow.value = XposedScopeState()
    serviceBindCallbacks.clear()
}
```

- [ ] **Step 5: Refresh scope on service bind and clear it on service death**

Update `onServiceBind`:

```kotlin
override fun onServiceBind(service: XposedService) {
    xposedService = service
    serviceConnectedState.value = true
    refreshScope()
    Timber.tag(TAG).i("XposedService connected (%s)", service.frameworkName)
    serviceBindCallbacks.forEach { callback ->
        runCatching(callback).onFailure { e ->
            Timber.tag(TAG).w(e, "XposedService bind callback failed")
        }
    }
}
```

Update `onServiceDied`:

```kotlin
override fun onServiceDied(service: XposedService) {
    xposedService = null
    serviceConnectedState.value = false
    scopeStateFlow.value = XposedScopeState()
    Timber.tag(TAG).w("XposedService died")
}
```

- [ ] **Step 6: Add a public refresh function**

Add below `isConnected()`:

```kotlin
fun refreshScope() {
    val service = xposedService
    if (service == null) {
        scopeStateFlow.value = XposedScopeState()
        return
    }

    runCatching { service.scope.toSet() }
        .onSuccess { packages ->
            scopeStateFlow.value = XposedScopeState(isAvailable = true, packages = packages)
        }
        .onFailure { e ->
            scopeStateFlow.update { current -> current.copy(isAvailable = false) }
            Timber.tag(TAG).w(e, "Failed to read LSPosed scope")
        }
}
```

- [ ] **Step 7: Run targeted tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedScopeState.kt app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt
git commit -m "feat(app): expose lsposed scope state"
```

---

### Task 4: Add app-level enablement repository API

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/ISpoofRepository.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeSpoofRepository.kt`

- [ ] **Step 1: Add method to `SpoofAppAssignmentRepository`**

In `ISpoofRepository.kt`, change the interface block to:

```kotlin
interface SpoofAppAssignmentRepository {
    suspend fun addAppToGroup(groupId: String, packageName: String)

    suspend fun removeAppFromGroup(groupId: String, packageName: String)

    suspend fun setAppEnabled(packageName: String, enabled: Boolean)

    suspend fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean)

    suspend fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean)
}
```

- [ ] **Step 2: Implement in `SpoofRepository.kt`**

Add after `removeAppFromGroup()`:

```kotlin
override suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
    configManager.setAppEnabled(packageName, enabled)
}
```

- [ ] **Step 3: Implement in `FakeSpoofRepository.kt`**

Add after `removeAppFromGroup()`:

```kotlin
override suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
    _appConfigs.update { configs ->
        configs +
            (packageName to
                (configs[packageName] ?: AppConfig(packageName = packageName))
                    .copy(isEnabled = enabled))
    }
}
```

- [ ] **Step 4: Run repository-related tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/ISpoofRepository.kt app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeSpoofRepository.kt
git commit -m "feat(app): add app enablement repository action"
```

---

### Task 5: Wire scoped apps into `HomeViewModel`

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTest.kt`

- [ ] **Step 1: Update `HomeViewModel` constructor**

Change constructor parameters to:

```kotlin
class HomeViewModel(
    private val repository: ISpoofRepository,
    private val isXposedActiveFlow: StateFlow<Boolean> = XposedPrefs.isServiceConnected,
    private val xposedScopeStateFlow: StateFlow<XposedScopeState> = XposedPrefs.scopeState,
    @Suppress("unused") private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
```

Add import:

```kotlin
import com.astrixforge.devicemasker.data.XposedScopeState
```

- [ ] **Step 2: Replace the combined flow internals**

Replace the `init` combine block with:

```kotlin
init {
    viewModelScope.launch {
        combine(
                isXposedActiveFlow,
                repository.moduleEnabled,
                xposedScopeStateFlow,
                combine(
                    repository.groups,
                    repository.activeGroup,
                    repository.appConfigs,
                    repository.appScopeRepository.installedApps,
                ) { groups, activeGroup, appConfigs, installedApps ->
                    GroupFlows(groups, activeGroup, appConfigs, installedApps)
                },
            ) { connected, moduleEnabled, scopeState, inner ->
                val selectedGroup =
                    inner.activeGroup ?: inner.groups.find { it.isDefault } ?: inner.groups.firstOrNull()
                HomeState(
                    isXposedActive = connected,
                    isModuleEnabled = moduleEnabled,
                    isScopeAvailable = scopeState.isAvailable,
                    groups = inner.groups.toImmutableList(),
                    appConfigs = inner.appConfigs.toImmutableMap(),
                    selectedGroup = selectedGroup,
                    maskedIdentifiersCount = selectedGroup?.enabledCount() ?: 0,
                    enabledAppsCount =
                        if (selectedGroup?.isEnabled == true) {
                            inner.appConfigs.countEnabledAssignedToGroup(selectedGroup.id)
                        } else {
                            0
                        },
                    scopedApps =
                        buildHomeScopedApps(
                            scopedPackages = scopeState.packages,
                            installedApps = inner.installedApps,
                            appConfigs = inner.appConfigs,
                            groups = inner.groups,
                        ),
                    isLoading = false,
                )
            }
            .collect { homeState -> _state.value = homeState }
    }
}
```

- [ ] **Step 3: Add scoped-app enable action**

Add before `regenerateAll()`:

```kotlin
fun setScopedAppEnabled(packageName: String, enabled: Boolean) {
    viewModelScope.launch { repository.setAppEnabled(packageName, enabled) }
}
```

- [ ] **Step 4: Rename count helper**

Replace:

```kotlin
private fun Map<String, AppConfig>.countAssignedToGroup(groupId: String): Int =
    values.count { it.groupId == groupId && it.isEnabled }
```

with:

```kotlin
private fun Map<String, AppConfig>.countEnabledAssignedToGroup(groupId: String): Int =
    values.count { it.groupId == groupId && it.isEnabled }
```

Update both call sites to `countEnabledAssignedToGroup`.

- [ ] **Step 5: Remove old targeted-app code**

Delete from `HomeViewModel.kt`:

```kotlin
private val DEFAULT_SCOPE_PACKAGES = setOf("android", "system")

private fun computeTargetedInstalledApps(...)
```

Keep `GroupFlows`.

- [ ] **Step 6: Add HomeViewModel scoped app tests**

Append to `HomeViewModelTest.kt`:

```kotlin
@Test
fun `scopedApps are built from LSPosed scope not appConfigs alone`() = runTest {
    val group = SpoofGroup.createNew(name = "Research", isDefault = true)
    val appScopeRepository =
        com.astrixforge.devicemasker.testing.FakeAppScopeRepository(
            initialApps =
                listOf(
                    com.astrixforge.devicemasker.data.models.InstalledApp(
                        packageName = "com.example.scoped",
                        label = "Scoped",
                        isSystemApp = false,
                    ),
                    com.astrixforge.devicemasker.data.models.InstalledApp(
                        packageName = "com.example.configured",
                        label = "Configured",
                        isSystemApp = false,
                    ),
                )
        )
    val repository =
        FakeSpoofRepository(
            initialGroups = listOf(group),
            initialAppConfigs =
                mapOf(
                    "com.example.configured" to
                        AppConfig(
                            packageName = "com.example.configured",
                            groupId = group.id,
                            isEnabled = true,
                        ),
                    "com.example.scoped" to
                        AppConfig(
                            packageName = "com.example.scoped",
                            groupId = group.id,
                            isEnabled = true,
                        ),
                ),
            appScopeRepository = appScopeRepository,
        )
    val scopeFlow =
        MutableStateFlow(
            com.astrixforge.devicemasker.data.XposedScopeState(
                isAvailable = true,
                packages = setOf("com.example.scoped"),
            )
        )
    val viewModel =
        HomeViewModel(
            repository = repository,
            xposedScopeStateFlow = scopeFlow,
        )

    advanceUntilIdle()

    assertEquals(listOf("com.example.scoped"), viewModel.state.value.scopedApps.map { it.packageName })
}

@Test
fun `setScopedAppEnabled updates app config enablement`() = runTest {
    val group = SpoofGroup.createNew(name = "Research", isDefault = true)
    val appScopeRepository =
        com.astrixforge.devicemasker.testing.FakeAppScopeRepository(
            initialApps =
                listOf(
                    com.astrixforge.devicemasker.data.models.InstalledApp(
                        packageName = "com.example.app",
                        label = "Example",
                        isSystemApp = false,
                    )
                )
        )
    val repository =
        FakeSpoofRepository(
            initialGroups = listOf(group),
            initialAppConfigs =
                mapOf(
                    "com.example.app" to
                        AppConfig(
                            packageName = "com.example.app",
                            groupId = group.id,
                            isEnabled = true,
                        )
                ),
            appScopeRepository = appScopeRepository,
        )
    val scopeFlow =
        MutableStateFlow(
            com.astrixforge.devicemasker.data.XposedScopeState(
                isAvailable = true,
                packages = setOf("com.example.app"),
            )
        )
    val viewModel =
        HomeViewModel(
            repository = repository,
            xposedScopeStateFlow = scopeFlow,
        )

    viewModel.setScopedAppEnabled("com.example.app", false)
    advanceUntilIdle()

    assertFalse(viewModel.state.value.scopedApps.single().isAppEnabled)
    assertEquals(HomeScopedAppStatus.DISABLED, viewModel.state.value.scopedApps.single().status)
}
```

- [ ] **Step 7: Run Home tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTest.kt
git commit -m "feat(home): derive apps from lsposed scope"
```

---

### Task 6: Replace Home section UI with app cards and switches

**Files:**
- Delete: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Delete the old display-only section**

Delete:

```text
app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt
```

- [ ] **Step 2: Create `HomeScopedAppsSection.kt`**

```kotlin
package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.components.EmptyState
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveCard
import com.astrixforge.devicemasker.ui.components.expressive.ExpressiveSwitch
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScopedAppsSection(
    scopedApps: ImmutableList<HomeScopedApp>,
    isScopeAvailable: Boolean,
    onAppEnabledChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.home_scoped_apps_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                if (isScopeAvailable) {
                    stringResource(R.string.home_scoped_apps_subtitle)
                } else {
                    stringResource(R.string.home_scoped_apps_scope_unavailable)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (scopedApps.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Apps,
                title = stringResource(R.string.home_scoped_apps_empty),
                subtitle = stringResource(R.string.home_scoped_apps_empty_subtitle),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scopedApps.forEach { app ->
                    HomeScopedAppCard(
                        app = app,
                        onEnabledChange = { enabled -> onAppEnabledChange(app.packageName, enabled) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScopedAppCard(
    app: HomeScopedApp,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateDescription =
        if (app.isAppEnabled) {
            stringResource(R.string.home_scoped_app_enabled_state)
        } else {
            stringResource(R.string.home_scoped_app_disabled_state)
        }

    ExpressiveCard(
        onClick = { onEnabledChange(!app.isAppEnabled) },
        modifier =
            modifier
                .alpha(if (app.status == HomeScopedAppStatus.DISABLED) DISABLED_ALPHA else 1f)
                .semantics {
                    role = Role.Switch
                    this.stateDescription = stateDescription
                },
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScopedAppIcon(packageName = app.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.supportingText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.statusText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = app.statusColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ExpressiveSwitch(checked = app.isAppEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun HomeScopedApp.supportingText(): String =
    groupName ?: stringResource(R.string.home_scoped_app_no_group)

@Composable
private fun HomeScopedApp.statusText(): String =
    when (status) {
        HomeScopedAppStatus.READY -> stringResource(R.string.home_scoped_app_ready)
        HomeScopedAppStatus.DISABLED -> stringResource(R.string.home_scoped_app_disabled)
        HomeScopedAppStatus.GROUP_DISABLED -> stringResource(R.string.home_scoped_app_group_disabled)
        HomeScopedAppStatus.NOT_CONFIGURED -> stringResource(R.string.home_scoped_app_not_configured)
    }

@Composable
private fun HomeScopedApp.statusColor() =
    when (status) {
        HomeScopedAppStatus.READY -> MaterialTheme.colorScheme.primary
        HomeScopedAppStatus.DISABLED,
        HomeScopedAppStatus.GROUP_DISABLED,
        HomeScopedAppStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.error
    }

@Composable
private fun ScopedAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, packageName) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.packageManager
                                .getApplicationIcon(packageName)
                                .toBitmap(width = APP_ICON_SIZE_PX, height = APP_ICON_SIZE_PX)
                                .asImageBitmap()
                        }
                        .getOrNull()
                }
        }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier =
                modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private const val APP_ICON_SIZE_PX = 80
private const val DISABLED_ALPHA = 0.72f

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HomeScopedAppsSectionPreview() {
    DeviceMaskerTheme {
        HomeScopedAppsSection(
            isScopeAvailable = true,
            scopedApps =
                persistentListOf(
                    HomeScopedApp(
                        packageName = "com.example.ready",
                        label = "Ready App",
                        groupName = "Research",
                        isAssigned = true,
                        isAppEnabled = true,
                        isGroupEnabled = true,
                        canSpoof = true,
                        status = HomeScopedAppStatus.READY,
                    ),
                    HomeScopedApp(
                        packageName = "com.example.disabled",
                        label = "Disabled App",
                        groupName = "Research",
                        isAssigned = true,
                        isAppEnabled = false,
                        isGroupEnabled = true,
                        canSpoof = false,
                        status = HomeScopedAppStatus.DISABLED,
                    ),
                ),
            onAppEnabledChange = { _, _ -> },
        )
    }
}
```

- [ ] **Step 3: Update `strings.xml`**

Add near the Home strings:

```xml
<string name="home_scoped_apps_title">LSPosed Scoped Apps</string>
<string name="home_scoped_apps_subtitle">Apps currently selected in LSPosed scope. Switch controls Device Masker spoof eligibility.</string>
<string name="home_scoped_apps_scope_unavailable">LSPosed scope is unavailable. Open LSPosed or enable the module to refresh this list.</string>
<string name="home_scoped_apps_empty">No scoped user apps found</string>
<string name="home_scoped_apps_empty_subtitle">Select target apps in LSPosed scope to list them here.</string>
<string name="home_scoped_app_enabled_state">Spoofing enabled for this app</string>
<string name="home_scoped_app_disabled_state">Spoofing disabled for this app</string>
<string name="home_scoped_app_no_group">No Device Masker group assigned</string>
<string name="home_scoped_app_ready">Ready after target app restart</string>
<string name="home_scoped_app_disabled">Disabled here. Enable before spoofing.</string>
<string name="home_scoped_app_group_disabled">Assigned group is disabled</string>
<string name="home_scoped_app_not_configured">Scoped in LSPosed, not assigned in Device Masker</string>
```

Remove or stop using:

```xml
<string name="home_targeted_apps">Targeted Apps</string>
<string name="home_targeted_apps_empty">No apps targeted yet. Assign apps in a spoof group to see them here.</string>
```

- [ ] **Step 4: Update `HomeScreen.kt` parameters**

Replace `targetedInstalledApps` references with `scopedApps` and `isScopeAvailable`.

In `HomeScreen()` call:

```kotlin
scopedApps = state.scopedApps,
isScopeAvailable = state.isScopeAvailable,
onScopedAppEnabledChange = viewModel::setScopedAppEnabled,
```

In `HomeScreenContent` signature:

```kotlin
scopedApps: ImmutableList<HomeScopedApp> = persistentListOf(),
isScopeAvailable: Boolean = false,
onScopedAppEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
```

Replace the old section call with:

```kotlin
HomeScopedAppsSection(
    scopedApps = scopedApps,
    isScopeAvailable = isScopeAvailable,
    onAppEnabledChange = onScopedAppEnabledChange,
    modifier = Modifier.fillMaxWidth(),
)
```

- [ ] **Step 5: Update previews**

In `HomeScreenContentPreview`, pass:

```kotlin
isScopeAvailable = true,
scopedApps =
    persistentListOf(
        HomeScopedApp(
            packageName = "com.example.ready",
            label = "Ready App",
            groupName = "Research",
            isAssigned = true,
            isAppEnabled = true,
            isGroupEnabled = true,
            canSpoof = true,
            status = HomeScopedAppStatus.READY,
        )
    ),
onScopedAppEnabledChange = { _, _ -> },
```

- [ ] **Step 6: Run Home tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.* --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home app/src/main/res/values/strings.xml
git rm app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt
git commit -m "feat(home): show lsposed scoped app cards"
```

---

### Task 7: Keep disabled apps visible in group app lists

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt`

- [ ] **Step 1: Update `AppListItem` signature**

Change:

```kotlin
fun AppListItem(
    app: InstalledApp,
    isAssigned: Boolean,
    assignedToOtherGroupName: String?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
```

to:

```kotlin
fun AppListItem(
    app: InstalledApp,
    isAssigned: Boolean,
    assignedToOtherGroupName: String?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAppEnabled: Boolean = true,
)
```

- [ ] **Step 2: Pass enablement into content**

Update `AppListItemContent` call:

```kotlin
AppListItemContent(
    app = app,
    isAssigned = isAssigned,
    assignedToOtherGroupName = assignedToOtherGroupName,
    isAppEnabled = isAppEnabled,
    toggleRequested = onToggle,
)
```

Update `AppListItemContent` signature:

```kotlin
private fun AppListItemContent(
    app: InstalledApp,
    isAssigned: Boolean,
    assignedToOtherGroupName: String?,
    isAppEnabled: Boolean,
    toggleRequested: (Boolean) -> Unit,
)
```

- [ ] **Step 3: Show disabled app subtitle**

Update `AppDetails` signature:

```kotlin
private fun AppDetails(
    app: InstalledApp,
    assignedToOtherGroupName: String?,
    isAppEnabled: Boolean,
    modifier: Modifier = Modifier,
)
```

Call it with `isAppEnabled = isAppEnabled`.

Replace `appSubtitle()` with:

```kotlin
@Composable
private fun appSubtitle(
    packageName: String,
    assignedToOtherGroupName: String?,
    isAppEnabled: Boolean,
): String =
    when {
        assignedToOtherGroupName != null ->
            stringResource(id = R.string.group_spoofing_assigned_to, assignedToOtherGroupName)
        !isAppEnabled -> stringResource(id = R.string.group_spoofing_app_disabled_from_home)
        else -> packageName
    }
```

Set color:

```kotlin
color =
    if (isDisabled || !isAppEnabled) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
```

- [ ] **Step 4: Add string**

In `strings.xml`:

```xml
<string name="group_spoofing_app_disabled_from_home">Disabled from Home. Enable to spoof.</string>
```

- [ ] **Step 5: Split predicates in `AppsTabContent.kt`**

Replace helper functions at the bottom:

```kotlin
private fun Map<String, AppConfig>.isAssignedToGroup(
    packageName: String,
    groupId: String?,
): Boolean = groupId != null && this[packageName]?.groupId == groupId

private fun Map<String, AppConfig>.isAppEnabled(packageName: String): Boolean =
    this[packageName]?.isEnabled ?: true
```

Keep `countAssignedToGroup()` counting enabled assigned apps unless UI copy is changed:

```kotlin
private fun Map<String, AppConfig>.countAssignedToGroup(groupId: String?): Int =
    if (groupId == null) {
        0
    } else {
        values.count { it.groupId == groupId && it.isEnabled }
    }
```

- [ ] **Step 6: Pass enablement to `AppListItem`**

Update item call:

```kotlin
AppListItem(
    app = app,
    isAssigned = appConfigs.isAssignedToGroup(app.packageName, group?.id),
    assignedToOtherGroupName = app.assignedGroupName(group, allGroups, appConfigs),
    isAppEnabled = appConfigs.isAppEnabled(app.packageName),
    onToggle = { checked -> onAppToggle(app, checked) },
    modifier = Modifier.fillMaxWidth().animateItem(),
)
```

- [ ] **Step 7: Run app tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt app/src/main/res/values/strings.xml
git commit -m "fix(groups): keep disabled assigned apps visible"
```

---

### Task 8: Remove stale targeted-app names and verify static quality

**Files:**
- Modify: any files still referencing `TargetedApp`, `TargetedAppsSection`, or `targetedInstalledApps`.

- [ ] **Step 1: Search for stale names**

Run:

```powershell
rg -n "TargetedApp|TargetedAppsSection|targetedInstalledApps|home_targeted_apps" app/src/main app/src/test
```

Expected:

```text
No matches
```

- [ ] **Step 2: Run formatting and static analysis**

Run:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Run focused unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit**

```powershell
git add app/src/main app/src/test
git commit -m "chore(home): remove stale targeted app implementation"
```

---

### Task 9: Build and manual validation

**Files:**
- No source changes expected.

- [ ] **Step 1: Run debug build**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Install and launch debug APK**

Run:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.astrixforge.devicemasker
adb shell monkey -p com.astrixforge.devicemasker -c android.intent.category.LAUNCHER 1
```

Expected:

```text
Events injected: 1
```

- [ ] **Step 3: Visual check with Mobile MCP**

Use Mobile MCP to verify:

```text
1. Home opens.
2. LSPosed Scoped Apps section appears below Quick Actions.
3. Empty state appears if LSPosed scope has no user apps or scope is unavailable.
4. Scoped user apps render as cards.
5. Card switches are visible and 48dp touch targets.
6. Long app labels/package names truncate without overlap.
```

- [ ] **Step 4: Runtime off/on behavior check**

With a known LSPosed-scoped target app:

```powershell
adb logcat -c
```

In Device Masker Home:

```text
Switch the scoped target app off.
```

Then run:

```powershell
adb shell am force-stop <target.package>
adb shell monkey -p <target.package> -c android.intent.category.LAUNCHER 1
adb logcat -d -t 1200 | Select-String "DeviceMasker|XposedEntry|All hooks registered|Spoof event"
```

Expected:

```text
XposedEntry may load for the process if LSPosed scope is present.
No "Target package selected" for the disabled package.
No "All hooks registered" for the disabled package.
No "Spoof event" for the disabled package.
```

Switch the app on, force-stop/relaunch again.

Expected:

```text
Target package selected: <target.package>
All hooks registered for: <target.package>
Spoof event lines appear when target reads hooked values.
```

- [ ] **Step 5: Save runtime evidence under logs**

If runtime validation is performed, save logs under:

```text
logs/device/2026-05-15-home-scoped-apps/
```

Use names:

```text
disabled-logcat.txt
enabled-logcat.txt
```

- [ ] **Step 6: Final verification gate**

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit validation-only docs/log references only if files were intentionally added**

If no files changed, do not commit.

---

## Self-Review

**Spec coverage:**
- Shows LSPosed scoped app list: Tasks 3, 5, 6.
- Does not use spoofing groups as the list source: Task 5 builder uses `scopeState.packages`; `appConfigs` only enriches status.
- Per-app cards: Task 6.
- Per-app global switch: Tasks 4, 5, 6.
- Disabled app prevents spoofing: existing `ConfigSync`/`XposedEntry` gates preserved; runtime check in Task 9.
- Disabled app remains visible in group apps as disabled: Task 7.
- Report-only/current plan request: this file is a plan; no source changes are made by writing it.

**Placeholder scan:**
- No `TBD`.
- No "implement later."
- No "add appropriate error handling."
- Each code-changing task has concrete code snippets.

**Type consistency:**
- `HomeScopedApp`, `HomeScopedAppStatus`, `buildHomeScopedApps`, `scopedApps`, and `setScopedAppEnabled` are defined before later use.
- `XposedScopeState` is defined before ViewModel wiring.
- `setAppEnabled` is added to the repository interface, production implementation, and fake.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-15-home-lsposed-scoped-apps-section.md`. Two execution options:

**1. Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
