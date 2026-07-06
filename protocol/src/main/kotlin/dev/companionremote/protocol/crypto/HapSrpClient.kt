package dev.companionremote.protocol.crypto

import java.math.BigInteger

/**
 * SRP-6a client, HAP flavor, replicating exactly what pyatv uses:
 * srptools 1.0.1 (`srptools/context.py`, `client.py`, `common.py`) with the
 * RFC 5054 3072-bit group, SHA-512, username "Pair-Setup".
 *
 * srptools specifics that differ from a strict RFC reading and are kept
 * deliberately (see docs/protocol-notes.md):
 *  - K = H(S) is the session key handed to HKDF (not raw S);
 *  - integer terms in the proofs are minimal-length big-endian (leading
 *    zeros stripped), including A, B and the H(N)^H(g) term;
 *  - x = H(salt | H(I ":" P)) includes the username.
 */
class HapSrpClient(
    private val username: String,
    pin: String,
    privateKey: ByteArray = Crypto.randomBytes(32),
) {
    private val password = pin

    private val a = BigInteger(1, privateKey)

    /** Client public key A = g^a mod N. */
    val publicKey: ByteArray get() = minimalBytes(bigA)

    private val bigA: BigInteger = G.modPow(a, N)

    private var bigB: BigInteger? = null
    private var saltBytes: ByteArray? = null
    private var sessionKeyInternal: ByteArray? = null
    private var clientProofInternal: ByteArray? = null

    /** K = H(S): the 64-byte session key (input to all pair-setup HKDFs). */
    val sessionKey: ByteArray
        get() = sessionKeyInternal ?: error("call processServerParams first")

    /** Client proof M1. */
    val clientProof: ByteArray
        get() = clientProofInternal ?: error("call processServerParams first")

    /**
     * Process the server public key (B) and salt from message M2, computing
     * the shared session key and the client proof.
     */
    fun processServerParams(serverPublicKey: ByteArray, salt: ByteArray) {
        val b = BigInteger(1, serverPublicKey)
        if (b.mod(N) == BigInteger.ZERO) throw IllegalArgumentException("invalid server public key")
        bigB = b
        saltBytes = salt

        // x = H(s | H(I ":" P))
        val x = BigInteger(1, Crypto.sha512(salt, Crypto.sha512("$username:$password".toByteArray())))
        // u = H(PAD(A) | PAD(B))
        val u = BigInteger(1, Crypto.sha512(pad(bigA), pad(b)))
        // k = H(N | PAD(g))
        val k = BigInteger(1, Crypto.sha512(minimalBytes(N), pad(G)))
        // S = (B - k*g^x) ^ (a + u*x) mod N
        val v = G.modPow(x, N)
        val base = b.subtract(k.multiply(v)).mod(N)
        val s = base.modPow(a.add(u.multiply(x)), N)
        // K = H(S)
        val sessionKey = Crypto.sha512(minimalBytes(s))
        sessionKeyInternal = sessionKey

        // M1 = H( (H(N) xor H(g)) | H(I) | s | A | B | K ) with minimal-length ints
        val hN = BigInteger(1, Crypto.sha512(minimalBytes(N)))
        val hG = BigInteger(1, Crypto.sha512(minimalBytes(G)))
        val hI = BigInteger(1, Crypto.sha512(username.toByteArray()))
        clientProofInternal = Crypto.sha512(
            minimalBytes(hN.xor(hG)),
            minimalBytes(hI),
            salt,
            minimalBytes(bigA),
            minimalBytes(b),
            sessionKey,
        )
    }

    /** Verify the server proof M2 = H(A | M1 | K). */
    fun verifyServerProof(serverProof: ByteArray): Boolean {
        val expected = Crypto.sha512(minimalBytes(bigA), clientProof, sessionKey)
        return expected.contentEquals(serverProof)
    }

    private fun pad(value: BigInteger): ByteArray {
        val bytes = minimalBytes(value)
        return if (bytes.size >= GROUP_LENGTH) bytes else ByteArray(GROUP_LENGTH - bytes.size) + bytes
    }

    companion object {
        /** RFC 5054 3072-bit group prime (srptools constants.PRIME_3072). */
        internal val N = BigInteger(
            (
                "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
                    "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
                    "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                    "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
                    "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
                    "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                    "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
                    "3995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33" +
                    "A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                    "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864" +
                    "D87602733EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E2" +
                    "08E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF"
                ),
            16,
        )

        /** Generator g = 5 (srptools constants.PRIME_3072_GEN). */
        internal val G = BigInteger.valueOf(5)

        private const val GROUP_LENGTH = 384

        /**
         * Minimal-length big-endian bytes (no leading zeros), matching
         * srptools' int_to_bytes used inside its hash().
         */
        internal fun minimalBytes(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            var start = 0
            while (start < bytes.size - 1 && bytes[start] == 0.toByte()) start++
            return if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
        }
    }
}
