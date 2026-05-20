# StrictMode And Detekt Guardrails Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add debug-only Android StrictMode guardrails and Detekt static analysis without changing libxposed runtime hook behavior.

**Architecture:** StrictMode is installed only from the app `Application` process and never from `:xposed` target-process code. Detekt is configured centrally from Gradle, runs across `:app`, `:common`, and `:xposed`, and uses baselines/module overrides so existing Xposed defensive patterns are not refactored blindly.

**Tech Stack:** Android StrictMode, Kotlin 2.3.0, AGP 9.2.1, Detekt 2.x Gradle plugin, Compose rules for Detekt, GitHub Actions.

---

## Research Summary

- Android official docs: `StrictMode.setThreadPolicy()` applies to the current thread, `StrictMode.setVmPolicy()` applies process-wide, `penaltyListener` exists for thread and VM violations from API 28, and `penaltyDeath()` crashes the process. Use `penaltyLog()` for debug guardrails.
- Detekt official docs: the Gradle plugin creates `detekt`, `detektMain`, `detektTest`, Android variant tasks, baseline tasks, and XML/HTML/SARIF reports. Android modules should configure Detekt at module level.
- Compose rules docs: add `io.nlopez.compose.rules:detekt:<version>` through `detektPlugins` and enable the `Compose:` rules in `detekt.yml`.
- Android Ninja Detekt template analysis: use `.agents/skills/claude-android-ninja/assets/detekt.yml.template` as the full central baseline instead of replacing it with a small hand-written config. The template already encodes Android/Kotlin/Compose choices for complexity, exception handling, forbidden imports, ktlint wrapper disables, and Compose rules.
- Project constraints: `:xposed` runs inside target app processes. Do not install StrictMode there, do not change hook registration, and do not weaken R8-safe `stableHooker` behavior.

Sources checked:
- Google Developer MCP: `developer.android.com/reference/android/os/StrictMode`, `StrictMode.ThreadPolicy.Builder`, `StrictMode.VmPolicy.Builder`.
- Detekt docs: `https://detekt.dev/docs/gettingstarted/gradle/`.
- Compose rules docs: `https://mrmans0n.github.io/compose-rules/detekt/`.
- Compose rules Maven metadata: `io.nlopez.compose.rules:detekt` latest `0.5.8`.
- Local skill docs/assets: `.agents/skills/claude-android-ninja/SKILL.md`, `.agents/skills/claude-android-ninja/references/android-strictmode.md`, `.agents/skills/claude-android-ninja/references/code-quality.md`, `.agents/skills/claude-android-ninja/assets/detekt.yml.template`, `.agents/skills/claude-android-ninja/assets/convention/DetektConventionPlugin.kt`, `.agents/skills/libxposed/SKILL.md`.

## File Structure

