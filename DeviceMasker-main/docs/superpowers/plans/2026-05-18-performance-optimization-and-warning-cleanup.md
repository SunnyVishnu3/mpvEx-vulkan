# Performance Optimization And Warning Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan intentionally has no commit or push steps because the user explicitly requested no commit and no push.

**Goal:** Make Device Masker faster across startup, Home scoped-app loading, Compose list smoothness, config sync, Xposed hot paths, diagnostics export, and warning cleanup without removing required behavior.

**Architecture:** Fix the data shape first: Home should resolve only LSPosed-scoped package metadata, lists should lazy-render and reuse icon work, config sync should write only dirty app/group scopes for narrow user actions, and Xposed hook callbacks should read a process-local config snapshot instead of parsing fallback persona JSON repeatedly. Keep RemotePreferences-first config delivery, existing module boundaries, manual DI, and libxposed `StableHooker` callback safety.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 9.2.1, Gradle 9.5.0, Jetpack Compose BOM 2026.05.00, Material 3 1.5.0-alpha19, Navigation 3, kotlinx.coroutines 1.10.2, kotlinx.serialization 1.11.0, libxposed API 101.0.1, Detekt 2.0.0-alpha.3, Android Lint.

---

## Non-Negotiables

- No commits.
- No push.
- No new app module.
- No benchmark module.
- No baseline-profile module.
- No custom AIDL/Binder config path.
- No generated identifiers inside `:xposed`.
- No direct Kotlin SAM `.intercept { ... }` callbacks in runtime hookers.
- No global `commit()` to `apply()` rewrite; config correctness depends on committed RemotePreferences writes.
- Before editing any function/class/method in an implementation session, run GitNexus impact analysis for that symbol and record the risk in the task notes.
- If GitNexus impact is HIGH or CRITICAL, stop and tell the user before editing that symbol.
- Before final completion, run `mcp__gitnexus__detect_changes(scope = "all", repo = "DeviceMasker")`.

## File Structure

- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/IAppScopeRepository.kt`: add scoped metadata flow and scoped package load API.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt`: implement targeted package metadata loading, cache by package name, and avoid version lookup on the Home scoped path.
- Modify `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeAppScopeRepository.kt`: support scoped metadata in ViewModel tests.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`: stop loading all installed apps on Home init; load only LSPosed-scoped package metadata.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt`: join Home scoped apps against scoped metadata instead of full installed app list.
- Modify `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTest.kt`: verify Home scoped apps appear without full installed-app scan.
- Create `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppIconCache.kt`: shared bounded icon cache for PackageManager icon decode.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt`: replace local icon decode with shared cached icon composable.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt`: use the shared cached icon composable and lazy/bounded rendering.
- Modify `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilderTest.kt`: keep builder behavior covered after scoped metadata change.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`: add package-set and group-scoped sync operations while keeping full sync for init/import/repair.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`: pass sync hints for app toggles, assignment changes, group changes, and full sync cases.
- Modify `app/src/test/kotlin/com/astrixforge/devicemasker/service/ConfigSyncTest.kt`: verify dirty-scope sync writes only affected packages and still updates `enabled_apps`.
- Modify `app/src/test/java/com/astrixforge/devicemasker/data/ConfigSyncSnapshotTest.kt`: verify snapshot remains canonical and full sync stays correct.
- Create `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookConfigSnapshot.kt`: pre-read enabled values/persona once per process config version.
- Modify `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt`: expose snapshot construction helpers without adding app-private JSON reads.
- Modify `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`: add snapshot-based helper overloads.
- Modify hookers under `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/`: use the snapshot in value hot paths.
- Modify `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`: build one snapshot after target selection and coalesce success logging.
- Modify `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelperTest.kt`: preserve current preference fallback behavior.
- Create `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/HookConfigSnapshotTest.kt`: verify persona fallback parses once per snapshot and values are stable.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`: stream JSONL entries line-by-line instead of joining large strings.
- Modify or create `app/src/test/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`: verify large event streaming output.
- Modify `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffects.kt`: delay startup root capture until after first UI frame or gate it behind a cheap post-frame launch.
- Modify `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/*`, `app/src/main/res/mipmap-*/*`, and other lint-reported resources only after verifying each warning is truly unused.
- Update Memory Bank after implementation because runtime behavior and performance architecture change.

---

### Task 0: Preflight And Warning Inventory

**Files:**
- Read: `docs/AGENTS_PROJECT_RULES.md`
- Read: `docs/public/ARCHITECTURE.md`
- Read: `app/AGENTS.md`
- Read: `xposed/AGENTS.md`
- Read: `common/AGENTS.md`
- Read: `.agents/skills/libxposed/SKILL.md`
- Read: `docs/internal/reports/active/audits/2026-05-18/2026-05-18-comprehensive-performance-audit.md`
- Create during execution only if needed: `logs/build/2026-05-18-performance-warning-inventory.txt`

- [ ] **Step 1: Confirm the execution scope**

Record this exact scope in the task notes before code edits:

```text
Scope: performance fixes and warning cleanup only.
No commits.
No push.
No new app module.
No benchmark module.
No baseline-profile module.
```

- [ ] **Step 2: Check repository status**

Run:

```powershell
git status --short
```

Expected:

```text
Only known user/report/plan changes are present. If unexpected source changes appear, stop and ask the user how to proceed.
```

- [ ] **Step 3: Refresh warning inventory**

Run:

```powershell
.\gradlew.bat lint detekt --no-daemon *> logs/build/2026-05-18-performance-warning-inventory.txt
```

Expected:

```text
Gradle completes or reports existing warnings. The warning output is stored under logs/build, not the project root.
```

- [ ] **Step 4: Extract lint warning categories**

Run:

```powershell
Select-String -Path app/build/reports/lint-results-debug.txt -Pattern "Warning: .* \\[(.+)\\]" |
    ForEach-Object {
        if ($_.Line -match "\\[(.+)\\]") { $Matches[1] }
    } |
    Group-Object |
    Sort-Object Count -Descending |
    Format-Table Count,Name
