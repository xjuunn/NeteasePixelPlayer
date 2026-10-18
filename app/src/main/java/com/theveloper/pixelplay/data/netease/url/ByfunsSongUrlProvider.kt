package com.theveloper.pixelplay.data.netease.url

import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B3 Byfuns provider (`https://api.byfuns.top/1/`).
 *
 * Returns a plain-text direct link that must start with `http`. `lossless` is intentionally
 * skipped because it returns FLAC, which the player cannot decode here (see docs/external-apis.md).
 * This source supports VIP songs.
 */
@Singleton
class ByfunsSongUrlProvider @Inject constructor(
    okHttpClient: OkHttpClient
) : HttpSongUrlProvider(okHttpClient) {

    override val id: String = "byfuns"

    override val priority: Int = 1

    // One resolve() already walks the quality levels, so retries only add latency.
    override val maxAttempts: Int = 1

    private val levels = listOf("standard", "higher", "exhigh")

    override suspend fun resolve(songId: Long, quality: String): List<String> {
        for (level in levels) {
            val url = "https://api.byfuns.top/1/?id=$songId&level=$level"
            val body = runCatching { getText(url) }.getOrNull()?.trim().orEmpty()
            if (!body.startsWith("http")) {
                if (body.isNotEmpty()) {
                    Timber.d("ByfunsSongUrlProvider: unexpected body for songId=$songId level=$level")
                }
                continue
            }
            // This source answers with any CDN host, so filter here: handing the resolver a link
            // the stream proxy is not allowed to fetch would look like a success and then fail.
            if (!isNeteaseStreamableUrl(body)) {
                Timber.d("ByfunsSongUrlProvider: host not allowed for songId=$songId level=$level")
                continue
            }
            return listOf(body)
        }
        return emptyList()
    }
}
