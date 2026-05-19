# Logs System Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Device Masker logs capture, monitor, and export real app, LSPosed, root, and Xposed evidence instead of blank placeholder files.

**Architecture:** Keep the system KISS: app logs stay JSONL, hook evidence stays LSPosed/logcat-owned, root copies LSPosed's persisted log files, live monitoring tails logcat only when the user asks, and export packages only real captured artifacts with manifests. Do not add Room in this plan; JSONL plus an in-memory tail is the right first data shape.

**Tech Stack:** Kotlin, Android foreground service, Jetpack Compose, Navigation 3, kotlinx.serialization, libsu/root shell, existing JSONL diagnostics store, existing support ZIP builder.

---

## Execution Rules

- Do not include source-control steps in this implementation.
- Before editing each existing symbol, run GitNexus impact analysis as required by root `AGENTS.md`.
- Before editing `:app`, read `app/AGENTS.md`.
- Do not edit `:xposed` in this plan. Hook-side logging already writes to LSPosed/logcat; the fix is app/root capture and export.
- Do not add custom Binder/AIDL diagnostics.
- Do not add Room or another local database in this plan.
- Do not mutate, rotate, chmod, or delete LSPosed files. Copy only.
- Keep all temporary validation artifacts under `logs/`.

## Required Preflight

- [ ] **Step 1: Confirm project rules are loaded**

Read:

```powershell
Get-Content -Raw docs\AGENTS_PROJECT_RULES.md
Get-Content -Raw docs\public\ARCHITECTURE.md
Get-Content -Raw app\AGENTS.md
```

Expected: all three files are readable.

- [ ] **Step 2: Run GitNexus impact for the first touched symbols**

Run before editing:

```text
gitnexus_impact(repo="DeviceMasker", target="LogManager", direction="upstream")
gitnexus_impact(repo="DeviceMasker", target="SupportBundleBuilder", direction="upstream")
gitnexus_impact(repo="DeviceMasker", target="RootLogCollector", direction="upstream")
gitnexus_impact(repo="DeviceMasker", target="AppLogStore", direction="upstream")
```

Expected: record the risk levels and direct callers in the implementation notes before edits.

## File Structure

Create:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportArtifact.kt`  
  Owns support artifact encoding and per-artifact manifest data.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/LogCaptureContext.kt`  
  Owns target selection and capture-session inputs.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/LsposedLogCopyCollector.kt`  
  Root-only copier for `/data/adb/lspd/log` and `/data/adb/lspd/log.old`.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportSnapshotProvider.kt`  
  Builds real config, scope, RemotePreferences, and root-state snapshots for export.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/XposedLogParser.kt`  
  Parses Device Masker hook markers from copied logcat/LSPosed text into structured JSONL.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorModels.kt`  
  UI-facing log row, filter, level, source, and capture status models.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorStore.kt`  
  Bounded in-memory tail plus JSONL-backed live monitor session store.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LiveLogCaptureService.kt`  
  User-started foreground service that tails root logcat while monitor is running.
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorRepository.kt`  
  App-side API for start/stop, observe rows, clear view, and export current capture.
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorState.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorViewModel.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorScreen.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/LsposedLogCopyCollectorTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportSnapshotProviderTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/XposedLogParserTest.kt`
- `app/src/test/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorStoreTest.kt`
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorViewModelTest.kt`

Modify:
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootShell.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/NavDestination.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerViewModelFactories.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/settings/SettingsSections.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`
- `app/src/test/java/com/astrixforge/devicemasker/service/AppLogStoreTest.kt`
- `app/src/test/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigatorTest.kt`

Update after implementation:
- `docs/public/ARCHITECTURE.md`
- `memory-bank/activeContext.md`
- `memory-bank/progress.md`
- `memory-bank/systemPatterns.md`
- `memory-bank/techContext.md`

---

### Task 1: Typed Support Artifacts

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportArtifact.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilder.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`

- [ ] **Step 1: Run impact analysis**

Run:

```text
gitnexus_impact(repo="DeviceMasker", target="SupportBundleBuilder", direction="upstream")
```

Expected: direct callers include `LogManager.buildSupportBundle` and support bundle tests.

- [ ] **Step 2: Write failing tests for text and binary artifacts**

Add to `SupportBundleBuilderTest.kt`:

```kotlin
@Test
fun `support bundle copies binary artifacts byte for byte`() {
    val outputDir = createTempDirectory("bundle").toFile()
    val binary = createTempDirectory("binary").toFile().resolve("sample.tar.gz")
    val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x7f, 0x00)
    binary.writeBytes(bytes)

    val bundle =
        SupportBundleBuilder(
                appEvents = emptyList(),
                xposedEvents = emptyList(),
                supportArtifacts =
                    listOf(
                        SupportArtifact(
                            source = binary,
                            zipPath = "root/sample.tar.gz",
                            encoding = ArtifactEncoding.BINARY_RAW,
                        )
                    ),
            )
            .build(outputDir, RedactionMode.REDACTED)

    ZipFile(bundle).use { zip ->
        val copied = zip.getInputStream(zip.getEntry("root/sample.tar.gz")).readBytes()
        assertTrue(bytes.contentEquals(copied))
    }
}

@Test
fun `support bundle redacts only text artifacts marked for redaction`() {
    val outputDir = createTempDirectory("bundle").toFile()
    val text = createTempDirectory("text").toFile().resolve("logcat.txt")
    text.writeText("imei=490154203237518\nraw=visible\n")

    val bundle =
        SupportBundleBuilder(
                appEvents = emptyList(),
                xposedEvents = emptyList(),
                supportArtifacts =
                    listOf(
                        SupportArtifact(
                            source = text,
                            zipPath = "root/logcat.txt",
                            encoding = ArtifactEncoding.TEXT_REDACTED,
                        )
                    ),
            )
            .build(outputDir, RedactionMode.REDACTED)

    ZipFile(bundle).use { zip ->
        val content =
            zip.getInputStream(zip.getEntry("root/logcat.txt")).bufferedReader().readText()
        assertFalse(content.contains("490154203237518"))
        assertTrue(content.contains("[REDACTED_IMEI]"))
        assertTrue(content.contains("raw=visible"))
    }
}
```

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon
```

Expected: fail because `SupportArtifact` and `ArtifactEncoding` do not exist.

- [ ] **Step 4: Create support artifact model**

Create `SupportArtifact.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import java.io.File
import kotlinx.serialization.Serializable

enum class ArtifactEncoding {
    TEXT_REDACTED,
    TEXT_RAW,
    BINARY_RAW,
}

data class SupportArtifact(
    val source: File,
    val zipPath: String,
    val encoding: ArtifactEncoding,
)

@Serializable
data class SupportArtifactManifest(
    val path: String,
    val sourcePath: String,
    val encoding: ArtifactEncoding,
    val byteCount: Long,
)
```

- [ ] **Step 5: Modify bundle builder to accept typed artifacts**

Change constructor in `SupportBundleBuilder.kt`:

```kotlin
class SupportBundleBuilder(
    private val appEvents: List<String>,
    private val xposedEvents: List<String>,
    private val snapshots: Map<String, String> = emptyMap(),
    private val rootArtifactsDir: File? = null,
    private val supportArtifacts: List<SupportArtifact> = emptyList(),
)
```

Add inside `build()` after `rootArtifactsDir` copy:

```kotlin
supportArtifacts.forEach { artifact ->
    zip.writeArtifact(artifact, redactor)
}
```

Add helper methods:

```kotlin
private fun ZipOutputStream.writeArtifact(
    artifact: SupportArtifact,
    redactor: DiagnosticRedactor,
) {
    when (artifact.encoding) {
        ArtifactEncoding.TEXT_REDACTED -> writeRedactedFile(artifact.zipPath, artifact.source, redactor)
        ArtifactEncoding.TEXT_RAW -> writeRawTextFile(artifact.zipPath, artifact.source)
        ArtifactEncoding.BINARY_RAW -> writeBinaryFile(artifact.zipPath, artifact.source)
    }
}

private fun ZipOutputStream.writeRawTextFile(path: String, file: File) {
    putNextEntry(ZipEntry(path))
    file.inputStream().buffered().use { input -> input.copyTo(this) }
    closeEntry()
}

private fun ZipOutputStream.writeBinaryFile(path: String, file: File) {
    putNextEntry(ZipEntry(path))
    file.inputStream().buffered().use { input -> input.copyTo(this) }
    closeEntry()
}
```

