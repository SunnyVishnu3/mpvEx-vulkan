# Findings

## High: Current section is not actually an LSPosed scoped-target list

Evidence:
- Plan goal says the section should show apps assigned to LSPosed targeting via `JsonConfig.appConfigs`: `docs/superpowers/plans/2026-05-15-home-targeted-apps-section.md`.
- Current state source is `repository.appConfigs` joined with `repository.appScopeRepository.installedApps`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:49`, `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:127`.
- There is no `XposedService.getScope()` or LSPosed scope-state read in the implementation.

Problem:
The UI label and user mental model are "LSPosed scoped targeted apps", but the implementation lists "Device Masker configured and installed apps." Those are not the same data set. An app can be configured in Device Masker but missing from LSPosed scope, or scoped in LSPosed but not configured. Mixing those states creates hand-wavy bullshit around hook readiness.

Root cause:
The plan used `appConfigs` as a proxy for "LSPosed targeted." `appConfigs` is the canonical spoof-eligibility table, not the LSPosed manager scope table.

Fix direction:
Use a UI model with explicit fields:

```kotlin
@Immutable
data class HomeTargetApp(
    val packageName: String,
    val label: String,
    val groupName: String?,
    val isAssigned: Boolean,
    val isAppEnabled: Boolean,
    val isGroupEnabled: Boolean,
    val isLsposedScoped: Boolean?,
)
```

Treat `isLsposedScoped == null` as "scope unknown / service unavailable." If LSPosed scope access is not implemented in this pass, rename the section to "Configured Target Apps" or "Protected Apps" and avoid claiming LSPosed scope.

Verification:
- Unit test a configured-but-unscoped package once scope data exists.
- Runtime check: LSPosed-scoped-only app should not appear as configured unless appConfig exists; configured-only app should display "LSPosed scope missing" instead of implying hookable.

## High: The requested global app off toggle needs assignment and enablement separated everywhere

Evidence:
- Existing `AppConfig` already has separate `groupId` and `isEnabled`.
- Current group Apps tab treats assignment as `groupId == groupId && isEnabled`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:353`.
- Current Home count also counts only `groupId == groupId && isEnabled`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:121`.
- `ConfigManager.setAppEnabled()` exists, but `ISpoofRepository` does not expose an app-enabled setter: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:394`, `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/ISpoofRepository.kt:49`.

Problem:
Your requested behavior is: if a listed app is globally off, it remains visible in group app lists but shows disabled / please enable, and no spoof applies. If implementation reuses current `isAssignedToGroup()` checks, disabling an app makes it look unassigned. That is special-case insanity caused by a bad UI predicate, not a data-model limitation.

Root cause:
UI helpers conflate app assignment with runtime enablement. The data shape is already capable of representing both; the UI helper predicates are too coarse.

Fix direction:
- Add a repository method such as `suspend fun setAppEnabled(packageName: String, enabled: Boolean)` to the narrow app-assignment contract or a new `SpoofAppEnablementRepository`.
- Implement it through `ConfigManager.setAppEnabled()`.
- In group app UI, define:

```kotlin
fun Map<String, AppConfig>.isAssignedToGroup(packageName: String, groupId: String?): Boolean =
    groupId != null && this[packageName]?.groupId == groupId

fun Map<String, AppConfig>.isEnabledForGroup(packageName: String, groupId: String?): Boolean =
    groupId != null && this[packageName]?.let { it.groupId == groupId && it.isEnabled } == true
```

- Show assigned-disabled apps as assigned but unavailable, with a row/card state like "Disabled from Home. Enable to spoof."
- Keep `ConfigSync` behavior unchanged: disabled app writes `app_enabled_<pkg> = false`, which correctly prevents hooks.

Verification:
- Unit test: assigned disabled app remains in assigned group UI state and sorts/labels as disabled.
- Config sync test: disabled app remains in `enabled_apps` only if the existing design intentionally keeps appConfigs keys as the allowlist, but per-package key must be false.
- Xposed test: disabled app never passes `isPackageCurrentlyEnabledForHooks()`.

## Medium: Current Targeted Apps UI is row-only, not the requested per-app card design

