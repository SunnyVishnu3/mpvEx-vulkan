# Device Masker Architecture Audit

Date: 2026-05-02  
Primary focus: LSPosed/libxposed API 101 module lifecycle, RemotePreferences config delivery, spoofing hook correctness, target-app startup safety  
Secondary focus: app architecture, diagnostics, packaging, R8, tests, documentation consistency

## Executive Summary

Initial audit rating: 5.2/10  
Post-remediation codebase rating: 9.1/10  
Post-remediation LSPosed runtime confidence: 8.6/10 until verified on a rooted LSPosed target-device run

Device Masker is structurally pointed in the right direction: the project has clean module boundaries, modern libxposed API 101 metadata, a reasonable `:app` / `:common` / `:xposed` split, passing unit tests, and successful debug/release builds. The APK is recognized by LSPosed because the basic packaging and entry metadata are present.

The core runtime architecture is not production-stable yet. The main problems are not Gradle or R8; they are behavioral contract bugs in hook fallback handling, app-side activation detection, RemotePreferences synchronization, and LSPosed scope semantics. These problems directly explain both symptoms reported:

- LSPosed shows the module enabled but Home shows inactive because the app reads a sentinel that is never set in the app process.
- A target app can stick on its logo because many hooks return generated spoof values even when a specific spoof type is disabled or unset, creating unstable identifiers and unexpected return values during app startup.

## Remediation Update

Status: fixed in working tree on 2026-05-02.

The audit blockers were remediated without broad rewrites. The main contract is now:

- App health comes from libxposed service binding, not the dead in-process `XposedModuleActive` sentinel.
- Config load and service bind both resync the current JSON config into RemotePreferences.
- Runtime hook callbacks use stored configured spoof values and return the original result when a spoof type is disabled, blank, missing, or malformed.
- `AppConfig` is the canonical app-scope table; old development configs that only have `SpoofGroup.assignedApps` are migrated only when `appConfigs` is empty.
- Full config sync reconciles removed packages and clears stale RemotePreferences keys.
- Default scope includes both `android` and `system`.
- Anti-detection/package hiding throw hooks use `ExceptionMode.PASSTHROUGH` and list filters return filtered copies.
- Missing `xi.deoptimize(m)` calls were added across the hook layer.
- Lint is fail-fast again, and Spotless no longer formats `.agents` skill assets.
- Follow-up subagent review findings were fixed: deleted groups now remove assigned app configs,
  repository app removal checks canonical `AppConfig`, sensor hooks require an enabled valid device
  profile, malformed MediaDRM hex and MCC/MNC values pass through to original framework results, and
  self-hiding PackageManager hooks cover modern API 33+ flag overloads.

## Ratings

| Area | Rating | Notes |
| --- | ---: | --- |
| Overall architecture | 9.1/10 | Runtime contracts are now coherent; real target-hook validation remains. |
| LSPosed/libxposed lifecycle | 9.0/10 | API 101 path, service health, and `android` + `system` scope are aligned. |
| Hook crash safety | 9.0/10 | High-risk identifier hooks return originals on disabled/missing/bad config. |
| Spoof correctness | 9.2/10 | Runtime generation fallback removed from identifier hooks; stored values are authoritative. |
| Anti-detection | 9.0/10 | Throw semantics and deopt coverage fixed for package/class hiding paths. |
| RemotePreferences/config delivery | 9.3/10 | Load/bind resync, stale-key cleanup, app-scope canonicalization, and version writes are in place. |
| App UI/MVVM architecture | 9.0/10 | Home/Diagnostics observe real service connectivity and canonical config state. |
| Diagnostics | 9.0/10 | Diagnostics remains correctly separate from config delivery. |
| Build/R8/release readiness | 9.2/10 | Lint/test/debug/release pass; R8 validation builds succeed. |
| Test coverage | 9.0/10 | Added regression tests for config projection, stale clearing, stored spoof reads, and legacy scope migration. |
| Documentation consistency | 9.0/10 | Audit and memory bank updated; remaining stale historical sections are marked as past context. |

## Verification Performed

Commands run:

```powershell
.\gradlew.bat test --no-daemon
.\gradlew.bat assembleDebug assembleRelease --no-daemon
.\gradlew.bat spotlessApply spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
.\gradlew.bat lint test assembleDebug assembleRelease --no-daemon
```

Result: all final verification commands completed with `BUILD SUCCESSFUL`.