- [ ] **Step 6: Run tests to verify pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Root Artifact Manifests And Target Context

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/LogCaptureContext.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/RootLogCollector.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`

- [ ] **Step 1: Run impact analysis**

Run:

```text
gitnexus_impact(repo="DeviceMasker", target="RootLogCollector", direction="upstream")
```

Expected: direct callers include `LogManager.buildSupportBundle`, `RootLogCaptureService.runCapture`, and tests.

- [ ] **Step 2: Write failing test for all-buffers logcat and sidecar manifests**

Add to `RootLogCollectorTest.kt`:

```kotlin
@Test
fun `collector writes all-buffers logcat and sidecar manifests`() {
    val executor = RecordingExecutor()
    val collector = RootLogCollector(RootShell(executor))
    val outputDir = createTempDirectory("collector").toFile()

    collector.collect(
        outputDir = outputDir,
        context = LogCaptureContext(selectedTargetPackage = "com.mantle.verify"),
    )

    assertTrue(outputDir.resolve("logcat_all_buffers.txt").exists())
    assertTrue(outputDir.resolve("logcat_all_buffers.txt.manifest.json").exists())
    assertTrue(
        executor.commands.any {
            it.contains("logcat -d -v threadtime -b all")
        }
    )
    assertTrue(
        outputDir.resolve("capture_context.json").readText().contains("com.mantle.verify")
    )
}
```

- [ ] **Step 3: Write failing test for empty artifact explanation**

Add to `RootLogCollectorTest.kt`:

```kotlin
@Test
fun `collector explains empty command output in artifact and manifest`() {
    val executor = EmptyOutputExecutor()
    val collector = RootLogCollector(RootShell(executor))
    val outputDir = createTempDirectory("collector").toFile()

    collector.collect(
        outputDir = outputDir,
        context = LogCaptureContext(selectedTargetPackage = null),
    )

    val artifact = outputDir.resolve("logcat_filtered_devicemasker_lsposed.txt").readText()
    val manifest =
        outputDir.resolve("logcat_filtered_devicemasker_lsposed.txt.manifest.json").readText()
    assertTrue(artifact.contains("# empty:"))
    assertTrue(manifest.contains("stdoutBytes"))
    assertTrue(manifest.contains("stderrBytes"))
}

private class EmptyOutputExecutor : RootCommandExecutor {
    override fun isRootAvailable(): Boolean = true

    override fun execute(command: String, timeoutMillis: Long): RootExecutionResult =
        RootExecutionResult(
            exitCode = 1,
            stdout = "",
            stderr = "no matching lines",
            timedOut = false,
            durationMillis = 1,
        )
}
```

- [ ] **Step 4: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --no-daemon
```

Expected: fail because `LogCaptureContext` and new collect signature do not exist.

- [ ] **Step 5: Create capture context**

Create `LogCaptureContext.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class LogCaptureContext(
    val selectedTargetPackage: String? = null,
    val includeAllScopedTargets: Boolean = true,
    val sinceMillis: Long? = null,
)
```

- [ ] **Step 6: Update `RootLogCollector.collect` signature and compatibility overload**

In `RootLogCollector.kt`, replace:

```kotlin
fun collect(outputDir: File, targetPackage: String?): List<RootCommandResult>
```

with:

```kotlin
fun collect(outputDir: File, context: LogCaptureContext): List<RootCommandResult> {
    outputDir.mkdirs()
    outputDir.resolve("capture_context.json")
        .writeText(DiagnosticJson.encodeToString(LogCaptureContext.serializer(), context))
    val target = context.selectedTargetPackage?.takeIf(::isValidRootLogPackageName)
    val evidencePattern = buildRootEvidencePattern(target)
    val results = collectAllFiles(outputDir, target, evidencePattern)
    writeManifest(outputDir, results)
    return results
}

fun collect(outputDir: File, targetPackage: String?): List<RootCommandResult> =
    collect(outputDir, LogCaptureContext(selectedTargetPackage = targetPackage))
```

Add imports:

```kotlin
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
```

- [ ] **Step 7: Add all-buffers logcat capture**

In `collectAllFiles`, add immediately after `collectLogcatMain(outputDir)`:

```kotlin
add(collectLogcatAllBuffers(outputDir))
```

Add method:

```kotlin
private fun collectLogcatAllBuffers(outputDir: File) =
    collectFile(
        outputDir,
        "logcat_all_buffers.txt",
        "logcat -d -v threadtime -b all",
    )
```

- [ ] **Step 8: Add sidecar manifests and empty file explanation**

Replace `destination.writeText(...)` in `collectFile` with:

```kotlin
val stdout = result.stdoutPath?.readText().orEmpty()
val stderr = result.stderrPath?.readText().orEmpty()
val content =
    if (stdout.isBlank()) {
        "# empty: status=${result.status}, exitCode=${result.exitCode}, stderr=${stderr.take(200).jsonEscape()}\n"
    } else {
        stdout
    }
destination.writeText(content, Charsets.UTF_8)
writeArtifactManifest(destination, result, stdout, stderr)
```

Add helper:

```kotlin
private fun writeArtifactManifest(
    destination: File,
    result: RootCommandResult,
    stdout: String,
    stderr: String,
) {
    destination.resolveSibling("${destination.name}.manifest.json")
        .writeText(
            buildString {
                append("""{"path":"${destination.name.jsonEscape()}"""")
                append(""","command":"${result.command.jsonEscape()}"""")
                append(""","status":"${result.status}"""")
                append(""","exitCode":${result.exitCode ?: "null"}""")
                append(""","timedOut":${result.timedOut}""")
                append(""","durationMillis":${result.durationMillis}""")
                append(""","rootAvailable":${result.rootAvailable}""")
                append(""","stdoutBytes":${stdout.toByteArray(Charsets.UTF_8).size}""")
                append(""","stderrBytes":${stderr.toByteArray(Charsets.UTF_8).size}""")
                append("}")
            },
            Charsets.UTF_8,
        )
}
```

