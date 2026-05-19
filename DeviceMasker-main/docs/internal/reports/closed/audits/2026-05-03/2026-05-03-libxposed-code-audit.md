# Device Masker libxposed Code Audit

Date: 2026-05-03  
Scope: `:xposed`, app-side libxposed service/RemotePreferences bridge, shared preference key contracts, Xposed metadata, Gradle dependency wiring, and relevant diagnostics code.  
Initial mode: read-only audit.  
Fix update: source fixes were applied after the audit on 2026-05-03 and are summarized below. The original audit findings are retained after the fix summary as historical context.

## Fix Implementation Summary

Status after remediation: the audited high-risk and medium-risk code issues have been addressed with surgical changes. The remaining risk is runtime validation: these fixes passed local unit tests, but target-process LSPosed smoke testing on a rooted device/emulator is still needed before claiming runtime stability.

### Fixed Issues

1. `WebViewHooker` no longer mutates `chain.args`.
   - Changed `WebSettings.setUserAgentString(String)` interception to copy `chain.args` into an `Object[]` equivalent with `toTypedArray()`.
   - The hook now calls `chain.proceed(args)` with the modified UA argument, matching libxposed `Chain.proceed(Object[])`.
   - Added static regression coverage that fails if `chain.args[` appears under `xposed/src/main/kotlin`.

2. Hook registration no longer swallows libxposed framework errors.
   - Added explicit `catch (e: XposedFrameworkError) { throw e }` before generic `Throwable` fallback handling in:
     - `XposedEntry`
     - `BaseSpoofHooker`
     - `AntiDetectHooker`
     - `SystemServiceHooker`
     - `DeoptimizeManager`
   - Kept existing defensive fallback behavior for normal reflection/OEM API variation failures.
   - Added unit-test source checks for the guarded hook registration files.

3. Per-process package selection was improved.
   - Removed the unconditional `!param.isFirstPackage` skip.
   - `XposedEntry` now stores `ModuleLoadedParam.processName` and selects an enabled hook package from the loaded package and the process base package.
   - Hooks still register once per classloader to avoid duplicate hook chains, but secondary callbacks can now register hooks when the first package in the process is not the enabled app.
   - This is a pragmatic fix, not a complete shared-process identity model. A process can still only have one effective hook package per classloader.

4. Broad default locale/timezone spoofing was narrowed.
   - Removed `TimeZone.getID()` interception.
   - Removed `Locale.toString()` interception.
   - Kept `TimeZone.getDefault()` and `Locale.getDefault()` spoofing, which targets default process identity reads without rewriting every constructed instance.

5. `ConfigSync.syncApp()` now honors canonical app enablement.
   - `syncApp()` now includes `AppConfig.isEnabled` in the app-enabled calculation, matching `buildSnapshot()`.
   - Type enablement now requires a nonblank value before writing the spoof-enabled boolean as true.
   - Added a focused regression test that checks the quick-sync path includes `AppConfig.isEnabled`.

6. RemotePreferences writes now use synchronous commits.
   - Replaced AndroidX async `edit {}` usage in `ConfigSync` with explicit `commit()` and warning logs on failure.
   - Updated direct app-side `XposedPrefs` setters to commit synchronously as well.
   - This makes failed RemotePreferences writes visible instead of treating local snapshot mutation as success.

7. libxposed service listener registration is guarded.
   - `XposedPrefs.init()` now has a local `initialized` guard.
   - Comments now match libxposed's requirement that `XposedServiceHelper.registerListener` should be called once.

8. Stale libxposed migration names were cleaned in touched code.
   - Replaced stale `ModulePreferences` comments with `RemotePreferences`.
   - Replaced stale `ModulePreferencesProvider` with `XposedProvider`.
   - Replaced stale `onSystemServerLoaded` / `onPackageLoaded` references where code now uses `onSystemServerStarting` / `onPackageReady`.
   - Updated app R8 keep comments and provider keep rule to `io.github.libxposed.service.XposedProvider`.

9. Subscription list documentation now matches behavior.
   - The `SubscriptionManager.getActiveSubscriptionInfoList()` comment now states that list shape is preserved and individual `SubscriptionInfo` getters provide spoofed values.
   - The no-op list hook remains intentionally shape-preserving.

### Files Changed By Remediation

- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ServiceClient.kt`
- `app/src/test/java/com/astrixforge/devicemasker/ReleaseBuildSafetyTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/data/ConfigSyncSnapshotTest.kt`
- `xposed/build.gradle.kts`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/DeoptimizeManager.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt`

### Verification After Remediation

Command:

```powershell
.\gradlew.bat spotlessApply :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Broader gate:

```powershell
.\gradlew.bat spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Notes:

- First run exposed a test runtime classpath issue because `XposedFrameworkError` comes from the compile-only libxposed API dependency. `xposed/build.gradle.kts` now adds `testImplementation(libs.libxposed.api)` while preserving `compileOnly(libs.libxposed.api)` for module runtime packaging.
- Gradle still prints existing deprecation warnings for `sourceSets.srcDirs` and Spotless `indentWithSpaces`; those are pre-existing cleanup items and not part of this remediation.

### Residual Risk

- Runtime LSPosed validation has not been rerun in this remediation step.
- Some hooks still read config at registration time, especially profile-derived hooks such as `SystemHooker`, `WebViewHooker`, and `SensorHooker`. Changing those to fully live reads needs a separate hook-by-hook runtime pass because it affects callback behavior and performance.
- The shared-process/classloader model is improved but not fully per-package. Once a classloader is hooked, callbacks still use one selected effective package for that process.
- System-server diagnostics remain best effort; LSPosed logs are still the authoritative runtime proof.

## Executive Summary

Device Masker is broadly aligned with the modern libxposed API 101 model:

- The module entry class extends `XposedModule`.
- Runtime app hooks are registered from `onPackageReady`.
- System-server diagnostics setup is registered from `onSystemServerStarting`.
- The hook layer uses lambda interceptors through `xi.hook(method).intercept { chain -> ... }`.
- `:xposed` uses `compileOnly` for `io.github.libxposed:api`.
- App-to-hook configuration is RemotePreferences-first.
- The app uses the correct libxposed service listener method names: `onServiceBind` and `onServiceDied`.
- The Xposed metadata files exist and target API 101.

The highest-risk issues are not legacy API hallucinations. The main risks are modern libxposed edge-contract violations and runtime behavior mismatches:

1. `WebViewHooker` mutates `chain.args`, but libxposed `Chain.getArgs()` is immutable.
2. Hook registration wrappers catch `Throwable`, which swallows `HookFailedError` / `XposedFrameworkError`.
3. Per-app spoof config is effectively bound to the first package loaded for a classloader, which can be wrong in shared or multi-package processes.
4. Some hookers read RemotePreferences only during hook registration, so their values are not truly live despite comments and UI wording implying live behavior.
5. Some broad instance hooks, especially `TimeZone.getID()` and `Locale.toString()`, can spoof every instance rather than only defaults.
6. App-side `ConfigSync.syncApp()` ignores `AppConfig.isEnabled`.
7. RemotePreferences writes use async `apply()` through AndroidX `edit {}` and do not surface framework commit failure.

The current working base should be preserved. Fixes should be surgical and verified with both unit tests and LSPosed runtime smoke tests.

## Project Context

Device Masker is an Android LSPosed/libxposed module for privacy research and controlled per-app identity spoofing. It is in active development, not a stable release.

Current architecture:

- `:app`
  - Compose UI.
  - JSON config persistence.
  - libxposed `XposedService` listener.
  - Writes flattened config to RemotePreferences.
  - Rootless app logs and best-effort diagnostics UI.

- `:common`
  - Shared config models.
  - `SharedPrefsKeys` single source of truth.
  - Generators and validation helpers.
  - Diagnostics-only AIDL contract.

- `:xposed`
  - `XposedEntry`.
  - Hookers.
  - RemotePreferences reader helpers.
  - Safer anti-detection hooks.
  - Best-effort diagnostics service in system server.

Important project rules from the Memory Bank and AGENTS guidance:

- Config delivery is RemotePreferences-first.
- AIDL is diagnostics-only, never a spoof config channel.
- LSPosed logs are authoritative proof of target-process hook registration and spoof events.
- `JsonConfig.appConfigs` is canonical for app assignment and enablement.
- `SpoofGroup.assignedApps` is legacy/display compatibility only.
- `SharedPrefsKeys` is the only key builder.
- Hookers must not generate fresh identifiers at runtime.
- Hooks must return originals for disabled, missing, blank, malformed, unsafe, or unsupported config.
- Target app processes must not discover the custom diagnostics service through `ServiceManager`.
- Global `Class.forName` and `ClassLoader.loadClass` anti-detection hooks remain disabled by default.

## libxposed References Read

The audit used the local `libxposed` skill and verified project code against these official/reference materials:

- `.agents/skills/libxposed/SKILL.md`
- `.agents/skills/libxposed/references/javadoc/INDEX.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/01-package-and-XposedInterface.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/02-Chain-HookBuilder-HookHandle-Hooker.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/03-Invoker-ExceptionMode-Module-Interface.md`
- `.agents/skills/libxposed/references/javadoc/api-javadoc/04-error-package.md`
- `.agents/skills/libxposed/references/javadoc/service-javadoc/01-service-complete.md`
- `.agents/skills/libxposed/references/github/api/api/src/main/java/io/github/libxposed/api/XposedInterface.java`
- `.agents/skills/libxposed/references/github/api/api/src/main/java/io/github/libxposed/api/XposedModule.java`
- `.agents/skills/libxposed/references/github/api/api/src/main/java/io/github/libxposed/api/XposedModuleInterface.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/java/io/github/libxposed/service/XposedServiceHelper.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/java/io/github/libxposed/service/XposedService.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/java/io/github/libxposed/service/RemotePreferences.java`
- `.agents/skills/libxposed/references/github/service/service/src/main/AndroidManifest.xml`
- `.agents/skills/libxposed/references/github/service/interface/src/main/aidl/io/github/libxposed/service/IXposedService.aidl`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/ModuleMain.java`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/ModuleMainKt.kt`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/App.kt`
- `.agents/skills/libxposed/references/github/example/app/src/main/java/io/github/libxposed/example/MainActivity.kt`
- `.agents/skills/libxposed/references/github/example/app/build.gradle.kts`
- `.agents/skills/libxposed/references/github/example/app/src/main/resources/META-INF/xposed/java_init.list`
- `.agents/skills/libxposed/references/github/example/app/src/main/resources/META-INF/xposed/module.prop`
- `.agents/skills/libxposed/references/github/example/app/src/main/resources/META-INF/xposed/scope.list`
- `.agents/skills/libxposed/references/github/LSPosed-wiki/Module-Scope.md`

The most relevant verified API facts for this audit:

- `Chain.getArgs()` returns an immutable `List<Object>`.
- Changed arguments must be passed with `chain.proceed(Object[])` or `chain.proceedWith(Object, Object[])`.
- `HookFailedError` extends `XposedFrameworkError`, which extends `Error`.
- `HookBuilder.intercept(...)` can throw `HookFailedError`.
- Module entry classes should extend `XposedModule`.
- `XposedModule` has a no-arg constructor.
- `onPackageReady(PackageReadyParam)` is the right callback when the app classloader is ready.
- `PackageLoadedParam.isFirstPackage()` / `PackageReadyParam.isFirstPackage()` is the correct API name.
- `XposedServiceHelper.registerListener(...)` should only be called once.
- The correct service listener methods are `onServiceBind(XposedService)` and `onServiceDied(XposedService)`.
- The correct scope listener methods are `onScopeRequestApproved(List<String>)` and `onScopeRequestFailed(String)`.
- Official service provider authority suffix is `.XposedService`.
- libxposed `api` should be `compileOnly` for module hook code.

## Verification Performed

Command run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

Result:

```text
BUILD SUCCESSFUL
```

Important note: this command verifies existing unit tests and compilation for `:xposed` and `:app`. It does not prove runtime hook correctness inside LSPosed. The unresolved findings below still require code changes and target-process validation.

## Findings By Severity

### High 1: `WebViewHooker` Mutates Immutable `chain.args`

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt`

Relevant code:

```kotlin
val ua = chain.args.firstOrNull() as? String ?: return@intercept chain.proceed()
if (ua.isNotEmpty() && !ua.contains(model)) {
    chain.args[0] = modifyUserAgent(ua, model)
    reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
}
chain.proceed()
```

Why this is wrong:

libxposed `Chain.getArgs()` returns an immutable list. The official API source says argument changes must be made by calling `proceed(Object[])` or `proceedWith(Object, Object[])` with new argument arrays.

Impact:

- `WebSettings.setUserAgentString(String)` spoofing can fail at runtime.
- Under the default/protective exception mode, this failure may be logged and the original call may proceed, leaving a false impression that WebView UA spoofing is fully active.
- This directly contradicts the project safety rule that hookers should use correct libxposed API 101 patterns.

Recommended fix:

```kotlin
xi.hook(m).intercept { chain ->
    val model = preset?.model ?: return@intercept chain.proceed()
    val ua = chain.args.firstOrNull() as? String ?: return@intercept chain.proceed()
    if (ua.isEmpty() || ua.contains(model)) return@intercept chain.proceed()

    val newArgs = arrayOf<Any?>(modifyUserAgent(ua, model))
    reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
    chain.proceed(newArgs)
}
```

Recommended tests:

- Unit test the argument conversion helper if extracted.
- Add a static safety test that fails on `chain.args[` assignments in `xposed/src/main/kotlin`.
- Runtime smoke: call `WebSettings.setUserAgentString(...)` in a target app and verify the target-observed UA changes.

