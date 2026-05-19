# Device Masker Combined Research Audit

Date: 2026-05-09
Status: Active review report
Scope: Review and verify these two AI research reports against current code, official docs, and project architecture:

- `docs/internal/reports/closed/research/2026-05-06/2026-05-06-device-masker-optimization-hardening-research.md`
- `docs/internal/reports/closed/audits/2026-05-09/2026-05-09-identifier-generators-libxposed-hooks-deep-audit-improvement-plan.md`

## Executive Verdict

The two research reports contain useful direction, but they cannot be implemented as-is.

The first report, `DeviceMasker Optimization and Hardening Research.md`, is too broad and mixes current issues with speculative 2025-2026 anti-fraud claims. Several of its "critical" findings are stale or inaccurate in the current repo. Its strongest value is strategic: coherent identity, explicit source/provenance for profile data, native detection limits, and Android 16 posture awareness. Its weakest parts are overconfident undetectability language, unsupported TAC/OUI assertions, and risky recommendations such as native syscall redaction and system_server package hiding as default work.

The second report, `DeviceMasker Identifier Generators and libxposed Hooks – Deep Audit and Improvement Plan.md`, is much closer to the codebase and mostly aligned with the project's architecture. However, it is also partly stale: WebView `chain.args` mutation, `XposedFrameworkError` swallowing, `ConfigSync.syncApp` ignoring `AppConfig.isEnabled`, `RemotePreferences.apply`, `Country` serialization, and broad TimeZone/Locale instance hooks are already fixed or not true in the current code.

The current repo is in much better shape than both reports imply. There are still real, high-value follow-up items:

1. The `DevicePersona` model exists and tests pass, but the persona blob is not published to RemotePreferences or consumed by xposed hooks.
2. Persona deterministic ICCID generation does not append a Luhn check digit, while the standalone `ICCIDGenerator` does.
3. `DeviceProfilePreset` documents fields as if they are runtime-spoofed, but `SystemHooker` only applies a smaller subset.
4. `SystemHooker`, `SensorHooker`, and `WebViewHooker` snapshot `DEVICE_PROFILE` at hook registration, so profile changes require target restart even though many flat values are live.
5. Package hiding is app-process `ApplicationPackageManager` hiding, not system_server `PackageManagerService` hiding.
6. `/proc/self/maps` filtering is Java `BufferedReader.readLine()` filtering, not native syscall filtering.
7. TAC/OUI/profile data needs provenance discipline. Do not claim "official GSMA" unless the exact source is tracked.

## Official Source Checks

The review used current official or primary references:

- libxposed `Chain.getArgs()` returns an immutable list; argument changes must use `proceed(Object[])` or `proceedWith(...)`.
  Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Chain.html
- libxposed `Hooker.intercept(Chain)` is the callback contract.
  Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Hooker.html
- libxposed `HookFailedError` extends `XposedFrameworkError` and `Error`; module developers should not catch it for fallback.
  Source: https://libxposed.github.io/api/io/github/libxposed/api/error/HookFailedError.html
- Android identifier guidance restricts non-resettable IDs such as IMEI, serial, and ICCID and recommends avoiding hardware identifiers where possible.
  Source: https://developer.android.com/identity/user-data-ids
- Android `Build.FINGERPRINT` uniquely identifies a build and should not be parsed by apps.
  Source: https://developer.android.com/reference/android/os/Build
- Android `Build.VERSION.SECURITY_PATCH` is the user-visible patch date.
  Source: https://developer.android.com/reference/android/os/Build.VERSION
- Android Advanced Protection Manager is real in API 36 and requires `QUERY_ADVANCED_PROTECTION_MODE`.
  Source: https://developer.android.com/reference/kotlin/android/security/advancedprotection/AdvancedProtectionManager
- Android Identity Check is real in API 36, but it is a biometric authentication policy bit, not a device identity field.
  Source: https://developer.android.com/reference/android/hardware/biometrics/BiometricManager.Authenticators#IDENTITY_CHECK

## Current Architecture Confirmed

The current project architecture is correct in its main boundaries:

- `:app` owns UI, config persistence, config sync, diagnostics, and app-side RemotePreferences writes.
- `:common` owns models, config contracts, shared keys, generators, and persona data structures.
- `:xposed` owns target-process hooks and must not generate random fallback identity values.
- Config delivery is RemotePreferences-first. No custom Binder/AIDL config path should be reintroduced.
- `JsonConfig.appConfigs` is canonical for per-app scope.
- `SharedPrefsKeys` is the only proper source for RemotePreferences key strings.
- Xposed hook callbacks use `stableHooker` or named hooker implementations, not direct Kotlin SAM `.intercept { ... }` runtime callbacks.
- `XposedFrameworkError` is rethrown before generic `Throwable` handling in the major hook registration paths.
- R8 release minification is currently expected to stay enabled and guarded by tests.

## Accuracy Matrix

| Research Claim | Current Status | Evidence | Reviewer Decision |
| --- | --- | --- | --- |
| `ConfigManager` uses unsafe read-modify-write and unguarded file writes | Stale/fixed | `ConfigManager.updateConfig` uses `_config.update(transform)`; saves use `saveMutex.withLock`; writes use `AtomicFile` | Do not repeat this work. Keep tests around concurrent config updates if added later. |
| `PersistentAppLogTree` does synchronous file I/O on log call | Stale/fixed | `PersistentAppLogTree.log()` calls `store.appendEvent`; `AppLogStore` writes through a channel on an IO dispatcher | No critical issue now. The only read path flushes pending writes intentionally. |
| `LogManager` file I/O executes on caller thread | Stale/fixed | `LogManager` uses `withContext(Dispatchers.IO)` for file operations | No action from the research claim. |
| `SpoofRepository` caches are not thread-safe | Stale/fixed | `cachedSIMConfig`, `cachedLocationConfig`, and `cachedDeviceHardwareConfig` are `AtomicReference` | No action unless a new cache is added. |
| `SpoofRepository` leaks Activity context | False/currently mitigated | Singleton is created with `context.applicationContext` | Keep using application context. |
| `DeviceMaskerApp` lazy getters race before init | Mostly stale/fixed | App stores nullable `appLogStoreInstance` and fails explicitly if accessed before `onCreate` | Acceptable app singleton behavior; no current crash evidence. |
| Exported `XposedProvider` is a critical vulnerability | Misframed | Manifest says it is required for libxposed service/RemotePreferences; provider is the official bridge shape | Do not add custom permission unless libxposed docs/support confirm it will not break LSPosed. |
| Release minification is disabled | Stale/fixed | `ReleaseBuildSafetyTest`, ProGuard rules, and R8 hook ABI tests exist | Keep R8 on; keep ABI guard tests. |
| Direct Kotlin SAM hook callbacks are risky under R8 | Correct principle, already addressed | `stableHooker` implements `XposedInterface.Hooker`; tests block direct runtime `.intercept {}` | Keep this as a permanent rule. |
| WebView mutates immutable `chain.args` | Stale/fixed | `WebViewHooker` copies `chain.args.toTypedArray()` and calls `chain.proceed(args)` | No current bug. Static safety test is correct. |
| `XposedFrameworkError` / `HookFailedError` is swallowed | Stale/fixed in key paths | `XposedEntry`, `BaseSpoofHooker`, `AntiDetectHooker`, and `DeoptimizeManager` catch/rethrow `XposedFrameworkError` first | Continue enforcing this. |
| `ConfigSync.syncApp` ignores `AppConfig.isEnabled` | Stale/fixed | `syncStateFor()` uses `isModuleEnabled && configApp?.isEnabled == true && group.isEnabled` | No action. |
| Critical RemotePreferences writes use `apply` | Stale/fixed | `ConfigSync` full/app/clear paths use `commit()` and warn on failure | No action. |
| `Country` is not serializable | Stale/fixed | `PersonaGeneratorTest` serializes/deserializes `Country`; model is serializable | No action except keep test. |
| TimeZone/Locale hooks spoof all instances | Stale/fixed | `LocationHooker` hooks `TimeZone.getDefault()` and `Locale.getDefault()` | Current behavior is narrower and safer. |
| No persona-level generator exists | Stale/partially fixed | `DevicePersona` and `PersonaGenerator` exist | Remaining issue is publication/consumption, not absence. |
| Luhn logic is inconsistent | Partly true | IMEI paths are tested; standalone ICCID appends Luhn; deterministic persona ICCID does not | Fix with centralized Luhn and ICCID tests. |
| Device profile fields should correlate with Build, radio, SIM, sensor, package features | Directionally correct | `DeviceProfilePreset` has many fields; current hook coverage is partial | Implement only verified surfaces with tests and device validation. |
| Native `/proc/self/maps` scanners bypass Java hooks | Correct limitation | Current AntiDetect hook filters `BufferedReader.readLine()` only | Document limitation. Native hook is high-risk and should be optional/experimental only. |
| Hook system_server `PackageManagerService` for package hiding | Aspirational/high risk | `onSystemServerStarting` only logs; current package hiding hooks `ApplicationPackageManager` in target process | Do not default to system_server hooking. Consider only with a scoped plan and boot-loop safety. |
| Android Advanced Protection and Identity Check matter | Real APIs, wrong implementation framing | Official docs confirm API 36 features | App-side read-only diagnostics may be useful; do not spoof biometric/security posture by default. |

