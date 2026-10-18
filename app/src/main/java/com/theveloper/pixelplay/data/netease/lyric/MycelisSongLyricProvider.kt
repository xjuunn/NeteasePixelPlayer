package com.theveloper.pixelplay.data.netease.lyric

import com.theveloper.pixelplay.data.network.netease.NeteaseCloudApiService
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A18 provider: `/lyric` on the public gateway. Fallback used after the official and Qijieya
 * sources fail.
 */
@Singleton
class MycelisSongLyricProvider @Inject constructor(
    private val cloudApi: NeteaseCloudApiService
) : SongLyricProvider {

    override val id: String = "mycelis"

    override val priority: Int = 2

    override suspend fun fetch(songId: Long): String? {
        val raw = runCatching { cloudApi.lyric(songId) }.getOrNull() ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 200) {
            Timber.d("MycelisSongLyricProvider: code=${root.optInt("code", -1)} for songId=$songId")
            return null
        }
        return root.optJSONObject("lrc")?.optString("lyric", "").orEmpty().ifBlank { null }
    }
}
