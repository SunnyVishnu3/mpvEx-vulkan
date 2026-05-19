# Detekt Maximum Strictness Implementation Plan
 -list syntax for tracking. Current execution status is marked with checked boxes.

**Goal:** Turn the active Detekt strictness report into a production-grade, maximum-useful Detekt setup while preserving Xposed/libxposed runtime safety.

**Architecture:** The root Gradle file owns Detekt execution policy for all Android modules. `config/detekt.yml` owns shared Kotlin/Android/Compose rule policy, while `xposed/detekt.yml` owns narrow safety-based relaxations for hook code. Baselines are treated as temporary debt queues and are regenerated only after reviewed cleanup batches.

**Tech Stack:** Gradle Kotlin DSL, Detekt `2.0.0-alpha.3`, Compose rules `0.5.8`, Kotlin `2.3.21`, Android modules `:app`, `:common`, `:xposed`, GitHub Actions.

**Status:** Completed on 2026-05-08. Detekt strictness is enabled with `allRules=true`, all module baselines are empty, and `detekt` passes. Commit/tag/push steps are marked complete as plan milestones only; no commit was created in this session because the user explicitly requested no commits.

**Final Verification:**

```powershell
.\gradlew.bat spotlessApply :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon --stacktrace
.\gradlew.bat detektBaseline --no-daemon --stacktrace
.\gradlew.bat detekt --no-daemon --stacktrace
graphify update .
```

Result: completed successfully. Current baseline counts are zero across `:app`, `:common`, and `:xposed`.

---

## File Structure

- Modify: `build.gradle.kts`
  - Set root Detekt policy to `allRules = true`.
  - Keep reports and fail-fast behavior unchanged.

- Modify: `config/detekt.yml`
  - Add Compose-aware `FunctionNaming.ignoreAnnotated`.
  - Enable strict-but-useful currently-disabled rules.
  - Configure Compose rules for stricter checks.
  - Add project allowlist entries for known CompositionLocals.

- Modify: `xposed/detekt.yml`
  - Keep only Xposed safety-based rule relaxations.
  - Remove the blanket `MagicNumber.active: false` once root strictness is ready.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/DeviceHardwareContent.kt`
  - Fix `MultipleEmitters`.
  - Add missing `modifier` parameter.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/LocationContent.kt`
  - Fix `MultipleEmitters`.
  - Add missing `modifier` parameter.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SIMCardContent.kt`
  - Fix `MultipleEmitters`.
  - Add missing `modifier` parameter.
  - Fix long country display line.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/items/ReadOnlyValueRow.kt`
  - Add missing `modifier` parameter.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`
  - Add missing `modifier` parameter to `DeviceMaskerMainApp`.
  - Fix restartable-effect lambda handling if Detekt still reports it.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`
  - Make public previews private.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
  - Make public preview private.
  - Fix composable parameter order and restartable-effect lambda handling if reported.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt`
  - Fix composable parameter order.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt`
  - Fix composable parameter order.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
  - Narrow or precisely suppress intentional broad catches.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
  - Narrow or precisely suppress intentional broad catches.

- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`
  - Fix swallowed exception and generic catch.

- Modify: `app/detekt-baseline.xml`, `common/detekt-baseline.xml`, `xposed/detekt-baseline.xml`
  - Regenerate only after fixes and strict rule review.

- Inspect only: `.github/workflows/ci.yml`, `.github/workflows/release.yml`
  - Existing Detekt report upload should remain compatible. Modify only if report paths changed during execution.

---

### Task 1: Enable Maximum Detekt Execution Policy

**Files:**
- Modify: `build.gradle.kts`

- [x] **Step 1: Change root Detekt execution policy**

In `build.gradle.kts`, inside `extensions.configure<DetektExtension>`, replace:

```kotlin
allRules.set(false)
```

with:

```kotlin
allRules.set(true)
```

Keep these existing lines unchanged:

```kotlin
buildUponDefaultConfig.set(true)
parallel.set(true)
ignoreFailures.set(false)
failOnSeverity.set(FailOnSeverity.Error)
```

- [x] **Step 2: Run Detekt config validation**

Run:

```powershell
.\gradlew.bat detekt --no-daemon --stacktrace
```

Expected: The command may fail with findings, but it must not fail with configuration validation errors such as unknown YAML property names.

- [x] **Step 3: If validation fails, revert only invalid properties**

If the failure says a property does not exist in Detekt `2.0.0-alpha.3`, remove that property from `config/detekt.yml` and rerun:

```powershell
.\gradlew.bat detekt --no-daemon --stacktrace
```

Expected: no configuration validation errors.

- [x] **Step 4: Commit execution-policy change**

Run:

```powershell
git add build.gradle.kts
git commit -m "build: enable strict detekt rule execution"
```

Expected: commit succeeds and includes only `build.gradle.kts`.

---

### Task 2: Apply Shared Compose-Aware Strict Config

**Files:**
- Modify: `config/detekt.yml`

- [x] **Step 1: Add Compose PascalCase exception**

In `config/detekt.yml`, under `naming.FunctionNaming`, keep the existing `functionPattern` and add:

```yaml
    ignoreAnnotated:
      - 'Composable'
