# Android 16 Compatibility And Proc Maps Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Device Masker more stable on real Android 16 devices, isolate the DevCheck crash path, and implement the Java-first `/proc/self/maps` hardening report with evidence-driven testing.

**Architecture:** Keep the production architecture unchanged: `:app` writes RemotePreferences, `:common` owns contracts/keys, `:xposed` owns target-process hooks, and `:verifier` proves runtime behavior. Android 16 compatibility is implemented as evidence collection, hook policy isolation, safer Java maps filtering, and release/R8/device gates; native or system_server work stays opt-in and blocked behind proof.

**Tech Stack:** Kotlin 2.3.21, AGP 9.2.1, Gradle 9.5.0, compile SDK 37, target SDK 36, libxposed API 101, LSPosed, Android 13 emulator, real Android 16 device, Mobile MCP, ADB/logcat.

---

## Research Baseline

Use these references while executing:

- Android 16 all-app behavior changes: https://developer.android.com/about/versions/16/behavior-changes-all
- Android 16 target-SDK behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 16 compatibility framework changes: https://developer.android.com/about/versions/16/reference/compat-framework-changes
- Android 16 non-SDK restrictions: https://developer.android.com/about/versions/16/changes/non-sdk-16
- Non-SDK interface restrictions: https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
- 16 KB page-size guidance: https://developer.android.com/guide/practices/page-sizes
- libxposed API package summary: `.agents/skills/libxposed/references/javadoc/api-javadoc/01-package-and-XposedInterface.md`
- libxposed Chain/Hooker API: `.agents/skills/libxposed/references/javadoc/api-javadoc/02-Chain-HookBuilder-HookHandle-Hooker.md`
- libxposed error hierarchy: `.agents/skills/libxposed/references/javadoc/api-javadoc/04-error-package.md`
- Native hook reference: `.agents/skills/libxposed/references/github/LSPosed-wiki/Native-Hook.md`
- Current Java-first maps report: `docs/internal/reports/active/research/2026-05-09/2026-05-09-native-proc-self-maps-java-first-research-report.md`
- Combined audit: `docs/internal/reports/closed/research/2026-05-09/2026-05-09-device-masker-combined-research-audit.md`
- Hooking tools and Android 16 research: `docs/internal/reports/closed/research/2026-05-09/2026-05-09-xposed-hooking-tools-android-16-research-report.md`

Android 16 findings that matter for this project:

- Android 16 updates ART, and code relying on ART internal structures can break on Android 16 and even older versions with ART module updates.
- Android 16 updates restricted non-SDK interface lists. Reflection-heavy hooks must tolerate `NoSuchMethodException`, missing members, OEM variation, and target-SDK behavior.
- Android 16 compatibility framework toggles are useful for testing behavior without changing app target SDK.
- 16 KB page-size support matters because the app ships transitive native `.so` files even without first-party native code.
- Android 16 release notes mention known app crashes for libraries relying on internal ART structures, including HiddenApiBypass. Device Masker should not add HiddenApiBypass as a quick fix.
- `libxposed/helper` is useful only as a discovery/matching helper. It must not replace Device Masker's `stableHooker` callback registration path.
- Frida and DexKit are useful for lab research when DevCheck remains a black box. They must not become production runtime dependencies.
- ByteHook is the only native hook engine worth evaluating first, and only after verifier evidence proves Java maps filtering misses native reads. ShadowHook stays fallback-only; xHook and Dobby stay reference-only.

## Current Problem Statement

Real Android 16 device behavior is now the truth source for this track. Android 13 emulator success does not prove Android 16 stability.

Known issue:

- `flar2.devcheck` works in Android 13 emulator.
- On the user's real Android 16 device, some apps crash; DevCheck is a recurring crash case.
- Current `AntiDetectHooker` globally filters all `BufferedReader.readLine()` results containing LSPosed-like library patterns. It is not path-aware.
- Current `:verifier` does not prove Java maps read, byte maps read, native maps read, DevCheck-like detection paths, or Android 16 crash signatures.

Primary suspects for Android 16 DevCheck crashes:

- Hook incompatibility in `SystemHooker`, `SystemFeatureHooker`, `DeviceHooker`, or `SubscriptionHooker`.
- Broad anti-detection side effects from maps/package/class hiding behavior.
- ART/runtime changes affecting hook callback, reflection, deoptimization, or hidden member access.
- Android 16 / OEM framework method signature differences.
- Target app anti-detection intentionally aborting after seeing LSPosed/module evidence.

## File Responsibility Map

### New Files

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsHooker.kt`
  - Owns Java-first `/proc/self/maps` path detection, reader tracking, line redaction, byte redaction, and maps-specific logging.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsPolicy.kt`
  - Converts RemotePreferences into a boring hook policy object.
- `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsHookerTest.kt`
  - Unit tests path matching, line filtering, pattern matching, and failure fallback.
- `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/Android16CompatibilitySafetyTest.kt`
  - Static tests for A16 safety: no HiddenApiBypass, no direct SAM callbacks, no native/system_server default-on behavior, no raw maps logging.
- `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/LibxposedApiUsageSafetyTest.kt`
  - Static tests for libxposed API usage: `api` stays `compileOnly`, metadata stays API 101, legacy XposedBridge APIs stay absent, direct SAM callbacks stay absent, and framework errors are rethrown.
- `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/ProcMapsProbe.kt`
  - Reads `/proc/self/maps` through Java line, Java byte, RandomAccessFile, and optional native probe paths.
- `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/CrashProbe.kt`
  - Captures build, runtime, process, feature, and error facts that help compare A13 emulator vs A16 real device.
- `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/PackageVisibilityProbe.kt`
  - Captures PackageManager query behavior for known framework/module/root package names so package visibility hiding can be tested without copying Hide My Applist implementation.
- `scripts/collect-a16-crash-evidence.ps1`
  - One-command ADB capture for DevCheck crash logs, process state, device facts, and tombstone listing.
- `scripts/verify-16kb-page-support.ps1`
  - Restores/adds the missing 16 KB APK verification script referenced by Memory Bank if it is still absent during execution.
- `tools/frida/devcheck-proc-maps-trace.js`
  - Lab-only script for tracing DevCheck file-open/read behavior if normal logcat/tombstone evidence is inconclusive. Never package this into the APK.
- `docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md`
  - Living evidence report for Android 16 test runs and crash isolation results.
- `docs/internal/reports/closed/research/2026-05-09/2026-05-09-libxposed-helper-evaluation-report.md`
  - Records whether `libxposed/helper` is worth adopting for discovery-only matching in one hooker.
- `docs/internal/reports/active/research/2026-05-09/2026-05-09-native-hook-engine-decision-record.md`
  - Records the Java-vs-native maps evidence and whether ByteHook evaluation is justified.

### Modified Files

- `common/src/main/kotlin/com/astrixforge/devicemasker/common/AppConfig.kt`
  - Add safe default-false per-app compatibility flags only if needed for hook isolation.
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt`
  - Add all new RemotePreferences keys. No hardcoded preference keys elsewhere.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
  - Sync new default-false hook policy keys.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
  - Include new policy in `AppSyncState` if flags are added.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
  - Remove maps logic from broad anti-detect code and delegate to `ProcMapsHooker`.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHooker.kt`
  - Candidate for a small `libxposed/helper` discovery-only experiment if helper dependency resolution and tests pass.
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
  - Add hook-family policy checks if crash isolation proves they are needed.
