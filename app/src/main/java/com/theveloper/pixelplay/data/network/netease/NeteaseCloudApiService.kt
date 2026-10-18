package com.theveloper.pixelplay.data.network.netease

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the public Netease gateway documented in `docs/external-apis.md` (sections A1-A18).
 *
 * This client is intentionally separate from [NeteaseApiService]:
 * - [NeteaseApiService] talks to the official `music.163.com` endpoints with a logged-in session
 *   and is used for authenticated sync/streaming.
 * - This client targets a configurable public gateway base URL and needs no login, so it serves as
 *   an anonymous metadata source and as a fallback when the official API is unavailable.
 *
 * All methods return the raw response body (JSON or plain text). Parsing stays in the repositories,
 * matching the convention used by [NeteaseApiService].
 */
@Singleton
class NeteaseCloudApiService @Inject constructor(
    baseOkHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "NeteaseCloudApi"
        private const val USER_AGENT = "PixelPlayer/1.0 (Android; Music Player)"

        /** Default gateway base URL (see docs/external-apis.md section 2.1). */
        const val DEFAULT_BASE_URL = "https://mycelis.dpdns.org"

        // A5 /cloudsearch `type` values.
        const val SEARCH_TYPE_SONG = 1
        const val SEARCH_TYPE_ARTIST = 100
        const val SEARCH_TYPE_PLAYLIST = 1000
        const val SEARCH_TYPE_USER = 1002
        const val SEARCH_TYPE_RADIO = 1009

        /** A2 pagination: a page shorter than the requested limit is the last page. */
        fun isLastPage(fetchedCount: Int, limit: Int): Boolean = fetchedCount < limit
    }

    private val client: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val reachabilityClient: OkHttpClient = client.newBuilder()
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Active gateway base URL. Change it with [setBaseUrl]. */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    /** Set the gateway base URL, trimming whitespace, paired quotes and a trailing slash. */
    fun setBaseUrl(raw: String) {
        baseUrl = normalizeBaseUrl(raw)
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trim('"').trim()
        return trimmed.trimEnd('/').ifBlank { DEFAULT_BASE_URL }
    }

    // ─── Core request ──────────────────────────────────────────────────

    @Throws(IOException::class)
    suspend fun getJson(path: String, params: Map<String, Any?> = emptyMap()): String =
        get(path, params, acceptJson = true)

    @Throws(IOException::class)
    suspend fun getText(path: String, params: Map<String, Any?> = emptyMap()): String =
        get(path, params, acceptJson = false)

    @Throws(IOException::class)
    private suspend fun get(
        path: String,
        params: Map<String, Any?>,
        acceptJson: Boolean
    ): String = withContext(Dispatchers.IO) {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val urlBuilder = (baseUrl + normalizedPath).toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value != null) urlBuilder.addQueryParameter(key, value.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", if (acceptJson) "application/json,text/plain,*/*" else "text/plain,*/*")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    Timber.w("$TAG: HTTP ${response.code} for ${request.url.encodedPath}")
                }
                body
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: request failed for ${request.url.encodedPath}")
            throw e
        }
    }

    // ─── A1-A3: playlists ──────────────────────────────────────────────

    /** A1 playlist detail. */
    suspend fun playlistDetail(playlistId: Long): String =
        getJson("/playlist/detail", mapOf("id" to playlistId))

    /** A2 playlist tracks, paginated. */
    suspend fun playlistTracksPage(playlistId: Long, limit: Int, offset: Int): String =
        getJson(
            "/playlist/track/all",
            mapOf("id" to playlistId, "limit" to limit, "offset" to offset)
        )

    /** A3 user playlists, paginated. */
    suspend fun userPlaylists(userId: Long, limit: Int = 100, offset: Int = 0): String =
        getJson(
            "/user/playlist",
            mapOf("uid" to userId, "limit" to limit, "offset" to offset)
        )

    // ─── A4-A5: songs and search ───────────────────────────────────────

    /** A4 song detail. `ids` supports multiple comma-separated IDs. */
    suspend fun songDetail(songIds: List<Long>): String =
        getJson("/song/detail", mapOf("ids" to songIds.joinToString(",")))

    /** A5 cloudsearch. `type` is one of the `SEARCH_TYPE_*` constants. */
    suspend fun search(keywords: String, type: Int, limit: Int, offset: Int): String =
        getJson(
            "/cloudsearch",
            mapOf("keywords" to keywords, "type" to type, "limit" to limit, "offset" to offset)
        )

    // ─── A6-A9: artists ────────────────────────────────────────────────

    /** A6 artist detail. */
    suspend fun artistDetail(artistId: Long): String =
        getJson("/artists", mapOf("id" to artistId))

    /** A7 artist songs, paginated. Use `limit=1&offset=0` to read the `total` count. */
    suspend fun artistSongs(artistId: Long, limit: Int, offset: Int): String =
        getJson(
            "/artist/songs",
            mapOf("id" to artistId, "limit" to limit, "offset" to offset)
        )

    /** A8 artist top songs. */
    suspend fun artistTopSongs(artistId: Long): String =
        getJson("/artist/top/song", mapOf("id" to artistId))

    /** A9 top artists. */
    suspend fun topArtists(limit: Int, offset: Int = 0): String =
        getJson("/top/artists", mapOf("limit" to limit, "offset" to offset))

    // ─── A10-A11: playlists discovery ──────────────────────────────────

    /** A10 hot playlist category tags. */
    suspend fun hotPlaylistCategories(): String = getJson("/playlist/hot")

    /** A11 category top playlists. */
    suspend fun topPlaylists(
        category: String,
        limit: Int,
        offset: Int = 0,
        order: String = "hot"
    ): String = getJson(
        "/top/playlist",
        mapOf("order" to order, "cat" to category, "limit" to limit, "offset" to offset)
    )

    // ─── A12-A16: podcasts (radio) ─────────────────────────────────────

    /** A12 radio detail. */
    suspend fun radioDetail(radioId: Long): String =
        getJson("/dj/detail", mapOf("rid" to radioId))

    /** A13 radio programs, paginated. */
    suspend fun radioPrograms(
        radioId: Long,
        limit: Int,
        offset: Int = 0,
        ascending: Boolean = false
    ): String = getJson(
        "/dj/program",
        mapOf("rid" to radioId, "limit" to limit, "offset" to offset, "asc" to ascending)
    )

    /** A14 single program detail. */
    suspend fun programDetail(programId: Long): String =
        getJson("/dj/program/detail", mapOf("id" to programId))

    /** A15 hot radios. */
    suspend fun hotRadios(limit: Int, offset: Int = 0): String =
        getJson("/dj/hot", mapOf("limit" to limit, "offset" to offset))

    /** A16 radio category recommendations. */
    suspend fun radioCategories(): String = getJson("/dj/category/recommend")

    // ─── A17-A18: stream URL and lyrics ────────────────────────────────

    /** A17 single song stream URL. `level` is one of standard/higher/exhigh/lossless. */
    suspend fun songUrl(songId: Long, level: String = "standard"): String =
        getJson("/song/url/v1", mapOf("id" to songId, "level" to level))

    /** A18 lyrics. */
    suspend fun lyric(songId: Long): String =
        getJson("/lyric", mapOf("id" to songId))

    // ─── C3: reachability ──────────────────────────────────────────────

    /** C3 gateway reachability probe. Any HTTP response counts as reachable. */
    suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + "/")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        try {
            reachabilityClient.newCall(request).execute().use { true }
        } catch (e: Exception) {
            Timber.d("$TAG: reachability check failed: ${e.message}")
            false
        }
    }
}