## Confirmed Current Findings

### P1 - Persona Blob Contract Exists But Is Not Wired

`DevicePersona` is defined as a serialized coherent per-package snapshot. `SharedPrefsKeys` has `persona_blob_*` and `persona_version_*` keys. Tests confirm the keys. But current config sync only removes those keys for deleted packages; it does not write a persona blob or version. Current xposed hooks do not read or parse `DevicePersona`.

Evidence:

- `common/src/main/kotlin/com/astrixforge/devicemasker/common/DevicePersona.kt` defines `DevicePersona.toJsonString()` and `parseOrNull`.
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/PersonaGenerator.kt` builds a coherent persona.
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt` includes persona keys only in `keysForSyncedPackage`.
- `rg` found no writer for `SharedPrefsKeys.getPersonaBlobKey(packageName)` and no xposed-side `DevicePersona.parseOrNull`.

Impact:

Flat RemotePreferences values still work, so this is not a current hook-breaking bug. But the architecture now has two contracts: the documented persona contract and the real flat-key runtime contract. That drift will confuse future work and makes correlation improvements harder.

Recommendation:

Choose one of two simple paths:

1. Preferred: keep flat keys for compatibility, but also publish `DevicePersona` blob/version in `ConfigSync`, then let selected xposed hookers read persona where cross-field consistency matters.
2. Simpler short-term: explicitly document that `DevicePersona` is app/common only for now and remove any wording that claims it is the runtime hook contract.

### P1 - Deterministic Persona ICCID Does Not Use Luhn

Standalone `ICCIDGenerator.generate()` builds a base and appends a Luhn digit. Persona deterministic ICCID generation does not do this; it creates a `prefix + deterministicDigits(...)` string of fixed length.

Evidence:

- `common/src/main/kotlin/com/astrixforge/devicemasker/common/generators/ICCIDGenerator.kt` calculates and appends a Luhn check digit.
- `common/src/main/kotlin/com/astrixforge/devicemasker/common/PersonaIdentityValues.kt` `deterministicIccid()` returns `prefix + deterministicDigits(...)`.
- `PersonaGenerator.validate()` checks IMEI/TAC, MCC/MNC, SSID/BSSID, and tracking ID distinctness, but not ICCID Luhn validity.

Impact:

If persona-generated ICCID values reach hooks or UI-generated spoof values, a target app can run a simple check digit validation and flag the value as synthetic.

Recommendation:

Create a single `Luhn` helper in `:common`, use it from IMEI, ICCID, and persona deterministic generation, and add tests:

- generated IMEI validates through an independent check path
- generated ICCID validates through an independent check path
- deterministic persona ICCID validates
- known invalid examples fail

