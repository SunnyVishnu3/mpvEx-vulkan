# 2026-05-14 Toggle And Hook Scope Audit

## Implementation Status

The current code remediation for this audit has been implemented and summarized in `docs/internal/reports/closed/summaries/2026-05-14/2026-05-14-toggle-hook-scope-implementation-summary.md`.

Static/unit verification passed:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon --no-configuration-cache
```

Runtime LSPosed/device validation passed on `emulator-5554` for both the unassigned-package skip behavior and normal assigned-package hook behavior. This report is closed.

## Findings

### High: Hook eligibility still trusts stale per-package RemotePreferences booleans

**Evidence:** `XposedEntry.enabledHookPackageOrNull()` gates hook registration on `module_enabled` and `app_enabled_{package}` only, with `module_enabled` defaulting to `true`: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:198-202`. `selectHookPackage()` repeats the same per-package boolean check and never checks an authoritative package set, config version, group id, or assignment list: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:213-222`. Full sync only removes stale app keys for packages listed in the previous `enabled_apps` set: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt:126-143`.

**Problem:** If a package has a leftover `app_enabled_* = true` key from an older build, failed/partial sync, manual test state, or a config era before `enabled_apps` tracking, adding that package to LSPosed scope can immediately register hooks even when the current UI shows no group/app assignment. This matches the user-observed symptom. Current code is safe for a truly clean install, but it is not robust against stale RemotePreferences state. This is hand-wavy bullshit as a safety boundary: the target-process gate should prove current assignment, not trust a lone historical boolean.

**Root cause:** The data model has two different notions of scope: current `JsonConfig.appConfigs` in the app, and historical flattened keys in RemotePreferences. The hook side has no compact allowlist or generation marker to validate that `app_enabled_*` belongs to the latest config snapshot.

**Fix direction:** Publish a current canonical package allowlist and generation marker from `ConfigSync`, then require both in `XposedEntry`: `KEY_ENABLED_APPS` contains the package and `app_enabled_{pkg}` is true. For migration safety, add a one-time full RemotePreferences scrub or a `config_generation`/`schema_version` gate so old orphan keys cannot activate hooks. Also change `module_enabled` hook-side default from `true` to `false` unless there is a proven compatibility reason.

**Verification:** Add a unit/static test that seeds RemotePreferences with `app_enabled_com_example=true`, `enabled_apps=[]`, and asserts `XposedEntry` would not select the package. Add a runtime smoke: clear app UI config, leave LSPosed scope enabled, launch target, and verify no `Target package selected`, `Hooks registered`, or `Spoof event` lines appear.

### High: `JsonConfig.getGroupForApp()` falls back to the default group for packages without a canonical assignment

**Evidence:** `getGroupForApp(packageName)` returns `appConfig?.groupId?.let(groups::get) ?: getDefaultGroup()` when the app is not explicitly disabled: `common/src/main/kotlin/com/astrixforge/devicemasker/common/JsonConfig.kt:91-97`. `syncStateFor(packageName)` calls that function before checking the package's canonical app config and then computes `appEnabled = isModuleEnabled && configApp?.isEnabled == true && group.isEnabled`: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt:20-28`. `ConfigManager.setAppEnabled()` can create an `AppConfig(packageName, isEnabled = true)` with no `groupId`: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:393-398`.

**Problem:** A package can become enabled against the default group without the user explicitly assigning it to that group if any caller creates an enabled `AppConfig` without a `groupId`. Today the main group assignment UI uses `assignAppToGroup()`, but the public data model still permits a brain-damaged state where "enabled app" and "assigned group" are separate facts. That is special-case insanity waiting to leak into runtime scope.

**Root cause:** Default-group fallback lives in the canonical runtime resolver. That may be useful for old UX flows, but it violates the current project rule that `JsonConfig.appConfigs` is canonical for app scope and that unselected apps must pass through.

**Fix direction:** Split resolution APIs. Use `getExplicitGroupForApp()` for runtime sync and hook eligibility, returning a group only when `AppConfig.groupId` is present and valid. If default group fallback is still wanted for display or migration, keep it behind a separately named UI helper. Make `setAppEnabled(true)` refuse to create an enabled config without a group or require a group id parameter.