```

Expected:

```text
UnusedResources is the largest category. Any new category with behavior risk gets its own mini-plan before code edits.
```

- [ ] **Step 5: Run GitNexus freshness check**

Run through MCP:

```text
mcp__gitnexus__list_repos()
```

Expected:

```text
DeviceMasker appears. If GitNexus reports stale index, run npx gitnexus analyze --name DeviceMasker before impact checks.
```

---

### Task 1: Scoped Package Metadata Fast Path

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/IAppScopeRepository.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeAppScopeRepository.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilder.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTest.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepositoryTest.kt`

- [ ] **Step 1: Run impact checks**

Run through GitNexus before editing:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "IAppScopeRepository", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "AppScopeRepository", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "HomeViewModel", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "buildHomeScopedApps", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report the blast radius before edits.
```

- [ ] **Step 2: Write failing tests for scoped metadata**

Add tests with this shape to `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTest.kt`:

```kotlin
@Test
fun `home loads only scoped package metadata on init`() = runTest {
    val appScopeRepository =
        FakeAppScopeRepository(
            scopedAppsLoadedFromSystem =
                mapOf(
                    "com.scoped.app" to
                        InstalledApp("com.scoped.app", "Scoped App", isSystemApp = false)
                )
        )
    val repository = FakeSpoofRepository(appScopeRepository = appScopeRepository)
    val scopeState =
        MutableStateFlow<XposedScopeState>(
            XposedScopeState.Connected(setOf("android", "system", "com.scoped.app"))
        )

    val viewModel = HomeViewModel(repository, xposedScopeStateFlow = scopeState)
    advanceUntilIdle()

    assertEquals(0, appScopeRepository.loadAppsCalls)
    assertEquals(setOf("com.scoped.app"), appScopeRepository.lastScopedPackages)
    assertEquals(listOf("Scoped App"), viewModel.state.value.scopedApps.map { it.label })
}
```

Add this test to `app/src/test/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepositoryTest.kt`:

```kotlin
@Test
fun `loadScopedApps resolves only requested packages`() = runTest {
    val packageManager = FakePackageManager(
        applications =
            listOf(
                fakeApplicationInfo("com.scoped.one", "Scoped One"),
                fakeApplicationInfo("com.other.app", "Other App"),
            )
    )
    val context = fakeContext(packageManager)
    val repository = AppScopeRepository(context, packageManager)

    repository.loadScopedApps(setOf("com.scoped.one"), forceRefresh = true)

    assertEquals(setOf("com.scoped.one"), repository.scopedAppMetadata.value.keys)
    assertEquals("Scoped One", repository.scopedAppMetadata.value.getValue("com.scoped.one").label)
    assertEquals(0, packageManager.getInstalledApplicationsCalls)
}
```

- [ ] **Step 3: Run the tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --tests com.astrixforge.devicemasker.data.repository.AppScopeRepositoryTest --no-daemon
```

Expected:

```text
FAIL because scopedAppMetadata and loadScopedApps do not exist yet.
```

- [ ] **Step 4: Extend `IAppScopeRepository`**

Change `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/IAppScopeRepository.kt` to expose scoped metadata:

```kotlin
interface IAppScopeRepository {
    val installedApps: StateFlow<List<InstalledApp>>
    val scopedAppMetadata: StateFlow<Map<String, InstalledApp>>
    val isLoading: StateFlow<Boolean>

    suspend fun loadApps(forceRefresh: Boolean = false)

    suspend fun loadScopedApps(
        packageNames: Set<String>,
        forceRefresh: Boolean = false,
    )

    suspend fun getInstalledApps(
        includeSystem: Boolean = false,
        refreshCache: Boolean = false,
    ): List<InstalledApp>

    fun invalidateCache()
}
```

- [ ] **Step 5: Implement scoped loading in `AppScopeRepository`**

Add the new flow and targeted load. Keep `loadApps()` intact for the Apps tab:

```kotlin
private val _scopedAppMetadata = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())
override val scopedAppMetadata: StateFlow<Map<String, InstalledApp>> =
    _scopedAppMetadata.asStateFlow()

private val scopedCache = mutableMapOf<String, InstalledApp>()

override suspend fun loadScopedApps(packageNames: Set<String>, forceRefresh: Boolean) {
    val userPackages = packageNames.filterNotTo(linkedSetOf()) { it in DEFAULT_SCOPE_PACKAGES }
    cacheMutex.withLock {
        val missing =
            if (forceRefresh) {
                userPackages
            } else {
                userPackages.filterNotTo(linkedSetOf()) { it in scopedCache }
            }
        if (missing.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                missing.forEach { packageName ->
                    resolveInstalledApp(packageName, includeVersion = false)?.let { app ->
                        scopedCache[packageName] = app
                    }
                }
            }
        }
        _scopedAppMetadata.value = userPackages.mapNotNull { pkg -> scopedCache[pkg]?.let { pkg to it } }.toMap()
    }
}

private fun resolveInstalledApp(packageName: String, includeVersion: Boolean): InstalledApp? =
    try {
        createInstalledApp(
            appInfo = packageManager.getApplicationInfo(packageName, 0),
            includeVersion = includeVersion,
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

private companion object {
    val DEFAULT_SCOPE_PACKAGES = setOf("android", "system")
}
```

Refactor `createInstalledApp()` to accept `includeVersion`:

```kotlin
private fun createInstalledApp(
    appInfo: ApplicationInfo,
    includeVersion: Boolean = true,
): InstalledApp? {
    return try {
        val label = packageManager.getApplicationLabel(appInfo).toString()
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val versionName =
            if (includeVersion) {
                runCatching {
                        packageManager.getPackageInfo(appInfo.packageName, 0).versionName ?: ""
                    }
                    .getOrDefault("")
            } else {
                ""
            }

        InstalledApp(
            packageName = appInfo.packageName,
            label = label,
            isSystemApp = isSystem,
            versionName = versionName,
        )
    } catch (_: RuntimeException) {
        null
    }
}
```

- [ ] **Step 6: Update the fake repository**

Add scoped state to `FakeAppScopeRepository`:

