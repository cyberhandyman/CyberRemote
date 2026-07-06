package dev.companionremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(viewModel: AppViewModel) {
    val ui by viewModel.deviceList.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Companion Remote") },
                actions = {
                    IconButton(onClick = { showManualDialog = true }) {
                        Icon(Icons.Default.Link, contentDescription = "Connect by IP")
                    }
                    if (ui.scanning) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (ui.devices.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (ui.scanning) "Looking for Apple TVs…"
                    else "No Apple TV found.\nPhone and TV must be on the same Wi-Fi network.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(ui.devices, key = { it.name }) { device ->
                    Card(
                        onClick = { viewModel.selectDevice(device) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(
                                        device.model,
                                        "${device.host}:${device.port}",
                                        if (device.name in ui.pairedNames) "paired" else "not paired",
                                    ).joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (device.name in ui.pairedNames) {
                                TextButton(onClick = { viewModel.forgetDevice(device) }) { Text("Forget") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Connect by IP") },
            text = {
                Column {
                    Text(
                        "For networks where discovery doesn't work. " +
                            "The port changes after every Apple TV reboot.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(host, { host = it }, label = { Text("IP address") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        port.toIntOrNull()?.let { p ->
                            showManualDialog = false
                            viewModel.addManualDevice(host.trim(), p)
                        }
                    },
                ) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text("Cancel") } },
        )
    }
}
