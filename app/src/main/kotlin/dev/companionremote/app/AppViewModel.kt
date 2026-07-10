package dev.companionremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.companionremote.app.data.AppSkin
import dev.companionremote.app.data.CredentialsRepository
import dev.companionremote.app.data.HapticStrength
import dev.companionremote.app.data.SettingsRepository
import dev.companionremote.app.data.ThemeMode
import dev.companionremote.app.discovery.AtvDiscovery
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.app.i18n.AppLanguage
import dev.companionremote.app.i18n.AppStrings
import dev.companionremote.app.i18n.EnglishStrings
import dev.companionremote.app.i18n.currentSystemLanguage
import dev.companionremote.app.i18n.resolveStrings
import dev.companionremote.protocol.client.CompanionClient
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.client.TouchPhase
import kotlinx.coroutines.channels.Channel
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.app.update.AppUpdater
import dev.companionremote.app.update.UpdateInfo
import dev.companionremote.protocol.transport.SocketTransport
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which screen is showing. */
sealed interface Screen {
    data object DeviceList : Screen
    data object Settings : Screen
    data class Pairing(val device: DiscoveredAtv) : Screen
    data class Remote(val device: DiscoveredAtv) : Screen
}

enum class ConnectionState { Connecting, Connected, Disconnected }