```kotlin
private val _scopedAppMetadata = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())
override val scopedAppMetadata: StateFlow<Map<String, InstalledApp>> =
    _scopedAppMetadata.asStateFlow()

var loadScopedAppsCalls = 0
    private set

var lastScopedPackages: Set<String> = emptySet()
    private set

override suspend fun loadScopedApps(packageNames: Set<String>, forceRefresh: Boolean) {
    loadScopedAppsCalls += 1
    lastScopedPackages = packageNames - setOf("android", "system")
    _scopedAppMetadata.value =
        scopedAppsLoadedFromSystem.filterKeys { it in lastScopedPackages }
}
```

- [ ] **Step 7: Wire Home to scoped metadata**

In `HomeViewModel`, remove the init full scan:

```kotlin
init {
    viewModelScope.launch {
        xposedScopeStateFlow.collect { state ->
            val packages =
                when (state) {
                    is XposedScopeState.Connected -> state.packages
                    XposedScopeState.Disconnected,
                    is XposedScopeState.Error -> emptySet()
                }
            repository.appScopeRepository.loadScopedApps(packages, forceRefresh = false)
        }
    }
    viewModelScope.launch {
        combine(
            isXposedActiveFlow,
            repository.moduleEnabled,
            isScopedAppsRefreshing,
            combine(
                repository.groups,
                repository.activeGroup,
                repository.appConfigs,
                repository.appScopeRepository.scopedAppMetadata,
                xposedScopeStateFlow,
            ) { groups, activeGroup, appConfigs, scopedMetadata, xposedScopeState ->
                GroupFlows(groups, activeGroup, appConfigs, scopedMetadata, xposedScopeState)
            },
        ) { connected, moduleEnabled, scopedAppsRefreshing, inner ->
            // Existing HomeState construction, but pass inner.scopedMetadata.
        }.collect { homeState -> _state.value = homeState }
    }
}
```

Change `refreshScopedApps()`:

```kotlin
fun refreshScopedApps() {
    viewModelScope.launch {
        isScopedAppsRefreshing.value = true
        try {
            val packages =
                when (val state = xposedScopeStateFlow.value) {
                    is XposedScopeState.Connected -> state.packages
                    XposedScopeState.Disconnected,
                    is XposedScopeState.Error -> emptySet()
                }
            repository.appScopeRepository.loadScopedApps(packages, forceRefresh = true)
            XposedPrefs.refreshScope()
        } finally {
            isScopedAppsRefreshing.value = false
        }
    }
}
```

- [ ] **Step 8: Update scoped app builder input**

Change `buildHomeScopedApps()` signature:

```kotlin
fun buildHomeScopedApps(
    scopeState: XposedScopeState,
    scopedAppMetadata: Map<String, InstalledApp>,
    appConfigs: Map<String, AppConfig>,
    groups: List<SpoofGroup>,
): ImmutableList<HomeScopedApp>
```

Replace the installed app map with direct scoped metadata:

```kotlin
val groupsById = groups.associateBy { it.id }

return scopePackages
    .asSequence()
    .filterNot { it in DEFAULT_LSPOSED_SCOPE_PACKAGES }
    .mapNotNull { packageName ->
        val installedApp = scopedAppMetadata[packageName] ?: return@mapNotNull null
        val appConfig = appConfigs[packageName]
        val group = appConfig?.groupId?.let(groupsById::get)
        HomeScopedApp(
            packageName = packageName,
            label = installedApp.label,
            groupName = group?.name,
            isGloballyEnabled = appConfig?.isEnabled != false,
            status = appConfig.toHomeScopedAppStatus(group),
        )
    }
    .sortedWith(compareBy<HomeScopedApp> { it.label.lowercase() }.thenBy { it.packageName })
    .toList()
    .toImmutableList()
```

- [ ] **Step 9: Run scoped metadata tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --tests com.astrixforge.devicemasker.data.repository.AppScopeRepositoryTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Home scoped apps load from scoped metadata without full installed-app scan.
```

---

### Task 2: Shared App Icon Cache

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppIconCache.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt`
- Create: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/components/AppIconCacheTest.kt`

- [ ] **Step 1: Run impact checks**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "AppListItem", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "HomeScopedAppsSection", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report the blast radius before edits.
```

- [ ] **Step 2: Add cache unit tests**

Create `AppIconCacheTest.kt` with cache policy tests:

```kotlin
class AppIconCacheTest {
    @Test
    fun `cache returns same bitmap on repeated hit`() {
        val cache = AppIconCache(maxEntries = 2)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()

        cache.putForTest("com.example.app", bitmap)

        assertSame(bitmap, cache.getIfPresentForTest("com.example.app"))
        assertSame(bitmap, cache.getIfPresentForTest("com.example.app"))
    }

    @Test
    fun `cache evicts least recently used entry`() {
        val cache = AppIconCache(maxEntries = 2)
        val first = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()
        val second = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()
        val third = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()

        cache.putForTest("one", first)
        cache.putForTest("two", second)
        cache.getIfPresentForTest("one")
        cache.putForTest("three", third)

        assertSame(first, cache.getIfPresentForTest("one"))
        assertNull(cache.getIfPresentForTest("two"))
        assertSame(third, cache.getIfPresentForTest("three"))
    }
}
```

- [ ] **Step 3: Run the tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.components.AppIconCacheTest --no-daemon
```

Expected:

```text
FAIL because AppIconCache does not exist yet.
```

- [ ] **Step 4: Add `AppIconCache` and reusable composable**

Create `AppIconCache.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppIconCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val cache = LruCache<String, ImageBitmap>(maxEntries)

    suspend fun getIcon(context: Context, packageName: String): ImageBitmap? =
        cache.get(packageName)
            ?: withContext(Dispatchers.IO) {
                runCatching {
                        context.packageManager
                            .getApplicationIcon(packageName)
                            .toBitmap(width = APP_ICON_SIZE_PX, height = APP_ICON_SIZE_PX)
                            .asImageBitmap()
                    }
                    .getOrNull()
                    ?.also { cache.put(packageName, it) }
            }

    fun clear() {
        cache.evictAll()
    }

    internal fun putForTest(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    internal fun getIfPresentForTest(packageName: String): ImageBitmap? = cache.get(packageName)

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 128
        const val APP_ICON_SIZE_PX = 80
    }
}

