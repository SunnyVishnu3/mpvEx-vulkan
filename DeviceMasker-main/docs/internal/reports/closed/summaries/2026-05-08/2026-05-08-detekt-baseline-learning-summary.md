# Detekt Baseline Learning Summary

**Updated:** 2026-05-08  
**Scope:** Current Detekt baseline after maximum-strictness cleanup  
**Status:** Active learning/remediation guide

## Current State

Detekt is running with `allRules=true` and strict failures enabled. The project baseline is now empty.

| Stage | Baseline entries |
| --- | ---: |
| Initial strict baseline | 214 |
| After first cleanup | 162 |
| After continued cleanup | 123 |
| Current | 0 |

Current module split:

| Module | Entries |
| --- | ---: |
| `:app` | 0 |
| `:common` | 0 |
| `:xposed` | 0 |

## Current Rule Counts

| Rule | Count | Main cause |
| --- | ---: | --- |
| `CognitiveComplexMethod` | 0 | Cleared |
| `LongMethod` | 0 | Cleared |
| `TooManyFunctions` | 0 | Cleared |
| `ComplexInterface` | 0 | Cleared |

## What We Already Fixed

- Replaced wildcard imports with explicit imports.
- Extracted many magic numbers into named constants.
- Replaced simple binary `when` expressions with `if`.
- Moved secondary UI data classes into matching files.
- Renamed callback parameters that violated Detekt naming rules.
- Converted simple UI list parameters to `ImmutableList` where state already used immutable collections.
- Split long string literals in tests.
- Logged swallowed import failures instead of silently ignoring them.
- Reduced common return-count/cyclomatic issues in config, location, IMSI, network type, and persona lookup code.
- Split `AppListItem` and `SpoofValueCard` into smaller composable helpers.
- Removed navigation return-count findings by moving URI validation into helpers and making back behavior expression-based.
- Removed app-side broad exception catches from `LogManager` and `ConfigManager` with typed recovery paths.
- Reduced `PersonaGenerator.resolveSubscriptions` cyclomatic complexity by centralizing override/default value resolution.
- Reduced `:common` baseline to zero by splitting broad model/key/persona helpers into focused extension/helper files.
- Reduced `:xposed` baseline to zero by keeping hook behavior stable while splitting repeated hook registration into focused helpers.
- Removed `ViewModelInjection` findings by moving manual ViewModel construction to `ViewModelProvider.Factory` helpers and composable default parameters, following Android's factory pattern for ViewModels with dependencies.
- Split `MainActivity` root/edge-to-edge setup into focused effects and reduced `AnimatedSection` into header/body helpers.
- Split `CategorySection`, `GroupCard`, Home status/group selector UI, and Diagnostics module status UI into focused helper files/components.

## Lessons From The Actual Git Diff

These are the concrete mistakes the cleanup fixed. Future code should avoid repeating them.

### Constants Instead Of Mystery Numbers

Fixed examples:
- `JsonlDiagnosticStore`: `padStart(3, '0')` became a named file-index width.
- `RootShell`: output byte limits and stderr summary length became named constants.
- `SpoofValueCard`: mask length, visible suffix length, MAC part count, and byte width became named constants.
- `IMEIGenerator`, `ICCIDGenerator`, `UUIDGenerator`, `SerialGenerator`, `MACGenerator`: identifier lengths, checksum positions, and bit masks became domain constants.

Rule: if a number means something, name it. Raw numbers are acceptable only for obvious zero/one math, small local indexes, or Compose token calls where the meaning is already in the API.

### Long Strings Become Builders Or Helpers

Fixed examples:
- `RootCaptureStore` now builds manifest JSON through a helper instead of one long inline string.
- `RootShell` now builds command manifest JSON with `buildString`.
- Long test assertions and joined strings were split so the assertion remains readable.

Rule: long JSON, shell output summaries, and assertion text should be built by a helper. Do not hide dense string construction inside the main control flow.

### Compose API Shape

Fixed examples:
- `DeviceMaskerMainApp` now accepts a `modifier`.
- Callback parameters were renamed away from repeated `on*` parameter patterns where Detekt flags them.
- Effects that capture callbacks now use `rememberUpdatedState`.
- Simple reusable composables now take `ImmutableList` where state already provides immutable lists.
- Previews use `persistentListOf()` instead of plain mutable/list literals where the API expects immutable collections.
- Screen-level ViewModels with constructor dependencies now use explicit `ViewModelProvider.Factory` helpers.
- Compose screen APIs now put `viewModel(factory = ...)` in default parameters instead of constructing ViewModels inside the composable body.
- `MainActivity` now passes dependencies to route composables instead of constructing route ViewModels inline.

