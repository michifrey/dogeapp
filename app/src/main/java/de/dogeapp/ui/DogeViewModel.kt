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
                } else if (s == ConnState.DISCONNECTED) {
                    _pairState.value = PairState.UNKNOWN
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

    private fun onDogData(data: ByteArray) {
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
        ble.disconnect()
        super.onCleared()
    }
}
