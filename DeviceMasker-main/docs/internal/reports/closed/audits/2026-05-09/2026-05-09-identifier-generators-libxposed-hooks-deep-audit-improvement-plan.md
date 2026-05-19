# DeviceMasker Identifier Generators and libxposed Hooks – Deep Audit and Improvement Plan

## Executive Summary

This report analyzes the current design of DeviceMasker’s identifier generators and libxposed-based hooks using the attached comprehensive internal audit plus public libxposed and Android identifier documentation. The goal is to improve realism, internal correlation, performance, safety, and anti‑detection properties while keeping the architecture maintainable.[^1][^2][^3]

At a high level, DeviceMasker already follows strong architectural rules: config is generated in the app/common modules, delivered over RemotePreferences, and consumed in hooks without runtime generation inside target processes. The existing audit has already identified and partially fixed several critical issues (SecureRandom thread safety, hook registration safety, deoptimization error handling) but also leaves open areas around generator realism (e.g., Luhn inconsistencies), per‑process vs per‑app semantics, preference liveness, and some libxposed usage details.[^4][^3][^1]

This report consolidates the current issues, proposes concrete code‑level changes, and ranks improvements by impact for: identifier generators, realism and cross‑field correlation, hook behavior and libxposed safety, anti‑detection logic, diagnostics, and performance.

## Current Architecture Overview

DeviceMasker is structured into three main modules: app (Compose UI + config delivery), common (models, generators, SharedPrefsKeys, validation), and xposed (hookers, XposedEntry, diagnostics service running in target processes or system server). The app generates per‑persona and per‑app spoof configs, persists them as JSON (JsonConfig), and syncs a flattened view into libxposed RemotePreferences; the hooks then read those preferences per target package and spoof identifiers at runtime.[^1]

The xposed module entry point extends XposedModule and uses onModuleLoaded, onPackageReady, and onSystemServerStarting callbacks according to libxposed API 101 guidance. Hooks are registered in a fixed order (AntiDetect first, then telephony, subscription, network, system, location, sensor, advertising, WebView, package manager) via a hookSafely helper that prevents a single failing hook from breaking others.[^3][^4][^1]

Key project rules include: generators must not run inside target app hook callbacks, JsonConfig.appConfigs is canonical for app enablement, SharedPrefsKeys is the single source for preference keys, LSPosed logs are the ground truth for hook registration and spoof events, and diagnostics AIDL is one‑way and never used for config delivery. These rules strongly constrain where identifier generation and correlation logic is allowed to live and are preserved in the recommendations below.[^1]

## Identifier Generators – Current State

The common module exposes generators for IMEI, IMSI, ICCID, phone numbers, MAC addresses, serial numbers, and other identifiers, each implemented as synchronous objects using a single SecureRandom instance created at class loading time. A prior concern about SecureRandom thread safety has been resolved; SecureRandom instances are thread‑safe according to official JDK documentation, so the current singleton instance pattern is safe, though it may incur some contention under very high concurrency.[^1]

The audit highlights inconsistent Luhn algorithm implementations across generators: IMEIGenerator doubles from the right, ICCIDGenerator doubles from the left, and a Utils helper uses a third variant, which can lead to subtle correctness issues. There is also a noted serialization issue where Country is not Serializable despite being used inside Serializable models, and there is no single centralized “device persona” generator that drives correlated values like TAC ↔ model ↔ radio bands ↔ MCC/MNC.[^2][^1]

Despite these issues, the generators currently obey key safety rules: they run only in app/common config time, they do not rely on hidden APIs, and they use SecureRandom rather than predictable PRNGs, which helps avoid obvious anti‑spoofing detection based on patterns like simple counters.[^5][^1]

## Identifier Generators – Problems and Risks

### Luhn Inconsistency and Validation

Having multiple Luhn implementations (IMEI, ICCID, Utils) increases the chance of subtle bugs, especially when components are reused or when new identifiers re‑use helpers without fully understanding their directionality. If a generated IMEI or ICCID fails standard validation, apps can trivially detect spoofing by running simple check digit verification, which is widely available in open source libraries and security blogs.[^6][^7][^1]

The mismatch between algorithm direction (right vs left doubling) can also make internal tests pass while external validation tools fail, or vice versa, depending on which helper is used where. This undermines realism and may cause user confusion when validating values with external tools, and it can also weaken trust in the library.[^1]

### Lack of Real‑World Correlation

