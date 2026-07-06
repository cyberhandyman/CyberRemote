package dev.companionremote.protocol.plist

import java.io.ByteArrayOutputStream
import java.util.IdentityHashMap

/** NSKeyedArchiver-style UID reference inside a binary plist. */
data class PlistUid(val value: Long)

/** Thrown when binary plist data cannot be encoded or decoded. */
class PlistException(message: String) : Exception(message)

/**
 * Minimal binary property list (bplist00) codec — just enough for the RTI
 * text-input payloads and for reading NSKeyedArchiver document state.
 *
 * The encoder replicates CPython `plistlib._BinaryPlistWriter` exactly
 * (flatten order, scalar deduplication, integer/size encodings) so that the
 * two RTI payloads are byte-identical with what pyatv sends
 * (`plist_payloads/rti_text_operations.py`, verified by golden vectors).
 *
 * Supported values: `null`, [Boolean], [Long]/[Int], [Double], [String],
 * [ByteArray], [List], [Map] (string keys), [PlistUid].
 */
object BinaryPlist {

    // Encoding

    fun encode(root: Any?): ByteArray {
        val objectList = mutableListOf<Any?>()
        val scalarTable = HashMap<Any, Int>()
        val identityTable = IdentityHashMap<Any, Int>()

        fun scalarKey(value: Any?): Any? = when (value) {
            is String -> "s" to value
            is Boolean -> "b" to value
            is Long -> "i" to value
            is Int -> "i" to value.toLong()
            is Double -> "d" to value
            is ByteArray -> "y" to value.toList()
            null -> "n"
            else -> null // containers and UIDs dedup by identity
        }

        fun flatten(value: Any?) {
            val key = scalarKey(value)
            if (key != null) {
                if (key in scalarTable) return
                scalarTable[key] = objectList.size
                objectList.add(value)
                return
            }
            if (value in identityTable) return
            identityTable[value] = objectList.size
            objectList.add(value)
            when (value) {
                is Map<*, *> -> {
                    for (k in value.keys) {
                        if (k !is String) throw PlistException("dict keys must be strings")
                        flatten(k)
                    }
                    for (v in value.values) flatten(v)
                }
                is List<*> -> for (item in value) flatten(item)
                is PlistUid -> Unit
                else -> throw PlistException("unsupported type: ${value?.let { it::class }}")
            }
        }

        fun refOf(value: Any?): Int {
            val key = scalarKey(value)
            return if (key != null) scalarTable.getValue(key) else identityTable.getValue(value)
        }

        flatten(root)

        val refSize = sizeForCount(objectList.size.toLong())
        val out = ByteArrayOutputStream()
        out.write("bplist00".toByteArray())
        val offsets = LongArray(objectList.size)

        fun writeBe(value: Long, size: Int) {
            for (i in size - 1 downTo 0) out.write(((value ushr (8 * i)) and 0xFF).toInt())
        }

        fun writeSize(token: Int, size: Int) {
            if (size < 15) {
                out.write(token or size)
            } else {
                out.write(token or 0xF)
                when {
                    size < 0x100 -> { out.write(0x10); writeBe(size.toLong(), 1) }
                    size < 0x10000 -> { out.write(0x11); writeBe(size.toLong(), 2) }
                    else -> { out.write(0x12); writeBe(size.toLong(), 4) }
                }
            }
        }

        fun writeRef(value: Any?) = writeBe(refOf(value).toLong(), refSize)

        for ((index, value) in objectList.withIndex()) {
            offsets[index] = out.size().toLong()
            when (value) {
                null -> out.write(0x00)
                is Boolean -> out.write(if (value) 0x09 else 0x08)
                is Int, is Long -> {
                    val v = (value as Number).toLong()
                    when {
                        v < 0 -> { out.write(0x13); writeBe(v, 8) }
                        v < 0x100 -> { out.write(0x10); writeBe(v, 1) }
                        v < 0x10000 -> { out.write(0x11); writeBe(v, 2) }
                        v < 0x100000000L -> { out.write(0x12); writeBe(v, 4) }
                        else -> { out.write(0x13); writeBe(v, 8) }
                    }
                }
                is Double -> {
                    out.write(0x23)
                    writeBe(java.lang.Double.doubleToLongBits(value), 8)
                }
                is ByteArray -> {
                    writeSize(0x40, value.size)
                    out.write(value)
                }
                is String -> {
                    val isAscii = value.all { it.code < 0x80 }
                    if (isAscii) {
                        writeSize(0x50, value.length)
                        out.write(value.toByteArray(Charsets.US_ASCII))
                    } else {
                        val encoded = value.toByteArray(Charsets.UTF_16BE)
                        writeSize(0x60, encoded.size / 2)
                        out.write(encoded)
                    }
                }
                is PlistUid -> {
                    val v = value.value
                    val size = when {
                        v < 0x100 -> 1
                        v < 0x10000 -> 2
                        v < 0x100000000L -> 4
                        else -> 8
                    }
                    out.write(0x80 or (size - 1))
                    writeBe(v, size)
                }
                is List<*> -> {
                    writeSize(0xA0, value.size)
                    for (item in value) writeRef(item)
                }
                is Map<*, *> -> {
                    writeSize(0xD0, value.size)
                    for (k in value.keys) writeRef(k)
                    for (v in value.values) writeRef(v)
                }
                else -> throw PlistException("unsupported type: ${value::class}")
            }
        }

        val tableOffset = out.size().toLong()
        val offsetSize = sizeForCount(tableOffset)
        for (offset in offsets) writeBe(offset, offsetSize)

        // Trailer
        out.write(ByteArray(6))
        out.write(offsetSize)
        out.write(refSize)
        writeBe(objectList.size.toLong(), 8)
        writeBe(0, 8) // top object ref
        writeBe(tableOffset, 8)
        return out.toByteArray()
    }

