# Device Masker Complete Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:executing-plans` or `superpowers:subagent-driven-development` before implementing this plan. Track every checkbox in this file. Do not implement from memory.

**Source report:** `docs/internal/reports/closed/research/2026-05-09/2026-05-09-device-masker-combined-research-audit.md`

**Goal:** Implement every accepted finding from the combined research audit and prove it end to end on the already-running Android 13 emulator `emulator-5554`.

**Architecture:** Keep the existing three-module production architecture intact. `:app` owns UI, persistence, diagnostics, and RemotePreferences writes. `:common` owns data contracts, generated persona values, validation, and shared keys. `:xposed` owns target-process hooks only. Add a separate local verifier app only for validation so Device Masker can be tested against a controlled target package without weakening the production APK.

**Hard guardrails:**

- No custom Binder/AIDL config path.
- No hook evidence/config read from app-private JSON.
- No random identifier generation inside `:xposed`.
- No hardcoded RemotePreferences keys outside `SharedPrefsKeys`.
- No direct Kotlin SAM `.intercept { ... }` callbacks in runtime hookers.
- No xposed hook success claims without LSPosed/logcat evidence and target-app value checks.
- No broad system_server or native-hook behavior without per-app opt-in, emergency kill switch, and proof on Android 13 release builds.

---

## Official References To Keep Open

- Combined audit: `docs/internal/reports/closed/research/2026-05-09/2026-05-09-device-masker-combined-research-audit.md`
- Architecture: `docs/public/ARCHITECTURE.md`
- Agent project rules: `docs/AGENTS_PROJECT_RULES.md`
- Root guide: `AGENTS.md`
- Common guide: `common/AGENTS.md`
- Xposed guide: `xposed/AGENTS.md`
- libxposed skill: `.agents/skills/libxposed/SKILL.md`
- libxposed Javadoc index: `.agents/skills/libxposed/references/javadoc/INDEX.md`
- libxposed Chain/Hooker/HookBuilder: `.agents/skills/libxposed/references/javadoc/api-javadoc/02-Chain-HookBuilder-HookHandle-Hooker.md`
- libxposed RemotePreferences service docs: `.agents/skills/libxposed/references/javadoc/service-javadoc/01-service-complete.md`
- LSPosed native hook wiki scrape: `.agents/skills/libxposed/references/github/LSPosed-wiki/Native-Hook.md`
- Android identifier guidance: `https://developer.android.com/identity/user-data-ids`
- Android `Build`: `https://developer.android.com/reference/android/os/Build`
- Android `Build.VERSION`: `https://developer.android.com/reference/android/os/Build.VERSION`
- Android `PackageManager`: `https://developer.android.com/reference/android/content/pm/PackageManager`
- Android `TelephonyManager`: `https://developer.android.com/reference/android/telephony/TelephonyManager`
- Android `SubscriptionManager`: `https://developer.android.com/reference/android/telephony/SubscriptionManager`
- Android Advanced Protection: `https://developer.android.com/reference/kotlin/android/security/advancedprotection/AdvancedProtectionManager`
- Android Advanced Protection overview: `https://developer.android.com/privacy-and-security/advanced-protection-mode`
- Android Identity Check constant: `https://developer.android.com/reference/android/hardware/biometrics/BiometricManager.Authenticators#IDENTITY_CHECK`

---

## Current Device And Target Apps

Mobile MCP confirmed this device:

```text
id: emulator-5554
name: Medium Phone Android13
platform: android
version: 13
state: online
```

Currently installed target apps useful for smoke:

- `com.astrixforge.devicemasker`
- `com.mantle.verify`
- `flar2.devcheck`
- `org.lsposed.manager`
- `com.topjohnwu.magisk`
- `io.github.a13e300.ksuwebui`

Use Mobile MCP for device discovery, APK install, screenshots, app list, and app termination. Use `adb` for shell commands, activity launch, LSPosed/logcat evidence, permission checks, file pulls, and reboot checks because Mobile MCP does not expose shell/logcat tools.

---

## Success Criteria

- `:common` has one shared Luhn implementation used by IMEI, ICCID, and deterministic persona identifiers.
- Persona-generated ICCID values validate with Luhn.
- `ConfigSync` publishes both flat legacy keys and a parseable `DevicePersona` blob/version per enabled app.
- `PrefsHelper` can read persona values as a compatibility fallback without spoofing disabled types.
- Device profile coverage is explicit and tested: each preset field is marked as generated, synced, hooked, and emulator-validated or intentionally not hookable.
- Runtime hooks cover high-signal profile gaps: `Build.ID`, `Build.TIME`, `Build.VERSION.INCREMENTAL`, `Build.VERSION.SECURITY_PATCH`, ABI properties, NFC/5G feature checks, SIM count, and subscription count where safely hookable.
- `SubscriptionManager.getActiveSubscriptionInfoList()` is no longer a misleading no-op hook.
- Native `/proc/self/maps` hardening exists as an opt-in experimental track with kill switch and release-build proof, or is implemented only after the native scanner proves the Java hook is insufficient.
- system_server package hiding exists as an opt-in experimental track with boot-loop recovery, or is kept behind a disabled default path until boot validation passes.
- Android Advanced Protection and Identity Check are represented as diagnostics and support-bundle facts, not fake identity fields.
- A local verifier target app can read all supported surfaces and write machine-readable evidence.
- Android 13 emulator validation captures debug and release behavior using Mobile MCP plus `adb`.
- `spotless`, `detekt`, `lint`, unit tests, debug build, release build, R8 guard, and emulator smoke all pass.
- Memory Bank and public/internal docs are updated after implementation.

---

## Preflight

- [x] **Step 0.1: Read required project context**

Read these files before editing:

```powershell
Get-Content AGENTS.md
Get-Content docs/AGENTS_PROJECT_RULES.md
Get-Content common/AGENTS.md
Get-Content xposed/AGENTS.md
Get-Content .agents/skills/libxposed/SKILL.md
Get-Content docs/internal/reports/closed/research/2026-05-09/2026-05-09-device-masker-combined-research-audit.md
```

Verify:

```powershell
git status --short
```

Expected result: existing user/report changes are visible and not reverted.

- [x] **Step 0.2: Capture baseline build and quality state**

```powershell
New-Item -ItemType Directory -Force logs/build, logs/device, logs/tmp | Out-Null
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleDebug :app:assembleCiRelease --no-daemon *> logs/build/2026-05-09-hardening-baseline.txt
```

Verify:

```powershell
Select-String -Path logs/build/2026-05-09-hardening-baseline.txt -Pattern "BUILD SUCCESSFUL"
```

- [x] **Step 0.3: Capture emulator baseline**

Use Mobile MCP:

```text
mobile_list_available_devices
mobile_list_apps(device="emulator-5554")
mobile_get_screen_size(device="emulator-5554")
mobile_save_screenshot(device="emulator-5554", saveTo="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/logs/device/2026-05-09-baseline-home.png")
```

Use adb:

```powershell
$device = "emulator-5554"
adb -s $device shell getprop ro.build.version.release
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getprop ro.product.cpu.abilist
adb -s $device logcat -c
adb -s $device shell pm list packages | Select-String "mantle|devcheck|lsposed|devicemasker"
```

