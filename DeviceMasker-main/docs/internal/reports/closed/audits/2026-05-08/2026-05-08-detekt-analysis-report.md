# Detekt Maximum Strictness Report

**Project:** Device Masker  
**Updated:** May 2026  
**Scope:** `:app`, `:common`, `:xposed` Detekt policy and baseline cleanup  
**Decision:** Move toward maximum useful Detekt strictness with project-aware Xposed safeguards.

## Executive Summary

Device Masker should use Detekt as a hard quality gate, not only as a report generator. The best target state is:

- `buildUponDefaultConfig = true`
- `allRules = true`
- `ignoreFailures = false`
- `failOnSeverity = Error`
- SARIF/HTML/checkstyle reports uploaded in CI
- Compose rules enabled aggressively
- baselines kept only as temporary legacy-debt lists
- `:xposed` keeps narrow safety overrides where strict style rules conflict with safe hook behavior

This is stricter than the current setup. The current root Gradle config already has `buildUponDefaultConfig = true`, `ignoreFailures = false`, module baselines, and reports enabled, but `allRules` is still `false`.

## Current Baseline Snapshot

Current `app/detekt-baseline.xml` contains **255** suppressed findings, not 260.

| Rule | Count | Recommended handling |
| --- | ---: | --- |
| FunctionNaming | 152 | Config fix for Compose PascalCase |
| LongMethod | 25 | Refactor gradually, especially non-UI logic |
| MagicNumber | 23 | Name behavior constants; tolerate/configure UI constants carefully |
| ParameterNaming | 8 | Review callback/lambda cases individually |
| MaxLineLength | 5 | Fix directly |
| ModifierMissing | 5 | Fix directly with `modifier: Modifier = Modifier` |
| ViewModelInjection | 5 | Configure for project factory pattern or disable if manual DI remains intentional |
| CyclomaticComplexMethod | 5 | Refactor business logic; suppress only UI state rendering when justified |
| TooGenericExceptionCaught | 4 | Fix or narrow suppressions; special handling for Xposed safety |
| MatchingDeclarationName | 3 | Fix or file-suppress only when file intentionally groups UI helpers |
| MultipleEmitters | 3 | Fix directly with a single root emitter |
| PreviewPublic | 3 | Make previews private |
| ReturnCount | 3 | Prefer guard clauses/refactor; `:xposed` can be more tolerant |
| LambdaParameterInRestartableEffect | 3 | Fix directly |
| ComposableParamOrder | 3 | Fix directly |
| CompositionLocalAllowlist | 2 | Add project-approved allowlist entries |
| LambdaParameterEventTrailing | 1 | Fix directly |
| ObjectPropertyNaming | 1 | Fix directly |
| SwallowedException | 1 | Log, document, or narrow suppress |

## Official Docs Checked

- Detekt Gradle docs confirm `buildUponDefaultConfig`, `allRules`, `baseline`, `ignoreFailures`, `failOnSeverity`, report generation, and Android/JVM type-resolution tasks such as `detektMain`.
- Detekt baseline docs describe baselines as ignored-current-findings files, intended so only new findings are printed on later analysis.
- Detekt Compose docs confirm `@Composable` functions returning `Unit` can be PascalCase and recommend either broadening `functionPattern` or setting `ignoreAnnotated: ['Composable']`.
- Compose rules docs confirm Detekt rules and options for `ModifierMissing`, `MultipleEmitters`, `CompositionLocalAllowlist`, `ViewModelInjection`, `PreviewNaming`, and `UnstableCollections`.
- Android Compose docs confirm modifier parameters are a best practice for reusable composables.

## Target Detekt Operating Model

### Gradle Policy

Change root Detekt configuration to:

```kotlin
extensions.configure<DetektExtension> {
    buildUponDefaultConfig.set(true)
    allRules.set(true)
    parallel.set(true)
    ignoreFailures.set(false)
    failOnSeverity.set(FailOnSeverity.Error)
    config.setFrom(rootProject.file("config/detekt.yml"))
    baseline.set(file("detekt-baseline.xml"))
}
```

