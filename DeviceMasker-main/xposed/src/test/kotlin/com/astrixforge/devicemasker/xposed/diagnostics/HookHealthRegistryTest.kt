package com.astrixforge.devicemasker.xposed.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookHealthRegistryTest {
    @Test
    fun `registration counters increment by hooker and method`() {
        val registry = HookHealthRegistry()

        registry.recordRegistrationAttempt("DeviceHooker", "getImei")
        registry.recordRegistrationSuccess("DeviceHooker", "getImei")
        registry.recordRegistrationFailure("NetworkHooker", "getWifiInfo", "NoSuchMethodException")
        registry.recordSkipped("WebViewHooker", "abstractMethod", "abstract")
        registry.recordDeoptimizeFailure("DeviceHooker", "getImei")

        val snapshot = registry.snapshot()
        assertEquals(1, snapshot.registrationAttempts)
        assertEquals(1, snapshot.registrationSuccesses)
        assertEquals(1, snapshot.registrationFailures)
        assertEquals(1, snapshot.skippedMethods)
        assertEquals(1, snapshot.deoptimizeFailures)
        assertTrue(snapshot.methods.containsKey("DeviceHooker.getImei"))
        assertEquals(
            "NoSuchMethodException",
            snapshot.methods.getValue("NetworkHooker.getWifiInfo").lastFailureClass,
        )
    }

    @Test
    fun `spoof events aggregate by package and spoof type`() {
        val registry = HookHealthRegistry()

        registry.recordSpoofEvent("com.bank.example", "IMEI")
        registry.recordSpoofEvent("com.bank.example", "IMEI")
        registry.recordSpoofEvent("com.bank.example", "ANDROID_ID")

        val snapshot = registry.snapshot()
        assertEquals(2, snapshot.spoofEvents.getValue("com.bank.example/IMEI"))
        assertEquals(1, snapshot.spoofEvents.getValue("com.bank.example/ANDROID_ID"))
    }

    @Test
    fun `spoof event logging is first five then powers of ten`() {
        val registry = HookHealthRegistry()
        val loggedCounts = mutableListOf<Long>()

        repeat(1000) {
            val result = registry.recordSpoofEvent("com.bank.example", "IMEI")
            if (result.shouldLog) loggedCounts += result.count
        }

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 10L, 100L, 1000L), loggedCounts)
        assertFalse(registry.recordSpoofEvent("com.bank.example", "IMEI").shouldLog)
    }
}
