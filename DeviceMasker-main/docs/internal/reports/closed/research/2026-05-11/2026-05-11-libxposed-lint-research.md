# libxposed Lint Research

## Executive summary

libxposed has a new upstream repository named `libxposed/lint` that provides a custom Android Lint detector for Xposed API-version compatibility. The detector is real in GitHub source, but the advertised Maven Central artifacts `io.github.libxposed:lint:1.0.0` and `io.github.libxposed:annotation:1.0.0` were not available from Maven Central during this research; both Maven metadata URLs returned HTTP 404. Device Masker should not add the dependency until publication is confirmed or a deliberate GitHub Packages/local-build plan exists.

For Device Masker today, practical value is limited because `:xposed` declares `minApiVersion=101` and currently depends on `io.github.libxposed:api:101.0.1`. The lint check becomes valuable when libxposed APIs above 101 are introduced or when Device Masker wants compile-time enforcement that future API usage is guarded by `getApiVersion()` checks.

## Research question and scope

Question: What is "libxposed lint", how does it work, and should Device Masker adopt it for the `:xposed` module?

Scope:
- Upstream libxposed lint repository and source behavior.
- Android Lint integration path.
- Maven/publishing availability.
- Impact on Device Masker `:xposed`, release/R8 validation, and existing static guards.

Out of scope:
- Applying dependency changes.
- Running project lint with a new dependency.
- Editing build files, Memory Bank, or project rules.

## Source inventory with links or local paths

Official and upstream sources:
- libxposed lint GitHub repository: https://github.com/libxposed/lint
- libxposed lint `SinceApiDetector.java`: https://raw.githubusercontent.com/libxposed/lint/master/lint/src/main/java/io/github/libxposed/lint/SinceApiDetector.java
- libxposed lint `SinceApi.java`: https://raw.githubusercontent.com/libxposed/lint/master/annotation/src/main/java/io/github/libxposed/annotation/SinceApi.java
- libxposed lint `lint/build.gradle.kts`: https://raw.githubusercontent.com/libxposed/lint/master/lint/build.gradle.kts
- libxposed lint `annotation/build.gradle.kts`: https://raw.githubusercontent.com/libxposed/lint/master/annotation/build.gradle.kts
- libxposed API package docs: https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html
- Maven Central metadata checked:
  - https://repo1.maven.org/maven2/io/github/libxposed/lint/maven-metadata.xml
  - https://repo1.maven.org/maven2/io/github/libxposed/annotation/maven-metadata.xml
  - https://repo1.maven.org/maven2/io/github/libxposed/api/maven-metadata.xml

Android/Google sources:
- Android lint configuration docs: https://developer.android.com/studio/write/lint
- AGP lint dependency behavior for `lintChecks` and `lintPublish`: https://developer.android.com/build/releases/agp-3-4-0-release-notes

Context7:
- `/libxposed/lint` documentation index for installation and detector behavior.

