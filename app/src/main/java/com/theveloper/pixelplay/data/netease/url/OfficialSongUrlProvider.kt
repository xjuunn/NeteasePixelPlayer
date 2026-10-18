package com.theveloper.pixelplay.data.netease.url

import com.theveloper.pixelplay.data.network.netease.NeteaseApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highest-priority provider: the official `music.163.com` endpoint via [NeteaseApiService].
 *
 * Uses the logged-in session (when available) and degrades through quality levels, so a single
 * [resolve] call already covers the quality fallback. It is still subject to a global request
 * interval to avoid tripping upstream rate limits.
 *
 * A song this account may not play in full (VIP / paid album) is rejected here so the resolver can
 * move on to the third-party sources. That verdict is a property of the song, not of the quality
 * level, so the level loop stops at the first restricted result and the song is remembered for
 * [RESTRICTED_CACHE_TTL_MS] — without both, every play of a VIP song paid for a full round of
 * throttled requests (four levels at 1100 ms apart) before falling back.
 *
 * The rejected link itself is kept for [PREVIEW_CACHE_TTL_MS] and handed back by
 * [resolvePreview]: that is the last-resort fallback, restoring the original behaviour of playing
 * whatever the official API returns rather than failing outright.
 */
@Singleton
class OfficialSongUrlProvider @Inject constructor(
    private val api: NeteaseApiService
) : SongUrlProvider {

    override val id: String = "official"

    override val priority: Int = 0

    // A single resolve() already walks all quality levels, so extra retries are unnecessary.
    override val maxAttempts: Int = 1

    private companion object {
        const val RESTRICTED_CACHE_TTL_MS = 10L * 60 * 1000
        const val PREVIEW_CACHE_TTL_MS = 3L * 60 * 1000
        const val MIN_REQUEST_INTERVAL_MS = 1100L
    }

    private val requestMutex = Mutex()

    @Volatile
    private var lastRequestAtMs = 0L

    /** songId -> epoch millis until which the song is known to be restricted for this account. */
    private val restrictedUntilMs = ConcurrentHashMap<Long, Long>()

    private class PreviewUrl(val url: String, val expiresAtMs: Long)

    /** songId -> last restricted link, kept as the last-resort playback fallback. */
    private val previewUrls = ConcurrentHashMap<Long, PreviewUrl>()

    override suspend fun resolve(songId: Long, quality: String): List<String> {
        if (isKnownRestricted(songId)) {
            Timber.d("OfficialSongUrlProvider: songId=$songId known restricted, skipping official source")
            return emptyList()
        }

        for (level in levelsWith(quality)) {
            val (url, restricted) = fetch(songId, level) ?: continue
            if (restricted) {
                markRestricted(songId, url)
                Timber.d(
                    "OfficialSongUrlProvider: free trial/restricted clip for songId=$songId level=$level, falling back"
                )
                return emptyList()
            }
            Timber.d("OfficialSongUrlProvider: resolved songId=$songId level=$level")
            return listOf(url)
        }
        return emptyList()
    }

    /**
     * Last-resort fallback: return whatever the official API hands back, restricted preview
     * included. Used only after every third-party source has failed, because a short clip beats a
     * failed playback. A fresh URL is requested when the cached one is about to expire.
     */
    suspend fun resolvePreview(songId: Long, quality: String): List<String> {
        previewUrls[songId]?.let { cached ->
            if (System.currentTimeMillis() < cached.expiresAtMs) {
                return listOf(cached.url)
            }
            previewUrls.remove(songId, cached)
        }

        for (level in levelsWith(quality)) {
            val (url, restricted) = fetch(songId, level) ?: continue
            if (restricted) {
                previewUrls[songId] = PreviewUrl(url, System.currentTimeMillis() + PREVIEW_CACHE_TTL_MS)
            }
            Timber.d("OfficialSongUrlProvider: preview fallback songId=$songId level=$level")
            return listOf(url)
        }
        return emptyList()
    }

    /** Drops the per-song verdicts, e.g. after the account changes. */
    fun clearCaches() {
        restrictedUntilMs.clear()
        previewUrls.clear()
    }

    private fun isKnownRestricted(songId: Long): Boolean {
        val until = restrictedUntilMs[songId] ?: return false
        if (System.currentTimeMillis() < until) return true
        restrictedUntilMs.remove(songId, until)
        return false
    }

    private fun markRestricted(songId: Long, url: String) {
        restrictedUntilMs[songId] = System.currentTimeMillis() + RESTRICTED_CACHE_TTL_MS
        previewUrls[songId] = PreviewUrl(url, System.currentTimeMillis() + PREVIEW_CACHE_TTL_MS)
    }

    private fun levelsWith(quality: String): List<String> =
        (listOf(quality) + AUDIO_QUALITY_LEVELS).distinct()

    /** Returns the link plus whether it is a restricted (preview) result, or null if unusable. */
    private suspend fun fetch(songId: Long, level: String): Pair<String, Boolean>? {
        val raw = requestThrottled { api.getSongDownloadUrl(songId, level) }
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 200) return null

        val urlObj = root.optJSONArray("data")?.optJSONObject(0) ?: return null
        val url = urlObj.optString("url", "").orEmpty()
        if (!url.startsWith("http")) return null

        return url to isFreeTrialOrRestricted(urlObj)
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
            val waitMs = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAtMs)
            if (waitMs > 0) delay(waitMs)
            lastRequestAtMs = System.currentTimeMillis()
        }
        return block()
    }
}
