package de.dogeapp.data

object Hex {
    /** Parses "01 A2 ff", "01a2ff" or "0x01,0xA2" into bytes. Returns null when the input is invalid. */
    fun parse(input: String): ByteArray? {
        val clean = input
            .replace("0x", "", ignoreCase = true)
            .filter { !it.isWhitespace() && it != ',' && it != ':' && it != '-' }
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        if (!clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun format(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