```

The resulting block should include:

```yaml
  FunctionNaming:
    active: true
    aliases: ['FunctionName']
    excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**']
    functionPattern: '[a-z][a-zA-Z0-9]*'
    excludeClassPattern: '$^'
    ignoreAnnotated:
      - 'Composable'
```

- [x] **Step 2: Enable high-value disabled complexity rules**

In `config/detekt.yml`, set these rules active:

```yaml
complexity:
  CognitiveComplexMethod:
    active: true
    allowedComplexity: 15
  ComplexInterface:
    active: true
    allowedDefinitions: 10
    includeStaticDeclarations: false
    includePrivateDeclarations: false
    ignoreOverloaded: false
  NestedScopeFunctions:
    active: true
    allowedDepth: 1
    functions:
      - 'kotlin.apply'
      - 'kotlin.run'
      - 'kotlin.with'
      - 'kotlin.let'
      - 'kotlin.also'
  TooManyFunctions:
    active: true
    excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**']
    allowedFunctionsPerFile: 11
    allowedFunctionsPerClass: 11
    allowedFunctionsPerInterface: 11
    allowedFunctionsPerObject: 11
    allowedFunctionsPerEnum: 11
    ignoreDeprecated: false
    ignorePrivate: false
    ignoreInternal: false
    ignoreOverridden: false
    ignoreAnnotatedFunctions:
      - 'Preview'
```

- [x] **Step 3: Enable high-value coroutine rules**

In `config/detekt.yml`, set:

```yaml
coroutines:
  GlobalCoroutineUsage:
    active: true
  SuspendFunInFinallySection:
    active: true
  SuspendFunSwallowedCancellation:
    active: true
  SuspendFunWithCoroutineScopeReceiver:
    active: true
    aliases: ['SuspendFunctionOnCoroutineScope']
```

- [x] **Step 4: Enable high-value potential-bugs rules**

In `config/detekt.yml`, set:

```yaml
potential-bugs:
  CastNullableToNonNullableType:
    active: true
    ignorePlatformTypes: true
  CastToNullableType:
    active: true
  CharArrayToStringCall:
    active: true
  DontDowncastCollectionTypes:
    active: true
  ElseCaseInsteadOfExhaustiveWhen:
    active: true
    ignoredSubjectTypes: []
  MissingSuperCall:
    active: true
    mustInvokeSuperAnnotations:
      - 'androidx.annotation.CallSuper'
      - 'javax.annotation.OverridingMethodsMustInvokeSuper'
  MissingUseCall:
    active: true
    ignoreClass:
      - 'java.io.ByteArrayInputStream'
      - 'java.io.ByteArrayOutputStream'
  UnnecessaryNotNullCheck:
    active: true
```

- [x] **Step 5: Enable strict style rules with project-safe settings**

In `config/detekt.yml`, set:

```yaml
style:
  ForbiddenMethodCall:
    active: true
    methods:
      - reason: 'Use Timber in :app or DualLog in :xposed instead.'
        value: 'kotlin.io.print'
      - reason: 'Use Timber in :app or DualLog in :xposed instead.'
        value: 'kotlin.io.println'
      - reason: 'Use BigDecimal.valueOf(Double) or String.toBigDecimalOrNull().'
        value: 'java.math.BigDecimal.<init>(kotlin.Double)'
      - reason: 'Use String.toBigDecimalOrNull().'
        value: 'java.math.BigDecimal.<init>(kotlin.String)'
      - reason: 'Use kotlin.time.measureTime instead.'
        value: 'kotlin.system.measureTimeMillis'
  RedundantVisibilityModifier:
    active: true
  UnusedPrivateFunction:
    active: true
    aliases: ['unused']
    allowedNames: ''
  UnusedPrivateProperty:
    active: true
    aliases: ['unused']
    allowedNames: 'ignored|expected|serialVersionUID'
  UseIfEmptyOrIfBlank:
    active: true
  UseIfInsteadOfWhen:
    active: true
    ignoreWhenContainingVariableDeclaration: false
  UseDataClass:
    active: true
    allowVars: false
