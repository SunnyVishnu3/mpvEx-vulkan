package com.astrixforge.devicemasker.service

import com.astrixforge.devicemasker.common.SpoofGroup
import com.astrixforge.devicemasker.common.withPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConfigManagerPersonaTest {

    @Test
    fun personaSeedResolvesAndRotatesOnFullRegenerate() {
        val group =
            SpoofGroup.createNew(name = "Persona Test")
                .withPersona(seed = "seed-a", generatedAt = 1_000L)

        assertEquals("seed-a", ConfigManager.getPersonaSeed(group))
        assertEquals(group.updatedAt, ConfigManager.getPersonaVersion(group))

        val refreshed = ConfigManager.refreshPersonaLifecycle(group)

        assertNotEquals(group.resolvedPersonaSeed(), refreshed.resolvedPersonaSeed())
        assertNotEquals(group.personaGeneratedAt, refreshed.personaGeneratedAt)
        assertEquals(group.id, refreshed.id)
        assertEquals(group.name, refreshed.name)
    }
}
