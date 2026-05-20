# R8 + libxposed Runtime Crash Analysis - 2026-05-06

## Executive Summary

Release R8 shrinking is desirable and should remain a goal. It reduced the APK from roughly 16-18 MB to about 3.8-4.0 MB in the current audit work.

However, the current minified release build is not runtime-safe for LSPosed/libxposed target processes. `com.mantle.verify` crashes during startup before spoof events fire. The reproduced crash is a libxposed hook ABI failure:

```text
java.lang.AbstractMethodError:
abstract method "java.lang.Object ...XposedInterface$Hooker.intercept(...XposedInterface$Chain)"
```

This is not a Mantle business-logic crash and not a normal spoof config issue. LSPosed loads Device Masker, selects `com.mantle.verify`, starts hook registration, then hook dispatch fails when the framework tries to invoke `XposedInterface.Hooker.intercept(Chain)`.

The best long-term option is to keep R8 enabled for release, but remove Kotlin SAM/lambda hookers from the `:xposed` runtime path. Replace them with explicit named `XposedInterface.Hooker` implementations or a small set of stable hooker adapter classes, and keep those adapter classes and their `intercept(Chain)` methods. This preserves most APK-size benefits while making the libxposed ABI visible and stable under R8.

## Local Evidence

### Environment

- Device: `emulator-5554`
- Android: 13 / API 33
- LSPosed Manager: `2.0.2 (7668)`
- libxposed API shown by LSPosed: API 101
- Target: `com.mantle.verify` version `2.2.2` / versionCode `20202`
- Device Masker installed package:
  - package: `com.astrixforge.devicemasker`
  - versionName: `1.0.0`
  - versionCode: `1`
  - install/update time observed during release-test run: `2026-05-06 05:08:07`

### Reproduction Command

```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb shell pidof com.mantle.verify
adb logcat -d -v time -t 2500 > mantle_release_crash_logcat.txt
```

### Result

`pidof com.mantle.verify` returned empty after launch. Mantle crashed.

Relevant log sequence from `mantle_release_crash_logcat.txt`:

```text
XposedEntry loaded for process: com.mantle.verify
Target package selected: com.mantle.verify
[AntiDetectHooker] Hook registration started for com.mantle.verify
Class lookup hiding enabled for: com.mantle.verify
BufferedReader.readLine() hook failed: abstract method "...XposedInterface$Hooker.intercept(...XposedInterface$Chain)"
[AntiDetectHooker] Hook registration failed ... AbstractMethodError
[DeviceHooker] Hook registration failed ... AbstractMethodError
[SystemHooker] Hook registration failed ... AbstractMethodError
All hooks registered for: com.mantle.verify
FATAL EXCEPTION: main
Process: com.mantle.verify
java.lang.AbstractMethodError: abstract method "...XposedInterface$Hooker.intercept(...XposedInterface$Chain)"
```

No successful `Spoof event` entries were observed before the fatal crash.

### Important Mapping Observation

The release mapping keeps core hooker classes:

```text
com.astrixforge.devicemasker.xposed.hooker.AntiDetectHooker -> com.astrixforge.devicemasker.xposed.hooker.AntiDetectHooker
com.astrixforge.devicemasker.xposed.hooker.PackageManagerHooker -> com.astrixforge.devicemasker.xposed.hooker.PackageManagerHooker
```

But synthetic lambda classes are still optimized/renamed:

```text
AdvertisingHooker$$ExternalSyntheticLambda0 -> g3
PackageManagerHooker$$ExternalSyntheticLambda0 -> zv1
DeviceHooker$$ExternalSyntheticLambda22 -> pa0
NetworkHooker$$ExternalSyntheticLambda4 -> tr1
```

This means the current keep rules preserve the visible hooker classes but do not sufficiently protect the generated callback objects that libxposed actually invokes through the `Hooker` ABI.

## Official Documentation Findings

### libxposed Hook ABI

Official libxposed Javadoc states:

- `XposedInterface.Hooker` is the hooker interface for a method or constructor.
- `Hooker.intercept(Chain)` is the method invoked for a hook callback.
- `HookBuilder.intercept(Hooker hooker)` accepts a `Hooker` object and builds the hook.
- The libxposed package summary describes the modern API as an interceptor-chain model where modules implement `Hooker` and its `intercept(Chain)` method.

