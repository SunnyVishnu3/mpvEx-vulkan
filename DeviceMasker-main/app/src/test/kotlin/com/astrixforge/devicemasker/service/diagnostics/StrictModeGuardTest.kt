package com.astrixforge.devicemasker.service.diagnostics

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StrictModeGuardTest {
    @Test
    fun installDoesNotThrowInDebugUnitTests() {
        StrictModeGuard.install()
    }
}
