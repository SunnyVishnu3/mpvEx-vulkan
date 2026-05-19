package com.astrixforge.devicemasker.ui.components

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIconCacheTest {

    @Test
    fun `getIcon caches package manager result`() = runTest {
        val packageManager = mockk<PackageManager>()
        var loadCalls = 0
        every { packageManager.getApplicationIcon("com.example.app") } answers
            {
                loadCalls += 1
                ColorDrawable(Color.RED)
            }
        val cache = AppIconCache(packageManager, maxEntries = 4)

        assertNotNull(cache.getIcon("com.example.app"))
        assertNotNull(cache.getIcon("com.example.app"))

        assertEquals(1, loadCalls)
    }

    @Test
    fun `getIcon returns null when package manager cannot load icon`() = runTest {
        val packageManager = mockk<PackageManager>()
        every { packageManager.getApplicationIcon("com.missing") } throws
            PackageManager.NameNotFoundException()
        val cache = AppIconCache(packageManager, maxEntries = 4)

        assertNull(cache.getIcon("com.missing"))
    }
}
