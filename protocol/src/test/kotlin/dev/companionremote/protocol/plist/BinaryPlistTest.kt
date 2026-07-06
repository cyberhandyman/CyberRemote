package dev.companionremote.protocol.plist

import dev.companionremote.protocol.hap.hexToBytes
import dev.companionremote.protocol.hap.toHex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden vectors generated with CPython plistlib running pyatv's own
 * `rti_text_operations.py` (sessionUUID = 00010203…0f). The encoder must be
 * byte-identical with plistlib for these payloads.
 */
class BinaryPlistTest {

    private val sessionUuid = ByteArray(16) { it.toByte() }

    @Test
    fun `clear text payload matches pyatv plistlib output`() {
        assertEquals(CLEAR_VECTOR, RtiPayloads.clearTextPayload(sessionUuid).toHex())
    }

    @Test
    fun `input text payload matches pyatv plistlib output`() {
        assertEquals(INPUT_HELLO_VECTOR, RtiPayloads.inputTextPayload(sessionUuid, "hello").toHex())
    }

    @Test
    fun `input text payload with cjk and emoji matches plistlib utf16 output`() {
        assertEquals(INPUT_CJK_VECTOR, RtiPayloads.inputTextPayload(sessionUuid, "日本語🙂").toHex())
    }

    @Test
    fun `decode resolves keyed archive structure`() {
        @Suppress("UNCHECKED_CAST")
        val root = BinaryPlist.decode(CLEAR_VECTOR.hexToBytes()) as Map<Any?, Any?>
        assertEquals(100000L, root["\$version"])
        assertEquals("RTIKeyedArchiver", root["\$archiver"])
        @Suppress("UNCHECKED_CAST")
        val top = root["\$top"] as Map<Any?, Any?>
        assertEquals(PlistUid(1), top["textOperations"])
        @Suppress("UNCHECKED_CAST")
        val objects = root["\$objects"] as List<Any?>
        assertEquals("\$null", objects[0])
        @Suppress("UNCHECKED_CAST")
        val uuidHolder = objects[5] as Map<Any?, Any?>
        assertArrayEquals(sessionUuid, uuidHolder["NS.uuidbytes"] as ByteArray)
    }

    @Test
    fun `round trip of assorted values`() {
        val value = linkedMapOf<Any?, Any?>(
            "string" to "hello",
            "unicode" to "日本語🙂",
            "long" to 100000L,
            "double" to 1.5,
            "boolTrue" to true,
            "boolFalse" to false,
            "bytes" to ByteArray(20) { it.toByte() },
            "uid" to PlistUid(3),
            "list" to listOf("a", 1L, listOf("nested")),
            "dict" to linkedMapOf<Any?, Any?>("k" to "v"),
            "longString" to "x".repeat(300),
        )
        @Suppress("UNCHECKED_CAST")
        val decoded = BinaryPlist.decode(BinaryPlist.encode(value)) as Map<Any?, Any?>
        assertEquals("hello", decoded["string"])
        assertEquals("日本語🙂", decoded["unicode"])
        assertEquals(100000L, decoded["long"])
        assertEquals(1.5, decoded["double"])
        assertEquals(true, decoded["boolTrue"])
        assertEquals(false, decoded["boolFalse"])
        assertArrayEquals(ByteArray(20) { it.toByte() }, decoded["bytes"] as ByteArray)
        assertEquals(PlistUid(3), decoded["uid"])
        assertEquals(listOf("a", 1L, listOf("nested")), decoded["list"])
        assertEquals("x".repeat(300), decoded["longString"])
    }

    @Test
    fun `keyed archiver reads properties through uid references`() {
        val properties = KeyedArchiver.readArchiveProperties(
            TISTART_VECTOR.hexToBytes(),
            listOf("sessionUUID"),
            listOf("documentState", "docSt", "contextBeforeInput"),
        )
        assertArrayEquals(sessionUuid, properties[0] as ByteArray)
        assertEquals("current text", properties[1])
    }

    @Test
    fun `keyed archiver returns null for missing paths`() {
        val properties = KeyedArchiver.readArchiveProperties(
            TISTART_VECTOR.hexToBytes(),
            listOf("sessionUUID"),
            listOf("documentState", "nope", "missing"),
            listOf("unknownKey"),
        )
        assertTrue(properties[0] is ByteArray)
        assertEquals(null, properties[1])
        assertEquals(null, properties[2])
    }