Evidence:
- `TargetedAppsList` renders a `Column` of `TargetedAppItem`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt:61`.
- `TargetedAppItem` is a plain `Row` with padding, not an `ExpressiveCard` or Material card: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt:73`.
- Existing app assignment rows already use `ExpressiveCard`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt:49`.

Problem:
This does not match the user's requested visual direction or the existing project card language. The result will look like a flat list embedded in Home, while the rest of the app uses expressive cards for operational rows.

Root cause:
The first implementation followed the plan's compact display list instead of the updated requirement for app cards with controls.

Fix direction:
Create a `HomeTargetAppCard` composable:
- Root `ExpressiveCard` or `Card` with shape `MaterialTheme.shapes.small` or medium depending on visual density.
- Leading app icon.
- Middle column: label, package, group/status line.
- Trailing `ExpressiveSwitch` for global app enablement.
- Optional status chip/icon row for `Scoped`, `Scope missing`, `Disabled`, `Group disabled`.

Do not reuse `AppListItem` directly unless it is refactored into slots. Its current checkbox semantics are assignment-specific, not global enablement.

Verification:
- Add previews for enabled, disabled, group-disabled, scope-missing, and empty states.
- Mobile MCP screenshot after implementation for compact width.

## Medium: The planned toggle needs clear semantics and wording to avoid overclaiming hook success

Evidence:
- Product Context says app launch or app-side service connection must not imply hook success.
- Current new section title is `Targeted Apps`: `app/src/main/res/values/strings.xml:1171`.

Problem:
A switch on a Home app card can be misunderstood as "this app is hooked now." It only controls Device Masker eligibility. LSPosed scope and process restart still matter.

Root cause:
There are three separate runtime gates: LSPosed scope, Device Masker app enablement, and group/spoof-type enablement. The current display has one `isEnabled` boolean.

Fix direction:
Use explicit copy:
- Section title: "Target Apps" or "Configured Apps" if scope is unknown.
- Card enabled subtitle: "Spoofing allowed by Device Masker."
- Disabled subtitle: "Disabled here. Enable before spoofing."
- Scope missing subtitle: "Not in LSPosed scope."
- Hook proof must stay in diagnostics/log evidence, not this Home list.

Verification:
- UI review confirms no label says "hooked" or "active" without LSPosed/logcat evidence.

## Medium: Missing tests for the new Home targeted app state

Evidence:
- The plan requires `HomeViewModelTargetedAppsTest.kt`, but no such file exists in the current diff.
- Existing `HomeViewModelTest` has no assertions for `targetedInstalledApps`: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModelTest.kt`.

Problem:
This logic is pure state derivation and easy to regress. It controls a user-facing safety gate; test coverage should come before expanding it with toggles.

Root cause:
The plan test task was not completed in the current uncommitted implementation.

Fix direction:
Add targeted tests for:
- configured + installed appears.
- configured + not installed is excluded.
- `android` and `system` excluded.
- disabled app appears but `isAppEnabled=false`.
- disabled group appears with `isGroupEnabled=false`.
- assigned disabled app remains assigned in GroupSpoofing UI helper tests after predicate split.

Verification:
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon`

## Medium: `TargetedApp` violates the project naming rule if kept in `HomeState.kt`

Evidence:
- `TargetedApp` is a public data class in `HomeState.kt`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt:16`.
- Project rules say public data classes should live in files matching their declaration name.

Problem:
This is likely to trigger or fight the Detekt `MatchingDeclarationName` policy. The Memory Bank notes a previous concern around this exact rule.

Root cause:
The plan text is internally inconsistent: it says create `TargetedAppsSection.kt` "data class only" but the implementation places the public model in `HomeState.kt`.

Fix direction:
Move the model to `HomeTargetApp.kt` or make it private/internal only if it truly never crosses file boundaries. Because `HomeScreenContent` and `TargetedAppsSection` both use it, a matching file is the clean path.

Verification:
- `.\gradlew.bat detekt --no-daemon`

## Low: Icon loading is duplicated and can be made a shared app-icon primitive

