package dev.companionremote.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.companionremote.app.AppViewModel
import dev.companionremote.app.ConnectionState
import dev.companionremote.app.R
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.app.i18n.LocalAppStrings
import dev.companionremote.app.theme.glass
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
 * Semantic icons + brand-ish colours for Apple's built-in tvOS apps, which
 * aren't in the iTunes catalog (so real artwork can't be fetched). Generic
 * Material icons — no Apple artwork — keep this copyright-clean.
 */
private fun systemAppIcon(bundleId: String): Pair<ImageVector, Color>? = when (bundleId) {
    "com.apple.TVSettings" -> Icons.Rounded.Settings to Color(0xFF8E8E93)
    "com.apple.TVMusic" -> Icons.Rounded.MusicNote to Color(0xFFFA243C)
    "com.apple.podcasts" -> Icons.Rounded.Podcasts to Color(0xFF9B4DE0)
    "com.apple.TVPhotos" -> Icons.Rounded.PhotoLibrary to Color(0xFFF5A623)
    "com.apple.Fitness" -> Icons.Rounded.FitnessCenter to Color(0xFF30D158)
    "com.apple.Sing" -> Icons.Rounded.Mic to Color(0xFF5E5CE6)
    "com.apple.TVSearch" -> Icons.Rounded.Search to Color(0xFF8E8E93)
    "com.apple.TVWatchList" -> Icons.Rounded.Tv to Color(0xFF1C1C1E)
    "com.apple.TVAppStore" -> Icons.Rounded.Apps to Color(0xFF0D96F6)
    "com.apple.facetime" -> Icons.Rounded.Videocam to Color(0xFF34C759)
    "com.apple.TVHomeSharing" -> Icons.Rounded.Computer to Color(0xFF8E8E93)
    "com.apple.TestFlight" -> Icons.Rounded.FlightTakeoff to Color(0xFF0D96F6)
    else -> null
}

/**
 * An app tile icon: a generated initial+colour tile by default, or the real
 * App Store artwork when the user has opted into network fetching.
 */
