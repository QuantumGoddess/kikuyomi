package eu.kanade.tachiyomi.ui.deeplink.audiobook

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.GlobalAudiobookSearchScreen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreen
import eu.kanade.tachiyomi.ui.audioplayer.AudioplayerActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class DeepLinkAudiobookScreen(
    val query: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            DeepLinkAudiobookScreenModel(query = query)
        }
        val state by screenModel.state.collectAsState()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.action_search_hint),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (state) {
                is DeepLinkAudiobookScreenModel.State.Loading -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }
                is DeepLinkAudiobookScreenModel.State.NoResults -> {
                    navigator.replace(GlobalAudiobookSearchScreen(query))
                }
                is DeepLinkAudiobookScreenModel.State.Result -> {
                    val resultState = state as DeepLinkAudiobookScreenModel.State.Result
                    if (resultState.chapterId == null) {
                        navigator.replace(
                            AudiobookScreen(
                                resultState.audiobook.id,
                                true,
                            ),
                        )
                    } else {
                        navigator.pop()
                        AudioplayerActivity.newIntent(
                            context,
                            resultState.audiobook.id,
                            resultState.chapterId,
                        ).also(context::startActivity)
                    }
                }
            }
        }
    }
}
