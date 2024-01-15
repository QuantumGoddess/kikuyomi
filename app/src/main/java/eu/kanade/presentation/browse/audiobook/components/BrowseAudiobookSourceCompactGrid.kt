package eu.kanade.presentation.browse.audiobook.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.browse.manga.components.BrowseSourceLoadingItem
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryCompactGridItem
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAudiobookSourceCompactGrid(
    audiobookList: LazyPagingItems<StateFlow<Audiobook>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onAudiobookClick: (Audiobook) -> Unit,
    onAudiobookLongClick: (Audiobook) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        if (audiobookList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = audiobookList.itemCount) { index ->
            val audiobook by audiobookList[index]?.collectAsState() ?: return@items
            BrowseAudiobookSourceCompactGridItem(
                audiobook = audiobook,
                onClick = { onAudiobookClick(audiobook) },
                onLongClick = { onAudiobookLongClick(audiobook) },
            )
        }

        if (audiobookList.loadState.refresh is LoadState.Loading || audiobookList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun BrowseAudiobookSourceCompactGridItem(
    audiobook: Audiobook,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
) {
    EntryCompactGridItem(
        title = audiobook.title,
        coverData = AudiobookCover(
            audiobookId = audiobook.id,
            sourceId = audiobook.source,
            isAudiobookFavorite = audiobook.favorite,
            url = audiobook.thumbnailUrl,
            lastModified = audiobook.coverLastModified,
        ),
        coverAlpha = if (audiobook.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = audiobook.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
