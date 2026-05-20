package com.astrixforge.devicemasker.common.generators

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Unit tests for SerialGenerator.
 *
 * Tests validate:
 * 1. Serial format (alphanumeric)
 * 2. Length constraints (typically 10-16 chars)
 * 3. Manufacturer-specific patterns
 * 4. Character set compliance
 */
class SerialGeneratorTest {

    @Test
    fun `generate returns string within valid length range`() {
        repeat(100) {
            val serial = SerialGenerator.generate()
            assertTrue(
                serial.length in 10..16,
                "Serial should be 10-16 characters, got ${serial.length}: $serial",
            )
        }
    }

    @Test
    fun `generate returns alphanumeric string`() {
        repeat(100) {
            val serial = SerialGenerator.generate()
            assertTrue(
                serial.all { it.isLetterOrDigit() },
                "Serial should be alphanumeric, got: $serial",
            )
        }
    }

    @Test
    fun `generate for Samsung returns correct format`() {
        repeat(50) {
            val serial = SerialGenerator.generate("Samsung")

            // Samsung format: R + 2 digits + year letter + 8 digits = 12 chars
            assertEquals(12, serial.length, "Samsung serial should be 12 chars: $serial")

            // Should start with 'R'
            assertTrue(serial.startsWith("R"), "Samsung serial should start with 'R': $serial")

            // Positions 1-2 should be digits
            assertTrue(
                serial.substring(1, 3).all { it.isDigit() },
                "Samsung serial positions 1-2 should be digits: $serial",
            )

            // Position 3 should be a letter (year indicator)
            assertTrue(serial[3].isLetter(), "Samsung serial position 3 should be letter: $serial")

            // Positions 4-11 should be digits
            assertTrue(
                serial.substring(4).all { it.isDigit() },
                "Samsung serial positions 4-11 should be digits: $serial",
            )
        }
    }

    @Test
    fun `generate for Google returns correct format`() {
        repeat(50) {
            val serial = SerialGenerator.generate("Google")

            // Pixel format: 16 hex-like characters
            assertEquals(16, serial.length, "Pixel serial should be 16 chars: $serial")

            // Should be hex-like (uppercase)
            val hexChars = "0123456789ABCDEF"
            assertTrue(serial.all { it in hexChars }, "Pixel serial should be hex: $serial")
        }
    }

    @Test
    fun `generate for Xiaomi returns correct format`() {
        repeat(50) {
            val serial = SerialGenerator.generate("Xiaomi")

            // Xiaomi format: 12-16 alphanumeric
            assertTrue(serial.length in 12..16, "Xiaomi serial should be 12-16 chars: $serial")

            // Should be alphanumeric
            assertTrue(
                serial.all { it.isLetterOrDigit() },
                "Xiaomi serial should be alphanumeric: $serial",
            )
        }
    }

    @Test
    fun `generate for unknown manufacturer returns valid serial`() {
        repeat(50) {
            val serial = SerialGenerator.generate("UnknownBrand")

            // Generic format: 10-14 alphanumeric
            assertTrue(serial.length in 10..14, "Generic serial should be 10-14 chars: $serial")

            assertTrue(
                serial.all { it.isLetterOrDigit() },
                "Generic serial should be alphanumeric: $serial",
            )
        }
    }

    @Test
    fun `generated serials are unique`() {
        val serials = (1..1000).map { SerialGenerator.generate() }.toSet()

        assertTrue(serials.size >= 990, "Expected nearly 1000 unique serials, got ${serials.size}")
    }

    @Test
    fun `Samsung serials are uppercase`() {
        repeat(50) {
            val serial = SerialGenerator.generate("Samsung")
            assertEquals(serial.uppercase(), serial, "Samsung serial should be uppercase: $serial")
        }
    }

    @Test
    fun `Pixel serials are uppercase`() {
        repeat(50) {
            val serial = SerialGenerator.generate("Google")
            assertEquals(serial.uppercase(), serial, "Pixel serial should be uppercase: $serial")
        }
    }
}
