package de.dogeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Big friendly greeting at the top of the main screens. Without a name it invites the child
 * to enter one in the settings.
 */
@Composable
fun GreetingCard(name: String, dogConnected: Boolean, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${greetingText(name, hour)} 🐶",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            when {
                name.isBlank() -> "Wie heißt du? Trag deinen Namen in den Einstellungen ein."
                dogConnected -> "Dog-E ist da und freut sich auf dich!"
                else -> "Dog-E wartet schon auf dich. Schalte ihn ein und verbinde dich."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (name.isBlank()) {
            TextButton(onClick = onOpenSettings) { Text("Namen eintragen") }
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
