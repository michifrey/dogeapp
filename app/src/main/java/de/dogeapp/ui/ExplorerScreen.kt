package de.dogeapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.BleManager
import de.dogeapp.ble.CharInfo
import de.dogeapp.data.DogeConfig

/**
 * Shows every GATT characteristic of the connected device, lets you write raw bytes to it
 * and shows a live log of everything sent and received. This is the tool to find the protocol.
 */
@Composable
fun ExplorerScreen(vm: DogeViewModel, modifier: Modifier = Modifier) {
    val chars by vm.ble.chars.collectAsStateWithLifecycle()
    val log by vm.ble.log.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier.padding(horizontal = 16.dp)) {
        if (chars.isEmpty()) {
            HintBox(
                "Nicht verbunden: zuerst im Tab \"Verbinden\" ein Gerät auswählen.",
                Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(
            Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chars, key = { it.serviceUuid + it.uuid }) { c ->
                CharCard(c, isControl = isControl(c, config), vm)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Log", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { copyToClipboard(context, log.joinToString("\n") { "${it.time} ${it.text}" }) }) {
                Text("Kopieren")
            }
            TextButton(onClick = { vm.ble.clearLog() }) { Text("Leeren") }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.size - 1) }
        LazyColumn(Modifier.weight(0.8f).fillMaxWidth(), state = listState) {
            items(log) { line ->
                Text(
                    "${line.time} ${line.text}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun isControl(c: CharInfo, config: DogeConfig) =
    c.serviceUuid.equals(config.writeServiceUuid, true) && c.uuid.equals(config.writeCharUuid, true)

@Composable
private fun CharCard(c: CharInfo, isControl: Boolean, vm: DogeViewModel) {
    var hex by rememberSaveable(c.serviceUuid + c.uuid) { mutableStateOf("") }
    val writable = c.canWrite || c.canWriteNoResponse
    Card(
        Modifier.fillMaxWidth(),
        colors = if (isControl) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                BleManager.shortUuid(c.uuid) + if (isControl) "  (Steuer-Kanal)" else "",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Service ${BleManager.shortUuid(c.serviceUuid)} · ${c.propertyLabel}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (writable) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it },
                    label = { Text("Hex-Bytes, z. B. 01 FF 00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (writable) TextButton(onClick = { vm.rawWrite(c, hex) }) { Text("Senden") }
                if (c.canRead) TextButton(onClick = { vm.ble.read(c.serviceUuid, c.uuid) }) { Text("Lesen") }
                if (writable && !isControl) TextButton(onClick = { vm.useAsControlChannel(c) }) { Text("Als Steuer-Kanal") }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Dog-E", text))
}