- `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/VerifierActivity.kt`
  - Include maps, package visibility, and crash probes in `latest.json`.
- `app/src/test/java/com/astrixforge/devicemasker/ReleaseBuildSafetyTest.kt`
  - Add Android 16 and maps hardening static guards.
- `gradle/libs.versions.toml` and `xposed/build.gradle.kts`
  - Modify only if `libxposed/helper` adoption is proven useful and the artifact resolves cleanly. Do not add DexKit, Frida, ByteHook, ShadowHook, xHook, or Dobby as production dependencies in this plan.
- `docs/public/ARCHITECTURE.md`
  - Update only after implementation changes are real.
- Memory Bank files
  - Update only after implementation and validation, not while planning.

---

## Phase 0 - Preflight And No-Change Baseline

- [x] **Step 0.1: Verify branch and dirty state**

Run:

```powershell
git status --short --branch
```

Expected:

```text
## release/0.1.5
```

There may be untracked docs/reports. Do not delete, revert, stage, or commit them.

- [x] **Step 0.2: Read mandatory context before edits**

Run:

```powershell
Get-Content -Raw AGENTS.md
Get-Content -Raw docs/AGENTS_PROJECT_RULES.md
Get-Content -Raw xposed/AGENTS.md
Get-Content -Raw common/AGENTS.md
Get-Content -Raw .agents/skills/libxposed/SKILL.md
Get-Content -Raw .agents/skills/libxposed/references/javadoc/INDEX.md
Get-Content -Raw docs/internal/reports/closed/research/2026-05-09/2026-05-09-xposed-hooking-tools-android-16-research-report.md
```

Verify:

```powershell
Select-String -Path xposed/AGENTS.md -Pattern "stableHooker|XposedFrameworkError|/proc/self/maps"
Select-String -Path docs/internal/reports/closed/research/2026-05-09/2026-05-09-xposed-hooking-tools-android-16-research-report.md -Pattern "helper|ByteHook|Frida|DexKit"
```

Expected: all required guardrail and research terms are present.

- [x] **Step 0.3: Capture current build baseline**

Run:

