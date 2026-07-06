package dev.companionremote.protocol.hap

import dev.companionremote.protocol.companion.FRAME_HEADER_LENGTH
import dev.companionremote.protocol.companion.FrameType
import dev.companionremote.protocol.companion.frameHeader
import dev.companionremote.protocol.crypto.CompanionSessionCipher
import dev.companionremote.protocol.crypto.Crypto
import dev.companionremote.protocol.crypto.HapSrpClient
import dev.companionremote.protocol.opack.Opack
import dev.companionremote.protocol.tlv8.Tlv8
import dev.companionremote.protocol.tlv8.TlvValue
import dev.companionremote.protocol.transport.Transport
import java.math.BigInteger
import kotlinx.coroutines.channels.Channel

/**
 * A minimal in-process Apple TV: SRP-6a server (same srptools semantics as
 * the client), HAP pair-setup/pair-verify responder, and (for M4+) an
 * encrypted E_OPACK echo service. Runs the "device side" of a
 * [dev.companionremote.protocol.companion.CompanionConnection] attached to
 * the paired [transport][clientTransport].
 */
class FakeAtv(private val pin: String) {

    val atvLtsk = Crypto.randomBytes(32)
    val atvLtpk = Crypto.ed25519PublicKey(atvLtsk)
    val atvId = "fakeatv-identifier".toByteArray()

    // Pair-setup state
    private val salt = Crypto.randomBytes(16)
    private val srpPrivate = BigInteger(1, Crypto.randomBytes(32))
    private var srpSessionKey: ByteArray? = null

    // Pair-verify state
    private val verifyPrivate = Crypto.randomBytes(32)
    private var verifySessionKey: ByteArray? = null
    private var verifyShared: ByteArray? = null

    var pairedClientLtpk: ByteArray? = null
    var pairedClientId: ByteArray? = null
    var sessionCipher: CompanionSessionCipher? = null

    /** Handler for decrypted E_OPACK requests once the session is encrypted. */
    var requestHandler: ((Map<Any?, Any?>) -> Map<String, Any?>?)? = null

    /** Decrypted E_OPACK requests (`_t` = 2) in arrival order. */
    val requestLog = mutableListOf<Map<Any?, Any?>>()

    /** Decrypted E_OPACK events (`_t` = 1) in arrival order. */
    val eventLog = mutableListOf<Map<Any?, Any?>>()

    private val n = HapSrpClient.N
    private val g = HapSrpClient.G

    private fun h(vararg parts: ByteArray) = Crypto.sha512(*parts)
    private fun minimal(v: BigInteger) = HapSrpClient.minimalBytes(v)
    private fun pad(v: BigInteger): ByteArray {
        val bytes = minimal(v)
        return if (bytes.size >= 384) bytes else ByteArray(384 - bytes.size) + bytes
    }

    private val x = BigInteger(1, h(salt, h("Pair-Setup:$pin".toByteArray())))
    private val verifier = g.modPow(x, n)
    private val k = BigInteger(1, h(minimal(n), pad(g)))
    private val bigB = k.multiply(verifier).add(g.modPow(srpPrivate, n)).mod(n)

    /** Process one client frame; returns the response frame bytes (or null). */
    fun handleFrame(raw: ByteArray): ByteArray? {
        val type = FrameType.fromCode(raw[0].toInt() and 0xFF)!!
        var payload = raw.copyOfRange(FRAME_HEADER_LENGTH, raw.size)
        // Snapshot before handling: the response to PV M3 must still go out
        // in plaintext even though M3 processing arms the session cipher.
        val cipher = sessionCipher
        if (cipher != null && payload.isNotEmpty()) {
            payload = cipher.decrypt(payload, raw.copyOfRange(0, FRAME_HEADER_LENGTH))
        }
        @Suppress("UNCHECKED_CAST")
        val message = Opack.unpack(payload).first as Map<Any?, Any?>
        val response: Pair<FrameType, Map<String, Any?>>? = when (type) {
            FrameType.PS_Start, FrameType.PS_Next -> FrameType.PS_Next to handlePairSetup(message)
            FrameType.PV_Start, FrameType.PV_Next -> FrameType.PV_Next to handlePairVerify(message)
            FrameType.E_OPACK -> handleOpack(message)?.let { FrameType.E_OPACK to it }
            else -> null
        }
        return response?.let { (respType, data) ->
            var respPayload = Opack.pack(data)
            var header = frameHeader(respType, respPayload.size)
            if (cipher != null && respPayload.isNotEmpty()) {
                header = frameHeader(respType, respPayload.size + 16)
                respPayload = cipher.encrypt(respPayload, header)
            }
            header + respPayload
        }
    }

