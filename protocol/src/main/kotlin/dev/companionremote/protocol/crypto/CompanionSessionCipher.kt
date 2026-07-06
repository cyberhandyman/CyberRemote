package dev.companionremote.protocol.crypto

import dev.companionremote.protocol.companion.SessionCipher

/**
 * Companion session encryption: ChaCha20-Poly1305 with per-direction message
 * counters as nonces (12-byte little-endian, starting at 0), AAD = the
 * 4-byte frame header. Ported from pyatv `support/chacha20.py`
 * (`Chacha20Cipher` with `nonce_length=12`).
 */
class CompanionSessionCipher(
    private val outputKey: ByteArray,
    private val inputKey: ByteArray,
) : SessionCipher {

    private var outCounter = 0L
    private var inCounter = 0L

    override fun encrypt(data: ByteArray, aad: ByteArray): ByteArray {
        val nonce = counterNonce(outCounter)
        outCounter += 1
        return Crypto.chaChaEncrypt(outputKey, nonce, data, aad)
    }

    override fun decrypt(data: ByteArray, aad: ByteArray): ByteArray {
        val nonce = counterNonce(inCounter)
        inCounter += 1
        return Crypto.chaChaDecrypt(inputKey, nonce, data, aad)
    }

    private fun counterNonce(counter: Long): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) nonce[i] = ((counter ushr (8 * i)) and 0xFF).toByte()
        return nonce
    }
}
