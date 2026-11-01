package de.dogeapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One command that can be sent to the dog: a display label and the raw bytes as a hex string.
 *
 * [id] is stable and used by the control screen (e.g. the D-pad looks up "forward").
 * An empty [hex] means "not yet known": the button is shown but disabled.
 */
data class CommandDef(
    val id: String,
    val label: String,
    val hex: String,
)

/**
 * Control channel and command table. Defaults come from [DogeProtocol]; everything can be
 * changed in the app (tab "Befehle") and is stored on the phone.
 */
data class DogeConfig(
    /** Characteristic that commands are written to (default: the real Dog-E channel). */
    val writeServiceUuid: String = DogeProtocol.WRITE_SERVICE,
    val writeCharUuid: String = DogeProtocol.WRITE_CHAR,
    /** true = Write Without Response (what the official app uses). */
    val writeWithoutResponse: Boolean = true,
    /** While a hold button is pressed, the command is repeated at this interval (ms). 0 = send once. */
    val repeatIntervalMs: Long = 0,
    /** Part of the advertised name used to highlight the dog in the scan list ("Mint Dog"). */
    val nameHint: String = "dog",
    val commands: List<CommandDef> = DEFAULT_COMMANDS,
    /** Name of the child, used for the greeting. */
    val childName: String = "",
    /** In conversations the tablet speaks for the dog (text-to-speech). */
    val dogSpeaks: Boolean = true,
    /** Town for the weather report, e.g. "Zürich". */
    val city: String = "",
    /** Where new jokes are downloaded from (JSON, see content/witze.json). */
    val jokesUrl: String = de.dogeapp.net.JokeRepository.DEFAULT_URL,
) {
    val hasWriteTarget: Boolean get() = writeServiceUuid.isNotBlank() && writeCharUuid.isNotBlank()

    fun command(id: String): CommandDef? = commands.firstOrNull { it.id == id }

    companion object {
        const val FORWARD = "forward"
        const val HEAD = "head"
        const val STOP = "stop"
        /** Commands shown as hold buttons at the top of the control screen. */
        val DRIVE_IDS = setOf(FORWARD, HEAD, STOP)

        private fun hex(b: ByteArray) = Hex.format(b)

        val DEFAULT_COMMANDS = listOf(
            CommandDef(FORWARD, "Fahren", hex(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, 1))),
            CommandDef(HEAD, "Kopf", hex(DogeProtocol.motor(DogeProtocol.MOTOR_HEAD, 2))),
            CommandDef(STOP, "Stopp", hex(DogeProtocol.stopMotors())),
            CommandDef("bark", "Bellen", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_BARK))),
            CommandDef("wag", "Schwanzwedeln", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_WAG_TAIL))),
            CommandDef("howl", "Heulen", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_HOWL))),
            CommandDef("sing", "Singen", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_SING))),
            CommandDef("fart", "Furzen 💨", hex(DogeProtocol.playSequence(DogeProtocol.SEQ_FART))),
        )
    }
}

/** Stores the config as JSON in SharedPreferences. */
class ConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("doge_config", Context.MODE_PRIVATE)

    fun load(): DogeConfig {
        val raw = prefs.getString(KEY, null) ?: return DogeConfig()
        return runCatching { fromJson(JSONObject(raw)) }.getOrElse { DogeConfig() }
    }

    fun save(config: DogeConfig) {
        prefs.edit().putString(KEY, toJson(config).toString()).apply()
    }

    fun exportJson(config: DogeConfig): String = toJson(config).toString(2)

    fun importJson(json: String): DogeConfig = fromJson(JSONObject(json))

    private fun toJson(c: DogeConfig) = JSONObject().apply {
        put("writeServiceUuid", c.writeServiceUuid)
        put("writeCharUuid", c.writeCharUuid)
        put("writeWithoutResponse", c.writeWithoutResponse)
        put("repeatIntervalMs", c.repeatIntervalMs)
        put("nameHint", c.nameHint)
        put("childName", c.childName)
        put("dogSpeaks", c.dogSpeaks)
        put("city", c.city)
        put("jokesUrl", c.jokesUrl)
        put("commands", JSONArray().apply {
            c.commands.forEach {
                put(JSONObject().put("id", it.id).put("label", it.label).put("hex", it.hex))
            }
        })
    }

    private fun fromJson(o: JSONObject): DogeConfig {
        val defaults = DogeConfig()
        val cmds = o.optJSONArray("commands")?.let { arr ->
            (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                CommandDef(c.getString("id"), c.optString("label", c.getString("id")), c.optString("hex", ""))
            }
        } ?: defaults.commands
        return DogeConfig(
            writeServiceUuid = o.optString("writeServiceUuid", ""),
            writeCharUuid = o.optString("writeCharUuid", ""),
            writeWithoutResponse = o.optBoolean("writeWithoutResponse", defaults.writeWithoutResponse),
            repeatIntervalMs = o.optLong("repeatIntervalMs", defaults.repeatIntervalMs),
            nameHint = o.optString("nameHint", defaults.nameHint),
            commands = cmds,
            childName = o.optString("childName", ""),
            dogSpeaks = o.optBoolean("dogSpeaks", true),
            city = o.optString("city", ""),
            jokesUrl = o.optString("jokesUrl", defaults.jokesUrl).ifBlank { defaults.jokesUrl },
        )
    }

    private companion object {
        const val KEY = "config_json_v2"
    }
}
