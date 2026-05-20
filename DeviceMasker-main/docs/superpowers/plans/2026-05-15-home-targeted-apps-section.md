# Targeted Apps Section on Home Screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Targeted Apps" section below Quick Actions on the home screen that shows only the apps assigned to LSPosed targeting via `JsonConfig.appConfigs`, cross-referenced with actually installed apps on the device.

**Architecture:** `HomeViewModel` already collects `repository.appConfigs` and `repository.groups`. To show targeted apps with device-installed metadata (label, system flag), it must also collect `repository.appScopeRepository.installedApps`. The section joins `appConfigs` with `installedApps` to show only apps that are both configured AND installed. App icons are resolved at the UI layer via `PackageManager` (the `InstalledApp` model has no icon field).

**Tech Stack:** Kotlin 2.3.0, Android app module, Jetpack Compose, Material 3 Expressive, JVM/Robolectric tests.

---

## Current Findings

1. **Home screen sections (top to bottom):** StatusCard, QuickStats row, GroupSelectorCard, QuickActionsSection.
2. **HomeState** has `appConfigs: ImmutableMap<String, AppConfig>` and `selectedGroup: SpoofGroup?` but does NOT have `installedApps`.
3. **HomeViewModel** combines 5 flows: `isXposedActiveFlow`, `moduleEnabled`, `groups`, `activeGroup`, `appConfigs`. It does NOT collect `installedApps`.
4. **`AppConfig`** (canonical model) has `packageName`, `groupId`, `isEnabled`, `riskyHooksEnabled`, `classLookupHidingEnabled`.
5. **`InstalledApp`** has `packageName`, `label`, `isSystemApp`, `versionName` — no icon.
6. **No existing method** joins `appConfigs` with `installedApps` at the repository layer. The join happens only in `AppsTabContent.kt` at the UI layer.
7. **`enabledAppsCount`** in `HomeViewModel` counts `appConfigs` entries matching the selected group where `isEnabled == true`, without checking if the app is actually installed.
8. **`AppScopeRepository.installedApps`** is a `StateFlow<List<InstalledApp>>` loaded via `PackageManager.getInstalledApplications()`.
9. **`SpoofRepository`** exposes `appScopeRepository` as a property (via `SpoofStateRepository` interface).
10. **Existing `AppListItem`** composable shows app icon, name, package, assignment state — reusable but designed for the assignment toggle use case (has `onToggle`).
11. **`QuickActionGroup`** and `QuickActionRow` use M3 `ButtonGroup` with `clickableItem()` for coordinated press animation.

## Google Developer Docs Checked

- `PackageManager.getApplicationIcon(ApplicationInfo)` resolves icons on the fly — no caching needed for small lists.
- `LazyColumn` with `key` parameter is the correct pattern for performant lists in Compose.
- `derivedStateOf` should be used for expensive filtering/mapping from state.

Relevant official docs:

- `https://developer.android.com/reference/android/content/pm/PackageManager#getApplicationIcon(android.content.pm.ApplicationInfo)`
- `https://developer.android.com/develop/ui/compose/lists`

## File Structure

Create:

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt`
  - New composable: section header + lazy list of targeted app items.
  - Uses `AppIcon` resolution via `PackageManager` at the composable level.

- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTargetedAppsTest.kt`
  - Tests for the targeted apps filtering logic in `HomeViewModel`.

Modify:

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt`
  - Add `targetedInstalledApps: ImmutableList<TargetedApp>` to `HomeState`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`
  - Add `appScopeRepository.installedApps` to the combined flow.
  - Compute `targetedInstalledApps` by joining `appConfigs` with `installedApps`.
  - Expose via `HomeState`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`
  - Add `TargetedAppsSection` below `QuickActionsSection` in `HomeScreenContent`.
  - Pass `targetedInstalledApps` from state to the section composable.

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/ISpoofRepository.kt`
  - Expose `appScopeRepository` (already exposed via `SpoofStateRepository` — verify it is accessible from `HomeViewModel`).

