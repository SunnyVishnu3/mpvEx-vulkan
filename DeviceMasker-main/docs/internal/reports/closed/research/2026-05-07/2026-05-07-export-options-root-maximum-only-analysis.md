# Export Options Single Root-Backed Analysis

Date: 2026-05-07

Implementation update: this report has been reconciled with the 2026-05-07 cleanup. Production now exposes one `Export Logs` path with Save and Share actions. The old Basic/Full/Root labels describe the pre-cleanup UI only.

## Request

Before cleanup, Settings showed three export choices in the Export Options bottom sheet:

- Basic Export
- Full Debug Export
- Root Maximum Export

The requested product direction is a single export path. Internally it should use the existing maximum/root-backed support bundle pipeline, but the user-facing UI should not expose "Root Maximum" as a mode label. The visible UI should be a clean log export surface with two icon actions: Save and Share.

## Files Read

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModel.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsState.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ILogManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootAccessManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootCaptureStore.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt`
- `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeLogManager.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`
- `docs/public/ARCHITECTURE.md`
- `memory-bank/projectbrief.md`
- `memory-bank/productContext.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `graphify-out/GRAPH_REPORT.md`

## Previous Data Model

There were two mode enums:

- UI enum: `BundleExportMode` in `SettingsState.kt`
  - `BASIC`
  - `FULL_DEBUG`
  - `ROOT_MAXIMUM`
- Service enum: `SupportBundleMode` in `SupportBundleBuilder.kt`
  - `BASIC`
  - `FULL`
  - `ROOT_MAXIMUM`

`SettingsViewModel` maps the UI enum into the service enum:

- `BundleExportMode.BASIC` -> `SupportBundleMode.BASIC`
- `BundleExportMode.FULL_DEBUG` -> `SupportBundleMode.FULL`
- `BundleExportMode.ROOT_MAXIMUM` -> `SupportBundleMode.ROOT_MAXIMUM`

The selected mode was stored in `SettingsState.exportMode` and persisted across process death with `SavedStateHandle` key `exportMode`.

Current implementation:

- `BundleExportMode` was removed.
- `SupportBundleMode` was removed.
- `ILogManager` exposes `exportLogsToUri(context, uri)` and `createShareableLogFile(context)`.
- `SupportBundleBuilder` always writes manifest mode `MAXIMUM`.

## Previous UI Flow

`SettingsScreen` accepted:

- `exportMode`
- `onExportModeChange`
- `onExportLogsToUri`
- `onShareLogs`

The bottom sheet constructs a list of three `ExportModeAction` items. Each item has its own Save and Share action.

Previous behavior:

- Basic Export is always enabled.
- Full Debug Export is always enabled.
- Root Maximum Export is enabled only when `rootAccessState == RootAccessState.GRANTED`.
- The Settings list item description includes the current enum name, for example `... • BASIC`.

This is more state than the product now needs. Keeping `exportMode` after moving to one export path would be bogus shit: a mode selector with one valid value is not real state.

## Current Service Flow

`ILogManager` no longer accepts a mode. Both save and share use the same root-backed support bundle path.

`LogManager.exportLogsToUri()`:

- Builds the maximum support bundle.
- Copies it into the user-selected `content://` URI.

`LogManager.createShareableLogFile()`:

- Always builds a bundle.
- No longer returns `ShareableLogResult.NoLogs`.

`LogManager.buildSupportBundle()`:

- Reads app-owned diagnostic events from `AppLogStore`.
- Prepares root artifacts through `RootCaptureStore`.
- If root is currently granted, collects an export snapshot through `RootLogCollector`.
- If root is not granted, writes a `ROOT_UNAVAILABLE` manifest into the root artifacts directory.
- Builds redacted diagnostic snapshots.
- Calls `SupportBundleBuilder.build()`.

Important finding: the maximum support bundle can still produce a useful ZIP when root is unavailable. It includes normal app events, snapshots, and a manifest explaining root unavailability. Production now keeps export enabled when root is not granted.

## Current ZIP Contents

`SupportBundleBuilder` writes:

- `manifest.json`
- `README_REPRO.md`
- `app/app_events.jsonl`
- `xposed/xposed_events.jsonl`

- config snapshots under `config/`
- scope snapshots under `scope/`
- other snapshots under `diagnostics/`
- root artifacts under `root/` when present.

The old smaller export profiles did not contain unique diagnostic data. Removing them removed weaker profiles and UI friction.

## Tests And Docs Impact

Current direct test coverage is light:

- `SettingsViewModelTest` covers default export success/failure but does not assert mode mapping.
- `FakeLogManager` accepts a `SupportBundleMode` but does not record which mode was passed.
- `SupportBundleBuilderTest` only checks `ROOT_MAXIMUM` output.

Docs and Memory Bank still describe three support bundle modes:

- `memory-bank/systemPatterns.md`
- `memory-bank/progress.md`
- `memory-bank/activeContext.md`
- `docs/superpowers/plans/*`
- older internal reports

Implementation docs should be updated after code changes. Historical plans/reports can stay historical unless they are misleading in current architecture docs.

## Root Cause Of The Current Mess

The three-mode export UI came from the earlier maximum diagnostics plan. At that time, Basic and Full were reasonable staged export levels. After later cleanup, the app now treats LSPosed/logcat/root artifacts as the useful support evidence, and Root Maximum is the only path that can include that evidence.