private val processAppIconCache = AppIconCache()

@Composable
fun CachedAppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    fallback: @Composable (Modifier) -> Unit = { AppIconFallback(it) },
) {
    val context = LocalContext.current
    val iconBitmap by
        produceState<ImageBitmap?>(initialValue = null, packageName) {
            value = processAppIconCache.getIcon(context.applicationContext, packageName)
        }

    if (iconBitmap != null) {
        Image(bitmap = iconBitmap!!, contentDescription = label, modifier = modifier)
    } else {
        fallback(modifier)
    }
}
```

- [ ] **Step 5: Replace duplicate row icon loading**

In `AppListItem.kt`, replace the private `AppIcon()` body with:

```kotlin
@Composable
private fun AppIcon(app: InstalledApp, modifier: Modifier = Modifier) {
    CachedAppIcon(
        packageName = app.packageName,
        label = app.label,
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
    )
}
```

In `HomeScopedAppsSection.kt`, replace `HomeScopedAppIcon()` with:

```kotlin
@Composable
private fun HomeScopedAppIcon(packageName: String, label: String, modifier: Modifier = Modifier) {
    CachedAppIcon(
        packageName = packageName,
        label = label,
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        fallback = { fallbackModifier ->
            HomeScopedAppIconFallback(fallbackModifier)
        },
    )
}
```

Add the fallback helper:

```kotlin
@Composable
private fun HomeScopedAppIconFallback(modifier: Modifier = Modifier) {
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
```

- [ ] **Step 6: Run icon cache tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.components.AppIconCacheTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Icon decode cache behavior is covered.
```

---

### Task 3: Lazy Home Scoped Apps Rendering

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsSection.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScopedAppsBuilderTest.kt`

- [ ] **Step 1: Run impact check**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "HomeScopedAppsSection", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report the blast radius before edits.
```

- [ ] **Step 2: Replace eager `Column.forEach` with bounded `LazyColumn`**

Use a height-bound lazy list so Home can keep its outer scroll without composing every scoped row:

```kotlin
private val MAX_SCOPED_APPS_LIST_HEIGHT = 420.dp

@Composable
private fun HomeScopedAppsList(
    scopedApps: ImmutableList<HomeScopedApp>,
    onAppEnabledChange: (HomeScopedApp, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.heightIn(max = MAX_SCOPED_APPS_LIST_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = scopedApps,
            key = { it.packageName },
            contentType = { "home_scoped_app" },
        ) { app ->
            HomeScopedAppCard(
                app = app,
                onAppEnabledChange = onAppEnabledChange,
                modifier = Modifier.animateItem(),
            )
        }
    }
}
```

Then call it from `HomeScopedAppsSection`:

```kotlin
HomeScopedAppsList(
    scopedApps = scopedApps,
    onAppEnabledChange = onAppEnabledChange,
)
```

- [ ] **Step 3: Compile Home UI**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Home scoped rows are lazy and keyed by package name.
```

---

### Task 4: Dirty-Scope Config Sync

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/service/ConfigSyncTest.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/data/ConfigSyncSnapshotTest.kt`

- [ ] **Step 1: Run impact checks**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "ConfigSync", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "ConfigManager", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report the blast radius before edits.
```

- [ ] **Step 2: Add failing scoped sync tests**

Add to `ConfigSyncTest`:

```kotlin
@Test
fun `sync packages updates enabled app allowlist and only requested package keys`() {
    val prefs = FakeSharedPreferences()
    val group = SpoofGroup.createNew("Scoped").copy(id = "group-scoped")
    val config =
        JsonConfig.createDefault()
            .addOrUpdateGroup(group)
            .setAppConfig(AppConfig("com.example.one", groupId = group.id, isEnabled = true))
            .setAppConfig(AppConfig("com.example.two", groupId = group.id, isEnabled = true))

    ConfigSync.syncPackages(config, setOf("com.example.one"), prefs)

    assertEquals(
        setOf("com.example.one", "com.example.two"),
        prefs.getStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, null),
    )
    assertTrue(prefs.getBoolean(SharedPrefsKeys.getAppEnabledKey("com.example.one"), false))
    assertFalse(
        "syncPackages must not write unrelated package app key",
        prefs.contains(SharedPrefsKeys.getAppEnabledKey("com.example.two")),
    )
}
```

Add to `ConfigSyncSnapshotTest`:

```kotlin
@Test
fun `current enabled apps includes all app config packages during dirty sync`() {
    val config =
        JsonConfig.createDefault()
            .setAppConfig(AppConfig("com.example.one", groupId = null, isEnabled = false))
            .setAppConfig(AppConfig("com.example.two", groupId = null, isEnabled = false))

    assertEquals(setOf("com.example.one", "com.example.two"), config.appConfigs.keys)
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.ConfigSyncTest --tests com.astrixforge.devicemasker.data.ConfigSyncSnapshotTest --no-daemon
```

Expected:

```text
FAIL because ConfigSync.syncPackages does not exist.
```

- [ ] **Step 4: Add scoped sync API**

In `ConfigSync.kt`, add:

```kotlin
fun syncPackages(
    @Suppress("UNUSED_PARAMETER") context: Context,
    config: JsonConfig,
    packageNames: Set<String>,
) {
    syncPackages(config, packageNames, XposedPrefs.getPrefs())
}

internal fun syncPackages(
    config: JsonConfig,
    packageNames: Set<String>,
    prefs: android.content.SharedPreferences?,
) {
    if (prefs == null) {
        Timber.tag(TAG).d("XposedService not connected — scoped config will sync on next activation")
        return
    }

    val committed =
        prefs
            .edit()
            .apply {
                putStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, config.appConfigs.keys.toSortedSet())
                putLong(SharedPrefsKeys.KEY_CONFIG_VERSION, System.currentTimeMillis())
                packageNames.toSortedSet().forEach { packageName ->
                    val state = config.syncStateFor(packageName)
                    if (state == null) {
                        removePackageSyncKeys(packageName)
                        putAppDisabled(packageName)
                    } else {
                        putAppSyncState(state)
                    }
                }
            }
            .commit()
    if (!committed) {
        Timber.tag(TAG).w("RemotePreferences commit failed during scoped sync")
    }
}
```

Keep `syncFromConfig()` as the full repair/import/init path.

- [ ] **Step 5: Add sync hints in `ConfigManager`**

Add a private sync hint:

```kotlin
private sealed interface SyncHint {
    data object Full : SyncHint
    data class Packages(val packageNames: Set<String>) : SyncHint
}
```

Change update/persist helpers:

```kotlin
private fun updateConfig(
    syncHint: SyncHint = SyncHint.Full,
    transform: (JsonConfig) -> JsonConfig,
) {
    _config.update(transform)
    saveConfig(syncHint)
}