Current generators appear largely field‑local: IMEI, IMSI, ICCID, phone numbers, and MAC addresses are generated mostly independently, without strong links to model, region, or carrier. Real devices exhibit correlations: TAC ranges map to specific manufacturers and models, MCC/MNC values map to real carriers, and ICCID prefixes indicate SIM vendors and countries.[^6][^2][^1]

Apps and fraud‑detection backends increasingly cross‑check these identifiers, meaning uncorrelated random values (e.g., an IMEI TAC from one OEM, MCC/MNC for a different country, and ICCID prefix for yet another region) can be flagged as synthetic. Without a persona‑level generator, it is harder to preserve realistic combinations across all identifiers.[^5][^2]

### Serialization and Config Safety

The Country model being non‑Serializable while embedded in Serializable types is flagged as a missing piece in the audit, which could cause runtime issues when serializing or persisting configs for certain flows. Such inconsistencies can surface as subtle bugs in config migrations or diagnostics, making it harder to reason about the exact config applied to a given app.[^1]

## Identifier Generators – Recommendations

### 1. Centralize Luhn Logic

Introduce a single, well‑tested Luhn utility in the common module (e.g., Luhn.kt) and refactor IMEIGenerator, ICCIDGenerator, and any other check‑digit based generators to depend on it. The utility should explicitly document directionality (indexing from rightmost digit) and include unit tests comparing against known good IMEI and ICCID examples from public documentation.[^7][^6][^1]

Add property‑based tests where random base digits are generated and the check digit is verified by running the same algorithm in a second independent path, ensuring internal consistency. This both improves correctness and gives future contributors a single place to extend when new identifiers with Luhn‑like checks are added.[^1]

### 2. Create a Persona‑Level Generator

Extend or refine DevicePersona and DeviceHardwareConfig to include derived fields for TAC, model, manufacturer, MCC/MNC, ICCID and SIM vendor prefixes, and realistic phone number patterns. Instead of each generator choosing its own random prefix, define persona seeds and a persona profile that drives all identifiers, so that: IMEI TAC ↔ model, IMSI MCC/MNC ↔ ICCID prefix ↔ carrier, and phone number country code ↔ MCC.[^2][^1]

Implement a DevicePersonaGenerator that takes a SecureRandom and a region/carrier template (e.g., “IN‑Jio”, “US‑T‑Mobile”) and outputs a complete set of correlated identifiers. The existing per‑field generators can then be refactored into low‑level helpers used by the persona generator, while app‑level config chooses persona templates to match user preferences.[^8][^1]

### 3. Address Serialization Issues

Mark Country as Serializable (or use a stable code representation inside Serializable types) to eliminate the current “NOT Serializable” finding. Add unit tests that serialize and deserialize full config graphs including Country, DevicePersona, SIMConfig, and DeviceHardwareConfig to ensure the serialization contract is stable.[^1]

This helps avoid subtle bugs when configs or diagnostics are persisted, and ensures that future refactors do not silently break compatibility.

### 4. Optional: Optimize SecureRandom Usage

While SecureRandom is thread‑safe, high‑concurrency scenarios could benefit from reducing contention, especially if future versions perform many persona regenerations in quick succession. Consider either using ThreadLocal<SecureRandom> or a per‑persona SecureRandom seeded from a global instance, though this should be carefully benchmarked to avoid complexity without clear benefit.[^1]

Maintain the rule that all generators remain in app/common config time and never run in target app processes, regardless of the SecureRandom strategy.[^1]

## libxposed Hooks – Current State

The xposed module uses XposedEntry as the libxposed module class, with proper handling of onModuleLoaded, onPackageReady, and onSystemServerStarting. It maintains a process‑local singleton instance, tracks hooked class loaders via a ConcurrentHashMap‑backed key set, and uses SKIP_PACKAGES to avoid hooking system‑critical processes like SystemUI, Phone, and Google Play Services.[^4][^3][^1]

Hookers (DeviceHooker, SubscriptionHooker, NetworkHooker, SystemHooker, LocationHooker, SensorHooker, AdvertisingHooker, WebViewHooker, PackageManagerHooker) are stateless objects with hook(cl, xi, prefs, pkg) methods, and they are registered via hookSafely which wraps libxposed calls in try/catch with logging. AntiDetectHooker runs first to hide Xposed evidence via stack trace filtering, /proc/self/maps filtering, and package manager hiding, though Class.forName hiding hooks remain disabled by default for safety.[^9][^1]