Keeping three UI choices now creates pointless decision friction. Worse, Basic and Full can produce weaker bundles exactly when the user needs evidence for hook/runtime problems. This is not a performance optimization, it is just a confusing product surface.

## Recommendation

Use a single root-backed export path in Settings.

User-facing naming:

- Settings row title: `Export Logs`
- Bottom sheet title: `Export Logs`
- Actions: icon button for Save, icon button for Share
- Do not show Basic, Full Debug, Root Maximum, mode names, or implementation-heavy copy in the sheet.

Internal behavior:

- Always build the current maximum support bundle.
- Keep background root capture from startup and boot.
- Keep the export-time fresh root snapshot because it gives the latest LSPosed/logcat/hook evidence at the moment the user exports.
- Package both stored background capture and fresh export snapshot.

Recommended implementation scope:

1. Remove the Settings bottom-sheet list of three modes.
2. Replace it with a compact, polished action row containing two icon buttons: Save and Share.
3. Remove `BundleExportMode` entirely from UI state.
4. Remove `SettingsState.exportMode`.
5. Remove `SettingsViewModel.setExportMode()`.
6. Make `SettingsViewModel.exportLogsToUri()` always call `SupportBundleMode.ROOT_MAXIMUM`.
7. Make `SettingsViewModel.createShareableLogs()` always call `SupportBundleMode.ROOT_MAXIMUM`.
8. Change `ILogManager` defaults from `BASIC` to `ROOT_MAXIMUM`, or remove mode defaults if every caller passes the fixed mode.
9. Remove `SupportBundleMode.BASIC` and `SupportBundleMode.FULL` if no internal caller still needs them.
10. Simplify `SupportBundleBuilder`: always include snapshots and root artifacts when provided.
11. Remove the non-root `NoLogs` shortcut from the active share path.
12. Update tests to assert the single log manager path is used.
13. Update current architecture docs and Memory Bank after implementation.

## Final UX Shape

The sheet should feel like a normal export action, not a diagnostics architecture selector.

Suggested structure:

```text
Export Logs

[ Save icon ]    [ Share icon ]
```

The buttons can use accessible labels/content descriptions:

- `Save logs`
- `Share logs`

Avoid visible text like:

- `Root Maximum Export`
- `Basic Export`
- `Full Debug Export`
- `Unredacted logs may include...`
- `Root access is required...`

Root state can remain visible in the Settings debug section as a separate status row, but it should not make the export sheet noisy.

## Final Capture Design

Keep both capture moments:

```text
Startup / Boot
   -> RootLogCaptureService
   -> RootLogCollector
   -> files/logs/root-capture/latest/

Export
   -> LogManager
   -> copy latest_capture
   -> collect export_snapshot fresh
   -> SupportBundleBuilder
   -> single ZIP
```

Expected root entries in the ZIP:

- `root/latest_capture/...`
- `root/export_snapshot/...`
- `root/command_manifest.jsonl` or nested command manifests depending on the capture source

This is not overengineering. The background capture catches early boot/startup/hook timing evidence, and the export snapshot catches the freshest LSPosed/logcat state right before sharing.

## Root Access Behavior Recommendation

Do not disable the only export button just because root is unavailable.

Best behavior:

- Keep export actions enabled.
- If root is granted, include latest root capture plus export snapshot.
- If root is denied/unavailable, still create the bundle and include the `ROOT_UNAVAILABLE` manifest.
- Keep the Root Access status row visible so the user understands why root artifacts may be missing.

Reason: the service layer already handles the unavailable-root case. Disabling the only export path would throw away app logs and diagnostic snapshots for no technical gain.

## Proposed Verification

After implementation:

1. Static cleanup check -> verify no production references remain:
   - `rg "BundleExportMode|setExportMode|exportMode|SupportBundleMode.BASIC|SupportBundleMode.FULL" app/src/main`
2. UI string cleanup check -> verify removed labels are gone:
   - `rg "settings_export_basic|settings_export_full_debug" app/src/main`
3. Build check:
   - `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
4. Unit check:
   - `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModelTest --no-daemon`
   - `.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon`
5. Manual UI check:
   - Open Settings.
   - Tap Export Logs.
   - Confirm the sheet does not show Basic, Full Debug, or Root Maximum labels.
   - Confirm only Save and Share icon actions are available.
6. Root-unavailable check:
   - With root denied/unavailable, export still creates a ZIP containing manifest and snapshots.
7. Root-granted check:
   - With root granted, export includes `root/latest_capture/` and `root/export_snapshot/` artifacts.
8. LSPosed evidence check:
   - Confirm filtered logcat artifacts include DeviceMasker/LSPosed/hook evidence when those lines are present in logcat.

## Final Verdict

Removing Basic Export and Full Debug Export is safe if implemented as a single internal maximum export path, because the maximum bundle is the superset path and already handles unavailable root by writing an explanatory manifest.

The user-facing UI should not say `Root Maximum Export`. That is implementation wording. The clean product surface is one `Export Logs` sheet with Save and Share icon buttons. Internally, the app should still generate the maximum bundle with background capture plus fresh export-time LSPosed/logcat evidence.

The only thing to avoid is a half-cleanup where the UI shows one option but the old mode state/defaults remain underneath. That would be random churn, not cleanup. The clean fix is to collapse the data model to one export path and make the maximum bundle the fixed service behavior.
