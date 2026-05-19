# Technical Context: Device Masker

## Stack

| Area | Current |
| --- | --- |
| Language | Kotlin 2.3.21 |
| Android | compile SDK 37, target SDK 36, min SDK 26 |
| Build | Android Gradle Plugin 9.2.1, Gradle wrapper 9.5.0 |
| Java/Kotlin toolchain | JVM 17 for Android modules |
| UI | Jetpack Compose BOM 2026.05.00 |
| Material | Material 3 1.5.0-alpha19 |
| Navigation | Navigation 3 runtime/ui 1.1.1, lifecycle-viewmodel-navigation3 2.10.0 |
| Adaptive navigation | material3-adaptive-navigation3 1.3.0-beta01 |
| Lifecycle | Lifecycle 2.10.0 |
| Hooking | libxposed API 101.0.1 |
| App-side Xposed service | libxposed service/interface 101.0.0 |
| Config bridge | libxposed RemotePreferences |
| Local config | JSON in app `filesDir` |
| IPC | No custom AIDL/Binder path; app-side libxposed service is used for RemotePreferences |
| Serialization | kotlinx.serialization JSON 1.11.0 |
| Coroutines | kotlinx.coroutines 1.10.2 |
| Logging | Timber structured JSONL in `:app`, DualLog/XposedModule structured sink in `:xposed`, root copied LSPosed logs, user-started live logcat monitor |
| Root collection | libsu core 6.0.0 for startup root grant, boot/startup capture, and single root/logcat support export |
| Image loading | Coil Compose 3.4.0 |
| Static analysis | Detekt 2.0.0-alpha.3 with Compose rules 0.5.8 |
| App version | `VERSION_NAME=0.1.1`, `VERSION_CODE=2` in `gradle.properties` |

## Modules

| Module | Role |
| --- | --- |
| `:app` | UI, app state, local config, RemotePreferences writes, rootless logs, diagnostics views |
| `:common` | Shared models, generators, key builders, config contracts |
| `:xposed` | libxposed entry point, target-process hooks, anti-detection, LSPosed/logcat diagnostics |
| `:verifier` | Local validation target app that reads framework identity surfaces and writes machine-readable evidence |

## Xposed Metadata

Files:
- `xposed/src/main/resources/META-INF/xposed/java_init.list`
- `xposed/src/main/resources/META-INF/xposed/module.prop`
- `xposed/src/main/resources/META-INF/xposed/scope.list`

Current expectations:
- Entry point: `com.astrixforge.devicemasker.xposed.XposedEntry`
- `minApiVersion=101`
- `targetApiVersion=101`
- `staticScope=false`
- Default scope includes `android` and `system`
- Target app hook selection requires the current enabled-app allowlist plus the per-package enabled key.

## Important Files

| File | Role |
| --- | --- |
| `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt` | App initialization and wiring |
| `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt` | App-side libxposed service binding and RemotePreferences access |
| `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt` | Flattens config into RemotePreferences |
| `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt` | Derives explicit runtime app/group sync state and hook-family policy from canonical config |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt` | JSON config persistence and state |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt` | Rootless app log storage |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt` | Support bundle export bridge |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/JsonlDiagnosticStore.kt` | Rotating diagnostic JSONL store |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt` | Local support bundle ZIP builder |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt` | Opt-in root maximum artifact collector and command manifest writer |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/LsposedLogCopyCollector.kt` | Root-only copier for known LSPosed persisted log files |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootAccessManager.kt` | Central root grant state and startup root request |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCaptureService.kt` | Foreground service for bounded root startup/boot capture |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LiveLogCaptureService.kt` | User-started foreground service for live root logcat monitor |
| `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/BootCaptureReceiver.kt` | Starts root capture after `BOOT_COMPLETED` when Android allows it |
| `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt` | UI-facing config repository |
| `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/AppScopeRepository.kt` | Installed-app metadata repository plus scoped metadata fast path for Home |
| `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/IAppScopeRepository.kt` | App scope repository contract used by ViewModels/tests |
| `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/AppIconCache.kt` | Bounded app icon cache for Home scoped rows and app lists |
| `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/NavDestination.kt` | Navigation 3 `NavKey` destination model |
| `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt` | App-owned Navigation 3 top-level stacks and navigator |
| `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerDeepLinks.kt` | Navigation 3 deep-link URI parsing and synthetic stack definitions |
| `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerViewModelFactories.kt` | Manual factories that create lifecycle `SavedStateHandle` instances for production screens |
| `common/src/main/kotlin/com/astrixforge/devicemasker/common/JsonConfig.kt` | Root config model and migration helpers |
| `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt` | Preference key single source of truth |
| `common/src/main/kotlin/com/astrixforge/devicemasker/common/util/Luhn.kt` | Shared IMEI/ICCID check-digit implementation |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt` | libxposed module entry |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookConfigSnapshot.kt` | Per-package value/persona snapshot used by hot hook callbacks |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt` | Hook-side preference helper/reader |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt` | Shared hook utilities |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt` | R8-safe libxposed `XposedInterface.Hooker` adapter for runtime hook callbacks |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt` | Safer anti-detection hooks |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookFamilyPolicy.kt` | Per-app hook-family isolation from RemotePreferences |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsHooker.kt` | Path-aware Java maps/smaps filtering |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/ProcMapsPolicy.kt` | Per-app proc-maps byte/NIO policy reader |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt` | Defensive WebView UA hook |
| `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHooker.kt` | Device-profile PackageManager feature hooks |
| `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/VerifierActivity.kt` | Local target app evidence reader |
| `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/ProcMapsProbe.kt` | Verifier proc-maps Java reader/byte/RAF probe |
| `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/PackageVisibilityProbe.kt` | Verifier PackageManager API 33+ visibility probe |
| `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/CrashProbe.kt` | Verifier runtime/build facts for crash comparison |
| `scripts/collect-a16-crash-evidence.ps1` | ADB evidence capture for Android 16 target app crashes |
| `scripts/verify-16kb-page-support.ps1` | APK zipalign and packaged `.so` 16 KB page-size check |
| `app/src/test/kotlin/com/astrixforge/devicemasker/MainDispatcherRule.kt` | Test coroutine dispatcher rule |
| `app/src/test/kotlin/com/astrixforge/devicemasker/testing/*.kt` | Fake implementations for testing |
| `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/*/*ViewModelTest.kt` | ViewModel unit tests |
| `docs/reports/IMPLEMENTATION_COMPLETION_SUMMARY_2026-05-04.md` | Plan completion summary |

