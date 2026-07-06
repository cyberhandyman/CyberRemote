package dev.companionremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.companionremote.app.data.CredentialsRepository
import dev.companionremote.app.discovery.AtvDiscovery
import dev.companionremote.app.discovery.DiscoveredAtv
import dev.companionremote.protocol.client.CompanionClient
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.client.KeyboardFocusState
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.transport.SocketTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Which screen is showing. */
sealed interface Screen {
    data object DeviceList : Screen
    data class Pairing(val device: DiscoveredAtv) : Screen
    data class Remote(val device: DiscoveredAtv) : Screen
}

enum class ConnectionState { Connecting, Connected, Disconnected }

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

    val screen = MutableStateFlow<Screen>(Screen.DeviceList)
    val deviceList = MutableStateFlow(DeviceListUi())
    val pairing = MutableStateFlow(PairingUi())
    val connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionError = MutableStateFlow<String?>(null)

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

    init {
        startScan()
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
            val setup = PairSetup(connection, name = "Companion Remote")
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
            } catch (e: Exception) {
                client = null
                connectionState.value = ConnectionState.Disconnected
                connectionError.value = friendlyError(e)
            }
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

    fun wake() = withClient { it.wake() }

    fun sleep() = withClient { it.sleep() }

    private fun friendlyError(e: Exception): String = when {
        e.message?.contains("proof mismatch") == true -> "Wrong PIN — try pairing again."
        e.message?.contains("ECONNREFUSED") == true || e is java.net.ConnectException ->
            "Apple TV unreachable. Check that both devices are on the same network."
        e is java.net.SocketTimeoutException -> "Connection timed out."
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun onCleared() {
        pairingConnection?.close()
        client?.close()
    }
}
