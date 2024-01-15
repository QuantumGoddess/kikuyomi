package eu.kanade.tachiyomi.ui.browse.audiobook.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen
import eu.kanade.presentation.browse.audiobook.AudiobookExtensionFilterScreen


class AudiobookExtensionFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AudiobookExtensionFilterScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is AudiobookExtensionFilterState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AudiobookExtensionFilterState.Success

        AudiobookExtensionFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickToggle = screenModel::toggle,
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest {
                when (it) {
                    AudiobookExtensionFilterEvent.FailedFetchingLanguages -> {
                        context.stringResource(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
