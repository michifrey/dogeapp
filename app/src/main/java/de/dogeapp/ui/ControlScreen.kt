package de.dogeapp.ui

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import de.dogeapp.ui.theme.CoolGradient
import de.dogeapp.ui.theme.FunGradient
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import de.dogeapp.data.DogeProtocol
import de.dogeapp.voice.Conversation
import kotlin.math.roundToInt
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.data.CommandDef
import de.dogeapp.data.DogeConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(vm: DogeViewModel, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val pair by vm.pairState.collectAsStateWithLifecycle()
    val log by vm.ble.log.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY

    BoxWithConstraints(modifier.padding(16.dp)) {
        // Tablets (e.g. Samsung Galaxy Tab) get two columns and bigger buttons.
        val wide = maxWidth >= 700.dp
        val buttonSize = if (wide) 140.dp else 96.dp

        val left: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GreetingCard(config.childName, connected, onOpenSettings)
                if (!connected) {
                    Hint("Nicht verbunden. Zuerst im Tab \"Verbinden\" den Hund (\"Mint Dog\") auswählen.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kopplung: ${pair.label()}")
                        if (pair == PairState.NOT_PAIRED || pair == PairState.UNKNOWN) {
                            OutlinedButton(onClick = { vm.requestPairing() }) { Text("Koppeln") }
                        }
                    }
                }
                // Hold to move, release sends "stop" – same as the official app.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldButton(config.command(DogeConfig.FORWARD), Icons.Filled.KeyboardArrowUp, vm, buttonSize)
                    HoldButton(config.command(DogeConfig.HEAD), Icons.Filled.Pets, vm, buttonSize)
                    HoldButton(config.command(DogeConfig.STOP), Icons.Filled.Stop, vm, buttonSize, hold = false)
                }
                Text("Gedrückt halten zum Fahren bzw. Kopf bewegen", style = MaterialTheme.typography.bodySmall)
                if (connected) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BatteryBadge(battery)
                        TextButton(onClick = { vm.requestBattery() }) { Text("Aktualisieren") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Lautstärke")
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
                    Text("$volume")
                }
                VoicePanel(vm)
            }
        }
        val right: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionTitle("🎪", "Tricks")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    config.commands.filterNot { it.id in DogeConfig.DRIVE_IDS }.forEachIndexed { i, cmd ->
                        FunButton(
                            emoji = emojiFor(cmd.id),
                            label = plainLabel(cmd.label),
                            index = i,
                            onClick = { vm.send(cmd) },
                            enabled = cmd.hex.isNotBlank(),
                            minWidth = if (wide) 130.dp else 100.dp,
                            big = wide,
                        )
                    }
                }
                SectionTitle("💬", "Gespräch")
                Text(
                    "Sag es mit Sprachsteuerung oder tippe drauf – Dog-E antwortet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Conversation.REACTIONS.forEachIndexed { i, r ->
                        FunButton(
                            emoji = emojiFor(r.id),
                            label = r.label,
                            index = i + 3,
                            onClick = { vm.runReaction(r) },
                            minWidth = if (wide) 120.dp else 96.dp,
                        )
                    }
                }
                SequenceTester(vm)
                log.lastOrNull()?.let {
                    Text("${it.time}  ${it.text}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { left() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { right() }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                left()
                right()
            }
        }
    }
}

/** Switch for voice control: the tablet listens and the dog reacts to spoken commands. */
@Composable
private fun VoicePanel(vm: DogeViewModel) {
    val context = LocalContext.current
    val on by vm.voiceOn.collectAsStateWithLifecycle()
    val listening by vm.voiceListening.collectAsStateWithLifecycle()
    val heard by vm.voiceHeard.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.setVoice(true)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (listening) Icons.Filled.Mic else Icons.Filled.MicNone,
                contentDescription = null,
                tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text("🎤 Sprachsteuerung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = on, onCheckedChange = { want ->
                if (!want) vm.setVoice(false)
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.setVoice(true)
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            })
        }
        if (speaking) Text("🗣 Dog-E spricht …", style = MaterialTheme.typography.bodyMedium)
        else if (heard.isNotBlank()) Text(heard, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Sag z. B.: \"Hallo!\", \"Erzähl einen Witz\", \"Wie ist das Wetter?\", \"Wollen wir Ball spielen?\", \"Wie geht's?\", \"Gute Nacht\", \"Bell!\", \"Furz mal!\", \"Sing\", \"Heul\", \"Wedel\", \"Komm her\", \"Kopf\", \"Stopp\", " +
                "\"Augen blau\", \"Licht rot\", \"Lauter\", \"Leiser\", \"Regenbogen\", \"Licht aus\" – oder den Namen eines eigenen Knopfs.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Plays any sequence number – the dog knows many more tricks and sounds than the buttons above. */
@Composable
private fun SequenceTester(vm: DogeViewModel) {
    var text by rememberSaveable { mutableStateOf("106") }
    val id = text.trim().let { if (it.startsWith("0x", true)) it.drop(2).toIntOrNull(16) else it.toIntOrNull() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("🔢", "Sequenz ausprobieren")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { id?.let { text = (it - 1).coerceAtLeast(0).toString() } }) { Text("−") }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = id == null || id !in 0..0xFFFF,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { id?.let { text = (it + 1).coerceAtMost(0xFFFF).toString() } }) { Text("+") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { id?.let { vm.playSequence(it) } }, enabled = id != null && id in 0..0xFFFF) { Text("Abspielen") }
        }
        Text(
            "Bekannt: 106 Bellen, 114 Heulen, 115 Singen, 116 Furzen, 168 Schwanzwedeln. " +
                "Gute Funde unter \"Einstellungen\" als neuen Knopf speichern.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun PairState.label() = when (this) {
    PairState.UNKNOWN -> "unbekannt"
    PairState.CHECKING -> "prüfe …"
    PairState.PAIRING -> "koppele … (evtl. am Hund bestätigen)"
    PairState.PAIRED -> "gekoppelt ✔"
    PairState.NOT_PAIRED -> "nicht gekoppelt"
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun HoldButton(cmd: CommandDef?, icon: ImageVector, vm: DogeViewModel, size: Dp, hold: Boolean = true) {
    val enabled = cmd != null && cmd.hex.isNotBlank()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f), label = "hold")
    val brush = when {
        !hold -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
        cmd?.id == DogeConfig.HEAD -> CoolGradient
        else -> FunGradient
    }
    Box(
        Modifier
            .scale(scale)
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .shadow(if (pressed) 2.dp else 10.dp, RoundedCornerShape(28.dp))
            .background(brush, RoundedCornerShape(28.dp))
            .pointerInput(cmd, enabled, hold) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    pressed = true
                    if (hold) {
                        vm.press(cmd!!)
                        tryAwaitRelease()
                        vm.release()
                    } else {
                        vm.send(cmd!!)
                        tryAwaitRelease()
                    }
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = cmd?.label, tint = Color.White, modifier = Modifier.size(size * 0.4f))
            Text(
                cmd?.label ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
