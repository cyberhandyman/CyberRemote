package dev.companionremote.cli

import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

const val COMPANION_SERVICE = "_companion-link._tcp.local."

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val port: Int,
    val model: String?,
    val flags: String?,
)

private fun siteLocalAddresses(): List<InetAddress> =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filter { it.isSiteLocalAddress && it.address.size == 4 }
        .toList()

/**
 * Browse for Companion services for [durationMs]. A JmDNS instance is bound
 * to every site-local interface (VPN/VM adapters would otherwise shadow the
 * real LAN) and results are aggregated.
 */
suspend fun discover(durationMs: Long = 5_000): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
    val devices = LinkedHashMap<String, DiscoveredDevice>()
    val instances = siteLocalAddresses().mapNotNull { address ->
        runCatching { JmDNS.create(address) }.getOrNull()
    }
    try {
        for (jmdns in instances) {
            jmdns.addServiceListener(
                COMPANION_SERVICE,
                object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        jmdns.requestServiceInfo(event.type, event.name, true)
                    }

                    override fun serviceRemoved(event: ServiceEvent) = Unit

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info
                        val address = info.inet4Addresses.firstOrNull()?.hostAddress ?: return
                        synchronized(devices) {
                            devices[event.name] = DiscoveredDevice(
                                name = event.name,
                                address = address,
                                port = info.port,
                                model = info.getPropertyString("rpMd"),
                                flags = info.getPropertyString("rpFl"),
                            )
                        }
                    }
                },
            )
        }
        delay(durationMs)
    } finally {
        for (jmdns in instances) runCatching { jmdns.close() }
    }
    synchronized(devices) { devices.values.toList() }
}

/**
 * Resolve the current Companion port for [host] via mDNS. The port is
 * ephemeral and changes across reboots — never cache it.
 */
suspend fun resolvePort(host: String, durationMs: Long = 5_000): Int {
    val match = discover(durationMs).firstOrNull { it.address == host }
        ?: throw IllegalStateException(
            "could not resolve Companion port for $host via mDNS; pass --port explicitly",
        )
    return match.port
}
