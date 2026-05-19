# app/ Android Ninja Audit

Date: 2026-05-03

Scope: `app/` source audit for Device Masker, based on the `claude-android-ninja` skill and the current project Memory Bank. This audit focuses on `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main`, `app/src/test`, and `app/src/androidTest`. Generated files and `app/build/` outputs are excluded except where build configuration affects source behavior.

Skill used: `claude-android-ninja`

Skill references reviewed:

- `architecture.md`
- `compose-patterns.md`
- `kotlin-patterns.md`
- `coroutines-patterns.md`
- `testing.md`
- `android-theming.md`
- `android-accessibility.md`
- `android-navigation.md`
- `gradle-setup.md`
- `android-security.md`
- `android-performance.md`

Project context reviewed:

- `memory-bank/projectbrief.md`
- `memory-bank/productContext.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`

## Executive Summary

The `app/` module is a functional Android control surface for Device Masker. It already has several strong project-aligned decisions: Compose-first UI, RemotePreferences-first config delivery, rootless app-side logs, a bounded local log store, DataStore for UI preferences, edge-to-edge setup, a repository facade over config operations, and meaningful unit tests around config sync and log persistence.

The main issue is not that the app is poorly structured. It is that the app is now carrying privacy-sensitive state, LSPosed service coordination, group/app assignment semantics, diagnostics, export/import, and runtime status messaging in a mostly manual singleton/service-locator architecture. That raises correctness and testability risk as the feature set grows.

The most important fixes are:

1. Disable or tightly exclude Android backup and data extraction for sensitive config/log data.
2. Make config mutation and persistence transactional instead of fire-and-forget.
3. Fix import semantics so exported full config does not re-import only groups.
4. Make status and diagnostics wording match what the app can actually prove.
5. Add ViewModel, import/export, status, and UI accessibility tests around the privacy-critical workflows.

This app is best described as an active-development control app with a working base, not release-hardened software.

## Skill Criteria Matrix

| Area | Rating | Notes |
| --- | --- | --- |
| Architecture layering | Partial | Clear app/data/service/ui folders exist, but app-wide singleton wiring and AndroidViewModel usage limit isolation. |
| Compose patterns | Mostly good | Route/content split appears in several screens, lifecycle collection is used, and Lazy list keys exist in key places. State stability and UI tests need work. |
| Kotlin style | Partial | Code is readable and mostly idiomatic, but mutable singleton services, broad exception handling, legacy date APIs, and hardcoded dispatchers diverge from the skill. |
| Coroutines | Partial | StateFlow is used well, but config persistence uses a private global IO scope and asynchronous saves without serialization guarantees. |
| Navigation | Intentional divergence | Project uses Navigation Compose string routes. The skill prefers Navigation 3 typed `NavKey` patterns. The code documents an Android 16 sealed-class concern. |
| Theming | Partial | Dynamic color and expressive theme structure exist, but category colors and some scheme tweaks are hardcoded outside a richer token model. |
| Accessibility | Partial | Many UI elements have descriptions and semantic structure, but there is no automated accessibility coverage and some icon descriptions are hardcoded or absent. |
| Security/privacy | Needs work | Backup/data extraction rules are not hardened for a privacy app. App logs and config files are sensitive. |
| Testing | Needs expansion | There are useful unit tests, but example tests remain, Truth/Turbine are absent, and ViewModel/Compose flows are thinly covered. |
| Performance | Partial | Bounded logs and lazy lists help. Baseline profiles, Macrobenchmark, and StrictMode guardrails are absent. |
| Build/release | Active-development posture | Release minify/shrink are disabled intentionally for libxposed validation. That is acceptable for now, but must remain tracked as a release-readiness gap. |

## App Module Inventory

### Build And Manifest

Important files:

- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

Observed characteristics:

- Compile and target SDK are set to 36.
- Compose is enabled through the Kotlin Compose plugin.
- AIDL generation is enabled, which matches the diagnostics-only service state in project docs.
- Lint has `abortOnError = true`.
- Release minify and resource shrinking are currently disabled with a comment explaining libxposed live validation.
- Manifest requests `QUERY_ALL_PACKAGES`.
- Manifest requests legacy `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`.
- Manifest sets `android:allowBackup="true"`.
- Manifest uses `dataExtractionRules` and `fullBackupContent`.
- The libxposed service provider is exported.

### Startup And App Wiring