    private fun sizeForCount(count: Long): Int = when {
        count < 0x100 -> 1
        count < 0x10000 -> 2
        count < 0x100000000L -> 4
        else -> 8
    }

    // Decoding

    fun decode(data: ByteArray): Any? {
        if (data.size < 40 || !data.copyOfRange(0, 8).contentEquals("bplist00".toByteArray())) {
            throw PlistException("not a bplist00")
        }
        val trailerStart = data.size - 32
        val offsetSize = data[trailerStart + 6].toInt() and 0xFF
        val refSize = data[trailerStart + 7].toInt() and 0xFF
        val numObjects = readBe(data, trailerStart + 8, 8)
        val topObject = readBe(data, trailerStart + 16, 8)
        val tableOffset = readBe(data, trailerStart + 24, 8)

        val offsets = LongArray(numObjects.toInt()) {
            readBe(data, (tableOffset + it * offsetSize).toInt(), offsetSize)
        }
        val memo = arrayOfNulls<Any?>(numObjects.toInt())
        val done = BooleanArray(numObjects.toInt())

        fun readObject(ref: Int): Any? {
            if (done[ref]) return memo[ref]
            var pos = offsets[ref].toInt()
            val marker = data[pos].toInt() and 0xFF
            pos += 1
            val nibble = marker and 0x0F

            fun readCount(): Int {
                if (nibble != 0xF) return nibble
                val intMarker = data[pos].toInt() and 0xFF
                if ((intMarker and 0xF0) != 0x10) throw PlistException("bad size int")
                val size = 1 shl (intMarker and 0xF)
                val count = readBe(data, pos + 1, size)
                pos += 1 + size
                return count.toInt()
            }

            val value: Any? = when (marker and 0xF0) {
                0x00 -> when (marker) {
                    0x00 -> null
                    0x08 -> false
                    0x09 -> true
                    else -> throw PlistException("unsupported marker 0x${marker.toString(16)}")
                }
                0x10 -> {
                    val size = 1 shl nibble
                    val raw = readBe(data, pos, size)
                    raw
                }
                0x20 -> when (marker) {
                    0x22 -> java.lang.Float.intBitsToFloat(readBe(data, pos, 4).toInt()).toDouble()
                    0x23 -> java.lang.Double.longBitsToDouble(readBe(data, pos, 8))
                    else -> throw PlistException("unsupported real 0x${marker.toString(16)}")
                }
                0x30 -> java.lang.Double.longBitsToDouble(readBe(data, pos, 8)) // date as seconds
                0x40 -> {
                    val count = readCount()
                    data.copyOfRange(pos, pos + count)
                }
                0x50 -> {
                    val count = readCount()
                    String(data, pos, count, Charsets.US_ASCII)
                }
                0x60 -> {
                    val count = readCount()
                    String(data, pos, count * 2, Charsets.UTF_16BE)
                }
                0x80 -> PlistUid(readBe(data, pos, nibble + 1))
                0xA0, 0xC0 -> { // array / set (set decoded as list)
                    val count = readCount()
                    val refs = (0 until count).map { readBe(data, pos + it * refSize, refSize).toInt() }
                    memo[ref] = mutableListOf<Any?>()
                    done[ref] = true
                    val list = memo[ref] as MutableList<Any?>
                    for (r in refs) list.add(readObject(r))
                    return list
                }
                0xD0 -> {
                    val count = readCount()
                    val keyRefs = (0 until count).map { readBe(data, pos + it * refSize, refSize).toInt() }
                    val valueRefs = (0 until count).map {
                        readBe(data, pos + (count + it) * refSize, refSize).toInt()
                    }
                    memo[ref] = LinkedHashMap<Any?, Any?>()
                    done[ref] = true
                    val map = memo[ref] as LinkedHashMap<Any?, Any?>
                    for (i in 0 until count) map[readObject(keyRefs[i])] = readObject(valueRefs[i])
                    return map
                }
                else -> throw PlistException("unsupported marker 0x${marker.toString(16)}")
            }
            memo[ref] = value
            done[ref] = true
            return value
        }

        return readObject(topObject.toInt())
    }

    private fun readBe(data: ByteArray, offset: Int, size: Int): Long {
        var out = 0L
        for (i in 0 until size) out = (out shl 8) or (data[offset + i].toLong() and 0xFF)
        return out
    }
}