    companion object {
        // plistlib output of pyatv get_rti_clear_text_payload(bytes(range(16)))
        private const val CLEAR_VECTOR =
            "62706c6973743030d4010203040506070a582476657273696f6e592461726368" +
                "697665725424746f7058246f626a6563747312000186a05f10105254494b6579" +
                "65644172636869766572d108095e746578744f7065726174696f6e738001a80b" +
                "0c15171d1e222555246e756c6cd40d0e0f10111213145624636c6173735f1011" +
                "74617267657453657373696f6e555549445e6b6579626f6172644f7574707574" +
                "5c74657874546f4173736572748007800580028004d10d168003d218191a1b5a" +
                "24636c6173736e616d655824636c61737365735f101054494b6579626f617264" +
                "4f7574707574a21a1c584e534f626a65637450d21f0d20215c4e532e75756964" +
                "62797465734f1010000102030405060708090a0b0c0d0e0f8006d21819232456" +
                "4e5355554944a2231cd2181926275f1011525449546578744f7065726174696f" +
                "6e73a2261c00080011001a0024002900320037004a004d005c005e0067006d00" +
                "76007d009100a000ad00af00b100b300b500b800ba00bf00ca00d300e600e900" +
                "f200f300f801050118011a011f01260129012e01420000000000000201000000" +
                "0000000028000000000000000000000000000001" + "45"

        // plistlib output of pyatv get_rti_input_text_payload(uuid, "hello")
        private const val INPUT_HELLO_VECTOR =
            "62706c6973743030d4010203040506070a582476657273696f6e592461726368" +
                "697665725424746f7058246f626a6563747312000186a05f10105254494b6579" +
                "65644172636869766572d108095e746578744f7065726174696f6e738001a80b" +
                "0c1317181e222555246e756c6cd30d0e0f1011125e6b6579626f6172644f7574" +
                "7075745624636c6173735f101174617267657453657373696f6e555549448002" +
                "80078005d2140e15165d696e73657274696f6e54657874800380045568656c6c" +
                "6fd2191a1b1c5a24636c6173736e616d655824636c61737365735f101054494b" +
                "6579626f6172644f7574707574a21b1d584e534f626a656374d21f0e20215c4e" +
                "532e7575696462797465734f1010000102030405060708090a0b0c0d0e0f8006" +
                "d2191a2324564e5355554944a2231dd2191a26275f1011525449546578744f70" +
                "65726174696f6e73a2261d00080011001a0024002900320037004a004d005c00" +
                "5e0067006d00740083008a009e00a000a200a400a900b700b900bb00c100c600" +
                "d100da00ed00f000f900fe010b011e01200125012c012f013401480000000000" +
                "00020100000000000000280000000000000000000000000000014b"

        // plistlib output of pyatv get_rti_input_text_payload(uuid, "日本語🙂")
        private const val INPUT_CJK_VECTOR =
            "62706c6973743030d4010203040506070a582476657273696f6e592461726368" +
                "697665725424746f7058246f626a6563747312000186a05f10105254494b6579" +
                "65644172636869766572d108095e746578744f7065726174696f6e738001a80b" +
                "0c1317181e222555246e756c6cd30d0e0f1011125e6b6579626f6172644f7574" +
                "7075745624636c6173735f101174617267657453657373696f6e555549448002" +
                "80078005d2140e15165d696e73657274696f6e546578748003800465" +
                "65e5672c8a9ed83dde42" +
                "d2191a1b1c5a24636c6173736e616d655824636c61737365735f101054494b65" +
                "79626f6172644f7574707574a21b1d584e534f626a656374d21f0e20215c4e53" +
                "2e7575696462797465734f1010000102030405060708090a0b0c0d0e0f8006d2" +
                "191a2324564e5355554944a2231dd2191a26275f1011525449546578744f7065" +
                "726174696f6e73a2261d00080011001a0024002900320037004a004d005c005e" +
                "0067006d00740083008a009e00a000a200a400a900b700b900bb00c600cb00d6" +
                "00df00f200f500fe0103011001230125012a013101340139014d000000000000" +
                "0201000000000000002800000000000000000000000000000150"

        // Simulated _tiStart response document state (see scratch generator)
        private const val TISTART_VECTOR =
            "62706c6973743030d4010203040506070c582476657273696f6e592461726368" +
                "697665725424746f7058246f626a6563747312000186a05f10105254494b6579" +
                "65644172636869766572d208090a0b5b73657373696f6e555549445d646f6375" +
                "6d656e74537461746580018002a50d0e0f121555246e756c6c4f101000010203" +
                "0405060708090a0b0c0d0e0fd1101155646f6353748003d113145f1012636f6e" +
                "746578744265666f7265496e70757480045c63757272656e7420746578740811" +
                "1a242932374a4f5b696b6d73798c8f95979aafb1000000000000010100000000" +
                "00000016000000000000000000000000000000be"
    }
}
