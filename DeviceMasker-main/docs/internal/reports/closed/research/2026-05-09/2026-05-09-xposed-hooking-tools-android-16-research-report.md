# Xposed Hooking Tools And Android 16 Research Report

Date: 2026-05-09
Status: active research
Scope: Device Masker Android 16 compatibility, DevCheck crash triage, hook robustness, and `/proc/self/maps` hardening

## Executive Verdict

The best next improvements are not more spoof surfaces first. Device Masker needs better hook discovery, safer native-read verification, Android 16 compatibility gates, and crash isolation.

Recommended order:

1. Capture real Android 16 DevCheck crash evidence.
2. Add hook-family isolation so one risky family can be disabled per app.
3. Implement Java-first `/proc/self/maps` hardening.
4. Extend `:verifier` to prove Java line, Java byte, NIO, and native maps-read behavior.
5. Evaluate `libxposed/helper` for discovery-only matching.
6. Use DexKit and Frida as research tooling, not default runtime dependencies.
7. Evaluate ByteHook first if native maps redaction becomes necessary.
8. Keep ShadowHook, xHook, and Dobby as fallback/native-reference options only.

Native hook libraries should not be added to production by default until verifier evidence proves Java coverage is insufficient and the Android 16 crash path is understood.

## Current Project Context

Device Masker already has the correct high-level architecture:

- `:app` writes config through libxposed RemotePreferences.
- `:common` owns key contracts and generated identity values.
- `:xposed` owns target-process hooks.
- `:verifier` provides controlled runtime evidence.
- Release R8 is enabled and depends on `StableHooker`.
- Direct Kotlin SAM `.intercept { ... }` callbacks are forbidden in runtime hookers.
- Global class lookup hiding is disabled by default because it can destabilize target startup.
- Native `/proc/self/maps` redaction and system_server package hiding are not implemented and should remain advanced opt-in tracks.

The active Android 16 issue is real-device instability, especially DevCheck. Android 13 emulator success does not prove Android 16 support.

## Research Summary Matrix

| Tool/API | Category | Best Use | Production Readiness For Device Masker | Verdict |
| --- | --- | --- | --- | --- |
| `libxposed/helper` / `helper-ktx` | libxposed helper API | Structured class/method/field discovery with fallbacks | Medium | Strong candidate for discovery-only use |
| `libxposed/lint` | Static analysis | Catch libxposed API misuse | Unknown, needs Gradle evaluation | Worth investigating |
| DexKit | DEX analysis | Find obfuscated methods/classes and caller paths | Low as runtime dependency, high as research tool | Use offline/dev first |
| ByteHook | Native PLT hook | Native `/proc/self/maps` experiment | Medium-high risk | Best first native candidate |
| ShadowHook | Native inline hook | Fallback when PLT hooks miss | High risk | Advanced fallback only |
| xHook | Native PLT hook | Historical/reference PLT implementation | Low for Android 16 | Do not use first |
| Dobby | Native inline/multi-platform hook | General native hook fallback | High integration burden | Reference/fallback only |
| Frida / Frida Gum | Dynamic instrumentation | DevCheck crash research and hook prototyping | Not for production | Lab-only |
| Hide My Applist | Xposed module reference | Package visibility surface research | Not reusable directly | Learn concepts only |
| Android 16 compat APIs/checks | Platform validation | A16 behavior, hidden API, ART, 16 KB gates | Mandatory | Add to plan/gates |

## 1. `libxposed/helper` And `helper-ktx`

Sources:

- `https://github.com/libxposed/helper`
- Context7 `/libxposed/helper`
- Local skill: `.agents/skills/libxposed/SKILL.md`

What it provides:

- Class matching.
- Method matching.
- Field and constructor matching.
- Fallback matching through substitute/miss handlers.
- Kotlin DSL in `helper-ktx`.
- Exception handling around hook discovery.

Why it helps Device Masker:

- Android/OEM method signatures vary.
- Android 16 updates ART and restricted API behavior.
- Current hookers use manual reflection in several places.
- A structured matcher can reduce rats nest reflection code.
- `onMatch` / `onMiss` style fits discovery-first hooks and system_server exploration.

