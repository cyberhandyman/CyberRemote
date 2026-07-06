package dev.companionremote.protocol.opack

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Thrown when OPACK data cannot be encoded or decoded. */
class OpackException(message: String) : Exception(message)

/**
 * OPACK serialization, ported from pyatv `pyatv/support/opack.py`.
 *
 * Supported Kotlin types: `null`, [Boolean], [Long]/[Int]/[Short]/[Byte]
 * (non-negative only — the format has no negative integer encoding),
 * [Float] (float32), [Double] (float64), [String], [ByteArray],
 * [List], [Map] and [UUID].
 *
 * Pointer/back-reference behavior (0xA0–0xC4) and endless collections are
 * implemented exactly like pyatv, including the asymmetries of the original
 * (the encoder's object table contains encoded containers, the decoder's
 * table only leaf values) — port, don't invent.
 */
object Opack {

    /** Pack [data] into OPACK bytes. */
    fun pack(data: Any?): ByteArray = pack(data, mutableListOf())

    /** Unpack OPACK [data]; returns the decoded value and any trailing bytes. */
    fun unpack(data: ByteArray): Pair<Any?, ByteArray> {
        val objectList = mutableListOf<Any?>()
        val (value, offset) = unpack(data, 0, objectList)
        return value to data.copyOfRange(offset, data.size)
    }

    private fun pack(data: Any?, objectList: MutableList<ByteArray>): ByteArray {
        var packed: ByteArray = when (data) {
            null -> byteArrayOf(0x04)
            is Boolean -> byteArrayOf(if (data) 0x01 else 0x02)
            is UUID -> ByteBuffer.allocate(17)
                .put(0x05)
                .putLong(data.mostSignificantBits)
                .putLong(data.leastSignificantBits)
                .array()
            is Byte -> packLong(data.toLong())
            is Short -> packLong(data.toLong())
            is Int -> packLong(data.toLong())
            is Long -> packLong(data)
            is Float -> ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
                .put(0x35).putFloat(data).array()
            is Double -> ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                .put(0x36).putDouble(data).array()
            is String -> packString(data)
            is ByteArray -> packBytes(data)
            is List<*> -> {
                var out = byteArrayOf((0xD0 + minOf(data.size, 0xF)).toByte())
                for (item in data) out += pack(item, objectList)
                if (data.size >= 0xF) out += 0x03
                out
            }
            is Map<*, *> -> {
                var out = byteArrayOf((0xE0 + minOf(data.size, 0xF)).toByte())
                for ((k, v) in data) out += pack(k, objectList) + pack(v, objectList)
                if (data.size >= 0xF) out += 0x03
                out
            }
            else -> throw OpackException("unsupported type: ${data::class}")
        }

        // Reuse if in object list, otherwise add it to the list
        val index = objectList.indexOfFirst { it.contentEquals(packed) }
        if (index >= 0) {
            packed = when {
                index < 0x21 -> byteArrayOf((0xA0 + index).toByte())
                index <= 0xFF -> byteArrayOf(0xC1.toByte()) + leBytes(index.toLong(), 1)
                index <= 0xFFFF -> byteArrayOf(0xC2.toByte()) + leBytes(index.toLong(), 2)
                index <= 0xFFFFFFFF -> byteArrayOf(0xC3.toByte()) + leBytes(index.toLong(), 4)
                else -> byteArrayOf(0xC4.toByte()) + leBytes(index.toLong(), 8)
            }
        } else if (packed.size > 1) {
            objectList.add(packed)
        }
        return packed
    }

    private fun packLong(value: Long): ByteArray = when {
        value < 0 -> throw OpackException("negative integers are not supported by OPACK")
        value < 0x28 -> byteArrayOf((value + 8).toByte())
        value <= 0xFF -> byteArrayOf(0x30) + leBytes(value, 1)
        value <= 0xFFFF -> byteArrayOf(0x31) + leBytes(value, 2)
        value <= 0xFFFFFFFFL -> byteArrayOf(0x32) + leBytes(value, 4)
        else -> byteArrayOf(0x33) + leBytes(value, 8)
    }

    private fun packString(value: String): ByteArray {
        val encoded = value.toByteArray(Charsets.UTF_8)
        return when {
            encoded.size <= 0x20 -> byteArrayOf((0x40 + encoded.size).toByte()) + encoded
            encoded.size <= 0xFF -> byteArrayOf(0x61) + leBytes(encoded.size.toLong(), 1) + encoded
            encoded.size <= 0xFFFF -> byteArrayOf(0x62) + leBytes(encoded.size.toLong(), 2) + encoded
            encoded.size <= 0xFFFFFF -> byteArrayOf(0x63) + leBytes(encoded.size.toLong(), 3) + encoded
            else -> byteArrayOf(0x64) + leBytes(encoded.size.toLong(), 4) + encoded
        }
    }

    private fun packBytes(value: ByteArray): ByteArray = when {
        value.size <= 0x20 -> byteArrayOf((0x70 + value.size).toByte()) + value
        value.size <= 0xFF -> byteArrayOf(0x91.toByte()) + leBytes(value.size.toLong(), 1) + value
        value.size <= 0xFFFF -> byteArrayOf(0x92.toByte()) + leBytes(value.size.toLong(), 2) + value
        value.size <= 0xFFFFFFFFL -> byteArrayOf(0x93.toByte()) + leBytes(value.size.toLong(), 4) + value
        else -> byteArrayOf(0x94.toByte()) + leBytes(value.size.toLong(), 8) + value
    }

