package eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.entries.audiobook.model.toDomainAudiobook
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.interactor.NetworkToLocalAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

abstract class AudiobookSearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val extensionManager: AudiobookExtensionManager = Injekt.get(),
    private val networkToLocalAudiobook: NetworkToLocalAudiobook = Injekt.get(),
    private val getAudiobook: GetAudiobook = Injekt.get(),
) : StateScreenModel<AudiobookSearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledAudiobookSources().get()
    protected val pinnedSources = sourcePreferences.pinnedAudiobookSources().get()

    private var lastQuery: String? = null
    private var lastSourceFilter: AudiobookSourceFilter? = null

    protected var extensionFilter: String? = null

    private val sortComparator = { map: Map<AudiobookCatalogueSource, AudiobookSearchItemResult> ->
        compareBy<AudiobookCatalogueSource>(
            { (map[it] as? AudiobookSearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    @Composable
    fun getAudiobook(initialAudiobook: Audiobook): androidx.compose.runtime.State<Audiobook> {
        return produceState(initialValue = initialAudiobook) {
            getAudiobook.subscribe(initialAudiobook.url, initialAudiobook.source)
                .filterNotNull()
                .collectLatest { audiobook ->
                    value = audiobook
                }
        }
    }

    open fun getEnabledSources(): List<AudiobookCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun getSelectedSources(): List<AudiobookCatalogueSource> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filterIsInstance<AudiobookCatalogueSource>()
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: AudiobookSourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        mutableState.update { it.copy(onlyShowHasResults = !it.onlyShowHasResults) }
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return
        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: AudiobookSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { AudiobookSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is AudiobookSearchItemResult.Loading) {
                        return@async
                    }
                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchAudiobook(1, query, source.getFilterList())
                        }

                        val titles = page.audiobooks.map {
                            networkToLocalAudiobook.await(it.toDomainAudiobook(source.id))
                        }

                        if (isActive) {
                            updateItem(source, AudiobookSearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, AudiobookSearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: PersistentMap<AudiobookCatalogueSource, AudiobookSearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: AudiobookCatalogueSource, result: AudiobookSearchItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
    }

    @Immutable
    data class State(
        val fromSourceId: Long? = null,
        val searchQuery: String? = null,
        val sourceFilter: AudiobookSourceFilter = AudiobookSourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<AudiobookCatalogueSource, AudiobookSearchItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is AudiobookSearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }
}

enum class AudiobookSourceFilter {
    All,
    PinnedOnly,
}

sealed interface AudiobookSearchItemResult {
    data object Loading : AudiobookSearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : AudiobookSearchItemResult

    data class Success(
        val result: List<Audiobook>,
    ) : AudiobookSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