Important files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`

Observed characteristics:

- `DeviceMaskerApp` initializes app logs early.
- `ConfigManager` is initialized from the application.
- `XposedPrefs` is initialized from the application.
- `ServiceClient` is initialized from the application.
- `MainActivity` uses Compose, edge-to-edge, dynamic theme state, and Navigation Compose.
- ViewModels are created with manual `viewModel { ... }` factories.

### Persistence And Config Delivery

Important files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/SettingsDataStore.kt`

Observed characteristics:

- JSON config in app private files remains the app-side source of truth.
- `ConfigSync` flattens enabled per-app values into libxposed RemotePreferences.
- `ConfigSync` clears stale app keys when packages are removed from the active snapshot.
- `SharedPrefsKeys` from `:common` is used for preference key generation.
- `XposedPrefs` centralizes RemotePreferences access and service connection state.
- `SettingsDataStore` stores UI preferences through DataStore.

### Repositories

Important files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt`

Observed characteristics:

- `SpoofRepository` provides a single facade for groups, app configs, spoof values, export/import, and enablement state.
- `AppScopeRepository` enumerates installed apps for target selection.
- Config generation is delegated to shared/common models and generators, which matches project rules.

### UI

Important folders:

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation`

Observed characteristics:

- Screens are grouped by feature.
- Route/content separation exists in important screens such as Home, Groups, and Settings.
- Material 3 and expressive component naming are used throughout.
- Lazy lists use stable keys in several important places.
- Many user-facing strings are resource-backed.
- The string catalog is large and appears to contain many stale or near-duplicate variants.

### Tests

Important folders:

- `app/src/test`
- `app/src/androidTest`

Observed characteristics:

- Useful tests exist for config sync, app log storage, release build safety, and persona/config behavior.
- Default example tests are still present.
- Tests use JUnit assertions instead of Truth.
- No Turbine usage was found for Flow testing.
- No Compose UI test coverage was found for the main workflows.

## Findings By Severity

## Critical Findings

### C1. Backup And Data Extraction Are Unsafe For A Privacy-Sensitive App

Evidence:

- `app/src/main/AndroidManifest.xml` sets `android:allowBackup="true"`.
- `app/src/main/AndroidManifest.xml` references `@xml/backup_rules`.
- `app/src/main/AndroidManifest.xml` references `@xml/data_extraction_rules`.
- `app/src/main/res/xml/backup_rules.xml` still contains sample comments and no meaningful exclusions.
- `app/src/main/res/xml/data_extraction_rules.xml` still contains sample comments and no meaningful exclusions.

Why this matters:

Device Masker stores per-app spoofing profiles, selected target packages, group assignments, exported logs, and LSPosed-facing config state. That data is sensitive because it describes privacy posture, target apps, generated identities, and potentially app package names. Android backup or device-to-device transfer could copy this data outside the user's expected local control.

The Android Ninja security guidance strongly favors least-data retention, explicit backup posture, and avoiding accidental movement of sensitive app-private data.

Recommended fix:

- Prefer `android:allowBackup="false"` for this app unless there is a deliberate, documented restore requirement.
- If backup must remain enabled, explicitly exclude:
  - `files/config.json`
  - app log files under `files/logs/`
  - exported diagnostic artifacts
  - DataStore files that contain user privacy settings
  - any RemotePreferences mirror data if stored in app-accessible backup domains
- Update both `backup_rules.xml` and `data_extraction_rules.xml`.
- Add a unit or static manifest test that fails if backup is accidentally re-enabled without exclusions.

Suggested priority: immediate.

## High Findings

### H1. Config Persistence Is Fire-And-Forget And Can Produce Out-Of-Order State

Evidence:

- `ConfigManager` owns `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- `ConfigManager.initialize()` loads config asynchronously.
- `ConfigManager.saveConfig()` launches an asynchronous write.
- `ConfigManager.updateConfig()` updates `_config.value` and then calls asynchronous `saveConfig()`.
- `SpoofRepository.setActiveGroup()` updates the selected group and then updates other groups one-by-one.

Why this matters:

Multiple config operations can emit and persist intermediate states. If a user rapidly edits groups, toggles apps, imports config, and leaves the app, there is no clear single serialized transaction boundary. Because saves are launched asynchronously, the final on-disk JSON may depend on coroutine scheduling rather than the order of user intent.

For this project, config correctness is core behavior. `JsonConfig.appConfigs` is canonical, RemotePreferences sync derives from it, and the hook side must not invent fallback identifiers. Any stale or partially persisted state can lead to confusing spoof behavior.

Recommended fix:

- Introduce a serialized mutation path in `ConfigManager`, such as a `Mutex` around read/modify/write.
- Make save completion part of the logical mutation when correctness matters.
- Prefer one atomic API such as `updateConfigTransaction { current -> next }`.
- Avoid repository methods that perform several independent `ConfigManager.updateGroup()` calls for one logical operation.
- Add tests that simulate rapid group activation and verify exactly one final active group is persisted.

Suggested priority: immediate after backup hardening.

### H2. Import Semantics Appear To Drop Canonical App Assignment State

Evidence:

- Export/import logic lives in `SpoofRepository`.
- Import parses a full `JsonConfig`.
- The observed import path updates groups from the imported config but does not appear to replace or merge canonical `appConfigs`.
- Project rules state `JsonConfig.appConfigs` is canonical for app assignment and enablement.
- Project rules state `SpoofGroup.assignedApps` is legacy/display compatibility only.

Why this matters:

If export writes full config but import only restores groups, app assignment and per-app enabled state can be silently lost or left stale. That is especially risky because users will expect an exported profile to restore the actual spoofing setup.

This also conflicts with the current source-of-truth rule: group `assignedApps` must not be treated as the canonical assignment channel.

Recommended fix:

- Add an explicit `ConfigManager.replaceConfig()` or `mergeImportedConfig()` API.
- Import canonical `appConfigs` along with groups and module settings.
- Validate imported package names, group IDs, enabled flags, and spoof values before committing.
- After import, force a full `ConfigSync.syncConfig()` and stale key clearing.
- Add tests:
  - export config with assigned apps
  - import into empty config
  - verify `appConfigs` restored
  - verify removed packages are cleared from RemotePreferences

Suggested priority: immediate.

### H3. Runtime Status Can Overclaim Hook Success

Evidence:

- Project docs state LSPosed logs are authoritative proof of target-process hook registration and spoof events.
- App-side `XposedPrefs.isServiceConnected` proves service connection only.
- Home status combines service/module state into user-facing active/protection messaging.
- `XposedPrefs.isModuleEnabled()` returns `true` when preferences are unavailable.

Why this matters:

A connected libxposed service does not prove that selected target apps are scoped, restarted, hooked, or receiving spoof values. If the UI says or implies protection is active based only on service or module flags, users may trust a state the app cannot verify.

Recommended fix:

- Split status into precise layers:
  - app config exists
  - libxposed service connected
  - module enabled according to service preference
  - target package scoped, if detectable
  - target hook events observed, only when backed by logs or diagnostics
- Rename any overconfident copy such as "Protection Active" to a narrower claim unless hook evidence exists.
- Treat unavailable module preference as unknown rather than enabled.
- Add HomeViewModel tests for disconnected, unknown, enabled, disabled, and no-target states.

Suggested priority: high.

### H4. `QUERY_ALL_PACKAGES` Is Product-Relevant But High Risk

Evidence:

- Manifest requests `android.permission.QUERY_ALL_PACKAGES`.
- `AppScopeRepository` enumerates installed apps for app assignment.

Why this matters:

The permission is understandable for an LSPosed module manager that lets users assign spoofing profiles to installed apps. It is still a high-scrutiny Android permission and a privacy-sensitive surface. It also increases the importance of not backing up or leaking package lists.

Recommended fix:

- Keep the permission only if full installed-app selection remains a core requirement.
- Document why it is required in project docs and release notes.
- Avoid logging full app lists.
- Consider narrower package visibility declarations for known companions or verification targets if any mode can work without full enumeration.
- Add a privacy note in the app UI or docs explaining local-only use of package inventory.

Suggested priority: high.

### H5. Diagnostics Contain Aspirational Or Indirect Checks

Evidence:

- Diagnostics include anti-detection checks for surfaces that may not be directly validated from the app process.
- Some diagnostics report success based on configuration or local app observations, not target-process hook evidence.
- Project docs state class lookup hooks are currently disabled by default and LSPosed logs are authoritative.

Why this matters:

Diagnostics are most useful when they separate "configured", "service connected", "test executed in app process", and "observed in target process". If these states are blended, diagnostics can accidentally become a false assurance tool.

Recommended fix:

- Label diagnostics by evidence level:
  - configuration check
  - app-process check
  - service check
  - target-process hook evidence
- Do not mark disabled-by-default anti-detection surfaces as passing unless they are actually enabled and validated.
- Include "unknown" as a first-class state.
- Link or summarize LSPosed log evidence only when available.

Suggested priority: high.

## Medium Findings

### M1. Architecture Uses Manual Singletons Instead Of Dependency Injection

Evidence:

- `DeviceMaskerApp` initializes global managers.
- Repositories expose singleton-style access.
- `MainActivity` manually creates ViewModels with `viewModel { ... }`.
- Some ViewModels extend `AndroidViewModel`.

Why this matters:

The skill prefers MVVM with Hilt-style injection, scoped dependencies, and testable constructors. The current design works, but it makes it harder to test state transitions, replace dispatchers, and isolate config/service failures.

Recommended fix:

- Introduce DI gradually rather than with a large rewrite.
- Start by extracting interfaces for:
  - config store
  - config sync
  - service status
  - app scope repository
  - log store
- Convert ViewModels to constructor-injected dependencies.
- Keep `DeviceMaskerApp` as composition root until full Hilt migration is worth the cost.

Suggested priority: medium.

### M2. Navigation Diverges From Android Ninja Guidance

Evidence:

- `app/build.gradle.kts` uses Navigation Compose.
- `NavDestination` uses string routes.
- The code comment says string routes are used to avoid sealed class initialization issues on Android 16.
- `MainActivity` declares a route with `navArgument("groupId")`.

Why this matters:

The skill recommends Navigation 3 typed `NavKey` objects, route-scoped navigators, and type-safe back-stack modeling. The current approach is more fragile because route strings and argument names can drift.

Important context:

This appears intentional, not accidental. The code documents an Android 16 compatibility concern. Because Device Masker is already doing low-level Android and LSPosed work, runtime stability may outweigh adopting Navigation 3 right now.

Recommended fix:

- Document the exact Android 16 crash or initialization issue that blocks typed sealed navigation.
- Add route construction helpers for argument routes to reduce string drift.
- Add navigation tests around group details and back behavior.
- Revisit Navigation 3 only after the Android 16 issue is understood and reproducible.

Suggested priority: medium.

### M3. UI State Classes Are Not Marked Stable Or Immutable

Evidence:

- Screen state classes such as `HomeState`, `GroupsState`, `GroupSpoofingState`, `DiagnosticsState`, and `SettingsState` are plain data classes.
- Several contain `List` values.
- `MotionPolicy` is annotated `@Stable`, showing the project already uses Compose stability annotations selectively.

Why this matters:

Compose can still work without these annotations, but large screens with lists and frequent state updates benefit from stable state contracts. The skill recommends immutable UI state and stable collection patterns.

Recommended fix:

- Audit common model classes used inside UI state for immutability.
- Add `@Immutable` where true.
- Consider persistent immutable collections for frequently emitted lists.
- Avoid annotating types until their nested fields are actually stable.

Suggested priority: medium.

### M4. Hardcoded Dispatchers Reduce Test Control

Evidence:

- `ConfigManager` owns a hardcoded IO coroutine scope.
- `AppScopeRepository` uses IO dispatcher internally.
- `ServiceClient` uses IO dispatcher internally.
- Some ViewModels perform IO work directly with hardcoded dispatching.

Why this matters:

Hardcoded dispatchers make tests slower and more brittle. The Android Ninja coroutine guidance prefers injecting dispatchers or a dispatcher provider for classes that perform async work.

Recommended fix:

- Add a small dispatcher provider only where it pays off.
- Start with `ConfigManager`, `AppScopeRepository`, and `ServiceClient`.
- Use `StandardTestDispatcher` in tests.
- Avoid broad rewrites of UI-only code until tests require it.

Suggested priority: medium.

### M5. Broad Exception Handling Can Hide Cancellation And State Bugs

Evidence:

- Several app services catch broad `Exception`.
- Config load/save failures log and fall back.
- Export/import/log paths return user-facing failure strings.

Why this matters:

Some broad catches are practical at app boundaries. However, catching broadly inside coroutine code can accidentally swallow cancellation or collapse distinct failure modes into generic errors.

Recommended fix:

- Re-throw `CancellationException` in suspending paths.
- Keep broad catches at UI or file boundary layers.
- Log exception class names for diagnostics where privacy-safe.
- Add tests for malformed import, missing file, and save failure behavior.

Suggested priority: medium.

### M6. Theming Has Good Foundations But Hardcoded Accent Colors Are Spread Across Domain Categories

Evidence:

- Dynamic colors are supported.
- The theme has expressive motion and dark/AMOLED handling.
- `UIDisplayCategory` maps categories to hardcoded colors.
- Theme code contains several direct `Color(...)` scheme adjustments.

Why this matters:

Hardcoded category colors can clash with dynamic color and user-selected theme modes. They also make it harder to evolve an expressive design language consistently.

Recommended fix:

- Move category accents into an explicit extended color model.
- Harmonize category accents against the active color scheme.
- Use semantic names such as `identityAccent`, `networkAccent`, `locationAccent`, and `systemAccent`.
- Keep direct `Color(...)` values inside theme token definitions only.

Suggested priority: medium.

### M7. Accessibility Needs Automated Coverage

Evidence:

- Many buttons and icons include content descriptions.
- Some icons intentionally use `contentDescription = null`.
- Some preview/demo descriptions are hardcoded.
- No Compose accessibility tests were found.

Why this matters:

This app has dense controls, toggles, segmented settings, dialogs, and lists. Accessibility regressions can easily appear as the UI grows.

Recommended fix:

- Add Compose UI tests for:
  - main tabs
  - app enable toggle
  - group creation
  - settings switches
  - diagnostics actions
- Assert important nodes have labels and roles.
- Add state descriptions for privacy-critical toggles where TalkBack output would otherwise be ambiguous.
- Verify touch targets remain at least 48 dp.

Suggested priority: medium.

### M8. String Resources Need Pruning And Organization

Evidence:

- `strings.xml` is very large.
- Many keys appear to be final/reworded/role-specific variants.
- The catalog contains many similar settings, diagnostics, onboarding, and accessibility strings.

Why this matters:

String sprawl increases localization cost and makes it harder to know which copy is live. It also raises the chance that status language drifts from actual behavior.

Recommended fix:

- Split or group strings by screen with comments.
- Remove unused variants.
- Prefer precise status strings that reflect evidence level.
- Add a simple resource usage audit before localization.

Suggested priority: medium.

### M9. App Logs Are Useful But Privacy-Sensitive

Evidence:

- `DeviceMaskerApp` installs persistent app logging.
- `AppLogStore` stores logs in app-private files.
- Logs are bounded to 500 entries.
- Logs can include package names, config path messages, sync events, and service state.

Why this matters:

Rootless app logs are valuable for LSPosed troubleshooting, but this app's logs can reveal target packages and spoofing activity. That makes backup exclusion and export UX especially important.

Recommended fix:

- Keep logs app-private by default.
- Add a clear warning before exporting logs.
- Redact package names only if it does not destroy diagnostic value, or provide a redacted export option.
- Exclude logs from backup and device transfer.
- Consider lowering verbosity for package-specific sync events in release builds.

Suggested priority: medium.

### M10. Log Store Uses Whole-File Rewrites

Evidence:

- `AppLogStore` keeps a bounded entry count.
- Appending logs reads and rewrites log contents.

Why this matters:

At 500 entries, this is probably acceptable today. As diagnostics expand, persistent logging from Timber can become a main-thread or IO churn concern depending on call sites and startup volume.

Recommended fix:

- Keep the 500-entry bound.
- Consider a ring-buffer file format if log volume grows.
- Add StrictMode checks in debug builds to catch accidental main-thread file IO.

Suggested priority: medium-low.

### M11. Release Shrink Is Disabled

Evidence:

- Release build has `isMinifyEnabled = false`.
- Release build has `isShrinkResources = false`.
- Comment says this is intentional until live libxposed validation is complete.
- `proguard-rules.pro` contains important keep rules for libxposed, kotlinx serialization, and app entry points.

Why this matters:

The current setting is reasonable for active LSPosed validation. It is still a release-readiness gap because release builds are larger and less hardened than they will eventually need to be.

Recommended fix:

- Keep disabled while validating hooks if needed.
- Add a tracked task to re-enable minify and shrink.
- When re-enabled, test:
  - app launch
  - LSPosed module load
  - config sync
  - all hook entry points
  - export/import
  - diagnostics
- Preserve the existing keep rules and tighten only with runtime proof.

Suggested priority: medium.

## Low Findings

### L1. Example Tests Remain

Evidence:

- Default example unit and instrumentation tests are still present.

Why this matters:

Example tests add noise and can give a false sense of coverage.

Recommended fix:

- Remove example tests or replace them with meaningful smoke tests.

Suggested priority: low.

### L2. Tests Use JUnit Assertions Instead Of Truth

Evidence:

- Tests use `Assert.assertEquals`, `assertTrue`, and `assertFalse`.
- No Truth dependency usage was found.

Why this matters:

The skill prefers Truth for clearer failure messages. This is not a correctness bug, but consistency helps as the suite grows.

Recommended fix:

- Use Truth for new tests.
- Convert existing tests opportunistically when touching them.

Suggested priority: low.

### L3. No Turbine Tests For Flow State

Evidence:

- No Turbine usage was found.
- ViewModels expose state through StateFlow-style APIs.

Why this matters:

Turbine is useful for testing initial state, transitions, and one-shot async sequences without brittle sleeps.

Recommended fix:

- Add Turbine to test dependencies if Flow-heavy ViewModel tests are added.
- Cover Home, Groups, Diagnostics, and Settings state transitions.

Suggested priority: low-medium.

### L4. Legacy Date APIs Appear In Boundary Code

Evidence:

- Date formatting appears in logging/export/UI boundary code.
- The skill prefers `kotlinx.datetime` over legacy `Date`/`Calendar` in domain-like code.

Why this matters:

This is acceptable at Android/UI boundaries, but domain/config timestamps should avoid legacy date APIs.

Recommended fix:

- Leave harmless UI formatting alone for now.
- Use `kotlinx.datetime` for new persisted timestamps or domain models.

Suggested priority: low.

### L5. Legacy External Storage Permission May Be Unneeded

Evidence:

- Manifest requests `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"`.
- Current export/import appears to use Android document APIs rather than direct external storage writes.