### High 2: `HookFailedError` / `XposedFrameworkError` Are Swallowed By `Throwable` Catches

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/DeoptimizeManager.kt`

Relevant pattern:

```kotlin
try {
    block()
} catch (t: Throwable) {
    ...
}
```

Why this is wrong:

The official libxposed API declares `HookFailedError` as a framework-level `Error`, not a normal recoverable exception. `HookBuilder.intercept(...)` can throw it. The libxposed reference explicitly treats this class as a fatal framework error that modules should not catch as routine fallback.

Impact:

- Framework hook failures can be logged as ordinary per-method or per-hooker failures.
- The module can continue after a framework-level failure in a partially registered state.
- LSPosed/runtime failures become harder to diagnose because they are hidden behind generic warnings.
- This can create misleading evidence such as "All hooks registered" even if a framework-level hook failure was swallowed inside a hooker.

Recommended fix:

Add explicit rethrow handling anywhere hook registration is wrapped:

```kotlin
import io.github.libxposed.api.error.XposedFrameworkError

protected fun safeHook(methodName: String, block: () -> Unit) {
    try {
        block()
    } catch (e: XposedFrameworkError) {
        throw e
    } catch (t: Throwable) {
        val message = "safeHook($methodName) failed: ${t.javaClass.simpleName}: ${t.message}"
        DualLog.warn(tag, message, t)
        runCatching { XposedEntry.instance.log(android.util.Log.WARN, tag, message, null) }
    }
}
```

For `XposedEntry.hookSafely`, apply the same rule before the generic catch.

System-server code is more delicate. For `onSystemServerStarting` and `SystemServiceHooker`, the bootloop risk is real. Even there, framework `Error` should be separately logged at `ERROR` and carefully considered. If the project chooses not to rethrow inside `system_server` for boot safety, that exception should be explicitly documented as a policy exception, not accidentally caught by a broad `Throwable`.

Recommended tests:

- Add a unit-level helper test if `safeHook` behavior is made testable.
- Add a static test that flags `catch (t: Throwable)` in hook registration code unless immediately preceded by a dedicated `catch (e: XposedFrameworkError)`.

### Medium 3: Per-App Config Is Bound To The First Package/Classloader, Not Always The Actual Calling Package

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`

Relevant code:

```kotlin
if (!param.isFirstPackage) {
    log(Log.DEBUG, TAG, "Skipping secondary package load for process-stable hooks: $pkg", null)
    return
}
...
val classLoaderKey = System.identityHashCode(cl)
if (!hookedClassLoaders.add(classLoaderKey)) {
    log(Log.DEBUG, TAG, "Hooks already registered for classloader of $pkg", null)
    return
}
...
hookSafely(pkg, "DeviceHooker") { DeviceHooker.hook(cl, this, prefs, pkg) }
```

Why this matters:

libxposed can invoke package callbacks for multiple packages in one process. The official API docs warn that modules may receive callbacks beyond the originally scoped packages and should filter by process/package. Device Masker intentionally hooks only the first package per classloader to keep process-global hook state stable. That is a valid stability tradeoff, but it makes the current "per-app" config model imperfect in shared-process or multi-package scenarios.

Impact:

- If two package identities load in the same process/classloader, only the first package's config is captured by hook lambdas.
- A secondary package may receive spoof values for the first package.
- A secondary package's own enablement and spoof values may never be consulted.
- Diagnostics may report hook registration for the first package only, even though hooks affect process-global framework methods.

This is especially relevant for shared UID packages, packages loaded through `Context.createPackageContext(..., CONTEXT_INCLUDE_CODE)`, and app/plugin architectures.

Recommended paths:

1. Document this explicitly as "process-first-package scoped" behavior for now.
2. Rename internal wording from per-app to per-process where hook registration is process-global.
3. Where possible, resolve package identity at call time from context-bearing APIs.
4. Add tests or runtime smoke cases for multi-package/shared-process callbacks if available.
5. Consider a process policy table keyed by package and process name instead of only classloader identity.

### Medium 4: Some Hookers Read RemotePreferences Only At Registration, So They Are Not Fully Live

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SensorHooker.kt`

Relevant patterns:

```kotlin
val presetId = getSpoofValue(prefs, pkg, SpoofType.DEVICE_PROFILE) { "" }
val preset = if (presetId.isNotEmpty()) DeviceProfilePreset.findById(presetId) else null
```

```kotlin
val presetId = getConfiguredSpoofValue(prefs, pkg, SpoofType.DEVICE_PROFILE) ?: return
val preset = DeviceProfilePreset.findById(presetId) ?: return
```

Why this matters:

RemotePreferences can deliver live updates, but only if hook callbacks read current values at call time or register preference listeners and update local state correctly. These hookers snapshot `DEVICE_PROFILE` during registration:

- `SystemHooker` snapshots profile once and mutates static `Build` fields once.
- `WebViewHooker` snapshots profile once and closes over `preset`.
- `SensorHooker` snapshots profile once and closes over `preset`.

Impact:

- UI changes to the device profile may not affect these surfaces until the target app is force-stopped and relaunched.
- This conflicts with comments and UI wording that imply no target restart is needed for all RemotePreferences-backed config.
- Static `Build` field changes are inherently process-start-sensitive.

Recommended fix:

- For callback-returned values, read `getConfiguredSpoofValue(...)` inside the interceptor.
- For expensive profile resolution, consider a small cache invalidated by a RemotePreferences listener.
- For `Build` static fields, explicitly document that profile changes require target restart.
- Update UI wording to distinguish "live for callback-read values" from "restart required for process-start or static-field surfaces."

Suggested classification:

- Live-safe to update during runtime: Telephony getters, Settings.Secure, SystemProperties hooks, PackageManager list filtering, many network getters.
- Restart-likely: static `Build` field mutation, hook registration gating by app enablement, hookers that only register when profile exists.
- Mixed: WebView and Sensor hooks after callback-time lookup is fixed.

### Medium 5: Broad Instance Hooks Spoof All `TimeZone.getID()` And `Locale.toString()` Calls

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt`

