package dev.companionremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.discovery.DiscoveredAtv

@Composable
fun PairingScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val ui by viewModel.pairing.collectAsState()
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pairing with ${device.name}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        when {
            ui.error != null -> {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.selectDevice(device) }) { Text("Try again") }
            }
            ui.awaitingPin -> {
                Text(
                    "Enter the 4-digit PIN shown on your TV",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.submitPin(pin) }, enabled = pin.length == 4) {
                    Text("Pair")
                }
            }
            else -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Contacting Apple TV…")
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { viewModel.cancelPairing() }) { Text("Cancel") }
    }
}