Keep the existing report outputs:

```kotlin
reports {
    checkstyle.required.set(true)
    html.required.set(true)
    sarif.required.set(true)
}
```

### Local Commands

Use the fast gate during normal work:

```powershell
.\gradlew.bat spotlessApply detekt --no-daemon
```

Use type-resolution checks when doing cleanup passes or before major commits:

```powershell
.\gradlew.bat detektMain --no-daemon
```

Use the full quality gate before release or after large refactors:

```powershell
.\gradlew.bat spotlessCheck detekt lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon
```

## Recommended Strict Rule Policy

### Enable Maximum Useful Rules

Enable or keep enabled:

- all potential-bugs rules that compile under the current Detekt version
- all exception rules except where `:xposed` has documented runtime-safety needs
- coroutine rules, including dispatcher injection and sleep-vs-delay checks
- complexity rules: long method, long parameter list, nested block depth, cognitive/cyclomatic complexity, too many functions
- performance rules, including primitive arrays and temporary allocation rules
- strict style rules for imports, TODO/FIXME/STOPSHIP, line length, return count, magic numbers, forbidden calls, naming, and redundant code
- Compose rules, including `ModifierMissing`, `ModifierNotUsedAtRoot`, `MultipleEmitters`, `MutableStateParam`, `RememberMissing`, `ComposableParamOrder`, and `LambdaParameterInRestartableEffect`

### Compose-Specific Config

Use config for known Compose false positives instead of suppressing each file:

```yaml
naming:
  FunctionNaming:
    ignoreAnnotated:
      - 'Composable'

Compose:
  ModifierMissing:
    active: true
    checkModifiersForVisibility: public_and_internal
  MultipleEmitters:
    active: true
  UnstableCollections:
    active: true
  PreviewNaming:
    active: true
  CompositionLocalAllowlist:
    active: true
    allowedCompositionLocals:
      - LocalMotionPolicy
      - LocalEmphasizedTypography
```

Fix these in code, do not suppress:

- `MultipleEmitters`
- `ModifierMissing`
- `ModifierNotUsedAtRoot`
- `ComposableParamOrder`
- `LambdaParameterInRestartableEffect`
- `PreviewPublic`
- `MaxLineLength`

## Xposed/libxposed Safety Impact

Strict Detekt itself will not break Xposed/libxposed runtime code. Static analysis only reports or fails builds. The risk appears when an agent blindly changes hook code to satisfy generic style rules.

For `:xposed`, correctness rules are stronger than style rules:

- Do not remove defensive fallback behavior just to satisfy complexity or return-count rules.
- Do not replace broad hook-registration catches unless the libxposed failure model is preserved.
- Always rethrow `XposedFrameworkError` / `HookFailedError` before generic `Throwable` handling.
- Do not remove original-value pass-through branches; they are safety behavior, not noise.
- Do not change `intercept(stableHooker { ... })` back to direct `.intercept { ... }`.
- Do not remove `safeHook()` isolation or `xi.deoptimize(m)` calls.
- Do not move random generation into `:xposed` to simplify app/common code.

