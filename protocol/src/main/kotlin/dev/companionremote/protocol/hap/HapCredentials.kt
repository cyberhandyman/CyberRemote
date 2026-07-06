package dev.companionremote.protocol.hap

/**
 * HAP credentials produced by pair-setup and consumed by pair-verify.
 * String format identical to pyatv (`auth/hap_pairing.py HapCredentials`):
 * `ltpk_hex:ltsk_hex:atv_id_hex:client_id_hex`, so credentials are
 * interchangeable with `atvremote --companion-credentials`.
 *
 * @property ltpk the Apple TV's long-term Ed25519 public key
 * @property ltsk our long-term Ed25519 private key seed (32 bytes)
 * @property atvId the Apple TV's pairing identifier
 * @property clientId our pairing identifier (an ASCII UUID string)
 */
data class HapCredentials(
    val ltpk: ByteArray,
    val ltsk: ByteArray,
    val atvId: ByteArray,
    val clientId: ByteArray,
) {
    override fun toString(): String = listOf(ltpk, ltsk, atvId, clientId)
        .joinToString(":") { it.toHex() }

    override fun equals(other: Any?): Boolean = other is HapCredentials &&
        ltpk.contentEquals(other.ltpk) && ltsk.contentEquals(other.ltsk) &&
        atvId.contentEquals(other.atvId) && clientId.contentEquals(other.clientId)

    override fun hashCode(): Int = toString().hashCode()

    companion object {
        fun parse(value: String): HapCredentials {
            val parts = value.trim().split(":")
            require(parts.size == 4) { "invalid credentials (expected 4 hex fields)" }
            return HapCredentials(
                parts[0].hexToBytes(),
                parts[1].hexToBytes(),
                parts[2].hexToBytes(),
                parts[3].hexToBytes(),
            )
        }
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd-length hex string" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