HookHealthRegistry and XposedDiagnosticEventSink record hook metrics, including registration successes and failures, and can dump summaries into DualLog for diagnostics, which is helpful for correlating config with observed behavior.[^1]

## libxposed Hooks – Problems and Risks

### 1. Mutating Immutable chain.args in WebViewHooker

The audit identifies WebViewHooker’s current approach of mutating chain.args directly when spoofing WebView user‑agent strings, which is incorrect because libxposed’s Chain.getArgs returns an immutable list. This can cause WebSettings.setUserAgentString spoofing to silently fail, leaving the original UA while giving the impression that spoofing is active.[^3][^1]

Under the default exception mode, this mistake may only produce a log message or be swallowed, causing inconsistent behavior that is hard to debug without inspecting underlying libxposed errors.[^3][^1]

### 2. Swallowing XposedFrameworkError / HookFailedError

Several hook registration code paths catch Throwable and log generic messages without specially handling XposedFrameworkError or HookFailedError, which libxposed treats as framework‑level errors. Swallowing these errors can leave modules in partially registered states, make LSPosed runtime failures harder to diagnose, and produce misleading logs like “All hooks registered” even when a framework error occurred.[^4][^3][^1]

This behavior is particularly risky in system server hooks, where boot loops and stability issues must be handled very carefully but framework‑level errors should still be surfaced distinctly.[^1]

### 3. Per‑App Config Bound to First Package/Classloader

XposedEntry currently avoids duplicate hook registration by tracking class loaders and only registering hooks for the first package in a given process/classloader. While this is a valid tradeoff for stability, it means the effective scope is “first package per process” rather than “per app.”[^1]

This can cause scenarios where a secondary package in a shared process receives spoofed values based on the first package’s config, while its own enablement flags and values are ignored. For shared UID apps, plugin architectures, or Context.createPackageContext with CONTEXT_INCLUDE_CODE, this divergence between UI expectations and actual behavior may be detectable by apps comparing process‑global and per‑app behavior.[^9][^1]

### 4. RemotePreferences Liveness

Some hookers (SystemHooker, WebViewHooker, SensorHooker) snapshot DEVICEPROFILE values at registration time and close over those presets for the lifetime of the process, rather than reading from RemotePreferences at call time or reacting to preference changes. This breaks the expectation that RemotePreferences updates propagate live without restarting the target app.[^1]

As a result, user changes in the UI may not affect certain surfaces until the app is force‑stopped or restarted, and diagnostics may show config as updated while the effective spoof values in the process remain stale.[^1]

### 5. Over‑Broad Instance Hooks for TimeZone / Locale

LocationHooker currently hooks TimeZone.getID and Locale.toString as instance methods, spoofing values without checking chain.thisObject or ensuring the instance corresponds to the default TimeZone or Locale. This means all TimeZone or Locale instances can report the spoofed default, which can produce impossible behavior and make spoofing trivially detectable.[^1]

Apps or libraries that create multiple Locale or TimeZone instances (e.g., comparing US vs UK formats) may see all instances reporting the same spoofed default, which is inconsistent with normal behavior and could be used as an anti‑hooking heuristic.[^9]

### 6. ConfigSync.syncApp Ignores AppConfig.isEnabled

In the app module, ConfigSync.syncApp currently does not include AppConfig.isEnabled in its appEnabled calculation, instead only checking module‑level and group‑level enablement. Although full sync does the right thing, any future callers of syncApp could accidentally write enabled config for apps that the user disabled explicitly.[^1]

This violates the rule that JsonConfig.appConfigs is canonical for app enablement, and can lead to confusing situations where the UI shows an app as disabled but hooks still read it as enabled via RemotePreferences.[^1]

### 7. RemotePreferences Writes via apply Instead of commit

XposedPrefs writes snapshots to RemotePreferences using apply, which updates the app‑side snapshot immediately but writes to the framework asynchronously. If the framework write fails (e.g., due to LSPosed issues or storage problems), the app’s local view may show config as synced while hooks never receive the values.[^4][^1]

This asynchronous behavior makes debugging config delivery harder and can produce mismatches between UI and target processes, especially in error scenarios.[^1]

### 8. XposedPrefs.init Comment vs Reality

XposedPrefs.init currently has a comment claiming it is safe to call multiple times, but there is no guard preventing multiple registrations with XposedServiceHelper.registerListener, which expects exactly one static listener. While the current app calls init only once in DeviceMaskerApp.onCreate, future code or third‑party integrations might accidentally call it again and cause unexpected behavior.[^4][^1]