Verify: Android version is `13`, SDK is `33`, and `com.mantle.verify`, `flar2.devcheck`, `org.lsposed.manager`, and `com.astrixforge.devicemasker` are installed.

Commit checkpoint after preflight:

```powershell
git status --short
```

Do not commit unless the user explicitly asks during execution.

---

## Phase 1 - Shared Luhn And Generator Correctness

**Why:** The audit found that persona deterministic ICCID does not append a Luhn check digit. The current ICCID generator appends a digit but should be verified by one shared implementation instead of duplicate private algorithms.

**Files:**

- Create: `common/src/main/kotlin/com/astrixforge/devicemasker/common/util/Luhn.kt`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/PersonaIdentityValues.kt`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/generators/ICCIDGenerator.kt`
- Modify if needed: `common/src/main/kotlin/com/astrixforge/devicemasker/common/generators/IMEIGenerator.kt`
- Create: `common/src/test/kotlin/com/astrixforge/devicemasker/common/util/LuhnTest.kt`
- Modify: `common/src/test/kotlin/com/astrixforge/devicemasker/common/PersonaGeneratorTest.kt`
- Modify: `common/src/test/kotlin/com/astrixforge/devicemasker/common/generators/IMEIGeneratorTest.kt`

- [x] **Step 1.1: Add one shared Luhn helper**

Create `common/src/main/kotlin/com/astrixforge/devicemasker/common/util/Luhn.kt`:

```kotlin
package com.astrixforge.devicemasker.common.util

internal object Luhn {
    private const val DECIMAL_RADIX = 10
    private const val DOUBLE_THRESHOLD = 9

    fun appendCheckDigit(partial: String): String = partial + calculateCheckDigit(partial)

    fun calculateCheckDigit(partial: String): Int {
        require(partial.isNotEmpty()) { "Luhn input must not be empty" }
        require(partial.all(Char::isDigit)) { "Luhn input must contain digits only" }

        var sum = 0
        var shouldDouble = true
        for (index in partial.length - 1 downTo 0) {
            var digit = partial[index].digitToInt()
            if (shouldDouble) {
                digit *= 2
                if (digit > DOUBLE_THRESHOLD) digit -= DOUBLE_THRESHOLD
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return (DECIMAL_RADIX - (sum % DECIMAL_RADIX)) % DECIMAL_RADIX
    }

    fun isValid(value: String): Boolean {
        if (value.isEmpty() || value.any { !it.isDigit() }) return false

        var sum = 0
        var shouldDouble = false
        for (index in value.length - 1 downTo 0) {
            var digit = value[index].digitToInt()
            if (shouldDouble) {
                digit *= 2
                if (digit > DOUBLE_THRESHOLD) digit -= DOUBLE_THRESHOLD
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return sum % DECIMAL_RADIX == 0
    }
}
```

Verify with known IMEI:

```kotlin
assertTrue(Luhn.isValid("490154203237518"))
assertEquals("8", Luhn.calculateCheckDigit("49015420323751").toString())
```

- [x] **Step 1.2: Route deterministic persona IMEI and ICCID through `Luhn`**

In `PersonaIdentityValues.kt`, replace private `calculateLuhnCheckDigit(...)` usage with:

```kotlin
import com.astrixforge.devicemasker.common.util.Luhn

internal fun deterministicImei(
    rootSeed: String,
    label: String,
    preset: DeviceProfilePreset,
): String {
    val tac =
        if (preset.tacPrefixes.isNotEmpty()) {
            pickFrom(rootSeed, "$label:tac", preset.tacPrefixes)
        } else {
            "35000000"
        }
    val serial = deterministicDigits(rootSeed, "$label:serial", IMEI_SERIAL_LENGTH)
    return Luhn.appendCheckDigit(tac + serial)
}

internal fun deterministicIccid(rootSeed: String, label: String, carrier: Carrier): String {
    val prefix = "89${carrier.countryCode}${carrier.iccidIssuerCode}"
    val baseLength = (ICCID_LENGTH - 1).coerceAtLeast(prefix.length + 1)
    val serialLength = (baseLength - prefix.length).coerceAtLeast(1)
    return Luhn.appendCheckDigit(prefix + deterministicDigits(rootSeed, label, serialLength))
}
```

Remove the private `calculateLuhnCheckDigit(...)` from `PersonaIdentityValues.kt`.

- [x] **Step 1.3: Route `ICCIDGenerator` through `Luhn`**

In `ICCIDGenerator.kt`, import the helper:

```kotlin
import com.astrixforge.devicemasker.common.util.Luhn
```

Replace both private check-digit calls:

```kotlin
return Luhn.appendCheckDigit(base)
```

Remove duplicate constants and private `calculateLuhnCheckDigit(...)` if they become unused.

- [x] **Step 1.4: Add regression tests**

Create `LuhnTest.kt`:

```kotlin
package com.astrixforge.devicemasker.common.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuhnTest {
    @Test
    fun `known IMEI validates`() {
        assertEquals(8, Luhn.calculateCheckDigit("49015420323751"))
        assertTrue(Luhn.isValid("490154203237518"))
        assertFalse(Luhn.isValid("490154203237519"))
    }

    @Test
    fun `appended check digit validates`() {
        val generated = Luhn.appendCheckDigit("899110120000320451")
        assertTrue(Luhn.isValid(generated))
    }
}
```

Add `PersonaGeneratorTest` checks:

```kotlin
assertTrue(Luhn.isValid(persona.hardware.primaryImei))
assertTrue(Luhn.isValid(persona.subscriptions.first().iccid))
```

Add `ICCIDGenerator` checks:

```kotlin
repeat(100) {
    assertTrue(Luhn.isValid(ICCIDGenerator.generate()))
    assertTrue(Luhn.isValid(ICCIDGenerator.generate(Carrier.ALL_CARRIERS.first())))
}
```

Verify:

```powershell
.\gradlew.bat :common:testDebugUnitTest spotlessCheck detekt --no-daemon
```

---

## Phase 2 - Publish And Consume Persona Blob Safely

**Why:** `DevicePersona`, `PersonaGenerator`, and persona RemotePreferences keys already exist, but the app does not publish persona JSON and xposed does not consume it.

**Files:**

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/data/ConfigSyncSnapshotTest.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/service/ConfigSyncTest.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt`
- Modify: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelperTest.kt`
- Modify: `common/src/test/kotlin/com/astrixforge/devicemasker/common/PersonaGeneratorTest.kt`

- [x] **Step 2.1: Extend `AppSyncState` with persona data**

In `ConfigSyncHelpers.kt`, add imports:

```kotlin
import com.astrixforge.devicemasker.common.DevicePersona
import com.astrixforge.devicemasker.common.PersonaGenerator
```

Change `AppSyncState`:

```kotlin
internal data class AppSyncState(
    val packageName: String,
    val appEnabled: Boolean,
    val riskyHooksEnabled: Boolean,
    val classLookupHidingEnabled: Boolean,
    val persona: DevicePersona?,
    val spoofTypes: List<SpoofTypeSyncState>,
)
```

In `JsonConfig.syncStateFor(...)`, generate persona only when the package is truly enabled:

```kotlin
val persona = if (appEnabled) PersonaGenerator.generate(group, packageName) else null
```

Set each enabled type value from explicit/group value first, then persona:

