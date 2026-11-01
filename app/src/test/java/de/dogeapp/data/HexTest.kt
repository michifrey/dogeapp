package de.dogeapp.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexTest {
    @Test
    fun parsesCommonFormats() {
        val expected = byteArrayOf(0x01, 0xA2.toByte(), 0xFF.toByte())
        assertArrayEquals(expected, Hex.parse("01 A2 ff"))
        assertArrayEquals(expected, Hex.parse("01a2ff"))
        assertArrayEquals(expected, Hex.parse("0x01, 0xA2, 0xFF"))
        assertArrayEquals(expected, Hex.parse("01:a2:ff"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(Hex.parse(""))
        assertNull(Hex.parse("abc"))
        assertNull(Hex.parse("zz"))
    }

    @Test
    fun formatsUpperCase() {
        assertEquals("00 7F 80 FF", Hex.format(byteArrayOf(0, 0x7F, 0x80.toByte(), 0xFF.toByte())))
    }
}
