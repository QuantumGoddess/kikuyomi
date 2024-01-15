package tachiyomi.data.updates.audiobook

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.domain.updates.audiobook.model.AudiobookUpdatesWithRelations
import tachiyomi.domain.updates.audiobook.repository.AudiobookUpdatesRepository

class AudiobookUpdatesRepositoryImpl(
    private val databaseHandler: AudiobookDatabaseHandler,
) : AudiobookUpdatesRepository {

    override suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<AudiobookUpdatesWithRelations> {
        return databaseHandler.awaitList {
            audiobookupdatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    override fun subscribeAllAudiobookUpdates(after: Long, limit: Long): Flow<List<AudiobookUpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            audiobookupdatesViewQueries.getRecentAudiobookUpdates(
                after,
                limit,
                ::mapUpdatesWithRelations,
            )
        }
    }

    override fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<AudiobookUpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            audiobookupdatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    private fun mapUpdatesWithRelations(
        audiobookId: Long,
        audiobookTitle: String,
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastSecondRead: Long,
        totalSeconds: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateUpload: Long,
        dateFetch: Long,
    ): AudiobookUpdatesWithRelations = AudiobookUpdatesWithRelations(
        audiobookId = audiobookId,
        audiobookTitle = audiobookTitle,
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastSecondRead = lastSecondRead,
        totalSeconds = totalSeconds,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = AudiobookCover(
            audiobookId = audiobookId,
            sourceId = sourceId,
            isAudiobookFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
