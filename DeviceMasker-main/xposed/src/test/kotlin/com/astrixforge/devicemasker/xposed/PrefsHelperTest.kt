package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.PersonaGenerator
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefsHelperTest {

    @Test
    fun `storedSpoofValue returns null when type is disabled`() {
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey("com.example.app", SpoofType.IMEI) to false,
                    SharedPrefsKeys.getSpoofValueKey("com.example.app", SpoofType.IMEI) to
                        "490154203237518",
                )
            )

        assertNull(PrefsHelper.getStoredSpoofValue(prefs, "com.example.app", SpoofType.IMEI))
    }

    @Test
    fun `storedSpoofValue returns null when enabled value is blank`() {
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey("com.example.app", SpoofType.IMEI) to true,
                    SharedPrefsKeys.getSpoofValueKey("com.example.app", SpoofType.IMEI) to "",
                )
            )

        assertNull(PrefsHelper.getStoredSpoofValue(prefs, "com.example.app", SpoofType.IMEI))
    }

    @Test
    fun `storedSpoofValue returns configured value when enabled`() {
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey("com.example.app", SpoofType.IMEI) to true,
                    SharedPrefsKeys.getSpoofValueKey("com.example.app", SpoofType.IMEI) to
                        "490154203237518",
                )
            )

        assertEquals(
            "490154203237518",
            PrefsHelper.getStoredSpoofValue(prefs, "com.example.app", SpoofType.IMEI),
        )
    }

    @Test
    fun `storedSpoofValue falls back to matching persona when flat value is blank`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), packageName)
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to true,
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        assertEquals(
            persona.hardware.primaryImei,
            PrefsHelper.getStoredSpoofValue(prefs, packageName, SpoofType.IMEI),
        )
    }

    @Test
    fun `storedSpoofValue ignores persona when type is disabled`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), packageName)
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to false,
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        assertNull(PrefsHelper.getStoredSpoofValue(prefs, packageName, SpoofType.IMEI))
    }

    @Test
    fun `storedSpoofValue ignores persona for different package`() {
        val packageName = "com.example.app"
        val persona = PersonaGenerator.generate(SpoofGroup.createNew("Persona"), "com.other.app")
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI) to true,
                    SharedPrefsKeys.getPersonaBlobKey(packageName) to persona.toJsonString(),
                )
            )

        assertNull(PrefsHelper.getStoredSpoofValue(prefs, packageName, SpoofType.IMEI))
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
