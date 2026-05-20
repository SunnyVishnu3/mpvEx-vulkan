# Device Masker :app UI And Architecture Audit

Date: 2026-05-16
Status: Active
Scope: `:app` Kotlin sources, current audit report content, relevant project rules, Memory Bank context, and official Android/Compose/Material guidance.

Write boundary: this workflow intentionally writes only this audit report. It does not modify source, tests, Memory Bank, build files, commits, branches, or raw evidence.

## Findings

### Critical

No critical confirmed findings.

The previous report treated several Compose lambda-allocation claims as critical. Those are not valid in this project because Compose strong skipping is enabled by default for Kotlin 2.0.20+ and the project is on a newer Kotlin line. Official Compose documentation says strong skipping makes restartable composables skippable and automatically remembers lambdas inside composables. Do not spend implementation time on replacing lambda fields or manually remembering ordinary item lambdas unless profiler/compiler evidence proves a real problem.

### High

#### H1. Service and data layers depend on UI package types

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/IDiagnosticsProvider.kt:4`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/IDiagnosticsProvider.kt:5`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/DefaultDiagnosticsProvider.kt:8`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/DefaultDiagnosticsProvider.kt:9`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ISettingsDataStore.kt:3`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/SettingsDataStore.kt:10`

Problem:
`service/` imports `ui.screens.diagnostics.AntiDetectionTest` and `DiagnosticResult`; `data/` imports `ui.theme.ThemeMode`. This reverses the intended dependency direction. A non-UI layer should not need UI packages to compile.

Official basis:
Android architecture recommendations strongly recommend clearly defined UI and data layers, with data exposed to the UI through repositories and ViewModels. Layer models can be mapped when needed.

Root cause:
UI display models were reused as service/data contracts instead of being placed in a shared app model package.

Fix direction:
- Move diagnostics result contracts to a non-UI package such as `app/src/main/.../data/models/diagnostics/`.
- Move `ThemeMode` to a non-UI settings model package, or introduce a data-layer enum and map it to UI display state.
- Keep UI-only labels, icons, and string resources in UI.

Verification:
- `rg "import com\\.astrixforge\\.devicemasker\\.ui" app/src/main/kotlin/com/astrixforge/devicemasker/service app/src/main/kotlin/com/astrixforge/devicemasker/data`
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

#### H2. Diagnostics refresh and loading can complete out of order and can get stuck on exceptions

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt:47`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt:54`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt:60`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt:61`

Problem:
`refresh()` launches a coroutine, delays, calls `runDiagnostics()`, and immediately sets `isRefreshing = false`. But `runDiagnostics()` launches a second coroutine. The refresh spinner can stop before diagnostics finish. If `runDiagnosticTests()` or `runAntiDetectionTests()` throws, `isLoading` is not reset and the exception escapes the child coroutine.

Official basis:
Android architecture guidance recommends ViewModels expose UI state with loading and error signals. Kotlin coroutine best practices require exceptions to be handled at the boundary that can recover. The Compose side-effects guidance also warns against hidden lifecycle work; the same rule applies to nested async work in state holders.

Root cause:
The diagnostics state machine is split across nested `launch` calls instead of one suspendable refresh operation with a single `try/finally` boundary.

Fix direction:
- Make diagnostics execution a `private suspend fun runDiagnosticsInternal(): DiagnosticsResult`.
- In `refresh()`, do one `viewModelScope.launch { try { ... } catch { ... } finally { isRefreshing=false; isLoading=false } }`.
- Add an error field to `DiagnosticsState` so failures are visible instead of silently leaving stale data.
- Cancel or sequence overlapping diagnostics if refresh can be tapped repeatedly.

Verification:
- Add a fake repository/provider test that throws and assert `isLoading=false`, `isRefreshing=false`, and error state is set.
- Run `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsViewModelTest --no-daemon`.

