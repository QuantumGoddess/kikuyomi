package eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch

import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource

class GlobalAudiobookSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : AudiobookSearchScreenModel(
    State(
        searchQuery = initialQuery,
    ),
) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(AudiobookSourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<AudiobookCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AudiobookSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
