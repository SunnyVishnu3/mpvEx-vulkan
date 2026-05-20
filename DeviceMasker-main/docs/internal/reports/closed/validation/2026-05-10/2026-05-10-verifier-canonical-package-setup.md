# Verifier Canonical Package Setup - 2026-05-10

## Scope

- Device: `emulator-5554`, Pixel 10 Pro XL API 36.1 / Android 16.
- Verifier package: `com.astrixforge.devicemasker.verifier`.
- Device Masker group: `TestingA16`.

## Result

- `:verifier` builds with the canonical application ID.
- Verifier APK installed successfully.
- LSPosed scope includes `android`, `system`, `flar2.devcheck`, `com.mantle.verify`, and `com.astrixforge.devicemasker.verifier` for Device Masker.
- Device Masker UI shows `TestingA16` with `6 apps • 3 assigned`; checked apps are DevCheck, DeviceMasker Verifier, and Mantle Verify.
- Verifier launched under LSPosed with `XposedEntry loaded`, `All hooks registered`, and spoof events.
- Checked logcat window had no `FATAL EXCEPTION`, `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, or `HookFailedError`.

## Evidence

- Build log: `logs/build/2026-05-10-verifier-canonical-package-build.txt`
- LSPosed scope DB snapshot: `logs/device/2026-05-10-lsposed-modules-config-after-verifier-ui.db`
- Device Masker config snapshot: `logs/device/2026-05-10-devicemasker-config-after-verifier-ui.json`
- Verifier logcat: `logs/device/2026-05-10-verifier-canonical-package-logcat.txt`
- Verifier latest JSON: `logs/device/2026-05-10-verifier-canonical-package-latest-2.json`