**Verification:** Add tests for `JsonConfig(appConfigs = mapOf("pkg" to AppConfig("pkg", groupId = null, isEnabled = true)), defaultGroup)` and assert runtime sync writes `app_enabled_pkg=false` with no spoof values.

### Medium: App assignment UI still reads legacy `SpoofGroup.assignedApps` instead of canonical `appConfigs`

**Evidence:** `AppsTabContent` displays assigned count with `group?.assignedAppCount()` and sorts/toggles with `group?.isAppAssigned(packageName)`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:104-110`, `:143-163`, and `:310-318`. Cross-group labels use `allGroups.firstOrNull { it.isAppAssigned(packageName) }`: `AppsTabContent.kt:331-332`. `GroupCard` defaults its app count from `group.assignedAppCount()`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/GroupCard.kt:75`. `HomeViewModel` and `HomeGroupSelector` also use `assignedAppCount()` for selected/default group display.

**Problem:** The UI can disagree with runtime if `assignedApps` and `appConfigs` drift. Runtime sync iterates `config.appConfigs.keys`: `ConfigSync.kt:130-143`. The app list and group cards inspect the legacy display set. That means a user can believe a toggle is off or an app is unassigned while RemotePreferences is still generated from `appConfigs`, or the reverse. This is bogus shit because the UI is presenting a legacy mirror as if it were the source of truth.

**Root cause:** The migration to canonical `appConfigs` was incomplete at the presentation layer. `ConfigManager.assignAppToGroup()` still updates both `assignedApps` and `appConfigs`: `ConfigManager.kt:356-374`, but reads throughout UI still favor the legacy side.

**Fix direction:** Add a small derived UI model from `JsonConfig.appConfigs`: `groupAppCounts`, `isPackageAssignedToGroup(packageName, groupId)`, and `assignedGroupName(packageName)`. Feed that into `GroupSpoofingState`, `GroupsState`, and home state instead of asking `SpoofGroup` directly. Keep `assignedApps` only for import compatibility or remove it from active display paths.

**Verification:** Add a test config with `assignedApps` stale and `appConfigs` current. Assert app list checked state, counts, and runtime snapshot all agree with `appConfigs`.

### Medium: Correlated category master switch state is computed with `any`, not `all`

**Evidence:** `UIDisplayCategory.isEnabledFor(group)` returns `isCorrelated && types.any { group?.isTypeEnabled(it) ?: false }`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/CategorySection.kt:330-331`. The switch uses that value, and on toggle it writes every type in the category: `CategorySection.kt:199-214`.

**Problem:** A partially enabled correlated category renders as fully on. If one SIM/location/device-related value is enabled and another is disabled, the master switch looks enabled even though the runtime will spoof only the enabled subset. Tapping it off disables all, but tapping an apparently-on partial state does not communicate that the group is partially configured. This is a UI correctness bug, not directly a hook bug.

**Root cause:** Boolean data shape cannot represent tri-state category state: none, partial, all. The UI collapses partial into enabled.

**Fix direction:** Introduce a tiny `CategoryToggleState { NONE, PARTIAL, ALL }` derived from category types. Render partial state explicitly, or make the master switch checked only when all category types are enabled and show a separate mixed-state label/count.

**Verification:** Add Compose/state tests for one enabled and one disabled type in a correlated category. Expected UI state should be partial or unchecked, not fully checked.

### Medium: Hook-family policy is tied only to app enabled, not type/category enabled

**Evidence:** `ConfigSync.buildSnapshot()` writes every hook-family key to `state?.appEnabled == true`: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt:148-151`. `putAppSyncState()` does the same: `ConfigSyncHelpers.kt:60-66`. `XposedEntry` registers each hooker if the family policy is enabled: `XposedEntry.kt:127-166`.