    private fun handlePairSetup(message: Map<Any?, Any?>): Map<String, Any?> {
        val tlv = Tlv8.read(message["_pd"] as ByteArray)
        return when (tlv[TlvValue.SEQ_NO]?.get(0)?.toInt()) {
            1 -> mapOf(
                "_pd" to Tlv8.write(
                    linkedMapOf(
                        TlvValue.SEQ_NO to byteArrayOf(0x02),
                        TlvValue.SALT to salt,
                        TlvValue.PUBLIC_KEY to minimal(bigB),
                    ),
                ),
            )
            3 -> {
                val bigA = BigInteger(1, tlv[TlvValue.PUBLIC_KEY]!!)
                val clientProof = tlv[TlvValue.PROOF]!!
                val u = BigInteger(1, h(pad(bigA), pad(bigB)))
                val s = bigA.multiply(verifier.modPow(u, n)).mod(n).modPow(srpPrivate, n)
                val sessionKey = h(minimal(s))
                srpSessionKey = sessionKey
                val hN = BigInteger(1, h(minimal(n)))
                val hG = BigInteger(1, h(minimal(g)))
                val hI = BigInteger(1, h("Pair-Setup".toByteArray()))
                val expectedM1 = h(
                    minimal(hN.xor(hG)), minimal(hI), salt,
                    minimal(bigA), minimal(bigB), sessionKey,
                )
                if (!expectedM1.contentEquals(clientProof)) {
                    return mapOf(
                        "_pd" to Tlv8.write(
                            linkedMapOf(
                                TlvValue.SEQ_NO to byteArrayOf(0x04),
                                TlvValue.ERROR to byteArrayOf(0x02),
                            ),
                        ),
                    )
                }
                val m2 = h(minimal(bigA), expectedM1, sessionKey)
                mapOf(
                    "_pd" to Tlv8.write(
                        linkedMapOf(
                            TlvValue.SEQ_NO to byteArrayOf(0x04),
                            TlvValue.PROOF to m2,
                        ),
                    ),
                )
            }
            5 -> {
                val setupKey = Crypto.hkdfSha512(
                    "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info", srpSessionKey!!,
                )
                val decrypted = Crypto.chaChaDecrypt(
                    setupKey, Crypto.padNonce("PS-Msg05"),
                    tlv[TlvValue.ENCRYPTED_DATA]!!,
                )
                val inner = Tlv8.read(decrypted)
                pairedClientId = inner[TlvValue.IDENTIFIER]
                pairedClientLtpk = inner[TlvValue.PUBLIC_KEY]

                // Verify the controller signature like a real device would
                val iosDeviceX = Crypto.hkdfSha512(
                    "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info", srpSessionKey!!,
                )
                val info = iosDeviceX + pairedClientId!! + pairedClientLtpk!!
                check(Crypto.ed25519Verify(pairedClientLtpk!!, info, inner[TlvValue.SIGNATURE]!!)) {
                    "controller signature invalid"
                }

                val accessoryX = Crypto.hkdfSha512(
                    "Pair-Setup-Accessory-Sign-Salt", "Pair-Setup-Accessory-Sign-Info", srpSessionKey!!,
                )
                val accessoryInfo = accessoryX + atvId + atvLtpk
                val accessorySignature = Crypto.ed25519Sign(atvLtsk, accessoryInfo)
                val innerResponse = Tlv8.write(
                    linkedMapOf(
                        TlvValue.IDENTIFIER to atvId,
                        TlvValue.PUBLIC_KEY to atvLtpk,
                        TlvValue.SIGNATURE to accessorySignature,
                    ),
                )
                mapOf(
                    "_pd" to Tlv8.write(
                        linkedMapOf(
                            TlvValue.SEQ_NO to byteArrayOf(0x06),
                            TlvValue.ENCRYPTED_DATA to Crypto.chaChaEncrypt(
                                setupKey, Crypto.padNonce("PS-Msg06"), innerResponse,
                            ),
                        ),
                    ),
                )
            }
            else -> error("unexpected pair-setup state")
        }
    }

