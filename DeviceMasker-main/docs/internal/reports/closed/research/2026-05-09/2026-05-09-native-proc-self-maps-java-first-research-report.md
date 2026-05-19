# Native `/proc/self/maps` Java-First Hardening Research Report

Date: 2026-05-09
Branch context: `release/0.1.5`
Status: implemented for Java-first production hooks; native hook engine still not implemented

## Executive Verdict

Device Masker should implement a Java-first `/proc/self/maps` hardening layer, but it must be honest about the boundary: Java can cover Java/Kotlin readers and many Android libcore-backed file APIs, but it cannot reliably hide direct native `libc` / syscall reads from C/C++ code.

The best approach is:

1. Improve the current Java filter first.
2. Add verifier evidence for Java and native maps reads.
3. Keep the Java layer as the stable default.
4. Add any true native redaction later as an advanced, opt-in, kill-switch guarded track.

This gives maximum practical Java coverage without turning target startup into risky low-level hook sludge.

## Current Project State

Original implementation before this track:

- `AntiDetectHooker` runs first from `XposedEntry`.
- It filters stack traces.
- It hides selected manager/framework packages through PackageManager hooks.
- It hooked `java.io.BufferedReader.readLine()` globally.
- If any line contained known library fragments such as `libxposed`, `liblspd`, `libriru`, `libsandhook`, `libpine`, `libwhale`, `libdobby`, or `libsubstrate`, it returned an empty string.

Original limitation:

- The hook is not path-aware.
- It affects every `BufferedReader.readLine()` call in the target process.
- It only covers readers that reach `BufferedReader.readLine()`.
- Native C/C++ reads using `fopen`, `open`, `read`, `pread`, `readlink`, or direct syscall paths bypass it.
- Before this track, the `:verifier` module did not test `/proc/self/maps`, Java maps reads, native maps reads, or suspicious line counts.

## Implementation Update

Implemented in this branch:

- `AntiDetectHooker` no longer owns the global maps line filter.
- `ProcMapsHooker` owns path-aware Java maps filtering for `/proc/self/maps`, `/proc/<pid>/maps`, `/proc/self/smaps`, and `/proc/<pid>/smaps`.
- Hidden maps lines are skipped, not replaced with blank strings.
- Java line filtering tracks `FileReader`, `BufferedReader`, and `RandomAccessFile` instances so unrelated readers are not modified.
- Java byte redaction for `FileInputStream` is implemented but guarded by an explicit per-app RemotePreferences key.
- Java NIO redaction for `Files.readAllLines`, `Files.readString`, and `Files.newBufferedReader` is implemented but guarded by an explicit per-app RemotePreferences key.
- `HookFamilyPolicy` adds per-app hook-family isolation, defaulting to enabled to preserve current behavior.
- `SharedPrefsKeys`, `ConfigSync`, and `PrefsKeys` own the new hook-family and proc-maps policy keys.
- `:verifier` now captures Java `BufferedReader`, Java `FileInputStream`, `RandomAccessFile`, package visibility, and runtime facts in `latest.json`.
- Static tests guard against HiddenApiBypass, production native hook dependencies, direct `.intercept { ... }` runtime callbacks, and libxposed API packaging mistakes.
- 2026-05-10 Android 16 emulator validation ran on `emulator-5554` / Pixel 10 Pro XL API 36.1 with SDK 36 and 16 KB pages. Debug and debug-key-signed `ciRelease` DevCheck runs stayed alive, registered all safe hooks, emitted spoof events, and did not show checked fatal/ABI signatures.

Still out of scope:

- Native `fopen/open/read/pread` redaction.
- Java hidden `libcore.io.IoBridge` hooks.
- Default-on byte/NIO proc-maps redaction.
- Any claim that Android 16 DevCheck is fixed on physical devices before real-device evidence exists.

## Source Findings

### `/proc/self/maps`

Linux documents `/proc/<pid>/maps` as the process memory map: each line describes a mapped memory region, permissions, offsets, device/inode information, and optionally the backing path. That backing path is why loaded framework/module libraries can be visible to apps.

