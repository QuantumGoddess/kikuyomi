package eu.kanade.presentation.browse.audiobook.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.browse.manga.components.BrowseSourceLoadingItem
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryListItem
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAudiobookSourceList(
    audiobookList: LazyPagingItems<StateFlow<Audiobook>>,
    contentPadding: PaddingValues,
    onAudiobookClick: (Audiobook) -> Unit,
    onAudiobookLongClick: (Audiobook) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (audiobookList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = audiobookList.itemCount) { index ->
            val audiobook by audiobookList[index]?.collectAsState() ?: return@items
            BrowseAudiobookSourceListItem(
                audiobook = audiobook,
                onClick = { onAudiobookClick(audiobook) },
                onLongClick = { onAudiobookLongClick(audiobook) },
            )
        }

        item {
            if (audiobookList.loadState.refresh is LoadState.Loading || audiobookList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun BrowseAudiobookSourceListItem(
    audiobook: Audiobook,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    EntryListItem(
        title = audiobook.title,
        coverData = AudiobookCover(
            audiobookId = audiobook.id,
            sourceId = audiobook.source,
            isAudiobookFavorite = audiobook.favorite,
            url = audiobook.thumbnailUrl,
            lastModified = audiobook.coverLastModified,
        ),
        coverAlpha = if (audiobook.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = audiobook.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