```kotlin
val value =
    if (typeEnabled) {
        group.getValue(type)?.takeIf { it.isNotBlank() } ?: persona?.getValue(type)
    } else {
        null
    }
```

Pass `persona = persona` into `AppSyncState`.

- [x] **Step 2.2: Write persona blob/version through RemotePreferences**

In `putAppSyncState(...)`, after risky/class lookup keys:

```kotlin
if (state.persona != null) {
    putString(SharedPrefsKeys.getPersonaBlobKey(state.packageName), state.persona.toJsonString())
    putLong(SharedPrefsKeys.getPersonaVersionKey(state.packageName), state.persona.version)
} else {
    remove(SharedPrefsKeys.getPersonaBlobKey(state.packageName))
    remove(SharedPrefsKeys.getPersonaVersionKey(state.packageName))
}
```

In `ConfigSync.buildSnapshot(...)`, add persona strings and longs for enabled states:

```kotlin
state?.persona?.let { persona ->
    strings[SharedPrefsKeys.getPersonaBlobKey(packageName)] = persona.toJsonString()
    longs[SharedPrefsKeys.getPersonaVersionKey(packageName)] = persona.version
} ?: run {
    removeKeys += SharedPrefsKeys.getPersonaBlobKey(packageName)
    removeKeys += SharedPrefsKeys.getPersonaVersionKey(packageName)
}
```

Also update `removePackageSyncKeys(...)` to remove both persona keys. It currently removes only app/risky/class/type keys, so add:

```kotlin
remove(SharedPrefsKeys.getPersonaBlobKey(packageName))
remove(SharedPrefsKeys.getPersonaVersionKey(packageName))
```

- [x] **Step 2.3: Add xposed persona fallback without bypassing type enablement**

In `PrefsHelper.kt`, add:

```kotlin
import com.astrixforge.devicemasker.common.DevicePersona
```

Add helper:

```kotlin
private fun getPersonaSpoofValue(
    prefs: SharedPreferences,
    packageName: String,
    type: SpoofType,
): String? {
    val personaJson = prefs.getString(SharedPrefsKeys.getPersonaBlobKey(packageName), null)
    val persona = DevicePersona.parseOrNull(personaJson) ?: return null
    if (persona.packageName != packageName) return null
    return persona.getValue(type)?.takeIf { it.isNotBlank() }
}
```

Change `getStoredSpoofValue(...)`:

```kotlin
val stored = prefs.getString(SharedPrefsKeys.getSpoofValueKey(packageName, type), null)
return stored?.takeIf { it.isNotBlank() } ?: getPersonaSpoofValue(prefs, packageName, type)
```

Do not return persona values when `typeEnabled` is false.

- [x] **Step 2.4: Add snapshot tests**

In `ConfigSyncSnapshotTest`, add one enabled-app test:

```kotlin
val personaJson = snapshot.strings.getValue(SharedPrefsKeys.getPersonaBlobKey("com.example.enabled"))
val persona = DevicePersona.parse(personaJson)
assertEquals("com.example.enabled", persona.packageName)
assertEquals(group.id, persona.groupId)
assertTrue(snapshot.longs.containsKey(SharedPrefsKeys.getPersonaVersionKey("com.example.enabled")))
```

Add one disabled-app test:

```kotlin
assertTrue(snapshot.removeKeys.contains(SharedPrefsKeys.getPersonaBlobKey("com.example.disabled")))
assertTrue(snapshot.removeKeys.contains(SharedPrefsKeys.getPersonaVersionKey("com.example.disabled")))
```

Add `PrefsHelperTest` cases:

```kotlin
assertEquals("490154203237518", PrefsHelper.getStoredSpoofValue(prefs, "com.example.app", SpoofType.IMEI))
assertNull(PrefsHelper.getStoredSpoofValue(prefsWithDisabledType, "com.example.app", SpoofType.IMEI))
assertNull(PrefsHelper.getStoredSpoofValue(prefsWithMismatchedPersonaPackage, "com.example.app", SpoofType.IMEI))
```

Verify:

```powershell
.\gradlew.bat :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest spotlessCheck detekt --no-daemon
```

---

## Phase 3 - Profile Coverage Matrix And Runtime Hook Gaps

**Why:** `DeviceProfilePreset` exposes fields that current hooks do not fully apply. Implement high-signal fields and document anything intentionally not hookable.

**Files:**

- Create: `docs/internal/reports/closed/validation/2026-05-09/2026-05-09-device-profile-runtime-coverage-matrix.md`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/DeviceProfilePreset.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHooker.kt`
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/DeviceHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SubscriptionHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemHookerProfileCoverageTest.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemFeatureHookerTest.kt`
- Modify: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt`

- [x] **Step 3.1: Create coverage matrix**

Create `docs/internal/reports/closed/validation/2026-05-09/2026-05-09-device-profile-runtime-coverage-matrix.md` with this table:

```markdown
# Device Profile Runtime Coverage Matrix

| Preset field | Generated | Synced | Hooked | Emulator validated | Notes |
| --- | --- | --- | --- | --- | --- |
| `brand` | yes | persona + flat profile id | `Build.BRAND`, system properties | pending | Restart-sensitive static field. |
| `manufacturer` | yes | persona + flat profile id | `Build.MANUFACTURER`, system properties | pending | Restart-sensitive static field. |
| `model` | yes | persona + flat profile id | `Build.MODEL`, system properties, WebView UA | pending | Restart target app after profile change. |
| `device` | yes | persona + flat profile id | `Build.DEVICE`, system properties | pending | Restart-sensitive static field. |
| `product` | yes | persona + flat profile id | `Build.PRODUCT`, system properties | pending | Restart-sensitive static field. |
| `board` | yes | persona + flat profile id | `Build.BOARD`, `Build.HARDWARE`, system properties | pending | Board and hardware are linked. |
| `fingerprint` | yes | persona + flat profile id | `Build.FINGERPRINT`, system properties | pending | Android docs say fingerprint identifies a build. |
| `securityPatch` | yes | persona + flat profile id | `Build.VERSION.SECURITY_PATCH`, `ro.build.version.security_patch` | pending | API 23+. |
| `buildTime` | yes | persona + flat profile id | `Build.TIME`, `ro.build.date.utc` | pending | `Build.TIME` is millis; property may be seconds. |
| `buildId` | yes | persona + flat profile id | `Build.ID`, `ro.build.id` | pending | Must match fingerprint. |
| `incremental` | yes | persona + flat profile id | `Build.VERSION.INCREMENTAL`, `ro.build.version.incremental` | pending | Must match fingerprint. |
| `supportedAbis` | yes | persona + flat profile id | `Build.SUPPORTED_ABIS`, `SUPPORTED_64_BIT_ABIS`, `SUPPORTED_32_BIT_ABIS`, ABI properties | pending | Do not claim impossible CPU emulation. |
| `tacPrefixes` | yes | persona-generated IMEI | generation only | pending | Used by `PersonaGenerator`; not a runtime hook field. |
| `simCount` | yes | persona + flat profile id | `TelephonyManager.getSimCount`, `SubscriptionManager.getActiveSubscriptionInfoCount` | pending | Do not fabricate full subscription list until separately proven. |
| `hasNfc` | yes | persona + flat profile id | `PackageManager.hasSystemFeature` | pending | Feature constants only. |
| `has5G` | yes | persona + flat profile id | 5G feature constants + network type mapping | pending | Must not override unsupported telephony states blindly. |
```

- [x] **Step 3.2: Extend `SystemHooker` profile fields**

Add field mappings for `Build`:

```kotlin
"ID" to preset.buildId,
"TIME" to preset.buildTime,
"SUPPORTED_ABIS" to preset.supportedAbis.toTypedArray(),
"SUPPORTED_64_BIT_ABIS" to preset.supportedAbis.filter { it.contains("64") }.toTypedArray(),
"SUPPORTED_32_BIT_ABIS" to preset.supportedAbis.filterNot { it.contains("64") }.toTypedArray(),
```

Add nested `Build.VERSION` mappings:

```kotlin
private fun applyBuildVersionFieldOverrides(cl: ClassLoader, preset: DeviceProfilePreset) {
    val versionClass = cl.loadClassOrNull("android.os.Build\$VERSION") ?: return
    mapOf(
        "SECURITY_PATCH" to preset.securityPatch,
        "INCREMENTAL" to preset.incremental,
    ).forEach { (fieldName, value) ->
        if (value.isNotEmpty()) setStaticField(versionClass, fieldName, value)
    }
}
```

Use a single typed setter:

```kotlin
private fun setStaticField(targetClass: Class<*>, fieldName: String, value: Any) {
    runCatching {
        val field = targetClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }.onFailure { t ->
        DualLog.warn("SystemHooker", "Could not set ${targetClass.name}.$fieldName", t)
    }
}
```

Add system property mappings:

```kotlin
put("ro.build.id", preset.buildId)
put("ro.build.version.incremental", preset.incremental)
put("ro.build.version.security_patch", preset.securityPatch)
put("ro.product.cpu.abilist", preset.supportedAbis.joinToString(","))
put("ro.product.cpu.abilist64", preset.supportedAbis.filter { it.contains("64") }.joinToString(","))
put("ro.product.cpu.abilist32", preset.supportedAbis.filterNot { it.contains("64") }.joinToString(","))
```

For `ro.build.date.utc`, convert millis to seconds:

```kotlin
if (preset.buildTime > 0L) put("ro.build.date.utc", (preset.buildTime / 1000L).toString())
```

Verify static tests assert all new keys are present.

- [x] **Step 3.3: Add `SystemFeatureHooker`**

Create `SystemFeatureHooker.kt`:

```kotlin
package com.astrixforge.devicemasker.xposed.hooker

