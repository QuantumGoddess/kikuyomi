package eu.kanade.presentation.updates.audiobook

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.audiobook.components.ChapterDownloadAction
import eu.kanade.presentation.entries.audiobook.components.ChapterDownloadIndicator
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.ui.updates.audiobook.AudiobookUpdatesItem
import tachiyomi.domain.updates.audiobook.model.AudiobookUpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.ReadItemAlpha
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import java.util.concurrent.TimeUnit

internal fun LazyListScope.audiobookUpdatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "audiobookUpdates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItemPlacement()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
        ) {
            Text(
                text = stringResource(MR.strings.updates_last_update_info, relativeTimeSpanString(lastUpdated)),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

internal fun LazyListScope.audiobookUpdatesUiItems(
    uiModels: List<AudiobookUpdatesUiModel>,
    selectionMode: Boolean,
    onUpdateSelected: (AudiobookUpdatesItem, Boolean, Boolean, Boolean) -> Unit,
    onClickCover: (AudiobookUpdatesItem) -> Unit,
    onClickUpdate: (AudiobookUpdatesItem, altPlayer: Boolean) -> Unit,
    onDownloadChapter: (List<AudiobookUpdatesItem>, ChapterDownloadAction) -> Unit,
) {
    items(
        items = uiModels,
        contentType = {
            when (it) {
                is AudiobookUpdatesUiModel.Header -> "header"
                is AudiobookUpdatesUiModel.Item -> "item"
            }
        },
        key = {
            when (it) {
                is AudiobookUpdatesUiModel.Header -> "audiobookUpdatesHeader-${it.hashCode()}"
                is AudiobookUpdatesUiModel.Item -> "audiobookUpdates-${it.item.update.audiobookId}-${it.item.update.chapterId}"
            }
        },
    ) { item ->
        when (item) {
            is AudiobookUpdatesUiModel.Header -> {
                ListGroupHeader(
                    modifier = Modifier.animateItemPlacement(),
                    text = item.date,
                )
            }
            is AudiobookUpdatesUiModel.Item -> {
                val updatesItem = item.item
                AudiobookUpdatesUiItem(
                    modifier = Modifier.animateItemPlacement(),
                    update = updatesItem.update,
                    selected = updatesItem.selected,
                    watchProgress = updatesItem.update.lastSecondRead
                        .takeIf { !updatesItem.update.read && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.chapter_progress,
                                formatProgress(it),
                                formatProgress(updatesItem.update.totalSeconds),
                            )
                        },
                    onLongClick = {
                        onUpdateSelected(updatesItem, !updatesItem.selected, true, true)
                    },
                    onClick = {
                        when {
                            selectionMode -> onUpdateSelected(
                                updatesItem,
                                !updatesItem.selected,
                                true,
                                false,
                            )
                            else -> onClickUpdate(updatesItem, false)
                        }
                    },
                    onClickCover = { onClickCover(updatesItem) }.takeIf { !selectionMode },
                    onDownloadChapter = { action: ChapterDownloadAction ->
                        onDownloadChapter(listOf(updatesItem), action)
                    }.takeIf { !selectionMode },
                    downloadStateProvider = updatesItem.downloadStateProvider,
                    downloadProgressProvider = updatesItem.downloadProgressProvider,
                )
            }
        }
    }
}

@Composable
private fun AudiobookUpdatesUiItem(
    update: AudiobookUpdatesWithRelations,
    selected: Boolean,
    watchProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> AudiobookDownload.State,
    downloadProgressProvider: () -> Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.read) ReadItemAlpha else 1f

    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = update.coverData,
            onClick = onClickCover,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = update.audiobookTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableIntStateOf(0) }
                if (!update.read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = stringResource(MR.strings.unread),
                        modifier = Modifier
                            .height(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(
                                maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp },
                            ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.chapterName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier
                        .weight(weight = 1f, fill = false),
                )
                if (watchProgress != null) {
                    DotSeparatorText()
                    Text(
                        text = watchProgress,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = ReadItemAlpha),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ChapterDownloadIndicator(
            enabled = onDownloadChapter != null,
            modifier = Modifier.padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadChapter?.invoke(it) },
        )
    }
}

private fun formatProgress(milliseconds: Long): String {
    return if (milliseconds > 3600000L) {
        String.format(
            "%d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(milliseconds),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else {
        String.format(
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    }
}
