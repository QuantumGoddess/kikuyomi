package eu.kanade.presentation.track.audiobook

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.entries.audiobook.track.AudiobookTrackItem
import eu.kanade.test.DummyTracker
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import java.text.DateFormat

internal class AudiobookTrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val aTrack = AudiobookTrack(
        id = 1L,
        audiobookId = 2L,
        syncId = 3L,
        remoteId = 4L,
        libraryId = null,
        title = "Manage Name On Tracker Site",
        lastChapterRead = 2.0,
        totalChapters = 12L,
        status = 1L,
        score = 2.0,
        remoteUrl = "https://example.com",
        startDate = 0L,
        finishDate = 0L,
    )
    private val trackItemWithoutTrack = AudiobookTrackItem(
        track = null,
        tracker = DummyTracker(
            id = 1L,
            name = "Example Tracker",
        ),
    )
    private val trackItemWithTrack = AudiobookTrackItem(
        track = aTrack,
        tracker = DummyTracker(
            id = 2L,
            name = "Example Tracker 2",
        ),
    )

    private val trackersWithAndWithoutTrack = @Composable {
        AudiobookTrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
            ),
            dateFormat = DateFormat.getDateInstance(),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
        )
    }

    private val noTrackers = @Composable {
        AudiobookTrackInfoDialogHome(
            trackItems = listOf(),
            dateFormat = DateFormat.getDateInstance(),
            onStatusClick = {},
            onChapterClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
        )
    }

    override val values: Sequence<@Composable () -> Unit>
        get() = sequenceOf(
            trackersWithAndWithoutTrack,
            noTrackers,
        )
}