Project-local sources:
- `AGENTS.md`
- `docs/AGENTS_PROJECT_RULES.md`
- `xposed/AGENTS.md`
- `memory-bank/projectbrief.md`
- `memory-bank/productContext.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `xposed/src/main/resources/META-INF/xposed/module.prop`
- `gradle/libs.versions.toml`
- `xposed/build.gradle.kts`

## Verified facts

- Upstream `libxposed/lint` exists and is public. Its GitHub API metadata reports description `Lint for API checks`.
- The repository has two included modules: `:annotation` and `:lint`.
- `annotation/src/main/java/io/github/libxposed/annotation/SinceApi.java` defines `@SinceApi` with `RetentionPolicy.CLASS`, targets annotation types, constructors, fields, methods, packages, and types, and requires an integer value with `@IntRange(from = 101)`.
- `lint/src/main/java/io/github/libxposed/lint/LibXposedIssueRegistry.java` registers exactly one issue from `SinceApiDetector`.
- `SinceApiDetector` creates issue ID `XposedNewApi`, category `CORRECTNESS`, priority `6`, severity `ERROR`.
- The detector checks UAST calls, simple-name references, and overriding methods.
- The detector reads `minApiVersion` by walking the module `src` tree for `META-INF/xposed/module.prop`.
- If multiple module.prop files are found, the detector uses the minimum parsed `minApiVersion`.
- If `minApiVersion` is missing, unreadable, unparsable, or no `src` directory exists, the detector returns `UNKNOWN_API` and does not report.
- The detector reports when an accessed element, containing class, containing method, or containing field is annotated with `@SinceApi(N)`, `N` is greater than declared `minApiVersion`, and the usage is not guarded.
- Constant fields are ignored when `PsiField.computeConstantValue()` is non-null.
- Recognized guard shapes include direct `getApiVersion()` calls, Kotlin-style `apiVersion` references, numeric constants, and named constants matching `API_(number)`.
- The detector recognizes then-branch checks like `getApiVersion() >= N`, reversed comparisons like `N <= getApiVersion()`, equality when the value is high enough, logical `&&`, and logical `||` only when both sides independently imply the required API.
- The detector recognizes else-branch safety when a prior condition proves the API is below the requirement, such as `if (getApiVersion() < N) { fallback } else { newApi() }`.
- Upstream build files publish planned artifacts with group `io.github.libxposed`, artifact IDs `lint` and `annotation`, version `1.0.0`.
- The lint jar declares `Lint-Registry-v2: io.github.libxposed.lint.LibXposedIssueRegistry` in the jar manifest.
- The upstream lint module compiles against `com.android.tools.lint:lint-api:32.2.0` and `lint-checks:32.2.0`.
- Android's documented Gradle integration for local lint checks is `dependencies { lintChecks(...) }`.
- Android's documented `lintPublish` configuration is for packaging custom lint checks inside a published Android library AAR; Device Masker does not need `lintPublish` for local app/module validation.
- Maven Central metadata for `io.github.libxposed:api` resolved and reported latest/release `101.0.1`.
- Maven Central metadata for `io.github.libxposed:lint` returned HTTP 404 during this research.
- Maven Central metadata for `io.github.libxposed:annotation` returned HTTP 404 during this research.
- Device Masker currently declares `minApiVersion=101`, `targetApiVersion=101`, and `staticScope=false` in `xposed/src/main/resources/META-INF/xposed/module.prop`.
- Device Masker currently uses `compileOnly(libs.libxposed.api)` in `:xposed`, with version catalog value `libxposed-api = "101.0.1"`.
- Device Masker does not currently declare `libxposed-lint`, `libxposed-annotation`, `lintChecks`, or `lintPublish` for libxposed lint.

## Source-backed findings

### What libxposed lint does

libxposed lint is not a runtime hook framework feature. It is a build-time Android Lint extension that enforces Xposed framework API compatibility. Its single detector, `SinceApiDetector`, looks for APIs annotated with `io.github.libxposed.annotation.SinceApi` and reports unguarded use when the required Xposed API is higher than the module's declared `minApiVersion`.

The report message in source says to either wrap the usage in `if (getApiVersion() >= requiredApi)` or raise `minApiVersion`.

### What it does not do

It does not validate:
- R8-safe hook callback shapes.
- Direct Kotlin SAM `.intercept { ... }` callback risk.
- `compileOnly` vs `implementation` for `io.github.libxposed:api`.
- `java_init.list`, `scope.list`, or module entry correctness.
- Hook method existence on Android platform APIs.
- LSPosed runtime scope, module load, hook registration, spoof events, or target-app returned values.
- Device Masker RemotePreferences key correctness.
- Target-process safety rules such as no Timber, no random generation, no app-private JSON reads, and pass-through fallback.

Those remain Device Masker project-specific static tests, Detekt rules, greps, and runtime validation responsibilities.

### Publication status is the main blocker

Context7 indexed installation snippets for `compileOnly("io.github.libxposed:annotation:1.0.0")` and `lintChecks("io.github.libxposed:lint:1.0.0")`. Upstream Gradle files also define publications for these coordinates. However, Maven Central metadata for both artifact IDs returned 404 during this research. That means a normal `mavenCentral()` dependency add is likely to fail right now.

The repository build file also includes a GitHub Packages publishing target, but consuming GitHub Packages may require authentication and repository configuration. That is not a low-friction default for this project unless the user explicitly accepts it.

## Inferences

- Inference: The upstream repo is likely newer than the public Maven Central artifact state. The publishing intent exists in source, but the artifacts may not have been released to Central yet.
- Inference: Because `@SinceApi` is not present in Device Masker code and the current libxposed API artifact is API 101, adding the lint dependency today would probably produce no new findings unless upstream `api` classes are annotated with `@SinceApi` in the consumed artifact or Device Masker adds annotations to local APIs.
- Inference: The check will matter more when Device Masker moves beyond API 101 or supports a lower `minApiVersion` while conditionally using newer libxposed APIs.
- Inference: Device Masker should keep its existing `R8HookerAbiTest` and libxposed safety tests even if libxposed lint is adopted, because the upstream lint detector does not cover the release crash class that Device Masker already hit.

## Project impact

`:xposed`:
- Possible future dependency:
  - `compileOnly("io.github.libxposed:annotation:1.0.0")`
  - `lintChecks("io.github.libxposed:lint:1.0.0")`
- Do not add until artifact resolution is confirmed from the project's configured repositories.
- Existing `module.prop` path should be readable by the detector because it lives under `xposed/src/main/resources/META-INF/xposed/module.prop`; the detector walks `src` and matches the `META-INF/xposed/module.prop` suffix.

`:app`:
- No direct hook-runtime effect.
- If app-side libxposed service APIs ever gain `@SinceApi` annotations above the declared support level, app lint could matter too, but the detector's `minApiVersion` lookup expects a module `src/.../META-INF/xposed/module.prop`. The app module may not be the right place to run this unless it can find the xposed module metadata.

`:common`:
- No direct impact unless shared code starts exposing or consuming annotated libxposed APIs, which it should not.

`:verifier`:
- No direct impact. Runtime verification still requires target-app evidence.

Release/R8:
- No replacement for R8 runtime smoke. libxposed lint is API-version compatibility only.
- Existing `StableHooker` and R8 ABI guards remain mandatory.

Docs/rules:
- If adopted later, document it as an optional build-time Xposed API compatibility guard, not as hook-success evidence.

## Compatibility risks and edge cases

- Artifact availability: Central currently does not expose `lint` or `annotation`; blindly adding the coordinates would break builds.
- Lint API compatibility: upstream uses lint API `32.2.0`; Device Masker uses AGP `9.2.1`. Compatibility should be verified with a local `lint` run before committing any dependency adoption.
- Guard-pattern limitations: custom wrapper functions around `getApiVersion()` may not be recognized. The detector recognizes direct `getApiVersion()` or `apiVersion` forms and source-pattern fallback.
- Silent no-op risk: if the detector cannot find or parse `minApiVersion`, it returns unknown and does not report. A setup test should intentionally trigger a known violation before trusting the dependency.
- Scope limitation: the detector scans Java/Kotlin source UAST for annotated API use. It will not validate resource packaging, Gradle metadata, or LSPosed runtime behavior.
- Multiple `module.prop` files: the detector takes the minimum version. That is conservative but may surprise multi-source-set projects.
- GitHub Packages use: possible, but likely requires credentials and is less reproducible for external contributors than Maven Central.

## Unknowns and gaps

- Whether `io.github.libxposed:lint:1.0.0` and `io.github.libxposed:annotation:1.0.0` will be published to Maven Central soon.
- Whether the current `io.github.libxposed:api:101.0.1` artifact includes `@SinceApi` annotations in a way the detector can use.
- Whether the detector passes under Device Masker's current AGP/lint version without binary API incompatibilities.
- Whether GitHub Packages is accessible without authentication for these artifacts in this environment.
- Whether future libxposed API versions will add constants such as `API_102` or higher that Device Masker can use in guard checks.

## Recommendations

1. Do not add libxposed lint to Device Masker yet from Maven Central; the required artifacts returned 404.
2. Track `https://github.com/libxposed/lint` as a future build-safety candidate.
3. If adoption is desired now, create a separate plan to test one of these controlled paths:
   - build `libxposed/lint` locally and publish to a local Maven repo for evaluation only;
   - consume from GitHub Packages if credentials and reproducibility are acceptable;
   - wait for Maven Central release and use `mavenCentral()`.
