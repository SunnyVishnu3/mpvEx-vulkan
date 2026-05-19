package com.astrixforge.devicemasker.service

import app.cash.turbine.test
import com.astrixforge.devicemasker.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class ConfigManagerTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        ConfigManager.resetForTests()
        context.filesDir.resolve("config.json").delete()
        context.filesDir
            .listFiles { file -> file.name.startsWith("config.json.corrupted.") }
            ?.forEach { it.delete() }
        Timber.plant(
            object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // swallow logs in tests
                }
            }
        )
    }

    @Test
    fun `double init does not crash`() = runTest {
        ConfigManager.init(context)
        ConfigManager.init(context)

        awaitInitialized()
    }

    @Test
    fun `concurrent modifications are consistent`() = runTest {
        ConfigManager.init(context)
        awaitInitialized()

        val jobs =
            List(20) { index ->
                launch {
                    if (index % 2 == 0) {
                        ConfigManager.setModuleEnabled(index % 4 == 0)
                    } else {
                        ConfigManager.createGroup("Group-$index")
                    }
                }
            }
        jobs.joinAll()

        val groups = ConfigManager.getAllGroups()
        assertEquals(10, groups.size)
        // module enabled state should be whatever the last write was — just assert no crash
    }

    @Test
    fun `corrupted JSON recovery falls back to default`() = runTest {
        val configFile = File(context.filesDir, "config.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText("this is not json { broken")

        ConfigManager.resetForTests()
        ConfigManager.init(context)

        ConfigManager.config.test {
            val config = awaitItem()
            assertTrue(config.groups.isEmpty() || config.groups.containsKey("default"))
        }
    }

    @Test
    fun `corrupted JSON recovery preserves broken file as backup`() = runTest {
        val configFile = File(context.filesDir, "config.json")
        configFile.parentFile?.mkdirs()
        configFile.writeText("this is not json { broken")

        ConfigManager.resetForTests()
        ConfigManager.init(context)

        awaitInitialized()

        val backups =
            context.filesDir
                .listFiles { file -> file.name.startsWith("config.json.corrupted.") }
                .orEmpty()
        assertEquals(1, backups.size)
        assertEquals("this is not json { broken", backups.single().readText())
    }

    @Test
    fun `unassign app preserves standalone home enabled state`() = runTest {
        ConfigManager.init(context)
        awaitInitialized()
        val group = ConfigManager.createGroup("Scoped")

        ConfigManager.assignAppToGroup("com.example.app", group.id)
        ConfigManager.setAppEnabled("com.example.app", false)
        ConfigManager.unassignApp("com.example.app")

        val appConfig = ConfigManager.getAppConfig("com.example.app")
        assertEquals(null, appConfig?.groupId)
        assertEquals(false, appConfig?.isEnabled)
    }

    @Test
    fun `assign app preserves standalone home disabled state`() = runTest {
        ConfigManager.init(context)
        awaitInitialized()
        val group = ConfigManager.createGroup("Scoped")

        ConfigManager.setAppEnabled("com.example.app", false)
        ConfigManager.assignAppToGroup("com.example.app", group.id)

        val appConfig = ConfigManager.getAppConfig("com.example.app")
        assertEquals(group.id, appConfig?.groupId)
        assertEquals(false, appConfig?.isEnabled)
    }

    private suspend fun awaitInitialized() {
        ConfigManager.isInitialized.first { it }
    }
}
