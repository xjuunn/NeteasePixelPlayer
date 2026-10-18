package com.theveloper.pixelplay.data.netease.url

/**
 * A pluggable source of playable audio URLs for a Netease song.
 *
 * [SongUrlResolver] orders implementations by [priority] (ascending) and retries each one up to
 * [maxAttempts] times before moving on, so lower-priority sources are only reached after
 * higher-priority ones are exhausted. The priority order and retry rules are defined in
 * `docs/netease-integration.md`; `mycelis` must remain the last resort.
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