For Android/Xposed detection, this matters because a target app can scan its own maps file and look for loaded framework libraries, injected module paths, APK/JAR paths, or native hook libraries.

### Java file reads on Android

Android's `FileInputStream` source shows that Android routes file opening and byte reads through libcore helpers such as `IoBridge.open(...)` and `IoBridge.read(...)` in the Java framework path. This means a Java-level Xposed module can potentially hook more than `BufferedReader.readLine()`:

- `FileInputStream(String)`
- `FileInputStream(File)`
- `FileReader(...)`
- `RandomAccessFile(...)`
- `java.nio.file.Files.*`
- hidden libcore APIs such as `libcore.io.IoBridge.open(...)` and `IoBridge.read(...)`, if available and hookable on the device

That is useful, but still only covers Java/libcore traffic. C/C++ code that calls bionic libc directly does not need to enter Java `FileInputStream`.

### Native hook support

Current libxposed API documentation says Java entry classes use `META-INF/xposed/java_init.list`, and native entries use `META-INF/xposed/native_init.list`. The archived LSPosed native-hook wiki describes a `native_init` entrypoint, `hook_func`, `unhook_func`, and callbacks when native libraries load.

There is a documentation mismatch to handle carefully:

- Modern libxposed API docs: `META-INF/xposed/native_init.list`
- Older LSPosed wiki examples: `assets/native_init`

For this repo, prefer the current libxposed API-101 Javadoc, because the project already uses modern `META-INF/xposed/java_init.list`.

### Android NDK / JNI guidance

Google Android NDK docs support adding C/C++ via Gradle `externalNativeBuild` with CMake or ndk-build, packaging shared libraries per ABI, and loading libraries with `System.loadLibrary`.

Android JNI guidance recommends:

- Use `System.loadLibrary("name")` with the undecorated library name.
- Prefer explicit `RegisterNatives()` from `JNI_OnLoad()` for normal JNI methods.
- Use a version script or `-fvisibility=hidden` so only necessary symbols are exported.
- Avoid long-running I/O under `@FastNative` / `@CriticalNative`.

For Device Masker, this means any native fallback must be very small, symbol-minimized, and treated as an advanced runtime risk.

## Threat Model

Apps can inspect hook evidence through these layers:

| Layer | Example detection | Java-first coverage |
| --- | --- | --- |
| Java stack traces | `Throwable.getStackTrace()` | Already covered |
| Java class lookup | `Class.forName("...xposed...")` | Implemented but disabled by default |
| Java package queries | `PackageManager` APIs | Covered by PM hooks |
| Java maps read | `BufferedReader(FileReader("/proc/self/maps"))` | Partially covered |
| Java byte maps read | `FileInputStream("/proc/self/maps").read(...)` | Not properly covered yet |
| Java NIO maps read | `Files.readAllLines(Path.of(...))` | Not covered yet |
| libcore open/read | `IoBridge.open/read` from Java framework path | Possible but hidden/API-fragile |
| Native libc read | `fopen/open/read/pread` from C/C++ | Not coverable with pure Java |
| Direct syscall | `syscall(__NR_openat, ...)` | Not coverable with pure Java |
| Linker/library enumeration | `dl_iterate_phdr`, linker APIs | Not solved by maps filtering |

## Maximum Java-First Design

### Phase 1: Make Current `BufferedReader` Filter Path-Aware

Current behavior filters all `BufferedReader.readLine()` calls. That is broad and can create weird behavior in unrelated file readers.

Recommended change:

- Track only readers known to originate from sensitive proc files.
- Sensitive paths:
  - `/proc/self/maps`
  - `/proc/<currentPid>/maps`
  - optionally `/proc/self/smaps`
  - optionally `/proc/<currentPid>/smaps`
- Keep `/proc/self/mountinfo`, `/proc/self/status`, and other files out of scope unless separately researched.

Implementation shape:

```kotlin
internal object ProcMapsReadTracker {
    private val mapsReaders = Collections.synchronizedMap(WeakHashMap<Any, Unit>())

    fun markReader(reader: Any) {
        mapsReaders[reader] = Unit
    }

    fun isMapsReader(reader: Any): Boolean = mapsReaders.containsKey(reader)
}
```

Then `BufferedReader.readLine()` should redact only if `chain.thisObject` is tracked.

### Phase 2: Hook Java Open Entrypoints

Hook constructors or methods that create readers/streams from file paths:

| API | Reason | Risk |
| --- | --- | --- |
| `FileReader(String/File)` | Common maps scanner path | Low-medium |
| `BufferedReader(Reader)` | Needed to mark wrapped readers | Medium |
| `FileInputStream(String/File)` | Common byte scanner path | Medium |
| `RandomAccessFile(String/File, mode)` | Less common but plausible | Medium |
| `Scanner(File/InputStream)` | Some simple scanners use it | Medium |
| `Files.readAllLines/readString/lines/newBufferedReader` | Java NIO path on newer Android | Medium |
| `libcore.io.IoBridge.open(String, int)` | Central Android Java file open path | Higher, hidden API |

Do not hook these globally without path checks. Every open/read hook must return original behavior for unrelated files.

### Phase 3: Add Java Byte-Level Filtering

Line filtering catches `BufferedReader`, but not direct Java byte reads:

```kotlin
FileInputStream("/proc/self/maps").read(buffer)
```

A Java-only byte filter can be done, but it is more complex because reads may split a line across multiple chunks.

Safer Java strategy:

- When a tracked `FileInputStream` reads from maps for the first time:
  - call original reads until EOF into a bounded buffer
  - redact complete lines
  - store sanitized bytes in a per-stream pending buffer
  - serve future `read(...)` calls from the sanitized buffer
- Use a maximum size guard.
- If size guard, exception, or parser issue happens, switch to pass-through.

This is more code than `readLine()`, but it covers Java byte scanners without native hooks.

### Phase 4: Hook Java NIO Convenience APIs

For APIs that return full text or lists, post-filtering is simpler:

- `Files.readAllLines(path, charset)` -> filter returned list
- `Files.readString(path)` -> filter returned string
- `Files.lines(path)` -> filter stream content if practical, or avoid if too fragile

These hooks should be optional and guarded because Android API availability varies by level.

### Phase 5: Add Verifier Evidence Before Native Work

Extend `:verifier` first. Add evidence fields:

```json
{
  "procMaps": {
    "javaBufferedReader": {
      "lineCount": 1234,
      "suspiciousLineCount": 0,
      "sampleRedacted": true
    },
    "javaFileInputStream": {
      "byteCount": 100000,
      "suspiciousLineCount": 0
    },
    "nativeFopenRead": {
      "lineCount": 1234,
      "suspiciousLineCount": 3
    }
  }
}
```

This gives clean proof:

- Java filter works.
- Java byte filter works.
- Native direct read still sees evidence until a real native hook exists.

## Redaction Rules

Redaction must be boring and deterministic.

Use a small central matcher:

```kotlin
internal fun shouldRedactMapsLine(line: String): Boolean =
    hiddenPatterns.any { pattern ->
        line.contains(pattern, ignoreCase = true)
    }
```

Suggested initial patterns:

- `libxposed`
- `liblspd`
- `lsposed`
- `lspd`
- `riru`
- `zygisk`
- `sandhook`
- `substrate`
- `edxposed`
- `devicemasker`
- `com.astrixforge.devicemasker`

Do not redact generic Android runtime libraries such as `libart`, `libc`, `linker`, or app-owned ordinary libraries. Over-redaction creates suspicious maps output and may break scanners that expect sane structure.

For line mode:

- Prefer skipping hidden lines by returning the next safe line.
- Returning `""` is less clean because it creates blank lines in a file format that normally does not depend on blank separator lines.

For byte mode:

- Preserve newline structure for remaining lines.
- Do not emit half-lines.
- Do not create malformed binary/text output.

## Recommended Architecture

