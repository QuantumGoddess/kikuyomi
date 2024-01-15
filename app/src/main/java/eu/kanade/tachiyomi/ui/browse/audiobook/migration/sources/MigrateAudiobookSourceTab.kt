package eu.kanade.tachiyomi.ui.browse.audiobook.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.audiobook.MigrateAudiobookSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.audiobook.migration.audiobook.MigrateAudiobookScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.migrateAudiobookSourceTab(): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { MigrateAudiobookSourceScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_migration_audiobook,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.migration_help_guide),
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                onClick = {
                    uriHandler.openUri("https://aniyomi.org/help/guides/source-migration/")
                },
            ),
        ),
        content = { contentPadding, _ ->
            MigrateAudiobookSourceScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrateAudiobookScreen(source.id))
                },
                onToggleSortingDirection = screenModel::toggleSortingDirection,
                onToggleSortingMode = screenModel::toggleSortingMode,
            )
        },
    )
}
