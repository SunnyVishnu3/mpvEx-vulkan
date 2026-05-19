# R8 Libxposed Explicit Hookers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep release R8 shrinking enabled while eliminating the libxposed `AbstractMethodError` crash by replacing every libxposed-facing Kotlin SAM/lambda hook callback with explicit named `XposedInterface.Hooker` implementations and release/runtime verification gates.

**Architecture:** The app, config, RemotePreferences, and spoofing behavior stay unchanged. Only the callback objects passed to `HookBuilder.intercept(Hooker)` change: every `intercept { chain -> ... }` becomes a named class that extends a small stable base and implements the official libxposed `Hooker.intercept(Chain)` ABI directly. R8 keep rules become narrow and intentional: keep the module entry point, libxposed API callback interfaces, and Device Masker hooker/callback classes, then verify minified release bytecode and real LSPosed target runtime.

**Tech Stack:** Kotlin, Android Gradle Plugin, R8/ProGuard, libxposed API 101, LSPosed, RemotePreferences, JUnit, Gradle unit tests, ADB emulator runtime smoke.

---

## Assumptions

- The root cause is the release-only libxposed callback ABI failure documented in `docs/internal/reports/closed/validation/2026-05-06/2026-05-06-r8-libxposed-runtime-crash-analysis.md`.
- R8 must remain enabled for release because it reduces APK size from about 16 MB to about 3.8 MB.
- No spoofing architecture change is intended: `JsonConfig.appConfigs` stays canonical, config delivery remains RemotePreferences-first, AIDL remains diagnostics-only, and hookers continue returning original values for disabled, missing, blank, malformed, unsafe, or unsupported config.
- The modern libxposed API 101 contract is the source of truth: callbacks passed to `HookBuilder.intercept(...)` must be real `io.github.libxposed.api.XposedInterface.Hooker` instances with a working `intercept(XposedInterface.Chain)` implementation.
- Global `Class.forName` and `ClassLoader.loadClass` anti-detection hooks remain disabled by default and are not made broader by this work.

## Success Criteria

- `rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed` returns no Kotlin lambda hook callbacks.
- Unit/static tests fail before the migration and pass after the migration.
- `assembleRelease` succeeds with R8 minification and resource shrinking enabled.
- Minified release bytecode still contains concrete Device Masker callback classes that implement `io.github.libxposed.api.XposedInterface$Hooker`.
- Installing the signed release APK under LSPosed and launching `com.mantle.verify` does not crash with `AbstractMethodError`.
- LSPosed/logcat shows `XposedEntry loaded`, target package selection, `All hooks registered for: com.mantle.verify`, and spoof events for at least Android ID, IMEI, Wi-Fi MAC, Wi-Fi SSID, Advertising ID, Media DRM ID, SIM/operator/carrier, and a device profile field.
- Runtime smoke also passes for two additional target apps after Mantle passes.
- Memory Bank and the R8/libxposed report are updated after validation.

## File Structure

- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt`
  - One narrow base class for named libxposed hook callbacks.
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt`
  - Static source/keep-rule tests that prevent lambda callback regressions and protect the ABI.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`
  - Update documentation from lambda-based examples to named `StableHooker` examples.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AdvertisingHooker.kt`
  - Convert four advertising/GSF/MediaDrm interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt`
  - Convert all TelephonyManager, Build serial, Settings.Secure, and SystemProperties interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/NetworkHooker.kt`
  - Convert Wi-Fi, network, Bluetooth, and connectivity interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt`
  - Convert subscription/SIM/carrier interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`
  - Convert locale/timezone/device profile interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt`
  - Convert location interceptors and preserve copied-location behavior.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SensorHooker.kt`
  - Convert sensor list/default sensor interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt`
  - Convert WebView UA interceptors and preserve `chain.proceed(Object[])` for changed arguments.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/PackageManagerHooker.kt`
  - Convert PackageManager/package visibility interceptors and preserve `ExceptionMode.PASSTHROUGH` where app-visible throws are intentional.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
  - Convert stack trace, class lookup, `/proc/self/maps`, and PackageManager anti-detection interceptors.
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt`
  - Convert system-server service registration interceptors.
- Modify: `xposed/consumer-rules.pro`
  - Replace lambda-focused comments/rules with explicit callback-class rules.
- Modify: `app/proguard-rules.pro`
  - Keep only the app-level libxposed and callback rules required by merged release R8.
- Modify: `docs/internal/reports/closed/validation/2026-05-06/2026-05-06-r8-libxposed-runtime-crash-analysis.md`
  - Add implementation result, release APK size, and runtime evidence after validation.
- Modify: `memory-bank/activeContext.md`, `memory-bank/progress.md`, and any other Memory Bank file whose current state becomes stale.

## Current Callback Inventory

Convert every call site reported by this command:

```powershell
rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed
```

Current files containing lambda interceptors:

- `AntiDetectHooker.kt`: 11 hook callback groups.
- `AdvertisingHooker.kt`: 4 hook callbacks.
- `DeviceHooker.kt`: 25 hook callbacks.
- `NetworkHooker.kt`: 7 hook callbacks.
- `PackageManagerHooker.kt`: 5 hook callbacks.
- `LocationHooker.kt`: 5 hook callbacks.
- `WebViewHooker.kt`: 3 hook callbacks.
- `SubscriptionHooker.kt`: 10 hook callbacks.
- `SystemHooker.kt`: 2 hook callbacks.
- `SensorHooker.kt`: 4 hook callbacks.
- `SystemServiceHooker.kt`: 2 hook callbacks.

---

### Task 1: Add Static ABI Regression Tests

**Files:**
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt`

