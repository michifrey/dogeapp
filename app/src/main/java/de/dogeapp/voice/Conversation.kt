package de.dogeapp.voice

import de.dogeapp.data.DogeProtocol
import de.dogeapp.data.LedTarget

/** One step of a reaction: the tablet speaks, the dog plays a sound/trick, moves or lights up. */
sealed interface Step {
    data class Say(val text: String) : Step
    data class Sequence(val id: Int, val waitMs: Long = 1800) : Step
    data class Drive(val ms: Long) : Step
    data class Head(val ms: Long) : Step
    data class Light(val rgb: Int, val targets: Set<LedTarget> = LedTarget.entries.toSet()) : Step
    data object Rainbow : Step
    data class Wait(val ms: Long) : Step
    /** Tell a joke (downloaded or built-in), then laugh. */
    data object Joke : Step
    /** Fetch and tell the current weather for the town from the settings. */
    data object Weather : Step
}

/**
 * A small "conversation": when the child says one of [phrases] / a word starting with one of
 * [stems], the dog reacts with [steps]. `{name}` in spoken text is replaced by the child's name.
 */
data class Reaction(
    val id: String,
    val label: String,
    val stems: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val steps: List<Step>,
)

object Conversation {
    private val EYES = setOf(LedTarget.LEFT_EYE, LedTarget.RIGHT_EYE)
    private const val PINK = 0xFF1490

    val REACTIONS = listOf(
        Reaction(
            "hello", "Hallo",
            stems = listOf("hallo", "hello", "hi", "huhu", "servus", "moin", "grüezi", "grüß", "gruess"),
            phrases = listOf("guten morgen", "guten tag", "guten abend"),
            steps = listOf(
                Step.Light(PINK, EYES),
                Step.Say("Wuff! Hallo {name}! Schön, dass du da bist!"),
                Step.Sequence(DogeProtocol.SEQ_BARK),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
            ),
        ),
        Reaction(
            "ball", "Ball spielen",
            stems = listOf("ball", "spiel", "fang", "wirf", "werf"),
            steps = listOf(
                Step.Say("Au ja, spielen! Wirf den Ball, {name}!"),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
                Step.Drive(1500),
                Step.Sequence(DogeProtocol.SEQ_BARK),
                Step.Say("Wuff! Nochmal!"),
            ),
        ),
        Reaction(
            "howareyou", "Wie geht's?",
            phrases = listOf("wie geht", "wie gehts", "alles gut", "alles klar"),
            steps = listOf(
                Step.Say("Mir geht es super, wuff! Und dir, {name}?"),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
            ),
        ),
        Reaction(
            "name", "Wie heißt du?",
            phrases = listOf("wie heißt du", "wie heisst du", "wer bist du", "dein name"),
            steps = listOf(
                Step.Say("Ich bin Dog-E, dein Roboterhund! Wuff!"),
                Step.Head(1200),
            ),
        ),
        Reaction(
            "goodboy", "Braver Hund",
            stems = listOf("brav", "fein", "super", "toll", "klasse"),
            phrases = listOf("gut gemacht", "guter hund", "gutes hündchen"),
            steps = listOf(
                Step.Light(PINK),
                Step.Say("Danke! Das freut mich!"),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
            ),
        ),
        Reaction(
            "love", "Hab dich lieb",
            stems = listOf("lieb", "kuschel", "knuddel", "streichel"),
            steps = listOf(
                Step.Light(PINK),
                Step.Say("Ich hab dich auch ganz doll lieb, {name}!"),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
                Step.Sequence(DogeProtocol.SEQ_SING),
            ),
        ),
        Reaction(
            "hungry", "Hunger",
            stems = listOf("hunger", "fress", "leckerli", "futter", "essen", "knochen"),
            steps = listOf(
                Step.Say("Mmmh, lecker! Hast du ein Leckerli für mich?"),
                Step.Sequence(DogeProtocol.SEQ_BARK),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
            ),
        ),
        Reaction(
            "dance", "Tanzen",
            stems = listOf("tanz", "party", "disco", "musik"),
            steps = listOf(
                Step.Rainbow,
                Step.Say("Party! Wuff wuff!"),
                Step.Sequence(DogeProtocol.SEQ_SING),
                Step.Head(1000),
                Step.Sequence(DogeProtocol.SEQ_WAG_TAIL),
            ),
        ),
        Reaction(
            "joke", "Witz",
            stems = listOf("witz", "lustig", "lachen"),
            steps = listOf(Step.Joke),
        ),
        Reaction(
            "weather", "Wetter",
            stems = listOf("wetter", "regnet", "schneit", "draußen", "draussen", "temperatur"),
            phrases = listOf("wie warm", "wie kalt", "scheint die sonne", "brauche ich eine jacke"),
            steps = listOf(Step.Weather),
        ),
        Reaction(
            "night", "Gute Nacht",
            stems = listOf("schlaf", "müde", "muede"),
            phrases = listOf("gute nacht", "bis morgen"),
            steps = listOf(
                Step.Say("Gute Nacht, {name}. Schlaf schön!"),
                Step.Sequence(DogeProtocol.SEQ_HOWL),
                Step.Light(0x000030),
            ),
        ),
        Reaction(
            "bye", "Tschüss",
            stems = listOf("tschüss", "tschuess", "tschau", "ciao", "bye"),
            phrases = listOf("auf wiedersehen", "bis bald", "bis später"),
            steps = listOf(
                Step.Say("Tschüss {name}! Komm bald wieder!"),
                Step.Sequence(DogeProtocol.SEQ_HOWL),
            ),
        ),
    )

    fun match(phrase: String): Reaction? {
        val text = phrase.lowercase()
        val words = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return REACTIONS.firstOrNull { r ->
            r.phrases.any { text.contains(it) } ||
                words.any { w -> r.stems.any { s -> if (s.length <= 2) w == s else w.startsWith(s) } }
        }
    }

    fun personalize(text: String, name: String): String =
        if (name.isBlank()) text.replace(", {name}", "").replace(" {name}", "").replace("{name}", "")
        else text.replace("{name}", name.trim())
}
