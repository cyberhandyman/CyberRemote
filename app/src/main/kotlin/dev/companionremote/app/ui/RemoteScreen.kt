package dev.companionremote.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.ConnectionState
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState

private val ConnectedGreen = Color(0xFF9ECE6A)
private val ConnectingAmber = Color(0xFFE0AF68)

// Soft palette for the generated app tiles (no network needed — real
// artwork would require calling out to Apple's servers, which this app
// deliberately avoids).
private val AppTileColors = listOf(
    Color(0xFF7AA2F7), Color(0xFF9ECE6A), Color(0xFFE0AF68), Color(0xFFF7768E),
    Color(0xFFBB9AF7), Color(0xFF7DCFFF), Color(0xFF73DACA), Color(0xFFFF9E64),
)

private fun appTileColor(key: String): Color {
    val hash = key.fold(0) { acc, c -> acc * 31 + c.code }
    return AppTileColors[Math.floorMod(hash, AppTileColors.size)]
}

private fun appInitial(name: String): String =
    name.trim().firstOrNull()?.toString()?.uppercase() ?: "?"

/**
 * An app tile icon: a generated initial+colour tile by default, or the real
 * App Store artwork when the user has opted into network fetching.
 */
@Composable
private fun AppIcon(bundleId: String, name: String, fetchIcons: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val context = androidx.compose.ui.platform.LocalContext.current
    val artwork by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = bundleId,
        key2 = fetchIcons,
    ) {
        value = if (fetchIcons) {
            dev.companionremote.app.data.AppIconFetcher.fetch(context, bundleId, name)
        } else {
            null
        }
    }

    val current = artwork
    if (current != null) {
        androidx.compose.foundation.Image(
            bitmap = current,
            contentDescription = name,
            modifier = Modifier.size(44.dp).clip(shape),
        )
    } else {
        Box(
            Modifier.size(44.dp).clip(shape).background(appTileColor(bundleId)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                appInitial(name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0B0E13),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(viewModel: AppViewModel, device: DiscoveredAtv) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val focusState by viewModel.keyboardFocus.collectAsState()
    val keyboardText by viewModel.keyboardText.collectAsState()
    val s = LocalAppStrings.current
    val haptics = LocalHapticFeedback.current
    val softKeyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var powerMenu by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    fun tap() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    fun press(command: HidCommand) { tap(); viewModel.pressButton(command) }
    fun hold(command: HidCommand) { tap(); viewModel.holdButton(command) }
    fun ok() { tap(); viewModel.touchTap() }
    fun okLong() { tap(); viewModel.holdButton(HidCommand.Select) }

    LaunchedEffect(focusState) {
        when (focusState) {
            KeyboardFocusState.Focused -> keyboardOpen = true
            KeyboardFocusState.Unfocused -> keyboardOpen = false
            KeyboardFocusState.Unknown -> Unit
        }
    }
    LaunchedEffect(tab) { if (tab == 2) viewModel.loadApps() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connectionState)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                device.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (connectionState) {
                                    ConnectionState.Connected -> s.connected
                                    ConnectionState.Connecting -> s.connecting
                                    ConnectionState.Disconnected -> s.disconnected
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeRemote() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openSettings() }) {
                        Icon(Icons.Rounded.Settings, contentDescription = s.settings)
                    }
                    IconButton(onClick = { powerMenu = true }) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Power")
                    }
                    DropdownMenu(expanded = powerMenu, onDismissRequest = { powerMenu = false }) {
                        DropdownMenuItem(text = { Text(s.wake) }, onClick = { powerMenu = false; viewModel.wake() })
                        DropdownMenuItem(text = { Text(s.sleep) }, onClick = { powerMenu = false; viewModel.sleep() })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            SegmentedTabs(tab, onSelect = { tab = it })

            if (connectionState == ConnectionState.Disconnected) {
                ConnectionBanner(connectionError) { viewModel.reconnect() }
            }

            if (keyboardOpen) {
                KeyboardBar(
                    text = keyboardText,
                    focused = focusState == KeyboardFocusState.Focused,
                    deviceName = device.name,
                    onTextChange = viewModel::onKeyboardTextChanged,
                    onClear = { viewModel.clearKeyboardText() },
                    onHide = {
                        keyboardOpen = false
                        softKeyboard?.hide()
                    },
                )
                // Only shrink to the compact layout when the soft keyboard is
                // actually up (stealing screen space). If it's dismissed, keep
                // the full remote so it doesn't look cramped.
                if (imeVisible) {
                    CompactRemote(press = ::press, hold = ::hold, ok = ::ok, okLong = ::okLong)
                } else {
                    DpadPane(press = ::press, hold = ::hold, ok = ::ok, okLong = ::okLong, openKeyboard = { softKeyboard?.show() })
                }
            } else {
                when (tab) {
                    0 -> DpadPane(press = ::press, hold = ::hold, ok = ::ok, okLong = ::okLong) { keyboardOpen = true }
                    1 -> TouchpadPane(viewModel, ::press, ::hold, ok = ::ok, okLong = ::okLong) { keyboardOpen = true }
                    2 -> AppsPane(viewModel)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Connected -> ConnectedGreen
        ConnectionState.Connecting -> ConnectingAmber
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit) {
    val s = LocalAppStrings.current
    val titles = listOf(s.tabRemote to Icons.Default.Gamepad, s.tabTouch to Icons.Default.TouchApp, s.tabApps to Icons.Default.Apps)
    TabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.background,
        indicator = { positions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(positions[selected]),
                width = 40.dp,
            )
        },
    ) {
        titles.forEachIndexed { i, (title, icon) ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                icon = { Icon(icon, contentDescription = null, Modifier.size(20.dp)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionBanner(error: String?, onReconnect: () -> Unit) {
    val s = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error ?: s.connectionLost,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReconnect) { Text(s.reconnect) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardBar(
    text: String,
    focused: Boolean,
    deviceName: String,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onHide: () -> Unit,
) {
    val s = LocalAppStrings.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val softKeyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            shape = RoundedCornerShape(16.dp),
            label = { Text(if (focused) s.typingOn.format(deviceName) else s.noFieldFocused) },
            enabled = focused,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, contentDescription = "Clear TV text")
                }
            },
        )
        IconButton(onClick = onHide) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Hide keyboard, back to remote")
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            focusRequester.requestFocus()
            softKeyboard?.show()
        }
    }
}

/** Full remote pane: big D-pad dial + a row of round action keys. */
@Composable
private fun DpadPane(
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
    openKeyboard: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DpadDial(press = press, ok = ok, okLong = okLong)
        Spacer(Modifier.height(32.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = { press(HidCommand.Menu) })
            RoundKey(
                Icons.Rounded.Home,
                "Home (hold: Control Center)",
                onClick = { press(HidCommand.Home) },
                onLongClick = { hold(HidCommand.Home) },
            )
            RoundKey(Icons.Rounded.PlayArrow, "Play/Pause", onClick = { press(HidCommand.PlayPause) })
            RoundKey(Icons.Rounded.Keyboard, "Keyboard", onClick = { openKeyboard() })
        }
        Spacer(Modifier.height(20.dp))
        VolumePill(press)
    }
}

