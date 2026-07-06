package dev.companionremote.protocol.companion

import dev.companionremote.protocol.opack.Opack
import dev.companionremote.protocol.transport.Transport
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Base class for protocol failures. */
open class CompanionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The device replied with an error (`_em` in the response). */
class CompanionCommandException(message: String, val errorCode: Long?) : CompanionException(message)

/** Connection was closed (EOF, I/O error, or AEAD failure). */
class CompanionConnectionClosedException(message: String, cause: Throwable? = null) :
    CompanionException(message, cause)

/** An event pushed by the Apple TV (`_t` = 1). */
data class CompanionEvent(val name: String, val content: Map<Any?, Any?>)

/**
 * Message types in E_OPACK messages, ported from pyatv
 * `pyatv/protocols/companion/protocol.py MessageType`.
 */
object MessageType {
    const val EVENT = 1L
    const val REQUEST = 2L
    const val RESPONSE = 3L
}

/**
 * Coroutine-based Companion connection: frame codec on top of a [Transport],
 * XID request/response correlation, event dispatch and (from M3) transparent
 * payload encryption.
 *
 * Ported from pyatv `pyatv/protocols/companion/connection.py` and
 * `protocol.py` (send/exchange logic, auth-frame matching, `_em` handling).
 */
class CompanionConnection(
    private val transport: Transport,
    private val defaultTimeoutMs: Long = 5_000,
) {
    private val scope = CoroutineScope(Job() + Dispatchers.Default + CoroutineName("companion-connection"))
    private val reader = FrameReader()
    private val writeMutex = Mutex()
    private val lock = Any()

    @Volatile
    private var cipher: SessionCipher? = null
    private var xid: Long = Random.nextLong(0, 1 shl 16)
    private var closed = false
    private var closeCause: Throwable? = null

    // Response waiters: key is either a Long XID or a FrameType (auth frames)
    private val waiters = mutableMapOf<Any, CompletableDeferred<Map<Any?, Any?>>>()

    private val _events = MutableSharedFlow<CompanionEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Events pushed by the device (`_t` = 1), e.g. `_iMC`, `_tiStarted`, SystemStatus. */
    val events: SharedFlow<CompanionEvent> = _events

    private val readLoop = scope.launch(start = CoroutineStart.LAZY) {
        try {
            while (true) {
                val chunk = transport.read() ?: throw CompanionConnectionClosedException("connection closed by device")
                for ((header, payload) in reader.feed(chunk)) {
                    handleFrame(header, payload)
                }
            }
        } catch (e: Throwable) {
            shutdown(e)
        }
    }

    /** Start the read loop. Must be called once before any exchange. */
    fun start() {
        readLoop.start()
    }

    /**
     * Enable payload encryption. Applied to every subsequent frame with a
     * non-empty payload (zero-length payloads stay plaintext, like pyatv).
     */
    fun enableEncryption(sessionCipher: SessionCipher) {
        cipher = sessionCipher
    }

    /**
     * Send an auth frame (PS_x / PV_x) and wait for the reply. Replies to both
     * xx_Start and xx_Next arrive as xx_Next, so that frame type is the
     * correlation key (auth exchanges are never concurrent).
     */
    suspend fun exchangeAuth(
        frameType: FrameType,
        data: Map<String, Any?>,
        timeoutMs: Long = defaultTimeoutMs,
    ): Map<Any?, Any?> {
        val identifier: Any = when (frameType) {
            FrameType.PS_Start -> FrameType.PS_Next
            FrameType.PV_Start -> FrameType.PV_Next
            else -> frameType
        }
        return exchange(frameType, data, identifier, timeoutMs)
    }

    /** Send an OPACK request and wait for the response with the same XID. */
    suspend fun exchangeOpack(
        frameType: FrameType,
        data: Map<String, Any?>,
        timeoutMs: Long = defaultTimeoutMs,
    ): Map<Any?, Any?> {
        val withXid = LinkedHashMap<String, Any?>(data)
        val identifier: Long = synchronized(lock) {
            val id = xid
            withXid["_x"] = id
            xid += 1
            id
        }
        return exchange(frameType, withXid, identifier, timeoutMs)
    }

    /** Send an OPACK message without waiting for a response (events). */
    suspend fun sendOpack(frameType: FrameType, data: Map<String, Any?>) {
        val withXid = LinkedHashMap<String, Any?>(data)
        if ("_x" !in withXid) {
            synchronized(lock) {
                withXid["_x"] = xid
                xid += 1
            }
        }
        sendFrame(frameType, Opack.pack(withXid))
    }

    private suspend fun exchange(
        frameType: FrameType,
        data: Map<String, Any?>,
        identifier: Any,
        timeoutMs: Long,
    ): Map<Any?, Any?> {
        val deferred = CompletableDeferred<Map<Any?, Any?>>()
        synchronized(lock) {
            closeCause?.let { throw CompanionConnectionClosedException("connection closed", it) }
            waiters[identifier] = deferred
        }
        try {
            sendFrame(frameType, Opack.pack(data))
            val response = withTimeout(timeoutMs) { deferred.await() }
            val errorMessage = response["_em"]
            if (errorMessage != null) {
                throw CompanionCommandException(
                    "command failed: $errorMessage",
                    response["_ec"] as? Long,
                )
            }
            return response
        } finally {
            synchronized(lock) { waiters.remove(identifier) }
        }
    }

    private suspend fun sendFrame(frameType: FrameType, payload: ByteArray) {
        val activeCipher = cipher
        val encrypted = activeCipher != null && payload.isNotEmpty()
        val payloadLength = payload.size + if (encrypted) AUTH_TAG_LENGTH else 0
        val header = frameHeader(frameType, payloadLength)
        writeMutex.withLock {
            // Encrypt under the write lock: the nonce counter must match the
            // order in which frames hit the wire.
            val body = if (encrypted) activeCipher!!.encrypt(payload, header) else payload
            transport.write(header + body)
        }
    }

    private fun handleFrame(header: ByteArray, payload: ByteArray) {
        val frameType = FrameType.fromCode(header[0].toInt() and 0xFF) ?: return
        val activeCipher = cipher
        val plain = if (activeCipher != null && payload.isNotEmpty()) {
            // Any AEAD failure is fatal: sequence-number nonces mean we can
            // never resynchronize — the exception thrown here reaches the
            // read loop, which closes the connection (reconnect + re-verify).
            activeCipher.decrypt(payload, header)
        } else {
            payload
        }

        when (frameType) {
            FrameType.PS_Start, FrameType.PS_Next, FrameType.PV_Start, FrameType.PV_Next -> {
                val message = unpackDict(plain) ?: return
                completeWaiter(frameType, message)
            }
            FrameType.U_OPACK, FrameType.E_OPACK, FrameType.P_OPACK -> {
                val message = unpackDict(plain) ?: return
                when (message["_t"]) {
                    MessageType.EVENT -> {
                        val name = message["_i"] as? String ?: return
                        @Suppress("UNCHECKED_CAST")
                        val content = (message["_c"] as? Map<Any?, Any?>) ?: emptyMap()
                        _events.tryEmit(CompanionEvent(name, content))
                    }
                    MessageType.RESPONSE -> {
                        val responseXid = message["_x"] as? Long ?: return
                        completeWaiter(responseXid, message)
                    }
                    // Requests from the device are not supported (like pyatv)
                }
            }
            else -> Unit // unsupported frame type, ignore
        }
    }

    private fun unpackDict(payload: ByteArray): Map<Any?, Any?>? {
        val (value, _) = Opack.unpack(payload)
        @Suppress("UNCHECKED_CAST")
        return value as? Map<Any?, Any?>
    }

    private fun completeWaiter(identifier: Any, message: Map<Any?, Any?>) {
        val deferred = synchronized(lock) { waiters.remove(identifier) }
        deferred?.complete(message)
    }

    private fun shutdown(cause: Throwable) {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            closeCause = cause
            val list = waiters.values.toList()
            waiters.clear()
            list
        }
        for (waiter in pending) {
            waiter.completeExceptionally(
                CompanionConnectionClosedException("connection closed", cause),
            )
        }
        transport.close()
    }

    /** Close the connection and cancel all pending exchanges. */
    fun close() {
        shutdown(CompanionConnectionClosedException("closed by client"))
        scope.cancel()
    }
}
