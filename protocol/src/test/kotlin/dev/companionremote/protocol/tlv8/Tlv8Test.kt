package dev.companionremote.protocol.tlv8

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Tlv8Test {

    @Test
    fun `write simple entries`() {
        val out = Tlv8.write(
            linkedMapOf(
                TlvValue.METHOD to byteArrayOf(0x00),
                TlvValue.SEQ_NO to byteArrayOf(0x01),
            ),
        )
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x06, 0x01, 0x01), out)
    }

    @Test
    fun `read simple entries`() {
        val tlv = Tlv8.read(byteArrayOf(0x00, 0x01, 0x00, 0x06, 0x01, 0x01))
        assertEquals(2, tlv.size)
        assertArrayEquals(byteArrayOf(0x00), tlv[TlvValue.METHOD])
        assertArrayEquals(byteArrayOf(0x01), tlv[TlvValue.SEQ_NO])
    }

    @Test
    fun `values larger than 255 bytes are fragmented and merged`() {
        val value = ByteArray(600) { (it % 251).toByte() }
        val written = Tlv8.write(linkedMapOf(TlvValue.PUBLIC_KEY to value))
        // 255 + 255 + 90 → three fragments, each with its own tag+length
        assertEquals(600 + 3 * 2, written.size)
        assertEquals(TlvValue.PUBLIC_KEY, written[0].toInt())
        assertEquals(255, written[1].toInt() and 0xFF)
        val readBack = Tlv8.read(written)
        assertArrayEquals(value, readBack[TlvValue.PUBLIC_KEY])
    }

    @Test
    fun `round trip multiple entries`() {
        val entries = linkedMapOf(
            TlvValue.IDENTIFIER to "Pair-Setup".toByteArray(),
            TlvValue.PUBLIC_KEY to ByteArray(384) { it.toByte() },
            TlvValue.PROOF to ByteArray(64) { (it * 3).toByte() },
        )
        val readBack = Tlv8.read(Tlv8.write(entries))
        assertEquals(entries.keys, readBack.keys)
        for ((tag, value) in entries) assertArrayEquals(value, readBack[tag])
    }

    @Test
    fun `empty value writes tag with zero length`() {
        val out = Tlv8.write(linkedMapOf(TlvValue.METHOD to ByteArray(0)))
        assertArrayEquals(byteArrayOf(0x00, 0x00), out)
        assertArrayEquals(ByteArray(0), Tlv8.read(out)[TlvValue.METHOD])
    }
}
