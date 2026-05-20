# Executive summary

Device Masker cannot reliably spoof arbitrary user apps when only `android` and `system` are selected in LSPosed. With libxposed API 101, modules are loaded only in processes for packages included in the module scope. `android` and `system` cover framework/system-server paths; they do not inject Device Masker into a normal app process such as `com.example.target`.

The high-confidence design is not to bypass LSPosed scope. The high-confidence design is to keep `android` and `system` as required baseline scope, then let Device Masker request user-app scope from inside the app through `XposedService.requestScope(packages, callback)` when the user assigns an app. Device Masker should keep its current internal allowlist gate, so LSPosed scope grants injection and Device Masker config grants spoof eligibility.

Expected working chance: high for modern libxposed frameworks that implement the service scope API, because the API is official and directly exists for adding apps to module scope. It is not 99% universal across every LSPosed fork/device/version because user approval, framework support, process restart timing, and module activation state remain external conditions.

# Research question and scope

Question: Can Device Masker require users to scope only `android` and `system` in LSPosed, then choose target user apps inside Device Masker and spoof those apps?

Affected modules:
- `:app`: Xposed service bridge, app selection UX, config sync, diagnostics.
- `:common`: preference key contracts, app assignment model.
- `:xposed`: process injection, target selection, hook registration.
- `:verifier`: validation target for proving scope request and hook behavior.

Decision supported:
- Whether to attempt `android`/`system`-only runtime spoofing.
- Whether to add in-app LSPosed scope request management.

# Source inventory

Official and upstream sources:
- LSPosed Module Scope wiki, `Module-Scope.md`: module scopes are required to activate a module; manifest metadata declares default scope.
- libxposed API package summary: `META-INF/xposed/scope.list`, `staticScope`, process injection behavior, `system` and `android` scope semantics.
- libxposed `XposedInterface` Javadoc: API 101 modules cannot be injected into zygote and are loaded only within scope processes.
- libxposed service Javadoc: `getScope()`, `requestScope(List<String>, OnScopeEventListener)`, and `removeScope(List<String>)`.
- libxposed example app: demonstrates `service.requestScope(listOf(...), callback)` and `OnScopeEventListener`.

Project-local sources:
- `xposed/src/main/resources/META-INF/xposed/scope.list`
- `xposed/src/main/resources/META-INF/xposed/module.prop`
- `app/src/main/res/values/arrays.xml`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/XposedPrefs.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Memory Bank: `productContext.md`, `systemPatterns.md`, `techContext.md`, `activeContext.md`, `progress.md`

# Verified facts

1. LSPosed module scope is required for module activation in target packages.
   - The LSPosed Module Scope wiki states that module scopes are required to activate a module and that declared scope metadata selects apps by default when the module is enabled.

2. libxposed API 101 scope is package-process based.
   - The libxposed API package summary says module scope is defined by package names in `META-INF/xposed/scope.list`, and the framework injects the module into regular processes declared by those packages.

3. `system` is special and is not a shortcut for all apps.
   - The libxposed API docs say packages whose components all run in system server need the virtual `system` scope. That covers system-server execution, not arbitrary user app processes.

4. `android` is also special and limited.
   - The libxposed API docs say `android` is a valid scope target because some components use `android:process=":ui"`, even though the `android` package has no code. This does not mean all normal app processes receive module injection.

5. API 101 modules are not loaded globally through zygote.
   - `XposedInterface` Javadoc says API 101 behavior is that modules cannot be injected into zygote and are only loaded within the process of the scope.

6. The service API supports app-side scope requests.
   - libxposed service Javadoc exposes `getScope()`, `requestScope(List<String>, OnScopeEventListener)`, and `removeScope(List<String>)`.
   - The official example uses `service.requestScope(listOf("com.android.settings"), callback)`.

7. Device Masker already uses the libxposed app-side service.
   - `XposedPrefs.init()` registers `XposedServiceHelper.OnServiceListener`.
   - `XposedPrefs.getPrefs()` uses `xposedService?.getRemotePreferences(PREFS_GROUP)`.

8. Device Masker currently protects against LSPosed-scope-only activation.
   - `XposedEntry.isPackageCurrentlyEnabledForHooks()` requires `module_enabled`, membership in `enabled_apps`, and `app_enabled_<pkg>`.
   - Memory Bank records Android 16 emulator validation where an LSPosed-scoped but unassigned Verifier app loaded only `XposedEntry`, with no target selection, no hook registration, and no spoof events.

# Source-backed findings

`android` + `system` only cannot spoof normal target apps because there is no module instance inside those target app processes. Hooking `Settings.Secure` or framework classes from `system_server` does not intercept method calls made inside an app process unless the hooked method call crosses into the hooked process. Many spoof surfaces are local method calls in the target process: `Build.*`, `TelephonyManager` client-side methods, `WifiInfo`, `BluetoothAdapter`, `WebSettings`, `Location` getters, stack traces, and package visibility calls. Those require hooks inside the target app process.

The official path for dynamic user-app targeting is app-side scope management, not broad zygote injection. `requestScope()` exists precisely to ask the framework to add packages to module scope. That lets Device Masker own the UX while LSPosed/framework still owns the security boundary and final approval.

`staticScope=false` is required for this direction. Device Masker's current `module.prop` already has `staticScope=false`, so users should be able to apply the module outside the packaged `scope.list`. By contrast, the official libxposed example has `staticScope=true`, which would be wrong for Device Masker's per-user dynamic target-app model.

# Inferences

Inference: Device Masker should keep `scope.list` minimal and baseline-focused, likely `android` and `system`. User app scope should be requested dynamically when the user assigns apps. The existing app manifest `xposedscope` array includes `com.android.providers.settings`, but libxposed API docs say that package is not a valid scope target and modules should use `system` and wait for the package loading event. This should be reviewed in a separate docs/config cleanup.