private fun saveConfig(syncHint: SyncHint = SyncHint.Full) {
    val generation = initGeneration.get()
    val snapshot = _config.value
    scope.launch { saveConfigInternal(snapshot, generation, syncHint) }
}

private suspend fun persistAndSyncConfig(config: JsonConfig, syncHint: SyncHint) {
    writeConfigFile(config) { throw it }
    when (syncHint) {
        SyncHint.Full -> ConfigSync.syncFromConfig(requireAppContext(), config)
        is SyncHint.Packages -> ConfigSync.syncPackages(requireAppContext(), config, syncHint.packageNames)
    }
}
```

Use scoped hints in package-specific methods:

```kotlin
override fun setAppEnabled(packageName: String, enabled: Boolean) {
    val appConfig =
        getAppConfig(packageName)?.copy(isEnabled = enabled)
            ?: AppConfig(packageName = packageName, isEnabled = enabled)
    updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) { it.setAppConfig(appConfig) }
}

override fun assignAppToGroup(packageName: String, groupId: String) {
    updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) { config ->
        // Existing transform body.
    }
}

override fun unassignApp(packageName: String) {
    updateConfig(syncHint = SyncHint.Packages(setOf(packageName))) { config ->
        // Existing transform body.
    }
}
```

For group-wide changes, compute affected packages before `updateConfig`:

```kotlin
private fun packagesAssignedToGroup(groupId: String): Set<String> =
    _config.value.appConfigs.values
        .asSequence()
        .filter { it.groupId == groupId }
        .map { it.packageName }
        .toSet()
```

Use `SyncHint.Packages(packagesAssignedToGroup(groupId))` for `updateGroup`, `setIdentifierValue`, `setTypeEnabled`, and `regenerateAllValues`. Use `SyncHint.Full` for module enable, import/recovery/default-group changes, and config load.

- [ ] **Step 6: Run config sync tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.ConfigSyncTest --tests com.astrixforge.devicemasker.data.ConfigSyncSnapshotTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Dirty package sync writes the allowlist and touched packages without full snapshot work.
```

---

### Task 5: Xposed Hook Config Snapshot

**Files:**
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookConfigSnapshot.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/NetworkHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SensorHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AdvertisingHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/HookConfigSnapshotTest.kt`

- [ ] **Step 1: Run impact checks**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "PrefsHelper", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "BaseSpoofHooker", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "XposedEntry", direction = "upstream")
```

Expected:

```text
Risk is likely MEDIUM or higher because hookers depend on these helpers. If HIGH/CRITICAL, stop and report before edits.
```

- [ ] **Step 2: Add snapshot tests first**

Create `HookConfigSnapshotTest.kt`:

```kotlin
class HookConfigSnapshotTest {
    @Test
    fun `snapshot uses flat configured value before persona fallback`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), packageName)
        val prefs =
            PrefsHelperTest.MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.KEY_CONFIG_VERSION to 7L,
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to true,
                    SharedPrefsKeys.getSpoofValueKey(packageName, SpoofType.IMEI) to
                        "490154203237518",
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        val snapshot = HookConfigSnapshot.fromPrefs(prefs, packageName)

        assertEquals(7L, snapshot.version)
        assertEquals("490154203237518", snapshot.getValue(SpoofType.IMEI))
    }

    @Test
    fun `snapshot uses matching persona fallback once during construction`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), packageName)
        val prefs =
            PrefsHelperTest.MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to true,
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        val snapshot = HookConfigSnapshot.fromPrefs(prefs, packageName)

        assertEquals(persona.hardware.primaryImei, snapshot.getValue(SpoofType.IMEI))
        assertEquals(persona.hardware.primaryImei, snapshot.getValue(SpoofType.IMEI))
    }
}
```

If `MapSharedPreferences` is private, move it to `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/MapSharedPreferences.kt` and update `PrefsHelperTest` to use it.

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.HookConfigSnapshotTest --no-daemon
```

Expected:

```text
FAIL because HookConfigSnapshot does not exist yet.
```

- [ ] **Step 4: Add `HookConfigSnapshot`**

Create `HookConfigSnapshot.kt`:

```kotlin
package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.DevicePersona
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofType

