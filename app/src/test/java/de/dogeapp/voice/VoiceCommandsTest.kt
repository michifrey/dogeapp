package de.dogeapp.voice

import de.dogeapp.data.DogeConfig
import de.dogeapp.data.LedTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {
    private val cmds = DogeConfig.DEFAULT_COMMANDS
    private fun parse(s: String) = VoiceCommands.parse(s, cmds)

    @Test
    fun tricks() {
        assertEquals("fart", (parse("Furz mal!") as VoiceAction.Command).cmd.id)
        assertEquals("fart", (parse("pupsen") as VoiceAction.Command).cmd.id)
        assertEquals("bark", (parse("Bell mal") as VoiceAction.Command).cmd.id)
        assertEquals("wag", (parse("wedel mit dem Schwanz") as VoiceAction.Command).cmd.id)
        assertEquals("sing", (parse("Sing ein Lied") as VoiceAction.Command).cmd.id)
    }

    @Test
    fun movement() {
        assertEquals(VoiceAction.Stop, parse("Halt!"))
        assertTrue(parse("Komm her") is VoiceAction.Drive)
        assertTrue(parse("Kopf") is VoiceAction.Head)
    }

    @Test
    fun lights() {
        val eyes = parse("Augen blau") as VoiceAction.Leds
        assertEquals(setOf(LedTarget.LEFT_EYE, LedTarget.RIGHT_EYE), eyes.targets)
        assertEquals(0x0000FF, eyes.rgb)
        val all = parse("Licht rot") as VoiceAction.Leds
        assertEquals(LedTarget.entries.toSet(), all.targets)
        assertEquals(0x000000, (parse("Licht aus") as VoiceAction.Leds).rgb)
        assertEquals(setOf(LedTarget.TAIL), (parse("Schwanz grün") as VoiceAction.Leds).targets)
        assertEquals(VoiceAction.Rainbow, parse("Regenbogen"))
    }

    @Test
    fun nothing() {
        assertNull(parse("Wie spät ist es heute"))
        // "aus" alone in a long sentence must not switch the lights off
        assertNull(parse("Das ist aus Holz"))
    }
}
