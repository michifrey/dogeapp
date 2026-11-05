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
import de.dogeapp.data.ToyGame
import de.dogeapp.net.JokeRepository
import de.dogeapp.net.WeatherApi
import de.dogeapp.net.WeatherText
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


    fun checkPairing() {
        _pairState.value = PairState.CHECKING
        sendRaw(DogeProtocol.authCheck(phoneId))
    }

    private var pairingTimeout: Job? = null

    /**
     * Official flow ("Confirm Connection: Press the nose button after you hear the chime."):
     * the dog chimes after the request and waits for its nose button; then it answers 01 01 04/05.
     * Until then we stay in PAIRING (other answers just mean "still waiting").
     */
    fun requestPairing() {
        pairingRequested = true
        _pairState.value = PairState.PAIRING
        ble.log("Kopplung angefragt – nach dem Klingelton Dog-Es Nase drücken 👃")
        sendRaw(DogeProtocol.pairingRequest())
        pairingTimeout?.cancel()
        pairingTimeout = viewModelScope.launch {
            delay(30_000)
            if (_pairState.value == PairState.PAIRING) {
                _pairState.value = PairState.NOT_PAIRED
                ble.log("Kopplung: keine Bestätigung an der Nase – nochmal \"Koppeln\" tippen")
            }
        }
    }

    // ------------------------------------------------------------------ Driving

    private val _driveSpeed = MutableStateFlow(2)
    /** 1 = slow … 3 = fast (the official app's slider sends motor speed 1, 2 or 3). */
    val driveSpeed: StateFlow<Int> = _driveSpeed.asStateFlow()
    private val _driving = MutableStateFlow(false)
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    fun setDriveSpeed(speed: Int) {
        _driveSpeed.value = speed.coerceIn(1, 3)
        if (_driving.value) sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, _driveSpeed.value))
    }

    /** Hold-to-drive: start on press … */
    fun driveStart() {
        reactionJob?.cancel()
        _driving.value = true
        sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, _driveSpeed.value))
    }

    /** … stop on release. */
    fun driveStop() {
        _driving.value = false
        sendRaw(DogeProtocol.stopMotors())
    }

    fun headStart() = sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_HEAD, 2))
    fun headStop() = sendRaw(DogeProtocol.stopMotors())

    /**
     * Experimental: the official app never drives backwards. Try other speed bytes for the drive
     * motor (e.g. 4…6 or 0xFD…0xFF) to find out whether the motor can reverse.
     */
    fun driveRaw(speedByte: Int) {
        _driving.value = true
        sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, speedByte and 0xFF))
    }

    /** Big red button: stops motors, running sequences/sounds, the tail animation and any reaction. */
    fun stopAll() {
        holdJob?.cancel()
        reactionJob?.cancel()
        _driving.value = false
        sendRaw(DogeProtocol.stopMotors())
        sendRaw(DogeProtocol.stopSequences())
        sendRaw(DogeProtocol.stopTail())
    }

    // ------------------------------------------------------------------ Games, tail, sleep, firmware

    fun launchGame(game: ToyGame) {
        reactionJob?.cancel()
        ble.log("Spiel starten: ${game.label}")
        sendRaw(DogeProtocol.launchGame(game))
    }

    private val _tailText = MutableStateFlow(
        app.getSharedPreferences("doge_config", 0).getString("tail_text", "") ?: ""
    )
    /** Last message shown on the tail (kept as the text field's initial value). */
    val tailText: StateFlow<String> = _tailText.asStateFlow()

    fun sendTailText(text: String) {
        val t = text.trim().take(DogeProtocol.TAIL_TEXT_MAX)
        if (t.isEmpty()) return
        _tailText.value = t
        getApplication<Application>().getSharedPreferences("doge_config", 0).edit().putString("tail_text", t).apply()
        ble.log("Schwanz-Nachricht: $t")
        sendRaw(DogeProtocol.tailText(t))
    }

    fun stopTail() = sendRaw(DogeProtocol.stopTail())

    fun sleepDog() {
        ble.log("Dog-E schlafen legen")
        sendRaw(DogeProtocol.sleep())
    }

    private val _firmware = MutableStateFlow<String?>(null)
    /** Firmware answer as text (raw bytes, as long as the format is not known). */
    val firmware: StateFlow<String?> = _firmware.asStateFlow()

    fun requestFirmware() = sendRaw(DogeProtocol.requestFirmware())

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
        if (data.size >= 4 && data[0] == 0xFF.toByte() && data[2].toInt() == DogeProtocol.CMD_FIRMWARE) {
            val payload = data.copyOfRange(3, data.size - 1)
            val ascii = payload.filter { it in 32..126 }.map { it.toInt().toChar() }.joinToString("")
            _firmware.value = if (ascii.length >= 3) ascii else payload.joinToString(".") { (it.toInt() and 0xFF).toString() }
            ble.log("Firmware: ${_firmware.value}")
            return
        }
        DogeProtocol.parseBattery(data)?.let {
            _battery.value = it
            ble.log("Akku: $it")
            return
        }
        when (val r = DogeProtocol.parseAuthReply(data) ?: return) {
            is DogeProtocol.AuthReply.Status -> {
                if (r.authenticated) {
                    pairingTimeout?.cancel()
                    _pairState.value = PairState.PAIRED
                    ble.log("Hund: gekoppelt ✔")
                } else if (_pairState.value == PairState.PAIRING) {
                    ble.log("Hund wartet auf den Nasen-Knopf …")
                } else if (!pairingRequested) {
                    requestPairing()
                } else {
                    _pairState.value = PairState.NOT_PAIRED
                    ble.log("Hund: nicht gekoppelt")
                }
            }
            is DogeProtocol.AuthReply.Pairing -> {
                // Same as the official app (PairingSceneController.OnPairingReceived): an answer
                // 01 01 04/05 to the pairing request means the dog accepted this phone.
                pairingTimeout?.cancel()
                _pairState.value = PairState.PAIRED
                ble.log("Hund: Kopplung angenommen (Code ${r.code}) ✔")
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
            is VoiceAction.Drive -> moveFor(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, _driveSpeed.value), action.ms)
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
                        sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_DRIVE, _driveSpeed.value)); delay(step.ms)
                        sendRaw(DogeProtocol.stopMotors())
                    }
                    is Step.Head -> {
                        sendRaw(DogeProtocol.motor(DogeProtocol.MOTOR_HEAD, 2)); delay(step.ms)
                        sendRaw(DogeProtocol.stopMotors())
                    }
                    is Step.Light -> setLedColor(step.targets, step.rgb)
                    Step.Rainbow -> rainbow()
                    is Step.Wait -> delay(step.ms)
                    Step.Joke -> tellJoke()
                    Step.Weather -> tellWeather()
                }
            }
        }
    }

    // ------------------------------------------------------------------ Internet: jokes & weather

    private val jokes = JokeRepository(app)
    private val _jokesStatus = MutableStateFlow(jokesStatusText())
    val jokesStatus: StateFlow<String> = _jokesStatus.asStateFlow()
    private val _weatherText = MutableStateFlow("")
    val weatherText: StateFlow<String> = _weatherText.asStateFlow()

    private fun jokesStatusText(): String {
        val n = jokes.all.size
        val t = jokes.lastUpdate
        return if (t == 0L) "$n Witze (eingebaut)"
        else "$n Witze, aktualisiert " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(t))
    }

    /** Downloads new jokes from the URL in the settings. */
    fun refreshJokes() {
        viewModelScope.launch {
            _jokesStatus.value = "Lade Witze …"
            _jokesStatus.value = runCatching { jokes.refresh(_config.value.jokesUrl) }
                .fold({ jokesStatusText() }, { "Fehler beim Laden: ${it.message ?: it.javaClass.simpleName}" })
        }
    }

    private suspend fun tellJoke() {
        if (jokes.isStale()) runCatching { jokes.refresh(_config.value.jokesUrl) }.onSuccess { _jokesStatus.value = jokesStatusText() }
        val j = jokes.next()
        ble.log("Witz: ${j.question} – ${j.answer}")
        _voiceHeard.value = "😄 ${j.question} ${j.answer}"
        say(j.question)
        if (j.answer.isNotBlank()) {
            delay(1200) // a little pause to think
            say(j.answer)
        }
        playSequence(if (Math.random() < 0.5) DogeProtocol.SEQ_FART else DogeProtocol.SEQ_BARK)
        delay(1500)
        say("Hihi!")
        playSequence(DogeProtocol.SEQ_WAG_TAIL)
    }

    private suspend fun tellWeather() {
        val cfg = _config.value
        if (cfg.city.isBlank()) {
            say("Ich weiß noch nicht, wo du wohnst. Trag deinen Ort in den Einstellungen ein!")
            _weatherText.value = "Kein Ort eingetragen (Einstellungen)"
            return
        }
        val result = runCatching {
            val place = WeatherApi.findPlace(cfg.city) ?: error("Ort \"${cfg.city}\" nicht gefunden")
            WeatherApi.current(place)
        }
        result.onSuccess { w ->
            val text = WeatherText.sentence(w, cfg.childName)
            _weatherText.value = text
            _voiceHeard.value = "🌦 $text"
            ble.log("Wetter: $text")
            setLedColor(LedTarget.entries.toSet(), WeatherText.ledColor(w.code, w.isDay))
            say(text)
            playSequence(DogeProtocol.SEQ_WAG_TAIL)
        }.onFailure {
            _weatherText.value = "Wetter nicht verfügbar: ${it.message}"
            ble.log("Wetter-Fehler: ${it.message}")
            say("Wuff, ich kann gerade nicht nach draußen schauen. Ist das Internet an?")
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

    // Runs last on purpose: viewModelScope starts collecting immediately (Main.immediate),
    // so every property used below must already be initialised.
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

    override fun onCleared() {
        voice.stop()
        dogVoice.shutdown()
        ble.disconnect()
        super.onCleared()
    }
}
