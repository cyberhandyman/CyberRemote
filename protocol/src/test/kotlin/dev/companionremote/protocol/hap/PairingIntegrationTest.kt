package dev.companionremote.protocol.hap

import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.companion.FrameType
import dev.companionremote.protocol.crypto.CompanionSessionCipher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Full pair-setup + pair-verify + encrypted-session exchange against the
 * in-process [FakeAtv] (SRP server with srptools semantics, real crypto on
 * both sides). This proves internal consistency of the handshake; the real
 * Apple TV check happens via the cli (see BUILD_REPORT checklist).
 */
class PairingIntegrationTest {

    @Test
    fun `pair setup with correct pin yields working credentials`() = runBlocking {
        val atv = FakeAtv(pin = "3921")
        val connection = CompanionConnection(FakeAtvTransport(atv))
        connection.start()

        val pairSetup = PairSetup(connection, name = "Test Remote")
        withTimeout(10_000) {
            pairSetup.startPairing()
            val credentials = pairSetup.finishPairing("3921")

            assertArrayEquals(atv.atvLtpk, credentials.ltpk)
            assertArrayEquals(atv.atvId, credentials.atvId)
            assertArrayEquals(credentials.clientId, atv.pairedClientId)
            assertArrayEquals(atv.pairedClientLtpk, dev.companionremote.protocol.crypto.Crypto.ed25519PublicKey(credentials.ltsk))

            // Round trip through the pyatv-compatible string format
            val parsed = HapCredentials.parse(credentials.toString())
            assertEquals(credentials, parsed)
        }
        connection.close()
    }

    @Test
    fun `pair setup with wrong pin fails with pairing error`() = runBlocking {
        val atv = FakeAtv(pin = "3921")
        val connection = CompanionConnection(FakeAtvTransport(atv))
        connection.start()

        val pairSetup = PairSetup(connection)
        withTimeout(10_000) {
            pairSetup.startPairing()
            val exception = runCatching { pairSetup.finishPairing("0000") }.exceptionOrNull()
            assertTrue(exception is PairingException, "got $exception")
        }
        connection.close()
    }

    @Test
    fun `pair verify derives working session keys`() = runBlocking {
        val atv = FakeAtv(pin = "1122")
        val setupConnection = CompanionConnection(FakeAtvTransport(atv))
        setupConnection.start()
        val pairSetup = PairSetup(setupConnection)
        val credentials = withTimeout(10_000) {
            pairSetup.startPairing()
            pairSetup.finishPairing("1122")
        }
        setupConnection.close()

        // New connection: verify + encrypted echo request
        val connection = CompanionConnection(FakeAtvTransport(atv))
        connection.start()
        atv.requestHandler = { message ->
            if (message["_i"] == "_systemInfo") mapOf("_c" to mapOf("echo" to true)) else null
        }

        withTimeout(10_000) {
            val keys = PairVerify(connection, credentials).verify()
            connection.enableEncryption(CompanionSessionCipher(keys.outputKey, keys.inputKey))

            val response = connection.exchangeOpack(
                FrameType.E_OPACK,
                mapOf("_i" to "_systemInfo", "_t" to 2L, "_c" to emptyMap<String, Any?>()),
            )
            @Suppress("UNCHECKED_CAST")
            assertEquals(true, (response["_c"] as Map<Any?, Any?>)["echo"])
        }
        connection.close()
    }

    @Test
    fun `pair verify with wrong credentials fails`() = runBlocking {
        val atv = FakeAtv(pin = "1122")
        val setupConnection = CompanionConnection(FakeAtvTransport(atv))
        setupConnection.start()
        val pairSetup = PairSetup(setupConnection)
        val credentials = withTimeout(10_000) {
            pairSetup.startPairing()
            pairSetup.finishPairing("1122")
        }
        setupConnection.close()

        // Tamper with the ATV's long-term public key → signature check fails
        val badCredentials = credentials.copy(ltpk = ByteArray(32) { 7 })
        val connection = CompanionConnection(FakeAtvTransport(atv))
        connection.start()
        withTimeout(10_000) {
            val exception = runCatching { PairVerify(connection, badCredentials).verify() }.exceptionOrNull()
            assertTrue(exception is VerificationException, "got $exception")
        }
        connection.close()
    }

    @Test
    fun `credentials parse rejects malformed strings`() {
        assertThrows(IllegalArgumentException::class.java) { HapCredentials.parse("abc") }
        assertNotNull(HapCredentials.parse("aa:bb:cc:dd"))
    }
}