- [ ] **Step 1: Write the failing source gate**

Create `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt` with this exact content:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class R8HookerAbiTest {

    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    private val xposedSourceRoot =
        File(repoRoot, "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed")

    @Test
    fun `xposed hook registration does not use Kotlin lambda interceptors`() {
        val offenders =
            xposedSourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        val normalized = line.trim()
                        val isInterceptorLambda =
                            normalized.contains(".intercept {") ||
                                normalized.startsWith("intercept {")
                        if (isInterceptorLambda) {
                            "${file.relativeTo(repoRoot).invariantSeparatorsPath}:${index + 1}:$normalized"
                        } else {
                            null
                        }
                    }
                }
                .toList()

        assertTrue(
            offenders.isEmpty(),
            "libxposed callbacks must use named XposedInterface.Hooker classes, not Kotlin SAM lambdas:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }

    @Test
    fun `stable hooker base implements official libxposed hooker interface`() {
        val source =
            File(
                    repoRoot,
                    "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt",
                )
                .readText()

        assertTrue(source.contains("import io.github.libxposed.api.XposedInterface"))
        assertTrue(source.contains("abstract class StableHooker : XposedInterface.Hooker"))
        assertTrue(source.contains("final override fun intercept(chain: XposedInterface.Chain): Any?"))
        assertFalse(source.contains("inline"))
        assertFalse(source.contains("crossinline"))
    }

    @Test
    fun `r8 rules keep explicit callback classes and libxposed hook ABI`() {
        val consumerRules = File(repoRoot, "xposed/consumer-rules.pro").readText()
        val appRules = File(repoRoot, "app/proguard-rules.pro").readText()
        val combined = consumerRules + "\n" + appRules

        assertTrue(combined.contains("io.github.libxposed.api.XposedInterface\$Hooker"))
        assertTrue(combined.contains("com.astrixforge.devicemasker.xposed.hooker.callback.**"))
        assertTrue(combined.contains("com.astrixforge.devicemasker.xposed.hooker.**"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD FAILED`. The first failure must list existing `.intercept {` call sites. The second failure may mention missing `StableHooker.kt` until Task 2 is implemented.

- [ ] **Step 3: Commit the failing test**

```powershell
git add xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt
git commit -m "test: add libxposed R8 hooker ABI gate"
```

---

### Task 2: Add Stable Named Hooker Base

**Files:**
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt`

- [ ] **Step 1: Add the stable callback base**

Create `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt`:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker.callback

import io.github.libxposed.api.XposedInterface

/**
 * Explicit libxposed callback base for release builds.
 *
 * R8 can rewrite Kotlin SAM/lambda callback classes aggressively. This base keeps the official
 * XposedInterface.Hooker.intercept(Chain) ABI as a named method on a named class. Subclasses put
 * hook-specific behavior in onIntercept(), which is ordinary app code and not the method LSPosed
 * dispatches through.
 */
internal abstract class StableHooker : XposedInterface.Hooker {

    final override fun intercept(chain: XposedInterface.Chain): Any? = onIntercept(chain)

    protected abstract fun onIntercept(chain: XposedInterface.Chain): Any?
}
```

- [ ] **Step 2: Run the focused test**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD FAILED` only because existing production hookers still contain `.intercept {` callback lambdas. The `stable hooker base implements official libxposed hooker interface` assertion must pass.

- [ ] **Step 3: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/callback/StableHooker.kt
git commit -m "feat: add stable libxposed hooker callback base"
```

---

### Task 3: Update R8 Keep Rules for Explicit Callback Classes

**Files:**
- Modify: `xposed/consumer-rules.pro`
- Modify: `app/proguard-rules.pro`

- [ ] **Step 1: Update `xposed/consumer-rules.pro` comments and keep rules**

Replace the current lambda-focused libxposed block with:

```proguard
# Hooker callback ABI — libxposed calls XposedInterface.Hooker.intercept(Chain)
# from target processes. Device Masker uses named StableHooker subclasses instead
# of Kotlin SAM lambdas so R8 cannot strip or rewrite the runtime callback ABI.
-keep interface io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.callback.** { *; }

# Chain, HookBuilder, HookHandle — used inside hook callback bodies and returned by API
-keep interface io.github.libxposed.api.XposedInterface$Chain { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookBuilder { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookHandle { *; }
```

Keep the existing:

```proguard
-keep class com.astrixforge.devicemasker.xposed.hooker.** { *; }
```

- [ ] **Step 2: Update `app/proguard-rules.pro` comments and keep rules**

Replace the current lambda-focused libxposed API block with:

```proguard
# LIBXPOSED API — Hook interface hierarchy and named callback classes.
# Device Masker uses explicit StableHooker subclasses for release R8 stability.
-keep interface io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.callback.** { *; }
-keep class com.astrixforge.devicemasker.xposed.hooker.** { *; }
-keep interface io.github.libxposed.api.XposedInterface$Chain { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookBuilder { *; }
-keep interface io.github.libxposed.api.XposedInterface$HookHandle { *; }
```

Remove the stale `-keep interface io.github.libxposed.api.XposedInterface$MethodHooker { *; }` line unless a compile error proves it is required by the currently resolved libxposed API jar.

- [ ] **Step 3: Run the focused test**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD FAILED` only because production hookers still contain `.intercept {` callback lambdas.

- [ ] **Step 4: Commit**

```powershell
git add xposed/consumer-rules.pro app/proguard-rules.pro
git commit -m "build: keep named libxposed hook callbacks"
```

---

### Task 4: Convert AdvertisingHooker First

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AdvertisingHooker.kt`

- [ ] **Step 1: Add the import**

Add:

```kotlin
import com.astrixforge.devicemasker.xposed.hooker.callback.StableHooker
```

- [ ] **Step 2: Replace the Advertising ID callback**

Replace:

```kotlin
xi.hook(m).intercept { chain ->
    val result = chain.proceed()
    val spoofed =
        getConfiguredSpoofValue(prefs, pkg, SpoofType.ADVERTISING_ID)
            ?: return@intercept result
    reportSpoofEvent(pkg, SpoofType.ADVERTISING_ID)
    spoofed
}
```

with:

```kotlin
xi.hook(m).intercept(AdvertisingIdHooker(prefs, pkg))
```

Add this nested class inside `AdvertisingHooker`:

```kotlin
private class AdvertisingIdHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val spoofed =
            getConfiguredSpoofValue(prefs, pkg, SpoofType.ADVERTISING_ID) ?: return result
        reportSpoofEvent(pkg, SpoofType.ADVERTISING_ID)
        return spoofed
    }
}
```

- [ ] **Step 3: Replace the Gservices callbacks**

Replace both `Gservices.getString(...)` and `Gservices.getLong(...)` callback lambdas with:

```kotlin
xi.hook(m).intercept(GservicesStringHooker(prefs, pkg))
```

and:

```kotlin
xi.hook(m).intercept(GservicesLongHooker(prefs, pkg))
```

Add:

```kotlin
private class GservicesStringHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val key = chain.args.getOrNull(1) as? String ?: return result
        if (key != "android_id") return result
        val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.GSF_ID) ?: return result
        reportSpoofEvent(pkg, SpoofType.GSF_ID)
        return spoofed
    }
}

private class GservicesLongHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val key = chain.args.getOrNull(1) as? String ?: return result
        if (key != "android_id") return result
        val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.GSF_ID) ?: return result
        val finalVal = runCatching { spoofed.toLong(16) }.getOrElse { result as Long }
        reportSpoofEvent(pkg, SpoofType.GSF_ID)
        return finalVal
    }
}
```

- [ ] **Step 4: Replace the MediaDrm callback**

Replace the MediaDrm lambda with:

```kotlin
xi.hook(m).intercept(MediaDrmDeviceUniqueIdHooker(prefs, pkg))
```

Add:

```kotlin
private class MediaDrmDeviceUniqueIdHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val property = chain.args.firstOrNull() as? String ?: return result
        if (property != "deviceUniqueId") return result
        val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.MEDIA_DRM_ID) ?: return result
        val bytes = hexToBytes(spoofed) ?: return result
        reportSpoofEvent(pkg, SpoofType.MEDIA_DRM_ID)
        return bytes
    }
}
```

- [ ] **Step 5: Run focused checks**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.PrefsHelperTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `R8HookerAbiTest` still fails because other hookers still contain callback lambdas. No new Kotlin compile errors.

- [ ] **Step 6: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AdvertisingHooker.kt
git commit -m "refactor: use named libxposed callbacks for advertising hooks"
```

