# Native Hook Engine Decision Record

Date started: 2026-05-09
Status: active

## Decision Rule

Do not add a native hook engine unless all of these are true:

1. `:verifier` proves Java line, byte, RandomAccessFile, and NIO maps reads are redacted correctly.
2. A native probe or DevCheck evidence still shows LSPosed/module strings from `/proc/self/maps`.
3. The Android 16 crash path is understood well enough that adding native code is not hiding the real bug.
4. The implementation can stay per-app opt-in and default-off with a kill switch.
5. Debug, release, and `ciRelease` APKs pass 16 KB page-size verification after native dependencies are added.

## Engine Order

1. ByteHook first, because it is Android-focused PLT hooking and Maven/Prefab friendly.
2. ShadowHook only if ByteHook cannot cover the proven native path.
3. xHook and Dobby are reference-only for this project track.

## Evidence Summary

| Source | Java line redacted | Java byte redacted | NIO redacted | Native read leaks suspicious lines | Decision |
| --- | --- | --- | --- | --- | --- |
| A13 verifier | probe present; redaction not active in unscoped baseline | probe present; redaction not active in unscoped baseline | production hook implemented; verifier NIO probe still pending | not implemented | keep native hooks out |
| A16 emulator verifier | probe present; redaction not active in unscoped baseline | probe present; redaction not active in unscoped baseline | production hook implemented; verifier NIO probe still pending | not implemented | keep native hooks out |
| A16 real-device verifier | pending physical-device run | pending physical-device run | pending physical-device run | pending physical-device run | keep native hooks out |
| DevCheck on A16 emulator | not applicable | not applicable | not applicable | no crash evidence tying DevCheck to native maps reads | keep native hooks out |

## Current Result

Native hook engine not justified. The current implementation adds path-aware Java maps line filtering, opt-in Java byte redaction, opt-in NIO redaction, and verifier probes. The 2026-05-10 Android 16 emulator runs did not reproduce a DevCheck crash under debug or debug-key-signed ciRelease/R8. No evidence yet proves that DevCheck's Android 16 crash is caused by native maps scanning or that Java coverage is insufficient.
