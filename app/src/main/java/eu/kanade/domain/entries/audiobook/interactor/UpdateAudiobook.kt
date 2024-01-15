package eu.kanade.domain.entries.audiobook.interactor

import eu.kanade.domain.entries.audiobook.model.hasCustomCover
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import tachiyomi.domain.entries.audiobook.interactor.AudiobookFetchInterval
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

class UpdateAudiobook(
    private val audiobookRepository: AudiobookRepository,
    private val audiobookFetchInterval: AudiobookFetchInterval,
) {

    suspend fun await(audiobookUpdate: AudiobookUpdate): Boolean {
        return audiobookRepository.updateAudiobook(audiobookUpdate)
    }

    suspend fun awaitAll(audiobookUpdates: List<AudiobookUpdate>): Boolean {
        return audiobookRepository.updateAllAudiobook(audiobookUpdates)
    }

    suspend fun awaitUpdateFromSource(
        localAudiobook: Audiobook,
        remoteAudiobook: SAudiobook,
        manualFetch: Boolean,
        coverCache: AudiobookCoverCache = Injekt.get(),
    ): Boolean {
        val remoteTitle = try {
            remoteAudiobook.title
        } catch (_: UninitializedPropertyAccessException) {
            ""
        }

        // if the audiobook isn't a favorite, set its title from source and update in db
        val title = if (remoteTitle.isEmpty() || localAudiobook.favorite) null else remoteTitle

        val coverLastModified =
            when {
                // Never refresh covers if the url is empty to avoid "losing" existing covers
                remoteAudiobook.thumbnail_url.isNullOrEmpty() -> null
                !manualFetch && localAudiobook.thumbnailUrl == remoteAudiobook.thumbnail_url -> null
                localAudiobook.isLocal() -> Instant.now().toEpochMilli()
                localAudiobook.hasCustomCover(coverCache) -> {
                    coverCache.deleteFromCache(localAudiobook, false)
                    null
                }
                else -> {
                    coverCache.deleteFromCache(localAudiobook, false)
                    Instant.now().toEpochMilli()
                }
            }

        val thumbnailUrl = remoteAudiobook.thumbnail_url?.takeIf { it.isNotEmpty() }

        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = localAudiobook.id,
                title = title,
                coverLastModified = coverLastModified,
                author = remoteAudiobook.author,
                artist = remoteAudiobook.artist,
                description = remoteAudiobook.description,
                genre = remoteAudiobook.getGenres(),
                thumbnailUrl = thumbnailUrl,
                status = remoteAudiobook.status.toLong(),
                updateStrategy = remoteAudiobook.update_strategy,
                initialized = true,
            ),
        )
    }

    suspend fun awaitUpdateFetchInterval(
        audiobook: Audiobook,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = audiobookFetchInterval.getWindow(dateTime),
    ): Boolean {
        return audiobookFetchInterval.toAudiobookUpdateOrNull(audiobook, dateTime, window)
            ?.let { audiobookRepository.updateAudiobook(it) }
            ?: false
    }

    suspend fun awaitUpdateLastUpdate(audiobookId: Long): Boolean {
        return audiobookRepository.updateAudiobook(AudiobookUpdate(id = audiobookId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(id = mangaId, coverLastModified = Instant.now().toEpochMilli()),
        )
    }

    suspend fun awaitUpdateFavorite(audiobookId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(id = audiobookId, favorite = favorite, dateAdded = dateAdded),
        )
    }
}
