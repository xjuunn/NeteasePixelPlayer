package com.theveloper.pixelplay.data.netease.url

import com.theveloper.pixelplay.data.network.netease.NeteaseCloudApiService
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-resort provider: A17 `/song/url/v1` on the public gateway (`mycelis.dpdns.org`).
 *
 * This endpoint is restricted and returns `code=404` with a null URL for copyright/VIP songs, so it
 * is only reached after every other provider has been exhausted. `maxAttempts` is 1 to keep calls
 * to the restricted gateway to a minimum.
 *
 * Not wired into [SongUrlResolver]: the chain stops at Qijieya and then falls back to the official
 * link as returned (preview included). Kept for reference in case the gateway is needed again.
 */
@Singleton
class MycelisSongUrlProvider @Inject constructor(
    private val cloudApi: NeteaseCloudApiService
) : SongUrlProvider {

    override val id: String = "mycelis"

    override val priority: Int = 3

    override val maxAttempts: Int = 1

    private val levels = listOf("standard", "higher", "exhigh", "lossless")

    override suspend fun resolve(songId: Long, quality: String): List<String> {
        for (level in levels) {
            val raw = runCatching { cloudApi.songUrl(songId, level) }.getOrNull() ?: continue
            val url = runCatching {
                JSONObject(raw)
                    .optJSONArray("data")
                    ?.optJSONObject(0)
                    ?.optString("url", "")
                    .orEmpty()
            }.getOrNull().orEmpty()

            if (url.startsWith("http")) {
                Timber.d("MycelisSongUrlProvider: resolved songId=$songId level=$level")
                return listOf(url)
            }
        }
        return emptyList()
    }
}
