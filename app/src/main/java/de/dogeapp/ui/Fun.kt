package de.dogeapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.ui.theme.ButtonColors
import de.dogeapp.ui.theme.FunGradient
import de.dogeapp.ui.theme.Mint

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

/** Gradient title bar with connection state and battery. */
@Composable
fun TopHeader(vm: DogeViewModel) {
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY
    Row(
        Modifier
            .fillMaxWidth()
            .background(FunGradient)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WobblyDog(size = 30.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Dog-E Control",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(10.dp).background(if (connected) Mint else Color.White.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when (state) {
                    ConnState.READY -> "verbunden" + (battery?.let { " · 🔋 $it %" } ?: "")
                    ConnState.CONNECTING, ConnState.DISCOVERING -> "verbinde …"
                    ConnState.DISCONNECTED -> "nicht verbunden"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A dog emoji that gently tilts back and forth. */
@Composable
fun WobblyDog(size: androidx.compose.ui.unit.TextUnit = 40.sp, emoji: String = "🐶") {
    val t = rememberInfiniteTransition(label = "wobble")
    val angle by t.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "angle",
    )
    Text(emoji, fontSize = size, modifier = Modifier.rotate(angle))
}

/**
 * Big colorful toy-box button: emoji on top, label below, bounces when pressed.
 * [index] picks the accent color so neighbouring buttons differ.
 */
@Composable
fun FunButton(
    emoji: String,
    label: String,
    index: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    minWidth: Dp = 104.dp,
    big: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.4f), label = "press")
    val color = ButtonColors[index % ButtonColors.size]
    Column(
        Modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .widthIn(min = minWidth)
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color)), RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (big) 16.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, fontSize = if (big) 36.sp else 26.sp)
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
    }
}

/** Section title with an emoji. */
@Composable
fun SectionTitle(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}
