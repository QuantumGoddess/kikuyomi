package eu.kanade.presentation.library.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.tachiyomi.ui.library.audiobook.AudiobookLibraryItem
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.domain.library.audiobook.LibraryAudiobook
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun AudiobookLibraryList(
    items: List<AudiobookLibraryItem>,
    contentPadding: PaddingValues,
    selection: List<LibraryAudiobook>,
    onClick: (LibraryAudiobook) -> Unit,
    onLongClick: (LibraryAudiobook) -> Unit,
    onClickContinueListening: ((LibraryAudiobook) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = items,
            contentType = { "audiobook_library_list_item" },
        ) { libraryItem ->
            val audiobook = libraryItem.libraryAudiobook.audiobook
            EntryListItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryAudiobook.id },
                title = audiobook.title,
                coverData = AudiobookCover(
                    audiobookId = audiobook.id,
                    sourceId = audiobook.source,
                    isAudiobookFavorite = audiobook.favorite,
                    url = audiobook.thumbnailUrl,
                    lastModified = audiobook.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
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