---

### Task 5: Convert DeviceHooker with Shared Value Callback Classes

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt`

- [ ] **Step 1: Add the import**

```kotlin
import com.astrixforge.devicemasker.xposed.hooker.callback.StableHooker
```

- [ ] **Step 2: Add reusable callback classes inside `DeviceHooker`**

Add these nested classes near the end of `DeviceHooker`, before the closing brace:

```kotlin
private class StoredValueHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
    private val type: SpoofType,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val spoofed = getConfiguredSpoofValue(prefs, pkg, type) ?: return result
        reportSpoofEvent(pkg, type)
        return spoofed
    }
}

private class SettingsSecureAndroidIdHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val key = chain.args.getOrNull(1) as? String ?: return result
        if (key != "android_id") return result
        val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.ANDROID_ID) ?: return result
        reportSpoofEvent(pkg, SpoofType.ANDROID_ID)
        return spoofed
    }
}

private class SystemPropertySerialHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
    private val serialKeys: Set<String>,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val key = chain.args.firstOrNull() as? String ?: return result
        if (key !in serialKeys) return result
        val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.SERIAL) ?: return result
        reportSpoofEvent(pkg, SpoofType.SERIAL)
        return spoofed
    }
}
```

- [ ] **Step 3: Replace each direct stored-value callback**

Replace the repeated lambda body:

```kotlin
val result = chain.proceed()
val spoofed =
    getConfiguredSpoofValue(prefs, pkg, SpoofType.X)
        ?: return@intercept result
