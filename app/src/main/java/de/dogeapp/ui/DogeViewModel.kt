package de.dogeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dogeapp.ble.BleManager
import de.dogeapp.ble.CharInfo
import de.dogeapp.ble.ConnState
import de.dogeapp.data.CommandDef
import de.dogeapp.data.ConfigRepository
import de.dogeapp.data.DogeConfig
import de.dogeapp.data.DogeProtocol
import de.dogeapp.data.Hex
import de.dogeapp.data.LedTarget
import de.dogeapp.voice.Conversation
import de.dogeapp.voice.DogVoice
import de.dogeapp.voice.Reaction
import de.dogeapp.voice.Step
import de.dogeapp.voice.VoiceAction
import de.dogeapp.voice.VoiceCommands
import de.dogeapp.voice.VoiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PairState { UNKNOWN, CHECKING, PAIRING, PAIRED, NOT_PAIRED }

class DogeViewModel(app: Application) : AndroidViewModel(app) {

    val ble = BleManager(app)
    private val repo = ConfigRepository(app)

    private val _config = MutableStateFlow(repo.load())
    val config: StateFlow<DogeConfig> = _config.asStateFlow()

    private var holdJob: Job? = null

    private val _pairState = MutableStateFlow(PairState.UNKNOWN)
    val pairState: StateFlow<PairState> = _pairState.asStateFlow()
    private var pairingRequested = false

    /** 6 random bytes that identify this phone to the dog, created once and kept. */
    private val phoneId: ByteArray by lazy {
        val prefs = app.getSharedPreferences("doge_config", 0)
        prefs.getString("phone_id", null)?.let { Hex.parse(it) }?.takeIf { it.size == 6 }
            ?: ByteArray(6).also {
                java.security.SecureRandom().nextBytes(it)
                prefs.edit().putString("phone_id", Hex.format(it)).apply()
            }
    }

    init {
        // After every connect: ask the dog whether this phone is paired (like the official app does).
        viewModelScope.launch {
            ble.state.collectLatest { s ->
                if (s == ConnState.READY) {
                    pairingRequested = false
                    delay(500) // let the notification subscriptions finish first
                    checkPairing()
                    // Battery: ask now and then every minute while connected.
                    while (true) {
                        delay(1500)
                        requestBattery()
                        delay(58_500)
                    }
                } else if (s == ConnState.DISCONNECTED) {
                    _pairState.value = PairState.UNKNOWN
                    _battery.value = null
                }
            }
        }
        viewModelScope.launch {
            ble.notifications.collect { (_, data) -> onDogData(data) }
        }
    }

    fun checkPairing() {
        _pairState.value = PairState.CHECKING
        sendRaw(DogeProtocol.authCheck(phoneId))
    }

    fun requestPairing() {
        pairingRequested = true
        _pairState.value = PairState.PAIRING
        ble.log("Kopplung angefragt – evtl. am Hund bestätigen")
        sendRaw(DogeProtocol.pairingRequest())
    }

    // ------------------------------------------------------------------ Battery & volume

    private val _battery = MutableStateFlow<Int?>(null)
    /** Last battery level reported by the dog (as sent, probably percent), null = unknown. */
    val battery: StateFlow<Int?> = _battery.asStateFlow()

    private val _volume = MutableStateFlow(
        app.getSharedPreferences("doge_config", 0).getInt("volume", 9)
    )
    val volume: StateFlow<Int> = _volume.asStateFlow()

    fun requestBattery() = sendRaw(DogeProtocol.requestBattery())

    /** Updates the slider; the value is sent when [send] is true (on release) to avoid flooding. */
    fun setVolume(v: Int, send: Boolean = true) {
        val clamped = v.coerceIn(0, DogeProtocol.VOLUME_MAX)
        _volume.value = clamped
        if (send) {
            sendRaw(DogeProtocol.setVolume(clamped))
            getApplication<Application>().getSharedPreferences("doge_config", 0)
                .edit().putInt("volume", clamped).apply()
        }
    }

    private fun onDogData(data: ByteArray) {
        DogeProtocol.parseBattery(data)?.let {
            _battery.value = it
            ble.log("Akku: $it")
            return
        }
        when (val r = DogeProtocol.parseAuthReply(data) ?: return) {
            is DogeProtocol.AuthReply.Status -> {
                if (r.authenticated) {
                    _pairState.value = PairState.PAIRED
                    ble.log("Hund: gekoppelt ✔")
                } else if (!pairingRequested) {
                    requestPairing()
                } else {
                    _pairState.value = PairState.NOT_PAIRED
                    ble.log("Hund: nicht gekoppelt")
                }
            }
            is DogeProtocol.AuthReply.Pairing -> {
                ble.log("Hund: Kopplungs-Antwort ${r.code}")
                // The dog accepted the pairing request: check again with our ID.
                viewModelScope.launch {
                    delay(300)
                    checkPairing()
                }
            }
        }
    }

