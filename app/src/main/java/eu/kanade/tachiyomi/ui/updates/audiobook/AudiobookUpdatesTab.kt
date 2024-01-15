package eu.kanade.tachiyomi.ui.updates.audiobook

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.audiobook.ChapterOptionsDialogScreen
import eu.kanade.presentation.updates.UpdatesDeleteConfirmationDialog
import eu.kanade.presentation.updates.audiobook.AudiobookUpdateScreen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

@Composable
fun Screen.audiobookUpdatesTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AudiobookUpdatesScreenModel() }
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val navigateUp: (() -> Unit)? = if (fromMore) navigator::pop else null

    suspend fun openChapter(updateItem: AudiobookUpdatesItem, altPlayer: Boolean = false) {
        val playerPreferences: PlayerPreferences by injectLazy()
        val update = updateItem.update
        val extPlayer = playerPreferences.alwaysUseExternalPlayer().get() != altPlayer
        MainActivity.startAudioPlayerActivity(context, update.audiobookId, update.chapterId, extPlayer)
    }

    return TabContent(
        titleRes = MR.strings.label_audiobook_updates,
        searchEnabled = false,
        content = { contentPadding, _ ->
            AudiobookUpdateScreen(
                state = state,
                snackbarHostState = screenModel.snackbarHostState,
                lastUpdated = screenModel.lastUpdated,
                relativeTime = screenModel.relativeTime,
                onClickCover = { item -> navigator.push(AudiobookScreen(item.update.audiobookId)) },
                onSelectAll = screenModel::toggleAllSelection,
                onInvertSelection = screenModel::invertSelection,
                onUpdateLibrary = screenModel::updateLibrary,
                onDownloadChapter = screenModel::downloadChapters,
                onMultiBookmarkClicked = screenModel::bookmarkUpdates,
                onMultiMarkAsReadClicked = screenModel::markUpdatesRead,
                onMultiDeleteClicked = screenModel::showConfirmDeleteChapters,
                onUpdateSelected = screenModel::toggleSelection,
                onOpenChapter = { updateItem: AudiobookUpdatesItem, altPlayer: Boolean ->
                    scope.launchIO {
                        openChapter(updateItem, altPlayer)
                    }
                    Unit
                },
            )

            val onDismissDialog = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is AudiobookUpdatesScreenModel.Dialog.DeleteConfirmation -> {
                    UpdatesDeleteConfirmationDialog(
                        onDismissRequest = onDismissDialog,
                        onConfirm = { screenModel.deleteChapters(dialog.toDelete) },
                        isManga = false,
                    )
                }
                is AudiobookUpdatesScreenModel.Dialog.ShowQualities -> {
                    ChapterOptionsDialogScreen.onDismissDialog = onDismissDialog
                    NavigatorAdaptiveSheet(
                        screen = ChapterOptionsDialogScreen(
                            useExternalDownloader = screenModel.useExternalDownloader,
                            chapterTitle = dialog.chapterTitle,
                            chapterId = dialog.chapterId,
                            audiobookId = dialog.audiobookId,
                            sourceId = dialog.sourceId,
                        ),
                        onDismissRequest = onDismissDialog,
                    )
                }
                null -> {}
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        AudiobookUpdatesScreenModel.Event.InternalError -> screenModel.snackbarHostState.showSnackbar(
                            context.stringResource(
                                MR.strings.internal_error,
                            ),
                        )
                        is AudiobookUpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                            val msg = if (event.started) {
                                MR.strings.updating_library
                            } else {
                                MR.strings.update_already_running
                            }
                            screenModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                        }
                    }
                }
            }

            LaunchedEffect(state.selectionMode) {
                HomeScreen.showBottomNav(!state.selectionMode)
            }

            LaunchedEffect(state.isLoading) {
                if (!state.isLoading) {
                    (context as? MainActivity)?.ready = true
                }
            }
            DisposableEffect(Unit) {
                screenModel.resetNewUpdatesCount()

                onDispose {
                    screenModel.resetNewUpdatesCount()
                }
            }
        },
        actions =
        if (screenModel.state.collectAsState().value.selected.isNotEmpty()) {
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.SelectAll,
                    onClick = { screenModel.toggleAllSelection(true) },
                ),
                AppBar.Action(
                    title = stringResource(MR.strings.action_select_inverse),
                    icon = Icons.Outlined.FlipToBack,
                    onClick = { screenModel.invertSelection() },
                ),
            )
        } else {
            persistentListOf(
                AppBar.Action(
                    title = stringResource(MR.strings.action_update_library),
                    icon = Icons.Outlined.Refresh,
                    onClick = { screenModel.updateLibrary() },
                ),
            )
        },
        navigateUp = navigateUp,
    )
}
