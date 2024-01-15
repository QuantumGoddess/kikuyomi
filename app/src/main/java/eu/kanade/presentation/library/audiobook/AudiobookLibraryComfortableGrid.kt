package eu.kanade.presentation.library.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.tachiyomi.ui.library.audiobook.AudiobookLibraryItem
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.domain.library.audiobook.LibraryAudiobook

@Composable
internal fun AudiobookLibraryComfortableGrid(
    items: List<AudiobookLibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryAudiobook>,
    onClick: (LibraryAudiobook) -> Unit,
    onLongClick: (LibraryAudiobook) -> Unit,
    onClickContinueListening: ((LibraryAudiobook) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "audiobook_library_comfortable_grid_item" },
        ) { libraryItem ->
            val audiobook = libraryItem.libraryAudiobook.audiobook
            EntryComfortableGridItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryAudiobook.id },
                title = audiobook.title,
                coverData = AudiobookCover(
                    audiobookId = audiobook.id,
                    sourceId = audiobook.source,
                    isAudiobookFavorite = audiobook.favorite,
                    url = audiobook.thumbnailUrl,
                    lastModified = audiobook.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryAudiobook) },
                onClick = { onClick(libraryItem.libraryAudiobook) },
                onClickContinueViewing = if (onClickContinueListening != null && libraryItem.unreadCount > 0) {
                    { onClickContinueListening(libraryItem.libraryAudiobook) }
                } else {
                    null
                },
            )
        }
    }
}