- [ ] **Step 9: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: LSPosed Log Copy Collector

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/LsposedLogCopyCollector.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/LsposedLogCopyCollectorTest.kt`

- [ ] **Step 1: Write failing tests**

Create `LsposedLogCopyCollectorTest.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class LsposedLogCopyCollectorTest {
    @Test
    fun `collector attempts only known LSPosed log paths`() {
        val executor = RecordingExecutor()
        val collector = LsposedLogCopyCollector(RootShell(executor))
        val outputDir = createTempDirectory("lsposed-copy").toFile()

        collector.collect(outputDir)

        assertTrue(executor.commands.any { it.contains("/data/adb/lspd/log") })
        assertTrue(executor.commands.any { it.contains("/data/adb/lspd/log.old") })
        assertTrue(outputDir.resolve("lsposed_copy_manifest.jsonl").exists())
    }

    @Test
    fun `collector records missing path instead of silently succeeding`() {
        val executor = MissingExecutor()
        val collector = LsposedLogCopyCollector(RootShell(executor))
        val outputDir = createTempDirectory("lsposed-copy").toFile()

        collector.collect(outputDir)

        val manifest = outputDir.resolve("lsposed_copy_manifest.jsonl").readText()
        assertTrue(manifest.contains("MISSING"))
        assertTrue(manifest.contains("/data/adb/lspd/log"))
    }

    private class RecordingExecutor : RootCommandExecutor {
        val commands = mutableListOf<String>()

        override fun isRootAvailable(): Boolean = true

        override fun execute(command: String, timeoutMillis: Long): RootExecutionResult {
            commands += command
            return RootExecutionResult(0, "COPIED", "", false, 1)
        }
    }

    private class MissingExecutor : RootCommandExecutor {
        override fun isRootAvailable(): Boolean = true

        override fun execute(command: String, timeoutMillis: Long): RootExecutionResult =
            RootExecutionResult(0, "MISSING", "", false, 1)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollectorTest --no-daemon
```

Expected: fail because `LsposedLogCopyCollector` does not exist.

- [ ] **Step 3: Implement LSPosed copier**

Create `LsposedLogCopyCollector.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import java.io.File

class LsposedLogCopyCollector(private val rootShell: RootShell = RootShell()) {
    fun collect(outputDir: File): List<RootCommandResult> {
        outputDir.mkdirs()
        val results =
            listOf(
                copyPath("/data/adb/lspd/log", File(outputDir, "log")),
                copyPath("/data/adb/lspd/log.old", File(outputDir, "log.old")),
            )
        outputDir.resolve("lsposed_copy_manifest.jsonl")
            .writeText(results.joinToString("\n") { it.toLsposedManifestLine() }, Charsets.UTF_8)
        return results
    }

    private fun copyPath(source: String, destination: File): RootCommandResult {
        destination.parentFile?.mkdirs()
        val command =
            buildString {
                append("if [ -d '${source.shellQuote()}' ]; then ")
                append("mkdir -p '${destination.absolutePath.shellQuote()}' && ")
                append("cp -a '${source.shellQuote()}/.' '${destination.absolutePath.shellQuote()}/' && ")
                append("echo COPIED; ")
                append("else echo MISSING; fi")
            }
        return rootShell.run(
            RootCommand(command = command, timeoutMillis = COPY_TIMEOUT_MILLIS),
            File(destination.parentFile, ".commands/${destination.name}"),
        )
    }

    private fun RootCommandResult.toLsposedManifestLine(): String =
        """{"command":"${command.jsonEscape()}","status":"$status","exitCode":${exitCode ?: "null"},"stdout":"${stdoutPath?.readText().orEmpty().trim().jsonEscape()}","stderrSummary":"${stderrSummary.jsonEscape()}"}"""

    private fun String.shellQuote(): String = replace("'", "'\\''")

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private companion object {
        private const val COPY_TIMEOUT_MILLIS = 5_000L
    }
}
```

- [ ] **Step 4: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollectorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Wire copier into export root artifacts**

In `LogManager.buildSupportBundle`, inside the `RootAccessManager.hasGrantedRoot()` branch after `RootLogCollector().collect(...)`, add:

```kotlin
LsposedLogCopyCollector().collect(File(rootDir, "lsposed"))
```

Add import:

```kotlin
import com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollector
```

- [ ] **Step 6: Run focused export tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --tests com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollectorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Real Support Snapshot Provider

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/SupportSnapshotProvider.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportSnapshotProviderTest.kt`

- [ ] **Step 1: Write failing test for snapshots**

Create `SupportSnapshotProviderTest.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.JsonConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportSnapshotProviderTest {
    @Test
    fun `snapshot provider exports real config scope and remote prefs`() {
        val config =
            JsonConfig.createDefault()
                .withAppConfig(AppConfig(packageName = "com.mantle.verify", groupId = null))
        val provider =
            StaticSupportSnapshotProvider(
                configJson = config.toPrettyJsonString(),
                remotePrefs = mapOf("module_enabled" to "true"),
                scopePackages = listOf("android", "system", "com.mantle.verify"),
                rootAvailable = true,
                xposedConnected = true,
            )

        val snapshots = provider.buildSnapshots()

        assertTrue(snapshots.getValue("config_snapshot_redacted.json").contains("[PKG:"))
        assertTrue(snapshots.getValue("remote_prefs_snapshot_redacted.json").contains("module_enabled"))
        assertTrue(snapshots.getValue("scope_snapshot.json").contains("android"))
        assertFalse(snapshots.getValue("config_snapshot_redacted.json") == "{}")
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportSnapshotProviderTest --no-daemon
```

Expected: fail because `StaticSupportSnapshotProvider` does not exist.

- [ ] **Step 3: Implement provider interface and static test provider**

Create `SupportSnapshotProvider.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import android.content.Context
import com.astrixforge.devicemasker.BuildConfig
import com.astrixforge.devicemasker.DeviceMaskerApp
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import com.astrixforge.devicemasker.common.toPrettyJsonString
import com.astrixforge.devicemasker.data.XposedPrefs
import com.astrixforge.devicemasker.data.XposedScopeState
import com.astrixforge.devicemasker.service.ConfigManager

interface SupportSnapshotProvider {
    fun buildSnapshots(): Map<String, String>
}

class DefaultSupportSnapshotProvider(private val context: Context) : SupportSnapshotProvider {
    override fun buildSnapshots(): Map<String, String> {
        val config = ConfigManager.config.value
        val scopePackages = XposedPrefs.scopedPackages.value.toPackageList()
        val remotePrefs =
            XposedPrefs.getPrefs()?.all.orEmpty().mapValues { (_, value) -> value.toString() }
        return buildSnapshotMap(
            context = context,
            configJson = config.toPrettyJsonString(),
            remotePrefs = remotePrefs,
            scopePackages = scopePackages,
            rootAvailable = RootAccessManager.hasGrantedRoot(),
            xposedConnected = DeviceMaskerApp.isXposedModuleActive,
        )
    }
}

class StaticSupportSnapshotProvider(
    private val configJson: String,
    private val remotePrefs: Map<String, String>,
    private val scopePackages: List<String>,
    private val rootAvailable: Boolean,
    private val xposedConnected: Boolean,
) : SupportSnapshotProvider {
    override fun buildSnapshots(): Map<String, String> =
        buildSnapshotMap(
            context = null,
            configJson = configJson,
            remotePrefs = remotePrefs,
            scopePackages = scopePackages,
            rootAvailable = rootAvailable,
            xposedConnected = xposedConnected,
        )
}

private fun buildSnapshotMap(
    context: Context?,
    configJson: String,
    remotePrefs: Map<String, String>,
    scopePackages: List<String>,
    rootAvailable: Boolean,
    xposedConnected: Boolean,
): Map<String, String> {
    val appVersion =
        context?.packageManager?.getPackageInfo(context.packageName, 0)?.versionName ?: "test"
    return DiagnosticSnapshotBuilder(
            metadata =
                DiagnosticSnapshotMetadata(
                    appVersion = appVersion,
                    buildType = BuildConfig.BUILD_TYPE,
                    androidSdk = android.os.Build.VERSION.SDK_INT,
                    androidRelease = android.os.Build.VERSION.RELEASE ?: "unknown",
                    device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    rootAvailable = rootAvailable,
                    xposedFrameworkConnected = xposedConnected,
                    moduleEnabled = xposedConnected,
                    targetPackage = null,
                    scopePackages = scopePackages,
                    droppedLogCount = 0,
                ),
            configJson = configJson,
            remotePrefs = remotePrefs,
            hookHealthJson = """{"source":"logcat","available":false}""",
        )
        .build(RedactionMode.REDACTED)
}

private fun XposedScopeState.toPackageList(): List<String> =
    when (this) {
        XposedScopeState.Disconnected -> emptyList()
        is XposedScopeState.Available -> packages.toList().sorted()
        is XposedScopeState.Error -> emptyList()
    }
```

- [ ] **Step 4: Replace placeholder snapshots in `LogManager`**

In `LogManager.buildSupportBundle`, replace the `DiagnosticSnapshotBuilder(...).build(...)` block with:

```kotlin
val snapshots =
    DefaultSupportSnapshotProvider(context).buildSnapshots()
        .plus(SecurityStateDiagnostics.snapshotFile(context))
```

Remove now-unused imports:

```kotlin
import android.os.Build
import com.astrixforge.devicemasker.DeviceMaskerApp
import com.astrixforge.devicemasker.common.diagnostics.RedactionMode
import com.astrixforge.devicemasker.service.diagnostics.DiagnosticSnapshotBuilder
import com.astrixforge.devicemasker.service.diagnostics.DiagnosticSnapshotMetadata
```

Keep `RedactionMode` import if still used in `SupportBundleBuilder.build(...)`.

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportSnapshotProviderTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Parse Xposed Events From Captured Logs

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/diagnostics/XposedLogParser.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Test: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/XposedLogParserTest.kt`

- [ ] **Step 1: Write parser tests**

Create `XposedLogParserTest.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XposedLogParserTest {
    @Test
    fun `parser extracts hook lifecycle events`() {
        val lines =
            listOf(
                "05-18 14:22:03.114  111  222 I DeviceMasker: XposedEntry loaded for process: com.mantle.verify",
                "05-18 14:22:03.220  111  222 I DeviceMasker: Target package selected: com.mantle.verify",
                "05-18 14:22:03.987  111  222 I DeviceMasker: All hooks registered for: com.mantle.verify",
                "05-18 14:22:04.103  111  222 D DeviceMasker: Spoof event: com.mantle.verify/ANDROID_ID",
            )

        val events = XposedLogParser.parseLines(lines, sessionId = "test-session")

        assertEquals(4, events.size)
        assertEquals(DiagnosticEventType.XPOSED_ENTRY_LOADED, events[0].eventType)
        assertEquals(DiagnosticEventType.TARGET_PACKAGE_SELECTED, events[1].eventType)
        assertEquals(DiagnosticEventType.HOOK_REGISTERED, events[2].eventType)
        assertEquals(DiagnosticEventType.SPOOF_RETURNED, events[3].eventType)
        assertTrue(events[3].message.contains("ANDROID_ID"))
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.XposedLogParserTest --no-daemon
```

Expected: fail because `XposedLogParser` does not exist.

- [ ] **Step 3: Implement parser**

Create `XposedLogParser.kt`:

```kotlin
package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEventType
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSeverity
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticSource

object XposedLogParser {
    fun parseLines(lines: List<String>, sessionId: String): List<DiagnosticEvent> =
        lines.mapNotNull { line -> parseLine(line, sessionId) }

    private fun parseLine(line: String, sessionId: String): DiagnosticEvent? {
        val eventType =
            when {
                "XposedEntry loaded for process:" in line -> DiagnosticEventType.XPOSED_ENTRY_LOADED
                "Target package selected:" in line -> DiagnosticEventType.TARGET_PACKAGE_SELECTED
                "All hooks registered for:" in line -> DiagnosticEventType.HOOK_REGISTERED
                "Spoof event:" in line -> DiagnosticEventType.SPOOF_RETURNED
                "Hook registration failed" in line -> DiagnosticEventType.HOOK_FAILED
                else -> null
            } ?: return null

        val now = System.currentTimeMillis()
        return DiagnosticEvent(
            eventId = DiagnosticEvent.nextEventId(now),
            timestampWallMillis = now,
            timestampElapsedMillis = System.nanoTime() / 1_000_000,
            sessionId = sessionId,
            bootId = "unknown",
            source = DiagnosticSource.XPOSED,
            severity = line.toSeverity(),
            eventType = eventType,
            hooker = "DeviceMasker",
            message = line.substringAfter("DeviceMasker:", line).trim(),
            extras = rawLineExtras(line),
        )
    }

    private fun rawLineExtras(line: String): Map<String, String> =
        mapOf("rawLine" to line)

    private fun String.toSeverity(): DiagnosticSeverity =
        when {
            " F " in this -> DiagnosticSeverity.FATAL
            " E " in this -> DiagnosticSeverity.ERROR
            " W " in this -> DiagnosticSeverity.WARN
            " I " in this -> DiagnosticSeverity.INFO
            " V " in this -> DiagnosticSeverity.VERBOSE
            else -> DiagnosticSeverity.DEBUG
        }
}
```

- [ ] **Step 4: Run parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.XposedLogParserTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Feed parsed events into support bundle**

In `LogManager.buildSupportBundle`, after root collection is complete and before `SupportBundleBuilder(...)`, compute:

```kotlin
val xposedEvents =
    rootArtifactsDir
        .walkTopDown()
        .filter { it.isFile && it.name.contains("logcat", ignoreCase = true) }
        .flatMap { file -> XposedLogParser.parseLines(file.readLines(), sessionId = "export") }
        .map { event -> DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event) }
        .toList()
```

Then replace:

```kotlin
xposedEvents = emptyList(),
```

with:

```kotlin
xposedEvents = xposedEvents,
```

- [ ] **Step 6: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.XposedLogParserTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 6: App Log Queue Drop Accounting

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/AppLogStore.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/AppLogStoreTest.kt`

- [ ] **Step 1: Run impact analysis**

Run:

```text
gitnexus_impact(repo="DeviceMasker", target="AppLogStore", direction="upstream")
```

Expected: direct callers include `DeviceMaskerApp`, `LogManager`, and tests.

- [ ] **Step 2: Write failing test for dropped queue events**

Add to `AppLogStoreTest.kt`:

```kotlin
@Test
fun `app log store counts events dropped by saturated queue`() {
    val store =
        AppLogStore(
            file = File(temp.root, "structured.log"),
            maxEntries = 10,
            queueCapacity = 0,
        )

    repeat(5) { index ->
        store.append(AppLogEntry(index.toLong(), "I", "app", "Test", "message-$index"))
    }

    assertTrue(store.queueDroppedEventCount() > 0)
}
```

- [ ] **Step 3: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.AppLogStoreTest --no-daemon
```

Expected: fail because `queueCapacity` and `queueDroppedEventCount()` do not exist.

- [ ] **Step 4: Implement bounded queue and counter**

In `AppLogStore.kt`, add constructor parameter:

```kotlin
private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
```

Replace:

```kotlin
private val eventChannel = Channel<DiagnosticEvent>(Channel.BUFFERED)
```

with:

```kotlin
private val eventChannel = Channel<DiagnosticEvent>(queueCapacity)
private val droppedQueueEvents = AtomicInteger(0)
```

In `appendEvent`, replace failure branch:

```kotlin
if (eventChannel.trySend(event).isFailure) {
    droppedQueueEvents.incrementAndGet()
    pendingEvents.decrementAndGet()
    synchronized(pendingMonitor) { pendingMonitor.notifyAll() }
}
```

Add public method:

```kotlin
fun queueDroppedEventCount(): Int = droppedQueueEvents.get()
```

Add companion constant:

```kotlin
private const val DEFAULT_QUEUE_CAPACITY = 2_048
```

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.AppLogStoreTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 7: Live Log Monitor Store

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorModels.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorStore.kt`
- Test: `app/src/test/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorStoreTest.kt`

- [ ] **Step 1: Write failing store tests**

Create `LogMonitorStoreTest.kt`:

```kotlin
package com.astrixforge.devicemasker.service.logmonitor

import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogMonitorStoreTest {
    @Test
    fun `store keeps bounded newest rows`() = runTest {
        val store = LogMonitorStore(createTempDirectory("monitor").toFile(), maxRows = 3)

        repeat(5) { index ->
            store.append(
                LogMonitorRow(
                    id = "row-$index",
                    wallTimeMillis = index.toLong(),
                    source = LogMonitorSource.XPOSED,
                    level = LogMonitorLevel.INFO,
                    tag = "DeviceMasker",
                    message = "message-$index",
                    rawLine = "raw-$index",
                )
            )
        }

        val rows = store.rows.value
        assertEquals(listOf("row-2", "row-3", "row-4"), rows.map { it.id })
    }

    @Test
    fun `store filters by source level and query`() = runTest {
        val store = LogMonitorStore(createTempDirectory("monitor").toFile(), maxRows = 10)
        store.append(row("1", LogMonitorSource.APP, LogMonitorLevel.INFO, "config loaded"))
        store.append(row("2", LogMonitorSource.XPOSED, LogMonitorLevel.ERROR, "hook failed"))

        val filtered =
            store.filterRows(
                LogMonitorFilter(
                    source = LogMonitorSource.XPOSED,
                    minLevel = LogMonitorLevel.WARN,
                    query = "hook",
                )
            )

        assertEquals(1, filtered.size)
        assertTrue(filtered.single().message.contains("hook"))
    }

    private fun row(
        id: String,
        source: LogMonitorSource,
        level: LogMonitorLevel,
        message: String,
    ): LogMonitorRow =
        LogMonitorRow(
            id = id,
            wallTimeMillis = 1,
            source = source,
            level = level,
            tag = "DeviceMasker",
            message = message,
            rawLine = message,
        )
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.logmonitor.LogMonitorStoreTest --no-daemon
```

Expected: fail because log monitor models do not exist.

- [ ] **Step 3: Implement models**

Create `LogMonitorModels.kt`:

```kotlin
package com.astrixforge.devicemasker.service.logmonitor

import androidx.compose.runtime.Immutable

enum class LogMonitorSource {
    ALL,
    APP,
    XPOSED,
    LSPOSED,
    ROOT,
    CRASH,
}

enum class LogMonitorLevel(val rank: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    FATAL(5),
}

enum class LogCaptureStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ROOT_UNAVAILABLE,
    FAILED,
}

@Immutable
data class LogMonitorRow(
    val id: String,
    val wallTimeMillis: Long,
    val source: LogMonitorSource,
    val level: LogMonitorLevel,
    val tag: String,
    val message: String,
    val rawLine: String,
)

@Immutable
data class LogMonitorFilter(
    val source: LogMonitorSource = LogMonitorSource.ALL,
    val minLevel: LogMonitorLevel = LogMonitorLevel.DEBUG,
    val query: String = "",
)
```

- [ ] **Step 4: Implement store**

Create `LogMonitorStore.kt`:

```kotlin
package com.astrixforge.devicemasker.service.logmonitor

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LogMonitorStore(
    private val rootDir: File,
    private val maxRows: Int = DEFAULT_MAX_ROWS,
) {
    private val _rows = MutableStateFlow<List<LogMonitorRow>>(emptyList())
    val rows: StateFlow<List<LogMonitorRow>> = _rows.asStateFlow()

    @Synchronized
    fun append(row: LogMonitorRow) {
        rootDir.mkdirs()
        rootDir.resolve("live_monitor.jsonl").appendText(row.toJsonLine() + "\n", Charsets.UTF_8)
        _rows.update { current -> (current + row).takeLast(maxRows) }
    }

    fun filterRows(filter: LogMonitorFilter): List<LogMonitorRow> =
        rows.value.filter { row ->
            val sourceMatches = filter.source == LogMonitorSource.ALL || row.source == filter.source
            val levelMatches = row.level.rank >= filter.minLevel.rank
            val queryMatches =
                filter.query.isBlank() ||
                    row.message.contains(filter.query, ignoreCase = true) ||
                    row.tag.contains(filter.query, ignoreCase = true)
            sourceMatches && levelMatches && queryMatches
        }

    @Synchronized
    fun clear() {
        rootDir.resolve("live_monitor.jsonl").delete()
        _rows.value = emptyList()
    }

    private fun LogMonitorRow.toJsonLine(): String =
        """{"id":"${id.escape()}","wallTimeMillis":$wallTimeMillis,"source":"$source","level":"$level","tag":"${tag.escape()}","message":"${message.escape()}","rawLine":"${rawLine.escape()}"}"""

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private companion object {
        private const val DEFAULT_MAX_ROWS = 2_000
    }
}
```

- [ ] **Step 5: Run store tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.logmonitor.LogMonitorStoreTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 8: Live Log Capture Foreground Service

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LiveLogCaptureService.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorRepository.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/astrixforge/devicemasker/service/logmonitor/LogMonitorStoreTest.kt`

- [ ] **Step 1: Add parser test for raw logcat row conversion**

Add to `LogMonitorStoreTest.kt`:

```kotlin
@Test
fun `logcat parser converts Device Masker line into monitor row`() {
    val row =
        LogMonitorRepository.parseLogcatLine(
            "05-18 14:22:03.114  111  222 I DeviceMasker: All hooks registered for: com.mantle.verify"
        )

    requireNotNull(row)
    assertEquals(LogMonitorSource.XPOSED, row.source)
    assertEquals(LogMonitorLevel.INFO, row.level)
    assertTrue(row.message.contains("All hooks registered"))
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.logmonitor.LogMonitorStoreTest --no-daemon
```

Expected: fail because `LogMonitorRepository` does not exist.

- [ ] **Step 3: Implement repository parser and service start/stop facade**

Create `LogMonitorRepository.kt`:

```kotlin
package com.astrixforge.devicemasker.service.logmonitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.astrixforge.devicemasker.service.diagnostics.RootAccessManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LogMonitorRepository {
    private val store by lazy {
        LogMonitorStore(com.astrixforge.devicemasker.DeviceMaskerApp.getInstance().filesDir.resolve("logs/live-monitor"))
    }
    private val _status = MutableStateFlow(LogCaptureStatus.STOPPED)
    val status: StateFlow<LogCaptureStatus> = _status.asStateFlow()
    val rows = store.rows

    fun start(context: Context) {
        if (!RootAccessManager.hasGrantedRoot()) {
            _status.value = LogCaptureStatus.ROOT_UNAVAILABLE
            return
        }
        _status.value = LogCaptureStatus.STARTING
        ContextCompat.startForegroundService(
            context,
            Intent(context, LiveLogCaptureService::class.java).setAction(LiveLogCaptureService.ACTION_START),
        )
    }

    fun stop(context: Context) {
        _status.value = LogCaptureStatus.STOPPING
        context.startService(
            Intent(context, LiveLogCaptureService::class.java).setAction(LiveLogCaptureService.ACTION_STOP)
        )
    }

    fun markRunning() {
        _status.value = LogCaptureStatus.RUNNING
    }

    fun markStopped() {
        _status.value = LogCaptureStatus.STOPPED
    }

    fun appendRawLogcat(line: String) {
        parseLogcatLine(line)?.let(store::append)
    }

    fun clear() {
        store.clear()
    }

    fun parseLogcatLine(line: String): LogMonitorRow? {
        if (!line.contains("DeviceMasker") && !line.contains("LSPosed") && !line.contains("XposedEntry")) {
            return null
        }
        val now = System.currentTimeMillis()
        val level = parseLevel(line)
        val source =
            when {
                line.contains("AndroidRuntime") -> LogMonitorSource.CRASH
                line.contains("LSPosed", ignoreCase = true) -> LogMonitorSource.LSPOSED
                line.contains("XposedEntry") || line.contains("Spoof event") -> LogMonitorSource.XPOSED
                else -> LogMonitorSource.APP
            }
        return LogMonitorRow(
            id = "log_$now_${line.hashCode()}",
            wallTimeMillis = now,
            source = source,
            level = level,
            tag = line.substringAfterLast(" ", "DeviceMasker").substringBefore(":").ifBlank { "DeviceMasker" },
            message = line.substringAfter(":", line).trim(),
            rawLine = line,
        )
    }

    private fun parseLevel(line: String): LogMonitorLevel =
        when {
            " F " in line -> LogMonitorLevel.FATAL
            " E " in line -> LogMonitorLevel.ERROR
            " W " in line -> LogMonitorLevel.WARN
            " I " in line -> LogMonitorLevel.INFO
            " V " in line -> LogMonitorLevel.VERBOSE
            else -> LogMonitorLevel.DEBUG
        }
}
```

- [ ] **Step 4: Implement foreground service**

Create `LiveLogCaptureService.kt`:

```kotlin
package com.astrixforge.devicemasker.service.logmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class LiveLogCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture(startId)
            else -> startCapture()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        process?.destroy()
        serviceScope.cancel()
        LogMonitorRepository.markStopped()
        super.onDestroy()
    }

    private fun startCapture() {
        if (process != null) return
        startAsForeground()
        serviceScope.launch {
            runCatching {
                    val newProcess =
                        ProcessBuilder("su", "-c", "logcat -b all -v threadtime").redirectErrorStream(true).start()
                    process = newProcess
                    LogMonitorRepository.markRunning()
                    newProcess.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach(LogMonitorRepository::appendRawLogcat)
                    }
                }
                .onFailure { error ->
                    Timber.w(error, "Live log capture failed")
                    LogMonitorRepository.markStopped()
                    stopSelf()
                }
        }
    }

    private fun stopCapture(startId: Int) {
        process?.destroy()
        process = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        LogMonitorRepository.markStopped()
        stopSelf(startId)
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.logs_monitor_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.logs_monitor_notification_title))
            .setContentText(getString(R.string.logs_monitor_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.astrixforge.devicemasker.action.START_LIVE_LOG_CAPTURE"
        const val ACTION_STOP = "com.astrixforge.devicemasker.action.STOP_LIVE_LOG_CAPTURE"
        private const val CHANNEL_ID = "live_log_capture"
        private const val NOTIFICATION_ID = 4102
    }
}
```

- [ ] **Step 5: Register service and strings**

Add to `AndroidManifest.xml` inside `<application>`:

```xml
<service
    android:name=".service.logmonitor.LiveLogCaptureService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="User-started live logcat monitor for Device Masker and LSPosed debugging" />
</service>
```

Add to `strings.xml`:

```xml
<string name="logs_monitor_notification_channel">Live log monitor</string>
<string name="logs_monitor_notification_title">Capturing logs</string>
<string name="logs_monitor_notification_text">Device Masker is collecting live LSPosed and logcat evidence.</string>
```

- [ ] **Step 6: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.logmonitor.LogMonitorStoreTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 9: Logs Monitor Screen And Navigation

**Files:**
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorState.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorViewModel.kt`
- Create: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorScreen.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/NavDestination.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigationState.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerViewModelFactories.kt`
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/screens/logsmonitor/LogsMonitorViewModelTest.kt`
- Test: `app/src/test/kotlin/com/astrixforge/devicemasker/ui/navigation/DeviceMaskerNavigatorTest.kt`

- [ ] **Step 1: Write ViewModel tests**

Create `LogsMonitorViewModelTest.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.screens.logsmonitor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.astrixforge.devicemasker.MainDispatcherRule
import com.astrixforge.devicemasker.service.logmonitor.LogCaptureStatus
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorFilter
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorLevel
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LogsMonitorViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `filter updates visible rows`() = runTest {
        val fake = FakeLogsMonitorController()
        fake.rows.value =
            listOf(
                row("1", LogMonitorSource.APP, LogMonitorLevel.INFO, "config loaded"),
                row("2", LogMonitorSource.XPOSED, LogMonitorLevel.ERROR, "hook failed"),
            )
        val viewModel =
            LogsMonitorViewModel(
                application = ApplicationProvider.getApplicationContext<Application>(),
                controller = fake,
            )

        viewModel.setSource(LogMonitorSource.XPOSED)
        viewModel.setMinLevel(LogMonitorLevel.WARN)
        viewModel.setQuery("hook")

        assertEquals(listOf("2"), viewModel.state.value.visibleRows.map { it.id })
    }

    @Test
    fun `start and stop delegate to controller`() = runTest {
        val fake = FakeLogsMonitorController()
        val viewModel =
            LogsMonitorViewModel(
                application = ApplicationProvider.getApplicationContext<Application>(),
                controller = fake,
            )

        viewModel.startCapture()
        viewModel.stopCapture()

        assertEquals(1, fake.startCalls)
        assertEquals(1, fake.stopCalls)
    }

    private fun row(
        id: String,
        source: LogMonitorSource,
        level: LogMonitorLevel,
        message: String,
    ): LogMonitorRow =
        LogMonitorRow(
            id = id,
            wallTimeMillis = 1,
            source = source,
            level = level,
            tag = "DeviceMasker",
            message = message,
            rawLine = message,
        )
}

private class FakeLogsMonitorController : LogsMonitorController {
    override val rows = MutableStateFlow<List<LogMonitorRow>>(emptyList())
    override val status = MutableStateFlow(LogCaptureStatus.STOPPED)
    var startCalls = 0
    var stopCalls = 0

    override fun start(context: android.content.Context) {
        startCalls++
    }

    override fun stop(context: android.content.Context) {
        stopCalls++
    }

    override fun clear() {
        rows.value = emptyList()
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorViewModelTest --no-daemon
```

Expected: fail because screen state/controller/ViewModel do not exist.

- [ ] **Step 3: Implement state and controller boundary**

Create `LogsMonitorState.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.screens.logsmonitor

import androidx.compose.runtime.Immutable
import com.astrixforge.devicemasker.service.logmonitor.LogCaptureStatus
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorFilter
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class LogsMonitorState(
    val status: LogCaptureStatus = LogCaptureStatus.STOPPED,
    val filter: LogMonitorFilter = LogMonitorFilter(),
    val visibleRows: ImmutableList<LogMonitorRow> = persistentListOf(),
)
```

Create controller interface in `LogsMonitorViewModel.kt`:

```kotlin
interface LogsMonitorController {
    val rows: StateFlow<List<LogMonitorRow>>
    val status: StateFlow<LogCaptureStatus>
    fun start(context: Context)
    fun stop(context: Context)
    fun clear()
}
```

Add production controller:

```kotlin
object DefaultLogsMonitorController : LogsMonitorController {
    override val rows = LogMonitorRepository.rows
    override val status = LogMonitorRepository.status
    override fun start(context: Context) = LogMonitorRepository.start(context)
    override fun stop(context: Context) = LogMonitorRepository.stop(context)
    override fun clear() = LogMonitorRepository.clear()
}
```

- [ ] **Step 4: Implement ViewModel**

Create `LogsMonitorViewModel.kt` with:

```kotlin
package com.astrixforge.devicemasker.ui.screens.logsmonitor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astrixforge.devicemasker.service.logmonitor.LogCaptureStatus
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorFilter
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorLevel
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRepository
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorSource
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LogsMonitorViewModel(
    application: Application,
    private val controller: LogsMonitorController = DefaultLogsMonitorController,
) : AndroidViewModel(application) {
    private val filter = MutableStateFlow(LogMonitorFilter())
    private val _state = MutableStateFlow(LogsMonitorState())
    val state: StateFlow<LogsMonitorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(controller.rows, controller.status, filter) { rows, status, activeFilter ->
                LogsMonitorState(
                    status = status,
                    filter = activeFilter,
                    visibleRows = rows.filter(activeFilter).toImmutableList(),
                )
            }.collect { next -> _state.value = next }
        }
    }

    fun startCapture() = controller.start(getApplication())

    fun stopCapture() = controller.stop(getApplication())

    fun clear() = controller.clear()

    fun setSource(source: LogMonitorSource) {
        filter.update { it.copy(source = source) }
    }

    fun setMinLevel(level: LogMonitorLevel) {
        filter.update { it.copy(minLevel = level) }
    }

    fun setQuery(query: String) {
        filter.update { it.copy(query = query) }
    }

    private fun List<LogMonitorRow>.filter(filter: LogMonitorFilter): List<LogMonitorRow> =
        filter { row ->
            val sourceMatches = filter.source == LogMonitorSource.ALL || row.source == filter.source
            val levelMatches = row.level.rank >= filter.minLevel.rank
            val queryMatches =
                filter.query.isBlank() ||
                    row.message.contains(filter.query, ignoreCase = true) ||
                    row.tag.contains(filter.query, ignoreCase = true)
            sourceMatches && levelMatches && queryMatches
        }
}
```

- [ ] **Step 5: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorViewModelTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Add destination**

Modify `NavDestination.kt`:

```kotlin
@Serializable @SerialName("logs-monitor") data object LogsMonitor : NavDestination
```

Modify `DeviceMaskerNavigationState.kt`:

```kotlin
fun navigateToLogsMonitor() {
    if (state.currentDestination != NavDestination.LogsMonitor) {
        state.push(NavDestination.LogsMonitor)
    }
}
```

Ensure focus-screen bottom bar hidden condition includes:

```kotlin
currentDestination == NavDestination.LogsMonitor
```

- [ ] **Step 7: Add navigation test**

Add to `DeviceMaskerNavigatorTest.kt`:

```kotlin
@Test
fun `navigate to logs monitor pushes settings child screen`() {
    val state = DeviceMaskerNavigationState()
    val navigator = DeviceMaskerNavigator(state)

    navigator.navigateToSettings()
    navigator.navigateToLogsMonitor()

    assertEquals(NavDestination.LogsMonitor, state.currentDestination)
}
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
```

Expected: pass after destination wiring.

- [ ] **Step 8: Implement screen UI**

Create `LogsMonitorScreen.kt`:

```kotlin
package com.astrixforge.devicemasker.ui.screens.logsmonitor

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrixforge.devicemasker.service.logmonitor.LogCaptureStatus
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorLevel
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorRow
import com.astrixforge.devicemasker.service.logmonitor.LogMonitorSource
import kotlinx.collections.immutable.ImmutableList

@Composable
fun LogsMonitorScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogsMonitorViewModel =
        viewModel(factory = logsMonitorViewModelFactory(application)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LogsMonitorContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onStart = viewModel::startCapture,
        onStop = viewModel::stopCapture,
        onClear = viewModel::clear,
        onSourceSelected = viewModel::setSource,
        onMinLevelSelected = viewModel::setMinLevel,
        onQueryChanged = viewModel::setQuery,
        modifier = modifier,
    )
}

@Composable
fun LogsMonitorContent(
    state: LogsMonitorState,
    onNavigateBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onSourceSelected: (LogMonitorSource) -> Unit,
    onMinLevelSelected: (LogMonitorLevel) -> Unit,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            LogsMonitorHeader(
                status = state.status,
                onNavigateBack = onNavigateBack,
                onStart = onStart,
                onStop = onStop,
                onClear = onClear,
            )
        }
        item(key = "filters") {
            LogsMonitorFilters(
                state = state,
                onSourceSelected = onSourceSelected,
                onMinLevelSelected = onMinLevelSelected,
                onQueryChanged = onQueryChanged,
            )
        }
        if (state.visibleRows.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No matching logs captured yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.visibleRows, key = { it.id }) { row -> LogRow(row) }
        }
    }
}

@Composable
private fun LogsMonitorHeader(
    status: LogCaptureStatus,
    onNavigateBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            Text(text = "Logs Monitor", style = MaterialTheme.typography.headlineSmall)
            Text(text = "Capture: $status", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = if (status == LogCaptureStatus.RUNNING) onStop else onStart) {
            Icon(
                imageVector = if (status == LogCaptureStatus.RUNNING) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = if (status == LogCaptureStatus.RUNNING) "Stop capture" else "Start capture",
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Outlined.Delete, contentDescription = "Clear logs")
        }
    }
}

@Composable
private fun LogsMonitorFilters(
    state: LogsMonitorState,
    onSourceSelected: (LogMonitorSource) -> Unit,
    onMinLevelSelected: (LogMonitorLevel) -> Unit,
    onQueryChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LogMonitorSource.ALL, LogMonitorSource.APP, LogMonitorSource.XPOSED, LogMonitorSource.LSPOSED, LogMonitorSource.CRASH)
                .forEach { source ->
                    FilterChip(
                        selected = state.filter.source == source,
                        onClick = { onSourceSelected(source) },
                        label = { Text(source.name) },
                    )
                }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LogMonitorLevel.DEBUG, LogMonitorLevel.INFO, LogMonitorLevel.WARN, LogMonitorLevel.ERROR)
                .forEach { level ->
                    AssistChip(
                        onClick = { onMinLevelSelected(level) },
                        label = { Text(level.name) },
                    )
                }
        }
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search") },
        )
    }
}

@Composable
private fun LogRow(row: LogMonitorRow) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${row.level.name} ${row.source.name} ${row.tag}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = row.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

- [ ] **Step 9: Add ViewModel factory**

In `DeviceMaskerViewModelFactories.kt`, add:

```kotlin
fun logsMonitorViewModelFactory(application: Application): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LogsMonitorViewModel(application = application)
        }
    }
