package tachiyomi.data.history.audiobook

import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.domain.history.audiobook.model.AudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import java.util.Date

object AudiobookHistoryMapper {
    fun mapAudiobookHistory(
        id: Long,
        chapterId: Long,
        readAt: Date?,
    ): AudiobookHistory = AudiobookHistory(
        id = id,
        chapterId = chapterId,
        readAt = readAt,
    )

    fun mapAudiobookHistoryWithRelations(
        historyId: Long,
        audiobookId: Long,
        chapterId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        chapterNumber: Double,
        readAt: Date?,
    ): AudiobookHistoryWithRelations = AudiobookHistoryWithRelations(
        id = historyId,
        chapterId = chapterId,
        audiobookId = audiobookId,
        title = title,
        chapterNumber = chapterNumber,
        readAt = readAt,
        coverData = AudiobookCover(
            audiobookId = audiobookId,
            sourceId = sourceId,
            isAudiobookFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