Why this matters:

Even legacy-scoped storage permissions increase manifest surface area.

Recommended fix:

- Verify no API 28 path writes directly to external storage.
- Remove the permission if SAF covers all export/import flows.

Suggested priority: low.

### L6. Deprecation Warnings Are Suppressed

Evidence:

- Kotlin compiler args suppress deprecation warnings.

Why this matters:

Suppression reduces noise during active development, but can hide migration work for Android, Compose, or Gradle APIs.

Recommended fix:

- Track why suppression exists.
- Periodically run without suppression and file cleanup tasks.

Suggested priority: low.

## Detailed Area Analysis

## Architecture And Boundaries

The app follows the broad intended architecture: UI screens call ViewModels, ViewModels call repositories/services, config is persisted by `ConfigManager`, and RemotePreferences sync is handled by `ConfigSync`/`XposedPrefs`. This aligns with the Memory Bank architecture and the app module's responsibility as the user-facing control plane.

The largest architectural weakness is dependency ownership. Most core services are globally initialized rather than injected. This makes sense for a young app, but the current scope has grown beyond trivial wiring:

- config persistence
- remote preference sync
- app enumeration
- diagnostics service access
- log persistence
- settings persistence
- import/export
- group/app assignment

These concerns are now important enough to justify test seams. The Android Ninja skill recommends Hilt and module boundaries for larger Android apps. A full Hilt migration may be more disruption than needed immediately, but constructor-injected ViewModels and small interfaces would reduce risk without changing user behavior.