Inference: Some target apps may need force-stop/relaunch after scope is approved. The module can only hook a target process after LSPosed injects the module into a new or restarted scoped process. Existing running processes may remain unhooked until restarted.

Inference: `removeScope()` should be used conservatively. Removing LSPosed scope when an app is unassigned reduces injection/detection footprint, but scope removal could surprise users if they intentionally keep scope for later. A better default is: request scope on assignment, keep Device Masker internal allowlist as the hard spoof gate, and offer optional cleanup/remove-scope action.

# Project impact

`:app`:
- Add scope-state read path over `XposedService.getScope()`.
- Add app-side request path using `XposedService.requestScope()`.
- Add UI state per app: configured in Device Masker, present in LSPosed scope, pending approval, approved, failed, restart needed.
- Add user action to request missing scope for one app or selected apps.

`:common`:
- No key format change required.
- `JsonConfig.appConfigs` remains canonical for spoof eligibility.

`:xposed`:
- Keep current target selection exactly strict: LSPosed injection is necessary but not sufficient.
- Do not weaken `enabled_apps` + `app_enabled_<pkg>` checks.
- No attempt to hook arbitrary apps through `android` or `system` only.

`:verifier`:
- Add a validation scenario: unscoped verifier cannot hook, scope request approved, verifier force-stopped/relaunched, hooks register and values spoof.

Validation:
- Need LSPosed/logcat evidence for request approval, target process restart, `XposedEntry loaded`, target selection, `All hooks registered`, spoof events, and actual value checks.

# Compatibility risks and edge cases

- Framework support: `requestScope()` depends on the libxposed service implementation. If an LSPosed fork lacks or breaks scope request support, Device Masker must fall back to opening LSPosed/manual instructions.
- User approval: scope request can fail or be denied. Device Masker cannot silently grant itself scope.
- Existing process lifetime: scope changes may not affect already-running target processes until force-stop/relaunch or reboot.
- Multi-process apps: selecting a package scopes package-declared processes, but Device Masker still needs package/process filtering and classloader dedupe. The current `XposedEntry` candidate logic is already designed for loaded package plus process base package.
- Detection footprint: adding a sensitive target app to LSPosed scope can itself be detectable by that app. This is inherent to Xposed-style process injection.
- System packages: user-app request flow must not casually request critical system packages. Existing skip-list and UI filtering should remain conservative.
- Scope cleanup: automatic `removeScope()` on unassign could remove a user's intentionally managed LSPosed scope. Treat removal as explicit cleanup.

# Unknowns and gaps

- Exact LSPosed Manager UX shown during `requestScope()` on current target devices needs runtime confirmation.
- Whether `requestScope()` immediately updates `getScope()` consistently across all supported LSPosed/libxposed builds needs device validation.
- Whether approval requires LSPosed Manager UI visibility/unlocked state on all devices is not guaranteed by Javadoc.
- Current app manifest legacy `xposedscope` includes `com.android.providers.settings`; this conflicts with current libxposed scope guidance and should be audited separately.

# Recommendations

Recommended design with highest working chance:

1. Keep LSPosed baseline scope as `android` and `system`.
   - `android` for valid Android package callbacks where applicable.
   - `system` for system-server components and settings-provider style paths.

2. Add in-app scope management over libxposed service.
   - When a user assigns app `pkg` in Device Masker, write canonical config as today.
   - Check `xposedService.getScope()`.
   - If `pkg` is missing, call `xposedService.requestScope(listOf(pkg), callback)`.
   - On `onScopeRequestApproved`, mark scope approved and tell the user the app must be force-stopped/relaunched.
   - On `onScopeRequestFailed`, keep app assigned but show "LSPosed scope missing" and provide manual LSPosed fallback.

3. Keep spoof eligibility separate from injection.
   - LSPosed scope = module can load in process.
   - Device Masker `enabled_apps` + `app_enabled_<pkg>` + group/type config = spoof is allowed.
   - This avoids stale LSPosed scope becoming active spoofing. Removing this gate would be bogus shit because it breaks the safety model already validated on Android 16 emulator.

4. Add a scope health model to the app UI.
   - `Not scoped`: configured but LSPosed scope missing.
   - `Scope requested`: waiting for callback.
   - `Scoped, restart needed`: approved but target process may still be old.
   - `Hook observed`: LSPosed/logcat evidence confirms target process hook registration/spoof event.

5. Do not attempt `android`/`system`-only app spoofing.
   - That would be hand-wavy bullshit: the official API says modules are loaded only within scope processes, and many target surfaces are local to the target process.

# Suggested next tasks

1. Design `XposedScopeManager` in `:app` around existing `XposedPrefs.xposedService`.
2. Add tests for scope-state mapping and request callback outcomes with a fake `XposedService` wrapper.
3. Add UI affordance in app assignment flow: request missing LSPosed scope and show restart-needed state.
4. Add verifier runtime script/manual checklist:
   - Ensure verifier not scoped.
   - Assign verifier in Device Masker.
   - Request scope from app.
   - Approve request.
   - Force-stop/relaunch verifier.
   - Confirm `XposedEntry loaded`, target selected, hooks registered, spoof events, and verifier values.
5. Audit `xposedscope`/`scope.list` consistency, especially `com.android.providers.settings` versus `system`.

# Report file path

`docs/internal/reports/active/research/2026-05-15/2026-05-15-lsposed-scope-selection-proposal.md`

# Write boundary confirmation

Only this report file was written. No source, config, build, validation, Memory Bank, commit, branch, or artifact changes were made.
