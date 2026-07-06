package dev.companionremote.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class DiscoveredAtv(
    val name: String,
    val host: String,
    val port: Int,
    val model: String?,
)

/**
 * mDNS discovery of `_companion-link._tcp` services via NsdManager.
 * A WifiManager.MulticastLock is held while discovering — without it,
 * discovery silently returns nothing on most devices.
 */
class AtvDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Browse for [durationMs] and return every resolved Apple TV.
     * Resolution happens sequentially (NsdManager allows one resolve at a
     * time on older Android versions).
     */
    suspend fun scan(durationMs: Long = 6_000, onDevice: (DiscoveredAtv) -> Unit) {
        val multicastLock = wifiManager.createMulticastLock("companion-remote-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = LinkedHashMap<String, NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found[serviceInfo.serviceName] = serviceInfo }
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            val deadline = System.currentTimeMillis() + durationMs
            val resolved = mutableSetOf<String>()
            while (System.currentTimeMillis() < deadline) {
                val pending = synchronized(found) {
                    found.entries.firstOrNull { it.key !in resolved }
                }
                if (pending == null) {
                    kotlinx.coroutines.delay(200)
                    continue
                }
                resolved.add(pending.key)
                resolve(pending.value)?.let(onDevice)
            }
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            runCatching { multicastLock.release() }
        }
    }

    /**
     * Re-resolve the current host/port for a service by name, returning as
     * soon as it is found. The Companion port is ephemeral (changes after
     * reboot) so this runs on every connect.
     */
    suspend fun resolveByName(name: String, timeoutMs: Long = 6_000): DiscoveredAtv? =
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val found = CompletableDeferred<DiscoveredAtv>()
                val scanJob = launch {
                    scan(timeoutMs) { device ->
                        if (device.name == name) found.complete(device)
                    }
                }
                val device = found.await()
                scanJob.cancel()
                device
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): DiscoveredAtv? =
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { continuation ->
                nsdManager.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress
                            if (host == null) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            val model = serviceInfo.attributes["rpMd"]?.toString(Charsets.UTF_8)
                            val flags = serviceInfo.attributes["rpFl"]?.toString(Charsets.UTF_8)
                                ?.removePrefix("0x")?.toIntOrNull(16) ?: 0
                            // The `_companion-link._tcp` service is also
                            // advertised by Macs and HomePods. Keep only Apple
                            // TVs: model starts with "AppleTV", or (model
                            // missing) the rpFl PIN-pairable bit is set — Macs
                            // and HomePods do not set it.
                            val isAppleTv = model?.startsWith("AppleTV") == true ||
                                (model.isNullOrEmpty() && (flags and PAIRABLE_MASK) != 0)
                            if (!isAppleTv) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    DiscoveredAtv(
                                        name = serviceInfo.serviceName,
                                        host = host,
                                        port = serviceInfo.port,
                                        model = model,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }

    companion object {
        const val SERVICE_TYPE = "_companion-link._tcp."

        // pyatv PAIRING_WITH_PIN_SUPPORTED_MASK — set on Apple TVs, not on
        // Macs/HomePods (pyatv/protocols/companion/__init__.py).
        private const val PAIRABLE_MASK = 0x4000
    }
}