**Problem:** Disabling all spoof types in a group stops spoof values through `PrefsHelper`, but hookers still register when the app and group are enabled. Most callbacks pass through correctly because `PrefsHelper.getStoredSpoofValue()` returns null when `spoof_enabled_*` is false: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt:33-43`. Still, registering unused hook families increases target-process surface area and can make users think "all value cards off" means "no hooks at all." For anti-detection and package-manager hooks, family enablement is especially sensitive because they are not ordinary spoof-value toggles.

**Root cause:** Hook-family policy is a coarse crash-isolation switchboard, not derived from value/category state. The UI does not explain that distinction.

**Fix direction:** Keep the current pass-through behavior as the safe baseline, but add a separate "Hook families active" diagnostic view and consider deriving low-risk families from enabled spoof type groups. Keep anti-detection and package-manager families explicit per-app controls, not hidden behind value toggles.

**Verification:** Add a no-spoof-types-enabled runtime check: target app scoped, app/group enabled, all value toggles off. Verify whether hook registration still occurs and whether every supported API returns original values. Document the expected behavior.

### Low: Group disabled switch appears functionally correct in config projection

**Evidence:** `GroupsScreen` calls `viewModel.setGroupEnabled(group.id, enabled)`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt:110`. `GroupsViewModel` delegates to `repository.setGroupEnabled()`: `GroupsViewModel.kt:51-52`. `SpoofRepository.setGroupEnabled()` writes `group.withEnabled(enabled)`: `SpoofRepository.kt:585-590`. `syncStateFor()` includes `group.isEnabled` in `appEnabled`: `ConfigSyncHelpers.kt:25-28`, and disables all spoof type values when `appEnabled` is false: `ConfigSyncHelpers.kt:35-48`.

**Problem:** No direct bug found in the main group-card switch path. Turning a group off should write `app_enabled_{pkg}=false`, clear persona, remove spoof values, and write `spoof_enabled_* = false` for that group apps on the next successful sync.

**Root cause:** None for the normal current-code path. Residual risk is stale RemotePreferences or UI/runtime drift described above.

**Fix direction:** Add regression tests specifically named after this user requirement: "disabled group does not hook scoped assigned app" and "disabled group clears all spoof values from RemotePreferences."

**Verification:** Unit-test `ConfigSync.buildSnapshot()` for group disabled. Runtime smoke with LSPosed scope still active should show no `Target package selected` for that app after force-stop/relaunch.

### Low: Per-value card toggles appear functionally correct for value pass-through

**Evidence:** `GroupSpoofingScreen` passes `onToggle = { type, enabled -> viewModel.toggleSpoofType(type, enabled) }`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingScreen.kt:194`. `toggleSpoofType()` copies `DeviceIdentifier.isEnabled`: `GroupSpoofingViewModel.kt:137-143`. Sync writes each `spoof_enabled_{pkg}_{type}` from `appEnabled && group.isTypeEnabled(type)` and only writes a value when enabled: `ConfigSyncHelpers.kt:35-48`. Hook reads return null when the type is disabled: `PrefsHelper.kt:33-43`.

**Problem:** No direct value-toggle-to-hook bug found for ordinary spoofed values. Disabled value cards should stop spoofing that type, assuming RemotePreferences commit succeeds and the target process sees the updated preferences.

**Root cause:** None for the direct path. Remaining issue is that hook registration may still happen even when no value spoofing happens.

**Fix direction:** Keep the path, add tests for each representative category: independent, SIM correlated, location, and device profile/persona fallback.

**Verification:** For each category, toggle off one type and assert `spoof_enabled=false`, no stored value for that type, and original target-app value at runtime.

## Executive Summary

The core toggle-to-value path is mostly right: group off and value-card off both collapse into `appEnabled=false` or `spoof_enabled=false`, and `PrefsHelper` passes through when type config is disabled or blank. The dangerous part is hook eligibility. `XposedEntry` currently trusts stale flattened booleans and does not verify a current canonical allowlist or config generation before registering hooks. That can explain "I only scoped the app in LSPosed and it hooked immediately" if stale RemotePreferences exist.

The second major issue is data-model drift. Runtime claims `appConfigs` is canonical, but UI assignment state still reads `SpoofGroup.assignedApps` in multiple places. That is a classic data supremacy failure: two structures are trying to answer the same question.

## Scope

Reviewed the app/group/type toggle path from Compose UI through ViewModels, repository, config manager, RemotePreferences sync, Xposed entry gating, and hook-side value reads. The audit focused on:

- Group card on/off switch.
- GroupSpoofing value/category toggles.
- App assignment toggle in the Apps tab.
- LSPosed-scoped app behavior when no current group/app selection exists.
- Recommendations and feature ideas for safer scope semantics.

## Source Inventory

- `docs/AGENTS_PROJECT_RULES.md`
- `memory-bank/projectbrief.md`
- `memory-bank/productContext.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `app/AGENTS.md`
- `common/AGENTS.md`
- `xposed/AGENTS.md`
- `.agents/skills/libxposed/SKILL.md`
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/JsonConfig.kt`
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/SpoofGroupExtensions.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsViewModel.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingScreen.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingViewModel.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/CategorySection.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt`
- `app/src/test/java/com/astrixforge/devicemasker/data/ConfigSyncSnapshotTest.kt`

