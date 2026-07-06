package dev.companionremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.ConnectionState
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.protocol.client.HidCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val haptics = LocalHapticFeedback.current
    var powerMenu by remember { mutableStateOf(false) }

    fun press(command: HidCommand) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.pressButton(command)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (connectionState) {
                                ConnectionState.Connected -> "connected"
                                ConnectionState.Connecting -> "connecting…"
                                ConnectionState.Disconnected -> "disconnected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeRemote() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { powerMenu = true }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power")
                    }
                    DropdownMenu(expanded = powerMenu, onDismissRequest = { powerMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Wake") },
                            onClick = { powerMenu = false; viewModel.wake() },
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep") },
                            onClick = { powerMenu = false; viewModel.sleep() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (connectionState == ConnectionState.Disconnected) {
                Snackbar(
                    action = { TextButton(onClick = { viewModel.reconnect() }) { Text("Reconnect") } },
                ) {
                    Text(connectionError ?: "Connection lost")
                }
                Spacer(Modifier.height(16.dp))
            }

            // D-pad
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                DpadButton(Icons.Default.KeyboardArrowUp, "Up", Modifier.align(Alignment.TopCenter)) {
                    press(HidCommand.Up)
                }
                DpadButton(Icons.Default.KeyboardArrowDown, "Down", Modifier.align(Alignment.BottomCenter)) {
                    press(HidCommand.Down)
                }
                DpadButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", Modifier.align(Alignment.CenterStart)) {
                    press(HidCommand.Left)
                }
                DpadButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", Modifier.align(Alignment.CenterEnd)) {
                    press(HidCommand.Right)
                }
                FilledTonalIconButton(
                    onClick = { press(HidCommand.Select) },
                    modifier = Modifier.align(Alignment.Center).size(88.dp),
                ) {
                    Text("OK", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(28.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { press(HidCommand.Menu) }
                RemoteKey(Icons.Default.Home, "Home") { press(HidCommand.Home) }
                RemoteKey(Icons.Default.PlayArrow, "Play/Pause") { press(HidCommand.PlayPause) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RemoteKey(Icons.Default.Add, "Volume up") { press(HidCommand.VolumeUp) }
                    Spacer(Modifier.height(12.dp))
                    RemoteKey(Icons.Default.Remove, "Volume down") { press(HidCommand.VolumeDown) }
                }
            }
        }
    }
}

@Composable
private fun DpadButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.size(72.dp)) {
        Icon(icon, contentDescription = label, Modifier.size(40.dp))
    }
}

@Composable
private fun RemoteKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(icon, contentDescription = label)
    }
}
