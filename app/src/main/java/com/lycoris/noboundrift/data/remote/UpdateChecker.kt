package com.lycoris.noboundrift.data.remote

import com.lycoris.noboundrift.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    /** The latest release tag, e.g. "v2.6.0". */
    val latestTag: String,
    /** Direct URL to the GitHub release page. */
    val releaseUrl: String,
)

/**
 * Checks GitHub Releases for a newer version of NoBoundRift.
 *
 * Calls the public GitHub API (no auth required for public repos).
 * Compares the tag's numeric part against [BuildConfig.VERSION_NAME].
 *
 * Returns [UpdateInfo] when a newer release exists, null otherwise
 * (including on any network or parse error — silently ignored so the
 * app never shows a spurious "update available" banner).
 */
@Singleton
class UpdateChecker @Inject constructor(private val okHttpClient: OkHttpClient) {

    companion object {
        private const val API_URL =
            "https://api.github.com/repos/Lycoris69/NoBoundRift/releases/latest"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                // GitHub recommends this Accept header for the v3 API
                .addHeader("Accept", "application/vnd.github+json")
                .build()

            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }

            val json = JSONObject(body)
            val tagName = json.optString("tag_name").ifEmpty { return@withContext null }
            val htmlUrl = json.optString("html_url").ifEmpty { return@withContext null }

            // Strip leading 'v' for comparison (tag "v2.6.0" → "2.6.0")
            val latestVersionStr = tagName.trimStart('v')
            val currentVersionStr = BuildConfig.VERSION_NAME

            if (isNewer(latestVersionStr, currentVersionStr)) {
                UpdateInfo(latestTag = tagName, releaseUrl = htmlUrl)
            } else {
                null
            }
        } catch (_: Exception) {
            // Network unavailable, JSON malformed, etc. — silently skip.
            null
        }
    }

    /**
     * Returns true when [candidate] is semantically newer than [current].
     * Compares major.minor.patch as integers; non-numeric segments default to 0.
     */
    private fun isNewer(candidate: String, current: String): Boolean {
        val c = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val r = current.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(c.size, r.size)
        for (i in 0 until len) {
            val cv = c.getOrElse(i) { 0 }
            val rv = r.getOrElse(i) { 0 }
            if (cv > rv) return true
            if (cv < rv) return false
        }
        return false // equal
    }
}