Latest Android 16 verifier evidence:
- `logs/device/2026-05-10-config-after-latlong-enabled.json`
- `logs/device/2026-05-10-verifier-matrix-latlong-enabled-latest.json`
- `logs/device/2026-05-10-verifier-matrix-latlong-enabled-logcat.txt`
- `docs/internal/reports/closed/validation/2026-05-10/2026-05-10-verifier-android-16-full-summary.md`
- `logs/device/2026-05-11-final-all-emulator-latest.json`
- `logs/device/2026-05-11-final-all-emulator-logcat.txt`
- `logs/build/2026-05-11-final-emulator-stability-gate.txt`
- `docs/internal/reports/closed/validation/2026-05-11/2026-05-11-android-16-emulator-stability-summary.md`
- `docs/public/validation/DEVICE_MASKER_VALIDATION_STATUS.md`
- `docs/public/validation/evidence/emulator/android-16/latest.json`
- `docs/public/validation/evidence/emulator/android-16/logcat.txt`
- `docs/public/validation/evidence/emulator/android-16/build-gate.txt`
- `docs/public/validation/evidence/emulator/android-16/config.json`

## Build Commands

Release prep:

```powershell
git tag v0.1.1
git push origin v0.1.1
```

Then run the manual `Manual Release` workflow with `tag_name=v0.1.1`.

Primary gate:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Targeted gates:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --no-daemon
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :common:testDebugUnitTest --no-daemon
.\gradlew.bat detekt --no-daemon
.\gradlew.bat detektBaseline --no-daemon
.\gradlew.bat assembleDebug --no-daemon
.\gradlew.bat :verifier:assembleDebug --no-daemon
```

Detekt strictness notes:
- Detekt currently runs with `allRules=true`.
- Per-module baselines are still present: `app/detekt-baseline.xml`, `common/detekt-baseline.xml`, and `xposed/detekt-baseline.xml`.
- Latest known baseline count after safe cleanup: 0 entries across `:app`, `:common`, and `:xposed`.
- Current empty-baseline verification passed with separate `detektBaseline` and `detekt` runs on 2026-05-08.
- Do not run `detektBaseline` and `detekt` in the same Gradle invocation; Gradle 9.5 can report implicit baseline file input/output ordering problems. Run them as separate commands.

Navigation 3 targeted gate:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
.\gradlew.bat spotlessCheck :app:testDebugUnitTest lint assembleDebug --no-daemon
adb shell am start -W -a android.intent.action.VIEW -d "devicemasker://open/diagnostics" com.astrixforge.devicemasker
```