Rule: reusable composables should have stable inputs: `modifier`, immutable collections, current callbacks inside effects, and no callback naming churn that fights the configured rules. For ViewModels, pass dependencies into the screen route and keep default ViewModel creation at the parameter boundary.

### File Names Match Public Types

Fixed examples:
- `ActionItem`, `QuickAction`, and timezone row models were moved toward files matching their declarations instead of being hidden inside unrelated component files.

Rule: public or reusable helper models get their own matching file. Keeping them inside a random composable file creates `MatchingDeclarationName` noise and makes reuse harder to track.

### Data Tables Beat Branch Piles

Fixed examples:
- `NetworkTypeMapper` moved away from a large branch pile toward set/prefix based lookup.
- `DevicePersona.getValue` moved toward lookup-style access instead of repeated branching.
- `IMSIGenerator` and location helpers reduced guard/return noise by shaping lookup data better.

Rule: if the logic is pure mapping, use a table, map, set, or prefix list. A huge `when` for static data is special-case insanity and will keep triggering complexity rules.

### Narrow And Logged Error Recovery

Fixed example:
- `SpoofRepository.importGroups` no longer catches broad `Exception` silently. It catches known parse/argument failures and logs recovery context.

Rule: fallback is allowed, silent fallback is not. Catch the failure you expect, log why recovery happened, and keep broad catches out of app-side code unless there is a real boundary.

### Imports Stay Explicit

Fixed examples:
- Generator and model files in `:common` dropped wildcard imports.
- Random source imports were made explicit or aliased when the file used more than one random API.

Rule: no wildcard imports in normal source. If two APIs have similar names, alias the one that would confuse readers.

### Xposed Baseline Needs Different Discipline

Fixed examples:
- `DeviceHooker` now uses a small telephony getter table instead of a long pile of repeated hook blocks.
- `AntiDetectHooker` delegates PackageManager hiding to `AntiDetectPackageManagerHooks`, so orchestration and package-query hiding are separate.
- `SensorHooker`, `WebViewHooker`, `AdvertisingHooker`, and `SubscriptionHooker` split independent hook registrations into focused helpers.
- `PrefsHelper` moved to a matching file, and unused hook parameters were removed only where they were not part of a required caller contract.

Observed from cleanup:
- Xposed complexity can be removed safely when method discovery, hook registration, and callback behavior are separated.
- Data tables beat copy-pasted hook blocks for repeated framework getters.
- Moving one hook family to a focused helper object is cleaner than letting one object become a dumping ground.
- Renaming unused Xposed parameters to `_param` can trade `UnusedParameter` for naming noise; remove the parameter only when current callers do not require it.
- Complexity inside hookers must be split only when hook isolation, fallback behavior, and R8 callback shape remain intact.

Rule: Xposed cleanup must be runtime-safe first. Load the libxposed skill, keep `stableHooker`, rethrow `XposedFrameworkError`, avoid target-process random generation, and verify with compile, Detekt, R8 ABI guard, and LSPosed/logcat evidence before claiming hook safety.

### App Cleanup Lessons From Latest Diff

Fixed examples:
- `HomeScreen`, `GroupsScreen`, `DiagnosticsScreen`, and `GroupSpoofingScreenContent` stopped receiving prebuilt ViewModels from navigation entries. They now receive real dependencies and create ViewModels through small factory helpers.
- `DeviceMaskerViewModelFactories.kt` keeps manual dependency wiring in one place without adding Hilt or enterprise sludge.
- `MainActivityEffects.kt` split startup root capture, root warning display, and edge-to-edge styling from `MainActivityContent`.
- `ShowRootWarningWhenUnavailable` uses `rememberUpdatedState` for the callback captured by `LaunchedEffect`.
- `AnimatedSection` was split into focused header, title, and body composables.
- `CategorySection` now separates category header, correlated action row, special category routing, and standard item rendering.
- `GroupCard` now separates header, title block, stats, and action buttons.
- Home group-selector helpers moved to `HomeGroupSelector.kt` to avoid file-level `TooManyFunctions`.
- Diagnostics module status moved to `DiagnosticsModuleStatusCard.kt` for the same reason.
- Config sync state mapping moved to `ConfigSyncHelpers.kt`, removing branch/return noise from `syncApp` and `buildSnapshot`.
- `SectionHeader`, `ExpressiveSwitch`, SIM card, device hardware, and location category UI were split into state extraction plus focused section/row composables.
- Plain `List` parameters introduced Compose stability warnings in picker helpers; stable wrapper data classes fixed the warning without adding a new dependency.
- SIM carrier options use an `@Immutable` wrapper so carrier lists do not leak as unstable composable parameters.

Rule: when Detekt flags a big Compose/navigation body, split along real UI/effect boundaries. Do not add wrapper composables that only forward a ViewModel; that triggers `ViewModelForwarding` and hides the actual dependency flow.

