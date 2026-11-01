package de.dogeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.data.DogeConfig
import de.dogeapp.data.DogeProtocol
import de.dogeapp.ui.theme.ButtonColors
import de.dogeapp.ui.theme.CoolGradient
import de.dogeapp.ui.theme.FunGradient
import de.dogeapp.ui.theme.StopRed
import de.dogeapp.voice.Conversation
import kotlin.math.roundToInt

/**
 * The child's main screen: drive, tricks and talking.
 *
 * Not connected → one friendly "Dog-E verbinden" call to action (plus a way to look around).
 * Phone portrait → one scrolling column, drive + stop at the top (reachable without scrolling).
 * ≥ 700 dp → two columns (drive | tricks + talk), ≥ 1000 dp → three columns (drive | tricks | talk).
 */
@Composable
fun PlayScreen(vm: DogeViewModel, onConnect: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val pair by vm.pairState.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY
    // "Ohne Hund umschauen": shows the controls without a connection (conversations still talk).
    var preview by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier) {
        val width = maxWidth
        val compact = maxHeight < 520.dp // e.g. phone in landscape: smaller drive buttons

        if (!connected && !preview) {
            ConnectCallToAction(
                name = config.childName,
                state = state,
                onConnect = onConnect,
                onOpenSettings = onOpenSettings,
                onPreview = { preview = true },
            )
        } else {
            val status = when {
                !connected -> "Vorschau – Dog-E ist nicht verbunden."
                pair == PairState.PAIRED -> "Dog-E ist bereit zum Spielen! 🎉"
                else -> "Fast bereit – erst noch koppeln."
            }
            val connectAction: (@Composable () -> Unit)? =
                if (!connected) ({ FilledTonalButton(onClick = onConnect) { Text("Verbinden") } }) else null
            val header: @Composable () -> Unit = {
                CompactGreeting(
                    name = config.childName,
                    status = status,
                    onOpenSettings = onOpenSettings,
                    trailing = connectAction,
                )
                // Driving may only work once the dog knows this device: nudge to pair first.
                if (connected && pair != PairState.PAIRED) {
                    PairingPrompt(pair, onPair = { vm.requestPairing() })
                }
            }
            val drive: @Composable () -> Unit = { DriveCard(vm, connected, compact) }
            val tricks: @Composable (Dp) -> Unit = { cell -> TricksCard(vm, connected, cell) }
            val talk: @Composable (Dp) -> Unit = { cell -> TalkCard(vm, cell) }
            val volume: @Composable () -> Unit = { VolumeCard(vm) }

            when {
                width >= 1000.dp -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScrollColumn(Modifier.weight(1f)) { drive(); volume() }
                        ScrollColumn(Modifier.weight(1f)) { tricks(112.dp) }
                        ScrollColumn(Modifier.weight(1f)) { talk(112.dp) }
                    }
                }
                width >= 700.dp -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScrollColumn(Modifier.weight(1f)) { drive(); volume() }
                        ScrollColumn(Modifier.weight(1.2f)) { tricks(104.dp); talk(104.dp) }
                    }
                }
                else -> ScrollColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 12.dp)) {
                    header()
                    drive()
                    tricks(92.dp)
                    talk(92.dp)
                    volume()
                }
            }
        }
    }
}