    /** Returns decoded value and the new offset. */
    private fun unpack(data: ByteArray, offset: Int, objectList: MutableList<Any?>): Pair<Any?, Int> {
        if (offset >= data.size) throw OpackException("no data to unpack")
        val tag = data[offset].toInt() and 0xFF
        var addToObjectList = true
        val value: Any?
        var next: Int
        when {
            tag == 0x01 -> { value = true; next = offset + 1; addToObjectList = false }
            tag == 0x02 -> { value = false; next = offset + 1; addToObjectList = false }
            tag == 0x04 -> { value = null; next = offset + 1; addToObjectList = false }
            tag == 0x05 -> {
                val buf = ByteBuffer.wrap(data, offset + 1, 16)
                value = UUID(buf.long, buf.long)
                next = offset + 17
            }
            tag == 0x06 -> {
                // Absolute time: like pyatv, only parsed as an integer
                value = leLong(data, offset + 1, 8)
                next = offset + 9
            }
            tag in 0x08..0x2F -> { value = (tag - 8).toLong(); next = offset + 1; addToObjectList = false }
            tag == 0x35 -> {
                value = ByteBuffer.wrap(data, offset + 1, 4).order(ByteOrder.LITTLE_ENDIAN).float
                next = offset + 5
            }
            tag == 0x36 -> {
                value = ByteBuffer.wrap(data, offset + 1, 8).order(ByteOrder.LITTLE_ENDIAN).double
                next = offset + 9
            }
            (tag and 0xF0) == 0x30 -> {
                val size = 1 shl (tag and 0xF)
                value = leLong(data, offset + 1, size)
                next = offset + 1 + size
            }
            tag in 0x40..0x60 -> {
                val length = tag - 0x40
                value = String(data, offset + 1, length, Charsets.UTF_8)
                next = offset + 1 + length
            }
            tag in 0x61..0x64 -> {
                val sizeBytes = tag and 0xF
                val length = leLong(data, offset + 1, sizeBytes).toInt()
                value = String(data, offset + 1 + sizeBytes, length, Charsets.UTF_8)
                next = offset + 1 + sizeBytes + length
            }
            tag in 0x70..0x90 -> {
                val length = tag - 0x70
                value = data.copyOfRange(offset + 1, offset + 1 + length)
                next = offset + 1 + length
            }
            tag in 0x91..0x94 -> {
                val sizeBytes = 1 shl ((tag and 0xF) - 1)
                val length = leLong(data, offset + 1, sizeBytes).toInt()
                value = data.copyOfRange(offset + 1 + sizeBytes, offset + 1 + sizeBytes + length)
                next = offset + 1 + sizeBytes + length
            }
            (tag and 0xF0) == 0xD0 -> {
                val count = tag and 0xF
                val output = mutableListOf<Any?>()
                var ptr = offset + 1
                if (count == 0xF) { // Endless list
                    while ((data[ptr].toInt() and 0xFF) != 0x03) {
                        val (item, newPtr) = unpack(data, ptr, objectList)
                        output.add(item)
                        ptr = newPtr
                    }
                    ptr += 1
                } else {
                    repeat(count) {
                        val (item, newPtr) = unpack(data, ptr, objectList)
                        output.add(item)
                        ptr = newPtr
                    }
                }
                value = output
                next = ptr
                addToObjectList = false
            }
            (tag and 0xE0) == 0xE0 -> {
                val count = tag and 0xF
                val output = LinkedHashMap<Any?, Any?>()
                var ptr = offset + 1
                if (count == 0xF) { // Endless dict
                    while ((data[ptr].toInt() and 0xFF) != 0x03) {
                        val (k, afterKey) = unpack(data, ptr, objectList)
                        val (v, afterValue) = unpack(data, afterKey, objectList)
                        output[k] = v
                        ptr = afterValue
                    }
                    ptr += 1
                } else {
                    repeat(count) {
                        val (k, afterKey) = unpack(data, ptr, objectList)
                        val (v, afterValue) = unpack(data, afterKey, objectList)
                        output[k] = v
                        ptr = afterValue
                    }
                }
                value = output
                next = ptr
                addToObjectList = false
            }
            tag in 0xA0..0xC0 -> {
                value = objectList[tag - 0xA0]
                next = offset + 1
            }
            tag in 0xC1..0xC4 -> {
                // NB: pyatv reads tag - 0xC0 bytes here (1..4), asymmetric with
                // its encoder which writes 8 bytes for 0xC4 — kept identical.
                val length = tag - 0xC0
                val uid = leLong(data, offset + 1, length).toInt()
                value = objectList[uid]
                next = offset + 1 + length
            }
            else -> throw OpackException("unsupported tag: 0x${tag.toString(16)}")
        }

        if (addToObjectList && !containsValue(objectList, value)) {
            objectList.add(value)
        }
        return value to next
    }

    private fun containsValue(list: List<Any?>, value: Any?): Boolean = list.any { item ->
        if (item is ByteArray && value is ByteArray) item.contentEquals(value) else item == value
    }

    private fun leBytes(value: Long, size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) out[i] = ((value ushr (8 * i)) and 0xFF).toByte()
        return out
    }

    private fun leLong(data: ByteArray, offset: Int, size: Int): Long {
        var out = 0L
        for (i in 0 until size) out = out or ((data[offset + i].toLong() and 0xFF) shl (8 * i))
        return out
    }
}
