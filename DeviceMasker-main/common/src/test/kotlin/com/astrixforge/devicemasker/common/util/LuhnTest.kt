package com.astrixforge.devicemasker.common.util

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LuhnTest {

    @Test
    fun `known IMEI validates`() {
        assertEquals(8, Luhn.calculateCheckDigit("49015420323751"))
        assertTrue(Luhn.isValid("490154203237518"))
        assertFalse(Luhn.isValid("490154203237519"))
    }

    @Test
    fun `appended check digit validates`() {
        val generated = Luhn.appendCheckDigit("899110120000320451")

        assertTrue(Luhn.isValid(generated))
    }
}
