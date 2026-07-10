package dev.companionremote.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A newer release available on GitHub. */
data class UpdateInfo(
    val versionName: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Checks GitHub Releases for a newer CyberRemote APK, downloads it and hands
 * it to the system installer. This is an optional, user-triggered network
 * feature (Settings → Check for updates); it only ever talks to GitHub's
 * public releases API — no accounts, no telemetry.
 */
object AppUpdater {

    // Change this if the repository ever moves.
    private const val REPO = "cyberhandyman/CyberRemote"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** The public releases page, for a "view on GitHub" fallback. */
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    /**
     * Returns the latest release if it is strictly newer than [currentVersion]
     * and ships an APK asset; null when up to date, unreachable or malformed.
     */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGetJson(LATEST_URL) ?: return@withContext null
        val tag = json.optString("tag_name").ifBlank { return@withContext null }
        val latest = normalizeVersion(tag)
        if (compareVersions(latest, normalizeVersion(currentVersion)) <= 0) return@withContext null

        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url")
                size = a.optLong("size")
                break
            }
        }
        val url = apkUrl?.takeIf { it.isNotBlank() } ?: return@withContext null

        UpdateInfo(
            versionName = latest,
            tag = tag,
            notes = json.optString("body").trim(),
            apkUrl = url,
            sizeBytes = size,
        )
    }

    /**
     * Downloads [info]'s APK into the cache, reporting fractional progress
     * (0..1, or -1 when the total size is unknown). Returns the file.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // A fresh file each time; clear stale downloads first.
        dir.listFiles()?.forEach { it.delete() }
        val dest = File(dir, "CyberRemote-${info.tag}.apk")

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 20000
            setRequestProperty("User-Agent", "CyberRemote")
        }
        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val total = if (info.sizeBytes > 0) info.sizeBytes else conn.contentLengthLong
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                onProgress(if (total > 0) 0f else -1f)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
                onProgress(1f)
            }
        }
        dest
    }

    /** Launches the system package installer for a downloaded APK [file]. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    // --- helpers ---

    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /** Compare dotted numeric versions; non-numeric parts sort as 0. */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-')
        val pb = b.split('.', '-')
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }

    private fun httpGetJson(url: String): JSONObject? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
            // GitHub's API rejects requests without a User-Agent.
            setRequestProperty("User-Agent", "CyberRemote")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        conn.inputStream.use { JSONObject(it.readBytes().decodeToString()) }
    }.getOrNull()
}
