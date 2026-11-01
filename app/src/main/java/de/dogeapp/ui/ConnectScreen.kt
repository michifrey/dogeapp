package de.dogeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.ble.FoundDevice

@Composable
fun ConnectScreen(
    vm: DogeViewModel,
    onEnableBluetooth: () -> Unit,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by vm.ble.devices.collectAsStateWithLifecycle()
    val scanning by vm.ble.scanning.collectAsStateWithLifecycle()
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val name by vm.ble.connectedName.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    var waitingForConnect by remember { mutableStateOf(false) }

    LaunchedEffect(state, waitingForConnect) {
        if (waitingForConnect && state == ConnState.READY) {
            waitingForConnect = false
            onConnected()
        }
    }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Status: ${state.label()}${name?.let { if (state != ConnState.DISCONNECTED) " – $it" else "" } ?: ""}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!vm.ble.isBluetoothOn) {
                Button(onClick = onEnableBluetooth) { Text("Bluetooth einschalten") }
            } else if (scanning) {
                OutlinedButton(onClick = { vm.ble.stopScan() }) { Text("Scan stoppen") }
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                Button(onClick = { vm.ble.startScan() }) { Text("Nach Hund suchen") }
            }
            if (state != ConnState.DISCONNECTED) {
                OutlinedButton(onClick = { vm.ble.disconnect() }) { Text("Trennen") }
            }
        }

        Text(
            "Tipp: Hund einschalten und die Original-App schließen. Geräte, deren Name " +
                "\"${config.nameHint}\" enthält, sind hervorgehoben.",
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { d ->
                DeviceCard(d, highlight = d.name?.contains(config.nameHint, ignoreCase = true) == true) {
                    waitingForConnect = true
                    vm.ble.connect(d.address)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(d: FoundDevice, highlight: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(d.name ?: "(ohne Namen)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("${d.rssi} dBm")
            }
            Text(d.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            if (d.serviceUuids.isNotEmpty()) {
                Text("Services: ${d.serviceUuids.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            d.manufacturerData?.let {
                Text("Hersteller-Daten: $it", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun ConnState.label() = when (this) {
    ConnState.DISCONNECTED -> "nicht verbunden"
    ConnState.CONNECTING -> "verbinde …"
    ConnState.DISCOVERING -> "lese Services …"
    ConnState.READY -> "verbunden"
}
