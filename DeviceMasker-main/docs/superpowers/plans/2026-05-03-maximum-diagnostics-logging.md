# Maximum Diagnostics Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local-first, privacy-aware maximum diagnostics system that captures app logs, hook events, LSPosed evidence, root logcat, ANR/tombstone traces, and reproducible support bundles without cloud crash SDKs.

**Architecture:** Add a shared structured diagnostic event model in `:common`, replace the app's simple TSV log store with rotating JSONL session logs, mirror hook-side events into both LSPosed/logcat and best-effort diagnostics, and add a root-enabled collector for maximum support bundles. Keep config delivery RemotePreferences-first and AIDL diagnostics-only.

**Tech Stack:** Kotlin, kotlinx.serialization JSON, Android internal storage, libxposed API 101 logging, existing AIDL diagnostics service, Compose diagnostics/settings UI, optional root shell execution via `su`, zip export through `FileProvider`.

---

## Scope And Non-Goals

This plan intentionally does not add Crashlytics, Sentry, Google Analytics, cloud upload, or network telemetry.

This plan does add root-assisted local collection because the project is in active development and the maintainer is willing to support root for maximum debugging evidence.

Privacy rule: default exports are redacted. Unredacted debug exports must require explicit user confirmation.

## File Structure

Create:
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEvent.kt`  
  Shared serializable event schema, severity/source/event enums, redaction mode, helpers for event creation.
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticRedactor.kt`  
  Redacts identifiers, package names, config values, command output, and stack traces consistently.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSessionManager.kt`  
  Owns session id, boot id, session folder layout, and session lifecycle.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStore.kt`  
  Rotating JSONL writer/reader with file size caps, dropped count tracking, and corruption-tolerant reads.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`  
  Builds `devicemasker_support_<timestamp>.zip` containing app logs, diagnostics logs, redacted config/scope snapshots, root artifacts, and manifest.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt`  
  Small root command runner with timeout, stderr capture, exit code, and no long-running background shell.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`  
  Runs bounded root collection commands: logcat, filtered logcat, ANR, tombstones, dumpsys, getprop.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilder.kt`  
  Builds redacted config, RemotePreferences, scope, app/device, and hook health snapshots.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt`  
  Central hook-side event writer that logs to Xposed log/logcat and best-effort diagnostics service.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/HookHealthRegistry.kt`  
  Tracks hook registration attempts, successes, failures, skipped methods, deoptimize failures, and spoof event aggregation.

Modify:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt`  
  Either replace with or adapt to `JsonlDiagnosticStore`. Preserve existing tests until migrated.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`  
  Delegate to `SupportBundleBuilder`; keep old save/share public methods for UI compatibility.
- `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt`  
  Initialize diagnostic session and persistent app logging.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/PersistentAppLogTree` if kept inside `AppLogStore.kt`  
  Write structured `DiagnosticEvent` entries instead of freeform TSV entries.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ServiceClient.kt`  
  Add richer connection events and expose service availability reasons.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt`  
  Show log source health, dropped counts, root availability, current session id, capture status.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModel.kt`  
  Add basic/full/root support bundle export options.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`  
  Add export mode selection and unredacted warning.
- `common/src/main/aidl/com/astrixforge/devicemasker/IDeviceMaskerService.aidl`  
  Add structured diagnostics methods only if needed. Keep hook-to-service calls `oneway`.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/DualLog.kt`  
  Route through `XposedDiagnosticEventSink`; keep simple `debug/info/warn/error` API for call sites.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`  
  Log lifecycle, process selection, config gates, package selection, and hook registration lifecycle using structured events.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`  
  Record hook registration failures/skips and spoof passthrough reasons where practical.
- Hookers under `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/`  
  Gradually add event codes for first-N spoof returns and passthrough reasons.

Tests:
- `common/src/test/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEventTest.kt`
- `common/src/test/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticRedactorTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStoreTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootShellTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`
- `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/HookHealthRegistryTest.kt`
- Extend `app/src/test/java/com/astrixforge/devicemasker/service/AppLogStoreTest.kt` or replace with JSONL tests.

---

### Task 1: Shared Diagnostic Event Contract

**Files:**
- Create: `common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEvent.kt`
- Test: `common/src/test/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEventTest.kt`

- [ ] **Step 1: Write failing serialization tests**