4. When artifacts resolve, add catalog entries first, then wire `lintChecks` only in `:xposed`.
5. Add a small verification note/test fixture that proves `XposedNewApi` actually fires before treating the lint dependency as active protection.
6. Keep existing Device Masker checks for:
   - direct `.intercept { ... }` callback prohibition;
   - `compileOnly` libxposed API;
   - metadata API 101;
   - R8 keep rules;
   - LSPosed/logcat runtime evidence.

## Suggested next tasks

1. Recheck Maven Central for `io.github.libxposed:lint` and `io.github.libxposed:annotation` before any implementation.
2. If artifacts appear, run a small branch experiment:

```kotlin
// gradle/libs.versions.toml
libxposed-lint = "1.0.0"
libxposed-annotation = "1.0.0"
libxposed-lint = { group = "io.github.libxposed", name = "lint", version.ref = "libxposed-lint" }
libxposed-annotation = { group = "io.github.libxposed", name = "annotation", version.ref = "libxposed-annotation" }

// xposed/build.gradle.kts
compileOnly(libs.libxposed.annotation)
lintChecks(libs.libxposed.lint)
```

3. Verify with:

```powershell
.\gradlew.bat :xposed:lint --no-daemon --stacktrace
.\gradlew.bat lint --no-daemon --stacktrace
```

4. If it works, update `xposed/AGENTS.md` and `docs/AGENTS_PROJECT_RULES.md` with a narrow rule: libxposed lint is an API-version guard only, not runtime hook proof.

## Report file path

`docs/internal/reports/active/research/2026-05-11/2026-05-11-libxposed-lint-research.md`

## Write boundary confirmation

This research wrote exactly one file: this report. No source files, build files, Memory Bank files, public docs, logs, commits, branches, tags, or pushes were changed.