Evidence:
- `TargetedAppIcon` duplicates the async PackageManager icon pattern: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt:109`.
- `AppListItem` already contains similar `AppIcon`: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt:173`.

Problem:
This is small duplication now, but adding new app-card UI will create the third variant. It is not catastrophic, but it is random churn waiting to happen.

Root cause:
The existing `AppIcon` is private inside `AppListItem`, so new UI cannot reuse it.

Fix direction:
Extract a tiny `AppIcon` or `InstalledAppIcon` component only if implementing the card/toggle redesign. Keep it simple:
- Inputs: `packageName`, optional `label`, `modifier`.
- Fallback icon with null/decorative content description by default.
- No repository dependency.

Verification:
- Existing AppListItem previews still render.
- New HomeTargetAppCard previews render fallback icon without PackageManager.

## Low: Plan still contains stale implementation constraints after the new toggle/card requirement

Evidence:
- Plan says "read-only display items, not toggleable assignment rows."
- User now wants a switch button for each app and per-app card UI.

Problem:
The plan is now stale. Executing it as-is would build the wrong thing.

Root cause:
Requirements changed after plan creation.

Fix direction:
Revise the implementation plan before coding:
- Replace read-only list with card + toggle.
- Add repository app-enable method.
- Split assigned/scoped/enabled status.
- Update group app-list predicates.
- Add tests for disabled-but-assigned visibility.

Verification:
- Updated plan checklist maps each new requirement to a test or UI preview.

# Executive summary

The current Targeted Apps section is a reasonable first visual stub, but it is not ready for the behavior you want. The biggest issue is not Compose layout; it is the state model. The Home card needs to show and control app-level enablement while preserving assignment. Right now much of the UI treats `AppConfig.isEnabled=false` as "not assigned," which would make a disabled app disappear from groups instead of showing "disabled, please enable."

The best direction is to keep `JsonConfig.appConfigs` as the canonical table and use `AppConfig.groupId` for assignment, `AppConfig.isEnabled` for global per-app spoof eligibility, group enabled state for group-level eligibility, and LSPosed service scope as a separate optional readiness field. Do not add a second config path. Do not infer hook success from this Home card.

# Scope

Reviewed:
- User request for Home targeted app list, per-app cards, and global on/off switches.
- Plan: `docs/superpowers/plans/2026-05-15-home-targeted-apps-section.md`.
- Current uncommitted Home implementation:
  - `HomeScreen.kt`
  - `HomeState.kt`
  - `HomeViewModel.kt`
  - `TargetedAppsSection.kt`
  - `strings.xml`
- Related existing app assignment UI and repository/config APIs.
- Memory Bank and project/module rules.

Not run:
- Gradle builds/tests.
- Mobile MCP visual checks.
- Runtime LSPosed validation.

This was intentionally report-only.

# Source inventory

- `docs/AGENTS_PROJECT_RULES.md`
- `app/AGENTS.md`
- `memory-bank/projectbrief.md`
- `memory-bank/productContext.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `docs/superpowers/plans/2026-05-15-home-targeted-apps-section.md`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeState.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/TargetedAppsSection.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/ISpoofRepository.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`

# Project rule violations

- Public helper model `TargetedApp` is in `HomeState.kt`, which conflicts with the local naming rule for public data classes.
- The current section risks overstating LSPosed scope because it uses Device Masker config state, not LSPosed scope state.
- The proposed global app toggle will violate config-scope correctness if UI continues to conflate assignment with enablement.

# AGENTS.md and rule drift audit

No root rule needs changing. The root/app rules already say:
- `JsonConfig.appConfigs` is canonical.
- Do not use legacy `SpoofGroup.assignedApps`.
- Runtime sync must require explicit app assignment.
- UI counts/checked state should derive from canonical app config.

However, the implementation should clarify a missing practical rule in the plan, not AGENTS:

```text
For app assignment UI, `AppConfig.groupId` means assigned; `AppConfig.isEnabled` means runtime spoof eligibility. Do not use `isEnabled` to decide whether an app is assigned.
```

# Root cause analysis

