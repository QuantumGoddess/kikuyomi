package eu.kanade.tachiyomi.ui.entries.audiobook

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.entries.audiobook.model.hasCustomCover
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.audiobook.DuplicateAudiobookDialog
import eu.kanade.presentation.entries.audiobook.ChapterOptionsDialogScreen
import eu.kanade.presentation.entries.audiobook.ChapterSettingsDialog
import eu.kanade.presentation.entries.audiobook.components.AudiobookCoverDialog
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.source.audiobook.isLocalOrStub
import eu.kanade.tachiyomi.ui.browse.audiobook.migration.search.MigrateAudiobookSearchScreen
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.BrowseAudiobookSourceScreen
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.GlobalAudiobookSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.audiobook.track.AudiobookTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.audiobook.AudiobookLibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.dialogs.SkipIntroLengthDialog
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.presentation.entries.audiobook.AudiobookScreen

class AudiobookScreen(
    private val audiobookId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { AudiobookScreenModel(context, audiobookId, fromSource) }

        val state by screenModel.state.collectAsState()

        if (state is AudiobookScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AudiobookScreenModel.State.Success
        val isAudiobookHttpSource = remember { successState.source is AudiobookHttpSource }

        LaunchedEffect(successState.audiobook, screenModel.source) {
            if (isAudiobookHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getAudiobookUrl(screenModel.audiobook, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get audiobook URL" }
                }
            }
        }

        AudiobookScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            dateRelativeTime = screenModel.relativeTime,
            dateFormat = screenModel.dateFormat,
            fetchInterval = successState.audiobook.fetchInterval,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            alwaysUseExternalPlayer = screenModel.alwaysUseExternalPlayer,
            onBackClicked = navigator::pop,
            onChapterClicked = { chapter, alt ->
                scope.launchIO {
                    val extPlayer = screenModel.alwaysUseExternalPlayer != alt
                    openChapter(context, chapter, extPlayer)
                }
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openAudiobookInWebView(
                    navigator,
                    screenModel.audiobook,
                    screenModel.source,
                )
            }.takeIf { isAudiobookHttpSource },
            onWebViewLongClicked = {
                copyAudiobookUrl(
                    context,
                    screenModel.audiobook,
                    screenModel.source,
                )
            }.takeIf { isAudiobookHttpSource },
            onTrackingClicked = {
                if (screenModel.loggedInTrackers.isEmpty()) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueListening = {
                scope.launchIO {
                    val extPlayer = screenModel.alwaysUseExternalPlayer
                    continueListening(context, screenModel.getNextUnreadChapter(), extPlayer)
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = {
                shareAudiobook(
                    context,
                    screenModel.audiobook,
                    screenModel.source,
                )
            }.takeIf { isAudiobookHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.audiobook.favorite },
            onEditFetchIntervalClicked = screenModel::showSetAudiobookFetchIntervalDialog.takeIf {
                screenModel.isUpdateIntervalEnabled && successState.audiobook.favorite
            },
            onMigrateClicked = {
                navigator.push(MigrateAudiobookSearchScreen(successState.audiobook.id))
            }.takeIf { successState.audiobook.favorite },
            changeAudiobookSkipIntro = screenModel::showAudiobookSkipIntroDialog.takeIf { successState.audiobook.favorite },
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
        )

        val onDismissRequest = {
            screenModel.dismissDialog()
            if (screenModel.autoOpenTrack && screenModel.isFromChangeCategory) {
                screenModel.isFromChangeCategory = false
                screenModel.showTrackDialog()
            }
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is AudiobookScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoriesTab(false)) },
                    onConfirm = { include, _ ->
                        screenModel.moveAudiobookToCategoriesAndAddToLibrary(dialog.audiobook, include)
                    },
                )
            }
            is AudiobookScreenModel.Dialog.DeleteChapters -> {
                DeleteItemsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                    isManga = false,
                )
            }
            is AudiobookScreenModel.Dialog.DuplicateAudiobook -> DuplicateAudiobookDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                onOpenAudiobook = { navigator.push(AudiobookScreen(dialog.duplicate.id)) },
            )
            AudiobookScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                audiobook = successState.audiobook,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
            )
            AudiobookScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = AudiobookTrackInfoDialogHomeScreen(
                        audiobookId = successState.audiobook.id,
                        audiobookTitle = successState.audiobook.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is AudiobookTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            AudiobookScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { AudiobookCoverScreenModel(successState.audiobook.id) }
                val audiobook by sm.state.collectAsState()
                if (audiobook != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    AudiobookCoverDialog(
                        coverDataProvider = { audiobook!! },
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(audiobook) { audiobook!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is AudiobookScreenModel.Dialog.SetAudiobookFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.audiobook.fetchInterval,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { screenModel.setFetchInterval(dialog.audiobook, it) },
                )
            }
            AudiobookScreenModel.Dialog.ChangeAudiobookSkipIntro -> {
                fun updateSkipIntroLength(newLength: Long) {
                    scope.launchIO {
                        screenModel.setAudiobookViewerFlags.awaitSetSkipIntroLength(audiobookId, newLength)
                    }
                }
                SkipIntroLengthDialog(
                    currentSkipIntroLength = successState.audiobook.skipIntroLength,
                    defaultSkipIntroLength = screenModel.playerPreferences.defaultIntroLength().get(),
                    fromPlayer = false,
                    updateSkipIntroLength = ::updateSkipIntroLength,
                    onDismissRequest = onDismissRequest,
                )
            }
            is AudiobookScreenModel.Dialog.ShowQualities -> {
                ChapterOptionsDialogScreen.onDismissDialog = onDismissRequest
                val chapterTitle = if (dialog.audiobook.displayMode == Audiobook.CHAPTER_DISPLAY_NUMBER) {
                    stringResource(
                        MR.strings.display_mode_chapter,
                        formatChapterNumber(dialog.chapter.chapterNumber),
                    )
                } else {
                    dialog.chapter.name
                }
                NavigatorAdaptiveSheet(
                    screen = ChapterOptionsDialogScreen(
                        useExternalDownloader = screenModel.useExternalDownloader,
                        chapterTitle = chapterTitle,
                        chapterId = dialog.chapter.id,
                        audiobookId = dialog.audiobook.id,
                        sourceId = dialog.source.id,
                    ),
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }

    private suspend fun continueListening(
        context: Context,
        unreadChapter: Chapter?,
        useExternalPlayer: Boolean,
    ) {
        if (unreadChapter != null) openChapter(context, unreadChapter, useExternalPlayer)
    }

    private suspend fun openChapter(context: Context, chapter: Chapter, useExternalPlayer: Boolean) {
        withIOContext {
            MainActivity.startAudioPlayerActivity(
                context,
                chapter.audiobookId,
                chapter.id,
                useExternalPlayer,
            )
        }
    }

    private fun getAudiobookUrl(audiobook_: Audiobook?, source_: AudiobookSource?): String? {
        val audiobook = audiobook_ ?: return null
        val source = source_ as? AudiobookHttpSource ?: return null

        return try {
            source.getAudiobookUrl(audiobook.toSAudiobook())
        } catch (e: Exception) {
            null
        }
    }

    private fun openAudiobookInWebView(navigator: Navigator, audiobook_: Audiobook?, source_: AudiobookSource?) {
        getAudiobookUrl(audiobook_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = audiobook_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareAudiobook(context: Context, audiobook_: Audiobook?, source_: AudiobookSource?) {
        try {
            getAudiobookUrl(audiobook_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalAudiobookSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                AudiobookLibraryTab.search(query)
            }
            is BrowseAudiobookSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: AudiobookSource,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseAudiobookSourceScreen && source is AudiobookHttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Audiobook URL to Clipboard
     */
    private fun copyAudiobookUrl(context: Context, audiobook_: Audiobook?, source_: AudiobookSource?) {
        val audiobook = audiobook_ ?: return
        val source = source_ as? AudiobookHttpSource ?: return
        val url = source.getAudiobookUrl(audiobook.toSAudiobook())
        context.copyToClipboard(url, url)
    }
}