import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

object SystemFeatureHooker : BaseSpoofHooker("SystemFeatureHooker") {
    private val NFC_FEATURES = setOf(
        PackageManager.FEATURE_NFC,
        PackageManager.FEATURE_NFC_HOST_CARD_EMULATION,
    )

    private val FIVE_G_FEATURES = setOf(
        "android.hardware.telephony.radio.access",
        "android.hardware.telephony.euicc",
    )

    fun hook(cl: ClassLoader, xi: XposedInterface, prefs: SharedPreferences, pkg: String) {
        val preset = activePreset(prefs, pkg) ?: return
        val pmClass = cl.loadClassOrNull("android.app.ApplicationPackageManager") ?: return
        hookHasSystemFeature(pmClass, xi, preset, pkg)
    }

    private fun activePreset(prefs: SharedPreferences, pkg: String): DeviceProfilePreset? {
        val presetId = getConfiguredSpoofValue(prefs, pkg, SpoofType.DEVICE_PROFILE) ?: return null
        return DeviceProfilePreset.findById(presetId)
    }

    private fun hookHasSystemFeature(
        pmClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset,
        pkg: String,
    ) {
        safeHook("PackageManager.hasSystemFeature(String)") {
            pmClass.methodOrNull("hasSystemFeature", String::class.java)?.let { method ->
                xi.hook(method).intercept(stableHooker { chain ->
                    val original = chain.proceed()
                    val feature = chain.args.firstOrNull() as? String ?: return@stableHooker original
                    val mapped = mappedFeature(feature, preset) ?: return@stableHooker original
                    reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                    mapped
                })
                xi.deoptimize(method)
            }
        }
    }

