package dev.companionremote.protocol.companion

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompanionConnectionTest {

    @Test
    fun `request response matched by xid`() = runBlocking {
        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()

        val response = async {
            connection.exchangeOpack(
                FrameType.E_OPACK,
                mapOf("_i" to "_hidC", "_t" to MessageType.REQUEST, "_c" to mapOf("_hidC" to 6L)),
            )
        }

        val written = withTimeout(2000) { transport.nextWrite() }
        val (type, message) = decodeWrittenFrame(written)
        assertEquals(FrameType.E_OPACK, type)
        assertEquals("_hidC", message["_i"])
        val xid = message["_x"] as Long

        transport.respondOpack(
            FrameType.E_OPACK,
            mapOf("_t" to MessageType.RESPONSE, "_x" to xid, "_c" to mapOf("ok" to true)),
        )

        val result = withTimeout(2000) { response.await() }
        @Suppress("UNCHECKED_CAST")
        assertEquals(true, (result["_c"] as Map<Any?, Any?>)["ok"])
        connection.close()
    }

    @Test
    fun `responses can arrive out of order`() = runBlocking {
        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()

        val first = async { connection.exchangeOpack(FrameType.E_OPACK, mapOf("_i" to "a", "_t" to 2L)) }
        val w1 = withTimeout(2000) { transport.nextWrite() }
        val second = async { connection.exchangeOpack(FrameType.E_OPACK, mapOf("_i" to "b", "_t" to 2L)) }
        val w2 = withTimeout(2000) { transport.nextWrite() }

        val xid1 = decodeWrittenFrame(w1).second["_x"] as Long
        val xid2 = decodeWrittenFrame(w2).second["_x"] as Long

        // Answer the second request first
        transport.respondOpack(FrameType.E_OPACK, mapOf("_t" to 3L, "_x" to xid2, "_i" to "b"))
        transport.respondOpack(FrameType.E_OPACK, mapOf("_t" to 3L, "_x" to xid1, "_i" to "a"))

        assertEquals("b", withTimeout(2000) { second.await() }["_i"])
        assertEquals("a", withTimeout(2000) { first.await() }["_i"])
        connection.close()
    }

    @Test
    fun `error message in response throws`() = runBlocking {
        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()

        val response = async {
            runCatching { connection.exchangeOpack(FrameType.E_OPACK, mapOf("_i" to "_launchApp", "_t" to 2L)) }
        }
        val written = withTimeout(2000) { transport.nextWrite() }
        val xid = decodeWrittenFrame(written).second["_x"] as Long
        transport.respondOpack(
            FrameType.E_OPACK,
            mapOf("_t" to 3L, "_x" to xid, "_em" to "No request handler", "_ec" to 58822L),
        )

        val result = withTimeout(2000) { response.await() }
        val exception = result.exceptionOrNull()
        assertTrue(exception is CompanionCommandException, "got $exception")
        assertEquals(58822L, (exception as CompanionCommandException).errorCode)
        connection.close()
    }

    @Test
    fun `auth exchange matches on next frame type`() = runBlocking {
        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()

        val response = async {
            connection.exchangeAuth(FrameType.PS_Start, mapOf("_pd" to byteArrayOf(6, 1, 1), "_pwTy" to 1L))
        }
        withTimeout(2000) { transport.nextWrite() }
        // The reply to PS_Start arrives as PS_Next
        transport.respondOpack(FrameType.PS_Next, mapOf("_pd" to byteArrayOf(6, 1, 2)))

        val result = withTimeout(2000) { response.await() }
        assertArrayEquals(byteArrayOf(6, 1, 2), result["_pd"] as ByteArray)
        connection.close()
    }

    @Test
    fun `events are emitted to the flow`() = runBlocking {
        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()

        val eventDeferred = async { connection.events.first() }
        // Give the collector time to subscribe before emitting
        kotlinx.coroutines.delay(50)
        transport.respondOpack(
            FrameType.E_OPACK,
            mapOf("_i" to "_iMC", "_t" to MessageType.EVENT, "_x" to 1L, "_c" to mapOf("_mcF" to 256L)),
        )
        val event = withTimeout(2000) { eventDeferred.await() }
        assertEquals("_iMC", event.name)
        assertEquals(256L, event.content["_mcF"])
        connection.close()
    }

    @Test
    fun `pending exchanges fail when connection closes`() = runBlocking {
        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()

        val response = async {
            runCatching { connection.exchangeOpack(FrameType.E_OPACK, mapOf("_i" to "x", "_t" to 2L)) }
        }
        withTimeout(2000) { transport.nextWrite() }
        transport.incoming.close() // EOF from device

        val result = withTimeout(2000) { response.await() }
        assertTrue(result.exceptionOrNull() is CompanionConnectionClosedException)
    }

    @Test
    fun `cipher is applied to non-empty payloads and aad is the header`() = runBlocking {
        // XOR "cipher" with a fake 16-byte tag that also records the AAD
        val seenAad = mutableListOf<ByteArray>()
        val xorCipher = object : SessionCipher {
            override fun encrypt(data: ByteArray, aad: ByteArray): ByteArray {
                seenAad.add(aad)
                return ByteArray(data.size) { (data[it].toInt() xor 0x55).toByte() } + ByteArray(AUTH_TAG_LENGTH)
            }

            override fun decrypt(data: ByteArray, aad: ByteArray): ByteArray {
                seenAad.add(aad)
                val body = data.copyOfRange(0, data.size - AUTH_TAG_LENGTH)
                return ByteArray(body.size) { (body[it].toInt() xor 0x55).toByte() }
            }
        }

        val transport = FakeTransport()
        val connection = CompanionConnection(transport)
        connection.start()
        connection.enableEncryption(xorCipher)

        val job = launch { connection.sendOpack(FrameType.E_OPACK, mapOf("_i" to "_hidC", "_t" to 1L)) }
        val written = withTimeout(2000) { transport.nextWrite() }
        job.join()

        // Header: type + length including the 16-byte tag
        val payload = written.copyOfRange(FRAME_HEADER_LENGTH, written.size)
        val declaredLength = ((written[1].toInt() and 0xFF) shl 16) or
            ((written[2].toInt() and 0xFF) shl 8) or (written[3].toInt() and 0xFF)
        assertEquals(payload.size, declaredLength)
        assertEquals(FrameType.E_OPACK.code, written[0].toInt())
        assertArrayEquals(written.copyOfRange(0, 4), seenAad[0])

        // Round trip through the fake decryption gives valid OPACK again
        val decrypted = xorCipher.decrypt(payload, written.copyOfRange(0, 4))
        val (value, _) = dev.companionremote.protocol.opack.Opack.unpack(decrypted)
        @Suppress("UNCHECKED_CAST")
        assertEquals("_hidC", (value as Map<Any?, Any?>)["_i"])
        connection.close()
    }
}
