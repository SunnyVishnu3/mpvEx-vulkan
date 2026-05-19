# Xposed Spoofing, Architecture, and Logging Audit

Date: 2026-05-03  
Mode: analysis only, no production code changes  
Scope: `:xposed` hook architecture, libxposed API 101 usage, RemotePreferences config flow, diagnostics/log export, root maximum logging design, and spoof realism/coherence risks.

## Executive Summary

Device Masker's libxposed foundation is currently sound. The module uses the API 101 entry model, `XposedEntry` extends `XposedModule`, app hooks run from `onPackageReady`, system-server setup uses `onSystemServerStarting`, metadata files are packaged under `META-INF/xposed`, and `:xposed` keeps `io.github.libxposed:api` as `compileOnly`. The app-side service listener also uses the correct `onServiceBind` / `onServiceDied` callbacks.

The largest current problem is the logging/export pipeline, not the basic hook lifecycle. The UI exposes Root Maximum export, and `RootLogCollector` exists, but production export code never calls it. `LogManager` always passes `xposedEvents = emptyList()`, hardcodes `rootAvailable = false`, and never supplies `rootArtifactsDir` to `SupportBundleBuilder`. Share export can also return `NoLogs` before root collection is attempted. This explains why support bundles can be mostly empty even on a rooted device.

The second largest architecture gap is diagnostics routing. Target app hook callbacks intentionally do not use `ServiceManager`, which protects target startup, but it means the custom diagnostics service usually cannot receive target-process hook logs. LSPosed/logcat remains the authoritative hook evidence. If the app wants "maximum possible logs," Root Maximum export must collect bounded logcat/LSPosed artifacts through root, and the bundle must clearly record root grant status and command failures.

Spoof realism is partly good but still inconsistent. Persona generation is centralized in `:common`, and runtime hookers mostly return originals for missing values. Remaining realism gaps are coherence across multi-SIM slots, device profile consistency across Build/SystemProperties/WebView/sensors, network interface metadata, location/provider fields, package visibility API overloads, and registration-time profile reads that are not live after config changes.

## References Used

Local project context:

- `memory-bank/projectbrief.md`
- `memory-bank/productContext.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `docs/reports/LIBXPOSED_CODE_AUDIT_2026-05-03.md`
- `docs/reports/MAXIMUM_DIAGNOSTICS_LOGGING_ARCHITECTURE_2026-05-03.md`

Local libxposed skill/reference files:

- `.agents/skills/libxposed/SKILL.md`
- `.agents/skills/libxposed/references/javadoc/INDEX.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/01-package-and-XposedInterface.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/02-Chain-HookBuilder-HookHandle-Hooker.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/03-Invoker-ExceptionMode-Module-Interface.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/04-error-package.md`
- `.agents/skills/libxposed/references/javadoc/service-javadoc/01-service-complete.md`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/ModuleMain.java`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/ModuleMainKt.kt`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/App.kt`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/MainActivity.kt`
- `.agents/skills/libxposed/references/github/example/app/build.gradle.kts`
- `.agents/skills/libxposed/references/github/service/service/src/main/java/io/github/libxposed/service/XposedProvider.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/java/io/github/libxposed/service/XposedServiceHelper.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/java/io/github/libxposed/service/RemotePreferences.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/AndroidManifest.xml`

External/current references:

- libxposed API docs: https://libxposed.github.io/api/
- libxposed service docs: https://libxposed.github.io/service/
- libxposed service source: https://github.com/libxposed/service
- libxposed example source: https://github.com/libxposed/example
- Android logcat command-line docs: https://developer.android.com/tools/logcat
- Android Log Info Disclosure guidance: https://developer.android.com/privacy-and-security/risks/log-info-disclosure
- Android `READ_LOGS` reference: https://developer.android.com/reference/kotlin/android/Manifest.permission#READ_LOGS
- AOSP logging overview: https://source.android.com/docs/core/tests/debug/understanding-logging
- topjohnwu/libsu project: https://github.com/topjohnwu/libsu
- libsu `Shell` API docs: https://topjohnwu.github.io/libsu/com/topjohnwu/superuser/Shell.html

## Verification Performed

Static checks:

```powershell
rg -n '@XposedHooker|@BeforeInvocation|@AfterInvocation|AfterHookCallback|beforeHookedMethod|afterHookedMethod|XC_MethodHook' xposed/src app/src common/src -S
rg -n 'Timber\.' xposed/src -S
rg -n 'chain\.args\s*\[' xposed/src/main/kotlin -S
rg -n '"module_enabled"|"app_enabled_"|"spoof_value_"|"spoof_enabled_"' app/src xposed/src -S
rg -n 'RootLogCollector\(' app/src/main/kotlin app/src/test -S
```

Results:

- No legacy Xposed hook APIs are active. The only `XC_MethodHook` occurrence is an intentional detection string in `AntiDetectHooker`.
- No `Timber.` use in `:xposed`.
- No direct `chain.args[...]` mutation remains.
- No hardcoded active RemotePreferences key strings were found in app/xposed Kotlin.
- `RootLogCollector` is referenced only by its implementation and tests, not by production export code.

Targeted unit verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

Result:

```text
BUILD SUCCESSFUL in 27s
```

No runtime LSPosed smoke was run in this audit. Runtime hook correctness and root-log collection still require rooted LSPosed validation.

## Confirmed Good Areas

### libxposed API 101 Contract

- `XposedEntry` extends `XposedModule` and has no custom constructor, satisfying the no-arg constructor requirement.
- `onModuleLoaded(ModuleLoadedParam)`, `onPackageReady(PackageReadyParam)`, and `onSystemServerStarting(SystemServerStartingParam)` use the correct lifecycle names and param types.
- `xposed/src/main/resources/META-INF/xposed/java_init.list` contains `com.astrixforge.devicemasker.xposed.XposedEntry`.
- `module.prop` declares `minApiVersion=101` and `targetApiVersion=101`.
- `scope.list` includes `android` and `system`.
- `xposed/build.gradle.kts` uses `compileOnly(libs.libxposed.api)` for hook runtime API.
- App-side `XposedPrefs` implements `onServiceBind(XposedService)` and `onServiceDied(XposedService)`.
- App manifest provider authority is `${applicationId}.XposedService`, matching the official service library manifest.

### Hook Safety Improvements Already Present

- Hook registration wrappers rethrow `XposedFrameworkError` before generic `Throwable` handling.
- WebView setter hook copies args and calls `chain.proceed(args)`.
- High-risk global class lookup hiding is skipped by default.
- Package/list filtering returns filtered copies rather than mutating framework-returned lists in place.
- App and hook sides delegate preference keys to `SharedPrefsKeys`.
- Config sync uses `commit()` instead of async `apply()` for important RemotePreferences writes.

## Findings

### P0: Root Maximum Export Does Not Run Root Collection

Evidence:

- `RootLogCollector` is only referenced by itself and `RootLogCollectorTest`.
- `LogManager.buildSupportBundle()` never constructs or calls `RootLogCollector`.
- `LogManager` passes `xposedEvents = emptyList()` and no `rootArtifactsDir` into `SupportBundleBuilder`.
- `DiagnosticSnapshotMetadata.rootAvailable` is hardcoded to `false`.

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:84`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:108`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:126`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt:5`

Impact:

- Selecting Root Maximum produces a bundle shaped like a normal app/service export.
- Root prompt/grant may never be requested.
- LSPosed/logcat, ANR, tombstone, dumpsys, and getprop artifacts are not included.
- Bundles can be "95% empty" because the only included live sources are app-owned JSONL and the best-effort service buffer.

Recommendation:

- In `LogManager.buildSupportBundle()`, when `mode == ROOT_MAXIMUM`, create a cache subdirectory and call `RootLogCollector.collect(...)`.
- Pass that directory as `rootArtifactsDir` into `SupportBundleBuilder`.
- Add a command-results manifest to the bundle with command name, status, exit code, timeout, root availability, stdout/stderr path, and duration.
- Set `rootAvailable` from actual root detection, not `false`.
- Allow Root Maximum export to run even when app/service logs are empty.

### P0: Share Export Can Skip Root Collection Before Trying `su`

Evidence:

- `createShareableLogFile()` returns `ShareableLogResult.NoLogs` if `hasAnyLogs(context)` is false.
- `hasAnyLogs()` checks only app entries and diagnostics service logs.
- Root artifacts are not considered.

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:54`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:145`

Impact:

- If app JSONL and diagnostics service logs are empty, Root Maximum share export never attempts root logcat.
- This creates the exact user-facing symptom where export says no logs or produces empty content even though root logcat could contain LSPosed/module evidence.

Recommendation:

- For `ROOT_MAXIMUM`, bypass `hasAnyLogs()` and always build a bundle containing at least manifest, snapshots, root status, and command results.
- Keep the `NoLogs` shortcut only for Basic/Full modes, or replace it with a small bundle that explains which sources were empty/unavailable.

### P0: Target-Process Hook Logs Cannot Reach The Diagnostics Service In The Current Design

Evidence:

- `XposedEntry.getService()` only returns the local singleton when `DeviceMaskerService.isInitialized()` is true.
- Target app processes intentionally do not discover `user.devicemasker_diag` through `ServiceManager`.
- `reportSpoofEvent()` and `reportLog()` call `getService()?. ...`, so target process events usually only reach LSPosed/logcat, not the custom service buffer.
- `SystemServiceHooker` comment still says registration is "for discovery by hooked app processes," which contradicts the current safety rule and `XposedEntry` implementation.

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:302`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:307`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:334`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:342`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt:114`

