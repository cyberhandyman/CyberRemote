package dev.companionremote.protocol.client

import dev.companionremote.protocol.companion.CompanionCommandException
import dev.companionremote.protocol.companion.CompanionConnection
import dev.companionremote.protocol.companion.CompanionEvent
import dev.companionremote.protocol.companion.FrameType
import dev.companionremote.protocol.companion.MessageType
import dev.companionremote.protocol.crypto.CompanionSessionCipher
import dev.companionremote.protocol.crypto.Crypto
import dev.companionremote.protocol.hap.HapCredentials
import dev.companionremote.protocol.hap.PairVerify
import dev.companionremote.protocol.hap.toHex
import dev.companionremote.protocol.plist.KeyedArchiver
import dev.companionremote.protocol.plist.RtiPayloads
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * High-level Companion client: pair-verify + encryption + the connect
 * sequence, then buttons / apps / power / touch / text input.
 *
 * Connect sequence ported from pyatv `protocols/companion/api.py
 * CompanionAPI.connect()`: `_systemInfo` → `_touchStart` → `_sessionStart` →
 * `TVRCSessionStart` (failure ignored) → `_tiStart` → subscribe `_iMC`.
 */
class CompanionClient(
    private val connection: CompanionConnection,
    private val credentials: HapCredentials,
    private val name: String = "Companion Remote",
) {
    /** Combined session id after `_sessionStart`: (remote << 32) | local. */
    var sessionId: Long = 0
        private set

    /** Raw `_tiStart` response content (used by text input, M6). */
    internal var textInputSession: Map<Any?, Any?>? = null

    private var touchBaseNanos: Long = System.nanoTime()
    private val subscribedEvents = mutableListOf<String>()
    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    /** Events pushed by the device. */
    val events: SharedFlow<CompanionEvent> get() = connection.events

    private val _keyboardFocus = MutableStateFlow(KeyboardFocusState.Unknown)

    // Most recent `_tiD` keyed archive from a `_tiStarted` event (or a
    // `_tiStart` response that happened to carry one). On this hardware the
    // sessionUUID lives here, NOT in the response to a `_tiStop`+`_tiStart`
    // restart (see docs/protocol-notes.md — real-device deviation from pyatv).
    @Volatile
    private var latestTiData: ByteArray? = null

    /**
     * Whether a text field is focused on the TV. Derived (like pyatv's
     * `CompanionKeyboard`) from `_tiStarted`/`_tiStopped` events and the
     * `_tiStart` response: focused ⇔ the payload contains `_tiD`.
     */
    val keyboardFocus: StateFlow<KeyboardFocusState> = _keyboardFocus

    /** Verify credentials, enable encryption and run the connect sequence. */
    suspend fun connect() {
        connection.start()
        val keys = PairVerify(connection, credentials).verify()
        connection.enableEncryption(CompanionSessionCipher(keys.outputKey, keys.inputKey))

        scope.launch {
            events.collect { event ->
                if (event.name == "_tiStarted" || event.name == "_tiStopped") {
                    onFocusEvent(event.content)
                }
            }
        }

        systemInfo()
        touchStart()
        sessionStart()
        tvRcSessionStart()
        textInputStart()
        subscribeEvent("_iMC")
    }

    /** Graceful teardown mirroring pyatv `CompanionAPI.disconnect`. */
    suspend fun disconnect() {
        runCatching {
            for (event in subscribedEvents.toList()) unsubscribeEvent(event)
            sendRequest("_sessionStop", mapOf("_srvT" to SERVICE_TYPE, "_sid" to sessionId))
            sendRequest("_touchStop", mapOf("_i" to 1L))
            sendRequest("_tiStop", emptyMap())
        }
        connection.close()
        scope.cancel()
    }

    fun close() {
        connection.close()
        scope.cancel()
    }

    // Connect sequence steps

    private suspend fun systemInfo() {
        // Semi-random values mirroring pyatv `system_info()`; `_i` must be
        // non-null or the device stops pushing (TV)SystemStatus events.
        val stableId = Crypto.sha512(credentials.clientId).toHex()
        sendRequest(
            "_systemInfo",
            linkedMapOf(
                "_bf" to 0L,
                "_cf" to 512L,
                "_clFl" to 128L,
                "_i" to stableId.substring(0, 12),
                "_idsID" to credentials.clientId,
                "_pubID" to macLike(stableId),
                "_sf" to 256L,
                "_sv" to "170.18",
                "model" to "iPhone10,6",
                "name" to name,
            ),
        )
    }

    private suspend fun touchStart() {
        touchBaseNanos = System.nanoTime()
        sendRequest(
            "_touchStart",
            linkedMapOf("_height" to 1000.0, "_tFl" to 0L, "_width" to 1000.0),
        )
    }

    private suspend fun sessionStart() {
        val localSid = Random.nextLong(0, 1L shl 32)
        val response = sendRequest(
            "_sessionStart",
            linkedMapOf("_srvT" to SERVICE_TYPE, "_sid" to localSid),
        )
        val content = contentOf(response)
        val remoteSid = content["_sid"] as? Long
            ?: throw CompanionCommandException("no _sid in _sessionStart response", null)
        sessionId = (remoteSid shl 32) or localSid
    }

    private suspend fun tvRcSessionStart() {
        // Older tvOS errors on this — ignore (pyatv `_tv_rc_session_start`)
        runCatching {
            sendRequest("TVRCSessionStart", mapOf("ProtocolVersionKey" to "1.2"))
        }
    }

    internal suspend fun textInputStart(): Map<Any?, Any?> {
        val response = sendRequest("_tiStart", emptyMap())
        val content = contentOf(response)
        textInputSession = content
        // A `_tiStart` response only carries `_tiD` if the field was already
        // focused when the session opened; when it does, cache it. An empty
        // `_c` must NOT clear focus — on this hardware it is the normal
        // response and the real state arrives via `_tiStarted` events.
        cacheTiDataIfPresent(content)
        return content
    }

    internal suspend fun textInputStop() {
        sendRequest("_tiStop", emptyMap())
    }

    /** Handle a `_tiStarted`/`_tiStopped` event (authoritative focus source). */
    private fun onFocusEvent(content: Map<Any?, Any?>) {
        val tiData = content["_tiD"] as? ByteArray
        if (tiData != null) {
            latestTiData = tiData
            _keyboardFocus.value = KeyboardFocusState.Focused
        } else {
            latestTiData = null
            _keyboardFocus.value = KeyboardFocusState.Unfocused
        }
    }

    private fun cacheTiDataIfPresent(content: Map<Any?, Any?>) {
        (content["_tiD"] as? ByteArray)?.let {
            latestTiData = it
            _keyboardFocus.value = KeyboardFocusState.Focused
        }
    }

    /** Poll for a cached `_tiD` (from response or `_tiStarted` event). */
    private suspend fun waitForTiData(timeoutMs: Long): ByteArray? {
        var waited = 0L
        while (latestTiData == null && waited < timeoutMs) {
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return latestTiData
    }

    // Text input (RTI), ported from pyatv `api.py text_input_command`

    /** Current text of the focused field, or null if nothing is focused. */
    suspend fun textGet(): String? = textInputCommand("", clearPreviousInput = false)

    /** Replace the focused field's contents with [text]. */
    suspend fun textSet(text: String): String? = textInputCommand(text, clearPreviousInput = true)

    /** Insert [text] at the cursor. */
    suspend fun textAppend(text: String): String? = textInputCommand(text, clearPreviousInput = false)

    /** Clear the focused field. */
    suspend fun textClear(): String? = textInputCommand("", clearPreviousInput = true)

    /**
     * Send a text-input command against the focused field. Uses the `_tiD`
     * cached from the latest `_tiStarted` event (the session UUID lives
     * there on real hardware); if none is cached yet, tries one `_tiStart`
     * in case the field was focused before we connected. Returns the
     * resulting text, or null when no field is focused (a graceful no-op).
     *
     * NB: unlike pyatv this does NOT `_tiStop`+`_tiStart` on every call —
     * that tears down the RTI session on this hardware and loses the
     * sessionUUID (docs/protocol-notes.md).
     */
    suspend fun textInputCommand(text: String, clearPreviousInput: Boolean): String? {
        // The session UUID comes from the `_tiD` cached out of a `_tiStarted`
        // event (the authoritative source on tvOS). If we have not seen one
        // yet, try a `_tiStart` in case the field was focused before we
        // connected (pyatv-style devices answer with `_tiD`), then wait
        // briefly for either path to populate the cache.
        if (latestTiData == null) textInputStart()
        val tiData = waitForTiData(FOCUS_WAIT_MS) ?: return null

        val properties = KeyedArchiver.readArchiveProperties(
            tiData,
            listOf("sessionUUID"),
            listOf("documentState", "docSt", "contextBeforeInput"),
        )
        val sessionUuid = properties[0] as? ByteArray ?: return null
        var currentText = properties[1] as? String ?: ""

        if (clearPreviousInput) {
            sendEvent(
                "_tiC",
                mapOf("_tiV" to 1L, "_tiD" to RtiPayloads.clearTextPayload(sessionUuid)),
            )
            currentText = ""
        }
        if (text.isNotEmpty()) {
            sendEvent(
                "_tiC",
                mapOf("_tiV" to 1L, "_tiD" to RtiPayloads.inputTextPayload(sessionUuid, text)),
            )
            currentText += text
        }
        return currentText
    }

    // Events

    suspend fun subscribeEvent(event: String) {
        if (event !in subscribedEvents) {
            sendEvent("_interest", mapOf("_regEvents" to listOf(event)))
            subscribedEvents.add(event)
        }
    }

    suspend fun unsubscribeEvent(event: String) {
        if (event in subscribedEvents) {
            sendEvent("_interest", mapOf("_deregEvents" to listOf(event)))
            subscribedEvents.remove(event)
        }
    }

    // Buttons

    /** Send a single HID button state change (down or up). */
    suspend fun hidCommand(down: Boolean, command: HidCommand) {
        sendRequest(
            "_hidC",
            linkedMapOf("_hBtS" to if (down) 1L else 2L, "_hidC" to command.code),
        )
    }

    /** A full button press: down then up. */
    suspend fun pressButton(command: HidCommand) {
        hidCommand(true, command)
        hidCommand(false, command)
    }

    /** Wake the device (single up event, like pyatv `CompanionPower.turn_on`). */
    suspend fun wake() = hidCommand(false, HidCommand.Wake)

    /** Put the device to sleep (single up event). */
    suspend fun sleep() = hidCommand(false, HidCommand.Sleep)

    // Touch (`api.py hid_event` / `swipe` / `click`)

    suspend fun touchEvent(x: Long, y: Long, phase: TouchPhase) {
        val cx = x.coerceIn(0, 1000)
        val cy = y.coerceIn(0, 1000)
        sendEvent(
            "_hidT",
            linkedMapOf(
                "_ns" to (System.nanoTime() - touchBaseNanos),
                "_tFg" to 1L,
                "_cx" to cx,
                "_tPh" to phase.code,
                "_cy" to cy,
            ),
        )
    }

    /**
     * Swipe from start to end coordinates ([0,1000]) over [durationMs],
     * emitting Hold events every ~16 ms (pyatv `CompanionAPI.swipe`).
     */
    suspend fun swipe(startX: Long, startY: Long, endX: Long, endY: Long, durationMs: Long) {
        val endTime = System.nanoTime() + durationMs * 1_000_000
        var x = startX.toDouble()
        var y = startY.toDouble()
        touchEvent(startX, startY, TouchPhase.Press)
        var now = System.nanoTime()
        while (now < endTime) {
            x += (endX - x) * TOUCH_DELAY_NS / (endTime - now)
            y += (endY - y) * TOUCH_DELAY_NS / (endTime - now)
            touchEvent(x.toLong(), y.toLong(), TouchPhase.Hold)
            delay(TOUCH_DELAY_MS)
            now = System.nanoTime()
        }
        touchEvent(endX, endY, TouchPhase.Release)
    }

    /** Touchpad tap = select press/release + a Click touch event. */
    suspend fun tap() {
        hidCommand(true, HidCommand.Select)
        delay(20)
        hidCommand(false, HidCommand.Select)
        touchEvent(1000, 1000, TouchPhase.Click)
    }

    // Apps (`api.py app_list` / `launch_app`)

    /** Launchable apps: bundle id → display name. */
    suspend fun appList(): Map<String, String> {
        val response = sendRequest("FetchLaunchableApplicationsEvent", emptyMap())
        return contentOf(response).entries
            .mapNotNull { (k, v) ->
                val bundle = k as? String ?: return@mapNotNull null
                val appName = v as? String ?: return@mapNotNull null
                bundle to appName
            }
            .toMap()
    }

    /** Launch an app by bundle id (requires the `_sessionStart` session). */
    suspend fun launchApp(bundleId: String) {
        sendRequest("_launchApp", mapOf("_bundleID" to bundleId))
    }

    // Power / media control

    /** Fetch the attention (power) state; null if unsupported by this tvOS. */
    suspend fun fetchAttentionState(): SystemStatus? = try {
        val response = sendRequest("FetchAttentionState", emptyMap())
        (contentOf(response)["state"] as? Long)?.let { SystemStatus.fromCode(it) }
    } catch (e: CompanionCommandException) {
        null // newer tvOS drops the handler; rely on SystemStatus events
    }

    suspend fun mediaControl(command: MediaControlCommand, args: Map<String, Any?> = emptyMap()): Map<Any?, Any?> {
        val response = sendRequest("_mcc", linkedMapOf<String, Any?>("_mcc" to command.code) + args)
        return contentOf(response)
    }

    // Plumbing

    internal suspend fun sendRequest(identifier: String, content: Map<String, Any?>): Map<Any?, Any?> =
        connection.exchangeOpack(
            FrameType.E_OPACK,
            mapOf("_i" to identifier, "_t" to MessageType.REQUEST, "_c" to content),
        )

    internal suspend fun sendEvent(identifier: String, content: Map<String, Any?>) {
        connection.sendOpack(
            FrameType.E_OPACK,
            mapOf("_i" to identifier, "_t" to MessageType.EVENT, "_c" to content),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun contentOf(response: Map<Any?, Any?>): Map<Any?, Any?> =
        (response["_c"] as? Map<Any?, Any?>) ?: emptyMap()

    private fun macLike(hex: String): String =
        (0 until 6).joinToString(":") { hex.substring(it * 2, it * 2 + 2).uppercase() }

    companion object {
        private const val SERVICE_TYPE = "com.apple.tvremoteservices"
        private const val TOUCH_DELAY_MS = 16L
        private const val TOUCH_DELAY_NS = 16.0 * 1_000_000
        private const val FOCUS_WAIT_MS = 1500L
        private const val POLL_INTERVAL_MS = 30L
    }
}