## Project Rule Violations

- `JsonConfig.appConfigs` is documented as canonical, but app-assignment UI still reads `SpoofGroup.assignedApps`. This is a project-rule drift and a real correctness risk.
- Hook safety says disabled/missing config must return original values. Value callbacks satisfy that in the reviewed path, but hook registration itself can still occur from stale `app_enabled_*` keys.
- "Show me the numbers" gap: there is no disabled/no-group runtime matrix proving no target selection and no spoof events after LSPosed scope changes.

## AGENTS.md And Rule Drift Audit

The root and module rules are directionally correct, but they do not explicitly forbid the current failure mode: stale RemotePreferences booleans acting as hook allowlist. Recommended exact rule addition:

```text
:xposed must not treat app_enabled_{package} alone as proof of current scope.
Hook package selection must validate the package against a current canonical allowlist or config generation written by ConfigSync.
```

Recommended `common/AGENTS.md` clarification:

```text
Runtime group resolution must not fall back to the default group for packages without an explicit AppConfig.groupId.
Default-group fallback, if kept, is display/migration-only.
```

Recommended `app/AGENTS.md` clarification:

```text
App assignment UI must derive checked state, counts, and assigned-group labels from JsonConfig.appConfigs, not SpoofGroup.assignedApps.
```

## Root Cause Analysis

The main root cause is duplicated scope state:

1. `JsonConfig.appConfigs` says what the current app believes.
2. `SpoofGroup.assignedApps` says what legacy/display code believes.
3. Flattened RemotePreferences keys say what target processes believe.

This is hack upon hack unless one structure becomes the hard authority and all other structures are derived, versioned, and validated. The current implementation says `appConfigs` is canonical but has not made the UI and hook gate obey that statement everywhere.

## Recommended Fixes

1. Add authoritative RemotePreferences scope proof.
   - Write `enabled_apps` as the current canonical allowlist.
   - Write a config generation/schema marker.
   - Require package membership and `app_enabled=true` in `XposedEntry`.
   - Default `module_enabled` to false when absent.

2. Remove default-group fallback from runtime sync.
   - Add explicit runtime resolver requiring `AppConfig.groupId`.
   - Keep fallback only for display/migration if still needed.

3. Move app assignment UI to canonical state.
   - Expose app assignment maps/counts from repository/ViewModel.
   - Stop using `SpoofGroup.assignedApps` for checked state, sorting, counts, and cross-group labels.

4. Add toggle contract tests.
   - Group disabled.
   - App unassigned.
   - Type disabled.
   - Stale RemotePreferences key.
   - Partial correlated category state.

## Architecture Improvement Opportunities

- Replace `SpoofGroup.assignedApps` active usage with a derived `GroupAssignmentView`:

```kotlin
data class GroupAssignmentView(
    val groupId: String,
    val assignedPackages: Set<String>,
    val enabledPackages: Set<String>,
)
```

- Add one `RuntimeScopeSnapshot` data shape for RemotePreferences projection:

```kotlin
data class RuntimeScopeSnapshot(
    val generation: Long,
    val moduleEnabled: Boolean,
    val enabledPackages: Set<String>,
)
```

- Make `AppConfig` creation impossible without an explicit group for enabled spoofing. A nullable `groupId` plus `isEnabled=true` is a brain-damaged API for runtime scope.

## UI/UX Review Audit