Recommended architecture path:

1. Keep current package layout.
2. Add interfaces for high-risk services.
3. Inject those into ViewModels manually first.
4. Add tests against fake implementations.
5. Move to Hilt only when the dependency graph is stable enough to justify it.

Avoid a large module split until the behavior stabilizes. The current `:common`, `:app`, and `:xposed` boundary is already meaningful.

## Config Persistence And Sync

This is the most important functional area in `app/`.

Good practices observed:

- App-side JSON remains the source for user configuration.
- `ConfigSync` builds a flattened RemotePreferences snapshot.
- Preference keys come from shared common code.
- Sync clears stale keys for apps removed from the active snapshot.
- Blank or invalid values are filtered before writing.
- Sync avoids direct target-process JSON reads, matching project rules.

Risks:

- Saves are asynchronous and not transactionally serialized.
- Multi-step repository operations can persist intermediate states.
- Import behavior appears incomplete for canonical app assignment.
- Service unavailable paths can silently skip writes and rely on later activation.

The service-unavailable behavior is not wrong by itself. The app must be usable when LSPosed service is disconnected. The issue is that the UI and tests should make the eventual-sync model explicit.

Recommended tests:

- Updating a spoof value persists once and syncs the expected key.
- Removing an app clears all per-app RemotePreferences keys.
- Importing a config restores `appConfigs`.
- Importing a config clears stale previously synced packages.
- Rapid group activation persists exactly one active group.
- Service disconnected state queues or clearly marks sync as pending.