### P1 - DeviceProfilePreset Overpromises Runtime Coverage

`DeviceProfilePreset` contains `securityPatch`, `buildTime`, `buildId`, `incremental`, `supportedAbis`, `simCount`, `hasNfc`, and `has5G`. Its comments say these are used to spoof `Build.VERSION.SECURITY_PATCH`, `Build.TIME`, `Build.ID`, `Build.VERSION.INCREMENTAL`, `Build.SUPPORTED_ABIS`, `TelephonyManager.getSimCount()`, `SubscriptionManager.getActiveSubscriptionInfoCount()`, `PackageManager.hasSystemFeature(...)`, and network type.

Current xposed code does not implement most of that. `SystemHooker` mutates a subset of `Build` fields and hooks some `SystemProperties.get(...)` values. Search found no current hooks for `hasSystemFeature`, `getSimCount`, `getActiveSubscriptionInfoCount`, `Build.VERSION.SECURITY_PATCH`, `Build.TIME`, `Build.VERSION.INCREMENTAL`, or `Build.SUPPORTED_ABIS`.

Impact:

This creates a false sense of coverage. The data model says Device Masker can present a coherent profile, but target apps can still read unmodified fields and see mismatches.

Recommendation:

Before adding new broad hooks, create a "profile coverage matrix" test/doc that lists each preset field, whether it is generated, synced, hooked, and validated on-device. Then implement the highest-signal gaps one at a time:

1. `Build.ID`, `Build.VERSION.INCREMENTAL`, `Build.VERSION.SECURITY_PATCH`, and `Build.TIME` if safely hookable/mutable in target process.
2. `PackageManager.hasSystemFeature(...)` for stable feature constants such as NFC and telephony/5G signals.
3. SIM count and active subscription count only after defining a real SIM/persona model.

### P2 - Some Hooks Are Live, Some Require Target Restart

Many hooks read flat spoof values from RemotePreferences inside the callback, so they can see config changes without hook re-registration. But profile-based hookers snapshot the active `DEVICE_PROFILE` at registration:

- `SystemHooker` resolves `DeviceProfilePreset` once during `hook()`.
- `SensorHooker` resolves `DeviceProfilePreset` once during `hook()`.
- `WebViewHooker` resolves `DeviceProfilePreset` once during `hook()`.

Impact:

The app can honestly say many per-type values are live, but it should not imply every profile surface updates live. A user changing Device Profile may need force-stop/relaunch of the target app.

Recommendation:

Document this explicitly in diagnostics and release notes:

- Flat per-type values: live where hook callback reads prefs.
- Profile-derived fields: target restart required unless hooker is refactored to read current profile each call.

Do not refactor all profile hookers to live reads until there is a measured need. Static fields such as `Build.MODEL` are inherently early-read and restart-sensitive.

### P2 - SubscriptionManager List Hook Is Currently a No-Op

`SubscriptionHooker` hooks `SubscriptionManager.getActiveSubscriptionInfoList()` but returns `chain.proceed()` unchanged. The comment says list shape is preserved and individual `SubscriptionInfo` getters are spoofed later.

Impact:

This is not a crash bug. It is an intentional pass-through. But a no-op hook still adds complexity and can mislead future reviewers into thinking list-level SIM count/slot shaping exists.

Recommendation:

Either remove the hook or rename/comment it as an explicit deoptimization/list-preservation hook. If SIM count consistency becomes a goal, implement it as a separate plan with tests around slot count, subscription ID, carrier, IMSI, ICCID, and phone number.

### P2 - Package Hiding Is App-Process Only

Current `PackageManagerHooker` hides Device Masker from target app calls to `android.app.ApplicationPackageManager`. `AntiDetectPackageManagerHooks` hides LSPosed/Magisk-style packages similarly.

This is useful for common app-side checks, but it is not system_server package manager hiding. `XposedEntry.onSystemServerStarting()` currently logs only.

Impact:

Apps that query through normal app-process APIs may be covered. Sophisticated detection using system_server-side paths, native code, or binder-level behavior may still detect module packages.

Recommendation:

