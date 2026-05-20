package com.astrixforge.devicemasker.service.diagnostics

import com.astrixforge.devicemasker.common.diagnostics.DiagnosticEvent
import com.astrixforge.devicemasker.common.diagnostics.DiagnosticJson
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.SerializationException

data class StoreStats(
    val totalEvents: Long,
    val droppedEvents: Long,
    val corruptedLines: Long,
    val files: Int,
    val bytes: Long,
)

class JsonlDiagnosticStore(
    private val sessionDir: File,
    private val filePrefix: String = "app_events",
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
) {
    private val droppedEvents = AtomicLong(0)

    @Synchronized
    fun append(event: DiagnosticEvent): Boolean {
        sessionDir.mkdirs()
        val encoded = DiagnosticJson.encodeToString(DiagnosticEvent.serializer(), event) + "\n"
        val bytes = encoded.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxFileBytes) {
            droppedEvents.incrementAndGet()
            writeState()
            return false
        }

        val file = writableFile(bytes.size.toLong())
        file.appendText(encoded, Charsets.UTF_8)
        writeState()
        return true
    }

    @Synchronized
    fun readEvents(): List<DiagnosticEvent> =
        eventFiles().flatMap { file ->
            file.readLines(Charsets.UTF_8).mapNotNull { line -> decodeLine(line).getOrNull() }
        }

    @Synchronized
    fun stats(): StoreStats {
        val files = eventFiles()
        val corruptedLines =
            files.sumOf { file ->
                file.readLines(Charsets.UTF_8).count { line -> decodeLine(line).isFailure }
            }
        val totalEvents =
            files.sumOf { file ->
                file.readLines(Charsets.UTF_8).count { line -> decodeLine(line).isSuccess }
            }
        val bytes = files.sumOf { it.length() }
        return StoreStats(
            totalEvents = totalEvents.toLong(),
            droppedEvents = droppedEvents.get(),
            corruptedLines = corruptedLines.toLong(),
            files = files.size,
            bytes = bytes,
        )
    }

    private fun writableFile(nextBytes: Long): File {
        val current = currentFile()
        return if (!current.exists() || current.length() + nextBytes <= maxFileBytes) {
            current
        } else {
            File(sessionDir, fileName(eventFiles().size))
        }
    }

    private fun currentFile(): File {
        val files = eventFiles()
        return files.lastOrNull() ?: File(sessionDir, fileName(0))
    }

    private fun eventFiles(): List<File> =
        sessionDir
            .listFiles { file -> file.isFile && file.name.matches(EVENT_FILE_REGEX) }
            .orEmpty()
            .sortedBy { it.name }

    private fun fileName(index: Int): String =
        "${filePrefix}_${index.toString().padStart(FILE_INDEX_WIDTH, '0')}.jsonl"

    private fun decodeLine(line: String): Result<DiagnosticEvent> =
        runCatching { DiagnosticJson.decodeFromString(DiagnosticEvent.serializer(), line) }
            .recoverCatching { throwable ->
                when (throwable) {
                    is IllegalArgumentException,
                    is SerializationException -> throw throwable
                    else -> throw throwable
                }
            }

    private fun writeState() {
        File(sessionDir, "store_state.json")
            .writeText("""{"droppedEvents":${droppedEvents.get()}}""", Charsets.UTF_8)
    }

    private companion object {
        private const val DEFAULT_MAX_FILE_BYTES = 2L * 1024L * 1024L
        private const val FILE_INDEX_WIDTH = 3
        private val EVENT_FILE_REGEX = Regex("""app_events_\d{3}\.jsonl""")
    }
}
