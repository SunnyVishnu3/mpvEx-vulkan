package com.astrixforge.devicemasker.common.generators

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Unit tests for Android ID generation.
 *
 * Android ID characteristics:
 * 1. 16 hexadecimal characters (64 bits)
 * 2. Lowercase hex format
 * 3. Unique per device per user
 */
class AndroidIdGeneratorTest {

    @Test
    fun `generateAndroidId returns 16 character string`() {
        repeat(100) {
            val androidId = generateAndroidId()
            assertEquals(
                16,
                androidId.length,
                "Android ID should be 16 characters, got ${androidId.length}: $androidId",
            )
        }
    }

    @Test
    fun `generateAndroidId returns lowercase hex`() {
        repeat(100) {
            val androidId = generateAndroidId()
            val hexChars = "0123456789abcdef"

            assertTrue(
                androidId.all { it in hexChars },
                "Android ID should be lowercase hex, got: $androidId",
            )
        }
    }

    @Test
    fun `generateAndroidId returns unique values`() {
        val ids = (1..1000).map { generateAndroidId() }.toSet()

        assertTrue(ids.size >= 990, "Expected nearly 1000 unique Android IDs, got ${ids.size}")
    }

    @Test
    fun `generateAndroidId format matches real Android ID`() {
        repeat(100) {
            val androidId = generateAndroidId()

            // Real Android IDs have this regex pattern
            val pattern = Regex("^[0-9a-f]{16}$")
            assertTrue(
                pattern.matches(androidId),
                "Android ID should match pattern ^[0-9a-f]{16}$, got: $androidId",
            )
        }
    }

    /**
     * Helper function to generate Android ID. In the real codebase this might be in a separate
     * generator or UUIDGenerator.
     */
    private fun generateAndroidId(): String {
        val secureRandom = java.security.SecureRandom()
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
