package de.dogeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
 * Poster version of the greeting: the big uppercase line ("HALLO YARA!", without the comma so it
 * reads like a headline) and the hand-written line below it.
 */
fun posterHello(name: String, hour: Int): Pair<String, String> {
    val big = greetingText(name, hour).replace(",", "")
    val script = if (sleepyHour(hour)) "noch eine Runde?" else "lass uns spielen!"
    return big to script
}

/** Battery level as icon + number; the dog reports a number, shown as percent. */
@Composable
fun BatteryBadge(level: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            when {
                level == null -> Icons.AutoMirrored.Filled.BatteryUnknown
                level >= 70 -> Icons.Filled.BatteryFull
                level >= 30 -> Icons.Filled.Battery5Bar
                else -> Icons.Filled.Battery2Bar
            },
            contentDescription = "Akku",
            tint = if (level != null && level < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (level == null) "Akku ?" else "Akku $level %",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
