package de.dogeapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DogeProtocolTest {
    private fun hex(b: ByteArray) = Hex.format(b)

    @Test
    fun motorPackets() {
        assertEquals("FF 03 0A 00 0D", hex(DogeProtocol.stopMotors()))
        assertEquals("FF 05 0A 01 01 01 12", hex(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, 1)))
        assertEquals("FF 05 0A 02 01 02 14", hex(DogeProtocol.motor(DogeProtocol.MOTOR_HEAD, 2)))
    }

    @Test
    fun sequencePackets() {
        assertEquals("FF 06 05 01 01 6A 00 77", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_BARK)))
        assertEquals("FF 06 05 01 01 74 00 81", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_FART)))
        assertEquals("FF 08 05 01 02 E8 03 6A 00 65", hex(DogeProtocol.playSequence(1000, 0x6A)))
    }

    @Test
    fun authPackets() {
        assertEquals("FF 03 01 01 05", hex(DogeProtocol.pairingRequest()))
        val id = byteArrayOf(1, 2, 3, 4, 5, 6)
        assertEquals("FF 09 01 00 01 02 03 04 05 06 1F", hex(DogeProtocol.authCheck(id)))
    }

    @Test
    fun parsesAuthReplies() {
        assertEquals(
            DogeProtocol.AuthReply.Status(true),
            DogeProtocol.parseAuthReply(Hex.parse("FF 04 01 00 01 06")!!),
        )
        assertEquals(
            DogeProtocol.AuthReply.Pairing(4),
            DogeProtocol.parseAuthReply(Hex.parse("FF 04 01 01 04 0A")!!),
        )
        assertEquals(null, DogeProtocol.parseAuthReply(Hex.parse("FF 03 0A 00 0D")!!))
    }

    @Test
    fun ledPacket() {
        val leds = mapOf(
            LedTarget.CHEST to 0x112233, LedTarget.TAIL to 0x445566,
            LedTarget.LEFT_EYE to 0xFF0000, LedTarget.RIGHT_EYE to 0x00FF00,
            LedTarget.LEFT_FOOT to 0x0000FF, LedTarget.RIGHT_FOOT to 0x010203,
        )
        val p = DogeProtocol.setLeds(leds, deciseconds = 36000)
        assertEquals(24, p.size)
        assertEquals(
            "FF 16 06 00 FF 00 FF 00 00 11 22 33 01 02 03 00 00 FF 44 55 66 A0 8C B0",
            hex(p),
        )
    }
}
