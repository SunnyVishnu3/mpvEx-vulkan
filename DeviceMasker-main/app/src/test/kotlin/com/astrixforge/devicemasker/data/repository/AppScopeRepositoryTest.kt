package com.astrixforge.devicemasker.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.astrixforge.devicemasker.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
class AppScopeRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var shadowPackageManager: ShadowPackageManager

    @Before
    fun setup() {
        shadowPackageManager = org.robolectric.Shadows.shadowOf(context.packageManager)
    }

    @Test
    fun `PackageManager failure handled gracefully`() = runTest {
        val fakePm = mockk<PackageManager>()
        every { fakePm.getInstalledApplications(any<Int>()) } throws RuntimeException("boom")

        val repo = AppScopeRepository(context, fakePm)

        var threw = false
        try {
            repo.loadApps()
        } catch (_: RuntimeException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `cache invalidation reloads apps`() = runTest {
        val repo = AppScopeRepository(context)

        repo.loadApps()
        assertFalse(repo.isLoading.value)
        val first = repo.installedApps.value

        repo.invalidateCache()
        repo.loadApps()
        val second = repo.installedApps.value
        assertEquals(first.size, second.size)
    }

    @Test
    fun `system app filtering excludes system apps`() = runTest {
        val repo = AppScopeRepository(context)

        repo.loadApps()
        val all = repo.getInstalledApps(includeSystem = true)
        val nonSystem = repo.getInstalledApps(includeSystem = false)

        assertTrue(all.size >= nonSystem.size)
        assertTrue(nonSystem.none { it.isSystemApp })
    }

    @Test
    fun `search filters apps correctly`() = runTest {
        val repo = AppScopeRepository(context)
        repo.loadApps()

        val apps = repo.installedApps.value
        if (apps.isEmpty()) return@runTest

        val query = apps.first().label.take(2)
        val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }

        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.all { it.label.contains(query, ignoreCase = true) })
    }

    @Test
    fun `loadScopedApps resolves only requested packages`() = runTest {
        val packageManager = mockk<PackageManager>()
        val scopedInfo = ApplicationInfo().apply { packageName = "com.scoped.one" }
        var getInstalledApplicationsCalls = 0

        every { packageManager.getApplicationInfo("com.scoped.one", 0) } returns scopedInfo
        every { packageManager.getApplicationLabel(scopedInfo) } returns "Scoped One"
        every { packageManager.getInstalledApplications(any<Int>()) } answers
            {
                getInstalledApplicationsCalls += 1
                emptyList()
            }

        val repository = AppScopeRepository(context, packageManager)

        repository.loadScopedApps(setOf("com.scoped.one"), forceRefresh = true)

        assertEquals(setOf("com.scoped.one"), repository.scopedAppMetadata.value.keys)
        assertEquals(
            "Scoped One",
            repository.scopedAppMetadata.value.getValue("com.scoped.one").label,
        )
        assertEquals(0, getInstalledApplicationsCalls)
    }
}