    private fun sendRaw(bytes: ByteArray) {
        val cfg = _config.value
        if (!cfg.hasWriteTarget) return
        ble.write(cfg.writeServiceUuid, cfg.writeCharUuid, bytes, cfg.writeWithoutResponse)
    }

    /** Plays any trick/sound sequence by number – handy to discover what the dog can do. */
    fun playSequence(id: Int) = sendRaw(DogeProtocol.playSequence(id))

    // ------------------------------------------------------------------ LEDs

    private val ledPrefs = app.getSharedPreferences("doge_leds", 0)
    private val _leds = MutableStateFlow(
        LedTarget.entries.associateWith { ledPrefs.getInt(it.name, 0xFFFFFF) }
    )
    val leds: StateFlow<Map<LedTarget, Int>> = _leds.asStateFlow()
    private var ledJob: Job? = null
    private var lastLedSend = 0L

    /** Sets [targets] to [rgb] (0xRRGGBB) and sends all six LEDs, at most every 120 ms while dragging. */
    fun setLedColor(targets: Set<LedTarget>, rgb: Int) {
        _leds.value = _leds.value.toMutableMap().apply { targets.forEach { put(it, rgb and 0xFFFFFF) } }
        scheduleLedSend()
    }

    fun setAllLeds(colors: Map<LedTarget, Int>) {
        _leds.value = _leds.value + colors
        scheduleLedSend()
    }

    fun rainbow() = setAllLeds(
        mapOf(
            LedTarget.LEFT_EYE to 0xFF0000, LedTarget.RIGHT_EYE to 0xFF8000,
            LedTarget.CHEST to 0xFFFF00, LedTarget.LEFT_FOOT to 0x00FF00,
            LedTarget.RIGHT_FOOT to 0x0080FF, LedTarget.TAIL to 0x8000FF,
        )
    )

    private fun scheduleLedSend() {
        if (ledJob?.isActive == true) return
        val wait = (120 - (System.currentTimeMillis() - lastLedSend)).coerceAtLeast(0)
        ledJob = viewModelScope.launch {
            delay(wait)
            lastLedSend = System.currentTimeMillis()
            val now = _leds.value
            sendRaw(DogeProtocol.setLeds(now))
            ledPrefs.edit().apply { now.forEach { (t, c) -> putInt(t.name, c) } }.apply()
        }
    }

    // ------------------------------------------------------------------ Voice

    private val _voiceOn = MutableStateFlow(false)
    val voiceOn: StateFlow<Boolean> = _voiceOn.asStateFlow()
    private val _voiceListening = MutableStateFlow(false)
    val voiceListening: StateFlow<Boolean> = _voiceListening.asStateFlow()
    private val _voiceHeard = MutableStateFlow("")
    val voiceHeard: StateFlow<String> = _voiceHeard.asStateFlow()

    private val voice = VoiceController(
        app,
        onPhrase = { onVoice(it) },
        onListening = { _voiceListening.value = it },
        onProblem = { msg -> _voiceHeard.value = "⚠ $msg"; ble.log("Sprache: $msg") },
    )

    fun setVoice(on: Boolean) {
        _voiceOn.value = on
        if (on) voice.start() else voice.stop()
    }

    private fun onVoice(alternatives: List<String>) {
        val cmds = _config.value.commands
        val hit = alternatives.firstNotNullOfOrNull { VoiceCommands.parse(it, cmds)?.let { a -> it to a } }
        if (hit == null) {
            _voiceHeard.value = "\"${alternatives.first()}\" – nicht verstanden"
            return
        }
        val (phrase, action) = hit
        _voiceHeard.value = "\"$phrase\" → ${action.description}"
        ble.log("Sprache: \"$phrase\" → ${action.description}")
        runAction(action)
    }

