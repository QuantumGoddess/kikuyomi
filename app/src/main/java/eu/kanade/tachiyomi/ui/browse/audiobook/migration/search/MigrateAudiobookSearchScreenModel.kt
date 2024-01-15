package eu.kanade.tachiyomi.ui.browse.audiobook.migration.search

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.audiobook.source.globalsearch.AudiobookSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAudiobookSearchScreenModel(
    val audiobookId: Long,
    initialExtensionFilter: String = "",
    getAudiobook: GetAudiobook = Injekt.get(),
) : AudiobookSearchScreenModel() {

    init {
        extensionFilter = initialExtensionFilter
        screenModelScope.launch {
            val audiobook = getAudiobook.await(audiobookId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = audiobook.source,
                    searchQuery = audiobook.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<AudiobookCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AudiobookSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
