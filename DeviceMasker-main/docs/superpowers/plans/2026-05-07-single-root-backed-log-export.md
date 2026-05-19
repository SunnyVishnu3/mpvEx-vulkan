# Single Root-Backed Log Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three export modes with one clean `Export Logs` UX backed by the existing maximum root/logcat support bundle pipeline.

**Architecture:** The UI stops exposing export modes. `SettingsViewModel` always requests the single root-backed support bundle. `LogManager` always packages app JSONL logs, diagnostic snapshots, latest background root capture, and a fresh export-time root/logcat snapshot when root is granted.

**Tech Stack:** Kotlin 2.3.0, Android app module, Jetpack Compose, Material 3, libsu root shell, FileProvider, JVM/Robolectric tests.

---

## Current Findings

1. Current UI has three mode choices: Basic, Full Debug, Root Maximum.
2. Current state has duplicate mode data: `BundleExportMode` in UI and `SupportBundleMode` in service.
3. Root Maximum is already the useful superset path.
4. Export-time fresh root collection is useful and must stay.
5. Boot/startup root capture already exists through `RootLogCaptureService`.
6. Bug found: `RootLogCollector` hardcodes `com.mantle.verify` in filtered logcat. That is bogus shit for a general support export.
7. UX issue found: Root Maximum export is currently disabled when root is unavailable, but the service can still build a useful bundle with app logs, snapshots, and a `ROOT_UNAVAILABLE` manifest.
8. Share path should keep using `FileProvider` with temporary read grants. Android docs also recommend putting the shared URI in `ClipData` when granting URI permission.

## Google Developer Docs Checked

- Foreground service background start exemptions allow foreground services after `BOOT_COMPLETED`, with Android 14/15 restrictions for some foreground service types. The current app uses `specialUse`, not restricted `dataSync`, `camera`, `microphone`, `mediaPlayback`, or `mediaProjection`.
- `specialUse` foreground service type requires `FOREGROUND_SERVICE_SPECIAL_USE`, `android:foregroundServiceType="specialUse"`, and a manifest property describing the use case.
- File sharing should use `FileProvider`, `content://` URIs, and `FLAG_GRANT_READ_URI_PERMISSION`; adding `ClipData` is the robust grant path for shared content URIs.

Relevant official docs:

- `https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start`
- `https://developer.android.com/about/versions/14/changes/fgs-types-required`
- `https://developer.android.com/about/versions/15/changes/foreground-service-types`
- `https://developer.android.com/training/secure-file-sharing/share-file`
- `https://developer.android.com/reference/androidx/core/content/FileProvider`

## File Structure

Modify:

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsState.kt`
  - Remove export mode state and mode enum.
  - Remove no-longer-used `ExportResult.NoLogs` if no production caller maps to it.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModel.kt`
  - Remove `SavedStateHandle` export-mode persistence.
  - Make save/share exports call the single root-backed export path.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
  - Remove three-mode bottom sheet.
  - Render one `Export Logs` sheet with two clean icon+text actions: Save and Share.
  - Keep export available even when root is not granted.

- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`
  - Stop passing export mode into `SettingsScreen`.
  - Stop passing mode through export callbacks.
  - Add `ClipData` to the share intent for robust URI permission.

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/ILogManager.kt`
  - Remove `SupportBundleMode` parameters.

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
  - Remove export mode branching.
  - Always build the maximum support bundle.
  - Always prepare root artifacts.
  - Keep fresh `export_snapshot` collection when root is granted.
  - Keep `ROOT_UNAVAILABLE` manifest when root is not granted.
  - Remove `hasAnyLogs()` and `ShareableLogResult.NoLogs`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
  - Remove `SupportBundleMode`.
  - Always include snapshots and root artifacts when present.
  - Write manifest mode as a stable string such as `"MAXIMUM"`.

- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`
  - Remove hardcoded `com.mantle.verify`.
  - Use generic LSPosed/hook evidence terms plus optional validated target package.

- `app/src/main/res/values/strings.xml`
  - Remove Basic/Full/Root Maximum labels from active UI strings.
  - Add or reuse clean Save/Share export strings.
  - Reword root warning strings so they mention logs/diagnostics, not `Root Maximum`.

- `docs/public/ARCHITECTURE.md`
  - Document single root-backed support export.

- `memory-bank/systemPatterns.md`
  - Replace three-mode diagnostics text with single export path.

- `memory-bank/progress.md`
  - Update current status after implementation.

Tests:

- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt`
- `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeLogManager.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`

