package dev.companionremote.protocol.companion

/**
 * Per-connection payload encryption. Installed on a [CompanionConnection]
 * after pair-verify succeeds (see M3, `crypto/CompanionSessionCipher`).
 */
interface SessionCipher {
    /** Encrypt [data] with [aad] (the 4-byte frame header); appends the auth tag. */
    fun encrypt(data: ByteArray, aad: ByteArray): ByteArray

    /** Decrypt [data] (auth tag included) with [aad]; throws on AEAD failure. */
    fun decrypt(data: ByteArray, aad: ByteArray): ByteArray
}
