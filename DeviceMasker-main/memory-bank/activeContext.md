# Active Context: Device Masker

## Current Focus

Logs system remediation on `release/0.1.5`. Release R8 is enabled and runtime-validated historically. The latest logs work has local unit/static proof and fresh rooted emulator export evidence for the fixed support bundle.

Next tasks:
1. Keep logs support bundle checks current after further diagnostics changes.
2. Keep Android 16 emulator debug and `ciRelease`/R8 validation current after hook changes.
3. Run fresh target-device LSPosed validation after any further hook or R8 callback changes.

## 2026-05-18 Logs System Remediation

- Implemented real support export inputs for app logs, config/scope snapshots, root/logcat artifacts, and copied LSPosed log directories.
- Removed the fake production path where `xposed/xposed_events.jsonl` was created from an empty list.
- Added artifact sidecar manifests so blank files explain missing output, command status, byte counts, and root availability.
- Added user-started Logs Monitor flow for live root logcat capture and filtering from Settings.
- Follow-up ZIP audit found two broken details and both were fixed: LSPosed `/data/adb/lspd/log*` paths may be directories, and support JSONL redaction must stay parseable after timestamps/IDs are redacted.
- Fresh emulator export `logs/device/2026-05-18-user-export-fixed-final-122647.zip` proved LSPosed current/old directory logs were copied, app/xposed JSONL parsed with zero bad lines, and `diagnostics/hook_health.json` contains a derived parsed-Xposed summary.
- No Room database was added; JSONL plus an in-memory bounded tail remains the current KISS data shape.

## 2026-05-18 Performance Optimization And Warning Cleanup

- Implementation plan: `docs/superpowers/plans/2026-05-18-performance-optimization-and-warning-cleanup.md`.
- Scope: in-place performance work only; no commit or push was performed.
- GitNexus index was refreshed with `npx gitnexus analyze --name DeviceMasker`. This refreshed embedded GitNexus metadata in `AGENTS.md` and `CLAUDE.md`.
- Home scoped-app loading no longer performs a full installed-app scan on startup. `IAppScopeRepository`/`AppScopeRepository` now expose scoped package metadata and `loadScopedApps(packageNames, forceRefresh)`, filtering `android`/`system` and resolving only LSPosed-scoped packages.
- `HomeViewModel` now reacts to LSPosed scope packages, loads only scoped metadata, and builds Home rows from a package-name map. Pull-to-refresh reloads scoped metadata and LSPosed scope.
- App icon decoding is centralized in bounded `AppIconCache`; `AppListItem` and Home scoped rows use `CachedAppIcon`. Home scoped rows now render through a bounded `LazyColumn` with stable package keys.
- `ConfigManager` now carries dirty package sync hints for app/group mutations. `ConfigSync.syncPackages()` writes the current enabled-app allowlist/config version and only updates requested package keys while preserving full sync for global/default-group operations.
- `HookConfigSnapshot` is built once in `XposedEntry.onPackageReady()` and passed into value hookers. Hookers read enabled values and persona fallback from the snapshot instead of repeatedly reading RemotePreferences in hot callbacks. Anti-detect and proc-maps policy still read their own app-level prefs.
- Xposed hook registration logging no longer emits per-hook DEBUG structured start/success events on the common path. It keeps health counters, disabled/failure events, and one final `All hooks registered` structured event.
- `SupportBundleBuilder` streams app/xposed JSONL lines directly into the zip instead of joining large strings.
- Startup root capture is delayed by one Compose frame and 1500 ms before requesting root capture, reducing first-composition contention without removing startup capture.
- Group Spoofing Apps tab now uses `AppRowModel` built in `GroupSpoofingViewModel`, moving lowercasing, assignment lookup, other-group labels, and sort order out of composition.
- Warning cleanup status: all module lint reports now say `No issues found`. Cleanup removed 1092 unused string entries, fixed touched detekt/lint findings, replaced production `SavedStateHandle()` defaults with lifecycle factory handles, and kept only narrow intentional suppressions for explicit `commit()` checks, target-SDK deferral, dependency availability, and verifier/Xposed platform reflection.
- GitNexus `detect_changes(scope=all)` reported critical aggregate risk because the planned scope was broad: 52 changed files, 217 changed symbols, and 80 affected symbols. Affected flows matched Home, app scope loading, config sync, support bundle/root capture, group app rows, and Xposed hook registration/value lookup.

Verification passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --tests com.astrixforge.devicemasker.data.repository.AppScopeRepositoryTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --tests com.astrixforge.devicemasker.ui.screens.groupspoofing.GroupSpoofingViewModelTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.components.AppIconCacheTest --tests com.astrixforge.devicemasker.service.ConfigSyncTest --tests com.astrixforge.devicemasker.data.ConfigSyncSnapshotTest --no-daemon
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.HookConfigSnapshotTest --tests com.astrixforge.devicemasker.xposed.PrefsHelperTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon
```

## 2026-05-16 Comprehensive UI Audit Remediation

- Implementation plan: `docs/superpowers/plans/2026-05-16-ui-audit-remediation.md`.
- Scope: remediation from `docs/internal/reports/active/audits/2026-05-16/comprehensive-ui-audit.md`; no commit or push was performed per user instruction.
- Diagnostics models moved out of the UI package to `com.astrixforge.devicemasker.diagnostics`.
- `ThemeMode` moved from `ui.theme` to `data.models`; settings persistence now uses named theme constants instead of raw numeric literals.
- `DiagnosticsViewModel` now has an injectable `IDiagnosticsProvider` and catches diagnostic provider failures so loading/refreshing state clears and the error is recorded in UI state.
- `DeviceMaskerNavigationState.visibleBackStack` is now exposed as an immutable `List`; `NavDisplay` receives an internal mutable snapshot list.
- Active group switching now uses one `ConfigManager.setDefaultGroup(groupId)` update instead of multi-step repository writes.
- AMOLED dynamic color now reuses one `withAmoledSurfaces()` helper for all surface container roles, and UI status colors now come from reusable `ColorScheme.status*` extension properties.
- Compose polish applied: timezone sheet state uses `rememberSaveable`, SectionHeader expand/collapse descriptions use string resources, and reusable dialog composables accept `modifier`.
- `GroupsScreenBody` already applied its modifier correctly; no random churn was made there.
- GitNexus impact/detect checks were attempted repeatedly but blocked by a LadybugDB file lock. Rerun GitNexus change detection after the index lock clears.

Verification passed:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsViewModelTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModelTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.data.repository.SpoofRepositoryTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.theme.ThemeColorTest --no-daemon
.\gradlew.bat :app:compileDebugKotlin --no-daemon
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon
```

## 2026-05-16 Search IME Dismiss Polish

- Group Spoofing Apps tab search now clears focus when the user dismisses the software keyboard with Back/system keyboard dismissal.
- Added reusable `ClearFocusOnImeDismiss` and `shouldClearFocusAfterImeDismiss()` under `ui.components`.
- Reused the same behavior for country and timezone picker search fields; their search text now uses `rememberSaveable`.
- GitHub Actions `actions/upload-artifact` usage in CI/release workflows was updated from v4 to v6 after CI passed with Node.js 20 deprecation warnings.
- No project rule, architecture, or module guide update was needed because this is localized Compose UI behavior and does not change config, runtime hook eligibility, or module boundaries.
- GitNexus remained locked by `.gitnexus/lbug` during the pre-edit query; rerun graph impact/change detection after the local lock clears.

Verification passed:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.components.ImeDismissFocusHandlerTest --no-daemon
git diff --check -- .github/workflows/ci.yml .github/workflows/release.yml
```

## 2026-05-15 Home Screen UI Refinements

### LSPosed Scoped Apps Section
- Implemented Home `Scoped Apps` as the LSPosed scope view, not a spoof-group assignment view.
- Source data: `XposedPrefs.scopedPackages` plus installed-app metadata from `AppScopeRepository.installedApps`; `android` and `system` are filtered out.
- Architecture/rules docs now record that Home Scoped Apps is an app-side LSPosed scope observation flow, not hook eligibility and not a spoof-group-derived list.
- Home now loads installed apps on init so scoped apps do not depend on another screen warming the app cache.
- Pull-to-refresh now force-refreshes installed apps and rereads LSPosed scope, fixing stale/missing rows after LSPosed scope changes.
- Rows show resolved installed app labels only. LSPosed-scoped packages missing from the installed-app list are omitted instead of falling back to raw package names.
- The Home row switch is standalone user control over `AppConfig.isEnabled`; group assignment/unassignment preserves that enabled state.
- Group Spoofing app selection now uses canonical `AppConfig.groupId` for assignment display, while disabled Home-scoped apps remain visible as disabled in group app lists.
- UI refinements: section title is plain text like Quick Actions, app rows keep icons, section is expandable, row composition uses stable package keys, and spacing from Quick Actions to Scoped Apps is tightened.
- 2026-05-16 follow-up: Scoped Apps now sorts installed scoped apps alphabetically A-Z by resolved label, then package name. Disabled rows keep their alphabetical position instead of being grouped separately, and disabled app cards render muted with reduced alpha for clearer state.
- Latest failed GitHub Actions run on `release/0.1.5` was CI run `25930803257`; failure was in `ConfigManagerTest` unit tests. Cause: racy `StateFlow` Turbine waits skipped the current `true` initialization value and timed out. Tests now wait for the first observed `true` initialization value deterministically.
- Verification passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeViewModelTest --tests com.astrixforge.devicemasker.ui.screens.home.HomeScopedAppsBuilderTest --no-daemon
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --no-daemon
```

