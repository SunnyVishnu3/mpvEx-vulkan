package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XposedEntryScopeTest {

    @Test
    fun `hook package gate rejects stale app enabled key without current allowlist membership`() {
        val packageName = "com.example.app"
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.KEY_MODULE_ENABLED to true,
                    SharedPrefsKeys.KEY_ENABLED_APPS to emptySet<String>(),
                    SharedPrefsKeys.getAppEnabledKey(packageName) to true,
                )
            )

        assertFalse(XposedEntry.isPackageCurrentlyEnabledForHooks(packageName, prefs))
    }

    @Test
    fun `hook package gate accepts package only when module allowlist and app key are enabled`() {
        val packageName = "com.example.app"
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.KEY_MODULE_ENABLED to true,
                    SharedPrefsKeys.KEY_ENABLED_APPS to setOf(packageName),
                    SharedPrefsKeys.getAppEnabledKey(packageName) to true,
                )
            )

        assertTrue(XposedEntry.isPackageCurrentlyEnabledForHooks(packageName, prefs))
    }

    @Test
    fun `hook package gate rejects missing module enabled key`() {
        val packageName = "com.example.app"
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.KEY_ENABLED_APPS to setOf(packageName),
                    SharedPrefsKeys.getAppEnabledKey(packageName) to true,
                )
            )

        assertFalse(XposedEntry.isPackageCurrentlyEnabledForHooks(packageName, prefs))
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
