package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.PersonaGenerator
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HookConfigSnapshotTest {

    @Test
    fun `snapshot uses flat configured value before persona fallback`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), packageName)
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.KEY_CONFIG_VERSION to 7L,
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.ANDROID_ID) to true,
                    SharedPrefsKeys.getSpoofValueKey(packageName, SpoofType.ANDROID_ID) to
                        "flat-android-id",
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        val snapshot = HookConfigSnapshot.fromPrefs(prefs, packageName)

        assertEquals(7L, snapshot.version)
        assertEquals("flat-android-id", snapshot.getValue(SpoofType.ANDROID_ID))
    }

    @Test
    fun `snapshot falls back to persona once for enabled missing flat value`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), packageName)
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to true,
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        val snapshot = HookConfigSnapshot.fromPrefs(prefs, packageName)

        assertEquals(persona.hardware.primaryImei, snapshot.getValue(SpoofType.IMEI))
        assertEquals(persona.hardware.primaryImei, snapshot.getValue(SpoofType.IMEI))
    }

    @Test
    fun `snapshot returns null for disabled values`() {
        val packageName = "com.example.app"
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to false,
                    SharedPrefsKeys.getSpoofValueKey(packageName, SpoofType.IMEI) to
                        "490154203237518",
                )
            )

        val snapshot = HookConfigSnapshot.fromPrefs(prefs, packageName)

        assertNull(snapshot.getValue(SpoofType.IMEI))
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
