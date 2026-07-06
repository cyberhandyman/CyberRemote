package dev.companionremote.protocol.opack

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden vectors ported from pyatv `tests/support/test_opack.py`.
 *
 * Deviations from the pyatv test file:
 *  - `_sized_int` re-encoding is not supported (we never re-encode decoded
 *    messages); the sized-int unpack tests assert decoded values instead.
 *  - `test_golden` is a real pack∘unpack identity check here (the pyatv
 *    version asserts on a tuple-vs-dict DeepDiff and is vacuous).
 */
class OpackTest {

    private fun hex(s: String): ByteArray {
        check(s.length % 2 == 0)
        return ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun packed(value: Any?): ByteArray = Opack.pack(value)

    private fun unpacked(data: ByteArray): Any? {
        val (value, remaining) = Opack.unpack(data)
        assertEquals(0, remaining.size, "trailing bytes after unpack")
        return value
    }

    /** Deep equality that treats ByteArray by content and numbers by long/double value. */
    private fun deepEquals(a: Any?, b: Any?): Boolean = when {
        a is ByteArray && b is ByteArray -> a.contentEquals(b)
        a is Number && b is Number ->
            if (a is Double || a is Float || b is Double || b is Float) {
                a.toDouble() == b.toDouble()
            } else {
                a.toLong() == b.toLong()
            }
        a is List<*> && b is List<*> ->
            a.size == b.size && a.indices.all { deepEquals(a[it], b[it]) }
        a is Map<*, *> && b is Map<*, *> ->
            a.size == b.size && a.entries.all { (k, v) ->
                b.keys.any { deepEquals(it, k) && deepEquals(b[it], v) }
            }
        else -> a == b
    }

    private fun assertDeepEquals(expected: Any?, actual: Any?) {
        assertTrue(deepEquals(expected, actual), "expected $expected but was $actual")
    }

    // pack

    @Test
    fun `pack unsupported type`() {
        assertThrows(OpackException::class.java) { Opack.pack(setOf(1)) }
    }

    @Test
    fun `pack boolean`() {
        assertArrayEquals(hex("01"), packed(true))
        assertArrayEquals(hex("02"), packed(false))
    }

    @Test
    fun `pack none`() {
        assertArrayEquals(hex("04"), packed(null))
    }

    @Test
    fun `pack uuid`() {
        assertArrayEquals(
            hex("05") + hex("12345678123456781234567812345678"),
            packed(UUID.fromString("12345678-1234-5678-1234-567812345678")),
        )
    }

    @Test
    fun `pack small integers`() {
        assertArrayEquals(hex("08"), packed(0))
        assertArrayEquals(hex("17"), packed(0xF))
        assertArrayEquals(hex("2f"), packed(0x27))
    }

    @Test
    fun `pack larger integers`() {
        assertArrayEquals(hex("3028"), packed(0x28))
        assertArrayEquals(hex("31ff01"), packed(0x1FF))
        assertArrayEquals(hex("32ffffff01"), packed(0x1FFFFFF))
        assertArrayEquals(hex("33ffffffffffffff01"), packed(0x1FFFFFFFFFFFFFFL))
    }

    @Test
    fun `pack negative integer throws`() {
        assertThrows(OpackException::class.java) { Opack.pack(-1) }
    }

    @Test
    fun `pack float32 and float64`() {
        assertArrayEquals(hex("350000803f"), packed(1.0f))
        assertArrayEquals(hex("36000000000000f03f"), packed(1.0))
    }

    @Test
    fun `pack short strings`() {
        assertArrayEquals(hex("4161"), packed("a"))
        assertArrayEquals(hex("43616263"), packed("abc"))
        assertArrayEquals(hex("60") + ByteArray(0x20) { 0x61 }, packed("a".repeat(0x20)))
    }

    @Test
    fun `pack longer strings`() {
        assertArrayEquals(hex("6121") + ByteArray(33) { 0x61 }, packed("a".repeat(33)))
        assertArrayEquals(hex("620001") + ByteArray(256) { 0x61 }, packed("a".repeat(256)))
    }

    @Test
    fun `pack short raw bytes`() {
        assertArrayEquals(hex("71ac"), packed(hex("ac")))
        assertArrayEquals(hex("73123456"), packed(hex("123456")))
        assertArrayEquals(hex("90") + ByteArray(0x20) { 0xAD.toByte() }, packed(ByteArray(0x20) { 0xAD.toByte() }))
    }

    @Test
    fun `pack longer raw bytes`() {
        assertArrayEquals(hex("9121") + ByteArray(33) { 0x61 }, packed(ByteArray(33) { 0x61 }))
        assertArrayEquals(hex("920001") + ByteArray(256) { 0x61 }, packed(ByteArray(256) { 0x61 }))
        assertArrayEquals(hex("9300000100") + ByteArray(65536) { 0x61 }, packed(ByteArray(65536) { 0x61 }))
    }

    @Test
    fun `pack array`() {
        assertArrayEquals(hex("d0"), packed(emptyList<Any?>()))
        assertArrayEquals(hex("d3094474657374" + "02"), packed(listOf(1, "test", false)))
        assertArrayEquals(hex("d1d101"), packed(listOf(listOf(true))))
    }

    @Test
    fun `pack endless array`() {
        assertArrayEquals(
            hex("df4161") + ByteArray(14) { 0xA0.toByte() } + hex("03"),
            packed(List(15) { "a" }),
        )
    }

    @Test
    fun `pack dict`() {
        assertArrayEquals(hex("e0"), packed(emptyMap<Any?, Any?>()))
        assertArrayEquals(hex("e24161140204"), packed(linkedMapOf<Any?, Any?>("a" to 12, false to null)))
        assertArrayEquals(hex("e101e14161" + "0a"), packed(mapOf(true to mapOf("a" to 2))))
    }

    @Test
    fun `pack endless dict`() {
        val dict = LinkedHashMap<Any?, Any?>()
        for (x in 97..126 step 2) dict[x.toChar().toString()] = (x + 1).toChar().toString()
        var expected = byteArrayOf(0xEF.toByte())
        for (x in 97..126) expected += byteArrayOf(0x41, x.toByte())
        expected += 0x03
        assertArrayEquals(expected, packed(dict))
    }

    @Test
    fun `pack pointers`() {
        assertArrayEquals(hex("d24161a0"), packed(listOf("a", "a")))
        assertArrayEquals(
            hex("d443666f6f43626172a0a1"),
            packed(listOf("foo", "bar", "foo", "bar")),
        )
        assertArrayEquals(
            hex("e3416141624163e14164a0a301"),
            packed(
                linkedMapOf<Any?, Any?>(
                    "a" to "b",
                    "c" to linkedMapOf("d" to "a"),
                    "d" to true,
                ),
            ),
        )
    }

    @Test
    fun `pack more pointers`() {
        // list(chr(x).encode() for x in range(257)) twice; vector generated
        // with pyatv's own opack.pack (matches pyatv test_pack_more_ptr).
        val data = (0..256).map { String(Character.toChars(it)).toByteArray(Charsets.UTF_8) }
        assertArrayEquals(hex(MORE_PTR_VECTOR), packed(data + data))
    }

    // unpack

    @Test
    fun `unpack unsupported type`() {
        assertThrows(OpackException::class.java) { Opack.unpack(hex("00")) }
    }

    @Test
    fun `unpack boolean`() {
        assertEquals(true, unpacked(hex("01")))
        assertEquals(false, unpacked(hex("02")))
    }

    @Test
    fun `unpack none`() {
        assertEquals(null, unpacked(hex("04")))
    }

    @Test
    fun `unpack uuid`() {
        assertEquals(
            UUID.fromString("12345678-1234-5678-1234-567812345678"),
            unpacked(hex("05") + hex("12345678123456781234567812345678")),
        )
    }

    @Test
    fun `unpack absolute time as integer`() {
        assertEquals(1L, unpacked(hex("060100000000000000")))
    }

    @Test
    fun `unpack small integers`() {
        assertEquals(0L, unpacked(hex("08")))
        assertEquals(0xFL, unpacked(hex("17")))
        assertEquals(0x27L, unpacked(hex("2f")))
    }

    @Test
    fun `unpack larger integers`() {
        assertEquals(0x28L, unpacked(hex("3028")))
        assertEquals(0x1FFL, unpacked(hex("31ff01")))
        assertEquals(0x1FFFFFFL, unpacked(hex("32ffffff01")))
        assertEquals(0x1FFFFFFFFFFFFFFL, unpacked(hex("33ffffffffffffff01")))
    }

    @Test
    fun `unpack sized integers`() {
        // pyatv preserves the encoded width via _sized_int; we return plain longs
        assertEquals(1L, unpacked(hex("3001")))
        assertEquals(1L, unpacked(hex("310100")))
        assertEquals(1L, unpacked(hex("3201000000")))
        assertEquals(1L, unpacked(hex("330100000000000000")))
    }

    @Test
    fun `unpack float32`() {
        assertEquals(1.0f, unpacked(hex("350000803f")))
    }

    @Test
    fun `unpack float64`() {
        assertEquals(1.0, unpacked(hex("36000000000000f03f")))
    }

    @Test
    fun `unpack short strings`() {
        assertEquals("a", unpacked(hex("4161")))
        assertEquals("abc", unpacked(hex("43616263")))
        assertEquals("a".repeat(0x20), unpacked(hex("60") + ByteArray(0x20) { 0x61 }))
    }

    @Test
    fun `unpack longer strings`() {
        assertEquals("a".repeat(33), unpacked(hex("6121") + ByteArray(33) { 0x61 }))
        assertEquals("a".repeat(256), unpacked(hex("620001") + ByteArray(256) { 0x61 }))
    }

    @Test
    fun `unpack short raw bytes`() {
        assertArrayEquals(hex("ac"), unpacked(hex("71ac")) as ByteArray)
        assertArrayEquals(hex("123456"), unpacked(hex("73123456")) as ByteArray)
        assertArrayEquals(
            ByteArray(0x20) { 0xAD.toByte() },
            unpacked(hex("90") + ByteArray(0x20) { 0xAD.toByte() }) as ByteArray,
        )
    }

    @Test
    fun `unpack longer raw bytes`() {
        assertArrayEquals(ByteArray(33) { 0x61 }, unpacked(hex("9121") + ByteArray(33) { 0x61 }) as ByteArray)
        assertArrayEquals(ByteArray(256) { 0x61 }, unpacked(hex("920001") + ByteArray(256) { 0x61 }) as ByteArray)
        assertArrayEquals(
            ByteArray(65536) { 0x61 },
            unpacked(hex("9300000100") + ByteArray(65536) { 0x61 }) as ByteArray,
        )
    }

    @Test
    fun `unpack array`() {
        assertDeepEquals(emptyList<Any?>(), unpacked(hex("d0")))
        assertDeepEquals(listOf(1L, "test", false), unpacked(hex("d309447465737402")))
        assertDeepEquals(listOf(listOf(true)), unpacked(hex("d1d101")))
    }

    @Test
    fun `unpack endless array`() {
        val list1 = hex("df4161") + ByteArray(15) { 0xA0.toByte() } + hex("03")
        val list2 = hex("df4162") + ByteArray(15) { 0xA1.toByte() } + hex("03")
        assertDeepEquals(List(16) { "a" }, unpacked(list1))
        assertDeepEquals(
            listOf(List(16) { "a" }, List(16) { "b" }),
            unpacked(hex("d2") + list1 + list2),
        )
    }

    @Test
    fun `unpack dict`() {
        assertDeepEquals(emptyMap<Any?, Any?>(), unpacked(hex("e0")))
        assertDeepEquals(
            linkedMapOf<Any?, Any?>("a" to 12L, false to null),
            unpacked(hex("e24161140204")),
        )
        assertDeepEquals(
            mapOf(true to mapOf("a" to 2L)),
            unpacked(hex("e101e141610a")),
        )
    }

    @Test
    fun `unpack endless dict`() {
        var encoded = byteArrayOf(0xEF.toByte())
        for (x in 97..126) encoded += byteArrayOf(0x41, x.toByte())
        encoded += 0x03
        val expected = LinkedHashMap<Any?, Any?>()
        for (x in 97..126 step 2) expected[x.toChar().toString()] = (x + 1).toChar().toString()
        assertDeepEquals(expected, unpacked(encoded))
    }

    @Test
    fun `unpack pointers`() {
        assertDeepEquals(listOf("a", "a"), unpacked(hex("d24161a0")))
        assertDeepEquals(
            listOf("foo", "bar", "foo", "bar"),
            unpacked(hex("d443666f6f43626172a0a1")),
        )
        assertDeepEquals(
            linkedMapOf<Any?, Any?>("a" to "b", "c" to mapOf("d" to "a"), "d" to true),
            unpacked(hex("e3416141624163e14164a0a301")),
        )
    }

    @Test
    fun `unpack more pointers`() {
        val data = (0..256).map { String(Character.toChars(it)).toByteArray(Charsets.UTF_8) }
        assertDeepEquals(data + data, unpacked(hex(MORE_PTR_VECTOR)))
    }

    @Test
    fun `unpack uid references`() {
        assertDeepEquals(listOf(1L, 2L, 2L), unpacked(hex("df30013002c10103")))
        assertDeepEquals(listOf(1L, 2L, 2L), unpacked(hex("df30013002c2010003")))
        assertDeepEquals(listOf(1L, 2L, 2L), unpacked(hex("df30013002c301000003")))
        assertDeepEquals(listOf(1L, 2L, 2L), unpacked(hex("df30013002c40100000003")))
    }

    @Test
    fun `docs example from pyatv protocol documentation`() {
        // https://pyatv.dev/documentation/protocols/ (Companion Link, OPACK)
        assertDeepEquals(
            linkedMapOf<Any?, Any?>("a" to false, "b" to "test", "c" to "test"),
            unpacked(hex("e3416102416244746573744163a2")),
        )
    }

    @Test
    fun `golden systemInfo-like message round trips`() {
        val data = linkedMapOf<Any?, Any?>(
            "_i" to "_systemInfo",
            "_x" to 1254122577L,
            "_btHP" to false,
            "_c" to linkedMapOf<Any?, Any?>(
                "_pubID" to "AA:BB:CC:DD:EE:FF",
                "_sv" to "230.1",
                "_bf" to 0L,
                "_siriInfo" to linkedMapOf<Any?, Any?>(
                    "collectorElectionVersion" to 1.0,
                    "deviceCapabilities" to linkedMapOf<Any?, Any?>(
                        "seymourEnabled" to 1L,
                        "voiceTriggerEnabled" to 2L,
                    ),
                    "sharedDataProtoBuf" to ByteArray(512) { 0x08 },
                ),
                "_stA" to listOf(
                    "com.apple.LiveAudio",
                    "com.apple.siri.wakeup",
                    "com.apple.Seymour",
                    "com.apple.announce",
                    "com.apple.coreduet.sync",
                    "com.apple.SeymourSession",
                ),
                "_i" to "6c62fca18b11",
                "_clFl" to 128L,
                "_idsID" to "44E14ABC-DDDD-4188-B661-11BAAAF6ECDE",
                "_hkUID" to listOf(UUID.fromString("17ed160a-81f8-4488-962c-6b1a83eb0081")),
                "_dC" to "1",
                "_sf" to 256L,
                "model" to "iPhone10,6",
                "name" to "iPhone",
            ),
            "_t" to 2L,
        )
        assertDeepEquals(data, unpacked(packed(data)))
    }

    @Test
    fun `round trip nested structures`() {
        val samples = listOf<Any?>(
            null,
            true,
            0L,
            255L,
            65535L,
            0xFFFFFFFFL,
            Long.MAX_VALUE,
            3.14,
            "",
            "hello",
            "日本語テキスト🙂",
            ByteArray(300) { it.toByte() },
            listOf("a", "a", "b", listOf(1L, 2L), mapOf("k" to "v")),
            linkedMapOf<Any?, Any?>("x" to listOf(1L, 1L), "y" to "x"),
        )
        for (sample in samples) {
            assertDeepEquals(sample, unpacked(packed(sample)))
        }
    }

    companion object {
        private const val MORE_PTR_VECTOR =
            "df7100710171027103710471057106710771087109710a710b710c710d710e710f" +
                "7110711171127113711471157116711771187119711a711b711c711d711e711f" +
                "7120712171227123712471257126712771287129712a712b712c712d712e712f" +
                "7130713171327133713471357136713771387139713a713b713c713d713e713f" +
                "7140714171427143714471457146714771487149714a714b714c714d714e714f" +
                "7150715171527153715471557156715771587159715a715b715c715d715e715f" +
                "7160716171627163716471657166716771687169716a716b716c716d716e716f" +
                "7170717171727173717471757176717771787179717a717b717c717d717e717f" +
                "72c28072c28172c28272c28372c28472c28572c28672c28772c28872c28972c2" +
                "8a72c28b72c28c72c28d72c28e72c28f72c29072c29172c29272c29372c29472" +
                "c29572c29672c29772c29872c29972c29a72c29b72c29c72c29d72c29e72c29f" +
                "72c2a072c2a172c2a272c2a372c2a472c2a572c2a672c2a772c2a872c2a972c2" +
                "aa72c2ab72c2ac72c2ad72c2ae72c2af72c2b072c2b172c2b272c2b372c2b472" +
                "c2b572c2b672c2b772c2b872c2b972c2ba72c2bb72c2bc72c2bd72c2be72c2bf" +
                "72c38072c38172c38272c38372c38472c38572c38672c38772c38872c38972c3" +
                "8a72c38b72c38c72c38d72c38e72c38f72c39072c39172c39272c39372c39472" +
                "c39572c39672c39772c39872c39972c39a72c39b72c39c72c39d72c39e72c39f" +
                "72c3a072c3a172c3a272c3a372c3a472c3a572c3a672c3a772c3a872c3a972c3" +
                "aa72c3ab72c3ac72c3ad72c3ae72c3af72c3b072c3b172c3b272c3b372c3b472" +
                "c3b572c3b672c3b772c3b872c3b972c3ba72c3bb72c3bc72c3bd72c3be72c3bf" +
                "72c480a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbc" +
                "bdbebfc0c121c122c123c124c125c126c127c128c129c12ac12bc12cc12dc12e" +
                "c12fc130c131c132c133c134c135c136c137c138c139c13ac13bc13cc13dc13e" +
                "c13fc140c141c142c143c144c145c146c147c148c149c14ac14bc14cc14dc14e" +
                "c14fc150c151c152c153c154c155c156c157c158c159c15ac15bc15cc15dc15e" +
                "c15fc160c161c162c163c164c165c166c167c168c169c16ac16bc16cc16dc16e" +
                "c16fc170c171c172c173c174c175c176c177c178c179c17ac17bc17cc17dc17e" +
                "c17fc180c181c182c183c184c185c186c187c188c189c18ac18bc18cc18dc18e" +
                "c18fc190c191c192c193c194c195c196c197c198c199c19ac19bc19cc19dc19e" +
                "c19fc1a0c1a1c1a2c1a3c1a4c1a5c1a6c1a7c1a8c1a9c1aac1abc1acc1adc1ae" +
                "c1afc1b0c1b1c1b2c1b3c1b4c1b5c1b6c1b7c1b8c1b9c1bac1bbc1bcc1bdc1be" +
                "c1bfc1c0c1c1c1c2c1c3c1c4c1c5c1c6c1c7c1c8c1c9c1cac1cbc1ccc1cdc1ce" +
                "c1cfc1d0c1d1c1d2c1d3c1d4c1d5c1d6c1d7c1d8c1d9c1dac1dbc1dcc1ddc1de" +
                "c1dfc1e0c1e1c1e2c1e3c1e4c1e5c1e6c1e7c1e8c1e9c1eac1ebc1ecc1edc1ee" +
                "c1efc1f0c1f1c1f2c1f3c1f4c1f5c1f6c1f7c1f8c1f9c1fac1fbc1fcc1fdc1fe" +
                "c1ffc2000103"
    }
}
