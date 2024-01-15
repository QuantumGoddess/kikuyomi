package eu.kanade.tachiyomi.ui.browse.audiobook.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.audiobook.AudiobookSourceOptionsDialog
import eu.kanade.presentation.browse.audiobook.AudiobookSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.BrowseAudiobookSourceScreen
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.GlobalAudiobookSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.audiobookSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AudiobookSourcesScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_audiobook_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalAudiobookSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(AudiobookSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            AudiobookSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    navigator.push(BrowseAudiobookSourceScreen(source.id, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
            )

            state.dialog?.let { dialog ->
                val source = dialog.source
                AudiobookSourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        screenModel.togglePin(source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(source)
                        screenModel.closeDialog()
                    },
                    onDismiss = screenModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        AudiobookSourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
