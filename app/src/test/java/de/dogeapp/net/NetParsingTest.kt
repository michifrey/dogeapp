package de.dogeapp.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetParsingTest {
    @Test
    fun parsesJokeFormats() {
        val obj = JokeParser.parse("""{"jokes":[{"q":"Frage?","a":"Antwort!"},{"q":""}]}""")
        assertEquals(listOf(Joke("Frage?", "Antwort!")), obj)
        assertEquals(listOf(Joke("Ein Witz", "")), JokeParser.parse("""["Ein Witz", " "]"""))
        assertEquals(listOf(Joke("F", "A")), JokeParser.parse("""[{"frage":"F","antwort":"A"}]"""))
    }

    @Test
    fun bundledJokeFileIsValid() {
        val file = listOf("../content/witze.json", "content/witze.json").map(::File).first { it.exists() }
        val jokes = JokeParser.parse(file.readText())
        assertTrue(jokes.size >= 10)
        assertTrue(jokes.all { it.question.isNotBlank() && it.answer.isNotBlank() })
    }

    @Test
    fun parsesGeocoding() {
        val p = WeatherApi.parsePlace("""{"results":[{"name":"Zürich","latitude":47.36667,"longitude":8.55,"country":"Schweiz"}]}""")!!
        assertEquals("Zürich", p.name)
        assertEquals(8.55, p.longitude, 0.0001)
        assertNull(WeatherApi.parsePlace("""{"generationtime_ms":0.5}"""))
    }

    @Test
    fun parsesForecastAndBuildsSentence() {
        val json = """{"current":{"time":"2026-11-01T16:00","temperature_2m":8.4,"apparent_temperature":5.9,
            "weather_code":61,"wind_speed_10m":12.0,"is_day":1},
            "daily":{"temperature_2m_max":[10.2],"temperature_2m_min":[3.6],"precipitation_probability_max":[80]}}"""
        val w = WeatherApi.parseForecast(json, "Zürich")
        assertEquals(61, w.code)
        assertEquals(80, w.rainChance)
        assertEquals(
            "Yara, hier ist das Wetter in Zürich: Es regnet und es sind 8 Grad. Heute wird es zwischen 4 und 10 Grad. " +
                "Nimm einen Regenschirm oder zieh die Gummistiefel an!",
            WeatherText.sentence(w, "Yara"),
        )
        assertEquals(0x0040FF, WeatherText.ledColor(61, true))
    }
}
