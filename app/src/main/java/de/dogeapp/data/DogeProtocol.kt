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
    const val CMD_SET_LED = 0x06
    const val CMD_LAUNCH_GAME = 0x08
    const val CMD_TAIL = 0x09
    const val CMD_MOTOR = 0x0A
    const val CMD_STOP_SEQUENCE = 0x0F
    const val CMD_SLEEP = 0x10
    const val CMD_FIRMWARE = 0x11
    const val CMD_BATTERY = 0x0D
    const val CMD_VOLUME = 0x0E

    /** Volume range used by the app (the official app's slider starts at 9). */
    const val VOLUME_MAX = 10

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

    /**
     * Sets all six LEDs at once. Colors are 0xRRGGBB. [deciseconds] is how long the colors stay
     * (the official app uses 36000 = 1 hour). Byte order in the packet (from SendSetLed):
     * right eye, left eye, chest, right foot, left foot, tail – each R, G, B – then the duration LE16.
     */
    fun setLeds(leds: Map<LedTarget, Int>, deciseconds: Int = 36000): ByteArray {
        val order = listOf(
            LedTarget.RIGHT_EYE, LedTarget.LEFT_EYE, LedTarget.CHEST,
            LedTarget.RIGHT_FOOT, LedTarget.LEFT_FOOT, LedTarget.TAIL,
        )
        val payload = order.flatMap { t ->
            val c = leds[t] ?: 0
            listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        } + listOf(deciseconds and 0xFF, (deciseconds shr 8) and 0xFF)
        return packet(CMD_SET_LED, *payload.toIntArray())
    }

    /** Starts one of the games built into the dog (SendLaunchGame). */
    fun launchGame(game: ToyGame) = packet(CMD_LAUNCH_GAME, game.id)

    /** Stops running sequences/sounds (SendStopSequence). */
    fun stopSequences() = packet(CMD_STOP_SEQUENCE, 0)

    /** Puts the dog into low-power sleep (SendSetLowPowerSleep). */
    fun sleep() = packet(CMD_SLEEP, 0)

    /** Asks for the firmware version; the dog answers FF .. 11 … */
    fun requestFirmware() = packet(CMD_FIRMWARE, 0)

    /** Max characters for a tail message (keeps the packet small for one BLE write). */
    const val TAIL_TEXT_MAX = 40

    /**
     * Scrolls [text] over the LED tail (SendPlayTailAnimation(string)):
     * FF <len> 09 00 02 <text in code page 437> <cs>.
     */
    fun tailText(text: String): ByteArray {
        val bytes = toCp437(text.take(TAIL_TEXT_MAX))
        return packet(CMD_TAIL, 0, 2, *bytes.map { it.toInt() and 0xFF }.toIntArray())
    }

    /** Stops the tail animation (SendStopTailAnimation): FF 03 09 02 <cs>. */
    fun stopTail() = packet(CMD_TAIL, 2)

    /** Text → IBM code page 437 (what the official app uses); unknown characters become '?'. */
    fun toCp437(text: String): ByteArray {
        val special = mapOf(
            'Ä' to 0x8E, 'Ö' to 0x99, 'Ü' to 0x9A, 'ä' to 0x84, 'ö' to 0x94, 'ü' to 0x81, 'ß' to 0xE1,
            'é' to 0x82, 'è' to 0x8A, 'à' to 0x85, 'ç' to 0x87, '♥' to 0x03, '❤' to 0x03, '☺' to 0x01,
            '★' to 0x0F, '♪' to 0x0D, '°' to 0xF8,
        )
        return ByteArray(text.length) { i ->
            val c = text[i]
            when {
                c.code in 0x20..0x7E -> c.code.toByte()
                c in special -> special.getValue(c).toByte()
                else -> '?'.code.toByte()
            }
        }
    }

    /** Asks for the battery level; the dog answers FF .. 0D <level> .. */
    fun requestBattery() = packet(CMD_BATTERY, 0)

    fun setVolume(volume: Int) = packet(CMD_VOLUME, volume.coerceIn(0, 255))

    /** Battery level from an answer packet, or null if [data] is something else. */
    fun parseBattery(data: ByteArray): Int? {
        if (data.size < 5 || data[0] != 0xFF.toByte() || data[2].toInt() != CMD_BATTERY) return null
        return data[3].toInt() and 0xFF
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

enum class LedTarget(val label: String) {
    CHEST("Brust"),
    TAIL("Schwanz"),
    LEFT_EYE("Auge links"),
    RIGHT_EYE("Auge rechts"),
    LEFT_FOOT("Fuß links"),
    RIGHT_FOOT("Fuß rechts"),
}

/** Games built into the dog (ToyGame enum of the official app). */
enum class ToyGame(val id: Int, val label: String, val emoji: String, val hint: String) {
    FORTUNE_TELLER(1, "Wahrsager", "🔮", "Stell Dog-E eine Ja/Nein-Frage!"),
    CODE_BREAKER(2, "Code knacken", "🔐", "Errate den geheimen Code."),
    TURBO_PET(3, "Turbo-Streicheln", "⚡", "Streichle so schnell du kannst!"),
    NOSE_BOOP(4, "Nasen-Stups", "👃", "10 Sekunden: drück die Nase so oft wie möglich!"),
    RED_LIGHT_GREEN_LIGHT(5, "Ampel-Spiel", "🚦", "Rotes Licht – stopp! Grünes Licht – los!"),
    BARK_BACK(6, "Bell zurück", "🗣️", "Dog-E bellt vor – mach es nach!"),
    VET(7, "Tierarzt", "🩺", "Hilf Dog-E, wieder gesund zu werden."),
}
