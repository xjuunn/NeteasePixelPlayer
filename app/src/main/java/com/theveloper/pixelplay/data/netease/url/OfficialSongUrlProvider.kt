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
        val levels = linkedSetOf(quality).apply {
            addAll(AUDIO_QUALITY_LEVELS)
        }
        for (level in levels) {
            val raw = requestThrottled { api.getSongDownloadUrl(songId, level) }
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            if (root.optInt("code", -1) != 200) continue

            val url = root.optJSONArray("data")
                ?.optJSONObject(0)
                ?.optString("url", "")
                .orEmpty()
            if (url.startsWith("http")) {
                Timber.d("OfficialSongUrlProvider: resolved songId=$songId level=$level")
                return listOf(url)
            }
        }
        return emptyList()
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