Relevant code:

```kotlin
safeHook("TimeZone.getID()") {
    tzClass.methodOrNull("getID")?.let { m ->
        xi.hook(m).intercept { chain ->
            val result = chain.proceed()
            val tzId = getSpoofValue(prefs, pkg, SpoofType.TIMEZONE) { "" }
            if (tzId.isNotBlank()) {
                reportSpoofEvent(pkg, SpoofType.TIMEZONE)
                tzId
            } else {
                result
            }
        }
        xi.deoptimize(m)
    }
}
```

```kotlin
safeHook("Locale.toString()") {
    localeClass.methodOrNull("toString")?.let { m ->
        xi.hook(m).intercept { chain ->
            val result = chain.proceed()
            val localeStr = getSpoofValue(prefs, pkg, SpoofType.LOCALE) { "" }
            if (localeStr.isNotBlank()) {
                reportSpoofEvent(pkg, SpoofType.LOCALE)
                localeStr
            } else {
                result
            }
        }
        xi.deoptimize(m)
    }
}
```

Why this matters:

`TimeZone.getID()` and `Locale.toString()` are instance methods. Hooking them without checking `chain.thisObject` means every `TimeZone` and every `Locale` object can report the spoofed default value.

Impact:

- Apps comparing multiple `Locale` or `TimeZone` objects can observe impossible behavior.
- Date/time formatting libraries can behave incorrectly.
- Fingerprint checks may detect that non-default objects all report the same configured default.

Recommended fix:

- Prefer `TimeZone.getDefault()` and `Locale.getDefault()` hooks only.
- If instance methods remain hooked, gate by `chain.thisObject`:
  - Only spoof when the instance equals the original default object.
  - Or only spoof when the original `result` matches the current default ID/string.
- Add unit tests around helper logic for "spoof only default-like instances."

### Medium 6: `ConfigSync.syncApp()` Ignores `AppConfig.isEnabled`

File:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`

Relevant code:

```kotlin
val group = config.getGroupForApp(packageName)
...
val appEnabled = config.isModuleEnabled && group.isEnabled
```

Why this is wrong:

Full sync correctly includes app-level enablement:

```kotlin
val appEnabled =
    config.isModuleEnabled && appConfig.isEnabled && group?.isEnabled == true
```

`syncApp()` does not include the app-specific `AppConfig.isEnabled` flag. It only checks module and group enabled state.

Impact:

- If `syncApp()` is used in future call paths, a disabled app can be written as enabled in RemotePreferences.
- This violates the project rule that `JsonConfig.appConfigs` is canonical for app assignment and enablement.
- Full sync and single-app sync can disagree.

Recommended fix:

```kotlin
val appConfig = config.getAppConfig(packageName)
val group = config.getGroupForApp(packageName)
if (group == null || appConfig == null) {
    prefs.edit(commit = true) { putBoolean(SharedPrefsKeys.getAppEnabledKey(packageName), false) }
    return
}

val appEnabled = config.isModuleEnabled && appConfig.isEnabled && group.isEnabled
```

Recommended tests:

- Extend `ConfigSyncSnapshotTest` or add a `syncApp`-focused test to verify disabled app configs write `app_enabled_* = false`.

### Medium 7: RemotePreferences Writes Use Async `apply()` And Do Not Surface Commit Failure

Files:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`

Relevant code:

```kotlin
prefs.edit {
    snapshot.removeKeys.forEach { remove(it) }
    snapshot.booleans.forEach { (key, value) -> putBoolean(key, value) }
    snapshot.strings.forEach { (key, value) -> putString(key, value) }
    snapshot.stringSets.forEach { (key, value) -> putStringSet(key, value) }
    snapshot.longs.forEach { (key, value) -> putLong(key, value) }
}
```

Why this matters:

AndroidX `SharedPreferences.edit {}` defaults to `commit = false`, which calls `apply()`.