- `app/src/main/res/values/strings.xml`
  - Add strings for section title, empty state, app count format.

Do not modify:

- `:xposed` module — no hook changes.
- `ConfigManager` / `ConfigSync` — no config flow changes.
- `AppScopeRepository` — already provides what we need.
- `AppConfig` / `InstalledApp` models — no schema changes.

---

### Task 1: Add TargetedApp Model and HomeState Field

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt` (data class only, composable comes in Task 3)
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt`

- [ ] **Step 1: Create the TargetedApp data class**

Create a lightweight UI model that holds the joined data:

```kotlin
package com.astrixforge.devicemasker.ui.screens.home

import androidx.compose.runtime.Immutable

/**
 * A targeted app that is both configured in appConfigs AND installed on the device.
 * Used by the home screen's targeted apps section.
 */
@Immutable
data class TargetedApp(
    val packageName: String,
    val label: String,
    val groupName: String,
    val isEnabled: Boolean,
)
```

This is a UI-only model — it does NOT replace `InstalledApp` or `AppConfig`. It holds the minimal fields needed for the home screen display: the resolved app label, which group it belongs to, and whether it is enabled.

- [ ] **Step 2: Add targetedInstalledApps to HomeState**

In `HomeState.kt`, add the new field:

```kotlin
@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val isXposedActive: Boolean = false,
    val isModuleEnabled: Boolean = false,
    val groups: ImmutableList<SpoofGroup> = persistentListOf(),
    val appConfigs: ImmutableMap<String, AppConfig> = persistentMapOf(),
    val selectedGroup: SpoofGroup? = null,
    val enabledAppsCount: Int = 0,
    val maskedIdentifiersCount: Int = 0,
    val targetedInstalledApps: ImmutableList<TargetedApp> = persistentListOf(),
)
```

Add the import for `TargetedApp` and `kotlinx.collections.immutable.persistentListOf`.

- [ ] **Step 3: Run Spotless + Detekt**

```
.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon
```

---

### Task 2: Compute Targeted Apps in HomeViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`

- [ ] **Step 1: Add installedApps to the combined flow**

`HomeViewModel` currently combines 5 flows. Add `repository.appScopeRepository.installedApps` as the 6th flow. The `combine` operator supports up to 5 inputs — use nested `combine` or `Flow.combine` extension.

Current pattern (simplified):
```kotlin
state = combine(
    isXposedActiveFlow,
    repository.moduleEnabled,
    repository.groups,
    repository.activeGroup,
    repository.appConfigs,
) { ... }.stateIn(...)
```

New pattern — nest the inner combine:
```kotlin
state = combine(
    isXposedActiveFlow,
    repository.moduleEnabled,
    combine(
        repository.groups,
        repository.activeGroup,
        repository.appConfigs,
        repository.appScopeRepository.installedApps,
    ) { groups, activeGroup, appConfigs, installedApps ->
        Quad(groups, activeGroup, appConfigs, installedApps)
    },
) { isXposedActive, moduleEnabled, inner ->
    val (groups, activeGroup, appConfigs, installedApps) = inner
    // ... build HomeState
}.stateIn(...)
```

Use a private data class or `kotlinx.collections.immutable` helper to avoid the `Quad` naming. A simple private `data class HomeFlows(...)` is clearest.

- [ ] **Step 2: Compute targetedInstalledApps**

Inside the combine lambda, after computing `selectedGroup`:

```kotlin
val targetedInstalledApps = computeTargetedInstalledApps(
    appConfigs = appConfigs,
    installedApps = installedApps,
    groups = groups,
)
```

Add a private helper function:

```kotlin
private fun computeTargetedInstalledApps(
    appConfigs: Map<String, AppConfig>,
    installedApps: List<InstalledApp>,
    groups: Map<String, SpoofGroup>,
): ImmutableList<TargetedApp> {
    val installedMap = installedApps.associateBy { it.packageName }
    return appConfigs.entries
        .mapNotNull { (packageName, config) ->
            val installed = installedMap[packageName] ?: return@mapNotNull null
            val group = config.groupId?.let { groups[it] }
            TargetedApp(
                packageName = packageName,
                label = installed.label,
                groupName = group?.name ?: "",
                isEnabled = config.isEnabled && (group?.isEnabled != false),
            )
        }
        .sortedWith(
            compareByDescending<TargetedApp> { it.isEnabled }
                .thenBy { it.label.lowercase() }
        )
        .toPersistentList()
}
```

Key decisions:
- **Only show installed apps.** If an app is in `appConfigs` but not installed, it is excluded. This matches the user's requirement: "only show that app that are the targeted in lsposed, only that apps" — meaning apps the module will actually hook.
- **Sort:** Enabled apps first, then alphabetical by label.
- **Group name:** Resolved from `SpoofGroup.name` via `config.groupId`. Empty string if no explicit group (app uses default group).

- [ ] **Step 3: Add targetedInstalledApps to HomeState construction**

In the combine lambda where `HomeState(...)` is built, add:
```kotlin
targetedInstalledApps = targetedInstalledApps,
```

- [ ] **Step 4: Run Spotless + Detekt + unit tests**

```
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --no-daemon
```

---

### Task 3: Create TargetedAppsSection Composable

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt` (add composable)

- [ ] **Step 1: Build the section composable**

The section follows the same pattern as `QuickActionsSection`: a title label + content. It shows a compact list of targeted apps with icons, names, and group badges.

```kotlin
@Composable
fun TargetedAppsSection(
    targetedApps: ImmutableList<TargetedApp>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.home_targeted_apps),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (targetedApps.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Apps,
                message = stringResource(R.string.home_targeted_apps_empty),
            )
        } else {
            TargetedAppsList(targetedApps = targetedApps)
        }
    }
}
```

- [ ] **Step 2: Build the app list and item composables**

The list should be compact — these are read-only display items, not toggleable assignment rows. Use a `Column` (not `LazyColumn`) since the list is bounded by the number of targeted apps (typically < 20).

```kotlin
@Composable
private fun TargetedAppsList(
    targetedApps: ImmutableList<TargetedApp>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        targetedApps.forEach { app ->
            TargetedAppItem(app = app)
        }
    }
}

