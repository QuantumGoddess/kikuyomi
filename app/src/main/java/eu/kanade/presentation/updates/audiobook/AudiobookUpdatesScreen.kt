package eu.kanade.presentation.updates.audiobook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.entries.audiobook.components.ChapterDownloadAction
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.updates.audiobook.AudiobookUpdatesItem
import eu.kanade.tachiyomi.ui.updates.audiobook.AudiobookUpdatesScreenModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun AudiobookUpdateScreen(
    state: AudiobookUpdatesScreenModel.State,
    snackbarHostState: SnackbarHostState,
    relativeTime: Boolean,
    lastUpdated: Long,
    onClickCover: (AudiobookUpdatesItem) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onUpdateLibrary: () -> Boolean,
    onDownloadChapter: (List<AudiobookUpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<AudiobookUpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<AudiobookUpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<AudiobookUpdatesItem>) -> Unit,
    onUpdateSelected: (AudiobookUpdatesItem, Boolean, Boolean, Boolean) -> Unit,
    onOpenChapter: (AudiobookUpdatesItem, altPlayer: Boolean) -> Unit,
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            AudiobookUpdatesBottomBar(
                selected = state.selected,
                onDownloadChapter = onDownloadChapter,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onOpenChapter = onOpenChapter,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                val scope = rememberCoroutineScope()
                var isRefreshing by remember { mutableStateOf(false) }

                PullRefresh(
                    refreshing = isRefreshing,
                    onRefresh = {
                        val started = onUpdateLibrary()
                        if (!started) return@PullRefresh
                        scope.launch {
                            // Fake refresh status but hide it after a second as it's a long running task
                            isRefreshing = true
                            delay(1.seconds)
                            isRefreshing = false
                        }
                    },
                    enabled = { !state.selectionMode },
                    indicatorPadding = contentPadding,
                ) {
                    FastScrollLazyColumn(
                        contentPadding = contentPadding,
                    ) {
                        audiobookUpdatesLastUpdatedItem(lastUpdated)

                        audiobookUpdatesUiItems(
                            uiModels = state.getUiModel(context, relativeTime),
                            selectionMode = state.selectionMode,
                            onUpdateSelected = onUpdateSelected,
                            onClickCover = onClickCover,
                            onClickUpdate = onOpenChapter,
                            onDownloadChapter = onDownloadChapter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookUpdatesBottomBar(
    selected: List<AudiobookUpdatesItem>,
    onDownloadChapter: (List<AudiobookUpdatesItem>, ChapterDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<AudiobookUpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<AudiobookUpdatesItem>, read: Boolean) -> Unit,
    onMultiDeleteClicked: (List<AudiobookUpdatesItem>) -> Unit,
    onOpenChapter: (AudiobookUpdatesItem, altPlayer: Boolean) -> Unit,
) {
    val playerPreferences: PlayerPreferences = Injekt.get()
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, true)
        }.takeIf { selected.fastAny { !it.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, false)
        }.takeIf { selected.fastAll { it.update.bookmark } },
        onMarkAsViewedClicked = {
            onMultiMarkAsReadClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.read } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsReadClicked(selected, false)
        }.takeIf { selected.fastAny { it.update.read || it.update.lastSecondRead > 0L } },
        onDownloadClicked = {
            onDownloadChapter(selected, ChapterDownloadAction.START)
        }.takeIf {
            selected.fastAny { it.downloadStateProvider() != AudiobookDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected)
        }.takeIf { selected.fastAny { it.downloadStateProvider() == AudiobookDownload.State.DOWNLOADED } },
        onExternalClicked = {
            onOpenChapter(selected[0], true)
        }.takeIf { !playerPreferences.alwaysUseExternalPlayer().get() && selected.size == 1 },
        onInternalClicked = {
            onOpenChapter(selected[0], true)
        }.takeIf { playerPreferences.alwaysUseExternalPlayer().get() && selected.size == 1 },
        isManga = false,
    )
}

sealed interface AudiobookUpdatesUiModel {
    data class Header(val date: String) : AudiobookUpdatesUiModel
    data class Item(val item: AudiobookUpdatesItem) : AudiobookUpdatesUiModel
}
