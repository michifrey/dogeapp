package de.dogeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.dogeapp.ble.BleManager
import de.dogeapp.ble.CharInfo
import de.dogeapp.data.CommandDef
import de.dogeapp.data.ConfigRepository
import de.dogeapp.data.DogeConfig
import de.dogeapp.data.Hex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DogeViewModel(app: Application) : AndroidViewModel(app) {

    val ble = BleManager(app)
    private val repo = ConfigRepository(app)

    private val _config = MutableStateFlow(repo.load())
    val config: StateFlow<DogeConfig> = _config.asStateFlow()

    private var holdJob: Job? = null

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
