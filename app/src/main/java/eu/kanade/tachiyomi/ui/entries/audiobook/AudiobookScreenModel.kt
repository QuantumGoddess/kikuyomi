package eu.kanade.tachiyomi.ui.entries.audiobook

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.entries.audiobook.interactor.SetAudiobookViewerFlags
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.downloadedFilter
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.domain.items.audiobookchapter.interactor.SetReadStatus
import eu.kanade.domain.items.audiobookchapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.track.audiobook.interactor.AddAudiobookTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.audiobook.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.data.track.AudiobookTracker
import eu.kanade.tachiyomi.data.track.EnhancedAudiobookTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.ui.entries.audiobook.track.AudiobookTrackItem
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.AniChartApi
import eu.kanade.tachiyomi.util.audiobookchapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.preference.CheckboxState
import tachiyomi.core.preference.TriState
import tachiyomi.core.preference.mapAsCheckboxState
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.audiobook.interactor.GetAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookWithChapters
import tachiyomi.domain.entries.audiobook.interactor.GetDuplicateLibraryAudiobook
import tachiyomi.domain.entries.audiobook.interactor.SetAudiobookChapterFlags
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.items.audiobookchapter.interactor.SetAudiobookDefaultChapterFlags
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate
import tachiyomi.domain.items.audiobookchapter.model.NoChaptersException
import tachiyomi.domain.items.audiobookchapter.service.calculateChapterGap
import tachiyomi.domain.items.audiobookchapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import kotlin.math.floor