```

Add imports for `Application`, `LogsMonitorViewModel`, and lifecycle factory APIs if missing.

- [ ] **Step 10: Wire MainActivity route**

In `MainActivity.kt` entry provider, add a destination branch for `NavDestination.LogsMonitor`:

```kotlin
NavDestination.LogsMonitor -> {
    LogsMonitorScreen(
        application = application,
        onNavigateBack = navigator::goBack,
        modifier = Modifier.fillMaxSize(),
    )
}
```

Add import:

```kotlin
import com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorScreen
```

- [ ] **Step 11: Add Settings row**

In `SettingsSections.kt`, add parameter:

```kotlin
onNavigateToLogsMonitor: () -> Unit,
```

Add row between Export Logs and Diagnostics:

```kotlin
Spacer(modifier = Modifier.height(8.dp))
SettingsClickableItem(
    icon = Icons.Outlined.Article,
    title = stringResource(id = R.string.settings_logs_monitor),
    description = stringResource(id = R.string.settings_logs_monitor_description),
    onClick = onNavigateToLogsMonitor,
)
```

Add import:

```kotlin
import androidx.compose.material.icons.outlined.Article
```

Thread `onNavigateToLogsMonitor` through `SettingsScreen.kt`, `SettingsScreenHelpers.kt`, and `MainActivity.kt`.

Add strings:

```xml
<string name="settings_logs_monitor">Logs Monitor</string>
<string name="settings_logs_monitor_description">Watch live Device Masker, LSPosed, and hook evidence while debugging a target app.</string>
```

- [ ] **Step 12: Run focused UI/navigation tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorViewModelTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 10: Make Export Use Real Inputs End To End

**Files:**
- Modify: `app/src/main/kotlin/com/astrixforge/devicemasker/service/LogManager.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/SupportBundleBuilderTest.kt`
- Modify: `app/src/test/java/com/astrixforge/devicemasker/service/diagnostics/RootLogCollectorTest.kt`

- [ ] **Step 1: Run impact analysis**

Run:

```text
gitnexus_impact(repo="DeviceMasker", target="LogManager", direction="upstream")
```

Expected: direct consumer is `SettingsViewModel`; risk should be reviewed before edit.

- [ ] **Step 2: Remove placeholder Xposed events and snapshots**

In `LogManager.kt`, final production flow should look like:

```kotlin
private suspend fun buildSupportBundle(
    context: Context,
    outputDir: File = File(context.cacheDir, "logs"),
): File {
    val appEvents = AppLogStore.from(context).readDiagnosticEvents()
    val rootArtifactsDir =
        RootCaptureStore.prepareExportArtifacts(context, outputDir).also { rootDir ->
            if (RootAccessManager.hasGrantedRoot()) {
                val exportDir = File(rootDir, "export_snapshot")
                RootLogCollector()
                    .collect(
                        outputDir = exportDir,
                        context = LogCaptureContext(selectedTargetPackage = null),
                    )
                LsposedLogCopyCollector().collect(File(rootDir, "lsposed"))
            } else {
                RootCaptureStore.writeManifest(
                    dir = rootDir,
                    trigger = "export",
                    status = "ROOT_UNAVAILABLE",
                    message =
                        "Root access is not currently granted; export used app logs and captured root manifests only.",
                )
            }
        }

    val snapshots =
        DefaultSupportSnapshotProvider(context).buildSnapshots()
            .plus(SecurityStateDiagnostics.snapshotFile(context))

    val xposedEvents =
        rootArtifactsDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "txt" }
            .flatMap { file ->
                XposedLogParser.parseLines(file.readLines(Charsets.UTF_8), sessionId = "export")
            }
            .map { event -> DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event) }
            .toList()

    return SupportBundleBuilder(
            appEvents =
                appEvents.map { event ->
                    DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event)
                },
            xposedEvents = xposedEvents,
            snapshots = snapshots,
            rootArtifactsDir = rootArtifactsDir,
        )
        .build(outputDir, RedactionMode.REDACTED)
}
```

- [ ] **Step 3: Add export manifest test at builder level**

Add to `SupportBundleBuilderTest.kt`:

```kotlin
@Test
fun `support bundle can contain parsed xposed events`() {
    val outputDir = createTempDirectory("bundle").toFile()
    val bundle =
        SupportBundleBuilder(
                appEvents = emptyList(),
                xposedEvents =
                    listOf(
                        """{"message":"All hooks registered for: [PKG:abcd1234]"}"""
                    ),
            )
            .build(outputDir, RedactionMode.REDACTED)

    ZipFile(bundle).use { zip ->
        val content =
            zip.getInputStream(zip.getEntry("xposed/xposed_events.jsonl"))
                .bufferedReader()
                .readText()
        assertTrue(content.contains("All hooks registered"))
    }
}
```

- [ ] **Step 4: Run export-related tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --tests com.astrixforge.devicemasker.service.diagnostics.XposedLogParserTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportSnapshotProviderTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 11: Documentation And Memory Bank Updates

**Files:**
- Modify: `docs/public/ARCHITECTURE.md`
- Modify: `memory-bank/activeContext.md`
- Modify: `memory-bank/progress.md`
- Modify: `memory-bank/systemPatterns.md`
- Modify: `memory-bank/techContext.md`

- [ ] **Step 1: Update architecture diagnostics section**

In `docs/public/ARCHITECTURE.md`, replace the diagnostics/export facts with this accurate wording:

```markdown
- App logs are stored as app-owned structured JSONL.
- Hook-side logs remain LSPosed/logcat-owned; the app does not use a custom Binder path for hook evidence.
- Export copies app JSONL, real redacted snapshots, LSPosed log files copied from known root paths when available, and root/logcat artifacts with sidecar manifests.
- `xposed/xposed_events.jsonl` is generated only from parsed copied logcat/LSPosed lines. If no matching hook lines are captured, the bundle manifest explains that no Xposed events were parsed.
- Logs Monitor is a user-started live root logcat capture screen for debugging target launches. It is not proof of hook success unless LSPosed/logcat lines and target-app values are present.
```

- [ ] **Step 2: Update Memory Bank active context**

Append to `memory-bank/activeContext.md`:

```markdown
## 2026-05-18 Logs System Remediation

