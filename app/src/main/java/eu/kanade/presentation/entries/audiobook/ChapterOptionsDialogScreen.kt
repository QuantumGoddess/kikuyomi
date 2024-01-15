package eu.kanade.presentation.entries.audiobook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.audioplayer.loader.ChapterLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.util.lang.launchUI
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.interactor.GetChapter
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ChapterOptionsDialogScreen(
    private val useExternalDownloader: Boolean,
    private val chapterTitle: String,
    private val chapterId: Long,
    private val audiobookId: Long,
    private val sourceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val sm = rememberScreenModel {
            ChapterOptionsDialogScreenModel(
                chapterId = chapterId,
                audiobookId = audiobookId,
                sourceId = sourceId,
            )
        }
        val state by sm.state.collectAsState()

        ChapterOptionsDialog(
            useExternalDownloader = useExternalDownloader,
            chapterTitle = chapterTitle,
            chapter = state.chapter,
            audiobook = state.audiobook,
            resultList = state.resultList,
        )
    }

    companion object {
        var onDismissDialog: () -> Unit = {}
    }
}

class ChapterOptionsDialogScreenModel(
    chapterId: Long,
    audiobookId: Long,
    sourceId: Long,
) : StateScreenModel<State>(State()) {
    private val sourceManager: AudiobookSourceManager = Injekt.get()

    init {
        screenModelScope.launch {
            // To show loading state
            mutableState.update { it.copy(chapter = null, audiobook = null, resultList = null) }

            val chapter = Injekt.get<GetChapter>().await(chapterId)!!
            val audiobook = Injekt.get<GetAudiobook>().await(audiobookId)!!
            val source = sourceManager.getOrStub(sourceId)

            val result = withIOContext {
                try {
                    val results = ChapterLoader.getLinks(chapter, audiobook, source)
                    Result.success(results)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }

            mutableState.update { it.copy(chapter = chapter, audiobook = audiobook, resultList = result) }
        }
    }
}

@Immutable
data class State(
    val chapter: Chapter? = null,
    val audiobook: Audiobook? = null,
    val resultList: Result<List<Audio>>? = null,
)

@Composable
fun ChapterOptionsDialog(
    useExternalDownloader: Boolean,
    chapterTitle: String,
    chapter: Chapter?,
    audiobook: Audiobook?,
    resultList: Result<List<Audio>>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = TabbedDialogPaddings.Vertical)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = chapterTitle,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = stringResource(MR.strings.choose_audio_quality),
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (resultList == null || chapter == null || audiobook == null) {
            LoadingScreen()
        } else {
            val audioList = resultList.getOrNull()
            if (!audioList.isNullOrEmpty()) {
                AudioList(
                    useExternalDownloader = useExternalDownloader,
                    chapter = chapter,
                    audiobook = audiobook,
                    audioList = audioList,
                )
            } else {
                logcat(LogPriority.ERROR) { "Error getting links" }
                scope.launchUI { context.toast("Audio list is empty") }
                ChapterOptionsDialogScreen.onDismissDialog()
            }
        }
    }
}

@Composable
private fun AudioList(
    useExternalDownloader: Boolean,
    chapter: Chapter,
    audiobook: Audiobook,
    audioList: List<Audio>,
) {
    val downloadManager = Injekt.get<AudiobookDownloadManager>()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedString = stringResource(MR.strings.copied_audio_link_to_clipboard)

    var showAllQualities by remember { mutableStateOf(false) }
    var selectedAudio by remember { mutableStateOf(audioList.first()) }

    AnimatedVisibility(
        visible = !showAllQualities,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(),
    ) {
        Column {
            if (selectedAudio.audioUrl != null && !showAllQualities) {
                ClickableRow(
                    text = selectedAudio.quality,
                    icon = null,
                    onClick = { showAllQualities = true },
                    showDropdownArrow = true,
                )

                val downloadChapter: (Boolean) -> Unit = {
                    downloadManager.downloadChapters(
                        audiobook,
                        listOf(chapter),
                        true,
                        it,
                        selectedAudio,
                    )
                }

                QualityOptions(
                    onDownloadClicked = { downloadChapter(useExternalDownloader) },
                    onExtDownloadClicked = { downloadChapter(!useExternalDownloader) },
                    onCopyClicked = {
                        clipboardManager.setText(AnnotatedString(selectedAudio.audioUrl!!))
                        scope.launch { context.toast(copiedString) }
                    },
                    onExtPlayerClicked = {
                        scope.launch {
                            MainActivity.startAudioPlayerActivity(
                                context,
                                audiobook.id,
                                chapter.id,
                                true,
                                selectedAudio,
                            )
                        }
                    },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showAllQualities,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        if (showAllQualities) {
            Column {
                audioList.forEach { audio ->
                    ClickableRow(
                        text = audio.quality,
                        icon = null,
                        onClick = {
                            selectedAudio = audio
                            showAllQualities = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptions(
    onDownloadClicked: () -> Unit = {},
    onExtDownloadClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onExtPlayerClicked: () -> Unit = {},
) {
    val closeMenu = { ChapterOptionsDialogScreen.onDismissDialog() }

    Column {
        ClickableRow(
            text = stringResource(MR.strings.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onCopyClicked() },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_start_download_internally),
            icon = Icons.Outlined.Download,
            onClick = {
                onDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_start_download_externally),
            icon = Icons.Outlined.SystemUpdateAlt,
            onClick = {
                onExtDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_play_externally),
            icon = Icons.Outlined.OpenInNew,
            onClick = {
                onExtPlayerClicked()
                closeMenu()
            },
        )
    }
}

@Composable
private fun ClickableRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    showDropdownArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = TabbedDialogPaddings.Horizontal)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var textPadding = MaterialTheme.padding.medium

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            textPadding = MaterialTheme.padding.small
        }
        Text(
            text = text,
            modifier = Modifier.padding(vertical = textPadding),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (showDropdownArrow) {
            Icon(
                imageVector = Icons.Outlined.NavigateNext,
                contentDescription = null,
                modifier = Modifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
