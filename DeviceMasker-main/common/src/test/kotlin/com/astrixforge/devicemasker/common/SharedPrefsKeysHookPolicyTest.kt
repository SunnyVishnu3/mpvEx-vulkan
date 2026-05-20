package com.astrixforge.devicemasker.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPrefsKeysHookPolicyTest {

    @Test
    fun `hook family key is sanitized and valid`() {
        val key = SharedPrefsKeys.getHookFamilyEnabledKey("flar2.devcheck", "system_feature")

        assertEquals("hook_family_enabled_flar2_devcheck_system_feature", key)
        assertTrue(SharedPrefsKeys.isValidKey(key))
    }

    @Test
    fun `java proc maps policy keys are valid`() {
        val packageName = "flar2.devcheck"

        assertTrue(
            SharedPrefsKeys.isValidKey(
                SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName)
            )
        )
        assertTrue(
            SharedPrefsKeys.isValidKey(
                SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(packageName)
            )
        )
    }
}
