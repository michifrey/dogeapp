package de.dogeapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.data.CommandDef
import de.dogeapp.ui.theme.DisplayFont
import de.dogeapp.ui.theme.DoneGreen
import de.dogeapp.ui.theme.Orange
import de.dogeapp.ui.theme.ScriptStyle
import de.dogeapp.ui.theme.pastel
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Labels & emojis
// ---------------------------------------------------------------------------------------------

/** Emoji for built-in commands and reactions; own buttons get a paw. */
fun emojiFor(id: String): String = when (id) {
    "bark" -> "🐶"
    "wag" -> "🐕"
    "howl" -> "🌙"
    "sing" -> "🎵"
    "fart" -> "💨"
    "hello" -> "👋"
    "ball" -> "⚽"
    "howareyou" -> "😊"
    "name" -> "🤖"
    "goodboy" -> "⭐"
    "love" -> "💖"
    "hungry" -> "🦴"
    "dance" -> "🪩"
    "joke" -> "😂"
    "weather" -> "🌦️"
    "night" -> "😴"
    "bye" -> "👋"
    else -> "🐾"
}

/** Removes a trailing emoji that a label might already contain ("Furzen 💨" → "Furzen"). */
fun plainLabel(label: String): String = label.filter { it.isLetterOrDigit() || it.isWhitespace() || it in "-'!?.,äöüÄÖÜß" }.trim()

/**
 * Short label for a trick tile. Long built-in words are shortened so they fit a phone-sized tile
 * ("Schwanzwedeln" → "Wedeln"); own labels are shown as entered (tiles wrap to two lines).
 */
fun shortLabel(cmd: CommandDef): String {
    val plain = plainLabel(cmd.label).ifBlank { cmd.label }
    return if (cmd.id == "wag" && plain.equals("Schwanzwedeln", ignoreCase = true)) "Wedeln" else plain
}

// ---------------------------------------------------------------------------------------------
// Header: small wordmark left, compact connection + battery pills right
// ---------------------------------------------------------------------------------------------

/**
 * Slim transparent top row on the paper: "DOG-E 🐾" wordmark and compact status pills
 * (connection, battery – tap the battery to refresh). The big poster headline lives in each page.
 */
