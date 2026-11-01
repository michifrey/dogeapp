package de.dogeapp.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import de.dogeapp.data.Hex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 0 = name looks like the dog, 1 = offers the dog's BLE services, 2 = other named, 3 = unnamed. */
fun dogRank(d: FoundDevice): Int = when {
    d.name?.let { n -> n.contains("dog", true) || n.contains("mint", true) } == true -> 0
    looksLikeDog(d) -> 1
    d.name != null -> 2
    else -> 3
}

/** The Dog-E uses services FFE0 (notify) and FFE5 (write); some firmwares advertise them. */
fun looksLikeDog(d: FoundDevice): Boolean =
    d.serviceUuids.any { it.startsWith("0000ffe0", true) || it.startsWith("0000ffe5", true) }

data class FoundDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturerData: String?,
)

data class CharInfo(
    val serviceUuid: String,
    val uuid: String,
    val properties: Int,
) {
    val canWrite get() = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    val canWriteNoResponse get() = properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
    val canRead get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    val canNotify get() = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    val canIndicate get() = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

    val propertyLabel: String
        get() = buildList {
            if (canRead) add("READ")
            if (canWrite) add("WRITE")
            if (canWriteNoResponse) add("WRITE_NR")
            if (canNotify) add("NOTIFY")
            if (canIndicate) add("INDICATE")
        }.joinToString(" ")
}

enum class ConnState { DISCONNECTED, CONNECTING, DISCOVERING, READY }

data class LogLine(val time: String, val text: String)

