package eu.kanade.presentation.browse.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.audiobook.components.BaseAudiobookListItem
import eu.kanade.tachiyomi.ui.browse.audiobook.migration.audiobook.MigrateAudiobookScreenModel
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateAudiobookScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateAudiobookScreenModel.State,
    onClickItem: (Audiobook) -> Unit,
    onClickCover: (Audiobook) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateAudiobookContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
        )
    }
}

@Composable
private fun MigrateAudiobookContent(
    contentPadding: PaddingValues,
    state: MigrateAudiobookScreenModel.State,
    onClickItem: (Audiobook) -> Unit,
    onClickCover: (Audiobook) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.titles) { audiobook ->
            MigrateAudiobookItem(
                audiobook = audiobook,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
private fun MigrateAudiobookItem(
    audiobook: Audiobook,
    onClickItem: (Audiobook) -> Unit,
    onClickCover: (Audiobook) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseAudiobookListItem(
        modifier = modifier,
        audiobook = audiobook,
        onClickItem = { onClickItem(audiobook) },
        onClickCover = { onClickCover(audiobook) },
    )
}