```powershell
New-Item -ItemType Directory -Force logs/build,logs/device,logs/tmp | Out-Null
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest :verifier:assembleDebug lint assembleDebug :app:assembleCiRelease --no-daemon *> logs/build/2026-05-09-a16-baseline-gate.txt
Select-String logs/build/2026-05-09-a16-baseline-gate.txt -Pattern "BUILD SUCCESSFUL"
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 0.4: Capture A13 emulator baseline**

Run:

```powershell
$device = "emulator-5554"
adb -s $device shell getprop ro.build.version.release
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getconf PAGE_SIZE
adb -s $device shell pm list packages | Select-String "devicemasker|devcheck|mantle|lsposed"
```

Expected:

- Android release is `13`.
- SDK is `33`.
- Page size is normally `4096` on the current emulator.
- Device Masker, verifier, DevCheck, Mantle, and LSPosed are visible if installed.

No commit in this phase.

---

## Phase 1 - Android 16 Crash Evidence Capture

**Why:** Do not fix DevCheck from vibes. First collect exact crash class: Java exception, native tombstone, anti-detection exit, process kill, or hook registration failure.

**Files:**

- Create: `scripts/collect-a16-crash-evidence.ps1`
- Create: `docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md`

- [x] **Step 1.1: Add A16 evidence collection script**

Create `scripts/collect-a16-crash-evidence.ps1`:

```powershell
param(
    [string]$Device = "",
    [string]$TargetPackage = "flar2.devcheck",
    [string]$OutputDir = "logs/device"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $OutputDir | Out-Null
$stamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$prefix = Join-Path $OutputDir "$stamp-a16-$TargetPackage"
$adb = if ($Device.Trim().Length -gt 0) { @("adb", "-s", $Device) } else { @("adb") }

& $adb[0] @($adb[1..($adb.Length - 1)]) shell getprop > "$prefix-getprop.txt"
& $adb[0] @($adb[1..($adb.Length - 1)]) shell getconf PAGE_SIZE > "$prefix-page-size.txt"
& $adb[0] @($adb[1..($adb.Length - 1)]) shell dumpsys package $TargetPackage > "$prefix-package.txt"
& $adb[0] @($adb[1..($adb.Length - 1)]) logcat -c
& $adb[0] @($adb[1..($adb.Length - 1)]) shell am force-stop $TargetPackage
& $adb[0] @($adb[1..($adb.Length - 1)]) shell monkey -p $TargetPackage -c android.intent.category.LAUNCHER 1 | Out-File "$prefix-launch.txt"
Start-Sleep -Seconds 8
& $adb[0] @($adb[1..($adb.Length - 1)]) shell pidof $TargetPackage > "$prefix-pid.txt"
& $adb[0] @($adb[1..($adb.Length - 1)]) logcat -d -b main,system,crash,events -v threadtime > "$prefix-logcat.txt"
& $adb[0] @($adb[1..($adb.Length - 1)]) shell ls -lt /data/tombstones > "$prefix-tombstones-list.txt" 2>&1

Write-Host "Wrote evidence files with prefix: $prefix"
```

Verify:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\collect-a16-crash-evidence.ps1 -TargetPackage flar2.devcheck -OutputDir logs/tmp
Get-ChildItem logs/tmp | Select-String "a16-flar2.devcheck"
```

Expected: script writes files; if no A16 device is attached, the script may fail at ADB connection time, which is acceptable during local planning but must pass during A16 execution.

- [x] **Step 1.2: Add Android 16 crash report shell**

Create `docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md`:

```markdown
# Android 16 Compatibility And DevCheck Crash Report

Date started: 2026-05-09
Status: active

## Purpose

Track real Android 16 behavior separately from Android 13 emulator behavior. Android 13 smoke passing is not enough to claim Android 16 stability.

## Device Matrix

| Device | Android | SDK | Page size | LSPosed | App build | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Android 13 emulator | 13 | 33 | collect with `adb shell getconf PAGE_SIZE` | collect from LSPosed Manager/logcat | collect from APK metadata | baseline |
| Real Android 16 device | 16 | 36 | collect with `adb shell getconf PAGE_SIZE` | collect from LSPosed Manager/logcat | collect from APK metadata | DevCheck crash evidence required |

## DevCheck Crash Evidence

| Run | Module state | App enabled | Hook policy | Crash? | Evidence files | Root cause |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | disabled in LSPosed | none | none | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |
| 2 | enabled | app not configured | load-only | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |
| 3 | enabled | enabled | anti-detect only | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |
| 4 | enabled | enabled | device/system hooks only | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |
| 5 | enabled | enabled | telephony/subscription only | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |
| 6 | enabled | enabled | package/feature hooks only | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |
| 7 | enabled | enabled | all safe hooks | record yes/no after script run | record generated file prefix | classify from logcat/tombstone |

## Root Cause Notes

- No conclusion until logcat/tombstone proves the crash class.
- If crash occurs with module disabled, it is not a Device Masker hook crash.
- If crash occurs with module enabled but app not configured, suspect module load or anti-detection.
- If crash appears only after a hook family is enabled, fix that hook family first.
```

Verify:

```powershell
Select-String docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md -Pattern "No conclusion until"
```

Expected: the safety rule is present.

---

## Phase 2 - Per-App Hook Family Isolation

**Why:** A16 DevCheck crash needs a switchboard. Without hook-family isolation, every run is a giant “all hooks maybe broke” mess.

**Files:**

- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/AppConfig.kt`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookFamilyPolicy.kt`
- Modify tests under `app/src/test/.../ConfigSync*Test.kt` and `xposed/src/test/...`

- [x] **Step 2.1: Add default-safe hook-family policy**

Create `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookFamilyPolicy.kt`:

```kotlin
package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys

internal data class HookFamilyPolicy(
    val antiDetectEnabled: Boolean,
    val deviceEnabled: Boolean,
    val subscriptionEnabled: Boolean,
    val networkEnabled: Boolean,
    val systemEnabled: Boolean,
    val systemFeatureEnabled: Boolean,
    val locationEnabled: Boolean,
    val sensorEnabled: Boolean,
    val advertisingEnabled: Boolean,
    val webViewEnabled: Boolean,
    val packageManagerEnabled: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences, packageName: String): HookFamilyPolicy {
            fun enabled(key: String): Boolean = prefs.getBoolean(key, true)
            return HookFamilyPolicy(
                antiDetectEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "anti_detect")),
                deviceEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "device")),
                subscriptionEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "subscription")),
                networkEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "network")),
                systemEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "system")),
                systemFeatureEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "system_feature")),
                locationEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "location")),
                sensorEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "sensor")),
                advertisingEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "advertising")),
                webViewEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "webview")),
                packageManagerEnabled = enabled(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, "package_manager")),
            )
        }
    }
}
```

Add to `SharedPrefsKeys.kt`:

```kotlin
private const val PREFIX_HOOK_FAMILY_ENABLED = "hook_family_enabled_"

fun getHookFamilyEnabledKey(packageName: String, family: String): String {
    return "$PREFIX_HOOK_FAMILY_ENABLED${sanitize(packageName)}_$family"
}
```

Update `VALID_KEY_REGEX` to include:

```kotlin
"${PREFIX_HOOK_FAMILY_ENABLED}[a-zA-Z0-9_]+_[a-z_]+"
```

Verify:

```powershell
.\gradlew.bat :common:compileDebugKotlin :xposed:compileDebugKotlin --no-daemon
```

- [x] **Step 2.2: Gate hook registration by policy**

In `XposedEntry.onPackageReady(...)`, after classloader selection:

```kotlin
val policy = HookFamilyPolicy.fromPrefs(prefs, hookPackage)
```

Wrap each hooker:

```kotlin
if (policy.antiDetectEnabled) {
    hookSafely(hookPackage, "AntiDetectHooker") { AntiDetectHooker.hook(cl, this, hookPackage) }
} else {
    log(Log.INFO, TAG, "AntiDetectHooker disabled by policy for: $hookPackage", null)
}
```

Repeat for each existing hook family. Default is enabled, so current userspace behavior stays unchanged unless a policy key is explicitly false.

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

- [x] **Step 2.3: Add policy static tests**

Create tests asserting:

```kotlin
assertTrue(HookFamilyPolicy.fromPrefs(emptyPrefs, "flar2.devcheck").systemEnabled)
assertFalse(HookFamilyPolicy.fromPrefs(prefsWithSystemFalse, "flar2.devcheck").systemEnabled)
```

Also assert `SharedPrefsKeys.isValidKey(...)` accepts each hook-family key.

Verify:

```powershell
.\gradlew.bat :common:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

No UI is required for this phase. Use direct RemotePreferences/test setup first. UI controls can come later if the isolation proves useful.

---

## Phase 3 - Java-First Proc Maps Hooker

**Why:** Implement the report correctly: path-aware Java maps filtering first, not broad global readLine mutation and not native syscall hooks as default.

**Files:**

- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsHooker.kt`
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsPolicy.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsHookerTest.kt`

- [x] **Step 3.1: Add policy object**

Create `ProcMapsPolicy.kt`:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys

internal data class ProcMapsPolicy(
    val javaLineRedactionEnabled: Boolean,
    val javaByteRedactionEnabled: Boolean,
    val javaNioRedactionEnabled: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: SharedPreferences, packageName: String): ProcMapsPolicy {
            val risky = prefs.getBoolean(SharedPrefsKeys.getRiskyHooksEnabledKey(packageName), false)
            return ProcMapsPolicy(
                javaLineRedactionEnabled = true,
                javaByteRedactionEnabled = risky && prefs.getBoolean(
                    SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName),
                    false,
                ),
                javaNioRedactionEnabled = risky && prefs.getBoolean(
                    SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(packageName),
                    false,
                ),
            )
        }
    }
}
```

Add the two keys to `SharedPrefsKeys.kt`:

```kotlin
private const val PREFIX_JAVA_PROC_MAPS_BYTE_REDACTION_ENABLED = "java_proc_maps_byte_redaction_enabled_"
private const val PREFIX_JAVA_PROC_MAPS_NIO_REDACTION_ENABLED = "java_proc_maps_nio_redaction_enabled_"

fun getJavaProcMapsByteRedactionEnabledKey(packageName: String): String {
    return "$PREFIX_JAVA_PROC_MAPS_BYTE_REDACTION_ENABLED${sanitize(packageName)}"
}

fun getJavaProcMapsNioRedactionEnabledKey(packageName: String): String {
    return "$PREFIX_JAVA_PROC_MAPS_NIO_REDACTION_ENABLED${sanitize(packageName)}"
}
```

Default behavior:

- Java line redaction remains on for enabled targets.
- Byte/NIO redaction are off unless risky hooks are enabled and explicit keys are true.

Verify:

```powershell
.\gradlew.bat :common:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

- [x] **Step 3.2: Extract redaction matcher**

Create `ProcMapsHooker.kt` with this core first:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker

import java.io.File

internal object ProcMapsHooker {
    private val hiddenPatterns =
        listOf(
            "libxposed",
            "liblspd",
            "lsposed",
            "lspd",
            "riru",
            "zygisk",
            "sandhook",
            "substrate",
            "edxposed",
            "devicemasker",
            "com.astrixforge.devicemasker",
        )

    internal fun isSensitiveMapsPath(path: String, currentPid: Int = android.os.Process.myPid()): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized == "/proc/self/maps" ||
            normalized == "/proc/$currentPid/maps" ||
            normalized == "/proc/self/smaps" ||
            normalized == "/proc/$currentPid/smaps"
    }

    internal fun shouldRedactMapsLine(line: String): Boolean =
        hiddenPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }

    internal fun filterMapsText(text: String): String =
        text.lineSequence()
            .filterNot(::shouldRedactMapsLine)
            .joinToString(separator = "\n", postfix = if (text.endsWith('\n')) "\n" else "")

    internal fun pathFromArg(arg: Any?): String? =
        when (arg) {
            is String -> arg
            is File -> arg.path
            else -> null
        }
}
```

Verify with unit tests:

```kotlin
assertTrue(ProcMapsHooker.isSensitiveMapsPath("/proc/self/maps", currentPid = 123))
assertTrue(ProcMapsHooker.isSensitiveMapsPath("/proc/123/maps", currentPid = 123))
assertFalse(ProcMapsHooker.isSensitiveMapsPath("/proc/999/maps", currentPid = 123))
assertFalse(ProcMapsHooker.isSensitiveMapsPath("/proc/self/status", currentPid = 123))
assertTrue(ProcMapsHooker.shouldRedactMapsLine("7f00-7f10 r-xp /data/lib/liblspd.so"))
assertFalse(ProcMapsHooker.shouldRedactMapsLine("7f00-7f10 r-xp /system/lib64/libart.so"))
```

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.ProcMapsHookerTest --no-daemon
```

- [x] **Step 3.3: Add path-aware Java reader tracking**

Inside `ProcMapsHooker`, add weak tracking:

```kotlin
private val mapsReaders = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Any, Unit>())
private val mapsStreams = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Any, Unit>())