- Implemented real support export inputs for app logs, config/scope snapshots, root/logcat artifacts, and copied LSPosed log directories.
- Removed the fake production path where `xposed/xposed_events.jsonl` was created from an empty list.
- Added artifact sidecar manifests so blank files explain missing output, command status, and root availability.
- Added user-started Logs Monitor flow for live root logcat capture and filtering.
- No Room database was added; JSONL plus an in-memory bounded tail remains the current KISS data shape.
```

- [ ] **Step 3: Update Memory Bank progress**

Append to `memory-bank/progress.md`:

```markdown
### 2026-05-18 Logs System Remediation

- Support export now uses real app JSONL events and real diagnostic snapshots instead of placeholder `{}` values.
- Root export captures all logcat buffers, filtered Device Masker/LSPosed evidence, and copied LSPosed log directories when root is granted.
- Every root artifact has manifest evidence for status, exit code, byte counts, and root availability.
- Logs Monitor is available from Settings for live root logcat observation while reproducing target-app behavior.
- Local database storage remains intentionally deferred until measured monitor volume requires it.
```

- [ ] **Step 4: Update system patterns**

In `memory-bank/systemPatterns.md`, under Diagnostics Pattern, add:

```markdown
Logs Monitor pattern:
- User-started only; no always-on logcat tailing.
- Root required for full logcat capture.
- Stores a bounded in-memory row tail and JSONL raw monitor file.
- Export remains the durable evidence path.
- Hook success still requires LSPosed/logcat hook lines plus target-app value evidence.
```

- [ ] **Step 5: Update technical context**

In `memory-bank/techContext.md`, update the logging row:

```markdown
| Logging | Timber structured JSONL in `:app`, DualLog/XposedModule structured sink in `:xposed`, root copied LSPosed logs, user-started live logcat monitor |
```

- [ ] **Step 6: Verify docs have no stale blank-export claim**

Run:

```powershell
rg -n "xposedEvents = emptyList|placeholder|\\{\\}\"|hardcoded two-package|blank by design" docs\public memory-bank
```

Expected: no stale production claim remains. Historical audit files may still contain those terms and can be ignored if the path starts with `docs/internal/reports/`.

---

### Task 12: Full Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused app tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportBundleBuilderTest --tests com.astrixforge.devicemasker.service.diagnostics.RootLogCollectorTest --tests com.astrixforge.devicemasker.service.diagnostics.LsposedLogCopyCollectorTest --tests com.astrixforge.devicemasker.service.diagnostics.SupportSnapshotProviderTest --tests com.astrixforge.devicemasker.service.diagnostics.XposedLogParserTest --tests com.astrixforge.devicemasker.service.AppLogStoreTest --tests com.astrixforge.devicemasker.service.logmonitor.LogMonitorStoreTest --tests com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorViewModelTest --tests com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigatorTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run app static checks**

Run:

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt :app:testDebugUnitTest lint --no-daemon --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run broad local gate**

Run:

```powershell
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint test assembleDebug --no-daemon --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run GitNexus change detection**

