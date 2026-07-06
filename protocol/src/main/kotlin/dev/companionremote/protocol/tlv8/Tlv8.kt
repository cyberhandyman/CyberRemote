package dev.companionremote.protocol.tlv8

/**
 * TLV8 as used by the HAP pairing process.
 * Ported from pyatv `pyatv/auth/hap_tlv8.py`.
 */
object Tlv8 {

    /**
     * Parse TLV8 bytes into a map. Values larger than 255 bytes are split
     * over multiple entries with the same tag; they are merged here.
     */
    fun read(data: ByteArray): Map<Int, ByteArray> {
        val result = LinkedHashMap<Int, ByteArray>()
        var pos = 0
        while (pos < data.size) {
            val tag = data[pos].toInt() and 0xFF
            val length = data[pos + 1].toInt() and 0xFF
            val value = data.copyOfRange(pos + 2, pos + 2 + length)
            result[tag] = result[tag]?.plus(value) ?: value
            pos += 2 + length
        }
        return result
    }

    /** Convert a map to TLV8 bytes, splitting values > 255 bytes. */
    fun write(data: Map<Int, ByteArray>): ByteArray {
        var tlv = ByteArray(0)
        for ((tag, value) in data) {
            var pos = 0
            while (pos < value.size) {
                val size = minOf(value.size - pos, 255)
                tlv += tag.toByte()
                tlv += size.toByte()
                tlv += value.copyOfRange(pos, pos + size)
                pos += size
            }
            if (value.isEmpty()) {
                // pyatv's write_tlv silently drops empty values; HAP never
                // needs them, but be explicit rather than surprising.
                tlv += tag.toByte()
                tlv += 0
            }
        }
        return tlv
    }
}

/** TLV tags from the HAP specification (pyatv `hap_tlv8.py TlvValue`). */
object TlvValue {
    const val METHOD = 0x00
    const val IDENTIFIER = 0x01
    const val SALT = 0x02
    const val PUBLIC_KEY = 0x03
    const val PROOF = 0x04
    const val ENCRYPTED_DATA = 0x05
    const val SEQ_NO = 0x06
    const val ERROR = 0x07
    const val BACK_OFF = 0x08
    const val CERTIFICATE = 0x09
    const val SIGNATURE = 0x0A
    const val PERMISSIONS = 0x0B
    const val FRAGMENT_DATA = 0x0C
    const val FRAGMENT_LAST = 0x0D
    const val NAME = 0x11
    const val FLAGS = 0x13
}

/** HAP pairing error codes (pyatv `hap_tlv8.py ErrorCode`). */
object TlvErrorCode {
    const val UNKNOWN = 0x01
    const val AUTHENTICATION = 0x02
    const val BACK_OFF = 0x03
    const val MAX_PEERS = 0x04
    const val MAX_TRIES = 0x05
    const val UNAVAILABLE = 0x06
    const val BUSY = 0x07
}
