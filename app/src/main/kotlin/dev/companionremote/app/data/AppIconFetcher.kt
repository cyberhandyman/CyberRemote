package dev.companionremote.app.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Fetches real App Store artwork for a tvOS app, **only when the user has
 * opted in** (Settings → Fetch real app icons). This is the app's one and
 * only optional deviation from "no network except the Apple TV": it calls
 * Apple's public iTunes lookup/search API. Results are cached on disk so the
 * network is hit at most once per app.
 */
object AppIconFetcher {

    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "app-icons").apply { mkdirs() }

    private fun safeName(bundleId: String): String = bundleId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /**
     * Returns the artwork for [bundleId], or null if it can't be resolved.
     *
     * Matching is done **only by bundle ID** (which is unique) — never by app
     * name, because searching by a localized name like "音乐" or "唱歌" returns
     * wrong apps (Spotify, random karaoke apps, …). Apple's own system apps
     * (`com.apple.*`) aren't in the third-party iTunes catalog, so they always
     * fall back to the generated tile rather than a bad guess.
     */
    suspend fun fetch(context: Context, bundleId: String, name: String): ImageBitmap? {
        if (bundleId.startsWith("com.apple.")) return null
        memoryCache[bundleId]?.let { return it }

        val cacheFile = File(cacheDir(context), "${safeName(bundleId)}.png")
        if (cacheFile.exists()) {
            decodeFile(cacheFile)?.let {
                memoryCache[bundleId] = it
                return it
            }
        }

        return withContext(Dispatchers.IO) {
            val artworkUrl = lookupArtworkUrl(bundleId) ?: return@withContext null
            val bytes = download(artworkUrl) ?: return@withContext null
            runCatching { cacheFile.writeBytes(bytes) }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            bitmap.asImageBitmap().also { memoryCache[bundleId] = it }
        }
    }

    private fun decodeFile(file: File): ImageBitmap? =
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()

    private fun lookupArtworkUrl(bundleId: String): String? {
        val encoded = URLEncoder.encode(bundleId, "UTF-8")
        // Try tvOS artwork first, then any platform for the same bundle ID.
        for (entity in listOf("tvSoftware", "software")) {
            val json = httpGetJson("https://itunes.apple.com/lookup?bundleId=$encoded&entity=$entity") ?: continue
            firstArtwork(json)?.let { return it }
        }
        return null
    }

    private fun firstArtwork(json: JSONObject): String? {
        val results = json.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)
        return first.optString("artworkUrl512").ifEmpty {
            first.optString("artworkUrl100").ifEmpty { null }
        }
    }

    private fun httpGetJson(url: String): JSONObject? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }
        conn.inputStream.use { JSONObject(it.readBytes().decodeToString()) }
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 8000
        }
        conn.inputStream.use { it.readBytes() }
    }.getOrNull()
}
