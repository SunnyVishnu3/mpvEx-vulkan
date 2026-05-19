# Logs System Remediation Summary

Date: 2026-05-18

Status: closed summary.

## Summary

The logs/export system was rebuilt from placeholder-oriented output into a real support bundle flow. The app now exports app-owned JSONL, redacted config and RemotePreferences snapshots, LSPosed scope data, root/logcat artifacts, copied LSPosed persisted logs, parsed Xposed events, and derived hook health.

No local database was added. The current KISS data shape is still app-owned JSONL plus bounded in-memory live monitor rows. A database remains deferred until measured log volume or query needs justify it.

## What Changed

- Added real snapshot export through `DefaultSupportSnapshotProvider`.
- Added `LogCaptureContext` and root command sidecar manifests.
- Added all-buffer logcat capture and filtered Device Masker/LSPosed evidence capture.
- Added LSPosed persisted log copying from `/data/adb/lspd/log` and `/data/adb/lspd/log.old`, supporting both file and directory layouts.
- Added parsed Xposed event export from copied logcat/LSPosed evidence.
- Added derived `diagnostics/hook_health.json` from parsed Xposed events.
- Kept redacted app and Xposed event streams valid JSONL.
- Added Settings -> Logs Monitor for live root logcat capture, filtering, searching, and clearing during target reproduction.
- Updated architecture docs, project rules, and Memory Bank with the support bundle contract.

## Verification Evidence

Final rooted emulator export:

```text
logs/device/2026-05-18-user-export-fixed-final-122647.zip
```

Verified archive checks:

```text
app/app_events.jsonl: 1063 lines, 0 bad JSON lines
xposed/xposed_events.jsonl: 908 lines, 0 bad JSON lines
root/lsposed/lsposed_log.txt: 252560 bytes, copied from /data/adb/lspd/log directory
root/lsposed/lsposed_log_old.txt: 298241 bytes, copied from /data/adb/lspd/log.old directory
diagnostics/hook_health.json: totalEvents=908, hookRegistered=120, spoofReturned=576, hookFailed=0
```

Local gates run after the final code changes:

```powershell
.\gradlew.bat spotlessApply :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollectorTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --tests com.astrixforge.devicemasker.service.diagnostics.ParsedHookHealthSnapshotBuilderTest --no-daemon --no-configuration-cache
.\gradlew.bat spotlessCheck detekt :app:testDebugUnitTest lint :app:assembleDebug --no-daemon --no-configuration-cache
```

GitNexus change detection reported critical aggregate risk because the full implementation touches support export, root diagnostics, app logging, settings/navigation, docs, and tests. The affected flows match the intended logging/export work.

## Remaining Boundaries

- Support bundle hook evidence proves module/backend/log behavior. It does not replace verifier or target-app value checks for final spoof success.
- Redaction intentionally hides sensitive identifiers and package names.
- Logcat and LSPosed persisted logs are still bounded by device retention and rotation.
- Room/local database remains intentionally not added.
