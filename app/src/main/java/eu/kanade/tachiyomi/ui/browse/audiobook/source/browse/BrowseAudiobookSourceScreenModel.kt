package eu.kanade.tachiyomi.ui.browse.audiobook.source.browse

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.toDomainAudiobook
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.audiobook.interactor.AddAudiobookTracks
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.preference.CheckboxState
import tachiyomi.core.preference.mapAsCheckboxState
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.category.audiobook.interactor.GetAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.interactor.GetDuplicateLibraryAudiobook
import tachiyomi.domain.entries.audiobook.interactor.NetworkToLocalAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.toAudiobookUpdate
import tachiyomi.domain.items.audiobookchapter.interactor.SetAudiobookDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.audiobook.interactor.GetRemoteAudiobook
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilter as AudiobookSourceModelFilter

class BrowseAudiobookSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: AudiobookSourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    basePreferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: AudiobookCoverCache = Injekt.get(),
    private val getRemoteAudiobook: GetRemoteAudiobook = Injekt.get(),
    private val getDuplicateAudiobooklibAudiobook: GetDuplicateLibraryAudiobook = Injekt.get(),
    private val getCategories: GetAudiobookCategories = Injekt.get(),
    private val setAudiobookCategories: SetAudiobookCategories = Injekt.get(),
    private val setAudiobookDefaultChapterFlags: SetAudiobookDefaultChapterFlags = Injekt.get(),
    private val getAudiobook: GetAudiobook = Injekt.get(),
    private val networkToLocalAudiobook: NetworkToLocalAudiobook = Injekt.get(),
    private val updateAudiobook: UpdateAudiobook = Injekt.get(),
    private val addTracks: AddAudiobookTracks = Injekt.get(),
) : StateScreenModel<BrowseAudiobookSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)

    val source = sourceManager.getOrStub(sourceId)

    init {
        if (source is AudiobookCatalogueSource) {
            mutableState.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                    listing = Listing.Search(query, source.getFilterList())
                }

                it.copy(
                    listing = listing,
                    filters = source.getFilterList(),
                    toolbarQuery = query,
                )
            }
        }

        if (!basePreferences.incognitoMode().get()) {
            sourcePreferences.lastUsedAudiobookSource().set(source.id)
        }
    }

    /**
     * Flow of Pager flow tied to [State.listing]
     */
    private val hideInLibraryItems = sourcePreferences.hideInAudiobookLibraryItems().get()
    val audiobookPagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAudiobook.subscribe(sourceId, listing.query ?: "", listing.filters)
            }.flow.map { pagingData ->
                pagingData.map {
                    networkToLocalAudiobook.await(it.toDomainAudiobook(sourceId))
                        .let { localAudiobook -> getAudiobook.subscribe(localAudiobook.url, localAudiobook.source) }
                        .filterNotNull()
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.favorite }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.audiobookLandscapeColumns()
        } else {
            libraryPreferences.audiobookPortraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    fun resetFilters() {
        if (source !is AudiobookCatalogueSource) return

        mutableState.update { it.copy(filters = source.getFilterList()) }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: AudiobookFilterList) {
        if (source !is AudiobookCatalogueSource) return

        mutableState.update {
            it.copy(
                filters = filters,
            )
        }
    }

    fun search(query: String? = null, filters: AudiobookFilterList? = null) {
        if (source !is AudiobookCatalogueSource) return

        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = source.getFilterList())

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun searchGenre(genreName: String) {
        if (source !is AudiobookCatalogueSource) return

        val defaultFilters = source.getFilterList()
        var genreExists = false

        filter@ for (sourceFilter in defaultFilters) {
            if (sourceFilter is AudiobookSourceModelFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is AudiobookSourceModelFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is AudiobookSourceModelFilter.TriState -> filter.state = 1
                            is AudiobookSourceModelFilter.CheckBox -> filter.state = true
                            else -> {}
                        }
                        genreExists = true
                        break@filter
                    }
                }
            } else if (sourceFilter is AudiobookSourceModelFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    genreExists = true
                    break
                }
            }
        }
        mutableState.update {
            val listing = if (genreExists) {
                Listing.Search(query = null, filters = defaultFilters)
            } else {
                Listing.Search(query = genreName, filters = defaultFilters)
            }
            it.copy(
                filters = defaultFilters,
                listing = listing,
                toolbarQuery = listing.query,
            )
        }
    }

    /**
     * Adds or removes an audiobook from the library.
     *
     * @param audiobook the audiobook to update.
     */
    fun changeAudiobookFavorite(audiobook: Audiobook) {
        screenModelScope.launch {
            var new = audiobook.copy(
                favorite = !audiobook.favorite,
                dateAdded = when (audiobook.favorite) {
                    true -> 0
                    false -> Instant.now().toEpochMilli()
                },
            )

            if (!new.favorite) {
                new = new.removeCovers(coverCache)
            } else {
                setAudiobookDefaultChapterFlags.await(audiobook)
                addTracks.bindEnhancedTrackers(audiobook, source)
            }

            updateAudiobook.await(new.toAudiobookUpdate())
        }
    }

    fun addFavorite(audiobook: Audiobook) {
        screenModelScope.launch {
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultAudiobookCategory().get()
            val defaultCategory = categories.find { it.id == defaultCategoryId.toLong() }

            when {
                // Default category set
                defaultCategory != null -> {
                    moveAudiobookToCategories(audiobook, defaultCategory)

                    changeAudiobookFavorite(audiobook)
                }
                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    moveAudiobookToCategories(audiobook)

                    changeAudiobookFavorite(audiobook)
                }

                // Choose a category
                else -> {
                    val preselectedIds = getCategories.await(audiobook.id).map { it.id }
                    setDialog(
                        Dialog.ChangeAudiobookCategory(
                            audiobook,
                            categories.mapAsCheckboxState { it.id in preselectedIds },
                        ),
                    )
                }
            }
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.subscribe()
            .firstOrNull()
            ?.filterNot { it.isSystemCategory }
            .orEmpty()
    }

    suspend fun getDuplicateAudiobooklibAudiobook(audiobook: Audiobook): Audiobook? {
        return getDuplicateAudiobooklibAudiobook.await(audiobook).getOrNull(0)
    }

    private fun moveAudiobookToCategories(audiobook: Audiobook, vararg categories: Category) {
        moveAudiobookToCategories(audiobook, categories.filter { it.id != 0L }.map { it.id })
    }

    fun moveAudiobookToCategories(audiobook: Audiobook, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setAudiobookCategories.await(
                audiobookId = audiobook.id,
                categoryIds = categoryIds.toList(),
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: AudiobookFilterList) {
        data object Popular : Listing(
            query = GetRemoteAudiobook.QUERY_POPULAR,
            filters = AudiobookFilterList(),
        )
        data object Latest : Listing(
            query = GetRemoteAudiobook.QUERY_LATEST,
            filters = AudiobookFilterList(),
        )
        data class Search(override val query: String?, override val filters: AudiobookFilterList) : Listing(
            query = query,
            filters = filters,
        )

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    GetRemoteAudiobook.QUERY_POPULAR -> Popular
                    GetRemoteAudiobook.QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = AudiobookFilterList()) // filters are filled in later
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
        data class RemoveAudiobook(val audiobook: Audiobook) : Dialog
        data class AddDuplicateAudiobook(val audiobook: Audiobook, val duplicate: Audiobook) : Dialog
        data class ChangeAudiobookCategory(
            val audiobook: Audiobook,
            val initialSelection: List<CheckboxState.State<Category>>,
        ) : Dialog
        data class Migrate(val newAudiobook: Audiobook) : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: AudiobookFilterList = AudiobookFilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }
}
