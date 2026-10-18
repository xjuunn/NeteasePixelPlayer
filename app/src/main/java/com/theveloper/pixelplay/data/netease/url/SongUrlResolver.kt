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
 * - Providers are tried from highest to lowest priority: official, Byfuns, Qijieya.
 * - Each provider is retried up to its [SongUrlProvider.maxAttempts] before moving on, with a small
 *   jittered backoff between attempts.
 * - Only links the stream proxy could actually fetch are accepted, so a source returning a
 *   foreign CDN host is treated as a failure instead of succeeding and dying at the proxy.
 * - When every third-party source is exhausted, the official link is used as-is (restricted
 *   preview included) — the original behaviour of this app, since a short clip beats no playback.
 *   The public `mycelis` gateway is deliberately not part of the chain.
 * - Per-provider failures never abort resolution; the next provider is always attempted.
 */
@Singleton
class SongUrlResolver @Inject constructor(
    private val officialProvider: OfficialSongUrlProvider,
    byfunsProvider: ByfunsSongUrlProvider,
    qijieyaProvider: QijieyaSongUrlProvider
) {

    private val providers: List<SongUrlProvider> = listOf(
        officialProvider,
        byfunsProvider,
        qijieyaProvider
    ).sortedBy { it.priority }

    suspend fun resolve(songId: Long, quality: String = "lossless"): Result<ResolvedSongUrl> {
        var lastError: Throwable? = null

        for (provider in providers) {
            val attempts = provider.maxAttempts.coerceAtLeast(1)
            repeat(attempts) { attempt ->
                val url = try {
                    provider.resolve(songId, quality).firstOrNull { isNeteaseStreamableUrl(it) }
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

        val preview = try {
            officialProvider.resolvePreview(songId, quality).firstOrNull { isNeteaseStreamableUrl(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
            null
        }

        if (!preview.isNullOrBlank()) {
            Timber.d("SongUrlResolver: songId=$songId falling back to official preview link")
            return Result.success(ResolvedSongUrl(url = preview, providerId = "official"))
        }

        return Result.failure(
            lastError ?: IllegalStateException("No playable URL available for song $songId")
        )
    }

    /** Forgets the official source verdicts so a newly logged-in account is re-evaluated. */
    fun invalidateOfficialCaches() {
        officialProvider.clearCaches()
    }

    private fun retryDelayMs(attempt: Int): Long {
        val base = 300L * (attempt + 1)
        return base + Random.nextLong(0L, 150L)
    }
}