- The group card switch uses normal UI wiring and should disable runtime spoofing after sync, but the UI does not show whether the target app is still LSPosed-scoped and stale-hook-risk exists.
- The Apps tab count and checked state can drift because they read `assignedApps`, not `appConfigs`.
- Correlated category switches need a partial state. Showing partial as on is misleading.
- Add a visible app detail status row: `Not LSPosed scoped`, `Scoped but not assigned`, `Assigned but group disabled`, `Assigned and active`, `Hooks observed`, `Spoof observed`. Do not claim hook success from app-side service connection.

## Best Solution Direction

Fix the data structure instead of spraying conditionals everywhere. Make `appConfigs` the only mutable assignment table. Make RemotePreferences a versioned projection of that table. Make `XposedEntry` reject packages that are not present in the current projection allowlist. Everything else should be display-only derived state.

## Optional Improvements And Feature Ideas

- Add a "Scope Doctor" screen that compares LSPosed scope, current `appConfigs`, RemotePreferences allowlist, and latest LSPosed logs.
- Add a "Dry run target state" card per app: shows exactly why the app will or will not be hooked.
- Add a "Disable all groups and clear runtime prefs" panic action.
- Add a stale RemotePreferences detector that warns when RemotePreferences contains packages absent from `appConfigs`.
- Add a verifier mode for disabled/no-group/malformed pass-through, not only enabled spoof success.
- Add per-app hook-family switches to UI only after they are clearly separated from value toggles.

## Proposed APIs, Interfaces, Dependencies, Or Tools

No new dependency is needed. Proposed narrow APIs:

```kotlin
fun JsonConfig.getExplicitGroupForApp(packageName: String): SpoofGroup?
fun JsonConfig.groupAssignmentView(groupId: String): GroupAssignmentView
fun ConfigSync.buildRuntimeScopeSnapshot(config: JsonConfig): RuntimeScopeSnapshot
```

Proposed test helper:

```kotlin
fun fakeRemotePrefs(vararg entries: Pair<String, Any>): SharedPreferences
```

## Rejected Or Risky Approaches

- Do not fix this by clearing all RemotePreferences on every app start only. That masks stale-key bugs and can race target processes.
- Do not make LSPosed scope alone imply app assignment. That breaks userspace expectations and would spoof apps the user did not select.
- Do not add a new Binder/AIDL config path. RemotePreferences is the project rule and is sufficient if the projected data shape is fixed.
- Do not broad-rewrite group/config architecture in one patch. The surgical path is resolver plus projection plus UI derived state.

## Verification Plan

1. Add static/unit tests for `ConfigSync.buildSnapshot()` group disabled, app unassigned, and type disabled states -> verify `app_enabled=false`, no persona blob, no spoof values, and `spoof_enabled=false`.
2. Add stale RemotePreferences selection test -> verify orphan `app_enabled=true` does not select hooks without current allowlist membership.
3. Add UI state tests for stale `assignedApps` versus canonical `appConfigs` -> verify counts and checked state follow `appConfigs`.
4. Run `.\gradlew.bat :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon` -> verify unit regression suite.
5. Runtime disabled matrix -> verify LSPosed/logcat has no target selection or spoof events for no-group/unassigned/disabled-group states after force-stop and relaunch.

## Residual Risks And Unknowns

- I did not inspect live device RemotePreferences contents, so the user-observed instant hook is a strong inference from code shape, not a reproduced runtime fact.
- I did not run Gradle tests because this skill workflow is report-only and avoids creating build artifacts.
- Existing dirty worktree changes in `AGENTS.md`, `memory-bank/activeContext.md`, and `memory-bank/progress.md` were treated as user-owned and were not modified.

## Suggested Next Tasks

1. Implement current allowlist/generation gating in `ConfigSync` and `XposedEntry`.
2. Replace runtime `getGroupForApp()` usage with explicit assignment resolution.
3. Convert Groups and GroupSpoofing assignment UI to `appConfigs`-derived state.
4. Add disabled/no-group/stale-pref regression tests.
5. Run a real LSPosed disabled-state matrix and archive evidence under `logs/device/`.

## Report File Path

`docs/internal/reports/closed/audits/2026-05-14/2026-05-14-toggle-hook-scope-audit.md`

## Write Boundary Confirmation

This review workflow wrote exactly this one Markdown report file. It created only the minimum parent audit folder required for the report path and did not edit source, tests, docs outside this report, Memory Bank, build files, logs, commits, branches, or pushes.
