package de.dogeapp.data

/**
 * Dog-E BLE protocol, recovered from the official app (com.wowwee.doge_robot 1.1.4, Unity/IL2CPP).
 * See docs/PROTOKOLL.md for how it was found.
 *
 * Every packet is:  FF <len> <cmd> <payload...> <checksum>
 *  - len      = number of bytes after the len byte (cmd + payload + checksum)
 *  - checksum = sum of all bytes from len up to the last payload byte, truncated to 8 bit
 *
 * Packets are written without response to characteristic FFE9 in service FFE5.
 * The dog answers with notifications on characteristic FFE4 in service FFE0.
 */
object DogeProtocol {
    const val WRITE_SERVICE = "0000ffe5-0000-1000-8000-00805f9b34fb"
    const val WRITE_CHAR = "0000ffe9-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_CHAR = "0000ffe4-0000-1000-8000-00805f9b34fb"

    /** Name the dog advertises with. */
    const val DEVICE_NAME = "Mint Dog"

    const val CMD_AUTH = 0x01
    const val CMD_PLAY_SEQUENCE = 0x05
    const val CMD_MOTOR = 0x0A

    const val MOTOR_DRIVE = 1
    const val MOTOR_HEAD = 2

    /** Sequence IDs used by the official app's "Command Mode" buttons. */
    const val SEQ_BARK = 0x6A
    const val SEQ_HOWL = 0x72
    const val SEQ_SING = 0x73
    const val SEQ_FART = 0x74
    const val SEQ_WAG_TAIL = 0xA8

    fun packet(cmd: Int, vararg payload: Int): ByteArray {
        val body = intArrayOf(cmd, *payload)
        val out = ByteArray(body.size + 3)
        out[0] = 0xFF.toByte()
        out[1] = (body.size + 1).toByte()
        body.forEachIndexed { i, b -> out[2 + i] = b.toByte() }
        var sum = 0
        for (i in 1 until out.size - 1) sum += out[i].toInt() and 0xFF
        out[out.size - 1] = sum.toByte()
        return out
    }

    fun stopMotors() = packet(CMD_MOTOR, 0)

    /** Same bytes the official app sends: drive slider = (1, 1), head button = (2, 2). */
    fun motor(motor: Int, speed: Int) = packet(CMD_MOTOR, motor, 1, speed)

    fun playSequence(vararg ids: Int): ByteArray {
        val payload = mutableListOf(1, ids.size)
        ids.forEach { payload += it and 0xFF; payload += (it shr 8) and 0xFF }
        return packet(CMD_PLAY_SEQUENCE, *payload.toIntArray())
    }

    /** "Am I already paired?" – sent right after connecting, with a 6-byte ID identifying this phone. */
    fun authCheck(phoneId: ByteArray): ByteArray =
        packet(CMD_AUTH, 0, *phoneId.map { it.toInt() and 0xFF }.toIntArray())

    /** Ask the dog to pair with this phone (sent when the check says "not paired"). */
    fun pairingRequest() = packet(CMD_AUTH, 1)

    /** Result of an incoming auth/pairing packet, or null if [data] is something else. */
    sealed interface AuthReply {
        data class Status(val authenticated: Boolean) : AuthReply
        data class Pairing(val code: Int) : AuthReply
    }

    fun parseAuthReply(data: ByteArray): AuthReply? {
        if (data.size < 5 || data[0] != 0xFF.toByte() || data[2].toInt() != CMD_AUTH) return null
        val type = data[3].toInt() and 0xFF
        val value = data[4].toInt() and 0xFF
        return if (type == 1 && (value == 4 || value == 5)) AuthReply.Pairing(value)
        else AuthReply.Status(authenticated = value == 1)
    }
}