Do not modify:

- `:xposed` hook behavior.
- RemotePreferences config flow.
- AIDL/Binder, because it has already been removed and must not come back.

---

### Task 1: Lock ViewModel Export Behavior With Tests

**Files:**
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeLogManager.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Make the fake record export calls**

Replace `FakeLogManager` with:

```kotlin
package com.astrixforge.devicemasker.testing

import android.content.Context
import android.net.Uri
import com.astrixforge.devicemasker.service.ILogManager
import com.astrixforge.devicemasker.service.LogExportResult
import com.astrixforge.devicemasker.service.ShareableLogResult

/** Fake [ILogManager] for Settings testing. */
class FakeLogManager : ILogManager {

    var exportResult: LogExportResult = LogExportResult.Success("/path/to/file.zip", 42)
    var shareableResult: ShareableLogResult = ShareableLogResult.Success(Uri.EMPTY, "file.zip", 42)
    var fileName: String = "devicemasker_support_20240101_000000.zip"
    var exportCallCount: Int = 0
        private set
    var shareCallCount: Int = 0
        private set
    var lastExportUri: Uri? = null
        private set

    override suspend fun exportLogsToUri(context: Context, uri: Uri): LogExportResult {
        exportCallCount += 1
        lastExportUri = uri
        return exportResult
    }

    override suspend fun createShareableLogFile(context: Context): ShareableLogResult {
        shareCallCount += 1
        return shareableResult
    }

    override fun generateLogFileName(): String = fileName
}
```

- [ ] **Step 2: Update SettingsViewModel tests for no mode state**

Add this test to `SettingsViewModelTest`:

```kotlin
@Test
fun `export uses single log manager path without mode state`() = runTest {
    val logManager = FakeLogManager()
    val viewModel = createViewModel(logManager = logManager)
    val uri = Uri.parse("content://test/export.zip")

    viewModel.exportLogsToUri(uri)
    advanceUntilIdle()

    assertEquals(1, logManager.exportCallCount)
    assertEquals(uri, logManager.lastExportUri)
    assertFalse(viewModel.state.value.isExportingLogs)
    assertTrue(viewModel.state.value.exportResult is ExportResult.Success)
}
```

Add this test:

```kotlin
@Test
fun `share uses single log manager path without mode state`() = runTest {
    val logManager = FakeLogManager()
    val viewModel = createViewModel(logManager = logManager)
    var result: ShareableLogResult? = null

    viewModel.createShareableLogs { shareResult -> result = shareResult }
    advanceUntilIdle()

    assertEquals(1, logManager.shareCallCount)
    assertTrue(result is ShareableLogResult.Success)
    assertFalse(viewModel.state.value.isExportingLogs)
}
```

Add import:

```kotlin
import com.astrixforge.devicemasker.service.ShareableLogResult
```

- [ ] **Step 3: Run the focused test and verify it fails before implementation**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModelTest --no-daemon
```

Expected: fail because `ILogManager` still requires `SupportBundleMode` parameters and `SettingsViewModel` still has mode plumbing.

- [ ] **Step 4: Commit after Task 1 passes in the later implementation task**

Commit command after implementation:

```powershell
git add app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeLogManager.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "test: lock single settings log export path"
```

---

### Task 2: Collapse Service Export API To One Path

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/ILogManager.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`

- [ ] **Step 1: Update `ILogManager`**

Replace `ILogManager.kt` with:

```kotlin
package com.astrixforge.devicemasker.service

import android.content.Context
import android.net.Uri

interface ILogManager {
    suspend fun exportLogsToUri(context: Context, uri: Uri): LogExportResult

    suspend fun createShareableLogFile(context: Context): ShareableLogResult

    fun generateLogFileName(): String
}
```

- [ ] **Step 2: Update `SupportBundleBuilder`**

