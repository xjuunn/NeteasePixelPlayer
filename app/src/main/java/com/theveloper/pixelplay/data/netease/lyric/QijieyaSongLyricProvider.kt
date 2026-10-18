package com.theveloper.pixelplay.data.netease.lyric

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B2 Qijieya provider (`https://api.qijieya.cn/meting/?type=lrc`).
 *
 * Returns plain-text LRC (no JSON). The body must look like LRC (contain a `[` timestamp marker)
 * before it is accepted.
 */
@Singleton
class QijieyaSongLyricProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : SongLyricProvider {

    override val id: String = "qijieya"

    override val priority: Int = 1

    override suspend fun fetch(songId: Long): String? = withContext(Dispatchers.IO) {
        val url = "https://api.qijieya.cn/meting/?type=lrc&id=$songId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/plain,*/*")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.string().trim()
                if (body.contains('[')) body else null
            }
        } catch (e: Exception) {
            Timber.d("QijieyaSongLyricProvider: fetch failed for songId=$songId: ${e.message}")
            null
        }
    }

    private companion object {
        const val USER_AGENT = "PixelPlayer/1.0 (Android; Music Player)"
    }
}