reportSpoofEvent(pkg, SpoofType.X)
spoofed
```

with:

```kotlin
xi.hook(m).intercept(StoredValueHooker(prefs, pkg, SpoofType.X))
```

Use these exact mappings:

- `getDeviceId()` -> `SpoofType.IMEI`
- `getDeviceId(int)` -> `SpoofType.IMEI`
- `getImei()` -> `SpoofType.IMEI`
- `getImei(int)` -> `SpoofType.IMEI`
- `getSubscriberId()` -> `SpoofType.IMSI`
- `getSubscriberId(int)` -> `SpoofType.IMSI`
- `getSimSerialNumber()` -> `SpoofType.ICCID`
- `getSimSerialNumber(int)` -> `SpoofType.ICCID`
- `getSimCountryIso()` -> `SpoofType.SIM_COUNTRY_ISO`
- `getSimCountryIso(int)` -> `SpoofType.SIM_COUNTRY_ISO`
- `getNetworkCountryIso()` -> `SpoofType.NETWORK_COUNTRY_ISO`
- `getNetworkCountryIso(int)` -> `SpoofType.NETWORK_COUNTRY_ISO`
- `getSimOperatorName()` -> `SpoofType.SIM_OPERATOR_NAME`
- `getSimOperatorName(int)` -> `SpoofType.SIM_OPERATOR_NAME`
- `getSimOperator()` -> `SpoofType.CARRIER_MCC_MNC`
- `getSimOperator(int)` -> `SpoofType.CARRIER_MCC_MNC`
- `getNetworkOperator()` -> `SpoofType.NETWORK_OPERATOR`
- `getNetworkOperator(int)` -> `SpoofType.NETWORK_OPERATOR`
- `getLine1Number()` -> `SpoofType.PHONE_NUMBER`
- `Build.getSerial()` -> `SpoofType.SERIAL`

- [ ] **Step 4: Replace Settings.Secure and SystemProperties callbacks**

Use:

```kotlin
xi.hook(m).intercept(SettingsSecureAndroidIdHooker(prefs, pkg))
```

for both `Settings.Secure.getString(...)` and `Settings.Secure.getStringForUser(...)`.

Use:

```kotlin
xi.hook(m).intercept(SystemPropertySerialHooker(prefs, pkg, SERIAL_KEYS))
```

for both `SystemProperties.get(String)` and `SystemProperties.get(String, String)`.

- [ ] **Step 5: Run focused checks**

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD FAILED` until every remaining hooker is converted. `DeviceHooker.kt` must not appear in the offender list.

- [ ] **Step 6: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt
git commit -m "refactor: use named libxposed callbacks for device hooks"
```

---

### Task 6: Convert Remaining Spoof Hookers

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/NetworkHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SensorHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/PackageManagerHooker.kt`

- [ ] **Step 1: Add `StableHooker` import to each file**

Add:

```kotlin
import com.astrixforge.devicemasker.xposed.hooker.callback.StableHooker
```

- [ ] **Step 2: Use this exact conversion pattern for value-returning hooks**

For any callback that reads `chain.proceed()`, validates args/config, reports a spoof event, and returns a spoof value, replace the lambda with a named nested class. The class name must include the Android API being hooked.

Example for `NetworkHooker.kt`:

```kotlin
xi.hook(m).intercept(WifiMacAddressHooker(prefs, pkg))
```

```kotlin
private class WifiMacAddressHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.WIFI_MAC) ?: return result
        reportSpoofEvent(pkg, SpoofType.WIFI_MAC)
        return spoofed
    }
}
```

- [ ] **Step 3: Preserve changed-argument WebView behavior**

For `WebViewHooker.kt`, any existing callback that modifies `setUserAgentString(String)` arguments must still copy args and call `chain.proceed(Object[])`. The named class must use this shape:

```kotlin
private class SetUserAgentStringHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val originalArgs = chain.args.toTypedArray()
        val originalUa = originalArgs.firstOrNull() as? String
        val spoofedUa = originalUa?.let { buildSpoofedUserAgent(it, prefs, pkg) }
        if (spoofedUa == null || spoofedUa == originalUa) {
            return chain.proceed()
        }
        originalArgs[0] = spoofedUa
        reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
        return chain.proceed(originalArgs)
    }
}
```

If the current helper method names differ, keep the existing helper names and keep the same `Object[]` proceed behavior.

- [ ] **Step 4: Preserve copied-location behavior**

For `LocationHooker.kt`, named classes must return copied `Location` objects rather than mutating framework-returned instances in place. Use this shape:

```kotlin
private class LastKnownLocationHooker(
    private val prefs: SharedPreferences,
    private val pkg: String,
) : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        val spoofed = buildSpoofedLocationOrNull(prefs, pkg, result) ?: return result
        reportSpoofEvent(pkg, SpoofType.LOCATION)
        return spoofed
    }
}
```

Keep the existing location parsing/copy helper behavior. Do not generate fallback coordinates in `:xposed`.

- [ ] **Step 5: Preserve package list copy behavior**

For `PackageManagerHooker.kt`, named classes that filter lists must create filtered lists and must not mutate the original framework list. Use this shape:

```kotlin
private class InstalledPackagesHooker : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        @Suppress("UNCHECKED_CAST")
        val packages = result as? List<android.content.pm.PackageInfo> ?: return result
        return packages.filterNot { shouldHidePackage(it.packageName) }
    }
}
```