internal data class HookConfigSnapshot(
    val packageName: String,
    val version: Long,
    private val enabledTypes: Set<SpoofType>,
    private val values: Map<SpoofType, String>,
) {
    fun isEnabled(type: SpoofType): Boolean = type in enabledTypes

    fun getValue(type: SpoofType): String? =
        values[type]?.takeIf { it.isNotBlank() }?.takeIf { type in enabledTypes }

    fun getDeviceProfilePreset(): DeviceProfilePreset? =
        getValue(SpoofType.DEVICE_PROFILE)?.let(DeviceProfilePreset::findById)

    companion object {
        fun fromPrefs(prefs: SharedPreferences, packageName: String): HookConfigSnapshot {
            val persona =
                DevicePersona.parseOrNull(
                    prefs.getString(SharedPrefsKeys.getPersonaBlobKey(packageName), null)
                )
                    ?.takeIf { it.packageName == packageName }
            val enabled = linkedSetOf<SpoofType>()
            val values = linkedMapOf<SpoofType, String>()

            SpoofType.entries.forEach { type ->
                val typeEnabled =
                    prefs.getBoolean(SharedPrefsKeys.getSpoofEnabledKey(packageName, type), false)
                if (typeEnabled) {
                    enabled += type
                    val flatValue =
                        prefs
                            .getString(SharedPrefsKeys.getSpoofValueKey(packageName, type), null)
                            ?.takeIf { it.isNotBlank() }
                    val value = flatValue ?: persona?.getValue(type)?.takeIf { it.isNotBlank() }
                    if (value != null) values[type] = value
                }
            }

            return HookConfigSnapshot(
                packageName = packageName,
                version = prefs.getLong(SharedPrefsKeys.KEY_CONFIG_VERSION, 0L),
                enabledTypes = enabled,
                values = values,
            )
        }
    }
}
```

- [ ] **Step 5: Add snapshot helper overloads**

In `BaseSpoofHooker.kt`, add:

```kotlin
protected fun getConfiguredSpoofValue(
    snapshot: HookConfigSnapshot,
    type: SpoofType,
): String? = snapshot.getValue(type)

protected fun getConfiguredDeviceProfilePreset(snapshot: HookConfigSnapshot): DeviceProfilePreset? =
    snapshot.getDeviceProfilePreset()

protected fun isSpoofTypeEnabled(snapshot: HookConfigSnapshot, type: SpoofType): Boolean =
    snapshot.isEnabled(type)
```

Keep the existing `SharedPreferences` helper methods until every hooker is migrated.

- [ ] **Step 6: Pass snapshot from `XposedEntry`**

After `policy` creation:

```kotlin
val snapshot = HookConfigSnapshot.fromPrefs(prefs, hookPackage)
```

Change value hooker registrations from:

```kotlin
DeviceHooker.hook(cl, this, prefs, hookPackage)
```

to:

```kotlin
DeviceHooker.hook(cl, this, prefs, hookPackage, snapshot)
```

Keep `prefs` for hook families that still need policy-specific booleans such as proc-maps, risky hooks, or class lookup hiding.

- [ ] **Step 7: Migrate hooker hot paths**

For each hooker listed in this task, change the hook signature to accept `snapshot: HookConfigSnapshot` and replace callback reads:

```kotlin
getConfiguredSpoofValue(prefs, pkg, SpoofType.IMEI)
```

with:

```kotlin
getConfiguredSpoofValue(snapshot, SpoofType.IMEI)
```

Replace device profile reads:

```kotlin
getConfiguredDeviceProfilePreset(prefs, pkg)
```

with:

```kotlin
getConfiguredDeviceProfilePreset(snapshot)
```

Do not change hook registration order.

- [ ] **Step 8: Run Xposed unit tests and ABI guard**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.HookConfigSnapshotTest --tests com.astrixforge.devicemasker.xposed.PrefsHelperTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Snapshot reads preserve values and R8 callback shape stays safe.
```

---

### Task 6: Coalesce Xposed Registration Logging

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Modify or create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/XposedEntryLoggingTest.kt`

- [ ] **Step 1: Run impact check**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "hookSafely", file_path = "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report before edits.
```

- [ ] **Step 2: Remove debug structured success events from common path**

Change `hookSafely()` success path from per-hook structured DEBUG logs to health counters only:

```kotlin
try {
    XposedDiagnosticEventSink.hookHealth.recordRegistrationAttempt(name, "hook")
    block()
    XposedDiagnosticEventSink.hookHealth.recordRegistrationSuccess(name, "hook")
} catch (e: XposedFrameworkError) {
    throw e
} catch (t: Throwable) {
    // Existing failure logging stays.
}
```

Keep disabled and failed structured events. Keep final evidence:

```kotlin
log(Log.INFO, TAG, "All hooks registered for: $hookPackage", null)
XposedDiagnosticEventSink.log(
    Log.INFO,
    TAG,
    "All hooks registered for: $hookPackage",
    eventType = DiagnosticEventType.HOOK_REGISTERED,
)
```

- [ ] **Step 3: Run Xposed tests**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Required load/target/final evidence remains; per-hook success spam is gone.
```

---

### Task 7: Stream Support Bundle JSONL Entries

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- Create or modify: `app/src/test/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`

- [ ] **Step 1: Run impact check**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "SupportBundleBuilder", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report before edits.
```

- [ ] **Step 2: Add large-event test**

Add:

```kotlin
@Test
fun `support bundle streams app and xposed events as jsonl`() {
    val outputDir = temporaryFolder.newFolder("bundle")
    val appEvents = (1..1000).map { """{"message":"app-$it"}""" }
    val xposedEvents = (1..1000).map { """{"message":"xposed-$it"}""" }

    val bundle =
        SupportBundleBuilder(appEvents = appEvents, xposedEvents = xposedEvents)
            .build(outputDir, RedactionMode.UNREDACTED)

    ZipFile(bundle).use { zip ->
        val appText = zip.getInputStream(zip.getEntry("app/app_events.jsonl")).reader().readText()
        val xposedText =
            zip.getInputStream(zip.getEntry("xposed/xposed_events.jsonl")).reader().readText()
        assertTrue(appText.contains("""{"message":"app-1000"}"""))
        assertTrue(xposedText.contains("""{"message":"xposed-1000"}"""))
    }
}
```

- [ ] **Step 3: Replace `joinToString()` with streaming writer**

Add:

```kotlin
private fun ZipOutputStream.writeJsonl(
    path: String,
    lines: List<String>,
    redactor: DiagnosticRedactor,
) {
    putNextEntry(ZipEntry(path))
    lines.forEach { line ->
        write(line.redact(redactor).toByteArray(Charsets.UTF_8))
        write('\n'.code)
    }
    closeEntry()
}
```

Replace:

```kotlin
zip.writeText("app/app_events.jsonl", appEvents.joinToString("\n").redact(redactor))
zip.writeText("xposed/xposed_events.jsonl", xposedEvents.joinToString("\n").redact(redactor))
```

with:

