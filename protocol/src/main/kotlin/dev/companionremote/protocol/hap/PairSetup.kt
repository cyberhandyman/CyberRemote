package dev.companionremote.protocol.hap

import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.companion.CompanionException
import dev.companionremote.protocol.companion.FrameType
import dev.companionremote.protocol.crypto.Crypto
import dev.companionremote.protocol.crypto.HapSrpClient
import dev.companionremote.protocol.opack.Opack
import dev.companionremote.protocol.tlv8.Tlv8
import dev.companionremote.protocol.tlv8.TlvValue
import java.util.UUID

/** Pairing failed (bad PIN, device refused, proof mismatch, ...). */
class PairingException(message: String) : CompanionException(message)

/**
 * HAP pair-setup (M1–M6) over Companion frames.
 * Ported from pyatv `protocols/companion/pairing.py`
 * (CompanionPairSetupProcedure) and `auth/hap_srp.py` (step1–step4).
 *
 * Flow: [startPairing] makes the PIN appear on the TV; then call
 * [finishPairing] with the PIN the user read off the screen.
 */
class PairSetup(
    private val connection: CompanionConnection,
    private val name: String = "Companion Remote",
) {
    private var atvSalt: ByteArray? = null
    private var atvPublicKey: ByteArray? = null

    /** M1/M2: request pairing; the Apple TV shows the PIN when this runs. */
    suspend fun startPairing() {
        val response = connection.exchangeAuth(
            FrameType.PS_Start,
            mapOf(
                PAIRING_DATA_KEY to Tlv8.write(
                    linkedMapOf(
                        TlvValue.METHOD to byteArrayOf(0x00),
                        TlvValue.SEQ_NO to byteArrayOf(0x01),
                    ),
                ),
                "_pwTy" to 1L,
            ),
        )
        val tlv = pairingData(response)
        atvSalt = tlv[TlvValue.SALT] ?: throw PairingException("no salt in M2")
        atvPublicKey = tlv[TlvValue.PUBLIC_KEY] ?: throw PairingException("no public key in M2")
    }

    /** M3–M6: complete pairing with the PIN shown on the TV screen. */
    suspend fun finishPairing(pin: String): HapCredentials {
        val salt = atvSalt ?: error("call startPairing first")
        val serverPublicKey = atvPublicKey ?: error("call startPairing first")

        val srp = HapSrpClient("Pair-Setup", pin)
        srp.processServerParams(serverPublicKey, salt)

        // M3: client public key + proof
        val m4 = connection.exchangeAuth(
            FrameType.PS_Next,
            mapOf(
                PAIRING_DATA_KEY to Tlv8.write(
                    linkedMapOf(
                        TlvValue.SEQ_NO to byteArrayOf(0x03),
                        TlvValue.PUBLIC_KEY to srp.publicKey,
                        TlvValue.PROOF to srp.clientProof,
                    ),
                ),
                "_pwTy" to 1L,
            ),
        )
        val m4Tlv = pairingData(m4)
        val serverProof = m4Tlv[TlvValue.PROOF] ?: throw PairingException("no proof in M4")
        if (!srp.verifyServerProof(serverProof)) {
            throw PairingException("server proof mismatch (wrong PIN?)")
        }

        // M5: exchange long-term identities inside encrypted TLV
        val ltsk = Crypto.randomBytes(32)
        val ltpk = Crypto.ed25519PublicKey(ltsk)
        val pairingId = UUID.randomUUID().toString().toByteArray()

        val iosDeviceX = Crypto.hkdfSha512(
            "Pair-Setup-Controller-Sign-Salt",
            "Pair-Setup-Controller-Sign-Info",
            srp.sessionKey,
        )
        val sessionKey = Crypto.hkdfSha512(
            "Pair-Setup-Encrypt-Salt",
            "Pair-Setup-Encrypt-Info",
            srp.sessionKey,
        )

        val deviceInfo = iosDeviceX + pairingId + ltpk
        val deviceSignature = Crypto.ed25519Sign(ltsk, deviceInfo)

        val innerTlv = Tlv8.write(
            linkedMapOf(
                TlvValue.IDENTIFIER to pairingId,
                TlvValue.PUBLIC_KEY to ltpk,
                TlvValue.SIGNATURE to deviceSignature,
                // The ATV shows this name under Settings; pyatv sends only "name"
                TlvValue.NAME to Opack.pack(mapOf("name" to name)),
            ),
        )
        val encrypted = Crypto.chaChaEncrypt(sessionKey, Crypto.padNonce("PS-Msg05"), innerTlv)

        val m6 = connection.exchangeAuth(
            FrameType.PS_Next,
            mapOf(
                PAIRING_DATA_KEY to Tlv8.write(
                    linkedMapOf(
                        TlvValue.SEQ_NO to byteArrayOf(0x05),
                        TlvValue.ENCRYPTED_DATA to encrypted,
                    ),
                ),
                "_pwTy" to 1L,
            ),
        )
        val m6Tlv = pairingData(m6)
        val m6Encrypted = m6Tlv[TlvValue.ENCRYPTED_DATA]
            ?: throw PairingException("no encrypted data in M6")

        val decrypted = try {
            Crypto.chaChaDecrypt(sessionKey, Crypto.padNonce("PS-Msg06"), m6Encrypted)
        } catch (e: Exception) {
            throw PairingException("M6 decrypt failed")
        }
        val deviceTlv = Tlv8.read(decrypted)
        val atvId = deviceTlv[TlvValue.IDENTIFIER] ?: throw PairingException("no identifier in M6")
        val atvLtpk = deviceTlv[TlvValue.PUBLIC_KEY] ?: throw PairingException("no public key in M6")

        return HapCredentials(ltpk = atvLtpk, ltsk = ltsk, atvId = atvId, clientId = pairingId)
    }

    companion object {
        internal const val PAIRING_DATA_KEY = "_pd"

        /** Extract and error-check the `_pd` TLV from an auth response. */
        internal fun pairingData(message: Map<Any?, Any?>): Map<Int, ByteArray> {
            val raw = message[PAIRING_DATA_KEY] as? ByteArray
                ?: throw PairingException("no pairing data in message")
            val tlv = Tlv8.read(raw)
            tlv[TlvValue.ERROR]?.let { error ->
                val code = if (error.isNotEmpty()) error[0].toInt() else -1
                throw PairingException("device returned pairing error code $code")
            }
            return tlv
        }
    }
}