### 9. No‑Op SubscriptionManager.getActiveSubscriptionInfoList Hook

SubscriptionHooker includes a commented intent to mutate each listed SubscriptionInfo for consistency but currently just calls chain.proceed without modifying the list, making it effectively a no‑op hook. The mismatch between comments and behavior can create false confidence about coverage of SIM‑related spoofing.[^1]

## libxposed Hooks – Recommendations

### 1. Fix WebViewHooker to Use proceedWithObject

Refactor WebViewHooker so that it: reads the current UA argument, decides whether to modify it, allocates a new argument array with the spoofed UA, and calls chain.proceed(newArgs) or chain.proceedWithObject depending on the libxposed API version. This aligns with the official contract that Chain.getArgs returns an immutable list and ensures the spoofed UA actually reaches WebView.[^3][^1]

Add a static safety test that fails if any code in the xposed module assigns to chain.args, preventing regressions where contributors accidentally reintroduce direct mutation.[^1]

### 2. Rethrow XposedFrameworkError in hookSafely and Deoptimize

Update hookSafely and DeoptimizeManager.deoptimizeAll to explicitly catch XposedFrameworkError (and HookFailedError if used) first and rethrow them, only using generic catch Throwable for non‑framework errors. This ensures framework‑level failures are propagated appropriately and can be surfaced by LSPosed as hard errors rather than being hidden behind warnings.[^3][^4][^1]

For system server hooks, consider logging XposedFrameworkError at ERROR level and optionally allowing a configuration flag to decide whether to rethrow (risking boot loops) or continue with reduced functionality, but make this choice explicit and documented rather than accidental.[^1]

### 3. Make Per‑Process vs Per‑App Semantics Explicit

Document clearly that hook registration is keyed by classloader and process, and that only the first package in a process/classloader gets config bound at hook registration time. Rename internal variables, diagnostic messages, and UI copy from “per‑app” to “first app in process” or “per process” where appropriate.[^1]

Longer term, consider enhancing hookers to resolve package identity at call time for APIs that expose context or package names, building a process policy table keyed by package and process, and gradually shifting more surfaces to true per‑calling‑package behavior.[^9][^1]

### 4. Make RemotePreferences Reads Live Where Safe

For hookers that currently snapshot DEVICEPROFILE at registration, refactor their interceptors to read getConfiguredSpoofValue from prefs at call time for callback‑returned values that are cheap to resolve. For expensive profile computations, introduce a small cache with invalidation based on a version or timestamp stored in RemotePreferences.[^4][^1]

For static Build field mutation and other inherently process‑start‑sensitive surfaces, document explicitly in the UI and diagnostics that changes require a target app restart, distinguishing them from truly live RemotePreferences‑backed surfaces.[^1]

### 5. Narrow TimeZone and Locale Hooks

Change LocationHooker to prefer hooks on TimeZone.getDefault and Locale.getDefault instead of instance methods, spoofing only the default objects and leaving other instances untouched. If instance hooks must remain, gate spoofing by checking chain.thisObject or by only spoofing when the original result matches the current default ID or string.[^9][^1]

Add unit tests that construct multiple TimeZone and Locale instances and verify that only default‑like instances are spoofed, preventing detection via simple comparisons between default and non‑default objects.[^1]

### 6. Fix ConfigSync.syncApp to Respect AppConfig.isEnabled

Update syncApp so that appEnabled is computed as config.isModuleEnabled && appConfig.isEnabled && group.isEnabled (or equivalent) to match full sync behavior and the canonical JsonConfig.appConfigs semantics. Extend ConfigSyncSnapshotTest or add a dedicated test to ensure that disabled apps write appenabled=false in RemotePreferences.[^1]

This keeps app‑level enablement consistent between UI, JSON, and RemotePreferences and avoids confusing mismatches where disabled apps still see spoofing.

### 7. Use commit for Critical RemotePreferences Writes

Switch config sync paths (e.g., XposedPrefs.writeSnapshot or equivalent) from apply to edit().commit(true) semantics, checking the return value and logging a clear warning if the commit fails. Reserve apply only for low‑risk UI toggles where eventual consistency is acceptable.[^4][^1]

This change ensures that success messages and diagnostics reflect actual persistence to the libxposed framework, reducing debugging complexity when LSPosed or storage issues occur.

### 8. Guard XposedPrefs.init with AtomicBoolean

Implement an AtomicBoolean guard around XposedPrefs.init so repeated calls become no‑ops, matching the expectation that XposedServiceHelper.registerListener should only be called once. Update the comment accordingly to explain that multiple calls are safe because of the guard, not because the underlying API supports multiple listeners.[^4][^1]

