package dev.companionremote.protocol.crypto

import dev.companionremote.protocol.hap.hexToBytes
import dev.companionremote.protocol.hap.toHex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cross-implementation vectors generated with Python `cryptography`
 * (same library pyatv uses).
 */
class CryptoPrimitivesTest {

    @Test
    fun `hkdf sha512 with pair-setup salts`() {
        // IKM = K from the SRP golden vector (see HapSrpClientTest)
        val ikm = (
            "a79f22ee61cb71c92a406af29cc5255c13105c497f5caad22aa435f50de74eca" +
                "5986474f8ece4493b0ced7ccc6c3efb0ec28666ed05c0bdf665489c46c2237ca"
            ).hexToBytes()
        assertEquals(
            "7a30155f6c136852a7180e3dd53bb0058433c771943a2ebc61043a06773fb725",
            Crypto.hkdfSha512("Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info", ikm).toHex(),
        )
        assertEquals(
            "070d072670aaa1e598c488da48e0fa54946e642997133a01144902903d2f15fa",
            Crypto.hkdfSha512("Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info", ikm).toHex(),
        )
        // Session keys use an empty salt
        assertEquals(
            "3de8ed247561b5750ff5eee3ec378f7899326f91989636c6ca7146e4b70ad8ad",
            Crypto.hkdfSha512("", "ClientEncrypt-main", ikm).toHex(),
        )
    }

    @Test
    fun `chacha20poly1305 with hap textual nonce`() {
        val key = ByteArray(32) { it.toByte() }
        val ciphertext = Crypto.chaChaEncrypt(key, Crypto.padNonce("PS-Msg05"), "hello companion".toByteArray())
        assertEquals(
            "5c9fee01068c4e49d95fbd421b269e785c8235a80f0307695fb8445e3d5cd6",
            ciphertext.toHex(),
        )
        assertArrayEquals(
            "hello companion".toByteArray(),
            Crypto.chaChaDecrypt(key, Crypto.padNonce("PS-Msg05"), ciphertext),
        )
    }

    @Test
    fun `chacha20poly1305 with counter nonce and frame header aad`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12).also { it[0] = 1 } // counter=1, little-endian
        val aad = "08000013".hexToBytes()
        val ciphertext = Crypto.chaChaEncrypt(key, nonce, "frame payload".toByteArray(), aad)
        assertEquals(
            "f24d1a83a1c3ec4b0e37bc9209f7cdaaaaa127aef29def8ee1f7674b2d",
            ciphertext.toHex(),
        )
    }

    @Test
    fun `chacha20poly1305 rejects tampered data`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12)
        val ciphertext = Crypto.chaChaEncrypt(key, nonce, "payload".toByteArray())
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { Crypto.chaChaDecrypt(key, nonce, ciphertext) }
    }

    @Test
    fun `ed25519 keygen sign verify`() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val publicKey = Crypto.ed25519PublicKey(seed)
        assertEquals("79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664", publicKey.toHex())
        val message = "pair-verify-test-message".toByteArray()
        val signature = Crypto.ed25519Sign(seed, message)
        assertEquals(
            "b637458a7e1bdd51f52ad2b7be309da8cd97b5a89984beaa4197bc7eec8c95e2" +
                "81f5637ad37c7f298033452a18f5079f65de52961a1da3226614a0beec8f9602",
            signature.toHex(),
        )
        assertTrue(Crypto.ed25519Verify(publicKey, message, signature))
        assertFalse(Crypto.ed25519Verify(publicKey, message + 1, signature))
    }

    @Test
    fun `x25519 key agreement`() {
        val privateA = ByteArray(32) { it.toByte() }
        val privateB = ByteArray(32) { (it + 100).toByte() }
        val publicA = Crypto.x25519PublicKey(privateA)
        val publicB = Crypto.x25519PublicKey(privateB)
        assertEquals("8f40c5adb68f25624ae5b214ea767a6ec94d829d3d7b5e1ad1ba6f3e2138285f", publicA.toHex())
        assertEquals("7d9c24316539825c1896e57f28197746793ce60cbee3ad47da9d07b85fa55e2a", publicB.toHex())
        val shared = Crypto.x25519SharedSecret(privateA, publicB)
        assertEquals("3ae143fb898911b4591769dc17ca01775cc4cad4d05b8f33a39c79e30fa63a72", shared.toHex())
        assertArrayEquals(shared, Crypto.x25519SharedSecret(privateB, publicA))
    }

    @Test
    fun `session cipher counters advance independently per direction`() {
        val outKey = ByteArray(32) { 1 }
        val inKey = ByteArray(32) { 2 }
        val client = CompanionSessionCipher(outKey, inKey)
        // Mirror of the device side: its output is our input
        val server = CompanionSessionCipher(inKey, outKey)

        val header = "08000013".hexToBytes()
        val c1 = client.encrypt("one".toByteArray(), header)
        val c2 = client.encrypt("two".toByteArray(), header)
        assertFalse(c1.contentEquals(c2))
        assertArrayEquals("one".toByteArray(), server.decrypt(c1, header))
        assertArrayEquals("two".toByteArray(), server.decrypt(c2, header))

        val s1 = server.encrypt("three".toByteArray(), header)
        assertArrayEquals("three".toByteArray(), client.decrypt(s1, header))
    }

    @Test
    fun `session cipher desync fails decryption`() {
        val outKey = ByteArray(32) { 1 }
        val inKey = ByteArray(32) { 2 }
        val client = CompanionSessionCipher(outKey, inKey)
        val server = CompanionSessionCipher(inKey, outKey)
        val header = "08000013".hexToBytes()
        client.encrypt("lost frame".toByteArray(), header) // never delivered
        val c2 = client.encrypt("arrives".toByteArray(), header)
        // Server still expects counter 0 → AEAD failure
        assertThrows(Exception::class.java) { server.decrypt(c2, header) }
    }
}