Best target files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/PackageManagerHooker.kt`
- Future `SystemServerPackageHooker.kt`
- Future `ProcMapsHooker.kt` only for Java API discovery, not native reads

Risk:

- Some example snippets use hook callback shapes that may not match Device Masker's R8-safe `stableHooker` rule.
- Helper repository has no published GitHub releases visible in the inspected page.
- Adding it everywhere at once would create broad random churn.

Recommendation:

- Evaluate it in one small hooker first.
- Use it for discovery/matching only.
- Continue registering hooks through existing `xi.hook(method).intercept(stableHooker { ... })`.
- Add a static test that helper adoption does not reintroduce direct `.intercept { ... }`.

Good implementation shape:

```kotlin
// Pseudocode only. Keep Device Masker's stableHooker path for registration.
val method = findMethodWithHelperOrNull(
    classLoader = cl,
    className = "android.content.pm.ApplicationPackageManager",
    methodName = "hasSystemFeature",
    parameterTypes = arrayOf(String::class.java),
)
if (method != null) {
    xi.hook(method).intercept(stableHooker { chain ->
        val original = chain.proceed()
        // Device Masker policy here
        original
    })
    xi.deoptimize(method)
}
```

## 2. `libxposed/lint`

Source:

- `https://github.com/libxposed/lint`

Why it matters:

Device Masker already hit a real release-only libxposed ABI problem: direct Kotlin SAM callbacks crashed with `AbstractMethodError` under R8. Static enforcement is valuable.

Potential checks:

- `io.github.libxposed:api` must be `compileOnly`.
- Module entry must be in `META-INF/xposed/java_init.list`.
- `module.prop` must include `minApiVersion=101` and `targetApiVersion=101`.
- No legacy `XposedBridge`, `XC_MethodHook`, `beforeHookedMethod`, or `afterHookedMethod`.
- No direct `.intercept { ... }` runtime callbacks.
- Hook error handling must rethrow `XposedFrameworkError`.

Risk:

- Need to verify whether the lint artifact is currently published and easy to consume.

Recommendation:

- Add an investigation task.
- If the lint plugin is not ready, keep implementing equivalent project tests:
  - `R8HookerAbiTest`
  - `ReleaseBuildSafetyTest`
  - a new `LibxposedApiUsageSafetyTest`

## 3. DexKit

Source:

- `https://github.com/LuckyPray/DexKit`

What it does:

DexKit is a DEX search/deobfuscation library. It can locate classes, methods, and call patterns when names are obfuscated.

Why it helps:

- DevCheck and similar apps may obfuscate detection paths.
- It can help find methods reading:
  - `/proc/self/maps`
  - `Build.*`
  - `Settings.Secure.ANDROID_ID`
  - `TelephonyManager`
  - `PackageManager`
  - native-loader calls
- libxposed API docs mention DexKit-like caller discovery for deoptimization scenarios.

Best use in Device Masker:

- Offline/dev tooling.
- A separate `tools/` or non-shipped diagnostic module.
- Optional target APK analyzer that emits a report of suspected identifier/detection reads.

Risk:

- Runtime use inside target apps is heavy.
- License needs detailed review. The repo page indicates Apache-2.0 generally, with `Core/` under LGPL-3.0.
- Adding it to production hooks would increase crash and detection surface.

Recommendation:

- Do not add DexKit to `:xposed` runtime yet.
- Add it as a research track after crash evidence capture.
- Use it to support decisions, not to hook everything dynamically in production.

## 4. ByteHook

Source:

- `https://github.com/bytedance/bhook`

What it does:

ByteHook is an Android PLT hook library. It is published on Maven Central and uses Prefab native dependency packaging.

Why it helps:

Native anti-detection code can bypass Java hooks by reading `/proc/self/maps` through libc-level APIs. ByteHook is a reasonable first native candidate for hooking functions such as:

- `fopen`
- `open`
- `openat`
- maybe `read` after file-descriptor tracking is proven

Best use:

- Experimental native maps redaction track.
- Per-app opt-in only.
- Kill switch required.
- Start with `/proc/self/maps` and `/proc/<pid>/maps` only.

Important packaging facts:

- ByteHook uses Prefab.
- If added, native packaging and duplicate `.so` handling must be reviewed.
- 16 KB page-size verification becomes mandatory.

Risk:

- The indexed README states Android 4.1-15 support. Android 16 must be verified.
- PLT hooks do not catch direct syscalls.
- Native code can crash target apps harder than Java hooks.

Recommendation:

- Best first native hook engine to evaluate.
- Do not enable by default.
- Add to plan only after verifier proves native maps reads still expose LSPosed/module evidence.

## 5. ShadowHook

Source:

- `https://github.com/bytedance/android-inline-hook`

What it does:

ShadowHook is an Android inline hook library for ARM/ARM64 and related instruction sets.

Why it helps:

- Can hook paths PLT hooks miss.
- Useful if target code bypasses PLT or resolves symbols in a way ByteHook cannot cover.

Risk:

- Higher crash risk than PLT hooks.
- More sensitive to Android runtime and CPU/ABI differences.
- Packaging includes multiple `.so` files and duplicate handling concerns.
- Android 16 must be validated.

Recommendation:

- Keep as fallback after ByteHook.
- Use only when there is concrete evidence:
  - Java filtering fails.
  - ByteHook fails.
  - Native verifier still sees suspicious maps lines.

## 6. xHook

Source:

- `https://github.com/iqiyi/xHook`

What it does:

xHook is a PLT hook library for Android ELF files.

Why it is less attractive:

- Its README states Android 4.0-10 support in the indexed source.
- Device Masker is targeting Android 13 and Android 16 validation.

Recommendation:

- Do not use as first implementation.
- Keep as historical reference for PLT hook design and redaction strategy only.

## 7. Dobby

Source:

- `https://github.com/jmpews/Dobby`

What it does:

Dobby is a lightweight multi-platform hook framework with Android/Linux and ARM/ARM64 support.

Why it may help:

- General native hook fallback.
- Can be useful if Android-specific hook libraries fail a specific case.

Risk:

- More integration work.
- Less Android Gradle/Prefab friendly than ByteHook for this project.
- Higher maintenance burden.

Recommendation:

- Not a first choice.
- Keep as fallback/reference.

## 8. Frida / Frida Gum

Source:

- `https://github.com/frida`

What it does:

Frida is a dynamic instrumentation toolkit. Frida Gum is its native instrumentation engine.

Why it helps:

- Fast DevCheck crash triage.
- Can prototype Java and native hooks without rebuilding Device Masker.
- Can confirm whether DevCheck reads `/proc/self/maps`, PackageManager, Build fields, or telephony APIs before crashing.

Use cases:

- Attach to DevCheck on the real Android 16 device.
- Trace file open/read functions.
- Trace Java API calls.
- Confirm if app exits after detecting LSPosed strings.

Risk:

- Frida is itself heavily detected.
- It should not be packaged into Device Masker.
- It is a lab tool, not production dependency.

Recommendation:

- Add Frida scripts to a non-shipped `tools/frida/` folder only if needed.
- Use for evidence, not runtime behavior.

## 9. Hide My Applist

Source:

- `https://github.com/Dr-TSNG/Hide-My-Applist`

What it does:

Hide My Applist is an Xposed module focused on app-list/package visibility hiding.

Why it helps:

- It validates that PackageManager/app-list detection is a real and common surface.
- It can inform Device Masker's package visibility test matrix.

Risk:

- Current README includes restrictive terms for newer versions:
  - no modifications
  - no redistribution
  - no code picking without credit
- Do not copy implementation.

Recommendation:

- Use conceptually only.
- Improve Device Masker's own PackageManager coverage and verifier tests.
- Add tests for installed package queries, package info queries, application info queries, and API 33+ flag overloads.

## 10. Android 16 Compatibility APIs And Checks

Sources:

- `https://developer.android.com/about/versions/16/behavior-changes-all`
- `https://developer.android.com/about/versions/16/changes/non-sdk-16`
- `https://developer.android.com/guide/practices/page-sizes`

Key findings:

- Android 16 includes ART internal changes.
- Apps/libraries relying on internal ART structures can break.
- Android 16 updates restricted non-SDK interface lists.
- Using non-SDK methods/fields carries high compatibility risk.
- Android 16 adds 16 KB page-size compatibility mode, but Google still recommends true 16 KB alignment.
- Google guidance says AGP 8.5.1+ and NDK r28+ make 16 KB support easier; NDK r28 builds 16 KB-aligned by default.

Device Masker implications:

- Do not add HiddenApiBypass as a quick fix.
- Treat reflection failures as normal OEM/Android-version variance.
- Add hidden API warning collection to A16 crash logs.
- Add `NoSuchMethodError`, `NoSuchFieldError`, `VerifyError`, and `UnsatisfiedLinkError` scans to runtime evidence.
- If native hook libraries are added, run 16 KB verification on debug, release, and CI release APKs.

## Recommended Plan Updates

Add these to `docs/superpowers/plans/2026-05-09-android-16-compatibility-and-proc-maps-hardening.md`:

### Add Phase: libxposed Helper Evaluation

Goal:

- Use helper APIs for discovery/matching only.

Acceptance:

- One hooker migrated experimentally.
- No direct `.intercept { ... }`.
- R8 release smoke still passes.
- Missing method path logs a skip, not a crash.

### Add Phase: libxposed API Static Guard

Goal:

- Evaluate `libxposed/lint`.
- If not usable, add equivalent tests.

Acceptance:

- Static tests catch legacy XposedBridge usage.
- Static tests catch direct Kotlin SAM intercept usage.
- Static tests verify module metadata and compileOnly API dependency.

### Add Phase: DexKit Research Track

Goal:

- Build optional research tooling to inspect target APKs.

Acceptance:

- Not included in production APK.
- Produces a report of likely identifier/detection API call sites.
- License impact documented before dependency adoption.

### Add Phase: Native Hook Engine Decision

Goal:

- Compare ByteHook, ShadowHook, xHook, and Dobby for native maps redaction.

Acceptance:

- ByteHook tested first.
- ShadowHook only tested if ByteHook cannot cover the target path.
- xHook and Dobby kept as fallback/reference.
- Native track remains per-app opt-in and default-off.

### Add Phase: Frida Lab Scripts

Goal:

- Use Frida to investigate DevCheck crash behavior.

Acceptance:

- Scripts are not packaged.
- Output goes under `logs/device/` or `logs/tmp/`.
- Findings are copied into the Android 16 crash report.

### Add Phase: Package Visibility Coverage Audit

Goal:

- Learn from HMA-style surfaces without copying code.

Acceptance:

- Device Masker tests cover PackageManager query variants.
- API 33+ flag overloads are verified.
- Hidden package list remains centralized and configurable.

## Final Recommendation

For Device Masker, the strongest near-term stack is:

1. Existing libxposed API 101 + `StableHooker`.
2. `libxposed/helper` only for safer discovery/fallback matching.
3. Project-owned static tests or `libxposed/lint` if usable.
4. DexKit as offline/dev research tooling.
5. ByteHook as the first native maps-redaction experiment.
6. ShadowHook only as an advanced fallback.
7. Frida only for lab/debug evidence.
8. Android 16 and 16 KB checks as mandatory gates.

Do not add native hook engines before DevCheck crash evidence is captured. Adding native hooks without knowing the crash path would increase the target-process crash surface and would be voodoo programming.

## Sources

- libxposed organization: https://github.com/libxposed
- libxposed helper: https://github.com/libxposed/helper
- libxposed lint: https://github.com/libxposed/lint
- libxposed example: https://github.com/libxposed/example
- Context7 `/libxposed/helper`
- ByteHook: https://github.com/bytedance/bhook
- ShadowHook: https://github.com/bytedance/android-inline-hook
- xHook: https://github.com/iqiyi/xHook
- Dobby: https://github.com/jmpews/Dobby
- Frida: https://github.com/frida
- DexKit: https://github.com/LuckyPray/DexKit
- Hide My Applist: https://github.com/Dr-TSNG/Hide-My-Applist
- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-all
- Android 16 non-SDK restrictions: https://developer.android.com/about/versions/16/changes/non-sdk-16
- Android 16 page-size support: https://developer.android.com/guide/practices/page-sizes