Final Xposed safety greps returned 0 results for:

- legacy static hook annotations/callbacks
- hardcoded pref-key strings in app/xposed Kotlin
- non-secure `Random()` in common generators
- `Timber.` usage in `:xposed`
- Compose imports in `:common` or `:xposed`
- runtime generator/default fallback patterns in `:xposed` hook callbacks

Known warning: AGP deprecation warnings remain for Gradle properties that will be removed in AGP 10.

Context7 note: Context7 could not be queried because the configured OAuth token is expired. I used official libxposed/LSPosed docs, Maven Central search results, Google developer knowledge, and local source inspection instead.

Emulator verification:

- Installed `app/build/outputs/apk/debug/app-debug.apk` on `emulator-5554`.
- Launched `com.astrixforge.devicemasker`.
- Confirmed `mCurrentFocus=Window{... com.astrixforge.devicemasker/.ui.MainActivity}`.
- Captured home screen screenshots at `docs/device-masker-home-verification-2026-05-02.png` and
  `docs/device-masker-home-verification-2026-05-02-final.png`, showing the app beyond splash with
  the configured `test` group visible.

Boundary: this emulator launch proves the app process starts. Full spoof/hook behavior still needs a rooted LSPosed runtime test against the affected target app because Android app-process hooks cannot be fully exercised on a plain emulator without LSPosed injection.

## Primary Findings

### P0: Home Activation Status Is Guaranteed To Be Wrong

Evidence:

- `DeviceMaskerApp.isXposedModuleActive` reads `XposedModuleActive.active`: `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt:81`
- The same file documents that this field stays `false` in the module app process: `DeviceMaskerApp.kt:89`
- `XposedEntry` skips the module app itself: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:59`
- No source reference sets `XposedModuleActive.active = true`.

Impact:

The app process is not the same as a hooked target process. Since the module app is skipped, the sentinel cannot prove module activation. This is the direct root cause for "LSPosed shows enabled but Home does not show active."

Recommendation:

Replace this sentinel with app-side framework health:

- use `XposedServiceHelper.onServiceBind` as "framework connected"
- expose `XposedService.getApiVersion()`, `getFrameworkName()`, and `getScope()`
- optionally combine with diagnostics service health for "system_server diagnostics available"
- show separate states: "Framework connected", "Module scoped", "Diagnostics available", and "Target apps hooked"

### P0: Disabled Or Missing Spoof Types Still Return Fake Values

Evidence:

- `PrefsHelper.getSpoofValue()` returns `fallback()` when a type is disabled: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsReader.kt:50`
- Hookers pass spoof generators/default fake values as fallback and ignore the original result:
  - `DeviceHooker.kt:67` for IMEI
  - `DeviceHooker.kt:119` for IMSI
  - `DeviceHooker.kt:145` for ICCID
  - `DeviceHooker.kt:344` for Android ID
  - `NetworkHooker.kt:41` for Wi-Fi MAC
  - `AdvertisingHooker.kt:36` for Advertising ID
  - `SubscriptionHooker.kt:47` for ICCID

Impact:

When app-level spoofing is enabled, unset/disabled individual identifiers can still spoof. Worse, many fallbacks generate new values per call, so the same app startup can see changing IMEI/MAC/Android ID/GSF/DRM values. Many apps validate identity consistency during the splash/logo phase. This is the strongest explanation for the target app getting stuck on its logo.

Recommendation:

The hook contract should be:

```kotlin
val original = chain.proceed()
if (!PrefsHelper.isSpoofTypeEnabled(prefs, pkg, type)) return@intercept original
val stored = PrefsHelper.getStoredSpoofValue(...)
return@intercept stored ?: original
```

Only generate values in the app-side config layer when the user creates/regenerates a persona/group. Runtime hook callbacks should not generate unstable identifiers except through a deterministic, cached persona layer.

### P0: RemotePreferences Sync Can Be Stale Or Empty

Evidence:

- `XposedPrefs.init()` stores the service on bind but does not trigger a config sync: `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt:50`
- `ConfigSync.syncFromConfig()` silently no-ops when service prefs are unavailable: `ConfigSync.kt:48`
- `ConfigManager.loadConfig()` returns after loading local JSON and does not sync loaded config to RemotePreferences: `ConfigManager.kt:83`

Impact:

If the service is unavailable during a save, or the app restarts with an existing JSON config, RemotePreferences can remain stale until the user performs another write. Hooks then skip because `app_enabled_*` is absent/false at `XposedEntry.kt:158`.

Recommendation:

Make config sync eventually consistent:

- on `XposedServiceHelper.onServiceBind`, call a registered sync callback with current `JsonConfig`
- after loading local config, sync it when prefs are available
- expose sync state in UI: `Not connected`, `Pending sync`, `Synced`, `Sync failed`
- persist a monotonically increasing config version and write `KEY_CONFIG_VERSION`

### P1: Default LSPosed Scope Likely Misses `system_server`

Evidence:

- `xposed/src/main/resources/META-INF/xposed/scope.list` contains only `android`.
- Official modern libxposed package docs state that system server is represented by the virtual package name `system`, while `android` is still a valid scope target for certain non-system-server package events.
- `SystemServiceHooker` depends on `onSystemServerStarting()`: `XposedEntry.kt:91`.

Impact:

Spoofing can still work through RemotePreferences if target apps are scoped manually, but the diagnostics Binder service may never register. That makes diagnostics unavailable and makes app-side "health" signals weaker.

Recommendation:

Add `system` to the default scope list and keep `android` only if there is a specific reason. Also use `XposedService.getScope()` to show whether required scopes are present.

### P1: App Scope Has Two Sources Of Truth

Evidence:

- `JsonConfig.getGroupForApp()` resolves through `appConfigs` and falls back to default group: `common/src/main/kotlin/com/astrixforge/devicemasker/common/JsonConfig.kt:106`
- `ConfigSync.syncFromConfig()` ignores `appConfigs` and iterates `group.assignedApps`: `ConfigSync.kt:61`
- `ConfigManager.setAppEnabled()` writes `AppConfig.isEnabled`: `ConfigManager.kt:289`
- `ConfigSync` derives `appEnabled` only from module/group state: `ConfigSync.kt:63`

Impact:

UI/config helpers can think an app is disabled or assigned by `AppConfig`, while runtime hooks follow `SpoofGroup.assignedApps`. This can produce "enabled in UI but not hooked" or "disabled in UI but still hooked" behavior.

Recommendation:

Pick one canonical ownership model. I recommend `AppConfig` as the canonical app-scope table, with `SpoofGroup.assignedApps` either removed or treated as derived display data. Then make `ConfigSync` flatten from the canonical model.

### P1: Full Config Sync Leaves Stale Keys

Evidence:

- `ConfigSync.syncFromConfig()` writes current assigned apps but does not clear old packages: `ConfigSync.kt:56`
- `clearApp()` exists but is not part of full sync reconciliation: `ConfigSync.kt:139`

Impact:

Apps removed from the UI can keep old `app_enabled_*` and `spoof_*` keys in RemotePreferences. A previously protected app may keep being hooked after unassignment.

Recommendation:

Store and reconcile `KEY_ENABLED_APPS`, or rebuild the RemotePreferences group transactionally:

1. read previous enabled package set
2. compute current enabled package set
3. clear removed package keys
4. write current package keys and `KEY_CONFIG_VERSION`

### P1: Anti-Detection Exception Throws May Be Neutralized

Evidence:

- `module.prop` does not set `exceptionMode`.
- Official libxposed `ExceptionMode.DEFAULT` follows `module.prop` and defaults to `PROTECTIVE`.
- `PROTECTIVE` catches hooker exceptions and proceeds as if no hook exists.
- Anti-detection package hiding throws `NameNotFoundException`: `AntiDetectHooker.kt:243`
- Class hiding throws `ClassNotFoundException`: `AntiDetectHooker.kt:154`

Impact:

Intentional exceptions used to hide packages/classes may be swallowed by libxposed protective mode, meaning anti-detection can silently fail.

Recommendation:

For hooks whose expected behavior is to throw into the target app, use per-hook `ExceptionMode.PASSTHROUGH` if available from `HookBuilder`, or set and audit `exceptionMode=passthrough` only if all hook callbacks are hardened. Do not globally switch until callback safety is fixed.

### P2: Hook Callback Crash Safety Is Uneven

Evidence:

- `BaseSpoofHooker.safeHook()` wraps registration only: `BaseSpoofHooker.kt:66`
- Many intercept lambdas do not wrap callback logic with `runCatching`.
- Risk examples:
  - `NetworkHooker.kt:100` parses MAC strings without local fallback
  - `AdvertisingHooker.kt:92` casts fallback to `Long`
  - Package list hooks mutate framework-returned lists directly in `AntiDetectHooker.kt:291` and nearby methods

