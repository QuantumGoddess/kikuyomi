package eu.kanade.tachiyomi.ui.browse.audiobook.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.presentation.browse.audiobook.MigrateAudiobookSearchScreen

class MigrateAudiobookSearchScreen(private val audiobookId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateAudiobookSearchScreenModel(audiobookId = audiobookId) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel {
            AudiobookMigrateSearchScreenDialogScreenModel(
                audiobookId = audiobookId,
            )
        }
        val dialogState by dialogScreenModel.state.collectAsState()

        MigrateAudiobookSearchScreen(
            state = state,
            fromSourceId = dialogState.audiobook?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getAudiobook = { screenModel.getAudiobook(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                navigator.push(
                    AudiobookSourceSearchScreen(dialogState.audiobook!!, it.id, state.searchQuery),
                )
            },
            onClickItem = {
                dialogScreenModel.setDialog(
                    (AudiobookMigrateSearchScreenDialogScreenModel.Dialog.Migrate(it)),
                )
            },
            onLongClickItem = { navigator.push(AudiobookScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is AudiobookMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                MigrateAudiobookDialog(
                    oldAudiobook = dialogState.audiobook!!,
                    newAudiobook = dialog.audiobook,
                    screenModel = rememberScreenModel { MigrateAudiobookDialogScreenModel() },
                    onDismissRequest = { dialogScreenModel.setDialog(null) },
                    onClickTitle = {
                        navigator.push(AudiobookScreen(dialog.audiobook.id, true))
                    },
                    onPopScreen = {
                        if (navigator.lastItem is AudiobookScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(AudiobookScreen(dialog.audiobook.id))
                        } else {
                            navigator.replace(AudiobookScreen(dialog.audiobook.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