### 9. Remove or Implement SubscriptionManager.getActiveSubscriptionInfoList Hook

Either remove the no‑op hook and its comment, or fully implement list‑level spoofing for SubscriptionInfo objects in a way that is safe and consistent with other telephony spoofing surfaces. If implemented, ensure that SubscriptionInfo values correlate with IMSI/ICCID/phone numbers generated by the persona generator to avoid cross‑field inconsistencies.[^8][^1]

## Anti‑Detection Patterns – Current State and Improvements

The AntiDetectHooker and related utilities implement several anti‑detection strategies: stack trace filtering (removing libxposed frames from stack traces), /proc/self/maps filtering, and package manager hiding of module packages. Global Class.forName and ClassLoader.loadClass hooks remain disabled by default due to stability and compatibility concerns, and LSPosed’s module scope API is used to control which packages see the module.[^9][^4][^1]

These patterns align with common anti‑hooking evasion techniques documented in security blogs, which show that apps often search for XposedBridge stack frames, native method anomalies, or suspicious classes in memory maps.[^5][^9]

To further harden against detection while preserving stability, the following optional improvements could be considered:

- Make anti‑detection behavior more configurable per package via RemotePreferences, so that high‑risk apps receive stricter hiding while low‑risk apps use conservative settings.[^9][^1]
- Add diagnostics that explicitly record which anti‑detection hooks were active for a target package and whether they encountered errors, aiding troubleshooting when apps still detect hooking.[^1]

## Performance and Robustness

The audit notes several blocking patterns in app‑side code (runBlocking in AppLogStore.appendEvent, busy‑waiting in flushPendingWrites, per‑command executor creation in RootShell, blocking ensureConnected in ServiceClient) that can impact responsiveness and battery but do not directly affect realism of identifiers or detection surface. However, improving these areas will contribute to overall robustness and perceived quality.[^1]

Hook‑side thread safety is generally good: XposedEntry.instance is volatile, hookedClassLoaders uses a concurrent key set, HookHealthRegistry uses AtomicLong and ConcurrentHashMap, and DiagnosticsLogBuffer uses ConcurrentLinkedDeque and AtomicInteger. Given that hooks run in arbitrary threads within target processes, this concurrency discipline is important.[^1]

On the generator side, SecureRandom’s intrinsic synchronization is acceptable but could be optimized if future benchmarks show contention under stress tests that repeatedly regenerate personas.[^1]

## Before/After Comparison and Ranking

### Identifier Generators

| Aspect | Current State | After Improvements | Impact |
|-------|---------------|--------------------|--------|
| Luhn implementation | Multiple inconsistent variants across IMEI/ICCID/Utils.[^1] | Single centralized Luhn utility with tests and documented direction.[^1][^6] | High (realism, undetectability) |
| Cross‑field correlation | Mostly field‑local, limited persona‑wide linkage.[^1] | Persona generator ties TAC, model, MCC/MNC, ICCID, phone numbers.[^1][^2] | Very High (realism, anti‑fraud) |
| Serialization | Country not Serializable inside Serializable graphs.[^1] | Country Serializable, full config serialization tests added.[^1] | Medium (stability) |
| SecureRandom usage | Single global instance per generator, thread‑safe but potential contention.[^1] | Optional ThreadLocal or per‑persona instance where needed.[^1] | Low–Medium (performance) |

### libxposed Hooks

| Aspect | Current State | After Improvements | Impact |
|--------|---------------|--------------------|--------|
| WebView UA spoofing | Mutates immutable chain.args, may silently fail.[^1] | Uses proceedWithObject/new args, fully correct per libxposed.[^1][^3] | High (correctness, realism) |
| Framework error handling | XposedFrameworkError may be swallowed by catch Throwable.[^1] | Explicit catch and rethrow framework errors, clearer diagnostics.[^1][^4] | High (stability, debuggability) |
| Per‑app semantics | Hooks bound to first package per classloader, not explicit.[^1] | Behavior documented; future path to per‑calling‑package resolution.[^1] | Medium–High (correctness vs expectations) |
| Pref liveness | Some hooks snapshot prefs at registration.[^1] | Call‑time reading or versioned caching, with restart‑required surfaces documented.[^1][^4] | High (user experience, realism) |
| TimeZone/Locale hooks | Spoof all instances, possible impossible behavior.[^1] | Narrowed to default or gated by thisObject/original result.[^1][^9] | High (undetectability) |
| ConfigSync.syncApp | Ignores AppConfig.isEnabled in appEnabled.[^1] | Honors AppConfig.isEnabled, tests updated.[^1] | Medium (consistency) |
| RemotePreferences writes | Uses apply; no commit failure surfaced.[^1] | commit used for critical paths with failure logging.[^1][^4] | Medium–High (reliability) |
| XposedPrefs.init | Comment claims multi‑call safe; no guard.[^1] | AtomicBoolean guard implements idempotent init.[^1][^4] | Low–Medium (future‑proofing) |
| Subscription list hook | No‑op with misleading comment.[^1] | Removed or correctly implemented consistent spoof list.[^1][^8] | Medium (realism, clarity) |

