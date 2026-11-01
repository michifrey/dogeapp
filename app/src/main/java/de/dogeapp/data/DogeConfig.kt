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
 * Everything the app needs to know about the Dog-E protocol.
 *
 * The protocol is not publicly documented. These values are filled in once they are found
 * (see docs/PROTOKOLL.md): either in the app (Explorer screen, "use as control channel")
 * or by changing [DogeConfig.DEFAULT] here.
 */
data class DogeConfig(
    /** Characteristic that commands are written to. */
    val writeServiceUuid: String = "",
    val writeCharUuid: String = "",
    /** true = Write Without Response (typical for toy robots), false = Write With Response. */
    val writeWithoutResponse: Boolean = true,
    /** While a drive button is held, the command is repeated at this interval (ms). 0 = send once. */
    val repeatIntervalMs: Long = 100,
    /** Part of the advertised name used to highlight the dog in the scan list. */
    val nameHint: String = "dog",
    val commands: List<CommandDef> = DEFAULT_COMMANDS,
) {
    val hasWriteTarget: Boolean get() = writeServiceUuid.isNotBlank() && writeCharUuid.isNotBlank()

    fun command(id: String): CommandDef? = commands.firstOrNull { it.id == id }

    companion object {
        const val FORWARD = "forward"
        const val BACKWARD = "backward"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val STOP = "stop"
        val DRIVE_IDS = setOf(FORWARD, BACKWARD, LEFT, RIGHT, STOP)

        // TODO: fill in the real bytes once the protocol is known.
        val DEFAULT_COMMANDS = listOf(
            CommandDef(FORWARD, "Vorwärts", ""),
            CommandDef(BACKWARD, "Rückwärts", ""),
            CommandDef(LEFT, "Links", ""),
            CommandDef(RIGHT, "Rechts", ""),
            CommandDef(STOP, "Stopp", ""),
            CommandDef("sit", "Sitz", ""),
            CommandDef("lie", "Platz", ""),
            CommandDef("bark", "Bellen", ""),
            CommandDef("dance", "Tanzen", ""),
            CommandDef("wag", "Schwanzwedeln", ""),
            CommandDef("tail_light", "Schwanz-Licht", ""),
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
        )
    }

    private companion object {
        const val KEY = "config_json"
    }
}
