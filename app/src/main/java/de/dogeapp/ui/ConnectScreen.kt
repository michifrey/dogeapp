package de.dogeapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
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
import de.dogeapp.ui.theme.DoneGreen
import de.dogeapp.ui.theme.pastel

/**
 * Parent-facing connection screen: poster headline with the mascot, progress card with the steps
 * "Suchen → Verbinden → Koppeln", likely dogs prominently with a big "Verbinden" pill, all other
 * devices collapsed.
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
    val btOn = vm.ble.isBluetoothOn
    val connecting = state == ConnState.CONNECTING || state == ConnState.DISCOVERING
    // Every item is at most 720 dp wide and centered, so tablets don't get stretched rows.
    val itemMod = Modifier.widthIn(max = 720.dp).fillMaxWidth()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "hero") {
            val (big, script, note) = when {
                !btOn -> Triple("Bluetooth ist aus", "einmal einschalten, bitte", "hilfe.")
                state == ConnState.READY -> Triple("Dog-E ist da!", "ihr seid verbunden", "Los geht's!")
                connecting -> Triple("Gleich geht's los", "Dog-E wird angerufen …", "wuff?")
                scanning -> Triple("Wo ist Dog-E?", "ich schnüffel mal …", "hier?")
                else -> Triple("Wo ist Dog-E?", "schalt mich ein!", "hallo?")
            }
            ConnectHero(
                big = big,
                script = script,
                note = note,
                childName = config.childName,
                onOpenSettings = onOpenSettings,
                modifier = itemMod,
            )
        }
        item(key = "status") {
            PaperCard(itemMod, spacing = 14.dp) {
                StepRows(state, scanning, pair)
                when {
                    !btOn -> {
                        StatusText("📴", "Bluetooth ist aus", "Schalte Bluetooth ein, damit die App Dog-E finden kann.")
                        PillButton("Bluetooth einschalten", onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth(), trailing = "→")
                    }
                    state == ConnState.READY -> {
                        StatusText("✅", "Verbunden" + (name?.let { " mit $it" } ?: ""), pair.friendly())
                        PairingPrompt(pair, onPair = { vm.requestPairing() })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BatteryBadge(battery)
                            Spacer(Modifier.weight(1f))
                            InkTextButton("Aktualisieren", onClick = { vm.requestBattery() })
                        }
                        PillButton("Los, spielen!", onClick = onPlay, modifier = Modifier.fillMaxWidth(), trailing = "→")
                        InkTextButton("Verbindung trennen", onClick = { vm.ble.disconnect() }, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    connecting -> {
                        StatusText(
                            "🔗",
                            "Verbinde" + (name?.let { " mit $it" } ?: "") + " …",
                            if (state == ConnState.DISCOVERING) "Fast fertig – Dog-E wird eingerichtet."
                            else "Einen Moment, Dog-E wird angerufen.",
                        )
                        InkTextButton("Abbrechen", onClick = { vm.ble.disconnect() }, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    scanning -> {
                        StatusText("🔍", "Suche nach Dog-E …", "Dog-E einschalten und in die Nähe stellen.")
                        PillButton(
                            "Suche stoppen",
                            onClick = { vm.ble.stopScan() },
                            modifier = Modifier.fillMaxWidth(),
                            style = PillStyle.Outline,
                            trailing = null,
                            minHeight = 56.dp,
                        )
                    }
                    else -> {
                        StatusText("💤", "Nicht verbunden", "Dog-E einschalten, die Original-App schließen und suchen.")
                        PillButton("Dog-E suchen", onClick = { vm.ble.startScan() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (dogs.isNotEmpty() || scanning) {
            item(key = "dogsTitle") { SectionTitle("🐶", "Gefundene Hunde", itemMod.padding(top = 8.dp), colorIndex = 2) }
        }
        if (dogs.isEmpty() && (scanning || devices.isNotEmpty())) {
            item(key = "dogsEmpty") {
                Text(
                    if (scanning) "Noch kein Dog-E gefunden … Ist er eingeschaltet?"
                    else "Kein Dog-E gefunden. Hund aus- und wieder einschalten, Original-App überall schließen, dann nochmal suchen.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
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
                TextButton(
                    onClick = { othersOpen = !othersOpen },
                    modifier = itemMod.padding(top = 8.dp).heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = inkColor()),
                ) {
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

/** Poster headline left, mascot with a hand-written note right. */
@Composable
private fun ConnectHero(
    big: String,
    script: String,
    note: String,
    childName: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (childName.isNotBlank()) {
                ScriptNote("Hallo $childName!", rotation = -4f, color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp)
            }
            PosterHeadline(big, script, bigSize = 46.sp, scriptSize = 30.sp)
            if (childName.isBlank()) InkTextButton("✏️ Namen eintragen", onClick = onOpenSettings)
        }
        Box {
            MascotWithNote(note, size = 120.dp)
            DoodleBurst(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 10.dp))
        }
    }
}

