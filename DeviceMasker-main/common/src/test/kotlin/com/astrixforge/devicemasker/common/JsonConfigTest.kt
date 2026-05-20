package com.astrixforge.devicemasker.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class JsonConfigTest {

    @Test
    fun `parseCatching returns failure for invalid json`() {
        val result = JsonConfig.parseCatching("{ invalid json }")

        assertTrue(result.isFailure)
    }

    @Test
    fun `parseOrDefault reports failure before returning default config`() {
        var failureCount = 0

        val config = JsonConfig.parseOrDefault("{ invalid json }") { failureCount += 1 }

        assertEquals(1, failureCount)
        assertTrue(config.groups.isEmpty())
        assertTrue(config.appConfigs.isEmpty())
    }

    @Test
    fun `parseCatching succeeds for valid json`() {
        val config = JsonConfig(isModuleEnabled = false)

        val result = JsonConfig.parseCatching(config.toJsonString())

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().isModuleEnabled)
    }

    @Test
    fun `legacy assigned apps derive app configs when canonical app configs are empty`() {
        val group =
            SpoofGroup(id = "group-1", name = "Group", assignedApps = setOf("com.example.app"))
        val config = JsonConfig(groups = mapOf(group.id to group))

        val migrated = config.withDerivedAppConfigsFromAssignedApps()

        assertEquals(
            AppConfig(packageName = "com.example.app", groupId = "group-1"),
            migrated.appConfigs["com.example.app"],
        )
    }

    @Test
    fun `existing app configs stay canonical during legacy assignment migration`() {
        val group =
            SpoofGroup(id = "group-1", name = "Group", assignedApps = setOf("com.legacy.stale"))
        val appConfig =
            AppConfig(packageName = "com.example.current", groupId = "group-1", isEnabled = false)
        val config =
            JsonConfig(
                groups = mapOf(group.id to group),
                appConfigs = mapOf(appConfig.packageName to appConfig),
            )

        val migrated = config.withDerivedAppConfigsFromAssignedApps()

        assertEquals(mapOf(appConfig.packageName to appConfig), migrated.appConfigs)
    }

    @Test
    fun `removeGroup removes app configs assigned to that group`() {
        val deletedGroup = SpoofGroup(id = "deleted", name = "Deleted")
        val keptGroup = SpoofGroup(id = "kept", name = "Kept")
        val deletedApp = AppConfig(packageName = "com.example.deleted", groupId = deletedGroup.id)
        val keptApp = AppConfig(packageName = "com.example.kept", groupId = keptGroup.id)
        val config =
            JsonConfig(
                groups = mapOf(deletedGroup.id to deletedGroup, keptGroup.id to keptGroup),
                appConfigs =
                    mapOf(deletedApp.packageName to deletedApp, keptApp.packageName to keptApp),
            )

        val updated = config.removeGroup(deletedGroup.id)

        assertFalse(deletedGroup.id in updated.groups)
        assertEquals(mapOf(keptApp.packageName to keptApp), updated.appConfigs)
    }
}