Replace `SupportBundleBuilder.kt` with:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticRedactor
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SupportBundleBuilder(
    private val appEvents: List<String>,
    private val xposedEvents: List<String>,
    private val snapshots: Map<String, String> = emptyMap(),
    private val rootArtifactsDir: File? = null,
) {
    fun build(outputDir: File, redactionMode: RedactionMode): File {
        outputDir.mkdirs()
        val file = File(outputDir, "devicemasker_support_${timestamp()}.zip")
        val redactor = DiagnosticRedactor(redactionMode)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.writeText(
                "manifest.json",
                """{"mode":"MAXIMUM","redaction":"$redactionMode","createdAt":"${timestamp()}"}""",
            )
            zip.writeText(
                "README_REPRO.md",
                "Reproduce the issue, then attach this local support bundle.\n",
            )
            zip.writeText("app/app_events.jsonl", appEvents.joinToString("\n").redact(redactor))
            zip.writeText(
                "xposed/xposed_events.jsonl",
                xposedEvents.joinToString("\n").redact(redactor),
            )

            snapshots.forEach { (name, content) ->
                val prefix =
                    when {
                        name.startsWith("config") || name.startsWith("remote") -> "config"
                        name.startsWith("scope") -> "scope"
                        else -> "diagnostics"
                    }
                zip.writeText("$prefix/$name", content.redact(redactor))
            }

            rootArtifactsDir
                ?.walkTopDown()
                ?.filter { it.isFile }
                ?.forEach { artifact ->
                    val relative = artifact.relativeTo(rootArtifactsDir).invariantSeparatorsPath
                    zip.writeRedactedFile("root/$relative", artifact, redactor)
                }
        }
        return file
    }

    private fun ZipOutputStream.writeText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeRedactedFile(
        path: String,
        file: File,
        redactor: DiagnosticRedactor,
    ) {
        putNextEntry(ZipEntry(path))
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                write(line.redact(redactor).toByteArray(Charsets.UTF_8))
                write('\n'.code)
            }
        }
        closeEntry()
    }

    private fun String.redact(redactor: DiagnosticRedactor): String = redactor.redactMessage(this)

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
```

- [ ] **Step 3: Update `SupportBundleBuilderTest`**

Change the build call to:

```kotlin
.build(outputDir, RedactionMode.REDACTED)
```

Add this assertion inside the `ZipFile(bundle).use { zip -> ... }` block:

```kotlin
assertTrue(
    zip.getInputStream(zip.getEntry("manifest.json"))
        .bufferedReader()
        .readText()
        .contains(""""mode":"MAXIMUM"""")
)
```

- [ ] **Step 4: Update `LogManager` signatures and maximum bundle build**

In `LogManager.kt`, remove:

```kotlin
import com.astrixforge.devicemasker.service.diagnostics.SupportBundleMode
```

Change function signatures to:

```kotlin
override suspend fun exportLogsToUri(
    context: Context,
    uri: Uri,
): LogExportResult =
```

```kotlin
override suspend fun createShareableLogFile(context: Context): ShareableLogResult =
```

Replace `createShareableLogFile` body with:

```kotlin
withContext(Dispatchers.IO) {
    try {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }

        val fileName = generateLogFileName()
        val bundle = buildSupportBundle(context, logsDir)
        val logFile = File(logsDir, fileName)
        if (bundle.name != logFile.name) {
            bundle.copyTo(logFile, overwrite = true)
        }

        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile,
            )

        ShareableLogResult.Success(uri, fileName, logFile.length().toInt())
    } catch (e: Exception) {
        ShareableLogResult.Error("Failed to create shareable log: ${e.message}")
    }
}
```

Replace `buildSupportBundle` with:

```kotlin
private suspend fun buildSupportBundle(
    context: Context,
    outputDir: File = File(context.cacheDir, "logs"),
): File {
    val appEvents = AppLogStore.from(context).readDiagnosticEvents()
    val rootArtifactsDir =
        RootCaptureStore.prepareExportArtifacts(context, outputDir).also { rootDir ->
            if (RootAccessManager.hasGrantedRoot()) {
                RootLogCollector().collect(File(rootDir, "export_snapshot"), null)
            } else {
                RootCaptureStore.writeManifest(
                    dir = rootDir,
                    trigger = "export",
                    status = "ROOT_UNAVAILABLE",
                    message =
                        "Root access is not currently granted; export used captured root artifacts only.",
                )
            }
        }
    val rootAvailable = RootAccessManager.hasGrantedRoot()
    val snapshots =
        DiagnosticSnapshotBuilder(
                metadata =
                    DiagnosticSnapshotMetadata(
                        appVersion =
                            context.packageManager
                                .getPackageInfo(context.packageName, 0)
                                .versionName ?: "unknown",
                        buildType = com.astrixforge.devicemasker.BuildConfig.BUILD_TYPE,
                        androidSdk = Build.VERSION.SDK_INT,
                        androidRelease = Build.VERSION.RELEASE ?: "unknown",
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        rootAvailable = rootAvailable,
                        xposedFrameworkConnected = DeviceMaskerApp.isXposedModuleActive,
                        moduleEnabled = DeviceMaskerApp.isXposedModuleActive,
                        targetPackage = null,
                        scopePackages = listOf("android", "system"),
                        droppedLogCount = 0,
                    ),
                configJson = "{}",
                remotePrefs = emptyMap(),
                hookHealthJson = "{}",
            )
            .build(RedactionMode.REDACTED)

    return SupportBundleBuilder(
            appEvents =
                appEvents.map { event ->
                    DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event)
                },
            xposedEvents = emptyList(),
            snapshots = snapshots,
            rootArtifactsDir = rootArtifactsDir,
        )
        .build(outputDir, RedactionMode.REDACTED)
}
```

Delete `hasAnyLogs()`.

Remove this sealed-object member:

```kotlin
data object NoLogs : ShareableLogResult()
```

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/ILogManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt
git commit -m "refactor: collapse support export to single maximum bundle"
```

