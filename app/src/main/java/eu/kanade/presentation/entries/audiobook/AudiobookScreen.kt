package eu.kanade.presentation.entries.audiobook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.entries.audiobook.model.chaptersFiltered
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.EntryScreenItem
import eu.kanade.presentation.entries.audiobook.components.AudiobookActionRow
import eu.kanade.presentation.entries.audiobook.components.AudiobookChapterListItem
import eu.kanade.presentation.entries.audiobook.components.AudiobookInfoBox
import eu.kanade.presentation.entries.audiobook.components.ChapterDownloadAction
import eu.kanade.presentation.entries.audiobook.components.ExpandableAudiobookDescription
import eu.kanade.presentation.entries.audiobook.components.NextChapterAiringListItem
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.EntryToolbar
import eu.kanade.presentation.entries.components.ItemHeader
import eu.kanade.presentation.entries.components.MissingItemCountListItem
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.audiobooksource.ConfigurableAudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.source.audiobook.getNameForAudiobookInfo
import eu.kanade.tachiyomi.ui.browse.audiobook.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.entries.audiobook.AudiobookScreenModel
import eu.kanade.tachiyomi.ui.entries.audiobook.ChapterList
import eu.kanade.tachiyomi.util.lang.toRelativeString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.delay
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.service.missingChaptersCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.isScrolledToEnd
import tachiyomi.presentation.core.util.isScrollingUp
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun AudiobookScreen(
    state: AudiobookScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    fetchInterval: Int?,
    dateRelativeTime: Boolean,
    dateFormat: DateFormat,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    alwaysUseExternalPlayer: Boolean,
    onBackClicked: () -> Unit,
    onChapterClicked: (chapter: Chapter, alt: Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueListening: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAudiobookSkipIntro: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(SourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableAudiobookSource }

    if (!isTabletUi) {
        AudiobookScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            dateRelativeTime = dateRelativeTime,
            dateFormat = dateFormat,
            fetchInterval = fetchInterval,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            onBackClicked = onBackClicked,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueListening = onContinueListening,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
        )
    } else {
        AudiobookScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            dateRelativeTime = dateRelativeTime,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            dateFormat = dateFormat,
            fetchInterval = fetchInterval,
            onBackClicked = onBackClicked,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueListening = onContinueListening,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudiobookScreenSmallImpl(
    state: AudiobookScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    dateRelativeTime: Boolean,
    dateFormat: DateFormat,
    fetchInterval: Int?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    alwaysUseExternalPlayer: Boolean,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueListening: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val chapterListState = rememberLazyListState()

    val chapters = remember(state) { state.processedChapters }
    val listItem = remember(state) { state.chapterListItems }

    val isAnySelected by remember {
        derivedStateOf {
            chapters.fastAny { it.selected }
        }
    }

    val internalOnBackPressed = {
        if (isAnySelected) {
            onAllChapterSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)
    Scaffold(
        topBar = {
            val selectedChapterCount: Int = remember(chapters) {
                chapters.count { it.selected }
            }
            val isFirstItemVisible by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex == 0 }
            }
            val isFirstItemScrolled by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset > 0 }
            }
            val animatedTitleAlpha by animateFloatAsState(
                if (!isFirstItemVisible) 1f else 0f,
                label = "Top Bar Title",
            )
            val animatedBgAlpha by animateFloatAsState(
                if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                label = "Top Bar Background",
            )
            EntryToolbar(
                title = state.audiobook.title,
                titleAlphaProvider = { animatedTitleAlpha },
                backgroundAlphaProvider = { animatedBgAlpha },
                hasFilters = state.audiobook.chaptersFiltered(),
                onBackClicked = internalOnBackPressed,
                onClickFilter = onFilterClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickSettings = onSettingsClicked,
                changeAnimeSkipIntro = null,
                actionModeCounter = selectedChapterCount,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                isManga = false,
            )
        },
        bottomBar = {
            val selectedChapters = remember(chapters) {
                chapters.filter { it.selected }
            }
            SharedAudiobookBottomActionMenu(
                selected = selectedChapters,
                onChapterClicked = onChapterClicked,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onDownloadChapter = onDownloadChapter,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = 1f,
                alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            val isFABVisible = remember(chapters) {
                chapters.fastAny { !it.chapter.read } && !isAnySelected
            }
            AnimatedVisibility(
                visible = isFABVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    text = {
                        val isListening = remember(state.chapters) {
                            state.chapters.fastAny { it.chapter.read }
                        }
                        Text(
                            text = stringResource(
                                if (isListening) MR.strings.action_resume else MR.strings.action_start,
                            ),
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    onClick = onContinueListening,
                    expanded = chapterListState.isScrollingUp() || chapterListState.isScrolledToEnd(),
                )
            }
        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = { !isAnySelected },
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            val layoutDirection = LocalLayoutDirection.current
            VerticalFastScroller(
                listState = chapterListState,
                topContentPadding = topPadding,
                endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = chapterListState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = EntryScreenItem.INFO_BOX,
                        contentType = EntryScreenItem.INFO_BOX,
                    ) {
                        AudiobookInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            title = state.audiobook.title,
                            author = state.audiobook.author,
                            artist = state.audiobook.artist,
                            sourceName = remember { state.source.getNameForAudiobookInfo() },
                            isStubSource = remember { state.source is StubAudiobookSource },
                            coverDataProvider = { state.audiobook },
                            status = state.audiobook.status,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                    }

                    item(
                        key = EntryScreenItem.ACTION_ROW,
                        contentType = EntryScreenItem.ACTION_ROW,
                    ) {
                        AudiobookActionRow(
                            favorite = state.audiobook.favorite,
                            trackingCount = state.trackingCount,
                            fetchInterval = fetchInterval,
                            isUserIntervalMode = state.audiobook.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                        )
                    }

                    item(
                        key = EntryScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = EntryScreenItem.DESCRIPTION_WITH_TAG,
                    ) {
                        ExpandableAudiobookDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.audiobook.description,
                            tagsProvider = { state.audiobook.genre },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                        )
                    }

                    item(
                        key = EntryScreenItem.ITEM_HEADER,
                        contentType = EntryScreenItem.ITEM_HEADER,
                    ) {
                        val missingChaptersCount = remember(chapters) {
                            chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                        }
                        ItemHeader(
                            enabled = !isAnySelected,
                            itemCount = chapters.size,
                            missingItemsCount = missingChaptersCount,
                            onClick = onFilterClicked,
                            isManga = false,
                        )
                    }



                    sharedChapterItems(
                        audiobook = state.audiobook,
                        chapters = listItem,
                        isAnyChapterSelected = chapters.fastAny { it.selected },
                        dateRelativeTime = dateRelativeTime,
                        dateFormat = dateFormat,
                        chapterSwipeStartAction = chapterSwipeStartAction,
                        chapterSwipeEndAction = chapterSwipeEndAction,
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter,
                        onChapterSelected = onChapterSelected,
                        onChapterSwipe = onChapterSwipe,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreenLargeImpl(
    state: AudiobookScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    dateRelativeTime: Boolean,
    dateFormat: DateFormat,
    fetchInterval: Int?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    alwaysUseExternalPlayer: Boolean,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueListening: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For swipe actions
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val chapters = remember(state) { state.processedChapters }
    val listItem = remember(state) { state.chapterListItems }

    val isAnySelected by remember {
        derivedStateOf {
            chapters.fastAny { it.selected }
        }
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }

    val chapterListState = rememberLazyListState()

    val internalOnBackPressed = {
        if (isAnySelected) {
            onAllChapterSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    Scaffold(
        topBar = {
            val selectedChapterCount = remember(chapters) {
                chapters.count { it.selected }
            }
            EntryToolbar(
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                title = state.audiobook.title,
                titleAlphaProvider = { if (isAnySelected) 1f else 0f },
                backgroundAlphaProvider = { 1f },
                hasFilters = state.audiobook.chaptersFiltered(),
                onBackClicked = internalOnBackPressed,
                onClickFilter = onFilterButtonClicked,
                onClickShare = onShareClicked,
                onClickDownload = onDownloadActionClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickRefresh = onRefresh,
                onClickMigrate = onMigrateClicked,
                onClickSettings = onSettingsClicked,
                changeAnimeSkipIntro = null,
                actionModeCounter = selectedChapterCount,
                onSelectAll = { onAllChapterSelected(true) },
                onInvertSelection = { onInvertSelection() },
                isManga = false,
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                val selectedChapters = remember(chapters) {
                    chapters.filter { it.selected }
                }
                SharedAudiobookBottomActionMenu(
                    selected = selectedChapters,
                    onChapterClicked = onChapterClicked,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                    onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                    onDownloadChapter = onDownloadChapter,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 0.5f,
                    alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            val isFABVisible = remember(chapters) {
                chapters.fastAny { !it.chapter.read } && !isAnySelected
            }
            AnimatedVisibility(
                visible = isFABVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    text = {
                        val isListening = remember(state.chapters) {
                            state.chapters.fastAny { it.chapter.read }
                        }
                        Text(
                            text = stringResource(
                                if (isListening) MR.strings.action_resume else MR.strings.action_start,
                            ),
                        )
                    },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueListening,
                    expanded = chapterListState.isScrollingUp() || chapterListState.isScrolledToEnd(),
                )
            }
        },
    ) { contentPadding ->
        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = { !isAnySelected },
            indicatorPadding = PaddingValues(
                start = insetPadding.calculateStartPadding(layoutDirection),
                top = with(density) { topBarHeight.toDp() },
                end = insetPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            TwoPanelBox(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
                startContent = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        AudiobookInfoBox(
                            isTabletUi = true,
                            appBarPadding = contentPadding.calculateTopPadding(),
                            title = state.audiobook.title,
                            author = state.audiobook.author,
                            artist = state.audiobook.artist,
                            sourceName = remember { state.source.getNameForAudiobookInfo() },
                            isStubSource = remember { state.source is StubAudiobookSource },
                            coverDataProvider = { state.audiobook },
                            status = state.audiobook.status,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                        AudiobookActionRow(
                            favorite = state.audiobook.favorite,
                            trackingCount = state.trackingCount,
                            fetchInterval = fetchInterval,
                            isUserIntervalMode = state.audiobook.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                        )
                        ExpandableAudiobookDescription(
                            defaultExpandState = true,
                            description = state.audiobook.description,
                            tagsProvider = { state.audiobook.genre },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                        )
                    }
                },
                endContent = {
                    VerticalFastScroller(
                        listState = chapterListState,
                        topContentPadding = contentPadding.calculateTopPadding(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            state = chapterListState,
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(
                                key = EntryScreenItem.ITEM_HEADER,
                                contentType = EntryScreenItem.ITEM_HEADER,
                            ) {
                                val missingChaptersCount = remember(chapters) {
                                    chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                                }
                                ItemHeader(
                                    enabled = !isAnySelected,
                                    itemCount = chapters.size,
                                    missingItemsCount = missingChaptersCount,
                                    onClick = onFilterButtonClicked,
                                    isManga = false,
                                )
                            }

                            sharedChapterItems(
                                audiobook = state.audiobook,
                                chapters = listItem,
                                isAnyChapterSelected = chapters.fastAny { it.selected },
                                dateRelativeTime = dateRelativeTime,
                                dateFormat = dateFormat,
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterClicked = onChapterClicked,
                                onDownloadChapter = onDownloadChapter,
                                onChapterSelected = onChapterSelected,
                                onChapterSwipe = onChapterSwipe,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SharedAudiobookBottomActionMenu(
    selected: List<ChapterList.Item>,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.bookmark } },
        onMarkAsViewedClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.read } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAny { it.chapter.read || it.chapter.lastSecondRead > 0L } },
        onMarkPreviousAsViewedClicked = {
            onMarkPreviousAsReadClicked(selected[0].chapter)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadChapter!!(selected.toList(), ChapterDownloadAction.START)
        }.takeIf {
            onDownloadChapter != null && selected.fastAny { it.downloadState != AudiobookDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.chapter })
        }.takeIf {
            onDownloadChapter != null && selected.fastAny { it.downloadState == AudiobookDownload.State.DOWNLOADED }
        },
        onExternalClicked = {
            onChapterClicked(selected.fastMap { it.chapter }.first(), true)
        }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
        onInternalClicked = {
            onChapterClicked(selected.fastMap { it.chapter }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
        isManga = false,
    )
}

private fun LazyListScope.sharedChapterItems(
    audiobook: Audiobook,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    dateRelativeTime: Boolean,
    dateFormat: DateFormat,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
) {
    items(
        items = chapters,
        key = { chapterItem ->
            when (chapterItem) {
                is ChapterList.MissingCount -> "missing-count-${chapterItem.id}"
                is ChapterList.Item -> "chapter-${chapterItem.id}"
            }
        },
        contentType = { EntryScreenItem.ITEM },
    ) { chapterItem ->
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current

        when (chapterItem) {
            is ChapterList.MissingCount -> {
                MissingItemCountListItem(count = chapterItem.count)
            }
            is ChapterList.Item -> {
                AudiobookChapterListItem(
                    title = if (audiobook.displayMode == Audiobook.CHAPTER_DISPLAY_NUMBER) {
                        stringResource(
                            MR.strings.display_mode_chapter,
                            formatChapterNumber(chapterItem.chapter.chapterNumber),
                        )
                    } else {
                        chapterItem.chapter.name
                    },
                    date = chapterItem.chapter.dateUpload
                        .takeIf { it > 0L }
                        ?.let {
                            Date(it).toRelativeString(
                                context,
                                dateRelativeTime,
                                dateFormat,
                            )
                        },
                    listenProgress = chapterItem.chapter.lastSecondRead
                        .takeIf { !chapterItem.chapter.read && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.audiobook_chapter_progress,
                                formatTime(it),
                                formatTime(chapterItem.chapter.totalSeconds),
                            )
                        },
                    scanlator = chapterItem.chapter.scanlator.takeIf { !it.isNullOrBlank() },
                    read = chapterItem.chapter.read,
                    bookmark = chapterItem.chapter.bookmark,
                    selected = chapterItem.selected,
                    downloadIndicatorEnabled = !isAnyChapterSelected,
                    downloadStateProvider = { chapterItem.downloadState },
                    downloadProgressProvider = { chapterItem.downloadProgress },
                    chapterSwipeStartAction = chapterSwipeStartAction,
                    chapterSwipeEndAction = chapterSwipeEndAction,
                    onLongClick = {
                        onChapterSelected(chapterItem, !chapterItem.selected, true, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onChapterItemClick(
                            chapterItem = chapterItem,
                            isAnyChapterSelected = isAnyChapterSelected,
                            onToggleSelection = { onChapterSelected(chapterItem, !chapterItem.selected, true, false) },
                            onChapterClicked = onChapterClicked,
                        )
                    },
                    onDownloadClick = if (onDownloadChapter != null) {
                        { onDownloadChapter(listOf(chapterItem), it) }
                    } else {
                        null
                    },
                    onChapterSwipe = {
                        onChapterSwipe(chapterItem, it)
                    },
                )
            }
        }
    }
}

private fun onChapterItemClick(
    chapterItem: ChapterList.Item,
    isAnyChapterSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onChapterClicked: (Chapter, Boolean) -> Unit,
) {
    when {
        chapterItem.selected -> onToggleSelection(false)
        isAnyChapterSelected -> onToggleSelection(true)
        else -> onChapterClicked(chapterItem.chapter, false)
    }
}

private fun formatTime(milliseconds: Long, useDayFormat: Boolean = false): String {
    return if (useDayFormat) {
        String.format(
            "Airing in %02dd %02dh %02dm %02ds",
            TimeUnit.MILLISECONDS.toDays(milliseconds),
            TimeUnit.MILLISECONDS.toHours(milliseconds) -
                TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds)),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else if (milliseconds > 3600000L) {
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