#### H3. Navigation back stack is publicly mutable

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt:65`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainNavigationContent.kt:140`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt:109`

Problem:
`visibleBackStack` is exposed as a public `mutableStateListOf`. Any caller can clear, push, or reorder it without going through `DeviceMaskerNavigator`. That breaks the navigation state data model.

Official basis:
Compose state hoisting guidance expects state owners to expose state and receive events, not leak mutable internals. Android architecture guidance recommends unidirectional data flow from state holders to UI.

Root cause:
The object exposes its internal mutable snapshot list because `NavDisplay` needs a mutable back stack.

Fix direction:
- Keep a private mutable list for `NavDisplay`.
- Expose read-only navigation state separately.
- If Navigation 3 requires a mutable list at the call site, restrict visibility to the navigation package or provide a narrow accessor used only by `DeviceMaskerNavDisplay`.

Verification:
- Unit test that public callers cannot mutate the visible stack directly.
- Existing navigation tests should still pass.

#### H4. `SpoofRepository.setActiveGroup()` performs multi-write default-group updates

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt:130`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt:134`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt:138`

Problem:
The active group change writes one group as default, then loops over other default groups and writes each one separately. During that sequence, persisted config and RemotePreferences sync can briefly represent multiple default groups.

Project basis:
Project rules say `JsonConfig.appConfigs` and config state must be canonical and coherent. The architecture guide emphasizes stable stored config and RemotePreferences sync as the cross-process delivery path.

Root cause:
The config API exposes only per-group updates for this operation, so a multi-entity invariant is enforced through repeated writes.

Fix direction:
- Add a single config transform operation that maps all groups in one `JsonConfig` update.
- Keep `SpoofRepository.setActiveGroup()` as a facade but make the actual mutation atomic in `ConfigManager`.

Verification:
- Add a unit test that active-group switching emits no intermediate config with more than one default group.
- Run `.\gradlew.bat :app:testDebugUnitTest --tests '*SpoofRepository*' --tests '*ConfigManager*' --no-daemon`.

#### H5. Theme status colors bypass Material color roles and dynamic color

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Color.kt:57`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Color.kt:58`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Color.kt:59`
- Used in `GroupCard.kt`, `StatusIndicator.kt`, `DiagnosticsModuleStatusCard.kt`, `DiagnosticsScreen.kt`, and `HomeScreen.kt`.

Problem:
`StatusActive`, `StatusInactive`, and `StatusWarning` are fixed colors. They do not adapt to dynamic color, AMOLED, or contrast preference. Several components also use fixed white icon tint on status backgrounds.

Official basis:
Material 3 guidance says custom components should use appropriate color roles and matching `on*` content roles to preserve contrast. Android accessibility guidance recommends 4.5:1 contrast for normal text and 3:1 for larger text/non-text where applicable.

Root cause:
Status colors are global constants rather than semantic roles derived from `MaterialTheme.colorScheme`.

Fix direction:
- Introduce a small status color model derived in composition, for example success/error/warning container and content colors.
- Prefer `primaryContainer/onPrimaryContainer`, `errorContainer/onErrorContainer`, and a theme-aware warning pair rather than fixed hex values.
- Add screenshot or accessibility checks for light, dark, AMOLED, dynamic color, and high contrast.

Verification:
- Compose UI accessibility checks or Accessibility Scanner for contrast.
- Manual light/dark/dynamic/AMOLED screenshot pass.

### Medium

#### M1. Dynamic AMOLED color scheme omits high surface containers

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt:198`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt:204`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt:205`

Problem:
`dynamicAmoledColorScheme()` overrides `background`, `surface`, `surfaceContainer`, `surfaceContainerLow`, and `surfaceContainerLowest`, but not `surfaceContainerHigh` or `surfaceContainerHighest`. Components using higher surface containers can pull generated dynamic dark colors instead of the AMOLED palette.

Official basis:
Material 3 color schemes define container roles as semantic surface tokens. If an AMOLED mode intentionally overrides surface behavior, the override should cover every used surface container role.

Fix direction:
Copy the complete AMOLED surface container set from `AmoledDarkColorScheme` into `dynamicAmoledColorScheme()`.

Verification:
- Screenshot cards using `surfaceContainerHigh` and `surfaceContainerHighest` in dynamic AMOLED mode.

#### M2. `@Immutable` is applied to wrappers around ordinary Kotlin collections

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedScopeState.kt:9`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/sheet/CountryPickerSheet.kt:42`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/sheet/TimezonePickerSheet.kt:41`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SimCardUiState.kt:24`

Problem:
Several `@Immutable` types wrap `Set` or `List`. Official Compose stability docs warn that `@Immutable` is a compiler contract and ordinary Kotlin collections are treated as unstable because the compiler cannot prove immutability.

