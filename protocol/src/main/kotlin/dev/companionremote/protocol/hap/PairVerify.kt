package dev.companionremote.protocol.hap

import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.companion.CompanionException
import dev.companionremote.protocol.companion.FrameType
import dev.companionremote.protocol.crypto.Crypto
import dev.companionremote.protocol.hap.PairSetup.Companion.PAIRING_DATA_KEY
import dev.companionremote.protocol.hap.PairSetup.Companion.pairingData
import dev.companionremote.protocol.tlv8.Tlv8
import dev.companionremote.protocol.tlv8.TlvValue

/** Pair-verify failed (bad credentials, signature error, ...). */
class VerificationException(message: String) : CompanionException(message)

/** Encryption keys derived by pair-verify. */
class SessionKeys(val outputKey: ByteArray, val inputKey: ByteArray)

/**
 * HAP pair-verify (M1–M4) over Companion frames; run on every connection.
 * Ported from pyatv `protocols/companion/auth.py`
 * (CompanionPairVerifyProcedure) and `auth/hap_srp.py`
 * (initialize/verify1/verify2).
 */
class PairVerify(
    private val connection: CompanionConnection,
    private val credentials: HapCredentials,
) {
    /**
     * Run the verification handshake and derive the session keys
     * (HKDF salt "" with ClientEncrypt-main / ServerEncrypt-main, IKM =
     * the raw X25519 shared secret).
     */
    suspend fun verify(): SessionKeys {
        val ephemeralPrivate = Crypto.randomBytes(32)
        val ephemeralPublic = Crypto.x25519PublicKey(ephemeralPrivate)

        // M1
        val m2 = connection.exchangeAuth(
            FrameType.PV_Start,
            mapOf(
                PAIRING_DATA_KEY to Tlv8.write(
                    linkedMapOf(
                        TlvValue.SEQ_NO to byteArrayOf(0x01),
                        TlvValue.PUBLIC_KEY to ephemeralPublic,
                    ),
                ),
                "_auTy" to 4L,
            ),
        )
        val m2Tlv = pairingData(m2)
        val serverPublic = m2Tlv[TlvValue.PUBLIC_KEY] ?: throw VerificationException("no public key in M2")
        val encrypted = m2Tlv[TlvValue.ENCRYPTED_DATA] ?: throw VerificationException("no encrypted data in M2")

        val shared = Crypto.x25519SharedSecret(ephemeralPrivate, serverPublic)
        val sessionKey = Crypto.hkdfSha512("Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info", shared)

        val decrypted = try {
            Crypto.chaChaDecrypt(sessionKey, Crypto.padNonce("PV-Msg02"), encrypted)
        } catch (e: Exception) {
            throw VerificationException("M2 decrypt failed (stale credentials?)")
        }
        val deviceTlv = Tlv8.read(decrypted)
        val identifier = deviceTlv[TlvValue.IDENTIFIER] ?: throw VerificationException("no identifier in M2")
        val signature = deviceTlv[TlvValue.SIGNATURE] ?: throw VerificationException("no signature in M2")

        if (!identifier.contentEquals(credentials.atvId)) {
            throw VerificationException("incorrect device response (identifier mismatch)")
        }
        val info = serverPublic + identifier + ephemeralPublic
        if (!Crypto.ed25519Verify(credentials.ltpk, info, signature)) {
            throw VerificationException("device signature error")
        }

        // M3
        val deviceInfo = ephemeralPublic + credentials.clientId + serverPublic
        val deviceSignature = Crypto.ed25519Sign(credentials.ltsk, deviceInfo)
        val tlv = Tlv8.write(
            linkedMapOf(
                TlvValue.IDENTIFIER to credentials.clientId,
                TlvValue.SIGNATURE to deviceSignature,
            ),
        )
        val m3Encrypted = Crypto.chaChaEncrypt(sessionKey, Crypto.padNonce("PV-Msg03"), tlv)

        val m4 = connection.exchangeAuth(
            FrameType.PV_Next,
            mapOf(
                PAIRING_DATA_KEY to Tlv8.write(
                    linkedMapOf(
                        TlvValue.SEQ_NO to byteArrayOf(0x03),
                        TlvValue.ENCRYPTED_DATA to m3Encrypted,
                    ),
                ),
            ),
        )
        pairingData(m4) // throws if the device reported an error

        return SessionKeys(
            outputKey = Crypto.hkdfSha512("", "ClientEncrypt-main", shared),
            inputKey = Crypto.hkdfSha512("", "ServerEncrypt-main", shared),
        )
    }
}