## Repository Layer

`SpoofRepository` is currently the app's main use-case layer. It provides a useful facade, but it is large and owns several unrelated workflows:

- group CRUD
- active group management
- app assignment
- spoof value updates
- config export
- config import
- module enablement
- RemotePreferences sync trigger behavior

This is not yet a fatal design problem, but it is the next place complexity will accumulate.

Recommended direction:

- Extract import/export into a `ConfigImportExportRepository` or use-case class.
- Extract active group transaction behavior into `ConfigManager`.
- Keep `SpoofRepository` as the compatibility facade for ViewModels until call sites can be simplified.

Do not refactor this purely for aesthetics. Refactor only around tests or bug fixes, especially import semantics and transactionality.

## Compose UI

Good practices observed:

- Compose is used consistently.
- Screens are feature-organized.
- `collectAsStateWithLifecycle` is used in important entry points.
- Route/content separation exists in several places.
- Lazy lists have stable keys in key screens.
- The app uses Material 3 and expressive component naming.
- Edge-to-edge support is enabled.

Risks:

- UI state stability is not explicit.
- Some Lazy lists lack `contentType`.
- Accessibility is not covered by tests.
- Dense settings/diagnostic screens are vulnerable to label/state drift.
- The string catalog is difficult to audit.

Recommended Compose improvements:

