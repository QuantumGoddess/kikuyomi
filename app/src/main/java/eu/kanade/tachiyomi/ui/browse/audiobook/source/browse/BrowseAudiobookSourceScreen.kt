package eu.kanade.tachiyomi.ui.browse.audiobook.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.audiobook.BrowseAudiobookSourceContent
import eu.kanade.presentation.browse.audiobook.MissingSourceScreen
import eu.kanade.presentation.browse.audiobook.components.BrowseAudiobookSourceToolbar
import eu.kanade.presentation.browse.audiobook.components.RemoveEntryDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.audiobook.DuplicateAudiobookDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.core.Constants
import eu.kanade.tachiyomi.ui.browse.audiobook.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.BrowseAudiobookSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource

data class BrowseAudiobookSourceScreen(
    private val sourceId: Long,
    private val listingQuery: String?,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { BrowseAudiobookSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(
                    null,
                )
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubAudiobookSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalAudiobookSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? AudiobookHttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? AudiobookHttpSource)?.baseUrl
        }

        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    BrowseAudiobookSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(SourcePreferencesScreen(sourceId)) },
                        onSearch = screenModel::search,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as AudiobookCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingFlow by screenModel.audiobookPagerFlowFlow.collectAsState()

            BrowseAudiobookSourceContent(
                source = screenModel.source,
                audiobookList = pagingFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalAudiobookSourceHelpClick = onHelpClick,
                onAudiobookClick = { navigator.push((AudiobookScreen(it.id, true))) },
                onAudiobookLongClick = { audiobook ->
                    scope.launchIO {
                        val duplicateAudiobook = screenModel.getDuplicateAudiobooklibAudiobook(audiobook)
                        when {
                            audiobook.favorite -> screenModel.setDialog(
                                BrowseAudiobookSourceScreenModel.Dialog.RemoveAudiobook(audiobook),
                            )
                            duplicateAudiobook != null -> screenModel.setDialog(
                                BrowseAudiobookSourceScreenModel.Dialog.AddDuplicateAudiobook(
                                    audiobook,
                                    duplicateAudiobook,
                                ),
                            )
                            else -> screenModel.addFavorite(audiobook)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseAudiobookSourceScreenModel.Dialog.Filter -> {
                SourceFilterAudiobookDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                )
            }
            is BrowseAudiobookSourceScreenModel.Dialog.AddDuplicateAudiobook -> {
                DuplicateAudiobookDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.audiobook) },
                    onOpenAudiobook = { navigator.push(AudiobookScreen(dialog.duplicate.id)) },
                )
            }
            is BrowseAudiobookSourceScreenModel.Dialog.RemoveAudiobook -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeAudiobookFavorite(dialog.audiobook)
                    },
                    entryToRemove = dialog.audiobook.title,
                )
            }
            is BrowseAudiobookSourceScreenModel.Dialog.ChangeAudiobookCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoriesTab(false)) },
                    onConfirm = { include, _ ->
                        screenModel.changeAudiobookFavorite(dialog.audiobook)
                        screenModel.moveAudiobookToCategories(dialog.audiobook, include)
                    },
                )
            }
            is BrowseAudiobookSourceScreenModel.Dialog.Migrate -> {}
            else -> {}
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow()
                .collectLatest {
                    when (it) {
                        is SearchType.Genre -> screenModel.searchGenre(it.txt)
                        is SearchType.Text -> screenModel.search(it.txt)
                    }
                }
        }
    }

    suspend fun search(query: String) = queryEvent.send(SearchType.Text(query))
    suspend fun searchGenre(name: String) = queryEvent.send(SearchType.Genre(name))

    companion object {
        private val queryEvent = Channel<SearchType>()
    }

    sealed class SearchType(val txt: String) {
        class Text(txt: String) : SearchType(txt)
        class Genre(txt: String) : SearchType(txt)
    }
}
