package com.theveloper.pixelplay.data.netease.lyric

/**
 * A source of synced LRC lyrics for a Netease song.
 *
 * [SongLyricResolver] tries providers by [priority] (ascending) and returns the first non-blank
 * result. The order follows docs/netease-integration.md: official, then Qijieya (B2), then Mycelis
 * (A18).
 */
interface SongLyricProvider {

    /** Stable identifier used in logs and diagnostics. */
    val id: String

    /** Lower values are tried first. */
    val priority: Int

    /** Return LRC text for [songId], or null when unavailable. */
    suspend fun fetch(songId: Long): String?
}
