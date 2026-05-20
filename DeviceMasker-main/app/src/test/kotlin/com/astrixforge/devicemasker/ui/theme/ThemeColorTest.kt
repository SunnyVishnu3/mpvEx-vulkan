package com.astrixforge.devicemasker.ui.theme

import androidx.compose.material3.darkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorTest {
    @Test
    fun amoledCopyOverridesEverySurfaceContainer() {
        val scheme = darkColorScheme().withAmoledSurfaces()

        assertEquals(AmoledBlack, scheme.background)
        assertEquals(AmoledBlack, scheme.surface)
        assertEquals(AmoledBlack, scheme.surfaceContainerLowest)
        assertEquals(AmoledSurface, scheme.surfaceContainerLow)
        assertEquals(AmoledSurfaceContainer, scheme.surfaceContainer)
        assertEquals(AmoledSurfaceContainerHigh, scheme.surfaceContainerHigh)
        assertEquals(AmoledSurfaceContainerHighest, scheme.surfaceContainerHighest)
    }

    @Test
    fun statusColorsUseSchemeRoles() {
        val scheme = darkColorScheme()

        assertEquals(scheme.primary, scheme.statusActive)
        assertEquals(scheme.error, scheme.statusInactive)
        assertEquals(scheme.tertiary, scheme.statusWarning)
    }
}