The old app-list code used one predicate for two concepts: assigned and enabled. That was tolerable when the only UI action was "assign/unassign this app to this group." It breaks once Home has a global per-app off switch. The data model is not the problem; `AppConfig(groupId, isEnabled)` is the correct boring shape. The bogus shit would be adding a second "disabled apps" list or hiding disabled rows. Fix the helper predicates and UI models instead.

# Recommended fixes

1. Rename and reshape the model:

```kotlin
@Immutable
data class HomeTargetApp(
    val packageName: String,
    val label: String,
    val groupName: String?,
    val isAssigned: Boolean,
    val isAppEnabled: Boolean,
    val isGroupEnabled: Boolean,
    val isLsposedScoped: Boolean?,
)
```

2. Add an app enablement repository method:

```kotlin
interface SpoofAppAssignmentRepository {
    suspend fun addAppToGroup(groupId: String, packageName: String)
    suspend fun removeAppFromGroup(groupId: String, packageName: String)
    suspend fun setAppEnabled(packageName: String, enabled: Boolean)
    suspend fun setAppRiskyHooksEnabled(packageName: String, enabled: Boolean)
    suspend fun setAppClassLookupHidingEnabled(packageName: String, enabled: Boolean)
}
```

3. Wire Home switch intent:

```kotlin
fun setTargetAppEnabled(packageName: String, enabled: Boolean) {
    viewModelScope.launch { repository.setAppEnabled(packageName, enabled) }
}
```

4. Split GroupSpoofing predicates:

```kotlin
private fun Map<String, AppConfig>.isAssignedToGroup(packageName: String, groupId: String?): Boolean =
    groupId != null && this[packageName]?.groupId == groupId

private fun Map<String, AppConfig>.isEnabledInGroup(packageName: String, groupId: String?): Boolean =
    groupId != null && this[packageName]?.let { it.groupId == groupId && it.isEnabled } == true
```

5. Update `AppListItem` or add a new group app row state so disabled assigned apps show:
- assigned checkbox/lock state preserved.
- disabled text: "Disabled from Home. Enable to spoof."
- assignment removal remains possible if the user explicitly unticks/removes it.

6. Keep `ConfigSync` and `XposedEntry` gates unchanged. They already enforce no spoof when app is disabled.

# Architecture improvement opportunities

Use one small derived-state helper for app target rows:

```kotlin
internal fun buildHomeTargetApps(
    appConfigs: Map<String, AppConfig>,
    installedApps: List<InstalledApp>,
    groups: List<SpoofGroup>,
    scopedPackages: Set<String>?,
): ImmutableList<HomeTargetApp>
```

Make it `internal` so tests can call it directly without needing Turbine timing for every branch. This is not enterprise sludge; it is a pure data transform with meaningful safety behavior.

If LSPosed scope is added later, keep it app-side:
- `XposedPrefs` can expose scope state via a narrow wrapper around `XposedService.getScope()`.
- Home ViewModel combines scope state as another flow.
- Do not let Home scope state replace `appConfigs`; scope is injection readiness, not spoof eligibility.

# UI/UX review audit

Recommended card design:

- Section header: "Target Apps".
- Optional summary line: "`N enabled • M disabled`".
- Empty state: "No apps configured yet. Assign apps from a group to list them here."
- Each app as a card:
  - leading 40dp app icon.
  - headline app label.
  - supporting line package name or group name.
  - status chip: `Enabled`, `Disabled`, `Group disabled`, `Scope missing`, `Scope unknown`.
  - trailing `ExpressiveSwitch`.

Behavior:
- Switch off: keeps the app assigned; disables spoof eligibility; group app lists show disabled message.
- Switch on: re-enables app spoof eligibility if assigned group exists and is enabled.
- Group disabled: switch can remain on but card status should say group disabled; effective spoofing is off until group is enabled.
- Scope missing/unknown: do not block switch. It controls Device Masker config only. Show separate scope status.

Visual consistency:
- Use `ExpressiveCard`/small Material shape like `AppListItem`.
- Use existing `ExpressiveSwitch`, not a raw text button.
- Avoid nested cards. The section is unframed; app rows are the cards.
- Keep dense operational layout, not a marketing-style hero block.

