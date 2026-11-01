package de.dogeapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Bluetooth as OutlinedBluetooth
import androidx.compose.material.icons.outlined.Lightbulb as OutlinedLightbulb
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material.icons.outlined.SportsEsports as OutlinedSportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dogeapp.ble.ConnState
import de.dogeapp.ui.ConnectScreen
import de.dogeapp.ui.DogeViewModel
import de.dogeapp.ui.ExplorerScreen
import de.dogeapp.ui.FeedbackBanner
import de.dogeapp.ui.FeedbackState
import de.dogeapp.ui.LedScreen
import de.dogeapp.ui.LocalFeedback
import de.dogeapp.ui.PlayScreen
import de.dogeapp.ui.SettingsScreen
import de.dogeapp.ui.TopHeader
import de.dogeapp.ui.Mascot
import de.dogeapp.ui.PaperBackground
import de.dogeapp.ui.PillButton
import de.dogeapp.ui.PosterHeadline
import de.dogeapp.ui.ScriptNote
import de.dogeapp.ui.Sticker
import de.dogeapp.ui.theme.DogeTheme
import de.dogeapp.ui.theme.Orange

/**
 * Top-level tabs. The first two are for the child, the last two for the parent
 * (on tablets they are visually separated in the side rail). The developer Explorer is not a tab:
 * it opens from Einstellungen → "Für Eltern / Experten".
 */
private enum class Tab(val label: String, val icon: ImageVector, val outlined: ImageVector, val forChild: Boolean) {
    PLAY("Spielen", Icons.Filled.SportsEsports, Icons.Outlined.OutlinedSportsEsports, true),
    LIGHT("Licht", Icons.Filled.Lightbulb, Icons.Outlined.OutlinedLightbulb, true),
    CONNECT("Verbinden", Icons.Filled.Bluetooth, Icons.Outlined.OutlinedBluetooth, false),
    SETTINGS("Einstellungen", Icons.Filled.Settings, Icons.Outlined.OutlinedSettings, false),
}

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ draws apps edge to edge. The page is cream paper (dark icons) in light mode and
        // warm brown-black (light icons) in dark mode; "auto" follows the system theme like DogeTheme.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent),
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent),
        )
        setContent {
            DogeTheme {
                var granted by remember { mutableStateOf(hasPermissions()) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted = hasPermissions() }

                if (granted) {
                    App(onEnableBluetooth = {
                        @Suppress("MissingPermission")
                        startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    })
                } else {
                    PermissionScreen { launcher.launch(requiredPermissions) }
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    PaperBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Mascot(Modifier.padding(top = 16.dp), size = 180.dp)
                ScriptNote("psst!", Modifier.align(Alignment.TopStart), rotation = -12f)
                Sticker(
                    "100 % harmlos*",
                    Modifier.align(Alignment.BottomEnd),
                    footnote = "*versprochen",
                    diameter = 92.dp,
                    rotation = 10f,
                )
            }
            PosterHeadline(
                "Darf ich?",
                "ich brauch Bluetooth!",
                modifier = Modifier.widthIn(max = 480.dp),
                centered = true,
                bigSize = 56.sp,
            )
            Text(
                "Die App braucht die Bluetooth-Berechtigung, um den Hund zu finden und zu steuern.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            PillButton(
                "Berechtigung erteilen",
                onClick = onRequest,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun App(onEnableBluetooth: () -> Unit) {
    val vm: DogeViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(Tab.PLAY) }
    // The Bluetooth Explorer is a sub-screen of the settings (developer tool), not a tab.
    var explorerOpen by rememberSaveable { mutableStateOf(false) }
    val feedback = remember { FeedbackState() }
    // Tablets (>= 600 dp wide, e.g. Samsung Galaxy Tab) and phones in landscape get a side rail.
    val tablet = LocalConfiguration.current.screenWidthDp >= 600

    // The explorer belongs to the settings, so that tab stays highlighted while it is open.
    val current = if (explorerOpen) Tab.SETTINGS else tab

    BackHandler(enabled = explorerOpen) { explorerOpen = false }

    fun select(t: Tab) {
        tab = t
        explorerOpen = false
    }

    /** "Dog-E verbinden" from the play/light screen: open the connect tab and start searching. */
    fun goConnect() {
        select(Tab.CONNECT)
        if (vm.ble.isBluetoothOn && vm.ble.state.value == ConnState.DISCONNECTED && !vm.ble.scanning.value) {
            vm.ble.startScan()
        }
    }

    val content: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
            if (explorerOpen) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { explorerOpen = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                        Text(
                            "BLUETOOTH-EXPLORER",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ExplorerScreen(vm, Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                when (tab) {
                    Tab.PLAY -> PlayScreen(
                        vm,
                        onConnect = { goConnect() },
                        onOpenSettings = { select(Tab.SETTINGS) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Tab.LIGHT -> LedScreen(vm, onConnect = { goConnect() }, modifier = Modifier.fillMaxSize())
                    Tab.CONNECT -> ConnectScreen(
                        vm,
                        onEnableBluetooth,
                        onConnected = {
                            if (vm.config.value.hasWriteTarget) {
                                select(Tab.PLAY)
                            } else {
                                explorerOpen = true
                            }
                        },
                        onOpenSettings = { select(Tab.SETTINGS) },
                        onPlay = { select(Tab.PLAY) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Tab.SETTINGS -> SettingsScreen(
                        vm,
                        onOpenExplorer = { explorerOpen = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // Short confirmation like "🐶 Bellen gesendet", above the bottom bar.
            FeedbackBanner(feedback, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }

    val ink = MaterialTheme.colorScheme.onBackground
    val line = MaterialTheme.colorScheme.outline

    CompositionLocalProvider(LocalFeedback provides feedback) {
        PaperBackground(Modifier.fillMaxSize()) {
            if (tablet) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = ink,
                    topBar = { TopHeader(vm) },
                ) { padding ->
                    Row(Modifier.padding(padding).fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            // The Scaffold padding already contains the system bars.
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                // Thin ink line on the right edge, like a paper strip.
                                drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.5.dp.toPx())
                            },
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Tab.entries.filter { it.forChild }.forEach { t -> RailItem(t, current == t) { select(t) } }
                            // Parent tabs at the bottom, away from the child's tabs.
                            Spacer(Modifier.weight(1f))
                            Tab.entries.filterNot { it.forChild }.forEach { t -> RailItem(t, current == t) { select(t) } }
                            Spacer(Modifier.height(8.dp))
                        }
                        content(Modifier.weight(1f).fillMaxSize())
                    }
                }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = ink,
                    topBar = { TopHeader(vm) },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                // Thin ink line on top of the cream bar.
                                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx())
                            },
                        ) {
                            Tab.entries.forEach { t ->
                                val selected = current == t
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { select(t) },
                                    icon = { Icon(if (selected) t.icon else t.outlined, contentDescription = null) },
                                    label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Orange,
                                        selectedTextColor = Orange,
                                        indicatorColor = Orange.copy(alpha = 0.16f),
                                        unselectedIconColor = ink,
                                        unselectedTextColor = ink,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun RailItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) t.icon else t.outlined, contentDescription = null) },
        label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)) },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = Orange,
            selectedTextColor = Orange,
            indicatorColor = Orange.copy(alpha = 0.16f),
            unselectedIconColor = ink,
            unselectedTextColor = ink,
        ),
    )
}