Treat system_server package hiding as an advanced hardening track, not as something to avoid. It must be behind a scoped implementation plan with boot-loop recovery, feature flags, and runtime evidence because failures here can affect the whole device. Current app-process hiding should remain the stable baseline while system_server hiding is designed and validated separately.

### P2 - `/proc/self/maps` Java Filtering Is Not Native Redaction

Current `AntiDetectHooker` filters `BufferedReader.readLine()` results containing known instrumentation library patterns. This can help Java/Kotlin code that reads proc maps through Java I/O.

It does not cover native code that calls `open/read/readlink` directly.

Impact:

The first report is correct that native scanners can bypass Java-level filtering. But a native syscall hook is not a small hardening task. It adds ABI, crash, compatibility, and detection risk.

Recommendation:

Keep Java filtering as the stable baseline and add native maps redaction as an advanced implementation track. This should be opt-in until validated because it touches native ABI and process stability. Start it only after:

- specific target detection evidence exists
- crash recovery and kill switch exist
- debug/release/native ABI builds are tested
- LSPosed logs and native crash tombstones are collected

## Advanced Workstreams To Add

These were previously framed as rejected or deferred because they are high-risk. They should not be removed from the roadmap. They should be added as advanced tracks with strict implementation gates, runtime kill switches, and documented validation evidence.

### Native Proc Maps Redaction

Native `/proc/self/maps` redaction is technically useful and should be added as an advanced hardening track. The current Java `BufferedReader.readLine()` filtering only helps Java-level readers; native anti-tamper code can bypass it through direct syscalls.

Implementation direction:

- Add a small native layer only for scoped target processes.
- Detect reads of `/proc/self/maps`.
- Redact lines containing module/libxposed/LSPosed native evidence.
- Keep the Java filtering as fallback.
- Add per-app opt-in and global emergency kill switch.
- Collect tombstones/logcat/LSPosed evidence during validation.

Validation gate:

- Debug and release APKs build for every supported ABI.
- Target apps launch without native crashes.
- Native scanner test proves redaction.
- Disabled mode proves original maps content is untouched.

### Android Advanced Protection And Identity Check Awareness

Advanced Protection and Identity Check are real Android API 36 surfaces. Add them to the roadmap, but do it in layers: diagnostics first, then optional spoof/hook behavior only if target evidence proves a need.

Reasons:

- Advanced Protection requires a specific permission to query.
- Identity Check is a biometric authentication requirement bit, not a hardware profile field.
- Hooking biometric/security posture APIs is high risk and can create worse user-visible failures than leaving them alone, so any hook must be separately gated.

Implementation direction:

- App-side diagnostic reads Advanced Protection state when API/permission allow it.
- Add support-bundle fields for API level, permission availability, and state.
- Add a policy layer that can disable risky hooks when Advanced Protection is enabled.
- Only later consider hook/spoof behavior for specific APIs with target evidence.

Validation gate:

- API < 36 pass-through behavior is clean.
- API 36+ no-permission behavior does not crash.
- Permission-granted diagnostics are recorded correctly.
- Any hook behavior has disabled/missing/malformed pass-through tests.

### DexParser-Based Selective Deoptimization

The first report recommends using DexParser to selectively deoptimize methods. Add this as a performance/stealth research track. Current broad per-hook `xi.deoptimize(m)` is simple and already validated, so selective deopt should be proven with measurements before replacing the stable path.

Implementation direction:

- Instrument current hook registration and hot callback timing.
- Identify which hooked methods really need deoptimization.
- Prototype selective deopt for one hooker first, likely `DeviceHooker` or `SystemHooker`.
- Compare hook reliability and timing against the broad-deopt baseline.
- Keep a fallback switch to broad deopt.

Validation gate:

- Same spoof coverage as broad deopt.
- No regression in Mantle/DevCheck release R8 smoke.
- Timing data shows benefit or stealth improvement.
- Failure mode falls back to current broad-deopt behavior.

### Source-Tracked TAC/OUI/Profile Database

