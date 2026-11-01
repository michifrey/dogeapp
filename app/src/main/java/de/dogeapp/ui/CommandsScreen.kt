package de.dogeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dogeapp.ble.ConnState
import de.dogeapp.data.CommandDef
import de.dogeapp.data.DogeConfig
import de.dogeapp.data.Hex
import de.dogeapp.voice.Conversation

/**
 * Settings. On top what parents normally need (child's name, voice, weather & jokes); everything
 * technical (Explorer, reverse test, sequence tester, control channel, command table, import/export)
 * lives in the collapsible "Für Eltern / Experten" section.
 */
@Composable
fun SettingsScreen(vm: DogeViewModel, onOpenExplorer: () -> Unit, modifier: Modifier = Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val jokesStatus by vm.jokesStatus.collectAsStateWithLifecycle()
    val weatherText by vm.weatherText.collectAsStateWithLifecycle()
    var expertOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(emoji = "👧", title = "Für das Kind") {
                OutlinedTextField(
                    value = config.childName,
                    onValueChange = { v -> vm.updateConfig { it.copy(childName = v.take(30)) } },
                    label = { Text("Name des Kindes") },
                    placeholder = { Text("z. B. Yara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(
                    if (config.childName.isBlank()) "Die App begrüßt das Kind dann mit seinem Namen."
                    else "Vorschau: ${greetingText(config.childName, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))}"
                )
                SwitchRow(
                    title = "Dog-E spricht",
                    subtitle = "Bei Gesprächen spricht das Tablet mit Hundestimme.",
                    checked = config.dogSpeaks,
                    onChange = { v -> vm.updateConfig { it.copy(dogSpeaks = v) } },
                )
            }

            SectionCard(emoji = "🌍", title = "Wetter & Witze") {
                OutlinedTextField(
                    value = config.city,
                    onValueChange = { v -> vm.updateConfig { it.copy(city = v.take(60)) } },
                    label = { Text("Wohnort fürs Wetter") },
                    placeholder = { Text("z. B. Zürich") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { vm.runReaction(Conversation.REACTIONS.first { it.id == "weather" }) }) {
                    Text("🌦️ Wetter testen")
                }
                if (weatherText.isNotBlank()) Hint(weatherText)
                OutlinedTextField(
                    value = config.jokesUrl,
                    onValueChange = { v -> vm.updateConfig { it.copy(jokesUrl = v.trim()) } },
                    label = { Text("Witze-Datei (Adresse)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshJokes() }) { Text("😂 Witze aktualisieren") }
                    Text(jokesStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                Hint(
                    "Die Witze kommen aus content/witze.json im GitHub-Projekt – dort kannst du selbst neue, " +
                        "kindgerechte Witze eintragen. Die App lädt sie einmal am Tag automatisch."
                )
            }

            ExpertSection(open = expertOpen, onToggle = { expertOpen = !expertOpen }) {
                ExpertContent(vm, onOpenExplorer)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

/** Collapsible card "Für Eltern / Experten". */
@Composable
private fun ExpertSection(open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SectionCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onToggle)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛠️", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Für Eltern / Experten", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Bluetooth-Explorer, Rückwärts-Test, Sequenzen, Befehle, Import/Export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (open) "Einklappen" else "Ausklappen",
            )
        }
        if (open) content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ExpertContent(vm: DogeViewModel, onOpenExplorer: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val log by vm.ble.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var importOpen by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }

    // --- Explorer
    SubTitle("🔍 Bluetooth-Explorer")
    Hint("Alle Kanäle des Hundes ansehen, rohe Bytes senden und das Protokoll mitlesen. Zum Fehlersuchen.")
    Button(onClick = onOpenExplorer, modifier = Modifier.heightIn(min = 48.dp)) { Text("Explorer öffnen") }
    log.lastOrNull()?.let {
        Text(
            "Letzte Meldung: ${it.time}  ${it.text}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider()
    ReverseProbe(vm)

    HorizontalDivider()
    SequenceTester(vm)

    // --- Control channel
    HorizontalDivider()
    SubTitle("📡 Steuer-Kanal")
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
    SwitchRow(
        title = "Write Without Response",
        subtitle = null,
        checked = config.writeWithoutResponse,
        onChange = { v -> vm.updateConfig { it.copy(writeWithoutResponse = v) } },
    )
    OutlinedTextField(
        value = config.repeatIntervalMs.toString(),
        onValueChange = { v -> v.ifBlank { "0" }.toLongOrNull()?.let { ms -> vm.updateConfig { it.copy(repeatIntervalMs = ms) } } },
        label = { Text("Wiederholung beim Halten (ms, 0 = aus)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    // --- Command table
    HorizontalDivider()
    SubTitle("🧾 Befehle")
    Hint("Name und Hex-Bytes je Knopf (siehe docs/PROTOKOLL.md). ▶ testet den Befehl am Hund.")
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
    }) { Text("➕ Befehl hinzufügen") }

    // --- Backup
    HorizontalDivider()
    SubTitle("💾 Sichern & Zurücksetzen")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { copyToClipboard(context, vm.exportConfig()) }) { Text("Export (Zwischenablage)") }
        OutlinedButton(onClick = { importOpen = true }) { Text("Import") }
        TextButton(onClick = { resetOpen = true }) { Text("Alles zurücksetzen", color = MaterialTheme.colorScheme.error) }
    }

    if (importOpen) ImportDialog(onDismiss = { importOpen = false }, onImport = { vm.importConfig(it) })
    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text("Alles zurücksetzen?") },
            text = { Text("Name, Befehle und Steuer-Kanal werden auf die Standardwerte gesetzt.") },
            confirmButton = {
                TextButton(onClick = { vm.resetConfig(); resetOpen = false }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { resetOpen = false }) { Text("Abbrechen") } },
        )
    }
}

/**
 * Experimental: the official app only drives forward. Hold a button to send another speed byte
 * to the drive motor and see whether the dog drives backwards; release = stop.
 */
@Composable
private fun ReverseProbe(vm: DogeViewModel) {
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val connected = state == ConnState.READY
    SubTitle("🧪 Rückwärts testen (experimentell)")
    Hint(
        "Dog-E fährt offiziell nur vorwärts. Halte einen Knopf gedrückt, um einen anderen Geschwindigkeits-Wert " +
            "an den Fahrmotor zu schicken – vielleicht fährt er damit rückwärts. Loslassen = Stopp. " +
            "Genug Platz um den Hund lassen!"
    )
    if (!connected) {
        Text(
            "Nicht verbunden – zuerst Dog-E verbinden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    EqualGrid(listOf(4, 5, 6, 0xFF, 0xFE, 0xFD), minCellWidth = 88.dp) { _, value ->
        HoldPad(
            brush = SolidColor(MaterialTheme.colorScheme.tertiaryContainer),
            onPress = { vm.driveRaw(value) },
            onRelease = { vm.driveStop() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            enabled = connected,
        ) { _ ->
            Text(
                "0x%02X".format(value),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text("($value)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** Plays any sequence number – the dog knows many more tricks and sounds than the buttons. */
@Composable
private fun SequenceTester(vm: DogeViewModel) {
    var text by rememberSaveable { mutableStateOf("106") }
    val id = text.trim().let { if (it.startsWith("0x", true)) it.drop(2).toIntOrNull(16) else it.toIntOrNull() }
    val valid = id != null && id in 0..0xFFFF
    SubTitle("🔢 Sequenz ausprobieren")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { id?.let { text = (it - 1).coerceAtLeast(0).toString() } }) { Text("−") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        OutlinedButton(onClick = { id?.let { text = (it + 1).coerceAtMost(0xFFFF).toString() } }) { Text("+") }
    }
    Button(onClick = { id?.let { vm.playSequence(it) } }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("▶ Abspielen")
    }
    Hint(
        "Bekannt: 106 Bellen, 114 Heulen, 115 Singen, 116 Furzen, 168 Schwanzwedeln. " +
            "Gute Funde unten unter \"Befehle\" als neuen Knopf speichern."
    )
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

/** Small explanatory text in the muted color. */
@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onChange(!checked) }
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
