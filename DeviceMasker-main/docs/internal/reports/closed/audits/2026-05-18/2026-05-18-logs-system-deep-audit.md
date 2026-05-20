# Device Masker Logs System Deep Audit

Date: 2026-05-18

Status: closed. The audit findings were remediated by the logs system implementation and verified with a rooted Android 16 emulator export.

Closure summary:
- `xposed/xposed_events.jsonl` is now generated from parsed copied LSPosed/logcat evidence and was verified non-empty in a real support ZIP.
- App and Xposed JSONL remain parseable after redaction.
- Config, RemotePreferences, scope, security, and hook health snapshots now contain real exported state instead of placeholders.
- Root/logcat artifacts include command sidecar manifests and blank-output explanations.
- LSPosed `/data/adb/lspd/log*` paths are handled as files or directories and were verified copied in a real support ZIP.
- Logs Monitor is available from Settings for live root logcat capture during target reproduction.
- Final emulator evidence: `logs/device/2026-05-18-user-export-fixed-final-122647.zip`.

## Findings

### Critical: `xposed/xposed_events.jsonl` is blank by design

Evidence:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:142-147` builds the support bundle with `xposedEvents = emptyList()`.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt:31-32` always creates `app/app_events.jsonl` and `xposed/xposed_events.jsonl`.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt:36-37` writes to LSPosed/logcat, not to an app-owned durable file.

Problem: The export advertises an Xposed event file but the app has no Xposed event source. This is bogus shit: a file that looks authoritative but is structurally empty.

Root cause: The old custom diagnostics bridge was removed, correctly, but the export contract was not reshaped. Hook-side truth moved to LSPosed/logcat, while the bundle still expects an app-owned Xposed JSONL stream.

Fix direction: Stop pretending app-owned Xposed JSONL exists. For phase 1, export `xposed/logcat_filtered_devicemasker_lsposed.txt` and `xposed/logcat_all_buffers.txt` from root/logcat. Keep `xposed_events.jsonl` only if it is parsed from copied logcat lines during export, and mark parse failures in a manifest.

Verification:
- Run a target app that produces `XposedEntry loaded`, `Target package selected`, `All hooks registered`, and at least one `Spoof event`.
- Export logs.
- Assert the ZIP has non-empty `xposed/logcat_filtered_devicemasker_lsposed.txt`.
- Assert `xposed_events.jsonl` is either absent or non-empty with parsed events and a parse manifest.

### Critical: export snapshots are placeholders, not diagnostics

Evidence:
- `LogManager.kt:131-137` writes `targetPackage = null`, `scopePackages = listOf("android", "system")`, `configJson = "{}"`, `remotePrefs = emptyMap()`, and `hookHealthJson = "{}"`.

Problem: The bundle claims to include diagnostic snapshots but exports empty objects and a hardcoded two-package scope. This is hand-wavy bullshit, not evidence.

Root cause: `LogManager.buildSupportBundle()` owns export assembly but has no small data-provider boundary for current config JSON, RemotePreferences state, LSPosed scope, selected target packages, root status, or hook-health evidence.

Fix direction: Add a narrow `SupportSnapshotProvider` in `:app` that returns:
- redacted current `config.json`
- redacted RemotePreferences keys/versions
- current LSPosed scope from `XposedPrefs.scopedPackages`
- app-config package set from `JsonConfig.appConfigs`
- root status and last capture manifest
- no fake hook-health JSON unless parsed from LSPosed/logcat

Verification:
- Unit test `LogManager` with a fake provider and assert snapshot files contain real fake values.
- Device export after config changes should show the current scope/app-config package set, redacted.

### High: root export captures generic logcat, not the target context

Evidence:
- `LogManager.kt:103-104` calls `RootLogCollector().collect(File(rootDir, "export_snapshot"), null)`.
- `RootLogCaptureService.kt:57` also calls `RootLogCollector().collect(outputDir, targetPackage = null)`.
- `RootLogCollector.kt:40-41` filters logcat through `DeviceMasker|LSPosed|lspd|XposedEntry|All hooks registered|Spoof event` plus an optional target package.
- `RootLogCollectorTest.kt:62-73` explicitly verifies the no-target filter is generic.

Problem: Export-time capture does not know which target app the user is debugging. If the log buffer contains multiple targets or old sessions, evidence is noisy. If the target's lines are older than the circular buffer window, export is blank or misleading.

Root cause: The log data model has no "active debug target" or "last observed target" field. Everything is collected after the fact from a circular system buffer.

Fix direction: Add a simple app-side `LogCaptureContext`:

```kotlin
data class LogCaptureContext(
    val selectedTargetPackage: String?,
    val includeAllScopedTargets: Boolean,
    val sinceMillis: Long?,
)
```

Use it to build root commands and export manifest. Default to all canonical app-config packages if the user does not choose one. This removes special-case insanity from shell commands.

Verification:
- Test that selecting `com.mantle.verify` adds target-specific filtered logcat and `dumpsys package com.mantle.verify`.
- Export manifest records `selectedTargetPackage`.

### High: root/logcat capture is snapshot-only, not realtime

Evidence:
- `RootLogCollector.kt:36` uses `logcat -d -v threadtime -b main,system,crash,events`.
- Android official docs describe logcat as circular buffers and document `-b all` for all buffers.
- `RootLogCaptureService.kt:20-24` runs one capture and stops itself.

Problem: `logcat -d` dumps whatever is still in the buffer. It does not tail live hook activity. If the user reproduces after capture, the exported files miss the event. If the buffer rolled, the relevant lines are gone.

Root cause: The system has no long-lived bounded root log capture loop and no observable store for the UI.

Fix direction: Add a foreground `LiveLogCaptureService` that runs only when the user starts monitoring:
- `su -c logcat -b all -v threadtime`
- writes bounded rolling raw files under app files
- tees matched lines into a structured monitor store
- stops on user action, timeout, or app exit

Do not make this boot-wide by default. That would be enterprise sludge for this app. Keep boot/startup capture separate and bounded.

Verification:
- Start monitor, launch target, trigger verifier, stop monitor.
- Assert live file contains new hook lines without needing export-time `logcat -d`.

### High: LSPosed Manager logs are not copied

Evidence:
- LSPosed-Irena stores module and verbose logs under `/data/adb/lspd/log` and old logs under `/data/adb/lspd/log.old` in `ConfigFileManager.java`.
- LSPosed-Irena's `getLogs()` zips its log directories, tombstones, ANR, `logcat -b all -d`, and `dmesg`.
- Device Masker currently only gathers Android logcat, ANR/tombstone, dumpsys, and getprop through `RootLogCollector`.

Problem: Device Masker does not copy LSPosed Manager's own persisted module logs. That misses the most direct evidence source the user asked for.

Root cause: The collector is generic Android support-bundle code. It does not know LSPosed's root-owned log locations.

Fix direction: Add a root-only `LsposedLogCopyCollector`:
- copy `/data/adb/lspd/log` and `/data/adb/lspd/log.old` when readable
- include a manifest row for each attempted path: copied, missing, denied, empty, size
- do not edit, delete, chmod, or rotate LSPosed logs
- include copied files under `root/lsposed/log/` and `root/lsposed/log.old/`

Verification:
- On rooted LSPosed device, export after target hook run.
- Assert copied LSPosed module log files exist and contain Device Masker hook markers.
- If path missing, manifest must say missing instead of silently producing blank files.

### Medium: blank files are accepted as success

Evidence:
- `RootLogCollector.kt:66-75` writes `result.stdoutPath?.readText().orEmpty()` to the destination.
- `RootLogCollector.kt:62-64` writes command status to a separate manifest, but the blank artifact itself carries no inline reason.

Problem: Empty `anr_traces.txt`, tombstones, filtered logs, or root outputs can mean "no evidence", "command failed", "permission denied", or "glob matched nothing". The user sees blank files and has to guess.

Root cause: Artifact content and command status are split. The export format does not force every artifact to explain emptiness.

Fix direction: For every captured artifact, write a sidecar `*.manifest.json` with command, status, exit code, stdout bytes, stderr bytes, timeout, and reason. For intentionally empty outputs, write a short header line:

```text
# empty: command exited 1, stderr="No such file or directory"
```

Do this only for support artifacts, not app JSONL logs.

Verification:
- Unit test command with empty stdout and stderr.
- Assert destination has a sidecar manifest and the exported ZIP explains why it is empty.

### Medium: app log store can silently drop realtime events

Evidence:
- `AppLogStore.kt:43` uses `Channel.BUFFERED`.
- `AppLogStore.kt:66-69` uses `trySend`; on failure it decrements pending count without recording a dropped-event counter.
- `JsonlDiagnosticStore.kt` tracks dropped events only when a single encoded event exceeds the file size limit.

Problem: If app logging bursts faster than the writer, events can be dropped without an exported count. That makes "realtime logs" unreliable under stress.

Root cause: There is no explicit queue policy in the data model. It relies on `Channel.BUFFERED`, which hides capacity and behavior.

Fix direction: Use a named bounded capacity and a dropped-count metric:

```kotlin
private const val APP_LOG_QUEUE_CAPACITY = 2048
private val eventChannel = Channel<DiagnosticEvent>(APP_LOG_QUEUE_CAPACITY)
private val droppedQueueEvents = AtomicLong(0)
```

Export queue drops in `store_state.json` and support summary.

Verification:
- Unit test a saturated fake channel path or inject a writer that blocks.
- Assert dropped count appears in store stats and export summary.

### Medium: root collector reads text files as UTF-8 and may choke on binary artifacts

Evidence:
- `SupportBundleBuilder.kt:74-84` reads every root artifact through `bufferedReader(Charsets.UTF_8)` and rewrites it as text.
- `RootLogCollector.kt` currently creates text outputs, but the proposed LSPosed/KernelSU-style copy path may include ZIP/GZIP/tombstone archives.

Problem: Text redaction is correct for logcat, props, dumpsys, and JSON. It is wrong for copied archives or binary logs. Treating every file as UTF-8 is a future trap.

Root cause: The bundle builder has no artifact type in its data structure.

Fix direction: Introduce:

```kotlin
enum class ArtifactEncoding { TEXT_REDACTED, TEXT_RAW, BINARY_RAW }
data class SupportArtifact(val source: File, val zipPath: String, val encoding: ArtifactEncoding)
```

Redact only `TEXT_REDACTED`. Copy `BINARY_RAW` byte-for-byte and mark it in manifest.

Verification:
- Unit test a small `.tar.gz` artifact survives byte-identical.
- Unit test redaction still applies to logcat text.

### Medium: Diagnostics screen is not a log monitor

Evidence:
- `SettingsSections.kt:125-129` opens Diagnostics from Settings.
- `DiagnosticsScreen.kt` renders module status, config sync info, anti-detection checks, and value comparisons.
- There is no log stream, source filter, level filter, target filter, pause/resume, clear, copy, or export-from-current-view control.

Problem: The existing Diagnostics screen is useful for status checks, but it cannot answer "what happened just now when I launched target X?".

Root cause: Status diagnostics and log observation are mixed conceptually in docs, but the UI only implements status diagnostics.

Fix direction: Add a separate `Logs Monitor` screen opened from Settings. Keep Diagnostics unchanged. The monitor should read from the live log store and root/logcat stream state.

Verification:
- Settings has separate rows: `Diagnostics` and `Logs Monitor`.
- Logs Monitor updates after a live capture line is appended.

### Low: report/docs still overstate export usefulness

Evidence:
- `docs/public/ARCHITECTURE.md` says export builds the maximum local support bundle and includes app JSONL, snapshots, latest root capture, and export-time root/logcat snapshot.
- Current `LogManager.kt` still exports empty Xposed events and placeholder snapshots.

Problem: The docs describe the intended architecture, not current behavior. This is stale evidence language.

Root cause: Implementation moved in pieces. The docs were not downgraded when the durable Xposed-event source and real snapshot provider failed to exist.

Fix direction: Do not edit docs in this audit workflow. When implementation is fixed, update architecture. Until then, future reports should explicitly say "support export is under development; Xposed JSONL is not durable yet."

Verification:
- After code fix, update Memory Bank and architecture in the same implementation branch.

## Executive Summary

The current logs system is not failing because of one bad command. The data structure is wrong.

Right now there are three incompatible ideas:

1. App logs are durable JSONL in app storage.
2. Xposed logs are emitted to LSPosed/logcat only.
3. Export pretends both are durable bundle inputs.

That mismatch explains blank exports. `xposed/xposed_events.jsonl` is blank because there is no app-side Xposed event file. Snapshots are `{}` because no provider supplies real current state. Root files are often blank because the system takes a late snapshot from circular logcat buffers and writes stdout even when the command had no useful output.

The KISS fix is not a new enterprise logging platform. It is:

1. Make app JSONL durable and observable.
2. Copy LSPosed logs from known root paths when root is available.
3. Capture live logcat only when the user starts monitoring.
4. Export exactly what was captured, with manifests explaining missing or empty artifacts.
5. Add a simple Logs Monitor screen from Settings.

## Scope

Reviewed:
- app-side logging and export
- root capture and logcat collection
- hook-side logging path
- Settings and Diagnostics UI entry points
- local tests and previous reports
- upstream LSPosed-Irena and KernelSU-Next log/export patterns
- official Android logcat behavior

Not performed:
- no device/emulator run
- no LSPosed Manager UI interaction
- no ZIP export generated from a live device
- no source-code fix

## Source Inventory

Project-local:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStore.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCaptureService.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootCaptureStore.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/*`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/*`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/*`
- Memory Bank and architecture docs

External:
- Android logcat official docs: `https://developer.android.com/tools/logcat`
- LSPosed-Irena `LogsFragment.java`: `https://raw.githubusercontent.com/re-zero001/LSPosed-Irena/dev/app/src/main/java/org/lsposed/manager/ui/fragment/LogsFragment.java`
- LSPosed-Irena `LogcatService.java`: `https://raw.githubusercontent.com/re-zero001/LSPosed-Irena/dev/daemon/src/main/java/org/lsposed/lspd/service/LogcatService.java`
- LSPosed-Irena `ConfigFileManager.java`: `https://raw.githubusercontent.com/re-zero001/LSPosed-Irena/dev/daemon/src/main/java/org/lsposed/lspd/service/ConfigFileManager.java`
- KernelSU-Next `LogEvent.kt`: `https://raw.githubusercontent.com/KernelSU-Next/KernelSU-Next/dev/manager/app/src/main/java/com/rifsxd/ksunext/ui/util/LogEvent.kt`
- KernelSU-Next `Settings.kt`: `https://raw.githubusercontent.com/KernelSU-Next/KernelSU-Next/dev/manager/app/src/main/java/com/rifsxd/ksunext/ui/screen/Settings.kt`

GitNexus:
- Repo `DeviceMasker`, 6723 symbols, 300 processes.
- `LogManager` direct import consumer: `SettingsViewModel`.
- `SupportBundleBuilder` callers: `LogManager.buildSupportBundle` and tests.
- `RootLogCollector` callers: `LogManager.buildSupportBundle`, `RootLogCaptureService.runCapture`, and tests.
- Root capture flow: `onStartCommand -> runCapture -> RootLogCollector`.

## Research Inputs

Android logcat:
- Android logcat uses circular buffers.
- The default buffers do not include every useful source.
- `-b all` reads all buffers.
- `-v threadtime` is a standard scannable format.
- Many logcat options are root-only or device-version-dependent.

LSPosed-Irena:
- Provides a Logs screen with two tabs: module logs and verbose logs.
- Reads logs through manager service file descriptors, not by scraping UI text.
- Saves logs through a service `getLogs(zipFd)` call.
- The daemon keeps `/data/adb/lspd/log` and `/data/adb/lspd/log.old`.
- Its log ZIP includes LSPosed log dirs, tombstones, ANR, full `logcat -b all -d`, and dmesg.

KernelSU-Next:
- Settings exposes export log save/share through a bottom sheet.
- Bugreport capture writes a cache directory, runs root commands, captures `logcat -b all -v uid -d`, dmesg, tombstones, dropbox, pstore, `/data/adb` details, props, and then tars it.
- The useful lesson is not to clone its breadth. The useful lesson is that root exports should write concrete files first, then package them, and include broad root context when debugging root/kernel behavior.

## Root Cause Analysis

The data model is the design, and the current model is broken:

```text
current
  app JSONL events         -> real source
  xposed JSONL events      -> no source
  root logcat snapshot     -> late, circular, targetless
  support snapshots        -> placeholders
  UI Diagnostics screen    -> status checks, not a log reader
```

The right model:

```text
proposed
  LogEvent                 -> normalized row for UI/search
  LogArtifact              -> file copied/captured for export
  LogCaptureSession        -> one user/manual/startup/export capture
  CommandManifest          -> why each artifact exists or is empty
  SupportBundleManifest    -> index of all sources and gaps
```

Every log artifact should answer:
- where did it come from?
- when was it captured?
- was root required?
- what command copied it?
- how many bytes were captured?
- why is it empty?
- which target package was intended?

Until the export can answer those questions, blank files will keep wasting debugging time.

## Recommended Fix Architecture

### Phase 1: Fix Export Truthfulness

1. Replace fake Xposed JSONL:
   - remove `xposedEvents = emptyList()` from production path
   - either omit `xposed_events.jsonl` or build it from parsed root/logcat lines
   - add `xposed/source_manifest.json`

2. Add real snapshot provider:
   - `SupportSnapshotProvider`
   - reads current config JSON
   - reads RemotePreferences where available
   - reads LSPosed scope through `XposedPrefs`
   - records root state

3. Copy LSPosed persisted logs:
   - root copy `/data/adb/lspd/log`
   - root copy `/data/adb/lspd/log.old`
   - manifest each path

4. Expand root logcat:
   - keep current filtered log
   - add `logcat_all_buffers.txt` using `logcat -d -v threadtime -b all`
   - add target-specific filtered output when a target is selected

5. Fix blank artifacts:
   - every root artifact gets a sidecar manifest
   - empty outputs explain why

### Phase 2: Add Logs Monitor

Settings should open a dedicated `Logs Monitor` screen, not overload Diagnostics.

Minimal navigation:

```text
Settings
  Debug
    Root access: Granted
    Export Logs
    Logs Monitor
    Diagnostics
```

Logs Monitor wireframe:

```text
+------------------------------------------------+
| <- Logs Monitor                         export |
| Root: Granted   Capture: Running        stop   |
+------------------------------------------------+
| [All] [App] [Xposed] [LSPosed] [Crash]         |
| Target: [All scoped apps v]   Level: [D v]     |
| Search: XposedEntry / package / hooker         |
+------------------------------------------------+
| 14:22:03.114 I DeviceMasker  XposedEntry...    |
| 14:22:03.220 I DeviceMasker  Target package... |
| 14:22:03.987 I DeviceMasker  All hooks...      |
| 14:22:04.103 D DeviceMasker  Spoof event...    |
| 14:22:04.118 W RootCapture   /data/anr empty   |
+------------------------------------------------+
| pause/resume | clear view | copy selected line |
+------------------------------------------------+
```

KISS UI rules:
- `LazyColumn` of `LogRowUi`
- monospace message only, not the whole screen
- chips for source/level
- start/stop live capture buttons
- no charts
- no timeline animation
- no nested cards inside cards
- export current capture from the toolbar

### Phase 3: Optional Indexing

Do not add a local DB just to fix export. That would be enterprise sludge.

Add Room only if the live monitor needs fast filtering/search across many sessions or more than roughly 10,000 rows. If added, keep it tiny:

```kotlin
@Entity(tableName = "log_events")
data class LogEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val wallTimeMillis: Long,
    val source: String,
    val level: String,
    val tag: String?,
    val packageNameHash: String?,
    val pid: Int?,
    val tid: Int?,
    val message: String,
    val rawLine: String?,
)
```

DAO:

```kotlin
@Query("""
SELECT * FROM log_events
WHERE (:source IS NULL OR source = :source)
  AND (:minLevel IS NULL OR level_rank >= :minLevel)
  AND (:query IS NULL OR message LIKE '%' || :query || '%')
ORDER BY wallTimeMillis DESC
LIMIT :limit
""")
fun observeLogs(source: String?, minLevel: Int?, query: String?, limit: Int): Flow<List<LogEventEntity>>
```

For phase 1 and phase 2, JSONL plus in-memory tail is enough:
- append-only files are simple
- export is direct
- less schema migration risk
- better for high-volume raw logcat
- no new dependency or KSP churn

## Proposed Data Structures

```kotlin
enum class LogSource { APP, XPOSED_LOGCAT, LSPOSED_FILE, ROOT_COMMAND, ANDROID_RUNTIME }
enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN }

data class LogEvent(
    val id: String,
    val sessionId: String,
    val wallTimeMillis: Long,
    val source: LogSource,
    val level: LogLevel,
    val tag: String?,
    val packageName: String?,
    val pid: Int?,
    val tid: Int?,
    val message: String,
    val rawLine: String?,
)

data class LogCaptureSession(
    val id: String,
    val trigger: String,
    val startedAtMillis: Long,
    val stoppedAtMillis: Long?,
    val targetPackage: String?,
    val rootState: RootAccessState,
)

data class CommandArtifactManifest(
    val path: String,
    val command: String?,
    val status: String,
    val exitCode: Int?,
    val stdoutBytes: Long,
    val stderrBytes: Long,
    val reason: String?,
)
```

This is the whole design. Anything more is random churn.

## Rejected Or Risky Approaches

- Custom Binder/AIDL from Xposed to app: forbidden by project rules and already removed for good reasons.
- Writing hook events from target processes into app-private files: unsafe and not available across target app sandboxes.
- Parsing LSPosed Manager UI text: brain-damaged API. Use root copy/logcat, not UI scraping.
- Always-on boot logcat tailing: too much battery/storage risk for a development app.
- Full Room database before export correctness: solves the wrong problem first.
- Copying `/data/adb/lspd` wholesale: too broad and privacy-risky. Copy only log dirs and manifest failures.
- Mutating LSPosed log permissions or rotating LSPosed logs: do not break userspace.

## UI/UX Review Audit

Current Settings debug section has:
- root status
- Export Logs
- Diagnostics

It needs:
- Logs Monitor between Export Logs and Diagnostics
- clear root/capture state
- no claim that module connected equals hooks working

Logs Monitor states:
- root unavailable: show app logs only, export still enabled
- root denied: show action to retry root grant
- capture stopped: show last session and Start button
- capture running: show Stop, Pause, source chips, current row count
- no hook evidence: show empty state saying no matching LSPosed/DeviceMasker lines captured yet
- parse errors: show raw lines still available

Accessibility:
- source and level chips need text labels
- log rows should be selectable/copyable
- long lines should wrap toggle or horizontal scroll option
- 48dp touch targets
- no color-only severity signal

Material direction:
- Foundational M3 Expressive only.
- Standard top app bar, filter chips, icon buttons, and `LazyColumn`.
- One prominent start/stop capture action is enough.

## Verification Plan

Use this plan for implementation later:

```text
1. Add real snapshot provider -> verify: unit test bundle contains fake config/scope/remote prefs, not "{}".
2. Add LSPosed log copy collector -> verify: fake root executor copies sample lspd log tree and writes path manifests.
3. Fix Xposed event export -> verify: xposed_events.jsonl absent or non-empty; no blank authoritative file.
4. Add artifact sidecar manifests -> verify: empty stdout still has reason/status in ZIP.
5. Add live log capture service -> verify: test parser and manual rooted run captures new target launch lines.
6. Add Logs Monitor screen -> verify: Compose state test for source/level/search filters and start/stop states.
7. Run app tests -> verify: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`.
8. Run static gate -> verify: `.\gradlew.bat spotlessCheck detekt lint --no-daemon`.
9. Run rooted device export smoke -> verify: ZIP contains app logs, LSPosed copied logs, root logcat, target filtered log, manifests.
10. Run target verifier smoke -> verify: LSPosed/logcat evidence plus verifier `latest.json` match expected config.
```

## Suggested Next Tasks

1. Implement export truthfulness first. Do not start with the UI.
2. Add LSPosed log copy collector with manifests.
3. Add live capture service and parser.
4. Add Logs Monitor screen from Settings.
5. Decide on Room only after the monitor has real volume data.

## AGENTS.md And Rule Drift Audit

Root and module rules are directionally correct:
- RemotePreferences-first remains correct.
- No custom Binder diagnostics remains correct.
- LSPosed/logcat is authoritative remains correct.
- Evidence must not claim hook success from app-side service connection remains correct.

Drift:
- Docs say support export is maximum and useful, but current code still exports placeholders and empty Xposed events.
- Architecture should explicitly say Xposed structured JSONL is not app-owned unless parsed from root/logcat or copied LSPosed files.
- Memory Bank should record that log export is currently under remediation.

Do not update those files in this audit workflow. Update them after implementation.

## Report File Path

`docs/internal/reports/active/audits/2026-05-18/2026-05-18-logs-system-deep-audit.md`

## Write Boundary Confirmation

This workflow wrote this report only, plus the minimum parent folder if it did not already exist. No source files, docs, Memory Bank files, generated logs, commits, branches, tags, or pushes were created or modified by this audit.
