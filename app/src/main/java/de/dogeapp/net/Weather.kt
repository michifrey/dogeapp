package de.dogeapp.net

import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

data class Place(val name: String, val latitude: Double, val longitude: Double)

data class WeatherNow(
    val place: String,
    val temperature: Double,
    val feelsLike: Double,
    val code: Int,
    val windKmh: Double,
    val max: Double?,
    val min: Double?,
    val rainChance: Int?,
    val isDay: Boolean,
)

/** Weather from Open-Meteo (free, no API key, no GPS needed – only the town name). */
object WeatherApi {
    suspend fun findPlace(city: String): Place? {
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        return parsePlace(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=de&format=json"))
    }

    suspend fun current(place: Place): WeatherNow {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=1&timezone=auto"
        return parseForecast(Http.get(url), place.name)
    }

    fun parsePlace(json: String): Place? {
        val results = JSONObject(json).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        return Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    fun parseForecast(json: String, place: String): WeatherNow {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        val d = o.optJSONObject("daily")
        fun first(key: String) = d?.optJSONArray(key)?.takeIf { it.length() > 0 && !it.isNull(0) }?.getDouble(0)
        return WeatherNow(
            place = place,
            temperature = c.getDouble("temperature_2m"),
            feelsLike = c.optDouble("apparent_temperature", c.getDouble("temperature_2m")),
            code = c.getInt("weather_code"),
            windKmh = c.optDouble("wind_speed_10m", 0.0),
            max = first("temperature_2m_max"),
            min = first("temperature_2m_min"),
            rainChance = first("precipitation_probability_max")?.roundToInt(),
            isDay = c.optInt("is_day", 1) == 1,
        )
    }
}

/** Child-friendly German sentences and a matching LED color for the weather. */
object WeatherText {
    /** WMO weather code → short German description. */
    fun describe(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "die Sonne scheint" else "der Himmel ist klar und man sieht die Sterne"
        1 -> if (isDay) "es ist meistens sonnig" else "es ist fast wolkenlos"
        2 -> "es ist ein bisschen bewölkt"
        3 -> "der Himmel ist grau und bewölkt"
        45, 48 -> "es ist neblig"
        51, 53, 55 -> "es nieselt"
        56, 57, 66, 67 -> "es gibt gefrierenden Regen, Vorsicht, es ist glatt"
        61, 63 -> "es regnet"
        65 -> "es regnet ganz stark"
        71, 73, 77 -> "es schneit"
        75 -> "es schneit ganz viel"
        80, 81 -> "es gibt Regenschauer"
        82 -> "es gibt heftige Regenschauer"
        85, 86 -> "es gibt Schneeschauer"
        95 -> "es gewittert"
        96, 99 -> "es gewittert mit Hagel"
        else -> "das Wetter ist schwer zu sagen"
    }

    fun advice(w: WeatherNow): String = when {
        w.code in 95..99 -> "Bleib lieber drinnen, wuff!"
        w.code in 71..86 && w.temperature <= 3 -> "Zieh dich warm an, vielleicht kannst du einen Schneemann bauen!"
        w.code in 51..82 -> "Nimm einen Regenschirm oder zieh die Gummistiefel an!"
        w.feelsLike < 5 -> "Brr! Zieh eine warme Jacke, Mütze und Handschuhe an!"
        w.feelsLike < 14 -> "Nimm lieber eine Jacke mit!"
        w.feelsLike >= 28 -> "Es ist heiß! Trink genug Wasser und denk an die Sonnencreme!"
        w.code <= 1 && w.isDay -> "Perfektes Wetter zum Ballspielen!"
        else -> "Viel Spaß draußen!"
    }

    fun sentence(w: WeatherNow, childName: String): String {
        val name = childName.trim()
        val intro = if (name.isEmpty()) "Hier ist das Wetter in ${w.place}:" else "$name, hier ist das Wetter in ${w.place}:"
        val temp = w.temperature.roundToInt()
        val what = describe(w.code, w.isDay).replaceFirstChar { it.uppercase() }
        val sb = StringBuilder("$intro $what und es sind $temp Grad.")
        if (w.max != null && w.min != null) {
            sb.append(" Heute wird es zwischen ${w.min.roundToInt()} und ${w.max.roundToInt()} Grad.")
        }
        if (w.rainChance != null && w.rainChance >= 50 && w.code !in 51..82) {
            sb.append(" Später kann es regnen.")
        }
        sb.append(' ').append(advice(w))
        return sb.toString()
    }

    /** LED color for the weather: sunny yellow, cloudy white, rain blue, snow ice-blue, thunder purple. */
    fun ledColor(code: Int, isDay: Boolean): Int = when {
        code in 95..99 -> 0x8000FF
        code in 71..77 || code in 85..86 -> 0xA0E0FF
        code in 51..67 || code in 80..82 -> 0x0040FF
        code == 45 || code == 48 -> 0x808080
        code <= 1 -> if (isDay) 0xFFC000 else 0x000060
        else -> 0xFFFFFF
    }
}
