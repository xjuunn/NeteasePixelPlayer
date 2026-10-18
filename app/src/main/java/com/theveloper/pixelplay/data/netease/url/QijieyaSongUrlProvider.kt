package com.theveloper.pixelplay.data.netease.url

import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B1 Qijieya provider (`https://api.qijieya.cn/meting/?type=url`).
 *
 * This endpoint returns the audio stream itself, so the request URL is the playable link. It is
 * only accepted after the response advertises an audio content type. This source supports VIP songs.
 */
@Singleton
class QijieyaSongUrlProvider @Inject constructor(
    okHttpClient: OkHttpClient
) : HttpSongUrlProvider(okHttpClient) {

    override val id: String = "qijieya"

    override val priority: Int = 2

    // A single probe is cheap but transient timeouts do happen, so allow one retry only:
    // three rounds of opening a stream connection made failed lookups painfully slow.
    override val maxAttempts: Int = 2

    override suspend fun resolve(songId: Long, quality: String): List<String> {
        val url = "https://api.qijieya.cn/meting/?type=url&id=" + songId
        val contentType = try {
            probeContentType(url)
        } catch (e: Exception) {
            Timber.d("QijieyaSongUrlProvider: probe failed for songId=$songId: ${e.message}")
            null
        }
        if (contentType.isNullOrBlank()) {
            return emptyList()
        }
        val normalized = contentType.substringBefore(';').trim()
        if (!normalized.startsWith("audio/", ignoreCase = true)) {
            Timber.d("QijieyaSongUrlProvider: non-audio content-type=$contentType for songId=$songId")
            return emptyList()
        }
        return listOf(url)
    }
}