    private fun runAction(action: VoiceAction) {
        val cfg = _config.value
        when (action) {
            VoiceAction.Stop -> {
                holdJob?.cancel()
                reactionJob?.cancel()
                sendRaw(DogeProtocol.stopMotors())
            }
            is VoiceAction.Drive -> moveFor(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, 1), action.ms)
            is VoiceAction.Head -> moveFor(DogeProtocol.motor(DogeProtocol.MOTOR_HEAD, 2), action.ms)
            is VoiceAction.Command -> cfg.command(action.cmd.id)?.let { send(it) } ?: send(action.cmd)
            is VoiceAction.Leds -> setLedColor(action.targets, action.rgb)
            VoiceAction.Rainbow -> rainbow()
            is VoiceAction.Volume -> setVolume(_volume.value + action.delta)
            is VoiceAction.React -> runReaction(action.reaction)
        }
    }

    // ------------------------------------------------------------------ Conversation

    private val dogVoice = DogVoice(app)
    private var reactionJob: Job? = null
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** Plays a little conversation: the tablet talks for the dog, the dog moves, sounds and lights up. */
    fun runReaction(r: Reaction) {
        reactionJob?.cancel()
        reactionJob = viewModelScope.launch {
            val name = _config.value.childName
            for (step in r.steps) {
                when (step) {
                    is Step.Say -> say(Conversation.personalize(step.text, name))
                    is Step.Sequence -> { playSequence(step.id); delay(step.waitMs) }
                    is Step.Drive -> {
                        sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, 1)); delay(step.ms)
                        sendRaw(DogeProtocol.stopMotors())
                    }
                    is Step.Head -> {
                        sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_HEAD, 2)); delay(step.ms)
                        sendRaw(DogeProtocol.stopMotors())
                    }
                    is Step.Light -> setLedColor(step.targets, step.rgb)
                    Step.Rainbow -> rainbow()
                    is Step.Wait -> delay(step.ms)
                }
            }
        }
    }

    /** Speaks while the microphone is paused, so the recognizer does not hear the tablet itself. */
    private suspend fun say(text: String) {
        if (!_config.value.dogSpeaks || !dogVoice.isReady) return
        val wasListening = _voiceOn.value
        if (wasListening) voice.stop()
        _speaking.value = true
        try {
            dogVoice.say(text)
        } finally {
            _speaking.value = false
            if (wasListening && _voiceOn.value) voice.start()
        }
    }

    private fun moveFor(packet: ByteArray, ms: Long) {
        holdJob?.cancel()
        holdJob = viewModelScope.launch {
            sendRaw(packet)
            delay(ms)
            sendRaw(DogeProtocol.stopMotors())
        }
    }

    // ------------------------------------------------------------------ Config

    fun updateConfig(transform: (DogeConfig) -> DogeConfig) {
        val next = transform(_config.value)
        _config.value = next
        repo.save(next)
    }

    fun useAsControlChannel(c: CharInfo) {
        updateConfig {
            it.copy(
                writeServiceUuid = c.serviceUuid,
                writeCharUuid = c.uuid,
                writeWithoutResponse = c.canWriteNoResponse && !c.canWrite,
            )
        }
        ble.log("Steuer-Kanal gesetzt: ${BleManager.shortUuid(c.uuid)}")
    }

    fun exportConfig(): String = repo.exportJson(_config.value)

    fun importConfig(json: String): Boolean = runCatching {
        val c = repo.importJson(json)
        _config.value = c
        repo.save(c)
    }.isSuccess

    fun resetConfig() = updateConfig { DogeConfig() }

    /** Sends a command once. */
    fun send(cmd: CommandDef) {
        val cfg = _config.value
        if (!cfg.hasWriteTarget) {
            ble.log("Kein Steuer-Kanal gesetzt: im Explorer eine schreibbare Characteristic wählen")
            return
        }
        val bytes = Hex.parse(cmd.hex) ?: run {
            ble.log("\"${cmd.label}\": keine gültigen Hex-Bytes hinterlegt")
            return
        }
        ble.write(cfg.writeServiceUuid, cfg.writeCharUuid, bytes, cfg.writeWithoutResponse)
    }

    /** Drive button pressed: send now and repeat until [release]. */
    fun press(cmd: CommandDef) {
        holdJob?.cancel()
        val interval = _config.value.repeatIntervalMs
        holdJob = viewModelScope.launch {
            send(cmd)
            if (interval <= 0) return@launch
            while (isActive) {
                delay(interval)
                send(cmd)
            }
        }
    }

    /** Drive button released: stop repeating and send "stop" if it is known. */
    fun release() {
        holdJob?.cancel()
        holdJob = null
        _config.value.command(DogeConfig.STOP)?.takeIf { it.hex.isNotBlank() }?.let { send(it) }
    }

    /** Free-form write from the Explorer. */
    fun rawWrite(c: CharInfo, hex: String) {
        val bytes = Hex.parse(hex) ?: run {
            ble.log("Ungültige Hex-Eingabe: \"$hex\"")
            return
        }
        ble.write(c.serviceUuid, c.uuid, bytes, withoutResponse = c.canWriteNoResponse && !c.canWrite)
    }

    override fun onCleared() {
        voice.stop()
        dogVoice.shutdown()
        ble.disconnect()
        super.onCleared()
    }
}