    private fun handlePairVerify(message: Map<Any?, Any?>): Map<String, Any?> {
        val tlv = Tlv8.read(message["_pd"] as ByteArray)
        return when (tlv[TlvValue.SEQ_NO]?.get(0)?.toInt()) {
            1 -> {
                val clientPublic = tlv[TlvValue.PUBLIC_KEY]!!
                val serverPublic = Crypto.x25519PublicKey(verifyPrivate)
                val shared = Crypto.x25519SharedSecret(verifyPrivate, clientPublic)
                verifyShared = shared
                val sessionKey = Crypto.hkdfSha512(
                    "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info", shared,
                )
                verifySessionKey = sessionKey
                val info = serverPublic + atvId + clientPublic
                val signature = Crypto.ed25519Sign(atvLtsk, info)
                val inner = Tlv8.write(
                    linkedMapOf(
                        TlvValue.IDENTIFIER to atvId,
                        TlvValue.SIGNATURE to signature,
                    ),
                )
                mapOf(
                    "_pd" to Tlv8.write(
                        linkedMapOf(
                            TlvValue.SEQ_NO to byteArrayOf(0x02),
                            TlvValue.PUBLIC_KEY to serverPublic,
                            TlvValue.ENCRYPTED_DATA to Crypto.chaChaEncrypt(
                                sessionKey, Crypto.padNonce("PV-Msg02"), inner,
                            ),
                        ),
                    ),
                )
            }
            3 -> {
                val decrypted = Crypto.chaChaDecrypt(
                    verifySessionKey!!, Crypto.padNonce("PV-Msg03"),
                    tlv[TlvValue.ENCRYPTED_DATA]!!,
                )
                val inner = Tlv8.read(decrypted)
                val clientId = inner[TlvValue.IDENTIFIER]!!
                check(clientId.contentEquals(pairedClientId)) { "unknown pairing identifier" }
                // Enable session encryption: our output is the client's input
                val outKey = Crypto.hkdfSha512("", "ServerEncrypt-main", verifyShared!!)
                val inKey = Crypto.hkdfSha512("", "ClientEncrypt-main", verifyShared!!)
                sessionCipher = CompanionSessionCipher(outKey, inKey)
                mapOf(
                    "_pd" to Tlv8.write(linkedMapOf(TlvValue.SEQ_NO to byteArrayOf(0x04))),
                )
            }
            else -> error("unexpected pair-verify state")
        }
    }

    private fun handleOpack(message: Map<Any?, Any?>): Map<String, Any?>? {
        when (message["_t"]) {
            1L -> {
                eventLog.add(message)
                return null // events get no response
            }
            2L -> {
                requestLog.add(message)
                val content = requestHandler?.invoke(message)
                    ?: mapOf("_c" to emptyMap<String, Any?>())
                return mapOf(
                    "_t" to 3L,
                    "_x" to (message["_x"] as Long),
                ) + content
            }
            else -> return null
        }
    }
}

/**
 * Transport connected to a [FakeAtv]: everything the client writes goes to
 * the fake device, whose responses become readable data.
 */
class FakeAtvTransport(private val atv: FakeAtv) : Transport {
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var readBuffer = ByteArray(0)

    override suspend fun read(): ByteArray? {
        val result = incoming.receiveCatching()
        return result.getOrNull()
    }

    override suspend fun write(data: ByteArray) {
        // Reassemble frames (the connection writes exactly one frame per call)
        readBuffer += data
        while (readBuffer.size >= FRAME_HEADER_LENGTH) {
            val length = FRAME_HEADER_LENGTH + (
                ((readBuffer[1].toInt() and 0xFF) shl 16) or
                    ((readBuffer[2].toInt() and 0xFF) shl 8) or
                    (readBuffer[3].toInt() and 0xFF)
                )
            if (readBuffer.size < length) break
            val frame = readBuffer.copyOfRange(0, length)
            readBuffer = readBuffer.copyOfRange(length, readBuffer.size)
            atv.handleFrame(frame)?.let { incoming.trySend(it) }
        }
    }

    override fun close() {
        incoming.close()
    }
}
