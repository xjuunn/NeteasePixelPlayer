package com.theveloper.pixelplay.data.netease.url

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Resolves a playable URL for a Netease song by walking the registered [SongUrlProvider]s in
 * priority order.
 *
 * Rules (see docs/netease-integration.md):
 * - Providers are tried from highest to lowest priority: official, Byfuns, Qijieya, then Mycelis.
 * - Each provider is retried up to its [SongUrlProvider.maxAttempts] before moving on, with a small
 *   jittered backoff between attempts.
 * - Mycelis (`mycelis.dpdns.org`) is a last resort and is only reached after all other providers
 *   have been exhausted; it never runs before them.
 * - Per-provider failures never abort resolution; the next provider is always attempted.
 */
@Singleton
class SongUrlResolver @Inject constructor(
    officialProvider: OfficialSongUrlProvider,
    byfunsProvider: ByfunsSongUrlProvider,
    qijieyaProvider: QijieyaSongUrlProvider,
    mycelisProvider: MycelisSongUrlProvider
) {

    private val providers: List<SongUrlProvider> = listOf(
        officialProvider,
        byfunsProvider,
        qijieyaProvider,
        mycelisProvider
    ).sortedBy { it.priority }

    suspend fun resolve(songId: Long, quality: String = "lossless"): Result<ResolvedSongUrl> {
        var lastError: Throwable? = null

        for (provider in providers) {
            val attempts = provider.maxAttempts.coerceAtLeast(1)
            repeat(attempts) { attempt ->
                val url = try {
                    provider.resolve(songId, quality).firstOrNull { it.isNotBlank() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    null
                }

                if (!url.isNullOrBlank()) {
                    Timber.d("SongUrlResolver: songId=$songId resolved by ${provider.id}")
                    return Result.success(ResolvedSongUrl(url = url, providerId = provider.id))
                }

                if (attempt < attempts - 1) {
                    delay(retryDelayMs(attempt))
                }
            }
            Timber.d("SongUrlResolver: provider=${provider.id} exhausted for songId=$songId")
        }

        return Result.failure(
            lastError ?: IllegalStateException("No playable URL available for song $songId")
        )
    }

    private fun retryDelayMs(attempt: Int): Long {
        val base = 300L * (attempt + 1)
        return base + Random.nextLong(0L, 150L)
    }
}
