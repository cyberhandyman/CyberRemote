package dev.companionremote.protocol.companion

/**
 * Companion frame types, ported from pyatv
 * `pyatv/protocols/companion/connection.py FrameType`.
 */
enum class FrameType(val code: Int) {
    Unknown(0),
    NoOp(1),
    PS_Start(3),
    PS_Next(4),
    PV_Start(5),
    PV_Next(6),
    U_OPACK(7),
    E_OPACK(8),
    P_OPACK(9),
    PA_Req(10),
    PA_Rsp(11),
    SessionStartRequest(16),
    SessionStartResponse(17),
    SessionData(18),
    FamilyIdentityRequest(32),
    FamilyIdentityResponse(33),
    FamilyIdentityUpdate(34),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): FrameType? = byCode[code]
    }
}

/** A raw Companion frame: 1-byte type + 3-byte big-endian length + payload. */
data class Frame(val type: FrameType, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is Frame && other.type == type && other.payload.contentEquals(payload)

    override fun hashCode(): Int = 31 * type.hashCode() + payload.contentHashCode()
}

const val FRAME_HEADER_LENGTH = 4
const val AUTH_TAG_LENGTH = 16

fun frameHeader(type: FrameType, payloadLength: Int): ByteArray = byteArrayOf(
    type.code.toByte(),
    ((payloadLength ushr 16) and 0xFF).toByte(),
    ((payloadLength ushr 8) and 0xFF).toByte(),
    (payloadLength and 0xFF).toByte(),
)

/**
 * Incremental frame parser: feed raw bytes as they arrive (in arbitrary
 * segmentation) and pull complete frames out.
 */
class FrameReader {
    private var buffer = ByteArray(0)

    /** Add received bytes and return all frames completed by them. */
    fun feed(data: ByteArray): List<Pair<ByteArray, ByteArray>> {
        buffer += data
        val frames = mutableListOf<Pair<ByteArray, ByteArray>>()
        while (buffer.size >= FRAME_HEADER_LENGTH) {
            val payloadLength = ((buffer[1].toInt() and 0xFF) shl 16) or
                ((buffer[2].toInt() and 0xFF) shl 8) or
                (buffer[3].toInt() and 0xFF)
            val totalLength = FRAME_HEADER_LENGTH + payloadLength
            if (buffer.size < totalLength) break
            val header = buffer.copyOfRange(0, FRAME_HEADER_LENGTH)
            val payload = buffer.copyOfRange(FRAME_HEADER_LENGTH, totalLength)
            buffer = buffer.copyOfRange(totalLength, buffer.size)
            frames.add(header to payload)
        }
        return frames
    }
}
