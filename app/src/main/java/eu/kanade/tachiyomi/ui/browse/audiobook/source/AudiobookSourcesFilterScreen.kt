package eu.kanade.tachiyomi.ui.browse.audiobook.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.presentation.browse.audiobook.AudiobookSourcesFilterScreen

class AudiobookSourcesFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AudiobookSourcesFilterScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is AudiobookSourcesFilterScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        if (state is AudiobookSourcesFilterScreenModel.State.Error) {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.stringResource(MR.strings.internal_error)
                navigator.pop()
            }
            return
        }

        val successState = state as AudiobookSourcesFilterScreenModel.State.Success

        AudiobookSourcesFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickLanguage = screenModel::toggleLanguage,
            onClickSource = screenModel::toggleSource,
        )
    }
}