Keep `ExceptionMode.PASSTHROUGH` for callbacks that intentionally throw `PackageManager.NameNotFoundException`.

- [ ] **Step 6: Run the source gate after each file**

After each file conversion, run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected after each intermediate file: `BUILD FAILED`, and the converted file is absent from the offender list. Expected after the seventh file in this task: only `AntiDetectHooker.kt` and `SystemServiceHooker.kt` may still appear.

- [ ] **Step 7: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/NetworkHooker.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/LocationHooker.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SensorHooker.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/WebViewHooker.kt xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/PackageManagerHooker.kt
git commit -m "refactor: use named libxposed callbacks for spoof hooks"
```

---

### Task 7: Convert AntiDetectHooker

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.astrixforge.devicemasker.xposed.hooker.callback.StableHooker
import io.github.libxposed.api.XposedInterface.Chain
```

- [ ] **Step 2: Convert stack trace hooks**

Use:

```kotlin
xi.hook(m).intercept(StackTraceHooker())
```

Add:

```kotlin
private class StackTraceHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        val result = chain.proceed()
        @Suppress("UNCHECKED_CAST")
        val stack = result as? Array<StackTraceElement> ?: return result
        return filterStackTrace(stack)
    }
}
```

- [ ] **Step 3: Convert class lookup hooks and keep pass-through throws**

Use:

```kotlin
xi.hook(method).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept(ClassLookupHooker())
```

Add:

```kotlin
private class ClassLookupHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        if (classLookupHookActive.get() == true) return chain.proceed()
        classLookupHookActive.set(true)
        return try {
            val className = chain.args.firstOrNull() as? String
            if (className != null && shouldHideClass(className)) {
                throw ClassNotFoundException(className)
            }
            chain.proceed()
        } finally {
            classLookupHookActive.set(false)
        }
    }
}
```

This class is used for both `ClassLoader.loadClass(...)` and `Class.forName(...)`. Do not enable these hooks by default; keep the existing `PrefsHelper.isClassLookupHidingEnabled(prefs, pkg)` guard.

- [ ] **Step 4: Convert `/proc/self/maps` hook**

Use:

```kotlin
xi.hook(m).intercept(ProcMapsReadLineHooker())
```

Add:

```kotlin
private class ProcMapsReadLineHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        val result = chain.proceed()
        val line = result as? String
        return if (
            line != null &&
                HIDDEN_LIBRARY_PATTERNS.any { line.contains(it, ignoreCase = true) }
        ) {
            ""
        } else {
            result
        }
    }
}
```

- [ ] **Step 5: Convert PackageManager anti-detection hooks**

Use these named classes:

```kotlin
private class HiddenPackageInfoHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        val pkgName = chain.args.firstOrNull() as? String
        if (pkgName != null && HIDDEN_PACKAGES.any { pkgName.equals(it, ignoreCase = true) }) {
            throw android.content.pm.PackageManager.NameNotFoundException(pkgName)
        }
        return chain.proceed()
    }
}

private class InstalledPackagesFilterHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        val result = chain.proceed()
        @Suppress("UNCHECKED_CAST")
        val packages = result as? List<PackageInfo> ?: return result
        return packages.filterNot { info ->
            HIDDEN_PACKAGES.any { info.packageName.equals(it, ignoreCase = true) }
        }
    }
}

private class InstalledApplicationsFilterHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        val result = chain.proceed()
        @Suppress("UNCHECKED_CAST")
        val apps = result as? List<ApplicationInfo> ?: return result
        return apps.filterNot { info ->
            HIDDEN_PACKAGES.any { info.packageName.equals(it, ignoreCase = true) }
        }
    }
}

private class QueryIntentActivitiesFilterHooker : StableHooker() {
    override fun onIntercept(chain: Chain): Any? {
        val result = chain.proceed()
        @Suppress("UNCHECKED_CAST")
        val infos = result as? List<ResolveInfo> ?: return result
        return infos.filterNot { info ->
            val packageName = info.activityInfo?.packageName ?: return@filterNot false
            HIDDEN_PACKAGES.any { packageName.equals(it, ignoreCase = true) }
        }
    }
}
```

Register `HiddenPackageInfoHooker` with `ExceptionMode.PASSTHROUGH` for `getPackageInfo(...)` and `getApplicationInfo(...)`.

- [ ] **Step 6: Run anti-detection tests**

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.HookSafetyTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `R8HookerAbiTest` still fails only if `SystemServiceHooker.kt` remains unconverted. `HookSafetyTest` passes.

- [ ] **Step 7: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt
git commit -m "refactor: use named libxposed callbacks for anti-detection hooks"
```

---

### Task 8: Convert SystemServiceHooker

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt`

- [ ] **Step 1: Add the import**

```kotlin
import com.astrixforge.devicemasker.xposed.hooker.callback.StableHooker
```

- [ ] **Step 2: Convert both system-server callbacks**

For the service-registration hook, use a named class that keeps the existing behavior body and calls `chain.proceed()` exactly where the current lambda does:

```kotlin
xi.hook(method).intercept(AddServiceHooker())
```

For the Runnable/boot callback hook, use:

```kotlin
xi.hook(runMethod).intercept(RunnableHooker())
```

Use this exact callback skeleton:

```kotlin
private class RunnableHooker : StableHooker() {
    override fun onIntercept(chain: XposedInterface.Chain): Any? {
        return chain.proceed()
    }
}
```