16 KB page-size verification:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-16kb-page-support.ps1 app\build\outputs\apk\debug\app-debug.apk
powershell -ExecutionPolicy Bypass -File scripts\verify-16kb-page-support.ps1 app\build\outputs\apk\release\app-release-unsigned.apk
powershell -ExecutionPolicy Bypass -File scripts\verify-16kb-page-support.ps1 app\build\outputs\apk\ciRelease\app-ciRelease-unsigned.apk
```

Android 16 crash evidence capture:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\collect-a16-crash-evidence.ps1 -TargetPackage flar2.devcheck -OutputDir logs/device
```

Current Android 16 emulator evidence capture used:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\collect-a16-crash-evidence.ps1 -Device emulator-5554 -TargetPackage flar2.devcheck -OutputDir logs/device
```

For local R8 smoke when production signing is unavailable, the unsigned `ciRelease` APK may be signed to `logs/tmp/app-ciRelease-debugkey-signed.apk` with the Android debug key and installed only as emulator evidence. Do not treat that artifact as production release signing evidence.

Install debug APK:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Target smoke launch:

```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
adb shell pidof com.mantle.verify
adb logcat -d -t 1200
```

## Static Safety Greps

These should return no matches unless intentionally updated:

```powershell
Get-ChildItem -Path xposed/src -Recurse -Filter '*.kt' | Select-String '@XposedHooker|@BeforeInvocation|@AfterInvocation|AfterHookCallback'
Get-ChildItem -Path app/src,xposed/src -Recurse -Filter '*.kt' | Select-String '"module_enabled"|"app_enabled_"|"spoof_value_"|"spoof_enabled_"'
Get-ChildItem -Path common/src -Recurse -Filter '*.kt' | Select-String 'Random\(\)' | Where-Object { $_ -notmatch 'SecureRandom' }
Get-ChildItem -Path xposed/src -Recurse -Filter '*.kt' | Select-String 'Timber\.'
Get-ChildItem -Path common/src,xposed/src -Recurse -Filter '*.kt' | Select-String 'import androidx.compose'
Get-ChildItem -Path xposed/src/main/kotlin -Recurse -Filter '*.kt' | Select-String 'IMEIGenerator|IMSIGenerator|ICCIDGenerator|MACGenerator|UUIDGenerator|PhoneNumberGenerator|SerialGenerator|\{ "(us|Carrier|310260|HomeNetwork)" \}|ByteArray\(32\)|\?: 310|\?: 260'
Get-ChildItem -Path xposed/src/main/kotlin -Recurse -Filter '*.kt' | Select-String '\(\?<=|UA_DEVICE_REGEX|hookClassForName\(cl, xi\)|hookClassLoaderLoadClass\(cl, xi\)'
Get-ChildItem -Path xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker -Filter '*.kt' | Where-Object { $_.Name -ne 'BaseSpoofHooker.kt' } | Select-String '\.intercept\s*\{'
rg -n "HiddenApiBypass|org\.lsposed\.hiddenapibypass|Timber\.|Random\(" xposed/src/main/kotlin app/build.gradle.kts xposed/build.gradle.kts gradle/libs.versions.toml
rg -n "DexKit|frida|bytehook|shadowhook|xhook|Dobby" app/src/main common/src/main xposed/src/main gradle
```

## Runtime Requirements

Runtime validation needs:
- Rooted device or emulator.
- LSPosed with libxposed API 101 support.
- Device Masker enabled as an LSPosed module.
- Scope includes `android`, `system`, and the selected target app.
- Target app force-stopped after scope or module changes.

## Known Technical Warnings

- `material3-adaptive-navigation3` is on `1.3.0-beta01`, which required moving the project to compile SDK 37.
- Release shrinking is enabled and was validated on emulator targets plus user-reported real
  Android 16 hardware. Do not bypass `StableHooker` for libxposed runtime callbacks.
- App/common/xposed/verifier lint reports are clean as of 2026-05-18. Narrow lint suppressions remain intentional for explicit `commit()` checks, target-SDK deferral, dependency availability, and verifier/Xposed platform reflection.
- The app target SDK remains 36 intentionally. Do not bump target SDK only to silence lint; do it through a behavior-validation plan.
- Version-catalog dependency availability warnings are suppressed until dependency upgrades can be verified against reachable repositories. An attempted coroutine bump was reverted because dependency resolution failed in this environment.
- HiddenApiBypass is intentionally not a dependency. Android 16 compatibility work must not add it as a shortcut.
- Java proc-maps redaction is not native scanner coverage. Native hook engines require a separate evidence-backed plan.
- Android 16 emulator evidence from `emulator-5554` / Pixel 10 Pro XL API 36.1 proves that emulator path only. Physical-device Android 16 claims still require separate evidence.
- In-app diagnostics Binder can be unavailable under SELinux; LSPosed logs remain the practical runtime source.
