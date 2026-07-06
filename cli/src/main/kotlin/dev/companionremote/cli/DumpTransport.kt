package dev.companionremote.cli

import dev.companionremote.protocol.transport.Transport

/**
 * Debug transport wrapper: hex-dumps raw traffic (truncated — credentials
 * and derived keys must never be logged in full).
 */
class DumpTransport(private val inner: Transport, private val maxBytes: Int = 64) : Transport {

    override suspend fun read(): ByteArray? {
        val data = inner.read()
        if (data != null) dump("<<", data)
        return data
    }

    override suspend fun write(data: ByteArray) {
        dump(">>", data)
        inner.write(data)
    }

    override fun close() = inner.close()

    private fun dump(direction: String, data: ByteArray) {
        val shown = data.take(maxBytes).joinToString("") { "%02x".format(it) }
        val suffix = if (data.size > maxBytes) "… (${data.size} bytes)" else ""
        System.err.println("$direction $shown$suffix")
    }
}
