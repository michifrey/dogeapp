package de.dogeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Greeting text for the time of day, e.g. "Guten Morgen, Yara!". Pure function so it can be tested. */
fun greetingText(name: String, hour: Int): String {
    val hello = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..16 -> "Hallo"
        in 17..21 -> "Guten Abend"
        else -> "Gute Nacht"
    }
    val n = name.trim()
    return if (n.isEmpty()) "$hello!" else "$hello, $n!"
}

private fun sleepyHour(hour: Int) = hour >= 20 || hour < 6

/**
 * Big friendly greeting (connect screen). Without a name it invites the parent to enter one.
 */
@Composable
fun GreetingCard(name: String, dogConnected: Boolean, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WobblyDog(size = 56.sp, emoji = if (sleepyHour(hour)) "😴" else "🐶")
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                greetingText(name, hour),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                when {
                    name.isBlank() -> "Wie heißt du? Trag deinen Namen in den Einstellungen ein."
                    dogConnected -> "Dog-E ist da und freut sich auf dich! 🎉"
                    else -> "Dog-E wartet schon auf dich. Schalte ihn ein und verbinde dich."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (name.isBlank()) {
                TextButton(onClick = onOpenSettings) { Text("✏️ Namen eintragen") }
            }
        }
    }
}

/**
 * One-line greeting for the play screen ("Hallo, Yara! 🐶") with a status line below.
 * [trailing] is an optional small action on the right (e.g. "Anfreunden").
 */
@Composable
fun CompactGreeting(
    name: String,
    status: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WobblyDog(size = 32.sp, emoji = if (sleepyHour(hour)) "😴" else "🐶")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                greetingText(name, hour),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            trailing != null -> { Spacer(Modifier.width(8.dp)); trailing() }
            name.isBlank() -> TextButton(onClick = onOpenSettings) { Text("✏️ Name") }
        }
    }
}

/** Battery level as icon + number; the dog reports a number, shown as percent. */
@Composable
fun BatteryBadge(level: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            when {
                level == null -> Icons.Filled.BatteryUnknown
                level >= 70 -> Icons.Filled.BatteryFull
                level >= 30 -> Icons.Filled.Battery5Bar
                else -> Icons.Filled.Battery2Bar
            },
            contentDescription = "Akku",
            tint = if (level != null && level < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(if (level == null) "Akku ?" else "Akku $level %")
    }
}
