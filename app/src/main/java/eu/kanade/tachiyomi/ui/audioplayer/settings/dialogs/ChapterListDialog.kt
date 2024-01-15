package eu.kanade.tachiyomi.ui.audioplayer.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.database.models.audiobook.Chapter
import eu.kanade.tachiyomi.util.lang.toRelativeString
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.ReadItemAlpha
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DateFormat
import java.util.Date

@Composable
fun ChapterListDialog(
    displayMode: Long,
    currentChapterIndex: Int,
    chapterList: List<Chapter>,
    dateRelativeTime: Boolean,
    dateFormat: DateFormat,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onChapterClicked: (Long?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val itemScrollIndex = (chapterList.size - currentChapterIndex) - 1
    val chapterListState = rememberLazyListState(initialFirstVisibleItemIndex = itemScrollIndex)

    AudioplayerDialog(
        titleRes = MR.strings.chapters,
        modifier = Modifier.fillMaxHeight(fraction = 0.8F).fillMaxWidth(fraction = 0.8F),
        onDismissRequest = onDismissRequest,
    ) {
        VerticalFastScroller(
            listState = chapterListState,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                state = chapterListState,
            ) {
                items(
                    items = chapterList.reversed(),
                    key = { "chapter-${it.id}" },
                    contentType = { "chapter" },
                ) { chapter ->

                    val isCurrentChapter = chapter.id == chapterList[currentChapterIndex].id

                    val title = if (displayMode == Audiobook.CHAPTER_DISPLAY_NUMBER) {
                        stringResource(
                            MR.strings.display_mode_chapter,
                            formatChapterNumber(chapter.chapter_number.toDouble()),
                        )
                    } else {
                        chapter.name
                    }

                    val date = chapter.date_upload
                        .takeIf { it > 0L }
                        ?.let {
                            Date(it).toRelativeString(context, dateRelativeTime, dateFormat)
                        } ?: ""

                    ChapterListItem(
                        chapter = chapter,
                        isCurrentChapter = isCurrentChapter,
                        title = title,
                        date = date,
                        onBookmarkClicked = onBookmarkClicked,
                        onChapterClicked = onChapterClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    chapter: Chapter,
    isCurrentChapter: Boolean,
    title: String,
    date: String?,
    onBookmarkClicked: (Long?, Boolean) -> Unit,
    onChapterClicked: (Long?) -> Unit,
) {
    var isBookmarked by remember { mutableStateOf(chapter.bookmark) }
    var textHeight by remember { mutableStateOf(0) }

    val bookmarkIcon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.Bookmark
    val bookmarkAlpha = if (isBookmarked) 1f else ReadItemAlpha
    val chapterColor = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val textAlpha = if (chapter.read) ReadItemAlpha else 1f
    val textWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal
    val textStyle = if (isCurrentChapter) FontStyle.Italic else FontStyle.Normal

    val clickBookmark: (Boolean) -> Unit = { bookmarked ->
        chapter.bookmark = bookmarked
        isBookmarked = bookmarked
        onBookmarkClicked(chapter.id, bookmarked)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onChapterClicked(chapter.id) })
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        IconButton(onClick = { clickBookmark(!isBookmarked) }) {
            Icon(
                imageVector = bookmarkIcon,
                contentDescription = null,
                tint = chapterColor,
                modifier = Modifier
                    .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp })
                    .alpha(bookmarkAlpha),
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = chapterColor,
                modifier = Modifier.alpha(textAlpha),
                onTextLayout = { textHeight = it.size.height },
                fontWeight = textWeight,
                fontStyle = textStyle,
            )

            Row {
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = chapterColor,
                        modifier = Modifier.alpha(textAlpha),
                        fontWeight = textWeight,
                        fontStyle = textStyle,
                    )
                    if (chapter.scanlator != null) DotSeparatorText()
                }
                if (chapter.scanlator != null) {
                    Text(
                        text = chapter.scanlator!!,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = chapterColor,
                        modifier = Modifier.alpha(textAlpha),
                        fontWeight = textWeight,
                        fontStyle = textStyle,
                    )
                }
            }
        }
    }
}