Create `DiagnosticEventTest.kt` with tests for:
- round-trip JSON serialization
- required stable enum names
- safe default values
- event id uniqueness format

Test cases:
```kotlin
@Test
fun `event serializes with stable event code and context`() {
    val event =
        DiagnosticEvent(
            eventId = "evt_1700000000000_000001",
            timestampWallMillis = 1_700_000_000_000,
            timestampElapsedMillis = 42,
            sessionId = "session-1",
            bootId = "boot-1",
            source = DiagnosticSource.APP,
            severity = DiagnosticSeverity.INFO,
            eventType = DiagnosticEventType.REMOTE_PREFS_SYNC_COMMITTED,
            processName = "com.astrixforge.devicemasker",
            packageName = "com.astrixforge.devicemasker",
            pid = 123,
            tid = 456,
            threadName = "main",
            hooker = null,
            method = null,
            spoofType = null,
            status = "success",
            reason = null,
            configVersion = 100,
            prefsVersion = 100,
            moduleEnabled = true,
            appEnabled = true,
            message = "RemotePreferences sync committed",
            throwableClass = null,
            stacktrace = emptyList(),
            extras = mapOf("changed_keys" to "12"),
        )

    val json = DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event)
    val decoded = DiagnosticJson.decodeFromString(DiagnosticEvent.serializer(), json)

    assertEquals(event, decoded)
}
```

- [ ] **Step 2: Run the failing common test**

Run:
```powershell
.\gradlew.bat :common:testDebugUnitTest --no-daemon
```

Expected: fails because `DiagnosticEvent` does not exist.

- [ ] **Step 3: Implement `DiagnosticEvent.kt`**

Define:
- `DiagnosticSource`: `APP`, `XPOSED`, `SYSTEM_SERVER`, `LOGCAT`, `LSPOSED`, `ROOT`, `ADB`
- `DiagnosticSeverity`: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`
- `DiagnosticEventType`: include all event types listed in this plan's goal section.
- `DiagnosticEvent` as `@Serializable data class`.
- `DiagnosticJson` configured as `Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = false }`.

Do not include Android framework types in `:common`.

- [ ] **Step 4: Run common tests**

Run:
```powershell
.\gradlew.bat :common:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEvent.kt common/src/test/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEventTest.kt
git commit -m "feat: add shared diagnostic event contract"
```

---

### Task 2: Redaction Engine

**Files:**
- Create: `common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticRedactor.kt`
- Test: `common/src/test/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticRedactorTest.kt`

- [ ] **Step 1: Write failing redaction tests**

Test:
- IMEI-like 15 digit values become `[REDACTED_IMEI]`
- IMSI/ICCID/MAC/Android ID/location/phone become redacted
- package names can be preserved in unredacted mode and hashed in redacted mode
- stacktrace class names remain useful but sensitive values in messages are redacted

Required cases:
```kotlin
assertEquals("[REDACTED_IMEI]", redactor.redactValue("490154203237518"))
assertEquals("[REDACTED_MAC]", redactor.redactValue("02:00:00:12:34:56"))
assertEquals("[PKG:4f9c2a10]", redactor.redactPackage("com.bank.example"))
assertEquals("com.bank.example", unredacted.redactPackage("com.bank.example"))
```

- [ ] **Step 2: Run failing tests**

Run:
```powershell
.\gradlew.bat :common:testDebugUnitTest --no-daemon
```

Expected: fails because redactor does not exist.

- [ ] **Step 3: Implement redactor**

Implement:
- `enum class RedactionMode { REDACTED, UNREDACTED }`
- `class DiagnosticRedactor(mode: RedactionMode)`
- `redactValue(value: String): String`
- `redactPackage(packageName: String): String`
- `redactMessage(message: String): String`
- `redactEvent(event: DiagnosticEvent): DiagnosticEvent`

Use SHA-256 and first 8 hex chars for package hashes.

- [ ] **Step 4: Run common tests**

Run:
```powershell
.\gradlew.bat :common:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticRedactor.kt common/src/test/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticRedactorTest.kt
git commit -m "feat: add diagnostic log redaction"
```

---

### Task 3: Rotating JSONL Store And Session Manager

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSessionManager.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStore.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStoreTest.kt`

- [ ] **Step 1: Write failing JSONL store tests**