The code currently contains TAC and OUI lists with comments implying authenticity. Some comments are too strong. GSMA TAC allocation data is authoritative, but not every TAC list in random public sources is reliable or legally complete.

Implementation direction:

For every profile data value, track provenance:

- source URL or source type
- date captured
- whether it is official, community-observed, or heuristic
- confidence level

If provenance is unknown, label it as heuristic. Do not claim "official GSMA" in comments without evidence.

Validation gate:

- Every preset has provenance metadata.
- Tests reject missing provenance for high-confidence presets.
- UI/docs distinguish official, observed, and heuristic profiles.
- Profile values that are not runtime-hooked are marked honestly in the coverage matrix.

### system_server Package Visibility Hardening

Current package hiding works in target app processes through `ApplicationPackageManager`. Add system_server hiding as an advanced track so Device Masker can cover deeper package visibility paths.

Implementation direction:

- Use `onSystemServerStarting` for system_server-only setup.
- Keep all hooks disabled by default until the user explicitly enables the hardening mode.
- Avoid affecting Device Masker UI and LSPosed management flows.
- Add boot-loop recovery guidance and a remote/LSPosed-safe kill switch.
- Start with package list/read-only visibility surfaces, not broad binder behavior changes.

Validation gate:

- Device boots after enabling hooks.
- Device boots after disabling hooks.
- Device Masker UI still opens.
- LSPosed module can still be disabled.
- App-process and system_server package queries both show expected hiding.
- Failure logs are captured in support bundle.

## Best Next Implementation Plan

The safest path is incremental and test-first.

### Phase 1 - Correctness Contract Cleanup

1. Add central `Luhn` helper in `:common`.
   Verify: IMEI, ICCID, and deterministic persona ICCID tests pass.

2. Fix deterministic ICCID generation to reserve one digit for Luhn.
   Verify: `PersonaGenerator.validate()` checks ICCID and tests fail on invalid examples.

3. Decide and implement persona publication.
   Verify: `ConfigSyncSnapshotTest` proves `persona_blob_*` and `persona_version_*` are written when an app is enabled, removed when disabled/deleted, and never written for disabled apps.

4. Add xposed-side persona reader only where it reduces real inconsistency.
   Verify: hooks still return originals for missing, blank, malformed, disabled, and unsupported persona data.

### Phase 2 - Profile Coverage Honesty

1. Create a profile coverage table in docs/tests.
   Verify: each `DeviceProfilePreset` field is marked as generated only, synced, hooked, or validated.

2. Remove or soften comments that claim unimplemented hooks.
   Verify: no comments say `simCount`, `hasNfc`, `has5G`, `supportedAbis`, or `securityPatch` are hooked unless code actually hooks them.

3. Add one profile surface at a time.
   Verify: unit/static tests plus LSPosed/logcat evidence for release APK.

### Phase 3 - Runtime Validation

1. Build a target-app smoke checklist for Mantle, DevCheck, and one real target app.
   Verify: actual spoofed values are observed, not just hook registration logs.

2. Capture disabled/missing/malformed config pass-through evidence.
   Verify: hooks return original values when config is unsafe.

3. Record profile restart semantics.
   Verify: changing Device Profile while target is running either updates only live surfaces or asks user to restart target.

### Phase 4 - Advanced Hardening Tracks

These are included roadmap items, not exclusions. They should start after the correctness/profile contract work because advanced hooks are harder to debug when the base identity contract is still inconsistent.

1. Add Android 16 Advanced Protection / Identity Check diagnostics.
   Verify: API/permission behavior is correct and no unsupported-device crashes occur.

2. Add native `/proc/self/maps` redaction behind opt-in hardening.
   Verify: native scanner evidence, disabled-mode pass-through, no tombstones, and release R8 target smoke.

3. Add system_server PackageManager hardening behind opt-in hardening.
   Verify: reboot safety, LSPosed disable recovery, app-process and system_server package visibility tests.

4. Add DexParser/selective deoptimization experiments.
   Verify: measured performance or stealth benefit without losing hook reliability.

