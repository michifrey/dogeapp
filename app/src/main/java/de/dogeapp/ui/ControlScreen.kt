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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.data.DogeConfig
import de.dogeapp.data.DogeProtocol
import de.dogeapp.ui.theme.DisplayFont
import de.dogeapp.ui.theme.Ink
import de.dogeapp.ui.theme.Mustard
import de.dogeapp.ui.theme.Orange
import de.dogeapp.ui.theme.pastel
import de.dogeapp.voice.Conversation
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * The child's main screen: drive, tricks and talking – styled like a collage poster.
 *
 * Not connected → "WO IST DOG-E?" empty state with the mascot and one big orange pill.
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
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    BoxWithConstraints(modifier) {
        val width = maxWidth
        val compact = maxHeight < 520.dp // e.g. phone in landscape: smaller drive buttons

        if (!connected && !preview) {
            ConnectCallToAction(
                name = config.childName,
                state = state,
                wide = width >= 700.dp,
                onConnect = onConnect,
                onOpenSettings = onOpenSettings,
                onPreview = { preview = true },
            )
        } else {
            val (big, script) = posterHello(config.childName, hour)
            // Tongue-in-cheek sticker that still tells the parent the pairing state.
            val (stickerText, stickerNote) = when {
                !connected -> "Nur Vorschau*" to "*kein Hund da"
                pair == PairState.PAIRED -> "Bester Hund*" to "*offiziell"
                else -> "Fast Freunde*" to "*erst koppeln"
            }
            val large = width >= 700.dp && maxHeight >= 640.dp
            val header: @Composable () -> Unit = {
                PlayHero(
                    big = big,
                    script = script,
                    stickerText = stickerText,
                    stickerNote = stickerNote,
                    large = large,
                    connected = connected,
                    showNameHint = config.childName.isBlank(),
                    onConnect = onConnect,
                    onOpenSettings = onOpenSettings,
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

            // Short wide screens (phone in landscape) scroll the hero away with the first column.
            val heroOnTop = maxHeight >= 640.dp
            when {
                width >= 1000.dp -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (heroOnTop) header()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ScrollColumn(Modifier.weight(1f)) { if (!heroOnTop) header(); drive(); volume() }
                        ScrollColumn(Modifier.weight(1f)) { tricks(150.dp) }
                        ScrollColumn(Modifier.weight(1f)) { talk(150.dp) }
                    }
                }
                width >= 700.dp -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (heroOnTop) header()
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScrollColumn(Modifier.weight(1f)) { if (!heroOnTop) header(); drive(); volume() }
                        ScrollColumn(Modifier.weight(1.2f)) { tricks(150.dp); talk(150.dp) }
                    }
                }
                else -> ScrollColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    header()
                    drive()
                    tricks(140.dp)
                    talk(140.dp)
                    volume()
                }
            }
        }
    }
}

/** Vertically scrolling column with the standard gap; extra space at the end for the feedback banner. */
@Composable
private fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        Spacer(Modifier.height(64.dp))
    }
}

/**
 * Poster hero of the play screen: "HALLO YARA!" + "lass uns spielen!", the mascot overlapping
 * the next card and a mustard sticker.
 */
@Composable
private fun PlayHero(
    big: String,
    script: String,
    stickerText: String,
    stickerNote: String,
    large: Boolean,
    connected: Boolean,
    showNameHint: Boolean,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // zIndex: the mascot hangs over the top edge of the drive card below.
    Row(Modifier.fillMaxWidth().zIndex(1f), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PosterHeadline(
                big,
                script,
                bigSize = if (large) 64.sp else 46.sp,
                scriptSize = if (large) 40.sp else 30.sp,
            )
            when {
                !connected -> PillButton("Dog-E verbinden", onClick = onConnect, minHeight = 56.dp)
                showNameHint -> InkTextButton("✏️ Wie heißt du? Namen eintragen", onClick = onOpenSettings)
            }
        }
        Box(Modifier.size(width = if (large) 210.dp else 136.dp, height = if (large) 220.dp else 156.dp)) {
            ConfettiDots(Modifier.align(Alignment.TopEnd).size(if (large) 80.dp else 56.dp))
            Mascot(
                Modifier.align(Alignment.BottomEnd).offset(y = 26.dp),
                size = if (large) 170.dp else 112.dp,
            )
            Sticker(
                stickerText,
                Modifier.align(Alignment.TopStart).offset(x = (-12).dp),
                footnote = stickerNote,
                diameter = if (large) 104.dp else 88.dp,
                rotation = -12f,
            )
        }
    }
}

