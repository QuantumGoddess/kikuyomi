package eu.kanade.tachiyomi.ui.browse.audiobook.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.audiobook.AudiobookExtensionDetailsScreen
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

data class AudiobookExtensionDetailsScreen(
    private val pkgName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel {
            AudiobookExtensionDetailsScreenModel(
                pkgName = pkgName,
                context = context,
            )
        }
        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current

        AudiobookExtensionDetailsScreen(
            navigateUp = navigator::pop,
            state = state,
            onClickSourcePreferences = { navigator.push(SourcePreferencesScreen(it)) },
            onClickWhatsNew = { uriHandler.openUri(screenModel.getChangelogUrl()) },
            onClickReadme = { uriHandler.openUri(screenModel.getReadmeUrl()) },
            onClickEnableAll = { screenModel.toggleSources(true) },
            onClickDisableAll = { screenModel.toggleSources(false) },
            onClickClearCookies = screenModel::clearCookies,
            onClickUninstall = screenModel::uninstallExtension,
            onClickSource = screenModel::toggleSource,
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is AudiobookExtensionDetailsEvent.Uninstalled) {
                    navigator.pop()
                }
            }
        }
    }
}