Run:

```text
gitnexus_detect_changes(repo="DeviceMasker", scope="all")
```

Expected: affected scope should be app logging/export, root diagnostics, Settings navigation, and Logs Monitor UI. If unrelated source areas are listed, inspect the diff before proceeding.

- [ ] **Step 5: Rooted device export smoke**

Run on a rooted LSPosed device or emulator:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.astrixforge.devicemasker
adb shell monkey -p com.astrixforge.devicemasker -c android.intent.category.LAUNCHER 1
```

Manual actions:
- grant root when prompted
- open Settings
- open Logs Monitor
- start capture
- launch a scoped target app that is assigned in Device Masker
- return to Device Masker
- stop capture
- export logs

Expected ZIP contents:

```text
app/app_events.jsonl
xposed/xposed_events.jsonl
config/config_snapshot_redacted.json
config/remote_prefs_snapshot_redacted.json
scope/scope_snapshot.json
root/latest_capture/root_capture_manifest.json
root/export_snapshot/logcat_all_buffers.txt
root/export_snapshot/logcat_all_buffers.txt.manifest.json
root/export_snapshot/logcat_filtered_devicemasker_lsposed.txt
root/lsposed/lsposed_copy_manifest.jsonl
```

Expected behavior:
- `xposed/xposed_events.jsonl` is non-empty after target hook lines are captured.
- If LSPosed log paths are missing, `root/lsposed/lsposed_copy_manifest.jsonl` says `MISSING`.
- Blank text artifacts contain `# empty:` and a sidecar manifest.