/** Empty state: "WO IST DOG-E?" + "schalt mich ein!" with the mascot and one big orange pill. */
@Composable
private fun ConnectCallToAction(
    name: String,
    state: ConnState,
    wide: Boolean,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreview: () -> Unit,
) {
    val connecting = state == ConnState.CONNECTING || state == ConnState.DISCOVERING
    val big = if (connecting) "Gleich da!" else "Wo ist Dog-E?"
    val script = if (connecting) "einen Moment …" else "schalt mich ein!"

    val mascot: @Composable (Dp) -> Unit = { size ->
        Box {
            MascotWithNote(if (connecting) "wuff?" else "hallo?", size = size)
            ConfettiDots(Modifier.align(Alignment.TopEnd).size(64.dp))
            Sticker(
                if (connecting) "Fast da*" else "Wartet schon*",
                Modifier.align(Alignment.BottomStart).offset(x = (-24).dp, y = (-8).dp),
                footnote = if (connecting) "*ehrlich" else "*seit Ewigkeiten",
                diameter = 92.dp,
                rotation = -10f,
            )
        }
    }
    val texts: @Composable ColumnScope.(Boolean) -> Unit = { centered ->
        if (name.isNotBlank()) {
            ScriptNote("Hallo $name!", rotation = -4f, color = Orange, fontSize = 28.sp)
        }
        PosterHeadline(big, script, centered = centered, bigSize = if (wide) 64.sp else 52.sp, scriptSize = 34.sp)
        Text(
            if (connecting) "Einen Moment bitte, gleich kann's losgehen."
            else "Schalte Dog-E ein und tippe auf den großen Knopf.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (connecting) {
            OrangeBar(null, Modifier.padding(vertical = 8.dp))
        } else {
            PillButton("Dog-E verbinden", onClick = onConnect, modifier = Modifier.fillMaxWidth())
        }
        InkTextButton("Ohne Hund umschauen", onClick = onPreview)
        if (name.isBlank()) InkTextButton("✏️ Namen eintragen", onClick = onOpenSettings)
    }

    if (wide) {
        Row(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mascot(240.dp)
            Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { texts(false) }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            mascot(170.dp)
            Column(
                Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { texts(true) }
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Driving
// ---------------------------------------------------------------------------------------------

private data class Speed(val value: Int, val emoji: String, val label: String, val tilt: Float)

private val SPEEDS = listOf(
    Speed(1, "🐢", "Langsam", -8f),
    Speed(2, "🐕", "Mittel", 5f),
    Speed(3, "🚀", "Schnell", -4f),
)

/** Speed stickers, big hold-to-drive pad, hold-for-head pad and the bold stop button. */
@Composable
private fun DriveCard(vm: DogeViewModel, connected: Boolean, compact: Boolean) {
    val speed by vm.driveSpeed.collectAsStateWithLifecycle()
    val driving by vm.driving.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current
    val notConnected: () -> Unit = { if (!connected) feedback.show("🔌 Erst Dog-E verbinden") }
    val ink = inkColor()

    PaperCard(spacing = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("🏁", "Fahren", colorIndex = 4)
            Spacer(Modifier.weight(1f))
            Caption("wie schnell?", Modifier.padding(end = 4.dp))
        }
        SpeedStickers(speed, compact) { vm.setDriveSpeed(it) }
        // The dog can only drive forward (like the official app), so there is one big drive button.
        Row(
            Modifier.fillMaxWidth().height(if (compact) 112.dp else 144.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoldPad(
                brush = SolidColor(MaterialTheme.colorScheme.primary),
                onPress = { notConnected(); vm.driveStart() },
                onRelease = { vm.driveStop() },
                outline = ink,
                modifier = Modifier.weight(1.6f).fillMaxHeight(),
            ) { pressed ->
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 32.dp else 40.dp),
                )
                Text(
                    if (pressed || driving) "FÄHRT! 🐾" else "FAHREN",
                    color = Color.White,
                    fontFamily = DisplayFont,
                    fontSize = if (compact) 26.sp else 32.sp,
                    maxLines = 1,
                )
                Caption("gedrückt halten", color = Color.White)
            }
            HoldPad(
                brush = SolidColor(Mustard),
                onPress = { notConnected(); vm.headStart() },
                onRelease = { vm.headStop() },
                outline = ink,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { _ ->
                Text("🐶", fontSize = if (compact) 26.sp else 32.sp)
                Text("KOPF", color = Ink, fontFamily = DisplayFont, fontSize = if (compact) 24.sp else 28.sp, maxLines = 1)
                Caption("halten", color = Ink)
            }
        }
        StopButton(onClick = { vm.driveStop(); feedback.show("✋ Stopp!") })
    }
}

/** Three tilted round stickers 🐢 🐕 🚀; the chosen one is mustard yellow. */
@Composable
private fun SpeedStickers(speed: Int, compact: Boolean, onSelect: (Int) -> Unit) {
    val ink = inkColor()
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SPEEDS.forEach { s ->
            val selected = s.value == speed
            StickerBase(
                Modifier
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(s.value) }),
                diameter = if (compact) 76.dp else 88.dp,
                rotation = s.tilt,
                fill = if (selected) Mustard else MaterialTheme.colorScheme.surface,
                ring = if (selected) Ink else outline,
                outline = if (selected) null else outline,
            ) {
                Text(s.emoji, fontSize = if (compact) 20.sp else 24.sp)
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Ink else ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Bold ink pill with an orange stop sign and "STOPP" in the poster font. */
@Composable
private fun StopButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(inkColor())
            .border(3.dp, Orange, shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(Orange, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("STOPP", color = MaterialTheme.colorScheme.background, fontFamily = DisplayFont, fontSize = 30.sp)
    }
}

@Composable
private fun VolumeCard(vm: DogeViewModel) {
    val volume by vm.volume.collectAsStateWithLifecycle()
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle("🔈", pastel(1), size = 40.dp)
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
            Text("🔊 $volume", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
    SectionCard(emoji = "🎪", title = "Tricks", colorIndex = 0) {
        if (tricks.isEmpty()) {
            Text("Noch keine Tricks – in den Einstellungen anlegen.", style = MaterialTheme.typography.bodyMedium)
        }
        EqualGrid(tricks, minCellWidth = minCell) { i, cmd ->
            val emoji = emojiFor(cmd.id)
            val label = shortLabel(cmd)
            ToyTile(
                emoji = emoji,
                label = label,
                accent = pastel(i),
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
    SectionCard(emoji = "💬", title = "Reden", colorIndex = 3) {
        VoiceCard(vm)
        EqualGrid(Conversation.REACTIONS, minCellWidth = minCell) { i, r ->
            val emoji = emojiFor(r.id)
            ToyTile(
                emoji = emoji,
                label = r.label,
                accent = pastel(i + 3),
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
    val ink = inkColor()

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
                    .size(60.dp)
                    .alpha(if (on && listening && !speaking) pulse else 1f)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(2.dp, ink, CircleShape)
                    .clickable(role = Role.Switch, onClick = { toggle(!on) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (on) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (on) "Zuhören ausschalten" else "Zuhören einschalten",
                    tint = if (on) Color.White else ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("🎤 Sprechen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                "„$heard“",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        InkTextButton(
            if (showExamples) "Beispiele ausblenden" else "💡 Was kann ich sagen?",
            onClick = { showExamples = !showExamples },
        )
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