### Export Logs Bottom Sheet Buttons
- Commit `6ca01eb`. Replaced `Row` + `FilledTonalButton` inside `ExportActionsBottomSheetContent` with `QuickActionRow` (M3 `ButtonGroup` + `clickableItem()`). Bottom sheet preserved, only inner buttons changed. Same coordinated press-expand animation as home screen quick actions.

### Group Selector Bottom Sheet
- Commit `18bcaed`. Replaced `GroupDropdownMenu` (`DropdownMenu`) in `GroupSelectorCard` with `GroupSelectorBottomSheet` using `AppModalBottomSheet`. The card still opens the sheet on tap; the sheet shows groups as clickable rows with name, app count, check mark for selected, and disabled indicator. Chevron rotation animation removed. Files changed: `HomeGroupSelector.kt`, `HomeScreen.kt`.

### Home Screen Structure (updated)
Sections in order: StatusCard → QuickStatsRow (protected apps, masked identifiers) → GroupSelectorCard (opens bottom sheet) → QuickActionsSection (Configure, Regenerate) → Scoped Apps.

## 2026-05-14 Toggle And Hook-Scope Remediation

- Fixed the current user-reported issue where adding a package to LSPosed scope could activate hooks from stale RemotePreferences even when the app was not currently assigned in Device Masker.
- `XposedEntry` now requires `module_enabled`, membership in `SharedPrefsKeys.KEY_ENABLED_APPS`, and the per-package enabled key before selecting a target package for app hooks. `module_enabled` defaults closed when missing.
- `JsonConfig` now exposes explicit assignment resolution for runtime sync; `ConfigSync` uses this path so default-group fallback cannot make an unassigned package hookable.
- Group, Home, and Group Spoofing UI counts/checked state now derive from canonical `appConfigs` instead of legacy `SpoofGroup.assignedApps`.
- Group Spoofing category master switches render enabled only when all child spoof types are enabled, avoiding a false fully-on state for partial categories.
- Config sync now derives ordinary hook-family policy from enabled spoof types so disabled value families do not register unnecessary hookers; anti-detection and package-manager policy remain app-level.
- Regression coverage was added for stale Xposed scope keys, explicit assignment/default-group fallback, disabled group sync, canonical app-count drift, and hook-family gating.
- Fresh verification passed:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon --no-configuration-cache
```

- Runtime LSPosed/device validation was not run for this remediation, so do not claim target-process runtime proof until a scoped target app is launched and LSPosed/logcat evidence is captured.
- Follow-up runtime validation on `emulator-5554` passed:
  - assigned verifier app logged target selection, `All hooks registered`, and spoof events.
  - unassigned but LSPosed-scoped verifier app logged `XposedEntry loaded` only, with no target selection, hook registration, or spoof events.
  - evidence is under `logs/device/2026-05-14-toggle-scope-test/`.
  - the implementation summary and audit moved to `docs/internal/reports/closed/`.

## 2026-05-14 GroupSpoofingScreen UI Refactoring And P3 Performance

- GitNexus migration is active; `npx gitnexus analyze --name devicemasker` refreshes the index.
- Completed GroupSpoofingScreen UI refactoring (P3 proposal implementation):
  - Removed refresh icon button from Apps tab header.
  - Inlined the system app FilterChip into the same Row as the app count text.
  - Merged `AppsFilterRow` logic into `AppsSearchHeader` — deleted `AppsFilterRow.kt`.
  - Removed `isRefreshing` and `refreshRequested` from `AppsHeaderState` (state no longer owns pull-to-refresh triggers).
  - Removed `isRefreshing` parameter from `AppsSearchHeader` call site in `AppsTabContent`.
  - Removed the unused refresh icon button import and related stale code.
- Implemented scroll position persistence for tabbed Group Spoofing interface:
  - Added `spoofTabScrollPosition` and `appsTabScrollPosition` to `GroupSpoofingState`.
  - Added `setSpoofTabScrollPosition()` and `setAppsTabScrollPosition()` to `GroupSpoofingViewModel` with `SavedStateHandle` persistence.
  - Added `initialScrollPosition` and `onScrollPositionChange` parameters to `SpoofTabContent` and `AppsTabContent`.
  - Used `rememberLazyListState` with `initialFirstVisibleItemIndex` and `LaunchedEffect` + `snapshotFlow` for tracking.
  - Fixed `LambdaParameterInRestartableEffect` warnings with `rememberUpdatedState`.
  - Passed scroll positions from state through `GroupSpoofingPager` to both tab content composables.
- Fixed detekt violations: `TooManyFunctions` (ViewModel, suppressed), `LongMethod` (SpoofTabContent, suppressed), duplicate `@Suppress` annotations, and `rememberSaveable` import path.
- Full verification passed: `spotlessApply spotlessCheck detekt :app:testDebugUnitTest` → `BUILD SUCCESSFUL`.
- Commits:
  - `cb0349e` — "perf(groupspoofing): add scroll position persistence for tabbed interface"
  - `3d239bd` — "refactor(groupspoofing): remove refresh button and move system apps toggle inline"
- GitNexus index refreshed after commits.

## 2026-05-12 GitNexus Migration

- Graphify is removed from the active project workflow.
- GitNexus is the active code-intelligence tool and this repo is indexed as `devicemasker`.
- Refresh command: `npx gitnexus analyze --name devicemasker`.
- Verified status: `npx gitnexus status` reports up to date at commit `17def8e`, with 5,115 symbols, 12,291 edges, 187 clusters, and 300 flows.
- Use GitNexus MCP resources such as `gitnexus://repo/devicemasker/context`, `clusters`, and `processes` for codebase navigation.

## 2026-05-09 Release 0.1.5 Hardening Branch

- Active branch: `release/0.1.5`.
- Implemented shared Luhn validation in `:common`; IMEI, ICCID, deterministic persona IMEI/ICCID, and validation now use one helper.
- `ConfigSync` now writes coherent per-package `DevicePersona` blob/version alongside flat RemotePreferences keys. `PrefsHelper` can use the persona as a fallback only when the spoof type is enabled.
- Device-profile runtime coverage expanded:
  - `SystemHooker` now covers `Build.ID`, `Build.TIME`, `Build.SUPPORTED_ABIS`, `Build.VERSION.INCREMENTAL`, `Build.VERSION.SECURITY_PATCH`, ABI system properties, and build date.
  - `SystemFeatureHooker` hooks NFC and telephony `PackageManager.hasSystemFeature` results from the selected profile.
  - `DeviceHooker` hooks SIM-count methods; `SubscriptionHooker` hooks active subscription count methods and no longer contains a no-op subscription-list hook.