internal fun markReader(reader: Any) {
    mapsReaders[reader] = Unit
}

internal fun isMarkedReader(reader: Any?): Boolean = reader != null && mapsReaders.containsKey(reader)

internal fun markStream(stream: Any) {
    mapsStreams[stream] = Unit
}

internal fun isMarkedStream(stream: Any?): Boolean = stream != null && mapsStreams.containsKey(stream)
```

Hook constructors:

- `java.io.FileReader(String)`
- `java.io.FileReader(File)`
- `java.io.BufferedReader(Reader)`
- `java.io.FileInputStream(String)`
- `java.io.FileInputStream(File)`
- `java.io.RandomAccessFile(String, String)`
- `java.io.RandomAccessFile(File, String)`

Important implementation rule:

```kotlin
val result = chain.proceed()
if (ProcMapsHooker.isSensitiveMapsPath(path)) {
    chain.thisObject?.let(ProcMapsHooker::markReader)
}
return@stableHooker result
```

Use `stableHooker`, rethrow `XposedFrameworkError`, call `xi.deoptimize(m)`, and keep every method in a separate safe registration block.

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest :xposed:compileDebugKotlin --no-daemon
rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker
```

Expected: no direct callback matches outside allowed test/source comments.

- [x] **Step 3.4: Replace broad `BufferedReader.readLine()` behavior**

Move maps hook registration from `AntiDetectHooker.hookProcMaps(...)` to `ProcMapsHooker.hook(...)`.

New behavior for `BufferedReader.readLine()`:

```kotlin
val result = chain.proceed()
if (!ProcMapsHooker.isMarkedReader(chain.thisObject)) return@stableHooker result
val line = result as? String ?: return@stableHooker result
if (ProcMapsHooker.shouldRedactMapsLine(line)) {
    return@stableHooker readNextSafeLine(chain)
}
result
```

`readNextSafeLine(chain)` must:

- Loop a small bounded number of times, for example 64 lines.
- Return the next non-hidden line.
- Return `null` at EOF.
- Return original hidden line if a failure happens, because target safety beats anti-detection.

Do not return `""` for hidden maps lines anymore. Blank lines are a suspicious output shape.

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

- [x] **Step 3.5: Add Java byte redaction as opt-in**

Implement only after line filtering tests pass.

Behavior for marked `FileInputStream.read(...)`:

- On first read, consume original stream into a bounded byte buffer.
- Decode as UTF-8 with fallback to ISO-8859-1 if needed.
- Filter lines through `filterMapsText`.
- Store sanitized bytes in a per-stream weak buffer.
- Serve future reads from sanitized bytes.
- If the maps file is larger than `MAX_MAPS_BYTES`, pass through original behavior.

Constants:

```kotlin
private const val MAX_MAPS_BYTES = 2 * 1024 * 1024
private const val READ_CHUNK_BYTES = 8 * 1024
```

Edge cases to test:

- Single-byte `read()`.
- `read(byteArray)`.
- `read(byteArray, off, len)`.
- `len == 0` returns `0`.
- EOF returns `-1`.
- Hidden line split across chunks still removed.
- Non-maps stream is untouched.
- Parser failure returns original behavior.

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.ProcMapsHookerTest --no-daemon
```

- [x] **Step 3.6: Add Java NIO redaction as opt-in**

Hook only if classes/methods exist:

- `java.nio.file.Files.readAllLines(Path, Charset)`
- `java.nio.file.Files.readString(Path)`
- `java.nio.file.Files.newBufferedReader(Path, Charset)`

Implementation rules:

- If class/method missing, log skipped and continue.
- Only redact when the `Path.toString()` is a sensitive maps path.
- For `readAllLines`, filter returned `List<String>` and return a new `ArrayList`.
- For `readString`, return filtered string.
- For `newBufferedReader`, mark returned reader if path is sensitive.

Verify on A13:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest :xposed:compileDebugKotlin --no-daemon
```

Verify on A16 later through verifier JSON.

---

## Phase 4 - Verifier Maps And Android 16 Probes

**Why:** DevCheck is a black-box smoke app. The verifier gives controlled evidence.

**Files:**

- Create: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/ProcMapsProbe.kt`
- Create: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/CrashProbe.kt`
- Create: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/PackageVisibilityProbe.kt`
- Modify: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/VerifierActivity.kt`

- [x] **Step 4.1: Add Java maps probes**

Create `ProcMapsProbe.kt`:

```kotlin
package com.astrixforge.devicemasker.verifier

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.RandomAccessFile
import org.json.JSONObject

internal object ProcMapsProbe {
    private val suspiciousPatterns =
        listOf("libxposed", "liblspd", "lsposed", "lspd", "devicemasker")

    fun capture(): JSONObject =
        JSONObject()
            .put("javaBufferedReader", scanLines { BufferedReader(FileReader("/proc/self/maps")).use { it.readLines() } })
            .put("javaFileInputStream", scanText { FileInputStream("/proc/self/maps").use { String(it.readBytes()) } })
            .put("javaRandomAccessFile", scanLines { readRandomAccessFileLines() })

    private fun readRandomAccessFileLines(): List<String> =
        RandomAccessFile(File("/proc/self/maps"), "r").use { raf ->
            generateSequence { raf.readLine() }.toList()
        }

    private fun scanLines(reader: () -> List<String>): JSONObject {
        val lines = runCatching(reader).getOrElse { error ->
            return JSONObject().put("error", "${error.javaClass.simpleName}:${error.message.orEmpty()}")
        }
        return JSONObject()
            .put("lineCount", lines.size)
            .put("suspiciousLineCount", lines.count(::isSuspicious))
    }

    private fun scanText(reader: () -> String): JSONObject {
        val text = runCatching(reader).getOrElse { error ->
            return JSONObject().put("error", "${error.javaClass.simpleName}:${error.message.orEmpty()}")
        }
        val lines = text.lines()
        return JSONObject()
            .put("byteCount", text.toByteArray().size)
            .put("lineCount", lines.size)
            .put("suspiciousLineCount", lines.count(::isSuspicious))
    }

    private fun isSuspicious(line: String): Boolean =
        suspiciousPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }
}
```

Modify `VerifierEvidenceCollector.capture(...)`:

```kotlin
.put("procMaps", ProcMapsProbe.capture())
```

Verify:

```powershell
.\gradlew.bat :verifier:assembleDebug --no-daemon
```

