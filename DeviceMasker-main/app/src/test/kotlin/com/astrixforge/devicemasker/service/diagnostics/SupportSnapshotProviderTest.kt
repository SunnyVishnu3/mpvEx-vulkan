package com.astrixforge.devicemasker.service.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SupportSnapshotProviderTest {
    @Test
    fun `snapshot provider emits real config and scope snapshot names`() {
        val context = RuntimeEnvironment.getApplication()

        val snapshots = DefaultSupportSnapshotProvider(context).buildSnapshots()

        assertTrue(snapshots.containsKey("config_snapshot_redacted.json"))
        assertTrue(snapshots.containsKey("remote_prefs_snapshot_redacted.json"))
        assertTrue(snapshots.containsKey("scope_snapshot.json"))
        assertFalse(snapshots.getValue("config_snapshot_redacted.json").trim() == "{}")
    }
}
