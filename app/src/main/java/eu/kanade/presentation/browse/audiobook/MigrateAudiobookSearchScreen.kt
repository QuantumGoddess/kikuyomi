package eu.kanade.presentation.browse.audiobook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import eu.kanade.presentation.browse.audiobook.components.GlobalAudiobookSearchToolbar
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSourceFilter
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun MigrateAudiobookSearchScreen(
    state: AudiobookSearchScreenModel.State,
    fromSourceId: Long?,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (AudiobookSourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getAudiobook: @Composable (Audiobook) -> State<Audiobook>,
    onClickSource: (AudiobookCatalogueSource) -> Unit,
    onClickItem: (Audiobook) -> Unit,
    onLongClickItem: (Audiobook) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalAudiobookSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        GlobalSearchContent(
            fromSourceId = fromSourceId,
            items = state.filteredItems,
            contentPadding = paddingValues,
            getAudiobook = getAudiobook,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}