## Implementation Strategy and Priorities

### Phase 1 – High‑Impact, Low‑Risk

1. Centralize Luhn logic and update IMEI/ICCID generators + tests.[^6][^1]
2. Fix WebViewHooker to use new args with proceed/proceedWithObject; add static tests to forbid chain.args mutation.[^3][^1]
3. Introduce explicit XposedFrameworkError rethrow in hookSafely and DeoptimizeManager.[^4][^1]
4. Fix ConfigSync.syncApp to honor AppConfig.isEnabled and extend tests.[^1]

### Phase 2 – Realism and Anti‑Detection

5. Design and implement DevicePersonaGenerator with correlated identifiers; refactor field‑local generators into components.[^2][^1]
6. Narrow TimeZone and Locale hooks to default‑only behavior or gated spoofing; add tests for multiple instances.[^9][^1]
7. Start making RemotePreferences reads live for safe surfaces, and classify surfaces into live vs restart‑required in UI/diagnostics.[^4][^1]

### Phase 3 – Robustness and Config Delivery

8. Switch critical RemotePreferences writes from apply to commit and surface failures in diagnostics.[^4][^1]
9. Guard XposedPrefs.init with AtomicBoolean; adjust comments.[^1]
10. Resolve SubscriptionManager.getActiveSubscriptionInfoList hook (remove or implement with correlation to persona).[^8][^1]
11. Address Country serialization and add full config serialization tests.[^1]

## Conclusion

With the above changes, DeviceMasker’s identifier generators will move from mostly independent random field generators to a correlated persona‑based system that better matches real‑world device/country/carrier patterns, significantly improving realism and reducing detection risk. The libxposed hooks will more strictly conform to the API’s immutability and error semantics, enhance RemotePreferences liveness where safe, and clarify per‑process vs per‑app behavior.[^6][^2][^1]

These improvements build on the strong foundations already verified by the existing internal audits and align DeviceMasker with modern best practices for Android identifier spoofing, hook safety, and anti‑detection measures.[^5][^3][^9][^4][^1]

---

## References

1. [Device identifiers - Android Open Source Project](https://source.android.com/docs/core/connect/device-identifiers) - All carrier apps can access device identifiers by updating the CarrierConfig.xml file with the signi...

2. [libxposed - GitHub](https://github.com/libxposed) - libxposed has 5 repositories available. Follow their code on GitHub.

3. [Develop Xposed Modules Using Modern Xposed API ... - GitHub](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API/f5e53a3c8758306a53832d2ed8c4e1faf83acef7) - Warning: this feature is not yet stable and currently under actively development; APIs may change un...

4. [Device Spoofing | Mobile Security Glossary - Zimperium](https://zimperium.com/glossary/device-spoofing) - Device spoofing refers to falsifying a device's identity to appear as a different device or to masqu...

5. [How to Generate Unique Android Device Identifiers - CelerSMS](https://www.celersms.com/device-id-android.htm) - Generating or obtaining a unique identifier for each device where an app is installed can be useful ...

6. [Unique ID of Android device - Stack Overflow](https://stackoverflow.com/questions/4468248/unique-id-of-android-device) - The IMEI Number is a very good and primary source to get the device ID. It is unique for each and ev...

7. [Android1500/AndroidFaker: Android Faker a Simple Xposed Module ...](https://github.com/Android1500/AndroidFaker) - Android Faker a Simple Xposed Module Which Spoof Your Device IDs Values. Supporting Android 8.1+ - A...

8. [Android Anti-Hooking Techniques in Java // dead && end](https://d3adend.org/blog/posts/android-anti-hooking-techniques-in-java/) - A recent internal thread about detecting hooking frameworks in native code (C/C++) got me thinking a...

