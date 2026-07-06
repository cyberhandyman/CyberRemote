package dev.companionremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.ConnectionState
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.client.TouchPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val focusState by viewModel.keyboardFocus.collectAsState()
    val keyboardText by viewModel.keyboardText.collectAsState()
    val haptics = LocalHapticFeedback.current
    var powerMenu by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    fun press(command: HidCommand) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.pressButton(command)
    }

    LaunchedEffect(focusState) {
        when (focusState) {
            KeyboardFocusState.Focused -> keyboardOpen = true
            KeyboardFocusState.Unfocused -> keyboardOpen = false
            KeyboardFocusState.Unknown -> Unit
        }
    }

    LaunchedEffect(tab) {
        if (tab == 2) viewModel.loadApps()
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
                        DropdownMenuItem(text = { Text("Wake") }, onClick = { powerMenu = false; viewModel.wake() })
                        DropdownMenuItem(text = { Text("Sleep") }, onClick = { powerMenu = false; viewModel.sleep() })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Remote") }, icon = { Icon(Icons.Default.Gamepad, null) })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Touch") }, icon = { Icon(Icons.Default.TouchApp, null) })
                Tab(tab == 2, onClick = { tab = 2 }, text = { Text("Apps") }, icon = { Icon(Icons.Default.Apps, null) })
            }

            if (connectionState == ConnectionState.Disconnected) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { viewModel.reconnect() }) { Text("Reconnect") } },
                ) {
                    Text(connectionError ?: "Connection lost")
                }
            }

            if (keyboardOpen) {
                val focusRequester = remember { FocusRequester() }
                val softKeyboard = LocalSoftwareKeyboardController.current
                OutlinedTextField(
                    value = keyboardText,
                    onValueChange = viewModel::onKeyboardTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    label = {
                        Text(
                            if (focusState == KeyboardFocusState.Focused) "Typing on ${device.name}"
                            else "No text field focused on the TV",
                        )
                    },
                    enabled = focusState == KeyboardFocusState.Focused,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.clearKeyboardText() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear TV text")
                        }
                    },
                )
                LaunchedEffect(keyboardOpen, focusState) {
                    if (focusState == KeyboardFocusState.Focused) {
                        focusRequester.requestFocus()
                        softKeyboard?.show()
                    }
                }
            }

            when (tab) {
                0 -> DpadPane(
                    press = ::press,
                    onOk = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.touchTap()
                    },
                    toggleKeyboard = { keyboardOpen = !keyboardOpen },
                )
                1 -> TouchpadPane(viewModel, ::press)
                2 -> AppsPane(viewModel)
            }
        }
    }
}

@Composable
private fun DpadPane(press: (HidCommand) -> Unit, onOk: () -> Unit, toggleKeyboard: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
                // Use the touchpad tap (adds the _hidT Click event); a bare
                // _hidC select is misread as a long-press on the tvOS home
                // screen (real-device finding).
                onClick = onOk,
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
            RemoteKey(Icons.Default.Keyboard, "Keyboard") { toggleKeyboard() }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteKey(Icons.Default.Add, "Volume up") { press(HidCommand.VolumeUp) }
                Spacer(Modifier.height(12.dp))
                RemoteKey(Icons.Default.Remove, "Volume down") { press(HidCommand.VolumeDown) }
            }
        }
    }
}

@Composable
private fun TouchpadPane(viewModel: AppViewModel, press: (HidCommand) -> Unit) {
    val lastTouch = remember { mutableStateOf(500L to 500L) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.touchTap() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val x = (offset.x * 1000 / size.width).toLong()
                            val y = (offset.y * 1000 / size.height).toLong()
                            lastTouch.value = x to y
                            viewModel.sendTouch(x, y, TouchPhase.Press)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = (change.position.x * 1000 / size.width).toLong()
                            val y = (change.position.y * 1000 / size.height).toLong()
                            lastTouch.value = x to y
                            viewModel.sendTouch(x, y, TouchPhase.Hold)
                        },
                        onDragEnd = {
                            val (x, y) = lastTouch.value
                            viewModel.sendTouch(x, y, TouchPhase.Release)
                        },
                        onDragCancel = {
                            val (x, y) = lastTouch.value
                            viewModel.sendTouch(x, y, TouchPhase.Release)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Swipe to navigate\nTap to select",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RemoteKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { press(HidCommand.Menu) }
            RemoteKey(Icons.Default.Home, "Home") { press(HidCommand.Home) }
            RemoteKey(Icons.Default.PlayArrow, "Play/Pause") { press(HidCommand.PlayPause) }
        }
    }
}

@Composable
private fun AppsPane(viewModel: AppViewModel) {
    val apps by viewModel.apps.collectAsState()
    val error by viewModel.appsError.collectAsState()

    when {
        error != null -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { viewModel.loadApps(force = true) }) { Text("Retry") }
        }
        apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            items(apps!!, key = { it.first }) { (bundleId, name) ->
                ElevatedCard(
                    onClick = { viewModel.launchApp(bundleId) },
                    modifier = Modifier.padding(6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            bundleId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
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
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(icon, contentDescription = label)
    }
}