Keep a module override for `:xposed`, but make it explicit and narrow:

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
```

Do **not** permanently disable `MagicNumber` for all Xposed code unless the next strict rollout proves the noise is mostly API constants. Prefer named constants for fallback limits, byte sizes, retry counts, and identifier lengths.

## Baseline Cleanup Plan

Use the baseline as a queue for AI-agent cleanup, not as a trash bin.

1. Turn on `allRules = true`.
2. Run `.\gradlew.bat detekt --no-daemon`.
3. Generate/update baselines only after reviewing the new rule categories.
4. Fix high-signal categories first:
   - Compose false positives/config: `FunctionNaming`
   - correctness: `MultipleEmitters`, `ModifierMissing`, `ModifierNotUsedAtRoot`
   - bug risk: exception handling, ignored return values, unsafe calls
   - coroutine and threading rules
   - non-UI complexity in repositories/services/hookers
5. Regenerate the relevant module baseline after each cleanup batch.
6. Track baseline count in commits. The count should trend down.

Recommended commands:

```powershell
.\gradlew.bat detekt --no-daemon
.\gradlew.bat :app:detektBaseline --no-daemon
.\gradlew.bat :common:detektBaseline --no-daemon
.\gradlew.bat :xposed:detektBaseline --no-daemon
```

Only regenerate a baseline after reviewing the report. Blind baseline regeneration is hand-wavy bullshit because it hides whether strictness improved anything.

## Priority Fix Order

1. **Config-only false positives**
   - Add `FunctionNaming.ignoreAnnotated: ['Composable']`.
   - Add `CompositionLocalAllowlist.allowedCompositionLocals`.

2. **Compose correctness**
   - Fix `MultipleEmitters` in `SIMCardContent.kt`, `DeviceHardwareContent.kt`, and `LocationContent.kt`.
   - Add missing `modifier` parameters.
   - Fix parameter ordering and restartable-effect lambda rules.

3. **Bug-risk rules**
   - Exception swallowing.
   - Too-generic catches in `:app` and `:common`.
   - Ignored return values.
   - Unsafe nullable usage.

4. **Architecture/maintainability**
   - Long methods and complex methods outside pure UI layout code.
   - Too many functions/classes.
   - Magic numbers that affect behavior.

5. **Xposed strict cleanup**
   - Keep safety semantics first.
   - Add tests for any hook refactor.
   - Run `:xposed:testDebugUnitTest`, `R8HookerAbiTest`, and `:app:assembleCiRelease`.

## AI-Agent Rules For Detekt Fixes

Every agent fixing Detekt must follow this checklist:

```text
1. Read the rule docs -> verify: cite rule behavior in the change summary.
2. Inspect the affected code path -> verify: identify whether it is UI, app logic, common contract, or Xposed hook code.
3. Fix root cause, not only the warning -> verify: no broad suppression unless justified.
4. Preserve public/runtime behavior -> verify: run touched module tests.
5. For Xposed hook edits -> verify: run R8 hook ABI guard and release/ciRelease build.
```

Acceptable suppressions:

- Narrow `@Suppress` on a specific composable for UI-only long methods.
- Catch-parameter suppression for intentional broad fallback catches.
- File-level suppression only for documented file-shape rules like Compose helper/previews grouping.

Forbidden suppressions:

- Blanket file suppressions in ViewModels, repositories, services, `:common` contracts, or hookers.
- Suppressing a rule because the fix is time-consuming.
- Changing hook behavior without LSPosed/R8-aware tests.

## Final Recommendation

Adopt maximum strict mode:

- Set `allRules = true`.
- Keep `ignoreFailures = false`.
- Keep module baselines temporarily.
- Make Compose config strict but Compose-aware.
- Keep `:xposed` overrides narrow and safety-based.
- Use AI agents to reduce baseline count until it reaches zero or only documented false positives remain.

This will improve production code quality without breaking Xposed/libxposed code, as long as agents treat Xposed safety branches as runtime requirements rather than style noise.

## Sources

- Detekt Gradle configuration: https://detekt.dev/docs/gettingstarted/gradle/
- Detekt baseline docs: https://detekt.dev/docs/introduction/baseline/
- Detekt Compose configuration: https://detekt.dev/docs/introduction/compose/
- Compose rules Detekt docs: https://mrmans0n.github.io/compose-rules/detekt/
- Android Compose API guidelines: https://developer.android.com/develop/ui/compose/api-guidelines
- Android Compose modifiers: https://developer.android.com/develop/ui/compose/modifiers
