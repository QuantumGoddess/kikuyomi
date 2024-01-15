package eu.kanade.tachiyomi.ui.history.audiobook

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.history.HistoryDeleteAllDialog
import eu.kanade.presentation.history.HistoryDeleteDialog
import eu.kanade.presentation.history.audiobook.AudiobookHistoryScreen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.i18n.stringResource
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

val resumeLastChapterSeenEvent = Channel<Unit>()

@Composable
fun Screen.audiobookHistoryTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val snackbarHostState = SnackbarHostState()

    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AudiobookHistoryScreenModel() }
    val state by screenModel.state.collectAsState()
    val searchQuery by screenModel.query.collectAsState()

    suspend fun openChapter(context: Context, chapter: Chapter?) {
        val playerPreferences: PlayerPreferences by injectLazy()
        val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
        if (chapter != null) {
            MainActivity.startAudioPlayerActivity(context, chapter.audiobookId, chapter.id, extPlayer)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }

    val navigateUp: (() -> Unit)? = if (fromMore) navigator::pop else null

    return TabContent(
        titleRes = MR.strings.label_audiobook_history,
        searchEnabled = true,
        content = { contentPadding, _ ->
            AudiobookHistoryScreen(
                state = state,
                searchQuery = searchQuery,
                snackbarHostState = snackbarHostState,
                onClickCover = { navigator.push(AudiobookScreen(it)) },
                onClickResume = screenModel::getNextChapterForAudiobook,
                onDialogChange = screenModel::setDialog,
            )

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is AudiobookHistoryScreenModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all ->
                            if (all) {
                                screenModel.removeAllFromHistory(dialog.history.audiobookId)
                            } else {
                                screenModel.removeFromHistory(dialog.history)
                            }
                        },
                        isManga = false,
                    )
                }
                is AudiobookHistoryScreenModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = screenModel::removeAllHistory,
                    )
                }
                null -> {}
            }

            LaunchedEffect(state.list) {
                if (state.list != null) {
                    (context as? MainActivity)?.ready = true
                }
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { e ->
                    when (e) {
                        AudiobookHistoryScreenModel.Event.InternalError ->
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.internal_error),
                            )
                        AudiobookHistoryScreenModel.Event.HistoryCleared ->
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.clear_history_completed),
                            )
                        is AudiobookHistoryScreenModel.Event.OpenChapter -> openChapter(
                            context,
                            e.chapter,
                        )
                    }
                }
            }

            LaunchedEffect(Unit) {
                resumeLastChapterSeenEvent.receiveAsFlow().collectLatest {
                    openChapter(context, screenModel.getNextChapter())
                }
            }
        },
        actions =
        persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { screenModel.setDialog(AudiobookHistoryScreenModel.Dialog.DeleteAll) },
            ),
        ),
        navigateUp = navigateUp,
    )
}
