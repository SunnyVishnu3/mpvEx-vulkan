package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import java.io.File
import kotlinx.serialization.encodeToString

class RootLogCollector(private val rootShell: RootShell = RootShell()) {
    fun collect(outputDir: File, context: LogCaptureContext): List<RootCommandResult> {
        outputDir.mkdirs()
        outputDir
            .resolve("capture_context.json")
            .writeText(DiagnosticJson.encodeToString(context), Charsets.UTF_8)
        val target = context.selectedTargetPackage?.takeIf(::isValidRootLogPackageName)
        val evidencePattern = buildRootEvidencePattern(target)
        val results = collectAllFiles(outputDir, target, evidencePattern)
        writeManifest(outputDir, results)
        return results
    }

    fun collect(outputDir: File, targetPackage: String?): List<RootCommandResult> =
        collect(outputDir, LogCaptureContext(selectedTargetPackage = targetPackage))

    private fun collectAllFiles(
        outputDir: File,
        target: String?,
        evidencePattern: String,
    ): List<RootCommandResult> = buildList {
        add(collectLogcatMain(outputDir))
        add(collectFile(outputDir, "logcat_all_buffers.txt", "logcat -d -v threadtime -b all"))
        add(collectLogcatFiltered(outputDir, evidencePattern))
        add(collectFile(outputDir, "anr/list.txt", "ls /data/anr"))
        add(collectFile(outputDir, "anr/anr_traces.txt", "cat /data/anr/anr_*"))
        add(collectFile(outputDir, "tombstones/list.txt", "ls /data/tombstones"))
        add(collectFile(outputDir, "tombstones/tombstones.txt", "cat /data/tombstones/tombstone_*"))
        add(
            collectFile(
                outputDir,
                "dumpsys_package_module.txt",
                "dumpsys package com.astrixforge.devicemasker",
            )
        )
        target?.let {
            add(collectFile(outputDir, "dumpsys_package_target.txt", "dumpsys package $it"))
        }
        add(collectFile(outputDir, "dumpsys_activity_processes.txt", "dumpsys activity processes"))
        add(collectFile(outputDir, "getprop_redacted.txt", "getprop"))
    }

    private fun collectLogcatMain(outputDir: File) =
        collectFile(
            outputDir,
            "logcat_main_system_crash.txt",
            "logcat -d -v threadtime -b main,system,crash,events",
        )

    private fun collectLogcatFiltered(outputDir: File, evidencePattern: String): RootCommandResult {
        val grepCommand = "logcat -d -t 2000 -v threadtime -b all | grep -i -E '$evidencePattern'"
        return collectFile(
            outputDir = outputDir,
            relativePath = "logcat_filtered_devicemasker_lsposed.txt",
            command = grepCommand,
            timeoutMillis = 10_000,
        )
    }

    private fun writeManifest(outputDir: File, results: List<RootCommandResult>) {
        outputDir
            .resolve("command_manifest.jsonl")
            .writeText(results.joinToString("\n") { it.toJsonLine() }, Charsets.UTF_8)
    }

    private fun collectFile(
        outputDir: File,
        relativePath: String,
        command: String,
        timeoutMillis: Long = 5_000,
    ): RootCommandResult {
        val commandDir = File(outputDir, ".commands/${relativePath.replace('/', '_')}")
        val result = rootShell.run(RootCommand(command, timeoutMillis = timeoutMillis), commandDir)
        val destination = File(outputDir, relativePath)
        destination.parentFile?.mkdirs()
        val stdout = result.stdoutPath?.readText(Charsets.UTF_8).orEmpty()
        val stderr = result.stderrPath?.readText(Charsets.UTF_8).orEmpty()
        val content =
            if (stdout.isBlank()) {
                "# empty: status=${result.status}, exitCode=${result.exitCode}, stderr=${stderr.take(200).jsonEscape()}\n"
            } else {
                stdout
            }
        destination.writeText(content, Charsets.UTF_8)
        writeArtifactManifest(destination, result, stdout, stderr)
        return result
    }

    private fun writeArtifactManifest(
        destination: File,
        result: RootCommandResult,
        stdout: String,
        stderr: String,
    ) {
        destination
            .resolveSibling("${destination.name}.manifest.json")
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
}

private fun buildRootEvidencePattern(target: String?): String =
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
        }
        .joinToString("|")

private fun isValidRootLogPackageName(value: String): Boolean =
    value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))

private fun RootCommandResult.toJsonLine(): String =
    """{"command":"${command.jsonEscape()}","status":"$status","exitCode":${exitCode ?: "null"},"timedOut":$timedOut,"durationMillis":$durationMillis,"rootAvailable":$rootAvailable,"stderrSummary":"${stderrSummary.jsonEscape()}"}"""

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
