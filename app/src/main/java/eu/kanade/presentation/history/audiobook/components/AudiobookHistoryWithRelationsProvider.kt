package eu.kanade.presentation.history.audiobook.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import java.util.Date

internal class AudiobookHistoryWithRelationsProvider : PreviewParameterProvider<AudiobookHistoryWithRelations> {

    private val simple = AudiobookHistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        audiobookId = 3L,
        title = "Test Title",
        chapterNumber = 10.2,
        readAt = Date(1697247357L),
        coverData = tachiyomi.domain.entries.audiobook.model.AudiobookCover(
            audiobookId = 3L,
            sourceId = 4L,
            isAudiobookFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithoutReadAt = AudiobookHistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        audiobookId = 3L,
        title = "Test Title",
        chapterNumber = 10.2,
        readAt = null,
        coverData = tachiyomi.domain.entries.audiobook.model.AudiobookCover(
            audiobookId = 3L,
            sourceId = 4L,
            isAudiobookFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithNegativeChapterNumber = AudiobookHistoryWithRelations(
        id = 1L,
        chapterId = 2L,
        audiobookId = 3L,
        title = "Test Title",
        chapterNumber = -2.0,
        readAt = Date(1697247357L),
        coverData = tachiyomi.domain.entries.audiobook.model.AudiobookCover(
            audiobookId = 3L,
            sourceId = 4L,
            isAudiobookFavorite = false,
            url = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    override val values: Sequence<AudiobookHistoryWithRelations>
        get() = sequenceOf(simple, historyWithoutReadAt, historyWithNegativeChapterNumber)
}