/** The large circular D-pad with a centre OK (tap = select, hold = menu). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DpadDial(press: (HidCommand) -> Unit, ok: () -> Unit, okLong: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DialArrow(Icons.Rounded.KeyboardArrowUp, "Up", Modifier.align(Alignment.TopCenter)) { press(HidCommand.Up) }
        DialArrow(Icons.Rounded.KeyboardArrowDown, "Down", Modifier.align(Alignment.BottomCenter)) { press(HidCommand.Down) }
        DialArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", Modifier.align(Alignment.CenterStart)) { press(HidCommand.Left) }
        DialArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", Modifier.align(Alignment.CenterEnd)) { press(HidCommand.Right) }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp).clip(CircleShape)
                .combinedClickable(onClick = ok, onLongClick = okLong),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "OK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DialArrow(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(76.dp)) {
        Icon(icon, contentDescription = label, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun VolumePill(press: (HidCommand) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { press(HidCommand.VolumeDown) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = "Volume down")
            }
            Text(
                "VOL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { press(HidCommand.VolumeUp) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Volume up")
            }
        }
    }
}

/**
 * Compact but complete remote shown above the soft keyboard. Laid out
 * **horizontally** — function keys flank the arrow cross — so its height is
 * just the cross (3 rows) and nothing gets pushed off-screen by the keyboard.
 *
 *   back              ↑              vol+
 *   home        ←   [OK]   →         vol-
 *   play              ↓              back
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactRemote(
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left column: navigation functions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { press(HidCommand.Menu) }
            MiniKey(
                Icons.Rounded.Home,
                "Home (hold: Control Center)",
                onLongClick = { hold(HidCommand.Home) },
            ) { press(HidCommand.Home) }
            MiniKey(Icons.Rounded.PlayArrow, "Play/Pause") { press(HidCommand.PlayPause) }
        }

        // Centre: aligned arrow cross with OK
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniKey(Icons.Rounded.KeyboardArrowUp, "Up") { press(HidCommand.Up) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiniKey(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left") { press(HidCommand.Left) }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .combinedClickable(onClick = ok, onLongClick = okLong),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "OK",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                MiniKey(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right") { press(HidCommand.Right) }
            }
            MiniKey(Icons.Rounded.KeyboardArrowDown, "Down") { press(HidCommand.Down) }
        }

        // Right column: volume
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniKey(Icons.Rounded.Add, "Volume up") { press(HidCommand.VolumeUp) }
            MiniKey(Icons.Rounded.Remove, "Volume down") { press(HidCommand.VolumeDown) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniKey(
    icon: ImageVector,
    label: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TouchpadPane(
    viewModel: AppViewModel,
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
    openKeyboard: () -> Unit,
) {
    val lastTouch = remember { mutableStateOf(500L to 500L) }
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { viewModel.touchTap() }, onLongPress = { viewModel.holdButton(HidCommand.Select) })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val x = (offset.x * 1000 / size.width).toLong()
                            val y = (offset.y * 1000 / size.height).toLong()
                            lastTouch.value = x to y
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Press)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = (change.position.x * 1000 / size.width).toLong()
                            val y = (change.position.y * 1000 / size.height).toLong()
                            lastTouch.value = x to y
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Hold)
                        },
                        onDragEnd = {
                            val (x, y) = lastTouch.value
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Release)
                        },
                        onDragCancel = {
                            val (x, y) = lastTouch.value
                            viewModel.sendTouch(x, y, dev.companionremote.protocol.client.TouchPhase.Release)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // Faint directional hints around the edges.
            Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.align(Alignment.TopCenter).padding(8.dp).size(28.dp), tint = hintColor)
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.align(Alignment.BottomCenter).padding(8.dp).size(28.dp), tint = hintColor)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.align(Alignment.CenterStart).padding(8.dp).size(28.dp), tint = hintColor)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.align(Alignment.CenterEnd).padding(8.dp).size(28.dp), tint = hintColor)
            Text(
                LocalAppStrings.current.swipeHint,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "Back", size = 50.dp, onClick = { press(HidCommand.Menu) })
            RoundKey(
                Icons.Rounded.Home,
                "Home (hold: Control Center)",
                size = 50.dp,
                onClick = { press(HidCommand.Home) },
                onLongClick = { hold(HidCommand.Home) },
            )
            RoundKey(Icons.Rounded.PlayArrow, "Play/Pause", size = 50.dp, onClick = { press(HidCommand.PlayPause) })
            RoundKey(Icons.Rounded.Keyboard, "Keyboard", size = 50.dp, onClick = openKeyboard)
            RoundKey(Icons.Rounded.Remove, "Volume down", size = 50.dp, onClick = { press(HidCommand.VolumeDown) })
            RoundKey(Icons.Rounded.Add, "Volume up", size = 50.dp, onClick = { press(HidCommand.VolumeUp) })
        }
    }
}

@Composable
private fun AppsPane(viewModel: AppViewModel) {
    val s = LocalAppStrings.current
    val apps by viewModel.apps.collectAsState()
    val error by viewModel.appsError.collectAsState()
    val fetchIcons by viewModel.fetchAppIcons.collectAsState()
    Column(Modifier.fillMaxSize()) {
        // Quick toggle for real icons (also in Settings).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                s.appIcons,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = fetchIcons, onCheckedChange = { viewModel.setFetchAppIcons(it) })
        }
        Box(Modifier.fillMaxSize()) {
            AppsGrid(viewModel, apps, error, fetchIcons)
        }
    }
}

@Composable
private fun AppsGrid(
    viewModel: AppViewModel,
    apps: List<Pair<String, String>>?,
    error: String?,
    fetchIcons: Boolean,
) {
    when {
        error != null -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { viewModel.loadApps(force = true) }) { Text(LocalAppStrings.current.retry) }
        }
        apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            items(apps!!, key = { it.first }) { (bundleId, name) ->
                ElevatedCard(
                    onClick = { viewModel.launchApp(bundleId) },
                    modifier = Modifier.padding(6.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        AppIcon(bundleId = bundleId, name = name, fetchIcons = fetchIcons)
                        Spacer(Modifier.height(12.dp))
                        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** A round tonal key; supports an optional long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundKey(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp = 60.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, Modifier.size(size * 0.42f))
        }
    }
}