### New Xposed Helper

Add a focused helper:

```text
xposed/src/main/kotlin/.../hooker/ProcMapsHooker.kt
```

Responsibilities:

- Detect sensitive proc maps paths.
- Track Java readers/streams.
- Filter maps lines.
- Filter Java byte reads where feasible.
- Log hook registration and redaction counts without logging raw maps content.

Keep `AntiDetectHooker` as the orchestrator:

```kotlin
ProcMapsHooker.hook(cl, xi, pkg, policy)
```

### Policy

Use app config / RemotePreferences:

```kotlin
data class RiskyHookPolicy(
    val riskyHooksEnabled: Boolean,
    val javaProcMapsRedaction: Boolean,
    val javaProcMapsByteRedaction: Boolean,
    val nativeProcMapsRedaction: Boolean,
)
```

Recommended defaults:

| Setting | Default |
| --- | --- |
| Java line redaction | On for enabled target apps |
| Java byte redaction | Off initially, then on after verifier proof |
| Java NIO redaction | Off initially |
| Native redaction | Off, advanced opt-in only |
| Emergency kill switch | Always available |

### Logging

Good logs:

- hook registered
- hook skipped
- redaction enabled/disabled
- redacted line count
- pass-through due to unsupported API
- pass-through due to parser failure

Bad logs:

- raw maps lines
- raw file paths that expose user/app internals unnecessarily
- full library inventories

## What Java Cannot Do

Pure Java cannot reliably cover:

- Native `fopen("/proc/self/maps", "r")`
- Native `open/openat/read/pread`
- direct Linux syscall wrappers
- native `dl_iterate_phdr`
- linker namespace inspection
- raw memory scanning for known symbols

This is not a failure of design. It is the boundary of the layer. Java hooks run in managed/framework paths; native code can bypass those paths.

## Native Fallback, If/When Needed

If the project later needs true native scanner resistance, use a very small native hook layer.

Minimum viable native track:

- Package one native library in `:xposed`.
- Add `META-INF/xposed/native_init.list`.
- Export only required `native_init` and optionally `JNI_OnLoad`.
- Hook only exact maps-related libc functions first.
- Start with `fopen` for `/proc/self/maps`, not broad `read`.
- Add `open/openat/read/pread` only after verifier evidence proves they are needed.
- Keep per-app opt-in and emergency kill switch.
- Disable native redaction automatically if crash/tombstone signatures appear.

Do not make native redaction default in the first implementation.

## Pros

- Better Java anti-detection coverage.
- Cleaner behavior than current global `BufferedReader` filtering.
- Stronger evidence through `:verifier`.
- Lower risk than jumping straight to native libc hooks.
- Keeps target app safety aligned with the project rules.
- Provides a clear path to native hardening later without mixing everything into one risky patch.

## Cons

- Java-only solution cannot cover true native scanners.
- Path-aware stream tracking is more complex than the current simple line hook.
- Byte-level Java filtering needs careful buffering to avoid corrupting reads.
- Hidden libcore hooks can vary across Android versions.
- NIO API availability varies by API level and desugaring behavior.
- Over-redaction can itself become a detection signal.

## Risk Matrix

| Track | Benefit | Crash risk | Detection bypass risk | Recommendation |
| --- | --- | --- | --- | --- |
| Current `BufferedReader.readLine()` filter | Medium | Low | Medium-high | Keep, but improve |
| Path-aware Java line filter | High for Java scanners | Low-medium | Medium | Do first |
| Java byte-stream filter | Medium-high | Medium | Medium | Do after verifier |
| Java NIO filter | Medium | Medium | Medium | Do after line filter |
| Hidden `IoBridge` hook | High for Java/libcore paths | Medium-high | Medium | Experimental |
| Native `fopen` hook | High | High | Low-medium | Opt-in advanced |
| Native `open/read` hook | Very high | Very high | Low | Last resort |

## Testing Plan

### Unit / Static Tests