### Latest App Cleanup Lessons

Fixed examples:
- `ConfigSync` now asks `JsonConfig.syncStateFor(packageName)` for a shaped app sync state, then writes it through small `SharedPreferences.Editor` helpers. The sync class no longer owns every key/value branch.
- `ExpressiveSwitch` keeps animation state in `ExpressiveSwitchState.kt` and dimensions in `SwitchDimensions.kt`; the component file now emits UI instead of mixing targets, colors, and layout constants.
- Picker dialogs keep filtered data local or wrapped in stable option models. Passing raw `List<T>` through reusable composables caused `UnstableCollections`.
- `SIMCardCategoryContent`, `DeviceHardwareCategoryContent`, and `LocationCategoryContent` now extract UI state first, then render focused cards/rows/items.
- Moving SIM carrier controls to `SimCarrierControls.kt` avoided file-level `TooManyFunctions`, but the extracted dropdown button had to receive `Modifier.menuAnchor()` from the `ExposedDropdownMenuBox` scope. Scope-specific modifiers should be passed in, not rebuilt after extraction.
- Helpers that emit more than one independent child need a parent layout; otherwise Compose rules flag `MultipleEmitters`.
- Apps tab filtering now lives in `rememberFilteredApps`, and the filtered list is converted to `ImmutableList` before crossing composable boundaries.
- Groups file import/export launchers moved to `GroupsFileActions.kt`; dialogs receive plain callbacks instead of a ViewModel to avoid `ViewModelForwarding`.
- `FloatingActionButtonMenuItem` must stay inside `FloatingActionButtonMenu` scope; extracting it to a standalone composable breaks compilation.
- Settings sections moved to `SettingsSections.kt`, while the outer screen frame/effects/dialogs moved to `SettingsScreenHelpers.kt`.
- `DeviceMaskerMainApp` now delegates Navigation 3 chrome, entry rendering, deep-link effects, and scene strategy selection to focused helpers.
- Theme color-scheme selection moved out of `DeviceMaskerThemeInternal`; dynamic AMOLED, regular dark, and contrast handling are now separate decisions.
- `AppLogStore` and `RootLogCollector` no longer keep pure string/JSON/file helpers inside their main classes.
- `ConfigSync` keeps public compatibility wrappers, while internal prefs execution helpers moved to file-level functions.
- Dead `XposedPrefs` convenience setters/getters were removed; config writes stay through `ConfigSync` and keys stay in `SharedPrefsKeys`.
- `IConfigManager` and `ISpoofRepository` are now split into smaller workflow contracts while preserving the unified compatibility facade for existing callers.
- `ConfigManager` and `SpoofRepository` keep narrow `TooManyFunctions` suppressions on the facade objects only; this documents intentional compatibility surface instead of leaving active baseline debt.

Rule: for category screens, first create a small UI-state model from `SpoofGroup`, then render one card/row/item helper per visible section. For Compose dropdowns, keep scope-specific modifiers at the call site and pass them down as `modifier`.

## Detekt Config Bar We Raised

The current config is stricter than normal Android defaults. New code must expect these rules to fire:

- Complexity: cognitive complexity, complex interfaces, nested scope functions, and too-many-functions are active.
- Coroutines: global coroutine usage and suspicious suspend patterns are active.
- Potential bugs: unsafe casts, missing `use`, missing super calls, nullable cast issues, and unnecessary null checks are active.
- Style: forbidden methods, redundant visibility, unused private members, data-class opportunities, `ifBlank`/`ifEmpty`, and `if` over binary `when` are active.
- Compose: modifier placement, preview naming, composition-local naming, and unstable collection rules are active.

## Checklist Before New Code

- Can this branch-heavy logic be a map, set, table, or small helper?
- Are all domain numbers named?
- Does each reusable composable accept `modifier`?
- Are composable collection parameters immutable when they come from state?
- Are callbacks captured in `LaunchedEffect`/restartable effects wrapped with `rememberUpdatedState`?
- Does each public helper type live in a matching file?
- Are catches narrow and logged?
- Did app-side code avoid silent fallback?
- Did Xposed code keep `stableHooker`, pass-through safety, and framework-error rethrow behavior?

## Rules And How To Avoid Them

### Complexity Rules

Rules: `CognitiveComplexMethod`, `CyclomaticComplexMethod`, `LongMethod`

Write code like this:
- Keep one composable focused on one visible UI section.
- Extract repeated UI rows, headers, state badges, and action rows early.
- Move branch-heavy mapping logic into lookup tables or small named helpers.
- In Xposed hookers, group method discovery, hook registration, and callback behavior into small helpers.