- Add `@Immutable` to UI states only after nested models are verified stable.
- Add `contentType` to larger heterogeneous Lazy lists.
- Add UI tests for core workflows.
- Add screenshot tests only after the UI stabilizes.
- Keep previews, but avoid preview-only hardcoded labels leaking into production component defaults.

## Navigation

The app currently uses Navigation Compose string routes. The skill prefers Navigation 3 typed keys and route separation.

Because the project has a comment about Android 16 sealed-class initialization issues, the current choice should be treated as a deliberate compatibility decision. The problem is not the decision itself. The problem is the lack of test coverage and documentation around that decision.

Recommended action:

- Add a short architecture note explaining why string routes are retained.
- Centralize route construction and parsing helpers.
- Add tests for group detail route generation and back navigation behavior.
- Re-evaluate Navigation 3 when the Android 16 issue has a reproducible test case or upstream fix.

## Theming And Design System

Good practices observed:

- Dynamic color support exists.
- Theme preferences are integrated with settings.
- AMOLED handling is considered.
- Expressive motion policy is modeled.
- UI components are organized under a theme/component system.

Risks:

- Hardcoded category colors sit outside a cohesive semantic token model.
- Direct scheme color copies can become hard to reason about.
- Dense UI can drift from Material semantics if every category invents its own visual language.

Recommended action:

- Introduce extended semantic colors for spoof categories.
- Harmonize accents against dynamic color.
- Keep category colors as accents, not as dominant page palettes.
- Add screenshot checks for light, dark, dynamic, and AMOLED modes before release.

## Accessibility

The app appears to care about accessibility, based on resource names and content descriptions. However, there is no clear automated accessibility safety net.

Important risks:

- Privacy-critical toggles may need state descriptions beyond visible labels.
- Icon-only actions need stable content descriptions.
- Dense lists need predictable traversal order.
- Dialogs need focus behavior and dismiss behavior verified.
- Settings and diagnostics screens need enough semantic labels for TalkBack users.

Recommended tests:

- Home screen exposes service/module status.
- App list exposes app name, package, and enable state.
- Group cards expose active state and action controls.
- Settings switches expose current state.
- Diagnostics actions and results are announced with useful labels.

## Security And Privacy

This is the area with the biggest gap between current behavior and the app's purpose.

Strong decisions:

- Config is app-private by default.
- Hook config delivery uses RemotePreferences instead of target-process JSON reads.
- Rootless app logs avoid requiring users to pull root logs.
- AIDL remains diagnostics-only according to project docs.

Weaknesses:

- Backup/data extraction are not hardened.
- Package inventory permission is broad.
- Logs can contain privacy-sensitive package and config information.
- Export/import workflows handle sensitive JSON and need clear trust boundaries.
- Provider export deserves explicit review against libxposed service docs and actual caller controls.

Recommended security checklist:

- Disable or tightly restrict backup.
- Exclude logs and config from transfer.
- Add privacy-safe logging guidance.
- Add export warning text.
- Verify provider exposure is exactly what libxposed service requires.
- Keep diagnostics AIDL isolated from config delivery.
- Do not add network capabilities unless explicitly needed.

## Performance

The app is unlikely to be CPU-heavy, but it has performance-sensitive areas:

- installed app enumeration
- app list filtering/search
- log persistence
- config sync writes
- startup initialization
- diagnostics checks

Good practices:

- Lazy lists are used.
- App logs are bounded.
- Config sync snapshots avoid writing disabled/blank values.

Gaps:

- No Baseline Profile or Macrobenchmark coverage.
- No StrictMode debug guardrails were observed.
- App enumeration scalability needs validation on devices with hundreds of apps.

Recommended performance checks:

- Measure cold start with and without LSPosed service connected.
- Measure installed-app list load time.
- Add debounce if search/filtering becomes expensive.
- Add debug StrictMode for accidental main-thread disk IO.
- Add Baseline Profile once navigation and startup stabilize.

## Build And Release Readiness

The build file is intentionally development-oriented in a few places.

Good:

- SDK levels are current.
- Lint fails builds.
- BuildConfig is explicit.
- Release signing can use environment variables.
- Proguard rules document important keep needs.

Gaps:

- Release minify and shrink disabled.
- Deprecation warnings suppressed.
- R8 keep rules have not been proven under a shrunk release.
- No benchmark/profile module.

Recommended release gate before stable release:

```powershell
.\gradlew.bat spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Runtime release gate:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
adb shell pidof com.mantle.verify
adb logcat -d -t 1200
```

Stable release should also include a shrunk-release LSPosed smoke test once minify is re-enabled.

## Recommended Action Plan

### Immediate

1. Harden backup and data extraction.
   - Verify by static manifest/resource test.

2. Fix config transactionality.
   - Verify with rapid mutation unit tests.

3. Fix import semantics.
   - Verify exported full config round-trips canonical `appConfigs`.

4. Tighten runtime status wording.
   - Verify HomeViewModel states for service connected, module unknown, module disabled, no apps, and hook evidence unavailable.

5. Clarify diagnostics evidence levels.
   - Verify disabled or unknown anti-detection surfaces do not show as passing.

### Next

1. Add ViewModel tests using fake repositories/services.
2. Add Compose tests for Home, Groups, Settings, and Diagnostics.
3. Introduce dispatcher injection for config, app enumeration, and service calls.
4. Extract import/export from the large repository facade.
5. Add privacy warning and optional redaction for log export.

### Later

1. Revisit Navigation 3 after documenting the Android 16 route issue.
2. Add Baseline Profile and Macrobenchmark coverage.
3. Re-enable release minify/shrink and validate LSPosed runtime behavior.
4. Clean string resources before localization.
5. Consider Hilt once constructor seams exist and dependency boundaries are stable.

## Positive Findings Worth Preserving

- RemotePreferences-first config delivery matches project architecture.
- `SharedPrefsKeys` is used instead of hardcoded preference keys in sync code.
- Hook-facing values are generated app/common-side, not in target processes.
- `ConfigSync` has a snapshot model and stale-key clearing.
- App-side logs are bounded and app-private by default.
- Compose screens are organized by feature.
- Lifecycle-aware state collection is used.
- Edge-to-edge is enabled.
- Dynamic theme settings exist.
- Release R8 keep rules are already documented for key libxposed and serialization surfaces.

## Open Questions

1. Does the libxposed `XposedProvider` enforce all necessary caller restrictions through the service library, or does the app need additional manifest-level documentation?
2. Should backup ever restore spoof personas, or should privacy always win over convenience?
3. What exact Android 16 issue required string navigation routes, and can it be captured in a regression note or test?
4. Should diagnostics surface LSPosed log-derived hook evidence directly, or should that remain a manual validation step?
5. Should exported configs include a schema version migration path before more fields are added?

## Bottom Line

The `app/` module is in a solid active-development state and already reflects many of the project's architecture rules. The largest gaps are privacy hardening, transactional config correctness, import/export semantics, truthful diagnostics/status evidence, and test coverage around those behaviors.

The next best engineering move is not a broad refactor. It is a small set of targeted hardening changes with tests: backup exclusion, config transaction serialization, full import round-trip behavior, and precise status/diagnostic states.