/** In-app update lifecycle (GitHub Releases). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Checking(val manual: Boolean) : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String?) : UpdateState
}

data class PairingUi(
    val awaitingPin: Boolean = false,
    val working: Boolean = true,
    val error: String? = null,
)

data class DeviceListUi(
    val devices: List<DiscoveredAtv> = emptyList(),
    val pairedNames: Set<String> = emptySet(),
    val scanning: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val discovery = AtvDiscovery(application)
    private val credentialsRepository = CredentialsRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val screen = MutableStateFlow<Screen>(Screen.DeviceList)
    val deviceList = MutableStateFlow(DeviceListUi())
    val pairing = MutableStateFlow(PairingUi())
    val connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionError = MutableStateFlow<String?>(null)

    /** Language choice (persisted); drives the UI strings. */
    val language = MutableStateFlow(AppLanguage.System)

    /** Theme mode (persisted). */
    val themeMode = MutableStateFlow(ThemeMode.System)

    /** Visual skin (persisted). */
    val skin = MutableStateFlow(AppSkin.Midnight)

    /** Whether to fetch real app icons over the network (opt-in). */
    val fetchAppIcons = MutableStateFlow(false)

    /** Button-press vibration feedback (persisted). */
    val hapticEnabled = MutableStateFlow(true)
    val hapticStrength = MutableStateFlow(HapticStrength.Medium)

    /** Whether the first-run remote tutorial has already been shown. */
    val introSeen = MutableStateFlow(false)

    /** In-app update settings + state. */
    val autoCheckUpdates = MutableStateFlow(true)
    val autoDownloadUpdates = MutableStateFlow(false)
    val updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private var updateJob: Job? = null

    // Where to return when leaving Settings (device list or the remote).
    private var settingsReturnTo: Screen = Screen.DeviceList

    /** Paired device names, shown in Settings for management. */
    val pairedDevices = MutableStateFlow<List<String>>(emptyList())

    // Current strings, used for error messages produced in the ViewModel.
    private var strings: AppStrings = EnglishStrings

    /** Keyboard focus state on the TV (drives auto-open of the soft keyboard). */
    val keyboardFocus = MutableStateFlow(KeyboardFocusState.Unknown)

    /** The phone-side edit buffer mirrored to the TV text field. */
    val keyboardText = MutableStateFlow("")

    /** Set while the remote screen is active. */
    var client: CompanionClient? = null
        private set
    private var pairSetup: PairSetup? = null
    private var pairingConnection: CompanionConnection? = null
    private var reconnectJob: Job? = null
    private var keyboardFocusJob: Job? = null
    private var textSyncJob: Job? = null

    /** Launchable apps (bundle id → name); null until loaded. */
    val apps = MutableStateFlow<List<Pair<String, String>>?>(null)
    val appsError = MutableStateFlow<String?>(null)

    // Touch events must reach the device in order: single consumer channel.
    private val touchEvents = Channel<Triple<Long, Long, TouchPhase>>(capacity = 256)
    private var touchJob: Job? = null
    private var lastHoldSentAt = 0L

    init {
        viewModelScope.launch {
            settingsRepository.language.collect { lang ->
                language.value = lang
                strings = resolveStrings(lang, currentSystemLanguage())
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { themeMode.value = it }
        }
        viewModelScope.launch {
            settingsRepository.skin.collect { skin.value = it }
        }
        viewModelScope.launch {
            settingsRepository.fetchAppIcons.collect { fetchAppIcons.value = it }
        }
        viewModelScope.launch {
            settingsRepository.hapticEnabled.collect { hapticEnabled.value = it }
        }
        viewModelScope.launch {
            settingsRepository.hapticStrength.collect { hapticStrength.value = it }
        }
        viewModelScope.launch {
            settingsRepository.introSeen.collect { introSeen.value = it }
        }
        viewModelScope.launch {
            settingsRepository.autoCheckUpdates.collect { autoCheckUpdates.value = it }
        }
        viewModelScope.launch {
            settingsRepository.autoDownloadUpdates.collect { autoDownloadUpdates.value = it }
        }
        // One-shot update check on launch, if enabled.
        viewModelScope.launch {
            if (settingsRepository.autoCheckUpdates.first()) checkForUpdates(manual = false)
        }
        startScan()
    }

    // Settings

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(lang) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setSkin(skin: AppSkin) {
        viewModelScope.launch { settingsRepository.setSkin(skin) }
    }

    fun setFetchAppIcons(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFetchAppIcons(enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticEnabled(enabled) }
    }

    fun setHapticStrength(strength: HapticStrength) {
        viewModelScope.launch { settingsRepository.setHapticStrength(strength) }
    }

    fun markIntroSeen() {
        viewModelScope.launch { settingsRepository.setIntroSeen(true) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckUpdates(enabled) }
    }

    fun setAutoDownloadUpdates(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDownloadUpdates(enabled) }
    }

    // In-app updates (GitHub Releases)

    fun checkForUpdates(manual: Boolean) {
        if (updateState.value is UpdateState.Downloading) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            updateState.value = UpdateState.Checking(manual)
            val info = runCatching { AppUpdater.check(BuildConfig.VERSION_NAME) }.getOrNull()
            when {
                info == null -> updateState.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                autoDownloadUpdates.value -> startDownload(info)
                else -> updateState.value = UpdateState.Available(info)
            }
        }
    }

    fun downloadUpdate() {
        val info = when (val st = updateState.value) {
            is UpdateState.Available -> st.info
            is UpdateState.Failed -> return
            else -> return
        }
        startDownload(info)
    }

    private fun startDownload(info: UpdateInfo) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            updateState.value = UpdateState.Downloading(info, 0f)
            runCatching {
                AppUpdater.download(getApplication(), info) { p ->
                    updateState.value = UpdateState.Downloading(info, p)
                }
            }.onSuccess { file ->
                updateState.value = UpdateState.Ready(info, file)
            }.onFailure {
                updateState.value = UpdateState.Failed(it.message)
            }
        }
    }

    fun installUpdate() {
        val st = updateState.value as? UpdateState.Ready ?: return
        runCatching { AppUpdater.install(getApplication(), st.file) }
    }

    fun dismissUpdate() {
        updateJob?.cancel()
        updateState.value = UpdateState.Idle
    }

    fun openSettings() {
        settingsReturnTo = screen.value
        viewModelScope.launch {
            pairedDevices.value = credentialsRepository.pairedDeviceNames().sorted()
            screen.value = Screen.Settings
        }
    }

    fun closeSettings() {
        // Return to wherever Settings was opened from (device list or remote).
        screen.value = settingsReturnTo
    }

    fun forgetDeviceByName(name: String) {
        viewModelScope.launch {
            credentialsRepository.delete(name)
            pairedDevices.value = credentialsRepository.pairedDeviceNames().sorted()
            deviceList.value = deviceList.value.copy(pairedNames = deviceList.value.pairedNames - name)
        }
    }

    fun startScan() {
        if (deviceList.value.scanning) return
        viewModelScope.launch {
            val pairedNames = credentialsRepository.pairedDeviceNames().toSet()
            deviceList.value = deviceList.value.copy(scanning = true, pairedNames = pairedNames)
            runCatching {
                discovery.scan(durationMs = 6_000) { device ->
                    val current = deviceList.value
                    if (current.devices.none { it.name == device.name }) {
                        deviceList.value = current.copy(devices = current.devices + device)
                    }
                }
            }
            deviceList.value = deviceList.value.copy(scanning = false)
        }
    }

    fun addManualDevice(host: String, port: Int) {
        val device = DiscoveredAtv(name = "$host:$port", host = host, port = port, model = null)
        selectDevice(device)
    }

    fun selectDevice(device: DiscoveredAtv) {
        viewModelScope.launch {
            val stored = credentialsRepository.load(device.name)
            if (stored != null) {
                openRemote(device, HapCredentials.parse(stored))
            } else {
                beginPairing(device)
            }
        }
    }

    fun forgetDevice(device: DiscoveredAtv) {
        viewModelScope.launch {
            credentialsRepository.delete(device.name)
            deviceList.value = deviceList.value.copy(
                pairedNames = deviceList.value.pairedNames - device.name,
            )
        }
    }

    // Pairing

    private suspend fun beginPairing(device: DiscoveredAtv) {
        screen.value = Screen.Pairing(device)
        pairing.value = PairingUi(working = true)
        try {
            val transport = SocketTransport.connect(device.host, device.port)
            val connection = CompanionConnection(transport)
            connection.start()
            pairingConnection = connection
            val setup = PairSetup(connection, name = "CyberRemote")
            setup.startPairing()
            pairSetup = setup
            pairing.value = PairingUi(awaitingPin = true, working = false)
        } catch (e: Exception) {
            pairing.value = PairingUi(working = false, error = friendlyError(e))
        }
    }

    fun submitPin(pin: String) {
        val device = (screen.value as? Screen.Pairing)?.device ?: return
        val setup = pairSetup ?: return
        viewModelScope.launch {
            pairing.value = pairing.value.copy(working = true, error = null)
            try {
                val credentials = setup.finishPairing(pin)
                credentialsRepository.save(device.name, credentials.toString())
                pairingConnection?.close()
                pairingConnection = null
                pairSetup = null
                openRemote(device, credentials)
            } catch (e: Exception) {
                pairing.value = PairingUi(working = false, error = friendlyError(e))
                pairingConnection?.close()
                pairingConnection = null
                pairSetup = null
            }
        }
    }

    fun cancelPairing() {
        pairingConnection?.close()
        pairingConnection = null
        pairSetup = null
        screen.value = Screen.DeviceList
    }

    // Remote / connection lifecycle

    private fun openRemote(device: DiscoveredAtv, credentials: HapCredentials) {
        screen.value = Screen.Remote(device)
        connect(device, credentials)
    }

    private fun connect(device: DiscoveredAtv, credentials: HapCredentials) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            connectionState.value = ConnectionState.Connecting
            connectionError.value = null
            // Transient "ws error" right after opening the app is common (the
            // ATV's port rotates, Wi-Fi just woke, etc). Retry a few times,
            // half a second apart, staying in the Connecting state; only
            // surface the error + manual Reconnect after all attempts fail.
            var lastError: Exception? = null
            repeat(RECONNECT_ATTEMPTS) { attempt ->
                if (attempt > 0) delay(RECONNECT_DELAY_MS)
                // The Companion port changes across reboots: re-resolve first,
                // falling back to the last known host/port (manual entry).
                val target = discovery.resolveByName(device.name) ?: device
                try {
                    val transport = SocketTransport.connect(target.host, target.port)
                    val newClient = CompanionClient(CompanionConnection(transport), credentials)
                    newClient.connect()
                    client = newClient
                    connectionState.value = ConnectionState.Connected
                    observeKeyboard(newClient)
                    consumeTouchEvents(newClient)
                    return@launch
                } catch (e: Exception) {
                    client = null
                    lastError = e
                }
            }
            connectionState.value = ConnectionState.Disconnected
            connectionError.value = friendlyError(lastError ?: java.io.IOException("connect failed"))
        }
    }

    fun reconnect() {
        val device = (screen.value as? Screen.Remote)?.device ?: return
        viewModelScope.launch {
            val stored = credentialsRepository.load(device.name) ?: return@launch
            connect(device, HapCredentials.parse(stored))
        }
    }

    /** Called when the remote screen returns to the foreground. */
    fun onForeground() {
        if (screen.value is Screen.Remote && connectionState.value == ConnectionState.Disconnected) {
            reconnect()
        }
    }

    fun closeRemote() {
        reconnectJob?.cancel()
        val current = client
        client = null
        connectionState.value = ConnectionState.Disconnected
        viewModelScope.launch { runCatching { current?.disconnect() } }
        screen.value = Screen.DeviceList
    }

    /** Run a remote-control action, flipping to Disconnected on I/O errors. */
    fun withClient(block: suspend (CompanionClient) -> Unit) {
        val current = client ?: return
        viewModelScope.launch {
            try {
                block(current)
            } catch (e: Exception) {
                connectionState.value = ConnectionState.Disconnected
                connectionError.value = friendlyError(e)
            }
        }
    }

    fun pressButton(command: HidCommand) = withClient { it.pressButton(command) }

    fun holdButton(command: HidCommand) = withClient { it.holdButton(command) }

    // Keyboard (M6): mirror the phone's edit buffer to the TV field

    private fun observeKeyboard(newClient: CompanionClient) {
        keyboardFocusJob?.cancel()
        keyboardFocusJob = viewModelScope.launch {
            newClient.keyboardFocus.collect { state ->
                keyboardFocus.value = state
                if (state == KeyboardFocusState.Focused) {
                    // Pre-fill the edit buffer with what's already in the field
                    runCatching { newClient.textGet() }.getOrNull()?.let { keyboardText.value = it }
                }
            }
        }
    }

    /**
     * Called on every phone-side keystroke. The whole current string is sent
     * (replace semantics) after a short debounce — the simplest reliable way
     * to keep both sides in sync.
     */
    fun onKeyboardTextChanged(text: String) {
        keyboardText.value = text
        textSyncJob?.cancel()
        textSyncJob = viewModelScope.launch {
            delay(250)
            val current = client ?: return@launch
            runCatching { current.textSet(text) }
        }
    }

    fun clearKeyboardText() {
        keyboardText.value = ""
        textSyncJob?.cancel()
        withClient { it.textClear() }
    }

    /**
     * Voice dictation result: replace the focused TV field with [text]
     * immediately (no debounce). A no-op on the TV side when nothing is
     * focused — the UI nudges the user to focus a field first.
     */
    fun dictateText(text: String) {
        if (text.isBlank()) return
        keyboardText.value = text
        textSyncJob?.cancel()
        withClient { it.textSet(text) }
    }

    // Touchpad (M7)

    private fun consumeTouchEvents(newClient: CompanionClient) {
        touchJob?.cancel()
        touchJob = viewModelScope.launch {
            for ((x, y, phase) in touchEvents) {
                runCatching { newClient.touchEvent(x, y, phase) }
            }
        }
    }

    /** Queue a touch event; Hold events are throttled to ~16 ms like pyatv. */
    fun sendTouch(x: Long, y: Long, phase: TouchPhase) {
        if (phase == TouchPhase.Hold) {
            val now = System.currentTimeMillis()
            if (now - lastHoldSentAt < 16) return
            lastHoldSentAt = now
        }
        touchEvents.trySend(Triple(x, y, phase))
    }

    fun touchTap() = withClient { it.tap() }

    // Apps (M7)

    fun loadApps(force: Boolean = false) {
        if (apps.value != null && !force) return
        appsError.value = null
        withClient { current ->
            runCatching { current.appList() }
                .onSuccess { list ->
                    apps.value = list.toList().sortedBy { it.second.lowercase() }
                }
                .onFailure { appsError.value = it.message }
        }
    }

    fun launchApp(bundleId: String) = withClient { it.launchApp(bundleId) }

    fun wake() = withClient { it.wake() }

    fun sleep() = withClient { it.sleep() }

    private fun friendlyError(e: Exception): String = when {
        e.message?.contains("proof mismatch") == true -> strings.wrongPin
        e.message?.contains("ECONNREFUSED") == true || e is java.net.ConnectException -> strings.atvUnreachable
        e is java.net.SocketTimeoutException -> strings.connectionTimedOut
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun onCleared() {
        pairingConnection?.close()
        client?.close()
    }

    private companion object {
        const val RECONNECT_ATTEMPTS = 3
        const val RECONNECT_DELAY_MS = 500L
    }
}
