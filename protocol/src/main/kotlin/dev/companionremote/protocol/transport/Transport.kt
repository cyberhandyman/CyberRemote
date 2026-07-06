package dev.companionremote.protocol.transport

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal byte transport used by the protocol layer. All I/O in `:protocol`
 * goes through this interface so tests can drive the stack with recorded
 * byte streams.
 */
interface Transport {
    /** Read up to some transport-defined amount of bytes; null on EOF. */
    suspend fun read(): ByteArray?

    /** Write all of [data]. */
    suspend fun write(data: ByteArray)

    fun close()
}

/** TCP socket transport. */
class SocketTransport private constructor(private val socket: Socket) : Transport {

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val buffer = ByteArray(8192)

    override suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        val n = try {
            input.read(buffer)
        } catch (e: Exception) {
            -1
        }
        if (n < 0) null else buffer.copyOf(n)
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        output.write(data)
        output.flush()
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        suspend fun connect(host: String, port: Int, timeoutMs: Int = 10_000): SocketTransport =
            withContext(Dispatchers.IO) {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                SocketTransport(socket)
            }
    }
}