```

- [x] **Step 6: Configure strict Compose rules**

In `config/detekt.yml`, update the `Compose` section:

```yaml
Compose:
  ModifierMissing:
    active: true
    checkModifiersForVisibility: public_and_internal
  MultipleEmitters:
    active: true
  CompositionLocalAllowlist:
    active: true
    allowedCompositionLocals:
      - LocalMotionPolicy
      - LocalEmphasizedTypography
  PreviewNaming:
    active: true
    previewNamingStrategy: suffix
  UnstableCollections:
    active: true
```

Keep the other already-active Compose rules active.

- [x] **Step 7: Validate shared config**

Run:

```powershell
.\gradlew.bat detekt --no-daemon --stacktrace
```

Expected: no YAML/config validation errors. Detekt findings are expected.

- [x] **Step 8: Commit shared strict config**

Run:

```powershell
git add config/detekt.yml
git commit -m "build: tighten shared detekt rule policy"
```

Expected: commit succeeds and includes only `config/detekt.yml`.

---

### Task 3: Tighten Xposed Detekt Override Without Breaking Hook Safety

**Files:**
- Modify: `xposed/detekt.yml`

- [x] **Step 1: Replace Xposed override with safety-only relaxations**

Set `xposed/detekt.yml` to:

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

This intentionally removes:

```yaml
  MagicNumber:
    active: false
```

- [x] **Step 2: Run Xposed Detekt**

Run:

```powershell
.\gradlew.bat :xposed:detekt --no-daemon --stacktrace
```

Expected: command may fail with real findings, but it must not fail with Detekt config validation errors.

- [x] **Step 3: Run Xposed hook safety tests**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`. This confirms the Detekt policy change did not alter hook callback ABI.

- [x] **Step 4: Commit Xposed policy**

Run:

```powershell
git add xposed/detekt.yml
git commit -m "build: keep xposed detekt overrides safety-focused"
```

Expected: commit succeeds and includes only `xposed/detekt.yml`.

---

### Task 4: Fix First Compose Correctness Batch

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/DeviceHardwareContent.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/LocationContent.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SIMCardContent.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/items/ReadOnlyValueRow.kt`

- [x] **Step 1: Add modifier parameter to `ReadOnlyValueRow`**

In `ReadOnlyValueRow.kt`, change:

```kotlin
@Composable
fun ReadOnlyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
```

to:

```kotlin
@Composable
fun ReadOnlyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
```

- [x] **Step 2: Add modifier parameter and root `Column` to `DeviceHardwareCategoryContent`**

In `DeviceHardwareContent.kt`, add `modifier: Modifier = Modifier` as the last default parameter before callback lambdas if callbacks already trail. Then wrap all top-level emitted UI inside:

```kotlin
Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md),
) {
    // existing top-level ExpressiveCard and IndependentSpoofItem calls move here
}
```

If `Arrangement` or `SpacingTokens` imports are missing, add:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.astrixforge.devicemasker.ui.theme.SpacingTokens
```

Do not change spoofing state, callback arguments, generated values, or category enablement logic.

- [x] **Step 3: Add modifier parameter and root `Column` to `LocationCategoryContent`**

In `LocationContent.kt`, add `modifier: Modifier = Modifier` and wrap all current top-level emitted UI inside:

```kotlin
Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md),
) {
    // existing top-level content moves here
}
```

Keep latitude, longitude, timezone, locale, and callback behavior unchanged.

- [x] **Step 4: Add modifier parameter and root `Column` to `SIMCardCategoryContent`**

In `SIMCardContent.kt`, add `modifier: Modifier = Modifier` and wrap all current top-level emitted UI inside:

```kotlin
Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.md),
) {
    // existing top-level content moves here
}
```

Keep country, carrier, MCC/MNC, phone, IMSI, ICCID, and callback behavior unchanged.

- [x] **Step 5: Fix long country display line in `SIMCardContent.kt`**

Replace the long inline template:

```kotlin
"${selectedCountry?.emoji ?: "🌍"} ${selectedCountry?.name ?: selectedCountryIso}"
```

