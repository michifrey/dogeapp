package de.dogeapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.ble.FoundDevice
import de.dogeapp.ble.dogRank
import de.dogeapp.ble.looksLikeDog
import de.dogeapp.ui.theme.Mint

/**
 * Parent-facing connection screen: big status card with the steps "Suchen → Verbinden → Bereit",
 * likely dogs prominently with a big "Verbinden" button, all other devices collapsed.
 */
@Composable
fun ConnectScreen(
    vm: DogeViewModel,
    onEnableBluetooth: () -> Unit,
    onConnected: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by vm.ble.devices.collectAsStateWithLifecycle()
    val scanning by vm.ble.scanning.collectAsStateWithLifecycle()
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val name by vm.ble.connectedName.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val pair by vm.pairState.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    var waitingForConnect by remember { mutableStateOf(false) }
    var othersOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state, waitingForConnect) {
        if (waitingForConnect && state == ConnState.READY) {
            waitingForConnect = false
            onConnected()
        }
    }

    val hint = config.nameHint
    fun isDog(d: FoundDevice) = dogRank(d) <= 1 || (hint.isNotBlank() && d.name?.contains(hint, ignoreCase = true) == true)
    val dogs = devices.filter { isDog(it) }.sortedWith(compareBy({ dogRank(it) }, { -it.rssi }))
    val others = devices.filterNot { isDog(it) }
    val canConnect = state == ConnState.DISCONNECTED
    val connect: (FoundDevice) -> Unit = { d ->
        waitingForConnect = true
        vm.ble.connect(d.address)
    }
    // Every item is at most 720 dp wide and centered, so tablets don't get stretched rows.
    val itemMod = Modifier.widthIn(max = 720.dp).fillMaxWidth()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "greeting") {
            GreetingCard(config.childName, state == ConnState.READY, onOpenSettings, itemMod)
        }
        item(key = "status") {
            SectionCard(itemMod) {
                StepIndicator(state, scanning)
                when {
                    !vm.ble.isBluetoothOn -> {
                        StatusText("📴", "Bluetooth ist aus", "Schalte Bluetooth ein, damit die App Dog-E finden kann.")
                        Button(onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Bluetooth einschalten")
                        }
                    }
                    state == ConnState.READY -> {
                        StatusText("✅", "Verbunden" + (name?.let { " mit $it" } ?: ""), pair.friendly())
                        PairingPrompt(pair, onPair = { vm.requestPairing() })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BatteryBadge(battery)
                            TextButton(onClick = { vm.requestBattery() }) { Text("Aktualisieren") }
                        }
                        BrandButton("🎮 Los, spielen!", onClick = onPlay, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { vm.ble.disconnect() }) { Text("Verbindung trennen") }
                    }
                    state == ConnState.CONNECTING || state == ConnState.DISCOVERING -> {
                        StatusText(
                            "🔗",
                            "Verbinde" + (name?.let { " mit $it" } ?: "") + " …",
                            if (state == ConnState.DISCOVERING) "Fast fertig – Dog-E wird eingerichtet."
                            else "Einen Moment, Dog-E wird angerufen.",
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { vm.ble.disconnect() }) { Text("Abbrechen") }
                    }
                    scanning -> {
                        StatusText("🔍", "Suche nach Dog-E …", "Dog-E einschalten und in die Nähe stellen.")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { vm.ble.stopScan() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Suche stoppen")
                        }
                    }
                    else -> {
                        StatusText("💤", "Nicht verbunden", "Dog-E einschalten, die Original-App schließen und suchen.")
                        BrandButton("🔍 Dog-E suchen", onClick = { vm.ble.startScan() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (dogs.isNotEmpty() || scanning) {
            item(key = "dogsTitle") { SectionTitle("🐶", "Gefundene Hunde", itemMod.padding(top = 8.dp)) }
        }
        if (dogs.isEmpty() && (scanning || devices.isNotEmpty())) {
            item(key = "dogsEmpty") {
                Text(
                    if (scanning) "Noch kein Dog-E gefunden … Ist er eingeschaltet?"
                    else "Kein Dog-E gefunden. Hund aus- und wieder einschalten, Original-App überall schließen, dann nochmal suchen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = itemMod,
                )
            }
        }
        items(dogs, key = { it.address }) { d ->
            DogDeviceCard(d, enabled = canConnect, onConnect = { connect(d) }, modifier = itemMod)
        }

        // Other Bluetooth devices are hidden by default; only a small link for experts.
        if (others.isNotEmpty()) {
            item(key = "othersTitle") {
                TextButton(onClick = { othersOpen = !othersOpen }, modifier = itemMod.padding(top = 8.dp)) {
                    Text(
                        if (othersOpen) "Andere Bluetooth-Geräte ausblenden"
                        else "Andere Bluetooth-Geräte anzeigen (${others.size})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        if (othersOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (othersOpen) {
                items(others, key = { it.address }) { d ->
                    OtherDeviceRow(d, enabled = canConnect, onConnect = { connect(d) }, modifier = itemMod)
                }
            }
        }
    }
}

/** Three dots "Suchen → Verbinden → Bereit"; the current step is highlighted, done steps get a tick. */
@Composable
private fun StepIndicator(state: ConnState, scanning: Boolean) {
    val current = when (state) {
        ConnState.DISCONNECTED -> if (scanning) 0 else -1
        ConnState.CONNECTING, ConnState.DISCOVERING -> 1
        ConnState.READY -> 3 // all done
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOf("Suchen", "Verbinden", "Bereit").forEachIndexed { i, label ->
            val done = i < current || current == 3
            val active = i == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(
                            when {
                                done -> Mint
                                active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done) "✓" else "${i + 1}",
                        color = if (done || active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusText(emoji: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 36.sp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun signalLabel(rssi: Int) = when {
    rssi >= -60 -> "Signal stark"
    rssi >= -80 -> "Signal mittel"
    else -> "Signal schwach"
}

@Composable
private fun DogDeviceCard(d: FoundDevice, enabled: Boolean, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🐶", fontSize = 36.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.name ?: "Dog-E (ohne Namen)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "📶 ${signalLabel(d.rssi)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onConnect, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("Verbinden", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Technical row for devices that are probably not the dog (developer info visible). */
@Composable
private fun OtherDeviceRow(d: FoundDevice, enabled: Boolean, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                d.name ?: if (looksLikeDog(d)) "(ohne Namen – vermutlich Dog-E)" else "(ohne Namen)",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${d.address} · ${d.rssi} dBm",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (d.serviceUuids.isNotEmpty()) {
                Text(
                    "Services: ${d.serviceUuids.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            d.manufacturerData?.let {
                Text(
                    "Hersteller-Daten: $it",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onConnect, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Verbinden", textAlign = TextAlign.Center)
        }
    }
}

/**
 * Pairing like the official app: after [onPair] the dog plays a chime and the nose button must be
 * pressed (the state stays PAIRING for up to 30 s). Shows nothing once paired.
 */
@Composable
fun PairingPrompt(pair: PairState, onPair: () -> Unit, modifier: Modifier = Modifier) {
    when (pair) {
        PairState.PAIRED -> Unit
        PairState.CHECKING -> Text(
            "🐽 Dog-E schnuppert … (Kopplung wird geprüft)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        PairState.PAIRING -> {
            // The nose emoji pulses so the child knows exactly what to do.
            val nose by rememberInfiniteTransition(label = "nose").animateFloat(
                initialValue = 0.85f, targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "noseScale",
            )
            Row(
                modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👃", fontSize = 48.sp, modifier = Modifier.scale(nose))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "🔔 Hörst du den Klingelton?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "Drück jetzt Dog-Es Nase!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        PairState.NOT_PAIRED, PairState.UNKNOWN -> Row(
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🤝", fontSize = 36.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Dog-E kennt dieses Gerät noch nicht",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Tippe auf „Koppeln“ und drück danach Dog-Es Nase. Vorher klappt das Fahren evtl. nicht.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onPair, modifier = Modifier.heightIn(min = 56.dp)) { Text("Koppeln") }
        }
    }
}

/** Kid- and parent-friendly wording for the pairing state. */
fun PairState.friendly(): String = when (this) {
    PairState.UNKNOWN -> "Dog-E kennt dieses Gerät vielleicht noch nicht."
    PairState.CHECKING -> "Dog-E schnuppert … (Kopplung wird geprüft)"
    PairState.PAIRING -> "Drück jetzt Dog-Es Nase, um zu koppeln! 👃"
    PairState.PAIRED -> "Dog-E und dieses Gerät sind Freunde ✔"
    PairState.NOT_PAIRED -> "Dog-E kennt dieses Gerät noch nicht."
}

fun ConnState.label() = when (this) {
    ConnState.DISCONNECTED -> "nicht verbunden"
    ConnState.CONNECTING -> "verbinde …"
    ConnState.DISCOVERING -> "lese Services …"
    ConnState.READY -> "verbunden"
}
