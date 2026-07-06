package dev.companionremote.protocol.client

import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.hap.FakeAtv
import dev.companionremote.protocol.hap.FakeAtvTransport
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairSetup
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompanionClientTest {

    private fun runWithClient(
        handler: (Map<Any?, Any?>) -> Map<String, Any?>?,
        block: suspend (CompanionClient, FakeAtv) -> Unit,
    ) = runBlocking {
        val atv = FakeAtv(pin = "9876")
        // Pair first to obtain credentials
        val setupConnection = CompanionConnection(FakeAtvTransport(atv))
        setupConnection.start()
        val pairSetup = PairSetup(setupConnection)
        val credentials: HapCredentials = withTimeout(10_000) {
            pairSetup.startPairing()
            pairSetup.finishPairing("9876")
        }
        setupConnection.close()

        atv.requestHandler = requestHandler@{ message ->
            when (message["_i"]) {
                "_sessionStart" -> mapOf("_c" to mapOf("_sid" to 0xCAFEL))
                "TVRCSessionStart" -> mapOf("_em" to "No request handler") // old tvOS
                else -> handler(message)
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
    fun `connect runs the pyatv sequence in order`() = runWithClient({ null }) { client, atv ->
        val requests = atv.requestLog.map { it["_i"] }
        assertEquals(
            listOf("_systemInfo", "_touchStart", "_sessionStart", "TVRCSessionStart", "_tiStart"),
            requests,
        )
        // TVRCSessionStart error was ignored, session id combined
        val localSid = client.sessionId and 0xFFFFFFFFL
        assertEquals((0xCAFEL shl 32) or localSid, client.sessionId)

        // _iMC subscription went out as an _interest event
        val interest = atv.eventLog.first { it["_i"] == "_interest" }
        @Suppress("UNCHECKED_CAST")
        val content = interest["_c"] as Map<Any?, Any?>
        assertEquals(listOf("_iMC"), content["_regEvents"])

        // _systemInfo carried a non-null identifier and our client id
        @Suppress("UNCHECKED_CAST")
        val sysInfo = atv.requestLog.first { it["_i"] == "_systemInfo" }["_c"] as Map<Any?, Any?>
        assertTrue((sysInfo["_i"] as String).isNotEmpty())
    }

    @Test
    fun `button press sends down then up`() = runWithClient({ null }) { client, atv ->
        atv.requestLog.clear()
        client.pressButton(HidCommand.Select)
        @Suppress("UNCHECKED_CAST")
        val states = atv.requestLog
            .filter { it["_i"] == "_hidC" }
            .map { (it["_c"] as Map<Any?, Any?>) }
        assertEquals(listOf(1L, 2L), states.map { it["_hBtS"] })
        assertEquals(listOf(6L, 6L), states.map { it["_hidC"] })
    }

    @Test
    fun `wake is a single up event`() = runWithClient({ null }) { client, atv ->
        atv.requestLog.clear()
        client.wake()
        @Suppress("UNCHECKED_CAST")
        val commands = atv.requestLog
            .filter { it["_i"] == "_hidC" }
            .map { it["_c"] as Map<Any?, Any?> }
        assertEquals(1, commands.size)
        assertEquals(2L, commands[0]["_hBtS"])
        assertEquals(HidCommand.Wake.code, commands[0]["_hidC"])
    }

    @Test
    fun `app list parses bundle map and launch sends bundle id`() = runWithClient({ message ->
        when (message["_i"]) {
            "FetchLaunchableApplicationsEvent" -> mapOf(
                "_c" to mapOf(
                    "com.apple.TVSettings" to "Settings",
                    "com.google.ios.youtube" to "YouTube",
                ),
            )
            else -> null
        }
    }) { client, atv ->
        val apps = client.appList()
        assertEquals("Settings", apps["com.apple.TVSettings"])
        assertEquals("YouTube", apps["com.google.ios.youtube"])

        client.launchApp("com.google.ios.youtube")
        @Suppress("UNCHECKED_CAST")
        val launch = atv.requestLog.last { it["_i"] == "_launchApp" }["_c"] as Map<Any?, Any?>
        assertEquals("com.google.ios.youtube", launch["_bundleID"])
    }

    @Test
    fun `fetch attention state maps to system status`() = runWithClient({ message ->
        when (message["_i"]) {
            "FetchAttentionState" -> mapOf("_c" to mapOf("state" to 3L))
            else -> null
        }
    }) { client, _ ->
        assertEquals(SystemStatus.Awake, client.fetchAttentionState())
    }

    @Test
    fun `fetch attention state returns null when handler is missing`() = runWithClient({ message ->
        when (message["_i"]) {
            "FetchAttentionState" -> mapOf("_em" to "No request handler")
            else -> null
        }
    }) { client, _ ->
        assertNull(client.fetchAttentionState())
    }

    @Test
    fun `touch tap emits select press and click event`() = runWithClient({ null }) { client, atv ->
        atv.requestLog.clear()
        atv.eventLog.clear()
        client.tap()
        val hid = atv.requestLog.filter { it["_i"] == "_hidC" }
        assertEquals(2, hid.size)
        @Suppress("UNCHECKED_CAST")
        val touch = atv.eventLog.filter { it["_i"] == "_hidT" }.map { it["_c"] as Map<Any?, Any?> }
        assertEquals(1, touch.size)
        assertEquals(TouchPhase.Click.code, touch[0]["_tPh"])
        assertEquals(1000L, touch[0]["_cx"])
        assertTrue((touch[0]["_ns"] as Long) >= 0)
    }

    @Test
    fun `swipe emits press, holds and release`() = runWithClient({ null }) { client, atv ->
        atv.eventLog.clear()
        client.swipe(500, 800, 500, 200, durationMs = 100)
        @Suppress("UNCHECKED_CAST")
        val phases = atv.eventLog
            .filter { it["_i"] == "_hidT" }
            .map { (it["_c"] as Map<Any?, Any?>)["_tPh"] as Long }
        assertEquals(TouchPhase.Press.code, phases.first())
        assertEquals(TouchPhase.Release.code, phases.last())
        assertTrue(phases.count { it == TouchPhase.Hold.code } >= 2, "phases: $phases")
    }

    @Test
    fun `disconnect stops session touch and text input`() = runWithClient({ null }) { client, atv ->
        atv.requestLog.clear()
        atv.eventLog.clear()
        client.disconnect()
        val identifiers = atv.requestLog.map { it["_i"] }
        assertEquals(listOf("_sessionStop", "_touchStop", "_tiStop"), identifiers)
        // unsubscribe of _iMC went out first as an event
        @Suppress("UNCHECKED_CAST")
        val dereg = atv.eventLog.first { it["_i"] == "_interest" }["_c"] as Map<Any?, Any?>
        assertEquals(listOf("_iMC"), dereg["_deregEvents"])
    }
}