@Composable
fun TopHeader(vm: DogeViewModel) {
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val scanning by vm.ble.scanning.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY
    val ink = inkColor()
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DOG-E", fontFamily = DisplayFont, fontSize = 24.sp, color = ink, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text("🐾", fontSize = 18.sp, modifier = Modifier.rotate(-12f))
        Spacer(Modifier.weight(1f))
        StatusPill {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        when {
                            connected -> DoneGreen
                            state != ConnState.DISCONNECTED || scanning -> Orange
                            else -> MaterialTheme.colorScheme.outline
                        },
                        CircleShape,
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when (state) {
                    ConnState.READY -> "verbunden"
                    ConnState.CONNECTING, ConnState.DISCOVERING -> "verbinde …"
                    ConnState.DISCONNECTED -> if (scanning) "suche …" else "nicht verbunden"
                },
                color = ink,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        val level = battery
        if (connected && level != null) {
            Spacer(Modifier.width(6.dp))
            StatusPill(onClick = { vm.requestBattery() }) {
                Icon(
                    when {
                        level >= 70 -> Icons.Filled.BatteryFull
                        level >= 30 -> Icons.Filled.Battery5Bar
                        else -> Icons.Filled.Battery2Bar
                    },
                    contentDescription = "Akku",
                    tint = if (level < 20) MaterialTheme.colorScheme.error else ink,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text("$level %", color = ink, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** Small outlined paper pill for the header status. */
@Composable
fun StatusPill(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    var m = Modifier
        .heightIn(min = 36.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.5.dp, inkColor(), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** A dog emoji that gently tilts back and forth. */
@Composable
fun WobblyDog(size: TextUnit = 40.sp, emoji: String = "🐶") {
    val t = rememberInfiniteTransition(label = "wobble")
    val angle by t.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "angle",
    )
    Text(emoji, fontSize = size, modifier = Modifier.rotate(angle))
}

// ---------------------------------------------------------------------------------------------
// Cards & titles
// ---------------------------------------------------------------------------------------------

/** Section title: emoji in a pastel circle + condensed uppercase title. */
@Composable
fun SectionTitle(emoji: String, text: String, modifier: Modifier = Modifier, colorIndex: Int = 2) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (emoji.isNotEmpty()) {
            IconCircle(emoji, pastel(colorIndex), size = 40.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The one card style used everywhere: cream paper card with thin outline, 16 dp corners,
 * 16 dp padding, 12 dp between children. Optional emoji + title on top.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    emoji: String = "",
    title: String? = null,
    colorIndex: Int = 2,
    content: @Composable ColumnScope.() -> Unit,
) {
    PaperCard(modifier) {
        if (title != null) SectionTitle(emoji, title, colorIndex = colorIndex)
        content()
    }
}

/** Small butter-yellow hint box (e.g. "Dog-E ist nicht verbunden"). */
@Composable
fun HintBox(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .dashedBorder(MaterialTheme.colorScheme.outline, 1.5.dp, 16.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** The primary call to action of a screen: the orange pill. Kept for older call sites. */
@Composable
fun BrandButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PillButton(text, onClick, modifier, enabled = enabled, trailing = "🐾")
}

/**
 * Equal-size toy tile for tricks and conversations: cream outlined card with the emoji in a pastel
 * circle. Wide cells (≥ 150 dp) are laid out like the subject rows of the poster (circle left,
 * label right), narrow cells stack circle and label. Bounces when pressed.
 * Fills the width it gets – put it into an [EqualGrid].
 */
@Composable
fun ToyTile(
    emoji: String,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 84.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.4f), label = "press")
    val shape = MaterialTheme.shapes.medium
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (pressed) accent else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= 150.dp
        val text: @Composable (TextAlign) -> Unit = { align ->
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                // Long single words get a slightly smaller font instead of being cut off.
                fontSize = if (label.length > 11) 15.sp else 17.sp,
                lineHeight = 19.sp,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(emoji, accent, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) { text(TextAlign.Start) }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconCircle(emoji, accent, size = 44.dp)
                Spacer(Modifier.height(4.dp))
                text(TextAlign.Center)
            }
        }
    }
}

/**
 * Press-and-hold pad: [onPress] when the finger goes down, [onRelease] when it is lifted,
 * cancelled (e.g. by scrolling) or the pad leaves the screen. Used for driving and the head.
 */
@Composable
fun HoldPad(
    brush: Brush,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(28.dp),
    outline: Color? = null,
    content: @Composable ColumnScope.(pressed: Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.45f), label = "hold")
    var m = modifier
        .scale(scale)
        .alpha(if (enabled) 1f else 0.4f)
        .clip(shape)
        .background(brush)
        // Darken a little while pressed so the child sees the button is "on".
        .background(if (pressed) Color.Black.copy(alpha = 0.18f) else Color.Transparent)
    if (outline != null) m = m.border(2.dp, outline, shape)
    Column(
        m
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    pressed = true
                    try {
                        press()
                        tryAwaitRelease()
                    } finally {
                        // Also runs when the gesture is cancelled: never leave the motor running.
                        pressed = false
                        release()
                    }
                })
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content(pressed) }
}

/**
 * Grid with equal-size cells that works inside a scrolling Column (unlike LazyVerticalGrid).
 * The number of columns follows the available width; every cell is at least [minCellWidth] wide.
 */
@Composable
fun <T> EqualGrid(
    items: List<T>,
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    cell: @Composable (index: Int, item: T) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // +0.5 dp tolerance so e.g. exactly 3 × 96 dp still gives 3 columns despite rounding.
        val columns = (((maxWidth + spacing + 0.5.dp) / (minCellWidth + spacing)).toInt()).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEachIndexed { row, chunk ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    chunk.forEachIndexed { i, item ->
                        Box(Modifier.weight(1f)) { cell(row * columns + i, item) }
                    }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Feedback banner ("🐶 Bellen gesendet")
// ---------------------------------------------------------------------------------------------

/** Holds the short confirmation message shown at the bottom after a button press. */
class FeedbackState {
    var text by mutableStateOf("")
        private set
    var visible by mutableStateOf(false)
        private set
    /** Increases with every message so the hide timer restarts. */
    var id by mutableIntStateOf(0)
        private set

    fun show(message: String) {
        text = message
        visible = true
        id++
    }

    fun hide() {
        visible = false
    }
}

/** Provided once in MainActivity; screens call `LocalFeedback.current.show("…")`. */
val LocalFeedback = staticCompositionLocalOf { FeedbackState() }

/** Ink pill that slides in from the bottom (slightly tilted, like a taped note) and hides after 1.6 s. */
@Composable
fun FeedbackBanner(state: FeedbackState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.id) {
        if (state.visible) {
            delay(1600)
            state.hide()
        }
    }
    AnimatedVisibility(
        visible = state.visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Text(
            state.text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .rotate(-2f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .border(2.dp, Orange, RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** Small hand-written caption (e.g. "gedrückt halten" under a pad). */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = ScriptStyle.copy(fontSize = 20.sp, lineHeight = 22.sp), color = color, modifier = modifier)
}