Create:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/StrictModeGuard.kt` - app-process StrictMode installer.
- `app/src/test/kotlin/com/astrixforge/devicemasker/service/diagnostics/StrictModeGuardTest.kt` - JVM/Robolectric no-crash and policy install tests.
- `config/detekt.yml` - central Detekt and Compose rules config copied from the Android Ninja Detekt template with only Device Masker-specific deltas.
- `xposed/detekt.yml` - Xposed-specific rule overrides for defensive hook code.
- `app/detekt-baseline.xml`, `common/detekt-baseline.xml`, `xposed/detekt-baseline.xml` - generated initial baselines if existing findings would block rollout.

Modify:
- `gradle/libs.versions.toml` - Detekt plugin and Compose rules versions.
- `build.gradle.kts` - central Detekt Gradle configuration for Android modules.
- `app/build.gradle.kts` - gate Compose compiler metrics/reports behind Gradle properties.
- `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt` - call `StrictModeGuard.install()` before app startup work in debug builds.
- `.github/workflows/ci.yml` - run Detekt and upload reports.
- `app/AGENTS.md`, `xposed/AGENTS.md`, `memory-bank/activeContext.md`, `memory-bank/techContext.md`, `memory-bank/progress.md` - document new guardrails after implementation.

Do not modify:
- `xposed/src/main/kotlin/**` runtime hook files for StrictMode.
- `xposed/src/main/resources/META-INF/xposed/**`.
- `xposed/consumer-rules.pro` unless Detekt implementation proves a generated config file needs to be excluded from packaging, which should not happen.

---

### Task 1: Add Detekt Versions To The Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add Detekt versions**

Add under `[versions]`:

```toml
detekt = "2.0.0-alpha.3"
composeRules = "0.5.8"
```

- [ ] **Step 2: Add Compose rules library**

Add under `[libraries]`:

```toml
detekt-compose-rules = { group = "io.nlopez.compose.rules", name = "detekt", version.ref = "composeRules" }
```

- [ ] **Step 3: Add Detekt plugin alias**

Add under `[plugins]`:

```toml
detekt = { id = "dev.detekt", version.ref = "detekt" }
```

- [ ] **Step 4: Verify catalog parses**

Run:

```powershell
.\gradlew.bat help --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If Gradle reports Detekt/Kotlin metadata incompatibility, switch only `detekt` to `2.0.0-alpha.2` and rerun `help`; keep `composeRules = "0.5.8"` unless dependency resolution fails.

- [ ] **Step 5: Commit**

```powershell
git add gradle\libs.versions.toml
git commit -m "build: add detekt catalog entries"
```

---

### Task 2: Configure Detekt Centrally Without Build Logic

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add Detekt imports**

Add at the top of `build.gradle.kts`:

```kotlin
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.artifacts.VersionCatalogsExtension
```

- [ ] **Step 2: Add Detekt plugin to root plugins**

Add inside the existing `plugins` block:

```kotlin
alias(libs.plugins.detekt) apply false
```

- [ ] **Step 3: Add Android-module Detekt helper**

Add after the existing `ext { ... }` block:

```kotlin
fun Project.configureDeviceMaskerDetekt() {
    pluginManager.apply("dev.detekt")

    val libsCatalog =
        rootProject.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

    dependencies {
        add("detektPlugins", libsCatalog.findLibrary("detekt-compose-rules").get())
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        ignoreFailures = false
        failOnSeverity = FailOnSeverity.Error
        basePath.set(rootProject.layout.projectDirectory)
        config.setFrom(rootProject.file("config/detekt.yml"))

        val moduleConfig = file("detekt.yml")
        if (moduleConfig.exists()) {
            config.from(moduleConfig)
        }

        baseline = file("detekt-baseline.xml")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget.set("17")
        reports {
            checkstyle.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            markdown.required.set(false)
        }
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/resources/**")
    }

    tasks.withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget.set("17")
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/resources/**")
    }
}

subprojects {
    plugins.withId("com.android.application") {
        configureDeviceMaskerDetekt()
    }
    plugins.withId("com.android.library") {
        configureDeviceMaskerDetekt()
    }
}
```

Note: Android Ninja provides `assets/convention/DetektConventionPlugin.kt` for projects that already use `build-logic`. This repo does not currently use `build-logic`; keep the first rollout in the root Gradle file to avoid a structural build-system migration. If Device Masker later adopts convention plugins, port this helper into build logic using the skill asset as the reference.

- [ ] **Step 4: Verify Detekt tasks exist**

Run:

```powershell
.\gradlew.bat tasks --all --no-daemon
```

Expected: output contains `:app:detekt`, `:common:detekt`, `:xposed:detekt`, and type-resolution tasks such as `detektDebug` or `detektMain`.

- [ ] **Step 5: Commit**

```powershell
git add build.gradle.kts
git commit -m "build: configure detekt for android modules"
```

---

### Task 3: Add Template-Based Detekt Rules And Xposed Overrides

**Files:**
- Create: `config/detekt.yml`
- Create: `xposed/detekt.yml`

- [ ] **Step 1: Copy the Android Ninja Detekt template**

Copy the full template into the repo-level config:

```powershell
Copy-Item .agents\skills\claude-android-ninja\assets\detekt.yml.template config\detekt.yml
```

Expected: `config/detekt.yml` starts with `config.validation: true`, keeps Detekt 2-compatible `processors` and `console-reports` exclusions, and includes the full `Compose:` rule set.

Implementation note: Detekt 2.0.0-alpha.3 rejected several older template property names (`threshold`, `functionThreshold`, `constructorThreshold`, `EmptyKtFile`, `MayBeConst`, etc.). Generate Detekt's live config with `detektGenerateConfig`, then reapply the template's intended Android/Compose policy choices using Detekt 2 property names. Do not disable config validation to hide schema drift.

- [ ] **Step 2: Keep template defaults unless evidence proves they are wrong**

Do not replace the template with a reduced custom config. Preserve these important template decisions:

- `TooGenericExceptionCaught` and `TooGenericExceptionThrown` stay active globally; Xposed gets a module override instead.
- `ForbiddenImport` keeps `androidx.lifecycle.LiveData` and `androidx.lifecycle.MutableLiveData` forbidden.
- `ForbiddenComment` keeps `TODO:`, `FIXME:`, and `STOPSHIP:` active. Existing debt should be baselined or fixed, not hidden by weakening the central rule.
- `MagicNumber`, `MaxLineLength = 120`, `ReturnCount max = 2`, and `WildcardImport` stay active globally.
- Compose rules stay enabled from the template, including `Material2`, `ModifierClickableOrder`, `ModifierMissing`, `ModifierReused`, `MutableStateAutoboxing`, `RememberMissing`, `ViewModelForwarding`, and `ViewModelInjection`.
- Template-disabled rules such as `ModifierComposed`, `PreviewNaming`, `UnstableCollections`, `SpreadOperator`, `UnusedImports`, and `UnusedPrivateMember` stay disabled for the first rollout.

Reason: this keeps the implementation aligned with the skill asset and avoids inventing a one-off ruleset that will drift from the Android guidance.

- [ ] **Step 3: Create Xposed override**

Create `xposed/detekt.yml`:

```yaml
exceptions:
  TooGenericExceptionCaught:
    active: false
  TooGenericExceptionThrown:
    active: false

complexity:
  LongMethod:
    allowedLines: 140
  CyclomaticComplexMethod:
    allowedComplexity: 25

style:
  ReturnCount:
    max: 6
  MagicNumber:
    active: false
```

Reason: Xposed hookers intentionally isolate OEM/reflection/libxposed failures and frequently work with framework constants, reflection branches, and defensive guard returns. This override prevents Detekt from forcing unsafe target-process refactors while keeping central app/common rules strong.

- [ ] **Step 4: Verify copied config stayed template-aligned**

Run:

```powershell
Compare-Object (Get-Content .agents\skills\claude-android-ninja\assets\detekt.yml.template) (Get-Content config\detekt.yml)
```

Expected: no output unless an intentional Device Masker-specific central delta was documented in this task. Prefer exact template parity for the first rollout.

- [ ] **Step 5: Verify config syntax**

Run:

```powershell
.\gradlew.bat :app:detekt --no-daemon
```

Expected: Detekt starts and reads `config/detekt.yml`. It may fail with findings before baselines are generated; it must not fail with YAML/config validation errors.

- [ ] **Step 6: Commit**

```powershell
git add config\detekt.yml xposed\detekt.yml
git commit -m "build: add detekt rule configuration"
```

---

### Task 4: Generate Initial Detekt Baselines

**Files:**
- Create: `app/detekt-baseline.xml`
- Create: `common/detekt-baseline.xml`
- Create: `xposed/detekt-baseline.xml`

- [ ] **Step 1: Generate module baselines**

Run:

```powershell
.\gradlew.bat :app:detektBaseline :common:detektBaseline :xposed:detektBaseline --no-daemon
```

Expected: baseline XML files are created for modules with findings. If a module has no findings and no baseline is created, do not create an empty baseline by hand.

- [ ] **Step 2: Run Detekt with baselines**

Run:

```powershell
.\gradlew.bat detekt --no-daemon
```

Expected: `BUILD SUCCESSFUL`. Reports should exist under each module's `build/reports/detekt/`.

- [ ] **Step 3: Check Xposed runtime source stayed unchanged**

Run:

```powershell
git diff -- xposed\src\main\kotlin
```

Expected: no output. If there is output, stop and revert only the accidental Xposed runtime source edits before continuing.

- [ ] **Step 4: Commit**

```powershell
git add app\detekt-baseline.xml common\detekt-baseline.xml xposed\detekt-baseline.xml
git commit -m "build: baseline existing detekt findings"
```

---

### Task 5: Gate Compose Compiler Metrics Behind Properties

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Replace current always-on Compose compiler output**

Replace:

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler/reports")
    metricsDestination = layout.buildDirectory.dir("compose_compiler/metrics")
}
```

with:

```kotlin
composeCompiler {
    val enableReports =
        providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()
    val enableMetrics =
        providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()

    if (enableReports) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler/reports")
    }
    if (enableMetrics) {
        metricsDestination = layout.buildDirectory.dir("compose_compiler/metrics")
    }
}
```

- [ ] **Step 2: Verify normal debug compile does not emit metrics by default**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`; no new `app/build/compose_compiler/metrics` files are required for a normal build.

- [ ] **Step 3: Verify explicit metrics still work**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin -PenableComposeCompilerReports=true -PenableComposeCompilerMetrics=true --no-daemon
```

Expected: `BUILD SUCCESSFUL`; `app/build/compose_compiler/reports` and `app/build/compose_compiler/metrics` exist.

- [ ] **Step 4: Commit**

```powershell
git add app\build.gradle.kts
git commit -m "build: gate compose compiler diagnostics"
```

---

### Task 6: Add Debug-Only StrictMode Guard

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/StrictModeGuard.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/DeviceMaskerApp.kt`

- [ ] **Step 1: Create StrictModeGuard**

Create `StrictModeGuard.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import android.os.Build
import android.os.StrictMode
import com.astrixforge.devicemasker.BuildConfig

internal object StrictModeGuard {
    fun install() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectFileUriExposure()
                .detectCleartextNetwork()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        detectUnsafeIntentLaunch()
                    }
                }
                .penaltyLog()
                .build()
        )
    }
}
```

StrictMode uses `penaltyLog()` only. Do not add `penaltyDeath()`, `penaltyDropBox()`, or production `penaltyListener()` in this project because diagnostics are local-first and no crash reporter is installed.

- [ ] **Step 2: Install from DeviceMaskerApp**

In `DeviceMaskerApp.kt`, add import:

```kotlin
import com.astrixforge.devicemasker.service.diagnostics.StrictModeGuard
```

Call it immediately after `instance = this`:

```kotlin
StrictModeGuard.install()
```

- [ ] **Step 3: Verify no Xposed runtime StrictMode reference exists**

Run:

```powershell
Get-ChildItem -Path xposed\src\main\kotlin -Recurse -Filter *.kt | Select-String -Pattern 'StrictMode'
```

Expected: no output.

- [ ] **Step 4: Commit**

```powershell
git add app\src\main\kotlin\com\astrixforge\devicemasker\service\diagnostics\StrictModeGuard.kt app\src\main\kotlin\com\astrixforge\devicemasker\DeviceMaskerApp.kt
git commit -m "debug: install app strictmode guard"
```

---

### Task 7: Add StrictMode And Xposed Safety Tests

**Files:**
- Create: `app/src/test/kotlin/com/astrixforge/devicemasker/service/diagnostics/StrictModeGuardTest.kt`
- Create: `xposed/src/test/kotlin/com/astrixforge/devicemasker/xposed/StrictModeIsolationTest.kt`

- [ ] **Step 1: Add app StrictMode test**

Create `StrictModeGuardTest.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import kotlin.test.Test

class StrictModeGuardTest {
    @Test
    fun installDoesNotThrowInDebugUnitTests() {
        StrictModeGuard.install()
    }
}
```

- [ ] **Step 2: Add Xposed isolation test**

Create `StrictModeIsolationTest.kt`:

```kotlin
package com.astrixforge.devicemasker.xposed

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class StrictModeIsolationTest {
    @Test
    fun xposedRuntimeSourceDoesNotInstallStrictMode() {
        val sourceRoot = File("src/main/kotlin")
        val matches =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains("StrictMode") }
                .map { it.invariantSeparatorsPath }
                .toList()

        assertFalse(
            matches.isNotEmpty(),
            "StrictMode must stay app-process only, but was found in: $matches",
        )
    }
}
```

- [ ] **Step 3: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.StrictModeGuardTest :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.StrictModeIsolationTest --no-daemon
```

Expected: both tests pass.

- [ ] **Step 4: Run R8/libxposed ABI guard**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`; direct runtime `.intercept { ... }` callbacks remain forbidden.

- [ ] **Step 5: Commit**

```powershell
git add app\src\test\kotlin\com\astrixforge\devicemasker\service\diagnostics\StrictModeGuardTest.kt xposed\src\test\kotlin\com\astrixforge\devicemasker\xposed\StrictModeIsolationTest.kt
git commit -m "test: guard strictmode xposed isolation"
```

---

### Task 8: Add Detekt To CI

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add Detekt step after Spotless**

In the `quality` job, after `Spotless check`, add:

```yaml
      - name: Detekt
        run: ./gradlew detekt --build-cache --stacktrace
```

- [ ] **Step 2: Ensure report upload includes Detekt reports**

The existing artifact paths already include module `build/reports/**`. Confirm the upload step still contains:

```yaml
          path: |
            app/build/reports/**
            common/build/reports/**
            xposed/build/reports/**
```

- [ ] **Step 3: Verify CI-equivalent local quality gate**

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :app:compileDebugKotlin :common:compileDebugKotlin :xposed:compileDebugKotlin lint test --build-cache --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add .github\workflows\ci.yml
git commit -m "ci: run detekt in quality gate"
```

---

### Task 9: Full Safety Verification

**Files:**
- No source edits.

- [ ] **Step 1: Run full Gradle gate**

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run release R8 build smoke gate**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest :app:assembleRelease --no-daemon
```

Expected: `BUILD SUCCESSFUL`. This proves the Detekt/StrictMode work did not alter the R8-safe hooker ABI guard.

- [ ] **Step 3: Verify no StrictMode or Timber regression in Xposed**

Run:

```powershell
Get-ChildItem -Path xposed\src\main\kotlin -Recurse -Filter *.kt | Select-String -Pattern 'StrictMode|Timber\.'
Get-ChildItem -Path xposed\src\main\kotlin -Recurse -Filter *.kt | Select-String -Pattern 'xi\.hook\(m\)\.intercept\s*\{'
```

Expected: no output except intentional comments if already present. If a direct runtime callback match appears in production code, stop before claiming success.

- [ ] **Step 4: Optional debug runtime StrictMode smoke**

Run on emulator only if available:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -c
adb shell monkey -p com.astrixforge.devicemasker -c android.intent.category.LAUNCHER 1
adb logcat -d -t 500 | Select-String -Pattern 'StrictMode|Device Masker Application initialised'
```

Expected: app launches. StrictMode violations may be logged in debug, but the process must stay alive because only `penaltyLog()` is used.

- [ ] **Step 5: Commit verification docs if updated**

If verification results are documented in Memory Bank or reports:

```powershell
git add memory-bank\activeContext.md memory-bank\techContext.md memory-bank\progress.md
git commit -m "docs: record strictmode detekt validation"
```

---

### Task 10: Documentation Updates

**Files:**
- Modify: `app/AGENTS.md`
- Modify: `xposed/AGENTS.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/techContext.md`
- Modify: `memory-bank/progress.md`

- [ ] **Step 1: Update app guide**

Add to `app/AGENTS.md` under `Diagnostics & Root`:

```md
- `StrictModeGuard`: debug-only app-process StrictMode policy. Never install StrictMode from `:xposed`.
```

- [ ] **Step 2: Update xposed guide**

Add to `xposed/AGENTS.md` under `Critical Constraints`:

```md
- **No StrictMode installation** in target app or system_server hook paths. StrictMode is app-process debug tooling only.
```

- [ ] **Step 3: Update Memory Bank**

Record:

```md
- Debug builds install app-process StrictMode through `StrictModeGuard`; no StrictMode code runs from `:xposed`.
- Detekt runs across `:app`, `:common`, and `:xposed` with central config, Xposed-safe overrides, and initial baselines.
- CI quality gate includes Detekt alongside Spotless, lint, tests, and build tasks.
```

- [ ] **Step 4: Verify documentation references**

Run:

```powershell
Select-String -Path app\AGENTS.md,xposed\AGENTS.md,memory-bank\activeContext.md,memory-bank\techContext.md,memory-bank\progress.md -Pattern 'StrictModeGuard','detekt','No StrictMode'
```

Expected: the new guardrails are visible in module docs and Memory Bank.

- [ ] **Step 5: Commit**

```powershell
git add app\AGENTS.md xposed\AGENTS.md memory-bank\activeContext.md memory-bank\techContext.md memory-bank\progress.md
git commit -m "docs: document strictmode detekt guardrails"
```

---

## Xposed/Libxposed Safety Rules For This Plan

- StrictMode must remain in `:app` only.
- Do not call `StrictMode.setThreadPolicy()` or `StrictMode.setVmPolicy()` from `XposedEntry`, hookers, diagnostics sink, or system_server service setup.
- Do not change `java_init.list`, `module.prop`, `scope.list`, `consumer-rules.pro`, or `StableHooker` as part of this work.
- Detekt findings in `:xposed` must be addressed by baseline, narrow module config, or a separately reviewed hook-safety refactor. Do not rewrite hook code just to satisfy generic static-analysis style rules.
- Before claiming completion, run `R8HookerAbiTest` and scan production Xposed Kotlin sources for direct `.intercept { ... }`, `StrictMode`, and `Timber`.

## Final Verification Command Set

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease --no-daemon
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest :app:assembleRelease --no-daemon
Get-ChildItem -Path xposed\src\main\kotlin -Recurse -Filter *.kt | Select-String -Pattern 'StrictMode|Timber\.|xi\.hook\(m\)\.intercept\s*\{'
```

Expected:
- Gradle commands return `BUILD SUCCESSFUL`.
- Xposed scan has no unsafe production matches.
- Release minification remains enabled.
- No runtime hook source was changed for StrictMode.

## Self-Review

- Spec coverage: StrictMode, Detekt, CI, app-only install, xposed/libxposed safety, Memory Bank/docs, and verification are each covered by tasks.
- Placeholder scan: no open-ended implementation placeholders are required; generated Detekt baselines are produced by Gradle commands because their exact contents depend on current findings.
- Type consistency: planned Kotlin files use `StrictModeGuard.install()`, and the `DeviceMaskerApp` import/call matches that name.

Plan complete and saved to `docs/superpowers/plans/2026-05-06-strictmode-detekt-guardrails.md`. Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.
