package eu.kanade.presentation.history.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.RelativeDateHeader
import eu.kanade.presentation.history.audiobook.components.AudiobookHistoryItem
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.history.audiobook.AudiobookHistoryScreenModel
import tachiyomi.core.preference.InMemoryPreferenceStore
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

@Composable
fun AudiobookHistoryScreen(
    state: AudiobookHistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onClickCover: (audiobookId: Long) -> Unit,
    onClickResume: (audiobookId: Long, chapterId: Long) -> Unit,
    onDialogChange: (AudiobookHistoryScreenModel.Dialog?) -> Unit,
    preferences: UiPreferences = Injekt.get(),
    searchQuery: String? = null,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.information_no_recent_audiobook
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                AudiobookHistoryContent(
                    history = it,
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.audiobookId) },
                    onClickResume = { history -> onClickResume(history.audiobookId, history.chapterId) },
                    onClickDelete = { item ->
                        onDialogChange(
                            AudiobookHistoryScreenModel.Dialog.Delete(item),
                        )
                    },
                    preferences = preferences,
                )
            }
        }
    }
}

@Composable
private fun AudiobookHistoryContent(
    history: List<AudiobookHistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (AudiobookHistoryWithRelations) -> Unit,
    onClickResume: (AudiobookHistoryWithRelations) -> Unit,
    onClickDelete: (AudiobookHistoryWithRelations) -> Unit,
    preferences: UiPreferences,
) {
    val relativeTime = remember { preferences.relativeTime().get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat().get()) }

    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = history,
            key = { "history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is AudiobookHistoryUiModel.Header -> "header"
                    is AudiobookHistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is AudiobookHistoryUiModel.Header -> {
                    RelativeDateHeader(
                        modifier = Modifier.animateItemPlacement(),
                        date = item.date,
                        relativeTime = relativeTime,
                        dateFormat = dateFormat,
                    )
                }
                is AudiobookHistoryUiModel.Item -> {
                    val value = item.item
                    AudiobookHistoryItem(
                        modifier = Modifier.animateItemPlacement(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { onClickDelete(value) },
                    )
                }
            }
        }
    }
}

sealed interface AudiobookHistoryUiModel {
    data class Header(val date: Date) : AudiobookHistoryUiModel
    data class Item(val item: AudiobookHistoryWithRelations) : AudiobookHistoryUiModel
}

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(AudiobookHistoryScreenModelStateProvider::class)
    historyState: AudiobookHistoryScreenModel.State,
) {
    TachiyomiTheme {
        AudiobookHistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            searchQuery = null,
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            preferences = UiPreferences(
                InMemoryPreferenceStore(
                    sequenceOf(
                        InMemoryPreferenceStore.InMemoryPreference(
                            key = "relative_time_v2",
                            data = false,
                            defaultValue = false,
                        ),
                    ),
                ),
            ),
        )
    }
}