Official libxposed `RemotePreferences.apply()` updates the app process's local snapshot immediately and writes to the framework asynchronously. If the framework write fails, the local app-side map can still look updated.

Impact:

- UI and app logs can claim "Config synced" before LSPosed framework persistence succeeds.
- Hook-side target processes may not receive the expected values.
- Debugging config delivery becomes harder because app-side reads can be optimistic.

Recommended fix:

- Use `prefs.edit(commit = true)` for config sync paths where correctness matters.
- If commit fails, log a clear warning and expose sync failure in diagnostics.
- Keep `apply()` only for low-risk UI toggles where eventual consistency is acceptable.

Potential implementation:

```kotlin
val ok = prefs.edit(commit = true) {
    ...
}
if (!ok) {
    Timber.tag(TAG).w("RemotePreferences commit failed")
}
```

### Low 8: `XposedPrefs.init()` Claims Multiple Calls Are Safe But Has No Guard

File:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`

Relevant code/comment:

```kotlin
/**
 * Must be called once in `DeviceMaskerApp.onCreate()`. Safe to call multiple times.
 */
fun init() {
    XposedServiceHelper.registerListener(...)
}
```

Why this matters:

Official `XposedServiceHelper.registerListener(...)` has one static listener and should only be called once. Re-registering replaces the listener and can create surprising state behavior.

Current impact:

- Current app calls `XposedPrefs.init()` once from `DeviceMaskerApp.onCreate()`, so this is not currently breaking runtime.

Recommended fix:

- Add an `AtomicBoolean` or synchronized guard around `init()`.
- Or change the comment to remove "Safe to call multiple times."

Suggested implementation:

```kotlin
private val initialized = AtomicBoolean(false)

fun init() {
    if (!initialized.compareAndSet(false, true)) return
    XposedServiceHelper.registerListener(...)
}
```

### Low 9: `SubscriptionManager.getActiveSubscriptionInfoList()` Hook Is A No-Op But Comments Say It Mutates

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt`

Relevant comment:

```kotlin
* - SubscriptionManager.getActiveSubscriptionInfoList() — mutates each listed SubscriptionInfo
```

Relevant code:

```kotlin
xi.hook(m).intercept { chain -> chain.proceed() }
```

Impact:

- No functional harm from the hook as written, but it adds hook overhead with no effect.
- The comment overstates coverage and may cause false confidence in SIM list consistency.

Recommended fix:

- Remove the no-op hook and comment.
- Or implement list-level consistency if needed. Since `SubscriptionInfo` is generally not easy or safe to mutate, getter-level hooks may be the safer current design.

### Low 10: Stale Migration Names And Comments Remain

Files/examples:

- `app/proguard-rules.pro`
- `xposed/consumer-rules.pro`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`

Examples:

- Comments refer to `ModulePreferences` or `ModulePreferencesProvider`, but current libxposed service uses `XposedProvider` and `RemotePreferences`.
- Comments refer to `onSystemServerLoaded`, but the correct libxposed lifecycle callback is `onSystemServerStarting`.
- Some comments refer to `onPackageLoaded` for code now using `onPackageReady`.

Impact:

- Low runtime risk.
- Medium future-maintenance risk, because stale names are exactly the kind of issue that causes libxposed API regressions.

Recommended fix:

- Replace stale comments with current terms:
  - `ModulePreferences` -> `RemotePreferences`
  - `ModulePreferencesProvider` -> `XposedProvider`
  - `onSystemServerLoaded` -> `onSystemServerStarting`
  - `onPackageLoaded` -> `onPackageReady` where applicable
- Remove keep rules for nonexistent `io.github.libxposed.service.ModulePreferencesProvider` if confirmed unused by dependencies.

## Confirmed Good Areas

### Module Entrypoint

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`

Current state:

- `XposedEntry` extends `XposedModule`.
- No custom constructor is declared, so the no-arg constructor requirement is satisfied.
- `onModuleLoaded(ModuleLoadedParam)` is used for process-level initialization/logging.
- `onPackageReady(PackageReadyParam)` is used for app classloader-ready hooks.
- `onSystemServerStarting(SystemServerStartingParam)` is used for system-server setup.

This matches modern libxposed API 101 lifecycle expectations.

### Gradle Dependency Wiring

Files:

- `xposed/build.gradle.kts`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

Current state:

- `:xposed` uses `compileOnly(libs.libxposed.api)`.
- `:app` uses `implementation(libs.libxposed.iface)` and `implementation(libs.libxposed.service)`.
- This is the correct separation:
  - The hook-side API is provided by LSPosed at runtime.
  - The app-side service/interface artifacts are packaged for service IPC and RemotePreferences writes.

### Xposed Metadata

Files:

- `xposed/src/main/resources/META-INF/xposed/java_init.list`
- `xposed/src/main/resources/META-INF/xposed/module.prop`
- `xposed/src/main/resources/META-INF/xposed/scope.list`

Current state:

```text
java_init.list:
com.astrixforge.devicemasker.xposed.XposedEntry

module.prop:
minApiVersion=101
targetApiVersion=101
staticScope=false

scope.list:
android
system
```

This is correct for a dynamic-scope module that still declares default framework/system scope.

### App-Side Xposed Service Listener

File:

- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`

Current state:

- Uses `XposedServiceHelper.OnServiceListener`.
- Implements `onServiceBind(XposedService)`.
- Implements `onServiceDied(XposedService)`.
- Does not use legacy or wrong names such as `onServiceConnected` or `onServiceDisconnected`.

### XposedProvider Authority

File:

- `app/src/main/AndroidManifest.xml`

Current state:

```xml
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="${applicationId}.XposedService"
    android:exported="true"
    tools:ignore="ExportedContentProvider" />
```

This matches the official service source and `IXposedService.AUTHORITY_SUFFIX = ".XposedService"`.

### Shared Preference Key Contract

Files:

- `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsReader.kt`

Current state:

- App and hook side both delegate to `SharedPrefsKeys`.
- No hardcoded active key strings were found in hook callbacks.
- Full config sync clears stale keys for removed apps.

### Legacy API Scan

No active legacy hook API usage was found:

- No `@XposedHooker`.
- No `@BeforeInvocation`.
- No `@AfterInvocation`.
- No `AfterHookCallback`.
- No legacy `beforeHookedMethod` / `afterHookedMethod`.
- No `onServiceConnected` / `onServiceDisconnected`.
- No wrong scope listener names.
- `XC_MethodHook` appears only as a string to hide legacy Xposed detection patterns in `AntiDetectHooker`.

### Anti-Detection Safety Direction

Current safer surfaces:

- Stack trace filtering.
- `/proc/self/maps` filtering through `BufferedReader.readLine()`.
- PackageManager/package list hiding.

Current disabled-by-default surfaces:

- Global `ClassLoader.loadClass`.
- Global `Class.forName`.

This matches the current stability decision in the Memory Bank. Reintroducing class lookup hooks should remain gated by a per-app kill switch and fresh runtime validation.

## Additional Technical Observations

### Direct `Build` Field Mutation Is A Stability And Coherence Tradeoff

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`

Relevant code:

```kotlin
val f: Field = buildClass.getDeclaredField(fieldName)
f.isAccessible = true
f.set(null, value)
```

This is not a libxposed API misuse, but it has inherent limitations:

- It is process-global inside the target process.
- It may not affect values already constant-folded or cached by app code.
- It is not live after profile changes.
- It can affect code paths beyond a single app identity in shared processes.

Recommended documentation:

- Mark Build field spoofing as "best effort; requires target restart after profile changes."
- Prefer SystemProperties hooks for read-time behavior where possible.

### Diagnostics Service Uses System Service Registration, But Target Processes No Longer Discover It

Files:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ServiceClient.kt`

Current design:

- `SystemServiceHooker` registers `DeviceMaskerService` in system server under `user.devicemasker_diag`.
- App-side `ServiceClient` uses `ServiceManager` to read diagnostics.
- Target app hook path does not use `ServiceManager`; `XposedEntry.getService()` only uses local initialized singleton when present and otherwise returns null.

This matches the current project rule that target processes must not discover the custom service. The stale comment in `SystemServiceHooker` saying "for discovery by hooked app processes" should be corrected to "for discovery by the module app diagnostics client."

### `getSpoofValue(...)` Compatibility Wrapper Is Still Used In Callbacks

File:

- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsReader.kt`

Comment says hook callbacks should prefer `getStoredSpoofValue` and return original values when it returns null. Some callbacks still use `getSpoofValue(...)` with original-derived fallback, for example `LocationHooker`. This is not inherently wrong because the fallback is original/current value, not random generation. It is still worth tightening for clarity:

- Use `getConfiguredSpoofValue(...) ?: return@intercept result` where possible.
- Reserve `getSpoofValue(...)` only for non-callback helpers or where fallback-to-original string conversion is explicitly needed.

## Recommended Remediation Order

### Phase 1: Correct libxposed API Contract Violations

1. Fix `WebViewHooker` to use `chain.proceed(Object[])`.
2. Add a static regression test banning `chain.args[` mutation under `xposed/src/main/kotlin`.
3. Update `safeHook` / `hookSafely` to avoid swallowing `XposedFrameworkError`.
4. Add a static or unit test to prevent future broad `Throwable` catches around hook registration without an `XposedFrameworkError` rethrow path.