class AudiobookScreenModel(
    val context: Context,
    val audiobookId: Long,
    private val isFromSource: Boolean,
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val downloadManager: AudiobookDownloadManager = Injekt.get(),
    private val downloadCache: AudiobookDownloadCache = Injekt.get(),
    private val getAudiobookAndChapters: GetAudiobookWithChapters = Injekt.get(),
    private val getDuplicateLibraryAudiobook: GetDuplicateLibraryAudiobook = Injekt.get(),
    private val setAudiobookChapterFlags: SetAudiobookChapterFlags = Injekt.get(),
    private val setAudiobookDefaultChapterFlags: SetAudiobookDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateAudiobook: UpdateAudiobook = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCategories: GetAudiobookCategories = Injekt.get(),
    private val getTracks: GetAudiobookTracks = Injekt.get(),
    private val addTracks: AddAudiobookTracks = Injekt.get(),
    private val setAudiobookCategories: SetAudiobookCategories = Injekt.get(),
    private val audiobookRepository: AudiobookRepository = Injekt.get(),
    internal val setAudiobookViewerFlags: SetAudiobookViewerFlags = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<AudiobookScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    val loggedInTrackers by lazy { trackerManager.trackers.filter { it.isLoggedIn && it is AudiobookTracker } }

    val audiobook: Audiobook?
        get() = successState?.audiobook

    val source: AudiobookSource?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = audiobook?.favorite ?: false

    private val processedChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeChapterEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeChapterStartAction().get()

    val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer().get()
    val useExternalDownloader = downloadPreferences.useExternalDownloader().get()

    val relativeTime by uiPreferences.relativeTime().asState(screenModelScope)
    val dateFormat by mutableStateOf(UiPreferences.dateFormat(uiPreferences.dateFormat().get()))

    val isUpdateIntervalEnabled =
        LibraryPreferences.ENTRY_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateItemRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedChapterIds: HashSet<Long> = HashSet()

    internal var isFromChangeCategory: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.trackingAvailable == true && trackPreferences.trackOnAddingToLibrary().get()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            combine(
                getAudiobookAndChapters.subscribe(audiobookId).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { audiobookAndChapters, _, _ -> audiobookAndChapters }
                .collectLatest { (audiobook, chapters) ->
                    updateSuccessState {
                        it.copy(
                            audiobook = audiobook,
                            chapters = chapters.toChapterListItems(audiobook),
                        )
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val audiobook = getAudiobookAndChapters.awaitAudiobook(audiobookId)
            val chapters = getAudiobookAndChapters.awaitChapters(audiobookId)
                .toChapterListItems(audiobook)

            if (!audiobook.favorite) {
                setAudiobookDefaultChapterFlags.await(audiobook)
            }

            val needRefreshInfo = !audiobook.initialized
            val needRefreshChapter = chapters.isEmpty()

            // Show what we have earlier
            mutableState.update {
                State.Success(
                    audiobook = audiobook,
                    source = Injekt.get<AudiobookSourceManager>().getOrStub(audiobook.source),
                    isFromSource = isFromSource,
                    chapters = chapters,
                    isRefreshingData = needRefreshInfo || needRefreshChapter,
                    dialog = null,
                )
            }
            // Start observe tracking since it only needs audiobookId
            observeTrackers()

            // Fetch info-chapters when needed
            if (screenModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchAudiobookFromSource() },
                    async { if (needRefreshChapter) fetchChaptersFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
                async { fetchAudiobookFromSource(manualFetch) },
                async { fetchChaptersFromSource(manualFetch) },
            )
            fetchFromSourceTasks.awaitAll()
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    // Audiobook info - start

    /**
     * Fetch audiobook information from source.
     */
    private suspend fun fetchAudiobookFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val networkAudiobook = state.source.getAudiobookDetails(state.audiobook.toSAudiobook())
                updateAudiobook.awaitUpdateFromSource(state.audiobook, networkAudiobook, manualFetch)
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                screenModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.delete_downloads_for_manga),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of audiobook, (removes / adds) audiobook (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val audiobook = state.audiobook

            if (isFavorited) {
                // Remove from library
                if (updateAudiobook.awaitUpdateFavorite(audiobook.id, false)) {
                    // Remove covers and update last modified in db
                    if (audiobook.removeCovers() != audiobook) {
                        updateAudiobook.awaitUpdateCoverLastModified(audiobook.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryAudiobook.await(audiobook).getOrNull(0)
                    if (duplicate != null) {
                        updateSuccessState {
                            it.copy(
                                dialog = Dialog.DuplicateAudiobook(audiobook, duplicate),
                            )
                        }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultAudiobookCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateAudiobook.awaitUpdateFavorite(audiobook.id, true)
                        if (!result) return@launchIO
                        moveAudiobookToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateAudiobook.awaitUpdateFavorite(audiobook.id, true)
                        if (!result) return@launchIO
                        moveAudiobookToCategory(null)
                    }

                    // Choose a category
                    else -> {
                        isFromChangeCategory = true
                        showChangeCategoryDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(audiobook, state.source)
                if (autoOpenTrack) {
                    showTrackDialog()
                }
            }
        }
    }

    fun showChangeCategoryDialog() {
        val audiobook = successState?.audiobook ?: return
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getAudiobookCategoryIds(audiobook)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        audiobook = audiobook,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection },
                    ),
                )
            }
        }
    }

    fun showSetAudiobookFetchIntervalDialog() {
        val audiobook = successState?.audiobook ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetAudiobookFetchInterval(audiobook))
        }
    }

    fun setFetchInterval(audiobook: Audiobook, interval: Int) {
        screenModelScope.launchIO {
            updateAudiobook.awaitUpdateFetchInterval(
                // Custom intervals are negative
                audiobook.copy(fetchInterval = -interval),
            )
            val updatedAudiobook = audiobookRepository.getAudiobookById(audiobook.id)
            updateSuccessState { it.copy(audiobook = updatedAudiobook) }
        }
    }

    /**
     * Returns true if the audiobook has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val audiobook = successState?.audiobook ?: return false
        return downloadManager.getDownloadCount(audiobook) > 0
    }

    /**
     * Deletes all the downloads for the audiobook.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteAudiobook(state.audiobook, state.source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the audiobook is in, if the audiobook is not in a category, returns the default id.
     *
     * @param audiobook the audiobook to get categories from.
     * @return Array of category ids the audiobook is in, if none returns default id
     */
    private suspend fun getAudiobookCategoryIds(audiobook: Audiobook): List<Long> {
        return getCategories.await(audiobook.id)
            .map { it.id }
    }

    fun moveAudiobookToCategoriesAndAddToLibrary(audiobook: Audiobook, categories: List<Long>) {
        moveAudiobookToCategory(categories)
        if (audiobook.favorite) return

        screenModelScope.launchIO {
            updateAudiobook.awaitUpdateFavorite(audiobook.id, true)
        }
    }

    /**
     * Move the given audiobook to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveAudiobookToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveAudiobookToCategory(categoryIds)
    }

    private fun moveAudiobookToCategory(categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setAudiobookCategories.await(audiobookId, categoryIds)
        }
    }

    /**
     * Move the given audiobook to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveAudiobookToCategory(category: Category?) {
        moveAudiobookToCategories(listOfNotNull(category))
    }

    // Audiobook info - end

    // Chapters list - start

    private fun observeDownloads() {
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.audiobook.id == successState?.audiobook?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            downloadManager.progressFlow()
                .filter { it.audiobook.id == successState?.audiobook?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: AudiobookDownload) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.chapters.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newChapters = successState.chapters.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(chapters = newChapters)
        }
    }

    private fun List<Chapter>.toChapterListItems(audiobook: Audiobook): List<ChapterList.Item> {
        val isLocal = audiobook.isLocal()
        return map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    audiobook.title,
                    audiobook.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> AudiobookDownload.State.DOWNLOADED
                else -> AudiobookDownload.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
            )
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val chapters = state.source.getChapterList(state.audiobook.toSAudiobook())

                val newChapters = syncChaptersWithSource.await(
                    chapters,
                    state.audiobook,
                    state.source,
                    manualFetch,
                )

                if (manualFetch) {
                    downloadNewChapters(newChapters)
                }
            }
        } catch (e: Throwable) {
            val message = if (e is NoChaptersException) {
                context.stringResource(MR.strings.no_chapters_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newAudiobook = audiobookRepository.getAudiobookById(audiobookId)
            updateSuccessState { it.copy(audiobook = newAudiobook, isRefreshingData = false) }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        screenModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    AudiobookDownload.State.ERROR,
                    AudiobookDownload.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    AudiobookDownload.State.QUEUE,
                    AudiobookDownload.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    AudiobookDownload.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): Chapter? {
        val successState = successState ?: return null
        return successState.chapters.getNextUnread(successState.audiobook)
    }

    private fun getUnreadChapters(): List<Chapter> {
        return successState?.processedChapters
            ?.filter { (chapter, dlStatus) -> !chapter.read && dlStatus == AudiobookDownload.State.NOT_DOWNLOADED }
            ?.map { it.chapter }
            ?.toList()
            ?: emptyList()
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val audiobook = successState?.audiobook ?: return emptyList()
        val chapters = getUnreadChapters().sortedWith(getChapterSort(audiobook))
        return if (audiobook.sortDescending()) chapters.reversed() else chapters
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
        audio: Audio? = null,
    ) {
        val successState = successState ?: return

        if (startNow) {
            val chapterId = chapters.singleOrNull()?.id ?: return
            downloadManager.startDownloadNow(chapterId)
        } else {
            downloadChapters(chapters, false, audio)
        }
        if (!isFavorited && !successState.hasPromptedToAddBefore) {
            updateSuccessState { state ->
                state.copy(hasPromptedToAddBefore = true)
            }
            screenModelScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.snack_add_to_audiobook_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == AudiobookDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = items.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(items.map { it.chapter })
            }
            ChapterDownloadAction.SHOW_QUALITIES -> {
                val chapter = items.singleOrNull()?.chapter ?: return
                showQualitiesDialog(chapter)
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_ITEM -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_ITEMS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_ITEMS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_ITEMS -> getUnreadChaptersSorted().take(25)

            DownloadAction.UNVIEWED_ITEMS -> getUnreadChapters()
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = AudiobookDownload.State.NOT_DOWNLOADED })
    }

    fun markPreviousChapterRead(pointer: Chapter) {
        val audiobook = successState?.audiobook ?: return
        val chapters = processedChapters.orEmpty().map { it.chapter }.toList()
        val prevChapters = if (audiobook.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        screenModelScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(
        chapters: List<Chapter>,
        alt: Boolean = false,
        audio: Audio? = null,
    ) {
        val audiobook = successState?.audiobook ?: return
        downloadManager.downloadChapters(audiobook, chapters, true, alt, audio)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteChapters(
                        chapters,
                        state.audiobook,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewChapters(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            val audiobook = successState?.audiobook ?: return@launchNonCancellable
            val categories = getCategories.await(audiobook.id).map { it.id }
            if (chapters.isEmpty() || !audiobook.shouldDownloadNewChapters(
                    categories,
                    downloadPreferences,
                )
            ) {
                return@launchNonCancellable
            }
            downloadChapters(chapters)
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val audiobook = successState?.audiobook ?: return

        val flag = when (state) {
            TriState.DISABLED -> Audiobook.SHOW_ALL
            TriState.ENABLED_IS -> Audiobook.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Audiobook.CHAPTER_SHOW_READ
        }
        screenModelScope.launchNonCancellable {
            setAudiobookChapterFlags.awaitSetUnreadFilter(audiobook, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val audiobook = successState?.audiobook ?: return

        val flag = when (state) {
            TriState.DISABLED -> Audiobook.SHOW_ALL
            TriState.ENABLED_IS -> Audiobook.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Audiobook.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setAudiobookChapterFlags.awaitSetDownloadedFilter(audiobook, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val audiobook = successState?.audiobook ?: return

        val flag = when (state) {
            TriState.DISABLED -> Audiobook.SHOW_ALL
            TriState.ENABLED_IS -> Audiobook.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Audiobook.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setAudiobookChapterFlags.awaitSetBookmarkFilter(audiobook, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val audiobook = successState?.audiobook ?: return

        screenModelScope.launchNonCancellable {
            setAudiobookChapterFlags.awaitSetDisplayMode(audiobook, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val audiobook = successState?.audiobook ?: return

        screenModelScope.launchNonCancellable {
            setAudiobookChapterFlags.awaitSetSortingModeOrFlipOrder(audiobook, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val audiobook = successState?.audiobook ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setAudiobookChapterSettingsDefault(audiobook)
            if (applyToExisting) {
                setAudiobookDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.chapter_settings_updated),
            )
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newChapters = successState.processedChapters.toMutableList().apply {
                val selectedIndex = successState.processedChapters.indexOfFirst { it.id == item.chapter.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedChapterIds.addOrRemove(item.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedChapterIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(chapters = newChapters)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newChapters = successState.chapters.map {
                selectedChapterIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(chapters = newChapters)
        }
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val audiobook = successState?.audiobook ?: return
        screenModelScope.launchIO {
            getTracks.subscribe(audiobook.id)
                .catch { logcat(LogPriority.ERROR, it) }
                .map { tracks ->
                    loggedInTrackers
                        // Map to TrackItem
                        .map { service ->
                            AudiobookTrackItem(
                                tracks.find { it.syncId == service.id },
                                service,
                            )
                        }
                        // Show only if the service supports this audiobook's source
                        .filter { (it.tracker as? EnhancedAudiobookTracker)?.accept(source!!) ?: true }
                }
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateSuccessState { it.copy(trackItems = trackItems) }

                }
        }
    }

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val audiobook: Audiobook,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateAudiobook(val audiobook: Audiobook, val duplicate: Audiobook) : Dialog
        data class SetAudiobookFetchInterval(val audiobook: Audiobook) : Dialog
        data class ShowQualities(val chapter: Chapter, val audiobook: Audiobook, val source: AudiobookSource) : Dialog
        data object ChangeAudiobookSkipIntro : Dialog
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    fun showAudiobookSkipIntroDialog() {
        updateSuccessState { it.copy(dialog = Dialog.ChangeAudiobookSkipIntro) }
    }

    private fun showQualitiesDialog(chapter: Chapter) {
        updateSuccessState { it.copy(dialog = Dialog.ShowQualities(chapter, it.audiobook, it.source)) }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val audiobook: Audiobook,
            val source: AudiobookSource,
            val isFromSource: Boolean,
            val chapters: List<ChapterList.Item>,
            val trackItems: List<AudiobookTrackItem> = emptyList(),
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val nextAiringChapter: Pair<Int, Long> = Pair(
                audiobook.nextChapterToAir,
                audiobook.nextChapterAiringAt,
            ),
        ) : State {

            val processedChapters by lazy {
                chapters.applyFilters(audiobook).toList()
            }

            val chapterListItems by lazy {
                processedChapters.insertSeparators { before, after ->
                    val (lowerChapter, higherChapter) = if (audiobook.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherChapter == null) return@insertSeparators null

                    if (lowerChapter == null) {
                        floor(higherChapter.chapter.chapterNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateChapterGap(higherChapter.chapter, lowerChapter.chapter)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            ChapterList.MissingCount(
                                id = "${lowerChapter?.id}-${higherChapter.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val trackingAvailable: Boolean
                get() = trackItems.isNotEmpty()

            val trackingCount: Int
                get() = trackItems.count { it.track != null }


            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(audiobook: Audiobook): Sequence<ChapterList.Item> {
                val isLocalAudiobook = audiobook.isLocal()
                val unreadFilter = audiobook.unreadFilter
                val downloadedFilter = audiobook.downloadedFilter
                val bookmarkedFilter = audiobook.bookmarkedFilter
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAudiobook } }
                    .sortedWith { (chapter1), (chapter2) ->
                        getChapterSort(audiobook).invoke(
                            chapter1,
                            chapter2,
                        )
                    }
            }
        }
    }
}

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: AudiobookDownload.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
    ) : ChapterList() {
        val id = chapter.id
        val isDownloaded = downloadState == AudiobookDownload.State.DOWNLOADED
    }
}
