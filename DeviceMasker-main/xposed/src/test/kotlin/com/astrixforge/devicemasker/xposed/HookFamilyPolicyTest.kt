package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookFamilyPolicyTest {

    @Test
    fun `hook families default to enabled for existing userspace behavior`() {
        val policy = HookFamilyPolicy.fromPrefs(MapSharedPreferences(emptyMap()), "flar2.devcheck")

        assertTrue(policy.antiDetectEnabled)
        assertTrue(policy.deviceEnabled)
        assertTrue(policy.systemEnabled)
        assertTrue(policy.packageManagerEnabled)
    }

    @Test
    fun `hook family key disables only matching family`() {
        val prefs =
            MapSharedPreferences(
                mapOf(SharedPrefsKeys.getHookFamilyEnabledKey("flar2.devcheck", "system") to false)
            )

        val policy = HookFamilyPolicy.fromPrefs(prefs, "flar2.devcheck")

        assertFalse(policy.systemEnabled)
        assertTrue(policy.deviceEnabled)
    }

    private class MapSharedPreferences(private val values: Map<String, Any?>) : SharedPreferences {
        override fun getAll(): Map<String, *> = values

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = error("Not needed")

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }
}
