package eu.kanade.tachiyomi.ui.browse.audiobook.migration.audiobook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.audiobook.migration.search.MigrateAudiobookSearchScreen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.presentation.browse.audiobook.MigrateAudiobookScreen


data class MigrateAudiobookScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateAudiobookScreenModel(sourceId) }

        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        MigrateAudiobookScreen(
            navigateUp = navigator::pop,
            title = state.source!!.name,
            state = state,
            onClickItem = { navigator.push(MigrateAudiobookSearchScreen(it.id)) },
            onClickCover = { navigator.push(AudiobookScreen(it.id)) },
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    MigrationAudiobookEvent.FailedFetchingFavorites -> {
                        context.stringResource(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