@Composable
private fun TargetedAppItem(
    app: TargetedApp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appIcon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
        }.getOrNull()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appIcon != null) {
            Image(
                painter = rememberDrawablePainter(drawable = appIcon),
                contentDescription = app.label,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Android,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.groupName.isNotEmpty()) {
                Text(
                    text = app.groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        if (!app.isEnabled) {
            Text(
                text = stringResource(R.string.disabled),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
```

Use `coil3.compose.rememberAsyncImagePainter` or `androidx.compose.ui.graphics.painter.BitmapPainter` for icon rendering. If the project already has an `AppIcon` composable or icon utility in `ui/components/`, reuse that instead. Check `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt` for how it resolves icons.

- [ ] **Step 3: Run Spotless + Detekt**

```
.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon
```

---

### Task 4: Wire TargetedAppsSection into HomeScreen

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: Add targetedInstalledApps to HomeScreenContent parameters**

In the `HomeScreenContent` composable signature, add:
```kotlin
targetedInstalledApps: ImmutableList<TargetedApp> = persistentListOf(),
```

- [ ] **Step 2: Place TargetedAppsSection below QuickActionsSection**

In the `LazyColumn` inside `HomeScreenContent`, after the `item(key = "quick_actions")` block, add:

```kotlin
item(key = "targeted_apps") {
    TargetedAppsSection(
        targetedApps = targetedInstalledApps,
    )
}
```

- [ ] **Step 3: Pass targetedInstalledApps from HomeScreen to HomeScreenContent**

In the `HomeScreen` composable (the stateful entry point), where it calls `HomeScreenContent(...)`:
```kotlin
targetedInstalledApps = state.targetedInstalledApps,
```

- [ ] **Step 4: Run Spotless + Detekt + unit tests + build**

```
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest assembleDebug --no-daemon
```

---

### Task 5: Add String Resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add new strings**

```xml
<string name="home_targeted_apps">Targeted Apps</string>
<string name="home_targeted_apps_empty">No apps targeted yet. Assign apps in a spoof group to see them here.</string>
```

- [ ] **Step 2: Run Spotless + Detekt**

```
.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon
```

---

### Task 6: Add Unit Tests for Targeted Apps Filtering

**Files:**
- Create: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTargetedAppsTest.kt`

- [ ] **Step 1: Test targeted apps are computed correctly**

```kotlin
@Test
fun `targetedInstalledApps includes only apps that are both configured and installed`() = runTest {
    // Given: appConfigs has pkgA, pkgB, pkgC
    // installedApps has pkgA, pkgB (pkgC is not installed)
    // When: state is emitted
    // Then: targetedInstalledApps contains pkgA and pkgB only
}
```

- [ ] **Step 2: Test disabled apps are sorted below enabled**

```kotlin
@Test
fun `targetedInstalledApps sorts enabled apps before disabled`() = runTest {
    // Given: pkgA isEnabled=false, pkgB isEnabled=true
    // When: state is emitted
    // Then: targetedInstalledApps = [pkgB, pkgA]
}
```

- [ ] **Step 3: Test empty state when no apps targeted**

```kotlin
@Test
fun `targetedInstalledApps is empty when no apps configured`() = runTest {
    // Given: appConfigs is empty
    // When: state is emitted
    // Then: targetedInstalledApps is empty
}
```

- [ ] **Step 4: Test group name resolution**

```kotlin
@Test
fun `targetedInstalledApps resolves group name from config`() = runTest {
    // Given: pkgA has groupId pointing to "My Group"
    // When: state is emitted
    // Then: targetedInstalledApps[0].groupName == "My Group"
}
```

- [ ] **Step 5: Run all tests**

```
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

---

## Key Implementation Notes

1. **No repository-layer join needed.** The join between `appConfigs` and `installedApps` is computed in `HomeViewModel`'s combine lambda. This matches the existing pattern in `AppsTabContent` and avoids adding a new repository method for a UI-only concern.

2. **Icons are resolved at the UI layer.** `InstalledApp` has no icon field. `TargetedApp` also has no icon field. The `TargetedAppItem` composable resolves icons via `PackageManager.getApplicationIcon(packageName)` with `remember` caching. This is efficient for small lists (< 20 apps).

3. **No LazyColumn for the list.** The targeted apps list is bounded and small. A `Column` with `forEach` is simpler and avoids nested scrollable layout issues inside the home screen's `LazyColumn`.

4. **AppScopeRepository is already accessible.** `SpoofRepository` implements `SpoofStateRepository` which exposes `appScopeRepository: IAppScopeRepository`. `HomeViewModel` receives `ISpoofRepository`, so `repository.appScopeRepository.installedApps` is available without any interface changes.

5. **combine operator limit.** Kotlin's `combine` supports up to 5 flows. Adding `installedApps` as a 6th requires nesting: combine the 4 non-boolean flows into a data class, then combine that with the 2 boolean flows.

6. **Empty state.** When no apps are targeted, show the existing `EmptyState` composable with a message guiding the user to assign apps in a spoof group.

7. **Disabled indicator.** If an app's `isEnabled` is false or its group is disabled, show a small "Disabled" text label in error color. This matches the pattern used in `GroupSelectorCard` for disabled groups.

## Verification

After all tasks:

```
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon
```

Manual verification:
1. Open the app — home screen should show the new "Targeted Apps" section below Quick Actions.
2. If no apps are assigned, the empty state message should appear.
3. Assign apps to a group via GroupSpoofing — they should appear in the targeted apps section.
4. Disable an app or its group — it should show as disabled in the list.
5. Uninstall a targeted app — it should disappear from the list.