Official basis:
Compose stability docs recommend immutable collections from `kotlinx.collections.immutable` or a carefully controlled wrapper when collection immutability matters. They also warn that annotations do not make mutable objects immutable.

Fix direction:
- Use `ImmutableSet` / `ImmutableList` for these wrappers, or remove `@Immutable` if the type is not part of a skip-sensitive public UI boundary.
- For `XposedScopeState.Connected`, prefer `ImmutableSet<String>` because it is a state object consumed by UI.

Verification:
- Compile with Compose compiler stability reports enabled and confirm these types are stable or intentionally not annotated.

#### M3. Diagnostics category filtering is done repeatedly inside `LazyColumn` construction

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt:166`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt:167`

Problem:
For each `SpoofCategory`, the composable filters the full diagnostics list and converts it to an immutable list while constructing lazy items. Today the list is small, so this is not a performance emergency, but the code scales as repeated O(categories * results) work.

Official basis:
Compose performance guidance says expensive calculations in composition should be cached with `remember` when inputs are stable.

Fix direction:
- Build a `Map<SpoofCategory, ImmutableList<DiagnosticResult>>` with `remember(diagnosticResults)`.
- Iterate the precomputed map in the lazy list.

Verification:
- Existing diagnostics UI tests and a simple unit test for category grouping if extracted.

#### M4. Dialog composables do not expose `modifier`

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/dialog/GroupsDialog.kt:35`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/dialog/GroupsDialog.kt:100`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/dialog/GroupsDialog.kt:144`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/dialog/StandardDialogs.kt:28`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/dialog/StandardDialogs.kt:78`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/dialog/ThemeModeDialog.kt:29`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt:298`

Problem:
Reusable composables that emit UI should accept a `modifier` parameter and apply it to the root UI element. These dialogs cannot be placed, tested, or constrained by callers without modifying internals.

Official basis:
Compose modifier docs say it is a best practice for composables to accept a modifier parameter and pass it to the first child that emits UI.

Fix direction:
Add `modifier: Modifier = Modifier` after required parameters and pass it to the root `AlertDialog`.

Verification:
- `.\gradlew.bat spotlessApply spotlessCheck :app:compileDebugKotlin --no-daemon`

#### M5. `GroupsScreenBody` drops its own modifier before calling content

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreenBody.kt:29`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreenBody.kt:42`

Problem:
The outer `Box` respects the caller modifier, but the child `GroupsScreenContent` receives `Modifier.fillMaxSize()` instead of the parent-supplied constrained modifier. This is only safe because the Box currently owns the entire content area; it becomes wrong if the body is reused under a constrained parent.

Official basis:
Compose modifier guidance says modifier ownership belongs to the parent and should be forwarded predictably.

Fix direction:
Use a clearly named content modifier or rely on the Box as the placement boundary. If `GroupsScreenContent` is a full child of the Box, record that as intentional; otherwise forward the modifier shape consistently.

Verification:
- Preview/screenshot check for Groups content under compact and wider layout.

#### M6. Searchable picker sheet state is not saveable

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/sheet/CountryPickerSheet.kt:54`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/sheet/TimezonePickerSheet.kt:53`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/LocationContent.kt:58`

Problem:
Search query and timezone-sheet visibility use `remember`, so rotation/configuration change resets them. This is UI element state the user can edit, and losing it is unnecessary.

Official basis:
Compose state docs recommend `rememberSaveable` for UI element state that should survive configuration changes.

Fix direction:
Use `rememberSaveable` for sheet visibility and search query strings.

Verification:
- Compose state restoration test or manual rotate check.

#### M7. `LaunchedEffect(Unit)` captures changing initial scroll inputs

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/SpoofTabContent.kt:67`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt:308`

Problem:
Both effects use `Unit` while reading `initialScrollPosition`. If the composable remains in composition and the initial position changes, the effect does not restart.

Official basis:
Compose side-effect docs say variables used in an effect should be keys, or wrapped with `rememberUpdatedState` when restarts are intentionally avoided. They warn that constant keys should be used carefully.

Fix direction:
Key by `initialScrollPosition` and the relevant stable list identity, or remove the second scroll effect if `rememberLazyListState(initialFirstVisibleItemIndex=...)` already covers the intended initialization.

Verification:
- Unit/UI test that switching tabs restores updated saved scroll positions.

#### M8. `ConfigManager.resetForTests()` does not cancel in-flight initialization work

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:58`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:91`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:108`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt:420`

