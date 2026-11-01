package de.dogeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.data.CommandDef
import de.dogeapp.data.DogeConfig
import de.dogeapp.data.Hex

/** Edit the command table: label + hex bytes per command, plus connection settings and import/export. */
@Composable
fun CommandsScreen(vm: DogeViewModel, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var importOpen by remember { mutableStateOf(false) }

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Steuer-Kanal", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = config.writeServiceUuid,
            onValueChange = { v -> vm.updateConfig { it.copy(writeServiceUuid = v.trim()) } },
            label = { Text("Service-UUID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = config.writeCharUuid,
            onValueChange = { v -> vm.updateConfig { it.copy(writeCharUuid = v.trim()) } },
            label = { Text("Characteristic-UUID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = config.writeWithoutResponse,
                onCheckedChange = { v -> vm.updateConfig { it.copy(writeWithoutResponse = v) } },
            )
            Text("Write Without Response")
        }
        OutlinedTextField(
            value = config.repeatIntervalMs.toString(),
            onValueChange = { v -> v.ifBlank { "0" }.toLongOrNull()?.let { ms -> vm.updateConfig { it.copy(repeatIntervalMs = ms) } } },
            label = { Text("Wiederholung beim Halten (ms, 0 = aus)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Befehle", style = MaterialTheme.typography.titleMedium)
        Text(
            "Die Bytes sind noch unbekannt. Sobald du sie herausgefunden hast (siehe docs/PROTOKOLL.md), hier eintragen.",
            style = MaterialTheme.typography.bodySmall,
        )

        config.commands.forEachIndexed { index, cmd ->
            CommandRow(
                cmd = cmd,
                removable = cmd.id !in DogeConfig.DRIVE_IDS,
                onChange = { updated ->
                    vm.updateConfig { c -> c.copy(commands = c.commands.toMutableList().also { it[index] = updated }) }
                },
                onDelete = { vm.updateConfig { c -> c.copy(commands = c.commands.filterIndexed { i, _ -> i != index }) } },
                onTest = { vm.send(cmd) },
            )
        }
        OutlinedButton(onClick = {
            vm.updateConfig { c ->
                c.copy(commands = c.commands + CommandDef("custom_${System.currentTimeMillis()}", "Neu", ""))
            }
        }) { Text("Befehl hinzufügen") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, vm.exportConfig()) }) { Text("Export (Zwischenablage)") }
            OutlinedButton(onClick = { importOpen = true }) { Text("Import") }
        }
        TextButton(onClick = { vm.resetConfig() }) { Text("Alles zurücksetzen") }
    }

    if (importOpen) ImportDialog(onDismiss = { importOpen = false }, onImport = { vm.importConfig(it) })
}

@Composable
private fun CommandRow(
    cmd: CommandDef,
    removable: Boolean,
    onChange: (CommandDef) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    val valid = cmd.hex.isBlank() || Hex.parse(cmd.hex) != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = cmd.label,
            onValueChange = { onChange(cmd.copy(label = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(0.9f),
        )
        OutlinedTextField(
            value = cmd.hex,
            onValueChange = { onChange(cmd.copy(hex = it)) },
            label = { Text("Hex") },
            singleLine = true,
            isError = !valid,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1.1f),
        )
        IconButton(onClick = onTest, enabled = cmd.hex.isNotBlank() && valid) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Testen")
        }
        if (removable) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
        }
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfiguration importieren") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = false },
                label = { Text("JSON einfügen") },
                isError = error,
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (onImport(text)) onDismiss() else error = true }) { Text("Importieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