---

### Task 3: Remove Settings Export Mode State

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Replace `SettingsState.kt`**

Use:

```kotlin
package com.astrixforge.devicemasker.ui.screens.settings

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.ui.theme.ThemeMode

/** UI state for the Settings screen. */
@Immutable
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledMode: Boolean = true,
    val dynamicColors: Boolean = true,
    val isExportingLogs: Boolean = false,
    val redactionChoice: RedactionChoice = RedactionChoice.REDACTED,
    val exportResult: ExportResult? = null,
)

enum class RedactionChoice {
    REDACTED,
    UNREDACTED_REQUIRES_CONFIRMATION,
}

/** Result of a log export operation. */
@Immutable
sealed class ExportResult {
    data class Success(val filePath: String, val lineCount: Int) : ExportResult()

    data class Error(val message: String) : ExportResult()
}
```

- [ ] **Step 2: Update `SettingsViewModel.kt`**

Remove imports:

```kotlin
import androidx.lifecycle.SavedStateHandle
import com.astrixforge.devicemasker.service.diagnostics.SupportBundleMode
```

Change constructor to:

```kotlin
class SettingsViewModel(
    application: Application,
    private val settingsStore: ISettingsDataStore,
    private val logManager: ILogManager = LogManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {
```

Change `_state` initialization to:

```kotlin
private val _state = MutableStateFlow(SettingsState())
```

Delete `setExportMode()`.

Replace export functions with:

```kotlin
/** Exports app-owned structured logs and available diagnostics logs to a custom URI. */
fun exportLogsToUri(uri: Uri) {
    viewModelScope.launch {
        _state.update { it.copy(isExportingLogs = true, exportResult = null) }

        val result =
            withContext(ioDispatcher) {
                logManager.exportLogsToUri(getApplication(), uri).toExportResult()
            }

        _state.update { it.copy(isExportingLogs = false, exportResult = result) }
    }
}

/**
 * Creates a shareable log file and returns the result. The caller should use the URI to launch
 * a share intent.
 */
fun createShareableLogs(onResult: (ShareableLogResult) -> Unit) {
    viewModelScope.launch {
        _state.update { it.copy(isExportingLogs = true, exportResult = null) }

        val result =
            withContext(ioDispatcher) {
                logManager.createShareableLogFile(getApplication())
            }

        _state.update { it.copy(isExportingLogs = false) }
        onResult(result)
    }
}
```

