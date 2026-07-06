package dev.companionremote.cli

import dev.companionremote.protocol.client.CompanionClient
import dev.companionremote.protocol.client.HidCommand
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.crypto.CompanionSessionCipher
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.hap.PairVerify
import dev.companionremote.protocol.transport.SocketTransport
import dev.companionremote.protocol.transport.Transport
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class Commands(private val options: Map<String, String>) {

    private val credentialsFile = File(options["credentials"] ?: "credentials.json")
    private val store = CredentialsStore(credentialsFile)

    suspend fun run(command: String, positional: List<String>) {
        when (command) {
            "scan" -> scan()
            "pair" -> pair()
            "verify" -> verify()
            "command" -> button(positional.firstOrNull() ?: fail("command name required"))
            "apps" -> withClient { client ->
                for ((bundleId, appName) in client.appList().toList().sortedBy { it.second }) {
                    println("%-40s %s".format(bundleId, appName))
                }
            }
            "launch" -> withClient { client ->
                client.launchApp(positional.firstOrNull() ?: fail("bundle id required"))
                println("launched")
            }
            "power" -> withClient { client ->
                val state = client.fetchAttentionState()
                println("power state: ${state ?: "unknown (not supported by this tvOS; use events)"}")
            }
            "swipe" -> swipe(positional.firstOrNull() ?: fail("direction required"))
            "tap" -> withClient { client -> client.tap(); println("tap sent") }
            "text-set" -> textCommand { it.textSet(positional.firstOrNull() ?: fail("text required")) }
            "text-append" -> textCommand { it.textAppend(positional.firstOrNull() ?: fail("text required")) }
            "text-clear" -> textCommand { it.textClear() }
            "text-get" -> textCommand { it.textGet() }
            "focus-state" -> focusState()
            "text-live" -> textLive(positional.firstOrNull() ?: fail("text required"))
            else -> fail("unknown command: $command")
        }
    }

    private suspend fun button(name: String) {
        // "select" as a click uses the touchpad tap (adds the _hidT Click
        // event); a bare _hidC select is misread as a long-press on the tvOS
        // home screen (real-device finding, see docs/protocol-notes.md).
        if (name.lowercase() == "select") {
            withClient { it.tap() }
            println("select (tap) sent")
            return
        }
        val command = when (name.lowercase()) {
            "up" -> HidCommand.Up
            "down" -> HidCommand.Down
            "left" -> HidCommand.Left
            "right" -> HidCommand.Right
            "menu", "back" -> HidCommand.Menu
            "select" -> HidCommand.Select
            "home" -> HidCommand.Home
            "volume_up" -> HidCommand.VolumeUp
            "volume_down" -> HidCommand.VolumeDown
            "siri" -> HidCommand.Siri
            "screensaver" -> HidCommand.Screensaver
            "sleep" -> HidCommand.Sleep
            "wake" -> HidCommand.Wake
            "play_pause" -> HidCommand.PlayPause
            "channel_up" -> HidCommand.ChannelIncrement
            "channel_down" -> HidCommand.ChannelDecrement
            "guide" -> HidCommand.Guide
            "page_up" -> HidCommand.PageUp
            "page_down" -> HidCommand.PageDown
            else -> fail("unknown button: $name")
        }
        withClient { client ->
            when (command) {
                HidCommand.Wake -> client.wake()
                HidCommand.Sleep -> client.sleep()
                else -> client.pressButton(command)
            }
            println("$name sent")
        }
    }

    private suspend fun swipe(direction: String) {
        withClient { client ->
            when (direction.lowercase()) {
                "up" -> client.swipe(500, 750, 500, 250, 300)
                "down" -> client.swipe(500, 250, 500, 750, 300)
                "left" -> client.swipe(750, 500, 250, 500, 300)
                "right" -> client.swipe(250, 500, 750, 500, 300)
                else -> fail("unknown swipe direction: $direction")
            }
            println("swipe $direction sent")
        }
    }

    private suspend fun textCommand(block: suspend (CompanionClient) -> String?) {
        withClient { client ->
            val result = block(client)
            if (result == null) {
                println("no text field is focused on the TV — focus one first")
            } else {
                println("text is now: \"$result\"")
            }
        }
    }

    /**
     * Keep the connection open, wait for a text field on the TV to gain
     * focus (the `_tiStarted` event that carries the session UUID), then
     * type [text]. This mirrors how the Android app works (long-lived
     * connection), unlike the one-shot `text-set`.
     */
    private suspend fun textLive(text: String) {
        withClient { client ->
            println("connected — now focus a text field on the TV (e.g. a search box).")
            println("waiting up to 60s for focus…")
            val focused = kotlinx.coroutines.withTimeoutOrNull(60_000) {
                client.keyboardFocus.first { it == dev.companionremote.protocol.client.KeyboardFocusState.Focused }
                true
            }
            if (focused == null) {
                println("no text field became focused — giving up")
                return@withClient
            }
            println("focused! typing…")
            val result = client.textSet(text)
            if (result == null) {
                println("could not type (session UUID not available)")
            } else {
                println("typed — TV field should now read: \"$result\"")
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    private suspend fun focusState() {
        withClient { client ->
            println("watching keyboard focus + all events (Ctrl-C to stop)…")
            println("current state: ${client.keyboardFocus.value}")
            kotlinx.coroutines.coroutineScope {
                launch {
                    client.events.collect { event ->
                        println("event: ${event.name}  content-keys=${event.content.keys}")
                    }
                }
                client.keyboardFocus.collect { state ->
                    println("focus: $state")
                }
            }
        }
    }

    /** Connect + verify + connect-sequence, run [block], then tear down. */
    internal suspend fun withClient(block: suspend (CompanionClient) -> Unit) {
        val (host, transport) = connectTransport()
        val credentials = loadCredentials(host)
        val client = CompanionClient(CompanionConnection(transport), credentials)
        try {
            client.connect()
            block(client)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private suspend fun scan() {
        println("Scanning for Apple TVs (5 s)...")
        val devices = discover()
        if (devices.isEmpty()) {
            println("No Companion devices found. Same subnet/VLAN? mDNS allowed?")
            return
        }
        for (device in devices) {
            println("${device.name}")
            println("  address: ${device.address}:${device.port}")
            device.model?.let { println("  model:   $it") }
            device.flags?.let { println("  flags:   $it") }
        }
    }

    internal suspend fun connectTransport(): Pair<String, Transport> {
        val host = options["host"] ?: fail("--host is required")
        val port = options["port"]?.toInt() ?: run {
            println("Resolving Companion port for $host via mDNS...")
            resolvePort(host)
        }
        println("Connecting to $host:$port")
        var transport: Transport = SocketTransport.connect(host, port)
        if (options["dump-frames"] == "true") transport = DumpTransport(transport)
        return host to transport
    }

    private suspend fun pair() {
        val (host, transport) = connectTransport()
        val connection = CompanionConnection(transport)
        connection.start()
        try {
            val pairSetup = PairSetup(connection, name = options["name"] ?: "CyberRemote")
            pairSetup.startPairing()
            print("Enter the PIN shown on the TV: ")
            val pin = readLine()?.trim() ?: fail("no PIN entered")
            val credentials = pairSetup.finishPairing(pin)
            store.save(host, credentials.toString())
            println("Paired! Credentials stored in ${credentialsFile.absolutePath}")
        } finally {
            connection.close()
        }
    }

    private suspend fun verify() {
        val (host, transport) = connectTransport()
        val credentials = loadCredentials(host)
        val connection = CompanionConnection(transport)
        connection.start()
        try {
            val keys = PairVerify(connection, credentials).verify()
            connection.enableEncryption(CompanionSessionCipher(keys.outputKey, keys.inputKey))
            println("pair-verify OK — session keys derived, encryption enabled")
        } finally {
            connection.close()
        }
    }

    internal fun loadCredentials(host: String): HapCredentials {
        val stored = store.load(host)
            ?: fail("no credentials for $host in ${credentialsFile.absolutePath} — run pair first")
        return HapCredentials.parse(stored)
    }
}
