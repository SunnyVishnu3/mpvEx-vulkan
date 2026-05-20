package com.astrixforge.devicemasker.service

import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.addOrUpdateGroup
import com.astrixforge.devicemasker.common.setAppConfig
import com.astrixforge.devicemasker.data.ConfigSync
import com.astrixforge.devicemasker.data.clearAppAsync
import com.astrixforge.devicemasker.data.syncAppAsync
import com.astrixforge.devicemasker.testing.FakeSharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigSyncTest {

    @Test
    fun `null prefs is no-op`() {
        val config = JsonConfig.createDefault()
        ConfigSync.syncFromConfig(config, null)
        // should not crash
    }

    @Test
    fun `commit failure does not apply changes`() {
        val prefs = FakeSharedPreferences(commitResult = false)
        val config = JsonConfig.createDefault().copy(isModuleEnabled = true)

        ConfigSync.syncFromConfig(config, prefs)

        assertFalse(prefs.getBoolean(SharedPrefsKeys.KEY_MODULE_ENABLED, false))
    }

    @Test
    fun `empty config writes module enabled and empty apps`() {
        val prefs = FakeSharedPreferences()
        val config = JsonConfig.createDefault()

        ConfigSync.syncFromConfig(config, prefs)

        assertEquals(true, prefs.getBoolean(SharedPrefsKeys.KEY_MODULE_ENABLED, false))
        assertEquals(emptySet<String>(), prefs.getStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, null))
    }

    @Test
    fun `large config produces many keys`() {
        val prefs = FakeSharedPreferences()
        var config = JsonConfig.createDefault()
        repeat(50) { index ->
            val group = SpoofGroup.createNew(name = "Group-$index")
            config = config.addOrUpdateGroup(group)
            config =
                config.setAppConfig(
                    com.astrixforge.devicemasker.common.AppConfig(
                        packageName = "com.app.$index",
                        groupId = group.id,
                        isEnabled = true,
                    )
                )
        }

        ConfigSync.syncFromConfig(config, prefs)

        val all = prefs.all
        assertTrue(all.size > 200)
        assertNotNull(prefs.getStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, null))
    }

    @Test
    fun `async app sync variants write through shared preferences`() = runTest {
        val prefs = FakeSharedPreferences()
        val group = SpoofGroup.createNew(name = "Async")
        val config =
            JsonConfig.createDefault()
                .addOrUpdateGroup(group)
                .setAppConfig(
                    com.astrixforge.devicemasker.common.AppConfig(
                        packageName = "com.example.target",
                        groupId = group.id,
                        isEnabled = true,
                    )
                )

        syncAppAsync(config, "com.example.target", prefs)

        assertTrue(prefs.getBoolean(SharedPrefsKeys.getAppEnabledKey("com.example.target"), false))
        assertNotNull(
            prefs.getString(SharedPrefsKeys.getPersonaBlobKey("com.example.target"), null)
        )

        clearAppAsync("com.example.target", prefs)

        assertFalse(prefs.getBoolean(SharedPrefsKeys.getAppEnabledKey("com.example.target"), false))
        assertEquals(
            null,
            prefs.getString(SharedPrefsKeys.getPersonaBlobKey("com.example.target"), null),
        )
    }

    @Test
    fun `sync packages updates enabled app allowlist and only requested package keys`() {
        val prefs = FakeSharedPreferences()
        val group = SpoofGroup.createNew("Scoped").copy(id = "group-scoped")
        val config =
            JsonConfig.createDefault()
                .addOrUpdateGroup(group)
                .setAppConfig(
                    com.astrixforge.devicemasker.common.AppConfig(
                        "com.example.one",
                        groupId = group.id,
                        isEnabled = true,
                    )
                )
                .setAppConfig(
                    com.astrixforge.devicemasker.common.AppConfig(
                        "com.example.two",
                        groupId = group.id,
                        isEnabled = true,
                    )
                )

        ConfigSync.syncPackages(config, setOf("com.example.one"), prefs)

        assertEquals(
            setOf("com.example.one", "com.example.two"),
            prefs.getStringSet(SharedPrefsKeys.KEY_ENABLED_APPS, null),
        )
        assertTrue(prefs.getBoolean(SharedPrefsKeys.getAppEnabledKey("com.example.one"), false))
        assertFalse(
            "syncPackages must not write unrelated package app key",
            prefs.contains(SharedPrefsKeys.getAppEnabledKey("com.example.two")),
        )
    }
}
