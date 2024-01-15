package eu.kanade.presentation.browse.audiobook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.audiobook.components.BaseAudiobookSourceItem
import eu.kanade.tachiyomi.ui.browse.audiobook.source.AudiobookSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.audiobook.source.browse.BrowseAudiobookSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import tachiyomi.domain.source.audiobook.model.Pin
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.SecondaryItemAlpha
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource

@Composable
fun AudiobookSourcesScreen(
    state: AudiobookSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (AudiobookSource, Listing) -> Unit,
    onClickPin: (AudiobookSource) -> Unit,
    onLongClickItem: (AudiobookSource) -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            ScrollbarLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(
                    items = state.items,
                    contentType = {
                        when (it) {
                            is AudiobookSourceUiModel.Header -> "header"
                            is AudiobookSourceUiModel.Item -> "item"
                        }
                    },
                    key = {
                        when (it) {
                            is AudiobookSourceUiModel.Header -> it.hashCode()
                            is AudiobookSourceUiModel.Item -> "source-${it.source.key()}"
                        }
                    },
                ) { model ->
                    when (model) {
                        is AudiobookSourceUiModel.Header -> {
                            AudiobookSourceHeader(
                                modifier = Modifier.animateItemPlacement(),
                                language = model.language,
                            )
                        }
                        is AudiobookSourceUiModel.Item -> AudiobookSourceItem(
                            modifier = Modifier.animateItemPlacement(),
                            source = model.source,
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookSourceHeader(
    language: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        text = LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun AudiobookSourceItem(
    source: AudiobookSource,
    onClickItem: (AudiobookSource, Listing) -> Unit,
    onLongClickItem: (AudiobookSource) -> Unit,
    onClickPin: (AudiobookSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseAudiobookSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            AudiobookSourcePinButton(
                isPinned = Pin.Pinned in source.pin,
                onClick = { onClickPin(source) },
            )
        },
    )
}

@Composable
private fun AudiobookSourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SecondaryItemAlpha,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun AudiobookSourceOptionsDialog(
    source: AudiobookSource,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalAudiobookSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface AudiobookSourceUiModel {
    data class Item(val source: AudiobookSource) : AudiobookSourceUiModel
    data class Header(val language: String) : AudiobookSourceUiModel
}
