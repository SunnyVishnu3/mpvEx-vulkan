# Product Context: Device Masker

## Problem

Android apps can collect identifiers and signals that become a device fingerprint:
- Android ID, Advertising ID, GSF-like identifiers, and Media DRM identifiers.
- Telephony identifiers such as IMEI, IMSI, ICCID, MCC/MNC, carrier name, and phone number.
- Network signals such as Wi-Fi MAC, SSID, BSSID, Bluetooth address, and network operator.
- Build and system profile values such as manufacturer, model, fingerprint, serial, board, brand, and hardware.
- Locale, timezone, location, and sensor metadata.
- Installed packages, stack traces, loaded maps, and other evidence of LSPosed or hook frameworks.

Researchers need a controlled way to present consistent alternate identities to selected apps without modifying those apps.

## Product Shape

Device Masker has three product-facing responsibilities:

1. Configuration app
   - Enable/disable the module globally.
   - Create and edit spoof groups.
   - Assign apps to groups.
   - Generate and persist stable identity values.
   - Sync configuration to libxposed RemotePreferences.
   - Export rootless app logs and available diagnostics.

2. Xposed module
   - Loads only in LSPosed-scoped processes.
   - Reads RemotePreferences from the libxposed service bridge.
   - Applies hooks only for enabled target apps and enabled spoof types.
   - Returns original values whenever config is unsafe.
   - Logs hook and spoof events to LSPosed Manager logs.

3. Diagnostics and verification
   - Shows app-side framework/config/log state.
   - Uses LSPosed logs as the reliable proof that target-process hooks loaded and fired.
   - Uses optional root/logcat artifacts for deeper support bundles when root is available.
   - Uses the separate `:verifier` target app for controlled framework-surface evidence on emulator/device tests.

## Expected User Flow

1. Install Device Masker.
2. Enable it as an LSPosed module.
3. Scope `android`, `system`, and the selected target apps.
4. Open Device Masker and wait for libxposed service connection.
5. Create or select a spoof group.
6. Assign target apps to that group.
7. Enable specific spoof types and verify generated values.
8. Force-stop and relaunch target apps.
9. Validate with LSPosed logs and an identifier-checking app.

## Working Base

The first working base was validated on 2026-05-02:
- Target app: `com.mantle.verify`.
- Target process started under LSPosed after crash remediation.
- Hooks registered.
- Multiple spoof events were emitted to LSPosed logs.
- Earlier startup crash signatures were absent in the final smoke check.

The latest R8 release working base was validated on 2026-05-06:
- Emulator targets: `com.mantle.verify` and `flar2.devcheck`.
- Release minification/resource shrinking stayed enabled.
- Production hook callbacks use `StableHooker`/named `XposedInterface.Hooker` implementations instead of direct Kotlin SAM `.intercept { ... }` callbacks.
- LSPosed logs showed target selection, hook registration, and spoof events.
- Mobile MCP observed Mantle displaying spoofed model/fingerprint values.
- The user confirmed the same R8 build path works on a real Android 16 device.

This proves the architecture can work end to end for scoped targets with release R8 enabled. It does not prove stable release readiness.

## UX Principles

- Never imply that app launch alone proves hook success.
- Distinguish app-side Xposed service connection from target-process hook registration.
- Prefer clear status labels over optimistic activation claims.
- Keep configuration recoverable and stable.
- Make development limitations visible.
- Encourage LSPosed log verification after changing scope, module state, or target app config.

## Correct Hook Behavior

For each target app and spoof type:
- If the module is disabled, return the original value.
- If the app is not present in the current enabled-app allowlist, return the original value.
- If the app is not enabled in `AppConfig`, return the original value.
- If the app has no explicit assigned group, return the original value.
- If the assigned group is missing or disabled, return the original value.
- If the spoof type is disabled, blank, missing, or malformed, return the original value.
- If a valid stored value exists and the type is enabled, return the stored spoofed value.

## Primary Product Risk

Target apps can fail during startup when hooks are too invasive, too early, or inconsistent. The latest working base was achieved by favoring pass-through safety and disabling global class lookup anti-detection by default.