- [ ] **Step 6: Runtime hook evidence check**

Capture logcat to `logs/device/`:

```powershell
adb logcat -d -b all -v threadtime > logs\device\2026-05-18-logs-remediation-logcat.txt
```

Expected markers:

```text
XposedEntry loaded for process:
Target package selected:
All hooks registered for:
Spoof event:
```

Do not claim hook success unless target-app value evidence also exists.

## Self-Review

- Spec coverage:
  - Blank Xposed JSONL fixed by Tasks 5 and 10.
  - Placeholder snapshots fixed by Task 4.
  - Generic root logcat improved by Task 2.
  - Realtime capture added by Tasks 7 and 8.
  - LSPosed log copy added by Task 3.
  - Blank artifact explanation added by Task 2.
  - App log dropped-event accounting added by Task 6.
  - Binary/text artifact split added by Task 1.
  - Logs Monitor screen added by Task 9.
  - Docs and Memory Bank updates included in Task 11.
- Placeholder scan:
  - No unresolved placeholders are intentionally left in the plan.
  - No source-control steps are included.
- Type consistency:
  - `LogCaptureContext`, `SupportArtifact`, `ArtifactEncoding`, `LogMonitorRow`, `LogMonitorFilter`, `LogMonitorSource`, `LogMonitorLevel`, and `LogCaptureStatus` are defined before later tasks use them.