```kotlin
zip.writeJsonl("app/app_events.jsonl", appEvents, redactor)
zip.writeJsonl("xposed/xposed_events.jsonl", xposedEvents, redactor)
```

- [ ] **Step 4: Run support bundle tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Export no longer builds large joined JSONL strings.
```

---

### Task 8: Delay Startup Root Capture Work

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffects.kt`
- Modify or create: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/MainActivityEffectsTest.kt`

- [ ] **Step 1: Run impact check**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "RequestStartupRootCapture", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report before edits.
```

- [ ] **Step 2: Delay root capture by one frame and a short idle window**

Change:

```kotlin
@Composable
internal fun RequestStartupRootCapture(appContext: Context) {
    LaunchedEffect(Unit) { requestRootAndCaptureStartup(appContext, force = false) }
}
```

to:

```kotlin
private const val STARTUP_ROOT_CAPTURE_DELAY_MILLIS = 1500L

@Composable
internal fun RequestStartupRootCapture(appContext: Context) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        kotlinx.coroutines.delay(STARTUP_ROOT_CAPTURE_DELAY_MILLIS)
        requestRootAndCaptureStartup(appContext, force = false)
    }
}
```

This keeps startup capture behavior but stops it from competing with first composition.

- [ ] **Step 3: Compile app**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Startup root capture is delayed, not removed.
```

---

### Task 9: Apps Tab Derived Row Model

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/AppRowModel.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingViewModel.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingViewModelTest.kt`

- [ ] **Step 1: Run impact checks**

Run:

```text
mcp__gitnexus__impact(repo = "DeviceMasker", target = "GroupSpoofingViewModel", direction = "upstream")
mcp__gitnexus__impact(repo = "DeviceMasker", target = "AppsTabContent", direction = "upstream")
```

Expected:

```text
Risk is not HIGH/CRITICAL. If it is HIGH/CRITICAL, stop and report before edits.
```

- [ ] **Step 2: Add row model**

Create:

```kotlin
package com.astrixforge.devicemasker.ui.screens.groupspoofing

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.data.models.InstalledApp

@Immutable
data class AppRowModel(
    val app: InstalledApp,
    val normalizedLabel: String,
    val normalizedPackageName: String,
    val isAssignedToCurrentGroup: Boolean,
    val isAppEnabled: Boolean,
    val assignedToOtherGroupName: String?,
) {
    fun matches(query: String, showSystemApps: Boolean, selfPackageName: String): Boolean =
        app.packageName != selfPackageName &&
            (showSystemApps || !app.isSystemApp) &&
            (query.isEmpty() ||
                normalizedLabel.contains(query) ||
                normalizedPackageName.contains(query))
}
```

- [ ] **Step 3: Move normalization out of composition**

In `GroupSpoofingViewModel`, build row models when installed apps, group, all groups, or app configs change:

```kotlin
private fun buildAppRows(
    installedApps: List<InstalledApp>,
    currentGroup: SpoofGroup?,
    allGroups: List<SpoofGroup>,
    appConfigs: Map<String, AppConfig>,
): ImmutableList<AppRowModel> {
    val groupsById = allGroups.associateBy { it.id }
    return installedApps
        .map { app ->
            val appConfig = appConfigs[app.packageName]
            val assignedGroupId = appConfig?.groupId
            AppRowModel(
                app = app,
                normalizedLabel = app.label.lowercase(),
                normalizedPackageName = app.packageName.lowercase(),
                isAssignedToCurrentGroup = currentGroup != null && assignedGroupId == currentGroup.id,
                isAppEnabled = appConfig?.isEnabled != false,
                assignedToOtherGroupName =
                    assignedGroupId
                        ?.takeIf { it != currentGroup?.id }
                        ?.let { groupsById[it]?.name },
            )
        }
        .sortedWith(compareByDescending<AppRowModel> { it.isAssignedToCurrentGroup }.thenBy { it.normalizedLabel })
        .toImmutableList()
}
```

- [ ] **Step 4: Simplify `AppsTabContent` filtering**

Change `AppsTabContent` to accept `appRows: ImmutableList<AppRowModel>` and filter without sorting:

```kotlin
private fun rememberFilteredRows(
    appRows: ImmutableList<AppRowModel>,
    debouncedQuery: String,
    showSystemApps: Boolean,
): ImmutableList<AppRowModel> {
    val query = debouncedQuery.lowercase()
    return remember(appRows, query, showSystemApps) {
        appRows
            .asSequence()
            .filter { it.matches(query, showSystemApps, BuildConfig.APPLICATION_ID) }
            .toList()
            .toImmutableList()
    }
}
```

Render:

```kotlin
items(items = rows, key = { it.app.packageName }, contentType = { "app_item" }) { row ->
    AppListItem(
        app = row.app,
        isAssigned = row.isAssignedToCurrentGroup,
        isAppEnabled = row.isAppEnabled,
        assignedToOtherGroupName = row.assignedToOtherGroupName,
        onToggle = { checked -> onAppToggle(row.app, checked) },
        modifier = Modifier.fillMaxWidth().animateItem(),
    )
}
```

- [ ] **Step 5: Run Group Spoofing tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.groupspoofing.GroupSpoofingViewModelTest --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Apps tab no longer sorts the full installed app list inside composition on every app config change.
```

---

### Task 10: Fix Lint And Build Warnings Without Random Churn

**Files:**
- Modify only lint-proven unused resources under `app/src/main/res/`
- Modify Kotlin source only when a warning points to the exact source issue
- Do not modify public docs, Memory Bank, or unrelated UI text in this task

- [ ] **Step 1: Generate exact unused resource list**

Run:

```powershell
.\gradlew.bat lint --no-daemon
Select-String -Path app/build/reports/lint-results-debug.txt -Pattern "\\[UnusedResources\\]" |
    ForEach-Object { $_.Line } |
    Set-Content logs/build/2026-05-18-unused-resources.txt