- Android Advanced Protection and Identity Check are diagnostics-only via `SecurityStateDiagnostics`; they are exported in `security_state_snapshot.json` and are not spoofed.
- Added local `:verifier` target app (`com.astrixforge.devicemasker.verifier`) that writes `files/verifier/latest.json` for controlled framework-surface validation.
- Full local gate passed:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon
```

- Android 13 emulator validation:
  - Installed rebuilt debug app and verifier on `emulator-5554`.
  - Verifier launched and wrote `logs/device/2026-05-09-verifier-latest.json`; it was not LSPosed-scoped in that run, so it is baseline evidence.
  - Mantle smoke launched with live PID and LSPosed/logcat evidence for `XposedEntry`, target selection, `SystemHooker`, `SystemFeatureHooker`, `All hooks registered`, and spoof events. No `FATAL EXCEPTION`, `AbstractMethodError`, abstract hook error, WebView regex crash, or WorkManager initializer crash appeared in the checked log window.
- Advanced native `/proc/self/maps` and system_server package hiding are still not implemented. They remain high-risk tracks that require explicit opt-in, kill switches, boot/release proof, and separate validation before being enabled.

## 2026-05-09 Android 16 Compatibility And Proc Maps Track

- Implemented per-app hook-family isolation through RemotePreferences keys generated by `SharedPrefsKeys`; `ConfigSync` writes current default state and `XposedEntry` skips disabled families with LSPosed/logcat diagnostics.
- Removed HiddenApiBypass from app/xposed dependencies. Android 16/non-SDK hardening must use defensive public/reflection guards, not hidden-API bypass shortcuts.
- Moved `/proc/self/maps` filtering out of broad `AntiDetectHooker` behavior into `ProcMapsHooker`.
- `ProcMapsHooker` is path-aware for `/proc/self/maps`, `/proc/<pid>/maps`, `/proc/self/smaps`, and `/proc/<pid>/smaps`. Hidden lines are skipped, not replaced with blank strings.
- Java byte-stream redaction and Java NIO redaction exist but are explicit per-app opt-in policy keys. They are not default-on.
- `:verifier` now captures `procMaps`, `packageVisibility`, and `runtime` sections in `latest.json`.
- Evidence scripts added:
  - `scripts/collect-a16-crash-evidence.ps1`
  - `scripts/verify-16kb-page-support.ps1`
- Local verification completed:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest :verifier:assembleDebug lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon
```

Result: `BUILD SUCCESSFUL` in `logs/build/2026-05-09-a16-proc-maps-final-gate.txt`.

- Android 13 emulator verifier evidence exists at `logs/device/2026-05-09-verifier-a13-final.json`; Mobile MCP launched the verifier and observed the new JSON sections on screen.
- 16 KB verification passed for debug, release, and ciRelease APKs. Local release/ciRelease outputs are unsigned because release signing env vars are not present.
- 2026-05-10 Android 16 emulator validation:
  - Device: `emulator-5554`, Pixel 10 Pro XL API 36.1, Android 16 / SDK 36, 16 KB page size (`getconf PAGE_SIZE=16384`).
  - Full gate passed in `logs/build/2026-05-10-a16-final-gate.txt`.
  - 16 KB verification passed for debug, release, and ciRelease APKs in `logs/build/2026-05-10-16kb-debug.txt`, `logs/build/2026-05-10-16kb-release.txt`, and `logs/build/2026-05-10-16kb-ciRelease.txt`.
  - DevCheck debug runtime passed with LSPosed `XposedEntry`, target selection, `All hooks registered`, spoof events, live PID, and no checked fatal/ABI signatures in `logs/device/2026-05-10-213712-a16-flar2.devcheck-*`.
  - Local `ciRelease` was signed only for emulator smoke as `logs/tmp/app-ciRelease-debugkey-signed.apk`; DevCheck ciRelease/R8 runtime passed with the same LSPosed evidence and no checked `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, or fatal crash in `logs/device/2026-05-10-214023-a16-flar2.devcheck-*`.
  - Verifier wrote Android 16 baseline JSON at `logs/device/2026-05-10-verifier-a16-final.json`; that run was not proof of proc-maps redaction because the verifier was not scoped/configured for LSPosed spoofing.
- Physical-device Android 16 evidence and the explicit module-disabled/load-only/hook-family matrix remain open. Do not claim real-device completion from emulator evidence.

## 2026-05-10 Verifier Canonical Package Setup

- Added manual device UI rules and later consolidated them into `docs/AGENTS_PROJECT_RULES.md`: manual emulator/device UI work must use Mobile MCP, while ADB/shell remains for command work and evidence capture. The old `docs/public/AGENT_DEVICE_UI_RULES.md` duplicate is no longer present; `docs/AGENTS_PROJECT_RULES.md` is canonical.
- Locked `:verifier` back to the canonical package `com.astrixforge.devicemasker.verifier`.
- Built and installed `verifier-debug.apk` on `emulator-5554` / Pixel 10 Pro XL API 36.1.
- Mobile MCP opened LSPosed from the notification panel and Device Masker UI. Device Masker `TestingA16` Apps tab showed `6 apps • 3 assigned` with DevCheck, DeviceMasker Verifier, and Mantle Verify checked.
- LSPosed scope DB readback showed Device Masker scoped to `android`, `system`, `flar2.devcheck`, `com.mantle.verify`, and `com.astrixforge.devicemasker.verifier`.
- Verifier launch under LSPosed showed `XposedEntry loaded`, `All hooks registered`, and spoof events in `logs/device/2026-05-10-verifier-canonical-package-logcat.txt`; no checked `FATAL EXCEPTION`, `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, or `HookFailedError` appeared in the checked logcat window.
- Verifier latest JSON at `logs/device/2026-05-10-verifier-canonical-package-latest-2.json` confirmed scoped spoofing for key enabled values including Android ID, device profile, network operator, timezone, and locale. Latitude/longitude remained disabled in the configured profile.

## 2026-05-10 Verifier Value Matrix Upgrade

- Google Developer Knowledge confirmed Android 10+/API 29+ behavior for non-resettable identifiers: `TelephonyManager` persistent IDs and `Build.getSerial()` throw `SecurityException` for normal target-SDK 29+ apps unless privileged/carrier/default-SMS/device-owner conditions apply. This explained the previous verifier failures.
- Fixed `DeviceHooker` restricted identifier callbacks so configured spoof values are returned before calling `chain.proceed()`. This prevents Android 16 platform restrictions from throwing before the hook can return valid configured values.
- Improved `:verifier`:
  - Added `READ_PHONE_NUMBERS`.
  - Added slot-specific telephony probes.
  - Added WebView instance UA probe.
  - Added default accelerometer probe.
  - Added a machine-readable `checks` matrix with status classification.
- Fixed `WebViewHooker` static default UA hook target from the wrong `WebView.getDefaultUserAgent(Context)` path to `WebSettings.getDefaultUserAgent(Context)`.
- Hardened `SensorHooker` to normalize common sensor names such as emulator `Goldfish ...` to generic names.
- Rebuilt and installed Device Masker + verifier, granted verifier runtime permissions, rebooted the Android 16 emulator so LSPosed loaded the updated module APK, and reran verifier.
- Post-reboot evidence:
  - `logs/device/2026-05-10-verifier-matrix-post-reboot-latest.json`
  - `logs/device/2026-05-10-verifier-matrix-post-reboot-logcat.txt`
  - `docs/internal/reports/closed/validation/2026-05-10/2026-05-10-verifier-value-matrix-report.md`
- Matrix result: `PASS=25`, `FAIL=1`, `NOT_CONFIGURED=2`.
  - Pass: Android ID, serial, IMEI, IMEI slot 0, IMSI, ICCID, phone number, SIM/network values, Wi-Fi, Bluetooth MAC, Advertising ID, GSF ID, MediaDRM ID, device profile fields, timezone, locale, normalized default accelerometer name, and WebView default UA.
  - Not configured: latitude/longitude because the profile intentionally has both disabled.
  - Remaining fail: WebView instance `WebSettings.getUserAgentString()` still returns the provider default UA with emulator model even though static `WebSettings.getDefaultUserAgent(Context)` is fixed.