Avoid:
- One huge composable rendering header, body, dialogs, empty state, loading state, and item rows.
- Large `when` blocks inside hot or frequently reused functions.
- Hook methods that discover classes, register many hooks, parse values, and handle fallbacks all in one method.

### Size Rules

Rules: `TooManyFunctions`, `ComplexInterface`

Write code like this:
- Keep interfaces narrow by workflow: config read/write, app assignment, generation, export.
- Split repository responsibilities only when there is a real boundary.
- Prefer small internal helpers over adding public API surface.

Avoid:
- Dumping every operation into one repository or config manager interface.
- Adding methods to interfaces just because a screen needs a convenience shortcut.

### Constants

Rule: `MagicNumber`

Write code like this:
- Name behavior numbers: `IMEI_LENGTH`, `MAC_ADDRESS_PARTS`, `SEARCH_DEBOUNCE_MILLIS`.
- In Xposed code, name API constants and argument indexes before use.
- Keep UI-only spacing/elevation tokens in theme/token files when repeated.

Avoid:
- Raw `3`, `5`, `16`, `20`, or argument indexes inside hook callbacks.
- Numeric literals that require domain knowledge to understand.

### Compose API Stability

Rule: `UnstableCollections`

Write code like this:
- Use `ImmutableList<T>` for composable parameters when data comes from state.
- Convert once in ViewModel/state with `toImmutableList()`.
- Use `persistentListOf()` in previews.

Avoid:
- Passing mutable or plain `List<T>` through reusable composables.
- Rebuilding lists inline in frequently recomposed call sites unless the list is tiny and local.

### Naming

Rules: `ParameterNaming`, `MatchingDeclarationName`, `ObjectPropertyNaming`

Write code like this:
- Callback parameters can be noun/action style: `timezoneSelected`, `groupSelected`.
- Put public data classes in files matching their declaration name.
- Use backing state names like `serviceConnectedState`, not `_isServiceConnected`.

Avoid:
- Public helper types hidden inside unrelated files.
- Leading underscore properties in Kotlin objects.
- Callback parameter names that Detekt flags repeatedly.

### Error Handling

Rules: `TooGenericExceptionCaught`, `SwallowedException`

Write code like this:
- Catch specific exceptions when parsing, file IO, or serialization is the known failure.
- Log recovery paths with enough context.
- In Xposed hook registration, keep defensive isolation but rethrow `XposedFrameworkError`.

Avoid:
- `catch (Exception) { false }` without logging.
- Hiding parse or IO failures silently.
- Treating Xposed framework errors like ordinary recoverable failures.

### Xposed-Specific Exceptions

Rules: `UnusedParameter`, `ThrowsCount`, complexity rules

Current Xposed baseline is zero. Future Xposed findings are still not automatically bogus: hook API signatures and target-process safety can require defensive shapes, but any accepted exception must be documented instead of silently added to the baseline.

Rules for future Xposed code:
- Load `.agents/skills/libxposed/SKILL.md` before Xposed edits.
- Keep hook signatures compatible with current callers.
- Do not rename unused parameters to `_param`; it can create `FunctionParameterNaming` noise.
- Use `intercept(stableHooker { ... })`, not direct `.intercept { ... }`.
- Rethrow `XposedFrameworkError` before generic catches.
- Only reduce complexity when the split preserves hook isolation and runtime fallback behavior.

## Next Cleanup Order

1. Keep `app/detekt-baseline.xml`, `common/detekt-baseline.xml`, and `xposed/detekt-baseline.xml` empty.
2. Do not add new entries to baselines unless the debt is explicitly accepted and documented.
3. Future repository/config cleanup should gradually replace broad facade usage with the smaller workflow interfaces where that improves call sites.

## Current Verification

Latest verified commands:

```powershell
.\gradlew.bat spotlessApply :common:compileDebugKotlin :app:compileDebugKotlin --no-daemon --stacktrace
.\gradlew.bat spotlessApply :xposed:compileDebugKotlin --no-daemon --stacktrace
.\gradlew.bat detektBaseline --no-daemon --stacktrace
.\gradlew.bat detekt --no-daemon --stacktrace
.\gradlew.bat :common:testDebugUnitTest :app:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon --stacktrace
.\gradlew.bat spotlessApply :app:compileDebugKotlin detekt --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat detekt --no-daemon --stacktrace
.\gradlew.bat spotlessApply :app:compileDebugKotlin detekt --no-daemon --stacktrace
.\gradlew.bat detektBaseline --no-daemon --stacktrace
.\gradlew.bat spotlessApply :app:testDebugUnitTest detekt --no-daemon --stacktrace
.\gradlew.bat spotlessApply :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat detekt --no-daemon --stacktrace
graphify update .
```

All passed in the latest cleanup pass.