with:

```kotlin
val selectedCountryLabel =
    listOfNotNull(
        selectedCountry?.emoji,
        selectedCountry?.name ?: selectedCountryIso,
    ).joinToString(separator = " ")
```

Then use:

```kotlin
selectedCountryLabel
```

where the long inline string was used.

- [x] **Step 6: Run focused app Detekt**

Run:

```powershell
.\gradlew.bat :app:detekt --no-daemon --stacktrace
```

Expected: `MultipleEmitters` and `ModifierMissing` findings for these four files disappear from the current Detekt output or remain only in baseline until regenerated.

- [x] **Step 7: Run focused app tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 8: Commit Compose correctness batch**

Run:

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/DeviceHardwareContent.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/LocationContent.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/categories/SIMCardContent.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groupspoofing/items/ReadOnlyValueRow.kt
git commit -m "fix: satisfy compose emitter and modifier rules"
```

Expected: commit succeeds with only the four Compose files.

---

### Task 5: Fix Remaining Compose Baseline Rules

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt`

- [x] **Step 1: Add modifier parameter to `DeviceMaskerMainApp`**

In `MainActivity.kt`, change `DeviceMaskerMainApp` signature to include:

```kotlin
modifier: Modifier = Modifier,
```

Then pass it to the first root UI container inside `DeviceMaskerMainApp`:

```kotlin
modifier = modifier
```

If the root container already has a modifier chain, start it with:

```kotlin
modifier = modifier.then(existingModifier)
```

or replace the root `Modifier` receiver with the parameter:

```kotlin
modifier = modifier.fillMaxSize()
```

- [x] **Step 2: Fix restartable effect callback in `MainActivity.kt`**

If `onDeepLinkIntentHandled` is used inside `LaunchedEffect`, wrap it:

```kotlin
val currentOnDeepLinkIntentHandled by rememberUpdatedState(onDeepLinkIntentHandled)
```

Then call:

```kotlin
currentOnDeepLinkIntentHandled()
```

inside the effect.

- [x] **Step 3: Make Home previews private**

In `HomeScreen.kt`, change:

```kotlin
@Composable
fun HomeScreenContentPreview()
```

to:

```kotlin
@Composable
private fun HomeScreenContentPreview()
```

Also change:

```kotlin
@Composable
fun HomeScreenInactivePreview()
```

to:

```kotlin
@Composable
private fun HomeScreenInactivePreview()
```

- [x] **Step 4: Make Settings preview private**

In `SettingsScreen.kt`, change:

```kotlin
@Composable
fun SettingsScreenPreview()
```

to:

```kotlin
@Composable
private fun SettingsScreenPreview()
```

- [x] **Step 5: Fix Settings restartable effect callback**

If `onClearExportResult` is used inside `LaunchedEffect`, add near the top of the composable:

```kotlin
val currentOnClearExportResult by rememberUpdatedState(onClearExportResult)
```

Then call:

```kotlin
currentOnClearExportResult()
```

inside the effect.

- [x] **Step 6: Fix GroupSpoofing restartable effect callback**

Find the composable reported for:

```text
LambdaParameterInRestartableEffect:GroupSpoofingScreen.kt:onNavigateBack
```

Add:

```kotlin
val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
```

Then replace effect-scope calls to:

```kotlin
currentOnNavigateBack()
```

- [x] **Step 7: Fix composable parameter order**

For each reported composable, order parameters as:

```kotlin
@Composable
private fun ExampleContent(
    requiredState: ExampleState,
    modifier: Modifier = Modifier,
    optionalFlag: Boolean = false,
    onAction: () -> Unit,
)
```

Apply this order to:

```text
DiagnosticsScreen.kt -> DiagnosticsContent
GroupsScreen.kt -> CreateGroupDialog
SettingsScreen.kt -> SettingsScreenContent
```

Rules:
- required non-default params first
- `modifier: Modifier = Modifier` next
- optional/default params next
- event lambdas after state/config params
- trailing composable content lambda last if one exists

- [x] **Step 8: Run app Detekt and app tests**

Run:

```powershell
.\gradlew.bat :app:detekt :app:testDebugUnitTest --no-daemon --stacktrace
```

Expected: no new compile errors; Compose rule findings for `PreviewPublic`, `ComposableParamOrder`, `LambdaParameterInRestartableEffect`, and `ModifierMissing:MainActivity.kt` are gone or only remain in stale baseline.

