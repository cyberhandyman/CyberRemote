package dev.companionremote.protocol.crypto

import java.security.SecureRandom
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Crypto primitives for HAP pairing and the Companion session, built on
 * BouncyCastle's lightweight API (no JCE provider registration, identical
 * behavior on JVM and Android).
 */
object Crypto {

    private val secureRandom = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { secureRandom.nextBytes(it) }

    fun sha512(vararg parts: ByteArray): ByteArray {
        val digest = SHA512Digest()
        for (part in parts) digest.update(part, 0, part.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }

    /**
     * HKDF-SHA512, ported from pyatv `hap_srp.py hkdf_expand` (salt and info
     * are UTF-8 strings there; empty salt is equivalent to a zero salt).
     */
    fun hkdfSha512(salt: ByteArray, info: ByteArray, inputKeyMaterial: ByteArray, length: Int = 32): ByteArray {
        val generator = HKDFBytesGenerator(SHA512Digest())
        generator.init(HKDFParameters(inputKeyMaterial, salt, info))
        val out = ByteArray(length)
        generator.generateBytes(out, 0, length)
        return out
    }

    fun hkdfSha512(salt: String, info: String, inputKeyMaterial: ByteArray, length: Int = 32): ByteArray =
        hkdfSha512(salt.toByteArray(), info.toByteArray(), inputKeyMaterial, length)

    /**
     * ChaCha20-Poly1305 AEAD encrypt; returns ciphertext with the 16-byte tag
     * appended. [nonce] must be 12 bytes (use [padNonce] for HAP's 8-byte
     * textual nonces).
     */
    fun chaChaEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, n)
        return out
    }

    /**
     * ChaCha20-Poly1305 AEAD decrypt of ciphertext+tag.
     * @throws org.bouncycastle.crypto.InvalidCipherTextException on MAC failure
     */
    fun chaChaDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val n = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        cipher.doFinal(out, n)
        return out
    }

    /** Left-pad a short (e.g. "PS-Msg05") nonce with zeros to 12 bytes. */
    fun padNonce(nonce: ByteArray): ByteArray =
        if (nonce.size >= 12) nonce else ByteArray(12 - nonce.size) + nonce

    fun padNonce(nonce: String): ByteArray = padNonce(nonce.toByteArray())

    // Ed25519 (long-term identity keys)

    fun ed25519PublicKey(seed: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    fun ed25519Sign(seed: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = try {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(message, 0, message.size)
        verifier.verifySignature(signature)
    } catch (e: Exception) {
        // BouncyCastle throws on malformed keys/points; treat as invalid
        false
    }

    // X25519 (pair-verify ephemeral keys)

    fun x25519PublicKey(privateKey: ByteArray): ByteArray =
        X25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded

    fun x25519SharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val private = X25519PrivateKeyParameters(privateKey, 0)
        val out = ByteArray(32)
        private.generateSecret(X25519PublicKeyParameters(peerPublicKey, 0), out, 0)
        return out
    }
}
