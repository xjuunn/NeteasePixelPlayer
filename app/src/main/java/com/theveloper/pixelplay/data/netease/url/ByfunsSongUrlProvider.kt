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

    private val levels = listOf("standard", "higher", "exhigh")

    override suspend fun resolve(songId: Long, quality: String): List<String> {
        val candidates = LinkedHashSet<String>()
        for (level in levels) {
            val url = "https://api.byfuns.top/1/?id=$songId&level=$level"
            val body = runCatching { getText(url) }.getOrNull()?.trim().orEmpty()
            if (body.startsWith("http")) {
                candidates.add(body)
            } else if (body.isNotEmpty()) {
                Timber.d("ByfunsSongUrlProvider: unexpected body for songId=$songId level=$level")
            }
        }
        return candidates.toList()
    }
}