- [x] **Step 4.2: Add crash/environment probe**

Create `CrashProbe.kt`:

```kotlin
package com.astrixforge.devicemasker.verifier

import android.os.Build
import org.json.JSONObject

internal object CrashProbe {
    fun capture(): JSONObject =
        JSONObject()
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("previewSdkInt", Build.VERSION.PREVIEW_SDK_INT)
            .put("codename", Build.VERSION.CODENAME)
            .put("supportedAbis", org.json.JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("vmVersion", System.getProperty("java.vm.version"))
            .put("vmName", System.getProperty("java.vm.name"))
}
```

Modify `VerifierEvidenceCollector.capture(...)`:

```kotlin
.put("runtime", CrashProbe.capture())
```

Verify:

```powershell
.\gradlew.bat :verifier:assembleDebug --no-daemon
```

- [x] **Step 4.3: Add package visibility probe**

Create `PackageVisibilityProbe.kt`:

```kotlin
package com.astrixforge.devicemasker.verifier

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

internal object PackageVisibilityProbe {
    private val packagesToCheck =
        listOf(
            "com.astrixforge.devicemasker",
            "org.lsposed.manager",
            "com.topjohnwu.magisk",
            "flar2.devcheck",
        )

    fun capture(context: Context): JSONObject {
        val manager = context.packageManager
        val results = JSONArray()
        packagesToCheck.forEach { packageName ->
            results.put(
                JSONObject()
                    .put("packageName", packageName)
                    .put("getPackageInfoVisible", isPackageInfoVisible(manager, packageName))
                    .put("getApplicationInfoVisible", isApplicationInfoVisible(manager, packageName)),
            )
        }
        return JSONObject().put("packages", results)
    }

    private fun isPackageInfoVisible(manager: PackageManager, packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(packageName, 0)
            }
        }.isSuccess

    private fun isApplicationInfoVisible(manager: PackageManager, packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getApplicationInfo(packageName, 0)
            }
        }.isSuccess
}
```

Modify `VerifierEvidenceCollector.capture(...)`:

```kotlin
.put("packageVisibility", PackageVisibilityProbe.capture(context))
```

Verify:

```powershell
.\gradlew.bat :verifier:assembleDebug --no-daemon
```

- [x] **Step 4.4: Runtime verify verifier on A13**

Run:

```powershell
$device = "emulator-5554"
adb -s $device install -r verifier\build\outputs\apk\debug\verifier-debug.apk
adb -s $device shell am force-stop com.astrixforge.devicemasker.verifier
adb -s $device shell am start -n com.astrixforge.devicemasker.verifier/.VerifierActivity
Start-Sleep -Seconds 3
adb -s $device shell run-as com.astrixforge.devicemasker.verifier cat files/verifier/latest.json > logs/device/2026-05-09-verifier-a13-maps-baseline.json
```

Expected JSON contains:

```json
{
  "procMaps": {
    "javaBufferedReader": {
      "lineCount": 1,
      "suspiciousLineCount": 0
    }
  },
  "packageVisibility": {
    "packages": []
  },
  "runtime": {
    "sdkInt": 33
  }
}
```

Exact line counts vary. The structure must exist.

---

## Phase 5 - Android 16 Non-SDK And ART Safety Guards

**Why:** Android 16 ART/non-SDK changes make reflection and internal-runtime assumptions risky. This project must avoid adding more private-API sludge while still being a hook module.

**Files:**

- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/Android16CompatibilitySafetyTest.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/LibxposedApiUsageSafetyTest.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/ReleaseBuildSafetyTest.kt`

- [x] **Step 5.1: Add static guard against known risky dependencies**

Create test:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Android16CompatibilitySafetyTest {
    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `project does not add hidden api bypass libraries`() {
        val files = listOf("gradle/libs.versions.toml", "app/build.gradle.kts", "xposed/build.gradle.kts")
            .map { File(repoRoot, it).readText() }
            .joinToString("\n")

        assertFalse(files.contains("HiddenApiBypass", ignoreCase = true))
        assertFalse(files.contains("org.lsposed.hiddenapibypass", ignoreCase = true))
    }

    @Test
    fun `runtime hookers keep libxposed framework errors unhandled by generic catches`() {
        val xposedSources = File(repoRoot, "xposed/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(xposedSources.contains("catch (e: XposedFrameworkError)"))
        assertTrue(xposedSources.contains("throw e"))
    }
}
```

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.Android16CompatibilitySafetyTest --no-daemon
```

- [x] **Step 5.2: Add libxposed API usage guard**

Create test:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibxposedApiUsageSafetyTest {
    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `libxposed api stays compile only`() {
        val buildFile = File(repoRoot, "xposed/build.gradle.kts").readText()

        assertTrue(buildFile.contains("compileOnly"))
        assertFalse(Regex("""implementation\s*\([^)]*libxposed[^)]*api""").containsMatchIn(buildFile))
    }

    @Test
    fun `module metadata targets libxposed api 101`() {
        val metadataDir = File(repoRoot, "xposed/src/main/resources/META-INF/xposed")
        val moduleProp = File(metadataDir, "module.prop").readText()
        val javaInit = File(metadataDir, "java_init.list").readText()

        assertTrue(moduleProp.contains("minApiVersion=101"))
        assertTrue(moduleProp.contains("targetApiVersion=101"))
        assertTrue(javaInit.contains("com.astrixforge.devicemasker.xposed.XposedEntry"))
    }

    @Test
    fun `legacy xposed bridge api stays absent`() {
        val sources = File(repoRoot, "xposed/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        listOf("XposedBridge", "XC_MethodHook", "beforeHookedMethod", "afterHookedMethod").forEach { forbidden ->
            assertFalse("Forbidden legacy API found: $forbidden", sources.contains(forbidden))
        }
    }

    @Test
    fun `runtime hookers do not use direct intercept lambdas`() {
        val hookerSources = File(repoRoot, "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "StableHooker.kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(Regex("""\.intercept\s*\{""").containsMatchIn(hookerSources))
    }
}
```

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.LibxposedApiUsageSafetyTest --no-daemon
```

- [x] **Step 5.3: Add hidden API log capture to A16 script**

Update `scripts/collect-a16-crash-evidence.ps1` to extract:

```powershell
Select-String "$prefix-logcat.txt" -Pattern "Accessing hidden|non-SDK|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|VerifyError" |
    Set-Content "$prefix-hidden-api-signatures.txt"
```

Verify:

```powershell
Select-String scripts\collect-a16-crash-evidence.ps1 -Pattern "Accessing hidden|NoSuchMethodError"
```

Expected: hidden API signature extraction exists.

---

## Phase 5B - libxposed Helper Discovery Evaluation

**Why:** `libxposed/helper` can reduce fragile manual reflection, but adopting it broadly before proof would be random churn. Evaluate one small hooker first and keep Device Masker's R8-safe callback path.

**Files:**

- Create: `docs/internal/reports/closed/research/2026-05-09/2026-05-09-libxposed-helper-evaluation-report.md`
- Modify only if dependency resolution and tests pass: `gradle/libs.versions.toml`
- Modify only if dependency resolution and tests pass: `xposed/build.gradle.kts`
- Modify only for the experiment: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHooker.kt`
- Test: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/LibxposedApiUsageSafetyTest.kt`

- [x] **Step 5B.1: Record helper adoption criteria before touching code**

Create report:

```markdown
# libxposed Helper Evaluation Report

