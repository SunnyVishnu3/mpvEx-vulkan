package com.astrixforge.devicemasker.common

import com.astrixforge.devicemasker.common.models.Country
import com.astrixforge.devicemasker.common.util.Luhn
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class PersonaGeneratorTest {

    @Test
    fun `generate is stable for same group and package`() {
        val group =
            SpoofGroup.createNew(name = "Persona")
                .withValue(SpoofType.DEVICE_PROFILE, DeviceProfilePreset.PRESETS.first().id)
                .withPersona(seed = "seed-123", generatedAt = 1000L)

        val first = PersonaGenerator.generate(group, "com.example.one")
        val second = PersonaGenerator.generate(group, "com.example.one")

        assertEquals(first, second)
        assertTrue(PersonaGenerator.validate(first).isValid)
        assertTrue(Luhn.isValid(first.hardware.primaryImei))
        assertTrue(Luhn.isValid(first.subscriptions.first().iccid))
    }

    @Test
    fun `android id is package scoped while tracking ids remain stable`() {
        val group =
            SpoofGroup.createNew(name = "Persona")
                .withPersona(seed = "seed-android", generatedAt = 1000L)

        val appOne = PersonaGenerator.generate(group, "com.example.one")
        val appTwo = PersonaGenerator.generate(group, "com.example.two")

        assertNotEquals(appOne.tracking.androidId, appTwo.tracking.androidId)
        assertEquals(appOne.tracking.gsfId, appTwo.tracking.gsfId)
        assertEquals(appOne.tracking.advertisingId, appTwo.tracking.advertisingId)
        assertEquals(appOne.tracking.mediaDrmId, appTwo.tracking.mediaDrmId)
    }

    @Test
    fun `shared pref keys include persona keys`() {
        val packageName = "com.example.target"

        assertTrue(isPersonaBlobKey(SharedPrefsKeys.getPersonaBlobKey(packageName)))
        assertTrue(isPersonaVersionKey(SharedPrefsKeys.getPersonaVersionKey(packageName)))
    }

    @Test
    fun `country is serializable for config and UI persistence`() {
        val encoded = Json.encodeToString(Country("US", "United States", "US", "1"))

        assertTrue(encoded.contains("\"iso\":\"US\""))
        assertEquals("United States", Json.decodeFromString<Country>(encoded).name)
    }

    @Test
    fun `shared pref validation accepts generated keys`() {
        val packageName = "com.example.target"

        assertTrue(SharedPrefsKeys.isValidKey(SharedPrefsKeys.KEY_MODULE_ENABLED))
        assertTrue(SharedPrefsKeys.isValidKey(SharedPrefsKeys.getAppEnabledKey(packageName)))
        assertTrue(
            SharedPrefsKeys.isValidKey(
                SharedPrefsKeys.getSpoofEnabledKey(packageName, SpoofType.IMEI)
            )
        )
        assertTrue(
            SharedPrefsKeys.isValidKey(
                SharedPrefsKeys.getSpoofValueKey(packageName, SpoofType.ANDROID_ID)
            )
        )
        assertTrue(SharedPrefsKeys.isValidKey(SharedPrefsKeys.getRiskyHooksEnabledKey(packageName)))
        assertTrue(
            SharedPrefsKeys.isValidKey(SharedPrefsKeys.getClassLookupHidingEnabledKey(packageName))
        )
    }
}