Remove `BundleExportMode.toSupportMode()`.

Remove companion object:

```kotlin
private companion object {
    const val KEY_EXPORT_MODE = "exportMode"
}
```

Remove `NoLogs` branch from `toExportResult()` because `LogExportResult` has only Success/Error.

- [ ] **Step 3: Run focused ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModelTest --no-daemon
```

Expected: PASS after Task 2 and Task 3 are both implemented.

- [ ] **Step 4: Static check no UI mode state remains**

Run:

```powershell
rg "BundleExportMode|setExportMode|KEY_EXPORT_MODE|exportMode" app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings
```

Expected: no matches.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsState.kt app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModel.kt app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsViewModelTest.kt app/src/test/kotlin/com/astrixforge/devicemasker/testing/FakeLogManager.kt
git commit -m "refactor: remove settings export mode state"
```

---

### Task 4: Replace Three-Mode Export Sheet With Clean Save/Share UI

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Update strings**

In `strings.xml`, remove active references to:

```xml
<string name="settings_export_basic">Basic Export</string>
<string name="settings_export_full_debug">Full Debug Export</string>
<string name="settings_export_root_maximum">Root Maximum Export</string>
<string name="settings_export_unredacted_warning">Unredacted logs may include package names and sensitive debugging data.</string>
<string name="root_access_required_for_root_export">Root access is required. Grant root on app startup before using Root Maximum export.</string>
```

Keep or add:

```xml
<string name="settings_export_sheet_title">Export Logs</string>
<string name="settings_export_save">Save</string>
<string name="settings_export_save_desc">Save logs to device</string>
<string name="settings_export_share">Share</string>
<string name="settings_export_share_desc">Share logs</string>
```

Reword root messages:

```xml
<string name="root_access_denied_message">Root access was not granted. Configuration can still be edited, but privileged diagnostics and root log capture will not work.</string>
<string name="root_access_unavailable_message">Root access is unavailable on this device. Configuration can still be edited, but privileged diagnostics and root log capture will not work.</string>
<string name="root_capture_notification_title">Capturing diagnostic logs</string>
```

- [ ] **Step 2: Update `SettingsScreen` function signature**

Remove parameters:

```kotlin
exportMode: BundleExportMode = BundleExportMode.BASIC,
onExportModeChange: (BundleExportMode) -> Unit = {},
onExportLogsToUri: (Uri, BundleExportMode) -> Unit = { _, _ -> },
onShareLogs: (BundleExportMode) -> Unit = {},
```

Add:

```kotlin
onExportLogsToUri: (Uri) -> Unit = {},
onShareLogs: () -> Unit = {},
```

- [ ] **Step 3: Remove pending mode state**

Delete:

```kotlin
var pendingExportMode by remember { mutableStateOf(exportMode) }
LaunchedEffect(exportMode) { pendingExportMode = exportMode }
```

Change launcher callback to:

```kotlin
) { uri ->
    uri?.let { onExportLogsToUri(it) }
}
```

- [ ] **Step 4: Replace the bottom sheet call**

Replace the `ExportActionsBottomSheet(...)` call with:

```kotlin
ExportActionsBottomSheet(
    title = exportSheetTitle,
    saveLabel = saveTitle,
    saveDescription = saveDesc,
    shareLabel = shareTitle,
    shareDescription = shareDesc,
    onSave = { exportLogsLauncher.launch(generateLogFileName()) },
    onShare = onShareLogs,
    onDismiss = { showExportSheet = false },
)
```

- [ ] **Step 5: Replace bottom-sheet composables**

Replace `ExportModeAction`, `ExportActionsBottomSheet`, and `ExportModeSplitButton` with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportActionsBottomSheet(
    title: String,
    saveLabel: String,
    saveDescription: String,
    shareLabel: String,
    shareDescription: String,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        onSave()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Save, contentDescription = saveDescription)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(saveLabel)
                }

                FilledTonalButton(
                    onClick = {
                        onShare()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = shareDescription)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(shareLabel)
                }
            }
        }
    }
}
```

Add imports if missing:

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
```

Remove imports that become unused:

```kotlin
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
```

- [ ] **Step 6: Update `SettingsScreenContent`**