/** Vertically scrolling column with the standard 12 dp gap; extra space at the end for the feedback banner. */
@Composable
private fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun ConnectCallToAction(
    name: String,
    state: ConnState,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreview: () -> Unit,
) {
    val connecting = state == ConnState.CONNECTING || state == ConnState.DISCOVERING
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompactGreeting(
            name = name,
            status = if (connecting) "Gleich geht's los!" else "Dog-E wartet schon auf dich.",
            onOpenSettings = onOpenSettings,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        SectionCard(Modifier.widthIn(max = 560.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WobblyDog(size = 72.sp, emoji = if (connecting) "🐕" else "🐶")
                Text(
                    if (connecting) "Verbinde mit Dog-E …" else "Dog-E ist noch nicht verbunden",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (connecting) "Einen Moment bitte, gleich kann's losgehen."
                    else "Schalte Dog-E ein und tippe auf den großen Knopf.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (connecting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    BrandButton("🐶 Dog-E verbinden", onClick = onConnect, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = onPreview) { Text("Ohne Hund umschauen") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Driving
// ---------------------------------------------------------------------------------------------

private data class Speed(val value: Int, val emoji: String, val label: String)

private val SPEEDS = listOf(Speed(1, "🐢", "Langsam"), Speed(2, "🐕", "Mittel"), Speed(3, "🚀", "Schnell"))

/** Speed selector, big hold-to-drive button, hold-for-head button and a big stop button. */
@Composable
private fun DriveCard(vm: DogeViewModel, connected: Boolean, compact: Boolean) {
    val speed by vm.driveSpeed.collectAsStateWithLifecycle()
    val driving by vm.driving.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val notConnected: () -> Unit = { if (!connected) feedback.show("🔌 Erst Dog-E verbinden") }

    SectionCard {
        SpeedSelector(speed) { vm.setDriveSpeed(it) }
        // The dog can only drive forward (like the official app), so there is one big drive button.
        Row(
            Modifier.fillMaxWidth().height(if (compact) 112.dp else 144.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldPad(
                brush = FunGradient,
                onPress = { notConnected(); vm.driveStart() },
                onRelease = { vm.driveStop() },
                modifier = Modifier.weight(1.6f).fillMaxHeight(),
            ) { pressed ->
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 36.dp else 48.dp),
                )
                Text(
                    if (pressed || driving) "Fährt! 🐾" else "Fahren",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
                Text("gedrückt halten", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            HoldPad(
                brush = CoolGradient,
                onPress = { notConnected(); vm.headStart() },
                onRelease = { vm.headStop() },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { _ ->
                Text("🐶", fontSize = if (compact) 28.sp else 36.sp)
                Text("Kopf", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text("halten", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        StopButton(onClick = { vm.driveStop(); feedback.show("✋ Stopp!") })
    }
}

@Composable
private fun SpeedSelector(speed: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SPEEDS.forEach { s ->
            val selected = s.value == speed
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        2.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.medium,
                    )
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(s.value) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.emoji, fontSize = 22.sp)
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(StopRed)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(8.dp))
        Text("STOPP", color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun VolumeCard(vm: DogeViewModel) {
    val volume by vm.volume.collectAsStateWithLifecycle()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔈", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Slider(
                value = volume.toFloat(),
                onValueChange = { vm.setVolume(it.roundToInt(), send = false) },
                onValueChangeFinished = { vm.setVolume(vm.volume.value) },
                valueRange = 0f..DogeProtocol.VOLUME_MAX.toFloat(),
                steps = DogeProtocol.VOLUME_MAX - 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("🔊 $volume", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tricks & talking
// ---------------------------------------------------------------------------------------------

@Composable
private fun TricksCard(vm: DogeViewModel, connected: Boolean, minCell: Dp) {
    val config by vm.config.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val tricks = config.commands.filterNot { it.id in DogeConfig.DRIVE_IDS }
    SectionCard(emoji = "🎪", title = "Tricks") {
        if (tricks.isEmpty()) {
            Text("Noch keine Tricks – in den Einstellungen anlegen.", style = MaterialTheme.typography.bodyMedium)
        }
        EqualGrid(tricks, minCellWidth = minCell) { i, cmd ->
            val emoji = emojiFor(cmd.id)
            val label = shortLabel(cmd)
            ToyTile(
                emoji = emoji,
                label = label,
                accent = ButtonColors[i % ButtonColors.size],
                enabled = cmd.hex.isNotBlank(),
                onClick = {
                    vm.send(cmd)
                    feedback.show(if (connected) "$emoji $label gesendet" else "🔌 Erst Dog-E verbinden")
                },
            )
        }
    }
}

@Composable
private fun TalkCard(vm: DogeViewModel, minCell: Dp) {
    val feedback = LocalFeedback.current
    SectionCard(emoji = "💬", title = "Reden") {
        VoiceCard(vm)
        EqualGrid(Conversation.REACTIONS, minCellWidth = minCell) { i, r ->
            val emoji = emojiFor(r.id)
            ToyTile(
                emoji = emoji,
                label = r.label,
                accent = ButtonColors[(i + 3) % ButtonColors.size],
                onClick = {
                    vm.runReaction(r)
                    feedback.show("$emoji ${r.label}")
                },
            )
        }
    }
}

/** Voice control: big mic toggle, friendly status ("Ich höre zu …", "Dog-E spricht …") and the last heard text. */
@Composable
private fun VoiceCard(vm: DogeViewModel) {
    val context = LocalContext.current
    val on by vm.voiceOn.collectAsStateWithLifecycle()
    val listening by vm.voiceListening.collectAsStateWithLifecycle()
    val heard by vm.voiceHeard.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    var showExamples by rememberSaveable { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.setVoice(true)
    }
    val toggle: (Boolean) -> Unit = { want ->
        if (!want) vm.setVoice(false)
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.setVoice(true)
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // The mic button gently pulses while the tablet is listening.
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "micAlpha",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .alpha(if (on && listening && !speaking) pulse else 1f)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Switch, onClick = { toggle(!on) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (on) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (on) "Zuhören ausschalten" else "Zuhören einschalten",
                    tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("🎤 Sprechen", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !on -> "Tippe aufs Mikrofon und sprich mit Dog-E."
                        speaking -> "🗣 Dog-E spricht …"
                        listening -> "👂 Ich höre zu …"
                        else -> "⏳ Gleich höre ich wieder zu …"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = toggle)
        }
        if (heard.isNotBlank()) {
            Text(
                heard,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        TextButton(onClick = { showExamples = !showExamples }) {
            Text(if (showExamples) "Beispiele ausblenden" else "💡 Was kann ich sagen?")
        }
        if (showExamples) {
            Text(
                "\"Hallo!\", \"Erzähl einen Witz\", \"Wie ist das Wetter?\", \"Wollen wir Ball spielen?\", \"Wie geht's?\", " +
                    "\"Gute Nacht\", \"Bell!\", \"Furz mal!\", \"Sing\", \"Heul\", \"Wedel\", \"Komm her\", \"Kopf\", \"Stopp\", " +
                    "\"Augen blau\", \"Licht rot\", \"Lauter\", \"Leiser\", \"Regenbogen\", \"Licht aus\" – oder den Namen eines eigenen Knopfs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