@Composable
private fun AppIcon(bundleId: String, name: String, fetchIcons: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val context = LocalContext.current

    val system = systemAppIcon(bundleId)
    if (system != null) {
        Box(
            Modifier.size(44.dp).clip(shape).background(system.second),
            contentAlignment = Alignment.Center,
        ) {
            Icon(system.first, contentDescription = name, Modifier.size(26.dp), tint = Color.White)
        }
        return
    }

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
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val hapticStrength by viewModel.hapticStrength.collectAsState()
    val introSeen by viewModel.introSeen.collectAsState()
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val softKeyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var powerMenu by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var introDismissed by remember { mutableStateOf(false) }

    val buzz = rememberHaptic(hapticEnabled, hapticStrength)
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val startVoice = rememberVoiceInput(
        onResult = { viewModel.dictateText(it) },
        onError = { err ->
            toast(
                when (err) {
                    VoiceError.PermissionDenied -> s.voicePermissionNeeded
                    VoiceError.Unavailable -> s.voiceUnavailable
                    VoiceError.Failed -> s.voiceUnavailable
                },
            )
        },
    )

    fun press(command: HidCommand) { buzz(); viewModel.pressButton(command) }
    fun hold(command: HidCommand) { buzz(); viewModel.holdButton(command) }
    fun ok() { buzz(); viewModel.touchTap() }
    fun okLong() { buzz(); viewModel.holdButton(HidCommand.Select) }
    fun volStep(up: Boolean) { viewModel.pressButton(if (up) HidCommand.VolumeUp else HidCommand.VolumeDown) }
    fun volTap(up: Boolean) { buzz(); volStep(up) }
    fun onVoice() {
        buzz()
        if (focusState == KeyboardFocusState.Focused) startVoice() else toast(s.voiceNeedFocus)
    }

    LaunchedEffect(focusState) {
        when (focusState) {
            KeyboardFocusState.Focused -> keyboardOpen = true
            KeyboardFocusState.Unfocused -> keyboardOpen = false
            KeyboardFocusState.Unknown -> Unit
        }
    }
    LaunchedEffect(tab) { if (tab == 2) viewModel.loadApps() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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

            when (connectionState) {
                ConnectionState.Disconnected -> ConnectionBanner(connectionError) { viewModel.reconnect() }
                ConnectionState.Connecting -> ReconnectingBanner()
                ConnectionState.Connected -> Unit
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
                if (imeVisible) {
                    CompactRemote(::press, ::hold, ::ok, ::okLong, ::onVoice, ::volTap)
                } else {
                    DpadPane(::press, ::hold, ::ok, ::okLong, ::onVoice, ::volStep, ::volTap) { softKeyboard?.show() }
                }
            } else {
                when (tab) {
                    0 -> DpadPane(::press, ::hold, ::ok, ::okLong, ::onVoice, ::volStep, ::volTap) { keyboardOpen = true }
                    1 -> TouchpadPane(viewModel, ::press, ::hold, ::ok, ::okLong, ::onVoice, ::volTap) { keyboardOpen = true }
                    2 -> AppsPane(viewModel)
                }
            }
        }
    }

    // First-run tutorial, shown once right after the first pairing.
    if (!introSeen && !introDismissed) {
        IntroOverlay(onDone = { introDismissed = true; viewModel.markIntroSeen() })
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Connected -> ConnectedGreen
        ConnectionState.Connecting -> ConnectingAmber
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

@Composable
private fun ReconnectingBanner() {
    val s = LocalAppStrings.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                s.reconnecting,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit) {
    val s = LocalAppStrings.current
    val titles = listOf(s.tabRemote to Icons.Default.Gamepad, s.tabTouch to Icons.Default.TouchApp, s.tabApps to Icons.Default.Apps)
    TabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
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

/** Full remote pane: big D-pad dial, a row of action keys, volume slider. */
@Composable
private fun DpadPane(
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
    onVoice: () -> Unit,
    volStep: (Boolean) -> Unit,
    volTap: (Boolean) -> Unit,
    openKeyboard: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DpadDial(press = press, ok = ok, okLong = okLong)
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundKey(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", size = 56.dp, onClick = { press(HidCommand.Menu) })
            RoundKey(
                icon = Icons.Rounded.Home,
                label = "Home (hold: Control Center)",
                size = 56.dp,
                onClick = { press(HidCommand.Home) },
                onLongClick = { hold(HidCommand.Home) },
            )
            RoundKey(icon = Icons.Rounded.Mic, label = "Voice", size = 56.dp, accent = true, onClick = onVoice)
            RoundKey(painter = painterResource(R.drawable.ic_play_pause), label = "Play/Pause", size = 56.dp, onClick = { press(HidCommand.PlayPause) })
            RoundKey(icon = Icons.Rounded.Keyboard, label = "Keyboard", size = 56.dp, onClick = openKeyboard)
        }
        Spacer(Modifier.height(24.dp))
        VolumeSlider(onStep = volStep, onTap = volTap)
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
            .glass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DialArrow(Icons.Rounded.KeyboardArrowUp, "Up", Modifier.align(Alignment.TopCenter)) { press(HidCommand.Up) }
        DialArrow(Icons.Rounded.KeyboardArrowDown, "Down", Modifier.align(Alignment.BottomCenter)) { press(HidCommand.Down) }
        DialArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Left", Modifier.align(Alignment.CenterStart)) { press(HidCommand.Left) }
        DialArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Right", Modifier.align(Alignment.CenterEnd)) { press(HidCommand.Right) }
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .combinedClickable(onClick = ok, onLongClick = okLong),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "OK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun DialArrow(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(76.dp)) {
        Icon(icon, contentDescription = label, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Vertical volume control. Tapping the top half raises the volume and the
 * bottom half lowers it; dragging up/down scrubs (one step per ~22 dp). Both
 * gestures live on the whole pill — separate pointerInput blocks so a tap and
 * a drag don't fight each other (the earlier IconButton version swallowed the
 * drag, so swiping did nothing).
 */
@Composable
private fun VolumeSlider(onStep: (Boolean) -> Unit, onTap: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Box(
        Modifier
            .width(64.dp)
            .height(172.dp)
            .glass(shape)
            .pointerInput(Unit) {
                detectTapGestures { offset -> onTap(offset.y < size.height / 2f) }
            }
            .pointerInput(Unit) {
                var acc = 0f
                val step = 22.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f },
                ) { change, dy ->
                    change.consume()
                    acc += dy
                    while (acc <= -step) { onStep(true); acc += step }   // up = louder
                    while (acc >= step) { onStep(false); acc -= step }   // down = softer
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Faint track hint that the pill is swipeable.
        Box(
            Modifier
                .width(4.dp)
                .height(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        )
        Icon(
            Icons.Rounded.Add,
            contentDescription = "Volume up",
            Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            Icons.Rounded.Remove,
            contentDescription = "Volume down",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Compact but complete remote shown above the soft keyboard. Laid out
 * **horizontally** — function keys flank the arrow cross — so its height is
 * just the cross and nothing gets pushed off-screen by the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactRemote(
    press: (HidCommand) -> Unit,
    hold: (HidCommand) -> Unit,
    ok: () -> Unit,
    okLong: () -> Unit,
    onVoice: () -> Unit,
    volTap: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left column: navigation + media
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniKey(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back") { press(HidCommand.Menu) }
            MiniKey(
                icon = Icons.Rounded.Home,
                label = "Home (hold: Control Center)",
                onLongClick = { hold(HidCommand.Home) },
            ) { press(HidCommand.Home) }
            MiniKey(painter = painterResource(R.drawable.ic_play_pause), label = "Play/Pause") { press(HidCommand.PlayPause) }
        }

        // Centre: aligned arrow cross with OK
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniKey(icon = Icons.Rounded.KeyboardArrowUp, label = "Up") { press(HidCommand.Up) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MiniKey(icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, label = "Left") { press(HidCommand.Left) }
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .combinedClickable(onClick = ok, onLongClick = okLong),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "OK",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                MiniKey(icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, label = "Right") { press(HidCommand.Right) }
            }
            MiniKey(icon = Icons.Rounded.KeyboardArrowDown, label = "Down") { press(HidCommand.Down) }
        }

        // Right column: voice + volume
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniKey(icon = Icons.Rounded.Mic, label = "Voice", accent = true) { onVoice() }
            MiniKey(icon = Icons.Rounded.Add, label = "Volume up") { volTap(true) }
            MiniKey(icon = Icons.Rounded.Remove, label = "Volume down") { volTap(false) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniKey(
    label: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    accent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Box(
        Modifier.size(52.dp).glass(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        KeyGlyph(icon, painter, label, 24.dp, tint)
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
    onVoice: () -> Unit,
    volTap: (Boolean) -> Unit,
    openKeyboard: () -> Unit,
) {
    val lastTouch = remember { mutableStateOf(500L to 500L) }
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .glass(RoundedCornerShape(28.dp))
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
            RoundKey(icon = Icons.AutoMirrored.Filled.ArrowBack, label = "Back", size = 48.dp, onClick = { press(HidCommand.Menu) })
            RoundKey(
                icon = Icons.Rounded.Home,
                label = "Home (hold: Control Center)",
                size = 48.dp,
                onClick = { press(HidCommand.Home) },
                onLongClick = { hold(HidCommand.Home) },
            )
            RoundKey(icon = Icons.Rounded.Mic, label = "Voice", size = 48.dp, accent = true, onClick = onVoice)
            RoundKey(painter = painterResource(R.drawable.ic_play_pause), label = "Play/Pause", size = 48.dp, onClick = { press(HidCommand.PlayPause) })
            RoundKey(icon = Icons.Rounded.Keyboard, label = "Keyboard", size = 48.dp, onClick = openKeyboard)
            RoundKey(icon = Icons.Rounded.Remove, label = "Volume down", size = 48.dp, onClick = { volTap(false) })
            RoundKey(icon = Icons.Rounded.Add, label = "Volume up", size = 48.dp, onClick = { volTap(true) })
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

/** A round glass key; supports an optional long-press and an accent tint. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundKey(
    label: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    size: androidx.compose.ui.unit.Dp = 60.dp,
    accent: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    Box(
        Modifier.size(size).glass(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        KeyGlyph(icon, painter, label, size * 0.42f, tint)
    }
}

/** Renders either a vector [icon] or a drawable [painter] as a key glyph. */
@Composable
private fun KeyGlyph(icon: ImageVector?, painter: Painter?, label: String, glyphSize: androidx.compose.ui.unit.Dp, tint: Color) {
    val p = painter ?: icon?.let { rememberVectorPainter(it) } ?: return
    Icon(p, contentDescription = label, Modifier.size(glyphSize), tint = tint)
}
