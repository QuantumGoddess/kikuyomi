package eu.kanade.presentation.browse.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.audiobook.components.BrowseAudiobookSourceComfortableGrid
import eu.kanade.presentation.browse.audiobook.components.BrowseAudiobookSourceCompactGrid
import eu.kanade.presentation.browse.audiobook.components.BrowseAudiobookSourceList
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.i18n.stringResource
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource

@Composable
fun BrowseAudiobookSourceContent(
    source: AudiobookSource?,
    audiobookList: LazyPagingItems<StateFlow<Audiobook>>,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onWebViewClick: () -> Unit,
    onHelpClick: () -> Unit,
    onLocalAudiobookSourceHelpClick: () -> Unit,
    onAudiobookClick: (Audiobook) -> Unit,
    onAudiobookLongClick: (Audiobook) -> Unit,
) {
    val context = LocalContext.current

    val errorState = audiobookList.loadState.refresh.takeIf { it is LoadState.Error }
        ?: audiobookList.loadState.append.takeIf { it is LoadState.Error }

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        with(context) { state.error.formattedMessage }
    }

    LaunchedEffect(errorState) {
        if (audiobookList.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> audiobookList.retry()
            }
        }
    }

    if (audiobookList.itemCount <= 0 && errorState != null && errorState is LoadState.Error) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = getErrorMessage(errorState),
            actions = if (source is LocalAudiobookSource) {
                persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.local_source_help_guide,
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = onLocalAudiobookSourceHelpClick,
                    ),
                )
            } else {
                persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = audiobookList::refresh,
                    ),
                    EmptyScreenAction(
                        stringRes = MR.strings.action_open_in_web_view,
                        icon = Icons.Outlined.Public,
                        onClick = onWebViewClick,
                    ),
                    EmptyScreenAction(
                        stringRes = MR.strings.label_help,
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        onClick = onHelpClick,
                    ),
                )
            },
        )

        return
    }

    if (audiobookList.itemCount == 0 && audiobookList.loadState.refresh is LoadState.Loading) {
        LoadingScreen(
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid -> {
            BrowseAudiobookSourceComfortableGrid(
                audiobookList = audiobookList,
                columns = columns,
                contentPadding = contentPadding,
                onAudiobookClick = onAudiobookClick,
                onAudiobookLongClick = onAudiobookLongClick,
            )
        }
        LibraryDisplayMode.List -> {
            BrowseAudiobookSourceList(
                audiobookList = audiobookList,
                contentPadding = contentPadding,
                onAudiobookClick = onAudiobookClick,
                onAudiobookLongClick = onAudiobookLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
            BrowseAudiobookSourceCompactGrid(
                audiobookList = audiobookList,
                columns = columns,
                contentPadding = contentPadding,
                onAudiobookClick = onAudiobookClick,
                onAudiobookLongClick = onAudiobookLongClick,
            )
        }
    }
}

@Composable
internal fun MissingSourceScreen(
    source: StubAudiobookSource,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = source.name,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        EmptyScreen(
            message = stringResource(MR.strings.source_not_installed, source.toString()),
            modifier = Modifier.padding(paddingValues),
        )
    }
}
