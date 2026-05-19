package com.astrixforge.devicemasker.service.diagnostics

import java.io.File

class LsposedLogCopyCollector(private val rootShell: RootShell = RootShell()) {

    fun collect(outputDir: File): List<RootCommandResult> {
        outputDir.mkdirs()
        val results =
            LsposedLogPath.entries.map { path ->
                rootShell
                    .run(
                        RootCommand(
                            command = copyCommand(path.sourcePath),
                            maxOutputBytes = LSPOSED_LOG_MAX_BYTES,
                        ),
                        File(outputDir, ".commands/${path.fileName}"),
                    )
                    .also { result -> writeCopiedLog(outputDir, path, result) }
            }
        writeCopyManifest(outputDir, results)
        return results
    }

    private fun writeCopiedLog(outputDir: File, path: LsposedLogPath, result: RootCommandResult) {
        val stdout = result.stdoutPath?.readText(Charsets.UTF_8).orEmpty()
        val destination = outputDir.resolve(path.fileName)
        val missing = stdout.trim() == MISSING_MARKER
        destination.writeText(
            if (missing) {
                "# missing: ${path.sourcePath}\n"
            } else {
                stdout
            },
            Charsets.UTF_8,
        )
        destination
            .resolveSibling("${destination.name}.manifest.json")
            .writeText(
                buildString {
                    append("""{"sourcePath":"${path.sourcePath}"""")
                    append(""","zipName":"${path.fileName}"""")
                    append(""","status":"${if (missing) "MISSING" else result.status}"""")
                    append(""","exitCode":${result.exitCode ?: "null"}""")
                    append(""","rootAvailable":${result.rootAvailable}""")
                    append(""","byteCount":${destination.length()}}""")
                },
                Charsets.UTF_8,
            )
    }

    private fun writeCopyManifest(outputDir: File, results: List<RootCommandResult>) {
        outputDir
            .resolve("lsposed_copy_manifest.jsonl")
            .writeText(results.joinToString("\n") { it.toLsposedManifestLine() }, Charsets.UTF_8)
    }

    private fun copyCommand(sourcePath: String): String =
        "if [ -d $sourcePath ]; then " +
            "for f in $sourcePath/*; do " +
            "if [ -f \"\$f\" ]; then echo \"===== \$f =====\"; cat \"\$f\"; fi; " +
            "done; " +
            "elif [ -f $sourcePath ]; then cat $sourcePath; " +
            "else echo '$MISSING_MARKER'; fi"

    private enum class LsposedLogPath(val sourcePath: String, val fileName: String) {
        CURRENT("/data/adb/lspd/log", "lsposed_log.txt"),
        PREVIOUS("/data/adb/lspd/log.old", "lsposed_log_old.txt"),
    }

    private fun RootCommandResult.toLsposedManifestLine(): String =
        """{"command":"${command.jsonEscape()}","status":"$status","exitCode":${exitCode ?: "null"},"timedOut":$timedOut,"durationMillis":$durationMillis,"rootAvailable":$rootAvailable,"stderrSummary":"${stderrSummary.jsonEscape()}"}"""

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private companion object {
        private const val MISSING_MARKER = "__DEVICEMASKER_LSPOSED_MISSING__"
        private const val MIB_BYTES = 1024 * 1024
        private const val LSPOSED_LOG_MAX_BYTES = 2 * MIB_BYTES
    }
}
