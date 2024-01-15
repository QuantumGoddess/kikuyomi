package eu.kanade.domain.source.audiobook.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository
import java.util.SortedMap

class GetLanguagesWithAudiobookSources(
    private val repository: AudiobookSourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<SortedMap<String, List<AudiobookSource>>> {
        return combine(
            preferences.enabledLanguages().changes(),
            preferences.disabledAudiobookSources().changes(),
            repository.getOnlineAudiobookSources(),
        ) { enabledLanguage, disabledSource, onlineSources ->
            val sortedSources = onlineSources.sortedWith(
                compareBy<AudiobookSource> { it.id.toString() in disabledSource }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

            sortedSources
                .groupBy { it.lang }
                .toSortedMap(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
