package de.dogeapp.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Joke(val question: String, val answer: String)

object JokeParser {
    /**
     * Accepts {"jokes":[{"q":..,"a":..}]}, a plain array of such objects, or an array of strings
     * (a string joke has no separate punchline).
     */
    fun parse(json: String): List<Joke> {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("jokes")
        return (0 until arr.length()).mapNotNull { i ->
            when (val item = arr.get(i)) {
                is String -> item.takeIf { it.isNotBlank() }?.let { Joke(it.trim(), "") }
                is JSONObject -> {
                    val q = item.optString("q", item.optString("frage", "")).trim()
                    val a = item.optString("a", item.optString("antwort", "")).trim()
                    if (q.isEmpty()) null else Joke(q, a)
                }
                else -> null
            }
        }
    }
}

/** Built-in jokes + jokes downloaded from [url]; avoids repeating recently told jokes. */
class JokeRepository(context: Context) {
    private val prefs = context.getSharedPreferences("doge_jokes", Context.MODE_PRIVATE)

    private var downloaded: List<Joke> =
        prefs.getString("json", null)?.let { runCatching { JokeParser.parse(it) }.getOrNull() }.orEmpty()

    val all: List<Joke> get() = (downloaded + BUILT_IN).distinctBy { it.question }
    val lastUpdate: Long get() = prefs.getLong("updated", 0)

    fun next(): Joke {
        val list = all
        val recent = prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotEmpty() }
        val fresh = list.filter { it.question !in recent }.ifEmpty { list }
        val joke = fresh.random()
        val keep = (recent + joke.question).takeLast((list.size / 2).coerceIn(1, 15))
        prefs.edit().putString("recent", keep.joinToString("\n")).apply()
        return joke
    }

    /** Downloads the joke list; returns how many jokes were loaded. */
    suspend fun refresh(url: String): Int {
        val json = Http.get(url)
        val jokes = JokeParser.parse(json)
        require(jokes.isNotEmpty()) { "Keine Witze in der Datei" }
        downloaded = jokes
        prefs.edit().putString("json", json).putLong("updated", System.currentTimeMillis()).apply()
        return jokes.size
    }

    fun isStale(): Boolean = System.currentTimeMillis() - lastUpdate > 24L * 60 * 60 * 1000

    companion object {
        const val DEFAULT_URL =
            "https://raw.githubusercontent.com/michifrey/dogeapp/ccr-47b219fd-ly8emu/content/witze.json"

        /** Works without internet. */
        val BUILT_IN = listOf(
            Joke("Was macht ein Hund im Kino?", "Er schaut sich einen Wau-Wau-Film an!"),
            Joke("Welcher Hund kann zaubern?", "Der Labra-dabra-dor!"),
            Joke("Was sagt ein Hund, wenn er sich wehtut?", "Au-wau!"),
            Joke("Warum trägt der Dalmatiner keine Brille?", "Weil er schon genug Punkte hat!"),
            Joke("Warum hat die Katze ihr Handy verloren?", "Sie hatte keinen Mäuse-Empfang!"),
        )
    }
}
