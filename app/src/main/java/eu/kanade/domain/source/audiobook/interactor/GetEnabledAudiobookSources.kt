package eu.kanade.domain.source.audiobook.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import tachiyomi.domain.source.audiobook.model.Pin
import tachiyomi.domain.source.audiobook.model.Pins
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource

class GetEnabledAudiobookSources(
    private val repository: AudiobookSourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<AudiobookSource>> {
        return combine(
            preferences.pinnedAudiobookSources().changes(),
            preferences.enabledLanguages().changes(),
            preferences.disabledAudiobookSources().changes(),
            preferences.lastUsedAudiobookSource().changes(),
            repository.getAudiobookSources(),
        ) { pinnedSourceIds, enabledLanguages, disabledSources, lastUsedSource, sources ->
            sources
                .filter { it.lang in enabledLanguages || it.id == LocalAudiobookSource.ID }
                .filterNot { it.id.toString() in disabledSources }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val source = it.copy(pin = flag)
                    val toFlatten = mutableListOf(source)
                    if (source.id == lastUsedSource) {
                        toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
    }
}