Remove parameter:

```kotlin
exportMode: BundleExportMode,
```

Change export row description to:

```kotlin
description =
    if (isExportingLogs) {
        stringResource(id = R.string.settings_exporting)
    } else {
        stringResource(id = R.string.settings_export_logs_description)
    },
```

- [ ] **Step 7: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Static UI cleanup checks**

Run:

```powershell
rg "Basic Export|Full Debug Export|Root Maximum Export|settings_export_basic|settings_export_full_debug|settings_export_root_maximum|BundleExportMode|SplitButtonLayout" app/src/main
```

Expected: no production UI matches.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "refactor: simplify log export sheet"
```

---

### Task 5: Update MainActivity Export Wiring And Share URI Grant

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`

- [ ] **Step 1: Remove mode args from `SettingsScreen` call**

Remove:

```kotlin
exportMode = settingsState.exportMode,
onExportModeChange = settingsViewModel::setExportMode,
```

Change:

```kotlin
onExportLogsToUri = { uri, mode ->
    Timber.d("Exporting logs to: $uri")
    settingsViewModel.exportLogsToUri(uri, mode)
},
onShareLogs = { mode ->
    Timber.d("Sharing logs...")
    settingsViewModel.createShareableLogs(mode) { result ->
```

To:

```kotlin
onExportLogsToUri = { uri ->
    Timber.d("Exporting logs to: $uri")
    settingsViewModel.exportLogsToUri(uri)
},
onShareLogs = {
    Timber.d("Sharing logs...")
    settingsViewModel.createShareableLogs { result ->
```

- [ ] **Step 2: Remove `ShareableLogResult.NoLogs` branch**

Delete:

```kotlin
is ShareableLogResult.NoLogs -> {
    Timber.d("No logs to share")
}
```

- [ ] **Step 3: Add `ClipData` for robust share grants**

Add import:

```kotlin
import android.content.ClipData
```

Inside the `ShareableLogResult.Success` branch, replace the `Intent(Intent.ACTION_SEND).apply { ... }` block with:

```kotlin
val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, result.uri)
        putExtra(Intent.EXTRA_SUBJECT, shareLogsChooserTitle)
        clipData = ClipData.newUri(context.contentResolver, result.fileName, result.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
```

- [ ] **Step 4: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt
git commit -m "fix: grant shared log uri through clip data"
```

---

### Task 6: Fix LSPosed Filter Logcat Hardcoding

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`

- [ ] **Step 1: Add failing tests for generic LSPosed filter**

Add this test:

```kotlin
@Test
fun `collector filtered logcat is generic when target package is absent`() {
    val executor = RecordingExecutor()
    val collector = RootLogCollector(RootShell(executor))
    val outputDir = createTempDirectory("collector").toFile()

    collector.collect(outputDir, targetPackage = null)

    val filteredCommand = executor.commands.single { it.contains("logcat -d -v threadtime | grep") }
    assertTrue(filteredCommand.contains("DeviceMasker"))
    assertTrue(filteredCommand.contains("LSPosed"))
    assertTrue(filteredCommand.contains("XposedEntry"))
    assertTrue(filteredCommand.contains("All hooks registered"))
    assertTrue(filteredCommand.contains("Spoof event"))
    assertFalse(filteredCommand.contains("com.mantle.verify"))
}
```

Update `collector skips target commands when package is blank or invalid` to assert no hardcoded Mantle package:

```kotlin
assertFalse(executor.commands.any { it.contains("com.mantle.verify") })
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --no-daemon
```

Expected: FAIL because current filter hardcodes `com.mantle.verify` and does not include all hook evidence terms.

- [ ] **Step 3: Implement generic target-aware pattern**

In `RootLogCollector.collect`, replace:

```kotlin
val targetPattern = target?.let { "|$it" }.orEmpty()
```

With:

```kotlin
val evidencePattern =
    buildList {
        add("DeviceMasker")
        add("LSPosed")
        add("lspd")
        add("XposedEntry")
        add("All hooks registered")
        add("Spoof event")
        add("AndroidRuntime")
        add("FATAL EXCEPTION")
        add("ANR")
        target?.let(::add)
    }.joinToString("|")
```