Date started: 2026-05-09
Status: active

## Decision Rule

Adopt `libxposed/helper` only if all of these pass:

1. The artifact resolves from the configured repositories without adding a custom repository.
2. The dependency does not package `io.github.libxposed:api` into the APK.
3. One small hooker becomes simpler without changing runtime behavior.
4. Hook registration still uses `intercept(stableHooker { ... })`.
5. R8 release smoke still passes.

If any item fails, do not adopt the helper in production. Keep project-owned reflection plus static guards.

## Candidate Hooker

`SystemFeatureHooker` is the only candidate for this experiment because it has a small PackageManager method-discovery surface and useful Android/OEM variance.

## Result

Record: not evaluated yet.
```

Verify:

```powershell
Select-String docs\internal\reports\active\LIBXPOSED_HELPER_EVALUATION_REPORT.md -Pattern "SystemFeatureHooker|stableHooker|R8"
```

- [x] **Step 5B.2: Check dependency resolution without committing to adoption**

Use the version already proven by Maven/official docs during implementation. Do not guess a version. If no current artifact version is verified, skip to Step 5B.5 and record "not adopted".

Temporary local check only:

```powershell
.\gradlew.bat :xposed:dependencies --configuration debugRuntimeClasspath --no-daemon *> logs/build/2026-05-09-libxposed-helper-dependencies.txt
Select-String logs/build/2026-05-09-libxposed-helper-dependencies.txt -Pattern "io.github.libxposed:helper"
```

Expected:

- If the artifact appears and `io.github.libxposed:api` is not packaged as an implementation dependency, continue.
- If dependency resolution fails or pulls the API incorrectly, remove the temporary dependency edits and record "not adopted".

- [x] **Step 5B.3: If adopted, use helper only for discovery**

Keep the hook registration shape exactly like this:

```kotlin
method?.let { resolvedMethod ->
    xi.hook(resolvedMethod).intercept(
        stableHooker { chain ->
            val original = chain.proceed()
            // Existing SystemFeatureHooker policy decides whether to return original or spoofed value.
            original
        },
    )
    xi.deoptimize(resolvedMethod)
}
```

Hard rule:

```text
Do not use helper callback DSLs that register direct Kotlin SAM callbacks.
Do not migrate any second hooker in this phase.
```

Verify:

```powershell
rg -n "\.intercept\s*\{" xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.LibxposedApiUsageSafetyTest --no-daemon
```

Expected: no direct runtime `.intercept {` callbacks and static guard passes.

- [x] **Step 5B.4: Verify the experiment under release/R8**

Run:

```powershell
.\gradlew.bat spotlessCheck :xposed:testDebugUnitTest :app:assembleRelease :app:assembleCiRelease --no-daemon *> logs/build/2026-05-09-libxposed-helper-r8-gate.txt
Select-String logs/build/2026-05-09-libxposed-helper-r8-gate.txt -Pattern "BUILD SUCCESSFUL"
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5B.5: Close the helper decision**

Update `LIBXPOSED_HELPER_EVALUATION_REPORT.md` with one of these exact outcomes:

```markdown
## Result

Adopted for `SystemFeatureHooker` discovery only. Runtime registration still uses `stableHooker`; no second hooker was migrated.
```

or:

```markdown
## Result

Not adopted. The current project-owned reflection path remains because dependency resolution, packaging, or R8 safety did not meet the decision rule.
```

---

## Phase 6 - 16 KB Page-Size Verification

**Why:** Android 16 devices may use 16 KB pages. Google guidance says apps with native libraries must verify alignment. Device Masker has transitive native libraries even if first-party native code is absent.

**Files:**

- Create if missing: `scripts/verify-16kb-page-support.ps1`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/ReleaseBuildSafetyTest.kt`

- [x] **Step 6.1: Restore 16 KB verifier script if absent**

If `scripts/verify-16kb-page-support.ps1` does not exist, create a script that:

- Accepts APK path.
- Runs `zipalign -c -P 16 -v 4`.
- Extracts packaged `.so` files to `logs/tmp/16kb-check`.
- Uses `llvm-readelf` or `readelf` when available to inspect `LOAD` segment alignment.
- Fails if a packaged `.so` is clearly 4 KB aligned.
- Prints a warning, not a false pass, if readelf is unavailable.

Minimum command interface:

```powershell
param([Parameter(Mandatory=$true)][string]$ApkPath)
```

Verify:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
powershell -ExecutionPolicy Bypass -File scripts\verify-16kb-page-support.ps1 app\build\outputs\apk\debug\app-debug.apk
```

Expected: script exits `0` only when zip alignment passes and `.so` alignment is pass or explicitly unverifiable with a warning.

- [x] **Step 6.2: Add release safety reference**

In `ReleaseBuildSafetyTest`, assert the script exists:

```kotlin
assertTrue(File(repoRoot, "scripts/verify-16kb-page-support.ps1").isFile)
```

Verify:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ReleaseBuildSafetyTest --no-daemon
```

---

## Phase 6B - Native Hook Engine Decision Gate

**Why:** Native `/proc/self/maps` reads can bypass Java hooks, but native hooking can crash target apps harder than Java code. This phase decides whether native work is justified; it does not add native hooks by default.

**Files:**

- Create: `docs/internal/reports/active/research/2026-05-09/2026-05-09-native-hook-engine-decision-record.md`
- No production code changes in this phase unless verifier evidence proves Java filtering is insufficient and the user explicitly accepts native implementation risk.

- [x] **Step 6B.1: Record the native decision rule**

Create:

```markdown
# Native Hook Engine Decision Record

Date started: 2026-05-09
Status: active

## Decision Rule

Do not add a native hook engine unless all of these are true:

1. `:verifier` proves Java line, byte, RandomAccessFile, and NIO maps reads are redacted correctly.
2. A native probe or DevCheck evidence still shows LSPosed/module strings from `/proc/self/maps`.
3. The Android 16 crash path is understood well enough that adding native code is not hiding the real bug.
4. The implementation can stay per-app opt-in and default-off with a kill switch.
5. Debug, release, and `ciRelease` APKs pass 16 KB page-size verification after native dependencies are added.

## Engine Order

1. ByteHook first, because it is Android-focused PLT hooking and Maven/Prefab friendly.
2. ShadowHook only if ByteHook cannot cover the proven native path.
3. xHook and Dobby are reference-only for this project track.

## Current Result

Native hook engine not justified until verifier and DevCheck evidence prove Java coverage is insufficient.
```

Verify:

```powershell
Select-String docs\internal\reports\active\research\2026-05-09\2026-05-09-native-hook-engine-decision-record.md -Pattern "ByteHook|ShadowHook|default-off|16 KB"
```

- [x] **Step 6B.2: Compare verifier maps evidence before native adoption**

After Phase 4 and Phase 7 runs, copy the key evidence into the decision record:

```markdown
## Evidence Summary

| Source | Java line redacted | Java byte redacted | NIO redacted | Native read leaks suspicious lines | Decision |
| --- | --- | --- | --- | --- | --- |
| A13 verifier | record yes/no | record yes/no | record yes/no | record yes/no | record result |
| A16 verifier | record yes/no | record yes/no | record yes/no | record yes/no | record result |
| DevCheck | not applicable | not applicable | not applicable | record yes/no from evidence | record result |
```

Expected:

- If native leakage is not proven, keep native hooks out.
- If native leakage is proven and linked to DevCheck behavior, create a separate ByteHook implementation plan before coding.

---

## Phase 7 - A16 DevCheck Isolation Matrix

**Why:** This is where we turn the real device crash into a specific fix target.

**Current execution note:** updated 2026-05-10. Mobile MCP/ADB exposed `emulator-5554` as Pixel 10 Pro XL API 36.1 / Android 16 / SDK 36 with 16 KB pages. Debug and debug-key-signed ciRelease/R8 DevCheck smoke passed on that emulator with LSPosed hook registration and spoof-event evidence. The module-disabled, load-only, isolated hook-family, and physical-device rows below remain unchecked because they require explicit LSPosed/config state changes on the target environment. Do not rewrite the emulator pass as physical-device evidence.

**Files:**

- Modify only evidence report: `docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md`
- No code changes unless a specific failing hook family is identified.

- [ ] **Step 7.1: Run module-disabled control on A16**

On real A16 device:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\collect-a16-crash-evidence.ps1 -TargetPackage flar2.devcheck -OutputDir logs/device
```

Manual setup:

- Disable Device Masker module in LSPosed, or remove DevCheck from scope.
- Force stop DevCheck.
- Launch DevCheck.

Expected:

- If DevCheck crashes here, root cause is not Device Masker hook code.
- If DevCheck does not crash, continue.

Update report row 1 with evidence files.

- [ ] **Step 7.2: Run load-only control**

Manual setup:

- Enable Device Masker module.
- Keep DevCheck scoped in LSPosed.
- Disable DevCheck app config in Device Masker.

Run evidence script.

Expected:

- If crash happens here, suspect module injection, anti-detection startup, or LSPosed/framework interaction.
- If no crash, continue.

- [ ] **Step 7.3: Run hook-family matrix**

Use RemotePreferences/test config to set hook family keys for `flar2.devcheck`.

Run these combinations:

```text
anti_detect=true, all others=false
device=true, system=true, system_feature=true, all others=false
subscription=true, device=true, all others=false
network=true, all others=false
package_manager=true, anti_detect=true, all others=false
all safe hooks=true
```

For each run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\collect-a16-crash-evidence.ps1 -TargetPackage flar2.devcheck -OutputDir logs/device
```

Expected:

- First crashing combination identifies the fix area.
- If only `all safe hooks=true` crashes, suspect cross-hook coherence or ordering.

- [ ] **Step 7.4: Fix only the identified hook family**

Do not change unrelated hookers.

Examples:

- If `SystemHooker` crashes, add Android 16 method/field availability guards and original-value fallback.
- If `SystemFeatureHooker` crashes, narrow method signature matching and return original for unknown feature calls.
- If `DeviceHooker`/`SubscriptionHooker` crashes, split unsafe telephony methods and return original when missing permissions/OEM behavior differs.
- If `AntiDetectHooker` crashes, disable the exact detection sub-hook for DevCheck until a safer implementation exists.

Every fix must have:

```text
1. failing evidence log
2. focused code change
3. unit/static test
4. A16 rerun showing crash gone
5. A13 emulator rerun showing no regression
```

- [ ] **Step 7.5: Use Frida only if normal evidence is inconclusive**

Create `tools/frida/devcheck-proc-maps-trace.js` only when logcat, tombstones, and the hook-family matrix do not identify the crash path:

```javascript
Java.perform(function () {
  var FileInputStream = Java.use("java.io.FileInputStream");
  FileInputStream.$init.overload("java.lang.String").implementation = function (path) {
    if (path && path.indexOf("/proc/") === 0 && path.indexOf("/maps") !== -1) {
      console.log("FileInputStream maps open: " + path);
    }
    return this.$init(path);
  };

  var FileReader = Java.use("java.io.FileReader");
  FileReader.$init.overload("java.lang.String").implementation = function (path) {
    if (path && path.indexOf("/proc/") === 0 && path.indexOf("/maps") !== -1) {
      console.log("FileReader maps open: " + path);
    }
    return this.$init(path);
  };

  var Runtime = Java.use("java.lang.Runtime");
  Runtime.exit.implementation = function (code) {
    console.log("Runtime.exit called with code: " + code);
    return this.exit(code);
  };
});
```

Run manually on the real Android 16 lab device only:

```powershell
frida -U -f flar2.devcheck -l tools\frida\devcheck-proc-maps-trace.js --no-pause *> logs/device/2026-05-09-frida-devcheck-proc-maps.txt
```

Expected:

- Output is evidence only.
- Do not package Frida, Frida Gum, or this script into Device Masker.
- Copy findings into `docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md`.

- [ ] **Step 7.6: Use DexKit only as offline target analysis**

Do this only if Frida/logcat/tombstones still do not explain the crash and the target APK is available for local analysis.

Create a report section:

```markdown
## Optional Offline APK Analysis

DexKit was considered only as offline research tooling. It is not a `:xposed` runtime dependency.

| Target APK | Evidence searched | Result |
| --- | --- | --- |
| DevCheck | `/proc/self/maps`, PackageManager queries, Build reads, Telephony reads, Runtime.exit/System.exit | record result |
```

Rules:

```text
Do not add DexKit to :xposed.
Do not copy target app code.
Do not adopt any dependency until license impact is documented.
Use findings only to choose which Device Masker hook family to test next.
```

---

## Phase 8 - Runtime Verification Gates

**Why:** Completion requires evidence, not confidence.

**Current execution note:** local Gradle/static/Android 13 verifier gates passed. Local `release` and `ciRelease` APKs are unsigned because signing env vars are absent, so release/R8 install smoke remains pending until a signed release APK is available.

- [x] **Step 8.1: Full local gate**

Run:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest :verifier:assembleDebug lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon *> logs/build/2026-05-09-a16-proc-maps-final-gate.txt
Select-String logs/build/2026-05-09-a16-proc-maps-final-gate.txt -Pattern "BUILD SUCCESSFUL"
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 8.2: Static hook safety gate**

Run:

```powershell
rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin
rg -n "HiddenApiBypass|org\.lsposed\.hiddenapibypass|Timber\.|Random\(" xposed/src/main/kotlin app/build.gradle.kts xposed/build.gradle.kts gradle/libs.versions.toml
rg -n "DexKit|frida|bytehook|shadowhook|xhook|Dobby" app common xposed gradle
rg -n "java_proc_maps|hook_family_enabled|persona_blob_|persona_version_" common app xposed
```

Expected:

- No direct `.intercept {` runtime hookers.
- No HiddenApiBypass dependency.
- No DexKit, Frida, ByteHook, ShadowHook, xHook, or Dobby production dependency unless a later explicit native implementation plan changed this with evidence.
- No Timber or random generation in `:xposed`.
- New keys flow through `SharedPrefsKeys`, ConfigSync, and xposed readers.

- [x] **Step 8.3: libxposed static guard gate**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.LibxposedApiUsageSafetyTest --tests com.astrixforge.devicemasker.xposed.hooker.Android16CompatibilitySafetyTest --no-daemon
```

Expected: tests pass.

- [x] **Step 8.4: A13 verifier maps runtime**

Run:

```powershell
$device = "emulator-5554"
adb -s $device install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $device install -r verifier\build\outputs\apk\debug\verifier-debug.apk
adb -s $device logcat -c
adb -s $device shell am force-stop com.astrixforge.devicemasker.verifier
adb -s $device shell am start -n com.astrixforge.devicemasker.verifier/.VerifierActivity
Start-Sleep -Seconds 3
adb -s $device shell run-as com.astrixforge.devicemasker.verifier cat files/verifier/latest.json > logs/device/2026-05-09-verifier-a13-final.json
adb -s $device logcat -d -v threadtime > logs/device/2026-05-09-verifier-a13-final-logcat.txt
```

Expected:

- Verifier JSON includes `procMaps`.
- Logcat has no `FATAL EXCEPTION`, `AbstractMethodError`, `VerifyError`, or `NoSuchMethodError`.

- [ ] **Step 8.5: A16 DevCheck runtime**

2026-05-10 partial completion: Android 16 emulator DevCheck runtime passed for all currently enabled safe hooks. Evidence:

- Debug APK: `logs/device/2026-05-10-213712-a16-flar2.devcheck-*`
- ciRelease/R8 APK signed with debug key for local smoke: `logs/device/2026-05-10-214023-a16-flar2.devcheck-*`
- Device facts: SDK 36, Android 16, page size 16384

Still open: physical-device A16 run and the explicit module-disabled/load-only/family-isolated matrix.

On real A16:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\collect-a16-crash-evidence.ps1 -TargetPackage flar2.devcheck -OutputDir logs/device
```

Expected:

- DevCheck no longer crashes for the enabled hook policy being claimed.
- If DevCheck still crashes, report must name the remaining crash class and this plan is not complete.

- [ ] **Step 8.6: Release/R8 runtime**

2026-05-10 partial completion: local `ciRelease` was assembled, signed into `logs/tmp/app-ciRelease-debugkey-signed.apk` only for emulator smoke, installed on Android 16 emulator, and DevCheck stayed alive with LSPosed hook/spoof evidence and no checked release-only ABI crash signatures. Still open: production-signed release/ciRelease runtime and physical-device A16 repeat.

Run:

```powershell
.\gradlew.bat :app:assembleCiRelease --no-daemon
Get-ChildItem app\build\outputs\apk\ciRelease -Filter *.apk -Recurse
```

Install the ciRelease APK on A13 emulator and real A16, then repeat verifier and DevCheck smoke.

Expected:

- Same behavior as debug.
- No `AbstractMethodError`.
- No new release-only crash signatures.

---

## Phase 9 - Documentation And Memory Bank

**Why:** This project depends on Memory Bank and reports staying honest.

**Files:**

- Modify: `docs/internal/reports/active/validation/2026-05-09/2026-05-09-android-16-compatibility-devcheck-crash-report.md`
- Modify: `docs/internal/reports/active/research/2026-05-09/2026-05-09-native-proc-self-maps-java-first-research-report.md`
- Modify: `docs/public/ARCHITECTURE.md`
- Modify: `xposed/AGENTS.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Modify: `memory-bank/systemPatterns.md`
- Modify: `memory-bank/techContext.md`

- [x] **Step 9.1: Update reports with actual result**

Update the A16 report with:

- Device model/build.
- Android build and SDK.
- Page size.
- LSPosed version if visible.
- App build variant and commit.
- Hook policy matrix.
- Exact crash/no-crash result.
- Evidence file paths.
- Final root cause.

Update maps report with:

- What was implemented.
- What stayed out of scope.
- Whether native reads still reveal suspicious lines.
- Whether Java line/byte/NIO paths pass.

- [x] **Step 9.2: Update public architecture only for real behavior**

Add a compact section:

```markdown
### Android 16 Compatibility

Android 16 support is validated separately from Android 13 emulator smoke. Hook families can be isolated per target for crash triage. Java `/proc/self/maps` redaction is path-aware; native scanner coverage is not claimed unless native evidence proves it.
```

- [x] **Step 9.3: Update Memory Bank**

Read every file first:

```powershell
Get-Content -Raw memory-bank/projectbrief.md
Get-Content -Raw memory-bank/productContext.md
Get-Content -Raw memory-bank/systemPatterns.md
Get-Content -Raw memory-bank/techContext.md
Get-Content -Raw memory-bank/activeContext.md
Get-Content -Raw memory-bank/progress.md
```

Update:

- `activeContext.md`: Android 16 status, DevCheck result, maps hardening state.
- `progress.md`: what works and what remains open.
- `systemPatterns.md`: hook-family policy and maps redaction rules.
- `techContext.md`: any new scripts and verifier probes.

No Memory Bank claim can say A16 is fixed unless A16 evidence proves it.

---

## Edge Cases Checklist

Implementation is not complete unless these are considered:

- [ ] Module disabled returns originals.
- [ ] App disabled returns originals.
- [ ] Missing config returns originals.
- [ ] Malformed config returns originals.
- [ ] Unknown OEM methods are skipped, not fatal.
- [x] `XposedFrameworkError` and `HookFailedError` are rethrown.
- [x] Hook registration uses `stableHooker`.
- [x] No direct `.intercept { ... }` callbacks return.
- [x] `libxposed/helper`, if adopted, is used only for discovery/matching and never for direct callback registration.
- [x] libxposed API dependency remains `compileOnly`.
- [x] Legacy XposedBridge APIs remain absent.
- [x] Proc maps redaction does not touch non-maps files.
- [x] Proc maps hidden lines are skipped, not replaced with blank strings.
- [x] Byte reads preserve EOF behavior.
- [x] Byte reads with `len == 0` return `0`.
- [x] Very large maps files pass through safely instead of OOMing.
- [x] Java NIO hooks skip safely when classes/methods are absent.
- [x] Native maps read remains documented as unsupported unless native hook is explicitly implemented.
- [x] ByteHook is not added until native leakage is proven and recorded.
- [x] ShadowHook remains fallback-only; xHook and Dobby remain reference-only.
- [x] Frida and DexKit outputs are lab evidence only and never packaged into production APKs.
- [x] Package visibility probes cover API 33+ flag overloads.
- [x] A13 emulator still passes after A16 fixes.
- [ ] A16 real device gets its own evidence.
- [ ] DevCheck is tested with module disabled, load-only, isolated hook families, and final policy.
- [ ] Release/R8 build repeats the relevant runtime checks.
- [x] 16 KB script exists and runs against debug/release APKs.

---

## Completion Criteria

This plan is complete only when:

```text
1. A16 DevCheck crash is reproduced or proven absent with captured evidence.
2. The crashing hook family is identified, or the report proves crash is outside Device Masker.
3. Java-first proc maps hardening is path-aware and verifier-backed.
4. A13 emulator and real A16 device both pass the claimed runtime matrix.
5. Full Gradle gate passes.
6. libxposed API static guards pass and no legacy/direct callback patterns return.
7. Native hook engine decision is recorded before any native hook dependency is added.
8. Reports, architecture docs, and Memory Bank reflect the actual evidence.
```

Do not commit during execution unless the user explicitly asks.