Impact:

- `DeviceMaskerService.getLogs()` can be empty even while hooks are firing in target apps.
- App diagnostics may show no hook logs because target events were deliberately not sent to the service.
- LSPosed/logcat root collection is mandatory for full evidence.

Recommendation:

- Keep target processes from using `ServiceManager`; that was a stability win.
- Treat the diagnostics service as app/system-server health only.
- Make Root Maximum export collect LSPosed/logcat as the source of target hook truth.
- Update comments and UI wording to say "LSPosed/logcat evidence required for target hook events."
- Longer term: if durable target-process logs are required, use a libxposed-supported remote file or RemotePreferences event counter design, not custom ServiceManager lookup from target apps.

### P1: Root Shell Implementation Can Deadlock Or Hide Failure Reasons

Evidence:

- `SuCommandExecutor.execute()` waits for process completion before draining stdout/stderr.
- It reads full stdout/stderr with `readText()` after `waitFor`.
- Only stdout is byte-capped after it is already fully read.
- Stderr is not byte-capped.
- `RootLogCollector.collectFile()` writes only stdout to the final artifact and discards command metadata at the destination level.

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:62`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:63`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:95`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:96`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:100`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:101`

Impact:

- Large `logcat`/`dumpsys` output can fill OS pipe buffers and block the child process, causing timeouts or empty artifacts.
- Root denial, SELinux denial, command failure, and timeout can all look like empty files.
- The app cannot clearly tell the user whether root was denied, unavailable, timed out, or command output was empty.

Recommendation:

- Prefer `libsu` for root execution. It provides a maintained root shell abstraction, async shell access, and `Shell.isAppGrantedRoot()` for grant state.
- If keeping `ProcessBuilder`, stream stdout/stderr concurrently while the process runs, cap both streams, and always write command metadata.
- Include stderr and exit status in the bundle.
- Use short, bounded commands with explicit `timeout`.

### P1: Root Request UX Is Missing

Evidence:

- The app has no explicit root grant state in Settings export UI.
- Root availability is checked by running `su -c id`.
- There is no preflight explanation, retry, or "root denied" result path in UI state.

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt:86`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt:235`

Impact:

- Magisk/KernelSU may show a prompt, but the app does not surface the result well.
- If root is denied or not in the root manager allowlist, the user gets empty logs rather than actionable status.

Recommendation:

- Add an explicit Root Maximum preflight:
  - "Root not checked"
  - "Root prompt pending"
  - "Root granted"
  - "Root denied"
  - "Root unavailable"
- If using libsu, use `Shell.isAppGrantedRoot()` after creating a shell to report grant state.
- Do not add `READ_LOGS` as a normal solution. Android documents `READ_LOGS` as not for third-party apps and privileged-only in practice since API 16.

### P1: Root Log Commands Need Safer Quoting And Regex Handling

Evidence:

- `targetPackage` is interpolated into shell commands and grep regex.
- If `targetPackage` is empty, the grep regex ends with a trailing alternation (`|`), which can match broadly.
- If target contains regex metacharacters, grep behavior changes.
- `dumpsys package $target` runs even when target is blank.

File:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt:7`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt:14`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt:28`

Impact:

- Root logcat filtered output can become huge or misleading.
- Package-specific dumpsys commands can fail or dump unintended data.

Recommendation:

- Validate target package against an Android package-name regex before shell interpolation.
- Use fixed-string grep (`grep -F`) where possible.
- Build target-specific commands only when target is nonblank and valid.
- Avoid arbitrary user-controlled shell fragments.

### P1: Xposed Events Are Never Persisted As App-Owned `xposed_events.jsonl`

Evidence:

- `SupportBundleBuilder` supports `xposed/xposed_events.jsonl`.
- `LogManager` always passes `xposedEvents = emptyList()`.
- `XposedDiagnosticEventSink` logs to Android logcat / LSPosed and attempts best-effort service reporting, but there is no app-owned persistence path for target events.

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt:29`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt:126`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/diagnostics/XposedDiagnosticEventSink.kt:30`

Impact:

- `xposed_events.jsonl` is expected by the bundle structure but empty by design.
- The only practical target event source is LSPosed/logcat, which is not included unless Root Maximum is wired.

Recommendation:

- Either remove or clearly label `xposed_events.jsonl` as service/app-bridge only, or implement a real durable source.
- For now, root logcat artifacts should be the canonical target hook evidence in bundles.

### P1: Registration-Time Reads Reduce Live Config Realism

Evidence:

- `SystemHooker` reads `DEVICE_PROFILE` once at hook registration and mutates `Build` fields.
- `SensorHooker` reads `DEVICE_PROFILE` once at hook registration.
- `WebViewHooker` reads `DEVICE_PROFILE` once at hook registration.

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt:31`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SensorHooker.kt:36`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt:32`

Impact:

- UI may imply RemotePreferences live delivery, but profile-derived hooks need target restart or callback-time reads.
- Build field mutation is inherently process-global and restart-bound.

Recommendation:

- Document these hooks as "restart-bound" until changed.
- Move callback-safe profile reads into WebView and Sensor hooks if live behavior is required.
- Keep direct `Build` field mutation as an early-read best effort, but pair it with callback-time `SystemProperties` hooks.

### P2: Location Spoofing Mutates Returned `Location` Objects And Misses Provider Metadata

Evidence:

- `LocationManager.getLastKnownLocation()` hook mutates the returned `Location` instance in place.
- Latitude and longitude are spoofed, but provider, accuracy, altitude, bearing, speed, elapsed realtime, and mock flags are not made coherent.

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt:77`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt:84`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt:88`

Impact:

- Apps that cross-check full `Location` objects can detect impossible combinations.
- Mutating a framework-returned object can affect app-local references in surprising ways.

Recommendation:

- Return a copied `Location(original)` with spoofed fields instead of mutating original where practical.
- Add coherent optional fields: accuracy, altitude, speed, bearing, provider, time/elapsedRealtimeNanos.
- Consider hooks for active location callbacks/request flows, not only getters and last-known location.

### P2: Multi-SIM And Slot Semantics Are Too Simple

Evidence:

- Slot-indexed TelephonyManager methods return the same configured IMEI/IMSI/ICCID for every slot.
- SubscriptionInfo getters return one configured identity regardless of subscription object.

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt:75`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt:89`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt:45`

Impact:

- Dual-SIM devices may expose identical identifiers across slots.
- Apps can compare slot/subscription IDs and detect unrealistic identity mapping.

Recommendation:

- Add persona schema support for per-slot SIM identities.
- Tie `SubscriptionInfo` spoof values to subscription/slot identity where possible.
- If only one SIM persona exists, prefer preserving original list shape and spoofing only the active/default slot consistently.

### P2: Package Visibility Hiding Still Has API 33+ Coverage Risk

Evidence:

- `PackageManagerHooker` uses generic reflection to catch two-parameter lookups and one-parameter list methods.
- `AntiDetectHooker` comment mentions API 33 flags but only explicitly hooks int signatures in the shown paths.

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt:278`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/PackageManagerHooker.kt:48`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/PackageManagerHooker.kt:72`

Impact:

- Some modern overloads may be covered by generic method selection, but tests should prove this on API 33+.
- AntiDetect and PackageManager hookers duplicate similar responsibilities with slightly different method coverage.

Recommendation:

- Add reflection/static tests that assert API 33+ `PackageInfoFlags` / `ApplicationInfoFlags` overloads are discovered.
- Consolidate package hiding method discovery into one shared helper to reduce drift.

### P2: Network Realism Needs Interface-Level Coherence

