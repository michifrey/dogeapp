package de.dogeapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.dogeapp.ui.CommandsScreen
import de.dogeapp.ui.ConnectScreen
import de.dogeapp.ui.ControlScreen
import de.dogeapp.ui.DogeViewModel
import de.dogeapp.ui.ExplorerScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    CONNECT("Verbinden", Icons.Filled.Bluetooth),
    CONTROL("Steuern", Icons.Filled.Gamepad),
    EXPLORER("Explorer", Icons.Filled.Search),
    COMMANDS("Befehle", Icons.Filled.Tune),
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
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

    Scaffold(
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
    ) { padding ->
        val mod = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            Tab.CONNECT -> ConnectScreen(
                vm,
                onEnableBluetooth,
                // Without a known control channel the Explorer is the useful next step.
                onConnected = { tab = if (vm.config.value.hasWriteTarget) Tab.CONTROL else Tab.EXPLORER },
                modifier = mod,
            )
            Tab.CONTROL -> ControlScreen(vm, mod)
            Tab.EXPLORER -> ExplorerScreen(vm, mod)
            Tab.COMMANDS -> CommandsScreen(vm, mod)
        }
    }
}
