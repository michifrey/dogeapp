package de.dogeapp.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.data.CommandDef
import de.dogeapp.data.DogeConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(vm: DogeViewModel, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val log by vm.ble.log.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!connected) {
            Hint("Nicht verbunden. Zuerst im Tab \"Verbinden\" den Hund auswählen.")
        } else if (!config.hasWriteTarget) {
            Hint("Noch kein Steuer-Kanal festgelegt. Im Explorer eine schreibbare Characteristic wählen.")
        }

        // D-pad: hold to drive, release sends "stop".
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DriveButton(config.command(DogeConfig.FORWARD), Icons.Filled.KeyboardArrowUp, vm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                DriveButton(config.command(DogeConfig.LEFT), Icons.Filled.KeyboardArrowLeft, vm)
                DriveButton(config.command(DogeConfig.STOP), Icons.Filled.Stop, vm, hold = false)
                DriveButton(config.command(DogeConfig.RIGHT), Icons.Filled.KeyboardArrowRight, vm)
            }
            DriveButton(config.command(DogeConfig.BACKWARD), Icons.Filled.KeyboardArrowDown, vm)
        }

        Text("Aktionen", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            config.commands.filterNot { it.id in DogeConfig.DRIVE_IDS }.forEach { cmd ->
                FilledTonalButton(onClick = { vm.send(cmd) }, enabled = cmd.hex.isNotBlank()) {
                    Text(cmd.label)
                }
            }
        }

        log.lastOrNull()?.let {
            Text("${it.time}  ${it.text}", style = MaterialTheme.typography.bodySmall)
        }
    }
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
private fun DriveButton(cmd: CommandDef?, icon: ImageVector, vm: DogeViewModel, hold: Boolean = true) {
    val enabled = cmd != null && cmd.hex.isNotBlank()
    Box(
        Modifier
            .padding(4.dp)
            .size(84.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .pointerInput(cmd, enabled, hold) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    if (hold) {
                        vm.press(cmd!!)
                        tryAwaitRelease()
                        vm.release()
                    } else {
                        vm.send(cmd!!)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = cmd?.label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.size(2.dp))
            Text(cmd?.label ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