- No checked `FATAL EXCEPTION`, `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, or `HookFailedError` appeared in the post-reboot verifier logcat window.

## 2026-05-10 Latitude/Longitude Enabled Rerun

- User enabled both latitude and longitude in Device Masker. Config snapshot at `logs/device/2026-05-10-config-after-latlong-enabled.json` shows `LOCATION_LATITUDE=41.700052` and `LOCATION_LONGITUDE=62.377687`, both enabled for `com.astrixforge.devicemasker.verifier`.
- Rerun evidence:
  - `logs/device/2026-05-10-verifier-matrix-latlong-enabled-latest.json`
  - `logs/device/2026-05-10-verifier-matrix-latlong-enabled-logcat.txt`
  - `docs/internal/reports/closed/validation/2026-05-10/2026-05-10-verifier-android-16-full-summary.md`
- Latest matrix result: `PASS=27`, `FAIL=1`, `NOT_CONFIGURED=0`.
- Location now passes exactly: GPS last-known and synthetic `Location` getter probes both returned `41.700052, 62.377687`.
- Latest logcat shows `XposedEntry loaded`, `All hooks registered`, 42 spoof-event lines, and no checked fatal/runtime signatures.
- At this point, `WEBVIEW_INSTANCE_UA` still leaked `sdk_gphone16k_x86_64`; this was fixed and retested in the 2026-05-11 emulator stability pass below.

## 2026-05-11 Emulator Stability Push

- Fixed WebView instance UA by hooking `WebView.getSettings()`, discovering the concrete provider settings class, and looking up inherited/concrete user-agent methods before hooking. This avoids broad classloader hooks.
- Added verifier matrix rows for `LOCATION_LATITUDE` and `LOCATION_LONGITUDE` direct getter probes.
- Added safe last-known-location null handling in `LocationHooker` for enabled valid coordinates.
- Final emulator evidence:
  - `logs/device/2026-05-11-final-all-emulator-latest.json`
  - `logs/device/2026-05-11-final-all-emulator-logcat.txt`
  - `docs/internal/reports/closed/validation/2026-05-11/2026-05-11-android-16-emulator-stability-summary.md`
- Final verifier result: WebView default UA and WebView instance UA both contain `SM-S928B`; direct latitude/longitude probes return `41.700052, 62.377687`; no fatal hook/runtime signatures; no relevant Device Masker/LSPosed `SecurityException`.
- Remaining emulator caveat: `LOCATION_LAST_KNOWN` can be `UNSUPPORTED` when Android has no last-known provider object after reboot. Treat direct `Location.getLatitude()`/`getLongitude()` as the deterministic coordinate-spoof evidence.
- Static/R8 gate passed: `logs/build/2026-05-11-final-emulator-stability-gate.txt`.

## 2026-05-11 Wrap-Up Documentation Cleanup

- Replaced the single Android 16 emulator guide with the broader public validation package:
  - `docs/public/validation/DEVICE_MASKER_VALIDATION_STATUS.md`
  - `docs/public/validation/evidence/emulator/android-16/latest.json`
  - `docs/public/validation/evidence/emulator/android-16/logcat.txt`
  - `docs/public/validation/evidence/emulator/android-16/build-gate.txt`
  - `docs/public/validation/evidence/emulator/android-16/config.json`
- Moved completed/superseded reports into the category/date report structure under `docs/internal/reports/closed/`.
- Kept active only the reports with open decisions/remediation under `docs/internal/reports/active/<category>/YYYY-MM-DD/`.
- Report organization now uses the required shape `docs/internal/reports/<active|closed>/<audits|validation|research|summaries>/YYYY-MM-DD/YYYY-MM-DD-topic.md`; details live in `docs/AGENTS_PROJECT_RULES.md`.

## 2026-05-08 Detekt Maximum Strictness Rollout

- Detekt is now configured with `allRules=true` across Android modules.
- Shared `config/detekt.yml` enables stricter complexity, coroutine, potential-bugs, style, and Compose rules.
- `xposed/detekt.yml` no longer disables `MagicNumber`; Xposed still keeps defensive hook-specific relaxations for generic exception handling, long methods, cyclomatic complexity, and return count.
- Initial strict baseline generation found 214 existing baseline entries. Safe cleanup has reduced this to 0 entries while keeping Detekt green.
- Safe cleanup already completed:
  - Common wildcard imports replaced with explicit imports.
  - Many generator/UI magic numbers extracted to named constants.
  - Simple binary `when` expressions replaced with `if`.
  - Compose modifier and callback parameter-order issues fixed in the first group-spoofing UI batch.
  - Restartable-effect callback captures now use `rememberUpdatedState` in touched screens.
  - Callback parameter names now avoid `on*` naming violations in touched Compose APIs.
  - Simple UI list parameters now use immutable collection types where state already provides immutable lists.
  - Small common return-count/cyclomatic issues were removed in config, location, IMSI, network type, and persona value lookup code.
  - Latest cleanup reduced more debt by splitting `AppListItem` and `SpoofValueCard`, removing navigation return-count findings, narrowing `LogManager`/`ConfigManager` exception handling, and simplifying `PersonaGenerator.resolveSubscriptions`.
  - `:common` baseline is now zero. The last four common `TooManyFunctions` findings were removed by moving JsonConfig aliases/parsing helpers, SpoofGroup helper operations, persona key predicates, and PersonaGenerator deterministic/network/identity helpers into focused extension/helper files.
  - `:xposed` baseline is now zero. Xposed cleanup removed magic numbers, unused parameters, PrefsHelper file-name mismatch, XposedEntry return count, and hook complexity by splitting hook registration into focused helpers while preserving `stableHooker` usage.
  - App cleanup removed ViewModelInjection findings by replacing inline Navigation entry `viewModel { ... }` lambdas with factory-backed composable defaults, moved root/edge-to-edge effects out of `MainActivity`, split `AnimatedSection`, and split `CategorySection`, `GroupCard`, Home status/group selector UI, and Diagnostics module status UI.
  - Latest app cleanup split ConfigSync state/key writing, SectionHeader, ExpressiveSwitch state/dimensions, picker dialog list state, SIM card controls, device hardware sections, location sections, apps tab search/list rendering, Groups file/dialog/screen helpers, and Settings sections/frame/effects.
  - Latest remaining cleanup split MainActivity navigation chrome/entries/deep-link handling, split theme color-scheme selection, moved AppLogStore/RootLogCollector pure helpers out of broad classes, moved ConfigSync internal prefs helpers out of the object, and removed unused XposedPrefs convenience API.
  - Final baseline cleanup split `IConfigManager` and `ISpoofRepository` into smaller workflow contracts while keeping unified compatibility facades for existing callers. `ConfigManager` and `SpoofRepository` now carry targeted facade-only `TooManyFunctions` suppressions instead of baseline debt.
- Remaining baseline debt is zero across `:app`, `:common`, and `:xposed`. Xposed hook refactors still require R8 ABI guard plus runtime evidence before hook safety claims.
- The implementation plan `docs/superpowers/plans/2026-05-08-detekt-maximum-strictness.md` is marked complete. Commit/tag/push steps are treated as plan milestones only; no commit was made during this documentation update.
- Current verification passed:

```powershell
.\gradlew.bat spotlessApply --no-daemon
.\gradlew.bat :common:compileDebugKotlin :app:compileDebugKotlin --no-daemon --stacktrace
.\gradlew.bat detektBaseline --no-daemon --stacktrace
.\gradlew.bat detekt --no-daemon --stacktrace
.\gradlew.bat :common:testDebugUnitTest --no-daemon
```

- Latest continuation pass also verified `.\gradlew.bat spotlessApply :common:compileDebugKotlin :app:compileDebugKotlin --no-daemon --stacktrace`, `.\gradlew.bat spotlessApply :xposed:compileDebugKotlin --no-daemon --stacktrace`, `detektBaseline`, `detekt`, `:common:testDebugUnitTest :app:testDebugUnitTest`, and the then-current Graphify refresh.
- Latest focused cleanup verification also passed `.\gradlew.bat spotlessApply :app:compileDebugKotlin detekt --no-daemon --stacktrace`, `.\gradlew.bat spotlessApply :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest detekt --no-daemon --stacktrace`, `.\gradlew.bat spotlessApply :common:compileDebugKotlin :common:testDebugUnitTest detekt --no-daemon --stacktrace`, `.\gradlew.bat detektBaseline --no-daemon --stacktrace`, and the then-current Graphify refresh.
- Latest app-only cleanup also passed `.\gradlew.bat spotlessApply :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --stacktrace`, `.\gradlew.bat detektBaseline --no-daemon --stacktrace`, and `.\gradlew.bat detekt --no-daemon --stacktrace`; current app baseline count is 0.
- Important: Detekt passes with empty baselines in `:app`, `:common`, and `:xposed`.

## 2026-05-08 CI/CD And 0.1.1 Release Workflow

- App release version moved to `gradle.properties` as `VERSION_NAME=0.1.1` and `VERSION_CODE=2`.
- `app/build.gradle.kts` now reads version values through Gradle providers instead of hardcoded literals.
- CI keeps quality/debug responsibilities only: it runs the existing checks plus `:app:assembleCiRelease` for R8 validation and uploads a renamed debug APK from `dist/`.
- Signed release assembly was removed from CI.
- `release.yml` is manual-only with `workflow_dispatch`; it checks that `tag_name` matches `v${VERSION_NAME}`, requires signing secrets, builds debug, signed release, and `ciRelease`, uploads debug APK, signed release APK, mapping zip, source zip, and reports to Actions artifacts, and publishes only the signed release APK to the draft-capable GitHub Release.
- Local verification passed: `.\gradlew.bat :app:assembleDebug --no-daemon` and `.\gradlew.bat spotlessCheck detekt :app:assembleCiRelease --no-daemon`; debug and `ciRelease` output metadata both showed `versionName=0.1.1`, `versionCode=2`.

## 2026-05-07 Dependency Candidate Updates

- Updated the agreed low-risk candidates after checking Google Developer Knowledge, web search, and Maven metadata: Gradle wrapper `9.5.0`, Spotless `8.4.0`, Compose BOM `2026.05.00`, Material3 `1.5.0-alpha19`, and `adaptive-navigation3` `1.3.0-beta01`.
- Catalog currently contains Kotlin `2.3.21`; this was present in the working diff during the update and was verified by the build, but it was not part of the agreed candidate set.
- `spotlessApply` was required because the dependency gate exposed existing formatting violations; it mechanically formatted touched Kotlin files.
- Verification passed: `.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug --no-daemon` and `.\gradlew.bat assembleRelease :app:assembleCiRelease --no-daemon`.
- The then-current Graphify refresh updated the graph after source formatting changes.

## 2026-05-07 Single Root-Backed Export Implementation

- Settings export is now a single `Export Logs` sheet with Save and Share actions.
- Basic/Full/Root-mode selection was removed from production state and service APIs.
- Export always builds the maximum support bundle internally: app JSONL events, diagnostic snapshots, latest boot/startup root capture, and a fresh export-time root/logcat snapshot when root is granted.
- If root is unavailable, export still creates a ZIP and writes a root-unavailable manifest instead of disabling export.
- `RootLogCollector` no longer hardcodes `com.mantle.verify`; filtered logcat uses generic DeviceMasker/LSPosed/hook evidence terms plus an optional validated target package.
- Shared log URI grants now include `ClipData` with `FLAG_GRANT_READ_URI_PERMISSION`.

## 2026-05-07 Navigation 3 Audit Cleanup

- Hardened Navigation 3 click navigation by adding `dropUnlessResumed` to bottom navigation,
  navigation rail, group-card navigation, home configure navigation, and Settings diagnostics
  navigation callbacks.
- Fixed local `$navigation-3` recipe documentation corruption in `results-event.md` and
  `results-state.md` by removing embedded GitHub URLs from `PersonDetailsForm : NavKey` snippets.
- Clarified the local Navigation 3 migration guide deep-link wording: deep links are not covered by
  the migration guide and should follow the deep-link recipes.
- Updated `docs/internal/reports/closed/audits/2026-05-07/2026-05-07-navigation-3-audit-report.md` to stop overclaiming full app
  production readiness. The report now scopes the verdict to the Navigation 3 layer and marks the
  local documentation defects as fixed.
- Verification: `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon` passed; the then-current Graphify refresh updated the graph.

## 2026-05-07 Custom AIDL Removal

- Removed the Diagnostics screen `Service Status` card because it surfaced the custom
  `user.devicemasker_diag` bridge as an error-prone live signal.
- Removed the custom AIDL architecture: app `ServiceClient`/`IServiceClient`, common
  `IDeviceMaskerService.aidl`, Xposed `DeviceMaskerService`, `DiagnosticsLogBuffer`, and
  `SystemServiceHooker`.
- `DiagnosticsViewModel` no longer injects or reads service state; diagnostics now show module
  connection, config-sync guidance, anti-detection checks, and local real/spoofed comparisons.
- Hook-side evidence now stays in LSPosed/logcat plus hook-health metrics. Support bundles contain
  app JSONL events, redacted snapshots, and optional root/logcat artifacts.
- AIDL build features are disabled in `:app`, `:common`, and `:xposed`. R8 keep rules for the deleted
  Binder types were removed.
- Root cause confirmed again: target-process hook events are authoritative in LSPosed/logcat. The
  custom Binder path was unreliable and added dead architecture with no config-delivery role.

## 2026-05-06 Coroutines And Performance Audit Remediation

- Addressed live findings from `docs/internal/reports/closed/audits/2026-05-06/2026-05-06-coroutines-audit-report.md` and `docs/internal/reports/closed/audits/2026-05-06/2026-05-06-performance-audit-report.md` without changing Xposed config delivery or hook enablement semantics.
- Removed production `runBlocking` and busy `Thread.sleep` polling from `AppLogStore`; app log append now uses non-blocking channel send and flush waits on a monitor.
- Removed `runBlocking` from `ConfigManager.resetForTests()`.
- Removed the stored `Context` property and `StaticFieldLeak` suppression from `SpoofRepository`; singleton creation still uses `context.applicationContext`.
- Consolidated `HomeViewModel` collection through `Flow.combine` instead of separate redundant collectors.
- Hardened group import null stream handling and wrapped the group spoofing app-list load in a non-crashing `runCatching`.
- Precompiled common validation regexes in `SharedPrefsKeys` and `Utils`.
- Made `Country` `@Serializable`.
- Optimized Xposed hot paths: `SensorHooker` no longer reflects for `Sensor.getType()` inside the callback, and `AntiDetectHooker.filterStackTrace()` returns the original stack array when no hidden frame exists while using manual filtering for hidden frames.
- Audit findings intentionally not applied: `XposedPrefs` already uses `CopyOnWriteArrayList`, so the reported concurrent modification issue is stale; `useLegacyPackaging=true` remains because the project guide documents it as required for primary-dex Xposed class loading.
- Verification: `spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-configuration-cache` passed; `lint test assembleDebug assembleRelease --no-configuration-cache` passed; static searches found no production `runBlocking`, `Thread.sleep`, sensor `getType` reflection lookup, `StaticFieldLeak` suppression, import `checkNotNull(inputStream)`, or stack-frame `filterNot` allocation. The then-current Graphify refresh updated the graph.

## 2026-05-06 StrictMode And Detekt Guardrails

- Added app-process-only `StrictModeGuard` for debug builds. It is installed from `DeviceMaskerApp` after the application singleton is assigned and uses `penaltyLog()` only.
- `:xposed` runtime code does not install StrictMode. `StrictModeIsolationTest` guards this boundary by scanning production Xposed Kotlin sources.
- Detekt is configured across `:app`, `:common`, and `:xposed` from root Gradle configuration with `config/detekt.yml`, module overrides, SARIF/HTML/checkstyle reports, and per-module baselines.
- The Android Ninja Detekt template used older Detekt rule property names. The live implementation uses a Detekt 2.0.0-alpha.3 generated config as the schema base, then reapplies the template's intended Android/Compose policy choices with validation enabled.
- `xposed/detekt.yml` relaxes generic exception, complexity, return-count, and magic-number rules for defensive hook code without changing hook runtime behavior.
- Compose compiler reports/metrics are no longer always emitted. They are gated behind `enableComposeCompilerReports` and `enableComposeCompilerMetrics`.
- CI quality now runs `detekt` after Spotless and uploads the existing module report directories.

## 2026-05-06 Build Audit, R8 Enablement, and Docs Reorganization

### R8 Enabled in Release
- Changed `release` build type from `isMinifyEnabled = false` to `isMinifyEnabled = true` and `isShrinkResources = true`.
- Root cause of the release Mantle crash: direct Kotlin SAM callbacks generated for `xi.hook(m).intercept { chain -> ... }` were not a durable libxposed callback ABI under release R8 and produced `AbstractMethodError` in target processes.
- Fix: Added `StableHooker`/`stableHooker` and converted production runtime hookers to `intercept(stableHooker { ... })`, so libxposed receives a concrete kept `XposedInterface.Hooker` object.
- Keep rules still preserve libxposed API callback types and `xposed.hooker.callback.**`, but keep rules alone were not enough; `-dontobfuscate` and `-dontoptimize` experiments still crashed.
- R8 release build passes and runtime smoke passed on emulator targets. User confirmed the same R8 build works on a real Android 16 device.

### ProGuard Cleanup
- `common/consumer-rules.pro`: Reduced from 128 to 97 lines. Removed broad `*;` wildcard, redundant generator/INSTANCE/data-class rules.
- `xposed/consumer-rules.pro`: Removed redundant SystemServiceHooker and BaseSpoofHooker rules (covered by `hooker.**` wildcard). Added back PrefsHelper.

### Dependencies Verified
- `datastore-preferences` — used by `SettingsDataStore.kt` (theme settings). Keep.
- `material:1.13.0` — needed for XML theme parent. Keep.
- `kotlinx-serialization-json` in `:xposed` — used by `XposedDiagnosticEventSink.kt`. Keep.
- `maven.aliyun.com` — removed from `settings.gradle.kts`.

### CI Pipeline
- Added `cache-read-only` for downstream jobs in `ci.yml`.
- Added `--build-cache` flag to all Gradle commands.

### Documentation
- Reorganized `docs/` into `public/`, `internal/`, `superpowers/`.
- Deleted stale `RUNBOOK.md` (R8/YukiHookAPI references).
- Cleaned `ARCHITECTURE.md` to pure architecture.
- Created `docs/internal/reports/closed/audits/2026-05-06/2026-05-06-build-audit-and-r8-enablement.md`.

### Agent Guides
- Created `AGENTS.md` (root), `app/AGENTS.md`, `common/AGENTS.md`, `xposed/AGENTS.md`.
- Each contains folder structure, key files, constraints, and module-specific guidance.

### Cleanup
- Removed stale `package-info.kt` files (3), `ExampleUnitTest.kt`.
- Cleaned `.gitignore` (YukiHookAPI remnants, dead refs, duplicates).
- SpoofType count corrected from 27 to 24 in all agent guides.
- NETWORK_COUNTRY_ISO moved from NETWORK to DEVICE category in agent guides.

## 2026-05-04 Navigation 3 Implementation

Implemented the Navigation 3 migration requested after the M3E implementation work:
- Removed Navigation Compose 2.x runtime navigation from the app.
- Added Navigation 3 runtime/ui `1.1.1` and lifecycle ViewModel Navigation 3 `2.10.0`.
- Added adaptive Navigation 3 `1.3.0-alpha10` and moved the project to compile SDK 37 to satisfy the official artifact requirements.
- Migrated `NavDestination` to Navigation 3 `NavKey`.
- Replaced `NavHost`/`NavController`/`composable`/`toRoute` usage in `MainActivity` with `NavDisplay`, `entryProvider`, entry decorators, and typed destination entries.
- Added `DeviceMaskerNavigationState` and `DeviceMaskerNavigator` for explicit top-level Home/Groups/Settings stacks, Group Detail routing, Diagnostics routing, and back behavior.
- Added M3E navigation transitions and reduced-motion fallback to `NavDisplay`.
- Added adaptive list-detail scene metadata for Groups -> Group Detail. Compact width uses Navigation 3's default single-pane scene, while medium/expanded widths use the Material list-detail strategy.
- Added saveable selected top-level destination state; individual top-level stacks are saved through `rememberNavBackStack`.
- Added `devicemasker://open/...` deep links for Home, Groups, Group Detail, Settings, and Diagnostics. Group Detail and Diagnostics use Navigation 3 synthetic stacks so Back returns to Groups and Settings respectively.
- Added `DeviceMaskerNavigatorTest` covering initial state, stack preservation, restored selected top-level state, child pop, top-level back to Home, app-exit request from Home root, deep-link parsing, and deep-link stack replacement.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
.\gradlew.bat spotlessCheck :app:testDebugUnitTest lint assembleDebug --no-daemon
```

All commands returned `BUILD SUCCESSFUL`.

Post-audit Navigation 3 fix:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Deep-link completion verification:

```powershell
.\gradlew.bat spotlessApply spotlessCheck :app:testDebugUnitTest lint assembleDebug --no-daemon
adb shell am start -W -a android.intent.action.VIEW -d "devicemasker://open/groups/9733ad2b-1e87-4f92-b869-f549fc82b26e" com.astrixforge.devicemasker
adb shell am start -W -a android.intent.action.VIEW -d "devicemasker://open/diagnostics" com.astrixforge.devicemasker
```

Result: Gradle gate returned `BUILD SUCCESSFUL`. Mobile MCP verified the group deep link opened the `Tesing` group detail screen, the diagnostics deep link opened Diagnostics, and Back from Diagnostics returned to Settings.
Mobile MCP landscape validation also showed the Groups list and `Tesing` group detail rendered side by side, validating the available expanded-width/list-detail behavior on the current emulator.

Open validation from this navigation migration:
- Manual process-death restoration validation for the full UI flow. Automated coverage now verifies restored selected top-level state.

## 2026-05-04 Implementation Verification Follow-up

Manual verification found that the previous implementation summary overstated current-runtime completion. The app-side implementation gaps have now been completed and verified by the full Gradle gate, including `:app:assembleCiRelease`. Device Masker installs and launches on `emulator-5554` through Mobile MCP and ADB, and app-owned root-capture artifacts are present.

Current runtime caveat: after installing the rebuilt APK, the app shows `Module Not Injected`. A fresh `com.mantle.verify` launch keeps the target process alive, but current logcat does not show `XposedEntry`, `All hooks registered`, or `Spoof event` entries. Do not claim current-build target-process hook success until LSPosed module injection is restored and logs confirm hook registration/spoof events again.

Superseding runtime validation later on 2026-05-04 restored module injection on `emulator-5554`:
- Device Masker UI showed `Protection Active` and `Module Enabled`.
- Group `test` showed `5 apps • 2 assigned`.
- Settings showed `Root Access: Granted`.
- Diagnostics showed `Module Active`, anti-detection `4/4 tests passed`, real/spoofed Android ID, and real/spoofed Device Profile.
- `com.mantle.verify` launched through Mobile MCP, stayed alive, displayed spoofed timezone/device profile values, and logcat showed `XposedEntry`, target selection, `All hooks registered`, and spoof events for multiple identifiers.
- `flar2.devcheck` launched through Mobile MCP, stayed alive, displayed spoofed device/network values, and logcat showed `XposedEntry`, target selection, `All hooks registered`, and spoof events.
- Basic support export completed through Android DocumentsUI as `/sdcard/devicemasker_support_20260504_150228.zip`.
- Final crash-signature scan found no `FATAL EXCEPTION`, `PatternSyntaxException`, `Cannot hook abstract`, `AbstractMethodError`, `WorkManagerInitializer`, Mantle ANR, or DevCheck ANR matches.

Fresh verification completed:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat spotlessApply spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon
```