Problem:
The singleton owns a long-lived `CoroutineScope`. `resetForTests()` increments generation and resets state but cannot cancel already running work. `loadConfig()` writes `_config.value` before the generation check that only guards `_isInitialized`. A stale init job can still write config after a test reset.

Official basis:
Coroutine best practices require clear ownership and cancellation for long-lived work. Android test guidance recommends deterministic StateFlow assertions and test doubles.

Fix direction:
- Make the scope injectable/recreatable for tests, or track and cancel the init job in `resetForTests()`.
- Guard all `_config` writes in `loadConfig()` by generation, not only `_isInitialized`.

Verification:
- Stress test repeated `resetForTests()` + `init()` with different files and assert no stale config emission.

#### M9. `AppLogStore` can block readers indefinitely if the writer coroutine dies

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt:43`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt:45`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt:86`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt:120`

Problem:
`flushPendingWrites()` waits while `pendingEvents > 0`. If the writer coroutine terminates and later `appendEvent()` increments `pendingEvents`, no writer remains to decrement it. The loop uses 1-second waits repeatedly, but no max wait count or writer health check exists.

Root cause:
The write channel has no lifecycle/error state exposed to the synchronous flush path.

Fix direction:
- Catch and log append failures inside the writer loop so the coroutine continues.
- Track writer failure state and bound flush time.
- Prefer making reads suspend on IO, or return last durable events without blocking forever.

Verification:
- Fake `JsonlDiagnosticStore.append()` failure test and assert `readDiagnosticEvents()` returns or reports failure within a bounded timeout.

#### M10. Sim carrier UI still has hardcoded English strings

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SimCarrierControls.kt:188`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SimCarrierControls.kt:218`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SimCarrierControls.kt:223`

Problem:
Visible labels such as `Select carrier`, `SIM Country`, and `Network Operator` are hardcoded. This blocks localization and makes TalkBack output inconsistent with the rest of the app.

Official basis:
Android accessibility guidance recommends localized descriptions/labels so accessibility services can present content in the user's language.

Fix direction:
Move labels to `strings.xml`; use existing spoof type display resources where possible.

Verification:
- `rg '"(Select carrier|SIM Country|Network Operator)"' app/src/main`
- `.\gradlew.bat :app:lintDebug --no-daemon`

### Low

#### L1. Section expand/collapse content descriptions are hardcoded

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/SectionHeader.kt:172`

Problem:
The expand icon announces `"Collapse"` / `"Expand"` directly from source instead of localized string resources.

Fix direction:
Pass localized strings to the header or read `R.string.action_expand` / `R.string.action_collapse`.

#### L2. App icon fallback has a hardcoded content description

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppListItem.kt:226`

Problem:
Fallback icon uses `"App icon"` directly. It is also likely redundant if the row already announces the app label.

Fix direction:
Use `contentDescription = null` if decorative, or a localized string if it conveys unique information.

#### L3. Dynamic `CompositionLocalProvider` for motion is duplicated

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt:117`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt:161`

Problem:
`MainActivityContent` provides `LocalMotionPolicy`, then `DeviceMaskerThemeInternal` provides it again for the themed subtree. Nested providers are legal, but the outer provider currently has no clear consumer in the shown subtree.

Fix direction:
Keep the provider in one owner. If the outer provider is needed for edge-to-edge or pre-theme work, document the consumer; otherwise remove it in a normal source-change task.

#### L4. Header `LazyColumn` items lack stable keys

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt:265`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt:127`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/SpoofTabContent.kt:86`

Problem:
Several singleton/header lazy items rely on implicit positional keys. This is low risk for static headers but easy to make robust.

Fix direction:
Add explicit `key` and `contentType` values to singleton lazy items when nearby list content is keyed.

#### L5. Report lifecycle and filename were non-compliant

Evidence:
- Previous file path: `docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md`
- Project rule path shape: `docs/internal/reports/<active|closed>/<category>/YYYY-MM-DD/YYYY-MM-DD-short-topic-kebab-case.md`

Problem:
The old report filename was not date-prefixed. The content also mixed corrected findings, duplicated stale Top 10 entries, and post-audit implementation notes.

Fix direction:
This report replaces it at the compliant path:
`docs/internal/reports/active/audits/2026-05-16/2026-05-16-app-ui-architecture-audit.md`

## Rejected Findings From The Old Report