/**
 * Small BLE helper: scanning, connecting, GATT exploration, writing and notifications.
 *
 * Android only allows one GATT operation at a time, so all reads, writes and descriptor writes go
 * through [queue] and the next one starts when the callback for the previous one arrives.
 * All permission checks happen in the UI before these methods are called.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val main = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    private val _chars = MutableStateFlow<List<CharInfo>>(emptyList())
    val chars: StateFlow<List<CharInfo>> = _chars.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    /** Every notification as (characteristic UUID, value). */
    private val _notifications = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 64)
    val notifications: SharedFlow<Pair<String, ByteArray>> = _notifications

    private var gatt: BluetoothGatt? = null
    private val queue = ArrayDeque<() -> Boolean>()
    private var opRunning = false
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    val isBluetoothOn: Boolean get() = adapter?.isEnabled == true

    // ---------------------------------------------------------------- Logging

    fun log(text: String) {
        val line = LogLine(timeFmt.format(Date()), text)
        _log.update { (it + line).takeLast(MAX_LOG) }
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    // ---------------------------------------------------------------- Scan

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val manu = record?.manufacturerSpecificData?.let { sparse ->
                (0 until sparse.size()).joinToString("; ") { i ->
                    "%04X: %s".format(sparse.keyAt(i), Hex.format(sparse.valueAt(i)))
                }
            }?.ifBlank { null }
            val device = FoundDevice(
                address = result.device.address,
                name = record?.deviceName ?: result.device.name,
                rssi = result.rssi,
                serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
                manufacturerData = manu,
            )
            _devices.update { list ->
                (list.filterNot { it.address == device.address } + device)
                    .sortedWith(compareBy<FoundDevice> { dogRank(it) }.thenByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scan fehlgeschlagen (Code $errorCode)")
            _scanning.value = false
        }
    }

    private val scanTimeout = Runnable { stopScan() }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            log("Bluetooth ist aus oder nicht verfügbar")
            return
        }
        if (_scanning.value) return
        _devices.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
        _scanning.value = true
        log("Scan gestartet")
        main.removeCallbacks(scanTimeout) // an old timer must not stop this new scan early
        main.postDelayed(scanTimeout, SCAN_DURATION_MS)
    }

    fun stopScan() {
        main.removeCallbacks(scanTimeout)
        if (!_scanning.value) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
        log("Scan beendet (${_devices.value.size} Geräte)")
    }

    // ---------------------------------------------------------------- Connection

    // Connection bookkeeping (all touched on the main thread only).
    private var targetAddress: String? = null
    private var manualDisconnect = false
    private var connectAttempt = 0
    private var reconnects = 0
    private var servicesRequested = false
    private val connectTimeout = Runnable {
        if (_state.value == ConnState.CONNECTING || _state.value == ConnState.DISCOVERING) {
            log("Zeitüberschreitung beim Verbinden")
            retryOrGiveUp("Timeout")
        }
    }

    fun connect(address: String) {
        stopScan()
        closeGatt()
        targetAddress = address
        manualDisconnect = false
        connectAttempt = 0
        reconnects = 0
        openGatt()
    }

    private fun openGatt() {
        val address = targetAddress ?: return
        val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        connectAttempt++
        servicesRequested = false
        _connectedName.value = device.name ?: address
        _state.value = ConnState.CONNECTING
        log("Verbinde mit ${device.name ?: "?"} ($address) … Versuch $connectAttempt")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        main.removeCallbacks(connectTimeout)
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    /** Status 133 / timeouts are common on Android (esp. Samsung): close cleanly and try again. */
    private fun retryOrGiveUp(reason: String) {
        closeGatt()
        if (!manualDisconnect && connectAttempt < MAX_CONNECT_ATTEMPTS) {
            log("$reason – neuer Versuch in 1 s")
            _state.value = ConnState.CONNECTING
            main.postDelayed({ if (!manualDisconnect) openGatt() }, 1000)
        } else {
            log("Verbindung fehlgeschlagen ($reason). Hund aus- und einschalten und nochmal versuchen.")
            _state.value = ConnState.DISCONNECTED
        }
    }

    private fun closeGatt() {
        main.removeCallbacks(connectTimeout)
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        queue.clear()
        opRunning = false
        currentOpToken = null
        _chars.value = emptyList()
    }

    fun disconnect() {
        manualDisconnect = true
        if (gatt != null) log("Trenne Verbindung")
        closeGatt()
        _state.value = ConnState.DISCONNECTED
    }

    private fun startServiceDiscovery(g: BluetoothGatt) {
        if (servicesRequested || g != gatt) return
        servicesRequested = true
        // Samsung stacks are more reliable with a short pause before discovery.
        main.postDelayed({ if (g == gatt && !g.discoverServices()) retryOrGiveUp("Service-Suche nicht gestartet") }, 600)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g != gatt) {
                    // Callback from an old connection: just make sure it is closed.
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) runCatching { g.close() }
                    return@post
                }
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        log("Verbunden, suche Services …")
                        _state.value = ConnState.DISCOVERING
                        // Larger MTU helps with long notifications; discovery starts in onMtuChanged
                        // (or right away if the request is refused).
                        if (!g.requestMtu(185)) startServiceDiscovery(g)
                        else main.postDelayed({ startServiceDiscovery(g) }, 1500) // if onMtuChanged never comes
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> {
                        val wasReady = _state.value == ConnState.READY
                        log("Verbindung getrennt (Status $status${statusHint(status)})")
                        if (manualDisconnect) {
                            closeGatt(); _state.value = ConnState.DISCONNECTED
                        } else if (wasReady && reconnects < MAX_RECONNECTS) {
                            // The dog dropped out during play: reconnect automatically.
                            reconnects++
                            connectAttempt = 0
                            retryOrGiveUp("Hund weg")
                        } else if (!wasReady) {
                            retryOrGiveUp("Status $status")
                        } else {
                            closeGatt(); _state.value = ConnState.DISCONNECTED
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post {
                if (g != gatt) return@post
                log("MTU = $mtu")
                startServiceDiscovery(g) // ignored if discovery already started (MTU can change twice)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g != gatt || _state.value == ConnState.READY) return@post
                if (status != BluetoothGatt.GATT_SUCCESS || g.services.isEmpty()) {
                    log("Service-Suche fehlgeschlagen (Status $status)")
                    retryOrGiveUp("Service-Suche")
                    return@post
                }
                main.removeCallbacks(connectTimeout)
                connectAttempt = 0
                val list = g.services.flatMap { s ->
                    s.characteristics.map { c -> CharInfo(s.uuid.toString(), c.uuid.toString(), c.properties) }
                }
                _chars.value = list
                log("${g.services.size} Services, ${list.size} Characteristics gefunden")
                g.services.forEach { s ->
                    log("Service ${shortUuid(s.uuid.toString())}")
                    s.characteristics.forEach { c ->
                        val info = list.first { it.serviceUuid == s.uuid.toString() && it.uuid == c.uuid.toString() }
                        log("   ↳ ${shortUuid(info.uuid)}  [${info.propertyLabel}]")
                    }
                }
                if (g.getService(UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb")) == null) {
                    log("⚠ Dieses Gerät hat den Dog-E-Steuerkanal FFE5 nicht – ist es wirklich der Hund?")
                }
                _state.value = ConnState.READY
                // Subscribe to everything that can notify, so we see every answer from the dog.
                list.filter { it.canNotify || it.canIndicate }.forEach { setNotify(it.serviceUuid, it.uuid, true) }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (status != BluetoothGatt.GATT_SUCCESS) log("Schreiben auf ${shortUuid(c.uuid.toString())} fehlgeschlagen ($status)")
                opDone()
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            main.post {
                log("READ ${shortUuid(c.uuid.toString())}: ${Hex.format(value)}  \"${printable(value)}\"")
                opDone()
            }
        }

        @Deprecated("Used on Android < 13")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            onCharacteristicRead(g, c, c.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post {
                log("◀ ${shortUuid(c.uuid.toString())}: ${Hex.format(copy)}")
                _notifications.tryEmit(c.uuid.toString() to copy)
            }
        }

        @Deprecated("Used on Android < 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            onCharacteristicChanged(g, c, c.value ?: ByteArray(0))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post {
                log("Benachrichtigungen ${shortUuid(d.characteristic.uuid.toString())}: ${if (status == 0) "aktiv" else "Fehler $status"}")
                opDone()
            }
        }
    }

    // ---------------------------------------------------------------- Operation queue

    private fun enqueue(op: () -> Boolean) {
        main.post {
            queue.addLast(op)
            if (!opRunning) nextOp()
        }
    }

    private fun nextOp() {
        while (queue.isNotEmpty()) {
            val op = queue.removeFirst()
            opRunning = true
            if (op()) {
                // Safety net: if the callback never comes, do not block the queue forever.
                val token = Any()
                currentOpToken = token
                main.postDelayed({ if (currentOpToken === token) opDone() }, OP_TIMEOUT_MS)
                return
            }
            opRunning = false
        }
    }

    private var currentOpToken: Any? = null

    private fun opDone() {
        currentOpToken = null
        opRunning = false
        nextOp()
    }

    private fun find(service: String, char: String): BluetoothGattCharacteristic? =
        gatt?.getService(UUID.fromString(service))?.getCharacteristic(UUID.fromString(char))

    // ---------------------------------------------------------------- Public GATT operations

    /** Writes [data]. Returns false right away if not connected or the characteristic is unknown. */
    fun write(service: String, char: String, data: ByteArray, withoutResponse: Boolean): Boolean {
        val g = gatt ?: return false
        val c = runCatching { find(service, char) }.getOrNull() ?: run {
            log("Characteristic ${shortUuid(char)} nicht gefunden")
            return false
        }
        val type = if (withoutResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        enqueue {
            if (g != gatt) return@enqueue false
            log("▶ ${shortUuid(char)}: ${Hex.format(data)}")
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var rc = g.writeCharacteristic(c, data, type)
                // The stack can still be busy with the previous write: retry briefly instead of dropping it.
                var tries = 0
                while (rc == WRITE_BUSY && tries < 5) {
                    Thread.sleep(15)
                    rc = g.writeCharacteristic(c, data, type)
                    tries++
                }
                rc == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = type
                    c.value = data
                    g.writeCharacteristic(c)
                }
            }
            if (!ok) log("Schreiben konnte nicht gestartet werden")
            ok
        }
        return true
    }

    fun read(service: String, char: String) {
        val g = gatt ?: return
        val c = find(service, char) ?: return
        enqueue { g.readCharacteristic(c) }
    }

    fun setNotify(service: String, char: String, enable: Boolean) {
        val g = gatt ?: return
        val c = find(service, char) ?: return
        enqueue {
            g.setCharacteristicNotification(c, enable)
            val cccd = c.getDescriptor(CCCD) ?: return@enqueue false
            val value = when {
                !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = value
                    g.writeDescriptor(cccd)
                }
            }
        }
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_DURATION_MS = 15_000L
        private const val OP_TIMEOUT_MS = 2_000L
        private const val MAX_LOG = 500
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val MAX_CONNECT_ATTEMPTS = 3
        private const val MAX_RECONNECTS = 3
        /** BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY (API 33+). */
        private const val WRITE_BUSY = 201

        private fun statusHint(status: Int) = when (status) {
            0 -> ""
            8 -> " – Verbindung abgelaufen/außer Reichweite"
            19 -> " – vom Hund beendet"
            22 -> " – vom Handy beendet"
            62 -> " – Verbindungsaufbau fehlgeschlagen"
            133 -> " – Android-Bluetooth-Fehler, neuer Versuch"
            else -> ""
        }

        /** "0000ffe5-0000-1000-8000-00805f9b34fb" → "FFE5"; custom 128-bit UUIDs stay as they are. */
        fun shortUuid(uuid: String): String {
            val u = uuid.lowercase()
            return if (u.startsWith("0000") && u.endsWith("-0000-1000-8000-00805f9b34fb")) u.substring(4, 8).uppercase() else u
        }

        private fun printable(b: ByteArray) = String(b.map { if (it in 32..126) it.toInt().toChar() else '.' }.toCharArray())
    }
}