Test:
- appending writes one JSON object per line
- corrupted lines are skipped and counted
- max file size triggers rotation
- max sessions deletes oldest session folders
- dropped event counts are tracked

Expected file layout:
```text
files/logs/sessions/session_<sessionId>/
  app_events_000.jsonl
  app_events_001.jsonl
  store_state.json
```

- [ ] **Step 2: Run failing app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fails because store/session classes do not exist.

- [ ] **Step 3: Implement session manager**

Responsibilities:
- create `sessionId`
- read a stable `bootId` from `Settings.Global.BOOT_COUNT` when available, fallback to current elapsed realtime bucket
- create session directory under `filesDir/logs/sessions`
- keep last 10 sessions
- expose current session directory

- [ ] **Step 4: Implement JSONL store**

Required behavior:
- append events with `DiagnosticJson.encodeToString`
- rotate file at 2 MB by default
- tolerate corrupted lines on read
- expose `StoreStats(totalEvents, droppedEvents, corruptedLines, files, bytes)`
- no UI thread file I/O

- [ ] **Step 5: Run app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSessionManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStore.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStoreTest.kt
git commit -m "feat: add rotating diagnostic event store"
```

---

### Task 4: App Logging Migration

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/AppLogStoreTest.kt`

- [ ] **Step 1: Write failing compatibility tests**

Add tests proving:
- Timber app logs become `DiagnosticEvent`
- exception class is captured
- stacktrace is captured for errors
- messages are redacted before storage in redacted mode

- [ ] **Step 2: Run failing app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: fails until Timber tree writes structured events.

- [ ] **Step 3: Implement app logging migration**

Implementation direction:
- keep `PersistentAppLogTree` API but write `DiagnosticEvent` into `JsonlDiagnosticStore`
- emit `APP_START` during `DeviceMaskerApp.onCreate`
- emit `XPOSED_SERVICE_CONNECTED`, `XPOSED_SERVICE_DIED`, and RemotePreferences sync events from existing app-side call sites
- preserve old `LogFileFormatter` only as a temporary compatibility wrapper until support bundle export replaces it

- [ ] **Step 4: Run app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt app/src/test/java/com/astrixforge/devicemasker/service/AppLogStoreTest.kt
git commit -m "feat: write app logs as diagnostic events"
```

---

### Task 5: Xposed Structured Event Sink And Hook Health

**Files:**
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt`
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/HookHealthRegistry.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/DualLog.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`
- Test: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/HookHealthRegistryTest.kt`

- [ ] **Step 1: Write failing hook health tests**

Test:
- registration attempts increment
- success/failure/skipped/deoptimize failure counters increment
- spoof events aggregate by package/spoof type
- first 5 spoof events are logged, later events aggregate at counts 10, 100, 1000

- [ ] **Step 2: Run failing xposed tests**

Run:
```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

Expected: fails because registry does not exist.

- [ ] **Step 3: Implement `HookHealthRegistry`**

Keep it JVM-testable:
- no Android-only dependencies
- use `ConcurrentHashMap`
- expose `snapshot(): HookHealthSnapshot`

- [ ] **Step 4: Implement `XposedDiagnosticEventSink`**

Sinks:
- `XposedEntry.instance.log(priority, tag, message, throwable)` for LSPosed/Xposed log
- `android.util.Log` only through existing safe wrapper
- `XposedEntry.instance.reportLog(...)` best-effort diagnostics

Never let logging throw into hook callbacks.

- [ ] **Step 5: Migrate `DualLog`**

Keep call sites stable:
```kotlin
DualLog.debug(tag, message)
DualLog.info(tag, message)
DualLog.warn(tag, message, throwable)
DualLog.error(tag, message, throwable)
```

Internally route to structured event sink.

- [ ] **Step 6: Add lifecycle/hook registration events**

In `XposedEntry`, log:
- `XPOSED_ENTRY_LOADED`
- `REMOTE_PREFS_UNAVAILABLE`
- `TARGET_PACKAGE_SELECTED`
- `HOOK_REGISTRATION_STARTED`
- `HOOK_REGISTERED`
- `HOOK_FAILED`

In `BaseSpoofHooker.safeHook`, log:
- method name
- hooker tag
- failure exception class
- framework errors are still rethrown

- [ ] **Step 7: Run xposed tests**

Run:
```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 8: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/DualLog.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/HookHealthRegistryTest.kt
git commit -m "feat: add structured xposed diagnostics"
```

---

### Task 6: Diagnostics Service Structured Events

**Files:**
- Modify: `common/src/main/aidl/com/astrixforge/devicemasker/IDeviceMaskerService.aidl`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/service/DeviceMaskerService.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ServiceClient.kt`
- Test: add or extend xposed/app unit tests around formatting and parsing

