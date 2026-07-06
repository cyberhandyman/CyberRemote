package dev.companionremote.protocol.companion

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameReaderTest {

    private fun frame(type: FrameType, payload: ByteArray): ByteArray =
        frameHeader(type, payload.size) + payload

    @Test
    fun `parses a complete frame`() {
        val reader = FrameReader()
        val frames = reader.feed(frame(FrameType.E_OPACK, byteArrayOf(1, 2, 3)))
        assertEquals(1, frames.size)
        val (header, payload) = frames[0]
        assertEquals(FrameType.E_OPACK.code, header[0].toInt())
        assertArrayEquals(byteArrayOf(1, 2, 3), payload)
    }

    @Test
    fun `handles byte-by-byte segmentation`() {
        val reader = FrameReader()
        val data = frame(FrameType.PS_Next, ByteArray(300) { it.toByte() })
        val collected = mutableListOf<Pair<ByteArray, ByteArray>>()
        for (b in data) collected += reader.feed(byteArrayOf(b))
        assertEquals(1, collected.size)
        assertArrayEquals(ByteArray(300) { it.toByte() }, collected[0].second)
    }

    @Test
    fun `handles multiple frames in one chunk plus a partial tail`() {
        val reader = FrameReader()
        val f1 = frame(FrameType.E_OPACK, byteArrayOf(1))
        val f2 = frame(FrameType.E_OPACK, byteArrayOf(2, 2))
        val f3 = frame(FrameType.E_OPACK, byteArrayOf(3, 3, 3))
        val frames = reader.feed(f1 + f2 + f3.copyOfRange(0, 5)).toMutableList()
        assertEquals(2, frames.size)
        frames += reader.feed(f3.copyOfRange(5, f3.size))
        assertEquals(3, frames.size)
        assertArrayEquals(byteArrayOf(3, 3, 3), frames[2].second)
    }

    @Test
    fun `three byte length is big endian`() {
        val reader = FrameReader()
        val payload = ByteArray(0x010203)
        val frames = reader.feed(frame(FrameType.E_OPACK, payload))
        assertEquals(1, frames.size)
        assertEquals(0x010203, frames[0].second.size)
        // header bytes 1..3 encode the length big-endian
        assertTrue(frames[0].first.contentEquals(byteArrayOf(0x08, 0x01, 0x02, 0x03)))
    }

    @Test
    fun `zero length frame`() {
        val reader = FrameReader()
        val frames = reader.feed(frame(FrameType.NoOp, ByteArray(0)))
        assertEquals(1, frames.size)
        assertEquals(0, frames[0].second.size)
    }
}
