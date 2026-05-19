package com.astrixforge.devicemasker.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalSheetArchitectureTest {
    @Test
    fun `direct ModalBottomSheet usage is only in reusable sheet wrapper`() {
        val uiSourceRoot = File("src/main/kotlin/com/astrixforge/devicemasker/ui")
        val offenders =
            uiSourceRoot
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .filterNot { file -> file.name == "AppModalBottomSheet.kt" }
                .filter { file -> file.readText().hasDirectModalSheetUsage() }
                .map { file -> file.relativeTo(uiSourceRoot).invariantSeparatorsPath }
                .toList()

        assertTrue("Direct ModalBottomSheet usage found in $offenders", offenders.isEmpty())
    }

    @Test
    fun `group spoofing pickers use modal sheets instead of dialogs`() {
        val uiSourceRoot = File("src/main/kotlin/com/astrixforge/devicemasker/ui")
        val checkedRoots =
            listOf(
                uiSourceRoot.resolve("screens/groupspoofing"),
                uiSourceRoot.resolve("components/sheet"),
            )
        val offenders =
            checkedRoots
                .asSequence()
                .flatMap { root -> root.walkTopDown().asSequence() }
                .filter { file -> file.isFile && file.extension == "kt" }
                .filter { file -> file.readText().hasSpoofingDialogUsage() }
                .map { file -> file.relativeTo(uiSourceRoot).invariantSeparatorsPath }
                .toList()

        assertTrue("Group spoofing picker dialog usage found in $offenders", offenders.isEmpty())
    }
}

private fun String.hasDirectModalSheetUsage(): Boolean =
    contains("import androidx.compose.material3.ModalBottomSheet") ||
        contains(Regex("""(?<![A-Za-z0-9_])ModalBottomSheet\("""))

private fun String.hasSpoofingDialogUsage(): Boolean =
    contains("import androidx.compose.material3.AlertDialog") ||
        contains("CountryPickerDialog") ||
        contains("TimezonePickerDialog")
