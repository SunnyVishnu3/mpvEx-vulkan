package com.astrixforge.devicemasker.xposed.hooker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileHookCoverageTest {

    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: error("user.dir is not set"))) {
                file ->
                file.parentFile
            }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `device profile hookers cover enriched build and feature surfaces`() {
        val systemHooker = source("SystemHooker.kt")
        val featureHooker = source("SystemFeatureHooker.kt")
        val entry = source("../XposedEntry.kt")

        assertTrue(systemHooker.contains("\"ID\" to preset.buildId"))
        assertTrue(systemHooker.contains("\"TIME\" to preset.buildTime"))
        assertTrue(systemHooker.contains("\"SUPPORTED_ABIS\""))
        assertTrue(systemHooker.contains("\"INCREMENTAL\" to preset.incremental"))
        assertTrue(systemHooker.contains("\"SECURITY_PATCH\" to preset.securityPatch"))
        assertTrue(systemHooker.contains("ro.product.cpu.abilist"))
        assertTrue(systemHooker.contains("SystemProperties.getLong"))
        assertTrue(featureHooker.contains("PackageManager.hasSystemFeature"))
        assertTrue(featureHooker.contains("android.hardware.nfc"))
        assertTrue(featureHooker.contains("android.hardware.telephony.subscription"))
        assertTrue(entry.contains("SystemFeatureHooker.hook"))
    }

    @Test
    fun `sim and subscription count hooks are not no-op placeholders`() {
        val deviceHooker = source("DeviceHooker.kt")
        val subscriptionHooker = source("SubscriptionHooker.kt")

        assertTrue(deviceHooker.contains("getSimCount"))
        assertTrue(deviceHooker.contains("getPhoneCount"))
        assertTrue(deviceHooker.contains("getActiveModemCount"))
        assertTrue(subscriptionHooker.contains("getActiveSubscriptionInfoCount"))
        assertTrue(subscriptionHooker.contains("getActiveSubscriptionInfoCountMax"))
        assertFalse(
            subscriptionHooker.contains(
                "xi.hook(m).intercept(stableHooker { chain -> chain.proceed() })"
            )
        )
    }

    @Test
    fun `webview hooker discovers concrete web settings implementation`() {
        val webViewHooker = source("WebViewHooker.kt")

        assertTrue(webViewHooker.contains("\"WebView.getSettings()\""))
        assertTrue(webViewHooker.contains("hookConcreteWebSettings"))
        assertTrue(webViewHooker.contains("hookedSettingsClasses.add"))
        assertTrue(webViewHooker.contains("webSettingsMethodOrNull"))
        assertTrue(webViewHooker.contains("getMethod(name, *params)"))
        assertTrue(webViewHooker.contains("\"getUserAgentString\""))
        assertTrue(webViewHooker.contains("\"setUserAgentString\""))
    }

    private fun source(relativePath: String): String =
        File(
                repoRoot,
                "xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/hooker/$relativePath",
            )
            .readText()
}
