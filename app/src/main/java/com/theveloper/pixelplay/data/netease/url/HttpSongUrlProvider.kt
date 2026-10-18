package com.theveloper.pixelplay.data.netease.url

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Base class for HTTP-based [SongUrlProvider]s.
 *
 * Provides small GET helpers so each provider only declares its endpoint and response shape.
 * Providers using the shared gateway client (e.g. Mycelis) may implement [SongUrlProvider] directly.
 */
abstract class HttpSongUrlProvider(
    protected val okHttpClient: OkHttpClient
) : SongUrlProvider {

    companion object {
        const val USER_AGENT = "PixelPlayer/1.0 (Android; Music Player)"
    }

    /** GET a plain-text (or any) response body. */
    protected suspend fun getText(
        url: String,
        accept: String = "text/plain,*/*"
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response -> response.body.string() }
    }

    /**
     * Probe only the response `Content-Type` of [url], reading a single leading byte to keep the
     * request cheap. Returns null when the request fails.
     */
    protected suspend fun probeContentType(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response -> response.header("Content-Type") }
    }
}
