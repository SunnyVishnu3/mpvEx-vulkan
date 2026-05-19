package com.astrixforge.devicemasker.verifier

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.RandomAccessFile
import org.json.JSONObject

internal object ProcMapsProbe {
    private val suspiciousPatterns =
        listOf("libxposed", "liblspd", "lsposed", "lspd", "devicemasker")

    fun capture(): JSONObject =
        JSONObject()
            .put(
                "javaBufferedReader",
                scanLines { BufferedReader(FileReader(MAPS_PATH)).use { it.readLines() } },
            )
            .put(
                "javaFileInputStream",
                scanText { FileInputStream(MAPS_PATH).use { String(it.readBytes()) } },
            )
            .put("javaRandomAccessFile", scanLines(::readRandomAccessFileLines))

    private fun readRandomAccessFileLines(): List<String> =
        RandomAccessFile(File(MAPS_PATH), "r").use { raf ->
            generateSequence { raf.readLine() }.toList()
        }

    private fun scanLines(reader: () -> List<String>): JSONObject {
        val lines =
            runCatching(reader).getOrElse { error ->
                return JSONObject().put("error", error.asProbeError())
            }
        return JSONObject()
            .put("lineCount", lines.size)
            .put("suspiciousLineCount", lines.count(::isSuspicious))
    }

    private fun scanText(reader: () -> String): JSONObject {
        val text =
            runCatching(reader).getOrElse { error ->
                return JSONObject().put("error", error.asProbeError())
            }
        val lines = text.lines()
        return JSONObject()
            .put("byteCount", text.toByteArray().size)
            .put("lineCount", lines.size)
            .put("suspiciousLineCount", lines.count(::isSuspicious))
    }

    private fun isSuspicious(line: String): Boolean =
        suspiciousPatterns.any { pattern -> line.contains(pattern, ignoreCase = true) }

    private fun Throwable.asProbeError(): String = "${javaClass.simpleName}:${message.orEmpty()}"

    private const val MAPS_PATH = "/proc/self/maps"
}
