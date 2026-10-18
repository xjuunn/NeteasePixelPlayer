package com.theveloper.pixelplay.data.netease.url

import com.theveloper.pixelplay.data.stream.CloudStreamSecurity

/**
 * A pluggable source of playable audio URLs for a Netease song.
 *
 * [SongUrlResolver] orders implementations by [priority] (ascending) and retries each one up to
 * [maxAttempts] times before moving on, so lower-priority sources are only reached after
 * higher-priority ones are exhausted. The priority order and retry rules are defined in
 * `docs/netease-integration.md`.
 */
interface SongUrlProvider {

    /** Stable identifier used in logs and diagnostics, e.g. "official", "byfuns". */
    val id: String

    /** Lower values are tried first. */
    val priority: Int

    /** Maximum attempts for this provider before the resolver moves to the next one. */
    val maxAttempts: Int get() = 3

    /**
     * Resolve candidate playable URLs for [songId]. Returning an empty list means this provider
     * could not resolve the song and the resolver may retry.
     */
    suspend fun resolve(songId: Long, quality: String): List<String>
}

/** A successfully resolved stream URL and the provider that produced it. */
data class ResolvedSongUrl(
    val url: String,
    val providerId: String
)

/** Netease quality levels, tried in this order. `lossless` is last because it most often
 * triggers a free-trial or downgraded result for accounts without SVIP. */
internal val AUDIO_QUALITY_LEVELS = listOf("exhigh", "higher", "standard", "lossless")

/**
 * Host suffixes the Netease stream proxy is allowed to fetch from.
 *
 * Shared by [com.theveloper.pixelplay.data.netease.NeteaseStreamProxy] and the providers so that
 * a candidate URL is filtered while it is being picked instead of after the resolver already
 * reported success. A URL the proxy would reject is worse than no URL at all: the player would
 * get a 502 while every retry keeps reusing the same rejected link.
 */
val NETEASE_STREAM_HOST_SUFFIXES: Set<String> = setOf(
    "music.126.net",
    "music.163.com",
    "126.net",
    "163.com",
    // Qijieya returns the audio stream itself, so the proxy fetches directly from it.
    "qijieya.cn"
)

/** True when [url] can actually be fetched by the Netease stream proxy. */
fun isNeteaseStreamableUrl(url: String): Boolean = CloudStreamSecurity.isSafeRemoteStreamUrl(
    url = url,
    allowedHostSuffixes = NETEASE_STREAM_HOST_SUFFIXES,
    allowHttpForAllowedHosts = true
)