Replace the filtered logcat command:

```kotlin
"logcat -d -v threadtime | grep -i -E 'DeviceMasker|LSPosed|lspd|AndroidRuntime|FATAL EXCEPTION|ANR|com.mantle.verify$targetPattern'",
```

With:

```kotlin
"logcat -d -v threadtime | grep -i -E '$evidencePattern'",
```

- [ ] **Step 4: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt
git commit -m "fix: make root logcat filter target agnostic"
```

---

### Task 7: Keep Boot/Startup Capture Bounded And Document Platform Constraints

**Files:**
- Inspect: `app/src/main/AndroidManifest.xml`
- Inspect: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCaptureService.kt`
- Inspect: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/BootCaptureReceiver.kt`
- Modify only if current manifest drift is found.

- [ ] **Step 1: Verify manifest foreground-service setup**

Run:

```powershell
rg -n "RECEIVE_BOOT_COMPLETED|FOREGROUND_SERVICE_SPECIAL_USE|foregroundServiceType=\"specialUse\"|PROPERTY_SPECIAL_USE_FGS_SUBTYPE|BootCaptureReceiver|RootLogCaptureService" app/src/main/AndroidManifest.xml
```

Expected: matches for all required declarations.

- [ ] **Step 2: Verify service is bounded**

Run:

```powershell
rg -n "START_NOT_STICKY|stopForeground|stopSelf|RootLogCollector\\(\\)\\.collect" app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCaptureService.kt
```

Expected:

- `START_NOT_STICKY`
- `stopForeground(STOP_FOREGROUND_REMOVE)`
- `stopSelf(startId)`
- one bounded `RootLogCollector().collect(...)` call

- [ ] **Step 3: Do not convert this to continuous tailing**

No code change. Continuous root `logcat -f` or `logcat` tailing would need a long-running foreground notification and more failure handling. The requested current design is bounded boot/startup capture plus bounded export snapshot.

- [ ] **Step 4: Commit only if a manifest/service drift fix was required**

If no code changed, do not commit.

---

### Task 8: Update Current Docs And Memory Bank

**Files:**
- Modify: `docs/public/ARCHITECTURE.md`
- Modify: `memory-bank/systemPatterns.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Modify: `memory-bank/techContext.md`
- Modify: `docs/internal/reports/closed/research/2026-05-07/2026-05-07-export-options-root-maximum-only-analysis.md` only if implementation differs from the report.

- [ ] **Step 1: Update architecture docs**

In `docs/public/ARCHITECTURE.md`, replace text saying Root Maximum is one of multiple modes with:

```markdown
- Support export has one user-facing path: Export Logs.
- Export Logs builds the maximum local support bundle.
- The bundle includes app JSONL events, redacted diagnostic snapshots, latest boot/startup root capture, and a fresh export-time root/logcat snapshot when root is granted.
- If root is unavailable, export still creates a ZIP with app logs, snapshots, and a root-unavailable manifest.
```

- [ ] **Step 2: Update Memory Bank system patterns**

In `memory-bank/systemPatterns.md`, replace:

```markdown
- Support bundles are ZIP files with Basic, Full Debug, and Root Maximum modes.
```

With:

```markdown
- Support export has one user-facing `Export Logs` path backed by the maximum root/logcat bundle.
```

Replace:

```markdown
- Root Maximum collection is opt-in and uses bounded fixed command templates.
```

With:

```markdown
- Root/logcat collection uses bounded fixed command templates during boot/startup capture and export-time fresh snapshot.
```

- [ ] **Step 3: Update Memory Bank active context**

Add a dated entry:

```markdown
## 2026-05-07 Single Root-Backed Export Plan

- Planned cleanup: Settings export becomes a single `Export Logs` sheet with Save and Share actions.
- Basic and Full Debug modes will be removed from current production state.
- Export keeps the maximum support bundle internally: app JSONL events, diagnostic snapshots, latest root capture, and fresh export-time root/logcat snapshot.
- `RootLogCollector` will remove the hardcoded `com.mantle.verify` filter and use generic LSPosed/hook evidence terms plus optional target package.
```

- [ ] **Step 4: Update Memory Bank progress after implementation**

Replace mode references in current status with:

```markdown
- Single support export works through `Export Logs` and builds the maximum root/logcat bundle.
- Boot/startup capture writes latest root artifacts before export.
- Export-time snapshot captures fresh LSPosed/logcat evidence when root is granted.
```

- [ ] **Step 5: Run docs grep**

Run:

```powershell
rg "Basic, Full Debug|Basic Export|Full Debug Export|Root Maximum Export|three support bundle modes" docs/public memory-bank
```

Expected: no current-architecture matches. Historical internal reports under `docs/internal/reports` can still mention old behavior.

- [ ] **Step 6: Commit docs**

```powershell
git add docs/public/ARCHITECTURE.md memory-bank/systemPatterns.md memory-bank/activeContext.md memory-bank/progress.md memory-bank/techContext.md docs/internal/reports/closed/research/2026-05-07/2026-05-07-export-options-root-maximum-only-analysis.md
git commit -m "docs: document single root backed log export"
```

---

### Task 9: Full Verification And Graph Update

**Files:**
- Generated: `logs/` only if command output is intentionally saved.
- Modify: `graphify-out/*` after `graphify update .`

- [ ] **Step 1: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModelTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run compile and lint-light gate**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest lint --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run static cleanup checks**

Run:

```powershell
rg "BundleExportMode|SupportBundleMode|setExportMode|KEY_EXPORT_MODE|ShareableLogResult.NoLogs|settings_export_basic|settings_export_full_debug|settings_export_root_maximum|Root Maximum Export|Full Debug Export|Basic Export" app/src/main
```

Expected: no matches.

Run:

```powershell
rg "com\\.mantle\\.verify" app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt
```

Expected: no matches in production collector. Test fixtures may mention target packages only when intentionally passed as `targetPackage`.

- [ ] **Step 4: Manual emulator check**

Install and launch:

```powershell
.\gradlew.bat assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.astrixforge.devicemasker/.ui.MainActivity
```

Expected:

- Settings -> Export Logs opens a sheet with only Save and Share.
- No Basic, Full Debug, or Root Maximum visible.
- Root Access row remains separate.

- [ ] **Step 5: Root export artifact check**

On rooted test device/emulator after exporting:

```powershell
adb shell run-as com.astrixforge.devicemasker ls files/logs/root-capture/latest
```

Expected: latest capture directory exists when boot/startup capture succeeded.

Inspect exported ZIP manually. Expected:

- `root/latest_capture/...`
- `root/export_snapshot/...` when root granted
- `root/root_capture_manifest.json` or nested manifest showing `ROOT_UNAVAILABLE` when root is not granted
- `root/export_snapshot/logcat_filtered_devicemasker_lsposed.txt` includes DeviceMasker/LSPosed/hook evidence when present in logcat

- [ ] **Step 6: Run graph update after code changes**

Run:

```powershell
graphify update .
```

Expected: graph updates without API cost.

- [ ] **Step 7: Final commit**

```powershell
git add app/src docs/public memory-bank graphify-out
git commit -m "refactor: use single root backed log export"
```

---

## Self-Review

Spec coverage:

- Single export UX: Task 4.
- Only Save and Share actions: Task 4.
- No user-facing Root Maximum wording: Task 4 and Task 9 static checks.
- Keep startup/boot capture: Task 7.
- Keep export-time fresh capture: Task 2.
- LSPosed target-process log capture: Task 6.
- Use Google developer docs: documented above and applied in Task 5/Task 7.
- Find issues and fix them: hardcoded Mantle filter and share URI grant are included.

Placeholder scan:

- No `TBD`.
- No `TODO`.
- No "implement later".
- Each code-changing task includes concrete snippets or full replacement code.

Type consistency:

- `ILogManager.exportLogsToUri(context, uri)` matches `FakeLogManager`, `SettingsViewModel`, and `LogManager`.
- `ILogManager.createShareableLogFile(context)` matches `FakeLogManager`, `SettingsViewModel`, and `LogManager`.
- `SupportBundleBuilder.build(outputDir, redactionMode)` matches `LogManager` and `SupportBundleBuilderTest`.
- `ShareableLogResult.NoLogs` is removed from service and `MainActivity`.

