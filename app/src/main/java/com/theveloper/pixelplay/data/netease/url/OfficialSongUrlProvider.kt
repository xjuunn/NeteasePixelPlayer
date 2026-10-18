package com.theveloper.pixelplay.data.netease.url

import com.theveloper.pixelplay.data.network.netease.NeteaseApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highest-priority provider: the official `music.163.com` endpoint via [NeteaseApiService].
 *
 * Uses the logged-in session (when available) and degrades through quality levels, so a single
 * [resolve] call already covers the quality fallback. It is still subject to a global request
 * interval to avoid tripping upstream rate limits.
 */
@Singleton
class OfficialSongUrlProvider @Inject constructor(
    private val api: NeteaseApiService
) : SongUrlProvider {

    override val id: String = "official"

    override val priority: Int = 0

    // A single resolve() already walks all quality levels, so extra retries are unnecessary.
    override val maxAttempts: Int = 1

    private val requestMutex = Mutex()

    @Volatile
    private var lastRequestAtMs = 0L

    private val minRequestIntervalMs = 1100L

    override suspend fun resolve(songId: Long, quality: String): List<String> {
        val levels = (listOf(quality) + AUDIO_QUALITY_LEVELS).distinct()
        for (level in levels) {
            val raw = requestThrottled { api.getSongDownloadUrl(songId, level) }
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            if (root.optInt("code", -1) != 200) continue

            val urlObj = root.optJSONArray("data")?.optJSONObject(0) ?: continue
            val url = urlObj.optString("url", "").orEmpty()
            if (!url.startsWith("http")) continue

            if (isFreeTrialOrRestricted(urlObj)) {
                // No full-play privilege (VIP or paid album): the URL is only a short preview.
                // Skip it so the resolver falls back to third-party sources for the full track.
                Timber.d(
                    "OfficialSongUrlProvider: free trial/restricted clip for songId=$songId level=$level, falling back"
                )
                continue
            }

            Timber.d("OfficialSongUrlProvider: resolved songId=$songId level=$level")
            return listOf(url)
        }
        return emptyList()
    }

    /**
     * Detect a free-trial or otherwise restricted result. The official API returns a playable
     * (but truncated) preview URL together with these markers, so accepting `url` alone would
     * silently limit VIP songs to the preview length.
     */
    private fun isFreeTrialOrRestricted(urlObj: JSONObject): Boolean {
        if (urlObj.optInt("code", 200) != 200) return true

        val freeTrialInfo = urlObj.opt("freeTrialInfo")
        if (freeTrialInfo != null && freeTrialInfo != JSONObject.NULL) return true

        val privilege = urlObj.optJSONObject("freeTrialPrivilege")
        if (privilege != null) {
            if (privilege.optBoolean("resConsumable", false)) return true
            if (privilege.optInt("cannotListenReason", 0) != 0) return true
        }
        return false
    }

    private suspend fun requestThrottled(block: suspend () -> String): String {
        requestMutex.withLock {
            val waitMs = minRequestIntervalMs - (System.currentTimeMillis() - lastRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastRequestAtMs = System.currentTimeMillis()
        }
        return block()
    }
}
