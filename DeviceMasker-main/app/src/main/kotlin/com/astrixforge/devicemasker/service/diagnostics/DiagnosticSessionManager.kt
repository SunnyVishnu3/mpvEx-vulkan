package com.astrixforge.devicemasker.service.diagnostics

import java.io.File
import java.util.UUID

class DiagnosticSessionManager(
    private val filesDir: File,
    private val sessionIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val bootIdProvider: () -> String = {
        "elapsed-${System.currentTimeMillis() / BOOT_BUCKET_MS}"
    },
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
) {
    var currentSessionId: String? = null
        private set

    var currentBootId: String? = null
        private set

    var currentSessionDirectory: File? = null
        private set

    fun startSession(): File {
        val sessionId = sessionIdProvider()
        val bootId = bootIdProvider()
        val sessionDir = File(sessionsRoot(), "session_$sessionId")
        sessionDir.mkdirs()

        currentSessionId = sessionId
        currentBootId = bootId
        currentSessionDirectory = sessionDir
        pruneOldSessions()
        return sessionDir
    }

    fun sessionsRoot(): File = File(filesDir, "logs/sessions")

    private fun pruneOldSessions() {
        val root = sessionsRoot()
        val sessions =
            root
                .listFiles { file -> file.isDirectory && file.name.startsWith("session_") }
                .orEmpty()
                .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })

        sessions.dropLast(maxSessions).forEach { session -> session.deleteRecursively() }
    }

    private companion object {
        private const val DEFAULT_MAX_SESSIONS = 10
        private const val BOOT_BUCKET_MS = 6L * 60L * 60L * 1000L
    }
}