These should not be carried into implementation plans without new evidence:

- `ActionItem` / `QuickAction` lambda fields are not a confirmed Compose performance bug. Strong skipping treats lambdas as stable and auto-memoizes lambdas inside composables.
- `navigationBackHandler` does not need manual `remember` for the old report's stated reason.
- Lambdas inside `LazyColumn.items` are not automatically a high-severity allocation bug under strong skipping.
- Animation labels with string interpolation are tooling labels, not a production performance finding.
- `CompactExpressiveIconButton` being visually 36dp is not enough to prove a 48dp accessibility violation if Material minimum interactive sizing is applied. Verify actual semantics/touch bounds before filing.
- Multiple independent Flow collectors are not automatically wrong. File a finding only when they create a proven inconsistent UI state or redundant work.
- A global uncaught exception handler should not be recommended blindly for an Xposed-adjacent app/module. It can interfere with host processes if placed in the wrong module. Any crash logging must be app-process only and narrowly scoped.

## Executive Summary

The current `:app` UI and state-holder code is broadly healthy: lifecycle-aware state collection is used, most screen state is immutable at the UI boundary, Navigation 3 is established, and recent Home scoped-app changes have tests. The real problems are not the old report's broad lambda/performance claims. The real problems are data ownership and state-machine shape:

- UI model types leaked down into service/data packages.
- Diagnostics uses nested coroutines with weak error handling.
- Navigation exposes mutable back-stack internals.
- Active-group switching spans multiple config writes.
- Theme status colors bypass dynamic/contrast-aware Material roles.

The old report had useful raw observations, but its recommendations were polluted by corrected non-issues. Treat this rewritten report as the source of truth for this audit.

## Scope

Reviewed:
- `app/src/main/kotlin/com/astrixforge/devicemasker/**`
- `docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md`
- `docs/AGENTS_PROJECT_RULES.md`
- `docs/public/ARCHITECTURE.md`
- `app/AGENTS.md`
- Memory Bank files
- Official Android/Compose/Material docs through Google Developer Knowledge MCP
- Local skills:
  - `device-masker-review`
  - `compose-modifier-and-layout-style`
  - `compose-recomposition-performance`
  - `compose-state-authoring`
  - `compose-side-effects`
  - `compose-state-hoisting`
  - `material-3-expressive`

Not reviewed:
- Runtime screenshots through Mobile MCP.
- Compose compiler stability reports.
- Full UI tests or accessibility scanner output.
- `:xposed` hook implementation details, except project rule context.

## Source Inventory

Current working tree at audit time was dirty before this report update:

- Existing modified app UI files: `DiagnosticsScreen.kt`, `HomeScreen.kt`, `SettingsSections.kt`
- Existing modified root `AGENTS.md`
- Existing deleted/moved skill files under `.agents/skills/jetpack-compose/`
- Existing untracked audit folder containing the old report

Those pre-existing changes were treated as current code for review evidence, but this workflow did not modify them.

## Official Documentation Inputs

Google Developer Knowledge MCP was used for:

- Compose strong skipping and lambda memoization:
  `https://developer.android.com/develop/ui/compose/performance/stability/strongskipping`
- Compose stability and immutable collections:
  `https://developer.android.com/develop/ui/compose/performance/stability/fix`
- Compose modifiers:
  `https://developer.android.com/develop/ui/compose/modifiers`
- Compose side effects:
  `https://developer.android.com/develop/ui/compose/side-effects`
- Android architecture recommendations:
  `https://developer.android.com/topic/architecture/recommendations`
- Compose accessibility and touch targets:
  `https://developer.android.com/develop/ui/compose/accessibility/api-defaults`
- Android accessibility guidance:
  `https://developer.android.com/guide/topics/ui/accessibility/apps`
- Material 3 color/accessibility guidance:
  `https://developer.android.com/develop/ui/compose/designsystems/material3`

## Project Rule Violations

- The old report filename violated the internal report naming rule.
- The old report mixed confirmed, corrected, and duplicate findings in a way that would generate random churn if followed literally.
- `service/` -> `ui/` and `data/` -> `ui/theme/` imports violate the project module boundary intent documented in `app/AGENTS.md`.

## AGENTS.md And Rule Drift Audit

Confirmed current rules still match the project direction:

