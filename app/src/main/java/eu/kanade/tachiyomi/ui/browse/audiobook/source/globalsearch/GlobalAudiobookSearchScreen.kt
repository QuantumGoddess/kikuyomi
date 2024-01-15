package eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.BrowseAudiobookSourceScreen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.presentation.browse.audiobook.GlobalAudiobookSearchScreen

class GlobalAudiobookSearchScreen(
    val searchQuery: String = "",
    private val extensionFilter: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalAudiobookSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()
        var showSingleLoadingScreen by remember {
            mutableStateOf(
                searchQuery.isNotEmpty() && !extensionFilter.isNullOrEmpty() && state.total == 1,
            )
        }

        if (showSingleLoadingScreen) {
            LoadingScreen()

            LaunchedEffect(state.items) {
                when (val result = state.items.values.singleOrNull()) {
                    AudiobookSearchItemResult.Loading -> return@LaunchedEffect
                    is AudiobookSearchItemResult.Success -> {
                        val audiobook = result.result.singleOrNull()
                        if (audiobook != null) {
                            navigator.replace(AudiobookScreen(audiobook.id, true))
                        } else {
                            // Backoff to result screen
                            showSingleLoadingScreen = false
                        }
                    }
                    else -> showSingleLoadingScreen = false
                }
            }
        } else {
            GlobalAudiobookSearchScreen(
                state = state,
                navigateUp = navigator::pop,
                onChangeSearchQuery = screenModel::updateSearchQuery,
                onSearch = { screenModel.search() },
                getAudiobook = { screenModel.getAudiobook(it) },
                onChangeSearchFilter = screenModel::setSourceFilter,
                onToggleResults = screenModel::toggleFilterResults,
                onClickSource = {
                    navigator.push(BrowseAudiobookSourceScreen(it.id, state.searchQuery))
                },
                onClickItem = { navigator.push(AudiobookScreen(it.id, true)) },
                onLongClickItem = { navigator.push(AudiobookScreen(it.id, true)) },
            )
        }
    }
}