    private fun mappedFeature(feature: String, preset: DeviceProfilePreset): Boolean? =
        when {
            feature in NFC_FEATURES -> preset.hasNfc
            feature in FIVE_G_FEATURES -> preset.has5G
            else -> null
        }
}
```

If Detekt flags the Android constants or feature list, move feature sets to a private `object ProfileFeatureMappings`.

- [x] **Step 3.4: Hook SIM count and subscription count**

In `DeviceHooker.kt`, add a `TelephonyManager.getSimCount()` hook:

```kotlin
private fun hookSimCount(
    tmClass: Class<*>,
    xi: XposedInterface,
    prefs: SharedPreferences,
    pkg: String,
) {
    safeHook("TelephonyManager.getSimCount()") {
        tmClass.methodOrNull("getSimCount")?.let { method ->
            xi.hook(method).intercept(stableHooker { chain ->
                val original = chain.proceed()
                val presetId = getConfiguredSpoofValue(prefs, pkg, SpoofType.DEVICE_PROFILE)
                    ?: return@stableHooker original
                val preset = DeviceProfilePreset.findById(presetId) ?: return@stableHooker original
                reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                preset.simCount
            })
            xi.deoptimize(method)
        }
    }
}
```

In `SubscriptionHooker.kt`, replace the no-op list hook with an explicit count hook:

```kotlin
private fun hookActiveSubscriptionInfoCount(
    cl: ClassLoader,
    xi: XposedInterface,
    prefs: SharedPreferences,
    pkg: String,
) {
    val smClass = cl.loadClassOrNull("android.telephony.SubscriptionManager") ?: return
    safeHook("SubscriptionManager.getActiveSubscriptionInfoCount()") {
        smClass.methodOrNull("getActiveSubscriptionInfoCount")?.let { method ->
            xi.hook(method).intercept(stableHooker { chain ->
                val original = chain.proceed()
                val presetId = getConfiguredSpoofValue(prefs, pkg, SpoofType.DEVICE_PROFILE)
                    ?: return@stableHooker original
                val preset = DeviceProfilePreset.findById(presetId) ?: return@stableHooker original
                reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                preset.simCount
            })
            xi.deoptimize(method)
        }
    }
}
```

Remove `hookSubscriptionManagerList(...)` unless it is renamed and documented as an intentional deoptimization hook. The preferred path is removal because a pass-through hook is random churn.

- [x] **Step 3.5: Register `SystemFeatureHooker`**

In `XposedEntry.kt`, import and register it after `SystemHooker`:

```kotlin
hookSafely(hookPackage, "SystemFeatureHooker") {
    SystemFeatureHooker.hook(cl, this, prefs, hookPackage)
}
```

Verify registration order keeps `AntiDetectHooker` first.

- [x] **Step 3.6: Tests**

Add static tests that fail if:

- `SystemHooker.kt` does not mention `SECURITY_PATCH`, `INCREMENTAL`, `SUPPORTED_ABIS`, `ro.build.id`, and `ro.product.cpu.abilist`.
- `SubscriptionHooker.kt` still contains `chain.proceed() })` for `getActiveSubscriptionInfoList`.
- `XposedEntry.kt` does not register `SystemFeatureHooker`.
- `SystemFeatureHooker.kt` uses direct `.intercept {`.

Verify:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest spotlessCheck detekt --no-daemon
```

---

## Phase 4 - Profile Restart Semantics And Diagnostics

**Why:** Some hooks read RemotePreferences live, but profile-derived static fields are registration-time and target-restart sensitive. The app and docs must be honest.

**Files:**

- Modify: `docs/public/ARCHITECTURE.md`
- Modify: `xposed/AGENTS.md`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilder.kt`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEvent.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilderTest.kt`

- [ ] **Step 4.1: Document live vs restart-required behavior**

In `docs/public/ARCHITECTURE.md`, add a concise section:

```markdown
## Live Config Semantics

Flat per-type values are read from RemotePreferences inside most hook callbacks, so enabled target apps can see changed values after the app commits config. Profile-derived static fields are restart-sensitive because apps often read `Build.*` and `Build.VERSION.*` during process startup and ART may inline or cache them. After changing `DEVICE_PROFILE`, force-stop and relaunch the target app before judging hook behavior.
```

In `xposed/AGENTS.md`, update the hook table notes for `SystemHooker`, `SensorHooker`, and `WebViewHooker` to state "profile changes require target restart".

- [x] **Step 4.2: Add diagnostics snapshot fields**

Add a diagnostic snapshot field:

```kotlin
val profileRestartRequired: Boolean
```

Set it true when `DEVICE_PROFILE` is enabled for any scoped app. Add support-bundle text:

```text
Profile changes require target force-stop/relaunch for Build/SystemProperties/WebView/Sensor surfaces.
```

Verify with unit tests that support bundle contains this string when a device profile is active.

---

## Phase 5 - Android 16 Security Posture Diagnostics

**Why:** Advanced Protection and Identity Check are real Android 16/API 36 surfaces, but they are not normal device identity fields. Add diagnostics first.

**Files:**

- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/AdvancedProtectionDiagnostics.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/DiagnosticSnapshotBuilder.kt`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/diagnostics/DiagnosticEvent.kt`
- Create: `app/src/test/kotlin/com/astrixforge/devicemasker/service/diagnostics/AdvancedProtectionDiagnosticsTest.kt`

- [x] **Step 5.1: Add permission**

In `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.QUERY_ADVANCED_PROTECTION_MODE" />
```

- [x] **Step 5.2: Add SDK-gated diagnostic helper**

Create `AdvancedProtectionDiagnostics.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi

data class AdvancedProtectionSnapshot(
    val apiAvailable: Boolean,
    val permissionGranted: Boolean,
    val enabled: Boolean?,
    val error: String?,
)

object AdvancedProtectionDiagnostics {
    fun read(context: Context): AdvancedProtectionSnapshot =
        if (Build.VERSION.SDK_INT >= 36) {
            readApi36(context)
        } else {
            AdvancedProtectionSnapshot(
                apiAvailable = false,
                permissionGranted = false,
                enabled = null,
                error = null,
            )
        }

    @RequiresApi(36)
    private fun readApi36(context: Context): AdvancedProtectionSnapshot {
        val permissionGranted =
            context.checkSelfPermission(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE) ==
                PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            return AdvancedProtectionSnapshot(
                apiAvailable = true,
                permissionGranted = false,
                enabled = null,
                error = "QUERY_ADVANCED_PROTECTION_MODE not granted",
            )
        }

        return runCatching {
            val service =
                context.getSystemService(
                    android.security.advancedprotection.AdvancedProtectionManager::class.java
                )
            AdvancedProtectionSnapshot(
                apiAvailable = true,
                permissionGranted = true,
                enabled = service?.isAdvancedProtectionEnabled,
                error = null,
            )
        }.getOrElse { throwable ->
            AdvancedProtectionSnapshot(
                apiAvailable = true,
                permissionGranted = true,
                enabled = null,
                error = throwable.javaClass.simpleName,
            )
        }
    }
}
```

If compile SDK naming differs, use the exact API signature from Google Developer Knowledge and fix compile errors, not reflection first.

- [x] **Step 5.3: Add Identity Check as informational only**

Add support-bundle text:

```text
Identity Check is Android biometric/security policy state, not a spoofed identity field.
```

Do not hook or fake Identity Check in this phase.

Verify:

```powershell
.\gradlew.bat :app:testDebugUnitTest lint spotlessCheck detekt --no-daemon
```

On Android 13 emulator, expected diagnostic result:

```text
apiAvailable=false
enabled=null
```

---

## Phase 6 - Advanced Anti-Detection Tracks

This phase implements advanced work without pretending it is risk-free. Both tracks must be off by default and controlled by explicit opt-ins.

### Phase 6A - Native `/proc/self/maps` Redaction

**Why:** Java `BufferedReader.readLine()` filtering does not stop native code that calls libc directly.

**Files:**

- Modify: `app/build.gradle.kts`
- Modify: `xposed/build.gradle.kts`
- Create: `xposed/src/main/cpp/native_hook.h`
- Create: `xposed/src/main/cpp/native_maps_redactor.cpp`
- Create: `xposed/src/main/cpp/CMakeLists.txt`
- Create: `xposed/src/main/assets/native_init`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/PrefsHelper.kt`
- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/NativeMapsRedactionConfigTest.kt`

- [ ] **Step 6A.1: Add config keys**

In `SharedPrefsKeys.kt`, add:

```kotlin
private const val PREFIX_NATIVE_MAPS_REDACTION_ENABLED = "native_maps_redaction_enabled_"

fun getNativeMapsRedactionEnabledKey(packageName: String): String {
    return "$PREFIX_NATIVE_MAPS_REDACTION_ENABLED${sanitize(packageName)}"
}
```

Add it to `VALID_KEY_REGEX`.

In `AppConfig`, add a default-false field:

```kotlin
val nativeMapsRedactionEnabled: Boolean = false
```

In `ConfigSyncHelpers.kt`, only sync this key when both `riskyHooksEnabled` and the new per-app flag are true.

- [ ] **Step 6A.2: Wire native build**

In `xposed/build.gradle.kts`, add CMake under `android`:

```kotlin
externalNativeBuild {
    cmake { path = file("src/main/cpp/CMakeLists.txt") }
}

defaultConfig {
    externalNativeBuild {
        cmake {
            cppFlags += listOf("-std=c++20", "-fvisibility=hidden")
        }
    }
}
```

In `app/build.gradle.kts`, keep native libraries packaged and debuggable:

```kotlin
packaging {
    jniLibs {
        keepDebugSymbols += "**/libdevicemasker_native.so"
    }
}
```

Create `xposed/src/main/assets/native_init`:

```text
libdevicemasker_native.so
```

- [ ] **Step 6A.3: Implement minimal native hook**

Use the LSPosed native hook header shape from `.agents/skills/libxposed/references/github/LSPosed-wiki/Native-Hook.md`.

`native_hook.h` must contain:

```cpp
#pragma once

#include <cstdint>

typedef int (*HookFunType)(void *func, void *replace, void **backup);
typedef int (*UnhookFunType)(void *func);
typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

typedef struct {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
} NativeAPIEntries;
```

`native_maps_redactor.cpp` must export:

```cpp
extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries);
```

First implementation should hook only `fopen`/`fopen64` for `/proc/self/maps` and return a redacted temp stream. Do not hook `open`, `read`, or `readlink` in the first commit. That avoids a broad native syscall net before proof.

Redaction patterns:

```cpp
static constexpr const char* kHiddenPatterns[] = {
    "libxposed",
    "liblspd",
    "libriru",
    "libsandhook",
    "libpine",
    "libwhale",
    "libdobby",
    "libsubstrate",
};
```

Crash safety:

- If temp stream creation fails, call the original `fopen`.
- If filename is null, call the original `fopen`.
- If filename is not exactly `/proc/self/maps`, call the original `fopen`.

- [ ] **Step 6A.4: Load native library only when enabled**

In `AntiDetectHooker.kt`, before Java maps hook:

```kotlin
if (PrefsHelper.isNativeMapsRedactionEnabled(prefs, pkg)) {
    runCatching { System.loadLibrary("devicemasker_native") }
        .onFailure { DualLog.warn(TAG, "Native maps redaction library failed to load", it) }
}
```

This requires passing `prefs` into `AntiDetectHooker.hook(...)`. Update `XposedEntry`:

```kotlin
AntiDetectHooker.hook(cl, this, prefs, hookPackage)
```

- [ ] **Step 6A.5: Native scanner verifier**

Add the verifier app in Phase 7 with native code that reads `/proc/self/maps` through libc. The scanner must print:

```json
{"nativeMapsContainsXposed":true}
```

when native redaction is disabled and:

```json
{"nativeMapsContainsXposed":false}
```

when native redaction is enabled.

Verify:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleCiRelease --no-daemon
adb -s emulator-5554 logcat -c
```

Then perform Phase 9 emulator validation.

### Phase 6B - system_server Package Visibility Hardening

**Why:** App-process `ApplicationPackageManager` hiding is useful but does not cover system_server-side PackageManagerService paths.

**Files:**

- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- Modify: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Create: `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServerPackageHooker.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/SystemServerPackageHookerTest.kt`

- [ ] **Step 6B.1: Add disabled-by-default global and per-app gates**

Add keys:

```kotlin
const val KEY_SYSTEM_SERVER_PACKAGE_HIDING_ENABLED = "system_server_package_hiding_enabled"
private const val PREFIX_SYSTEM_SERVER_PACKAGE_HIDING_ENABLED = "system_server_package_hiding_enabled_"
```

Per-app must also require `riskyHooksEnabled`.

- [ ] **Step 6B.2: Implement discovery-only first**

In `XposedEntry.onSystemServerStarting(...)`, read RemotePreferences and return unless the global key is true:

```kotlin
val prefs = getRemotePreferences(PREFS_GROUP)
if (!prefs.getBoolean(SharedPrefsKeys.KEY_SYSTEM_SERVER_PACKAGE_HIDING_ENABLED, false)) {
    log(Log.INFO, TAG, "system_server package hiding disabled", null)
    return
}
SystemServerPackageHooker.hook(param.classLoader, this, prefs)
```

First commit of `SystemServerPackageHooker` should log candidate class/method discovery only:

```kotlin
private val candidateClasses = listOf(
    "com.android.server.pm.PackageManagerService",
    "com.android.server.pm.ComputerEngine",
)
```

Do not alter return values until discovery logs are captured on Android 13.

- [ ] **Step 6B.3: Implement one method at a time**

After discovery proves exact API 33 method signatures, hook one method first:

- `getPackageInfoInternal(...)` if present.
- Return original when caller package is not an enabled target.
- Return original when hidden package is not one of Device Masker, LSPosed Manager, Magisk, or KSU.
- Throw the same `NameNotFoundException` behavior only if the original app-process hook already does so for the same package.

Do not hook all PackageManagerService methods in one patch. That would be random churn and boot-loop risk.

- [ ] **Step 6B.4: Boot-loop recovery**

Before enabling this on emulator:

```powershell
adb -s emulator-5554 shell su -c "settings put global device_masker_system_server_hiding 0"
adb -s emulator-5554 shell getprop sys.boot_completed
```

Keep this recovery command in `docs/internal/reports/closed/validation/2026-05-09/2026-05-09-device-profile-runtime-coverage-matrix.md`:

```powershell
adb -s emulator-5554 shell su -c "pm clear com.astrixforge.devicemasker"
adb -s emulator-5554 reboot
```

Verify boot after enabling:

```powershell
adb -s emulator-5554 reboot
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 shell getprop sys.boot_completed
adb -s emulator-5554 logcat -d | Select-String "DeviceMasker|SystemServerPackageHooker|FATAL EXCEPTION"
```

---

## Phase 7 - Local Hook Verifier Target App

**Why:** Mantle and DevCheck are useful smoke apps, but a local verifier gives exact values for every feature and can prove pass-through, enabled, disabled, malformed, and release/R8 behavior.

**Files:**

- Modify: `settings.gradle.kts`
- Create: `verifier/build.gradle.kts`
- Create: `verifier/src/main/AndroidManifest.xml`
- Create: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/MainActivity.kt`
- Create: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/IdentifierSnapshot.kt`
- Create: `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/IdentifierReaders.kt`
- Create: `verifier/src/main/cpp/maps_scanner.cpp`
- Create: `verifier/src/androidTest/kotlin/com/astrixforge/devicemasker/verifier/IdentifierReadersInstrumentedTest.kt`

- [x] **Step 7.1: Add verifier module**

In `settings.gradle.kts`:

```kotlin
include(":verifier")
```

Create `verifier/build.gradle.kts` as an Android application with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.astrixforge.devicemasker.verifier"
    compileSdk = rootProject.ext.get("compileSdk") as Int

    defaultConfig {
        applicationId = "com.astrixforge.devicemasker.verifier"
        minSdk = rootProject.ext.get("minSdk") as Int
        targetSdk = rootProject.ext.get("targetSdk") as Int
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [x] **Step 7.2: Add verifier readers**

`IdentifierSnapshot.kt`:

```kotlin
package com.astrixforge.devicemasker.verifier

import kotlinx.serialization.Serializable

@Serializable
data class IdentifierSnapshot(
    val packageName: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val fingerprint: String,
    val buildId: String,
    val incremental: String,
    val securityPatch: String,
    val buildTime: Long,
    val supportedAbis: List<String>,
    val androidId: String?,
    val simCount: Int?,
    val activeSubscriptionInfoCount: Int?,
    val hasNfc: Boolean,
    val hasFeatureTelephony: Boolean,
    val javaMapsContainsXposed: Boolean,
    val nativeMapsContainsXposed: Boolean?,
)
```

`IdentifierReaders.kt` must read:

- `Build.MODEL`, `MANUFACTURER`, `BRAND`, `FINGERPRINT`, `ID`, `TIME`, `SUPPORTED_ABIS`
- `Build.VERSION.INCREMENTAL`, `Build.VERSION.SECURITY_PATCH`
- `Settings.Secure.ANDROID_ID`
- `TelephonyManager.getSimCount()`
- `SubscriptionManager.getActiveSubscriptionInfoCount()`
- `PackageManager.hasSystemFeature(PackageManager.FEATURE_NFC)`
- Java `/proc/self/maps`
- Native `/proc/self/maps` when Phase 6A is implemented

Log one line:

```text
DeviceMaskerVerifier: {"packageName":"com.astrixforge.devicemasker.verifier",...}
```

- [x] **Step 7.3: Build and install verifier**

```powershell
.\gradlew.bat :verifier:assembleDebug --no-daemon
```

Use Mobile MCP:

```text
mobile_install_app(device="emulator-5554", path="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/verifier/build/outputs/apk/debug/verifier-debug.apk")
```

Use adb:

```powershell
adb -s emulator-5554 shell am start -n com.astrixforge.devicemasker.verifier/.MainActivity
adb -s emulator-5554 logcat -d | Select-String "DeviceMaskerVerifier"
```

Capture screenshot:

```text
mobile_save_screenshot(device="emulator-5554", saveTo="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/logs/device/2026-05-09-verifier-baseline.png")
```

---

## Phase 8 - App UI And Config Controls

**Why:** Advanced risky tracks need explicit user control. Keep controls compact and hidden from normal flows unless advanced/risky mode is opened.

**Files:**

- Modify: `common/src/main/kotlin/com/astrixforge/devicemasker/common/AppConfig.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/tabs/AppsTabContent.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingViewModel.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/GroupSpoofingViewModelTest.kt`

- [ ] **Step 8.1: Add flags to app config**

Add default-false flags:

```kotlin
val nativeMapsRedactionEnabled: Boolean = false,
val systemServerPackageHidingEnabled: Boolean = false,
```

Keep `riskyHooksEnabled` as parent gate. If `riskyHooksEnabled` is false, both flags must sync false.

- [ ] **Step 8.2: Add compact advanced controls**

In app scope UI, under the existing risky-hook area:

- Risky hooks toggle.
- Class lookup hiding toggle.
- Native maps redaction toggle.
- system_server package hiding toggle.

Do not add explanatory paragraphs in-app. Use short labels and tooltips/dialog title only:

```text
Native maps redaction
system_server package hiding
```

Tests must confirm:

- Child flags turn false when `riskyHooksEnabled` is false.
- Config sync writes child keys false when parent is false.
- Existing configs deserialize with both new flags false.

---

## Phase 9 - End-To-End Android 13 Emulator Validation

Run this after every stable phase and again after all phases.

### 9A - Build And Install

```powershell
$device = "emulator-5554"
$root = "C:\Users\akila\OneDrive\Desktop\OSS\MobileApps\Spoofer\devicemasker"
Set-Location $root
adb -s $device logcat -c
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest :verifier:testDebugUnitTest lint assembleDebug :app:assembleCiRelease :verifier:assembleDebug --no-daemon *> logs/build/2026-05-09-hardening-full-gate.txt
```

Verify:

```powershell
Select-String logs/build/2026-05-09-hardening-full-gate.txt -Pattern "BUILD SUCCESSFUL"
```

Install with Mobile MCP:

```text
mobile_install_app(device="emulator-5554", path="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/app/build/outputs/apk/debug/app-debug.apk")
mobile_install_app(device="emulator-5554", path="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/verifier/build/outputs/apk/debug/verifier-debug.apk")
```

### 9B - Configure LSPosed Scope

Manual or LSPosed UI validation is required unless an existing script is present. Scope must include:

- `android`
- `com.astrixforge.devicemasker.verifier`
- `com.mantle.verify`
- `flar2.devcheck`

Capture LSPosed UI screenshot with Mobile MCP:

```text
mobile_save_screenshot(device="emulator-5554", saveTo="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/logs/device/2026-05-09-lsposed-scope.png")
```

### 9C - Configure Device Masker

Use the app UI or existing config import path to create:

- Module enabled.
- Group `E2E Pixel`.
- Device profile `pixel_8_pro`.
- Target app `com.astrixforge.devicemasker.verifier` enabled.
- Spoof types enabled: `DEVICE_PROFILE`, `ANDROID_ID`, `IMEI`, `IMSI`, `ICCID`, `PHONE_NUMBER`, `SIM_COUNTRY_ISO`, `NETWORK_COUNTRY_ISO`, `SIM_OPERATOR_NAME`, `CARRIER_NAME`, `CARRIER_MCC_MNC`, `WIFI_MAC`, `WIFI_SSID`, `WIFI_BSSID`, `ADVERTISING_ID`, `MEDIA_DRM_ID`, `TIMEZONE`, `LOCALE`.
- Risky hooks enabled only when validating Phase 6.

Capture app screenshot:

```text
mobile_save_screenshot(device="emulator-5554", saveTo="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/logs/device/2026-05-09-devicemasker-config.png")
```

### 9D - Force Stop And Launch Verifier

```powershell
$target = "com.astrixforge.devicemasker.verifier"
adb -s $device shell am force-stop $target
adb -s $device shell am start -n "$target/.MainActivity"
Start-Sleep -Seconds 3
adb -s $device logcat -d > logs/device/2026-05-09-verifier-spoofed-logcat.txt
```

Check required evidence:

```powershell
Select-String logs/device/2026-05-09-verifier-spoofed-logcat.txt -Pattern "XposedEntry loaded|Target package selected|All hooks registered|Spoof event|DeviceMaskerVerifier"
```

Expected verifier facts for `pixel_8_pro`:

```text
model=Pixel 8 Pro
manufacturer=Google
brand=google
buildId=AD1A.240530.047
securityPatch=2024-12-05
simCount=1
hasNfc=true
```

Write parsed JSON to:

```powershell
Select-String logs/device/2026-05-09-verifier-spoofed-logcat.txt -Pattern "DeviceMaskerVerifier" | Set-Content logs/device/2026-05-09-verifier-spoofed-json.txt
```

### 9E - Pass-Through And Safety Tests

Disabled app:

```powershell
adb -s $device shell am force-stop $target
```

Disable app in Device Masker, relaunch verifier, then capture:

```powershell
adb -s $device shell am start -n "$target/.MainActivity"
Start-Sleep -Seconds 3
adb -s $device logcat -d > logs/device/2026-05-09-verifier-disabled-logcat.txt
Select-String logs/device/2026-05-09-verifier-disabled-logcat.txt -Pattern "DeviceMaskerVerifier|Spoof event"
```

Expected: verifier values match real emulator for disabled state; no new spoof events for the target after disable.

Malformed config:

- Manually corrupt one RemotePreferences value through the app test helper or by importing malformed config.
- Relaunch target.
- Expected: original values returned, no crash, warning log if malformed value is rejected.

Missing config:

- Clear Device Masker app data.
- Relaunch verifier.
- Expected: hooks do not register for target or return originals, no crash.

### 9F - Existing App Smoke

```powershell
$targets = @("com.mantle.verify", "flar2.devcheck")
foreach ($pkg in $targets) {
    adb -s $device shell am force-stop $pkg
    adb -s $device shell monkey -p $pkg -c android.intent.category.LAUNCHER 1
    Start-Sleep -Seconds 5
}
adb -s $device logcat -d > logs/device/2026-05-09-mantle-devcheck-smoke.txt
Select-String logs/device/2026-05-09-mantle-devcheck-smoke.txt -Pattern "com.mantle.verify|flar2.devcheck|All hooks registered|Spoof event|FATAL EXCEPTION|AbstractMethodError"
```

Expected:

- `All hooks registered` for both apps when scoped/enabled.
- No `AbstractMethodError`.
- No target crash.

### 9G - Release/R8 Runtime Smoke

Install release/ciRelease:

```text
mobile_install_app(device="emulator-5554", path="C:/Users/akila/OneDrive/Desktop/OSS/MobileApps/Spoofer/devicemasker/app/build/outputs/apk/ciRelease/app-ciRelease.apk")
```

If output APK name differs, locate it:

```powershell
Get-ChildItem app/build/outputs/apk/ciRelease -Filter *.apk -Recurse
```

Repeat verifier and Mantle smoke. Save logs:

```powershell
adb -s $device logcat -d > logs/device/2026-05-09-release-r8-smoke.txt
Select-String logs/device/2026-05-09-release-r8-smoke.txt -Pattern "AbstractMethodError|FATAL EXCEPTION|All hooks registered|Spoof event|DeviceMaskerVerifier"
```

Expected: release R8 behaves like debug for supported hooks.

### 9H - Reboot Validation

```powershell
adb -s $device reboot
adb -s $device wait-for-device
Start-Sleep -Seconds 30
adb -s $device shell getprop sys.boot_completed
adb -s $device shell monkey -p com.astrixforge.devicemasker.verifier -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 5
adb -s $device logcat -d > logs/device/2026-05-09-post-reboot-smoke.txt
Select-String logs/device/2026-05-09-post-reboot-smoke.txt -Pattern "DeviceMasker|DeviceMaskerVerifier|FATAL EXCEPTION"
```

Expected: emulator boots, Device Masker does not break startup, verifier still works after LSPosed reload.

---

## Phase 10 - CI And Regression Gates

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml` only if release artifacts need verifier exclusion.
- Modify: `app/src/test/java/com/astrixforge/devicemasker/ReleaseBuildSafetyTest.kt`
- Modify: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/hooker/R8HookerAbiTest.kt`

- [x] **Step 10.1: Add verifier to local CI gate**

CI should build verifier debug but never publish it as a release artifact:

```yaml
- name: Build verifier debug
  run: ./gradlew :verifier:assembleDebug --build-cache --stacktrace
```

Do not upload verifier APK to public GitHub releases.

- [x] **Step 10.2: Add static release safety checks**

`ReleaseBuildSafetyTest` must assert:

- `app/build.gradle.kts` still has release minify enabled.
- `xposed` source contains no direct `.intercept {`.
- `SystemFeatureHooker` is registered.
- Native maps redaction is disabled by default.
- system_server hiding is disabled by default.
- `verifier` is not included in release upload paths.

Verify:

```powershell
.\gradlew.bat spotlessCheck detekt lint test assembleDebug :app:assembleCiRelease :verifier:assembleDebug --no-daemon
```

---

## Phase 11 - Documentation And Memory Bank

**Files:**

- Modify: `docs/public/ARCHITECTURE.md`
- Modify: `docs/AGENTS_PROJECT_RULES.md`
- Modify: `AGENTS.md` only if permanent guardrails change.
- Modify: `common/AGENTS.md`
- Modify: `xposed/AGENTS.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Modify if needed: `memory-bank/systemPatterns.md`
- Modify: `docs/internal/reports/closed/research/2026-05-09/2026-05-09-device-masker-combined-research-audit.md`
- Modify: `docs/internal/reports/closed/validation/2026-05-09/2026-05-09-device-profile-runtime-coverage-matrix.md`

- [x] **Step 11.1: Update docs with real implemented state**

Document:

- Persona blob is now published and read as fallback.
- Flat keys remain compatibility/stability contract.
- Profile static fields are restart-sensitive.
- Native maps redaction is experimental/off by default.
- system_server package hiding is experimental/off by default.
- Android Advanced Protection is diagnostics only.
- Identity Check is diagnostics only.
- Verifier module is a local validation app, not a shipped user feature.

- [x] **Step 11.2: Update Memory Bank**

Read every file:

```powershell
Get-Content memory-bank/projectbrief.md
Get-Content memory-bank/productContext.md
Get-Content memory-bank/systemPatterns.md
Get-Content memory-bank/techContext.md
Get-Content memory-bank/activeContext.md
Get-Content memory-bank/progress.md
```

Update at least:

- `activeContext.md`: current implementation and validation evidence.
- `progress.md`: what now works and what remains risky/experimental.
- `systemPatterns.md`: persona RemotePreferences contract and advanced hook gates.
- `techContext.md`: verifier module and native build toolchain if Phase 6A is implemented.

---

## Phase 12 - Final Verification And Review

- [x] **Step 12.1: Full local command gate**

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest :verifier:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon *> logs/build/2026-05-09-hardening-final-gate.txt
```

Verify:

```powershell
Select-String logs/build/2026-05-09-hardening-final-gate.txt -Pattern "BUILD SUCCESSFUL"
```

- [x] **Step 12.2: Static safety audit**

```powershell
rg -n "\.intercept\s*\{|intercept\s*\{" xposed/src/main/kotlin
rg -n "Random|SecureRandom|Timber|app-private|config\\.json|hardcoded" xposed/src/main/kotlin
rg -n "persona_blob_|persona_version_|native_maps_redaction|system_server_package_hiding" common app xposed
```

Expected:

- No direct `.intercept {` in runtime hookers.
- No random generation in `:xposed`.
- `persona_blob_` and `persona_version_` appear through `SharedPrefsKeys`, ConfigSync, and PrefsHelper only.
- Advanced risky keys default false.

- [x] **Step 12.3: End-to-end evidence bundle**

Required files:

- `logs/build/2026-05-09-hardening-final-gate.txt`
- `logs/device/2026-05-09-lsposed-scope.png`
- `logs/device/2026-05-09-devicemasker-config.png`
- `logs/device/2026-05-09-verifier-spoofed-logcat.txt`
- `logs/device/2026-05-09-verifier-spoofed-json.txt`
- `logs/device/2026-05-09-verifier-disabled-logcat.txt`
- `logs/device/2026-05-09-mantle-devcheck-smoke.txt`
- `logs/device/2026-05-09-release-r8-smoke.txt`
- `logs/device/2026-05-09-post-reboot-smoke.txt`

Review evidence before claiming success:

```powershell
Select-String logs/device/*.txt -Pattern "FATAL EXCEPTION|AbstractMethodError|VerifyError|NoSuchMethodError|UnsatisfiedLinkError"
Select-String logs/device/*.txt -Pattern "All hooks registered|Spoof event|DeviceMaskerVerifier"
```

- [x] **Step 12.4: Self-review against bogus patch risks**

Reject or revise the implementation if any of these are true:

- It adds a new config path instead of RemotePreferences.
- It makes native or system_server hooks default-on.
- It fakes success without target-app values.
- It adds broad hook behavior without kill switches.
- It changes unrelated UI/design/code style.
- It commits verifier APKs or logs into source.
- It increases Detekt baseline debt.
- It disables R8 to make hooks pass.

---

## Recommended Commit Slices During Execution

Do not commit unless the user allows commits. If commits are allowed, use these slices:

1. Shared Luhn and persona ICCID validation.
2. Persona RemotePreferences publication/consumption.
3. Profile runtime coverage hooks and tests.
4. Diagnostics/docs for restart semantics and Android security posture.
5. Verifier app and emulator evidence commands.
6. Native maps redaction experimental gate.
7. system_server package hiding discovery/gate.
8. Final docs and Memory Bank.

Each slice must pass:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
```

Before any release claim, also pass:

```powershell
.\gradlew.bat lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon
```