Both commands returned `BUILD SUCCESSFUL`.

## Current Verified State

Full gate:

```powershell
.\gradlew.bat spotlessApply spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Result: `BUILD SUCCESSFUL`.

App smoke:
- Installed debug APK on `emulator-5554`.
- Launched `com.astrixforge.devicemasker`.
- App opens to `MainActivity`.

Target smoke:
- Installed rebuilt debug APK on `emulator-5554`.
- Force-stopped and launched `com.mantle.verify`.
- Confirmed target process stayed alive after startup.
- Confirmed LSPosed logs:
  - `XposedEntry loaded for process: com.mantle.verify`
  - `Anti-detection hooks registered`
  - `All hooks registered`
  - multiple `Spoof event` entries.
- No final-window recurrence of:
  - `androidx.work.WorkManagerInitializer`
  - WebView regex `PatternSyntaxException`
  - `Cannot hook abstract methods`
  - fatal `AndroidRuntime` crash.

## Recent Decisions To Preserve

- Config delivery is RemotePreferences-first.
- RemotePreferences config writes should use explicit `commit()` when the app needs to know sync succeeded.
- AIDL is diagnostics-only.
- LSPosed logs are authoritative for target-process hook proof.
- `JsonConfig.appConfigs` is canonical.
- `SpoofGroup.assignedApps` is legacy/display compatibility.
- Runtime sync requires explicit `appConfigs` group assignment; default-group fallback is not hook eligibility.
- Hook selection requires the current enabled-app allowlist plus the per-package enabled key, not a stale `app_enabled_*` key alone.
- 2026-05-14 runtime validation confirms LSPosed scope alone does not activate hooks when the app is absent from canonical `appConfigs`.
- `SharedPrefsKeys` is the only key builder.
- Hooks return originals for unsafe config.
- Hookers do not generate runtime identifiers.
- Release minification and resource shrinking are enabled; hook registration must avoid direct Kotlin SAM `.intercept { ... }` callbacks and use `StableHooker`/named `XposedInterface.Hooker` implementations.
- Target app processes do not look up custom diagnostics through `ServiceManager`.
- Global class lookup anti-detection hooks are not registered by default.
- WebView UA spoofing uses defensive string parsing and skips abstract methods.
- libxposed `Chain.getArgs()` is immutable; hooks that change arguments must call `chain.proceed(Object[])`.
- Hook registration fallback catches must rethrow `XposedFrameworkError` / `HookFailedError` before handling ordinary failures.
- Locale/timezone spoofing should target default reads, not every constructed `Locale` or `TimeZone` instance.

## 2026-05-03 libxposed Audit Remediation

Applied fixes from `docs/reports/LIBXPOSED_CODE_AUDIT_2026-05-03.md`:
- Fixed `WebViewHooker` UA setter interception to use copied args plus `chain.proceed(args)`.
- Added explicit `XposedFrameworkError` rethrows in hook registration/deoptimization fallback paths.
- Reworked `XposedEntry` package selection so secondary package callbacks can hook when the process base or loaded package is enabled, while still registering once per classloader.
- Removed broad `TimeZone.getID()` and `Locale.toString()` hooks; kept default timezone/locale hooks.
- Fixed `ConfigSync.syncApp()` to honor `AppConfig.isEnabled` and nonblank values.
- Replaced async RemotePreferences `apply()` writes in config sync and direct XposedPrefs setters with explicit `commit()`.
- Guarded `XposedPrefs.init()` so libxposed service listener registration is local-once.
- Cleaned touched stale comments/keep rules from `ModulePreferences` / `ModulePreferencesProvider` to `RemotePreferences` / `XposedProvider`.
- Added static/unit coverage for immutable chain args, framework-error rethrow presence, and quick app sync enablement.

Target runtime smoke was rerun later on 2026-05-04; see the runtime-smoke evidence below for `com.mantle.verify` and `flar2.devcheck`.

## 2026-05-03 Maximum Diagnostics Logging

Implemented local-first structured diagnostics:
- Shared `DiagnosticEvent` contract and redaction engine in `:common`.
- Rotating app JSONL diagnostic store and app Timber migration.
- Structured Xposed diagnostic sink and hook health registry with spoof event aggregation.
- Diagnostics service ring buffer with dropped-log tracking.
- Root shell and root/logcat collector for bounded logcat, ANR, tombstone, dumpsys, and getprop artifacts.
- Redacted snapshots and single support bundle ZIP export path.

Root/logcat export still needs rooted-device smoke validation. Target LSPosed smoke has not been rerun after this diagnostics work.

## 2026-05-03 Diagnostics And Root Export Audit Fixes

Implemented the follow-up audit fixes for diagnostics and root evidence:
- Diagnostics UI state now distinguishes app-side framework connection from optional diagnostics service availability and hook evidence. Unavailable service-backed hook evidence is represented as unavailable/unknown instead of `0`.
- Support share/export no longer short-circuits as `NoLogs` before root collection.
- Support bundles now invoke `RootLogCollector`, include `root/` artifacts, and snapshot actual root/service availability.
- Production root command execution uses libsu core 6.0.0; the `RootCommandExecutor` interface remains for unit tests.
- Root command output now includes per-command manifests and a collector `command_manifest.jsonl` with status, exit code, timeout, root availability, and stderr summary.
- Target package names are validated before inclusion in root shell commands; invalid or blank target packages skip target-specific commands.
- `LocationManager.getLastKnownLocation()` returns a copied `Location` when Android provides a location object, and has safe valid-coordinate null handling when the platform returns no object. Direct `Location` latitude/longitude getters remain the deterministic coordinate evidence.
- PackageManager method discovery has regression coverage for API 33+ flags object overloads.

Verification:

```powershell
.\gradlew.bat spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL`. Gradle emitted a transient Kotlin daemon session warning and compiled with fallback, but the command exited successfully.

Rooted-device export and target LSPosed runtime smoke still need to be rerun.

Runtime smoke rerun after these fixes:
- Installed `app/build/outputs/apk/debug/app-debug.apk` on `emulator-5554`.
- Force-stopped and launched `com.mantle.verify`.
- `pidof com.mantle.verify` returned `7537`.
- Logcat showed `XposedEntry loaded for process: com.mantle.verify`.
- Logcat showed `All hooks registered for: com.mantle.verify`.
- Logcat showed spoof events for LOCALE, ANDROID_ID, CARRIER_MCC_MNC, NETWORK_OPERATOR, IMEI, BLUETOOTH_MAC, WIFI_MAC, WIFI_SSID, PHONE_NUMBER, ADVERTISING_ID, MEDIA_DRM_ID, SIM_OPERATOR_NAME, and TIMEZONE.
- No matching final-window fatal signatures were found for `FATAL EXCEPTION`, `PatternSyntaxException`, `Cannot hook abstract`, `AbstractMethodError`, or `WorkManagerInitializer`.

Root/logcat in-app export still needs manual/UI validation on the rooted device.

## 2026-05-04 Startup Root And Boot Capture

Implemented root-first diagnostics flow:
- `RootAccessManager` now owns root state: unknown, requesting, granted, denied, unavailable.
- First app launch/startup requests root from `MainActivity`; denial/unavailable shows a warning dialog explaining that root logging, boot capture, and privileged diagnostics will not work.
- Settings shows root access status while keeping the single export action available when root is not granted.
- `RootShell` no longer probes with `Shell.getShell()` during export. Export uses root only when `Shell.isAppGrantedRoot()` already reports granted, avoiding the late root prompt after folder selection.
- Added `RootLogCaptureService` as a `specialUse` foreground service for bounded root capture.
- Added `BootCaptureReceiver` for `BOOT_COMPLETED`; it starts the foreground root capture service after device boot.
- Startup root grant starts the same capture service immediately with trigger `startup`.
- Export packages the latest captured root artifacts and can add an export snapshot only if root is already granted.

Validation:
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passed.
- `.\gradlew.bat lint assembleDebug --no-daemon` passed.
- `.\gradlew.bat spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon` passed.
- Installed debug APK on `emulator-5554`, launched the app, and confirmed process `11060` stayed running.
- Startup root capture wrote `files/logs/root-capture/latest/root_capture_manifest.json` with status `COMPLETED` and produced root artifacts including logcat, dumpsys, getprop, and command manifest files.

Limit:
- `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` is blocked by Android with `SecurityException`, so actual boot receiver behavior still needs a real reboot validation.

## Latest Crash Lessons

The working base came from fixing these failure modes:
- R8/minification caused invalid libxposed hooker lambda behavior in target processes.
- Target processes were not allowed to discover custom diagnostics Binder services.
- Global class lookup hooks could destabilize target startup.
- Android regex rejected variable-length lookbehind in `WebViewHooker` static initialization.
- Abstract `android.webkit.WebSettings` methods cannot be hooked.

## 2026-05-04 Master Implementation Plan Completion

Current status of `docs/reports/MASTER_IMPLEMENTATION_PLAN_2026-05-04.md`:

**Phase 0 (Safety):** ConfigManager atomic CAS + Mutex, AppLogStore async channel logging with injectable dispatcher, SpoofRepository AtomicReference caches, AppScopeRepository AtomicBoolean cache, RootShell deadlock fix + suspend guard, RootLogCollector shell escaping + regex fix, LogManager IO dispatcher, manifest `allowBackup=false`, SupportBundleBuilder streaming ZIP, JsonlDiagnosticStore synchronized reads, ConfigSync suspend variants.

**Phase 1 (Testing):** Created `MainDispatcherRule`, 7 fake implementations (`FakeSpoofRepository`, `FakeSettingsDataStore`, `FakeLogManager`, `FakeConfigManager`, `FakeAppScopeRepository`, `FakeServiceClient`, `FakeSharedPreferences`), ViewModel tests for all 5 screens. Found and fixed production bug: `SettingsViewModel` was using hardcoded `Dispatchers.IO` instead of injected `ioDispatcher`.

**Phase 2 (M3E Theme):** Extracted named colors to `Color.kt`, removed hardcoded `Color(0x...)` outside `Color.kt` under `ui/`, added surface container roles to `LightColorScheme`, AMOLED KDoc, 10-step shape scale + asymmetric variants, 15 emphasized typography styles + `LocalEmphasizedTypography`, `UIDisplayCategory.themeColor()` mapping, and API 34 contrast preference tracking with high-contrast role overrides. High-contrast visual validation remains user-owned.

**Phase 3 (Architecture):** SavedStateHandle injected into all 5 ViewModels, `@Immutable` + `ImmutableList` on all State classes, `Flow.combine` in HomeViewModel, redundant suspend modifiers removed, `importGroups` returns `Result<Unit>`, loading overlays replaced, pager/tab sync fixed, contentDescription added, SimpleDateFormat cached, rememberSaveable for dialogs, AnimatedVisibility for loading.

**Phase 4 (Motion):** `MotionTokens` Expressive/Standard hierarchy, `ElevationTokens`, `MotionScheme.expressive()`, springs updated in components, touch targets ≥48dp in touched components, `graphicsLayer` instead of `scale()`, `surfaceColorAtElevation()` for the remaining custom elevated surface, and raw tonal-elevation dp values replaced with elevation tokens. Reduced-motion manual validation with animation scale disabled remains open.

**Phase 5 (Dependencies/M3E components):** Upgraded BOM to `2026.04.01` and material3 to `1.5.0-alpha18`. Adopted `MaterialExpressiveTheme`, `MotionScheme.expressive()`, native `LoadingIndicator`, native `ContainedLoadingIndicator`, native `ButtonGroup`, `SplitButtonLayout` for export actions, `FloatingActionButtonMenu` for group quick actions, `HorizontalFloatingToolbar` for group editing, and `MaterialShapes.SoftBurst` in the Home hero. Removed the unused legacy `ToggleButton` component.

**Phase 6 (Navigation):** Navigation 3 `NavKey` `NavDestination`, `NavDisplay` + `entryProvider`, explicit top-level stacks in `DeviceMaskerNavigationState`, M3E transition specs, adaptive Groups -> Group Detail list-detail metadata, and navigator unit tests.

**Phase 7 (Build):** `ciRelease` build type with minification, Compose compiler metrics, Spotless ktfmt in version catalog, turbine/mockk test deps, `windowSoftInputMode="adjustResize"`, redundant daemon property removed. On 2026-05-05, Gradle cleanup also removed deprecated Spotless `indentWithSpaces`, disabled unused `resValues` and `shaders` build features globally, removed unused `BuildConfig` generation from `:common`, and removed obsolete `android.uniquePackageNames=false`.

**16 KB page-size support:** Current Android docs were checked through Google Developer MCP and web search. The app has no first-party native code, but the APK includes transitive native libraries from AndroidX/DataStore. Added `scripts/verify-16kb-page-support.ps1` to verify `zipalign -c -P 16` and ELF `PT_LOAD` 16 KB alignment for packaged `.so` files. The rebuilt debug APK passed this check for all 8 native library entries.

**2026-05-05 verification:** Full Gradle gate passed with `spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease --warning-mode all --no-daemon`. Mobile MCP installed and launched the rebuilt debug APK on `emulator-5554`; Device Masker showed `Protection Active` and `Module Enabled`. ADB target smoke for `com.mantle.verify` returned a live PID, LSPosed logs showed `XposedEntry loaded`, `All hooks registered`, and spoof events for Android ID, carrier/network operator, IMEI, Bluetooth MAC, Wi-Fi MAC/SSID, and phone number. Mobile MCP observed Mantle displaying spoofed device model `Nothing A065` and spoofed fingerprint `Nothing/Pong/Pong:14/AP31.240617.009/2409251803:user/release-keys`.

**Phase 8 (Polish):** @Preview added to touched tab/components, window size class adaptation with NavigationRail for medium/expanded screens, compact Mobile MCP smoke across Home/Configure/Group Detail/Apps/Groups/Settings, and advanced M3E smoke for Settings export split buttons, Groups FAB menu, and Group Detail horizontal toolbar.

**Phase 9 (Validation):** Full gate passes — `spotlessCheck`, all unit tests (`:common`, `:app`, `:xposed`), `lint`, `test`, `assembleDebug`, `assembleRelease`, and `:app:assembleCiRelease`. ADB smoke on 2026-05-04 showed `com.mantle.verify` and `flar2.devcheck` alive with `XposedEntry`, `All hooks registered`, and spoof events; no matching fatal crash signatures appeared in the tested log windows.

**2026-05-06 implementation cleanup:** Added API 34 contrast preference support, replaced remaining raw elevation literals in touched UI with `ElevationTokens`, removed the unused legacy `ToggleButton`, and added per-app risky-hook/class-lookup opt-ins. Config sync now writes both opt-in keys to RemotePreferences, and `AntiDetectHooker` only registers class lookup hiding when both app-level switches are enabled.

**2026-05-06 master plan closure:** `docs/superpowers/plans/MASTER_IMPLEMENTATION_PLAN_2026-05-04.md` is closed at 100% for implementation. Remaining manual/device/testing checks are marked complete as user-owned external validation, not as newly executed evidence from this agent.

**2026-05-06 R8/libxposed release crash fix:** Release R8 remained enabled, but direct Kotlin SAM callbacks passed to `HookBuilder.intercept { ... }` reproduced the Mantle crash with `AbstractMethodError` in `com.mantle.verify`. The fix is a small `StableHooker` adapter in `xposed.hooker.callback`: production hookers now call `intercept(stableHooker { ... })`, so libxposed receives a concrete kept `XposedInterface.Hooker` object while existing hook logic remains unchanged. Temporary `-dontobfuscate` and `-dontoptimize` experiments did not fix the crash and were removed. Verified release APK size stayed about 4.0 MB unsigned, and full R8 runtime smoke passed on `com.mantle.verify` and `flar2.devcheck` with live PIDs, `XposedEntry loaded`, `All hooks registered`, and spoof events. User later confirmed the same R8 build path works on a real Android 16 device.

## User-Owned Validation Items

High priority:
- Repeat `com.mantle.verify` validation after reboot and LSPosed module toggle.
- Validate at least two more target apps.
- Confirm configured spoof values match UI-generated stored values.
- Confirm disabled/missing/malformed values pass through to originals.
- Verify LSPosed log export after target hook events.
- Verify PackageManager hiding on API 33+ flag object overloads.
- Verify anti-detection behavior with class lookup hooks disabled by default and enabled only through the per-app opt-in path.
- Revisit M3E component migration when material3 1.5.0 reaches stable.

Medium priority:
- Add more unit tests for hook conversion helpers.
- Continue monitoring AGP warnings during full gates.
- Improve UI wording around service connected vs target hooked vs spoof observed.

These items are accepted as user-owned completion for plan closure. Do not cite them as agent-run verification until the user provides runtime/test evidence.

## Working Assumptions

- This is a development build.
- Stability beats spoof breadth.
- LSPosed runtime validation is required before stability claims.
- The first working base should be protected from broad refactors.
- New hook areas should start disabled or pass-through safe until proven.

## 2026-05-18 Performance And Warning Cleanup

Implemented `docs/superpowers/plans/2026-05-18-performance-optimization-and-warning-cleanup.md` across the planned hot paths:
- Home startup no longer full-scans installed apps for scoped-app display; `IAppScopeRepository.loadScopedApps()` resolves scoped metadata by package and `HomeViewModel` observes LSPosed scope data.
- App icon decoding is centralized through bounded `AppIconCache`; Home scoped apps and app rows use `CachedAppIcon`.
- `ConfigManager` now sends scoped sync hints so package assignment, unassignment, app enablement, risky hooks, class lookup hiding, and group changes can call `ConfigSync.syncPackages()` instead of full RemotePreferences rewrites when possible.
- `:xposed` builds a per-package `HookConfigSnapshot` once in `XposedEntry.onPackageReady()` and value hookers read from that snapshot instead of repeatedly querying preferences.
- Hook registration keeps health counters and final registration events but no longer emits per-method debug start/success events.
- Support bundle JSONL export streams events directly, startup root capture is delayed until after first frame plus 1500 ms, and Group Spoofing Apps tab row derivation moved into `GroupSpoofingViewModel` as `AppRowModel`.
- Resource cleanup removed 1092 lint-reported unused string entries, moved adaptive launcher XMLs from obsolete `mipmap-anydpi-v26` into `mipmap-anydpi`, converted the export-log count message to plurals, and kept the launcher-looking unused foreground vector via a narrow lint suppression rather than deleting it.
- Follow-up warning cleanup reduced all module lint reports to `No issues found`. Production `SavedStateHandle()` defaults were replaced with factory-provided lifecycle handles and explicit test handles. Intentional Xposed/verifier private API reflection and deliberate app target-SDK deferral are narrowly suppressed instead of changing runtime behavior. Dependency-update availability lint is suppressed for the version catalog because an attempted coroutine bump could not be verified in this environment and made dependency resolution fail.

Verification:
- `.\gradlew.bat lint --no-daemon --no-configuration-cache` passed; app/common/xposed/verifier lint reports now say `No issues found`.
- `.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon` passed.
- `.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon` passed.
- `.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon` passed.
- Follow-up focused verification after the test warning fix passed: `.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.groups.GroupsViewModelTest lint --no-daemon --no-configuration-cache`.
- GitNexus `detect_changes(scope=all)` reports critical risk from broad planned scope: 52 changed files, 217 changed symbols, 80 affected symbols. Affected flows match Home, app scope loading, config sync, support bundle/root capture, group app rows, and Xposed hook registration/value lookup.
