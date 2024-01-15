package eu.kanade.presentation.history.audiobook

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.audiobook.AudiobookHistoryScreenModel
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.random.Random

class AudiobookHistoryScreenModelStateProvider : PreviewParameterProvider<AudiobookHistoryScreenModel.State> {

    private val multiPage = AudiobookHistoryScreenModel.State(
        searchQuery = null,
        list =
        listOf(HistoryUiModelExamples.headerToday)
            .asSequence()
            .plus(HistoryUiModelExamples.items().take(3))
            .plus(HistoryUiModelExamples.header { it.minus(1, ChronoUnit.DAYS) })
            .plus(HistoryUiModelExamples.items().take(1))
            .plus(HistoryUiModelExamples.header { it.minus(2, ChronoUnit.DAYS) })
            .plus(HistoryUiModelExamples.items().take(7))
            .toList(),
        dialog = null,
    )

    private val shortRecent = AudiobookHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val shortFuture = AudiobookHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val empty = AudiobookHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = AudiobookHistoryScreenModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = AudiobookHistoryScreenModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<AudiobookHistoryScreenModel.State> = sequenceOf(
        multiPage,
        shortRecent,
        shortFuture,
        empty,
        loadingWithSearchQuery,
        loading,
    )

    private object HistoryUiModelExamples {
        val headerToday = header()
        val headerTomorrow =
            AudiobookHistoryUiModel.Header(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))

        fun header(instantBuilder: (Instant) -> Instant = { it }) =
            AudiobookHistoryUiModel.Header(Date.from(instantBuilder(Instant.now())))

        fun items() = sequence {
            var count = 1
            while (true) {
                yield(randItem { it.copy(title = "Example Title $count") })
                count += 1
            }
        }

        fun randItem(historyBuilder: (AudiobookHistoryWithRelations) -> AudiobookHistoryWithRelations = { it }) =
            AudiobookHistoryUiModel.Item(
                historyBuilder(
                    AudiobookHistoryWithRelations(
                        id = Random.nextLong(),
                        chapterId = Random.nextLong(),
                        audiobookId = Random.nextLong(),
                        title = "Test Title",
                        chapterNumber = Random.nextDouble(),
                        readAt = Date.from(Instant.now()),
                        coverData = AudiobookCover(
                            audiobookId = Random.nextLong(),
                            sourceId = Random.nextLong(),
                            isAudiobookFavorite = Random.nextBoolean(),
                            url = "https://example.com/cover.png",
                            lastModified = Random.nextLong(),
                        ),
                    ),
                ),
            )
    }
}
