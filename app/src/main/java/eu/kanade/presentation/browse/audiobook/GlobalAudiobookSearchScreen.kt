package eu.kanade.presentation.browse.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import eu.kanade.presentation.browse.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.GlobalSearchResultItem
import eu.kanade.presentation.browse.audiobook.components.GlobalAudiobookSearchCardRow
import eu.kanade.presentation.browse.audiobook.components.GlobalAudiobookSearchToolbar
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSearchItemResult
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun GlobalAudiobookSearchScreen(
    state: AudiobookSearchScreenModel.State,
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
            items = state.filteredItems,
            contentPadding = paddingValues,
            getAudiobook = getAudiobook,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    items: Map<AudiobookCatalogueSource, AudiobookSearchItemResult>,
    contentPadding: PaddingValues,
    getAudiobook: @Composable (Audiobook) -> State<Audiobook>,
    onClickSource: (AudiobookCatalogueSource) -> Unit,
    onClickItem: (Audiobook) -> Unit,
    onLongClickItem: (Audiobook) -> Unit,
    fromSourceId: Long? = null,
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items.forEach { (source, result) ->
            item(key = source.id) {
                GlobalSearchResultItem(
                    title = fromSourceId
                        ?.let { "▶ ${source.name}".takeIf { source.id == fromSourceId } } ?: source.name,
                    subtitle = LocaleHelper.getDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                ) {
                    when (result) {
                        AudiobookSearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is AudiobookSearchItemResult.Success -> {
                            GlobalAudiobookSearchCardRow(
                                titles = result.result,
                                getAudiobook = getAudiobook,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is AudiobookSearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}