Accessibility:
- Card switch needs a content description/state description such as "`Example App spoofing enabled`".
- App icon should usually be decorative (`contentDescription = null`) because the label is visible next to it.
- Text must survive long app names and package names with ellipsis.
- Touch target for the switch must stay at least 48dp.

# Best solution direction

Best path:

1. Rework current Targeted Apps section into `HomeTargetAppsSection`.
2. Use per-app cards with an `ExpressiveSwitch`.
3. Add repository `setAppEnabled`.
4. Split assignment and enablement predicates in GroupSpoofing.
5. Add tests before runtime validation.

This keeps the data structure honest:

```text
AppConfig.groupId      -> assigned to group
AppConfig.isEnabled   -> app-level spoof eligibility
Group.isEnabled       -> group-level spoof eligibility
SpoofType enabled     -> value-family eligibility
LSPosed scope         -> process injection readiness
LSPosed logs          -> actual hook proof
```

# Optional improvements

- Add a "Manage" click target on each card to open that app's assigned group screen.
- Add a small filter toggle: show enabled/all.
- Add a one-time "scope missing" action later if `XposedService.requestScope()` is implemented.
- Extract shared `InstalledAppIcon`.

# Proposed APIs, interfaces, dependencies, or tools

No new dependencies are needed.

Proposed API changes:
- `ISpoofRepository` app assignment contract: add `setAppEnabled(packageName, enabled)`.
- `FakeSpoofRepository`: implement the same method for tests.
- Optional future `XposedScopeStateRepository` or narrow wrapper around `XposedPrefs.xposedService?.scope`.

# Rejected or risky approaches

- Rejected: hide disabled apps from group app lists. That contradicts the requested UX and makes config state hard to recover.
- Rejected: add a second disabled-apps store. This is enterprise sludge; `AppConfig.isEnabled` already exists.
- Rejected: treat LSPosed scope as spoof enablement. Scope is injection permission, not Device Masker policy.
- Rejected: claim Home card means hooks are active. Hook proof belongs to LSPosed/logcat and target value checks.

# Verification plan

Static/unit:

```powershell
.\gradlew.bat spotlessCheck detekt :app:testDebugUnitTest --no-daemon
```

Recommended unit tests:
- `HomeTargetApps` includes configured+installed apps only.
- `android` and `system` never appear.
- disabled assigned app remains visible in Home.
- disabled assigned app remains assigned in GroupSpoofing list.
- switch calls `setAppEnabled`.
- disabled app sync produces per-package app enabled false.

Manual UI:
- Home empty state with no app configs.
- Home card list with 1 enabled, 1 disabled, 1 group-disabled app.
- Long app names/package names at compact width.
- Large font and dark/AMOLED mode.

Runtime:
- Disable app from Home.
- Force-stop/relaunch target app.
- LSPosed/logcat should not show target selection/hook registration for that app.
- Re-enable app, force-stop/relaunch, confirm hook events return.

# Residual risks and unknowns

- The current report did not run Gradle, so compile/Detekt state is not confirmed here.
- LSPosed scope-state integration is not in the current code, so the "LSPosed scoped" wording remains a product risk until implemented or renamed.
- The final visual shape needs screenshot/Mobile MCP verification after code changes.

# Suggested next tasks

1. Update the plan file to replace read-only rows with app cards and switches.
2. Add `setAppEnabled` through repository/fakes.
3. Split assigned/enabled predicates in GroupSpoofing.
4. Rename `TargetedApp` to `HomeTargetApp` in a matching file.
5. Implement `HomeTargetAppCard`.
6. Add unit tests, then run `spotlessCheck detekt :app:testDebugUnitTest`.
7. Only after that, run runtime LSPosed validation for off/on behavior.

# Report file path

`docs/internal/reports/active/audits/2026-05-15/2026-05-15-home-targeted-apps-section-audit.md`

# Write boundary confirmation

This audit workflow wrote exactly one report file and created only the minimum parent folder required for that report path. It did not modify source, config, tests, Memory Bank, build files, commits, branches, or runtime evidence.
