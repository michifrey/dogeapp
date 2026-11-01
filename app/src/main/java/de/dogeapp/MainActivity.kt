package de.dogeapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dogeapp.ui.CommandsScreen
import de.dogeapp.ui.ConnectScreen
import de.dogeapp.ui.ControlScreen
import de.dogeapp.ui.DogeViewModel
import de.dogeapp.ui.ExplorerScreen
import de.dogeapp.ui.LedScreen
import de.dogeapp.ui.TopHeader
import de.dogeapp.ui.theme.DogeTheme
import de.dogeapp.ui.theme.backgroundBrush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color

private enum class Tab(val label: String, val icon: ImageVector) {
    CONNECT("Verbinden", Icons.Filled.Bluetooth),
    CONTROL("Steuern", Icons.Filled.Gamepad),
    LIGHT("Licht", Icons.Filled.Lightbulb),
    EXPLORER("Explorer", Icons.Filled.Search),
    COMMANDS("Einstellungen", Icons.Filled.Settings),
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
        // Android 15+ draws apps edge to edge; white status bar icons on the purple header.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
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
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🐶", style = MaterialTheme.typography.displayLarge)
        Text("Dog-E Control", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Die App braucht die Bluetooth-Berechtigung, um den Hund zu finden und zu steuern.",
            Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onRequest) { Text("Berechtigung erteilen") }
    }
}

@Composable
private fun App(onEnableBluetooth: () -> Unit) {
    val vm: DogeViewModel = viewModel()
    var tab by rememberSaveable { mutableStateOf(Tab.CONNECT) }
    // Tablets (>= 600 dp wide, e.g. Samsung Galaxy Tab) get a side rail instead of the bottom bar.
    val tablet = LocalConfiguration.current.screenWidthDp >= 600

    val content: @Composable (Modifier) -> Unit = { mod ->
        when (tab) {
            Tab.CONNECT -> ConnectScreen(
                vm,
                onEnableBluetooth,
                onConnected = { tab = if (vm.config.value.hasWriteTarget) Tab.CONTROL else Tab.EXPLORER },
                onOpenSettings = { tab = Tab.COMMANDS },
                modifier = mod,
            )
            Tab.CONTROL -> ControlScreen(vm, onOpenSettings = { tab = Tab.COMMANDS }, modifier = mod)
            Tab.LIGHT -> LedScreen(vm, mod)
            Tab.EXPLORER -> ExplorerScreen(vm, mod)
            Tab.COMMANDS -> CommandsScreen(vm, mod)
        }
    }

    Box(Modifier.fillMaxSize().background(backgroundBrush())) {
    if (tablet) {
        Scaffold(containerColor = Color.Transparent, topBar = { TopHeader(vm) }) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                NavigationRail(containerColor = Color.Transparent) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
                content(Modifier.weight(1f).fillMaxSize())
            }
        }
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { TopHeader(vm) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
    }
    }
}