Sources:

- https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Hooker.html
- https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.HookBuilder.html
- https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html

Implication for Device Masker:

`intercept(Chain)` is a runtime ABI boundary. If R8 changes, merges, adapts, or rewrites the callback class so that the runtime object no longer implements the exact `Hooker.intercept(Chain)` method that LSPosed expects, target processes can crash with `AbstractMethodError`.

### LSPosed / libxposed Project State

The old `LSPosed/LSPosed` repository is archived/read-only as of 2026, while the `libxposed` organization hosts the modern API, service, helper, and example repositories. GitHub currently shows the `libxposed/api` and `libxposed/example` repos as updated in 2026.

Sources:

- https://github.com/LSPosed/LSPosed
- https://github.com/libxposed

Implication:

For modern API 101 behavior, Device Masker should follow `libxposed` API/Javadoc and example behavior, not legacy XposedBridge-era assumptions.

### Android R8 Keep Rule Guidance

Android's official R8 docs state that R8 performs major modifications such as renaming, moving, and removing classes, fields, and methods. R8 can preserve direct code calls, but it cannot always see indirect usage such as reflection or native-code calls, so keep rules are needed for those cases.

The same docs recommend keeping rules as narrow as possible, preserving only what is needed, and avoiding broad package-wide rules because broad rules reduce R8 benefits.

Sources:

- https://developer.android.com/topic/performance/app-optimization/keep-rules-overview
- https://developer.android.com/blog/posts/configure-and-troubleshoot-r8-keep-rules
- https://developer.android.com/topic/performance/app-optimization/global-options

Implication:

Device Masker is in a difficult but common R8 category: libxposed calls module code indirectly from a framework/native hook pipeline. R8 cannot fully infer that all synthetic Kotlin lambda callback classes must preserve the exact libxposed `Hooker` ABI.

## Root Cause

The current release crash is caused by R8 optimizing Device Masker's xposed hook callback bytecode in a way that is incompatible with libxposed runtime dispatch.

More specifically:

1. Device Masker uses Kotlin SAM/lambda syntax in many hook registrations:

   ```kotlin
   xi.hook(method).intercept { chain ->
       ...
   }
   ```

2. The Kotlin compiler/D8/R8 pipeline materializes these callbacks as synthetic lambda classes or optimized callback methods.

3. libxposed/LSPosed invokes the callback object through the `XposedInterface.Hooker.intercept(Chain)` ABI.

4. In the minified release APK, at least some callback objects no longer satisfy the exact runtime method contract LSPosed expects.

5. LSPosed throws `AbstractMethodError` when dispatching the hook.

This explains why:

- Debug/non-minified runtime previously worked.
- Release R8 build compiles and mapping looks superficially OK.
- Core hooker class names are preserved, but target process startup still crashes.
- Multiple hooker areas fail with the same `AbstractMethodError`, not just one hook implementation.
- Mantle and DevCheck both show the same class of crash in DropBox history.

## Why The Current Keep Rules Are Not Enough

Current rules include:

```proguard
-keep interface io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep interface io.github.libxposed.api.XposedInterface$Chain { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookBuilder { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookHandle { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.** { *; }
```

These rules protect named classes and interface declarations, but they do not reliably protect every generated callback form that Kotlin/R8 can produce for SAM lambdas. The release mapping confirms synthetic lambda classes remain heavily optimized/renamed.

R8 may also inline, merge, adapt method signatures, or rewrite synthetic classes in ways that are legal for normal app code but unsafe for a third-party framework that invokes callbacks through an external runtime ABI.

## Option Analysis

### Option 1 - Disable R8 for `release`

Use:

```kotlin
release {
    isMinifyEnabled = false
    isShrinkResources = false
}
```

Pros:

- Fastest way to restore target-app stability.
- Lowest engineering risk.
- Matches the previously working runtime base.
- Avoids synthetic lambda ABI failures entirely.

Cons:

- APK returns to about 16-18 MB.
- Loses the main size win from the audit.
- Does not solve the underlying compatibility problem.

Use when:

- A working APK is needed immediately.
- Runtime stability is more important than size.

Assessment:

Good emergency rollback, not ideal as the final strategy.

### Option 2 - Keep R8, Disable Optimization Only

Use a temporary global rule:

```proguard
-dontoptimize
```

or selectively reduce optimization around xposed classes.

Pros:

- Keeps shrinking and possibly obfuscation.
- May avoid lambda/interface optimization bugs.
- Useful as a diagnostic split: if crash disappears, optimization rather than shrinking is the culprit.

Cons:

- Android docs warn global optimization-disabling flags are development tools and should not be used as final release strategy.
- APK may grow significantly compared with full R8.
- Still may not protect the exact hook callback ABI.
- Broad global flags can mask the real problem.

Use when:

- Diagnosing which R8 phase breaks the hookers.

Assessment:

Useful for investigation, not the best production answer.

### Option 3 - Keep R8, Add Broader Synthetic Lambda Keep Rules

Examples to test:

```proguard
-keep class com.astrixforge.devicemasker.xposed.hooker.**$$ExternalSyntheticLambda* { *; }
-keep class com.astrixforge.devicemasker.xposed.**$$ExternalSyntheticLambda* { *; }
-keepclassmembers class * implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain) throws java.lang.Throwable;
}
```

Pros:

- Minimal source-code change.
- Preserves much of R8 shrink/resource benefit if it works.
- Can be quickly validated against Mantle and DevCheck.

Cons:

- Fragile. Synthetic class naming is compiler/R8 implementation detail.
- Kotlin/R8 may emit callback forms that do not match the expected name pattern.
- Future Kotlin, AGP, or R8 upgrades can re-break it.
- Does not address bridge-method or interface adaptation edge cases.

Use when:

- A short-term R8-on experiment is needed.

Assessment:

Possible stopgap, but not the strongest long-term architecture.

### Option 4 - Explicit Named `XposedInterface.Hooker` Classes

Replace lambda hook callbacks with explicit classes/objects that implement the ABI directly:

```kotlin
private class ReadLineHooker(
    private val prefs: SharedPreferences,
    private val packageName: String,
) : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val original = chain.proceed()
        ...
        return original
    }
}
```

Then register:

```kotlin
xi.hook(method)
    .setExceptionMode(ExceptionMode.PROTECTIVE)
    .intercept(ReadLineHooker(prefs, packageName))
```

Keep rule:

```proguard
-keep,includedescriptorclasses class com.astrixforge.devicemasker.xposed.hooker.** implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain) throws java.lang.Throwable;
}
```

Pros:

- Makes the libxposed ABI explicit in source and bytecode.
- Avoids Kotlin SAM/lambda synthetic callback ambiguity.
- Allows targeted keep rules.
- Preserves most R8 size reduction.
- Easier to inspect in mapping and APK bytecode.
- Easier to unit/static-test: every hooker callback class implements the expected interface.

Cons:

- More code than lambdas.
- Must refactor many hook registrations.
- Captured variables need constructor fields or shared context objects.
- Must avoid over-abstraction while converting.

Use when:

- R8 must stay enabled for release.
- Runtime stability matters.

Assessment:

Best long-term solution.

### Option 5 - One Stable Dispatching Hooker Adapter

Use one or a small number of explicitly kept hooker classes with a function field:

```kotlin
internal class StableHooker(
    private val callback: Callback,
) : XposedInterface.Hooker {
    fun interface Callback {
        @Throws(Throwable::class)
        fun intercept(chain: XposedInterface.Chain): Any?
    }

    override fun intercept(chain: XposedInterface.Chain): Any? {
        return callback.intercept(chain)
    }
}
```

Pros:

- Reduces the number of classes compared with one named class per hook.
- Keeps libxposed-facing ABI stable on `StableHooker`.
- Migration can be incremental.

Cons:

- If `Callback` remains a lambda, R8 can still optimize the internal callback. The difference is that LSPosed only calls `StableHooker.intercept`, so internal lambda failures are less likely to be ABI failures, but still must be validated.
- Can hide hook-specific logic behind generic dispatch.
- If the callback throws, it still happens inside target process.

Assessment:

Good pragmatic compromise if full named hooker classes are too large. The libxposed-facing class must be explicit and strongly kept.

