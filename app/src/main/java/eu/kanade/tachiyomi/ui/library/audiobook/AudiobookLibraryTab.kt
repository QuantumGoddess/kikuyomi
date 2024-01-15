package eu.kanade.tachiyomi.ui.library.audiobook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.components.LibraryBottomActionMenu
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.audiobook.AudiobookLibraryContent
import eu.kanade.presentation.library.audiobook.AudiobookLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.audiobook.AudiobookLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.GlobalAudiobookSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.library.audiobook.LibraryAudiobook
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.injectLazy

object AudiobookLibraryTab : Tab() {

    val libraryPreferences: LibraryPreferences by injectLazy()
    private val fromMore = libraryPreferences.bottomNavStyle().get() == 2

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = if (fromMore) {
                MR.strings.label_library
            } else {
                MR.strings.label_audiobook_library
            }
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(
                R.drawable.anim_audiobooklibrary_leave,
            )
            return TabOptions(
                index = 0u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { AudiobookLibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { AudiobookLibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            val started = AudiobookLibraryUpdateJob.startNow(context, category)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        suspend fun openChapter(chapter: Chapter) {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            MainActivity.startAudioPlayerActivity(context, chapter.audiobookId, chapter.id, extPlayer)
        }

        val defaultTitle = if (fromMore) {
            stringResource(MR.strings.label_library)
        } else {
            stringResource(
                MR.strings.label_audiobook_library,
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                val title = state.getToolbarTitle(
                    defaultTitle = defaultTitle,
                    defaultCategoryTitle = stringResource(MR.strings.label_default),
                    page = screenModel.activeCategoryIndex,
                )
                val tabVisible = state.showCategoryTabs && state.categories.size > 1
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = state.selection.size,
                    title = title,
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex) },
                    onClickInvertSelection = {
                        screenModel.invertSelection(
                            screenModel.activeCategoryIndex,
                        )
                    },
                    onClickFilter = screenModel::showSettingsDialog,
                    onClickRefresh = {
                        onClickRefresh(
                            state.categories[screenModel.activeCategoryIndex],
                        )
                    },
                    onClickGlobalUpdate = { onClickRefresh(null) },
                    onClickOpenRandomEntry = {
                        scope.launch {
                            val randomItem = screenModel.getRandomAudiobooklibItemForCurrentCategory()
                            if (randomItem != null) {
                                navigator.push(AudiobookScreen(randomItem.libraryAudiobook.audiobook.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior.takeIf { !tabVisible }, // For scroll overlay when no tab
                )
            },
            bottomBar = {
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                    onMarkAsViewedClicked = { screenModel.markReadSelection(true) },
                    onMarkAsUnviewedClicked = { screenModel.markReadSelection(false) },
                    onDownloadClicked = screenModel::runDownloadActionSelection
                        .takeIf { state.selection.fastAll { !it.audiobook.isLocal() } },
                    onDeleteClicked = screenModel::openDeleteAudiobookDialog,
                    isManga = false,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                    val handler = LocalUriHandler.current
                    EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(contentPadding),
                        actions = persistentListOf(
                            EmptyScreenAction(
                                stringRes = MR.strings.getting_started_guide,
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                onClick = { handler.openUri(GETTING_STARTED_URL) },
                            ),
                        ),
                    )
                }
                else -> {
                    AudiobookLibraryContent(
                        categories = state.categories,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = contentPadding,
                        currentPage = { screenModel.activeCategoryIndex },
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                        onChangeCurrentPage = { screenModel.activeCategoryIndex = it },
                        onAudiobookClicked = { navigator.push(AudiobookScreen(it)) },
                        onContinueListeningClicked = { it: LibraryAudiobook ->
                            scope.launchIO {
                                val chapter = screenModel.getNextUnreadChapter(it.audiobook)
                                if (chapter != null) openChapter(chapter)
                            }
                            Unit
                        }.takeIf { state.showAudiobookContinueButton },
                        onToggleSelection = screenModel::toggleSelection,
                        onToggleRangeSelection = {
                            screenModel.toggleRangeSelection(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onRefresh = onClickRefresh,
                        onGlobalSearchClicked = {
                            navigator.push(
                                GlobalAudiobookSearchScreen(screenModel.state.value.searchQuery ?: ""),
                            )
                        },
                        getNumberOfAudiobookForCategory = { state.getAudiobookCountForCategory(it) },
                        getDisplayMode = { screenModel.getDisplayMode() },
                        getColumnsForOrientation = {
                            screenModel.getColumnsPreferenceForCurrentOrientation(
                                it,
                            )
                        },
                    ) { state.getAudiobooklibItemsByPage(it) }
                }
            }
        }

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is AudiobookLibraryScreenModel.Dialog.SettingsSheet -> run {
                val category = state.categories.getOrNull(screenModel.activeCategoryIndex)
                if (category == null) {
                    onDismissRequest()
                    return@run
                }
                AudiobookLibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                    category = category,
                )
            }
            is AudiobookLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        screenModel.clearSelection()
                        navigator.push(CategoriesTab(false))
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setAudiobookCategories(dialog.audiobook, include, exclude)
                    },
                )
            }
            is AudiobookLibraryScreenModel.Dialog.DeleteAudiobook -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = dialog.audiobook.any(Audiobook::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteAudiobook, deleteChapter ->
                        screenModel.removeAudiobooks(dialog.audiobook, deleteAudiobook, deleteChapter)
                        screenModel.clearSelection()
                    },
                    isManga = false,
                )
            }
            null -> {}
        }

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.showSettingsDialog() } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
