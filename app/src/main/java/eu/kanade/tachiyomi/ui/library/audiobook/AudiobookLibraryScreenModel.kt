package eu.kanade.tachiyomi.ui.library.audiobook

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastDistinctBy
import eu.kanade.core.util.fastFilter
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastMapNotNull
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.items.audiobookchapter.interactor.SetReadStatus
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.audiobookchapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.preference.CheckboxState
import tachiyomi.core.preference.TriState
import tachiyomi.core.util.lang.compareToWithCollator
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.domain.category.audiobook.interactor.GetVisibleAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.audiobook.interactor.GetLibraryAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.history.audiobook.interactor.GetNextChapters
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.library.audiobook.LibraryAudiobook
import tachiyomi.domain.library.audiobook.model.AudiobookLibrarySort
import tachiyomi.domain.library.audiobook.model.sort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.track.audiobook.interactor.GetTracksPerAudiobook
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections

/**
 * Typealias for the library audiobook, using the category as keys, and list of audiobook as values.
 */
typealias AudiobookLibraryMap = Map<Category, List<AudiobookLibraryItem>>

class AudiobookLibraryScreenModel(
    private val getLibraryAudiobook: GetLibraryAudiobook = Injekt.get(),
    private val getCategories: GetVisibleAudiobookCategories = Injekt.get(),
    private val getTracksPerAudiobook: GetTracksPerAudiobook = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateAudiobook: UpdateAudiobook = Injekt.get(),
    private val setAudiobookCategories: SetAudiobookCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AudiobookCoverCache = Injekt.get(),
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val downloadManager: AudiobookDownloadManager = Injekt.get(),
    private val downloadCache: AudiobookDownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<AudiobookLibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedAudiobookCategory().asState(
        screenModelScope,
    )

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTracksPerAudiobook.subscribe(),
                getTrackingFilterFlow(),
                downloadCache.changes,
            ) { searchQuery, library, tracks, loggedInTrackers, _ ->
                library
                    .applyFilters(tracks, loggedInTrackers)
                    .applySort(tracks)
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            // Filter query
                            value.filter { it.matches(searchQuery) }
                        } else {
                            // Don't do anything
                            value
                        }
                    }
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueViewingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showAudiobookCount, showAudiobookContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showAudiobookCount = showAudiobookCount,
                        showAudiobookContinueButton = showAudiobookContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            getAudiobooklibItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnread,
                    prefs.filterStarted,
                    prefs.filterBookmarked,
                    prefs.filterCompleted,
                ) + trackFilter.values
                ).any { it != TriState.DISABLED }
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)
    }

    /**
     * Applies library filters to the given map of audiobook.
     */
    private suspend fun AudiobookLibraryMap.applyFilters(
        trackMap: Map<Long, List<AudiobookTrack>>,
        loggedInTrackers: Map<Long, TriState>,
    ): AudiobookLibraryMap {
        val prefs = getAudiobooklibItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val filterDownloaded =
            if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnread = prefs.filterUnread
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted

        val isNotLoggedInAnyTrack = loggedInTrackers.isEmpty()

        val excludedTracks = loggedInTrackers.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = loggedInTrackers.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (AudiobookLibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryAudiobook.audiobook.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryAudiobook.audiobook) > 0
            }
        }

        val filterFnUnread: (AudiobookLibraryItem) -> Boolean = {
            applyFilter(filterUnread) { it.libraryAudiobook.unreadCount > 0 }
        }

        val filterFnStarted: (AudiobookLibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAudiobook.hasStarted }
        }

        val filterFnBookmarked: (AudiobookLibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAudiobook.hasBookmarks }
        }

        val filterFnCompleted: (AudiobookLibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryAudiobook.audiobook.status.toInt() == SAudiobook.COMPLETED }
        }

        val filterFnTracking: (AudiobookLibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val audiobookTracks = trackMap
                .mapValues { entry -> entry.value.map { it.syncId } }[item.libraryAudiobook.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && audiobookTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || audiobookTracks.fastAny { it in includedTracks }

            return@tracking !isExcluded && isIncluded
        }

        val filterFn: (AudiobookLibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnread(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                filterFnCompleted(it) &&
                filterFnTracking(it)
        }

        return this.mapValues { entry -> entry.value.fastFilter(filterFn) }
    }

    /**
     * Applies library sorting to the given map of audiobook.
     */
    private fun AudiobookLibraryMap.applySort(
        // Map<MangaId, List<Track>>
        trackMap: Map<Long, List<AudiobookTrack>>,
    ): AudiobookLibraryMap {
        val sortAlphabetically: (AudiobookLibraryItem, AudiobookLibraryItem) -> Int = { i1, i2 ->
            i1.libraryAudiobook.audiobook.title.lowercase().compareToWithCollator(i2.libraryAudiobook.audiobook.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.loggedInTrackers().associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.syncId]?.audiobookService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        val sortFn: (AudiobookLibraryItem, AudiobookLibraryItem) -> Int = { i1, i2 ->
            val sort = keys.find { it.id == i1.libraryAudiobook.category }!!.sort
            when (sort.type) {
                AudiobookLibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                AudiobookLibrarySort.Type.LastRead -> {
                    i1.libraryAudiobook.lastRead.compareTo(i2.libraryAudiobook.lastRead)
                }
                AudiobookLibrarySort.Type.LastUpdate -> {
                    i1.libraryAudiobook.audiobook.lastUpdate.compareTo(i2.libraryAudiobook.audiobook.lastUpdate)
                }
                AudiobookLibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    i1.libraryAudiobook.unreadCount == i2.libraryAudiobook.unreadCount -> 0
                    i1.libraryAudiobook.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                    i2.libraryAudiobook.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                    else -> i1.libraryAudiobook.unreadCount.compareTo(i2.libraryAudiobook.unreadCount)
                }
                AudiobookLibrarySort.Type.TotalChapters -> {
                    i1.libraryAudiobook.totalChapters.compareTo(i2.libraryAudiobook.totalChapters)
                }
                AudiobookLibrarySort.Type.LatestChapter -> {
                    i1.libraryAudiobook.latestUpload.compareTo(i2.libraryAudiobook.latestUpload)
                }
                AudiobookLibrarySort.Type.ChapterFetchDate -> {
                    i1.libraryAudiobook.chapterFetchedAt.compareTo(i2.libraryAudiobook.chapterFetchedAt)
                }
                AudiobookLibrarySort.Type.DateAdded -> {
                    i1.libraryAudiobook.audiobook.dateAdded.compareTo(i2.libraryAudiobook.audiobook.dateAdded)
                }
                AudiobookLibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryAudiobook.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryAudiobook.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
            }
        }

        return this.mapValues { entry ->
            val comparator = if (keys.find { it.id == entry.key.id }!!.sort.isAscending) {
                Comparator(sortFn)
            } else {
                Collections.reverseOrder(sortFn)
            }

            entry.value.sortedWith(comparator.thenComparator(sortAlphabetically))
        }
    }

    private fun getAudiobooklibItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloadedAudiobooks().changes(),
            libraryPreferences.filterUnread().changes(),
            libraryPreferences.filterStartedAudiobooks().changes(),
            libraryPreferences.filterBookmarkedAudiobooks().changes(),
            libraryPreferences.filterCompletedAudiobooks().changes(),
            transform = {
                ItemPreferences(
                    downloadBadge = it[0] as Boolean,
                    localBadge = it[1] as Boolean,
                    languageBadge = it[2] as Boolean,
                    globalFilterDownloaded = it[3] as Boolean,
                    filterDownloaded = it[4] as TriState,
                    filterUnread = it[5] as TriState,
                    filterStarted = it[6] as TriState,
                    filterBookmarked = it[7] as TriState,
                    filterCompleted = it[8] as TriState,
                )
            },
        )
    }

    /**
     * Get the categories and all its audiobook from the database.
     */
    private fun getLibraryFlow(): Flow<AudiobookLibraryMap> {
        val audiobooklibAudiobooksFlow = combine(
            getLibraryAudiobook.subscribe(),
            getAudiobooklibItemPreferencesFlow(),
            downloadCache.changes,
        ) { audiobooklibAudiobookList, prefs, _ ->
            audiobooklibAudiobookList
                .map { audiobooklibAudiobook ->
                    // Display mode based on user preference: take it from global library setting or category
                    AudiobookLibraryItem(
                        audiobooklibAudiobook,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(audiobooklibAudiobook.audiobook).toLong()
                        } else {
                            0
                        },
                        unreadCount = audiobooklibAudiobook.unreadCount,
                        isLocal = if (prefs.localBadge) audiobooklibAudiobook.audiobook.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(audiobooklibAudiobook.audiobook.source).lang
                        } else {
                            ""
                        },
                    )
                }
                .groupBy { it.libraryAudiobook.category }
        }

        return combine(getCategories.subscribe(), audiobooklibAudiobooksFlow) { categories, audiobooklibAudiobook ->
            val displayCategories = if (audiobooklibAudiobook.isNotEmpty() && !audiobooklibAudiobook.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            displayCategories.associateWith { audiobooklibAudiobook[it.id].orEmpty() }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        val loggedInTrackers = trackerManager.loggedInTrackers()
        return if (loggedInTrackers.isNotEmpty()) {
            val prefFlows = loggedInTrackers
                .map { libraryPreferences.filterTrackedAudiobooks(it.id.toInt()).changes() }
                .toTypedArray()
            combine(*prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        } else {
            flowOf(emptyMap())
        }
    }

    /**
     * Returns the common categories for the given list of audiobook.
     *
     * @param audiobooks the list of audiobook.
     */
    private suspend fun getCommonCategories(audiobooks: List<Audiobook>): Collection<Category> {
        if (audiobooks.isEmpty()) return emptyList()
        return audiobooks
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(audiobook: Audiobook): Chapter? {
        return getChaptersByAudiobookId.await(audiobook.id).getNextUnread(audiobook, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of audiobook.
     *
     * @param audiobooks the list of audiobook.
     */
    private suspend fun getMixCategories(audiobooks: List<Audiobook>): Collection<Category> {
        if (audiobooks.isEmpty()) return emptyList()
        val nimeCategories = audiobooks.map { getCategories.await(it.id).toSet() }
        val common = nimeCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return nimeCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val audiobooks = selection.map { it.audiobook }.toList()
        when (action) {
            DownloadAction.NEXT_1_ITEM -> downloadUnreadChapters(audiobooks, 1)
            DownloadAction.NEXT_5_ITEMS -> downloadUnreadChapters(audiobooks, 5)
            DownloadAction.NEXT_10_ITEMS -> downloadUnreadChapters(audiobooks, 10)
            DownloadAction.NEXT_25_ITEMS -> downloadUnreadChapters(audiobooks, 25)
            DownloadAction.UNVIEWED_ITEMS -> downloadUnreadChapters(audiobooks, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unread chapters from the list of audiobooks given.
     *
     * @param audiobooks the list of audiobook.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnreadChapters(audiobooks: List<Audiobook>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            audiobooks.forEach { audiobook ->
                val chapters = getNextChapters.await(audiobook.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                audiobook.title,
                                audiobook.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(audiobook, chapters)
            }
        }
    }

    /**
     * Marks audiobooks' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val audiobooks = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            audiobooks.forEach { audiobook ->
                setReadStatus.await(
                    audiobook = audiobook.audiobook,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected audiobook.
     *
     * @param audiobookList the list of audiobook to delete.
     * @param deleteFromLibrary whether to delete audiobook from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeAudiobooks(audiobookList: List<Audiobook>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        screenModelScope.launchNonCancellable {
            val audiobookToDelete = audiobookList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = audiobookToDelete.map {
                    it.removeCovers(coverCache)
                    AudiobookUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAudiobook.awaitAll(toDelete)
            }

            if (deleteChapters) {
                audiobookToDelete.forEach { audiobook ->
                    val source = sourceManager.get(audiobook.source) as? AudiobookHttpSource
                    if (source != null) {
                        downloadManager.deleteAudiobook(audiobook, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of audiobook using old and new common categories.
     *
     * @param audiobookList the list of audiobook to move.
     * @param addCategories the categories to add for all audiobooks.
     * @param removeCategories the categories to remove in all audiobooks.
     */
    fun setAudiobookCategories(
        audiobookList: List<Audiobook>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            audiobookList.forEach { audiobook ->
                val categoryIds = getCategories.await(audiobook.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setAudiobookCategories.await(audiobook.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.audiobookLandscapeColumns()
            } else {
                libraryPreferences.audiobookPortraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomAudiobooklibItemForCurrentCategory(): AudiobookLibraryItem? {
        if (state.value.categories.isEmpty()) return null

        return withIOContext {
            state.value
                .getAudiobooklibItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(audiobook: LibraryAudiobook) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == audiobook.id }) {
                    list.removeAll { it.id == audiobook.id }
                } else {
                    list.add(audiobook)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all nimes between and including the given audiobook and the last pressed audiobook from the
     * same category as the given audiobook
     */
    fun toggleRangeSelection(audiobook: LibraryAudiobook) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.category != audiobook.category) {
                    list.add(audiobook)
                    return@mutate
                }

                val items = state.getAudiobooklibItemsByCategoryId(audiobook.category)
                    ?.fastMap { it.libraryAudiobook }.orEmpty()
                val lastAudiobookIndex = items.indexOf(lastSelected)
                val curAudiobookIndex = items.indexOf(audiobook)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastAudiobookIndex < curAudiobookIndex -> IntRange(lastAudiobookIndex, curAudiobookIndex)
                    curAudiobookIndex < lastAudiobookIndex -> IntRange(curAudiobookIndex, lastAudiobookIndex)
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                val newSelections = selectionRange.mapNotNull { index ->
                    items[index].takeUnless { it.id in selectedIds }
                }
                list.addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = list.fastMap { it.id }
                state.getAudiobooklibItemsByCategoryId(categoryId)
                    ?.fastMapNotNull { item ->
                        item.libraryAudiobook.takeUnless { it.id in selectedIds }
                    }
                    ?.let { list.addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories[index].id
                val items = state.getAudiobooklibItemsByCategoryId(categoryId)?.fastMap { it.libraryAudiobook }.orEmpty()
                val selectedIds = list.fastMap { it.id }
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.fastMap { it.id }
                list.removeAll { it.id in toRemoveIds }
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected audiobook
            val audiobookList = state.value.selection.map { it.audiobook }

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(audiobookList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(audiobookList)
            val preselected = categories.map {
                when (it) {
                    in common -> CheckboxState.State.Checked(it)
                    in mix -> CheckboxState.TriState.Exclude(it)
                    else -> CheckboxState.State.None(it)
                }
            }
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(audiobookList, preselected)) }
        }
    }

    fun openDeleteAudiobookDialog() {
        val nimeList = state.value.selection.map { it.audiobook }
        mutableState.update { it.copy(dialog = Dialog.DeleteAudiobook(nimeList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val audiobook: List<Audiobook>,
            val initialSelection: List<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteAudiobook(val audiobook: List<Audiobook>) : Dialog
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnread: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        val filterCompleted: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: AudiobookLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryAudiobook> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showAudiobookCount: Boolean = false,
        val showAudiobookContinueButton: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryAudiobook.audiobook.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        fun getAudiobooklibItemsByCategoryId(categoryId: Long): List<AudiobookLibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getAudiobooklibItemsByPage(page: Int): List<AudiobookLibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getAudiobookCountForCategory(category: Category): Int? {
            return if (showAudiobookCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showAudiobookCount -> null
                !showCategoryTabs -> getAudiobookCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}