### Option 6 - Split Xposed Runtime Into A Separate Unminified Artifact

Try to keep the app minified while packaging the xposed runtime classes in a separate unminified dex/jar/feature-like unit.

Pros:

- Keeps UI/app code optimized.
- Could avoid minifying fragile hook ABI classes.

Cons:

- Android app R8 normally sees program classes together and can still process library-module classes.
- More build complexity.
- LSPosed expects normal APK metadata and class loading.
- Risky and likely not worth it before simpler options.

Assessment:

Not recommended as the first solution.

## Recommended Plan

### Phase 1 - Prove The R8 Failure Boundary

Build three release variants and test Mantle after each:

1. `releaseNoMinify`: minify off.
2. `releaseShrinkNoOptimize`: shrink on, optimization constrained with diagnostic flags.
3. `releaseR8Full`: current full R8.

Expected result:

- `releaseNoMinify` should pass if the old working base is still valid.
- `releaseR8Full` currently fails.
- The middle variant tells us whether shrinking/obfuscation alone is safe or whether optimization is the direct trigger.

### Phase 2 - Add A Bytecode/Mapping Gate

Before runtime testing, add a release verification script that checks:

- `XposedEntry` is preserved.
- `META-INF/xposed/java_init.list` still contains `com.astrixforge.devicemasker.xposed.XposedEntry`.
- Every class that should be libxposed-callable has an `intercept(XposedInterface.Chain)` method.
- Synthetic lambda classes are not the only libxposed-facing callback implementation.
- Mapping does not show xposed hook callback classes collapsed into ambiguous short classes without explicit keep coverage.

This cannot replace emulator testing, but it can catch obvious release hazards earlier.

### Phase 3 - Refactor libxposed-Facing Lambdas

Convert hook callback registration in priority order:

1. `AntiDetectHooker`
   - Class lookup hooks.
   - Stack trace hooks.
   - `/proc/self/maps` hook.
   - PackageManager/package visibility hooks.
2. `BaseSpoofHooker` helper registrations and `loadClassOrNull`-adjacent paths.
3. Device/network/system hooks that run during early app startup.
4. Lower-risk hooks that fire after app initialization.

Reason:

The crash happens before Mantle app initialization completes. The earliest classloader, package manager, stack trace, and class lookup hooks are the most dangerous.

### Phase 4 - Keep R8 Enabled With Narrow Rules

Use explicit keep rules for stable hooker callback classes, not broad package-wide rules unless a temporary experiment proves they are required.

Example direction:

```proguard
-keep,includedescriptorclasses class com.astrixforge.devicemasker.xposed.hooker.** implements io.github.libxposed.api.XposedInterface$Hooker {
    public java.lang.Object intercept(io.github.libxposed.api.XposedInterface$Chain) throws java.lang.Throwable;
}
```

If named hooker classes use constructor-captured state:

```proguard
-keepclassmembers class com.astrixforge.devicemasker.xposed.hooker.** implements io.github.libxposed.api.XposedInterface$Hooker {
    <init>(...);
    <fields>;
}
```

Avoid permanent global flags like:

```proguard
-dontoptimize
-dontobfuscate
-dontshrink
```

Those are useful only as diagnostics.

### Phase 5 - Runtime Gate

A release APK is not acceptable until this exact runtime gate passes:

```powershell
.\gradlew.bat assembleRelease --no-daemon
adb install -r <signed-release-apk>
adb reboot
adb wait-for-device
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
adb shell pidof com.mantle.verify
adb logcat -d -v time -t 2500
```

Required evidence:

- Mantle PID is alive after launch.
- LSPosed logs show:
  - `XposedEntry loaded for process: com.mantle.verify`
  - `Target package selected: com.mantle.verify`
  - `All hooks registered for: com.mantle.verify`
- No `AbstractMethodError`.
- No `NoClassDefFoundError` from a failed class initializer after an earlier hook dispatch failure.
- At least one real spoofed value is observed in Mantle UI or logs.
- Repeat on `flar2.devcheck` because DropBox already shows the same crash class there.

## Edge Cases To Cover

### 1. Class Lookup Hiding Enabled

The reproduced log shows:

```text
Class lookup hiding enabled for: com.mantle.verify
```

