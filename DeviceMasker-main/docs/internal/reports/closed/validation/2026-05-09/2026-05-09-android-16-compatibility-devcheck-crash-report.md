# Android 16 Compatibility And DevCheck Crash Report

Date started: 2026-05-09
Status: active

## Purpose

Track real Android 16 behavior separately from Android 13 emulator behavior. Android 13 smoke passing is not enough to claim Android 16 stability.

## Device Matrix

| Device | Android | SDK | Page size | LSPosed | App build | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Android 13 emulator | 13 | 33 | 4096 from preflight | installed | local debug + unsigned local release/ciRelease builds | Gradle gate green; verifier probe renders `procMaps`, `packageVisibility`, and `runtime` |
| Pixel 10 Pro XL emulator (`emulator-5554`) | 16 | 36 | 16384 | LSPosed active in target logcat | local debug, local unsigned release/ciRelease, debug-key-signed ciRelease smoke | DevCheck stays alive; hooks register; spoof events emitted; no checked fatal/ABI signatures |
| Real Android 16 device | 16 | 36 | collect with `adb shell getconf PAGE_SIZE` | collect from LSPosed Manager/logcat | collect from APK metadata | still required before claiming real-device completion |

## DevCheck Crash Evidence

| Run | Module state | App enabled | Hook policy | Crash? | Evidence files | Root cause |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | disabled in LSPosed | none | none | not run yet | pending A16 device run | no conclusion |
| 2 | enabled | app not configured | load-only | not run yet | pending A16 device run | no conclusion |
| 3 | enabled | enabled | anti-detect only | not run yet | pending A16 device run | no conclusion |
| 4 | enabled | enabled | device/system hooks only | not run yet | pending A16 device run | no conclusion |
| 5 | enabled | enabled | telephony/subscription only | not run yet | pending A16 device run | no conclusion |
| 6 | enabled | enabled | package/feature hooks only | not run yet | pending A16 device run | no conclusion |
| 7 | enabled | enabled | all safe hooks | no on Android 16 emulator | `logs/device/2026-05-10-213712-a16-flar2.devcheck-*` | no crash reproduced; hooks registered and spoof events emitted |
| 8 | enabled | enabled | all safe hooks, ciRelease/R8 signed with debug key for local smoke | no on Android 16 emulator | `logs/device/2026-05-10-214023-a16-flar2.devcheck-*`, `logs/device/2026-05-10-ciRelease-install.txt`, `logs/build/2026-05-10-ciRelease-debugkey-apksigner-verify.txt` | no release-only `AbstractMethodError`, `VerifyError`, `NoSuchMethodError`, or fatal crash in checked log window |

## Root Cause Notes

- No conclusion until logcat/tombstone proves the crash class.
- If crash occurs with module disabled, it is not a Device Masker hook crash.
- If crash occurs with module enabled but app not configured, suspect module load or anti-detection.
- If crash appears only after a hook family is enabled, fix that hook family first.
- Hidden API, non-SDK, `NoSuchMethodError`, `NoSuchFieldError`, `VerifyError`, and `UnsatisfiedLinkError` signatures must be captured from logcat before claiming an Android 16 fix.
- The 2026-05-10 Android 16 evidence is emulator evidence (`sdk_gphone16k_x86_64` / 16 KB pages), not physical-device evidence. Do not rewrite this as a real-device pass.

## Local Verification Completed

| Check | Result | Evidence |
| --- | --- | --- |
| Full Gradle gate | pass | `logs/build/2026-05-09-a16-proc-maps-final-gate.txt` |
| Android 13 verifier runtime | pass | `logs/device/2026-05-09-verifier-a13-final.json`, `logs/device/2026-05-09-verifier-a13-final-logcat.txt` |
| Mobile MCP verifier launch | pass | Android 13 emulator rendered JSON with `procMaps`, `packageVisibility`, and `runtime` sections |
| 16 KB debug APK check | pass | command output from `scripts/verify-16kb-page-support.ps1 app/build/outputs/apk/debug/app-debug.apk` |
| 16 KB release APK check | pass | `logs/build/2026-05-09-16kb-release.txt` |
| 16 KB ciRelease APK check | pass | `logs/build/2026-05-09-16kb-ci-release.txt` |
| Full Android 16 emulator Gradle gate | pass | `logs/build/2026-05-10-a16-final-gate.txt` |
| Android 16 emulator verifier runtime | pass after relaunch | `logs/device/2026-05-10-verifier-a16-final.json`, `logs/device/2026-05-10-verifier-a16-final-logcat-2.txt` |
| Android 16 emulator DevCheck debug runtime | pass | `logs/device/2026-05-10-213712-a16-flar2.devcheck-*` |
| Android 16 emulator DevCheck ciRelease/R8 runtime | pass | `logs/device/2026-05-10-214023-a16-flar2.devcheck-*` |
| 16 KB debug APK check | pass | `logs/build/2026-05-10-16kb-debug.txt` |
| 16 KB release APK check | pass | `logs/build/2026-05-10-16kb-release.txt` |
| 16 KB ciRelease APK check | pass | `logs/build/2026-05-10-16kb-ciRelease.txt` |

Current Android 13 verifier maps counts are unredacted baseline values because the verifier run was not scoped/configured through LSPosed. They prove the probes execute and record suspicious line counts; they do not claim redaction is active.

Local `release` and `ciRelease` APKs are unsigned because release signing environment variables are not present in this workspace. For the 2026-05-10 runtime smoke only, `app-ciRelease-unsigned.apk` was signed into `logs/tmp/app-ciRelease-debugkey-signed.apk` with the Android debug key and installed on the Android 16 emulator. This does not replace production release signing evidence.

## Optional Offline APK Analysis

DexKit was considered only as offline research tooling. It is not a `:xposed` runtime dependency.

| Target APK | Evidence searched | Result |
| --- | --- | --- |
| DevCheck | `/proc/self/maps`, PackageManager queries, Build reads, Telephony reads, Runtime.exit/System.exit | not run; use only if logcat/tombstones/Frida remain inconclusive |
