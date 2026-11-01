package de.dogeapp.voice

import de.dogeapp.data.CommandDef
import de.dogeapp.data.LedTarget

/** What a spoken phrase should make the dog do. */
sealed interface VoiceAction {
    val description: String

    data object Stop : VoiceAction { override val description = "Stopp" }
    data class Drive(val ms: Long) : VoiceAction { override val description = "Fahren" }
    data class Head(val ms: Long) : VoiceAction { override val description = "Kopf bewegen" }
    data class Command(val cmd: CommandDef) : VoiceAction { override val description = cmd.label }
    data class Leds(val targets: Set<LedTarget>, val rgb: Int, val name: String) : VoiceAction {
        override val description = "Licht $name"
    }
    data object Rainbow : VoiceAction { override val description = "Regenbogen-Licht" }
}

/**
 * Turns German phrases like "Bell mal!", "Augen blau", "Licht aus" or "Komm her" into actions.
 * Matching is by word stems, so "bellen", "bell", "belle" all work.
 */
object VoiceCommands {

    val COLORS: List<Triple<List<String>, Int, String>> = listOf(
        Triple(listOf("rot"), 0xFF0000, "rot"),
        Triple(listOf("grün", "gruen"), 0x00FF00, "grün"),
        Triple(listOf("blau"), 0x0000FF, "blau"),
        Triple(listOf("gelb"), 0xFFD000, "gelb"),
        Triple(listOf("orange"), 0xFF6000, "orange"),
        Triple(listOf("pink", "rosa"), 0xFF1490, "pink"),
        Triple(listOf("lila", "violett"), 0x8000FF, "lila"),
        Triple(listOf("türkis", "tuerkis", "cyan"), 0x00FFD0, "türkis"),
        Triple(listOf("weiß", "weiss"), 0xFFFFFF, "weiß"),
        Triple(listOf("aus", "schwarz", "dunkel"), 0x000000, "aus"),
    )

    private val STOP = listOf("stopp", "stop", "halt", "steh", "warte", "aufhören", "aufhoeren")
    private val DRIVE = listOf("fahr", "lauf", "komm", "vorwärts", "vorwaerts", "los", "geh")
    private val HEAD = listOf("kopf", "nick", "schau")

    /** Built-in tricks by command id, with the stems that trigger them. */
    private val TRICKS = listOf(
        "fart" to listOf("furz", "pups", "pupsen", "puups"),
        "bark" to listOf("bell", "wuff", "wau"),
        "howl" to listOf("heul", "jaul"),
        "sing" to listOf("sing", "lied"),
        "wag" to listOf("wedel", "schwanzwedel"),
    )

    fun parse(phrase: String, commands: List<CommandDef>): VoiceAction? {
        val text = phrase.lowercase()
        val words = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        fun has(stems: List<String>) = words.any { w -> stems.any { w.startsWith(it) } }

        if (has(STOP)) return VoiceAction.Stop

        // Light: needs a color word (or "regenbogen"), optionally a body part.
        val wantsLight = has(listOf("licht", "farbe", "leucht", "led", "lampe"))
        if (has(listOf("regenbogen", "bunt"))) return VoiceAction.Rainbow
        val color = COLORS.firstOrNull { (stems, _, _) ->
            words.any { w -> stems.any { w == it || w == it + "e" || w == it + "en" } }
        }
        if (color != null && (color.third != "aus" || wantsLight || words.size <= 2)) {
            val targets = buildSet {
                if (has(listOf("auge"))) { add(LedTarget.LEFT_EYE); add(LedTarget.RIGHT_EYE) }
                if (has(listOf("brust", "bauch", "herz"))) add(LedTarget.CHEST)
                if (has(listOf("fuß", "fuss", "füße", "fuesse", "pfote", "bein"))) {
                    add(LedTarget.LEFT_FOOT); add(LedTarget.RIGHT_FOOT)
                }
                if (has(listOf("schwanz"))) add(LedTarget.TAIL)
            }.ifEmpty { LedTarget.entries.toSet() }
            return VoiceAction.Leds(targets, color.second, color.third)
        }

        TRICKS.forEach { (id, stems) ->
            if (has(stems)) commands.firstOrNull { it.id == id && it.hex.isNotBlank() }?.let { return VoiceAction.Command(it) }
        }
        if (has(HEAD)) return VoiceAction.Head(1500)
        if (has(DRIVE)) return VoiceAction.Drive(2000)

        // Own buttons from the "Befehle" tab: say their name.
        commands.firstOrNull { c ->
            val label = c.label.lowercase().filter { it.isLetter() || it == ' ' }.trim()
            label.length >= 3 && c.hex.isNotBlank() && text.contains(label)
        }?.let { return VoiceAction.Command(it) }

        return null
    }
}