- `shouldRedactMapsLine()` pattern tests.
- Path matching tests:
  - `/proc/self/maps`
  - `/proc/<pid>/maps`
  - non-current pid should not match unless explicitly allowed
  - `/proc/self/status` should not match
- Redaction preserves non-hidden lines.
- Redaction skips hidden lines instead of returning malformed text.
- Static test prevents raw maps lines from being logged.
- Static test prevents direct `.intercept { ... }` callback regression.

### Verifier Tests

Add `:verifier` probes:

- Java `BufferedReader(FileReader(...))`
- Java `FileInputStream(...).read(...)`
- Java `RandomAccessFile(...).readLine()`
- Java NIO `Files.readAllLines(...)` where available
- Native `fopen/read` probe through JNI

### Runtime Tests

For each target:

1. Disabled mode -> suspicious lines visible if framework loaded.
2. Java redaction enabled -> Java probes hide suspicious lines.
3. Native probe still visible before native layer -> confirms boundary.
4. Target app launches without fatal crash.
5. Release/R8 build repeats same result.
6. LSPosed/logcat checked for hook registration and no fatal signatures.

### Commands

```powershell
.\gradlew.bat spotlessCheck detekt :xposed:testDebugUnitTest :verifier:assembleDebug assembleDebug assembleRelease --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r verifier\build\outputs\apk\debug\verifier-debug.apk
adb shell am force-stop com.astrixforge.devicemasker.verifier
adb shell monkey -p com.astrixforge.devicemasker.verifier -c android.intent.category.LAUNCHER 1
adb shell run-as com.astrixforge.devicemasker.verifier cat files/verifier/latest.json
adb logcat -d -t 1500
```

## Implementation Sequence

Recommended sequence:

```text
1. Add verifier maps probes -> verify: JSON shows Java and native baseline maps evidence.
2. Extract maps redaction matcher -> verify: unit tests pass.
3. Replace broad BufferedReader filtering with path-aware line filtering -> verify: Java reader probe redacts only maps.
4. Add Java byte-stream filtering for tracked FileInputStream -> verify: Java byte probe redacts.
5. Add Java NIO convenience filtering where available -> verify: NIO probe redacts or safely skips.
6. Add docs/Memory Bank updates -> verify: architecture and active context match behavior.
7. Only then evaluate native fallback -> verify: native probe demonstrates remaining gap.
```

## Final Recommendation

Implement maximum Java coverage first. It is the right next move.

The first production-ready target should be:

- path-aware Java maps line filtering
- verifier evidence
- Java byte-stream filtering after proof
- no native libc hook yet

Native `/proc/self/maps` redaction should remain a separate advanced track. It is useful, but it should be added only when the verifier proves Java cannot cover the target detection path and only behind explicit risky-hook opt-in.

## Sources

- Current repo:
  - `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/AntiDetectHooker.kt`
  - `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt`
  - `verifier/src/main/kotlin/com/astrixforge/devicemasker/verifier/VerifierActivity.kt`
  - `common/src/main/kotlin/com/astrixforge/devicemasker/common/SharedPrefsKeys.kt`
  - `docs/internal/reports/closed/research/2026-05-09/2026-05-09-device-masker-combined-research-audit.md`
- libxposed API Javadoc: https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html
- LSPosed archived native hook wiki: https://github.com/LSPosed/LSPosed/wiki/Native-Hook
- Android FileInputStream AOSP source: https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-31/+/refs/heads/main/java/io/FileInputStream.java
- Android Gradle external native builds: https://developer.android.com/studio/projects/gradle-external-native-builds
- Android JNI tips: https://developer.android.com/ndk/guides/jni-tips
- Linux `proc_pid_maps(5)`: https://man7.org/linux/man-pages/man5/proc_pid_maps.5.html
- Android anti-hooking maps detection example: https://d3adend.org/blog/posts/android-anti-hooking-techniques-in-java/
- Context7 Android NDK documentation query for CMake/JNI/ABI guidance.
- Google Developer Knowledge MCP queries for Android NDK, JNI, FileInputStream, and native-library build behavior.
