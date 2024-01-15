package eu.kanade.tachiyomi.ui.browse.audiobook.migration.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.audiobook.BrowseAudiobookSourceContent
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.core.Constants
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.BrowseAudiobookSourceScreenModel
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.SourceFilterAudiobookDialog
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource

data class AudiobookSourceSearchScreen(
    private val oldAudiobook: Audiobook,
    private val sourceId: Long,
    private val query: String?,
) : Screen() {

    @Composable
    override fun Content() {
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val screenModel = rememberScreenModel { BrowseAudiobookSourceScreenModel(sourceId, query) }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.toolbarQuery ?: "",
                    onChangeSearchQuery = screenModel::setToolbarQuery,
                    onClickCloseSearch = navigator::pop,
                    onSearch = screenModel::search,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(visible = state.filters.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        text = { Text(text = stringResource(MR.strings.action_filter)) },
                        icon = { Icon(Icons.Outlined.FilterList, contentDescription = "") },
                        onClick = screenModel::openFilterSheet,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingFlow by screenModel.audiobookPagerFlowFlow.collectAsState()
            val openMigrateDialog: (Audiobook) -> Unit = {
                screenModel.setDialog(BrowseAudiobookSourceScreenModel.Dialog.Migrate(it))
            }
            BrowseAudiobookSourceContent(
                source = screenModel.source,
                audiobookList = pagingFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val source = screenModel.source as? AudiobookHttpSource ?: return@BrowseAudiobookSourceContent
                    navigator.push(
                        WebViewScreen(
                            url = source.baseUrl,
                            initialTitle = source.name,
                            sourceId = source.id,
                        ),
                    )
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalAudiobookSourceHelpClick = { uriHandler.openUri(LocalAudiobookSource.HELP_URL) },
                onAudiobookClick = openMigrateDialog,
                onAudiobookLongClick = { navigator.push(AudiobookScreen(it.id, true)) },
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
            is BrowseAudiobookSourceScreenModel.Dialog.Migrate -> {
                MigrateAudiobookDialog(
                    oldAudiobook = oldAudiobook,
                    newAudiobook = dialog.newAudiobook,
                    screenModel = rememberScreenModel { MigrateAudiobookDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AudiobookScreen(dialog.newAudiobook.id)) },
                    onPopScreen = {
                        scope.launch {
                            navigator.popUntilRoot()
                            HomeScreen.openTab(HomeScreen.Tab.Browse())
                            navigator.push(AudiobookScreen(dialog.newAudiobook.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