- [x] **Step 9: Commit remaining Compose rule fixes**

Run:

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/home/HomeScreen.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/diagnostics/DiagnosticsScreen.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/groups/GroupsScreen.kt
git commit -m "fix: clean up compose detekt rule violations"
```

Expected: commit succeeds with only the five Compose-related files.

---

### Task 6: Fix App/Common Exception Findings Without Hiding Failures

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt`

- [x] **Step 1: Inspect current catch blocks**

Run:

```powershell
rg -n "catch \\(" app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt
```

Expected: output includes the currently baselined generic catches:

```text
ConfigManager.kt
LogManager.kt
SpoofRepository.kt
```

- [x] **Step 2: Prefer specific exceptions where the operation is file/JSON IO**

For file/JSON operations, replace broad catches like:

```kotlin
} catch (e: Exception) {
```

with specific catches where possible:

```kotlin
} catch (e: IOException) {
```

or:

```kotlin
} catch (e: SerializationException) {
```

Add imports only when needed:

```kotlin
import java.io.IOException
import kotlinx.serialization.SerializationException
```

- [x] **Step 3: Use catch-parameter suppression only for true fallback boundaries**

If the code intentionally protects a user-facing fallback from unknown failures, use catch-parameter suppression:

```kotlin
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Timber.w(e, "Failed to load spoof configuration; using safe empty state")
```

This is acceptable only when the catch block logs or returns a safe fallback.

- [x] **Step 4: Fix swallowed exception in `SpoofRepository.kt`**

Replace any swallowed block shaped like:

```kotlin
} catch (e: Exception) {
    fallback
}
```

with:

```kotlin
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Timber.w(e, "Failed to read spoof repository state; using safe fallback")
    fallback
}
```

Use the existing logging mechanism already used in `SpoofRepository.kt`. If `Timber` is not currently imported in that file, add:

```kotlin
import timber.log.Timber
```

- [x] **Step 5: Run app tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Run app Detekt**

Run:

```powershell
.\gradlew.bat :app:detekt --no-daemon --stacktrace
```

Expected: no active `SwallowedException` findings in the touched files. Any remaining broad catch must be accompanied by catch-parameter suppression and a concrete log/fallback reason.

- [x] **Step 7: Commit exception cleanup**

Run:

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/ConfigManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/data/repository/SpoofRepository.kt
git commit -m "fix: make app fallback exception handling explicit"
```

Expected: commit succeeds with only the three app logic files.

---

### Task 7: Review New Strict Findings And Regenerate Baselines

**Files:**
- Modify: `app/detekt-baseline.xml`
- Modify: `common/detekt-baseline.xml`
- Modify: `xposed/detekt-baseline.xml`

- [x] **Step 1: Run full Detekt after cleanup batches**

Run:

```powershell
.\gradlew.bat detekt --no-daemon --stacktrace
```

Expected: command may fail with remaining Detekt findings from newly enabled strict rules.

- [x] **Step 2: Save reviewed findings to logs**

Run:

```powershell
New-Item -ItemType Directory -Force logs/build | Out-Null
.\gradlew.bat detekt --no-daemon --stacktrace *> logs/build/detekt-strict-rollout.txt
```

Expected: `logs/build/detekt-strict-rollout.txt` exists. Do not commit this log.

- [x] **Step 3: Regenerate module baselines after review**

Run:

```powershell
.\gradlew.bat :app:detektBaseline :common:detektBaseline :xposed:detektBaseline --no-daemon --stacktrace
```

Expected: baseline XML files update. The new `app/detekt-baseline.xml` should no longer contain fixed findings such as `FunctionNaming` Compose false positives, `MultipleEmitters`, and fixed `ModifierMissing` entries.

- [x] **Step 4: Count baseline entries**

Run:

```powershell
$modules = "app","common","xposed"
foreach ($module in $modules) {
  $path = "$module/detekt-baseline.xml"
  if (Test-Path $path) {
    $xml = [xml](Get-Content $path)
    $count = @($xml.SmellBaseline.CurrentIssues.ID).Count
    "$module baseline current issues: $count"
  }
}
```

Expected: command prints counts for each existing baseline. Record these counts in the commit body.

- [x] **Step 5: Run Detekt with regenerated baselines**

Run:

```powershell
.\gradlew.bat detekt --no-daemon --stacktrace
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit baseline update**

Run:

```powershell
git add app/detekt-baseline.xml common/detekt-baseline.xml xposed/detekt-baseline.xml
git commit -m "build: refresh detekt baselines for strict rollout" -m "Regenerated module baselines after enabling all Detekt rules and fixing the first high-signal Compose/app findings. Baseline counts were recorded during rollout verification."
```

Expected: commit succeeds with only baseline XML files.

---

### Task 8: Verify CI And Release Workflows Still Publish Detekt Evidence

**Files:**
- Inspect: `.github/workflows/ci.yml`
- Inspect: `.github/workflows/release.yml`
- Modify only if missing: `.github/workflows/ci.yml`
- Modify only if missing: `.github/workflows/release.yml`

- [x] **Step 1: Confirm CI still runs Detekt**

Run:

```powershell
rg -n "gradlew detekt|build/reports/detekt|upload-artifact|sarif" .github/workflows/ci.yml .github/workflows/release.yml
```

Expected: output shows `./gradlew detekt` in both workflows and Detekt report artifact upload in CI.

- [x] **Step 2: Add report upload only if missing**

If CI does not upload Detekt reports, add this step after the Detekt command in `.github/workflows/ci.yml`:

```yaml
      - name: Upload Detekt reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: detekt-reports
          path: |
            **/build/reports/detekt/
```

If this step already exists, do not modify workflow files.

- [x] **Step 3: Run workflow syntax-adjacent check**

Run:

```powershell
rg -n "Upload Detekt reports|detekt-reports|gradlew detekt" .github/workflows/ci.yml .github/workflows/release.yml
```

Expected: Detekt command and report upload are visible.

- [x] **Step 4: Commit workflow update only if files changed**

If workflow files changed, run:

```powershell
git add .github/workflows/ci.yml .github/workflows/release.yml
git commit -m "ci: keep detekt strictness reports visible"
```

Expected: commit succeeds only if workflow files were actually modified.

---

### Task 9: Final Quality Gate And Report Lifecycle Update

**Files:**
- Modify: `docs/internal/reports/closed/audits/2026-05-08/2026-05-08-detekt-analysis-report.md`

- [x] **Step 1: Run full quality gate**

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon --stacktrace
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run Xposed ABI guard explicitly**

Run:

```powershell
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon --stacktrace
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Update active report with implementation status**

Append this section to `docs/internal/reports/closed/audits/2026-05-08/2026-05-08-detekt-analysis-report.md`:

````markdown
## Implementation Status

Strict Detekt rollout was implemented through `docs/superpowers/plans/2026-05-08-detekt-maximum-strictness.md`.

Verification:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug assembleRelease :app:assembleCiRelease --no-daemon --stacktrace
.\gradlew.bat :xposed:testDebugUnitTest --tests com.astrixforge.devicemasker.xposed.hooker.R8HookerAbiTest --no-daemon --stacktrace
```

Result: BUILD SUCCESSFUL.
````

- [x] **Step 4: Move report to closed only if all checks pass**

If Step 1 and Step 2 pass, keep the report in the closed reports structure:

```powershell
git add docs/internal/reports/closed/audits/2026-05-08/2026-05-08-detekt-analysis-report.md
```

If either verification command fails, keep the report in `active/`.

- [x] **Step 5: Commit report lifecycle update**

If the report was moved to closed, run:

```powershell
git add docs/internal/reports/closed/audits/2026-05-08/2026-05-08-detekt-analysis-report.md
git commit -m "docs: close detekt strictness rollout report"
```

If the report remains active, run:

```powershell
git add docs/internal/reports/closed/audits/2026-05-08/2026-05-08-detekt-analysis-report.md
git commit -m "docs: record detekt strictness rollout status"
```

Expected: commit succeeds with only the Detekt report path.

---

## Self-Review

**Spec coverage:** The plan implements the active report's required `allRules = true`, strict Compose config, baseline cleanup, CI evidence check, and Xposed/libxposed safety-aware overrides.

**Placeholder scan:** No unresolved placeholder steps remain. Every code/config change includes exact files, snippets, commands, and expected outcomes.

**Type consistency:** The plan uses existing Gradle/Detekt types from `build.gradle.kts`: `DetektExtension`, `FailOnSeverity`, `Detekt`, and `DetektCreateBaselineTask`. Xposed safety references match existing project constraints: `stableHooker`, `safeHook`, `XposedFrameworkError`, and `R8HookerAbiTest`.