- [ ] **Step 1: Decide whether AIDL needs schema change**

Preferred minimal path:
- keep current `oneway reportLog(tag, message, level)`
- encode structured event JSON into `message`
- add `getStructuredLogs(maxCount)` only if plain strings become too ambiguous

Do not add blocking hook-to-service calls.

- [ ] **Step 2: Write tests for service buffer metadata**

Test:
- max logs cap
- dropped count increments when over cap
- invalid JSON falls back to plain message event
- `clearDiagnostics()` records a clear event after clearing

- [ ] **Step 3: Implement service buffer improvements**

Add:
- dropped count
- structured event read support
- service source marked as `SYSTEM_SERVER`
- boot/session metadata if available

- [ ] **Step 4: Run tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/aidl/com/astrixforge/devicemasker/IDeviceMaskerService.aidl xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/service/DeviceMaskerService.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/ServiceClient.kt
git commit -m "feat: improve diagnostics service event buffer"
```

---

### Task 7: Root Shell And Maximum Log Collector

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootShellTest.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`

- [ ] **Step 1: Write root shell tests with fake executor**

Test:
- command timeout returns failure result
- stderr is captured
- exit code is captured
- command output is capped by bytes
- `su` unavailable returns `RootUnavailable`

- [ ] **Step 2: Implement `RootShell`**

Define:
- `RootCommand(command: String, timeoutMillis: Long, maxOutputBytes: Int)`
- `RootCommandResult(command, exitCode, stdoutPath, stderrPath, timedOut, durationMillis)`
- no shell command is built from user input without fixed command templates

- [ ] **Step 3: Write root collector tests**

Test collector produces requested artifacts for:
- `logcat_all`
- `logcat_filtered`
- `anr`
- `tombstones`
- `dumpsys_package_module`
- `dumpsys_package_target`
- `dumpsys_activity_processes`
- `getprop_redacted`

- [ ] **Step 4: Implement `RootLogCollector`**

Commands:
```text
logcat -d -v threadtime -b main,system,crash,events
logcat -d -v threadtime | grep -i -E 'DeviceMasker|LSPosed|lspd|AndroidRuntime|FATAL EXCEPTION|ANR|com.mantle.verify|<target>'
ls /data/anr
cat /data/anr/anr_*
ls /data/tombstones
cat /data/tombstones/tombstone_*
getprop
dumpsys package com.astrixforge.devicemasker
dumpsys package <target>
dumpsys activity processes
```

Use command timeouts and output caps. Redact after capture before export.

- [ ] **Step 5: Run app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootShellTest.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt
git commit -m "feat: add root maximum log collector"
```

---

### Task 8: Diagnostic Snapshots

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilder.kt`
- Test: add `DiagnosticSnapshotBuilderTest.kt`

- [ ] **Step 1: Write snapshot tests**

Snapshots required:
- `summary.json`
- `config_snapshot_redacted.json`
- `remote_prefs_snapshot_redacted.json`
- `scope_snapshot.json`
- `hook_health.json`

Test that raw identifiers are absent in redacted mode.

- [ ] **Step 2: Implement snapshots**

Include:
- app version/build type
- Android SDK/release
- device manufacturer/model/ABI
- root available
- LSPosed service connected
- framework name/version/api
- module enabled
- target package
- scope contains `android`, `system`, target
- diagnostics service state
- dropped log counts

- [ ] **Step 3: Run app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilder.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilderTest.kt
git commit -m "feat: add diagnostic support snapshots"
```

---

### Task 9: Support Bundle Builder

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`

- [ ] **Step 1: Write bundle tests**

