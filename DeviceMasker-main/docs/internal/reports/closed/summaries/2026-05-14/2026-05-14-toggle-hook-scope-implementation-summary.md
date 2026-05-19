# Toggle And Hook-Scope Implementation Summary

Date: 2026-05-14

## Scope

This summary records the implementation that remediated the current toggle and hook-scope issues from `docs/internal/reports/closed/audits/2026-05-14/2026-05-14-toggle-hook-scope-audit.md`.

The work intentionally did not include optional UI features or unrelated cleanup.

## Issues Fixed

1. Stale RemotePreferences scope activation
   - `XposedEntry` no longer trusts a lone historical `app_enabled_* = true` key.
   - Target selection now requires module enabled, current `SharedPrefsKeys.KEY_ENABLED_APPS` membership, and the per-package enabled key.
   - Missing `module_enabled` now defaults closed for hook selection.

2. Default-group fallback in runtime sync
   - Runtime sync now uses explicit app-to-group assignment from `JsonConfig.appConfigs`.
   - An enabled `AppConfig` without a valid `groupId` does not become hookable through the default group.

3. UI/runtime assignment drift
   - Group cards, Home group selector counts, and Group Spoofing Apps tab state now derive from canonical `appConfigs`.
   - `SpoofGroup.assignedApps` remains legacy/display compatibility and is not used for the updated active toggle/count decisions.

4. Partial category switch display
   - Group Spoofing category master switches now render on only when all child spoof types are enabled.
   - No tri-state or optional UX expansion was added.

5. Hook-family over-registration
   - Ordinary value hook-family policy now follows enabled spoof types.
   - Anti-detection and package-manager policy remain app-level, conservative behavior.

## Files Changed

- `common/src/main/kotlin/com/astrixforge/devicemasker/common/JsonConfig.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSync.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt`
- `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
- Group, Home, and Group Spoofing UI/ViewModel state files that display app assignment and counts.
- Regression tests in `:app` and `:xposed`.

## Verification

Fresh static/unit gate:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon --no-configuration-cache
```

Result: `BUILD SUCCESSFUL`.

Runtime emulator validation:

- Device: `emulator-5554`, Android 16 / SDK 36, 16 KB page size.
- Built and installed fresh debug app and verifier APKs.
- Used Mobile MCP to toggle `com.astrixforge.devicemasker.verifier` off/on in `TestingA16 > Apps`.
- When assigned, Verifier logged `Target package selected`, `All hooks registered`, and multiple `Spoof event` lines.
- When unassigned but still LSPosed-scoped, Verifier logged `XposedEntry loaded` only. It did not log target selection, hook registration, or spoof events.
- No checked `FATAL EXCEPTION`, `AbstractMethodError`, `VerifyError`, or `NoSuchMethodError` appeared in the filtered log checks.

Evidence files:

- `logs/device/2026-05-14-toggle-scope-test/config-verifier-unassigned.json`
- `logs/device/2026-05-14-toggle-scope-test/unassigned-filtered-logcat.txt`
- `logs/device/2026-05-14-toggle-scope-test/unassigned-latest.json`
- `logs/device/2026-05-14-toggle-scope-test/config-verifier-assigned.json`
- `logs/device/2026-05-14-toggle-scope-test/assigned-filtered-logcat.txt`
- `logs/device/2026-05-14-toggle-scope-test/assigned-latest.json`

## Remaining Validation

The current bug path is validated on the Android 16 emulator. Broader app-category validation and real-device validation remain separate release-readiness work.

## Commit State

No commit was made.
