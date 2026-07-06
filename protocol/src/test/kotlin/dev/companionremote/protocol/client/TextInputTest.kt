package dev.companionremote.protocol.client

import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.FakeAtv
import dev.companionremote.protocol.hap.FakeAtvTransport
import dev.companionremote.protocol.hap.PairSetup
import dev.companionremote.protocol.plist.BinaryPlist
import dev.companionremote.protocol.plist.KeyedArchiver
import dev.companionremote.protocol.plist.PlistUid
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * RTI text-input flow against the fake ATV: `_tiStart` responses carry a
 * keyed-archive document state; `_tiC` events carry the RTI payloads.
 */
class TextInputTest {

    private val sessionUuid = ByteArray(16) { (it * 3).toByte() }

    /** Build a `_tiD` archive like a real device's _tiStart response. */
    private fun tiStartArchive(currentText: String): ByteArray = BinaryPlist.encode(
        linkedMapOf(
            "\$version" to 100000L,
            "\$archiver" to "RTIKeyedArchiver",
            "\$top" to linkedMapOf(
                "sessionUUID" to PlistUid(1),
                "documentState" to PlistUid(2),
            ),
            "\$objects" to listOf(
                "\$null",
                sessionUuid,
                linkedMapOf("docSt" to PlistUid(3)),
                linkedMapOf("contextBeforeInput" to PlistUid(4)),
                currentText,
            ),
        ),
    )

    private fun runWithClient(
        focused: () -> Boolean,
        currentText: () -> String,
        block: suspend (CompanionClient, FakeAtv) -> Unit,
    ) = runBlocking {
        val atv = FakeAtv(pin = "4455")
        val setupConnection = CompanionConnection(FakeAtvTransport(atv))
        setupConnection.start()
        val pairSetup = PairSetup(setupConnection)
        val credentials = withTimeout(10_000) {
            pairSetup.startPairing()
            pairSetup.finishPairing("4455")
        }
        setupConnection.close()

        atv.requestHandler = { message ->
            when (message["_i"]) {
                "_sessionStart" -> mapOf("_c" to mapOf("_sid" to 1L))
                "_tiStart" ->
                    if (focused()) mapOf("_c" to mapOf("_tiD" to tiStartArchive(currentText())))
                    else mapOf("_c" to emptyMap<String, Any?>())
                else -> null
            }
        }

        val client = CompanionClient(CompanionConnection(FakeAtvTransport(atv)), credentials)
        withTimeout(10_000) {
            client.connect()
            block(client, atv)
        }
        client.close()
    }

    @Test
    fun `text get returns current text when focused`() =
        runWithClient({ true }, { "seed" }) { client, _ ->
            assertEquals("seed", client.textGet())
        }

    @Test
    fun `text commands no-op gracefully when nothing is focused`() =
        runWithClient({ false }, { "" }) { client, atv ->
            assertNull(client.textGet())
            assertNull(client.textSet("hello"))
            // No _tiC events must have been sent
            assertEquals(0, atv.eventLog.count { it["_i"] == "_tiC" })
        }

    @Test
    fun `text set clears then inserts`() =
        runWithClient({ true }, { "old" }) { client, atv ->
            atv.eventLog.clear()
            val result = client.textSet("new text")
            assertEquals("new text", result)

            @Suppress("UNCHECKED_CAST")
            val tic = atv.eventLog.filter { it["_i"] == "_tiC" }
                .map { it["_c"] as Map<Any?, Any?> }
            assertEquals(2, tic.size)
            assertEquals(1L, tic[0]["_tiV"])

            // First event: clear payload (textToAssert present, no insertionText)
            val clearArchive = BinaryPlist.decode(tic[0]["_tiD"] as ByteArray)
            @Suppress("UNCHECKED_CAST")
            val clearObjects = (clearArchive as Map<Any?, Any?>)["\$objects"] as List<Any?>
            @Suppress("UNCHECKED_CAST")
            val clearOps = clearObjects[1] as Map<Any?, Any?>
            assertEquals(PlistUid(4), clearOps["textToAssert"])

            // Second event: insertion payload carrying the text and our session UUID
            val inserted = KeyedArchiver.readArchiveProperties(
                tic[1]["_tiD"] as ByteArray,
                listOf("textOperations", "keyboardOutput", "insertionText"),
                listOf("textOperations", "targetSessionUUID", "NS.uuidbytes"),
            )
            assertEquals("new text", inserted[0])
            assertArrayEquals(sessionUuid, inserted[1] as ByteArray)
        }

    @Test
    fun `text append does not clear`() =
        runWithClient({ true }, { "abc" }) { client, atv ->
            atv.eventLog.clear()
            assertEquals("abcdef", client.textAppend("def"))
            val tic = atv.eventLog.filter { it["_i"] == "_tiC" }
            assertEquals(1, tic.size)
        }

    @Test
    fun `text clear sends only clear payload`() =
        runWithClient({ true }, { "abc" }) { client, atv ->
            atv.eventLog.clear()
            assertEquals("", client.textClear())
            val tic = atv.eventLog.filter { it["_i"] == "_tiC" }
            assertEquals(1, tic.size)
        }

    @Test
    fun `cjk and emoji text survives the payload round trip`() =
        runWithClient({ true }, { "" }) { client, atv ->
            atv.eventLog.clear()
            val text = "こんにちは안녕🙂"
            client.textSet(text)
            @Suppress("UNCHECKED_CAST")
            val insertEvent = atv.eventLog.filter { it["_i"] == "_tiC" }
                .map { it["_c"] as Map<Any?, Any?> }
                .last()
            val inserted = KeyedArchiver.readArchiveProperties(
                insertEvent["_tiD"] as ByteArray,
                listOf("textOperations", "keyboardOutput", "insertionText"),
            )
            assertEquals(text, inserted[0])
        }

    @Test
    fun `keyboard focus state follows tiStarted and tiStopped events`() =
        runWithClient({ false }, { "" }) { client, atv ->
            // After connect with an empty _tiStart response, focus is Unknown
            // (an empty _c must NOT be read as Unfocused — real devices send
            // empty responses even while focused; see protocol-notes).
            assertEquals(KeyboardFocusState.Unknown, client.keyboardFocus.value)

            atv.pushEvent("_tiStarted", mapOf("_tiD" to tiStartArchive("")))
            waitUntil { client.keyboardFocus.value == KeyboardFocusState.Focused }

            atv.pushEvent("_tiStopped", emptyMap())
            waitUntil { client.keyboardFocus.value == KeyboardFocusState.Unfocused }
        }

    private suspend fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            delay(20)
        }
    }
}