5. Build a source-tracked TAC/OUI/profile database.
   Verify: every high-confidence profile has provenance metadata and the coverage matrix states what is actually hooked.

## Impactful Example Snippets

These are not complete patches. They show the intended shape of the highest-impact fixes without hiding the important constraints.

### Central Luhn Helper

One helper should own check digit generation and validation. IMEI, ICCID, and persona deterministic values should call the same code instead of carrying subtly different local implementations.

```kotlin
object Luhn {
    fun checkDigit(partial: String): Int {
        require(partial.all(Char::isDigit)) { "Luhn input must be decimal digits" }

        var sum = 0
        for ((indexFromRight, char) in partial.reversed().withIndex()) {
            var digit = char.digitToInt()
            if (indexFromRight % 2 == 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return (10 - (sum % 10)) % 10
    }

    fun appendCheckDigit(partial: String): String = partial + checkDigit(partial)

    fun isValid(value: String): Boolean =
        value.length > 1 &&
            value.all(Char::isDigit) &&
            checkDigit(value.dropLast(1)) == value.last().digitToInt()
}
```

### Deterministic Persona ICCID

Persona ICCID must reserve one digit for Luhn. The current shape generates a fixed-length string without appending a check digit.

```kotlin
internal fun deterministicIccid(rootSeed: String, label: String, carrier: Carrier): String {
    val prefix = "89${carrier.countryCode}${carrier.iccidIssuerCode}"
    val bodyLength = (ICCID_LENGTH - prefix.length - 1).coerceAtLeast(1)
    val partial = prefix + deterministicDigits(rootSeed, label, bodyLength)
    return Luhn.appendCheckDigit(partial)
}
```

### Persona Blob Publication

Flat keys can stay for compatibility, but the coherent persona blob should be written alongside them if the runtime contract is meant to exist.

```kotlin
private fun MutableMap<String, String>.putPersonaState(
    packageName: String,
    group: SpoofGroup,
) {
    val persona = PersonaGenerator.generate(group, packageName)
    put(SharedPrefsKeys.getPersonaBlobKey(packageName), persona.toJsonString())
}

private fun MutableMap<String, Long>.putPersonaVersion(
    packageName: String,
    group: SpoofGroup,
) {
    put(SharedPrefsKeys.getPersonaVersionKey(packageName), group.updatedAt)
}
```

Important: this must only run for enabled app/group state. Disabled, missing, or unsafe app state should remove the persona keys.

### Xposed Persona Reader With Pass-Through

Hookers should not assume persona parsing succeeds. Missing, blank, malformed, disabled, or version-mismatched persona data must return original values.

```kotlin
private fun SharedPreferences.getPersonaOrNull(packageName: String): DevicePersona? {
    val json = getString(SharedPrefsKeys.getPersonaBlobKey(packageName), null)
    return DevicePersona.parseOrNull(json)
}

private fun personaValueOrOriginal(
    prefs: SharedPreferences,
    packageName: String,
    type: SpoofType,
    original: Any?,
): Any? {
    val persona = prefs.getPersonaOrNull(packageName) ?: return original
    return persona.getValue(type)?.takeIf { it.isNotBlank() } ?: original
}
```

### Live-Vs-Restart Profile Reads

For hot values, read prefs inside the hook callback. For early/static fields such as `Build.MODEL`, expect target restart and document it.

```kotlin
xi.hook(method)
    .intercept(
        stableHooker { chain ->
            val original = chain.proceed()
            val currentProfileId =
                getConfiguredSpoofValue(prefs, pkg, SpoofType.DEVICE_PROFILE)
                    ?: return@stableHooker original
            val preset = DeviceProfilePreset.findById(currentProfileId)
                ?: return@stableHooker original

            preset.model.takeIf { it.isNotBlank() } ?: original
        }
    )
xi.deoptimize(method)
```

This pattern is useful for method returns. It does not solve already-read static `Build.*` fields.

### Profile Coverage Matrix Shape

Before adding more hooks, make coverage explicit so docs cannot overclaim what runtime code does.