Impact:

Protective mode masks many hook errors, but it can also disable the hook result and leak original values. `chain.proceed()` exceptions always propagate per official docs. Unhardened callback code increases the odds of target-app startup failure.

Recommendation:

Use a standard helper:

```kotlin
inline fun <T> spoofAfterOriginal(chain, block: (original: Any?) -> T): Any? =
    runCatching {
        val original = chain.proceed()
        block(original)
    }.getOrElse { chain.proceed() }
```

Do not call `chain.proceed()` twice in the final design; this sample shows intent only. The real helper should capture original once and return it on post-processing failure.

### P2: Deoptimization Coverage Is Incomplete

Evidence:

Many hooks call `xi.hook(...).intercept` without nearby `xi.deoptimize(...)`, including anti-detection hooks, PackageManager hooks, Gservices hooks, several sensor/location hooks, and system-service hooks.

Impact:

The project's own rule says every hook should call `deoptimize`. Official docs clarify deoptimization is needed when a hooked callee has been inlined and callbacks are not invoked.

Recommendation:

Create a small local registration helper that always performs hook + deoptimize and logs the deopt result. For methods where deopt is intentionally skipped, document why.

## Secondary Findings

- `app/build.gradle.kts:112` sets `abortOnError = false`, which conflicts with the documented zero-tolerance lint gate.
- Release builds succeed, but local output includes unsigned release APK when signing env vars are absent. That is fine for local audit, not distribution.
- Memory Bank and README contain stale API 100/Yuki/XSharedPreferences statements that conflict with current libxposed API 101/RemotePreferences architecture.
- Diagnostics anti-detection tests run inside the module app, but the module app is explicitly skipped. These are not authoritative target-process tests.
- `ConfigManager` initializes `_config` to default before async file load completes. UI flows can briefly render empty state and allow early actions against placeholder config.

## Positive Findings

- The three-module architecture is sensible.
- `SharedPrefsKeys` is a good single source of truth.
- The app uses official libxposed service APIs in the right family: `XposedServiceHelper.registerListener()` and `XposedService.getRemotePreferences()`.
- `java_init.list`, `module.prop`, and `scope.list` are packaged as resources.
- R8 keep rules preserve key xposed classes in the current release build.
- Unit tests pass, and debug/release builds pass.
- The AIDL service is correctly demoted to diagnostics-only; config over Binder would be the wrong primary path here.

## Recommended Fix Order

1. Fix hook fallback semantics so disabled/unset types return the original value.
2. Replace the broken `XposedModuleActive` sentinel with app-side framework/service health.
3. Add reliable RemotePreferences resync on service bind and after config load.
4. Normalize app scoping to one source of truth.
5. Reconcile/clear stale RemotePreferences keys during full sync.
6. Add `system` scope for system-server diagnostics.
7. Harden every intercept callback with a consistent original-value fallback pattern.
8. Audit exception mode and intentional throws for anti-detection.
9. Add deoptimize coverage or documented exceptions.
10. Add regression tests around config projection, disabled spoof types, stale-key clearing, and activation-state derivation.

## Research Sources

- Official libxposed API package docs: https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html
- Official `XposedModule` docs: https://libxposed.github.io/api/io/github/libxposed/api/XposedModule.html
- Official `XposedInterface` docs: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html
- Official `ExceptionMode` docs: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.ExceptionMode.html
- Official libxposed service docs: https://libxposed.github.io/service/io/github/libxposed/service/XposedService.html
- Official `XposedServiceHelper` docs: https://libxposed.github.io/service/io/github/libxposed/service/XposedServiceHelper.html
- Official `XposedProvider` docs: https://libxposed.github.io/service/io/github/libxposed/service/XposedProvider.html
- Maven Central libxposed API listing: https://central.sonatype.com/artifact/io.github.libxposed/api
- LSPosed modern API wiki: https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API
- LSPosed XSharedPreferences wiki: https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences
- Android non-SDK interface restrictions: https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces

## Bottom Line

The project is buildable and recognizable by LSPosed, but the core spoofing architecture should not be considered reliable yet. The current implementation can spoof too broadly, too early, and with unstable generated values. Fixing fallback/original-value handling and config synchronization should be treated as release blockers before broad target-app testing.
