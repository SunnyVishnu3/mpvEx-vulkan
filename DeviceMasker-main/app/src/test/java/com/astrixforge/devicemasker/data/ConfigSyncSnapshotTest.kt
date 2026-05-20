package com.astrixforge.devicemasker.data

import com.astrixforge.devicemasker.common.AppConfig
import com.astrixforge.devicemasker.common.DeviceIdentifier
import com.astrixforge.devicemasker.common.DevicePersona
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.setAppConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSyncSnapshotTest {

    @Test
    fun `snapshot uses app config as canonical scope and honors disabled app`() {
        val group =
            SpoofGroup.createNew("Work")
                .copy(
                    id = "group-work",
                    identifiers =
                        mapOf(
                            SpoofType.IMEI to
                                DeviceIdentifier(
                                    type = SpoofType.IMEI,
                                    value = "490154203237518",
                                    isEnabled = true,
                                )
                        ),
                    assignedApps = setOf("com.legacy.stale"),
                )
        val config =
            JsonConfig(
                groups = mapOf(group.id to group),
                appConfigs =
                    mapOf(
                        "com.example.enabled" to
                            AppConfig("com.example.enabled", groupId = group.id, isEnabled = true),
                        "com.example.disabled" to
                            AppConfig("com.example.disabled", groupId = group.id, isEnabled = false),
                    ),
            )

        val snapshot = ConfigSync.buildSnapshot(config, previousEnabledApps = emptySet())

        assertEquals(setOf("com.example.enabled", "com.example.disabled"), snapshot.currentApps)
        assertEnabledAppSnapshot(snapshot, group.id)
        assertDisabledAppSnapshot(snapshot)
    }

    @Test
    fun `snapshot does not enable app without explicit group assignment`() {
        val group =
            SpoofGroup.createNew("Default")
                .copy(
                    id = "default-group",
                    isDefault = true,
                    identifiers =
                        mapOf(
                            SpoofType.IMEI to
                                DeviceIdentifier(
                                    type = SpoofType.IMEI,
                                    value = "490154203237518",
                                    isEnabled = true,
                                )
                        ),
                )
        val config =
            JsonConfig(
                groups = mapOf(group.id to group),
                appConfigs =
                    mapOf(
                        "com.example.unassigned" to
                            AppConfig("com.example.unassigned", groupId = null, isEnabled = true)
                    ),
            )

        val snapshot = ConfigSync.buildSnapshot(config, previousEnabledApps = emptySet())

        assertFalse(
            snapshot.booleans.getValue(SharedPrefsKeys.getAppEnabledKey("com.example.unassigned"))
        )
        assertNull(
            snapshot.strings[
                    SharedPrefsKeys.getSpoofValueKey("com.example.unassigned", SpoofType.IMEI)]
        )
        assertTrue(
            snapshot.removeKeys.contains(
                SharedPrefsKeys.getPersonaBlobKey("com.example.unassigned")
            )
        )
    }

    @Test
    fun `snapshot disables assigned apps when group is disabled`() {
        val group =
            SpoofGroup.createNew("Disabled")
                .copy(
                    id = "disabled-group",
                    isEnabled = false,
                    identifiers =
                        mapOf(
                            SpoofType.IMEI to
                                DeviceIdentifier(
                                    type = SpoofType.IMEI,
                                    value = "490154203237518",
                                    isEnabled = true,
                                )
                        ),
                )
        val config =
            JsonConfig(
                groups = mapOf(group.id to group),
                appConfigs =
                    mapOf(
                        "com.example.disabledgroup" to
                            AppConfig(
                                "com.example.disabledgroup",
                                groupId = group.id,
                                isEnabled = true,
                            )
                    ),
            )

        val snapshot = ConfigSync.buildSnapshot(config, previousEnabledApps = emptySet())

        assertFalse(
            snapshot.booleans.getValue(
                SharedPrefsKeys.getAppEnabledKey("com.example.disabledgroup")
            )
        )
        assertFalse(
            snapshot.booleans.getValue(
                SharedPrefsKeys.getSpoofEnabledKey("com.example.disabledgroup", SpoofType.IMEI)
            )
        )
        assertNull(
            snapshot.strings[
                    SharedPrefsKeys.getSpoofValueKey("com.example.disabledgroup", SpoofType.IMEI)]
        )
    }

    @Test
    fun `snapshot disables value hook families when related spoof types are disabled`() {
        val group =
            SpoofGroup.createNew("Work")
                .copy(
                    id = "group-work",
                    identifiers =
                        listOf(
                                SpoofType.IMEI,
                                SpoofType.IMSI,
                                SpoofType.ICCID,
                                SpoofType.SIM_COUNTRY_ISO,
                                SpoofType.NETWORK_COUNTRY_ISO,
                                SpoofType.SIM_OPERATOR_NAME,
                                SpoofType.CARRIER_MCC_MNC,
                                SpoofType.NETWORK_OPERATOR,
                                SpoofType.PHONE_NUMBER,
                                SpoofType.SERIAL,
                                SpoofType.ANDROID_ID,
                            )
                            .associateWith { type ->
                                DeviceIdentifier(type = type, value = "disabled", isEnabled = false)
                            },
                )
        val config =
            JsonConfig(
                groups = mapOf(group.id to group),
                appConfigs =
                    mapOf(
                        "com.example.enabled" to
                            AppConfig("com.example.enabled", groupId = group.id, isEnabled = true)
                    ),
            )

        val snapshot = ConfigSync.buildSnapshot(config, previousEnabledApps = emptySet())

        assertFalse(
            snapshot.booleans.getValue(
                SharedPrefsKeys.getHookFamilyEnabledKey("com.example.enabled", "device")
            )
        )
    }

    @Test
    fun `snapshot clears packages removed since the last sync`() {
        val group =
            SpoofGroup.createNew("Work")
                .copy(
                    id = "group-work",
                    identifiers =
                        mapOf(
                            SpoofType.IMEI to
                                DeviceIdentifier(
                                    type = SpoofType.IMEI,
                                    value = "490154203237518",
                                    isEnabled = true,
                                )
                        ),
                )
        val config =
            JsonConfig(
                groups = mapOf(group.id to group),
                appConfigs =
                    mapOf(
                        "com.example.current" to
                            AppConfig("com.example.current", groupId = group.id, isEnabled = true)
                    ),
            )

        val snapshot =
            ConfigSync.buildSnapshot(
                config,
                previousEnabledApps = setOf("com.example.current", "com.example.removed"),
            )

        assertTrue(snapshot.removedApps.contains("com.example.removed"))
        assertTrue(
            snapshot.removeKeys.contains(SharedPrefsKeys.getAppEnabledKey("com.example.removed"))
        )
        assertTrue(
            snapshot.removeKeys.contains(
                SharedPrefsKeys.getSpoofEnabledKey("com.example.removed", SpoofType.IMEI)
            )
        )
        assertTrue(
            snapshot.removeKeys.contains(
                SharedPrefsKeys.getSpoofValueKey("com.example.removed", SpoofType.IMEI)
            )
        )
        assertTrue(
            snapshot.removeKeys.contains(SharedPrefsKeys.getPersonaBlobKey("com.example.removed"))
        )
        assertTrue(
            snapshot.removeKeys.contains(
                SharedPrefsKeys.getPersonaVersionKey("com.example.removed")
            )
        )
    }

    @Test
    fun `sync app path honors canonical app enabled flag`() {
        val syncFile =
            projectFile(
                    "app/src/main/kotlin/com/astrixforge/devicemasker/data/ConfigSyncHelpers.kt"
                )
                .readText()

        assertTrue(
            "ConfigSync.syncApp must include AppConfig.isEnabled, matching full snapshot sync.",
            syncFile.contains("configApp?.isEnabled == true"),
        )
    }

    @Test
    fun `current enabled apps includes all app config packages during dirty sync`() {
        val config =
            JsonConfig.createDefault()
                .setAppConfig(AppConfig("com.example.one", groupId = null, isEnabled = false))
                .setAppConfig(AppConfig("com.example.two", groupId = null, isEnabled = false))

        assertEquals(setOf("com.example.one", "com.example.two"), config.appConfigs.keys)
    }

    private fun projectFile(path: String): File {
        val normalized = path.replace("/", File.separator)
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, normalized) }
            .first { it.exists() }
    }

    private fun assertEnabledAppSnapshot(snapshot: ConfigSync.Snapshot, groupId: String) {
        assertTrue(
            snapshot.booleans.getValue(SharedPrefsKeys.getAppEnabledKey("com.example.enabled"))
        )
        assertEquals(
            "490154203237518",
            snapshot.strings[
                    SharedPrefsKeys.getSpoofValueKey("com.example.enabled", SpoofType.IMEI)],
        )
        val personaJson =
            snapshot.strings.getValue(SharedPrefsKeys.getPersonaBlobKey("com.example.enabled"))
        val persona = DevicePersona.parse(personaJson)
        assertEquals("com.example.enabled", persona.packageName)
        assertEquals(groupId, persona.groupId)
        assertTrue(
            snapshot.longs.containsKey(SharedPrefsKeys.getPersonaVersionKey("com.example.enabled"))
        )
    }

    private fun assertDisabledAppSnapshot(snapshot: ConfigSync.Snapshot) {
        assertFalse(
            snapshot.booleans.getValue(SharedPrefsKeys.getAppEnabledKey("com.example.disabled"))
        )
        assertNull(
            snapshot.strings[
                    SharedPrefsKeys.getSpoofValueKey("com.example.disabled", SpoofType.IMEI)]
        )
        assertTrue(
            snapshot.removeKeys.contains(SharedPrefsKeys.getPersonaBlobKey("com.example.disabled"))
        )
        assertTrue(
            snapshot.removeKeys.contains(
                SharedPrefsKeys.getPersonaVersionKey("com.example.disabled")
            )
        )
    }
}
