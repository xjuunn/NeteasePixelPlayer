package com.theveloper.pixelplay.data.netease.lyric

import com.theveloper.pixelplay.data.network.netease.NeteaseApiService
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official lyrics via [NeteaseApiService] (EAPI `/song/lyric/v1`). Tried first to preserve the
 * existing logged-in behaviour; Qijieya and the gateway act as fallbacks.
 */
@Singleton
class OfficialSongLyricProvider @Inject constructor(
    private val api: NeteaseApiService
) : SongLyricProvider {

    override val id: String = "official"

    override val priority: Int = 0

    override suspend fun fetch(songId: Long): String? {
        val raw = runCatching { api.getLyrics(songId) }.getOrNull() ?: return null
        val lyric = runCatching {
            JSONObject(raw).optJSONObject("lrc")?.optString("lyric", "").orEmpty()
        }.getOrNull().orEmpty()
        return lyric.ifBlank { null }
    }
}
