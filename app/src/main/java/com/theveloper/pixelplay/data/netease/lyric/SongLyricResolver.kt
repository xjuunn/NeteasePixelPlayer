package com.theveloper.pixelplay.data.netease.lyric

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves LRC lyrics by trying the registered [SongLyricProvider]s in priority order and returning
 * the first non-blank result.
 *
 * Order (docs/netease-integration.md): official, Qijieya (B2), Mycelis (A18).
 */
@Singleton
class SongLyricResolver @Inject constructor(
    officialProvider: OfficialSongLyricProvider,
    qijieyaProvider: QijieyaSongLyricProvider,
    mycelisProvider: MycelisSongLyricProvider
) {

    private val providers: List<SongLyricProvider> = listOf(
        officialProvider,
        qijieyaProvider,
        mycelisProvider
    ).sortedBy { it.priority }

    suspend fun resolve(songId: Long): Result<String> {
        var lastError: Throwable? = null

        for (provider in providers) {
            val lyric = try {
                provider.fetch(songId)?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                null
            }

            if (lyric != null) {
                Timber.d("SongLyricResolver: songId=$songId resolved by ${provider.id}")
                return Result.success(lyric)
            }
        }

        return Result.failure(
            lastError ?: IllegalStateException("No lyrics available for song $songId")
        )
    }
}
