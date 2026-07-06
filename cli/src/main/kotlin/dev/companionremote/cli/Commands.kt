package dev.companionremote.cli

import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.crypto.CompanionSessionCipher
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.hap.PairVerify
import dev.companionremote.protocol.transport.SocketTransport
import dev.companionremote.protocol.transport.Transport
import java.io.File

class Commands(private val options: Map<String, String>) {

    private val credentialsFile = File(options["credentials"] ?: "credentials.json")
    private val store = CredentialsStore(credentialsFile)

    suspend fun run(command: String, positional: List<String>) {
        when (command) {
            "scan" -> scan()
            "pair" -> pair()
            "verify" -> verify()
            else -> fail("unknown command: $command")
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
            val pairSetup = PairSetup(connection, name = options["name"] ?: "Companion Remote")
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