- Home Scoped Apps must read LSPosed scope and installed-app metadata, not spoof groups.
- `JsonConfig.appConfigs` is canonical for active app assignment and enablement.
- `AppConfig.isEnabled` must survive group assignment/unassignment.
- RemotePreferences-first config delivery remains the only accepted config path.
- Report path rules require active/closed category/date/date-prefixed filename.

Potential rule clarification:

- `app/AGENTS.md` says "State-backed collection parameters should use immutable collection types." The code still has `@Immutable` wrappers around `List`/`Set`; either the code should be fixed or the rule should explicitly call out this common failure mode.
- Root `AGENTS.md` currently has uncommitted local edits. This audit did not judge whether those edits should be committed.

## Root Cause Analysis

The confirmed issues cluster around three patterns:

1. **Shared contracts live in the wrong layer.** Diagnostics and theme preference models were placed where they were first needed, then reused downward. That made package dependencies backwards.
2. **State machines are split across convenience APIs.** Diagnostics and active-group switching use multiple small operations for what should be one coherent transaction.
3. **UI tokens started as constants.** Fixed status colors and hardcoded labels were quick to add, but they bypass dynamic color, localization, and accessibility verification.

## Recommended Fix Order

1. Move diagnostics and settings model contracts out of UI packages.
2. Rewrite diagnostics refresh as one coroutine with explicit loading/error/finally behavior.
3. Hide mutable navigation internals behind a narrow API.
4. Make active-group switching one atomic config update.
5. Replace fixed status colors with theme-derived semantic status roles.
6. Fix `@Immutable` collection wrappers.
7. Localize hardcoded UI strings and content descriptions.
8. Add missing modifiers to dialog composables.
9. Clean up low-risk lazy item keys and saveable picker state.

## Best Solution Direction

Do not launch a broad UI rewrite. The safe path is a sequence of small patches with tests:

- Patch 1: model/package ownership cleanup.
- Patch 2: diagnostics state-machine tests and fix.
- Patch 3: navigation API encapsulation.
- Patch 4: atomic active-group config transform.
- Patch 5: theme/accessibility polish.

This avoids enterprise sludge and keeps every changed line tied to a confirmed problem.

## Verification Plan

For code fixes derived from this report:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --no-daemon
.\gradlew.bat :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

For UI/theme/accessibility fixes:

- Run Compose previews or screenshots for compact and expanded widths.
- Run Accessibility Scanner or Compose accessibility checks for contrast/touch target issues.
- Manually verify light, dark, AMOLED, dynamic color, and high-contrast mode.

For config/active-group fixes:

- Add unit tests proving no intermediate config has multiple default groups.
- If RemotePreferences sync is touched, inspect generated preference keys and run app tests.

## Bogus Shit Detector

- Replacing lambda fields because of the old report is bogus shit unless compiler/profiler evidence shows it matters.
- Adding callback-holder classes only to reduce parameter count is enterprise sludge unless it improves API clarity for a real call site.
- Treating every `LaunchedEffect(Unit)` as a bug is hand-wavy bullshit. It is only a bug when it captures changing values that should restart the effect.
- Treating every separate Flow collector as wrong is also hand-wavy. Confirm inconsistent states or redundant expensive work first.
- Broad rewrites of `SpoofRepository` are random churn unless they target a specific proven invariant such as atomic active-group switching.

## Residual Risks And Unknowns

- No runtime UI screenshots were captured during this audit.
- No Compose compiler stability report was generated, so stability findings are source-level, not compiler-output-confirmed.
- Dirty pre-existing source changes may affect some line numbers after this report is committed or rebased.
- Accessibility contrast claims need scanner or screenshot-based validation before closure.

## Suggested Next Tasks

1. Fix `service/` and `data/` layer imports from UI packages.
2. Add failing tests for diagnostics exception/refresh behavior, then fix the ViewModel.
3. Add a navigation state test proving external callers cannot mutate the back stack.
4. Add a config test for atomic active-group switching.
5. Convert status colors to theme-derived semantic roles and run visual/accessibility checks.

## Report File Path

`docs/internal/reports/active/audits/2026-05-16/2026-05-16-app-ui-architecture-audit.md`

## Write Boundary Confirmation

This report update replaced the non-compliant `comprehensive-ui-audit.md` filename with a date-prefixed report file. No source, test, Memory Bank, build, commit, branch, or raw evidence file was intentionally changed by this audit workflow.