Class lookup hooks are inherently high-risk because app startup itself uses `ClassLoader.loadClass()` and `Class.forName()`. Even after fixing R8, this path should remain separately gated by per-app opt-in and should be tested both enabled and disabled.

### 2. Protective Mode Does Not Save All Failures

libxposed `ExceptionMode.PROTECTIVE` catches hooker exceptions in some cases, but the observed failure still killed the process. Reasons:

- Some `AbstractMethodError` failures occur in hook chain dispatch itself.
- Exceptions thrown by `chain.proceed()` can propagate.
- Startup-time classloader hooks affect framework/application initialization paths.

Do not assume protective mode makes unsafe hook bytecode harmless.

### 3. Static Initializer Poisoning

The log later shows `NoClassDefFoundError` for `SpoofType` after an earlier `AbstractMethodError` during class initialization:

```text
Rejecting re-init on previously-failed class com.astrixforge.devicemasker.common.SpoofType
NoClassDefFoundError: com.astrixforge.devicemasker.common.SpoofType
Caused by: AbstractMethodError
```

If a hook dispatch failure occurs while a shared class is initializing, the class can be poisoned for the rest of the process. Fixing the first `AbstractMethodError` may remove the later `NoClassDefFoundError`.

### 4. Mapping Retrace Can Mislead

Running retrace against the app mapping maps short class names like `h` to unrelated app classes because the stack includes obfuscated LSPosed/framework-generated classes and target-process classes. Do not rely on app mapping alone for frames outside Device Masker's package.

### 5. API Stub Packaging

`:xposed` correctly uses `compileOnly(libs.libxposed.api)`, but release R8 still reasons over API descriptors at compile time while LSPosed provides runtime implementations. The keep strategy must protect descriptor compatibility, not just class names.

### 6. App UI R8 Issues Are Separate

DropBox history also contains older Device Masker app crashes involving Navigation 3 method mismatch. Those are separate from the current Mantle crash. The current reproduced Mantle crash is in target process hook dispatch.

### 7. Reinstall/Signature/Scope Effects

Release-test installs can change package signatures and require reinstall/reboot/scope refresh. Always confirm:

- LSPosed module enabled.
- Scope includes `android`, `system`, and target app.
- Target app force-stopped after config/scope changes.
- Device rebooted after module APK replacement when needed.

### 8. Resource Shrinking Is Not The Hook Crash

The crash is Java/Kotlin method dispatch (`AbstractMethodError`) in hook callback invocation. Resource shrinking may introduce other issues, but it is not the primary cause of this Mantle crash.

## Final Recommendation

Keep R8 as the release goal, but do not ship the current R8-enabled release. The current minified release crashes scoped target apps.

Recommended implementation path:

1. Keep `release` R8 disabled only as a temporary safety fallback.
2. Add a separate experimental `r8RuntimeRelease` or `ciRelease` variant for ongoing R8 validation.
3. Refactor libxposed-facing Kotlin SAM lambdas into explicit named `XposedInterface.Hooker` classes or stable adapter classes.
4. Add narrow keep rules for those explicit hooker callback classes and their `intercept(Chain)` method.
5. Validate with Mantle and DevCheck using the runtime gate above.
6. Only then enable R8 for the main release build.

This gives the APK-size benefit without depending on fragile synthetic lambda behavior at the libxposed runtime boundary.

## Sources

- Local reproduced log: `mantle_release_crash_logcat.txt`
- Local mapping: `app/build/outputs/mapping/release/mapping.txt`
- Local report reviewed: `docs/internal/reports/closed/audits/2026-05-06/2026-05-06-build-audit-and-r8-enablement.md`
- libxposed Hooker Javadoc: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Hooker.html
- libxposed HookBuilder Javadoc: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.HookBuilder.html
- libxposed package summary: https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html
- libxposed GitHub organization: https://github.com/libxposed
- LSPosed GitHub repository: https://github.com/LSPosed/LSPosed
- Android R8 keep rules overview: https://developer.android.com/topic/performance/app-optimization/keep-rules-overview
- Android R8 keep rules troubleshooting: https://developer.android.com/blog/posts/configure-and-troubleshoot-r8-keep-rules
- Android R8 global options: https://developer.android.com/topic/performance/app-optimization/global-options