```

Expected:

```text
logs/build/2026-05-18-unused-resources.txt contains the current exact unused resource warnings.
```

- [ ] **Step 2: Protect launcher resources**

Before deleting launcher resources, verify manifest/adaptive icon references:

```powershell
rg -n "android:icon|android:roundIcon|ic_launcher|ic_launcher_foreground|ic_launcher_monochrome|ic_launcher_background" app/src/main
```

Expected:

```text
Launcher icons referenced by AndroidManifest.xml or adaptive icon XML stay. If lint still flags them, prefer tools:keep or lint suppression for the launcher resource instead of deleting app icons.
```

- [ ] **Step 3: Remove generated duplicate placeholder strings**

For strings ending in `_final` and strings named `done_placeholder`, verify no source reference:

```powershell
rg -n "R\\.string\\.[A-Za-z0-9_]*_final|R\\.string\\.done_placeholder|@string/[A-Za-z0-9_]*_final|@string/done_placeholder" app/src/main app/src/test
```

Expected:

```text
No matches before deletion. If a match exists, keep the resource or update the real call site intentionally.
```

Then remove only the matching `<string>` entries from `app/src/main/res/values/strings.xml`.

- [ ] **Step 4: Remove other unused resources one category at a time**

Use this command to verify each candidate resource name before deletion:

```powershell
$name = "RESOURCE_NAME_WITHOUT_PREFIX"
rg -n "R\\.[a-z]+\\.$name|@$name|@string/$name|@drawable/$name|@mipmap/$name|@xml/$name|@array/$name|\\\"$name\\\"" app/src/main app/src/test
```

Expected:

```text
Only delete when there is no real reference and the resource is not a launcher/theme/provider/manifest resource.
```

- [ ] **Step 5: Run lint after each resource batch**

Run:

```powershell
.\gradlew.bat lint --no-daemon
```

Expected:

```text
Warning count decreases. If a deleted resource breaks compilation or manifest packaging, restore it immediately.
```

- [ ] **Step 6: Fix Kotlin/Detekt warnings from the performance changes**

Run:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Do not create or update detekt baselines unless the user explicitly accepts documented existing debt.
```

---

### Task 11: Final Verification Gate

**Files:**
- Read/Write as produced by prior tasks
- Create logs only under `logs/build/` or `logs/tmp/`

- [ ] **Step 1: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --tests com.astrixforge.devicemasker.data.repository.AppScopeRepositoryTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.components.AppIconCacheTest --tests com.astrixforge.devicemasker.service.ConfigSyncTest --tests com.astrixforge.devicemasker.data.ConfigSyncSnapshotTest --no-daemon
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.HookConfigSnapshotTest --tests com.astrixforge.devicemasker.xposed.PrefsHelperTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected:

```text
All focused tests pass.
```

- [ ] **Step 2: Run static safety greps**

Run:

```powershell
Get-ChildItem -Path xposed/src -Recurse -Filter '*.kt' | Select-String '@XposedHooker|@BeforeInvocation|@AfterInvocation|AfterHookCallback'
Get-ChildItem -Path app/src,xposed/src -Recurse -Filter '*.kt' | Select-String '"module_enabled"|"app_enabled_"|"spoof_value_"|"spoof_enabled_"'
Get-ChildItem -Path common/src -Recurse -Filter '*.kt' | Select-String 'Random\(\)' | Where-Object { $_ -notmatch 'SecureRandom' }
Get-ChildItem -Path xposed/src -Recurse -Filter '*.kt' | Select-String 'Timber\.'
Get-ChildItem -Path common/src,xposed/src -Recurse -Filter '*.kt' | Select-String 'import androidx.compose'
Get-ChildItem -Path xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker -Filter '*.kt' | Where-Object { $_.Name -ne 'BaseSpoofHooker.kt' } | Select-String '\.intercept\s*\{'
```

Expected:

```text
No forbidden matches. If matches appear, fix before continuing.
```

- [ ] **Step 3: Run full local gate**

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. Lint warning count is lower; target is zero warnings unless Android lint keeps false-positive launcher resources that must be suppressed with a narrow tools:keep or lint suppression.
```

- [ ] **Step 4: Run release/R8 hook safety gate**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon
```

Expected:

```text
BUILD SUCCESSFUL. R8 callback ABI guard still passes.
```

- [ ] **Step 5: Run GitNexus change detection**

Run:

```text
mcp__gitnexus__detect_changes(repo = "DeviceMasker", scope = "all")
```

Expected:

```text
Affected flows match the planned Home scoped-app loading, app icon rendering, config sync, Xposed hook config reads, diagnostics export, startup root capture, Apps tab derivation, and resource cleanup work.
```

- [ ] **Step 6: Update Memory Bank**

Update:

```text
memory-bank/activeContext.md
memory-bank/progress.md
memory-bank/systemPatterns.md
memory-bank/techContext.md only if dependencies, toolchain, or build commands changed.
```

Expected:

```text
Memory Bank records the new scoped metadata path, icon cache, dirty config sync, HookConfigSnapshot, warning cleanup status, verification commands, and no commit/push status.
```

---

## Self-Review

Spec coverage:

- Maximum in-place performance optimization is covered by Tasks 1 through 9.
- Warning cleanup is covered by Task 10.
- No commit and no push is stated in the header, non-negotiables, and no task contains commit/push commands.
- No new app/benchmark/baseline-profile module is included.
- Xposed safety is covered by libxposed skill requirements, snapshot tests, and R8 ABI guard.
- Project verification and GitNexus requirements are covered by Tasks 0 and 11.

Placeholder scan:

- No placeholder markers remain in task instructions.
- No deferred-work phrases remain in task instructions.
- No vague error-handling instruction remains in task instructions.
- Each code-changing task includes concrete code shape and verification commands.

Type consistency:

- `IAppScopeRepository.scopedAppMetadata` is `StateFlow<Map<String, InstalledApp>>` and is used consistently by Home.
- `ConfigSync.syncPackages()` accepts `JsonConfig`, `Set<String>`, and `SharedPreferences?` in the internal testable overload.
- `HookConfigSnapshot` exposes `getValue()`, `isEnabled()`, and `getDeviceProfilePreset()`, matching the hooker migration steps.
- `AppRowModel` wraps existing `InstalledApp`, so existing toggle callback shape remains stable.