If the current `AddService` callback adds diagnostics registration before or after proceeding, keep that ordering exactly. The only allowed behavior change in this task is replacing the lambda object with a named `StableHooker` subclass.

- [ ] **Step 3: Run the ABI test**

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`. The callback-lambda offender list must be empty.

- [ ] **Step 4: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServiceHooker.kt
git commit -m "refactor: use named libxposed callbacks for system service hooks"
```

---

### Task 9: Update BaseSpoofHooker Documentation

**Files:**
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt`

- [ ] **Step 1: Replace lambda wording**

Change documentation that says hookers use lambda-based interception to named callback wording:

```kotlin
 * 3. Inside each [safeHook] block: resolve the [Method], call `xi.hook().intercept(NamedHooker)`,
 *    then `xi.deoptimize()`.
 * 4. The named callback handles the actual libxposed `Hooker.intercept(Chain)` call via
 *    `chain.proceed()` plus a spoofed or original return value.
```

- [ ] **Step 2: Replace the example**

Use this example:

```kotlin
 * ```kotlin
 * private class ImeiHooker(
 *     private val prefs: SharedPreferences,
 *     private val pkg: String,
 * ) : StableHooker() {
 *     override fun onIntercept(chain: XposedInterface.Chain): Any? {
 *         val original = chain.proceed()
 *         val spoofed = getConfiguredSpoofValue(prefs, pkg, SpoofType.IMEI) ?: return original
 *         reportSpoofEvent(pkg, SpoofType.IMEI)
 *         return spoofed
 *     }
 * }
 * ```
```

- [ ] **Step 3: Run formatting and test**

```powershell
.\gradlew.bat spotlessApply :xposed:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/BaseSpoofHooker.kt
git commit -m "docs: document named libxposed callback pattern"
```

---

### Task 10: Release R8 Build and Bytecode Inspection

**Files:**
- Modify only if required by verified failures: `app/build.gradle.kts`, `xposed/consumer-rules.pro`, `app/proguard-rules.pro`

- [ ] **Step 1: Confirm release minification is enabled**

Run:

```powershell
rg -n "isMinifyEnabled|isShrinkResources" app/build.gradle.kts gradle.properties
```

Expected: release build type has `isMinifyEnabled = true`. `isShrinkResources = true` should be enabled if the previous R8 size target depends on it.

- [ ] **Step 2: Build release**

Run:

```powershell
.\gradlew.bat clean :app:assembleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect release mapping for callback classes**

Run:

```powershell
Select-String -Path app\build\outputs\mapping\release\mapping.txt -Pattern "StableHooker|AdvertisingIdHooker|SettingsSecureAndroidIdHooker|StackTraceHooker|ClassLookupHooker|SystemServiceHooker"
```

Expected: mapping output includes `StableHooker` and representative named callback classes. They may be renamed if rules allow renaming, but they must exist as concrete classes. If a callback class is absent, tighten keep rules to keep `com.astrixforge.devicemasker.xposed.hooker.callback.**` and `* implements XposedInterface$Hooker`.

- [ ] **Step 4: Inspect APK size**

Run:

```powershell
Get-Item app\build\outputs\apk\release\app-release-unsigned.apk | Select-Object Name,Length
```

Expected: release APK remains close to the R8 target size. A small increase from explicit callback classes is acceptable; a return toward the 16 MB unshrunk size means release shrinking is not active.

- [ ] **Step 5: Commit only if build configuration changed**

```powershell
git add app/build.gradle.kts xposed/consumer-rules.pro app/proguard-rules.pro
git commit -m "build: verify release R8 callback retention"
```

Skip this commit if Task 10 produced no file changes.

---

### Task 11: Full Static and Unit Gate

**Files:**
- No planned source changes.

- [ ] **Step 1: Run the project gate**