Evidence:

- `WifiInfo.getMacAddress`, `getSSID`, `getBSSID`, `NetworkInterface.getHardwareAddress`, and `BluetoothAdapter.getAddress` are covered.
- `NetworkInterface` hook only changes hardware address for interface names starting with wlan/wifi/p2p.

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/NetworkHooker.kt:85`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/NetworkHooker.kt:96`

Impact:

- Apps can inspect interface display name, MTU, interface addresses, `/sys/class/net`, ARP/neighbors, or `WifiInfo` details beyond SSID/BSSID/MAC.
- Spoofing only getter values is useful but not complete realism.

Recommendation:

- Add coherent Wi-Fi persona fields: SSID, BSSID, MAC OUI/vendor, link speed, frequency/band, RSSI range.
- Consider file/proc/sysfs read filtering only after runtime stability validation.
- Avoid broad file hooks until target startup safety is proven.

### P3: Diagnostics UI Still Risks Overclaiming Target Hook Status

Evidence:

- Home/Diagnostics observe `XposedPrefs.isServiceConnected`.
- Memory bank correctly says service connection is not proof that a target app is hooked.

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeViewModel.kt:32`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsViewModel.kt:46`

Impact:

- Users can misread "active" as "target hooked and spoofing observed."

Recommendation:

- Split statuses:
  - Framework service connected.
  - Required scope present.
  - Config synced.
  - Target process hook registration observed.
  - Spoof event observed.
- For the last two, use LSPosed/root logcat evidence or verified service events only.

## Root Logging Research Notes

Android's logging system stores logs in fixed circular buffers such as `main`, `system`, `crash`, `radio`, and `events`. The `logcat` CLI can read multiple buffers with `-b`, filter output by tag/priority, and format with `-v threadtime`. Android docs also note that some logcat options are root-only.

Android security guidance says `READ_LOGS` is not for third-party apps, and since Android 4.1 only privileged system apps can normally be granted broad logcat access. Android's guidance recommends app-owned internal logs for detailed application logging and sanitized logs in production. Therefore, this project should not depend on `READ_LOGS` for maximum logging.

For a root-focused app, the practical options are:

- App-owned JSONL logs for rootless reliability.
- LSPosed/Xposed `XposedModule.log(...)` / logcat for target-process evidence.
- Opt-in root logcat collection for full support bundles.
- A maintained root shell library such as topjohnwu/libsu for root prompt, shell lifecycle, async execution, and root grant state.

## Recommended Fix Plan

### Phase 1: Wire Root Maximum Correctly

1. Add root preflight state to Settings/ViewModel.
2. In `LogManager`, run `RootLogCollector` for `ROOT_MAXIMUM`.
3. Always build a Root Maximum bundle even when app/service logs are empty.
4. Include command metadata and stderr.
5. Set `rootAvailable` from actual root state.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Runtime:

```powershell
adb shell su -c id
adb shell su -c "logcat -d -v threadtime -b main,system,crash,events | grep -i DeviceMasker"
```

### Phase 2: Harden Root Command Execution

1. Prefer libsu; otherwise stream stdout/stderr concurrently.
2. Cap stdout and stderr while reading, not after full collection.
3. Validate and quote target package.
4. Skip target-specific commands when target is blank.
5. Add result manifest to support bundle.

### Phase 3: Make Target Hook Evidence Honest

1. Treat diagnostics service logs as best effort.
2. Make LSPosed/root logcat artifacts the primary target hook proof.
3. Update UI wording and report manifests accordingly.

### Phase 4: Improve Spoof Realism

1. Add per-slot SIM persona support.
2. Add callback-time reads for WebView/Sensor profile values or document restart-bound behavior.
3. Copy `Location` objects and spoof coherent metadata.
4. Add API 33+ package visibility tests.
5. Expand network persona coherence carefully, avoiding broad file hooks until runtime validated.

## Final Assessment

The current hook architecture is a good development base, but the diagnostics/export implementation does not match the "maximum logging" architecture document yet. Root Maximum is currently mostly a UI/export mode label; it does not collect root artifacts in production code.

The immediate fix should be logging/export wiring, not more hook breadth. Once Root Maximum reliably captures LSPosed/logcat and command status, the remaining spoofing work can be validated with real evidence instead of empty bundles and ambiguous app-side diagnostics.
