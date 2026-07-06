package dev.companionremote.protocol.companion

import dev.companionremote.protocol.opack.Opack
import dev.companionremote.protocol.transport.Transport
import kotlinx.coroutines.channels.Channel

/**
 * Test transport: written frames can be inspected; incoming data is fed
 * through a channel (in arbitrary segmentation).
 */
class FakeTransport : Transport {
    val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val writtenChannel = Channel<ByteArray>(Channel.UNLIMITED)
    var closed = false
        private set

    override suspend fun read(): ByteArray? {
        val result = incoming.receiveCatching()
        return result.getOrNull()
    }

    override suspend fun write(data: ByteArray) {
        writtenChannel.trySend(data)
    }

    override fun close() {
        closed = true
        incoming.close()
    }

    /** Await the next write (a full header+payload chunk). */
    suspend fun nextWrite(): ByteArray = writtenChannel.receive()

    /** Feed a full frame to the connection. */
    fun respond(type: FrameType, payload: ByteArray) {
        incoming.trySend(frameHeader(type, payload.size) + payload)
    }

    fun respondOpack(type: FrameType, message: Map<String, Any?>) {
        respond(type, Opack.pack(message))
    }
}

/** Decode the OPACK dict from a written frame (plaintext). */
@Suppress("UNCHECKED_CAST")
fun decodeWrittenFrame(data: ByteArray): Pair<FrameType, Map<Any?, Any?>> {
    val type = FrameType.fromCode(data[0].toInt() and 0xFF)!!
    val payload = data.copyOfRange(FRAME_HEADER_LENGTH, data.size)
    return type to (Opack.unpack(payload).first as Map<Any?, Any?>)
}