```powershell
.\gradlew.bat spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the source query gate**

```powershell
rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed
```

Expected: no output.

- [ ] **Step 3: Run the R8 callback gate directly**

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 12: Signed Release Runtime Smoke on Emulator

**Files:**
- Create runtime artifacts only: `mantle_r8_named_hookers_logcat.txt`, `devcheck_r8_named_hookers_logcat.txt`, and one third-target log file.

- [ ] **Step 1: Sign the release APK with debug key for emulator testing**

```powershell
$apk = "app\build\outputs\apk\release\app-release-unsigned.apk"
$signed = "app\build\outputs\apk\release\app-release-debugkey-signed.apk"
$sdk = "$env:ANDROID_HOME\build-tools"
$buildTools = Get-ChildItem $sdk -Directory | Sort-Object Name -Descending | Select-Object -First 1
& "$($buildTools.FullName)\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out $signed $apk
& "$($buildTools.FullName)\apksigner.bat" verify --verbose $signed
```

Expected: `Verified using v2 scheme (APK Signature Scheme v2): true` or equivalent successful verification.

- [ ] **Step 2: Install release APK**

If signatures match:

```powershell
adb install -r app\build\outputs\apk\release\app-release-debugkey-signed.apk
```

Expected: `Success`.

If ADB reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and back up app data before uninstalling. Use the existing project backup pattern from the previous release test, then install again:

```powershell
adb shell su -c "tar -czf /sdcard/devicemasker_appdata_backup_$(date +%Y%m%d_%H%M%S).tar.gz /data/data/com.astrixforge.devicemasker"
adb uninstall com.astrixforge.devicemasker
adb install app\build\outputs\apk\release\app-release-debugkey-signed.apk
```

Expected: `Success`.

- [ ] **Step 3: Confirm LSPosed setup**

Manual prerequisite before runtime claims:

- Device Masker module enabled in LSPosed.
- LSPosed scope includes `android`, `system`, `com.mantle.verify`, and two additional target apps.
- Device Masker app has global module enabled, target apps enabled, groups assigned, and spoof types enabled.
- Force-stop Device Masker and all target apps after changing module/scope/config.

- [ ] **Step 4: Launch Mantle and collect logcat**

```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb shell pidof com.mantle.verify
adb logcat -d -v time -t 2500 | Out-File -Encoding utf8 mantle_r8_named_hookers_logcat.txt
```

Expected:

- `pidof` returns a PID.
- Logcat has no `AbstractMethodError`.
- Logcat has no `FATAL EXCEPTION` for `com.mantle.verify`.
- Logcat includes `XposedEntry loaded for process: com.mantle.verify`.
- Logcat includes `All hooks registered for: com.mantle.verify`.
- Logcat includes spoof events.

- [ ] **Step 5: Launch DevCheck or another existing scoped target**

```powershell
adb shell am force-stop flar2.devcheck
adb logcat -c
adb shell monkey -p flar2.devcheck -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb shell pidof flar2.devcheck
adb logcat -d -v time -t 2500 | Out-File -Encoding utf8 devcheck_r8_named_hookers_logcat.txt
```

Expected: same no-crash and LSPosed hook-registration evidence.

- [ ] **Step 6: Launch one more scoped target**

Select a third installed user app. Before this step, make sure the selected package is also enabled in LSPosed scope and Device Masker app config:

```powershell
$thirdTarget =
    adb shell pm list packages -3 |
    ForEach-Object { $_.Replace("package:", "").Trim() } |
    Where-Object {
        $_ -and
        $_ -ne "com.astrixforge.devicemasker" -and
        $_ -ne "com.mantle.verify" -and
        $_ -ne "flar2.devcheck"
    } |
    Select-Object -First 1
if (-not $thirdTarget) {
    throw "No third user app is installed. Install and scope one additional target before this validation step."
}
adb shell am force-stop $thirdTarget
adb logcat -c
adb shell monkey -p $thirdTarget -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb shell pidof $thirdTarget
adb logcat -d -v time -t 2500 | Out-File -Encoding utf8 third_target_r8_named_hookers_logcat.txt
```

Expected: same no-crash and LSPosed hook-registration evidence.

---

### Task 13: Edge Case Runtime Validation

**Files:**
- Runtime artifacts only.

- [ ] **Step 1: Disabled app pass-through**

Disable `com.mantle.verify` in Device Masker app config, force-stop Mantle, relaunch, and inspect logs:

```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb logcat -d -v time -t 2000 | Out-File -Encoding utf8 mantle_disabled_passthrough_logcat.txt
```

Expected: target does not crash. Hook registration should be skipped or spoof events should be absent for disabled config. Do not claim spoofing is active in this state.

- [ ] **Step 2: Missing or blank value pass-through**

For one low-risk type such as `ADVERTISING_ID`, clear the stored value in the app while leaving the target enabled. Relaunch Mantle and capture:

```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb logcat -d -v time -t 2000 | Out-File -Encoding utf8 mantle_blank_value_passthrough_logcat.txt
```

Expected: no target crash. That spoof type returns original value or emits no spoof event. Other enabled valid spoof types may still emit spoof events.

- [ ] **Step 3: Risky class lookup opt-in check**

Enable class lookup hiding only for one controlled target, force-stop, relaunch, and inspect for startup regressions:

```powershell
adb shell am force-stop com.mantle.verify
adb logcat -c
adb shell monkey -p com.mantle.verify -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
adb shell pidof com.mantle.verify
adb logcat -d -v time -t 3000 | Out-File -Encoding utf8 mantle_class_lookup_opt_in_logcat.txt
```

Expected: no `WorkManagerInitializer` crash, no class-loading ANR, no `AbstractMethodError`. If the target crashes here but not with class lookup disabled, keep class lookup hiding disabled by default and document the target-specific failure.

---

### Task 14: Documentation and Memory Bank Update

**Files:**
- Modify: `docs/internal/reports/closed/validation/2026-05-06/2026-05-06-r8-libxposed-runtime-crash-analysis.md`
- Modify: `docs/internal/reports/closed/audits/2026-05-06/2026-05-06-build-audit-and-r8-enablement.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Modify if stale: `memory-bank/systemPatterns.md`, `memory-bank/techContext.md`

- [ ] **Step 1: Update R8 crash analysis report**

Append a section with this structure:

```markdown
## Implementation Result: Explicit Named Hookers

Date: 2026-05-06

Release R8 remains enabled. Device Masker no longer passes Kotlin SAM/lambda callback classes to `HookBuilder.intercept(...)`; hook callbacks are named `StableHooker` subclasses implementing the official `XposedInterface.Hooker.intercept(Chain)` ABI.

Validation:
- Static callback lambda gate: PASS
- `:xposed:testDebugUnitTest`: PASS
- Full Gradle gate: PASS
- Release APK size: paste the exact output from:
  `Get-Item app\build\outputs\apk\release\app-release-unsigned.apk | Select-Object Name,Length`
- Mantle release smoke: PASS/FAIL with exact PID and log evidence
- Additional target 1: PASS/FAIL with package name
- Additional target 2: PASS/FAIL with package name

Remaining risk:
- R8 stability is validated for callback ABI, but broader target compatibility still depends on target-specific framework usage and LSPosed scope/config.
- Class lookup hiding remains opt-in because it is independently risky for target startup.
```

- [ ] **Step 2: Update build audit report**

Add a short note that release R8 enablement is now supported only with explicit named libxposed callbacks and the `R8HookerAbiTest` gate.

- [ ] **Step 3: Update Memory Bank**

Update `memory-bank/activeContext.md` and `memory-bank/progress.md` with:

```markdown
### 2026-05-06 R8/libxposed Callback ABI Hardening

- Release R8 remains enabled for APK size reduction.
- Kotlin SAM/lambda callbacks passed to `HookBuilder.intercept(...)` were replaced by named `StableHooker` subclasses.
- Added static gate preventing `.intercept { ... }` callbacks under `:xposed`.
- Release runtime validation on `com.mantle.verify` showed no `AbstractMethodError` after the migration.
- Class lookup hiding remains opt-in and is not part of the R8 callback stability guarantee.
```

If runtime validation has not passed yet, write `planned` or `pending` instead of claiming it passed.

- [ ] **Step 4: Update graphify after code changes**

```powershell
graphify update .
```

Expected: graphify completes without API-cost source re-analysis errors.

- [ ] **Step 5: Commit docs**

```powershell
git add docs/internal/reports/closed/validation/2026-05-06/2026-05-06-r8-libxposed-runtime-crash-analysis.md docs/internal/reports/closed/audits/2026-05-06/2026-05-06-build-audit-and-r8-enablement.md memory-bank/activeContext.md memory-bank/progress.md memory-bank/systemPatterns.md memory-bank/techContext.md graphify-out
git commit -m "docs: record R8 libxposed callback hardening"
```

---

### Task 15: Final Release Decision

**Files:**
- No planned source changes.

- [ ] **Step 1: Check release evidence**

Required evidence before declaring release R8 safe:

- `.\gradlew.bat spotlessCheck :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon` returned `BUILD SUCCESSFUL`.
- `rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed` returned no output.
- Mantle release smoke stayed alive and produced hook/spoof logs.
- Two additional targets stayed alive.
- No runtime log contains `AbstractMethodError`.
- No runtime log contains the previous crash signatures: `androidx.work.WorkManagerInitializer`, `PatternSyntaxException`, `Cannot hook abstract methods`, class-loading ANR from `AntiDetectHooker`.

- [ ] **Step 2: If any runtime smoke fails**

Do not disable R8 immediately. First classify the failure:

- `AbstractMethodError` from `XposedInterface$Hooker.intercept`: callback ABI migration incomplete or keep rules insufficient.
- `HookFailedError` or `XposedFrameworkError`: libxposed/framework registration issue; rethrow behavior must stay intact.
- `ClassNotFoundException` from opt-in class hiding: target-specific anti-detection issue; keep opt-in disabled for that target.
- Target app crash after successful `All hooks registered`: hook logic bug in one spoof surface; narrow by disabling spoof types per target.

- [ ] **Step 3: If all runtime smoke passes**

Keep R8 enabled as the long-term release configuration. Do not reintroduce Kotlin SAM/lambda callbacks for libxposed hook registration.

## Rollback Plan

Use this only if release validation blocks shipping and there is no time to fix the specific failure:

1. Keep all named callback code in the branch.
2. Temporarily set release `isMinifyEnabled = false` and `isShrinkResources = false`.
3. Build and ship an unminified emergency APK only for internal testing.
4. Open a follow-up issue to restore R8 before release distribution.

Do not roll back to Kotlin lambda callbacks; that restores the known Mantle crash path.

## Implementation Notes

- Do not change `XposedEntry` lifecycle methods or package selection as part of this plan.
- Do not change RemotePreferences key names or config semantics.
- Do not generate fallback identifiers inside `:xposed`.
- Do not broaden anti-detection behavior to solve the R8 problem.
- Keep `ExceptionMode.PASSTHROUGH` only where the app-visible exception is intentional.
- Keep `xi.deoptimize(m)` after every successful hook registration.
- Preserve `XposedFrameworkError` rethrow behavior before generic `Throwable` logging.
- Prefer named nested callback classes inside the hooker that owns the logic. Move to separate files only if a file becomes difficult to review after conversion.

## Self-Review

- Spec coverage: This plan covers R8 retention, libxposed official Hooker ABI, every current hooker file with `.intercept {}` callbacks, keep rules, tests, release build inspection, emulator runtime smoke, edge cases, docs, Memory Bank, and graphify.
- Placeholder scan: The plan contains no deferred implementation markers. Runtime package selection for the third target is intentionally operator-provided because the installed/scoped package list is environment-specific.
- Type consistency: All callback code uses `StableHooker`, `XposedInterface.Chain`, `SharedPreferences`, `SpoofType`, and existing hooker helper methods already present in the project.