```kotlin
data class ProfileSurfaceCoverage(
    val field: String,
    val generated: Boolean,
    val synced: Boolean,
    val hooked: Boolean,
    val restartRequired: Boolean,
    val validation: String,
)

val deviceProfileCoverage = listOf(
    ProfileSurfaceCoverage("Build.MODEL", generated = true, synced = true, hooked = true, restartRequired = true, validation = "Mantle/DevCheck"),
    ProfileSurfaceCoverage("Build.VERSION.SECURITY_PATCH", generated = true, synced = false, hooked = false, restartRequired = true, validation = "missing"),
    ProfileSurfaceCoverage("PackageManager.hasSystemFeature(NFC)", generated = true, synced = false, hooked = false, restartRequired = false, validation = "missing"),
)
```

### Advanced Hardening Kill Switch

Native maps redaction, system_server package hiding, class lookup hiding, and security posture hooks should all have explicit opt-ins and emergency disable paths.

```kotlin
data class HardeningPolicy(
    val nativeMapsRedaction: Boolean,
    val systemServerPackageHiding: Boolean,
    val classLookupHiding: Boolean,
    val securityPostureHooks: Boolean,
)

fun HardeningPolicy.canRunSystemServerHooks(): Boolean =
    systemServerPackageHiding && !nativeMapsRedaction // example: avoid stacking risky tracks first
```

This is intentionally conservative in rollout, not conservative in ambition. Add one high-risk track, validate it, then stack the next.

### system_server Hook Shape

The safe shape is: system_server lifecycle only, feature flag first, `XposedFrameworkError` rethrow, ordinary OEM mismatch logged and skipped.

```kotlin
override fun onSystemServerStarting(param: SystemServerStartingParam) {
    val prefs = getRemotePreferences(PREFS_GROUP)
    if (!prefs.getBoolean(SharedPrefsKeys.KEY_SYSTEM_SERVER_HARDENING_ENABLED, false)) return

    try {
        SystemServerPackageHidingHooker.hook(this)
    } catch (e: XposedFrameworkError) {
        throw e
    } catch (t: Throwable) {
        log(Log.ERROR, TAG, "system_server package hiding skipped: ${t.message}", t)
    }
}
```

### Native Maps Redaction Shape

Native redaction should start as opt-in and should prove disabled-mode pass-through. This is the shape, not a full native implementation.

```kotlin
if (!hardeningPolicy.nativeMapsRedaction) {
    return originalRead(fd, buffer, count)
}

if (!fdTracker.isProcSelfMaps(fd)) {
    return originalRead(fd, buffer, count)
}

val bytesRead = originalRead(fd, buffer, count)
return redactMappedLinesInPlace(buffer, bytesRead, hiddenPatterns)
```

### Android 16 Posture Diagnostics First

Advanced Protection should be observed first. Hooking or spoofing security posture should come only after target evidence.

```kotlin
@RequiresApi(36)
fun readAdvancedProtectionState(context: Context): Boolean? =
    runCatching {
        val manager = context.getSystemService(AdvancedProtectionManager::class.java)
        manager?.isAdvancedProtectionEnabled
    }.getOrNull()
```

Record unavailable/permission-denied separately from `false`; otherwise diagnostics will lie.

## Final Reviewer Decision

Implement the second report's conservative correctness ideas first, but also add the first report's broad hardening ideas as advanced roadmap tracks. The key correction is sequencing: do not bundle native hooks, system_server hooks, security posture hooks, selective deopt, and persona rewiring into one patch.

The highest quality path is:

1. Fix deterministic ICCID and centralize Luhn.
2. Wire or explicitly defer the `DevicePersona` RemotePreferences contract.
3. Make `DeviceProfilePreset` comments match actual runtime hook coverage.
4. Add profile coverage tests before adding more hooks.
5. Add system_server, native maps, Android 16 security posture, selective deopt, and source-tracked profile database work as advanced tracks with opt-ins and validation gates.

This keeps Device Masker moving toward production-grade identity consistency while still pursuing the deeper hardening work. The standard is not "avoid because solo dev"; the standard is "ship advanced work only with isolation, proof, and rollback."