Test zip contains:
```text
manifest.json
README_REPRO.md
app/app_events.jsonl
xposed/xposed_events.jsonl
diagnostics/service_events.jsonl
config/config_snapshot_redacted.json
config/remote_prefs_snapshot_redacted.json
scope/scope_snapshot.json
root/logcat_main_system_crash.txt
root/logcat_filtered_devicemasker_lsposed.txt
root/anr/
root/tombstones/
root/dumpsys_package_module.txt
root/dumpsys_package_target.txt
root/dumpsys_activity_processes.txt
root/getprop_redacted.txt
```

Test redacted bundle does not contain known raw identifiers.

- [ ] **Step 2: Implement builder**

Modes:
- `BASIC`: app events + diagnostics service if reachable + manifest
- `FULL`: basic + config/scope/hook snapshots
- `ROOT_MAXIMUM`: full + root collector artifacts

- [ ] **Step 3: Update `LogManager`**

Preserve:
- `exportLogsToUri(context, uri)`
- `createShareableLogFile(context)`

Internally generate support bundle zip. For compatibility, if UI expects `.log`, update UI filename generator in Task 10.

- [ ] **Step 4: Run app tests**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt
git commit -m "feat: export diagnostic support bundles"
```

---

### Task 10: UI Repro Capture And Export Modes

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Define UI states**

Add export modes:
- Basic
- Full Debug
- Root Maximum

Add redaction choices:
- Redacted
- Unredacted, requires confirmation

Add repro capture states:
- Idle
- Capturing
- Stopping
- ExportReady
- Error

- [ ] **Step 2: Write ViewModel tests if project has UI state test patterns**

At minimum test:
- root maximum mode requests root collector
- unredacted mode requires confirmation
- export result includes zip filename

- [ ] **Step 3: Implement Settings UI export mode selection**

Copy:
- "Basic Export"
- "Full Debug Export"
- "Root Maximum Export"
- "Redacted by default"
- "Unredacted logs may include package names and sensitive debugging data."

- [ ] **Step 4: Implement Diagnostics repro flow**

Steps in UI:
1. Select target app.
2. Confirm LSPosed scope.
3. Force-stop target reminder.
4. Start capture.
5. Reproduce issue.
6. Stop capture.
7. Export support bundle.

- [ ] **Step 5: Run app tests and lint**

Run:
```powershell
.\gradlew.bat :app:testDebugUnitTest lint --no-daemon
```

Expected: pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics app/src/main/res/values/strings.xml
git commit -m "feat: add diagnostic export modes"
```

---

### Task 11: Hooker Event Coverage Pass

**Files:**
- Modify hookers in `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/`
- Test: existing `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/HookSafetyTest.kt`

- [ ] **Step 1: Add static test for event coverage**

Test that every hooker file contains at least one structured event call or uses `BaseSpoofHooker.safeHook`.

- [ ] **Step 2: Add passthrough reason events**

Add event codes where practical:
- disabled module
- disabled app
- disabled spoof type
- missing value
- blank value
- malformed value
- unsupported method
- abstract method skipped
- original returned

Do not log sensitive raw values.

- [ ] **Step 3: Add spoof aggregation**

Use `HookHealthRegistry` from `reportSpoofEvent` so high-frequency hooks do not write every call forever.

- [ ] **Step 4: Run xposed tests**

Run:
```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/HookSafetyTest.kt
git commit -m "feat: expand hook diagnostic coverage"
```

---

### Task 12: Documentation And AGENTS Update

**Files:**
- Modify: `AGENTS.md`
- Modify: `memory-bank/systemPatterns.md`
- Modify: `memory-bank/techContext.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Create: `docs/reports/MAXIMUM_DIAGNOSTICS_LOGGING_ARCHITECTURE_2026-05-03.md`

- [ ] **Step 1: Document architecture**

Write:
- pipeline diagram
- support bundle contents
- root collection security model
- redaction rules
- runtime validation workflow
- known limitations: LSPosed logs still authoritative, diagnostics service best-effort

- [ ] **Step 2: Update AGENTS**

Add:
- no cloud logging
- local-first structured diagnostics
- root maximum mode requirements
- redacted default export rule
- never log raw identifiers by default

- [ ] **Step 3: Update Memory Bank**

Update:
- architecture diagrams
- diagnostics pattern
- build/runtime validation
- remaining work

- [ ] **Step 4: Commit docs**

```powershell
git add AGENTS.md memory-bank docs/reports/MAXIMUM_DIAGNOSTICS_LOGGING_ARCHITECTURE_2026-05-03.md
git commit -m "docs: document maximum diagnostics logging architecture"
```

---

### Task 13: Full Verification

**Files:**
- No source files unless verification finds bugs.

- [ ] **Step 1: Run full local gate**

Run:
```powershell
.\gradlew.bat spotlessApply spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Install debug APK**