/**
 * Three progress rows like the subject cards of the poster: emoji in a pastel circle, step name,
 * state on the right and an orange bar (indeterminate while the step is running).
 */
@Composable
private fun StepRows(state: ConnState, scanning: Boolean, pair: PairState) {
    // 0 = Suchen, 1 = Verbinden, 2 = Koppeln
    val connected = state == ConnState.READY
    val done = listOf(
        state != ConnState.DISCONNECTED,
        connected,
        connected && pair == PairState.PAIRED,
    )
    val active = listOf(
        state == ConnState.DISCONNECTED && scanning,
        state == ConnState.CONNECTING || state == ConnState.DISCOVERING,
        connected && (pair == PairState.PAIRING || pair == PairState.CHECKING),
    )
    val steps = listOf("🔍" to "Suchen", "🔗" to "Verbinden", "👃" to "Koppeln")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, (emoji, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(emoji, pastel(i), size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                done[i] -> "✓ fertig"
                                active[i] -> "läuft …"
                                else -> "–"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (done[i]) DoneGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OrangeBar(
                        when {
                            done[i] -> 1f
                            active[i] -> null
                            else -> 0f
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(emoji: String, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 32.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
    PaperCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle("🐶", pastel(2), size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.name ?: "Dog-E (ohne Namen)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "📶 ${signalLabel(d.rssi)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PillButton("Verbinden", onClick = onConnect, enabled = enabled, modifier = Modifier.fillMaxWidth(), minHeight = 56.dp)
    }
}

/** Technical row for devices that are probably not the dog (developer info visible). */
@Composable
private fun OtherDeviceRow(d: FoundDevice, enabled: Boolean, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    PaperCard(modifier, dashed = true, padding = 12.dp, spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    d.name ?: if (looksLikeDog(d)) "(ohne Namen – vermutlich Dog-E)" else "(ohne Namen)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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
            OutlinedButton(
                onClick = onConnect,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = inkColor()),
            ) {
                Text("Verbinden", textAlign = TextAlign.Center)
            }
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
        PairState.CHECKING -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text("🐽", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Caption("Dog-E schnuppert … (Kopplung wird geprüft)", color = MaterialTheme.colorScheme.onBackground)
        }
        PairState.PAIRING -> {
            // The nose sticker pulses so the child knows exactly what to do.
            val nose by rememberInfiniteTransition(label = "nose").animateFloat(
                initialValue = 0.9f, targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "noseScale",
            )
            PaperCard(modifier, container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StickerBase(Modifier.scale(nose), diameter = 96.dp, rotation = -10f) {
                        Text("👃", fontSize = 30.sp)
                        Text("Nase\ndrücken!", style = MaterialTheme.typography.labelMedium, color = de.dogeapp.ui.theme.Ink, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "🔔 Hörst du den Klingelton?",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "DRÜCK JETZT DOG-ES NASE!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        OrangeBar(null, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        PairState.NOT_PAIRED, PairState.UNKNOWN -> PaperCard(modifier, container = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StickerBase(diameter = 76.dp, rotation = 8f) {
                    Text("🤝", fontSize = 26.sp)
                }
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
            }
            PillButton("Koppeln", onClick = onPair, modifier = Modifier.fillMaxWidth(), trailing = "👃", minHeight = 56.dp)
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
