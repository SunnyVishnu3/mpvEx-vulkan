package com.astrixforge.devicemasker.xposed

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class StrictModeIsolationTest {
    @Test
    fun xposedRuntimeSourceDoesNotInstallStrictMode() {
        val matches =
            File("src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains("StrictMode") }
                .map { it.invariantSeparatorsPath }
                .toList()

        assertFalse(
            "StrictMode must stay app-process only, but was found in: $matches",
            matches.isNotEmpty(),
        )
    }
}