Verification:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
```

Runtime smoke:

- Launch `com.mantle.verify`.
- Confirm LSPosed logs still show `XposedEntry loaded`, `Anti-detection hooks registered`, and `All hooks registered`.
- Exercise WebView UA reads and writes if a target path exists.

### Phase 2: Fix Config Correctness And Sync Semantics

1. Fix `ConfigSync.syncApp()` to respect `AppConfig.isEnabled`.
2. Use `edit(commit = true)` for full RemotePreferences config sync or explicitly report async commit failure risk.
3. Add tests for disabled app single-app sync behavior.
4. Update UI/docs to distinguish app-side service connection from target hook registration and target-observed spoof events.

Verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

### Phase 3: Make Live Update Semantics Honest

1. Move `DEVICE_PROFILE` reads inside callbacks for WebView/Sensor where practical.
2. Document static `Build` field spoofing as restart-bound.
3. Update diagnostics copy and docs to avoid claiming all config changes are immediately live.

Verification:

- Change profile in app.
- Do not restart target.
- Confirm which values change live and which require force-stop/relaunch.

### Phase 4: Reduce Broad Instance Hook Behavior

1. Restrict or remove `TimeZone.getID()` spoofing.
2. Restrict or remove `Locale.toString()` spoofing.
3. Add helper tests for default-only spoofing.

Runtime smoke:

- In a target app, compare multiple constructed `Locale` / `TimeZone` instances and default values.
- Confirm only default-like values are spoofed.

### Phase 5: Documentation And Cleanup

1. Replace stale `ModulePreferences` / `ModulePreferencesProvider` comments with `RemotePreferences` / `XposedProvider`.
2. Replace stale `onSystemServerLoaded` and `onPackageLoaded` comments where the code uses `onSystemServerStarting` and `onPackageReady`.
3. Remove the no-op subscription list hook or implement it.
4. Add explicit documentation for process-first-package behavior.

## Suggested New Tests

### Static Safety Tests

- No `chain.args[` in `xposed/src/main/kotlin`.
- No legacy hook APIs:
  - `@XposedHooker`
  - `@BeforeInvocation`
  - `@AfterInvocation`
  - `AfterHookCallback`
  - `beforeHookedMethod`
  - `afterHookedMethod`
- No `Timber.` in `xposed/src`.
- No `Class.forName("android.os.ServiceManager")` in `XposedEntry`.
- No `catch (t: Throwable)` in hook registration helpers unless `XposedFrameworkError` is handled first.

### Unit Tests

- `WebViewHooker.modifyUserAgent(...)` existing tests should remain.
- Add a test around any extracted `buildSetUserAgentArgs(...)` helper.
- `ConfigSync.syncApp()` should preserve disabled app state.
- Locale/timezone helper should spoof only default-like instances.
- `XposedPrefs.init()` should register only once if guarded.

### Runtime Tests

- `com.mantle.verify` reboot/module-toggle smoke.
- At least two additional target apps.
- Disabled app config should pass through originals.
- Disabled spoof type should pass through originals.
- Missing/blank/malformed value should pass through originals.
- WebView UA setter/getter should return expected profile model.
- Package visibility hiding should be verified on API 33+ overloads.
- LSPosed logs should be exported and checked for target-process hook registration and spoof events.

## Current Risk Assessment

| Area | Risk | Notes |
| --- | --- | --- |
| libxposed lifecycle | Low | Entrypoint and callbacks are modern API 101 aligned. |
| Hook API syntax | Medium | Mostly correct, but `chain.args` mutation is a real API contract bug. |
| Framework error handling | High | `Throwable` catches can hide `HookFailedError`. |
| Config delivery | Medium | Architecture is right; sync failure and `syncApp` mismatch need fixes. |
| Runtime stability | Medium | Current working base is fragile but improved; avoid broad refactors. |
| Per-app semantics | Medium | Shared-process behavior is not truly per-app. |
| Live update claims | Medium | Some hooks are registration-time snapshots. |
| Anti-detection | Medium | Safer surfaces are active; class lookup hooks are correctly disabled by default. |
| Documentation drift | Low | Several stale migration names remain. |

## Final Assessment

Device Masker's libxposed migration is structurally sound. The code avoids the common modern API mistakes that often break API 101 modules: it does not use legacy hook annotations, it uses the correct service callbacks, it packages Xposed metadata, and it keeps `api` as `compileOnly` in `:xposed`.

The remaining problems are more subtle and worth fixing before expanding runtime validation:

- Fix the immutable `chain.args` misuse first.
- Stop swallowing libxposed framework `Error`s.
- Correct `syncApp()` app-level enablement.
- Make RemotePreferences write success visible.
- Clarify process-first-package and restart-bound behavior.
- Narrow broad instance hooks.

These changes should be made surgically. The current `com.mantle.verify` working base is valuable and should be protected with targeted tests plus LSPosed runtime smoke after each high-risk hook change.