Run:
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: install succeeds.

- [ ] **Step 3: Basic export smoke**

Manual:
- Open Device Masker.
- Export Basic bundle.
- Confirm zip opens.
- Confirm `manifest.json`, `app/app_events.jsonl`, and `README_REPRO.md` exist.

- [ ] **Step 4: Full debug export smoke**

Manual:
- Export Full Debug bundle.
- Confirm config and scope snapshots exist.
- Confirm redacted bundle does not contain known raw identifier values.

- [ ] **Step 5: Root maximum export smoke**

Manual on rooted emulator/device:
- Grant root.
- Start capture.
- Force-stop target app.
- Launch target app.
- Stop capture.
- Export Root Maximum bundle.
- Confirm logcat, filtered logcat, dumpsys, getprop, ANR/tombstone directories are present.

- [ ] **Step 6: LSPosed target smoke**

Run:
```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
adb shell pidof com.mantle.verify
adb logcat -d -t 1200
```

Expected:
- target process stays alive
- logs include `XposedEntry loaded`
- logs include hook registration
- logs include spoof events
- support bundle includes this evidence through root logcat or manual LSPosed logs

- [ ] **Step 7: Commit verification fixes if needed**

Only if bugs were fixed:
```powershell
git add <fixed-files>
git commit -m "fix: stabilize diagnostic logging verification"
```

---

## Rollout Strategy

Recommended implementation order:
1. Common event model and redaction.
2. JSONL store/session manager.
3. App logging migration.
4. Xposed event sink and hook health.
5. Support bundle builder.
6. Root collector.
7. UI export modes.
8. Hooker coverage pass.
9. Docs and full runtime validation.

Do not start root collection before the support bundle and redaction engine exist. Root output is too sensitive to collect without export controls.

## Risks

- Root commands can expose unrelated app/user data. Mitigation: opt-in root mode, redacted default, command timeouts, output caps, explicit warning.
- High-frequency hook logging can slow target apps. Mitigation: first-N logging and aggregation.
- Diagnostics service can be unavailable. Mitigation: support bundle must mark unavailable state and still include app/root/LSPosed evidence.
- JSONL files can corrupt after process death. Mitigation: one event per line and corruption-tolerant reads.
- Package names can be sensitive. Mitigation: redacted package hashes by default.
- LSPosed logs may not be readable from app without root. Mitigation: include root collector and manual LSPosed export instructions.

## Success Criteria

- Normal user can export a useful redacted support bundle without root.
- Rooted tester can export maximum logs including all logcat buffers, filtered DeviceMasker/LSPosed logs, ANR traces, tombstones, dumpsys, and getprop.
- Hook registration and spoof events are visible in LSPosed/logcat and represented in support bundles when collectable.
- Export records service unavailable, root unavailable, dropped log counts, and redaction mode.
- No raw identifiers appear in redacted exports.
- Full Gradle gate passes.
- `com.mantle.verify` runtime smoke remains stable.

## Self-Review

Spec coverage:
- Unified events: Task 1.
- Redaction/privacy: Task 2 and Task 9.
- Local JSONL logs: Task 3 and Task 4.
- Xposed/LSPosed hook diagnostics: Task 5, Task 6, Task 11.
- Root maximum logcat/ANR/tombstone/dumpsys collection: Task 7.
- Support bundle zip: Task 8 and Task 9.
- UI workflow: Task 10.
- Docs and Memory Bank: Task 12.
- Verification: Task 13.

Placeholder scan:
- No `TBD`, `TODO`, or "implement later" steps are present.
- Each task has concrete files, expected commands, and pass/fail criteria.

Type consistency:
- `DiagnosticEvent`, `DiagnosticRedactor`, `JsonlDiagnosticStore`, `DiagnosticSessionManager`, `RootShell`, `RootLogCollector`, `SupportBundleBuilder`, `XposedDiagnosticEventSink`, and `HookHealthRegistry` are introduced before later tasks use them.
