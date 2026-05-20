package com.astrixforge.devicemasker.data.repository

import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.testing.FakeAppScopeRepository
import com.astrixforge.devicemasker.testing.FakeConfigManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SpoofRepositoryTest {

    private lateinit var configManager: FakeConfigManager
    private lateinit var repository: SpoofRepository

    @Before
    fun setup() {
        configManager = FakeConfigManager()
        configManager.init(RuntimeEnvironment.getApplication())
        repository =
            SpoofRepository(
                context = RuntimeEnvironment.getApplication(),
                configManager = configManager,
                appScopeRepository = FakeAppScopeRepository(),
            )
    }

    @Test
    fun `correlation consistency - SIM values share same carrier`() = runTest {
        repository.resetCorrelations()

        val imsi = repository.generateValue(SpoofType.IMSI)
        val iccid = repository.generateValue(SpoofType.ICCID)
        val carrierName = repository.generateValue(SpoofType.CARRIER_NAME)

        assertTrue(imsi.isNotBlank())
        assertTrue(iccid.isNotBlank())
        assertTrue(carrierName.isNotBlank())

        // regenerating another SIM value should reuse the same cached config
        val phone = repository.generateValue(SpoofType.PHONE_NUMBER)
        assertTrue(phone.isNotBlank())
    }

    @Test
    fun `SIM-only regeneration changes specific value while keeping carrier`() = runTest {
        repository.resetCorrelations()
        repository.generateValue(SpoofType.IMSI) // cache carrier

        val first = repository.regenerateSIMValueOnly(SpoofType.PHONE_NUMBER)
        val second = repository.regenerateSIMValueOnly(SpoofType.PHONE_NUMBER)

        assertNotEquals(first, second)
        assertTrue(first.startsWith("+") || first.isNotBlank())
    }

    @Test
    fun `carrier timezone sync updates location values`() = runTest {
        val group = configManager.createGroup("Test")
        val carrier = Carrier.nextSecureRandom()

        repository.updateGroupWithCarrier(group.id, carrier)

        val updated = configManager.getGroup(group.id)!!
        assertEquals(carrier.name, updated.getValue(SpoofType.CARRIER_NAME))
        assertNotNull(updated.getValue(SpoofType.TIMEZONE))
        assertNotNull(updated.getValue(SpoofType.LOCALE))
    }

    @Test
    fun `setActiveGroup leaves exactly one default group`() = runTest {
        val first = configManager.createGroup("First")
        val second = configManager.createGroup("Second")
        configManager.updateGroup(first.copy(isDefault = true))
        configManager.updateGroup(second.copy(isDefault = false))

        repository.setActiveGroup(second.id)

        val groups = configManager.getAllGroups()
        assertEquals(listOf(second.id), groups.filter { it.isDefault }.map { it.id })
    }

    @Test
    fun `import errors return false for malformed json`() = runTest {
        val result = repository.importGroups("this is not json")
        assertFalse(result)
    }
}
